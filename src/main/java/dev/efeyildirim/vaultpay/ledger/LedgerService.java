package dev.efeyildirim.vaultpay.ledger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that writes to the ledger. It enforces the double-entry rule:
 * every journal entry has at least two postings, all in one currency, summing to exactly zero.
 */
@Service
public class LedgerService {

    private final JournalEntryRepository journalEntries;
    private final PostingRepository postings;
    private final Clock clock;

    public LedgerService(JournalEntryRepository journalEntries, PostingRepository postings, Clock clock) {
        this.journalEntries = journalEntries;
        this.postings = postings;
        this.clock = clock;
    }

    /** A leg of a journal entry before it is persisted. */
    public record Leg(UUID accountId, long amountMinor) {

        public static Leg debit(UUID accountId, long amountMinor) {
            return new Leg(accountId, -amountMinor);
        }

        public static Leg credit(UUID accountId, long amountMinor) {
            return new Leg(accountId, amountMinor);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public JournalEntry post(JournalEntryType type, UUID initiatedBy, String idempotencyKey, String requestHash,
                             String reference, String currency, List<Leg> legs) {
        if (legs.size() < 2) {
            throw new IllegalArgumentException("A journal entry needs at least two legs");
        }
        if (legs.stream().anyMatch(l -> l.amountMinor() == 0)) {
            throw new IllegalArgumentException("Legs must not be zero");
        }
        // Math.addExact throws on overflow instead of silently wrapping around.
        long sum = legs.stream().mapToLong(Leg::amountMinor).reduce(0L, Math::addExact);
        if (sum != 0) {
            throw new IllegalArgumentException("Journal entry does not balance: sum=" + sum);
        }

        Instant now = clock.instant();
        JournalEntry entry = journalEntries.saveAndFlush(
                new JournalEntry(type, initiatedBy, idempotencyKey, requestHash, reference, now));
        postings.saveAll(legs.stream()
                .map(l -> new Posting(entry.getId(), l.accountId(), l.amountMinor(), currency, now))
                .toList());
        return entry;
    }

    @Transactional(readOnly = true)
    public long balanceOf(UUID accountId) {
        return postings.balanceOf(accountId);
    }

    @Transactional(readOnly = true)
    public Optional<JournalEntry> findByIdempotencyKey(UUID initiatedBy, String idempotencyKey) {
        return journalEntries.findByInitiatedByAndIdempotencyKey(initiatedBy, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public List<Posting> postingsOf(UUID journalEntryId) {
        return postings.findByJournalEntryId(journalEntryId);
    }
}
