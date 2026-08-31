# Input Validation NFR Implementation Plan

## Overview

This NFR documents and verifies the input validation already implemented across Stories 001-005. The validation rules are in place; this plan ensures complete test coverage and documents the validation behavior.

## Current Implementation Status

| Acceptance Criteria | Status | Implementation |
|---------------------|--------|----------------|
| AC1: Text required | ✓ Done | `@NotBlank` on CreatePostRequest, UpdatePostRequest |
| AC2: URL validation | ✓ Done | `@URL` on attachment field |
| AC3: Empty attachment allowed | ✓ Done | Field is nullable |
| AC4: Remarks max 1000 chars | ✓ Done | `@Size(max = 1000)` on remarks |
| AC5: Empty remarks allowed | ✓ Done | Field is nullable |
| AC6: Multiple errors together | ✓ Done | GlobalExceptionHandler joins errors |
| AC7: Invalid UUID returns 400 | ✓ Done | MethodArgumentTypeMismatchException handler |
| AC8: Valid input passes | ✓ Done | Tested in existing tests |

## Test Coverage Gaps

After analysis, the following test scenarios are missing:

1. **UpdatePostRequest DTO validation tests** - Only CreatePostRequest has dedicated DTO tests
2. **Multiple validation errors test** - AC6 is not explicitly tested
3. **Empty string attachment test** - Verify empty string vs null behavior

## Test Rules

- Tests must describe only observable behaviour and outcomes.
- Tests must never reference story IDs, Jira IDs, phase numbers, or step numbers.
- Implement ONLY the test scenarios defined in the plan.
- Do not add tests solely to increase coverage metrics.

## Implementation Phases

### Phase 1: Characterization Safety Net (no production code changes)

**Files**: 
- `src/main/java/com/example/posts_service/dto/CreatePostRequest.java`
- `src/main/java/com/example/posts_service/dto/UpdatePostRequest.java`
- `src/main/java/com/example/posts_service/exception/GlobalExceptionHandler.java`

**Test Files**:
- `src/test/java/com/example/posts_service/dto/CreatePostRequestValidationTest.java`
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`

**What to do in this phase:**
- Run `./gradlew test --rerun` to verify all 62 tests pass
- Review existing validation test coverage
- No code changes needed

**High-level characterization coverage:**
- Existing DTO validation tests cover CreatePostRequest
- Existing controller tests cover 400 responses for validation failures

---

### Phase 2: Add UpdatePostRequest DTO Validation Tests

**Files**: None (no production changes)

**Test Files**: 
- `src/test/java/com/example/posts_service/dto/UpdatePostRequestValidationTest.java` (new)

Create dedicated validation tests for UpdatePostRequest DTO, mirroring CreatePostRequestValidationTest.

**Test scenarios (implement exactly these; no extra tests):**
- Blank text fails validation
- Invalid attachment URL fails validation
- Remarks exceeding 1000 characters fails validation
- Valid request with all fields passes validation
- Valid request with null attachment and remarks passes validation

**Technical details:**
- Use Jakarta Validation API directly (same pattern as CreatePostRequestValidationTest)
- Tests validate the DTO annotations work correctly

---

### Phase 3: Add Multiple Validation Errors Test

**Files**: None (no production changes)

**Test Files**: 
- `src/test/java/com/example/posts_service/controller/PostControllerTest.java`

Add test for AC6 - multiple validation errors reported together.

**Test scenarios (implement exactly these; no extra tests):**
- Request with multiple invalid fields returns all errors in single response

**Technical details:**
- Submit request with blank text AND invalid URL
- Verify response message contains both field errors

---

### Phase 4: Add Integration Validation Tests

**Files**: None (no production changes)

**Test Files**: 
- `src/test/java/com/example/posts_service/InputValidationIntegrationTest.java` (new)

Full stack validation tests to verify end-to-end behavior.

**Test scenarios (implement exactly these; no extra tests):**
- Create post with blank text returns 400 with clear error message
- Create post with invalid URL returns 400 with clear error message
- Create post with remarks over 1000 chars returns 400 with clear error message
- Update post with blank text returns 400 with clear error message
- Request with multiple invalid fields returns combined error message

**Technical details:**
- Follow existing integration test pattern
- Verify exact error message format matches story specification

---

## Technical Considerations

- **Dependencies**: No new dependencies required (validation already configured)
- **Edge Cases**: Empty string vs null for optional fields
- **Testing Strategy**: DTO unit tests + controller tests + integration tests
- **Performance**: Validation happens before database operations (no performance impact)
- **Security**: Input validation prevents malformed data from entering the system

## Testing Notes

- All tests verify existing behavior - no production code changes
- Tests document the validation contract for future maintenance

## Success Criteria

- [ ] All existing 62 tests continue to pass
- [ ] UpdatePostRequest has dedicated DTO validation tests
- [ ] Multiple validation errors are tested (AC6)
- [ ] Integration tests verify end-to-end validation behavior
- [ ] Total test count increases to ~72 tests
