package org.example.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionHistoryTest {
    private TransactionHistory history;

    @BeforeEach
    public void setUp() {
        history = new TransactionHistory();
    }

    private static Transaction tx(int n) {
        return new Transaction("id-" + n, "key-" + n, TransactionType.EXPENSE, "ana", null,
                new BigDecimal("1.00"), "tx " + n, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private void fill(int count) {
        IntStream.rangeClosed(1, count).forEach(i -> history.append(tx(i)));
    }

    private static List<String> ids(TransactionPage page) {
        return page.items().stream().map(Transaction::id).toList();
    }

    @Test
    public void shouldReturnEmptyFirstPageForEmptyHistory() {
        var page = history.page(1);
        assertTrue(page.items().isEmpty());
        assertEquals(1, page.page());
        assertFalse(page.hasNext());
    }

    @Test
    public void shouldPageTwentyFiveEntriesAsTenTenFiveNewestFirst() {
        fill(25);

        var p1 = history.page(1);
        var p2 = history.page(2);
        var p3 = history.page(3);

        assertEquals(IntStream.rangeClosed(16, 25).boxed().sorted((a, b) -> b - a).map(i -> "id-" + i).toList(), ids(p1));
        assertEquals(IntStream.rangeClosed(6, 15).boxed().sorted((a, b) -> b - a).map(i -> "id-" + i).toList(), ids(p2));
        assertEquals(List.of("id-5", "id-4", "id-3", "id-2", "id-1"), ids(p3));
        assertTrue(p1.hasNext());
        assertTrue(p2.hasNext());
        assertFalse(p3.hasNext());
        assertEquals(List.of(1, 2, 3), List.of(p1.page(), p2.page(), p3.page()));
    }

    @Test
    public void shouldReturnOneFullPageWithNoNextForExactlyTenEntries() {
        fill(10);
        var page = history.page(1);
        assertEquals(10, page.items().size());
        assertFalse(page.hasNext());
        assertTrue(history.page(2).items().isEmpty());
    }

    @Test
    public void shouldReturnEmptyPageBeyondTheEnd() {
        fill(3);
        var page = history.page(99);
        assertTrue(page.items().isEmpty());
        assertFalse(page.hasNext());
        assertEquals(99, page.page());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    public void shouldRejectNonPositivePage(int page) {
        assertThrows(IllegalArgumentException.class, () -> history.page(page));
    }

    @Test
    public void shouldOrderByInsertionNotByTimestamp() {
        var older = new Transaction("a", null, TransactionType.EXPENSE, "ana", null, BigDecimal.ONE, "a",
                Instant.parse("2026-01-02T00:00:00Z"));
        var clockWentBackwards = new Transaction("b", null, TransactionType.EXPENSE, "ana", null, BigDecimal.ONE, "b",
                Instant.parse("2026-01-01T00:00:00Z"));
        history.append(older);
        history.append(clockWentBackwards);

        assertEquals(List.of("b", "a"), ids(history.page(1)));
    }

    @Test
    public void shouldKeepSameInstantEntriesLaterRecordedFirst() {
        fill(3); // all share the same recordedAt
        assertEquals(List.of("id-3", "id-2", "id-1"), ids(history.page(1)));
    }

    // ---- Page cache (US4) ----

    @Test
    public void shouldServeRepeatedReadsFromTheSameImmutablePage() {
        fill(25);

        var first = history.page(2);
        var second = history.page(2);

        assertEquals(first, second);
        assertSame(first, second);
    }

    @Test
    public void shouldInvalidateCacheOnAppendSoNewEntryShowsFirst() {
        fill(10);
        var before = history.page(1);

        history.append(tx(11));
        var after = history.page(1);

        assertNotSame(before, after);
        assertEquals("id-11", after.items().getFirst().id());
        assertEquals("id-1", history.page(2).items().getFirst().id());
        assertTrue(after.hasNext());
    }

    @Test
    public void shouldNotKeepAnEmptyBeyondTheEndPageAfterEntriesAreAppended() {
        fill(3);
        assertTrue(history.page(5).items().isEmpty());

        IntStream.rangeClosed(4, 43).forEach(i -> history.append(tx(i)));

        assertEquals(3, history.page(5).items().size());
    }

    @Test
    public void shouldKeepAtMostOneHundredPagesEvictingLeastRecentlyUsed() {
        fill(1010); // 101 pages
        var firstRead = history.page(1);
        for (int p = 2; p <= 101; p++) {
            history.page(p);
        }
        var last = history.page(101);

        assertSame(last, history.page(101));   // the most recent page is still cached
        var reread = history.page(1);          // page 1 was the least recently used
        assertNotSame(firstRead, reread);      // so it was evicted and rebuilt
        assertEquals(firstRead, reread);       // with identical, correct content
        assertEquals("id-1010", reread.items().getFirst().id());
    }

    @Test
    public void shouldNeverCacheRejectedPages() {
        fill(5);
        assertThrows(IllegalArgumentException.class, () -> history.page(0));
        assertThrows(IllegalArgumentException.class, () -> history.page(0));
        assertEquals(5, history.page(1).items().size());
    }

    @Test
    public void shouldServeAnyPageOfTenThousandEntriesInUnderOneSecond() {
        fill(10_000);

        for (int page : new int[]{1, 500, 1000}) {
            var start = System.nanoTime();
            var result = history.page(page);
            var elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            assertEquals(10, result.items().size());
            assertTrue(elapsedMillis < 1000, "page " + page + " took " + elapsedMillis + " ms");
        }
    }

    @Test
    public void shouldPageCorrectlyThroughTenThousandAndOneEntries() {
        fill(10_001);

        assertEquals("id-10001", history.page(1).items().getFirst().id());
        assertEquals(10, history.page(500).items().size());
        var last = history.page(1001);
        assertEquals(List.of("id-1"), ids(last));
        assertFalse(last.hasNext());
        assertTrue(history.page(1000).hasNext());
    }
}
