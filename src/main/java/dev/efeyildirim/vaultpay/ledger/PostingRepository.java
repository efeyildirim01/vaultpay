package dev.efeyildirim.vaultpay.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostingRepository extends JpaRepository<Posting, UUID> {

    @Query("select coalesce(sum(p.amountMinor), 0) from Posting p where p.accountId = :accountId")
    long balanceOf(@Param("accountId") UUID accountId);

    List<Posting> findByJournalEntryId(UUID journalEntryId);

    /** Ledger invariant check: ids of journal entries whose postings do not sum to zero. Must be empty. */
    @Query("select p.journalEntryId from Posting p group by p.journalEntryId having sum(p.amountMinor) <> 0")
    List<UUID> findUnbalancedJournalEntries();

    @Query("select coalesce(sum(p.amountMinor), 0) from Posting p")
    long sumOfAllPostings();
}
