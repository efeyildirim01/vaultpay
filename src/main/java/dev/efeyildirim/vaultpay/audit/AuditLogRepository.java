package dev.efeyildirim.vaultpay.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    Slice<AuditLog> findAllByOrderByCreatedAtDesc(Pageable page);

    Slice<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable page);

    @Query("select distinct a.action from AuditLog a order by a.action")
    List<String> findDistinctActions();
}
