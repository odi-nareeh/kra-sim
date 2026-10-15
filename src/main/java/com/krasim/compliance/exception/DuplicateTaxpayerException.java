package com.krasim.compliance.exception;

public class DuplicateTaxpayerException extends RuntimeException {

    public DuplicateTaxpayerException(String pin) {
        super("A taxpayer with PIN " + pin + " is already registered.");
    }
}