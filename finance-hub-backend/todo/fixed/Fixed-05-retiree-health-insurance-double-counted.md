# [HIGH] Retiree health/social insurance premium is charged twice for every year a retiree also receives pension

## Severity: High (systematically understates net savings for the entire pensionStartAge–74 window, producing an artificially early apparent cash-bankruptcy year)

## Summary
Once a retiree starts receiving pension income, the simulation charges National-Health-Insurance
-equivalent premiums **twice**: once embedded inside `ActiveTaxCalculationEngine.calculatePensionTax()`
as a 9.5%-of-pension "social insurance" deduction, and a second time as a flat
`TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM` (¥400,000/yr) added directly to `annualExpenses`, for
every age from `pensionStartAge` up to 74. The two code paths were written independently with
overlapping, uncoordinated intent (both comments describe essentially the same real-world cost —
"Social Insurance for retirees" vs. "must pay National Health Insurance") with nothing making them
mutually exclusive.

## Evidence

[ActiveTaxCalculationEngine.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) lines 273-274:
```java
// Estimate Social Insurance for retirees (approx 5% of pension income)
long socialInsurance = (long) (pensionIncome * 0.095);
```
This is already subtracted from `netIncome` at line 290: `netIncome = pensionIncome - socialInsurance
- incomeTax - inhabitantTax;`.

[AssetSimulationEngineImpl.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java) lines 263-271:
```java
if ((context.isRetired() || effectiveAnnualSalary == 0) && age >= 20 && age < 75) {
    // In addition to pension, early retirees or non-working individuals must pay National Health Insurance.
    long healthPremium = TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM;
    ...
    annualExpenses += healthPremium;
}
```
This condition has no dependency on `pensionIncome` or `pensionStartAge` — it fires purely off
`isRetired()`/salary==0 and the 20-75 age band, so it is not mutually exclusive with the pension-tax
deduction above.

`netSavings = netIncome - annualExpenses` absorbs both charges in the same subtraction, and neither
this file nor `IncomeExpenseCalculator` reconciles or guards against the overlap.

## Failure scenario
Default config: `targetRetirementAge=65`, `pensionStartAge=65` (the constructor invariant enforces
`pensionStartAge >= targetRetirementAge`, so this is the common/default case). At age 65 the person
is retired and receives pension income, e.g. ¥2,000,000/yr basic+kousei combined:

1. `taxEngine.calculatePensionTax(2_000_000, 65)` computes `socialInsurance = 2,000,000 * 0.095 =
   ¥190,000`, already subtracted from `netIncome`.
2. Because `isRetired()==true` and age(65) < 75, the block at lines 263-271 **also** adds a full
   ¥400,000 into `annualExpenses` for that same year.
3. `netSavings` absorbs both charges (~¥590,000 combined, ~29.5% of pension income) every single
   year from age 65 through age 74 (10 years).

This directly and repeatedly understates net savings during exactly the retirement window that
drives the year-by-year liquid-cash trajectory used to detect "cash bankruptcy" — the simulation
will report an earlier bankruptcy year than the household's real cash flow would produce. Before
`pensionStartAge` (`pensionIncome==0`) there is no overlap, so the bug is specific to the
pension-receiving window, not the whole retirement period.

## Suggested direction (not required by this review, noted for scoping a fix)
Pick one line item to model this cost, not two: either remove the flat ¥400,000
`RETIREE_HEALTH_INSURANCE_PREMIUM` expense addition once pension income is present, or remove the
9.5%-of-pension `socialInsurance` deduction and rely solely on the flat premium (with any real
nursing-care/介護保険 premium modeled as an explicit, separate, intentionally-named line item if that
distinction is actually wanted).

---

## Developer Notes (Discussion & Proposed Mitigation)
Status: **Proposed Mitigation (Mutual Exclusivity) - Requesting Feedback**

經過詳細的領域邏輯確認，我們同意這個 Finding 指出的「重複扣款 (Double Counting)」問題確實存在且影響嚴重（尤其是在 65~74 歲領取養老金的區間）。但我們**不同意** Review 建議中的「二選一刪除 (Pick one line item... either remove... or remove...)」方向。

