package dev.efeyildirim.vaultpay.common;

/** Also used when a resource exists but belongs to someone else, so IDs cannot be probed. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
