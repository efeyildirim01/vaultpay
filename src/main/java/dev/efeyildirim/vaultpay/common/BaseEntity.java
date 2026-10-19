package dev.efeyildirim.vaultpay.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * IDs are generated in the application (UUIDs), not by the database.
 * Implementing {@link Persistable} tells Spring Data that a freshly built entity is new,
 * so {@code save()} does a plain INSERT instead of a SELECT followed by a merge.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew;

    protected BaseEntity() {
        // for JPA
    }

    protected BaseEntity(Instant createdAt) {
        this.id = UUID.randomUUID();
        this.createdAt = createdAt;
        this.isNew = true;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
