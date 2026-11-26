# Fixed-48：過渡年當中，日期已過的「一次性」生活事件支出仍會被整筆重複計入年度支出，跟已經反映在真實銀行餘額裡的支出重複扣款

## 現況說明 (Current State)

[IncomeExpenseCalculator.calculateExpensesForYear](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)（152-186 行）處理三種頻率的 `LifeEvent`：

```java
// 166-170 行：ONE_TIME
if (event.getFrequency() == LifeEventFrequency.ONE_TIME) {
    if (event.getEventDate().getYear() == year) {
        totalExpense += event.getAmount();
    }
}
// ...
// 172 行附近：YEARLY 會依剩餘月份比例折算
// 176 行：MONTHLY 會用 startMonth = Math.max(startMonth, currentMonth) 排除已過月份
```

`MONTHLY` 頻率的事件在過渡年會用 `Math.max(startMonth, currentMonth)` 排除掉已經發生（已過）的月份，`YEARLY` 也會依剩餘月份比例折算；但 `ONE_TIME` 只檢查「事件年份是否等於今年」，**完全不檢查事件的月份/日期是否已經過去**。

## 問題原因 (Cause)

在過渡年執行模擬時，使用者是從某個真實的時間點（例如 9 月）往後模擬。這個時間點的 `currentLiquidCash` 起始餘額，本身已經是「已經發生的支出（包含今年已經過去月份的一次性支出）都已經被實際扣款之後」的真實銀行餘額。

如果某個 `ONE_TIME` 事件的 `eventDate` 是今年 3 月（已經過去），但模擬是從 9 月開始跑，目前的程式碼仍然會因為「年份相同」而把這筆金額整筆計入 `totalExpense`，等於是「這筆錢已經在真實世界從銀行扣過一次（反映在起始餘額裡），模擬又在剩餘月份的支出裡把它扣了第二次」，導致過渡年的剩餘現金被低估。這跟 `MONTHLY`/`YEARLY` 頻率已經正確處理「排除已過月份/按比例折算」的邏輯不一致，是 `ONE_TIME` 分支獨有的遺漏。

## 建議修正方向 (Suggested Fix)

`ONE_TIME` 事件在過渡年應該額外檢查事件月份是否已經過去（小於等於當前模擬月份），若已過去則不應計入剩餘年度的 `totalExpense`（因為它已經反映在真實起始餘額裡了），只有事件月份落在「剩餘月份」範圍內時才計入。

## 最小範例程式碼 (Sample Code)

```java
// IncomeExpenseCalculator.calculateExpensesForYear：
// ONE_TIME 事件在過渡年也要排除已經過去的月份，避免跟真實起始餘額重複扣款
if (event.getFrequency() == LifeEventFrequency.ONE_TIME) {
    boolean sameYear = event.getEventDate().getYear() == year;
    boolean alreadyOccurred = isTransitionYear
            && event.getEventDate().getMonthValue() < currentMonth; // 新增：已過月份的一次性支出不重複計入
    if (sameYear && !alreadyOccurred) {
        totalExpense += event.getAmount();
    }
}
```

## 修正報告 (Resolution Report)

我們已經在 `IncomeExpenseCalculator.java` 針對 `ONE_TIME` 頻率的事件補上了當初遺漏的「已過月份排除」防呆邏輯。

### 1. 修正邏輯說明
原本的程式碼只檢查了年份（`year == eventStartYear`），導致在過渡年中，即便是早於當前真實月份（`currentMonth`）的一次性事件也會被扣除，進而引發 Double Count（因為初始存款中已經扣過這筆真實支出）。

我們補上的邏輯如下：
```java
boolean isTransitionYear = (year == context.getCurrentYear());
boolean alreadyOccurred = isTransitionYear && (event.getStartYearMonth().getMonthValue() < currentMonth);
if (!alreadyOccurred) {
    annualExpenses += eventCost;
}
```

### 2. 修正成果
經過此修改：
- 若使用者在 9 月起跑模擬，並在同年 3 月設定了買車（一次性支出），該筆支出會被判定為 `alreadyOccurred = true` 並完美跳過，不再重複扣除。
- 其他非過渡年的一次性支出，或是過渡年中尚未發生的月份（例如 11 月）的一次性支出，則完全不受影響，照常計入。
- 此邏輯與 `YEARLY` / `MONTHLY` 的防呆機制齊平，至此所有頻率的 LifeEvent 皆已具備完善的過渡年保護機制。
