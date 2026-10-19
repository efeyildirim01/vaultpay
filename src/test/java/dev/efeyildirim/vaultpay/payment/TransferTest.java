package dev.efeyildirim.vaultpay.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.efeyildirim.vaultpay.IntegrationTest;
import dev.efeyildirim.vaultpay.audit.AuditLogRepository;
import dev.efeyildirim.vaultpay.common.BusinessRuleException;
import dev.efeyildirim.vaultpay.common.NotFoundException;
import dev.efeyildirim.vaultpay.ledger.InsufficientFundsException;
import dev.efeyildirim.vaultpay.ledger.JournalEntryType;
import dev.efeyildirim.vaultpay.ledger.Posting;
import dev.efeyildirim.vaultpay.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TransferTest extends IntegrationTest {

    @Autowired
    AuditLogRepository auditLogs;

    @Test
    void depositThenTransferMovesMoneyWithBalancedPostings() {
        User alice = newUser();
        UUID current = openAccount(alice);
        UUID savings = openAccount(alice);

        MovementResult deposit = paymentService.deposit(alice.getId(), newKey(), current, 10_000, "salary");
        MovementResult transfer = paymentService.transfer(alice.getId(), newKey(), current, savings, 2_500, "save");

        assertThat(deposit.type()).isEqualTo(JournalEntryType.DEPOSIT);
        assertThat(transfer.type()).isEqualTo(JournalEntryType.TRANSFER);
        assertThat(transfer.replayed()).isFalse();
        assertThat(balance(current)).isEqualTo(7_500);
        assertThat(balance(savings)).isEqualTo(2_500);

        List<Posting> legs = ledgerService.postingsOf(transfer.journalEntryId());
        assertThat(legs).extracting(Posting::getAmountMinor).containsExactlyInAnyOrder(-2_500L, 2_500L);
        assertThat(auditLogs.findByEntityTypeAndEntityId("JOURNAL_ENTRY", transfer.journalEntryId()))
                .singleElement()
                .satisfies(log -> assertThat(log.getAction()).isEqualTo("TRANSFER_POSTED"));
    }

    @Test
    void transferBetweenDifferentCustomersIsAllowed() {
        User alice = newUser();
        User bob = newUser();
        UUID aliceAccount = openAccount(alice);
        UUID bobAccount = openAccount(bob);
        fund(alice, aliceAccount, 5_000);

        paymentService.transfer(alice.getId(), newKey(), aliceAccount, bobAccount, 1_234, "rent");

        assertThat(balance(aliceAccount)).isEqualTo(3_766);
        assertThat(balance(bobAccount)).isEqualTo(1_234);
    }

    @Test
    void overdraftIsRejectedAndNothingIsPosted() {
        User alice = newUser();
        UUID from = openAccount(alice);
        UUID to = openAccount(alice);
        fund(alice, from, 1_000);

        assertThatThrownBy(() -> paymentService.transfer(alice.getId(), newKey(), from, to, 1_001, null))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balance(from)).isEqualTo(1_000);
        assertThat(balance(to)).isZero();
    }

    @Test
    void cannotSpendFromSomeoneElsesAccount() {
        User alice = newUser();
        User mallory = newUser();
        UUID aliceAccount = openAccount(alice);
        UUID malloryAccount = openAccount(mallory);
        fund(alice, aliceAccount, 1_000);

        assertThatThrownBy(() -> paymentService.transfer(mallory.getId(), newKey(), aliceAccount, malloryAccount, 500, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> paymentService.deposit(mallory.getId(), newKey(), aliceAccount, 500, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(balance(aliceAccount)).isEqualTo(1_000);
    }

    @Test
    void rejectsSameAccountAndNonPositiveAmounts() {
        User alice = newUser();
        UUID account = openAccount(alice);
        UUID other = openAccount(alice);
        fund(alice, account, 1_000);

        assertThatThrownBy(() -> paymentService.transfer(alice.getId(), newKey(), account, account, 100, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> paymentService.transfer(alice.getId(), newKey(), account, other, 0, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> paymentService.transfer(alice.getId(), newKey(), account, other, -5, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unknownAccountIsNotFound() {
        User alice = newUser();
        UUID account = openAccount(alice);

        assertThatThrownBy(() -> paymentService.transfer(alice.getId(), newKey(), account, UUID.randomUUID(), 100, null))
                .isInstanceOf(NotFoundException.class);
    }
}
