# Fixed 95：全局最佳化測試每月NISA投入21萬到30萬時，沒有NISA基金的使用者會被悄悄砍成跟20萬一樣

## 嚴重程度
High——這不是邊緣情境或已停用的舊功能，而是「全局AI最佳化（Global Optimizer）」這個核心功能目前實際使用中的計算路徑。這個bug會讓最佳化搜尋在測試每月NISA投入21萬到30萬元這段範圍時，全部得到跟20萬元一樣的結果，等於白白浪費了搜尋空間裡三分之一的測試點，可能導致系統誤判某個財務目標「無法達成」，但實際上只要正確拆分成長枠與定期定額枠是可以達成的。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/FundOverrideApplier.java`（第94-150行：`applyGlobalNisaContribution`，第121-131行是在使用者完全沒有NISA基金時，建立一筆虛擬基金的邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java`（第87-130行、第150-165行：依帳戶類型分別套用年度上限的邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/OptimizationConstants.java`（第42-45行：`MAX_NISA_MONTHLY_TARGET` = 300,000，也就是搜尋範圍的上限是每月30萬元）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/GridSearchEngine.java`（第79行：窮舉搜尋迴圈，每次以1萬元為單位測試從0到30萬元）

## 現狀
「全局AI最佳化」功能會自動嘗試每月NISA投入金額從0元到30萬元之間、每次增加1萬元的所有組合，找出能達成使用者財務目標的最佳配置。這個測試範圍剛好對應到日本NISA制度「成長投資枠＋定期定額枠」合計每月最多可以投入30萬元（年度360萬元）的法定上限。

但如果使用者目前完全沒有任何NISA基金持股資料，當系統要測試「每月投入21萬到30萬元」這幾個數值時，實際計算出來的結果，會跟測試「每月投入20萬元」時完全一樣——也就是說，21萬、22萬、一直到30萬，這10個測試點全部被悄悄砍成跟20萬一樣，等於整個搜尋範圍裡三分之一以上的測試點都變成無效重複，沒有真正測試到。

## 原因
當使用者沒有既有的NISA基金時，`FundOverrideApplier.java` 的 `applyGlobalNisaContribution` 方法（第121-131行）會建立一筆虛擬基金來代表這次要測試的金額，但建立這筆虛擬基金時完全沒有指定它的帳戶類型（`.nisaAccountType(...)` 沒有被呼叫，等於留空／null）。

而 `TaxAdvantagedAccountProjector.java` 在依帳戶類型分桶、套用各自年度上限的邏輯裡，把「帳戶類型是null」的情況當成「成長投資枠」來處理（程式碼註解寫著「GROWTH is the default (conservative fallback for null)」）。因為成長投資枠的年度上限是240萬元（相當於每月20萬元），任何測試值超過每月20萬元的部分，都會被這個240萬上限卡住，導致21萬到30萬這段全部被砍成跟20萬一樣的結果。

值得注意的是，這個「因為帳戶類型被誤判成成長投資枠而卡在20萬」的機制，跟另一個問題（見 Fixed-96：個人設定填月投入超過20萬元時的類似bug）是同一個根本原因在不同情境下的重複發生——Fixed-96討論的是「使用者已經有既有NISA基金、且該基金目前投入金額大於0」的情境，本問題則是「使用者完全沒有任何NISA基金」的情境，兩者觸發條件不同，但底層都是「虛擬基金被當成成長投資枠處理，卡在240萬年度上限」。

## 具體錯誤情境
使用者目前完全沒有任何NISA基金，執行「全局AI最佳化」去測試「每月NISA投入金額」這個維度，系統會依1萬元為單位，從0到30萬元逐一測試。測試21萬到30萬這10個值時，全部得到跟測試20萬時完全相同的模擬結果。如果使用者真正的最佳解需要每月25萬元（例如20萬元放成長投資枠、5萬元放定期定額枠才能達成），系統會因為21萬到30萬這段全部被誤判成無效（跟20萬相同），而錯誤地判定「無法達成使用者設定的財務目標」，但實際上只要正確地拆分成長枠與定期定額枠，是可以達成的。

## 建議修正
這個問題本質上跟Fixed-96一樣，是「單一總金額該怎麼拆分到成長投資枠與定期定額枠」的分配規則選擇，但因為發生在「全局最佳化搜尋」這個情境，多了「要不要改變搜尋維度設計」的額外考量，以下三個方向各有取捨：

**方向A：建立虛擬基金測試值時，依規則自動拆分——先把20萬（成長枠上限）用滿，剩下的部分自動歸到定期定額枠**
- 優點：不需要改變現有的搜尋範圍設計，21萬到30萬這段範圍可以被正確測試到。
- 缺點：「先用滿成長枠、剩下才給定期定額枠」是系統自己選的一種分配方式，不一定是使用者實際上會採用的配置方式。

**方向B：把搜尋範圍的上限下修到24萬元，或是在結果裡加註說明這個限制**
- 優點：改動最小，不需要引入任何分配規則的假設。
- 缺點：放棄了測試「每月20萬到30萬」這段法規上合法、但需要跨枠分配才能達成的範圍，等於系統主動縮小了原本可以測試到的搜尋空間。

**方向C：把「每月NISA投入金額」這一個測試維度，拆成「成長投資枠每月投入」跟「定期定額枠每月投入」兩個獨立維度，讓最佳化引擎在這個二維空間裡搜尋組合**
- 優點：最精確，不需要任何分配規則的假設，能真正找到跨枠配置後的最佳解。
- 缺點：工程改動量最大，會讓搜尋維度數量加倍，可能影響整體搜尋所需的運算時間。

