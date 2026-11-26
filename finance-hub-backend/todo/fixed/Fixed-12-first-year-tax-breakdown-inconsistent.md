# [MEDIUM] First-year tax/social-insurance breakdown fields are computed on a full 12-month salary basis while netIncome is prorated for the partial year

## Severity: Medium (display/data-consistency bug — does not corrupt the bankruptcy-year projection itself, since netSavings uses the correctly-prorated netIncome, but the displayed tax breakdown for the first simulated year is wrong)

## Summary
For the initial (partial) simulation year, `estimatedIncomeTax`/`estimatedInhabitantTax`/
`estimatedSocialInsurance` are taken straight from `taxEngine.calculate(effectiveAnnualSalary, ...)`,
which uses the full annualized salary. But `netIncome` for that same row is separately
overwritten/prorated to reflect the partial year — so the three tax fields and `netIncome` end up
computed on inconsistent time bases within the same result row.

## Evidence

[AssetSimulationEngineImpl.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java) lines 213-229:
```java
estimatedIncomeTax = taxResult.incomeTax();
estimatedInhabitantTax = taxResult.inhabitantTax();
estimatedSocialInsurance = taxResult.socialInsurance();
netIncome = taxResult.netIncome();
```
computed from `taxEngine.calculate(effectiveAnnualSalary, ...)` (full-year basis — confirmed by
`ActiveTaxCalculationEngine.calculate`: `netIncome = grossSalary - socialInsurance - incomeTax -
inhabitantTax;`).

Lines 273-283 then reassign **only** `netIncome` for `age == initialAge` (via
`projectedRemainingNetIncomeThisYear` or `Math.round(netIncome * remainingRatio)`) — never touching
`estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance`, which remain on the
full-year basis for the rest of the method.

`IncomeExpenseCalculator.calculateSalaryForYear()` returns `totalYearlySalary / 12` — an annualized
figure not scaled by how many months remain in the current (partial) year, so
`effectiveAnnualSalary` passed into `taxEngine.calculate(...)` is a full 12-month basis figure even
for the initial partial year.

## Failure scenario
Running the simulation any month other than January (e.g. August, where `monthsToAdd = 12-8+1 = 5`
and `remainingRatio = 5/12`) produces a first-year row where `netIncome` correctly reflects ~5
months of pay, but `estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance` in the
same row still reflect a full 12-month gross salary's worth of tax/insurance. The displayed
breakdown is therefore roughly `12/5 ≈ 2.4x` too high relative to the `netIncome` shown in the same
row — a genuinely wrong number in the UI, even though it does not itself change the bankruptcy-year
output (`netSavings`/`remainingCashThisYear` only use the correctly-prorated `netIncome`).

## Suggested direction (not required by this review, noted for scoping a fix)
Scale `estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance` by the same
`remainingRatio` (or equivalent partial-year projection) applied to `netIncome` at lines 273-283, so
all four fields in the first-year row reconcile on the same time basis.

---
## Alternative Architectural Refactoring (YTD Tax Deduction Approach)

**Context:**
While the suggested direction above (applying `remainingRatio` to the tax fields) resolves the UI display inconsistency, it exposes a deeper systematic distortion in the simulation's first year. By applying a perfect `remainingRatio` smoothing to the annualized net income, the system ignores the actual Year-To-Date (YTD) taxes already withheld (which are reflected in the user's current bank balance). This causes the simulation to "eat" any potential year-end tax refunds (or ignore year-end tax payments) resulting from over/under-withholding in the first half of the year, leading to a slightly inaccurate Year 1 ending asset balance.

**Proposed Refactoring Direction:**
Instead of calculating the perfect annual tax and prorating it by a ratio, the system should leverage the existing historical `SalaryRecord` data to calculate the exact remaining tax liability for the year. This naturally and accurately handles the Japanese Year-End Tax Adjustment (年末調整 - Nenmatsu Chousei).

**Implementation Steps (Prompt-Level Detail):**

1. **Calculate YTD Actuals (Separating Base and Bonus):**
   - In `AssetSimulationEngineImpl.initializeContext()`, iterate through the current year's `SalaryRecord` entities (from January up to the current month).
   - Sum up the actual gross income earned YTD (`ytdGrossIncome`), and all the actual taxes and insurances already paid YTD.
   - Importantly, isolate and sum up the `baseSalary` strictly from records where `type == MONTHLY_SALARY` into a `ytdBaseSalary` variable. This separation is required to prevent double-counting bonuses during the annual tax projection.
   - Store these YTD totals inside the `SimulationContext`.

2. **Project Remaining Gross Income (Conservative Approach & Variable Income UX):**
   - For the first year in the simulation loop, calculate the projected gross income *strictly for the remaining months* of the current year.
   - **Forward-looking Income Projection Rules:**
     - **Base Salary:** Only use the fixed `baseSalary` for future baseline projections.
     - **Variable Income (Commission/Overtime):** Exclude `commission` and `overtimePay` from future projections to prevent over-optimistic compounding of sporadic income.
     - **Expected Bonus (UX/Domain Change):** Do not blindly extrapolate the historical `bonus` from `SalaryRecord` into the future. Introduce an explicit "Expected Annual Bonus" setting in `UserProfile`.
     - **Bonus UX Implementation:** Provide a toggle/checkbox in the UI:
       1. *Option A (Auto):* "Use last 12 months' actual bonus (extracted from `SalaryRecord`) as future expected bonus." (Provides value to historical tracking).
       2. *Option B (Manual):* "Manually enter a conservative expected bonus amount." (Crucial for users with Japanese mortgage bonus payments - ボーナス払い - ensuring they have a safe, predictable baseline).

