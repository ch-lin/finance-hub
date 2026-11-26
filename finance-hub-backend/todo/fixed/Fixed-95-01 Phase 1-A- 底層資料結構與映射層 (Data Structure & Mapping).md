# Fixed 95-01 Phase 1-A: 底層資料結構與映射層 (Data Structure & Mapping)

本階段目標為拆分 NISA 搜尋維度，將原先單一的 `nisaMonthlyTarget` 屬性分解為 `nisaGrowthTarget`（成長投資枠）與 `nisaTsumitateTarget`（積立投資枠）兩個獨立基因，並同步調整相關的常數設定與對應的 DTO 映射邏輯。

## User Review Required

> [!IMPORTANT]  
> 為了避免程式碼處於編譯損壞的狀態，雖然 `Chromosome.java` 的交配 (`crossover`) 與突變 (`mutate`) 方法在企劃中被歸類至後面的 GA 引擎部分，但因為它們位於 `Chromosome` 類別內，移除舊基因屬性會立刻導致這兩個方法編譯錯誤。因此，**本階段在修改 `Chromosome` 結構時，將會一併把這兩個方法中的 `nisaMonthlyTarget` 替換為 `nisaGrowthTarget` 與 `nisaTsumitateTarget` 的雙重處理邏輯**。請問這是否符合您的預期？

## Proposed Changes

---

### `OptimizationConstants` 常數更新
移除舊有的合併上限，新增成長枠與積立枠的獨立上限，並擴大 Grid Search 安全閥值。

#### [MODIFY] [OptimizationConstants.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java)
- 移除 `MAX_NISA_MONTHLY_TARGET = 300_000L`
- 新增 `MAX_NISA_GROWTH_TARGET = 200_000L`
- 新增 `MAX_NISA_TSUMITATE_TARGET = 100_000L`
- 將 `MAX_GRID_SEARCH_SPACE` 由 1000L 提高至 2000L

---

### `Chromosome` 基因結構擴充
將原本單一維度的目標，拆分為雙維度，並補齊對應的封裝與操作方法。

#### [MODIFY] [Chromosome.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java)
- 移除 `nisaMonthlyTargetLocked` 與 `nisaMonthlyTarget`
- 新增 `nisaGrowthTargetLocked` 與 `nisaGrowthTarget`
- 新增 `nisaTsumitateTargetLocked` 與 `nisaTsumitateTarget`
- 更新 `mutate` 方法，分別針對成長枠與積立枠進行隨機突變（遵守各自的 MAX 上限）。
- 更新 `crossover` 方法，均勻交配繼承這兩個新的基因屬性與 Lock 狀態。
- 移除舊有 `MAX_NISA_MONTHLY` 的內部常數參照。

---

### `ChromosomeMapper` 結果映射
確保最佳化引擎找出的基因組合，能正確透過新的 10 參數建構子塞入 `SimulationOverrides`。

#### [MODIFY] [ChromosomeMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java)
- 更新 `SimulationOverrides` 的建構子呼叫（原本為舊版的 8 參數建構子）。
- 第 1 個參數 `testNisaMonthlyContribution`（已廢棄）填入 `null`。
- 第 2 個參數 `testNisaGrowthMonthlyContribution` 填入 `chromosome.getNisaGrowthTarget()`。
- 第 3 個參數 `testNisaTsumitateMonthlyContribution` 填入 `chromosome.getNisaTsumitateTarget()`。
- 保留其餘屬性的正確對應，完成映射層橋接。

## Verification Plan

### Automated Tests
- 本階段為資料結構改動，將導致相依這些結構的 `GridSearchEngine` 等測試與實作產生編譯錯誤，這是預期中的正常現象（編譯損壞是重構過程中的防呆保障）。後續的 Phase 1-B 與 1-C 會將這些錯誤陸續修復。

### Manual Verification
- 檢視這三個檔案修改後的程式碼邏輯是否精確。

# Fixed-95-01 Phase 1-A: 底層資料結構與映射層完成報告

我們已經成功將最佳化引擎中，關於 NISA 投入目標的底層基因資料結構進行了雙維度拆分。

## 完成的修改內容

1. **常數更新 (`OptimizationConstants.java`)**：
   - 移除了舊的 `MAX_NISA_MONTHLY_TARGET` (30萬)。
   - 新增了 `MAX_NISA_GROWTH_TARGET` (20萬) 與 `MAX_NISA_TSUMITATE_TARGET` (10萬) 兩個獨立上限。
   - 將 Grid Search 的安全閥值 `MAX_GRID_SEARCH_SPACE` 提高到 2000L，以容納雙維度拆分後擴大的窮舉空間。

2. **基因結構擴充 (`Chromosome.java`)**：
   - 將舊有的 `nisaMonthlyTarget` 屬性及其 Lock 狀態刪除。
   - 新增了雙維度的 `nisaGrowthTarget` 與 `nisaTsumitateTarget` 基因屬性。
   - 同步修改了內部的 `mutate` 與 `crossover` 方法，讓這兩個新基因可以各自進行獨立的突變與均勻交配。

3. **映射層橋接 (`ChromosomeMapper.java`)**：
   - 更新了轉換邏輯，現在能精準提取雙基因數值，並對應至 `SimulationOverrides` 最新的 10 參數建構子中。

## 預期副作用 (Expected Side Effects)

如同計畫中所述，因為資料結構已經變更（舊屬性被刪除），目前整個專案會出現大量**預期內的編譯錯誤**（尤其是 `GridSearchEngine`、`GlobalOptimizerServiceImpl` 以及相關的單元測試）。這是為了確保所有相依模組都能被強制翻新。

> [!WARNING]
> 系統目前處於編譯損壞狀態，請勿直接啟動伺服器。我們將在 Phase 1-B 與 Phase 1-C 中修復這些受影響的模組。
