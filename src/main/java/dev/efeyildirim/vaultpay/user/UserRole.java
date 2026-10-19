package dev.efeyildirim.vaultpay.user;

public enum UserRole {
    CUSTOMER,
    /** Works the fraud review queue. Granted directly in the database; there is no endpoint for it. */
    REVIEWER
}
