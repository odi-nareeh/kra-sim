package com.krasim.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Integer id;

    @Column(name = "control_unit_invoice_no", nullable = false, unique = true, length = 40)
    private String controlUnitInvoiceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "taxpayer_pin", nullable = false)
    private Taxpayer taxpayer;

    /**
     * The POS device that submitted the invoice. Null only for invoices created
     * before auditing.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private PosDevice device;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "amount_before_tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountBeforeTax;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Invoice() {
        // required by JPA
    }

    public Invoice(Taxpayer taxpayer, BigDecimal amountBeforeTax, BigDecimal vatAmount, BigDecimal totalAmount,
            PosDevice device, String controlUnitInvoiceNo, String sourceIp) {
        this.taxpayer = taxpayer;
        this.amountBeforeTax = amountBeforeTax;
        this.vatAmount = vatAmount;
        this.totalAmount = totalAmount;
        this.device = device;
        this.controlUnitInvoiceNo = controlUnitInvoiceNo;
        this.sourceIp = sourceIp;
        this.createdAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public String getControlUnitInvoiceNo() {
        return controlUnitInvoiceNo;
    }

    public Taxpayer getTaxpayer() {
        return taxpayer;
    }

    public PosDevice getDevice() {
        return device;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public BigDecimal getAmountBeforeTax() {
        return amountBeforeTax;
    }

    public BigDecimal getVatAmount() {
        return vatAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}