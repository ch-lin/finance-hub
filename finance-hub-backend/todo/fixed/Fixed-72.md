# Fixed 72：子女教育費用投影把「年齡」當成「模擬年份」傳入，導致投影結果永遠為零

## 嚴重程度
High

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImpl.java:130-184`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/EducationExpenseCalculator.java`

## 現狀

```java
for (int age = 0; age <= 21; age++) {
    long cost = educationExpenseCalculator.calculateAnnualCost(child, age, config);
    ...
    projection.add(EducationProjectionResult.builder()
        .year(child.getBirthDate().getYear() + age)
        .age(age)
        .cost(cost)
        ...);
}
```

迴圈變數 `age`（範圍 0-21）被直接當作 `simulationYear` 參數傳入 `calculateAnnualCost(ChildProfile child, int simulationYear, FinanceHubConfig config)`。該方法內部會計算：

```java
childAge = simulationYear - academicBirthYear;
if (childAge < 0 || childAge > 21) return 0L;
```

## 原因
`academicBirthYear` 是一個真實的西元年（大約 2000 年以後），但傳入的 `simulationYear` 卻只是 0 到 21 的小數字，導致 `childAge` 永遠是一個深度負值，`childAge < 0` 的防呆條件在每一次呼叫時都會成立——這個投影端點對每一個孩子、每一個年齡，永遠回傳教育費用為 0。有趣的是，緊接著的下一行程式碼（`child.getBirthDate().getYear() + age`）已經正確地計算出真實西元年份用於結果的 `.year()` 欄位，顯示正確的年份值是存在的，只是沒有被用在計算費用的呼叫中。

## 具體錯誤情境
使用者在系統中建立一個孩子的 Profile，並查看教育費用投影圖表。無論孩子目前幾歲、無論設定了多高的私立學費預期，投影出來的每一年教育費用都是 ¥0——這個功能對所有使用者、所有孩子都是完全失效的。

## 建議修正
將傳入 `calculateAnnualCost` 的第二個參數改為 `child.getBirthDate().getYear() + age`（真實西元年），而不是原始迴圈變數 `age`。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 均未提及教育費用投影永遠為零的問題，屬於全新發現且影響面廣（對所有使用者的教育費用功能完全失效）的問題。

## Resolution Report (已解決)
- **問題確認**：開發者在 `ChildProfileServiceImpl` 中呼叫 `calculateAnnualCost` 時，錯誤地將迴圈變數 `age` 直接當作西元年 `simulationYear` 傳入，導致計算學齡時產生深度負數，觸發防呆機制永遠回傳 0。這是一個純粹的變數傳遞錯誤，無關商務邏輯爭議。
- **修正方式**：
  - 在 `ChildProfileServiceImpl` 中，將 `child.getBirthDate().getYear() + age` 提取出一個獨立變數 `simulationYear`，並加上註解說明其用途。
  - 將正確的 `simulationYear` 傳入 `calculateAnnualCost` 函數，同時也應用於下方 Builder 的 `year` 欄位中，提高程式碼的維護度與可讀性。
- **驗證結果**：請執行單元測試以確認教育費用已能正常計算並非全為零。
