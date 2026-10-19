package dev.efeyildirim.vaultpay.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VelocityRuleTest {

    private final VelocityRule rule = new VelocityRule();

    @Test
    void firesOnceFiveTransfersAlreadyHappenedInTheWindow() {
        assertThat(rule.evaluate(Facts.harmless().recentTransfers(4).build())).isEmpty();
        assertThat(rule.evaluate(Facts.harmless().recentTransfers(5).build()))
                .contains(new FraudRule.Hit(VelocityRule.CODE, VelocityRule.POINTS));
    }

    @Test
    void appliesToOwnAccountsToo() {
        assertThat(rule.evaluate(Facts.harmless().toOwnAccount().recentTransfers(9).build())).isPresent();
    }
}
