# Fixed 72-01: Fix Education Projection Zero Cost Bug

The education projection calculation is currently failing (returning zero) because the loop variable `age` (0-21) is incorrectly passed as the `simulationYear` argument to the `calculateAnnualCost` method. This results in the calculated academic age being a large negative number, triggering a guard clause that forces the cost to zero.

## User Review Required
This plan simply addresses the bug by extracting a proper `simulationYear` variable and passing it correctly. It improves code readability, fixes the projection logic, and leaves a resolution report in the issue tracker.

## Proposed Changes

### Education Service

#### [MODIFY] [ChildProfileServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImpl.java)
- In the `getEducationProjections` method loop (lines 164-188):
  - Extract a well-named variable with a comment: `int simulationYear = child.getBirthDate().getYear() + age;`
  - Pass `simulationYear` into `educationExpenseCalculator.calculateAnnualCost(...)` instead of `age`.
  - Pass `simulationYear` into `.year(...)` of the `EducationProjectionResult.builder()`.

### Documentation

#### [MODIFY] [issue-05.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/issue-05.md)
- Append "Resolution Report (已解決)" section explaining the root cause (variable passing error) and the fix applied.

## Verification Plan
### Automated Tests
- The user will run the backend unit tests (`mvn clean install`) to ensure that education costs are properly calculated and no longer project as zero.
