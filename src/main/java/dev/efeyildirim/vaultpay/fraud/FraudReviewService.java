package dev.efeyildirim.vaultpay.fraud;

import dev.efeyildirim.vaultpay.account.AccountService;
import dev.efeyildirim.vaultpay.audit.AuditService;
import dev.efeyildirim.vaultpay.common.BusinessRuleException;
import dev.efeyildirim.vaultpay.common.ConflictException;
import dev.efeyildirim.vaultpay.common.NotFoundException;
import dev.efeyildirim.vaultpay.ledger.LedgerService;
import dev.efeyildirim.vaultpay.ledger.Posting;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The human side of fraud screening: transfers scored REVIEW went through, and a reviewer now decides
 * whether they were fraud. Confirmed fraud freezes the account the money left from.
 */
@Service
public class FraudReviewService {

    static final int QUEUE_PAGE_SIZE = 50;

    private final FraudAssessmentRepository assessments;
    private final FraudReviewRepository reviews;
    private final LedgerService ledger;
    private final AccountService accounts;
    private final AuditService audit;
    private final Clock clock;

    public FraudReviewService(FraudAssessmentRepository assessments, FraudReviewRepository reviews,
                              LedgerService ledger, AccountService accounts, AuditService audit, Clock clock) {
        this.assessments = assessments;
        this.reviews = reviews;
        this.ledger = ledger;
        this.accounts = accounts;
        this.audit = audit;
        this.clock = clock;
    }

    /** One item in the review queue. Unlike the customer-facing 422, reviewers do see the reasons. */
    public record PendingReview(UUID assessmentId, UUID userId, int score, String reasons, UUID journalEntryId,
                                UUID fromAccountId, UUID toAccountId, long amountMinor, Instant flaggedAt) {
    }

    public record ReviewResult(UUID reviewId, UUID assessmentId, ReviewOutcome outcome, boolean accountFrozen) {
    }

    @Transactional(readOnly = true)
    public List<PendingReview> pending() {
        return assessments.findUnreviewed(FraudDecision.REVIEW, PageRequest.of(0, QUEUE_PAGE_SIZE)).stream()
                .map(a -> {
                    Movement m = movementOf(a.getJournalEntryId());
                    return new PendingReview(a.getId(), a.getUserId(), a.getScore(), a.getReasons(),
                            a.getJournalEntryId(), m.from(), m.to(), m.amountMinor(), a.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public ReviewResult review(UUID reviewerId, UUID assessmentId, ReviewOutcome outcome, String note) {
        FraudAssessment assessment = assessments.findById(assessmentId)
                .orElseThrow(() -> new NotFoundException("Assessment not found"));
        if (assessment.getDecision() != FraudDecision.REVIEW) {
            throw new BusinessRuleException("Only assessments with decision REVIEW can be reviewed");
        }
        // Four-eyes principle: whoever made the transfer cannot be the one who clears it.
        if (assessment.getUserId().equals(reviewerId)) {
            throw new BusinessRuleException("Reviewers cannot review their own transfers");
        }
        if (reviews.existsByAssessmentId(assessmentId)) {
            throw new ConflictException("Assessment was already reviewed");
        }

        FraudReview review;
        try {
            review = reviews.saveAndFlush(new FraudReview(assessmentId, reviewerId, outcome, note, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Two reviewers raced past the exists check: UNIQUE (assessment_id) lets only one of them win.
            throw new ConflictException("Assessment was already reviewed");
        }

        boolean frozen = false;
        if (outcome == ReviewOutcome.CONFIRMED_FRAUD) {
            accounts.freeze(reviewerId, movementOf(assessment.getJournalEntryId()).from(),
                    "confirmed fraud, review=" + review.getId());
            frozen = true;
        }
        audit.record(reviewerId, "FRAUD_REVIEWED", "FRAUD_ASSESSMENT", assessmentId, "outcome=" + outcome);
        return new ReviewResult(review.getId(), assessmentId, outcome, frozen);
    }

    private record Movement(UUID from, UUID to, long amountMinor) {
    }

    /** A REVIEW assessment always belongs to a posted transfer: one debit leg and one credit leg. */
    private Movement movementOf(UUID journalEntryId) {
        List<Posting> legs = ledger.postingsOf(journalEntryId);
        Posting debit = legs.stream().filter(p -> p.getAmountMinor() < 0).findFirst().orElseThrow();
        Posting credit = legs.stream().filter(p -> p.getAmountMinor() > 0).findFirst().orElseThrow();
        return new Movement(debit.getAccountId(), credit.getAccountId(), credit.getAmountMinor());
    }
}
