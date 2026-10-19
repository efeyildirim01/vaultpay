package dev.efeyildirim.vaultpay.payment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.efeyildirim.vaultpay.IntegrationTest;
import dev.efeyildirim.vaultpay.ledger.InsufficientFundsException;
import dev.efeyildirim.vaultpay.user.User;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * These tests fail without the row locks: two transactions would both read the old balance,
 * both pass the overdraft check, and both post (a classic lost update / double spend).
 */
class ConcurrentTransferTest extends IntegrationTest {

    @Test
    void concurrentTransfersNeverOverdraw() throws Exception {
        User alice = newUser();
        UUID from = openAccount(alice);
        UUID to = openAccount(alice);
        fund(alice, from, 10_000); // £100.00

        // 50 simultaneous £5 transfers against £100: exactly 20 can succeed.
        List<Future<MovementResult>> results = runConcurrently(50,
                i -> () -> paymentService.transfer(alice.getId(), newKey(), from, to, 500, "t" + i));

        int succeeded = 0;
        int insufficient = 0;
        for (Future<MovementResult> result : results) {
            try {
                result.get();
                succeeded++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(InsufficientFundsException.class);
                insufficient++;
            }
        }

        assertThat(succeeded).isEqualTo(20);
        assertThat(insufficient).isEqualTo(30);
        assertThat(balance(from)).isZero();
        assertThat(balance(to)).isEqualTo(10_000);
    }

    @Test
    void opposingTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        User alice = newUser();
        User bob = newUser();
        UUID a = openAccount(alice);
        UUID b = openAccount(bob);
        fund(alice, a, 5_000);
        fund(bob, b, 5_000);

        // Half the threads send A->B, half B->A. Locking in id order prevents a lock cycle.
        List<Future<MovementResult>> results = runConcurrently(40, i -> i % 2 == 0
                ? () -> paymentService.transfer(alice.getId(), newKey(), a, b, 100, null)
                : () -> paymentService.transfer(bob.getId(), newKey(), b, a, 100, null));

        for (Future<MovementResult> result : results) {
            result.get(); // all succeed: no deadlock victims, no insufficient funds
        }
        assertThat(balance(a)).isEqualTo(5_000);
        assertThat(balance(b)).isEqualTo(5_000);
    }

    @Test
    void concurrentDepositsAreAllCounted() throws Exception {
        User alice = newUser();
        UUID account = openAccount(alice);

        List<Future<MovementResult>> results = runConcurrently(25,
                i -> () -> paymentService.deposit(alice.getId(), newKey(), account, 100, null));
        for (Future<MovementResult> result : results) {
            result.get();
        }

        assertThat(balance(account)).isEqualTo(2_500); // no lost updates
    }
}
