package org.example.repository;

import org.example.domain.Group;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
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
    public Optional<Group> findById(String groupId) {
        return groupMap.get(groupId) != null ? Optional.of(groupMap.get(groupId)) : Optional.empty();
    }
}
