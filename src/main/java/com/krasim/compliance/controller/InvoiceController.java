package com.krasim.compliance.controller;

import com.krasim.compliance.dto.InvoiceRequest;
import com.krasim.compliance.dto.InvoiceResponse;
import com.krasim.compliance.dto.PosDevicePrincipal;
import com.krasim.compliance.service.InvoiceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/submit")
    public ResponseEntity<InvoiceResponse> submit(@Valid @RequestBody InvoiceRequest request,
            @AuthenticationPrincipal PosDevicePrincipal device,
            HttpServletRequest http) {
        InvoiceResponse response = invoiceService.submitInvoice(
                request.taxpayerPin(), request.amountBeforeTax(),
                device.deviceId(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}