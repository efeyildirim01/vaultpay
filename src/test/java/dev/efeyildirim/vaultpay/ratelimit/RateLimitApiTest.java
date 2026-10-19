package dev.efeyildirim.vaultpay.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.efeyildirim.vaultpay.IntegrationTest;
import dev.efeyildirim.vaultpay.auth.TokenService;
import dev.efeyildirim.vaultpay.user.User;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Low limits and a one-day window (so a test never straddles two windows). Every test uses fresh
 * users and its own client IP, so tests do not eat into each other's budget.
 */
@SpringBootTest(properties = {
        "vaultpay.rate-limit.window=PT24H",
        "vaultpay.rate-limit.login-per-window=3",
        "vaultpay.rate-limit.money-per-window=2"})
@AutoConfigureMockMvc
class RateLimitApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TokenService tokens;

    @Test
    void loginIsLimitedPerIpEvenForWrongPasswords() throws Exception {
        String ip = randomIp();
        User alice = newUser();

        for (int i = 0; i < 3; i++) {
            mvc.perform(login(alice.getEmail(), "wrong-password-123", ip)).andExpect(status().isUnauthorized());
        }
        // The 4th guess is not even checked, so the right password does not help an attacker either.
        mvc.perform(login(alice.getEmail(), "correct-horse-battery", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        mvc.perform(login(alice.getEmail(), "correct-horse-battery", randomIp())).andExpect(status().isOk());
    }

    @Test
    void moneyMovementIsLimitedPerUserAndRejectedRequestsMoveNoMoney() throws Exception {
        User alice = newUser();
        User bob = newUser();
        UUID aliceAccount = openAccount(alice);
        UUID bobAccount = openAccount(bob);

        mvc.perform(deposit(alice, aliceAccount)).andExpect(status().isCreated());
        mvc.perform(deposit(alice, aliceAccount)).andExpect(status().isCreated());
        mvc.perform(deposit(alice, aliceAccount))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));

        assertThat(balance(aliceAccount)).isEqualTo(200);
        // Bob has his own budget.
        mvc.perform(deposit(bob, bobAccount)).andExpect(status().isCreated());
    }

    private MockHttpServletRequestBuilder login(String email, String password, String ip) {
        return post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password));
    }

    private MockHttpServletRequestBuilder deposit(User user, UUID accountId) {
        return post("/api/accounts/" + accountId + "/deposits")
                .header("Authorization", "Bearer " + tokens.issue(user))
                .header("Idempotency-Key", newKey())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountMinor\": 100}");
    }

    private static String randomIp() {
        UUID u = UUID.randomUUID();
        return "10." + (u.hashCode() & 0xff) + "." + ((u.hashCode() >> 8) & 0xff) + "." + ((u.hashCode() >> 16) & 0xff);
    }
}
