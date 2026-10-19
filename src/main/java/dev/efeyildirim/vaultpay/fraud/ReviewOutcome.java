package dev.efeyildirim.vaultpay.fraud;

public enum ReviewOutcome {
    /** False alarm: nothing happens. */
    CLEARED,
    /** Real fraud: the account the money left from is frozen. Moving the money back is not modelled. */
    CONFIRMED_FRAUD
}
