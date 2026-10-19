-- Admins see the back-office panel (overview + audit log) and can also work the fraud review queue.
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'REVIEWER', 'ADMIN'));

-- Transaction history reads one account's postings newest first; the audit log is read newest first.
CREATE INDEX ix_postings_account_created ON postings (account_id, created_at);
CREATE INDEX ix_audit_created ON audit_log (created_at);
