package dev.efeyildirim.vaultpay.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FraudScorerTest {

    private final FraudScorer scorer = new FraudScorer(List.of(
            new LargeAmountRule(), new VelocityRule(), new NewPayeeRule(), new DepositThenTransferRule()));

    @Test
    void harmlessTransferIsAllowedWithNoReasons() {
        FraudScorer.Result result = scorer.score(Facts.harmless().build());

        assertThat(result.score()).isZero();
        assertThat(result.decision()).isEqualTo(FraudDecision.ALLOW);
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void newPayeePlusFreshDepositIsReviewed() {
        FraudScorer.Result result = scorer.score(Facts.harmless().newPayee().recentDeposits(2_000).amount(1_500).build());

        assertThat(result.score()).isEqualTo(45);
        assertThat(result.decision()).isEqualTo(FraudDecision.REVIEW);
        assertThat(result.reasons()).isEqualTo("DEPOSIT_THEN_TRANSFER:25,NEW_PAYEE:20");
    }

    @Test
    void muleLikeTransferIsBlocked() {
        FraudScorer.Result result = scorer.score(
                Facts.harmless().newPayee().recentDeposits(200_000).amount(190_000).build());

        assertThat(result.score()).isEqualTo(85);
        assertThat(result.decision()).isEqualTo(FraudDecision.BLOCK);
        assertThat(result.reasons()).isEqualTo("LARGE_AMOUNT:40,DEPOSIT_THEN_TRANSFER:25,NEW_PAYEE:20");
    }

    @Test
    void scoreIsCappedAtOneHundred() {
        FraudScorer.Result result = scorer.score(
                Facts.harmless().newPayee().recentDeposits(200_000).amount(190_000).recentTransfers(10).build());

        assertThat(result.hits()).hasSize(4); // 115 points in total
        assertThat(result.score()).isEqualTo(100);
    }

    @Test
    void noSingleRuleCanBlockOnItsOwn() {
        TransferFacts everything = Facts.harmless().newPayee().recentDeposits(200_000).amount(190_000)
                .recentTransfers(10).build();
        for (FraudRule rule : List.of(new LargeAmountRule(), new VelocityRule(), new NewPayeeRule(),
                new DepositThenTransferRule())) {
            Optional<FraudRule.Hit> hit = rule.evaluate(everything);
            assertThat(hit).isPresent();
            assertThat(FraudScorer.decide(hit.get().points())).isNotEqualTo(FraudDecision.BLOCK);
        }
    }

    @Test
    void thresholds() {
        assertThat(FraudScorer.decide(39)).isEqualTo(FraudDecision.ALLOW);
        assertThat(FraudScorer.decide(40)).isEqualTo(FraudDecision.REVIEW);
        assertThat(FraudScorer.decide(69)).isEqualTo(FraudDecision.REVIEW);
        assertThat(FraudScorer.decide(70)).isEqualTo(FraudDecision.BLOCK);
    }
}
