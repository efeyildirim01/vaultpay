package dev.efeyildirim.vaultpay.fraud;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudAssessmentRepository extends JpaRepository<FraudAssessment, UUID> {

    List<FraudAssessment> findByUserId(UUID userId);
}
