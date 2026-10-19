package dev.efeyildirim.vaultpay.ledger;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByInitiatedByAndIdempotencyKey(UUID initiatedBy, String idempotencyKey);
}
