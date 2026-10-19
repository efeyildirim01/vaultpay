package dev.efeyildirim.vaultpay.fraud;

import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Many transfers in a short time: the source account already sent {@value #MAX_IN_WINDOW} or more
 * transfers in the last 10 minutes (typical of a script emptying a compromised account).
 */
@Component
public class VelocityRule implements FraudRule {

    public static final String CODE = "HIGH_VELOCITY";
    public static final int POINTS = 30;
    public static final Duration WINDOW = Duration.ofMinutes(10);
    static final int MAX_IN_WINDOW = 5;

    @Override
    public Optional<Hit> evaluate(TransferFacts facts) {
        return facts.recentTransferCount() >= MAX_IN_WINDOW ? Optional.of(new Hit(CODE, POINTS)) : Optional.empty();
    }
}
