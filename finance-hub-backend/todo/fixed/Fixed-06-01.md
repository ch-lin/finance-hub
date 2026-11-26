# Re: FINDING-06 — the split-cap approach is correct, but it misses a second call site with the identical bug, plus two smaller gaps

## Short answer
Yes, the domain research is right: pension caps per-payment (¥650,000/mo → ¥1,500,000/bonus payment) while health insurance caps cumulatively per year (¥1,390,000/mo × 12 → ¥5,730,000/yr total bonus), and deriving `bonusRatio`/`bonusPaymentCount` from 12 months of historical `SalaryRecord`s to split `grossSalary` into base/bonus before applying each cap independently is the right shape of fix. I'd approve the architectural direction — with one addition that changes the scope of the fix, and two smaller items to close before merging.

## Gap 1 (the important one): `PensionCalculator.calculateYearlyKouseiBenefitIncrement` has the exact same bug, and your plan doesn't touch it

I checked `PensionCalculator.java` (lines 67-71):

```java
public long calculateYearlyKouseiBenefitIncrement(long annualSalary) {
    long actualMonthly = annualSalary / 12;
    long cappedMonthly = Math.min(actualMonthly, KOUSEI_SALARY_CAP_MONTHLY);
    return Math.round(cappedMonthly * KOUSEI_MULTIPLIER * 12);
}
```

This is the identical "divide annual salary by 12, cap at the monthly ceiling for all 12 months" pattern that Fixed-06 flagged in `ActiveTaxCalculationEngine` — except this one isn't computing a tax deduction or premium, it's computing how much **Kousei Pension benefit accrues** for the year. If your fix only patches `ActiveTaxCalculationEngine`, you'll end up with an internally inconsistent system: the premiums *deducted* from a high-bonus user's paycheck will be correctly split (base capped at ¥650k/mo, bonus capped at ¥1.5M/payment), but the pension *benefit* they're projected to eventually receive will still be computed from the old blended `annualSalary/12` average — silently under- or over-crediting their future pension relative to what they actually paid in.

This needs the same `bonusRatio`/`bonusPaymentCount` split applied here: split `annualSalary` into base/bonus, cap base at `KOUSEI_SALARY_CAP_MONTHLY` per month (×12) and bonus at the per-payment cap (×`bonusPaymentCount`), sum, then apply `KOUSEI_MULTIPLIER`. Whatever data structure carries the split for the tax engine should be threaded through to whatever calls `calculateYearlyKouseiBenefitIncrement` too — worth grepping call sites before considering Fixed-06 closed.

## Gap 2: cold-start / divide-by-zero guarding

You didn't specify what `bonusRatio`/`bonusPaymentCount` resolve to when there isn't 12 months of `SalaryRecord` history yet (new user, or a user who has never received a bonus). Two failure modes to guard explicitly:
- `bonusPaymentCount == 0` → make sure the per-payment cap math doesn't divide by it.
- Fewer than 12 months of history → decide whether you extrapolate from what exists, or fall back to the current flat `grossSalary/12` behavior. Falling back is actually fine here (it degrades to today's exact behavior when there's no bonus signal), just make sure that's a deliberate branch and not an accidental `NullPointerException`/`ArithmeticException`.

## Gap 3: document the equal-split assumption

Splitting `annualBonus` evenly across `bonusPaymentCount` payments (e.g., ¥6,000,000/yr ÷ 2 = ¥3,000,000/payment) is a reasonable simplification, but real summer/winter bonuses are rarely identical amounts. That's fine as an approximation — just leave a one-line comment at the split site saying so, so a future reader doesn't mistake it for an exact reconstruction of the actual payment schedule.

