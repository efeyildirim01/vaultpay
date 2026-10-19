package dev.efeyildirim.vaultpay.ledger;

import java.time.Instant;
import java.util.UUID;

/**
 * One account's side of a journal entry, joined with the other side (the counterparty).
 *
 * @param amountMinor signed from this account's point of view: negative = money out, positive = money in
 */
public record LedgerLine(UUID journalEntryId, JournalEntryType type, long amountMinor, String currency,
                         UUID counterpartyAccountId, String reference, Instant createdAt) {
}
