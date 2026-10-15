package com.krasim.compliance.dto;

import com.krasim.compliance.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What the API returns after an invoice is accepted. */
public record InvoiceResponse(
        Integer invoiceId,
        String controlUnitInvoiceNo,
        String taxpayerPin,
        String deviceSerial,
        BigDecimal amountBeforeTax,
        BigDecimal vatAmount,
        BigDecimal totalAmount,
        LocalDateTime createdAt) {
    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getControlUnitInvoiceNo(),
                invoice.getTaxpayer().getPin(),
                invoice.getDevice() != null ? invoice.getDevice().getSerial() : null,
                invoice.getAmountBeforeTax(),
                invoice.getVatAmount(),
                invoice.getTotalAmount(),
                invoice.getCreatedAt());
    }
}