## Bottom line
Approve the direction. Before marking this fixed, extend the same split to `PensionCalculator.calculateYearlyKouseiBenefitIncrement` (Gap 1 is the one I'd block on — it's not hypothetical, it's a second confirmed site with the same root cause), and close Gaps 2-3 as part of the same change.

---

## Architecture Course Correction & Clarification (To Claude)

Hi Claude, before we proceed to implementation, I need to issue a course correction regarding the architectural approach described in the previous "Proposed Fix Direction".

We previously proposed calculating a `bonusRatio` by extracting the `commission` field from the historical `SalaryRecord`s. This was based on a flawed assumption that "commission" and "bonus" are the same mechanism. 

In reality (and Japanese tax law):
1. **Commission (業績給 / コミッション)**: This is part of the regular monthly salary (給与) for sales/commission-based roles. It inflates the monthly income and is subject to the **monthly** social insurance caps (¥650,000 for pension, ¥1,390,000 for health).
2. **Bonus (賞与)**: This is a completely separate payment event. It is subject to the **bonus** social insurance caps (¥1,500,000 per payment for pension, ¥5,730,000 cumulative for health).

Therefore, treating `commission` as `bonus` would severely miscalculate social insurance caps for users with performance-based monthly pay.

### Revised Architectural Direction (Data-Driven & Zero-Assumption)

To properly support both standard monthly commission-based pay and traditional bonuses without making dangerous assumptions, we will implement the following:

#### 1. Data Model Refinement
We will introduce a discriminator, such as an enum `SalaryType` (e.g., `MONTHLY_SALARY`, `BONUS`), to `SalaryRecord` to explicitly differentiate between a monthly payslip and a bonus payslip.
- A `MONTHLY_SALARY` record will contain `baseSalary`, `commission`, `overtimePay`, etc. The sum is the monthly `grossAmount` (subject to monthly caps).
- A `BONUS` record will have `baseSalary = 0`, `commission = 0`, and the `grossAmount` represents the pure bonus amount (subject to bonus caps).

#### 2. Simulation Engine Refinement (Addressing Gaps 2 & 3)
The `AssetSimulationEngineImpl` will dynamically extract the true historical commission ratio from past `MONTHLY_SALARY` records and project it into future monthly baseline income (which is then capped by the monthly ceilings).
Simultaneously, it will extract `bonusPaymentCount` and `annualBonusAmount` **exclusively** from past `BONUS` records.

**Zero-Bonus Defensive Strategy (Addressing Gap 2 - Cold Start):**
If a user has **no** `BONUS` records in their history (e.g., new employee, or a purely commission-based foreign company), the engine will strictly assume **0 bonuses** and **0 bonus payment count**. It will not extrapolate or default to "2 months bonus". The simulation will fall back to a purely monthly-cap-based calculation, acting as the safest baseline defense against projecting unrealistic future cash flows. Once a `BONUS` record is logged by the user, the simulation will automatically upgrade to the dual-cap calculation.

Please review this revised direction to confirm it aligns with the expected tax behavior and safely closes the gaps identified in your review.

---

## Developer Note (Execution Strategy)

**Phase 1: Backend Data Model & Core Simulation Engine (Current Scope)**
- **DB & DTO**: Introduce `SalaryType` (`MONTHLY_SALARY`, `BONUS`) to `SalaryRecord`, `SalaryRecordRequest`, and `SalaryRecordResponse`. (Existing data will be reset during dev).
- **AssetSimulationEngineImpl**: Refactor the historical salary loop. 
  - `MONTHLY_SALARY` -> extracts latest base and calculates `historicalCommissionRatio`.
  - `BONUS` -> calculates `annualBonusAmount` and `bonusPaymentCount`. (Strict fallback: if 0 bonus records, assume 0 for future).
  - *Risk Warning*: This is the most hallucination-prone step. Extreme care must be taken with the loop logic and zero-checks to avoid NPEs or arithmetic errors. The backend logic will be reviewed by the user before moving further.
- **Calculators**: Update `ActiveTaxCalculationEngine` and `PensionCalculator` to accept bonus parameters and apply the "Dual-Cap" logic.
- **Unit Test Precision Fix (`ActiveTaxCalculationEngineTest`)**: During the implementation of the Dual-Cap logic, we updated the calculation for Employment Insurance (就業保險) in `ActiveTaxCalculationEngine`. Previously, the code calculated this by converting the annual salary to a monthly salary (`grossSalary / 12`, which truncates decimals) and then multiplying back by 12: `monthlySalary * employmentInsuranceRate * 12`. In the new implementation, because Employment Insurance does not have monthly or per-payment caps like Health and Pension insurances, it is mathematically more accurate to apply the rate directly to the total gross salary: `grossTotalSalary * employmentInsuranceRate`. As a result of eliminating the division-truncation error, the calculated Employment Insurance for a base salary of 8,000,000 JPY became exactly `48,000 JPY` instead of the previous `47,999 JPY`. The total `socialInsurance` assertion in `ActiveTaxCalculationEngineTest.calculate_baseCase_noDeductions` was thus explicitly updated from `1,161,698L` to `1,161,699L`, along with corresponding `netIncome` updates. This is an intended increase in precision, not a regression.

**Phase 2: Frontend UX Enhancements for SalaryType (Future Task / Out of Scope for Phase 1)**
- **Context**: In Phase 1, the backend and database were updated to support a new `SalaryType` enum (`MONTHLY_SALARY` or `BONUS`) for `SalaryRecord`s. The frontend `SalaryRecord` TypeScript interface was also updated to include the `type` field.
- **The Problem**: When a user creates or edits a salary record and sets it to `BONUS`, the fields `baseSalary`, `overtimePay`, and `commission` become mathematically irrelevant (bonuses are purely a lump sum represented by `grossAmount`). If users can still type into these fields, it causes UX confusion and potential data inconsistencies.
- **Goal (Your Task)**: Update the frontend salary form component (e.g., `SalaryForm.tsx` or the corresponding form UI) to dynamically disable irrelevant fields when the `BONUS` type is selected.
- **Specific Implementation Requirements**:
  1. Add a UI control (e.g., Radio Group or Select Dropdown) to let the user choose the `SalaryType` (`MONTHLY_SALARY` vs `BONUS`) in the form.
  2. Implement an observer/listener on the form's state for the `type` field.
  3. **When `type === 'BONUS'`**:
     - Automatically force the form state values of `baseSalary`, `overtimePay`, and `commission` to `0`.
     - Add the `disabled={true}` attribute to the input components for `baseSalary`, `overtimePay`, and `commission`.
     - Add a `title` attribute (or a UI Tooltip component) to these disabled inputs with the exact English text: `"Not applicable for bonus payments."` This explains to the user *why* the fields are locked.
  4. **When `type === 'MONTHLY_SALARY'`**:
     - Ensure the fields are enabled (`disabled={false}`) and users can input values normally.
- **Note to Future Agent**: Execute ONLY this UI constraint logic. Do not touch the backend logic which has already been verified in Phase 1.

---

## Implementation Report (To Claude)

Hi Claude,

The backend Phase 1 implementation for Fixed-06 has been fully completed, successfully verified with passing unit and integration tests, and merged. Here is a summary of how we addressed the gaps you identified:

1. **Gap 1 (Kousei Pension Benefit)**: We extended the Dual-Cap logic directly into `PensionCalculator.calculateYearlyKouseiBenefitIncrement`. The algorithm now accepts the parsed `annualBonus` and `bonusPaymentCount`. It applies the monthly cap (`KOUSEI_SALARY_CAP_MONTHLY` = 650,000 JPY) strictly to the monthly baseline, and independently applies the per-payment cap (`KOUSEI_BONUS_CAP_PER_PAYMENT` = 1,500,000 JPY) to the separated bonus structure, perfectly mirroring the tax deduction engine.
2. **Gap 2 (Zero-Bonus Fallback)**: `AssetSimulationEngineImpl` has been refactored with explicit zero-checks. If there are no `BONUS` records, `bonusPaymentCount` correctly resolves to `0`, and both `ActiveTaxCalculationEngine` and `PensionCalculator` safely default to the standard monthly math, successfully guarding against `ArithmeticException` or `NullPointerException`. 
3. **Gap 3 (Equal-Split Assumption Documented)**: We have added inline documentation regarding the equal-split simplification for bonuses.
4. **Data-Driven Restructuring (Course Correction)**: As detailed earlier, we abandoned the flawed `commission` heuristic. We introduced an explicit `SalaryType` (`MONTHLY_SALARY` vs `BONUS`) to the Database and API. This ensures we are processing ground-truth user data, not dangerous extrapolations.
5. **Bonus Precision Improvements**: During the refactor, we completely eliminated the division-truncation bug in the Employment Insurance calculation, resulting in higher mathematical fidelity (and an explicit 1 JPY correction in our test baseline).

Phase 2 (Frontend UI restrictions for SalaryType forms) has been cleanly decoupled and handed over as a separate task ticket. 

**Update on Phase 2 (Executed by Gemini):**
The frontend UI constraint logic described in Phase 2 has now been fully implemented in `add-salary-record-dialog.tsx`:
1. Added a `Select` dropdown for `SalaryType` (`MONTHLY_SALARY` vs `BONUS`).
2. Implemented a `useEffect` observer on the `type` state.
3. When `BONUS` is selected, the `baseSalary`, `overtimePay`, and `commission` fields are automatically forced to `0`, disabled (`disabled={true}`), and display the required tooltip `"Not applicable for bonus payments."`.
4. When `MONTHLY_SALARY` is selected, the fields function normally.

**Important Note (Integration Gap):**
While the backend POST/PUT endpoints for `SalaryRecord` have been scaffolded (albeit with some initial architectural missteps regarding `paymentDate` handling which had to be corrected), the frontend UI (`add-salary-record-dialog.tsx` and `use-salary-data.ts`) has **NOT** yet been connected to these new endpoints. The frontend currently still relies on a mock API call (`// Mock API call since backend doesn't support POST yet`).

Due to severe quality and architectural oversight issues during the backend API implementation by Gemini (specifically: ignoring domain invariants, leaving unresolved TODO comments in production code, and failing to handle `paymentDate` immutability correctly without prompting), the user has halted Gemini's execution. The final frontend-to-backend integration and any further architectural refinements are being handed over to Claude.

---

## Full Fix Summary — Fixed-06 (To Claude, for Second Review)

Fixed-06 has now been fully resolved across two phases. This document (`Fixed-06-01`) is the continuation and closure of that work. Here is the complete picture:

### Phase 1 — Backend Simulation & Data Model (Previously Completed)

The root cause was that the simulation engine treated all salary as uniformly monthly, blending bonuses into an average that misapplied the Japanese social insurance caps. The fix:

1. **`SalaryType` enum introduced** (`MONTHLY_SALARY` / `BONUS`) to the `SalaryRecord` domain entity, database schema, and all DTOs — separating ground-truth monthly payslips from bonus payslips with no assumptions.
2. **`AssetSimulationEngineImpl` refactored** to derive `historicalCommissionRatio`, `annualBonusAmount`, and `bonusPaymentCount` exclusively from typed historical records. Zero-bonus fallback is strict: if no `BONUS` records exist, the engine assumes no bonus income and applies only monthly caps.
3. **Dual-Cap logic applied to `ActiveTaxCalculationEngine`** — monthly base is capped at ¥650,000/mo (pension) and ¥1,390,000/mo (health); each bonus payment is independently capped at ¥1,500,000 (pension) and cumulative ¥5,730,000/yr (health).
4. **`PensionCalculator.calculateYearlyKouseiBenefitIncrement` patched** with the identical dual-cap split (Gap 1 from the original review), ensuring pension benefit accrual is internally consistent with premium deductions.
5. **Employment Insurance precision fix** — calculation now applies the rate directly to gross total salary, eliminating the 1 JPY truncation error from the old `grossSalary / 12 * 12` pattern.

### Phase 2 — Frontend `SalaryType` Form Constraints (Previously Completed)

`add-salary-record-dialog.tsx` was updated to include a `SalaryType` selector. When `BONUS` is selected, `baseSalary`, `overtimePay`, and `commission` are automatically forced to `0` and disabled with a `"Not applicable for bonus payments."` tooltip.

### Phase 3 — Frontend-Backend API Integration (Now Completed)

The frontend was previously entirely disconnected from the `POST` and `PUT` endpoints — the "Add Record" button submitted a mock toast. This has been fully wired:

- **Backend bug fixed**: `SalaryRecordController.createRecord` was importing `@RequestBody` from the Swagger documentation library instead of Spring, meaning the request body was never deserialized on `POST`. Fixed by correcting the import.
- **`add-salary-record-dialog.tsx`** upgraded to a dual-mode component: Create mode (`POST /api/finance/salaries`) and Edit mode (`PUT /api/finance/salaries/{id}`), with all deduction fields now present in the form.
- **`salary-table.tsx`** now has an Edit button per row that opens the dialog pre-populated with the existing record.
- On success, the table triggers a data refetch via a callback threaded from `page.tsx`.

---

## Request for Second Round of Deep Source Code Review

Hi Claude,

Fixed-06 is now considered fully resolved — the simulation engine, data model, and frontend CRUD flow are all aligned. We would appreciate a second-round deep review covering the following areas:

1. **Simulation correctness**: Review `AssetSimulationEngineImpl`'s salary history extraction loop and the dual-cap logic in `ActiveTaxCalculationEngine` and `PensionCalculator`. Verify there are no remaining edge cases (e.g., mixed MONTHLY_SALARY / BONUS records within the same calendar year, partial years at the start of history, `bonusPaymentCount > 12`).

2. **Data integrity at the API boundary**: Review `SalaryRecordController`, `SalaryRecordRequest`, `SalaryRecordServiceImpl`, and `SaveSalaryRecordCommand`. Specifically, check whether the immutability invariant for `paymentDate` (mentioned as a prior architectural misstep) is correctly enforced on the `PUT` path, and whether any domain invariants are missing from validation.

3. **Frontend type safety**: Review `use-salary-data.ts` (`SalaryRecord` interface) against `SalaryRecordResponse` (backend DTO). The `id` field is typed as `string?` on the frontend but `Long` on the backend; and `details` is `Record<string, string>` on the frontend but `Map<String, Long>` on the backend. Confirm whether these mismatches cause any runtime issues with the existing data.

Please share any findings, concerns, or approval.
