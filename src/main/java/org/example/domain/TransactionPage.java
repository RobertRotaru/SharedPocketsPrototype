package org.example.domain;

import java.util.List;

/**
 * One page of a group's history, newest first. A page beyond the last has empty items and
 * hasNext false.
 */
public record TransactionPage(List<Transaction> items, int page, boolean hasNext) {
    public TransactionPage {
        items = List.copyOf(items);
    }
}
