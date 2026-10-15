package com.krasim.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "pos_devices")
public class PosDevice {

    public enum Status {
        ACTIVE, REVOKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Integer id;

    @Column(name = "device_name", nullable = false, length = 100)
    private String name;

    @Column(name = "key_prefix", nullable = false, unique = true, length = 8)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(name = "last_invoice_no", nullable = false)
    private int lastInvoiceNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected PosDevice() {
        // required by JPA
    }

    public PosDevice(String name, String keyPrefix, String keyHash) {
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Sales Control Unit serial shown to users, derived from the id, e.g.
     * SCU000001.
     */
    public String getSerial() {
        return String.format("SCU%06d", id);
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** Replaces the key. The old key stops working immediately. */
    public void rotateKey(String newPrefix, String newHash) {
        this.keyPrefix = newPrefix;
        this.keyHash = newHash;
    }

    /** Revocation is permanent: register a new device instead. */
    public void revoke() {
        if (status == Status.REVOKED) {
            return;
        }
        this.status = Status.REVOKED;
        this.revokedAt = LocalDateTime.now();
    }

    public void markUsed() {
        this.lastUsedAt = LocalDateTime.now();
    }

    /** Next control unit invoice number for this device, e.g. SCU000001/000001. */
    public String nextControlUnitInvoiceNumber() {
        lastInvoiceNo++;
        return getSerial() + "/" + String.format("%06d", lastInvoiceNo);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public Status getStatus() {
        return status;
    }

    public int getLastInvoiceNo() {
        return lastInvoiceNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}