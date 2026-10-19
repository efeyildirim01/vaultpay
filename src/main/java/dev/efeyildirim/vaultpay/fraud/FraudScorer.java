package dev.efeyildirim.vaultpay.fraud;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Adds up the points of every rule that fires (capped at 100) and maps the score to a decision.
 * No single rule is worth {@value #BLOCK_FROM} points, so a transfer is only blocked when several
 * independent signals agree.
 */
@Component
public class FraudScorer {

    static final int REVIEW_FROM = 40;
    static final int BLOCK_FROM = 70;

    private final List<FraudRule> rules;

    public FraudScorer(List<FraudRule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** @param hits the rules that fired, highest points first */
    public record Result(int score, FraudDecision decision, List<FraudRule.Hit> hits) {

        /** Stored form of the hits, e.g. {@code LARGE_AMOUNT:40,NEW_PAYEE:20}. */
        public String reasons() {
            return hits.stream().map(h -> h.code() + ":" + h.points()).collect(Collectors.joining(","));
        }
    }

    public Result score(TransferFacts facts) {
        List<FraudRule.Hit> hits = rules.stream()
                .map(rule -> rule.evaluate(facts))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(FraudRule.Hit::points).reversed().thenComparing(FraudRule.Hit::code))
                .toList();
        int score = Math.min(100, hits.stream().mapToInt(FraudRule.Hit::points).sum());
        return new Result(score, decide(score), hits);
    }

    static FraudDecision decide(int score) {
        if (score >= BLOCK_FROM) {
            return FraudDecision.BLOCK;
        }
        return score >= REVIEW_FROM ? FraudDecision.REVIEW : FraudDecision.ALLOW;
    }
}
