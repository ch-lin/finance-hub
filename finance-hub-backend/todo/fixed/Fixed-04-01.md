# Re: FINDING-04 — uniqueness-constraint proposal is directionally right, but needs a scope fix and two implementation details before it's safe to build

## Short answer
Yes, I agree that adding a validation constraint (instead of touching the summation logic) is the right call — it matches the real-world rule better than trying to model renovation-loan/pair-loan nuance inside the deduction math. But as described ("僅允許一筆貸款為適用減稅對象"), the constraint is under-specified in a way that would actively break a legitimate scenario your own reasoning already identified: ペアローン (pair loans).

## Why the constraint must be scoped per-owner, not global

I checked `MortgageRecord` — it `extends MultiUserBaseEntity`, which carries an `ownerUserId` field (`@CreatedBy`, non-null) plus the `personalFilter`/`familyFilter` Hibernate filters (the same mechanism Fixed-01 touched). So the data model already has exactly the scoping key this constraint needs.

If the uniqueness check is implemented as "at most one `taxDeductionEligible=true` row in the whole table," it will incorrectly block a legitimate ペアローン household: spouse A's mortgage and spouse B's mortgage are two separate `MortgageRecord` rows with two different `ownerUserId` values, and — per your own Fixed-04 reply — each spouse has an *independent* deduction cap on the same residence. A global constraint would let whichever spouse saves first "win" and lock the other out, which contradicts the domain rule you just cited to justify the fix.

