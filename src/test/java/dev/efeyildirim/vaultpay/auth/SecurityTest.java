package dev.efeyildirim.vaultpay.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.efeyildirim.vaultpay.ApiTestSupport;
import dev.efeyildirim.vaultpay.user.User;
import dev.efeyildirim.vaultpay.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Attacks on authentication and authorisation that must all fail. */
class SecurityTest extends ApiTestSupport {

    @Autowired
    JwtEncoder jwtEncoder;

    // --- forged and stale tokens ----------------------------------------------------------------

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token = sign(jwtEncoder, claims(existingUserId(), TokenService.ISSUER)
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600)));

        getWithToken("/api/accounts", token).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejectedEvenWithAdminScope() throws Exception {
        JwtEncoder attackerEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("attacker-controlled-secret-0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256")));
        String forged = sign(attackerEncoder, claims(existingUserId(), TokenService.ISSUER)
                .claim("scope", TokenService.REVIEW_SCOPE + " " + TokenService.AUDIT_SCOPE));

        getWithToken("/api/admin/overview", forged).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedPayloadBreaksTheSignature() throws Exception {
        String token = customerToken();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        // Try to grant ourselves admin rights without re-signing.
        String escalated = payload.replace("\"iss\"", "\"scope\":\"fraud:review audit:read\",\"iss\"");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(escalated.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        getWithToken("/api/admin/overview", tampered).andExpect(status().isUnauthorized());
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() throws Exception {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(("{\"sub\":\"" + existingUserId() + "\",\"iss\":\"vaultpay\","
                + "\"exp\":" + (Instant.now().getEpochSecond() + 600) + ",\"scope\":\"audit:read\"}")
                .getBytes(StandardCharsets.UTF_8));

        getWithToken("/api/admin/overview", header + "." + payload + ".").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        String token = sign(jwtEncoder, claims(existingUserId(), "someone-else"));

        getWithToken("/api/accounts", token).andExpect(status().isUnauthorized());
    }

    @Test
    void malformedAuthorizationHeaderIsRejected() throws Exception {
        getWithToken("/api/accounts", "not-a-jwt").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    // --- authorisation --------------------------------------------------------------------------

    @Test
    void everyProtectedEndpointNeedsAToken() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String url : new String[] {"/api/accounts", "/api/accounts/" + id, "/api/accounts/" + id + "/transactions",
                "/api/fraud/reviews/pending", "/api/admin/overview", "/api/admin/audit"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customersCannotReachStaffEndpoints() throws Exception {
        String customer = customerToken();
        getWithToken("/api/fraud/reviews/pending", customer).andExpect(status().isForbidden());
        getWithToken("/api/admin/overview", customer).andExpect(status().isForbidden());
        getWithToken("/api/admin/audit", customer).andExpect(status().isForbidden());
        mvc.perform(post("/api/fraud/assessments/" + UUID.randomUUID() + "/review")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\": \"CLEARED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewersCannotReadTheAuditLog() throws Exception {
        getWithToken("/api/admin/audit", tokenWithRole(UserRole.REVIEWER)).andExpect(status().isForbidden());
    }

    @Test
    void roleCannotBeSetThroughRegistration() throws Exception {
        String email = newEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "fullName": "Mallory", "role": "ADMIN",
                         "scope": "audit:read"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(email).orElseThrow().getRole()).isEqualTo(UserRole.CUSTOMER);
        getWithToken("/api/admin/overview", login(email)).andExpect(status().isForbidden());
    }

    @Test
    void internalActuatorEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        getWithToken("/actuator/env", customerToken()).andExpect(status().isNotFound());
        getWithToken("/actuator/beans", customerToken()).andExpect(status().isNotFound());
    }

    // --- credentials ----------------------------------------------------------------------------

    @Test
    void passwordsAreStoredAsBcryptHashesAndNeverReturned() throws Exception {
        String email = newEmail();
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "fullName": "Test User"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD).doesNotContainIgnoringCase("password");
        String hash = userRepository.findByEmail(email).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);
    }

    @Test
    void loginDoesNotRevealWhetherAnEmailExists() throws Exception {
        String email = newEmail();
        register(email);

        String wrongPassword = loginFailure(email, "wrong-password-123");
        String unknownEmail = loginFailure(newEmail(), "wrong-password-123");
        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void injectionAttemptsInLoginAreJustWrongCredentials() throws Exception {
        loginFailure("' OR '1'='1' --@example.com", "' OR '1'='1");
        loginFailure("admin@vaultpay.local'; DROP TABLE users; --", "x");
        assertThat(userRepository.count()).isPositive();
    }

    @Test
    void weakPasswordsAreRejectedAtRegistration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "short", "fullName": "Test User"}""".formatted(newEmail())))
                .andExpect(status().isBadRequest());
    }

    // --- transport hygiene ----------------------------------------------------------------------

    @Test
    void apiResponsesCarrySecurityHeadersAndAreNotCached() throws Exception {
        getWithToken("/api/accounts", customerToken())
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void errorsDoNotLeakStackTraces() throws Exception {
        getWithToken("/api/accounts/not-a-uuid", customerToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    // --- helpers --------------------------------------------------------------------------------

    private UUID existingUserId() {
        User user = newUser();
        return user.getId();
    }

    private static JwtClaimsSet.Builder claims(UUID subject, String issuer) {
        Instant now = Instant.now();
        return JwtClaimsSet.builder().subject(subject.toString()).issuer(issuer).issuedAt(now)
                .expiresAt(now.plusSeconds(600));
    }

    private static String sign(JwtEncoder encoder, JwtClaimsSet.Builder claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    private String loginFailure(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email.replace("\"", ""), password)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
    }
}
