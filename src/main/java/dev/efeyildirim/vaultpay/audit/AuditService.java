package dev.efeyildirim.vaultpay.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogs;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogs, Clock clock) {
        this.auditLogs = auditLogs;
        this.clock = clock;
    }

    /**
     * MANDATORY: must join the caller's transaction, so the audit row commits or rolls back
     * together with the change it describes. Calling it outside a transaction is a bug and fails fast.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String entityType, UUID entityId, String details) {
        auditLogs.save(new AuditLog(actorId, action, entityType, entityId, details, clock.instant()));
    }
}
