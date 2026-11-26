# Plan 04：Grid Search 支援多筆房貸各自獨立搜尋清償年份

> 這是一份功能擴充構想的記錄，**不是 bug 修正**。對應的 bug（Grid Search 目前在多房貸情境下會把所有房貸橋接成同一年份一次清償）已在 [Issue-08-01.md 二](../pending%20review/Issue-08-01.md) 用「方向 A：偵測到多筆房貸時直接拒絕 FORCE_GRID」處理。本文記錄的是「拒絕」之外的另一種可能性——讓 Grid Search 真正對每筆房貸各自搜尋最佳清償年份——目前判斷不值得在這次一併做，但為避免這個想法連同 Issue 一起被關閉後永遠被遺忘，記錄於此。

## 動機

`GridSearchEngine` 目前的房貸維度（[GridSearchEngine.java:120-133](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java:120)）只搜尋「一個」清償年份，並透過 `ChromosomeMapper.toOverrides()` 把這一個年份廣播套用到使用者名下所有房貸。這在架構上就是為單一房貸情境設計的。真實情境中，使用者常見持有多筆房貸（自住房貸 + 投資用不動產貸款、或換屋重疊期），會希望知道「每一筆房貸各自的最佳清償時機」，而不是「把所有房貸綁在一起找一個共同年份」。

## 技術可行性

可行，但不是小改動，需要動到以下幾處：

- **`Chromosome`**：目前 `gridSingleMortgageRepaymentYear` 是單一純量欄位，需改成「每筆房貸 ID 各自一個候選年份」的結構（例如 `Map<Long, Integer>`）。
- **`GridSearchEngine.generateCombinations` case 4**：目前是單一年份的一維迴圈，需改成巢狀迴圈或遞迴逐一房貸展開，才能對每筆房貸各自搜尋。
- **`ChromosomeMapper.toOverrides()`**：現在的橋接邏輯（[ChromosomeMapper.java:59-70](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java:59)）是把同一個純量年份廣播給 `mortgageOverrides.keySet()` 裡的所有房貸——這正是 Issue-08-01 二節 bug 的根因。要支援多維，必須改成「每筆房貸各自去讀自己的候選年份」，而不是廣播。
- **`GlobalOptimizerServiceImpl.calculateSearchSpace()`**：`mortgageOptions` 目前只算一筆房貸的選項數（`剩餘年限 + 2`），需改成對每筆房貸的選項數各自相乘，才能正確估算多房貸情境下的真實搜尋空間。

## 搜尋空間爆炸風險（不是必然，但很容易發生）

各維度是**相乘**、不是相加。目前上限 `MAX_GRID_SEARCH_SPACE = 1000`（[OptimizationConstants.java](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java)）：

- 兩筆房貸都剩 ~20 年、其他維度全鎖：22 × 22 = 484 → 沒爆。
- 兩筆房貸都剩 ~30 年（常見）：32 × 32 = 1024 → 光房貸維度自己就已經超過上限。
- 上述任一情況只要再解鎖一個其他維度（例如 NISA 31 個選項）：484 × 31 ≈ 15,000 → 直接爆穿硬上限 `MAX_GRID_ENGINE_COMBINATIONS = 5000`。

結論：不是「兩筆房貸就 100% 一定爆」，但因為房貸維度從線性 O(年限) 變成 O(年限²)（N 筆房貸則是 O(年限^N)），實務上只要任一房貸剩餘年限稍長（10-20 年以上很常見），再加上使用者通常會順手多解鎖一個其他維度，就會很容易超過現有的 1000 上限。這個上限本來就是針對「單一房貸」校準的，若真的實作這個功能，必須同步重新評估這個數字，而不是原封不動沿用。

## 效能與 HTTP timeout 風險（現況調查，供未來實作參考）

- 已確認：每次 Grid Search 評估（[AssetSimulationFitnessEvaluator.java:72](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java:72)）**零 DB 存取**——所有 repository 查詢只在最開始的 `fetchSimulationData()` 執行一次，`runSimulationWithData()` 之後純粹是記憶體內運算。所以是純 CPU-bound，不會有 DB round-trip 疊加的風險。
- 但目前 repo 內**沒有任何 profiling / benchmark 數據**，無法給出有信心的絕對秒數，只能用比例推算：N 次評估 ≈ N 倍「使用者手動按一次模擬」的計算量。
- **目前完全沒有 timeout 防線**：後端 `finance-hub-backend` 全域搜尋不到任何針對這個 optimizer endpoint 的 servlet/request timeout 設定；前端 `use-global-optimizer.ts` 的 `fetch()` 呼叫也沒有 `AbortController`/timeout。也就是說，這個請求目前完全依賴看不到的基礎設施層（reverse proxy / API gateway）擋下超長請求，這部分無法從程式碼確認。

**若未來真的要實作這個功能，建議同時處理**：
1. 重新設計 `calculateSearchSpace()` 的估算公式，使其正確反映 N 筆房貸相乘後的真實組合數（含目前估算與 `GridSearchEngine` 實際迴圈之間已存在的 off-by-one 差異）。
2. 評估是否需要調整 `MAX_GRID_SEARCH_SPACE` 這個上限，或至少確保它在多房貸情境下依然有意義。
3. 補上這個 endpoint 目前缺失的請求層 timeout 保護（後端 servlet 層或前端 `AbortController`），避免使用者手動選 FORCE_GRID 又剛好卡在估算上限邊緣時，同步阻塞的請求無限期掛著等結果。

## 現階段結論

暫不執行。Issue-08-01 二節已用「偵測多房貸時拒絕 FORCE_GRID」（方向 A）處理掉當下的 bug，此文件僅保留「未來若要做真正的多維搜尋」這個構想與其技術評估，供日後排入 roadmap 時參考。
