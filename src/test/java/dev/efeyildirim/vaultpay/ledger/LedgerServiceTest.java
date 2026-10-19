package dev.efeyildirim.vaultpay.ledger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.efeyildirim.vaultpay.IntegrationTest;
import dev.efeyildirim.vaultpay.ledger.LedgerService.Leg;
import dev.efeyildirim.vaultpay.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class LedgerServiceTest extends IntegrationTest {

    @Autowired
    TransactionTemplate tx;

    @Test
    void rejectsUnbalancedEntry() {
        User alice = newUser();
        UUID a = openAccount(alice);
        UUID b = openAccount(alice);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ledgerService.post(JournalEntryType.TRANSFER,
                alice.getId(), newKey(), "hash", null, "GBP", List.of(Leg.debit(a, 100), Leg.credit(b, 99)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not balance");
    }

    @Test
    void rejectsSingleLegAndZeroLegs() {
        User alice = newUser();
        UUID a = openAccount(alice);
        UUID b = openAccount(alice);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ledgerService.post(JournalEntryType.DEPOSIT,
                alice.getId(), newKey(), "hash", null, "GBP", List.of(Leg.credit(a, 100)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ledgerService.post(JournalEntryType.TRANSFER,
                alice.getId(), newKey(), "hash", null, "GBP", List.of(Leg.debit(a, 0), Leg.credit(b, 0)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotWriteToLedgerOutsideATransaction() {
        User alice = newUser();
        UUID a = openAccount(alice);
        UUID b = openAccount(alice);

        assertThatThrownBy(() -> ledgerService.post(JournalEntryType.TRANSFER, alice.getId(), newKey(), "hash",
                null, "GBP", List.of(Leg.debit(a, 1), Leg.credit(b, 1))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
