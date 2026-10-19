package dev.efeyildirim.vaultpay.admin;

import dev.efeyildirim.vaultpay.account.Account;
import dev.efeyildirim.vaultpay.account.AccountRepository;
import dev.efeyildirim.vaultpay.account.AccountStatus;
import dev.efeyildirim.vaultpay.account.AccountType;
import dev.efeyildirim.vaultpay.audit.AuditLog;
import dev.efeyildirim.vaultpay.audit.AuditLogRepository;
import dev.efeyildirim.vaultpay.fraud.FraudAssessmentRepository;
import dev.efeyildirim.vaultpay.fraud.FraudDecision;
import dev.efeyildirim.vaultpay.ledger.LedgerService;
import dev.efeyildirim.vaultpay.user.User;
import dev.efeyildirim.vaultpay.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only back-office views. Nothing here changes data. */
@Service
public class AdminService {

    private final UserRepository users;
    private final AccountRepository accounts;
    private final LedgerService ledger;
    private final FraudAssessmentRepository assessments;
    private final AuditLogRepository auditLogs;

    public AdminService(UserRepository users, AccountRepository accounts, LedgerService ledger,
                        FraudAssessmentRepository assessments, AuditLogRepository auditLogs) {
        this.users = users;
        this.accounts = accounts;
        this.ledger = ledger;
        this.assessments = assessments;
        this.auditLogs = auditLogs;
    }

    /**
     * @param customerMoneyMinor all money held in customer accounts; by double entry this equals minus the
     *                           balance of the simulated external funding account
     */
    public record Overview(long users, long customerAccounts, long frozenAccounts, long journalEntries,
                           long customerMoneyMinor, long flaggedForReview, long pendingReviews,
                           long blockedTransfers, boolean ledgerBalanced) {
    }

    public record AuditView(UUID id, Instant createdAt, UUID actorId, String actorEmail, String action,
                            String entityType, UUID entityId, String details) {
    }

    public record AuditPage(List<AuditView> items, int page, int size, boolean hasMore, List<String> actions) {
    }

    @Transactional(readOnly = true)
    public Overview overview() {
        return new Overview(
                users.count(),
                accounts.countByType(AccountType.CUSTOMER),
                accounts.countByStatus(AccountStatus.FROZEN),
                ledger.journalEntryCount(),
                -ledger.balanceOf(Account.EXTERNAL_FUNDING_ID),
                assessments.countByDecision(FraudDecision.REVIEW),
                assessments.countUnreviewed(FraudDecision.REVIEW),
                assessments.countByDecision(FraudDecision.BLOCK),
                ledger.isBalanced());
    }

    @Transactional(readOnly = true)
    public AuditPage audit(String action, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Slice<AuditLog> rows = action == null || action.isBlank()
                ? auditLogs.findAllByOrderByCreatedAtDesc(pageRequest)
                : auditLogs.findByActionOrderByCreatedAtDesc(action, pageRequest);
        List<AuditLog> pageRows = rows.getContent();

        // One query for all actor emails on the page instead of one per row.
        Map<UUID, String> emails = users.findAllById(pageRows.stream().map(AuditLog::getActorId)
                        .filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getEmail, (a, b) -> a));
        List<AuditView> items = pageRows.stream()
                .map(a -> new AuditView(a.getId(), a.getCreatedAt(), a.getActorId(),
                        a.getActorId() == null ? null : emails.get(a.getActorId()), a.getAction(),
                        a.getEntityType(), a.getEntityId(), a.getDetails()))
                .toList();
        return new AuditPage(items, page, size, rows.hasNext(), auditLogs.findDistinctActions());
    }
}
