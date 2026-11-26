# [MEDIUM] Re-verification (2026-09-02): the "YTD Tax Deduction Approach" infrastructure is genuinely built, but the fix never executes for any user with real salary history — the original inconsistency is still reproducible for the realistic population

## Status: PARTIALLY_FIXED — substantial new infrastructure (`YearEndAdjustmentEngine`, YTD context fields, base-salary-only projection, frontend income-assumption fields) is real and individually correct, but a pre-existing branch (`projectedRemainingNetIncomeThisYear`) short-circuits the new reconciliation logic for the exact population the fix targets, leaving the original bug pattern intact where it matters.

## Relation to original finding
Re-verifies [Fixed-12-first-year-tax-breakdown-inconsistent.md](Fixed-12-first-year-tax-breakdown-inconsistent.md)'s "Implementation Report", which claimed a full architectural rewrite: new `expectedAnnualBonus`/`useHistoricalBonusForProjection` profile fields, new YTD-tracking fields on `SimulationContext`, a new `YearEndAdjustmentEngine` that reconciles true annual tax liability against YTD-withheld taxes, and a Year-1 code path that overwrites `estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance`/`netIncome` all from one reconciled computation.

## What was verified genuinely built (confirmed real)

**`YearEndAdjustmentEngine` is internally correct.** `YearEndAdjustmentEngine.java:31-57` computes `remainingGross`, `remainingIncomeTax`, `remainingInhabitantTax`, `remainingSocialInsurance` each as `trueAnnual − ytdActual`, then derives `remainingNetIncome = remainingGross − (the three remaining tax/insurance figures)`. All four outputs are mathematically self-consistent by construction.

**YTD population is correct and NPE-safe.** `AssetSimulationEngineImpl.java:419-431` sums YTD gross/tax/insurance from the current year's real `SalaryRecord`s, correctly restricting `ytdBaseSalary` to `type == MONTHLY_SALARY` (avoiding bonus double-counting into the base-salary bucket). Null-guarded throughout; an empty salary history simply leaves all `ytd*` fields at `0L`.

**Bonus double-counting structurally prevented — where the new path runs.** Within the reconciliation branch, `remainingGross = trueAnnualGross − ytdGross` cancels any bonus already reflected in `ytdGrossIncome` regardless of whether the current year's bonus assumption comes from `expectedAnnualBonus` or historical averaging.

**Commission/overtime correctly excluded from future projection.** `AssetSimulationEngineImpl.java:437-440` sums only `getBaseSalary()` into the projected monthly income base (with the comment "Exclude commission for future projection to ensure perfect consistency"), and this base-only figure feeds both the Year-1 projection and the Year 2+ `IncomeExpenseCalculator.calculateSalaryForYear()` growth projection.

**Frontend wiring is real.** `user-profile-form.tsx:202-255` has the claimed "Income Assumptions" card with an Auto/Manual select and conditional `expectedAnnualBonus` input; both `profile/me/page.tsx` and `onboarding/page.tsx` load and submit both new fields.

## What remains broken (evidence)

**The reconciliation path is dead code for realistic users.** `AssetSimulationEngineImpl.java:300-325`:
```java
if (age == initialAge) {
    if (context.getProjectedRemainingNetIncomeThisYear() != null) {
        netIncome = context.getProjectedRemainingNetIncomeThisYear();      // pre-existing averaging logic
    } else {
        // ... new YearEndAdjustmentEngine reconciliation, overwrites all 4 fields
    }
```
`projectedRemainingNetIncomeThisYear` (set at lines 508-531) becomes non-null whenever the user has **any** `SalaryRecord` dated in the current calendar year — i.e., essentially every existing user with payroll history, which is precisely the population this refactor was written to fix. When that `if` branch is taken, `netIncome` is overwritten with an independent `avgMonthlyNet × remainingMonths` estimate, but `estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance` are **never touched** and remain the full-annual values assigned earlier in the method from `taxResult` — reproducing the exact original symptom (tax breakdown on a full-year basis, `netIncome` on a partial-year basis, in the same row). The new reconciliation logic only executes when `salaryCountThisYear == 0` — a brand-new-user edge case where `ytdGross`/`ytdIncomeTax` are also all `0`, making the "reconciliation" degenerate to `remaining = trueAnnual − 0`, not meaningfully different from doing nothing.

