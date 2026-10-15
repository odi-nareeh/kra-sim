-- V3: audit trail on invoices (device, control unit invoice number, source IP)

ALTER TABLE invoices
    ADD COLUMN control_unit_invoice_no VARCHAR(40),
    ADD COLUMN device_id INTEGER REFERENCES pos_devices (device_id),
    ADD COLUMN source_ip VARCHAR(45);

-- Invoices created before this migration have no device, so give them a legacy number
UPDATE invoices
SET control_unit_invoice_no = 'LEGACY/' || LPAD(invoice_id::TEXT, 6, '0')
WHERE control_unit_invoice_no IS NULL;

ALTER TABLE invoices
    ALTER COLUMN control_unit_invoice_no SET NOT NULL,
    ADD CONSTRAINT uq_invoices_control_no UNIQUE (control_unit_invoice_no);

CREATE INDEX idx_invoices_device ON invoices (device_id);