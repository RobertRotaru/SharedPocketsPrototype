package org.example.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Group {
    public String getGid() {
        return gid;
    }

    private final String gid;
    private final String name;
    private final String description;

    private final LinkedHashMap<String, Member> members;
    private final LinkedHashMap<String, BigDecimal> balances;
    private final HashMap<String, String> transactions = new HashMap<>();

    public Group(String gid, String name, String description) {
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

    public synchronized void addExpense(String uid, BigDecimal amount) {
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
    }

    public synchronized void addExpense(String uid, BigDecimal amount, String transactionId) {
        var fingerprint = "EXPENSE|" + uid + "|" + amount.stripTrailingZeros().toPlainString();
        if (replayOrConflict(transactionId, fingerprint)) {
            return;
        }
        addExpense(uid, amount);
        this.transactions.put(transactionId, fingerprint);
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

    public synchronized void settleUp(String uid1, String uid2, BigDecimal amount) {
        if (!this.members.containsKey(uid1) || !this.members.containsKey(uid2)) {
            throw new IllegalArgumentException("Both members must exist in the group.");
        }
        if (uid1.equals(uid2)) {
            throw new IllegalArgumentException("Cannot settle up with the same member.");
        }
        validateAmount(amount);

        this.balances.merge(uid2, amount.negate(), BigDecimal::add);
        this.balances.merge(uid1, amount, BigDecimal::add);
    }

    public synchronized void settleUp(String uid1, String uid2, BigDecimal amount, String transactionId) {
        var fingerprint = "SETTLEUP|" + uid1 + "|" + uid2 + "|" + amount.stripTrailingZeros().toPlainString();
        if (replayOrConflict(transactionId, fingerprint)) {
            return;
        }
        settleUp(uid1, uid2, amount);
        this.transactions.put(transactionId, fingerprint);
    }
}

