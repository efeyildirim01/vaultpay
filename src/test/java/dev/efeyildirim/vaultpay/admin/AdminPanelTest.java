package dev.efeyildirim.vaultpay.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.efeyildirim.vaultpay.ApiTestSupport;
import dev.efeyildirim.vaultpay.user.UserRole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminPanelTest extends ApiTestSupport {

    @Test
    void onlyAdminsReachTheAdminPanel() throws Exception {
        getWithToken("/api/admin/overview", customerToken()).andExpect(status().isForbidden());
        getWithToken("/api/admin/audit", tokenWithRole(UserRole.REVIEWER)).andExpect(status().isForbidden());

        String admin = tokenWithRole(UserRole.ADMIN);
        getWithToken("/api/admin/overview", admin).andExpect(status().isOk());
        // Admins can also work the fraud queue.
        getWithToken("/api/fraud/reviews/pending", admin).andExpect(status().isOk());
    }

    @Test
    void overviewReportsTheLedgerAsBalanced() throws Exception {
        String alice = customerToken();
        String account = openAccountOverHttp(alice, "Current");
        depositOverHttp(alice, account, 5_000);

        getWithToken("/api/admin/overview", tokenWithRole(UserRole.ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ledgerBalanced").value(true))
                .andExpect(jsonPath("$.customerMoneyMinor").isNumber())
                .andExpect(jsonPath("$.users").isNumber());
    }

    @Test
    void auditLogShowsWhoDidWhatAndCanBeFiltered() throws Exception {
        String email = newEmail();
        register(email);
        String alice = login(email);
        String from = openAccountOverHttp(alice, "Current");
        String to = openAccountOverHttp(alice, "Savings");
        depositOverHttp(alice, from, 5_000);
        transferOverHttp(alice, from, to, 1_000, "Audit me");

        String body = getWithToken("/api/admin/audit?action=TRANSFER_POSTED&size=200", tokenWithRole(UserRole.ADMIN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> items = JsonPath.read(body, "$.items");
        assertThat(items).allSatisfy(item -> assertThat(item.get("action")).isEqualTo("TRANSFER_POSTED"));
        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("actorEmail")).isEqualTo(email);
            assertThat((String) item.get("details")).contains("from=" + from).contains("to=" + to);
        });
        List<String> actions = JsonPath.read(body, "$.actions");
        assertThat(actions).contains("USER_REGISTERED", "ACCOUNT_OPENED", "DEPOSIT_POSTED", "TRANSFER_POSTED");
    }
}
