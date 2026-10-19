-- Append-only audit trail. Rows are written in the same database transaction as
-- the change they describe, so an audit row exists if and only if the change committed.
CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY,
    actor_id    UUID,
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   UUID,
    details     VARCHAR(1000),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_audit_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_actor  ON audit_log (actor_id);

-- Result of rule-based fraud scoring for a transfer.
-- Schema only for now: the scoring rules are planned for the next slice.
CREATE TABLE fraud_assessments (
    id               UUID         PRIMARY KEY,
    journal_entry_id UUID         REFERENCES journal_entries (id),
    user_id          UUID         NOT NULL REFERENCES users (id),
    score            INTEGER      NOT NULL,
    decision         VARCHAR(10)  NOT NULL,
    reasons          VARCHAR(500),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_fraud_score    CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_fraud_decision CHECK (decision IN ('ALLOW', 'REVIEW', 'BLOCK'))
);

CREATE INDEX ix_fraud_user ON fraud_assessments (user_id);
