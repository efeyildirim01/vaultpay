package dev.efeyildirim.vaultpay.ledger;

import dev.efeyildirim.vaultpay.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One business event in the ledger. Its money movements are the {@link Posting}s that reference it. */
@Entity
@Table(name = "journal_entries")
public class JournalEntry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private JournalEntryType type;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    /** SHA-256 of the request, to tell a genuine retry from a different request reusing a key. */
    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    private String reference;

    protected JournalEntry() {
    }

    JournalEntry(JournalEntryType type, UUID initiatedBy, String idempotencyKey, String requestHash,
                 String reference, Instant createdAt) {
        super(createdAt);
        this.type = type;
        this.initiatedBy = initiatedBy;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.reference = reference;
    }

    public JournalEntryType getType() {
        return type;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getReference() {
        return reference;
    }
}
