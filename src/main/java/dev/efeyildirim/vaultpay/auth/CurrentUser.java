package dev.efeyildirim.vaultpay.auth;

import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The JWT subject is the user's id; this is the only place that knowledge lives. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
