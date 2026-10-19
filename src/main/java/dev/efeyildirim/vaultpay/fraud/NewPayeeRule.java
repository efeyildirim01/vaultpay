package dev.efeyildirim.vaultpay.fraud;

import java.util.Optional;
import org.springframework.stereotype.Component;

/** First ever transfer from this account to another customer's account. Moves between own accounts never fire. */
@Component
public class NewPayeeRule implements FraudRule {

    public static final String CODE = "NEW_PAYEE";
    public static final int POINTS = 20;

    @Override
    public Optional<Hit> evaluate(TransferFacts facts) {
        return !facts.toOwnAccount() && !facts.knownPayee() ? Optional.of(new Hit(CODE, POINTS)) : Optional.empty();
    }
}
