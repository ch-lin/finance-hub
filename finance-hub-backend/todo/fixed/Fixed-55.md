# Fixed-55：提前還款的時間點差異 (Timing Shift) 導致現有測試案例失敗

## 相關檔案與 Package 對照表 (Files Involved)

| Class / 檔案 | 專案 | 檔案路徑 |
|---|---|---|
| `MortgageCalculator.java` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java` |
| `AssetSimulationEngineImplTest.java` | `ch.lin.finance.hub.backend.api.app.service.simulation.core` | `hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java` |

## 現況說明與問題原因 (Current State & Cause)

在進行變更範圍的原始碼審查 (Source Code Review) 時，發現 `AssetSimulationEngineImplTest.shouldSimulateMortgageAmortizationAndEarlyRepaymentAtRetirement` 這個測試案例發生了數學上的失敗：
* **Expected (原本預期的一年後現金)**：`14,668,673` JPY
* **Actual (實際計算的一年後現金)**：`14,676,756` JPY
* **差異**：剛好多了 `8,083` JPY。

**原因分析：**
這個 `8,083` JPY 剛好是該筆房貸「第 12 個月」的利息。

1. **舊的保母機制 (Nanny Mechanism)**：
   舊的 `processEarlyRepaymentAtRetirement` 是放在 `YearlySimulationPipeline` 中執行的。它的執行時間點是在 `MortgageCalculator` 完全跑完「整整 12 個月」的常規還款迴圈**之後**。這意味著在舊機制下，使用者會完整支付 12 個月的本金與利息，然後系統才用現金把「剩餘餘額」一次結清。

2. **新的排程機制 (Feature A)**：
   根據 Fixed-44，提前還款邏輯被移入了 `MortgageCalculator.simulateMortgagesForYear` 迴圈內，並透過 `getOverrideEarlyRepaymentAmount` 取得覆蓋金額。由於該方法預設會在 `logicalMonth == 12`（或指定的還款月份）觸發，當迴圈跑到第 12 個月時，系統會**先**結清本金餘額（因為扣除 `Long.MAX_VALUE` 導致 `balance <= 0` 觸發 `break`），這直接導致**跳過了該月份常規還款的利息計算**。

## 邊際效應評估與建議 (Impact & Suggested Fix)

這並不是一個傳統意義上的 Bug，反而是一個**數學上更合理的行為修正**：如果你在當月提早結清了房貸（相當於月初就結清），你本來就不該再被收取當月的完整利息。

**建議方向：**
系統運算邏輯沒有錯，此為「行為改變 (Behavior Change)」導致的邊際效應。
不需要修改 `MortgageCalculator` 的核心演算法，但必須**修正測試案例 (`AssetSimulationEngineImplTest`)**，讓測試的預期結果（Expected Value）少扣除最後一個月的利息，使其與新的（且更合理的）排程型提早還款數學模型對齊。

## 修正與結案報告 (Update & Resolution)

**狀態：已結案 (Already Fixed)**

這是我（Gemini）在檢視舊版錯誤日誌時的誤判。經過重新確認最新 codebase 與 `mvn clean install` 的執行結果：

1. `AssetSimulationEngineImplTest` 中的測試迴圈**早就已經被修正為 `for (int month = 1; month <= 11; month++)`**。
2. 測試環境的預期現金 (`expectedYear1Cash`) 已經確實移除了第 12 個月的利息 (`8,083` JPY)，使其完全與新的排程型還款數學模型對齊。
3. 目前所有的 121 個測試案例皆全數通過，覆蓋率檢查也正常。

**結論**：此邊際效應確實存在（數學模型改變），但在我們先前的實作中**已經同步將測試案例修正完畢**，因此這個 Fixed-55 僅作為架構演進的歷史紀錄，不需要再進行任何程式碼更動。
