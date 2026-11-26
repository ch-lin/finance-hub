# Fix First Year Double Deduction Bug

這份計畫書解釋了為什麼第一年的 `Net Savings` 會出現 -370 萬的極端負值，以及我們該如何修正。

## The Bug: 到底是第幾行的哪個內容計算錯了？

在 `YearlySimulationPipeline.java` 中，有兩段程式碼在處理第一年的淨利時發生了嚴重的衝突。

### 第一處：`applyYearEndAdjustment` (Line 217)
```java
YearEndAdjustmentResult adj = yearEndAdjustmentEngine.calculateYearEndAdjustment(...);
state.setNetIncome(adj.remainingNetIncome()); // <--- 問題點
```
- **發生了什麼事**：`yearEndAdjustmentEngine` 在計算退稅時，順便算出了「今年剩餘月份的淨利 (`remainingNetIncome`)」。這個「剩餘月份的淨利」在數學上等於「今年度總淨利」減去「你已經上傳的 YTD 收入」。
- **錯誤所在**：第 217 行直接把這個「剩餘月份的淨利」，**強制覆寫**了 `state` 裡原本記錄的「今年度總淨利」。

### 第二處：`applyCashflowAndSettlement` (Lines 258-265)
```java
long fullYearNetIncome = state.getNetIncome();
long ytdNetIncome = 0;

if (context.getYear() == context.getCurrentYear()) {
    ytdNetIncome = context.getYtdNetAmount();
}

long remainingNetIncome = fullYearNetIncome - ytdNetIncome; // <--- 衝突點
```
- **發生了什麼事**：這是為了防止「薪資單與銀行存款重複計算 (Double Count)」所寫的防呆邏輯。它預期 `fullYearNetIncome` 變數裡面裝的是「今年度的總淨利」，然後它負責把 YTD 收入扣除，算出剩下的現金流。

## 為什麼會發生錯誤 (The Reason)？

這是一個標準的 **雙重扣除 (Double Deduction)** 錯誤。

因為第一處的 Line 217 已經把 `state.getNetIncome()` 覆寫成了 **「剩餘月份的淨利」** (以你的截圖為例，大約是 387 萬，這已經扣除過一次 YTD 了)。
當程式走到第二處的 Line 265 時，它拿著 387 萬去執行 `387萬 - ytdNetIncome (約613萬)`。
這導致算出來的 `remainingNetIncome` 變成 **-226 萬**。扣除掉這幾個月的生活開銷後，最終的 `Net Savings` 就變成了你在畫面上看到的 **-370 萬**。

> [!IMPORTANT]
> 簡單來說，YTD 收入被系統連續扣除了兩次。一次在年末調整的引擎裡，一次在結算現金流的防呆邏輯裡。

---

## 修正方向 (Proposed Changes)

為了解決這個問題，我們只需要確保 YTD 收入**只被扣除一次**。

### 推薦方案：修正 `applyYearEndAdjustment`，讓 `state` 保持今年度總計數字

我們將刪除 `applyYearEndAdjustment` 中覆寫 `state` 淨利的程式碼，但保留你在 `applyCashflowAndSettlement` 裡寫的防 Double Count 邏輯。

#### [MODIFY] `YearlySimulationPipeline.java`
```diff
    private void applyYearEndAdjustment(SimulationContext context, YearlySimulationState state, int age, int initialAge,
            long effectiveAnnualSalary) {
        if (age == initialAge) {
            long annualGross = effectiveAnnualSalary + context.getCurrentAnnualBonus();
            long ytdSocialInsurance = context.getYtdHealthInsurance() + context.getYtdPension()
                    + context.getYtdEmploymentInsurance();
            YearEndAdjustmentResult adj = yearEndAdjustmentEngine.calculateYearEndAdjustment(
                    annualGross,
                    state.getEstimatedIncomeTax(),
                    state.getEstimatedInhabitantTax(),
                    state.getEstimatedSocialInsurance(),
                    context.getYtdGrossIncome(),
                    context.getYtdIncomeTax(),
                    context.getYtdResidentTax(),
                    ytdSocialInsurance);
                    
-           state.setNetIncome(adj.remainingNetIncome());
            state.setEstimatedIncomeTax(adj.remainingIncomeTax());
            state.setEstimatedInhabitantTax(adj.remainingInhabitantTax());
            state.setEstimatedSocialInsurance(adj.remainingSocialInsurance());

            if (context.getProjectedAnnualEsppThisYear() != null) {
                state.setNetIncome(state.getNetIncome() + context.getProjectedAnnualEsppThisYear());
            }
        }
    }
```

### 為什麼這個方向最好？

1. **表格顯示正確**：UI 表格的第一年 `Salary` 顯示的是今年度總數字。如果我們不覆寫 `state` 的淨利，表格上的 `Net Income` 也會一致顯示「今年度總數字」，不會出現不成比例的超低淨利。
2. **現金流正確**：因為 `state` 保持著今年度總淨利，當程式走到 `applyCashflowAndSettlement` 時，你寫的 `fullYearNetIncome - ytdNetIncome` 就能完美運作，精準扣除一次 YTD 收入，算出正確的剩餘月份現金流。

## User Review Required

請確認這個雙重扣除的分析是否解答了你的疑惑，以及這個修正方向是否符合你的預期？如果同意，請點擊 Proceed，這份計畫書將作為本次 1 行程式碼刪除的正確備份。
