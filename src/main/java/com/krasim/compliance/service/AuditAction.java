package com.krasim.compliance.service;

/**
 * Every kind of event the system records. A fixed list keeps the log consistent
 * and filterable.
 */
public enum AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_BLOCKED,
    LOGOUT,
    TAXPAYER_REGISTERED,
    DEVICE_REGISTERED,
    DEVICE_KEY_ROTATED,
    DEVICE_REVOKED;

    /** Returns the matching action, or null for a missing or unknown value. */
    public static AuditAction parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}