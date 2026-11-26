# Fixed-44-02：Fixed-44「減法工程」修正驗證報告 + 新發現的邊際效應

> 本文為 Fixed-44.md 的修正驗證更新。Fixed-44 的最終修正方向大幅偏離原始建議（從「修正扣除額計算時序」改為「整個廢除動態自動清償機制」），因此依使用者要求對此次大範圍變更做了完整的邊際效應（side effect）稽核。

## 一、核心 Bug 驗證結果：✅ 已修復

原始問題（房貸於退休當年全額清償時，`MortgageCalculator.simulateMortgagesForYear` 仍用清償前的餘額計算住宅ローン控除，導致雙重優惠）已確認修復。

追蹤 [MortgageCalculator.java:389-399](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:389) 目前程式碼：

```java
if (isFutureMonth) {
    if (earlyPrincipal > balance) {
        earlyPrincipal = balance;   // clamp：不會清償超過實際餘額的金額
    }
    balance -= earlyPrincipal;
    annualPayment += earlyPrincipal + earlyFee;
}
if (balance <= 0) {
    balance = 0;
    break;   // 清償完畢就跳出月迴圈，balance 已經歸零
}
```

年末的住宅ローン控除計算（[MortgageCalculator.java:448](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:448) `long deduction = (long) (balance * rate);`）使用的是這個迴圈跑完後、已反映清償結果的 `balance`。也就是說：如果房貸在某年年中/年末被全額清償（不論是透過 GA 產生的排程還原、還是 Grid Search 透過 `Long.MAX_VALUE` 橋接的排程），該年年末的餘額就是 0，扣除額自然算成 0——不會再發生「已經還完的房貸還在領稅務扣除」的雙重計算問題。

同時確認 `processEarlyRepaymentAtRetirement` 及 `processMortgageEarlyRepayment` 在全 repo（`MortgageCalculator.java`、`YearlySimulationPipeline.java` 及其他所有檔案）已無任何殘留引用，[YearlySimulationPipeline.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java) 的 `applyCashflowAndSettlement` 簽章也確實不再帶 `overrides` 參數——移除乾淨，沒有殘留的死路徑。

**附帶驗證「測試驗證報告」聲稱的 ¥8,083 / 12→11 個月調整是否為真實現象（非幻覺）**：追蹤程式碼發現，若清償發生在 `logicalMonth`（預設第 12 月，若有獎金月則取獎金月）當月，`earlyPrincipal` 的扣款與 `balance <= 0` 的 `break` 發生在**當月常規本息攤還計算之前**（[MortgageCalculator.java:401-413](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:401) 的 `interest = balance * monthlyRate` 永遠不會執行到），所以「當月提前清償」會讓借款人省下最後一個月的利息。這是真實、財務上合理的行為（提前清償本來就該省下之後的利息），測試期望值的調整方向正確，非幻覺。

**結論：Fixed-44 的核心 bug 修復有效，且修正手法（減法工程）本身沒有引入計算邏輯上的退步。**

## 二、新發現的問題：Grid Search 在多房貸情境下會「一次性清償所有房貸」

### 現狀

`ChromosomeMapper.toOverrides()`（[ChromosomeMapper.java:59-70](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java:59)）把 Grid Search 產生的單一純量欄位 `gridSingleMortgageRepaymentYear` 橋接回 `mortgageEarlyRepayments` map 時：

```java
Integer legacyYear;
if (!hasComplexMortgages && chromosome.getGridSingleMortgageRepaymentYear() != null) {
    legacyYear = chromosome.getGridSingleMortgageRepaymentYear();
    for (Long mortgageId : mortgageOverrides.keySet()) {
        mortgageOverrides.computeIfAbsent(mortgageId, k -> new ArrayList<>())
                .add(new SimulationOverrides.MortgageEarlyRepaymentOverride(legacyYear, Long.MAX_VALUE));
    }
}
```

這裡對 `mortgageOverrides.keySet()`（也就是使用者名下**所有**房貸的 ID）逐一套用同一個 `legacyYear` + `Long.MAX_VALUE`。而 `mortgageOverrides.keySet()` 之所以會包含所有房貸 ID，是因為 `GlobalOptimizerServiceImpl.buildSeedChromosome()`（[GlobalOptimizerServiceImpl.java:145-161](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:145)）在 `config.optimizeMortgage()` 為真且無既有 baseOverrides 時，會主動幫每一筆房貸塞一個空陣列進去（讓 Grid Search / GA 都有起點可以變異）。