**原因與領域邏輯考量：**
這兩段程式碼分別負擔了不同生命週期的健康保險費計算，缺一不可：
1. **比例扣款 (`pensionIncome * 0.095`) 的必要性**：日本國民健康保險費實務上是依據「前一年所得」計算的，因此當退休者開始領取養老金時，按養老金比例扣款是貼近真實稅制的做法。
2. **定額扣款 (`400,000` 預設值) 的必要性**：如果我們聽從建議直接把定額扣款刪除，將會導致一個嚴重的漏洞。假設使用者選擇在 50 歲「提早退休 (FIRE)」，在 50~64 歲這段期間，他既沒有薪資所得 (`effectiveAnnualSalary == 0`) 也還沒有領取養老金 (`pensionIncome == 0`)。如果單靠比例扣款，算出來的保險費會是 0，這在日本是不可能的（提早退休者仍需繳納國民健保的底限費用）。因此，這個 40 萬其實是針對「吃老本區間」設計的保底健保費。

**預計解法：**
這並非一個「二選一」的冗餘設計，而是一個「缺乏狀態互斥」的 Bug。
我們計畫在 `AssetSimulationEngineImpl` 中加入互斥判斷條件：
- **當 `pensionIncome > 0` 時**：僅透過 `calculatePensionTax` 的 9.5% 機制扣款，停止觸發 40 萬的定額扣款。
- **當 `pensionIncome == 0` 且 `effectiveAnnualSalary == 0` 時**：這代表處於吃老本的提早退休區間，此時才觸發定額的保底保險費。

---
**@Claude (Review Request):**
We acknowledge the severity of the double-counting bug during the pension years (65-74). However, we plan to fix this by introducing a **mutually exclusive condition** rather than removing one of the code paths entirely as suggested. 

The 9.5% proportional deduction is needed for accurate modeling during pension years, while the flat 400k deduction acts as a necessary "baseline premium" for early retirees (e.g., ages 50-64) who have neither salary nor pension income yet. Removing the flat deduction entirely would falsely result in zero health insurance premiums during early retirement. 

Do you agree that making these two logic blocks mutually exclusive (if pension > 0 use proportional; else use flat baseline) is a more robust and realistic resolution than purely removing one? We welcome your feedback!

---

# Resolve Retiree Health Insurance Overlap (Fixed-05)

This implementation plan addresses Fixed-05 from the recent Claude review regarding the double-counting of retiree health insurance premiums.

## Proposed Changes

### Fix Fixed-05: Make Retiree Health Insurance Premiums Mutually Exclusive

**Component**: Core Asset Simulation Engine
Currently, retirees are charged health insurance twice when receiving a pension: once as a 9.5% proportional deduction from the pension, and again as a flat 400,000 JPY baseline premium. We will make these mechanisms mutually exclusive.

#### [MODIFY] `AssetSimulationEngineImpl.java`
- Extract the `pensionIncome` variable declaration so it is visible outside the `if (context.isRetired())` block.
  ```java
  long pensionIncome = 0;
  if (context.isRetired()) {
      // ... calculate pensionIncome ...
  }
  ```
- Update the condition for the flat baseline premium to ensure it only fires when there is neither salary nor pension income.
  ```java
  // Change from:
  // if ((context.isRetired() || effectiveAnnualSalary == 0) && age >= 20 && age < 75) { ... }
  // 
  // Change to:
  if ((context.isRetired() || effectiveAnnualSalary == 0) && age >= 20 && age < 75 && pensionIncome == 0) {
      long healthPremium = TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM;
      ...
  }
  ```
- This ensures early retirees (who have neither salary nor pension) still pay the flat baseline premium, but once the pension starts, they only pay the proportional 9.5% tax.

---

## Final Resolution
Status: **RESOLVED**
- The fix has been implemented precisely as proposed above.
- A new branch `feature/fix-finding-05` was created to isolate this change.
- `AssetSimulationEngineImpl.java` has been updated with the `pensionIncome == 0` mutual exclusivity condition.
- The double-counting of retiree health insurance premiums during the pension-receiving years is successfully mitigated, while correctly preserving the baseline premium for early retirees.
