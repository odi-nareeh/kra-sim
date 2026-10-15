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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class InvoiceService {

    /** Kenya's standard VAT rate. */
    private static final BigDecimal VAT_RATE = new BigDecimal("0.16");

    private final TaxpayerRepository taxpayerRepository;
    private final InvoiceRepository invoiceRepository;
    private final TaxLedgerRepository ledgerRepository;
    private final PosDeviceRepository deviceRepository;

    public InvoiceService(TaxpayerRepository taxpayerRepository,
            InvoiceRepository invoiceRepository,
            TaxLedgerRepository ledgerRepository,
            PosDeviceRepository deviceRepository) {
        this.taxpayerRepository = taxpayerRepository;
        this.invoiceRepository = invoiceRepository;
        this.ledgerRepository = ledgerRepository;
        this.deviceRepository = deviceRepository;
    }

    /**
     * One atomic unit of work: validate, number the invoice, calculate VAT, save it
     * with its
     * audit data, and increment the ledger. If any step fails, everything rolls
     * back
     * (including the device's invoice counter), so the tables can never disagree.
     */
    @Transactional
    public InvoiceResponse submitInvoice(String rawPin, BigDecimal amountBeforeTax,
            Integer deviceId, String sourceIp) {
        String pin = rawPin.trim().toUpperCase();

        Taxpayer taxpayer = taxpayerRepository.findById(pin)
                .orElseThrow(() -> new TaxpayerNotFoundException(pin));

        // Lock the device row so its counter cannot hand out the same number twice.
        // Lock order is always device first, then ledger, so transactions cannot
        // deadlock.
        PosDevice device = deviceRepository.findForUpdateById(deviceId)
                .filter(PosDevice::isActive)
                .orElseThrow(DeviceNotActiveException::new);

        String controlNumber = device.nextControlUnitInvoiceNumber();

        // 1. Calculate: VAT = amount * 0.16, rounded to cents
        BigDecimal net = amountBeforeTax.setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = net.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = net.add(vat);

        // 2. Save the invoice with its audit data
        Invoice invoice = invoiceRepository.save(
                new Invoice(taxpayer, net, vat, total, device, controlNumber, sourceIp));

        // 3. Increment the cumulative liability (row is locked until commit)
        TaxLedger ledger = ledgerRepository.findForUpdateByTaxpayerPin(pin)
                .orElseGet(() -> new TaxLedger(taxpayer));
        ledger.addVat(vat);
        ledgerRepository.save(ledger);

        return InvoiceResponse.from(invoice);
    }
}