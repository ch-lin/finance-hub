# Fixed-44: 廢除動態保母機制與強化排程型提前還款

此計畫旨在透過「減法工程」解決 Fixed-44 提到的稅金計算死結，並無縫接軌原有的 Grid Search 最佳化引擎。

## User Review Required
> [!IMPORTANT]
> 這是我們剛才討論並收斂出的「極簡」與「減法」對策。
> 1. 我們將徹底刪除 `processEarlyRepaymentAtRetirement` (保母機制) 以及它在 Pipeline 中的所有呼叫點。
> 2. 我們會在 `MortgageCalculator.java` 內部的迴圈加上防呆鎖，防止超額扣款破產。
> 3. Grid Search 產生的單一年份，由 `ChromosomeMapper` 直接轉成 `(year, Long.MAX_VALUE)` 寫入 `mortgageOverrides` map，觸發防呆鎖達到全額結清。`GlobalOptimizerResponse.mortgageEarlyRepaymentYear` **保留為 Display Only 欄位**，由後端從 map 中提取乾淨年份填入，確保前端 UX 不受影響。
>
> 請您審查此計畫。若無異議，請點擊 Proceed 授權我開始實作。

## Proposed Changes

---

### Backend Simulation Core

#### [MODIFY] [MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
- **新增防呆鎖**：在 `simulateMortgagesForYear`（約 390 行處）處理 `earlyPrincipal` 扣除餘額的地方，加上防呆判斷：
  ```java
  if (isFutureMonth) {
      if (earlyPrincipal > balance) {
          earlyPrincipal = balance;
      }
      balance -= earlyPrincipal;
      annualPayment += earlyPrincipal + earlyFee;
  }
  ```
- **橋接 Grid Search (Feature A 化)**：修改 `ChromosomeMapper.toOverrides()`，刪除 `legacyYear` 變數與 `mortgageEarlyRepaymentYear` 的映射。改為當 `gridSingleMortgageRepaymentYear != null` 時，直接將 `(year, Long.MAX_VALUE)` 插入 `mortgageOverrides` map 中所有已知的 `mortgageId`。`getOverrideEarlyRepaymentAmount` 完全不需修改，變更路徑更直接。
- **徹底刪除保母機制**：刪除 `processEarlyRepaymentAtRetirement` 方法。

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **刪除保母呼叫點**：刪除 `processMortgageEarlyRepayment` 方法，並將 `processMortgageEarlyRepayment(context, overrides)` 的所有呼叫處全部移除。這將徹底拔除年底動態結清房貸的行為，解決穅金死結。

#### [MODIFY] 清理 `@Deprecated` 欄位

隨著 Grid Search 改走 `ChromosomeMapper` 直接映射到 `mortgageOverrides` map，`mortgageEarlyRepaymentYear` 已完全失去存在意義，應一並清除：

- [SimulationOverrides.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)：刪除 `mortgageEarlyRepaymentYear` 欄位及其 `@Deprecated` 注釋。
- [GlobalOptimizerServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)：**修改**第 111 行，不再讀取（已刪除的）`optimizedOverrides.mortgageEarlyRepaymentYear()`，改為新增一個 `extractGridSearchPayoffYear(optimizedOverrides.mortgageEarlyRepayments())` 輔助方法，從 `mortgageEarlyRepayments` map 中提取含有 `Long.MAX_VALUE` 的 entry 的年份，填入回應 DTO。
- [GlobalOptimizerResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java)：**保留** `mortgageEarlyRepaymentYear` 欄位，但角色從「計算參數」轉型為「Display Only」。前端繼續讀取此欄位顯示「最佳清償年份：20XX 年」，UX 完全不受影響。`Long.MAX_VALUE` 的金額不會暴露給前端。

## Verification Plan

### Automated Tests
- 執行 `mvn test -Dtest=AssetSimulationEngineImplTest` 確保底層沙盒模擬邏輯依然正確。
- 執行 `mvn test -Dtest=GlobalOptimizerServiceTest` 確保 Grid Search 最佳化引擎依然能算出最佳的提前還款年份，且不會再被有 Bug 的保母機制干擾。

### Manual Verification
- 確認 `YearlySimulationPipeline` 中不再有任何動態結清的行為發生在 `simulateMortgagesForYear` 之後。
