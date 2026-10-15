-- V1: baseline schema (taxpayers, invoices, tax_ledger, officers) and demo data

CREATE TABLE taxpayers (
    pin           VARCHAR(11)  PRIMARY KEY
                  CHECK (pin ~ '^[AP][0-9]{9}[A-Z]$'),
    company_name  VARCHAR(150) NOT NULL,
    email         VARCHAR(150) NOT NULL
);

CREATE TABLE invoices (
    invoice_id         SERIAL        PRIMARY KEY,
    taxpayer_pin       VARCHAR(11)   NOT NULL REFERENCES taxpayers (pin),
    amount_before_tax  NUMERIC(15,2) NOT NULL CHECK (amount_before_tax > 0),
    vat_amount         NUMERIC(15,2) NOT NULL CHECK (vat_amount >= 0),
    total_amount       NUMERIC(15,2) NOT NULL CHECK (total_amount > 0),
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_invoices_pin_created ON invoices (taxpayer_pin, created_at DESC);

CREATE TABLE tax_ledger (
    ledger_id      SERIAL        PRIMARY KEY,
    taxpayer_pin   VARCHAR(11)   NOT NULL UNIQUE REFERENCES taxpayers (pin),
    total_vat_due  NUMERIC(15,2) NOT NULL DEFAULT 0 CHECK (total_vat_due >= 0),
    last_updated   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE officers (
    officer_id     SERIAL       PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Demo data
INSERT INTO taxpayers (pin, company_name, email)
VALUES ('A000111222B', 'Mombasa Logistics LTD', 'accounts@mombasalogistics.co.ke');

INSERT INTO invoices (taxpayer_pin, amount_before_tax, vat_amount, total_amount)
VALUES ('A000111222B', 50000.00, 8000.00, 58000.00);

INSERT INTO tax_ledger (taxpayer_pin, total_vat_due)
VALUES ('A000111222B', 8000.00);