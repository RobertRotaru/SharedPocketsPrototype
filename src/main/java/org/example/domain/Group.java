package org.example.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Group {
    public static final int MAX_DESCRIPTION_LENGTH = 200;

    public String getGid() {
        return gid;
    }

    private final String gid;
    private final String name;
    private final String description;

    private final LinkedHashMap<String, Member> members;
    private final LinkedHashMap<String, BigDecimal> balances;
    private final HashMap<String, String> transactions = new HashMap<>();
    private final TransactionHistory history = new TransactionHistory();
    private final Clock clock;

    public Group(String gid, String name, String description) {
        this(gid, name, description, Clock.systemUTC());
    }

    public Group(String gid, String name, String description, Clock clock) {
        this.clock = clock;
        this.gid = gid;
        this.name = name;
        this.description = description;
        this.members = new LinkedHashMap<>();
        this.balances = new LinkedHashMap<>();
    }

    public synchronized void addMember(Member member) {
        if (this.members.containsKey(member.uid())) {
            throw new ConflictException("Member with uid " + member.uid() + " already exists in the group.");
        }
        this.members.put(member.uid(), member);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("Amount must have at most two decimal places.");
        }
    }

    /** Keyless expense (legacy). Applies the expense and records a history entry without a key. */
    public synchronized void addExpense(String uid, BigDecimal amount) {
        applyExpense(uid, amount, null, null);
    }

    public synchronized void addExpense(String uid, BigDecimal amount, String transactionId) {
        addExpense(uid, amount, transactionId, null);
    }

    /**
     * Idempotent expense with an optional description (null means omitted). Order of checks, kept
     * identical to the pre-description behavior: normalize the description (needed for the
     * fingerprint), replay or conflict, member and amount validation, apply and record.
     */
    public synchronized void addExpense(String uid, BigDecimal amount, String idempotencyKey, String description) {
        var text = normalizeDescription(description);
        var fingerprint = "EXPENSE|" + uid + "|" + amount.stripTrailingZeros().toPlainString()
                + (text == null ? "" : "|" + text);
        if (replayOrConflict(idempotencyKey, fingerprint)) {
            return;
        }
        applyExpense(uid, amount, idempotencyKey, text);
        this.transactions.put(idempotencyKey, fingerprint);
    }

    /** Null (omitted) stays null and later becomes the default text; otherwise trimmed, 1 to 200 characters. */
    private static String normalizeDescription(String raw) {
        if (raw == null) {
            return null;
        }
        var trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Description must be 1 to " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return trimmed;
    }

    private void applyExpense(String uid, BigDecimal amount, String idempotencyKey, String description) {
        if (!this.members.containsKey(uid)) {
            throw new IllegalArgumentException("Member with uid " + uid + " does not exist in the group.");
        }
        validateAmount(amount);

        var cent = new BigDecimal("0.01");
        var total = amount.setScale(2, RoundingMode.UNNECESSARY);
        var count = BigDecimal.valueOf(this.members.size());
        var share = total.divide(count, 2, RoundingMode.DOWN);
        var extraCents = total.subtract(share.multiply(count)).divide(cent).intValueExact();

        this.balances.merge(uid, total, BigDecimal::add);
        for (var m : this.members.values()) {
            var owed = extraCents-- > 0 ? share.add(cent) : share;
            owed = owed.setScale(2, RoundingMode.UNNECESSARY);
            this.balances.merge(m.uid(), owed.negate(), BigDecimal::add);
        }
        record(TransactionType.EXPENSE, idempotencyKey, uid, null, total, description);
    }

    private void record(TransactionType type, String idempotencyKey, String payerId, String receiverId,
                        BigDecimal amount, String description) {
        var text = description != null ? description : (type == TransactionType.EXPENSE ? "Expense" : "Settlement");
        this.history.append(new Transaction(UUID.randomUUID().toString(), idempotencyKey, type, payerId,
                receiverId, amount, text, Instant.now(clock)));
    }

    private boolean replayOrConflict(String transactionId, String fingerprint) {
        var existing = this.transactions.get(transactionId);
        if (existing == null) {
            return false;
        }
        if (!existing.equals(fingerprint)) {
            throw new ConflictException("Transaction ID " + transactionId + " has already been used for a different request.");
        }
        return true;
    }

    public synchronized BigDecimal getExpenseForMember(String uid) {
        return this.balances.getOrDefault(uid, BigDecimal.ZERO);
    }

    public synchronized List<Member> getMembers() {
        return List.copyOf(this.members.values());
    }

    public synchronized Map<String, BigDecimal> getBalances() {
        var snapshot = new LinkedHashMap<String, BigDecimal>();
        for (var uid : this.members.keySet()) {
            snapshot.put(uid, this.balances.getOrDefault(uid, BigDecimal.ZERO));
        }
        return snapshot;
    }

    /** One page of the history, newest first, 10 per page. Shares the group lock with writes. */
    public synchronized TransactionPage getTransactionPage(int page) {
        return this.history.page(page);
    }

    /** Keyless settlement (legacy). Applies the settlement and records a history entry without a key. */
    public synchronized void settleUp(String uid1, String uid2, BigDecimal amount) {
        applySettlement(uid1, uid2, amount, null, null);
    }

    public synchronized void settleUp(String uid1, String uid2, BigDecimal amount, String transactionId) {
        settleUp(uid1, uid2, amount, transactionId, null);
    }

    /** Idempotent settlement with an optional description (null means omitted); same check order as addExpense. */
    public synchronized void settleUp(String uid1, String uid2, BigDecimal amount, String idempotencyKey,
                                      String description) {
        var text = normalizeDescription(description);
        var fingerprint = "SETTLEUP|" + uid1 + "|" + uid2 + "|" + amount.stripTrailingZeros().toPlainString()
                + (text == null ? "" : "|" + text);
        if (replayOrConflict(idempotencyKey, fingerprint)) {
            return;
        }
        applySettlement(uid1, uid2, amount, idempotencyKey, text);
        this.transactions.put(idempotencyKey, fingerprint);
    }

    private void applySettlement(String uid1, String uid2, BigDecimal amount, String idempotencyKey,
                                 String description) {
        if (!this.members.containsKey(uid1) || !this.members.containsKey(uid2)) {
            throw new IllegalArgumentException("Both members must exist in the group.");
        }
        if (uid1.equals(uid2)) {
            throw new IllegalArgumentException("Cannot settle up with the same member.");
        }
        validateAmount(amount);

        this.balances.merge(uid2, amount.negate(), BigDecimal::add);
        this.balances.merge(uid1, amount, BigDecimal::add);
        record(TransactionType.SETTLEMENT, idempotencyKey, uid1, uid2, amount.setScale(2, RoundingMode.UNNECESSARY),
                description);
    }
}
