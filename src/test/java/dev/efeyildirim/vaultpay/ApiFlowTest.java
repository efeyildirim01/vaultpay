package dev.efeyildirim.vaultpay;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.efeyildirim.vaultpay.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** The first vertical slice over HTTP: register, log in, open two accounts, deposit, transfer. */
@AutoConfigureMockMvc
class ApiFlowTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository userRepository;

    @Test
    void registerLoginOpenAccountsDepositAndTransfer() throws Exception {
        String token = registerAndLogin();
        String current = openAccountOverHttp(token, "Current");
        String savings = openAccountOverHttp(token, "Savings");

        postJson("/api/accounts/" + current + "/deposits", token, "dep-0000001", """
                {"amountMinor": 10000, "reference": "Simulated salary"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"));

        String transferBody = """
                {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": 2500, "reference": "Savings"}"""
                .formatted(current, savings);
        String journalId = JsonPath.read(postJson("/api/transfers", token, "xfer-0000001", transferBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replayed").value(false))
                .andReturn().getResponse().getContentAsString(), "$.journalEntryId");

        // The client retries (e.g. after a timeout): same key, same body -> same result, no new money movement.
        postJson("/api/transfers", token, "xfer-0000001", transferBody)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.journalEntryId").value(journalId));

        mvc.perform(get("/api/accounts/" + current).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(7500));
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].balanceMinor").value(2500));
    }

    @Test
    void overdraftReturns422() throws Exception {
        String token = registerAndLogin();
        String a = openAccountOverHttp(token, "A");
        String b = openAccountOverHttp(token, "B");

        postJson("/api/transfers", token, newKey(), """
                {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": 1}""".formatted(a, b))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Insufficient funds"));
    }

    @Test
    void fraudBlockedTransferReturns422WithAssessmentIdButNoReasons() throws Exception {
        String aliceToken = registerAndLogin();
        String bobToken = registerAndLogin();
        String alice = openAccountOverHttp(aliceToken, "Alice");
        String bob = openAccountOverHttp(bobToken, "Bob");
        postJson("/api/accounts/" + alice + "/deposits", aliceToken, newKey(), "{\"amountMinor\": 200000}")
                .andExpect(status().isCreated());

        postJson("/api/transfers", aliceToken, newKey(), """
                {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": 190000}""".formatted(alice, bob))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Transfer blocked by fraud screening"))
                .andExpect(jsonPath("$.assessmentId").isNotEmpty())
                .andExpect(jsonPath("$.reasons").doesNotExist());
    }

    @Test
    void reviewQueueIsForReviewersOnlyAndConfirmedFraudFreezesTheAccount() throws Exception {
        String aliceToken = registerAndLogin();
        String bobToken = registerAndLogin();
        String alice = openAccountOverHttp(aliceToken, "Alice");
        String bob = openAccountOverHttp(bobToken, "Bob");
        postJson("/api/accounts/" + alice + "/deposits", aliceToken, newKey(), "{\"amountMinor\": 10000}")
                .andExpect(status().isCreated());
        // £60 of a fresh £100 deposit to a new payee: scored REVIEW, so it still goes through.
        String journalId = JsonPath.read(postJson("/api/transfers", aliceToken, newKey(), """
                        {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": 6000}""".formatted(alice, bob))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.journalEntryId");

        mvc.perform(get("/api/fraud/reviews/pending").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isForbidden());

        String reviewer = reviewerToken();
        String pending = mvc.perform(get("/api/fraud/reviews/pending").header("Authorization", "Bearer " + reviewer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(pending,
                "$[?(@.journalEntryId == '" + journalId + "')].assessmentId");
        String assessmentId = ids.getFirst();

        postJson("/api/fraud/assessments/" + assessmentId + "/review", reviewer, newKey(),
                "{\"outcome\": \"CONFIRMED_FRAUD\", \"note\": \"mule account\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountFrozen").value(true));

        mvc.perform(get("/api/accounts/" + alice).header("Authorization", "Bearer " + aliceToken))
                .andExpect(jsonPath("$.status").value("FROZEN"));
        postJson("/api/transfers", aliceToken, newKey(), """
                {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": 100}""".formatted(alice, bob))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Account is not active"));
    }

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotSeeAnotherUsersAccount() throws Exception {
        String aliceToken = registerAndLogin();
        String malloryToken = registerAndLogin();
        String aliceAccount = openAccountOverHttp(aliceToken, "Alice");

        mvc.perform(get("/api/accounts/" + aliceAccount).header("Authorization", "Bearer " + malloryToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void moneyMovementRequiresAValidIdempotencyKey() throws Exception {
        String token = registerAndLogin();
        String account = openAccountOverHttp(token, "Current");

        mvc.perform(post("/api/accounts/" + account + "/deposits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\": 100}"))
                .andExpect(status().isBadRequest());
        postJson("/api/accounts/" + account + "/deposits", token, "bad key!", "{\"amountMinor\": 100}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        register(email);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"wrong-password-123\"}".formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        register(email);
        return login(email);
    }

    /** Reviewers have no sign-up endpoint: register normally, promote in the service, then log in. */
    private String reviewerToken() throws Exception {
        String email = "reviewer-" + UUID.randomUUID() + "@example.com";
        register(email);
        userService.promoteToReviewer(null, userRepository.findByEmail(email).orElseThrow().getId());
        return login(email);
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"correct-horse-battery\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "correct-horse-battery", "fullName": "Test User"}"""
                        .formatted(email)))
                .andExpect(status().isCreated());
    }

    private String openAccountOverHttp(String token, String name) throws Exception {
        String body = mvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"%s\", \"currency\": \"GBP\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions postJson(String url, String token, String idempotencyKey, String json) throws Exception {
        return mvc.perform(post(url)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
