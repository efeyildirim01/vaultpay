package dev.efeyildirim.vaultpay.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.efeyildirim.vaultpay.IntegrationTest;
import dev.efeyildirim.vaultpay.common.BusinessRuleException;
import dev.efeyildirim.vaultpay.common.ConflictException;
import dev.efeyildirim.vaultpay.fraud.FraudReviewService.ReviewResult;
import dev.efeyildirim.vaultpay.user.User;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FraudReviewTest extends IntegrationTest {

    @Autowired
    FraudReviewService reviewService;
    @Autowired
    FraudAssessmentRepository assessments;

    /** A customer with £100 who sends £60 to a new payee right after the deposit: scored REVIEW (45). */
    private record Flagged(User customer, UUID account, UUID payeeAccount, UUID assessmentId) {
    }

    private Flagged flaggedTransfer() {
        User alice = newUser();
        User bob = newUser();
        UUID aliceAccount = openAccount(alice);
        UUID bobAccount = openAccount(bob);
        fund(alice, aliceAccount, 10_000);
        paymentService.transfer(alice.getId(), newKey(), aliceAccount, bobAccount, 6_000, null);
        FraudAssessment assessment = assessments.findByUserId(alice.getId()).getFirst();
        assertThat(assessment.getDecision()).isEqualTo(FraudDecision.REVIEW);
        return new Flagged(alice, aliceAccount, bobAccount, assessment.getId());
    }

    private User newReviewer() {
        User reviewer = newUser();
        userService.promoteToReviewer(null, reviewer.getId());
        return reviewer;
    }

    @Test
    void pendingQueueShowsTheFlaggedTransferUntilItIsReviewed() {
        Flagged flagged = flaggedTransfer();

        assertThat(reviewService.pending())
                .filteredOn(p -> p.assessmentId().equals(flagged.assessmentId()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.fromAccountId()).isEqualTo(flagged.account());
                    assertThat(p.toAccountId()).isEqualTo(flagged.payeeAccount());
                    assertThat(p.amountMinor()).isEqualTo(6_000);
                    assertThat(p.reasons()).isEqualTo("DEPOSIT_THEN_TRANSFER:25,NEW_PAYEE:20");
                });

        reviewService.review(newReviewer().getId(), flagged.assessmentId(), ReviewOutcome.CLEARED, "known landlord");

        assertThat(reviewService.pending()).noneMatch(p -> p.assessmentId().equals(flagged.assessmentId()));
    }

    @Test
    void confirmedFraudFreezesTheSourceAccount() {
        Flagged flagged = flaggedTransfer();

        ReviewResult result = reviewService.review(newReviewer().getId(), flagged.assessmentId(),
                ReviewOutcome.CONFIRMED_FRAUD, "customer reported account takeover");

        assertThat(result.accountFrozen()).isTrue();
        assertThat(accountService.get(flagged.customer().getId(), flagged.account()).status().name())
                .isEqualTo("FROZEN");
        assertThatThrownBy(() -> paymentService.transfer(flagged.customer().getId(), newKey(), flagged.account(),
                flagged.payeeAccount(), 100, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Account is not active");
        assertThat(balance(flagged.account())).isEqualTo(4_000);
    }

    @Test
    void clearedLeavesTheAccountUsable() {
        Flagged flagged = flaggedTransfer();

        ReviewResult result = reviewService.review(newReviewer().getId(), flagged.assessmentId(),
                ReviewOutcome.CLEARED, null);

        assertThat(result.accountFrozen()).isFalse();
        paymentService.transfer(flagged.customer().getId(), newKey(), flagged.account(), flagged.payeeAccount(),
                100, null);
        assertThat(balance(flagged.payeeAccount())).isEqualTo(6_100);
    }

    @Test
    void anAssessmentCanOnlyBeReviewedOnce() {
        Flagged flagged = flaggedTransfer();
        reviewService.review(newReviewer().getId(), flagged.assessmentId(), ReviewOutcome.CLEARED, null);

        assertThatThrownBy(() -> reviewService.review(newReviewer().getId(), flagged.assessmentId(),
                ReviewOutcome.CONFIRMED_FRAUD, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void twoReviewersAtTheSameTimeCannotBothWin() throws Exception {
        Flagged flagged = flaggedTransfer();
        List<User> reviewers = List.of(newReviewer(), newReviewer());

        List<Future<ReviewResult>> results = runConcurrently(2, i -> () -> reviewService.review(
                reviewers.get(i).getId(), flagged.assessmentId(),
                i == 0 ? ReviewOutcome.CLEARED : ReviewOutcome.CONFIRMED_FRAUD, null));

        int succeeded = 0;
        int conflicts = 0;
        for (Future<ReviewResult> result : results) {
            try {
                result.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(ConflictException.class);
                conflicts++;
            }
        }
        assertThat(succeeded).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);
    }

    @Test
    void reviewersCannotReviewTheirOwnTransfers() {
        Flagged flagged = flaggedTransfer();
        userService.promoteToReviewer(null, flagged.customer().getId());

        assertThatThrownBy(() -> reviewService.review(flagged.customer().getId(), flagged.assessmentId(),
                ReviewOutcome.CLEARED, "it was me"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Reviewers cannot review their own transfers");
    }

    @Test
    void onlyReviewDecisionsCanBeReviewed() {
        User alice = newUser();
        UUID current = openAccount(alice);
        UUID savings = openAccount(alice);
        fund(alice, current, 1_000);
        paymentService.transfer(alice.getId(), newKey(), current, savings, 100, null);
        UUID allowed = assessments.findByUserId(alice.getId()).getFirst().getId();

        assertThatThrownBy(() -> reviewService.review(newReviewer().getId(), allowed, ReviewOutcome.CLEARED, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
