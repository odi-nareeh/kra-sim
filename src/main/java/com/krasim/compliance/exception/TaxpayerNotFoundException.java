package com.krasim.compliance.exception;

public class TaxpayerNotFoundException extends RuntimeException {

    public TaxpayerNotFoundException(String pin) {
        super("Taxpayer with PIN " + pin + " is not registered.");
    }
}