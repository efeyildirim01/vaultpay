-- Reviewers work the queue of transfers that fraud scoring flagged as REVIEW.
-- Existing users become customers.
ALTER TABLE users ADD COLUMN role VARCHAR(20) DEFAULT 'CUSTOMER' NOT NULL;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'REVIEWER'));

-- A human decision about one fraud assessment. Kept in its own append-only table instead of
-- updating fraud_assessments, so the original automated decision is never overwritten.
-- UNIQUE (assessment_id): two reviewers deciding at the same time cannot both win.
CREATE TABLE fraud_reviews (
    id            UUID        PRIMARY KEY,
    assessment_id UUID        NOT NULL REFERENCES fraud_assessments (id),
    reviewer_id   UUID        NOT NULL REFERENCES users (id),
    outcome       VARCHAR(20) NOT NULL,
    note          VARCHAR(500),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_fraud_reviews_assessment UNIQUE (assessment_id),
    CONSTRAINT ck_fraud_reviews_outcome CHECK (outcome IN ('CLEARED', 'CONFIRMED_FRAUD'))
);

CREATE INDEX ix_fraud_decision ON fraud_assessments (decision, created_at);
