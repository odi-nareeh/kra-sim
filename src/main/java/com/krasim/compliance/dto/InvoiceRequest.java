package com.krasim.compliance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * JSON body sent by the POS: {"taxpayerPin": "A112233445Z", "amountBeforeTax":
 * 10000}
 */
public record InvoiceRequest(

        @NotBlank(message = "taxpayerPin is required.") @Pattern(regexp = "^[AP]\\d{9}[A-Z]$", message = "taxpayerPin must look like A012345678Z (letter, 9 digits, letter).") String taxpayerPin,

        @NotNull(message = "amountBeforeTax is required.") @DecimalMin(value = "0.01", message = "amountBeforeTax must be greater than zero.") BigDecimal amountBeforeTax) {
}