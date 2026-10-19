package dev.efeyildirim.vaultpay.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DepositThenTransferRuleTest {

    private final DepositThenTransferRule rule = new DepositThenTransferRule();

    @Test
    void firesWhenAtLeastHalfOfARecentDepositLeaves() {
        Facts deposited = Facts.harmless().recentDeposits(10_000);

        assertThat(rule.evaluate(deposited.amount(4_999).build())).isEmpty();
        assertThat(rule.evaluate(deposited.amount(5_000).build()))
                .contains(new FraudRule.Hit(DepositThenTransferRule.CODE, DepositThenTransferRule.POINTS));
    }

    @Test
    void doesNotFireWithoutRecentDeposit() {
        assertThat(rule.evaluate(Facts.harmless().amount(50_000).build())).isEmpty();
    }

    @Test
    void doesNotFireWhenMovingIntoOwnAccount() {
        assertThat(rule.evaluate(Facts.harmless().toOwnAccount().recentDeposits(10_000).amount(10_000).build()))
                .isEmpty();
    }
}
