package com.krasim.compliance.dto;

/** The authenticated POS device for the current API request. */
public record PosDevicePrincipal(Integer deviceId, String serial, String name) {
}