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

/** A reviewer's decision on one assessment. Append-only, and at most one per assessment (UNIQUE in V3). */
@Entity
@Table(name = "fraud_reviews")
public class FraudReview extends BaseEntity {

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private ReviewOutcome outcome;

    private String note;

    protected FraudReview() {
    }

    FraudReview(UUID assessmentId, UUID reviewerId, ReviewOutcome outcome, String note, Instant createdAt) {
        super(createdAt);
        this.assessmentId = assessmentId;
        this.reviewerId = reviewerId;
        this.outcome = outcome;
        this.note = note;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public ReviewOutcome getOutcome() {
        return outcome;
    }

    public String getNote() {
        return note;
    }
}
