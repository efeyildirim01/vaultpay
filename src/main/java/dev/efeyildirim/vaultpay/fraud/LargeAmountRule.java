package dev.efeyildirim.vaultpay.fraud;

import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The amount is unusually large for this account. With enough history (at least {@value #MIN_HISTORY}
 * transfers in the last 90 days) "large" means at least {@value #MULTIPLIER}x the account's average transfer;
 * without that history it means at least £1,000. Amounts under £100 never fire, so tiny averages do not
 * make every normal payment look large.
 */
@Component
public class LargeAmountRule implements FraudRule {

    public static final String CODE = "LARGE_AMOUNT";
    public static final int POINTS = 40;
    public static final Duration HISTORY = Duration.ofDays(90);
    static final int MIN_HISTORY = 3;
    static final int MULTIPLIER = 5;
    static final long FLOOR_MINOR = 10_000;             // £100
    static final long NO_HISTORY_THRESHOLD_MINOR = 100_000; // £1,000

    @Override
    public Optional<Hit> evaluate(TransferFacts facts) {
        long amount = facts.amountMinor();
        if (amount < FLOOR_MINOR) {
            return Optional.empty();
        }
        boolean large;
        if (facts.historyTransferCount() >= MIN_HISTORY) {
            long average = facts.historyTransferTotalMinor() / facts.historyTransferCount();
            large = amount >= MULTIPLIER * average;
        } else {
            large = amount >= NO_HISTORY_THRESHOLD_MINOR;
        }
        return large ? Optional.of(new Hit(CODE, POINTS)) : Optional.empty();
    }
}
