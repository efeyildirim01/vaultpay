package dev.efeyildirim.vaultpay.account;

public enum AccountType {
    /** Owned by a user; may never go below zero. */
    CUSTOMER,
    /** Owned by the bank (e.g. simulated external funding); may go negative. */
    SYSTEM
}
