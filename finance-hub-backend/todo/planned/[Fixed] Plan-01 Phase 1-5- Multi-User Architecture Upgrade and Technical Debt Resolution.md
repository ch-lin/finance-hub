# [Fixed] Phase 1-5: Multi-User Architecture Upgrade and Technical Debt Resolution

This plan details the steps required to safely upgrade `ChildProfile` to the multi-user architecture and decouple the simulation engine from JPA entity relationships.

## Proposed Changes

---
### Domain Entity Refactoring (ORM Decoupling)

#### [MODIFY] [UserProfile.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java)
- Delete the `@OneToMany children` collection mapping completely to break the ORM foreign key leakage.

#### [MODIFY] [ChildProfile.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/ChildProfile.java)
- Change class definition to `extends MultiUserBaseEntity`.
- Replace `@Builder` with `@SuperBuilder(toBuilder = true)`.
- Add `@EqualsAndHashCode(callSuper = true)`.
- Delete `@Id private Long id;` (now inherited).
- Delete `private String userProfileId;` (replaced by `ownerUserId`).

---
### Simulation Engine Decoupling

#### [MODIFY] [SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- Add `private List<ChildProfile> children = new ArrayList<>();`.
- Ensure `children` is properly deep-copied in the `clone()` method.

#### [MODIFY] [SimulationContextFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)
- Inject `ChildProfileRepository`.
- In `createContext()`, call `childProfileRepository.findAll()` and explicitly set it into `context.setChildren(...)`. (Note: The `findAll()` will automatically be filtered by `OwnerFilterInterceptor` to only fetch the current user's children).

#### [MODIFY] [IncomeExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)
- In `calculateExpensesForYear`, replace `context.getProfile().getChildren()` with `context.getChildren()`.

---
### Service and Repository Refactoring

#### [MODIFY] [ChildProfileRepository.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/user/child/ChildProfileRepository.java)
- Delete `findByUserProfileId` and `findByIdAndUserProfileId` methods.

#### [MODIFY] [ChildProfileService.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileService.java)
- Remove `String userProfileId` from all method signatures.

#### [MODIFY] [ChildProfileServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImpl.java)
- Remove `UserProfileRepository` dependency entirely.
- **createChildProfile**: Remove `userProfile.getChildren().add(childProfile)`. Instead, directly instantiate the `ChildProfile` and call `childProfileRepository.save(childProfile)`. (JPA Auditing will automatically inject the `ownerUserId`).
- **deleteChildProfile**: Replace collection removal with `childProfileRepository.findById(childId).ifPresent(childProfileRepository::delete)`.
- **getChildProfiles**: Replace with `childProfileRepository.findAll()`.
- **updateChildProfile / getChildProfile**: Use standard `findById`.

---
### Web API Decoupling

#### [MODIFY] [ChildProfileController.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/ChildProfileController.java)
- Delete `@AuthenticationPrincipal Jwt jwt` from all endpoint parameters.
- Update calls to `childProfileService` to reflect the updated method signatures (no longer passing `userId`).

## Verification Plan

### Automated Tests
- **由使用者 (USER) 自行執行測試**：實作完成後，將由使用者親自在終端機執行 `mvn clean verify`。**AI Agent 絕對禁止自行使用 `run_command` 觸發測試**，以避免因為 CLI 授權導致流程中斷或卡死。
- 專案依賴 H2 + `ddl-auto`，不需要執行額外的 Flyway/Liquibase DB Migration script。

# Phase 1-5 Refactoring Completed!

## What was Changed

- **Domain Entity Refactored**:
  - `UserProfile` now has its `children` `@OneToMany` mapping entirely removed, severing the ORM tie.
  - `ChildProfile` now extends `MultiUserBaseEntity`, inheriting `ownerUserId` natively, and the legacy `userProfileId` has been removed.
- **Simulation Engine Decoupled**:
  - `SimulationContext` now holds an independent `List<ChildProfile>`.
  - `SimulationContextFactory` dynamically loads children via `childProfileRepository.findAll()`, utilizing the global `OwnerFilterInterceptor`.
  - `IncomeExpenseCalculator` no longer traverses `context.getProfile().getChildren()`, eliminating ORM leakage into the core calculation engine.
- **Service and Repository Cleaned**:
  - `ChildProfileRepository` no longer requires `userProfileId` specific queries.
  - `ChildProfileServiceImpl` has been refactored to use standard JPA repository methods without relying on `UserProfile` cascade saving.
- **Web API Decoupled**:
  - `ChildProfileController` no longer relies on `@AuthenticationPrincipal Jwt jwt` to pass `userId` down to the service layer.

## Verification

All code changes have been implemented exactly according to the `implementation_plan.md`.

> [!IMPORTANT]
> **Please execute the following command in your terminal to verify the changes:**
>
> `mvn clean verify`
>
> Once the tests pass successfully, we will be ready to move on to Phase 6 (Vertical Slice Implementation).
