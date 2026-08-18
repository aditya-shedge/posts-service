# Java & Spring Boot Coding Standards

## Purpose
This document defines Java and Spring Boot specific coding standards for the **backend service** in Splitwise-Lite. These guidelines complement the [Core Standards](core-standards.md) and should be followed for all backend development.

---

## Table of Contents
1. [Java Language Standards](#java-language-standards)
2. [Spring Boot Best Practices](#spring-boot-best-practices)
3. [Project Structure](#project-structure)
4. [Controller Layer](#controller-layer)
5. [Service Layer](#service-layer)
6. [Repository Layer](#repository-layer)
7. [Domain Models](#domain-models)
8. [DTOs and Validation](#dtos-and-validation)
9. [Exception Handling](#exception-handling)
10. [MongoDB with Spring Data](#mongodb-with-spring-data)
11. [Configuration Management](#configuration-management)
12. [Testing](#testing)
13. [Logging](#logging)
14. [Security](#security)
15. [Performance](#performance)

---

## Java Language Standards

### Java Version
- **Target**: Java 17 (LTS)
- Use modern Java features: Records, Switch expressions, Text blocks, Pattern matching
- Avoid deprecated APIs

### Code Style
- **Formatting**: Follow Google Java Style Guide or project's `.editorconfig`
- **Line length**: Max 120 characters
- **Indentation**: 4 spaces (no tabs)
- **Braces**: Always use braces, even for single-line if statements

### Type System

#### Use Explicit Types (Avoid `var` in complex cases)
```java
// Good - clear type
List<MemberDTO> members = groupService.getMembers(groupId);

// Bad - unclear type
var members = groupService.getMembers(groupId);

// OK - obvious type from constructor
var group = new Group();
```

#### Prefer Immutability
```java
// Good - immutable
public final class MemberId {
    private final String value;
    
    public MemberId(String value) {
        this.value = Objects.requireNonNull(value);
    }
    
    public String getValue() {
        return value;
    }
}

// Even better - use Java Record
public record MemberId(String value) {
    public MemberId {
        Objects.requireNonNull(value, "MemberId cannot be null");
    }
}
```

#### Collections
```java
// Good - specify interface type
List<Expense> expenses = new ArrayList<>();
Map<String, Member> memberMap = new HashMap<>();

// Bad - use concrete type
ArrayList<Expense> expenses = new ArrayList<>();

// Good - immutable when possible
List<String> memberIds = List.of("id1", "id2", "id3");
```

### Null Safety

#### Use Optional for Return Types
```java
// Good
public Optional<Group> findGroupById(String id) {
    return groupRepository.findById(id);
}

// Bad - returning null
public Group findGroupById(String id) {
    return groupRepository.findById(id).orElse(null);
}
```

#### Validate Parameters
```java
public void addMember(String groupId, Member member) {
    Objects.requireNonNull(groupId, "groupId must not be null");
    Objects.requireNonNull(member, "member must not be null");
    // ... implementation
}
```

#### Use `@NonNull` and `@Nullable` Annotations
```java
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

public class GroupService {
    public void processGroup(@NonNull Group group, @Nullable String note) {
        // IDE will warn if group can be null
    }
}
```

### Streams API

#### Use Streams for Collection Operations
```java
// Good - readable stream operations
List<String> memberNames = group.getMembers().stream()
    .filter(Member::isActive)
    .map(Member::getName)
    .sorted()
    .collect(Collectors.toList());

// Avoid overly complex streams
// If you need more than 3-4 operations, consider traditional loops for readability
```

#### Don't Modify State in Streams
```java
// Bad - side effects in stream
expenses.stream().forEach(e -> totalAmount += e.getAmount());

// Good - use proper reduction
double totalAmount = expenses.stream()
    .mapToDouble(Expense::getAmount)
    .sum();
```

### Exception Handling

#### Prefer Unchecked Exceptions
```java
// Good - unchecked exception for business logic
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}

// Only use checked exceptions for recoverable errors
```

#### Don't Swallow Exceptions
```java
// Bad
try {
    processExpense(expense);
} catch (Exception e) {
    // Silent failure
}

// Good - log and rethrow or handle
try {
    processExpense(expense);
} catch (ValidationException e) {
    log.error("Invalid expense: {}", expense.getId(), e);
    throw new BadRequestException("Expense validation failed", e);
}
```

---

## Spring Boot Best Practices

### Dependency Injection

#### Constructor Injection (Preferred)
```java
// Good - constructor injection
@Service
public class ExpenseService {
    private final ExpenseRepository expenseRepository;
    private final GroupRepository groupRepository;
    
    public ExpenseService(ExpenseRepository expenseRepository, 
                          GroupRepository groupRepository) {
        this.expenseRepository = expenseRepository;
        this.groupRepository = groupRepository;
    }
}

// Bad - field injection
@Service
public class ExpenseService {
    @Autowired
    private ExpenseRepository expenseRepository;
}
```

**Benefits**:
- Immutable dependencies (use `final`)
- Easy to test (pass mocks in constructor)
- Explicit dependencies
- No need for `@Autowired` in modern Spring

#### Avoid Circular Dependencies
```java
// Bad - circular dependency
@Service
class GroupService {
    private final ExpenseService expenseService;
    // ...
}

@Service  
class ExpenseService {
    private final GroupService groupService; // Circular!
    // ...
}

// Good - introduce intermediate service or refactor
@Service
class BalanceCalculationService {
    private final ExpenseRepository expenseRepository;
    // Used by both GroupService and ExpenseService
}
```

### Component Annotations

#### Use Specific Annotations
```java
// Good - specific annotations
@RestController
@RequestMapping("/api/groups")
public class GroupController { }

@Service
public class GroupService { }

@Repository
public interface GroupRepository extends MongoRepository<Group, String> { }

// Bad - generic @Component everywhere
```

#### Component Naming
```java
// Good - descriptive names
@Service
public class ExpenseCalculationService { }

@Service
public class BalanceSettlementService { }

// Bad - generic names
@Service
public class ExpenseService { } // Too generic, what does it do?

@Service
public class ExpenseManager { } // Avoid "Manager" suffix
```

---

## Project Structure

### Package Organization

#### Current Structure (Layer-Based)
```
com.workshop.splitwise/
├── config/              # Configuration classes
├── controller/          # REST controllers
├── service/             # Business logic
├── repository/          # Data access
├── model/               # Domain entities
├── dto/                 # Data transfer objects
└── util/                # Utility classes
```

#### Recommended Structure (Feature-Based for Growth)
```
com.workshop.splitwise/
├── config/
├── common/              # Shared utilities, exceptions
├── group/
│   ├── GroupController.java
│   ├── GroupService.java
│   ├── GroupRepository.java
│   ├── Group.java
│   └── dto/
├── expense/
│   ├── ExpenseController.java
│   ├── ExpenseService.java
│   ├── ExpenseRepository.java
│   ├── Expense.java
│   └── dto/
└── settlement/
    ├── BalanceCalculationService.java
    └── SettlementService.java
```

**When to switch**: When project grows beyond 20-30 classes

### File Naming Conventions
- Controllers: `*Controller.java` (e.g., `GroupController`)
- Services: `*Service.java` (e.g., `ExpenseService`)
- Repositories: `*Repository.java` (e.g., `GroupRepository`)
- DTOs: `*DTO.java` or `*Request.java`, `*Response.java`
- Exceptions: `*Exception.java` (e.g., `GroupNotFoundException`)

---

## Controller Layer

### REST Controller Structure

```java
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor // Lombok for constructor injection
@Slf4j // Lombok for logging
public class GroupController {
    
    private final GroupService groupService;
    
    @PostMapping
    public ResponseEntity<GroupDTO> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        log.info("Creating group: {}", request.getName());
        GroupDTO group = groupService.createGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<GroupDTO> getGroup(@PathVariable String id) {
        return groupService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping
    public ResponseEntity<List<GroupDTO>> getAllGroups() {
        return ResponseEntity.ok(groupService.findAll());
    }
}
```

### Controller Best Practices

#### 1. Thin Controllers
Controllers should only handle HTTP concerns:
- Request/response mapping
- HTTP status codes
- Routing
- Input validation (via annotations)

**All business logic belongs in services.**

```java
// Good - thin controller
@PostMapping("/{id}/members")
public ResponseEntity<Void> addMember(@PathVariable String id, 
                                      @Valid @RequestBody MemberDTO member) {
    groupService.addMemberToGroup(id, member);
    return ResponseEntity.ok().build();
}

// Bad - business logic in controller
@PostMapping("/{id}/members")
public ResponseEntity<Void> addMember(@PathVariable String id, 
                                      @Valid @RequestBody MemberDTO member) {
    Group group = groupRepository.findById(id).orElseThrow();
    if (group.getMembers().size() >= 50) {
        throw new IllegalArgumentException("Max members reached");
    }
    Member newMember = new Member();
    newMember.setId(UUID.randomUUID().toString());
    newMember.setName(member.getName());
    group.getMembers().add(newMember);
    groupRepository.save(group);
    return ResponseEntity.ok().build();
}
```

#### 2. Proper HTTP Status Codes
```java
// 200 OK - Successful GET
@GetMapping("/{id}")
public ResponseEntity<GroupDTO> getGroup(@PathVariable String id) {
    return ResponseEntity.ok(groupService.findById(id));
}

// 201 Created - Successful POST creating resource
@PostMapping
public ResponseEntity<GroupDTO> createGroup(@RequestBody CreateGroupRequest request) {
    GroupDTO group = groupService.createGroup(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(group);
}

// 204 No Content - Successful DELETE
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteGroup(@PathVariable String id) {
    groupService.deleteGroup(id);
    return ResponseEntity.noContent().build();
}

// 400 Bad Request - Validation error (handled by exception handler)
// 404 Not Found - Resource not found (handled by exception handler)
```

#### 3. Use DTOs, Not Domain Entities
```java
// Good - use DTOs
@PostMapping
public ResponseEntity<GroupDTO> createGroup(@RequestBody CreateGroupRequest request) {
    GroupDTO group = groupService.createGroup(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(group);
}

// Bad - exposing domain entities
@PostMapping
public ResponseEntity<Group> createGroup(@RequestBody Group group) {
    Group saved = groupRepository.save(group);
    return ResponseEntity.status(HttpStatus.CREATED).body(saved);
}
```

**Why**: Domain entities may contain internal state, lazy-loaded relationships, or sensitive data you don't want to expose.

#### 4. Validation
```java
// Use Bean Validation annotations
@PostMapping
public ResponseEntity<GroupDTO> createGroup(@Valid @RequestBody CreateGroupRequest request) {
    // Validation happens automatically
    GroupDTO group = groupService.createGroup(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(group);
}

public class CreateGroupRequest {
    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 100, message = "Name must be 3-100 characters")
    private String name;
    
    // getters/setters
}
```

---

## Service Layer

### Service Class Structure

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {
    
    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    
    @Transactional(readOnly = true)
    public Optional<GroupDTO> findById(String id) {
        return groupRepository.findById(id)
            .map(this::toDTO);
    }
    
    @Transactional
    public GroupDTO createGroup(CreateGroupRequest request) {
        validateGroupName(request.getName());
        
        Group group = new Group();
        group.setId(generateGroupId());
        group.setName(request.getName());
        group.setMembers(new ArrayList<>());
        
        Group saved = groupRepository.save(group);
        log.info("Created group: {}", saved.getId());
        
        return toDTO(saved);
    }
    
    private void validateGroupName(String name) {
        if (groupRepository.existsByName(name)) {
            throw new DuplicateGroupNameException(name);
        }
    }
    
    private String generateGroupId() {
        return UUID.randomUUID().toString();
    }
    
    private GroupDTO toDTO(Group group) {
        // Mapping logic
    }
}
```

### Service Best Practices

#### 1. Single Responsibility
Each service should have a focused purpose.

```java
// Good - focused services
@Service
class GroupManagementService {
    // Create, update, delete groups
}

@Service
class BalanceCalculationService {
    // Calculate balances for a group
}

@Service
class SettlementService {
    // Generate settlement suggestions
}

// Bad - god service doing everything
@Service
class ExpenseManagerService {
    // 500+ lines handling groups, expenses, balances, settlements
}
```

#### 2. Transaction Management
```java
// Read-only transactions for queries
@Transactional(readOnly = true)
public List<GroupDTO> findAllGroups() {
    return groupRepository.findAll().stream()
        .map(this::toDTO)
        .collect(Collectors.toList());
}

// Write transactions for modifications
@Transactional
public GroupDTO updateGroup(String id, UpdateGroupRequest request) {
    Group group = groupRepository.findById(id)
        .orElseThrow(() -> new GroupNotFoundException(id));
    
    group.setName(request.getName());
    Group updated = groupRepository.save(group);
    
    return toDTO(updated);
}
```

**Note**: Spring Data MongoDB doesn't support transactions by default unless using replica sets. Use `@Transactional` anyway for future-proofing.

#### 3. Avoid Business Logic in DTOs/Entities
```java
// Bad - business logic in entity
@Document
public class Group {
    public void addMember(Member member) {
        if (members.size() >= 50) {
            throw new MaxMembersExceededException();
        }
        members.add(member);
    }
}

// Good - business logic in service
@Service
public class GroupService {
    private static final int MAX_MEMBERS = 50;
    
    public void addMember(String groupId, MemberDTO memberDTO) {
        Group group = findGroupOrThrow(groupId);
        
        if (group.getMembers().size() >= MAX_MEMBERS) {
            throw new MaxMembersExceededException(groupId);
        }
        
        Member member = toEntity(memberDTO);
        group.getMembers().add(member);
        groupRepository.save(group);
    }
}
```

**Exception**: Simple domain logic (validation, calculations) can live in entities if it makes sense.

#### 4. Service Method Naming
```java
// Good - descriptive names
public GroupDTO createGroup(CreateGroupRequest request)
public void addMemberToGroup(String groupId, MemberDTO member)
public List<BalanceDTO> calculateGroupBalances(String groupId)
public Optional<GroupDTO> findGroupById(String id)

// Bad - vague names
public GroupDTO process(CreateGroupRequest request)
public void handle(String groupId, MemberDTO member)
public List<BalanceDTO> get(String groupId)
```

#### 5. Avoid Anemic Services
Services shouldn't just be pass-throughs to repositories.

```java
// Bad - anemic service
@Service
public class GroupService {
    private final GroupRepository repository;
    
    public Group findById(String id) {
        return repository.findById(id).orElse(null);
    }
    
    public Group save(Group group) {
        return repository.save(group);
    }
}

// Good - service adds value
@Service
public class GroupService {
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    
    public GroupDTO createGroupWithMembers(CreateGroupRequest request) {
        validateGroupName(request.getName());
        
        Group group = buildGroup(request);
        List<Member> members = createMembersFromRequest(request.getMembers());
        group.setMembers(members);
        
        Group saved = groupRepository.save(group);
        publishGroupCreatedEvent(saved);
        
        return toDTO(saved);
    }
}
```

---

## Repository Layer

### Repository Interface

```java
@Repository
public interface GroupRepository extends MongoRepository<Group, String> {
    
    // Query methods - Spring Data generates implementation
    Optional<Group> findByName(String name);
    
    List<Group> findByMembersContaining(Member member);
    
    boolean existsByName(String name);
    
    @Query("{ 'members.email': ?0 }")
    List<Group> findGroupsByMemberEmail(String email);
}
```

### Repository Best Practices

#### 1. Use Method Name Conventions
Spring Data can generate queries from method names.

```java
// Simple queries
Optional<Group> findByName(String name);
List<Group> findByCreatedDateAfter(LocalDateTime date);
boolean existsByName(String name);
long countByCreatedDateBetween(LocalDateTime start, LocalDateTime end);

// Complex queries - use @Query
@Query("{ 'members': { $elemMatch: { 'id': ?0 } } }")
Optional<Group> findByMemberId(String memberId);
```

#### 2. Return Types
```java
// Optional for single result
Optional<Group> findById(String id);
Optional<Group> findByName(String name);

// List for multiple results  
List<Group> findAll();
List<Group> findByCreatedDateAfter(LocalDateTime date);

// Stream for large result sets (close after use)
Stream<Group> findAllByCreatedDateAfter(LocalDateTime date);

// Page for pagination
Page<Group> findAll(Pageable pageable);
```

#### 3. Custom Queries
```java
@Repository
public interface ExpenseRepository extends MongoRepository<Expense, String> {
    
    // Method name query
    List<Expense> findByGroupId(String groupId);
    
    // Custom query with @Query
    @Query("{ 'groupId': ?0, 'amount': { $gte: ?1 } }")
    List<Expense> findExpensesByGroupAndMinAmount(String groupId, double minAmount);
    
    // Aggregation pipeline
    @Aggregation(pipeline = {
        "{ $match: { 'groupId': ?0 } }",
        "{ $group: { '_id': '$paidByMemberId', 'total': { $sum: '$amount' } } }"
    })
    List<MemberTotalDTO> calculateTotalPaidByMember(String groupId);
}
```

#### 4. Avoid Business Logic in Repositories
```java
// Bad - business logic in repository
@Repository
public interface GroupRepository extends MongoRepository<Group, String> {
    
    default Group createGroupWithValidation(Group group) {
        if (existsByName(group.getName())) {
            throw new DuplicateNameException();
        }
        return save(group);
    }
}

// Good - repositories only for data access
// Put validation in service layer
```

---

## Domain Models

### Entity Design

```java
@Document(collection = "groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String name;
    
    private List<Member> members = new ArrayList<>();
    
    @CreatedDate
    private LocalDateTime createdDate;
    
    @LastModifiedDate
    private LocalDateTime lastModifiedDate;
}
```

### Domain Model Best Practices

#### 1. Use Lombok Wisely
```java
// Good - selective Lombok
@Data  // Generates getters, setters, toString, equals, hashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {
    private String id;
    private String name;
}

// Be careful with @Data on entities with relationships
// It can cause infinite loops in toString/equals

// Better for entities - use specific annotations
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group {
    private String id;
    private String name;
    
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Member> members; // Exclude from toString to avoid deep object graphs
}
```

#### 2. Immutable Value Objects
```java
// Good - immutable value object
@Value // Lombok: final class, final fields, no setters
public class Money {
    double amount;
    String currency;
    
    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(amount + other.amount, currency);
    }
}

// Even better - use Java Record
public record Money(double amount, String currency) {
    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }
    
    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(amount + other.amount, currency);
    }
}
```

#### 3. Validation in Domain Models
```java
@Document
public class Expense {
    @Id
    private String id;
    
    @NotNull
    private String groupId;
    
    @NotBlank
    @Size(max = 200)
    private String description;
    
    @Positive
    private double amount;
    
    @NotNull
    private String paidByMemberId;
    
    @Valid
    @NotEmpty
    private List<Split> splits;
    
    // Business validation method
    public void validate() {
        double totalSplit = splits.stream()
            .mapToDouble(Split::getAmount)
            .sum();
        
        if (Math.abs(amount - totalSplit) > 0.01) {
            throw new InvalidExpenseException("Splits don't add up to total amount");
        }
    }
}
```

#### 4. Embedded vs Referenced Documents

**Embedded** (Current approach for Members in Groups):
```java
@Document
public class Group {
    @Id
    private String id;
    private String name;
    private List<Member> members; // Embedded documents
}

public class Member {
    private String id;
    private String name;
    private String email;
    // Not a separate @Document
}
```

**When to embed**:
- Data accessed together
- Bounded size (won't grow indefinitely)
- No need to query embedded data independently

**Referenced** (Current approach for Expenses):
```java
@Document
public class Expense {
    @Id
    private String id;
    private String groupId; // Reference to Group
    private String paidByMemberId; // Reference to Member
    // ...
}
```

**When to reference**:
- Unbounded growth (expenses can grow infinitely)
- Need to query independently
- Many-to-many relationships

---

## DTOs and Validation

### DTO Design

```java
// Request DTO
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequest {
    
    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 100, message = "Name must be 3-100 characters")
    private String name;
    
    @Valid
    private List<@Valid CreateMemberRequest> initialMembers;
}

// Response DTO
@Data
@Builder
public class GroupDTO {
    private String id;
    private String name;
    private List<MemberDTO> members;
    private LocalDateTime createdDate;
}
```

### Validation Best Practices

#### 1. Bean Validation Annotations
```java
import jakarta.validation.constraints.*;

public class ExpenseRequest {
    
    @NotBlank
    private String description;
    
    @Positive(message = "Amount must be positive")
    private double amount;
    
    @NotNull
    @Size(min = 1, message = "At least one split required")
    @Valid
    private List<SplitDTO> splits;
    
    @Email
    @NotBlank
    private String paidByEmail;
    
    @PastOrPresent
    private LocalDateTime expenseDate;
}
```

#### 2. Custom Validators
```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SplitsValidator.class)
public @interface ValidSplits {
    String message() default "Splits must add up to total amount";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class SplitsValidator implements ConstraintValidator<ValidSplits, List<SplitDTO>> {
    
    @Override
    public boolean isValid(List<SplitDTO> splits, ConstraintValidatorContext context) {
        if (splits == null || splits.isEmpty()) {
            return false;
        }
        
        double total = splits.stream()
            .mapToDouble(SplitDTO::getAmount)
            .sum();
        
        return Math.abs(total - 100.0) < 0.01; // Assuming percentage splits
    }
}

// Usage
public class ExpenseRequest {
    @ValidSplits
    private List<SplitDTO> splits;
}
```

#### 3. Mapping Between DTOs and Entities

**Manual mapping**:
```java
@Service
public class GroupMapper {
    
    public GroupDTO toDTO(Group group) {
        return GroupDTO.builder()
            .id(group.getId())
            .name(group.getName())
            .members(group.getMembers().stream()
                .map(this::toMemberDTO)
                .collect(Collectors.toList()))
            .createdDate(group.getCreatedDate())
            .build();
    }
    
    public Group toEntity(CreateGroupRequest request) {
        Group group = new Group();
        group.setId(UUID.randomUUID().toString());
        group.setName(request.getName());
        group.setMembers(new ArrayList<>());
        return group;
    }
    
    private MemberDTO toMemberDTO(Member member) {
        // ...
    }
}
```

**Using MapStruct** (recommended for larger projects):
```java
@Mapper(componentModel = "spring")
public interface GroupMapper {
    
    GroupDTO toDTO(Group group);
    
    List<GroupDTO> toDTOList(List<Group> groups);
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdDate", ignore = true)
    @Mapping(target = "lastModifiedDate", ignore = true)
    Group toEntity(CreateGroupRequest request);
}
```

---

## Exception Handling

### Custom Exceptions

```java
// Base exception
public abstract class SplitwiseException extends RuntimeException {
    protected SplitwiseException(String message) {
        super(message);
    }
    
    protected SplitwiseException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Domain exceptions
public class GroupNotFoundException extends SplitwiseException {
    public GroupNotFoundException(String groupId) {
        super(String.format("Group not found: %s", groupId));
    }
}

public class InsufficientBalanceException extends SplitwiseException {
    public InsufficientBalanceException(String memberId, double required, double available) {
        super(String.format("Member %s has insufficient balance. Required: %.2f, Available: %.2f", 
            memberId, required, available));
    }
}

public class InvalidSplitException extends SplitwiseException {
    public InvalidSplitException(String reason) {
        super("Invalid expense split: " + reason);
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(GroupNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGroupNotFound(GroupNotFoundException ex) {
        log.error("Group not found: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            ex.getMessage(),
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        log.warn("Validation error: {}", message);
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            message,
            LocalDateTime.now()
        );
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericError(Exception ex) {
        log.error("Unexpected error", ex);
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "An unexpected error occurred",
            LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

@Data
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
}
```

---

## MongoDB with Spring Data

### Configuration

```properties
# application.properties
spring.data.mongodb.uri=mongodb://localhost:27017/splitwise
spring.data.mongodb.auto-index-creation=true
```

### Indexing

```java
@Document(collection = "groups")
public class Group {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String name;
    
    @Indexed
    private LocalDateTime createdDate;
}

// Compound index
@Document
@CompoundIndex(name = "group_member_idx", def = "{'groupId': 1, 'paidByMemberId': 1}")
public class Expense {
    private String groupId;
    private String paidByMemberId;
}
```

### Optimistic Locking

```java
@Document
public class Group {
    @Id
    private String id;
    
    @Version
    private Long version;
    
    // ... fields
}

// Spring will automatically check version on updates
// Throws OptimisticLockingFailureException if version mismatch
```

### Aggregation Queries

```java
@Repository
public interface ExpenseRepository extends MongoRepository<Expense, String> {
    
    @Aggregation(pipeline = {
        "{ $match: { groupId: ?0 } }",
        "{ $group: { _id: '$paidByMemberId', totalPaid: { $sum: '$amount' } } }"
    })
    List<MemberExpenseTotal> getTotalPaidByMembers(String groupId);
}
```

---

## Configuration Management

### Application Properties

```properties
# application.properties
spring.application.name=splitwise-backend

# MongoDB
spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/splitwise}
spring.data.mongodb.auto-index-creation=true

# Server
server.port=${SERVER_PORT:8080}
server.error.include-message=always

# Logging
logging.level.com.workshop.splitwise=INFO
logging.level.org.springframework.data.mongodb=DEBUG

# CORS
app.cors.allowed-origins=${ALLOWED_ORIGINS:http://localhost:3000}
```

### Configuration Class

```java
@Configuration
public class MongoConfig {
    
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
    
    @Bean
    public AuditorAware<String> auditorProvider() {
        // Return current user for @CreatedBy, @LastModifiedBy
        return () -> Optional.of("system");
    }
}
```

### CORS Configuration

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins.split(","))
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

---

## Testing

### Unit Testing Services

```java
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {
    
    @Mock
    private GroupRepository groupRepository;
    
    @Mock
    private ExpenseRepository expenseRepository;
    
    @InjectMocks
    private GroupService groupService;
    
    @Test
    void shouldCreateGroup() {
        // Arrange
        CreateGroupRequest request = new CreateGroupRequest("Test Group", List.of());
        Group savedGroup = new Group("id1", "Test Group", new ArrayList<>());
        
        when(groupRepository.existsByName("Test Group")).thenReturn(false);
        when(groupRepository.save(any(Group.class))).thenReturn(savedGroup);
        
        // Act
        GroupDTO result = groupService.createGroup(request);
        
        // Assert
        assertNotNull(result);
        assertEquals("Test Group", result.getName());
        verify(groupRepository).save(any(Group.class));
    }
    
    @Test
    void shouldThrowExceptionWhenGroupNotFound() {
        // Arrange
        when(groupRepository.findById("invalid")).thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(GroupNotFoundException.class, 
            () -> groupService.getGroupById("invalid"));
    }
}
```

### Integration Testing

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GroupRepositoryIntegrationTest {
    
    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");
    
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }
    
    @Autowired
    private GroupRepository groupRepository;
    
    @Test
    void shouldSaveAndRetrieveGroup() {
        // Arrange
        Group group = new Group();
        group.setName("Test Group");
        group.setMembers(new ArrayList<>());
        
        // Act
        Group saved = groupRepository.save(group);
        Optional<Group> retrieved = groupRepository.findById(saved.getId());
        
        // Assert
        assertTrue(retrieved.isPresent());
        assertEquals("Test Group", retrieved.get().getName());
    }
}
```

### Controller Testing

```java
@WebMvcTest(GroupController.class)
class GroupControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private GroupService groupService;
    
    @Test
    void shouldCreateGroup() throws Exception {
        // Arrange
        CreateGroupRequest request = new CreateGroupRequest("Test Group", List.of());
        GroupDTO response = GroupDTO.builder()
            .id("id1")
            .name("Test Group")
            .members(List.of())
            .build();
        
        when(groupService.createGroup(any())).thenReturn(response);
        
        // Act & Assert
        mockMvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Group\",\"initialMembers\":[]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Test Group"));
    }
}
```

---

## Logging

### Logging Configuration

```java
@Slf4j
@Service
public class ExpenseService {
    
    public void addExpense(String groupId, ExpenseRequest request) {
        log.info("Adding expense to group: groupId={}, amount={}, description={}", 
            groupId, request.getAmount(), request.getDescription());
        
        try {
            // ... business logic
            log.debug("Expense validation successful: groupId={}", groupId);
        } catch (ValidationException e) {
            log.error("Expense validation failed: groupId={}, reason={}", 
                groupId, e.getMessage(), e);
            throw e;
        }
        
        log.info("Expense added successfully: groupId={}, expenseId={}", 
            groupId, expense.getId());
    }
}
```

### Logging Best Practices
- Use SLF4J + Logback (Spring Boot default)
- Use parameterized logging: `log.info("Message: {}", value)` not `log.info("Message: " + value)`
- Don't log sensitive data (passwords, tokens, PII)
- Use appropriate log levels
- Include context (IDs, usernames, request IDs)

---

## Security

### Input Validation
- Always validate user inputs
- Use Bean Validation (`@Valid`, `@NotNull`, etc.)
- Sanitize inputs to prevent injection attacks
- Enforce size limits

### Secrets Management
```properties
# Bad - hardcoded secrets
spring.data.mongodb.uri=mongodb://admin:password123@localhost:27017

# Good - use environment variables
spring.data.mongodb.uri=${MONGODB_URI}
```

### MongoDB Query Safety
```java
// Safe - using Spring Data methods
List<Group> groups = groupRepository.findByName(userInput);

// Unsafe - concatenating user input
// Don't do this in MongoDB queries!
```

---

## Performance

### Pagination

```java
@GetMapping
public ResponseEntity<Page<GroupDTO>> getAllGroups(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    
    Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
    Page<GroupDTO> groups = groupService.findAll(pageable);
    return ResponseEntity.ok(groups);
}
```

### Avoid N+1 Queries
```java
// Bad - N+1 query problem
public List<GroupWithExpensesDTO> getAllGroupsWithExpenses() {
    List<Group> groups = groupRepository.findAll();
    return groups.stream()
        .map(group -> {
            // This executes a query for EACH group
            List<Expense> expenses = expenseRepository.findByGroupId(group.getId());
            return new GroupWithExpensesDTO(group, expenses);
        })
        .collect(Collectors.toList());
}

// Good - single query or batch fetch
public List<GroupWithExpensesDTO> getAllGroupsWithExpenses() {
    List<Group> groups = groupRepository.findAll();
    List<String> groupIds = groups.stream()
        .map(Group::getId)
        .collect(Collectors.toList());
    
    // Single query for all expenses
    List<Expense> allExpenses = expenseRepository.findByGroupIdIn(groupIds);
    
    // Group expenses by groupId
    Map<String, List<Expense>> expensesByGroup = allExpenses.stream()
        .collect(Collectors.groupingBy(Expense::getGroupId));
    
    // Combine
    return groups.stream()
        .map(group -> new GroupWithExpensesDTO(
            group, 
            expensesByGroup.getOrDefault(group.getId(), List.of())
        ))
        .collect(Collectors.toList());
}
```

### Caching
```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("groups", "expenses");
    }
}

@Service
public class GroupService {
    
    @Cacheable(value = "groups", key = "#id")
    public Optional<GroupDTO> findById(String id) {
        return groupRepository.findById(id).map(this::toDTO);
    }
    
    @CacheEvict(value = "groups", key = "#id")
    public void updateGroup(String id, UpdateGroupRequest request) {
        // Update logic
    }
}
```

---

## Code Review Checklist

Before submitting Java/Spring Boot code:

- [ ] **Dependencies**: Using constructor injection with `final` fields
- [ ] **Layers**: Controllers are thin, business logic in services
- [ ] **DTOs**: Not exposing domain entities in API
- [ ] **Validation**: Using Bean Validation annotations
- [ ] **Exceptions**: Using custom exceptions with global handler
- [ ] **Transactions**: Using `@Transactional` appropriately
- [ ] **Null Safety**: Using `Optional` for return types, validating parameters
- [ ] **Logging**: Structured logging with appropriate levels
- [ ] **Testing**: Unit tests for services, integration tests for repositories
- [ ] **MongoDB**: Proper indexing, avoiding N+1 queries
- [ ] **Documentation**: JavaDoc for public methods, complex logic explained

---

## Common Pitfalls

1. **Using field injection instead of constructor injection**
2. **Catching generic `Exception` in services**
3. **Exposing domain entities in REST APIs**
4. **Not using transactions for multi-step operations**
5. **Circular dependencies between services**
6. **God classes doing too much**
7. **Not validating user inputs**
8. **Logging sensitive information**
9. **Not handling MongoDB connection failures**
10. **Forgetting to index frequently queried fields**

---

## References

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Data MongoDB](https://docs.spring.io/spring-data/mongodb/docs/current/reference/html/)
- [Effective Java](https://www.oreilly.com/library/view/effective-java/9780134686097/) by Joshua Bloch
- [Clean Code](https://www.oreilly.com/library/view/clean-code-a/9780136083238/) by Robert C. Martin

---

## Next Steps

After mastering these guidelines:
1. Explore Spring Security for authentication/authorization
2. Learn Spring Boot Actuator for monitoring
3. Implement event-driven architecture with Spring Events
4. Add API documentation with SpringDoc OpenAPI
5. Implement caching strategies with Redis
