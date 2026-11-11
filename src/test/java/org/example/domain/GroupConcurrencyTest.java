package org.example.domain;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.example.domain.GroupTestHelper.calculateTotal;
import static org.junit.jupiter.api.Assertions.*;

public class GroupConcurrencyTest {
    private static final int THREADS = 10;
    private static final int TOTAL = 1000;

    private Group newGroup(TestClock clock) {
        var group = new Group("g", "Concurrent", "history", clock);
        group.addMember(new Member("1", "One", ""));
        group.addMember(new Member("2", "Two", ""));
        group.addMember(new Member("3", "Three", ""));
        return group;
    }

    @RepeatedTest(5)
    public void shouldRecordExactlyOneThousandDistinctEntriesFromTenThreads() throws Exception {
        var group = newGroup(TestClock.stepping(Duration.ofMillis(1)));
        var pool = Executors.newFixedThreadPool(THREADS);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();

        for (int t = 0; t < THREADS; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < TOTAL / THREADS; j++) {
                    group.addExpense("1", new BigDecimal("0.03"), "k-" + thread + "-" + j);
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        var ids = new HashSet<String>();
        var keys = new HashSet<String>();
        int pages = 0;
        int page = 1;
        TransactionPage current;
        do {
            current = group.getTransactionPage(page++);
            pages++;
            for (var tx : current.items()) {
                ids.add(tx.id());
                keys.add(tx.idempotencyKey());
            }
        } while (current.hasNext());

        assertEquals(TOTAL / 10, pages);
        assertEquals(TOTAL, ids.size());
        assertEquals(TOTAL, keys.size());
        assertEquals(0, group.getExpenseForMember("1").compareTo(new BigDecimal("20.00")));
        assertEquals(0, group.getExpenseForMember("2").compareTo(new BigDecimal("-10.00")));
        assertEquals(0, group.getExpenseForMember("3").compareTo(new BigDecimal("-10.00")));
        assertEquals(0, calculateTotal(group).compareTo(BigDecimal.ZERO));
    }

    @Test
    public void shouldReturnConsistentPagesWhileWritersRun() throws Exception {
        var group = newGroup(TestClock.stepping(Duration.ofMillis(1)));
        var pool = Executors.newFixedThreadPool(THREADS + 2);
        var start = new CountDownLatch(1);
        var writersDone = new AtomicBoolean(false);
        var inconsistent = new AtomicInteger();
        var reads = new AtomicInteger();
        var futures = new ArrayList<Future<?>>();

        for (int t = 0; t < THREADS; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < TOTAL / THREADS; j++) {
                    group.addExpense("1", new BigDecimal("0.03"), "k-" + thread + "-" + j);
                }
                return null;
            }));
        }
        var readers = new ArrayList<Future<?>>();
        for (int r = 0; r < 2; r++) {
            readers.add(pool.submit(() -> {
                start.await();
                while (!writersDone.get()) {
                    var page = group.getTransactionPage(1);
                    reads.incrementAndGet();
                    var items = page.items();
                    boolean ok = items.size() <= 10;
                    for (int i = 1; i < items.size(); i++) {
                        ok &= items.get(i - 1).recordedAt().isAfter(items.get(i).recordedAt());
                    }
                    for (var tx : items) {
                        ok &= tx.id() != null && tx.idempotencyKey() != null && tx.payerId() != null;
                    }
                    if (!ok) inconsistent.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (var f : futures) f.get(10, TimeUnit.SECONDS);
        writersDone.set(true);
        for (var f : readers) f.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertTrue(reads.get() > 0);
        assertEquals(0, inconsistent.get());
        assertEquals(10, group.getTransactionPage(1).items().size());
    }
}
