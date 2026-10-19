package dev.efeyildirim.vaultpay.fraud;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FraudAssessmentRepository extends JpaRepository<FraudAssessment, UUID> {

    List<FraudAssessment> findByUserId(UUID userId);

    /** Assessments with this decision that no reviewer has decided yet, oldest first. */
    @Query("""
            select a from FraudAssessment a
            where a.decision = :decision
              and not exists (select r from FraudReview r where r.assessmentId = a.id)
            order by a.createdAt""")
    List<FraudAssessment> findUnreviewed(@Param("decision") FraudDecision decision, Pageable page);

    @Query("""
            select count(a) from FraudAssessment a
            where a.decision = :decision
              and not exists (select r from FraudReview r where r.assessmentId = a.id)""")
    long countUnreviewed(@Param("decision") FraudDecision decision);

    long countByDecision(FraudDecision decision);
}
