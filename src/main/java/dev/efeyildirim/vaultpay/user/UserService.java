package dev.efeyildirim.vaultpay.user;

import dev.efeyildirim.vaultpay.audit.AuditService;
import dev.efeyildirim.vaultpay.common.ConflictException;
import dev.efeyildirim.vaultpay.common.NotFoundException;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final Clock clock;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, AuditService audit, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public User register(String email, String rawPassword, String fullName) {
        String normalisedEmail = normaliseEmail(email);
        if (users.existsByEmail(normalisedEmail)) {
            throw new ConflictException("Email is already registered");
        }
        User user = new User(normalisedEmail, passwordEncoder.encode(rawPassword), fullName.strip(), clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email raced past the exists check: the UNIQUE constraint decides.
            throw new ConflictException("Email is already registered");
        }
        audit.record(user.getId(), "USER_REGISTERED", "USER", user.getId(), null);
        return user;
    }

    /**
     * Not exposed over HTTP: in this simulator reviewers are created by tests and by the dev-profile
     * bootstrap. The new role reaches the user's token at their next login.
     */
    @Transactional
    public void promoteToReviewer(UUID actorId, UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.promoteToReviewer();
        audit.record(actorId, "ROLE_GRANTED", "USER", userId, "role=" + UserRole.REVIEWER);
    }

    public static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
