package dev.efeyildirim.vaultpay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.efeyildirim.vaultpay.account.AccountService;
import dev.efeyildirim.vaultpay.ledger.LedgerService;
import dev.efeyildirim.vaultpay.ledger.PostingRepository;
import dev.efeyildirim.vaultpay.payment.PaymentService;
import dev.efeyildirim.vaultpay.user.User;
import dev.efeyildirim.vaultpay.user.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base for tests that need the full application and a real database (H2 locally, PostgreSQL in CI).
 * Not @Transactional on purpose: concurrency tests need real commits from several threads.
 * Every test creates its own users, so tests never see each other's data.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected UserService userService;
    @Autowired
    protected AccountService accountService;
    @Autowired
    protected PaymentService paymentService;
    @Autowired
    protected LedgerService ledgerService;
    @Autowired
    protected PostingRepository postingRepository;

    /** The double-entry invariant, checked after every single test. */
    @AfterEach
    void ledgerAlwaysBalances() {
        assertThat(postingRepository.findUnbalancedJournalEntries()).isEmpty();
        assertThat(postingRepository.sumOfAllPostings()).isZero();
    }

    protected User newUser() {
        return userService.register("user-" + UUID.randomUUID() + "@example.com", "correct-horse-battery", "Test User");
    }

    protected UUID openAccount(User user) {
        return accountService.open(user.getId(), "Current", "GBP").id();
    }

    protected void fund(User user, UUID accountId, long amountMinor) {
        paymentService.deposit(user.getId(), newKey(), accountId, amountMinor, "test funding");
    }

    protected long balance(UUID accountId) {
        return ledgerService.balanceOf(accountId);
    }

    protected static String newKey() {
        return UUID.randomUUID().toString();
    }

    /** Runs n tasks that all start at the same instant (released by one latch) and waits for them. */
    protected static <T> List<Future<T>> runConcurrently(int n, IntFunction<Callable<T>> task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Callable<T> callable = task.apply(i);
            futures.add(pool.submit(() -> {
                start.await();
                return callable.call();
            }));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).as("tasks finished (no deadlock)").isTrue();
        return futures;
    }
}
