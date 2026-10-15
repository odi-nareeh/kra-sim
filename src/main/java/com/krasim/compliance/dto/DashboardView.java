package com.krasim.compliance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Everything the dashboard page needs, shaped for the Thymeleaf template. */
public record DashboardView(
                TaxpayerView taxpayer,
                LedgerView ledger,
                long invoiceCount,
                List<InvoiceView> recentInvoices) {
        public record TaxpayerView(String pin, String companyName, String email) {
        }

        public record LedgerView(BigDecimal totalVatDue, LocalDateTime lastUpdated) {
        }

        public record InvoiceView(Integer id, String controlUnitInvoiceNo, String deviceSerial,
                        BigDecimal amountBeforeTax, BigDecimal vatAmount,
                        BigDecimal totalAmount, LocalDateTime createdAt) {
        }
}