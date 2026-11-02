package org.example.controllers;

import org.example.domain.ConflictException;
import org.example.domain.Group;
import org.example.domain.Member;
import org.example.repository.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/groups")
public class GroupController {
    public record CreateGroupRequest(String name, String description) {}

    public record GroupResponse(String id, String name, String description) {}

    public record AddMemberRequest(String uid, String name, String email) {}

    public record ExpenseRequest(String memberId, BigDecimal amount) {}

    public record SettlementRequest(String payerId, String receiverId, BigDecimal amount) {}

    private final GroupRepository groupRepository;

    public GroupController(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@RequestBody CreateGroupRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Group name is required.");
        }
        var group = new Group(UUID.randomUUID().toString(), request.name(), request.description());
        groupRepository.save(group);
        return new GroupResponse(group.getGid(), request.name(), request.description());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(@PathVariable String id, @RequestBody AddMemberRequest request) {
        if (request.uid() == null || request.uid().isBlank()) {
            throw new IllegalArgumentException("Member uid is required.");
        }
        requireGroup(id).addMember(new Member(request.uid(), request.name(), request.email()));
    }

    @PostMapping("/{id}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public void addExpense(@PathVariable String id,
                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                           @RequestBody ExpenseRequest request) {
        requireGroup(id);
        requireAmount(request.amount());
        groupRepository.addExpense(id, request.memberId(), request.amount(), idempotencyKey);
    }

    @GetMapping("/{id}/balances")
    public Map<String, BigDecimal> getBalances(@PathVariable String id) {
        return requireGroup(id).getBalances();
    }

    @PostMapping("/{id}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public void settleUp(@PathVariable String id,
                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                         @RequestBody SettlementRequest request) {
        requireGroup(id);
        requireAmount(request.amount());
        // Group.settleUp takes the receiver first, then the payer.
        groupRepository.settleUp(id, request.receiverId(), request.payerId(), request.amount(), idempotencyKey);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    private Group requireGroup(String id) {
        var group = groupRepository.findById(id);
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group with id " + id + " does not exist.");
        }
        return group;
    }

    private static void requireAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }
    }
}
