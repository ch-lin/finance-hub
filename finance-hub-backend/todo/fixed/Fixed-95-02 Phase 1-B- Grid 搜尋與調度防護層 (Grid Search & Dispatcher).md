# Fixed 95-02 Phase 1-B: Grid 搜尋與調度防護層 (Grid Search & Dispatcher)

本階段將實作 Grid Search 引擎的核心迴圈改寫，以及全局防護機制的公式調整，以配合 Phase 1-A 拆分出來的雙維度基因。

## User Review Required

無特別破壞性改動，皆符合預定藍圖之變更。

## Proposed Changes

---

### `GridSearchEngine` 迴圈重構
將原先的單一 NISA 搜尋迴圈，改寫為「成長枠」與「積立枠」的巢狀迴圈，將原本單一維度的 31 種組合，擴增為 `21 * 11 = 231` 種組合。

#### [MODIFY] [GridSearchEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)
- 在 `generateCombinations` 的 `case 0`：
  - 檢查 `!seed.isNisaGrowthTargetLocked() || !seed.isNisaTsumitateTargetLocked()`
  - 外層迴圈跑 `growth` 從 `0` 到 `200,000`（步長 `10,000`）。
  - 內層迴圈跑 `tsumitate` 從 `0` 到 `100,000`（步長 `10,000`）。
- 在 `cloneChromosome` 中，拔除已刪除的舊基因屬性拷貝，新增拷貝 `nisaGrowthTarget` 與 `nisaTsumitateTarget`（及其 Locked 狀態）。

---

### `GlobalOptimizerServiceImpl` 防護崩潰更新
修改估算公式以避免 500 錯誤，並初始化新版種子染色體。

#### [MODIFY] [GlobalOptimizerServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)
- **種子染色體初始化 (`buildSeedChromosome`)**：
  - 將 `seed.setNisaMonthlyTargetLocked` 的邏輯改為同時設定 `setNisaGrowthTargetLocked` 與 `setNisaTsumitateTargetLocked`。
  - 將從 `NisaUserSettings` 讀取的預設值，改為正確讀取 `getMonthlyGrowthContribution` 與 `getMonthlyTsumitateContribution`。
- **搜尋空間估算公式 (`calculateSearchSpace`)**：
  - 將原本乘上單一 31 種的邏輯，改為 `growthOptions (21) * tsumitateOptions (11)` 的乘積。
- **紀錄回填 (`optimize`)**：
  - 為了相容尚未拔除（將於 Phase 3 拔除）的 `appliedNisa` 紀錄，若 NISA 維度被鎖定，則暫時將從 DB 讀取的 growth 與 tsumitate 數值相加，存回 `appliedNisa`。

## Verification Plan

### Automated Tests
- 此階段改完後，`GridSearchEngine` 與 `GlobalOptimizerServiceImpl` 的編譯錯誤將會解除。
- 由於測試本身仍有舊參數，將留待 Phase 1-C（單元測試修復）一併驗證。

### Manual Verification
- 檢視這兩個類別的原始碼，確認邏輯替換無誤。
