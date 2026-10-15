package com.krasim.compliance.service;

import com.krasim.compliance.dto.InvoiceResponse;
import com.krasim.compliance.entity.Invoice;
import com.krasim.compliance.entity.PosDevice;
import com.krasim.compliance.entity.TaxLedger;
import com.krasim.compliance.entity.Taxpayer;
import com.krasim.compliance.exception.DeviceNotActiveException;
import com.krasim.compliance.exception.TaxpayerNotFoundException;
import com.krasim.compliance.repository.InvoiceRepository;
import com.krasim.compliance.repository.PosDeviceRepository;
import com.krasim.compliance.repository.TaxLedgerRepository;
import com.krasim.compliance.repository.TaxpayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final String PIN = "A112233445Z";
    private static final Integer DEVICE_ID = 1;
    private static final String IP = "197.232.10.5";

    @Mock
    private TaxpayerRepository taxpayerRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private TaxLedgerRepository ledgerRepository;
    @Mock
    private PosDeviceRepository deviceRepository;

    @InjectMocks
    private InvoiceService service;

    private Taxpayer taxpayer;
    private TaxLedger ledger;
    private PosDevice device;

    @BeforeEach
    void setUp() {
        taxpayer = new Taxpayer(PIN, "Nairobi Tech Supplies LTD", "finance@nairobitech.co.ke");
        ledger = new TaxLedger(taxpayer); // starts at 0.00
        device = new PosDevice("Till 1", "a1b2c3d4", "hash");
        ReflectionTestUtils.setField(device, "id", DEVICE_ID); // normally set by the database
    }

    private void stubHappyPath() {
        when(taxpayerRepository.findById(PIN)).thenReturn(Optional.of(taxpayer));
        when(deviceRepository.findForUpdateById(DEVICE_ID)).thenReturn(Optional.of(device));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(call -> call.getArgument(0));
        when(ledgerRepository.findForUpdateByTaxpayerPin(PIN)).thenReturn(Optional.of(ledger));
    }

    @Test
    void tenThousandSaleProducesSixteenHundredVat() {
        stubHappyPath();

        InvoiceResponse response = service.submitInvoice(PIN, new BigDecimal("10000"), DEVICE_ID, IP);

        assertEquals(new BigDecimal("10000.00"), response.amountBeforeTax());
        assertEquals(new BigDecimal("1600.00"), response.vatAmount());
        assertEquals(new BigDecimal("11600.00"), response.totalAmount());
    }

    @Test
    void ledgerIsIncrementedByTheInvoiceVat() {
        stubHappyPath();

        service.submitInvoice(PIN, new BigDecimal("10000"), DEVICE_ID, IP);

        assertEquals(new BigDecimal("1600.00"), ledger.getTotalVatDue());
        verify(ledgerRepository).save(ledger);
    }

    @Test
    void ledgerAccumulatesAcrossInvoices() {
        stubHappyPath();

        service.submitInvoice(PIN, new BigDecimal("10000"), DEVICE_ID, IP);
        service.submitInvoice(PIN, new BigDecimal("5000"), DEVICE_ID, IP);

        assertEquals(new BigDecimal("2400.00"), ledger.getTotalVatDue());
    }

    @Test
    void vatIsRoundedToTheNearestCent() {
        stubHappyPath();

        // 99.99 x 0.16 = 15.9984 -> 16.00
        InvoiceResponse response = service.submitInvoice(PIN, new BigDecimal("99.99"), DEVICE_ID, IP);

        assertEquals(new BigDecimal("16.00"), response.vatAmount());
        assertEquals(new BigDecimal("115.99"), response.totalAmount());
    }

    @Test
    void pinIsNormalisedToUpperCaseAndTrimmed() {
        stubHappyPath();

        InvoiceResponse response = service.submitInvoice("  a112233445z ", new BigDecimal("1000"), DEVICE_ID, IP);

        assertEquals(PIN, response.taxpayerPin());
    }

    @Test
    void invoiceGetsTheDevicesControlUnitNumber() {
        stubHappyPath();

        InvoiceResponse response = service.submitInvoice(PIN, new BigDecimal("1000"), DEVICE_ID, IP);

        assertEquals("SCU000001/000001", response.controlUnitInvoiceNo());
        assertEquals("SCU000001", response.deviceSerial());
    }

    @Test
    void controlUnitNumbersIncreaseForEachInvoiceOnTheSameDevice() {
        stubHappyPath();

        InvoiceResponse first = service.submitInvoice(PIN, new BigDecimal("1000"), DEVICE_ID, IP);
        InvoiceResponse second = service.submitInvoice(PIN, new BigDecimal("1000"), DEVICE_ID, IP);

        assertEquals("SCU000001/000001", first.controlUnitInvoiceNo());
        assertEquals("SCU000001/000002", second.controlUnitInvoiceNo());
    }

    @Test
    void auditFieldsAreStoredOnTheInvoice() {
        stubHappyPath();

        service.submitInvoice(PIN, new BigDecimal("1000"), DEVICE_ID, IP);

        ArgumentCaptor<Invoice> saved = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(saved.capture());
        assertSame(device, saved.getValue().getDevice());
        assertEquals(IP, saved.getValue().getSourceIp());
        assertEquals("SCU000001/000001", saved.getValue().getControlUnitInvoiceNo());
    }

    @Test
    void revokedDeviceCannotSubmitAndNothingIsSaved() {
        device.revoke();
        when(taxpayerRepository.findById(PIN)).thenReturn(Optional.of(taxpayer));
        when(deviceRepository.findForUpdateById(DEVICE_ID)).thenReturn(Optional.of(device));

        assertThrows(DeviceNotActiveException.class,
                () -> service.submitInvoice(PIN, new BigDecimal("1000"), DEVICE_ID, IP));

        verify(invoiceRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void unknownTaxpayerIsRejectedAndNothingIsSaved() {
        when(taxpayerRepository.findById("A999999999Z")).thenReturn(Optional.empty());

        assertThrows(TaxpayerNotFoundException.class,
                () -> service.submitInvoice("A999999999Z", new BigDecimal("1000"), DEVICE_ID, IP));

        verifyNoInteractions(deviceRepository);
        verify(invoiceRepository, never()).save(any());
        verify(ledgerRepository, never()).save(any());
    }
}