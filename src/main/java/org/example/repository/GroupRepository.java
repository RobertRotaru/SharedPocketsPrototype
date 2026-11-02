package org.example.repository;

import org.example.domain.Group;

import java.util.Optional;

public interface GroupRepository {
    void save(Group group);
    Optional<Group> findById(String groupId);
}
