# VaultPay

**A secure digital banking simulator in Java 21 + Spring Boot 4.**
VaultPay is a portfolio project that explores the backend problems real payment systems have to solve:
a double-entry ledger, ACID money movement, concurrency safety, idempotent APIs and an audit trail.

> **Simulator only.** No real money moves and nothing connects to a bank or card network.
> Deposits are simulated: they are funded from an internal "External funding (simulated)" system account.

## Status

| Area | Status |
|---|---|
| Registration and login (BCrypt, stateless JWT, Spring Security resource server) | Built |
| Customer accounts (GBP only for now) | Built |
| Double-entry ledger (journal entries + postings, balances derived from postings) | Built |
| Simulated deposits and account-to-account transfers in one ACID transaction | Built |
| Concurrency safety: pessimistic row locks taken in a fixed order | Built and tested |
| Idempotency keys on every money-moving endpoint | Built and tested |
| Audit log written atomically with each change | Built |
| Flyway migrations, PostgreSQL in production/CI, H2 for local dev | Built |
| Rule-based fraud scoring | Planned (schema exists, rules not yet implemented) |
| Redis rate limiting | Planned |
| Web dashboard (React / Next.js) | Planned |

## Architecture

A modular monolith: one deployable, with packages that only talk to each other through services.

```
auth      Spring Security config, JWT issuing, login/register endpoints
user      users and password hashing
account   accounts, row locking (SELECT ... FOR UPDATE in id order)
ledger    journal entries + postings; the only code allowed to write money movements
payment   deposits and transfers, idempotency handling
audit     append-only audit log, joined to the caller's transaction
```

### Data model

```
users ──< accounts ──< postings >── journal_entries
                                        │
                 audit_log, fraud_assessments (planned)
```

- **`accounts` has no balance column.** A balance is always `SUM(postings.amount_minor)` for that account,
  so the stored history and the balance can never disagree.
- **Every journal entry balances to zero.** A transfer of £25 writes two postings: `-2500` on the source and
  `+2500` on the destination. `LedgerService` rejects anything else, and every integration test asserts
  afterwards that no unbalanced journal entry exists.
- **Money is `BIGINT` minor units (pence).** No floating point anywhere.
- **`(initiated_by, idempotency_key)` is UNIQUE** on `journal_entries`, so the database itself guarantees
  that one key can post at most once.

Migrations live in [`src/main/resources/db/migration`](src/main/resources/db/migration).

### How a transfer works

All steps run in one database transaction (`@Transactional`):

1. Lock both account rows with `SELECT ... FOR UPDATE`, **always in ascending id order**.
   Two opposing transfers (A→B and B→A) therefore never wait on each other in a cycle, so they cannot deadlock.
2. Look up the idempotency key. Same key + same request hash → return the stored result (`200`,
   `Idempotent-Replayed: true`). Same key + different request → `409 Conflict`.
   This check runs *after* locking, so a concurrent retry waits for the first attempt to commit and then
   sees its result instead of racing it.
3. Check ownership, account status, currency and balance. The row lock means no other transaction can
   post against the source account between this check and the write: no lost updates, no double spend.
4. Write the journal entry, its two postings and an audit row, then commit.

## API

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/auth/register` | `{email, password, fullName}` |
| `POST` | `/api/auth/login` | returns `{accessToken, tokenType, expiresInSeconds}` |
| `POST` | `/api/accounts` | `{name, currency: "GBP"}` |
| `GET` | `/api/accounts` | your accounts with balances |
| `GET` | `/api/accounts/{id}` | one account with its balance |
| `POST` | `/api/accounts/{id}/deposits` | simulated deposit, needs `Idempotency-Key` |
| `POST` | `/api/transfers` | `{fromAccountId, toAccountId, amountMinor, reference}`, needs `Idempotency-Key` |

Errors use RFC 9457 problem details (`application/problem+json`). Accounts belonging to other users
return `404`, so account ids cannot be probed.

## Running locally

Requirements: JDK 21. No database or Docker needed: the `dev` profile uses in-memory H2 in PostgreSQL mode.

```bash
./gradlew bootRun
```

On Windows use `gradlew.bat bootRun`.

## Tests

```bash
./gradlew test                   # H2, no Docker needed
./gradlew test -PtestDb=postgres # same suite on real PostgreSQL 16 via Testcontainers (needs Docker)
```

CI runs both variants on every push ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

What the tests prove:

- **No overdraft under concurrency:** 50 simultaneous £5 transfers from a £100 account. Exactly 20 succeed,
  30 fail with insufficient funds, and the balance ends at exactly £0.
- **No deadlocks:** 40 simultaneous transfers in opposite directions between two accounts all complete.
- **No lost updates:** 25 concurrent deposits are all counted.
- **Idempotent retries:** a sequential retry and a burst of 10 concurrent requests with the same key move
  money exactly once. A reused key with a different body is rejected.
- **Ledger invariant:** after every test, every journal entry sums to zero and so does the whole ledger.
- **End-to-end HTTP flow:** register → login → open two accounts → deposit → transfer → replay, plus
  401/404/422/400 cases.

## Tech stack

Java 21 · Spring Boot 4.1 (Web MVC, Data JPA, Security, OAuth2 Resource Server, Validation, Actuator) ·
Hibernate 7 · Flyway · PostgreSQL 16 · H2 · JUnit 5 · AssertJ · Testcontainers · Gradle (Kotlin DSL) · GitHub Actions

## Licence

[MIT](LICENSE)
