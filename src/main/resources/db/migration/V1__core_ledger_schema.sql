-- VaultPay core schema: users, accounts and a double-entry ledger.
-- Written in portable SQL so the same migrations run on PostgreSQL (prod, CI)
-- and on H2 in PostgreSQL mode (local dev and tests).

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- An account holds no balance column: its balance is always SUM(postings.amount_minor).
-- SYSTEM accounts (e.g. external funding) have no owner and may go negative.
CREATE TABLE accounts (
    id         UUID         PRIMARY KEY,
    owner_id   UUID         REFERENCES users (id),
    type       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    currency   VARCHAR(3)   NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_accounts_type   CHECK (type IN ('CUSTOMER', 'SYSTEM')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_accounts_owner  CHECK ((type = 'CUSTOMER' AND owner_id IS NOT NULL)
                                      OR (type = 'SYSTEM' AND owner_id IS NULL))
);

CREATE INDEX ix_accounts_owner ON accounts (owner_id);

-- One journal entry per business event (deposit, transfer).
-- (initiated_by, idempotency_key) is unique, so a retried request can never post twice.
CREATE TABLE journal_entries (
    id              UUID         PRIMARY KEY,
    type            VARCHAR(20)  NOT NULL,
    initiated_by    UUID         NOT NULL REFERENCES users (id),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    reference       VARCHAR(140),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_journal_type CHECK (type IN ('DEPOSIT', 'TRANSFER')),
    CONSTRAINT uq_journal_idempotency UNIQUE (initiated_by, idempotency_key)
);

-- Postings are append-only. Debits are negative, credits positive, and the
-- postings of one journal entry always sum to zero (enforced by the ledger service
-- and verified by tests). Amounts are integer minor units (pence), never floats.
CREATE TABLE postings (
    id               UUID       PRIMARY KEY,
    journal_entry_id UUID       NOT NULL REFERENCES journal_entries (id),
    account_id       UUID       NOT NULL REFERENCES accounts (id),
    amount_minor     BIGINT     NOT NULL,
    currency         VARCHAR(3) NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_postings_non_zero CHECK (amount_minor <> 0)
);

CREATE INDEX ix_postings_account ON postings (account_id);
CREATE INDEX ix_postings_journal ON postings (journal_entry_id);

-- The simulated outside world: deposits are funded from this account.
INSERT INTO accounts (id, owner_id, type, name, currency, status, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', NULL, 'SYSTEM', 'External funding (simulated)', 'GBP', 'ACTIVE',
        TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00:00');
