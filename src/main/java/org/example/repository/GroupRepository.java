package org.example.repository;

import org.example.domain.Group;

import java.math.BigDecimal;

public interface GroupRepository {
    void save(Group group);
    Group findById(String groupId);
    void addExpense(String groupId, String memberId, BigDecimal amount, String transactionId);
    void settleUp(String groupId, String memberId1, String memberId2, BigDecimal amount, String transactionId);
}