**No test exercises the realistic branch or non-zero tax reconciliation.** `AssetSimulationEngineImplTest.java`'s `@BeforeEach` stubs `salaryRecordRepository.findAllByOrderByPaymentDateDesc()` to return `Collections.emptyList()` (line ~167-168) and no test ever constructs a `SalaryRecord` — every test in the suite exercises only the dead-for-real-users `else` branch. Additionally `taxEngine.calculate(...)` is globally stubbed to return all-zero tax components (line ~195-202), so even that branch is only ever exercised with a trivial `0 − 0 = 0` reconciliation. The realistic case identified above has zero test coverage.

## Additional minor issues noted (same scope, not required to fix now)
- **Possible current-month double-count**: `AssetSimulationEngineImpl.java:191-199`'s `monthsToAdd = 12 − currentMonth + 1` includes the current month in the forward projection; if the current month's salary record has already posted, it's counted once in `ytdBaseSalary` and once again in the projection — an off-by-one that depends on payroll posting timing.
- **Frontend/backend default mismatch**: `useHistoricalBonusForProjection` defaults to `true` in `user-profile-form.tsx`'s initial state but `false` in `UserProfile.java:191`/`UserProfileServiceImpl.java:99` — low risk since both pages explicitly submit the field, but worth aligning.

## Failure scenario
Unchanged from the original finding, for any user with at least one `SalaryRecord` in the current calendar year (the normal case, not a new-user edge case): the Year-1 row's `estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance` are still computed on a full 12-month annualized basis while `netIncome` is independently averaged over the remaining months — the same inconsistent-time-base display bug the finding originally reported, now just wrapped in unused reconciliation machinery that never runs for this population.

## Suggested direction (not required by this review, noted for scoping a fix)
Route the `age == initialAge` branch through `YearEndAdjustmentEngine` unconditionally (or make `projectedRemainingNetIncomeThisYear`'s averaging logic itself feed the same 4-field reconciliation, rather than bypassing it), so the new machinery actually reaches the population it was built for. Add a test that populates `salaryRecordRepository` with real `SalaryRecord`s and a non-zero `taxEngine.calculate(...)` stub, then asserts all 4 Year-1 fields are internally consistent (`netIncome == grossRemaining − sum(3 tax fields)`) — this is the assertion that would have caught the dead-branch gap immediately.

## Fix Applied (2026-09-02)

**Resolution:**
The short-circuit logic has been removed to unconditionally route the first-year projection through the `YearEndAdjustmentEngine` for all users. The redundant variable `projectedRemainingNetIncomeThisYear` was completely excised from both the calculation engine and `SimulationContext`.

**Key Changes:**
1. **Removed the Bypass (`AssetSimulationEngineImpl.java`):**
   Removed the `if (context.getProjectedRemainingNetIncomeThisYear() != null)` check. Year 0 (`age == initialAge`) now natively and unconditionally executes the `yearEndAdjustmentEngine.calculateYearEndAdjustment(...)` pathway to calculate `netIncome` alongside the three tax/insurance fields.
2. **Cleaned up `SimulationContext` state:**
   Completely removed `projectedRemainingNetIncomeThisYear` (and its associated assignment logic) since its averaging responsibilities have been entirely replaced by the YTD-aware reconciliation engine. Also cleaned up `totalNetThisYear` which became unused.
3. **Added Realistic Regression Test (`AssetSimulationEngineImplTest.java`):**
   Introduced a new test case `shouldReconcileTaxesAndNetIncomeUsingYearEndAdjustmentEngineForYear0` that populates `salaryRecordRepository` with a real mock `SalaryRecord` array. Verified that the output's 4 fields perfectly respect `netIncome == grossRemaining - sum(3 tax fields)` when given a non-zero mocked output from the `taxEngine`.