Grid Search 的 `generateCombinations` case 4（[GridSearchEngine.java:120-133](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java:120)）本身就只有**一維**的年份搜尋（`current.setGridSingleMortgageRepaymentYear(year)`），完全沒有「每筆房貸各自一個清償年」的維度；`GlobalOptimizerServiceImpl.calculateSearchSpace()`（[GlobalOptimizerServiceImpl.java:187-200](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:187)）計算搜尋空間時，`mortgageOptions` 也只算了一次（不會因為房貸數量而乘倍），可見這個功能架構上就是為「僅有一筆房貸」的情境設計的——這點在 Fixed-44.md 的討論記錄裡也被明確提及（「Grid search 是為了採用只有一唯房貸的情境」）。

**但程式碼裡完全沒有任何地方擋住「有多筆房貸的使用者選擇 Grid Search」這個組合**：

- 後端 `GlobalOptimizerServiceImpl.optimize()` 的 `FORCE_GRID` 分支只檢查 `gridCombinations > MAX_GRID_SEARCH_SPACE`（[GlobalOptimizerServiceImpl.java:81-89](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:81)），不會因為多房貸而超過此限制（因為上述計算完全沒把房貸數量算進去），所以不會被這道防線擋下。
- 前端 `global-optimizer-card.tsx:235` 直接把 `"Grid Search"` 列成一個可選策略（`<SelectItem value="FORCE_GRID">Grid Search</SelectItem>`），沒有依房貸數量做任何遮罩或警示。

**因此，一旦使用者名下有 2 筆以上房貸（例如自住房貸 + 投資用不動產貸款、或換屋重疊期），且勾選「優化房貸」並手動選擇 Grid Search 策略，其結果會是「找出一個最佳年份，把所有房貸都在該年一次全額清償」**——而不是使用者可能預期的「找出每筆房貸各自最適合的清償時機」。

需特別說明：這不是「搜尋跟結果不一致」的 bug——因為 `AssetSimulationFitnessEvaluator.evaluate()`（[AssetSimulationFitnessEvaluator.java:72](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java:72)）跟最終映射用的是**同一個** `chromosomeMapper.toOverrides()`，所以搜尋過程中被評分的情境，跟最終回傳給使用者的情境是一致的（都是「全部房貸同時清償」）。問題在於：這從頭到尾都不是使用者以為自己在優化的問題——UI 上完全沒有告知「多房貸情境下 Grid Search 會被套用成全部一起清償」這個限制，使用者可能會誤以為系統已經個別評估過每筆房貸的最佳清償時機。

### 影響範圍

僅影響：**多房貸使用者 + 主動選擇 Grid Search（`FORCE_GRID`）策略 + 勾選優化房貸**的組合。`AUTO` 模式下，只要 `optimizeMortgage()` 為真，`calculateSearchSpace` 會直接因為 `strategy != FORCE_GRID` 而回傳 `Long.MAX_VALUE`（[GlobalOptimizerServiceImpl.java:181-183](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:181)），強制走 GA（不受此限制影響）。所以只有使用者手動在下拉選單指定「Grid Search」時才會踩到。

### 建議修正方向

範圍限定為以下兩點，不要再擴大到其他改動（例如不要順手去改 `GridSearchEngine` 的搜尋維度、`Chromosome` 的欄位結構、或 `ChromosomeMapper.toOverrides()` 的橋接邏輯本身——這裡只需要在進入這些既有邏輯之前，用一個條件判斷直接擋掉多房貸情境並丟例外，不需要、也不應該去改動被擋掉之後就不會再執行到的下游程式碼）：

**1. 後端：在 `GlobalOptimizerServiceImpl.optimize()` 的 `case FORCE_GRID:` 分支最前面（即現有 `calculateSearchSpace()` 呼叫之前，[GlobalOptimizerServiceImpl.java:81-82](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:81)）加入以下判斷：**

```java
case FORCE_GRID:
    if (!seed.isMortgageRepaymentsLocked()
            && baseData.getAllMortgages() != null
            && baseData.getAllMortgages().size() > 1) {
        throw new OptimizationNotSupportedException(
                "Grid Search strategy only supports a single mortgage. Use AUTO or FORCE_GA for multiple mortgages.");
    }
    long gridCombinations = calculateSearchSpace(seed, baseData, strategy);
    ...
```

判斷條件請直接沿用 `seed.isMortgageRepaymentsLocked()`（`optimize()` 一開始 `buildSeedChromosome()` 就已經算好、存在 `seed` 這個既有變數上，[GlobalOptimizerServiceImpl.java:69](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:69) 及 [第 146-161 行](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:146) 的 null 分支已經處理過），**不要**自己重新寫一次 `command != null && command.config() != null && command.config().optimizeMortgage()`——這是舉例用的舊寫法，重新刻一次容易漏掉 `command`/`config` 為 null 的分支，變成跟 `buildSeedChromosome()` 邏輯不一致的第二套判斷。

