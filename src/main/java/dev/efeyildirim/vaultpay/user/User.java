package dev.efeyildirim.vaultpay.user;

import dev.efeyildirim.vaultpay.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private UserRole role;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, Instant createdAt) {
        super(createdAt);
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = UserRole.CUSTOMER;
    }

    void promoteToReviewer() {
        this.role = UserRole.REVIEWER;
    }

    public UserRole getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }
}
