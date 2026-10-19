package dev.efeyildirim.vaultpay.payment;

import dev.efeyildirim.vaultpay.ledger.JournalEntryType;
import java.time.Instant;
import java.util.UUID;

/**
 * Outcome of a deposit or transfer.
 *
 * @param replayed true when this is the stored result of an earlier request with the same idempotency key
 */
public record MovementResult(
        UUID journalEntryId,
        JournalEntryType type,
        UUID fromAccountId,
        UUID toAccountId,
        long amountMinor,
        String currency,
        String reference,
        Instant createdAt,
        boolean replayed) {
}