`OptimizationNotSupportedException` 丟出後**不需要新增任何例外處理程式碼**：`WebGlobalExceptionHandler.handleOptimizationNotSupportedException`（[WebGlobalExceptionHandler.java:275-281](finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java:275)）已經攔截這個例外類型，回傳 HTTP 400，body 為 `{"status":"failure","data":{"code":"OPTIMIZATION_NOT_SUPPORTED","message":"<例外訊息>"}}`（巢狀結構定義於 [ApiResponse.java](platform/src/main/java/ch/lin/platform/api/ApiResponse.java:71) 與 `ApiError`）。這條處理路徑本來就存在，不要重複寫一個新的 `@ExceptionHandler`。

**2. 前端：僅做以下兩點，不要額外新增資料抓取或 UI 改動**

- **修正錯誤訊息目前被吃掉的問題**：`use-global-optimizer.ts:51`（[use-global-optimizer.ts:51](hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts:51)）目前寫的是 `toast.error(json.message || "Failed to optimize simulation.")`，但後端實際回應的訊息是巢狀在 `json.data.message`（見上方 body 結構），不是 `json.message`，所以這行目前永遠只會顯示通用的 "Failed to optimize simulation."，看不到「Grid Search 僅支援單一房貸情境」這句真正原因。只需把這一行改成：

  ```ts
  toast.error(json.data?.message || json.message || "Failed to optimize simulation.");
  ```

  這個 hook 的其他部分（`try/catch`、`res.json()` 呼叫方式等）不需要更動。

- **下拉選單「偵測到多筆房貸時 disable Grid Search 選項」這件事，本次明確不做**：已追蹤確認 `GlobalOptimizerCard`（[global-optimizer-card.tsx:152](hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx:152)，簽章僅為 `GlobalOptimizerCard({ targetAge, onApplyResult })`）與其唯一呼叫者 `page.tsx` 目前完全沒有任何房貸數量或房貸清單資料——`page.tsx` 沒有抓取房貸資料，同頁面唯一抓過房貸資料的是另一個不相關元件 `simulation-override-panel.tsx` 的本地 `useState`，且從未往上傳或往下傳給 `GlobalOptimizerCard`。要做「disable 選項」必須先新增資料管線（例如讓 `page.tsx` 呼叫既有的 `useMortgageData()` hook 或新增一次 `/api/finance/mortgages` fetch，再新增一個 prop 往下傳給 `GlobalOptimizerCard`），這已經超出這次 bug 修正的最小改動範圍。**不要新增這條資料管線**，多房貸使用者選了 Grid Search 時，依賴上方第 1 點修好的 toast 錯誤訊息告知原因即可，讓他們自己改選 AUTO 或 FORCE_GA。

## 三、總結

| 檢查項目 | 結果 |
|---|---|
| 核心雙重扣除 bug 是否修復 | ✅ 已修復，程式碼與測試調整合理 |
| `processEarlyRepaymentAtRetirement` / `processMortgageEarlyRepayment` 是否清除乾淨 | ✅ 已確認全 repo 無殘留 |
| 是否因大範圍變更產生新的業務邏輯 bug | ⚠️ 是，見第二節：多房貸 + Grid Search 會被套用成全部一起清償，且無任何防呆 |

## 四、邊際效應修正報告 (Implementation Report)

本修復基於上述建議方向完成實作，透過最小干預原則，將新發現的多房貸 Grid Search 漏洞補齊防線。

### 1. 後端 (Backend) 防呆阻擋
- **檔案**：`GlobalOptimizerServiceImpl.java`
- **變更**：在 `optimize()` 方法的 `FORCE_GRID` 分支中，新增了房貸數量的檢核。當使用者名下有 2 筆以上房貸時，強制阻擋執行並拋出 `OptimizationNotSupportedException`。
- **結果**：成功從底層杜絕了「Grid Search 將多筆房貸強制在同一年結清」的荒謬行為，且有效利用既有的 HTTP 400 錯誤攔截機制，無需新增額外的 Error Handler。

### 2. 前端 (Frontend) 錯誤訊息顯示
- **檔案**：`use-global-optimizer.ts`
- **變更**：修正了 API 錯誤訊息解析。將原本只讀取 `json.message` 的邏輯改為優先讀取 `json.data?.message`。
- **結果**：前端現在能正確捕捉並顯示後端拋出的「Grid Search 僅支援單一房貸情境...」等詳細除錯訊息。透過精準的 Toast 提示使用者改用 `AUTO` 或 `FORCE_GA`，完美避免了需要大幅修改 UI 綁定邏輯所帶來的架構複雜度。

**狀態**：✅ 邊際效應已徹底修復完畢。
