package com.krasim.compliance.integration;

import com.krasim.compliance.dto.InvoiceResponse;
import com.krasim.compliance.exception.DeviceNotActiveException;
import com.krasim.compliance.service.AuditAction;
import com.krasim.compliance.service.AuditService;
import com.krasim.compliance.service.DeviceService;
import com.krasim.compliance.service.DeviceService.IssuedKey;
import com.krasim.compliance.service.InvoiceService;
import com.krasim.compliance.service.TaxpayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against a real PostgreSQL started by Testcontainers. Rows are never cleaned up
 * (the audit table is append-only by design), so each test uses its own PIN and device.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger PIN_SEQUENCE = new AtomicInteger(100_000_000);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TaxpayerService taxpayerService;
    @Autowired private DeviceService deviceService;
    @Autowired private InvoiceService invoiceService;
    @Autowired private AuditService auditService;

    private static String newPin() {
        return "A" + PIN_SEQUENCE.incrementAndGet() + "Z"; // letter + 9 digits + letter
    }

    private int auditRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Integer.class);
    }

    private void seedAuditRow() {
        auditService.record("integration-test", AuditAction.LOGIN_FAILURE, null, "seed row", "127.0.0.1");
    }

    // ================= Schema =================

    @Test
    void flywayMigrationsAreApplied() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL",
                String.class);

        assertTrue(versions.containsAll(List.of("1", "2", "3", "4")), "applied: " + versions);
    }

    @Test
    void databaseRejectsMalformedPins() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO taxpayers (pin, company_name, email) VALUES ('bad-pin', 'X', 'x@x.co.ke')"));
    }

        @Test
    void seededAccountsHaveTheirRoles() {
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM officers WHERE role = 'OFFICER'", Integer.class) >= 1);
        assertTrue(jdbc.queryForObject(
                "SELECT COUNT(*) FROM officers WHERE role = 'AUDITOR'", Integer.class) >= 1);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM officers WHERE password_hash NOT LIKE '$2%'", Integer.class),
                "every password must be a BCrypt hash");
    }

    @Test
    void databaseRejectsUnknownRoles() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO officers (username, password_hash, role) VALUES ('rogue', 'h', 'ROOT')"));
    }

    // ================= Append-only audit table =================

    @Test
    void auditRowsCannotBeUpdated() {
        seedAuditRow();

        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE audit_events SET actor = 'nobody'"));

        assertTrue(ex.getMessage().contains("append-only"), ex.getMessage());
    }

    @Test
    void auditRowsCannotBeDeleted() {
        seedAuditRow();
        int before = auditRows();

        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> jdbc.update("DELETE FROM audit_events"));

        assertTrue(ex.getMessage().contains("append-only"), ex.getMessage());
        assertEquals(before, auditRows());
    }

    @Test
    void auditTableCannotBeTruncated() {
        seedAuditRow();
        int before = auditRows();

        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> jdbc.execute("TRUNCATE TABLE audit_events"));

        assertTrue(ex.getMessage().contains("append-only"), ex.getMessage());
        assertEquals(before, auditRows());
    }

    // ================= Concurrent invoicing =================

    @Test
    void concurrentInvoicesGetUniqueGaplessNumbersAndACorrectLedger() throws Exception {
        String pin = newPin();
        taxpayerService.register(pin, "Concurrent Co", "ops@concurrent.co.ke");
        IssuedKey issued = deviceService.register("Concurrency test till");
        Integer deviceId = issued.device().getId();
        String serial = issued.device().getSerial();

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<InvoiceResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await(); // release every thread at the same moment
                return invoiceService.submitInvoice(pin, new BigDecimal("1000"), deviceId, "127.0.0.1");
            }));
        }
        start.countDown();

        Set<String> numbers = new HashSet<>();
        for (Future<InvoiceResponse> future : futures) {
            numbers.add(future.get(60, TimeUnit.SECONDS).controlUnitInvoiceNo());
        }
        pool.shutdown();

        Set<String> expected = IntStream.rangeClosed(1, threads)
                .mapToObj(i -> serial + "/" + String.format("%06d", i))
                .collect(Collectors.toSet());
        assertEquals(expected, numbers, "numbers must be unique and gapless");

        // 20 invoices x KSh 160 VAT = KSh 3,200.00
        BigDecimal ledger = jdbc.queryForObject(
                "SELECT total_vat_due FROM tax_ledger WHERE taxpayer_pin = ?", BigDecimal.class, pin);
        assertEquals(0, new BigDecimal("3200.00").compareTo(ledger), "ledger was " + ledger);

        assertEquals(threads, jdbc.queryForObject(
                "SELECT last_invoice_no FROM pos_devices WHERE device_id = ?", Integer.class, deviceId));
        assertEquals(threads, jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE taxpayer_pin = ?", Integer.class, pin));
    }

    // ================= Devices =================

    @Test
    void deviceKeyLifecycleAndAuditTrail() {
        IssuedKey issued = deviceService.register("Lifecycle till");
        Integer id = issued.device().getId();
        String serial = issued.device().getSerial();
        String secret = issued.rawKey().substring(13); // after "kra_" + 8-char prefix + "_"

        assertTrue(deviceService.authenticate(issued.rawKey()).isPresent());

        IssuedKey rotated = deviceService.rotate(id);
        assertTrue(deviceService.authenticate(issued.rawKey()).isEmpty(), "old key must stop working");
        assertTrue(deviceService.authenticate(rotated.rawKey()).isPresent(), "new key must work");

        deviceService.revoke(id);
        assertTrue(deviceService.authenticate(rotated.rawKey()).isEmpty(), "revoked device must be rejected");

        // The audit rows were written with the actions
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'DEVICE_REVOKED' AND target = ?",
                Integer.class, serial));

        // The raw key secret never reaches the audit table or the database at all
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE details LIKE ? OR target LIKE ?",
                Integer.class, "%" + secret + "%", "%" + secret + "%"));
        assertFalse(jdbc.queryForObject(
                "SELECT key_hash FROM pos_devices WHERE device_id = ?", String.class, id).contains(secret));
    }

    @Test
    void revokedDeviceCannotSubmitAndNothingIsWritten() {
        String pin = newPin();
        taxpayerService.register(pin, "Revoked Device Co", "ops@revoked.co.ke");
        IssuedKey issued = deviceService.register("Soon revoked till");
        Integer deviceId = issued.device().getId();
        deviceService.revoke(deviceId);

        assertThrows(DeviceNotActiveException.class,
                () -> invoiceService.submitInvoice(pin, new BigDecimal("1000"), deviceId, "127.0.0.1"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE taxpayer_pin = ?", Integer.class, pin));
        BigDecimal ledger = jdbc.queryForObject(
                "SELECT total_vat_due FROM tax_ledger WHERE taxpayer_pin = ?", BigDecimal.class, pin);
        assertEquals(0, BigDecimal.ZERO.compareTo(ledger));
    }
}