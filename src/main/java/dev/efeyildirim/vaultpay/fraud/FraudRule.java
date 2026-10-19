package dev.efeyildirim.vaultpay.fraud;

import java.util.Optional;

/**
 * One explainable risk signal. A rule either does not fire, or fires with a fixed number of points
 * and a stable reason code that is stored with the assessment, so every score can be explained afterwards.
 */
public interface FraudRule {

    Optional<Hit> evaluate(TransferFacts facts);

    record Hit(String code, int points) {
    }
}
