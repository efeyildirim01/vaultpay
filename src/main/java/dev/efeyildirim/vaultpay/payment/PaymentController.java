package dev.efeyildirim.vaultpay.payment;

import dev.efeyildirim.vaultpay.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money-moving endpoints. Each requires an Idempotency-Key header chosen by the client:
 * retrying with the same key and body returns the original result (200 + Idempotent-Replayed: true)
 * instead of moving money twice.
 */
@RestController
@RequestMapping("/api")
public class PaymentController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String KEY_PATTERN = "[A-Za-z0-9_-]{8,100}";
    /** Per-request ceiling for the simulator: 1,000,000.00 GBP in pence. */
    static final long MAX_AMOUNT_MINOR = 100_000_000L;

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    public record DepositRequest(
            @Positive @Max(MAX_AMOUNT_MINOR) long amountMinor,
            @Size(max = 140) String reference) {
    }

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @Positive @Max(MAX_AMOUNT_MINOR) long amountMinor,
            @Size(max = 140) String reference) {
    }

    @PostMapping("/accounts/{accountId}/deposits")
    public ResponseEntity<MovementResult> deposit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_PATTERN) String idempotencyKey,
            @PathVariable UUID accountId,
            @Valid @RequestBody DepositRequest request) {
        MovementResult result = payments.deposit(CurrentUser.id(jwt), idempotencyKey, accountId,
                request.amountMinor(), request.reference());
        return respond(result);
    }

    @PostMapping("/transfers")
    public ResponseEntity<MovementResult> transfer(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(IDEMPOTENCY_KEY) @Pattern(regexp = KEY_PATTERN) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        MovementResult result = payments.transfer(CurrentUser.id(jwt), idempotencyKey, request.fromAccountId(),
                request.toAccountId(), request.amountMinor(), request.reference());
        return respond(result);
    }

    private static ResponseEntity<MovementResult> respond(MovementResult result) {
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(result);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
