# Fixed 86：Grid Search窮舉搜尋預估組合數比實際少算1個，可能讓使用者收到未預期的錯誤

## 嚴重程度
Medium——不影響模擬正確性，也不會造成系統當機（因為還有另一道5000組合的硬上限保護機制存在），但會破壞「事先友善提示使用者搜尋範圍太大」的使用體驗，讓使用者本來應該收到的友善提示，變成執行過程中途才跳出來的未預期錯誤。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/GlobalOptimizerServiceImpl.java`（第264-283行：`calculateSearchSpace`，執行前的預估組合數邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/GridSearchEngine.java`（第43-56行：建構子設定 `maxMortgageYear`；第120-137行：`generateCombinations()` 實際執行迴圈與執行中途中止檢查）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/OptimizationConstants.java`（第48-49行：事前檢查的門檻值1000，與執行中途的硬上限5000）
- `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/GlobalOptimizerServiceTest.java`（第142-172行：既有測試裡有一段註解寫著「組合數=2002」，剛好符合預估公式算出來的數字，可以用來反向驗證這個落差）

## 現狀
使用者執行「全局AI最佳化」的窮舉搜尋（Grid Search）模式前，系統會先預估一下這次搜尋大概會產生多少種組合，如果預估組合數超過1000，會友善地事先提示使用者「搜尋範圍過大」；如果超過5000，執行中途會直接中止並丟出錯誤。問題是：這個「事先預估」的公式，跟Grid Search引擎實際執行時真正跑出來的組合數，兩者對不起來——實際執行時永遠會比事先預估的多算1個組合。

## 原因
事前預估公式（`GlobalOptimizerServiceImpl.java`）：

```java
mortgageOptions = (endYear - currentYear + 1) + 1; // +1 代表「永不還款」這個選項
```

實際引擎（`GridSearchEngine.java` 建構子）：

```java
this.maxMortgageYear = endYear + 1; // 允許在最後一年才還款
```

迴圈（`generateCombinations()` 第4步）：

```java
for (int year = minMortgageYear; year <= maxMortgageYear; year++) { ... }
```

另外再加一個獨立於迴圈之外的「永不還款」分支。

事前預估公式算出來的是 `(endYear - currentYear + 1) + 1`；但實際引擎的迴圈次數加上「永不還款」，等於 `(endYear - currentYear + 2) + 1`——因為引擎的 `maxMortgageYear` 是 `endYear + 1`（比事前預估假設的多了1年），所以實際執行時，永遠會比事前預估多跑剛好1個組合。

用既有測試資料反向驗證：`testOptimize_ThrowsException_WhenForceGridWithMortgageCombinationsExceedCap` 這個測試把房貸開始年設在2025年、貸款年限故意設成2000年（刻意設超大讓組合數觸發上限），所以 `currentYear=2025`、`endYear=4025`。事前預估公式算出來是 `(4025-2025+1)+1 = 2002`（跟測試裡標註「組合數=2002」的註解一致）。但實際引擎：`maxMortgageYear=4025+1=4026`，迴圈從2025跑到4026，共跑2002次，加上「永不還款」的1次，實際總共執行2003次——恰好比事前預估多1次。

## 具體錯誤情境
假設使用者房貸的 `endYear`（貸款到期年）剛好等於 `currentYear`（模擬起始年，也就是剩不到1年就要還完），且只解鎖「房貸還款年」這一個搜尋維度：事前預估 = `(0+1)+1 = 2`（通過1000這個門檻，正常繼續執行）；但實際執行時，會有 `currentYear`、`currentYear+1`，再加上「永不還款」，共3種組合。2（預估）對3（實際）等於足足差了50%。雖然這個極端例子裡3遠遠沒有超過5000這個硬上限，但這個案例說明了：房貸剩餘年限越短，這個「差1」造成的比例誤差就越明顯——如果同時解鎖了其他搜尋維度（例如NISA、儲蓄等），乘法效應下，一個「事前預估通過1000門檻」的設定，實際執行時可能真的超過5000這個硬上限，導致使用者收到的不是原本設計好的友善事前提示，而是執行途中跳出來的未預期錯誤。

## 建議修正
這純粹是計算邏輯層面的落差，不涉及「該不該允許窮舉搜尋」或「門檻數字該設多少」這類產品／UX決策，但仍有兩個實作方向的取捨：

**方向A：直接把 `calculateSearchSpace()` 的公式改成 `(endYear - currentYear + 2) + 1`，跟 `GridSearchEngine` 實際的 `maxMortgageYear = endYear + 1` 對齊**
- 優點：改動最小，只改一行，能立即讓事前預估跟實際執行一致。
- 缺點：這個「房貸還款年選項數量」的邏輯目前是分別手動維護在兩個不同檔案裡，這個修正沒有解決「未來如果有人又改了 `GridSearchEngine` 的年份範圍邏輯（例如支援多筆房貸，或改變『保留最後一年才能還完』的規則）卻忘了同步更新 `calculateSearchSpace()`」的風險，只是治標。

**方向B：把「房貸還款年選項一共有幾種」這段邏輯抽成一個共用的靜態方法，讓 `calculateSearchSpace()` 跟 `GridSearchEngine` 的建構子都呼叫同一份邏輯，而不是各自維護一份**
- 優點：從根本上消除「兩份各自維護的邏輯彼此漂移」的風險，是比較長久的修法。
- 缺點：改動範圍比方向A大，需要重新調整 `GlobalOptimizerServiceImpl` 跟 `GridSearchEngine` 之間的依賴關係（例如新增一個共用的工具類別，或讓其中一方依賴另一方），而且既有測試裡「組合數=2002」這類斷言也需要一併更新成修正後的正確數字。

## 與已知項目的區別
本問題跟已修復的「多筆房貸廣播bug」（`ChromosomeMapper.toOverrides()` 把單一房貸的還款年廣播到所有房貸上，造成多筆房貸情境下的結果算錯）是不同的問題，本問題完全不涉及廣播邏輯，純粹是單一房貸情境下的「事前預估數」跟「實際執行數」對不齊。也跟既有的「Grid Search不支援2筆以上房貸」的已知限制（`todo/planned/Plan-02-grid-search-multi-mortgage-dimension.md`）不同——該文件在描述目前 `calculateSearchSpace()` 的房貸選項數量時，其實引用的是另一個（同樣跟 `GridSearchEngine` 對不上、但公式不同）的估算方式（「剩餘年限+2」），從未拿它跟 `GridSearchEngine` 實際的迴圈次數做比對，所以既沒有涵蓋、也沒有記錄本問題這個具體的落差。也跟已修復的「FORCE_GRID缺少組合數爆炸保護」（那次修的是完全沒有任何執行中途保護機制的問題，現已透過 `OptimizationConstants.MAX_GRID_ENGINE_COMBINATIONS`=5000硬上限修復）不同——本問題是建立在這兩道既有保護機制都已經存在的前提下，單純討論「事前預估」跟「實際執行」兩個數字彼此不一致，跟這些保護機制本身是否存在無關。

---
## Implementation Report (Gemini AI)

**Status:** Resolved (Implemented Direction B)

### What was done
為了根除 `GlobalOptimizerServiceImpl` 的事前預估公式與 `GridSearchEngine` 的實際迴圈執行次數之間「差 1」的不一致，並避免未來雙方邏輯再次脫鉤，我們實作了**方向 B**：

1. **抽出共用邏輯**：在 `optimizer.core` 套件下新增了 `OptimizationUtils` 工具類別。裡面的 `calculateMortgageYearRange()` 會迭代檢視所有房貸，算出 `minYear = currentYear` 與 `maxYear = endYear + 1`，並回傳這組年份區間。
2. **重構執行引擎**：`GridSearchEngine` 的建構子不再自行尋找 `maxMortgageYear`，改為直接呼叫上述的 `OptimizationUtils` 取得區間，將其設為自己迴圈的起終點。
3. **重構預估公式**：`GlobalOptimizerServiceImpl.calculateSearchSpace()` 也不再自行尋找 `endYear`，而是呼叫 `OptimizationUtils` 取得區間後，直接利用區間的差距算出版面選項數 `(range.maxYear() - range.minYear() + 1) + 1`。
4. **修正測試斷言註解**：更新了 `GlobalOptimizerServiceTest` 中的註解，將預估的組合數由 `2002` 修正為正確的 `2003`，反映出現在預估公式已經能夠準確預測包含 `+1` 年在內的真實執行次數。

### Verification Pending
程式碼變更已全數完成，待使用者執行 `mvn clean test -Dtest=GlobalOptimizerServiceTest` 以確保相關測試全數通過並完成最終的驗證。


edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                    10005  12.73G   6.00    17:15:52  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
index 768a3bde..6bd4a231 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
@@ -44,6 +44,7 @@ import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chro
 import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.GeneticAlgorithmEngine;
 import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.GridSearchEngine;
 import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.OptimizationConstants;
+import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.OptimizationUtils;
 import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.OptimizerEngine;
 import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.evaluator.AssetSimulationFitnessEvaluator;
 import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.mapper.ChromosomeMapper;
@@ -240,14 +241,11 @@ public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {
         if (!seed.isMortgageRepaymentsLocked()) {
             long mortgageOptions = 1; // default
             if (baseData.getAllMortgages() != null && !baseData.getAllMortgages().isEmpty()) {
-                int currentYear = baseData.getBaseContext().getCurrentYear();
-                int endYear = currentYear;
-                for (MortgageRecord m : baseData.getAllMortgages()) {
-                    int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear();
-                    if (mEndYear > endYear)
-                        endYear = mEndYear;
+                var range = OptimizationUtils.calculateMortgageYearRange(baseData.getAllMortgages(),
+                        baseData.getBaseContext().getCurrentYear());
+                if (range != null) {
+                    mortgageOptions = (range.maxYear() - range.minYear() + 1) + 1; // +1 for never repay
                 }
-                mortgageOptions = (endYear - currentYear + 1) + 1; // +1 for never repay
             }
             combinations *= mortgageOptions;
         }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java
index d76a2f94..718fd21f 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java
@@ -30,7 +30,6 @@ import java.util.Map;
 
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
 import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome.MortgageRepaymentGene;
-import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
 import lombok.extern.slf4j.Slf4j;
 
 @Slf4j
@@ -42,17 +41,14 @@ public class GridSearchEngine implements OptimizerEngine {
 
     public GridSearchEngine(FitnessEvaluator evaluator, SimulationData baseData) {
         this.evaluator = evaluator;
-        
+
         if (baseData != null && baseData.getAllMortgages() != null && !baseData.getAllMortgages().isEmpty()) {
-            this.minMortgageYear = baseData.getBaseContext().getCurrentYear();
-            int endYear = this.minMortgageYear;
-            for (MortgageRecord m : baseData.getAllMortgages()) {
-                int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear();
-                if (mEndYear > endYear) {
-                    endYear = mEndYear;
-                }
+            var range = OptimizationUtils.calculateMortgageYearRange(baseData.getAllMortgages(),
+                    baseData.getBaseContext().getCurrentYear());
+            if (range != null) {
+                this.minMortgageYear = range.minYear();
+                this.maxMortgageYear = range.maxYear();
             }
-            this.maxMortgageYear = endYear + 1; // Allows paying off at the very end
         }
     }
 
@@ -67,11 +63,13 @@ public class GridSearchEngine implements OptimizerEngine {
 
         generateCombinations(seed, cloneChromosome(seed), 0, bestOverall, bestOverallScore, evaluatedCount);
 
-        log.info("Finished Grid Search. Evaluated {} combinations. Best overall score: {}", evaluatedCount[0], bestOverallScore[0]);
+        log.info("Finished Grid Search. Evaluated {} combinations. Best overall score: {}", evaluatedCount[0],
+                bestOverallScore[0]);
         return bestOverall[0];
     }
 
-    private void generateCombinations(Chromosome seed, Chromosome current, int step, Chromosome[] bestOverall, double[] bestOverallScore, int[] evaluatedCount) {
+    private void generateCombinations(Chromosome seed, Chromosome current, int step, Chromosome[] bestOverall,
+            double[] bestOverallScore, int[] evaluatedCount) {
         switch (step) {
             case 0 -> {
                 // NISA
@@ -136,7 +134,7 @@ public class GridSearchEngine implements OptimizerEngine {
                     throw new IllegalStateException("Grid search engine combinations exceeded hard safety limit: "
                             + OptimizationConstants.MAX_GRID_ENGINE_COMBINATIONS);
                 }
-                
+
                 double score = evaluator.evaluate(current);
                 if (score > bestOverallScore[0]) {
                     bestOverallScore[0] = score;
@@ -160,7 +158,7 @@ public class GridSearchEngine implements OptimizerEngine {
 
         clone.setNisaMonthlyTargetLocked(source.isNisaMonthlyTargetLocked());
         clone.setNisaMonthlyTarget(source.getNisaMonthlyTarget());
-        
+
         clone.setMortgageRepaymentsLocked(source.isMortgageRepaymentsLocked());
         clone.setGridSingleMortgageRepaymentYear(source.getGridSingleMortgageRepaymentYear());
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java
index 9bce3ff6..3d772bd6 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java
@@ -161,7 +161,7 @@ public class GlobalOptimizerServiceTest {
         // Mock a mortgage that takes 2000 years to repay to force combinations > 1000
         MortgageRecord m = org.mockito.Mockito.mock(MortgageRecord.class);
         org.mockito.Mockito.when(m.getStartDate()).thenReturn(java.time.LocalDate.of(2025, 1, 1));
-        org.mockito.Mockito.when(m.getLoanTermMonths()).thenReturn(12 * 2000); // 2000 years, so combinations = 2002 >
+        org.mockito.Mockito.when(m.getLoanTermMonths()).thenReturn(12 * 2000); // 2000 years, so combinations = 2003 >
                                                                                // 1000
         mockData.setAllMortgages(java.util.List.of(m));

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT