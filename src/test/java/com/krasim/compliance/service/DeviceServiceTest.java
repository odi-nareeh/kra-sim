package com.krasim.compliance.service;

import com.krasim.compliance.dto.PosDevicePrincipal;
import com.krasim.compliance.entity.PosDevice;
import com.krasim.compliance.repository.PosDeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceServiceTest {

    @Mock
    private PosDeviceRepository repository;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private DeviceService service;

    /**
     * Registers a device against the mock repository and makes it findable by its
     * key prefix.
     */
    private DeviceService.IssuedKey registerDevice() {
        when(repository.save(any(PosDevice.class))).thenAnswer(call -> call.getArgument(0));
        DeviceService.IssuedKey issued = service.register("Till 1");
        ReflectionTestUtils.setField(issued.device(), "id", 1); // normally set by the database
        when(repository.findByKeyPrefix(issued.device().getKeyPrefix())).thenReturn(Optional.of(issued.device()));
        return issued;
    }

    @Test
    void registerReturnsAKeyAndStoresOnlyItsHash() {
        DeviceService.IssuedKey issued = registerDevice();

        String rawKey = issued.rawKey();
        String secret = rawKey.substring(13);

        assertTrue(rawKey.startsWith("kra_"));
        assertEquals(64, issued.device().getKeyHash().length());
        assertFalse(issued.device().getKeyHash().contains(secret), "the secret must not be stored");
        assertEquals("SCU000001", issued.device().getSerial());
    }

    @Test
    void validKeyAuthenticatesAndRecordsUse() {
        DeviceService.IssuedKey issued = registerDevice();

        Optional<PosDevicePrincipal> principal = service.authenticate(issued.rawKey());

        assertTrue(principal.isPresent());
        assertEquals(Integer.valueOf(1), principal.get().deviceId());
        assertNotNull(issued.device().getLastUsedAt());
    }

    @Test
    void wrongSecretIsRejected() {
        DeviceService.IssuedKey issued = registerDevice();
        String forged = issued.rawKey().substring(0, 13) + "0".repeat(64);

        assertTrue(service.authenticate(forged).isEmpty());
    }

    @Test
    void malformedKeysAreRejected() {
        assertTrue(service.authenticate(null).isEmpty());
        assertTrue(service.authenticate("").isEmpty());
        assertTrue(service.authenticate("garbage").isEmpty());
        assertTrue(service.authenticate("kra_short").isEmpty());
    }

    @Test
    void revokedDeviceIsRejected() {
        DeviceService.IssuedKey issued = registerDevice();
        issued.device().revoke();

        assertTrue(service.authenticate(issued.rawKey()).isEmpty());
    }

    @Test
    void rotationInvalidatesTheOldKeyAndActivatesTheNewOne() {
        DeviceService.IssuedKey issued = registerDevice();
        String oldKey = issued.rawKey();
        when(repository.findById(1)).thenReturn(Optional.of(issued.device()));

        DeviceService.IssuedKey rotated = service.rotate(1);
        when(repository.findByKeyPrefix(rotated.device().getKeyPrefix())).thenReturn(Optional.of(rotated.device()));

        assertNotEquals(oldKey, rotated.rawKey());
        assertTrue(service.authenticate(oldKey).isEmpty(), "old key must stop working");
        assertTrue(service.authenticate(rotated.rawKey()).isPresent(), "new key must work");
    }

    @Test
    void revokedDeviceCannotBeRotated() {
        DeviceService.IssuedKey issued = registerDevice();
        issued.device().revoke();
        when(repository.findById(1)).thenReturn(Optional.of(issued.device()));

        assertThrows(IllegalStateException.class, () -> service.rotate(1));
    }
}