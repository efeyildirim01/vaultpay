package dev.efeyildirim.vaultpay.account;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.efeyildirim.vaultpay.ApiTestSupport;
import org.junit.jupiter.api.Test;

class TransactionHistoryTest extends ApiTestSupport {

    @Test
    void statementListsEveryMovementNewestFirstWithCounterparty() throws Exception {
        String alice = customerToken();
        String bob = customerToken();
        String current = openAccountOverHttp(alice, "Current");
        String savings = openAccountOverHttp(alice, "Savings");
        String bobAccount = openAccountOverHttp(bob, "Bob");

        depositOverHttp(alice, current, 10_000);
        transferOverHttp(alice, current, savings, 2_500, "Saving");
        transferOverHttp(alice, current, bobAccount, 1_000, "Dinner");

        getWithToken("/api/accounts/" + current + "/transactions", alice)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.items[0].amountMinor").value(-1000))
                .andExpect(jsonPath("$.items[0].counterpartyAccountId").value(bobAccount))
                .andExpect(jsonPath("$.items[0].reference").value("Dinner"))
                .andExpect(jsonPath("$.items[1].amountMinor").value(-2500))
                .andExpect(jsonPath("$.items[1].counterpartyAccountId").value(savings))
                .andExpect(jsonPath("$.items[2].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.items[2].amountMinor").value(10000))
                .andExpect(jsonPath("$.items[2].counterpartyLabel").value("External funding (simulated)"));

        // The receiving side sees the same movement as money in.
        getWithToken("/api/accounts/" + bobAccount + "/transactions", bob)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].amountMinor").value(1000))
                .andExpect(jsonPath("$.items[0].counterpartyAccountId").value(current));
    }

    @Test
    void statementIsPaged() throws Exception {
        String alice = customerToken();
        String account = openAccountOverHttp(alice, "Current");
        depositOverHttp(alice, account, 100);
        depositOverHttp(alice, account, 200);
        depositOverHttp(alice, account, 300);

        getWithToken("/api/accounts/" + account + "/transactions?size=2", alice)
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(true));
        getWithToken("/api/accounts/" + account + "/transactions?size=2&page=1", alice)
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void cannotReadSomeoneElsesStatement() throws Exception {
        String alice = customerToken();
        String mallory = customerToken();
        String account = openAccountOverHttp(alice, "Current");

        getWithToken("/api/accounts/" + account + "/transactions", mallory).andExpect(status().isNotFound());
    }

    @Test
    void rejectsOversizedPages() throws Exception {
        String alice = customerToken();
        String account = openAccountOverHttp(alice, "Current");

        getWithToken("/api/accounts/" + account + "/transactions?size=500", alice).andExpect(status().isBadRequest());
    }
}
