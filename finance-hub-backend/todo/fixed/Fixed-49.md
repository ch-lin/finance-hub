# Fixed-49：過渡年當中排定在剩餘月份的職涯事件（加薪/轉職）會被靜默忽略，模擬薪資直接用「目前月薪固定推算」蓋掉原本正確處理職涯事件的計算結果

## 現況說明 (Current State)

[YearlySimulationPipeline.java](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（82-87 行）在過渡年（`age == initialAge`）：

```java
if (age == initialAge) {
    effectiveAnnualSalary = context.getYtdBaseSalary() + monthsToAdd * context.getBaseMonthlySalary();
    annualizedSalaryRate = ...; // 同樣只用 baseMonthlySalary 推算
}
```

這段程式碼直接用「YTD 已發生薪資 + 剩餘月份 × 目前固定月薪」重新覆寫 `effectiveAnnualSalary`。但在這之前，[IncomeExpenseCalculator.calculateSalaryForYear](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)（62-109 行）本來已經正確地考慮了 `CareerEvent`（加薪、轉職等，會改變某個月份之後的薪資水準），算出一個有考慮職涯事件的 `effectiveAnnualSalary`。

## 問題原因 (Cause)

`YearlySimulationPipeline.java` 82-87 行這段「過渡年覆寫」邏輯，只看 `context.getBaseMonthlySalary()`（目前固定月薪），完全沒有檢查剩餘月份裡是否還有排定的 `CareerEvent`。如果使用者設定了一個「今年 11 月加薪」或「今年 12 月轉職，薪資變動」的職涯事件，且模擬時間點在該事件發生之前（例如 6 月），這個覆寫邏輯會用「目前月薪 × 剩餘月份」的方式，把原本 `calculateSalaryForYear` 已經正確算出的、有考慮到這個職涯事件的薪資推算結果整個蓋掉，變成好像這個職涯事件從未發生過一樣。

實際影響：使用者規劃了「今年年底加薪」，模擬結果卻完全反映不出這個加薪——過渡年的 `effectiveAnnualSalary` 會被低估（或高估，如果是降薪事件則反過來），連帶所有以此為基礎的稅金、儲蓄率、現金水位推算全部失真，且使用者不會有任何錯誤或警告提示,這個職涯事件的效果就是悄悄地在過渡年消失。

## 建議修正方向 (Suggested Fix)

過渡年的 `effectiveAnnualSalary`/`annualizedSalaryRate` 不應該無條件用「目前固定月薪 × 剩餘月份」覆寫掉 `calculateSalaryForYear` 已經算好的、有考慮 `CareerEvent` 的結果。應該讓 `calculateSalaryForYear` 本身就正確處理「YTD 已發生部分用真實值、剩餘月份部分依職涯事件推算」的過渡年邏輯，而不是在它算完之後另外用一段不知道職涯事件存在的邏輯整個蓋過去。

## 最小範例程式碼 (Sample Code)

```java
// YearlySimulationPipeline.java：
// 過渡年不應該無條件用固定月薪覆寫掉已考慮 CareerEvent 的薪資推算結果
if (age == initialAge) {
    // 錯誤：完全不看剩餘月份是否還有 CareerEvent
    // effectiveAnnualSalary = context.getYtdBaseSalary() + monthsToAdd * context.getBaseMonthlySalary();

    // 修正：讓 calculateSalaryForYear 本身處理「YTD 真實值 + 剩餘月份依 CareerEvent 推算」
    effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
            context, year, context.getYtdBaseSalary(), monthsToAdd); // 新增參數：帶入 YTD 已發生金額與剩餘月份
}
```

## 修正報告 (Resolution Report)

**狀態**: 已修復 (Resolved)

**實作細節**:
1. **IncomeExpenseCalculator.java**:
   - 擴充了 `calculateSalaryForYear` 方法，新增 `ytdSalary` 與 `monthsToAdd` 參數，專職負責處理過渡年（Transition Year）的薪資精確推算。
   - 加入了 `careerEvents` 的時間排序（依據發生年月），防止因未按時間順序傳入而導致計算錯亂。
   - 修正了「過渡年早期職涯事件」的問題：當事件發生在 `startMonth` 之前，系統會精準攔截，僅繼承加薪後的薪資水位，不會重複計算到剩餘月份加總中，徹底修復了事件被靜默吞噬的漏洞。
2. **YearlySimulationPipeline.java**:
   - 移除了 82-87 行原本「粗暴覆寫」`effectiveAnnualSalary` 的邏輯。
   - 改由 `if/else` 精準分流：在過渡年（`age == initialAge`）時，將真正的 YTD 薪水與剩餘月份傳遞給 Calculator 進行推算；完整年則維持直接推算整年。
   - 此作法確保每年僅呼叫一次 `calculateSalaryForYear`，完美避免 `SimulationContext` 內的薪水狀態被錯誤重複疊加。

**待確認風險與後續追蹤**:
在 `IncomeExpenseCalculator` 處理 `RELATIVE_CHANGE`（相對薪資變動）的乘法邏輯為 `salaryAtLastMonth * event.getEventValue()`。此計算高度依賴資料庫的存儲格式約定：
- 若「加薪 10%」於資料庫存為 `1.1`，則計算正確。
- 若存為 `0.1`，則會導致薪水變為原來的 10%（變相減薪）。
為維持本 Issue 最小變更原則，本次未做修改。強烈建議後續 Reviewer 盤點 API 契約與資料庫實務，若發現存儲為 `0.x`，須另開 Issue 修正為 `(1.0 + event.getEventValue())`。
