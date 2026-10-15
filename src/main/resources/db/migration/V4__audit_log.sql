-- V4: append-only audit log

CREATE TABLE audit_events (
    event_id     BIGSERIAL    PRIMARY KEY,
    occurred_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor        VARCHAR(60)  NOT NULL,   -- officer username, or 'system'
    action       VARCHAR(40)  NOT NULL,   -- e.g. LOGIN_FAILURE, DEVICE_REVOKED
    target       VARCHAR(100),            -- what was acted on (device serial, taxpayer PIN)
    details      VARCHAR(300),
    source_ip    VARCHAR(45)
);

CREATE INDEX idx_audit_occurred ON audit_events (occurred_at DESC);
CREATE INDEX idx_audit_action   ON audit_events (action, occurred_at DESC);

-- Audit rows can be added but never changed or removed
CREATE FUNCTION audit_events_block_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_block_changes();

CREATE TRIGGER trg_audit_no_truncate
    BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_events_block_changes();