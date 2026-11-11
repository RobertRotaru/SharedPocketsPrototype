package org.example.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.controllers.dtos.*;
import org.example.domain.ConflictException;
import org.example.domain.Group;
import org.example.domain.Member;
import org.example.domain.TransactionHistory;
import org.example.repository.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/groups")
@Tag(name = "Group Management", description = "APIs for managing groups, members, expenses, and settlements")
public class GroupController {

    private final GroupRepository groupRepository;

    public GroupController(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Operation(summary = "Create a new group", description = "Creates a new group with the provided name and description.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Group created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
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

    @Operation(summary = "Get group information", description = "Retrieves information about a specific group by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Group information retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMember(@PathVariable String id, @RequestBody AddMemberRequest request) {
        if (request.uid() == null || request.uid().isBlank()) {
            throw new IllegalArgumentException("Member uid is required.");
        }
        requireGroup(id).addMember(new Member(request.uid(), request.name(), request.email()));
    }

    @Operation(summary = "Add an expense to a group", description = "Adds an expense for a specific member in the group. Requires an Idempotency-Key header to ensure idempotent requests. An optional description (1 to 200 characters) is shown in the payment history.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Expense added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Group not found"),
            @ApiResponse(responseCode = "409", description = "Conflict due to duplicate expense submission")
    })
    @PostMapping("/{id}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public void addExpense(@PathVariable String id,
                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                           @RequestBody ExpenseRequest request) {
        requireAmount(request.amount());
        requireGroup(id).addExpense(request.memberId(), request.amount(), idempotencyKey, request.description());
    }

    @Operation(summary = "Get group balances", description = "Retrieves the current balances of all members in the specified group.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Balances retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @GetMapping("/{id}/balances")
    public Map<String, BigDecimal> getBalances(@PathVariable String id) {
        return requireGroup(id).getBalances();
    }

    @Operation(summary = "Settle up between members", description = "Settles up the balance between two members in the group. Requires an Idempotency-Key header to ensure idempotent requests. An optional description (1 to 200 characters) is shown in the payment history.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Settlement completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Group not found"),
            @ApiResponse(responseCode = "409", description = "Conflict due to duplicate settlement submission")
    })
    @PostMapping("/{id}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public void settleUp(@PathVariable String id,
                         @RequestHeader("Idempotency-Key") String idempotencyKey,
                         @RequestBody SettlementRequest request) {
        requireAmount(request.amount());
        requireGroup(id).settleUp(request.payerId(), request.receiverId(), request.amount(), idempotencyKey, request.description());
    }

    @Operation(summary = "Get a group's payment history", description = "Returns one page of the group's payments (expenses and settlements), newest first, 10 per page. Only public data is returned: description, payer and when it was recorded.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "History page retrieved successfully (empty items for a group with no payments or a page past the end)"),
            @ApiResponse(responseCode = "400", description = "Page is zero, negative or not a whole number"),
            @ApiResponse(responseCode = "404", description = "Group not found")
    })
    @GetMapping("/{id}/transactions")
    public TransactionPageResponse getTransactions(@PathVariable String id,
                                                   @RequestParam(defaultValue = "1") int page) {
        var result = requireGroup(id).getTransactionPage(page);
        var items = result.items().stream()
                .map(t -> new TransactionResponse(t.description(), t.payerId(), t.recordedAt()))
                .toList();
        return new TransactionPageResponse(result.page(), TransactionHistory.PAGE_SIZE, result.hasNext(), items);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> invalidArgumentType(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Page must be a positive whole number."));
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
        if (group.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group with id " + id + " does not exist.");
        }
        return group.get();
    }

    private static void requireAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }
    }
}