3. **Calculate True Annual Tax Liability (Using Isolated Base and Bonus):**
   - Calculate the first year's true annualized base salary: `effectiveAnnualSalary` = `ytdBaseSalary` + (`Remaining Months` * `Average Base Monthly Salary`).
   - Pass this precise `effectiveAnnualSalary` and the explicit `expectedAnnualBonus` into `taxEngine.calculate(...)` to determine the perfectly accurate true annual tax liability for the current year.

4. **Calculate Remaining Tax to Pay (The Magic Step):**
   - Calculate `Remaining Tax Liability` = `Total Annual Tax Liability` - `YTD Actual Taxes Paid`.
   - *Note: If `Remaining Tax Liability` is less than 0, it automatically represents a tax refund to be received in December!*

5. **Determine Remaining Net Income:**
   - Calculate `Remaining Net Income` = `Projected Remaining Gross` - `Remaining Tax Liability`.
   - Use this `Remaining Net Income` for the simulation's first-year cash flow addition.
   - For the UI breakdown, the displayed taxes for the first year should map directly to the `Remaining Tax Liability` values.

6. **Year 2 and Beyond (Standard Projection):**
   - For all subsequent years (Year 2 to retirement), **do not** apply the Year-End Adjustment logic.
   - Use the perfect, conservative projection for gross income: `Base Salary` (grown by `salaryGrowthRate`) + `Expected Annual Bonus` (configured via the UX toggle in Step 2).
   - Pass this pure projected gross income directly into `ActiveTaxCalculationEngine.calculate(...)` to get the flawless annual tax and social insurance.
   - *Rationale:* Since the projection excludes volatile variable income (commissions/overtime), the simulated withholding tax will perfectly equal the final tax liability. Therefore, a Year-End Adjustment for future years would mathematically be 0. We can safely rely on the engine's standard statutory formula without YTD reconciliation.

**Benefits:**
- **Zero Double-Counting:** The user's current bank balance already accounts for the YTD taxes. This method only projects the truly unpaid future taxes.
- **Automatic Year-End Adjustment:** It flawlessly simulates the real-world tax refund or tax payment that happens in December, significantly increasing the accuracy of the Year 1 ending asset projection.
- **Unified Logic:** It eliminates the need for the artificial `remainingRatio` multiplier at the end of the simulation loop, replacing it with a robust, data-driven accounting method.

## Implementation Report

The architectural refactoring (YTD Tax Deduction Approach) has been fully implemented in the backend:

1. **Domain & API Layer (`UserProfile`)**:
   - Added `expectedAnnualBonus` and `useHistoricalBonusForProjection` to the `UserProfile` entity, `UserProfileRequest`, `UserProfileResponse`, `SaveUserProfileCommand`, and `UserProfileResult`.
   - Updated `UserProfileServiceImpl` to handle persistence, allowing the user full responsibility over their future expected bonus baseline.

2. **Simulation Context Expansion**:
   - Added YTD tracking fields to `SimulationContext` (`ytdGrossIncome`, `ytdBaseSalary`, `ytdIncomeTax`, `ytdResidentTax`, `ytdHealthInsurance`, `ytdPension`, `ytdEmploymentInsurance`).

3. **Year-End Adjustment Engine**:
   - Created `YearEndAdjustmentEngine` to purely handle the mathematical reconciliation (Delta) between the True Annual Tax Liability (calculated by `ActiveTaxCalculationEngine`) and the taxes already withheld YTD.

4. **AssetSimulationEngineImpl Refactoring**:
   - Updated `initializeContext()` to accurately sum YTD actuals directly from `SalaryRecord`s (crucially isolating `ytdBaseSalary` to prevent phantom cash flow and bonus double-counting).
   - Excluded volatile commissions from future baseline projection to ensure perfect consistency in Year 2+.
   - Replaced the flawed `remainingRatio` logic in the Year 1 loop with the precise `YearEndAdjustmentEngine` call, ensuring that the first year's tax rate is perfectly matched to the true calendar year income, and the remaining net income flawlessly reconciles the YTD cashflow.

5. **Year 0 UI Tax Breakdown Consistency (Fixed-12 Resolved)**:
   - Created `YearEndAdjustmentResult` to hold the fully reconciled remaining taxes (Income Tax, Resident Tax, Social Insurance) alongside the remaining net income.
   - Updated `YearEndAdjustmentEngine` to return this composite record instead of a single primitive value.
   - Modified the Year 1 loop in `AssetSimulationEngineImpl` to unpack these exact remaining tax values and overwrite `estimatedIncomeTax`, `estimatedInhabitantTax`, and `estimatedSocialInsurance`. This strictly aligns the UI tax breakdown with the prorated net income, perfectly resolving the inconsistency originally reported in Fixed-12.

6. **Frontend UI Integration (`hub-ui`)**:
   - Created a dedicated **Income Assumptions** section in the `UserProfileForm` to logically group future income-related settings (Bonus Projection Method, Expected Annual Bonus, and Salary Growth Rate).
   - Added a UI toggle allowing users to choose between "Auto" (using historical `SalaryRecord` bonus data) and "Manual" (inputting a conservative fixed `expectedAnnualBonus`).
   - Updated the form state management and submit payloads in both `profile/me/page.tsx` and `onboarding/page.tsx` to ensure the new fields seamlessly integrate with the `UserProfile` API and backend simulation logic.
