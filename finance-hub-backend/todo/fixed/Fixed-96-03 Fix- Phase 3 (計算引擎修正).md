# Fixed-96-03 Fix: Phase 3 (計算引擎修正)

本階段旨在修正核心模擬引擎，確保 `NisaContextBuilder` (初始化) 與 `FundOverrideApplier` (覆寫邏輯) 能正確處理分離後的「成長投資枠」與「つみたて投資枠」，避免將所有投資額都灌入單一未分類基金。

## User Review Required

> [!IMPORTANT]
> 此階段會修改引擎核心配置與覆寫邏輯。這是所有目標試算與沙盒模擬的底層，請確認我們處理「虛擬基金建立」與「比例分配」的邏輯是否符合業務預期。

## Open Questions

無。目前的邏輯完全依照藍圖與上一階段的共識進行。

## Proposed Changes

### [Engine Initialization]

將 `NisaContextBuilder` 中讀取 Profile 並建立虛擬基金的邏輯，從單一欄位改為雙欄位獨立判斷。

#### [MODIFY] [NisaContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java)
- **修改行數**：L72 - L89
- **變更內容**：
  - 移除原先對 `monthlyNisaContribution` 的依賴。
  - 改為獨立判斷：
    1. 若 `nisaUserSettingsOpt.get().getMonthlyGrowthContribution() > 0`，則建立一筆 `NisaAccountType.GROWTH` 的虛擬基金。
    2. 若 `nisaUserSettingsOpt.get().getMonthlyTsumitateContribution() > 0`，則建立一筆 `NisaAccountType.TSUMITATE` 的虛擬基金。

---

### [Engine Overrides]

將 `FundOverrideApplier` 中的全域 NISA 覆寫分配邏輯 (`testNisaMonthlyContribution`)，拆分為對 Growth 與 Tsumitate 分別進行分配。

#### [MODIFY] [FundOverrideApplier.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)
- **修改行數**：L71 - L74 以及 L94 - L150
- **變更內容**：
  - **L71-L74**：將原本檢查 `overrides.testNisaMonthlyContribution() != null` 的邏輯，改為分別檢查 `testNisaGrowthMonthlyContribution()` 與 `testNisaTsumitateMonthlyContribution()`。
  - **L94-L150**：將原本的 `applyGlobalNisaContribution` 拆分為兩個獨立的方法（或參數化方法）：
    - `applyGlobalGrowthContribution`：過濾出現有的 `GROWTH` 真實基金進行按比例分配。若無，則建立一筆 `GROWTH` 虛擬基金並賦予該測試額度。
    - `applyGlobalTsumitateContribution`：過濾出現有的 `TSUMITATE` 真實基金進行按比例分配。若無，則建立一筆 `TSUMITATE` 虛擬基金並賦予該測試額度。

## Verification Plan

### Automated Tests
無（測試的修正會留在最後或由您自行執行）。

### Manual Verification
請在修改完成後，透過 Sandbox 發送一筆同時帶有 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution` 的 Request，觀察 Engine 是否正確分配並建立兩筆獨立的基金。

# Fixed-96-03 Phase 3 完成摘要 (Walkthrough)

## 修改項目

在取得您的授權後，我們完成了 Fixed-96 Phase 3 (計算引擎修正) 的實作，確保系統底層完全支援新的雙欄位邏輯。

### 1. `NisaContextBuilder.java` (Profile 初始化)
- 移除了依賴單一 `@Deprecated monthlyNisaContribution` 建立泛用虛擬基金的邏輯。
- 改為分別判斷 `monthlyGrowthContribution` 與 `monthlyTsumitateContribution`。
- 當這兩個數值大於 0 時，會精確地建立對應的 `GROWTH` 或 `TSUMITATE` 虛擬基金並加入 `SimulationContext`。這確保了使用者單純使用 Profile 跑模擬時，不會因為未標記類型而提早踩到 1,200 萬的成長枠天花板。

### 2. `FundOverrideApplier.java` (引擎覆寫邏輯)
- 將原先一刀切的 `applyGlobalNisaContribution` 廢棄，改寫為支援泛型帳戶類別的 `applyGlobalContribution` 方法。
- 現在 Sandbox 傳入的 `testNisaGrowthMonthlyContribution` 能夠正確分配給既有的 `GROWTH` 基金群；而 `testNisaTsumitateMonthlyContribution` 則獨立分配給 `TSUMITATE` 基金群。
- 如果使用者測試了一筆金額，但在資料庫找不到對應類別的真實基金，系統也能聰明地動態建立獨立的虛擬基金 (`Simulated NISA GROWTH (Virtual)` / `Simulated NISA TSUMITATE (Virtual)`) 來吸納這筆覆寫金額。

## 驗證計畫
如藍圖所列，測試的部分保留在最後或由您接手執行。建議您可以透過 Sandbox API 發送含有 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution` 的 JSON，觀察 Engine 吐出的 `AssetSimulationYearlyResult`，確認兩者是否依照預期被分流計算。
