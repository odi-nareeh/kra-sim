package com.krasim.compliance.service;

import com.krasim.compliance.dto.DashboardView;
import com.krasim.compliance.dto.DashboardView.InvoiceView;
import com.krasim.compliance.dto.DashboardView.LedgerView;
import com.krasim.compliance.dto.DashboardView.TaxpayerView;
import com.krasim.compliance.entity.TaxLedger;
import com.krasim.compliance.entity.Taxpayer;
import com.krasim.compliance.exception.DuplicateTaxpayerException;
import com.krasim.compliance.repository.InvoiceRepository;
import com.krasim.compliance.repository.TaxLedgerRepository;
import com.krasim.compliance.repository.TaxpayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class TaxpayerService {

    private final TaxpayerRepository taxpayerRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxLedgerRepository ledgerRepository;
    private final AuditService auditService;

    public TaxpayerService(TaxpayerRepository taxpayerRepository,
            InvoiceRepository invoiceRepository,
            TaxLedgerRepository ledgerRepository,
            AuditService auditService) {
        this.taxpayerRepository = taxpayerRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditService = auditService;
    }

    /** Creates the taxpayer and its empty ledger together. */
    @Transactional
    public void register(String pin, String companyName, String email) {
        if (taxpayerRepository.existsById(pin)) {
            throw new DuplicateTaxpayerException(pin);
        }
        Taxpayer taxpayer = taxpayerRepository.save(new Taxpayer(pin, companyName, email));
        ledgerRepository.save(new TaxLedger(taxpayer));
        auditService.record(AuditAction.TAXPAYER_REGISTERED, pin, "Company: " + companyName);
    }

    /**
     * Builds the dashboard data for one PIN, or empty if the PIN is not registered.
     */
    @Transactional(readOnly = true)
    public Optional<DashboardView> findDashboard(String pin) {
        return taxpayerRepository.findById(pin).map(taxpayer -> {

            LedgerView ledger = ledgerRepository.findByTaxpayerPin(pin)
                    .map(l -> new LedgerView(l.getTotalVatDue(), l.getLastUpdated()))
                    .orElse(new LedgerView(BigDecimal.ZERO.setScale(2), null));

            List<InvoiceView> recent = invoiceRepository
                    .findTop10ByTaxpayerPinOrderByCreatedAtDesc(pin).stream()
                    .map(i -> new InvoiceView(i.getId(), i.getControlUnitInvoiceNo(),
                            i.getDevice() != null ? i.getDevice().getSerial() : null,
                            i.getAmountBeforeTax(), i.getVatAmount(),
                            i.getTotalAmount(), i.getCreatedAt()))
                    .toList();

            return new DashboardView(
                    new TaxpayerView(taxpayer.getPin(), taxpayer.getCompanyName(), taxpayer.getEmail()),
                    ledger,
                    invoiceRepository.countByTaxpayerPin(pin),
                    recent);
        });
    }
}