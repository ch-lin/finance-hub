# Fix Fixed-50-02: Shared `monthsToAdd` Side-effect

修正過渡年計算中，因共用變數 `monthsToAdd` 連帶影響到生活費、保費、NISA 提撥與年金計算的 Bug。將「當月薪資是否入帳」的判斷限縮於薪資推算邏輯內部，確保其他各項支出與扣款恢復正確的「含當月」基準。

## User Review Required

請確認此份計畫書。這是一個純後端修改，變動範圍集中在 `YearlySimulationPipeline.java` 的過渡年計算分支，完全不影響其他的架構或前端顯示。確認無誤後請給予「同意 (Approve)」，我便會自動執行修改。

## Proposed Changes

---

### Backend Logic

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **位置**: `processSimulationYear` 方法內的 `age == initialAge` 分支 (約第 84-104 行)。
- **Action**:
  1. 將全域共用的 `monthsToAdd` 恢復為永遠包含當月的客觀物理時間（即 `12 - currentMonth + 1`），供後續的支出、提撥、年金等公式正常使用。
  2. 在計算薪資的 `if (age == initialAge)` 區塊內，新增一個專門用於薪資的區域變數 `salaryMonthsToAdd`。
  3. 將 `context.isCurrentMonthSalaryRecorded()` 的判斷搬移給 `salaryMonthsToAdd`，決定薪資推算是否要剔除當月。
  4. 將原本傳給 `incomeExpenseCalculator.calculateSalaryForYear` 的第四個參數由 `monthsToAdd` 替換為 `salaryMonthsToAdd`。

**修改後程式碼預期長相**:
```java
        int monthsToAdd = 12;
        int currentMonth = nowMonth.getMonthValue();
        if (age == initialAge) {
            monthsToAdd = 12 - currentMonth + 1; // 恢復為永遠含當月，供給支出與年金使用
        }

        long annualizedSalaryRate;
        long effectiveAnnualSalary;

        if (age == initialAge) {
            // 薪資專用的變數：如果薪資已領，推算時就不包含當月
            int salaryMonthsToAdd = context.isCurrentMonthSalaryRecorded()
                    ? 12 - currentMonth
                    : monthsToAdd;
            effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
                    context, careerEvents, context.getYtdBaseSalary(), salaryMonthsToAdd);
            annualizedSalaryRate = context.getCurrentAnnualSalary();
        } else {
            annualizedSalaryRate = incomeExpenseCalculator.calculateSalaryForYear(context, careerEvents);
            effectiveAnnualSalary = annualizedSalaryRate;
        }
        
        // 支出拿到的仍是原本包含當月的 monthsToAdd
        long annualExpenses = incomeExpenseCalculator.calculateExpensesForYear(context, lifeEvents, monthsToAdd,
                currentMonth);
```

## Verification Plan

### Manual Verification
1. 設定情境：當月薪資單已經輸入系統。
2. 執行模擬，檢查當年（第一年）的 `Annual Expenses` 與 `Liquid Cash` 變化。
3. 確認當月的生活費（Baseline Monthly Expenses）以及 NISA / DC 等固定提撥是否已經正確被納入計算（不應憑空消失）。
4. 同時確認 `Projected Annual Salary` 維持精準（沒有把當月薪水重複加回來），確保雙方各司其職不互相干擾。
