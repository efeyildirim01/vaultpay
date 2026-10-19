package dev.efeyildirim.vaultpay.fraud;

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

/** Append-only record of one fraud decision. {@code journalEntryId} is null for a blocked transfer (nothing posted). */
@Entity
@Table(name = "fraud_assessments")
public class FraudAssessment extends BaseEntity {

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private FraudDecision decision;

    private String reasons;

    protected FraudAssessment() {
    }

    FraudAssessment(UUID journalEntryId, UUID userId, FraudScorer.Result result, Instant createdAt) {
        super(createdAt);
        this.journalEntryId = journalEntryId;
        this.userId = userId;
        this.score = result.score();
        this.decision = result.decision();
        this.reasons = result.reasons();
    }

    public UUID getJournalEntryId() {
        return journalEntryId;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getScore() {
        return score;
    }

    public FraudDecision getDecision() {
        return decision;
    }

    public String getReasons() {
        return reasons;
    }
}
