package dev.efeyildirim.vaultpay.fraud;

public enum FraudDecision {
    /** Low risk: the transfer goes through. */
    ALLOW,
    /** Suspicious but not conclusive: the transfer goes through and the assessment is flagged for a human. */
    REVIEW,
    /** High risk: the transfer is rejected and nothing is posted. */
    BLOCK
}
