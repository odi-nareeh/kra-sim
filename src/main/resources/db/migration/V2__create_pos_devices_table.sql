CREATE TABLE pos_devices (
    device_id SERIAL PRIMARY KEY,
    device_name VARCHAR(100) NOT NULL,
    key_prefix VARCHAR(8) NOT NULL UNIQUE,
    key_hash VARCHAR(64) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    last_invoice_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP
);
