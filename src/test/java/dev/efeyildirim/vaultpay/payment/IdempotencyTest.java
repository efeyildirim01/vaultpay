package dev.efeyildirim.vaultpay.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.efeyildirim.vaultpay.IntegrationTest;
import dev.efeyildirim.vaultpay.user.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class IdempotencyTest extends IntegrationTest {

    @Test
    void retryWithSameKeyReturnsOriginalResultAndMovesMoneyOnce() {
        User alice = newUser();
        UUID from = openAccount(alice);
        UUID to = openAccount(alice);
        fund(alice, from, 10_000);
        String key = newKey();

        MovementResult first = paymentService.transfer(alice.getId(), key, from, to, 3_000, "rent");
        MovementResult retry = paymentService.transfer(alice.getId(), key, from, to, 3_000, "rent");

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.journalEntryId()).isEqualTo(first.journalEntryId());
        assertThat(balance(from)).isEqualTo(7_000);
        assertThat(balance(to)).isEqualTo(3_000);
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() {
        User alice = newUser();
        UUID from = openAccount(alice);
        UUID to = openAccount(alice);
        fund(alice, from, 10_000);
        String key = newKey();

        paymentService.transfer(alice.getId(), key, from, to, 3_000, "rent");

        assertThatThrownBy(() -> paymentService.transfer(alice.getId(), key, from, to, 4_000, "rent"))
                .isInstanceOf(IdempotencyConflictException.class);
        assertThat(balance(from)).isEqualTo(7_000);
    }

    @Test
    void keysAreScopedPerUser() {
        User alice = newUser();
        User bob = newUser();
        UUID aliceAccount = openAccount(alice);
        UUID bobAccount = openAccount(bob);
        String sharedKey = newKey();

        paymentService.deposit(alice.getId(), sharedKey, aliceAccount, 100, null);
        MovementResult bobs = paymentService.deposit(bob.getId(), sharedKey, bobAccount, 100, null);

        assertThat(bobs.replayed()).isFalse();
        assertThat(balance(bobAccount)).isEqualTo(100);
    }

    @Test
    void concurrentRetriesWithSameKeyPostExactlyOnce() throws Exception {
        User alice = newUser();
        UUID from = openAccount(alice);
        UUID to = openAccount(alice);
        fund(alice, from, 10_000);
        String key = newKey();

        // A client that times out and fires the same request 10 times at once.
        List<Future<MovementResult>> results = runConcurrently(10,
                i -> () -> paymentService.transfer(alice.getId(), key, from, to, 1_000, "retry storm"));

        Set<UUID> journalIds = new HashSet<>();
        long originals = 0;
        for (Future<MovementResult> f : results) {
            MovementResult r = f.get();
            journalIds.add(r.journalEntryId());
            if (!r.replayed()) {
                originals++;
            }
        }
        assertThat(journalIds).hasSize(1);
        assertThat(originals).isEqualTo(1);
        assertThat(balance(from)).isEqualTo(9_000);
        assertThat(balance(to)).isEqualTo(1_000);
    }

    @Test
    void depositRetryIsIdempotentToo() {
        User alice = newUser();
        UUID account = openAccount(alice);
        String key = newKey();

        Set<UUID> ids = Stream.of(1, 2, 3)
                .map(i -> paymentService.deposit(alice.getId(), key, account, 500, "top-up").journalEntryId())
                .collect(Collectors.toSet());

        assertThat(ids).hasSize(1);
        assertThat(balance(account)).isEqualTo(500);
    }
}
