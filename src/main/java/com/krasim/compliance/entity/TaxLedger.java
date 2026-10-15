package com.krasim.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_ledger")
public class TaxLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "taxpayer_pin", nullable = false, unique = true)
    private Taxpayer taxpayer;

    @Column(name = "total_vat_due", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalVatDue;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    protected TaxLedger() {
        // required by JPA
    }

    /** A new ledger starts at zero liability. */
    public TaxLedger(Taxpayer taxpayer) {
        this.taxpayer = taxpayer;
        this.totalVatDue = BigDecimal.ZERO.setScale(2);
        this.lastUpdated = LocalDateTime.now();
    }

    /** Adds VAT from a new invoice to the running liability. */
    public void addVat(BigDecimal vat) {
        this.totalVatDue = this.totalVatDue.add(vat);
        this.lastUpdated = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public Taxpayer getTaxpayer() {
        return taxpayer;
    }

    public BigDecimal getTotalVatDue() {
        return totalVatDue;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }
}