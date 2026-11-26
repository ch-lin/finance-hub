# Fixed-95-03 Phase 1-C: GA 引擎支援與單元測試修復

這份計畫主要是接續完成 Fixed-95 Phase 1-C，確保 Genetic Algorithm 引擎能正確處理雙維度的 NISA 設定，並全面修復因維度拆分而導致編譯與斷言失敗的單元測試。

## User Review Required

- **`SimulationOverrides` 參數修復**：在 `GlobalOptimizerServiceTest` 中，所有 `SimulationOverrides` 的建構子目前只有 8 個參數，這與目前系統中該類別的 10 個參數（新增了 Growth 與 Tsumitate 分離）不符，將導致編譯失敗。我計畫將舊的單一 `10000L` NISA target 參數，替換為 `null, 10000L, 10000L` (即 `testNisaMonthlyContribution`, `Growth`, `Tsumitate`)，藉此修復編譯錯誤。
- **新增 `GeneticAlgorithmEngineTest`**：這是我先前開發時遺漏的單元測試，我將在此次修改中一併補上，以確保 GA 引擎的正確性。

## Open Questions

- 目前無其他待確認事項，請檢閱下方修改清單。

## Proposed Changes

---

### GA 引擎核心邏輯

#### [MODIFY] `GeneticAlgorithmEngine.java` (ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngine.java)
- 前往 `cloneChromosome()` 方法。
- 刪除舊有針對 `isNisaMonthlyTargetLocked` 與 `getNisaMonthlyTarget` 的拷貝。
- 新增 `nisaGrowthTargetLocked/Target` 與 `nisaTsumitateTargetLocked/Target` 的深拷貝邏輯。

---

### 單元測試修復與補齊 (Unit Tests)

#### [NEW] `GeneticAlgorithmEngineTest.java` (ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngineTest.java)
- 新增 `GeneticAlgorithmEngine` 的單元測試。
- 建立基本模擬環境 (`SimulationContext`、`FinanceHubConfig`、`UserProfile`)。
- 透過 Mock `FitnessEvaluator` 來模擬演化過程，驗證 `optimize()` 方法是否能成功基於突變與交配邏輯，保留並輸出分數最高的 `Chromosome`。
- 特別針對 `cloneChromosome` 的深拷貝正確性（包含新的雙維度 NISA 設定）進行斷言，確保不漏失。

#### [MODIFY] `GridSearchEngineTest.java` (ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngineTest.java)
- 將測試資料的 Seed 初始化中，舊的 `setNisaMonthlyTargetLocked(false)` 取代為 `setNisaGrowthTargetLocked(false)` 與 `setNisaTsumitateTargetLocked(false)`。
- 將 Assert 中 `fitnessEvaluator` 內的評分條件，從原本檢查單一 NISA target 100,000，改為檢查 `nisaGrowthTarget == 200_000L` 與 `nisaTsumitateTarget == 100_000L`。
- 修改最後斷言回傳的最佳結果 (best) 應為 Growth 200_000L, Tsumitate 100_000L。
- 修正註解與測試情境，將 31 種組合更新為 231 種 (21 x 11)。

#### [MODIFY] `GlobalOptimizerServiceTest.java` (ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java)
- **修復編譯錯誤**：全面更新所有的 `new SimulationOverrides(...)` 建構子呼叫，由 8 個參數增加為 10 個，並將舊的 NISA 參數分拆。
- **更新組合數斷言**：在 `testOptimize_RunsGridSearch_WhenLowDimensional()` 測試中，將驗證 `fitnessEvaluator` 被呼叫的次數從 `times(31)` 改為 `times(231)`。
- 修正註解中提到的組合數計算 (例如將 31 * 31 改為 231 等)。
- **更新 ProfileBaselineUsageDetails 斷言**：在 `testOptimize_ReturnsNullDetails_WhenAllConfigOptimized()` 中，將針對 `nisaMonthlyContribution()` 的 `assertNull` 替換為針對新的 `nisaGrowthMonthlyContribution()` 與 `nisaTsumitateMonthlyContribution()`。

## Verification Plan

### Automated Tests
修改完成後，將交由您來執行以下測試指令，以確認這三個測試能成功通過並完成編譯：
- `mvn test -Dtest=GridSearchEngineTest`
- `mvn test -Dtest=GlobalOptimizerServiceTest`
- `mvn test -Dtest=GeneticAlgorithmEngineTest`

[狀態回報] 目前處於等待狀態，請指示下一步。


