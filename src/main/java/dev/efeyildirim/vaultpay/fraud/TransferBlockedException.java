package dev.efeyildirim.vaultpay.fraud;

import java.util.UUID;

/**
 * The transfer was rejected by fraud screening. The client only gets the assessment id, not the reasons:
 * telling a fraudster which rule fired would show them how to stay under it.
 */
public class TransferBlockedException extends RuntimeException {

    private final UUID assessmentId;

    public TransferBlockedException(UUID assessmentId) {
        super("Transfer blocked by fraud screening");
        this.assessmentId = assessmentId;
    }

    public UUID getAssessmentId() {
        return assessmentId;
    }
}
