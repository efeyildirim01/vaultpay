package dev.efeyildirim.vaultpay;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.efeyildirim.vaultpay.user.UserRepository;
import dev.efeyildirim.vaultpay.user.UserRole;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HTTP-level helpers: sign up, log in with a given role, open accounts and move money over the real API. */
@AutoConfigureMockMvc
public abstract class ApiTestSupport extends IntegrationTest {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected UserRepository userRepository;

    protected String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s", "fullName": "Test User"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated());
    }

    protected String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    protected String customerToken() throws Exception {
        String email = newEmail();
        register(email);
        return login(email);
    }

    /** Staff roles have no sign-up endpoint: register normally, grant the role in the service, then log in. */
    protected String tokenWithRole(UserRole role) throws Exception {
        String email = newEmail();
        register(email);
        userService.grantRole(null, userRepository.findByEmail(email).orElseThrow().getId(), role);
        return login(email);
    }

    protected String openAccountOverHttp(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"currency\": \"GBP\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    protected ResultActions postJson(String url, String token, String idempotencyKey, String json) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected ResultActions getWithToken(String url, String token) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    protected void depositOverHttp(String token, String accountId, long amountMinor) throws Exception {
        postJson("/api/accounts/" + accountId + "/deposits", token, newKey(),
                "{\"amountMinor\": %d, \"reference\": \"Salary\"}".formatted(amountMinor))
                .andExpect(status().isCreated());
    }

    protected void transferOverHttp(String token, String from, String to, long amountMinor, String reference)
            throws Exception {
        postJson("/api/transfers", token, newKey(), """
                {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": %d, "reference": "%s"}"""
                .formatted(from, to, amountMinor, reference))
                .andExpect(status().isCreated());
    }
}