## 與已知項目的區別
本問題與已知的 `FINDING-09`（舊版NISA目標試算器的單調性判斷錯誤）與 `FINDING-27`（舊版NISA目標試算器前端數值範圍限制只是裝飾、沒有真正生效）都不是同一個問題——那兩項討論的是舊版 `SimulationOptimizerServiceOldImpl.calculateNisaGoal` 這條已經不再使用的舊計算路徑，跟本問題所在的、目前實際使用中的 `GlobalOptimizerServiceImpl`／`GridSearchEngine` 計算路徑完全不同，不是重複報告。

## 決策與結論 (Decision & Conclusion)
經過確認，決定採用 **方向 C** 作為修復方案。

**具體實作方向與規格：**
1. **最佳化引擎維度拆分 (為了規避 AI 幻覺與上下文超載，強制拆分為三個原子 Phase)**：
   - **Phase 1-A: 底層資料結構與映射層 (Data Structure & Mapping)**
     - **常數更新 (`OptimizationConstants`)**：移除 `MAX_NISA_MONTHLY_TARGET`，替換為 `MAX_NISA_GROWTH_TARGET = 200,000` 與 `MAX_NISA_TSUMITATE_TARGET = 100,000`。配合雙維度擴增，將 `MAX_GRID_SEARCH_SPACE` 安全閥值由 `1000L` 提高至 `2000L`，確保更多中小型多維度組合能保有絕對精準的窮舉搜尋權利。
     - **基因結構擴充 (`Chromosome`)**：將原本單一的 `nisaMonthlyTarget` 屬性，拆分為 `nisaGrowthTarget` 與 `nisaTsumitateTarget` 兩個獨立的整數型別基因，並新增對應的 getter/setter/lock 邏輯。
     - **結果映射 (`ChromosomeMapper`)**：修改從 `Chromosome` 轉換為 `SimulationOverrides` 的映射邏輯，將尋獲的雙基因精準對應到 Fixed-96 新增的 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution` 欄位。

   - **Phase 1-B: Grid 搜尋與調度防護層 (Grid Search & Dispatcher)**
     - **迴圈重構 (`GridSearchEngine`)**：將原本針對 NISA 搜尋的單一 `for` 迴圈，改寫為外層測試成長枠 (`0`~`200,000`，21 種)、內層測試積立枠 (`0`~`100,000`，11 種) 的兩層獨立嵌套迴圈，使單純 NISA 測試組合擴增為 231 種。
     - **防護崩潰更新 (`GlobalOptimizerServiceImpl`)**：
       - 【重要】必須同步修改 `calculateSearchSpace` 估算公式，將 NISA 的估算乘數從 31 替換為 `21 * 11 = 231`。否則系統會嚴重低估實際迴圈數，導致防禦降級機制失效，撞上 5000 次硬限制而拋出 500 崩潰。
       - 修改 `buildSeedChromosome`，在初始化種子染色體時正確設定兩組新的目標鎖定狀態與初始值。

   - **Phase 1-C: GA 引擎支援與單元測試修復 (Genetic Algorithm & Tests)**
     - **交配與突變邏輯 (`GeneticAlgorithmEngine`)**：【極易漏改的盲點】全面翻修 GA 的隨機突變 (Mutation) 與交配 (Crossover) 邏輯，確保在產生下一代時，新的 `nisaGrowthTarget` 與 `nisaTsumitateTarget` 兩個基因都能各自遵守其 `200,000` 與 `100,000` 的獨立上限，且具備正確的突變機率。
     - **受影響的測試案例修復**：全面檢視並修復 `GridSearchEngineTest`、`GlobalOptimizerServiceImplTest`、`GeneticAlgorithmEngineTest` 等因維度拆分而編譯失敗或 Assert 錯誤的單元測試，確保整體最佳化引擎回歸正常運作。

2. **Phase 2: 與 Fixed-96 的引擎無痛串接 (Dependency on Fixed-96)**：
   - **底層分配邏輯由 29 負責**：Optimizer (GA/Grid) 測試組合時，是透過 Sandbox Override 欄位與底層引擎溝通的。因此，處理虛擬基金分配的 `FundOverrideApplier`，已經完全交由 **Fixed-96 的 Phase 3** 去處理。
   - **本 Fixed-95 專注於搜尋層**：待 Fixed-96 實作好雙欄位的 DTO 與底層虛擬基金分配後，本 Fixed-95 的 `GlobalOptimizerServiceImpl` 只需要專心扮演「產生測試組合」的角色（將最佳化組合餵給 Fixed-96 寫好的雙欄位介面），即可達到無痛串接，不需重複修改底層引擎。

3. **Phase 3: 移除舊有過渡欄位 (Cleanup & Removal)**：
   - 在本 Fixed-95 的維度拆分與 Optimizer 更新全部完工並驗證無誤後，**正式拔除** Fixed-96 所保留的舊有單一欄位。
   - **前端 UI "Apply to Sandbox" 邏輯更新**：
     - 將原本 Fixed-96 建立的「降級拆分（優先填滿積立、剩餘給成長）」過渡邏輯徹底刪除。
     - 改為直接讀取新版 Optimizer 回傳的 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution`，並 1:1 精準對應回填到前端畫面的兩個新輸入框中。
   - **清除舊有 API 欄位**：
     - 清除 Entity 與 DTO (含 Request/Response/Overrides) 中標記為 `@Deprecated` 的 `monthlyNisaContribution` 與 `testNisaMonthlyContribution`。
     - 移除前端發送 Request 時，為了相容舊版 Optimizer 而「自動加總成單一欄位」的過渡邏輯。
