package dev.efeyildirim.vaultpay.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

    /** Number of debits of one journal-entry type on an account since a point in time. */
    @Query("""
            select count(p) from Posting p join JournalEntry j on j.id = p.journalEntryId
            where p.accountId = :accountId and p.amountMinor < 0 and j.type = :type and p.createdAt >= :since""")
    long countDebitsSince(@Param("accountId") UUID accountId, @Param("type") JournalEntryType type,
                          @Param("since") Instant since);

    /** Money (as a positive number) that left an account through one journal-entry type since a point in time. */
    @Query("""
            select coalesce(-sum(p.amountMinor), 0) from Posting p join JournalEntry j on j.id = p.journalEntryId
            where p.accountId = :accountId and p.amountMinor < 0 and j.type = :type and p.createdAt >= :since""")
    long sumDebitsSince(@Param("accountId") UUID accountId, @Param("type") JournalEntryType type,
                        @Param("since") Instant since);

    /** Money that arrived in an account through one journal-entry type since a point in time. */
    @Query("""
            select coalesce(sum(p.amountMinor), 0) from Posting p join JournalEntry j on j.id = p.journalEntryId
            where p.accountId = :accountId and p.amountMinor > 0 and j.type = :type and p.createdAt >= :since""")
    long sumCreditsSince(@Param("accountId") UUID accountId, @Param("type") JournalEntryType type,
                         @Param("since") Instant since);

    /**
     * An account's statement, newest first. Every journal entry today has exactly two legs, so joining the
     * other leg of the same entry gives one row per posting with its counterparty.
     */
    @Query("""
            select new dev.efeyildirim.vaultpay.ledger.LedgerLine(
                       j.id, j.type, p.amountMinor, p.currency, c.accountId, j.reference, p.createdAt)
            from Posting p
            join JournalEntry j on j.id = p.journalEntryId
            join Posting c on c.journalEntryId = p.journalEntryId and c.accountId <> p.accountId
            where p.accountId = :accountId
            order by p.createdAt desc, j.id desc""")
    Slice<LedgerLine> statement(@Param("accountId") UUID accountId, Pageable pageable);

    /** Number of journal entries that debited {@code from} and credited {@code to}. */
    @Query("""
            select count(d) from Posting d, Posting c
            where d.journalEntryId = c.journalEntryId
              and d.accountId = :from and d.amountMinor < 0
              and c.accountId = :to and c.amountMinor > 0""")
    long countMovements(@Param("from") UUID from, @Param("to") UUID to);
}