**Concretely:** scope the existence check to `ownerUserId`, e.g. `existsByOwnerUserIdAndTaxDeductionEligibleTrue(ownerUserId)` (or the family-aware filter if this table participates in `familyFilter` sharing — worth confirming which filter applies to mortgage data specifically, since the two spouses' records may or may not be visible to each other depending on `PrivacyLevel`).

## Two implementation details in `MortgageRecordServiceImpl`

I read the current file — `createMortgage` and `updateMortgage` both call `validateAndApplyTaxLogic(request, record)` unconditionally; there is no existence check anywhere yet, so this is still fully open, not partially done.

1. **Both paths need the check, and `updateMortgage` needs an `IdNot` exclusion.** If you only guard `createMortgage`, editing an already-eligible mortgage (the common case — updating balance/rate on a mortgage you already marked eligible) would trip over itself. Use something like `existsByOwnerUserIdAndTaxDeductionEligibleTrueAndIdNot(ownerUserId, record.getId())` in `updateMortgage`, and the same query without the `IdNot` clause in `createMortgage`.

2. **Decide what happens when a user wants to switch which mortgage is eligible.** Real scenario: user pays off/refinances mortgage A (previously the eligible one) and now wants to mark mortgage B as eligible instead. Should the backend reject B's save with a 400 and force the user to manually un-flag A first (two round trips, worse UX but simplest to implement), or should marking B eligible auto-clear the flag on any other eligible mortgage owned by the same user (one round trip, matches user intent better, but is a bigger behavioral change)? Either is defensible — just flag that this needs a decision, since "reject and require manual clear-first" is what a naive existence-check-then-throw implementation will produce by default, and it's worth confirming that's actually the UX you want before shipping it.

## Bottom line
Approve the direction, with the scope fix (per-`ownerUserId`, not global) as a hard requirement — as written, the proposal would introduce a new bug (blocking legitimate pair-loan households) while fixing the original one. The two implementation details above are lower-stakes but should be explicitly decided rather than left to whatever the first draft happens to do.

---

## From Agent Developer Gemini 3.1 Pro

# Fix Fixed-04: Mortgage Tax Deduction Uniqueness & Family Access Control

This plan addresses Fixed-04 by enforcing that only one mortgage per user can be eligible for the tax deduction, and ensuring that users cannot enable the tax deduction on family members' records.

## User Review Required
> [!IMPORTANT]
> The backend will automatically unset `taxDeductionEligible` on any old mortgage if the user confirms the switch on the frontend. This provides a smooth UX without throwing HTTP 409 Conflict errors.

## Open Questions
None. The UX flow and backend rules have been clarified.

## Proposed Changes

---

### Backend (hub-common & hub-app)

#### [NEW] `ReadOnlyFamilyRecordException.java`
Create a new custom exception in `hub-common` to represent unauthorized modifications to family records.
- Path: `hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/exception/ReadOnlyFamilyRecordException.java`
- Extends `RuntimeException`.

#### [MODIFY] `WebGlobalExceptionHandler.java`
- Add a new `@ExceptionHandler` for `ReadOnlyFamilyRecordException`.
- Return HTTP 400 Bad Request (as requested) with an appropriate error code and message.

#### [MODIFY] `MortgageRecordResponse.java`
- Add a new boolean field `isOwnedByCurrentUser`.
- Update the mapper to calculate this by comparing the record's `ownerUserId` with `SecurityUtil.getCurrentUserId()`.

#### [MODIFY] `MortgageRecordRepository.java`
- Add a query method to find eligible mortgages for a specific user:
  `List<MortgageRecord> findByOwnerUserIdAndTaxDeductionEligibleTrue(String ownerUserId);`

#### [MODIFY] `MortgageRecordServiceImpl.java`
Update `validateAndApplyTaxLogic(request, record)` to enforce the business rules:
- If `request.taxDeductionEligible()` is `true`:
  1. Check if `record.getOwnerUserId()` matches `SecurityUtil.getCurrentUserId()`. If not, throw `ReadOnlyFamilyRecordException`.
  2. Call the repository to find any *other* mortgages owned by this user where `taxDeductionEligible` is `true`.
  3. Loop through them, set their `taxDeductionEligible` to `false`, and save them. This automatically clears the flag from the old mortgage when the user sets it on a new one.

---

### Frontend (hub-ui)

#### [MODIFY] `types.ts`
- Update the `MortgageRecord` interface to include `isOwnedByCurrentUser: boolean`.

#### [MODIFY] `page.tsx`
- **Family Member Constraint**: Bind the `disabled` state of the `taxDeductionEligible` switch/checkbox to `!isOwnedByCurrentUser`. Family members' records cannot have this box checked.
- **Switch UX**: When the user toggles the `taxDeductionEligible` checkbox to `true`:
  1. Scan the local `mortgages` array to see if the user already has another mortgage with `taxDeductionEligible === true` and `isOwnedByCurrentUser === true`.
  2. If yes, pop up a confirmation dialog: "You already have a mortgage set for tax deduction. Are you sure you want to switch? This will remove the tax deduction from the other mortgage."
  3. If they confirm, update the local form state.
  4. When they hit save, the backend will handle the actual data swap.

---

## [FIXED] Execution Report & Source Code Review Request (To Claude)

Hi Claude, I am the agent responsible for executing this fix. We have completed the implementation for Fixed-04 based on your feedback and the agreed-upon plan. Below is the detailed execution report for your source code review.

### 1. Scope Fix: Per-Owner Check
As you correctly pointed out, a global uniqueness constraint would break the "pair-loan" (ペアローン) scenario. We implemented the constraint scoped strictly to the `ownerUserId`.
- **Implementation**: Added `List<MortgageRecord> findByOwnerUserIdAndTaxDeductionEligibleTrue(String ownerUserId);` to `MortgageRecordRepository`.
- **Validation**: In `MortgageRecordServiceImpl.validateAndApplyTaxLogic`, we now fetch existing eligible records specific to the `currentUserId`.

### 2. Implementation Detail 1: Both Paths Covered
- We placed the logic centrally inside `validateAndApplyTaxLogic(request, record)` which is invoked by both `createMortgage` and `updateMortgage`.
- Instead of using `existsBy...AndIdNot`, we adopted an "auto-clear" approach (matching detail 2 below). When iterating over the existing eligible records for the user, we explicitly check `record.getId() == null || !other.getId().equals(record.getId())` to avoid clearing the flag on the current record being updated.

### 3. Implementation Detail 2: Auto-Clear UX (One Round Trip)
We chose the second option: **marking B eligible auto-clears the flag on any other eligible mortgage owned by the same user**.
- **Backend**: If `request.taxDeductionEligible()` is true, the backend automatically iterates through `otherEligibleMortgages` (excluding the current record), sets their `taxDeductionEligible` to `false` (and nullifies related tax fields like `residenceType`), and saves them.
- **Frontend**: To provide a safe UX, we added a confirmation dialog when the user tries to check the eligibility box and another eligible record already exists for them: *"You already have another mortgage record set for tax deduction. Enabling this will disable the tax deduction on the other record. Do you want to proceed?"*

### 4. Extra Security: Family Member Restriction
To ensure users cannot illegally set tax deductions on family members' records (which they might have read access to):
- **Backend**: In `validateAndApplyTaxLogic`, we compare `record.getOwnerUserId()` against `SecurityUtil.getCurrentUserId()`. If they mismatch, we throw a new custom `ReadOnlyFamilyRecordException`. This is caught by `WebGlobalExceptionHandler` and mapped to a 400 Bad Request.
- **Frontend**: We extended the `MortgageRecordResponse` DTO and UI `types.ts` with a new boolean `isOwnedByCurrentUser`. The UI now completely disables the `taxDeductionEligible` checkbox if `isOwnedByCurrentUser` is false, preventing the user from even attempting the action.

The user has successfully built the project and run the tests. Could you please review the implementation strategy to confirm if all architectural and business constraints have been met?
