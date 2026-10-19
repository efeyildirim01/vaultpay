package dev.efeyildirim.vaultpay.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LargeAmountRuleTest {

    private final LargeAmountRule rule = new LargeAmountRule();

    @Test
    void withHistoryFiresAtFiveTimesTheAverage() {
        Facts history = Facts.harmless().history(4, 4 * 5_000); // average £50

        assertThat(rule.evaluate(history.amount(24_999).build())).isEmpty();
        assertThat(rule.evaluate(history.amount(25_000).build()))
                .contains(new FraudRule.Hit(LargeAmountRule.CODE, LargeAmountRule.POINTS));
    }

    @Test
    void withoutEnoughHistoryFiresFromOneThousandPounds() {
        Facts thinHistory = Facts.harmless().history(2, 2 * 100);

        assertThat(rule.evaluate(thinHistory.amount(99_999).build())).isEmpty();
        assertThat(rule.evaluate(thinHistory.amount(100_000).build())).isPresent();
    }

    @Test
    void neverFiresBelowOneHundredPoundsEvenWithTinyAverage() {
        Facts tinyAverage = Facts.harmless().history(10, 10 * 10); // average 10p

        assertThat(rule.evaluate(tinyAverage.amount(9_999).build())).isEmpty();
        assertThat(rule.evaluate(tinyAverage.amount(10_000).build())).isPresent();
    }
}