# 重構 Chromosome 深拷貝邏輯

將目前分散在 `GridSearchEngine` 與 `GeneticAlgorithmEngine` 中的 `Chromosome` 深拷貝邏輯，提取至 `Chromosome` 本身的 Copy Constructor 中，以符合單一職責原則並消除重複的程式碼。

## Proposed Changes

### Core Models

#### [MODIFY] [Chromosome.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java)
- 新增 Copy Constructor `public Chromosome(Chromosome other)`。
- 將 `GridSearchEngine` 中的深拷貝邏輯完整移植過來，包含所有基本資料型別的拷貝，以及針對 `mortgageRepayments` (Map 與內部的 `MortgageRepaymentGene` List) 的深拷貝。

### Optimizer Engines

#### [MODIFY] [GridSearchEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)
- 移除私有方法 `cloneChromosome(Chromosome source)`。
- 將 `cloneChromosome(current)` 的呼叫，替換為 `new Chromosome(current)`。

#### [MODIFY] [GeneticAlgorithmEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngine.java)
- 移除私有方法 `cloneChromosome(Chromosome source)`。
- 將 `cloneChromosome(best.chromosome())` 的呼叫，替換為 `new Chromosome(best.chromosome())`。

## Verification Plan

### Automated Tests
- 將請您手動執行現有的單元測試，確認拷貝邏輯的重構沒有破壞原本引擎的運作：
  - `mvn test -Dtest=GridSearchEngineTest`
  - `mvn test -Dtest=GeneticAlgorithmEngineTest`
  - `mvn test -Dtest=GlobalOptimizerServiceTest`

# Fixed-95-03 Phase 1-C 實作完成總結

我已經完成了針對 Fixed-95 Phase 1-C 的所有程式碼與測試修復，並依約完成了額外的架構重構任務：

## 完成項目 (Changes Made)

1. **Chromosome 封裝深拷貝邏輯 (Refactoring)**
   - 在 `Chromosome.java` 中實作了 Copy Constructor `public Chromosome(Chromosome other)`。
   - 負責自身所有欄位、集合與多維度設定的深層複製，符合 OOD 單一職責原則。

2. **引擎層重複邏輯消除**
   - 移除了 `GridSearchEngine.java` 內部的私有 `cloneChromosome` 方法。
   - 移除了 `GeneticAlgorithmEngine.java` 內部的私有 `cloneChromosome` 方法。
   - 將所有的深拷貝操作替換為 `new Chromosome(...)`，成功消除了重複的程式碼。

3. **新增遺漏的 GA 引擎測試**
   - 新增了 `GeneticAlgorithmEngineTest.java` 單元測試檔案。
   - 提供基本的 `SimulationContext` 並且透過 Mock 的 `FitnessEvaluator` 驅動，驗證 `optimize()` 方法是否能結合雙維度的突變 (Mutation) 正確產出高分數的 Chromosome。

4. **修復 `GridSearchEngineTest.java`**
   - 修正了測試資料中的 Seed 初始化，解鎖雙維度 NISA (`nisaGrowthTargetLocked` 與 `nisaTsumitateTargetLocked`)。
   - 將 Mock 的評分條件從舊有單一目標更新為同時檢查 Growth (200,000) 與 Tsumitate (100,000)。
   - 更新註解與組合數量 (改為 231 組組合)。

5. **修復 `GlobalOptimizerServiceTest.java` 編譯與斷言**
   - 全面修復了所有測試案例中的 `SimulationOverrides` 建構子參數不匹配錯誤，將原本的 8 個參數擴增至 10 個，並將舊的 NISA 貢獻分拆傳入。
   - 修正了 Grid Search 相關的組合次數驗證 (由 31 次提升至 231 次)。
   - 更新了 Profile 檢查斷言，移除舊的 `@Deprecated` 屬性斷言，改為檢查新的 `nisaGrowthMonthlyContribution` 與 `nisaTsumitateMonthlyContribution`。

## 手動驗證指南 (Validation Results)

請您使用以下指令進行編譯與測試，以確認重構與原本的修復都沒有破壞現有的引擎運作：

```bash
mvn test -Dtest=GridSearchEngineTest
mvn test -Dtest=GlobalOptimizerServiceTest
mvn test -Dtest=GeneticAlgorithmEngineTest
```

確認所有測試通過後，整個 Fixed-95 的核心優化引擎維度拆分與測試即全數完美收尾！
