package dev.efeyildirim.vaultpay.auth;

import dev.efeyildirim.vaultpay.user.User;
import dev.efeyildirim.vaultpay.user.UserRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    /** Reviewers get the {@value #REVIEW_SCOPE} scope, which Spring Security exposes as SCOPE_fraud:review. */
    public static final String REVIEW_SCOPE = "fraud:review";

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, @Value("${vaultpay.jwt.ttl}") Duration ttl, Clock clock) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("vaultpay")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", user.getEmail());
        if (user.getRole() == UserRole.REVIEWER) {
            claims.claim("scope", REVIEW_SCOPE);
        }
        return sign(claims.build());
    }

    private String sign(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Duration ttl() {
        return ttl;
    }
}
