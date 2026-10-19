package dev.efeyildirim.vaultpay.fraud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

import dev.efeyildirim.vaultpay.IntegrationTest;
import dev.efeyildirim.vaultpay.audit.AuditLogRepository;
import dev.efeyildirim.vaultpay.payment.MovementResult;
import dev.efeyildirim.vaultpay.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Fraud scoring wired into real transfers, against the real database. */
class FraudScreeningTest extends IntegrationTest {

    @Autowired
    FraudAssessmentRepository assessments;
    @Autowired
    AuditLogRepository auditLogs;

    @Test
    void blockedTransferMovesNothingButItsAssessmentSurvivesTheRollback() {
        User alice = newUser();
        User bob = newUser();
        UUID aliceAccount = openAccount(alice);
        UUID bobAccount = openAccount(bob);
        fund(alice, aliceAccount, 200_000); // £2,000 arrives...
        String key = newKey();

        // ...and £1,900 leaves at once to someone never paid before: large + new payee + pass-through.
        TransferBlockedException blocked = catchThrowableOfType(TransferBlockedException.class,
                () -> paymentService.transfer(alice.getId(), key, aliceAccount, bobAccount, 190_000, "urgent"));

        assertThat(blocked).isNotNull();
        assertThat(balance(aliceAccount)).isEqualTo(200_000);
        assertThat(balance(bobAccount)).isZero();
        assertThat(ledgerService.findByIdempotencyKey(alice.getId(), key)).isEmpty();

        // Written with REQUIRES_NEW, so it committed although the transfer's transaction rolled back.
        assertThat(assessments.findByUserId(alice.getId())).singleElement().satisfies(a -> {
            assertThat(a.getId()).isEqualTo(blocked.getAssessmentId());
            assertThat(a.getDecision()).isEqualTo(FraudDecision.BLOCK);
            assertThat(a.getScore()).isEqualTo(85);
            assertThat(a.getReasons()).isEqualTo("LARGE_AMOUNT:40,DEPOSIT_THEN_TRANSFER:25,NEW_PAYEE:20");
            assertThat(a.getJournalEntryId()).isNull();
        });
        assertThat(auditLogs.findByEntityTypeAndEntityId("FRAUD_ASSESSMENT", blocked.getAssessmentId()))
                .singleElement()
                .satisfies(log -> assertThat(log.getAction()).isEqualTo("TRANSFER_BLOCKED"));
    }

    @Test
    void reviewedTransferGoesThroughAndKnownPayeeLowersTheNextScore() {
        User alice = newUser();
        User bob = newUser();
        UUID aliceAccount = openAccount(alice);
        UUID bobAccount = openAccount(bob);
        fund(alice, aliceAccount, 10_000);

        MovementResult first = paymentService.transfer(alice.getId(), newKey(), aliceAccount, bobAccount, 6_000, null);
        MovementResult second = paymentService.transfer(alice.getId(), newKey(), aliceAccount, bobAccount, 100, null);

        assertThat(balance(bobAccount)).isEqualTo(6_100);
        assertThat(assessments.findByUserId(alice.getId()))
                .extracting(FraudAssessment::getJournalEntryId, FraudAssessment::getDecision, FraudAssessment::getScore)
                .containsExactlyInAnyOrder(
                        tuple(first.journalEntryId(), FraudDecision.REVIEW, 45),
                        tuple(second.journalEntryId(), FraudDecision.ALLOW, 0));
    }

    @Test
    void velocityIsCountedFromTheLedger() {
        User alice = newUser();
        UUID current = openAccount(alice);
        UUID savings = openAccount(alice);
        fund(alice, current, 10_000);

        for (int i = 0; i < 6; i++) {
            paymentService.transfer(alice.getId(), newKey(), current, savings, 100, "t" + i);
        }

        // Transfers 1-5 are fine; the 6th sees 5 earlier ones in the window. Own accounts: nothing else fires.
        assertThat(assessments.findByUserId(alice.getId()))
                .extracting(FraudAssessment::getScore)
                .containsExactlyInAnyOrder(0, 0, 0, 0, 0, VelocityRule.POINTS);
    }
}
