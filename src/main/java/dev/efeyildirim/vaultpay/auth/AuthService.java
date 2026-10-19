package dev.efeyildirim.vaultpay.auth;

import dev.efeyildirim.vaultpay.user.User;
import dev.efeyildirim.vaultpay.user.UserRepository;
import dev.efeyildirim.vaultpay.user.UserService;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    /** Hash compared against when the email is unknown, so both failure paths take similar time. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public String login(String email, String rawPassword) {
        Optional<User> user = users.findByEmail(UserService.normaliseEmail(email));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(rawPassword, hash);
        if (user.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return tokens.issue(user.get());
    }
}
