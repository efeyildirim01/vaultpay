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
| Rule-based fraud scoring on transfers (4 explainable rules, 0-100 score, ALLOW / REVIEW / BLOCK) | Built and tested |
| Fraud review queue: reviewer role, four-eyes rule, confirmed fraud freezes the account | Built and tested |
| Rate limiting: login per IP, money movement per user; Redis in production, in-memory locally | Built and tested (Redis store tested in CI only) |
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
fraud     rule-based transfer scoring; pure rules over facts read from the ledger; reviewer queue
ratelimit fixed-window limits on login and money movement (Redis or in-memory counters)
```

### Data model

```
users ──< accounts ──< postings >── journal_entries
                                        │
                 audit_log, fraud_assessments ──< fraud_reviews
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
4. Score fraud risk (see below), still under the same locks. `BLOCK` → `422`, nothing is posted.
5. Write the journal entry, its two postings, the fraud assessment and an audit row, then commit.

### Fraud scoring

Every transfer that passes the banking checks is scored by a small set of rules. Each rule is a pure
function over a `TransferFacts` value (gathered once from the ledger), adds fixed points when it fires,
and leaves a reason code on the stored assessment, so every decision can be explained afterwards.

| Rule | Fires when | Points |
|---|---|---|
| `LARGE_AMOUNT` | ≥ 5× the account's average transfer over 90 days (needs ≥ 3 transfers); with less history ≥ £1,000. Never under £100 | 40 |
| `HIGH_VELOCITY` | the source account already made ≥ 5 transfers in the last 10 minutes | 30 |
| `DEPOSIT_THEN_TRANSFER` | deposits arrived in the last 30 minutes and ≥ half of that leaves to another customer | 25 |
| `NEW_PAYEE` | first transfer from this account to another customer's account | 20 |

Score = sum of points, capped at 100. **0-39 ALLOW**, **40-69 REVIEW** (goes through, flagged), **70+ BLOCK**.
No single rule can block on its own; e.g. "£2,000 deposited, £1,900 sent at once to a new payee" scores 85.

- `ALLOW` / `REVIEW` assessments are saved **in the transfer's transaction** and point at its journal entry,
  so they exist if and only if the transfer committed.
- A `BLOCK` throws, which rolls the transfer's transaction back. The assessment and a `TRANSFER_BLOCKED`
  audit row are therefore written with **`REQUIRES_NEW`**: Spring suspends the outer transaction and commits
  this record in its own one, so the evidence of a blocked attempt survives the rollback. The cost is a
  second database connection for a moment while the account locks are held.
- The client gets `422` with an `assessmentId`, but not the reasons: telling a fraudster which rule fired
  shows them how to stay under it.

### Fraud review

`REVIEW` transfers go through, then land in a queue for users with the `REVIEWER` role
(their JWT carries the `fraud:review` scope; everything under `/api/fraud` requires it, others get `403`).

- A reviewer marks an assessment `CLEARED` or `CONFIRMED_FRAUD`. Confirmed fraud **freezes the account the
  money left from**, under the same row lock transfers use, so no later transfer or deposit gets through.
- **Four-eyes rule:** a reviewer cannot review their own transfer.
- Decisions go to an append-only `fraud_reviews` table instead of overwriting the assessment, so the automated
  decision and the human one are both kept. `UNIQUE (assessment_id)` means two reviewers deciding at the same
  moment cannot both win: the loser gets `409` (tested with two concurrent reviewers).
- The `dev` profile creates `reviewer@vaultpay.local` / `dev-reviewer-password` at startup so the queue can be
  tried locally. There is no endpoint that grants the role.

Limits, honestly: thresholds are hard-coded constants, not tuned on real data; there is no machine learning and
no device/IP signals. Confirmed fraud only freezes the account: money is not clawed back, and there is no unfreeze
flow yet. `BLOCK` decisions are recorded but not queued. A new role reaches a token only at the next login, and a
removed role stays in already-issued tokens until they expire (30 minutes). A retried blocked request is scored
(and recorded) again.

### Rate limiting

| Limit | Key | Default |
|---|---|---|
| `POST /api/auth/login` | client IP | 10 per minute |
| `POST` deposits and transfers | user id from the verified JWT | 30 per minute |

Over the limit → `429 Too Many Requests` with a `Retry-After` header, before the request reaches the controller,
so nothing is posted. The login limit counts wrong passwords too, which is what slows down password guessing.

- **Fixed window:** the counter key contains the window number (`rl:money:<user>:<minute>`), so each window
  starts at zero and old counters expire on their own. Trade-off: up to 2× the limit can get through around a
  window boundary; a sliding window would fix that with more state per key.
- **Redis in production** so every instance shares one count. `INCR` and `PEXPIRE` run in one Lua script, so a
  counter can never be left without an expiry. Locally (`dev`, H2 tests) the counters live in memory, so no
  Redis is needed; CI runs the Redis store against a real Redis container.
- **Fail open:** if Redis is unreachable, requests are allowed and a warning is logged. The limiter is
  protection, not correctness: overdrafts and double posting are still prevented by locks and idempotency.
- The client IP is `getRemoteAddr()`. Behind a load balancer this must be switched to a header set by a
  trusted proxy; `X-Forwarded-For` from the client is not trusted.

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
| `GET` | `/api/fraud/reviews/pending` | reviewers only: unreviewed `REVIEW` assessments with reasons and transfer details |
| `POST` | `/api/fraud/assessments/{id}/review` | reviewers only: `{outcome: CLEARED \| CONFIRMED_FRAUD, note}` |

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
./gradlew test -PtestDb=postgres # same suite on real PostgreSQL 16 + Redis 7 via Testcontainers (needs Docker)
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
- **Fraud rules:** each rule has unit tests at its boundaries; integration tests show a blocked transfer
  moves no money while its assessment survives the rollback, and that a known payee lowers the next score.
- **Fraud review:** confirmed fraud freezes the account and stops the next transfer; one review per assessment,
  even with two reviewers at once; no self-review; customers get `403` on the queue.
- **Rate limiting:** the 4th login from one IP gets `429` even with the right password; a user over the money
  limit gets `429` and no money moves; the in-memory counter loses no increments under 16 threads.
- **End-to-end HTTP flow:** register → login → open two accounts → deposit → transfer → replay, plus
  401/404/422/400 cases and a fraud-blocked transfer (`422` with an assessment id).

## Tech stack

Java 21 · Spring Boot 4.1 (Web MVC, Data JPA, Security, OAuth2 Resource Server, Validation, Actuator) ·
Hibernate 7 · Flyway · PostgreSQL 16 · Redis 7 · H2 · JUnit 5 · AssertJ · Testcontainers · Gradle (Kotlin DSL) · GitHub Actions

## Licence

[MIT](LICENSE)
