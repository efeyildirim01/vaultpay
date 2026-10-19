package dev.efeyildirim.vaultpay.fraud;

import dev.efeyildirim.vaultpay.account.Account;
import dev.efeyildirim.vaultpay.audit.AuditService;
import dev.efeyildirim.vaultpay.ledger.LedgerService;
import dev.efeyildirim.vaultpay.ledger.LedgerService.OutgoingTransfers;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scores a transfer and records the outcome. Called by the payment service while it holds the row lock
 * on the source account, so the history the rules see cannot change underneath them.
 */
@Service
public class FraudService {

    private final LedgerService ledger;
    private final FraudScorer scorer;
    private final FraudAssessmentRepository assessments;
    private final AuditService audit;
    private final Clock clock;

    public FraudService(LedgerService ledger, FraudScorer scorer, FraudAssessmentRepository assessments,
                        AuditService audit, Clock clock) {
        this.ledger = ledger;
        this.scorer = scorer;
        this.assessments = assessments;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FraudScorer.Result assess(Account source, Account target, long amountMinor) {
        Instant now = clock.instant();
        OutgoingTransfers history = ledger.outgoingTransfersSince(source.getId(), now.minus(LargeAmountRule.HISTORY));
        TransferFacts facts = new TransferFacts(
                amountMinor,
                Objects.equals(source.getOwnerId(), target.getOwnerId()),
                ledger.hasTransferredBefore(source.getId(), target.getId()),
                ledger.outgoingTransfersSince(source.getId(), now.minus(VelocityRule.WINDOW)).count(),
                history.count(),
                history.totalMinor(),
                ledger.depositedSince(source.getId(), now.minus(DepositThenTransferRule.WINDOW)));
        return scorer.score(facts);
    }

    /**
     * ALLOW / REVIEW: joins the transfer's transaction, so the assessment exists if and only if
     * the transfer committed, and it points at the journal entry it describes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAccepted(UUID userId, UUID journalEntryId, FraudScorer.Result result) {
        assessments.save(new FraudAssessment(journalEntryId, userId, result, clock.instant()));
    }

    /**
     * BLOCK: the caller throws right after this and its transaction rolls back, which would also erase
     * an assessment written inside it. REQUIRES_NEW suspends the caller's transaction and commits this
     * record (plus its audit row) in a separate one, so the evidence of a blocked attempt survives.
     * It must be called through the Spring proxy (from another bean), or the propagation is ignored.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordBlocked(UUID userId, UUID fromAccountId, UUID toAccountId, long amountMinor,
                              FraudScorer.Result result) {
        FraudAssessment assessment = assessments.save(new FraudAssessment(null, userId, result, clock.instant()));
        audit.record(userId, "TRANSFER_BLOCKED", "FRAUD_ASSESSMENT", assessment.getId(),
                "from=" + fromAccountId + " to=" + toAccountId + " amountMinor=" + amountMinor
                        + " score=" + result.score() + " reasons=" + result.reasons());
        return assessment.getId();
    }
}
