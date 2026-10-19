package dev.efeyildirim.vaultpay.account;

import dev.efeyildirim.vaultpay.audit.AuditService;
import dev.efeyildirim.vaultpay.common.BusinessRuleException;
import dev.efeyildirim.vaultpay.common.NotFoundException;
import dev.efeyildirim.vaultpay.ledger.JournalEntryType;
import dev.efeyildirim.vaultpay.ledger.LedgerLine;
import dev.efeyildirim.vaultpay.ledger.LedgerService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    /** Only GBP for now: the simulated funding account is GBP and there is no FX yet. */
    static final Set<String> SUPPORTED_CURRENCIES = Set.of("GBP");

    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final AuditService audit;
    private final Clock clock;

    public AccountService(AccountRepository accounts, LedgerService ledger, AuditService audit, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.audit = audit;
        this.clock = clock;
    }

    public record AccountView(UUID id, String name, String currency, AccountStatus status,
                              long balanceMinor, Instant createdAt) {
    }

    @Transactional
    public AccountView open(UUID ownerId, String name, String currency) {
        if (!SUPPORTED_CURRENCIES.contains(currency)) {
            throw new BusinessRuleException("Unsupported currency: " + currency);
        }
        Account account = accounts.save(Account.openCustomerAccount(ownerId, name.strip(), currency, clock.instant()));
        audit.record(ownerId, "ACCOUNT_OPENED", "ACCOUNT", account.getId(), "currency=" + currency);
        return view(account, 0L);
    }

    @Transactional(readOnly = true)
    public List<AccountView> listFor(UUID ownerId) {
        return accounts.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                .map(a -> view(a, ledger.balanceOf(a.getId())))
                .toList();
    }

    /** One row of an account statement, as the customer sees it. */
    public record TransactionView(UUID journalEntryId, JournalEntryType type, long amountMinor, String currency,
                                  UUID counterpartyAccountId, String counterpartyLabel, String reference,
                                  Instant createdAt) {
    }

    public record TransactionPage(List<TransactionView> items, int page, int size, boolean hasMore) {
    }

    @Transactional(readOnly = true)
    public TransactionPage history(UUID ownerId, UUID accountId, int page, int size) {
        accounts.findById(accountId)
                .filter(a -> a.isOwnedBy(ownerId))
                .orElseThrow(() -> new NotFoundException("Account not found"));
        // A Slice fetches one row more than the page size to know whether another page exists,
        // which is cheaper than a COUNT(*) over the account's whole history.
        Slice<LedgerLine> lines = ledger.statement(accountId, page, size);
        List<TransactionView> items = lines.stream().map(AccountService::toView).toList();
        return new TransactionPage(items, page, size, lines.hasNext());
    }

    private static TransactionView toView(LedgerLine line) {
        String label = Account.EXTERNAL_FUNDING_ID.equals(line.counterpartyAccountId())
                ? "External funding (simulated)" : null;
        return new TransactionView(line.journalEntryId(), line.type(), line.amountMinor(), line.currency(),
                line.counterpartyAccountId(), label, line.reference(), line.createdAt());
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID ownerId, UUID accountId) {
        Account account = accounts.findById(accountId)
                .filter(a -> a.isOwnedBy(ownerId))
                .orElseThrow(() -> new NotFoundException("Account not found"));
        return view(account, ledger.balanceOf(accountId));
    }

    /**
     * Locks the given accounts in ascending id order. If transfer A->B and transfer B->A run at the
     * same time, both lock the lower id first, so neither can hold one lock while waiting for the
     * other (the classic deadlock). Must run inside the caller's transaction, which holds the locks.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Account> lockInOrder(UUID... accountIds) {
        Map<UUID, Account> locked = new LinkedHashMap<>();
        Stream.of(accountIds).distinct().sorted().forEach(id -> locked.put(id,
                accounts.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Account not found"))));
        return locked;
    }

    /**
     * Freezes a customer account. Takes the same row lock as transfers, so a transfer that is already
     * running finishes first and every later one sees the frozen status.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void freeze(UUID actorId, UUID accountId, String reason) {
        Account account = lockInOrder(accountId).get(accountId);
        if (account.getStatus() == AccountStatus.ACTIVE) {
            account.freeze();
            audit.record(actorId, "ACCOUNT_FROZEN", "ACCOUNT", accountId, reason);
        }
    }

    private static AccountView view(Account a, long balanceMinor) {
        return new AccountView(a.getId(), a.getName(), a.getCurrency(), a.getStatus(), balanceMinor, a.getCreatedAt());
    }
}
