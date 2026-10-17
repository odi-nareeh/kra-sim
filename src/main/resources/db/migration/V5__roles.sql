-- V5: staff roles. Existing accounts become OFFICERs.
ALTER TABLE officers
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'OFFICER'
    CHECK (role IN ('OFFICER', 'AUDITOR'));