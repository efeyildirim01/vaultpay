package dev.efeyildirim.vaultpay.account;

import dev.efeyildirim.vaultpay.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Deliberately has no balance field: the balance is derived from ledger postings. */
@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    /** Well-known id of the simulated "outside world" account that funds deposits (seeded by V1). */
    public static final UUID EXTERNAL_FUNDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Column(name = "owner_id")
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private AccountType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private AccountStatus status;

    protected Account() {
    }

    private Account(UUID ownerId, AccountType type, String name, String currency, Instant createdAt) {
        super(createdAt);
        this.ownerId = ownerId;
        this.type = type;
        this.name = name;
        this.currency = currency;
        this.status = AccountStatus.ACTIVE;
    }

    public static Account openCustomerAccount(UUID ownerId, String name, String currency, Instant createdAt) {
        return new Account(ownerId, AccountType.CUSTOMER, name, currency, createdAt);
    }

    public boolean isOwnedBy(UUID userId) {
        return type == AccountType.CUSTOMER && ownerId.equals(userId);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean mayGoNegative() {
        return type == AccountType.SYSTEM;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public AccountType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }
}
