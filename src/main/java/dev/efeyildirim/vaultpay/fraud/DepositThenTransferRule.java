package dev.efeyildirim.vaultpay.fraud;

import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Money in, money straight out: the account received deposits in the last 30 minutes and this transfer
 * sends at least half of that amount to another customer (the classic "money mule" pattern).
 * Moving a fresh deposit into your own savings account never fires.
 */
@Component
public class DepositThenTransferRule implements FraudRule {

    public static final String CODE = "DEPOSIT_THEN_TRANSFER";
    public static final int POINTS = 25;
    public static final Duration WINDOW = Duration.ofMinutes(30);

    @Override
    public Optional<Hit> evaluate(TransferFacts facts) {
        long deposited = facts.recentDepositsMinor();
        boolean passesThrough = !facts.toOwnAccount() && deposited > 0 && 2 * facts.amountMinor() >= deposited;
        return passesThrough ? Optional.of(new Hit(CODE, POINTS)) : Optional.empty();
    }
}
