package dev.efeyildirim.vaultpay.payment;

/** The idempotency key was already used for a different request. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency-Key was already used with a different request");
    }
}
