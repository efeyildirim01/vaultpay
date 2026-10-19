package dev.efeyildirim.vaultpay.payment;

import dev.efeyildirim.vaultpay.account.Account;
import dev.efeyildirim.vaultpay.account.AccountService;
import dev.efeyildirim.vaultpay.audit.AuditService;
import dev.efeyildirim.vaultpay.common.BusinessRuleException;
import dev.efeyildirim.vaultpay.common.Hashing;
import dev.efeyildirim.vaultpay.common.NotFoundException;
import dev.efeyildirim.vaultpay.fraud.FraudDecision;
import dev.efeyildirim.vaultpay.fraud.FraudScorer;
import dev.efeyildirim.vaultpay.fraud.FraudService;
import dev.efeyildirim.vaultpay.fraud.TransferBlockedException;
import dev.efeyildirim.vaultpay.ledger.InsufficientFundsException;
import dev.efeyildirim.vaultpay.ledger.JournalEntry;
import dev.efeyildirim.vaultpay.ledger.JournalEntryType;
import dev.efeyildirim.vaultpay.ledger.LedgerService;
import dev.efeyildirim.vaultpay.ledger.LedgerService.Leg;
import dev.efeyildirim.vaultpay.ledger.Posting;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deposits and transfers. Each call is one ACID transaction:
 * <ol>
 *   <li>lock the affected customer accounts (SELECT ... FOR UPDATE, in id order),</li>
 *   <li>check the idempotency key; a retry returns the stored result,</li>
 *   <li>validate ownership, status, currency and balance,</li>
 *   <li>transfers only: score fraud risk; BLOCK rejects the transfer (see {@link FraudService#recordBlocked}),</li>
 *   <li>write one balanced journal entry plus an audit row (and, for transfers, the fraud assessment).</li>
 * </ol>
 * The idempotency check runs after locking on purpose: a concurrent retry with the same key
 * waits for the first request to commit and then sees its journal entry, instead of racing it.
 */
@Service
public class PaymentService {

    private final AccountService accounts;
    private final LedgerService ledger;
    private final AuditService audit;
    private final FraudService fraud;

    public PaymentService(AccountService accounts, LedgerService ledger, AuditService audit, FraudService fraud) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.audit = audit;
        this.fraud = fraud;
    }

    /** Simulated deposit: money arrives from the external funding system account. No real money moves. */
    @Transactional
    public MovementResult deposit(UUID userId, String idempotencyKey, UUID accountId, long amountMinor,
                                  String reference) {
        requirePositive(amountMinor);
        String requestHash = Hashing.sha256Hex("DEPOSIT|" + accountId + "|" + amountMinor + "|" + reference);

        // The funding account may go negative, so it is not locked (that would make it a global bottleneck).
        Account target = accounts.lockInOrder(accountId).get(accountId);

        Optional<MovementResult> replay = replayIfSeen(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        requireOwnedAndActive(target, userId);
        JournalEntry entry = post(JournalEntryType.DEPOSIT, userId, idempotencyKey, requestHash, reference,
                target.getCurrency(), List.of(
                        Leg.debit(Account.EXTERNAL_FUNDING_ID, amountMinor),
                        Leg.credit(accountId, amountMinor)));
        audit.record(userId, "DEPOSIT_POSTED", "JOURNAL_ENTRY", entry.getId(),
                "account=" + accountId + " amountMinor=" + amountMinor);
        return toResult(entry, false);
    }

    @Transactional
    public MovementResult transfer(UUID userId, String idempotencyKey, UUID fromAccountId, UUID toAccountId,
                                   long amountMinor, String reference) {
        requirePositive(amountMinor);
        if (fromAccountId.equals(toAccountId)) {
            throw new BusinessRuleException("Cannot transfer to the same account");
        }
        String requestHash = Hashing.sha256Hex(
                "TRANSFER|" + fromAccountId + "|" + toAccountId + "|" + amountMinor + "|" + reference);

        Map<UUID, Account> locked = accounts.lockInOrder(fromAccountId, toAccountId);
        Account source = locked.get(fromAccountId);
        Account target = locked.get(toAccountId);

        Optional<MovementResult> replay = replayIfSeen(userId, idempotencyKey, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }

        requireOwnedAndActive(source, userId);
        if (!target.isActive()) {
            throw new BusinessRuleException("Destination account is not active");
        }
        if (!source.getCurrency().equals(target.getCurrency())) {
            throw new BusinessRuleException("Currency mismatch");
        }
        // Safe without lost updates: we hold the row lock on the source account, so no other
        // transaction can post against it until we commit.
        if (!source.mayGoNegative() && ledger.balanceOf(fromAccountId) < amountMinor) {
            throw new InsufficientFundsException();
        }

        // Only transfers that passed every banking check are scored, still under the same locks.
        FraudScorer.Result risk = fraud.assess(source, target, amountMinor);
        if (risk.decision() == FraudDecision.BLOCK) {
            // Recorded in its own transaction: throwing below rolls this one back.
            UUID assessmentId = fraud.recordBlocked(userId, fromAccountId, toAccountId, amountMinor, risk);
            throw new TransferBlockedException(assessmentId);
        }

        JournalEntry entry = post(JournalEntryType.TRANSFER, userId, idempotencyKey, requestHash, reference,
                source.getCurrency(), List.of(
                        Leg.debit(fromAccountId, amountMinor),
                        Leg.credit(toAccountId, amountMinor)));
        fraud.recordAccepted(userId, entry.getId(), risk);
        audit.record(userId, "TRANSFER_POSTED", "JOURNAL_ENTRY", entry.getId(),
                "from=" + fromAccountId + " to=" + toAccountId + " amountMinor=" + amountMinor
                        + " fraudScore=" + risk.score() + " fraudDecision=" + risk.decision());
        return toResult(entry, false);
    }

    private JournalEntry post(JournalEntryType type, UUID userId, String key, String hash, String reference,
                              String currency, List<Leg> legs) {
        try {
            return ledger.post(type, userId, key, hash, reference, currency, legs);
        } catch (DataIntegrityViolationException e) {
            // Same key used concurrently for requests on different accounts (so the locks did not
            // serialise them): the UNIQUE (initiated_by, idempotency_key) constraint rejects the loser.
            throw new IdempotencyConflictException();
        }
    }

    private Optional<MovementResult> replayIfSeen(UUID userId, String key, String requestHash) {
        return ledger.findByIdempotencyKey(userId, key).map(existing -> {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException();
            }
            return toResult(existing, true);
        });
    }

    private MovementResult toResult(JournalEntry entry, boolean replayed) {
        List<Posting> legs = ledger.postingsOf(entry.getId());
        Posting debit = legs.stream().filter(p -> p.getAmountMinor() < 0).findFirst().orElseThrow();
        Posting credit = legs.stream().filter(p -> p.getAmountMinor() > 0).findFirst().orElseThrow();
        return new MovementResult(entry.getId(), entry.getType(), debit.getAccountId(), credit.getAccountId(),
                credit.getAmountMinor(), credit.getCurrency(), entry.getReference(), entry.getCreatedAt(), replayed);
    }

    private static void requireOwnedAndActive(Account account, UUID userId) {
        if (!account.isOwnedBy(userId)) {
            throw new NotFoundException("Account not found");
        }
        if (!account.isActive()) {
            throw new BusinessRuleException("Account is not active");
        }
    }

    private static void requirePositive(long amountMinor) {
        if (amountMinor <= 0) {
            throw new BusinessRuleException("Amount must be positive");
        }
    }
}
