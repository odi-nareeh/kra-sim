package com.krasim.compliance.exception;

/**
 * Thrown if a device is revoked between key authentication and the invoice
 * being saved.
 */
public class DeviceNotActiveException extends RuntimeException {

    public DeviceNotActiveException() {
        super("This device is not active. Its API key may have been revoked.");
    }
}