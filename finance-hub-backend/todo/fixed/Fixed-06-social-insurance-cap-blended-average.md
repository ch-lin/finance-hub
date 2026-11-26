# [HIGH] Social-insurance monthly cap is applied to a blended salary+bonus average instead of splitting the monthly-base cap from the per-bonus cap

## Severity: High (systematically understates current-year social insurance AND the accrued kousei pension credit that determines future pension income, for any user with above-cap total compensation coming partly from bonuses)

## Note on prior review status
This exact issue was already identified in `todo/asset-simulation-gemini-review-round1.md` §2.1
("社會保險費的天花板計算被「平均化」稀釋了"). That document contains discussion of the problem and a
proposed fix approach, but — unlike the items in `todo/asset-simulation-review-round2.md` that carry
explicit `[FIXED]` tags and "Agent 補充註記" confirming the fix — there is no fix confirmation for this
item anywhere in the todo folder. Independent re-verification in this review confirms **the bug is
still present in the current code**, and additionally found a second call site
(`PensionCalculator.calculateYearlyKouseiBenefitIncrement`) with the identical defect, which
compounds the impact beyond what the original gemini review identified (it affects not just
current-year net income, but the retiree's actual future pension benefit).

## Summary
`ActiveTaxCalculationEngine.calculate()` computes `monthlySalary = grossSalary/12` and applies the
statutory monthly caps (¥1,390,000 for health, ¥650,000 for kousei pension) to that single blended
average, instead of separately capping the monthly base salary (¥650,000/mo cap) and each bonus
payment (¥1,500,000/payment cap) as Japanese 標準報酬月額/標準賞与額 rules actually require. The identical
averaging pattern is repeated in `PensionCalculator.calculateYearlyKouseiBenefitIncrement()` — the
method actually invoked in the live simulation loop (`AssetSimulationEngineImpl.java:199-200`) to
accrue the pension benefit the retiree will later receive — so the understatement compounds every
simulated working year into the retiree's projected kousei pension, not just into the current year's
net income.

## Evidence

[ActiveTaxCalculationEngine.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) lines 32-37:
```java
long monthlySalary = grossSalary / 12;
long healthBase = Math.min(monthlySalary, 1_390_000L);
long pensionBase = Math.min(monthlySalary, PensionCalculator.KOUSEI_SALARY_CAP_MONTHLY);
long annualHealth = (long) (healthBase * healthInsuranceRate * 12);
long annualPension = (long) (pensionBase * pensionRate * 12);
```
No separate bonus-cap path exists anywhere in the class.

[PensionCalculator.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/PensionCalculator.java) lines 67-71:
```java
public long calculateYearlyKouseiBenefitIncrement(long annualSalary) {
    long actualMonthly = annualSalary / 12;
    long cappedMonthly = Math.min(actualMonthly, KOUSEI_SALARY_CAP_MONTHLY);
    return Math.round(cappedMonthly * KOUSEI_MULTIPLIER * 12);
}
```
Same averaging pattern; this is the method invoked live at `AssetSimulationEngineImpl.java:199-200`
and added into `context.setAccumulatedKousei()` every simulated working year.

## Failure scenario
Employee earns ¥6,000,000/yr base salary (¥500,000/mo, under both caps) plus two bonuses of
¥2,000,000 each (¥4,000,000/yr total), so `grossSalary=¥10,000,000`.

- **Correct**: monthly pension base = ¥500,000×12 = ¥6,000,000 (under cap) + bonus base capped at
  ¥1,500,000/payment × 2 = ¥3,000,000 → total pension base ¥9,000,000 → `annualPension` ≈ ¥823,500,
  and kousei benefit credit ≈ ¥9,000,000 × 5.481/1000 ≈ ¥49,329/yr.
- **Code's blended calc**: `monthlySalary = 10,000,000/12 = ¥833,333`, `pensionBase = min(833,333,
  650,000) = ¥650,000` → `annualPension = 650,000×12×9.15% ≈ ¥713,700` (understated by ~¥110,000/yr,
  ~13%), and `calculateYearlyKouseiBenefitIncrement` gives `650,000×12×5.481/1000 ≈ ¥42,752/yr`
  (understated by ~¥6,577/yr, ~13%).

This systematically understates the current-year pension/health deduction (inflating apparent net
income now) and, more importantly, understates the accrued kousei pension credit that determines the
retiree's future pension income for the rest of the simulation — both feed directly into the
bankruptcy-year projection and compound annually for every high-bonus-earner year simulated.

## Suggested direction (not required by this review, noted for scoping a fix)
Split `grossSalary` into a base-monthly component and a bonus component (the data granularity for
this already exists upstream in `SalaryRecord`, which stores individual payslips, but is discarded
before reaching this capping logic when annualized), and apply the ¥650,000/mo cap to the base and
the ¥1,500,000/payment cap to each bonus payment separately, in both `ActiveTaxCalculationEngine`
and `PensionCalculator.calculateYearlyKouseiBenefitIncrement`.

---

## Proposed Fix Direction (For Claude's Review)
*(Agent Note: Drafted for Claude's validation before implementation)*

**Root Cause Analysis:**
Upon investigating the codebase, the root cause extends slightly upstream of the tax engine. In `AssetSimulationEngineImpl.java` (lines 359-362), the engine currently just grabs the single latest `SalaryRecord` (e.g., `salaryRecords.get(0)`) and blindly sets `baseMonthlySalary = latest.getGrossAmount()`. It completely fails to average the past 12 months (unlike the expense calculator) and discards the `baseSalary` vs `commission` (bonus) breakdown.

**Proposed Solution:**
To satisfy the exact statutory caps without introducing manual user inputs or arbitrary guessing, we propose the following fix:

1. **Extract Historical Structure (No Guessing):** 
   During initialization, scan the user's `SalaryRecord`s from the past 12 months. 
   - Sum all `baseSalary` and `commission` to calculate the exact `bonusRatio` (e.g., 40% of income comes from bonuses).
   - Count the number of payslips where `commission > 0` to determine `bonusPaymentCount` (e.g., 2 times a year).
2. **Pass Parameters Downstream:**
   Pass `bonusRatio` and `bonusPaymentCount` from the context into `ActiveTaxCalculationEngine` and `PensionCalculator`.
3. **Split and Cap (Exact Statutory Math):**
   When processing the projected `grossSalary` for future years:
   - `annualBonus = grossSalary * bonusRatio`, and `annualBase = grossSalary - annualBonus`.
   - **For Pension:** 
     - Base portion: `min(annualBase / 12, ¥650,000) * 12`
     - Bonus portion: `min(annualBonus / bonusPaymentCount, ¥1,500,000) * bonusPaymentCount`
   - **For Health Insurance:** 
     - Base portion: `min(annualBase / 12, ¥1,390,000) * 12`
     - Bonus portion: `min(annualBonus, ¥5,730,000)` (cumulative annual cap)

**Validation Request:**
Claude, does this approach fully resolve the blended average issue identified in Fixed-06? By deriving the exact historical ratio and frequency from the upstream `SalaryRecord`s, we avoid "rule of thumb" assumptions and can mathematically assert the separate caps. Would you approve this architectural direction?
