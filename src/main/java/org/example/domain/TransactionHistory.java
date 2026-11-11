package org.example.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Append-only, newest-first history of a group's transactions, with a small page cache.
 *
 * <p>NOT thread-safe by itself: the owning {@link Group} MUST hold its lock around every call.
 * That is also what makes the cache safe: it is cleared by every {@link #append}, and reads and
 * writes never interleave, so a cached page always equals a freshly built one.
 *
 * <p>Order is the order of recording (the insertion sequence), never the timestamp, so same-instant
 * entries and clock adjustments cannot reorder it.
 */
public class TransactionHistory {
    public static final int PAGE_SIZE = 10;
    static final int MAX_CACHED_PAGES = 100;

    private final List<Transaction> entries = new ArrayList<>();

    /** Access-ordered, so the least recently used page is evicted first. */
    private final LinkedHashMap<Integer, TransactionPage> pageCache =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, TransactionPage> eldest) {
                    return size() > MAX_CACHED_PAGES;
                }
            };

    public void append(Transaction transaction) {
        entries.add(transaction);
        pageCache.clear();
    }

    public TransactionPage page(int page) {
        if (page < 1) {
            throw new IllegalArgumentException("Page must be a positive whole number.");
        }
        var cached = pageCache.get(page);
        if (cached != null) {
            return cached;
        }
        var built = buildPage(page);
        pageCache.put(page, built);
        return built;
    }

    private TransactionPage buildPage(int page) {
        long end = (long) entries.size() - (long) PAGE_SIZE * (page - 1L);
        if (end <= 0) {
            return new TransactionPage(List.of(), page, false);
        }
        int to = (int) end;
        int from = Math.max(0, to - PAGE_SIZE);
        var items = new ArrayList<Transaction>(to - from);
        for (int i = to - 1; i >= from; i--) {
            items.add(entries.get(i));
        }
        return new TransactionPage(items, page, from > 0);
    }
}
