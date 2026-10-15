package com.krasim.compliance.service;

import com.krasim.compliance.dto.PosDevicePrincipal;
import com.krasim.compliance.entity.PosDevice;
import com.krasim.compliance.repository.PosDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceService {

    private static final String TAG = "kra_";
    private static final int PREFIX_LENGTH = 8;
    private static final int SEPARATOR_INDEX = TAG.length() + PREFIX_LENGTH; // 12

    /**
     * The raw key exists only in this object. It is never stored and cannot be
     * recovered later.
     */
    public record IssuedKey(PosDevice device, String rawKey) {
    }

    private final PosDeviceRepository repository;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public DeviceService(PosDeviceRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public IssuedKey register(String deviceName) {
        String prefix = newUniquePrefix();
        String secret = randomHex(32);
        PosDevice device = repository.save(new PosDevice(deviceName.trim(), prefix, sha256(secret)));
        auditService.record(AuditAction.DEVICE_REGISTERED, device.getSerial(), "Name: " + device.getName());
        return new IssuedKey(device, buildKey(prefix, secret));
    }

    @Transactional
    public IssuedKey rotate(Integer deviceId) {
        PosDevice device = repository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found."));
        if (!device.isActive()) {
            throw new IllegalStateException("A revoked device cannot be rotated. Register a new device instead.");
        }
        String prefix = newUniquePrefix();
        String secret = randomHex(32);
        device.rotateKey(prefix, sha256(secret));
        auditService.record(AuditAction.DEVICE_KEY_ROTATED, device.getSerial(), "Previous key invalidated");
        return new IssuedKey(device, buildKey(prefix, secret));
    }

    @Transactional
    public void revoke(Integer deviceId) {
        PosDevice device = repository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found."));
        boolean wasActive = device.isActive();
        device.revoke();
        if (wasActive) {
            auditService.record(AuditAction.DEVICE_REVOKED, device.getSerial(), "Name: " + device.getName());
        }
    }

    @Transactional(readOnly = true)
    public List<PosDevice> findAll() {
        return repository.findAllByOrderByIdAsc();
    }

    /**
     * Checks a raw X-API-Key value. Returns the device only if the key is
     * well-formed,
     * the prefix matches an ACTIVE device, and the secret's hash matches
     * (constant-time compare).
     */
    @Transactional
    public Optional<PosDevicePrincipal> authenticate(String rawKey) {
        if (rawKey == null
                || rawKey.length() <= SEPARATOR_INDEX + 1
                || !rawKey.startsWith(TAG)
                || rawKey.charAt(SEPARATOR_INDEX) != '_') {
            return Optional.empty();
        }

        String prefix = rawKey.substring(TAG.length(), SEPARATOR_INDEX);
        byte[] providedHash = sha256(rawKey.substring(SEPARATOR_INDEX + 1)).getBytes(StandardCharsets.UTF_8);

        return repository.findByKeyPrefix(prefix)
                .filter(PosDevice::isActive)
                .filter(device -> MessageDigest.isEqual(
                        device.getKeyHash().getBytes(StandardCharsets.UTF_8), providedHash))
                .map(device -> {
                    device.markUsed();
                    return new PosDevicePrincipal(device.getId(), device.getSerial(), device.getName());
                });
    }

    // ---------- helpers ----------

    private String newUniquePrefix() {
        String prefix;
        do {
            prefix = randomHex(PREFIX_LENGTH / 2);
        } while (repository.existsByKeyPrefix(prefix));
        return prefix;
    }

    private static String buildKey(String prefix, String secret) {
        return TAG + prefix + "_" + secret;
    }

    private String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}