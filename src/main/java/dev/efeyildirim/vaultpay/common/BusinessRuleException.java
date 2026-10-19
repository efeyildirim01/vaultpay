package dev.efeyildirim.vaultpay.common;

/** A well-formed request that breaks a banking rule (e.g. currency mismatch, frozen account). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
