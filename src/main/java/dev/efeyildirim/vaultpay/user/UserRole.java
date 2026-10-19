package dev.efeyildirim.vaultpay.user;

/** Roles are granted directly (tests, staff bootstrap); there is no endpoint to change them. */
public enum UserRole {
    CUSTOMER,
    /** Works the fraud review queue. */
    REVIEWER,
    /** Back-office panel (overview, audit log) plus everything a reviewer can do. */
    ADMIN
}
