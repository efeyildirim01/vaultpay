package dev.efeyildirim.vaultpay.fraud;

import dev.efeyildirim.vaultpay.auth.CurrentUser;
import dev.efeyildirim.vaultpay.fraud.FraudReviewService.PendingReview;
import dev.efeyildirim.vaultpay.fraud.FraudReviewService.ReviewResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reviewer-only endpoints: SecurityConfig requires the fraud:review scope for everything under /api/fraud. */
@RestController
@RequestMapping("/api/fraud")
public class FraudReviewController {

    private final FraudReviewService reviewService;

    public FraudReviewController(FraudReviewService reviewService) {
        this.reviewService = reviewService;
    }

    public record ReviewRequest(@NotNull ReviewOutcome outcome, @Size(max = 500) String note) {
    }

    @GetMapping("/reviews/pending")
    public List<PendingReview> pending() {
        return reviewService.pending();
    }

    @PostMapping("/assessments/{assessmentId}/review")
    public ReviewResult review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assessmentId,
                               @Valid @RequestBody ReviewRequest request) {
        return reviewService.review(CurrentUser.id(jwt), assessmentId, request.outcome(), request.note());
    }
}
