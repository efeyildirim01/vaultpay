package dev.efeyildirim.vaultpay.fraud;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudReviewRepository extends JpaRepository<FraudReview, UUID> {

    boolean existsByAssessmentId(UUID assessmentId);

    Optional<FraudReview> findByAssessmentId(UUID assessmentId);
}
