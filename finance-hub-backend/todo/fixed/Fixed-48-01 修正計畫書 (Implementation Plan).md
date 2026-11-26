# Fixed 48 修正計畫書 (Implementation Plan)

為了解決過渡年（模擬起始年）中「已經發生過的一次性生活事件」遭到重複扣款的問題，我們將針對 `IncomeExpenseCalculator.java` 中的 `ONE_TIME` 分支增加與 `MONTHLY` 相同的「已過月份排除」邏輯。

> [!NOTE]
> 這個問題的核心在於：模擬起始年的初始存款餘額（`currentLiquidCash`）是真實數字，裡面已經隱含了「過去這幾個月已經花掉的錢」。如果我們在剩餘的模擬期間再次把這筆錢扣掉，就會發生 Double Count，導致當年度的結餘縮水。

## Proposed Changes

我們將修改後端計算引擎中的 `IncomeExpenseCalculator.java` 檔案：

### [MODIFY] [IncomeExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)

找到 `calculateExpensesForYear` 函式中處理 `LifeEventFrequency.ONE_TIME` 的區塊（約第 166 行）。

**原本的邏輯：**
```java
case ONE_TIME -> {
    if (year == eventStartYear) {
        annualExpenses += eventCost;
    }
}
```

**修改後的邏輯：**
```java
case ONE_TIME -> {
    if (year == eventStartYear) {
        // 判斷這筆一次性支出是否發生在「今年」，而且「月份已經過去了」
        boolean isTransitionYear = (year == context.getCurrentYear());
        boolean alreadyOccurred = isTransitionYear && (event.getStartYearMonth().getMonthValue() < currentMonth);
        
        if (!alreadyOccurred) {
            annualExpenses += eventCost;
        }
    }
}
```

## Verification Plan

1. 執行完整的後端單元測試（包含既有測試，確保沒有破壞其他預期行為）。
2. 在前端沙盒輸入一個「發生月份早於目前真實月份」的 `ONE_TIME` Life Event。
3. 觀察第一年的總支出是否已經將該筆費用成功排除，且非過渡年的一次性事件仍能正常計入。
