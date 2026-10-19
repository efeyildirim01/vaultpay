package dev.efeyildirim.vaultpay.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewPayeeRuleTest {

    private final NewPayeeRule rule = new NewPayeeRule();

    @Test
    void firesForFirstTransferToAnotherCustomer() {
        assertThat(rule.evaluate(Facts.harmless().newPayee().build()))
                .contains(new FraudRule.Hit(NewPayeeRule.CODE, NewPayeeRule.POINTS));
    }

    @Test
    void doesNotFireForKnownPayee() {
        assertThat(rule.evaluate(Facts.harmless().build())).isEmpty();
    }

    @Test
    void doesNotFireBetweenOwnAccounts() {
        assertThat(rule.evaluate(Facts.harmless().toOwnAccount().newPayee().build())).isEmpty();
    }
}
