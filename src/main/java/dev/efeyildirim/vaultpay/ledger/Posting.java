package dev.efeyildirim.vaultpay.ledger;

import dev.efeyildirim.vaultpay.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One leg of a journal entry: negative = debit (money out), positive = credit (money in). */
@Entity
@Table(name = "postings")
public class Posting extends BaseEntity {

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private String currency;

    protected Posting() {
    }

    Posting(UUID journalEntryId, UUID accountId, long amountMinor, String currency, Instant createdAt) {
        super(createdAt);
        this.journalEntryId = journalEntryId;
        this.accountId = accountId;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public String getCurrency() {
        return currency;
    }
}
