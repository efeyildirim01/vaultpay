package dev.efeyildirim.vaultpay.account;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * SELECT ... FOR UPDATE: takes a row lock held until the surrounding transaction ends.
     * Any other transaction trying to lock the same account waits, which serialises
     * balance checks and postings per account.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    List<Account> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    long countByType(AccountType type);

    long countByStatus(AccountStatus status);
}
