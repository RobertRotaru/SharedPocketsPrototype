package org.example.repository;

import org.example.domain.Group;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ConcurrentInMemoryGroupRepository implements GroupRepository {
    private ConcurrentHashMap<String, Group> groupMap;

    public ConcurrentInMemoryGroupRepository() {
        this.groupMap = new ConcurrentHashMap<>();
    }

    @Override
    public void save(Group group) {
        groupMap.put(group.getGid(), group);
    }

    @Override
    public Group findById(String groupId) {
        return groupMap.getOrDefault(groupId, null);
    }

    @Override
    public void addExpense(String groupId, String memberId, BigDecimal amount, String transactionId) {
        if (!groupMap.containsKey(groupId)) {
            throw new IllegalArgumentException("Group with id " + groupId + " does not exist.");
        }
        Group group = groupMap.get(groupId);
        group.addExpense(memberId, amount, transactionId);
    }

    @Override
    public void settleUp(String groupId, String memberId1, String memberId2, BigDecimal amount, String transactionId) {
        if (!groupMap.containsKey(groupId)) {
            throw new IllegalArgumentException("Group with id " + groupId + " does not exist.");
        }
        Group group = groupMap.get(groupId);
        group.settleUp(memberId1, memberId2, amount, transactionId);
    }
}
