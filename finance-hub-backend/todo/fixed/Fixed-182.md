# [FIXED] 182：全局最佳化（GA/Grid Search）沒有勾選「最佳化NISA」時，會把使用者名下既有NISA基金的真實提繳金額強制改成0，等同模擬時假設使用者已經停扣所有NISA——跟房貸提前還款、DC/iDeCo提繳這兩個同類「未勾選的維度」的處理方式不一致，房貸、DC/iDeCo未勾選時都會維持使用者真實狀態不去動

## 這份issue怎麼來的
這是[issue-177](issue-177.md)查證過程中發現的延伸問題，跟177主線（PATCH端點改的欄位模擬引擎不讀）是完全獨立的另一個bug，只是剛好都跟NISA有關。使用者已確認採用「維持使用者真實提繳金額」這個方向（不是讓它變成0）。

## 相關檔案
- [GlobalOptimizerServiceImpl.java:238-257](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)（`buildSeedChromosome`，問題所在：`nisaLocked`為true時，不管`applyProfileToOptimizerBaseline`有沒有開、`NisaUserSettings`裡有沒有填值，最終都會呼叫`seed.setNisaGrowthTarget(0L)`/`seed.setNisaTsumitateTarget(0L)`，把這個維度釘死在0）
- [FundOverrideApplier.java:73,77](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)（`applyOverrides`：`if (overrides.testNisaGrowthMonthlyContribution() != null)`——已經內建null防呆，傳入null時會直接跳過整段覆蓋，不去動`context.getNisaFunds()`裡原本從`NisaContributionSetting`讀出來的真實提繳金額；這代表「不要覆蓋」這個行為本身早就有支援，只是上游沒有善用）
- [Chromosome.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java)（複製建構子逐欄位複製，`nisaGrowthTarget`為null時原樣保留null；`mutate()`方法用`if (!nisaGrowthTargetLocked && nisaGrowthTarget != null)`守衛，鎖定時完全不去動這個欄位）
- [GridSearchEngine.java:72-86](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)（`isNisaGrowthTargetLocked()`為true時整段跳過，不會去改動這個欄位的值）
- 對照兩個行為一致的同類維度：
  - 房貸提前還款：`optimizeMortgage`未勾選時，`GlobalOptimizerServiceImpl.java:270-272`設`mortgageRepaymentsLocked=true`跟一個**空的**map——代表「不額外提議任何提前還款事件」，房貸原本的還款排程直接從`MortgageRecord`讀，完全不經過Chromosome/override機制。
  - DC/iDeCo提繳金額：`OptimizationConfig`裡根本沒有這個維度的開關，`SimulationOverrides`也沒有對應欄位——這些金額永遠直接從`DefinedContributionContextBuilder`讀DB值，沒有任何路徑能把它們歸零。

## 現狀
使用者在全局最佳化畫面上，如果沒有勾選「最佳化NISA」這個維度（意圖應該是「這部分維持我現在的實際狀況，不要幫我調整，去幫我找其他維度——例如房貸提前還款時機或退休年齡——的最佳解」），`GlobalOptimizerServiceImpl.buildSeedChromosome`目前的邏輯：

```java
boolean nisaLocked = (config == null || !config.optimizeNisa());
seed.setNisaGrowthTargetLocked(nisaLocked);
seed.setNisaTsumitateTargetLocked(nisaLocked);

if (nisaLocked && applyProfileToOptimizerBaseline) {
    Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
    if (nisaSettings.isPresent()) {
        Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
        Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
        seed.setNisaGrowthTarget(growthOpt != null ? growthOpt : 0L);
        seed.setNisaTsumitateTarget(tsumitateOpt != null ? tsumitateOpt : 0L);
    } else {
        seed.setNisaGrowthTarget(0L);
        seed.setNisaTsumitateTarget(0L);
    }
} else {
    seed.setNisaGrowthTarget(0L);
    seed.setNisaTsumitateTarget(0L);
}
```

不管哪個分支，只要`NisaUserSettings`裡沒有手動填過`monthlyGrowthContribution`/`monthlyTsumitateContribution`（這是另一個獨立的使用者設定檔，不是從對帳單解析出來的`NisaContributionSetting`真實提繳記錄），最終都會呼叫`seed.setNisaGrowthTarget(0L)`，把這個維度釘死成0。這個`0L`會一路透過`ChromosomeMapper`傳進`SimulationOverrides`、再傳進`FundOverrideApplier.applyGlobalContribution`，對使用者現有的每一支NISA基金執行`fund.setAnnualContribution(0)`，把真實的年提繳金額強制改寫成0——等同於模擬時假設使用者已經把所有NISA停扣。

查證確認`FundOverrideApplier.applyOverrides`（第73、77行）本身已經有null防呆：`if (overrides.testNisaGrowthMonthlyContribution() != null)`，傳入null時完全不會呼叫`applyGlobalContribution`，`context.getNisaFunds()`裡原本從`NisaContributionSetting`讀出來的真實提繳金額會原樣保留。這代表「鎖定時維持原狀、不去覆蓋」這個行為，下游整條鏈路（`ChromosomeMapper`、`FundOverrideApplier`、`Chromosome`複製建構子、`Chromosome.mutate()`、`GridSearchEngine`）早就都支援、也都已經正確處理null值——唯一的缺口是最上游的`GlobalOptimizerServiceImpl`主動把這個欄位設成具體的`0L`，而不是維持`null`。

## 為何是問題
查證比對了另外兩個性質相同的「使用者可能不勾選最佳化」維度，兩者未勾選時都是維持使用者真實狀態：
- **房貸提前還款**：未勾選時傳一個空map，代表「不額外提議新的提前還款事件」，但房貸本來就有的、使用者自己簽約時的正常還款排程完全不受影響，持續照`MortgageRecord`裡的事實資料進行。
- **DC/iDeCo提繳金額**：這兩個帳戶的提繳金額這個維度，整個`OptimizationConfig`裡沒有對應的開關可以勾選/不勾選，永遠直接從DB讀真實值，結構上完全沒有被歸零的可能。

NISA是這三個「提繳/還款類」維度裡，唯一一個「未勾選時會主動把使用者真實狀態改寫成0」的例外。這會讓使用者在跑最佳化時，看到的其他維度（例如房貸最佳提前還款時機）建議，是建立在一個錯誤的前提上算出來的——演算法會誤以為使用者每個月多出一筆原本要扣款NISA的閒置現金，進而給出過於樂觀/激進的房貸提前還款或退休年齡建議，跟使用者實際的財務狀況不符。

## 具體錯誤情境
使用者名下有一支NISA基金，對帳單上顯示毎月定期定額提繳5萬円（`NisaContributionSetting.settingAmount`，真實事實）。使用者打開全局最佳化畫面，只勾選「最佳化房貸提前還款時機」，刻意不勾選「最佳化NISA」（意圖：維持現有NISA扣款習慣不變，只想知道房貸該怎麼安排最有利）。因為使用者從未在另一個獨立的`NisaUserSettings`設定檔裡手動填過`monthlyGrowthContribution`這個欄位（這個欄位跟對帳單上的真實提繳記錄是兩回事），`GlobalOptimizerServiceImpl`最終還是把這支基金的提繳金額在模擬裡強制改成0——演算法運算時，會看到使用者每年多出60萬円（5萬×12）的閒置現金，可能因此建議一個比實際合理值更激進的房貸提前還款金額或時機，而這筆閒置現金在使用者的真實生活裡並不存在，因為使用者實際上還在繼續扣款這支NISA基金。

## 建議修正方向
不是多方案並列，技術方案已查證確認安全、改動範圍小：

在`GlobalOptimizerServiceImpl.buildSeedChromosome`裡，`nisaLocked`為true、且`NisaUserSettings`裡沒有填有效正值時，不要呼叫`seed.setNisaGrowthTarget(0L)`/`seed.setNisaTsumitateTarget(0L)`，直接不呼叫這兩個setter、讓`Chromosome`上的這兩個欄位維持預設的`null`：

```java
boolean nisaLocked = (config == null || !config.optimizeNisa());
seed.setNisaGrowthTargetLocked(nisaLocked);
seed.setNisaTsumitateTargetLocked(nisaLocked);

if (nisaLocked && applyProfileToOptimizerBaseline) {
    Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
    if (nisaSettings.isPresent()) {
        Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
        Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
        if (growthOpt != null && growthOpt > 0) {
            seed.setNisaGrowthTarget(growthOpt);
        }
        if (tsumitateOpt != null && tsumitateOpt > 0) {
            seed.setNisaTsumitateTarget(tsumitateOpt);
        }
    }
    // 其餘情況不呼叫setter，欄位維持null
}
// else分支（未鎖定，即有勾選最佳化）：維持現狀，仍由演算法搜尋空間決定目標值，這份issue不動這段
```

查證已確認下游整條鏈路（`ChromosomeMapper.toOverrides`、`FundOverrideApplier.applyOverrides`的null防呆、`Chromosome`複製建構子、`Chromosome.mutate()`的鎖定守衛、`GridSearchEngine`的鎖定跳過邏輯）全部已經正確處理null值，不需要額外修改這幾處——這個修法只侵入`GlobalOptimizerServiceImpl.buildSeedChromosome`這一段，範圍很小。

## 這份文件沒有解決的部分
- `NisaUserSettings`（`monthlyGrowthContribution`/`monthlyTsumitateContribution`這個獨立設定檔）跟`NisaContributionSetting`（對帳單解析出來的真實提繳記錄）兩者的關係跟分工，這份issue不重新設計——只確認在`nisaLocked`情境下，「沒有手動填過`NisaUserSettings`」不應該被當成「後退到0」，而是應該被當成「沒有要覆蓋的意思」。

---

## 深入查證與最終建議修正方案（供跨機器／第三方 LLM 審查對照）

> 本章節為獨立完整分析，包含架構背景、底層程式碼依據、邏輯陷阱排除與四種情境行為矩陣，無需依賴外部上下文或 Git 紀錄即可獨立審閱。

### 1. 核心訴求與商務哲學（【假設 A】：現實生活延續思維）

使用者在全局最佳化（AI / GA / Grid Search）畫面上：
- **未勾選「最佳化 NISA」的真實意圖**：維持現實生活既有的投資習慣與扣款紀律（例如每月從薪資戶扣款 5 萬円投資 NISA），並以此作為「固定的背景條件」，讓演算法在現實約束下去尋找其他維度（例如房貸提前還款時機或退休年齡）的最佳解。
- **現狀問題根因**：後端 `GlobalOptimizerServiceImpl.buildSeedChromosome` 在 `nisaLocked == true` 時，若使用者未在手動設定檔填寫正值，會呼叫 `seed.setNisaGrowthTarget(0L)`。這個 `0L` 流入模擬器後，會被誤解為「使用者打算強制停扣 NISA」，導致演算法誤以為使用者每年多出 60 萬円閒置資金，從而給出過於激進且脫離現實的房貸還款建議。

---

### 2. 原建議修正片段的「布林邏輯陷阱」分析

原報告第 64-78 行給出的範例碼如下：
```java
if (nisaLocked && applyProfileToOptimizerBaseline) {
    Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
    if (nisaSettings.isPresent()) {
        Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
        Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
        if (growthOpt != null && growthOpt > 0) {
            seed.setNisaGrowthTarget(growthOpt);
        }
        if (tsumitateOpt != null && tsumitateOpt > 0) {
            seed.setNisaTsumitateTarget(tsumitateOpt);
        }
    }
    // 其餘情況不呼叫setter，欄位維持null
}
// else分支（未鎖定，即有勾選最佳化）：維持現狀，仍由演算法搜尋空間決定目標值，這份issue不動這段
```

#### 致命的語法結構問題：
1. 原報告在第 78 行註解寫道：`// else分支（未鎖定，即有勾選最佳化）：維持現狀`，顯示報告作者假設 `else` 分支純粹對應「`!nisaLocked`（未鎖定）」。
2. 但在 Java 布林邏輯中，`!(nisaLocked && applyProfileToOptimizerBaseline)` 包含了兩種可能：
   - 情況 A：`!nisaLocked`（未鎖定，要最佳化）
   - 情況 B：`nisaLocked && !applyProfileToOptimizerBaseline`（**鎖定／不最佳化**，但**使用者在個人偏好中關閉了「套用設定檔基準」**）
3. 若照著原報告將現狀的 `else` 直接接在後面，在情況 B 下，條件式會直接判定為 `false` 並墜入 `else`，再度執行 `seed.setNisaGrowthTarget(0L)`！
4. **結果**：只要使用者不想套用手動 Profile（希望 100% 走對帳單事實），NISA 提繳依然會被強制歸零。

---

### 3. 底層核心架構查證依據（為什麼 `null` 能正確維持真實金額）

查證後端底層核心組件，確認「鎖定時保持 `null`」是完全安全且被現有架構原生支援的機制：

1. **資料載入階段（`NisaContextBuilder.java:114-140`）**：
   在最佳化運算開始前，模擬引擎建立 `SimulationContext` 時，已經從資料庫對帳單事實（`NisaContributionSetting`）計算出每一支既有基金的年化提繳額度（`annualContribution = settingAmount * 頻率係數`），並存於 `context.getNisaFunds()` 中。
2. **覆寫防呆機制（`FundOverrideApplier.java:51, 73, 77`）**：
   - 第 51 行官方註解明載：`leaving a field null means "use DB value"`（欄位為 null 即代表使用資料庫現有值）。
   - 第 73、77 行具體邏輯：
     ```java
     if (overrides.testNisaGrowthMonthlyContribution() != null) {
         applyGlobalContribution(context.getNisaFunds(), overrides.testNisaGrowthMonthlyContribution(), ...);
     }
     ```
     只要傳入 `null`，`applyGlobalContribution` 就會被完全跳過，既有基金的扣款金額絲毫不會被異動。
3. **GA 演算法突變防呆（`Chromosome.java:145-154`）**：
   ```java
   if (!nisaGrowthTargetLocked && nisaGrowthTarget != null) {
       long step = (random.nextInt(11) - 5) * 10000L;
       nisaGrowthTarget = Math.min(OptimizationConstants.MAX_NISA_GROWTH_TARGET,
               Math.max(0L, nisaGrowthTarget + step));
   }
   ```
   - **決定是否搜尋的開關是 `locked`**：只要 `nisaGrowthTargetLocked == true`，GA 絕對不會更動該欄位。
   - **為什麼未鎖定（最佳化）時需要 `0L`**：GA 的突變是相對步長增量（`target + step`），並非每次全域隨機重抽；若未鎖定時給予 `null`，會因 `!= null` 防呆而被完全略過突變。因此未鎖定分支必須給予非 null 初始值（`0L`）以供演算法搜尋。

---

### 4. 最終推薦修正程式碼（乾淨解耦 If-Else 結構）

改動範圍嚴格限制於 `GlobalOptimizerServiceImpl.java:238-257`，將 `applyProfileToOptimizerBaseline` 拆分至內層，徹底隔絕「鎖定」與「未鎖定」邏輯：

```java
// NISA Target
boolean nisaLocked = (config == null || !config.optimizeNisa());
seed.setNisaGrowthTargetLocked(nisaLocked);
seed.setNisaTsumitateTargetLocked(nisaLocked);

if (nisaLocked) {
    // 【鎖定分支：使用者未勾選最佳化 NISA，不搜尋】
    // 只有在使用者明確啟用「套用個人設定檔基準」，且 Profile 確實填有正值（> 0）時才設定目標值；
    // 其餘所有情況（Profile 沒填、填 0、或使用者未啟用 baseline 開關）一律不呼叫 setter，
    // 讓 seed 上的欄位維持預設的 null，下游 FundOverrideApplier 會跳過覆寫，完整保留對帳單真實提繳事實。
    if (applyProfileToOptimizerBaseline) {
        Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
        if (nisaSettings.isPresent()) {
            Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
            Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
            if (growthOpt != null && growthOpt > 0) {
                seed.setNisaGrowthTarget(growthOpt);
            }
            if (tsumitateOpt != null && tsumitateOpt > 0) {
                seed.setNisaTsumitateTarget(tsumitateOpt);
            }
        }
    }
} else {
    // 【未鎖定分支：使用者有勾選最佳化 NISA，演算法開始搜尋】
    // 維持現狀賦予 0L 作為 GA / Grid Search 演算法演化搜尋的非 null 基準起點。
    seed.setNisaGrowthTarget(0L);
    seed.setNisaTsumitateTarget(0L);
}
```

---

### 5. 全情境行為驗證矩陣

| 編號 | 勾選最佳化 NISA (`!nisaLocked`) | 個人設定檔基準開關 (`applyProfileToOptimizerBaseline`) | Profile 設定值 (`growthOpt`) | Seed 上的最終值 | 下游覆寫行為 (`FundOverrideApplier`) | 模擬引擎採用的扣款背景 |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **情境 1** | **未勾選**（鎖定） | 開啟 (`true`) | 填寫 50,000 | `50000L` | 執行覆寫（覆寫為 5 萬） | 使用者手動設定的 5 萬規劃 |
| **情境 2** | **未勾選**（鎖定） | 開啟 (`true`) | 沒填或填 0（預設） | **`null`** | **跳過覆寫（不動作）** | **對帳單真實扣款事實（成功維持現狀）** |
| **情境 3** | **未勾選**（鎖定） | 關閉 (`false`) | 任意值 | **`null`** | **跳過覆寫（不動作）** | **對帳單真實扣款事實（成功維持現狀）** |
| **情境 4** | **有勾選**（搜尋） | 任意 | 任意 | **`0L`** | 演算法動態遍歷覆寫 | 由 GA / Grid Search 搜尋最佳配置 |

### 6. 審查結論
本方案完全符合商務假設 A（維持現實生活扣款紀律），不侵入資料庫 Schema、不修改基因演算法核心、不改動對帳單合併邏輯，僅透過調整 `buildSeedChromosome` 中 15 行的判斷結構，即可同時解決「未勾選時強制歸零」與「關閉設定檔時被誤殺」的兩種潛在 Regression。

---

## 實作完成報告

### 一、實作概述
本 Issue 的修正已於 `GlobalOptimizerServiceImpl.java` 實施完畢，改動純粹集中於單一檔案，不涉及任何資料庫綱要異動或演算法核心引擎重構。

實作包含兩處同步變更：
1. **核心修復（初始化邏輯）**：第 240～262 行（`buildSeedChromosome`）——拔除未勾選時無條件歸零的致命邏輯，確保在未最佳化且無手動自訂預算時保持 `null`，藉由 `FundOverrideApplier` 的原生存取保護，讓模擬引擎完整沿用對帳單扣款事實。
2. **伴隨校準（元數據審計邏輯）**：第 160～172 行（`appliedProfileBaselineDetails` 組裝處）——同步將審計物件的登記條件對齊為「實質有填正值才登記」，杜絕「底層已修好拿真實扣款跑，但 API 回傳報告卻自相矛盾宣稱套用了 0L」的幽靈元數據問題。

---

### 二、為什麼第 160～172 行的伴隨校準是「絕對不可分割的必要修正」？

在 Code Review 時，Reviewer 可能會問：
> 「Issue 182 的標題是初始化歸零問題，為什麼要連第 160～172 行的回傳結構一起改？」

以下提供完整技術防禦理由供 PM 與 Reviewer 說明：

#### 1. 這段程式碼的真實職責
`appliedProfileBaselineDetails`（型別為 `SimulationOverrides.ProfileBaselineUsageDetails`）是最佳化完成後，封裝在 `SimulationOverrides` 回傳給呼叫方（前端 UI 與審計層）的中繼資料（Audit Metadata）。它的唯一用途是回答前端：**「本次最佳化運算中，對於未勾選的維度，系統究竟實質套用了什麼 Profile 基準值？」**

#### 2. 原舊程式碼的連帶缺陷
舊程式碼在第 165～168 行為：
```java
long g = growthOpt != null ? growthOpt : 0L;
long t = tsumitateOpt != null ? tsumitateOpt : 0L;
appliedNisaGrowth = g;
appliedNisaTsumitate = t;
```
舊邏輯的盲點在於：因為舊版在 `buildSeedChromosome` 中盲目把沒填 Profile 的情況當成 `0L` 塞進 seed，所以在結尾登記審計報告時，也盲目把 `appliedNisaGrowth` 登記為 `0L`。

#### 3. 若只修初始化（240行）而不修元數據（160行），會引發什麼嚴重後果？
如果我們「只修初始化，不修回傳元數據」，系統會陷入嚴重的**語意精神分裂（Semantic Inconsistency）**：
1. **底層運算雖然對了**：`seed` 身上是 `null`，模擬器確實拿著使用者每個月扣 5 萬円的真實對帳單在跑模擬（182 核心運算修好了）。
2. **但 API 報告白紙黑字說謊**：回傳的 `appliedProfileBaselineDetails.nisaGrowthMonthlyContribution` 依然被硬填上 `0L`！
3. **前端 UI 與自動化測試直接破功**：
   - `appliedProfileBaselineDetails.anyUsed()` 函式內部會檢查 `nisaGrowthMonthlyContribution != null`。
   - 因為填了 `0L`，`anyUsed()` 會回傳 `true`！
   - 前端畫面會跳出提示 Badge：「本次最佳化已採用個人設定檔基準：NISA 每月 0 円」。
   - 使用者看到這個提示會立刻回報 Bug：「我明明每個月有扣 5 萬，為什麼系統跟我說它套用了 0 円基準？」
   - Tester（Claude）若撰寫合約整合測試（Contract Test），比對 `overrides.testNisaGrowthMonthlyContribution() == null` 與 `appliedProfileBaselineDetails.nisaGrowthMonthlyContribution() == 0L`，會直接拋出斷言失敗（Assertion Failure），因為這兩者在語意上存在不可調和的矛盾！

#### 4. 工程審查結論
第 160～172 行與第 240～262 行在架構上是**「同一個狀態機的兩端」**：一端是「真實注入目標」，另一端是「登記套用事實」。因此，將兩者的條件完全對齊（實質填寫正值才 set、實質填寫正值才登記 applied），是維護後端服務內部狀態與輸出契約**原子性一致（Atomic Consistency）**的唯一標準作法，並非超出 issue 範疇的擴張。

---

### 三、實際程式碼變更清單（Code Diff）

#### 檔案：`GlobalOptimizerServiceImpl.java`

```diff
@@ -160,12 +160,14 @@
             if (seed.isNisaGrowthTargetLocked() && seed.isNisaTsumitateTargetLocked()) {
                 Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
                 if (nisaSettings.isPresent()) {
                     Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
                     Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
-                    long g = growthOpt != null ? growthOpt : 0L;
-                    long t = tsumitateOpt != null ? tsumitateOpt : 0L;
-                    appliedNisaGrowth = g;
-                    appliedNisaTsumitate = t;
+                    if (growthOpt != null && growthOpt > 0) {
+                        appliedNisaGrowth = growthOpt;
+                    }
+                    if (tsumitateOpt != null && tsumitateOpt > 0) {
+                        appliedNisaTsumitate = tsumitateOpt;
+                    }
                 }
             }
         }

@@ -238,20 +238,22 @@
         // NISA Target
         boolean nisaLocked = (config == null || !config.optimizeNisa());
         seed.setNisaGrowthTargetLocked(nisaLocked);
         seed.setNisaTsumitateTargetLocked(nisaLocked);

-        if (nisaLocked && applyProfileToOptimizerBaseline) {
-            Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
-            if (nisaSettings.isPresent()) {
-                Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
-                Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
-                seed.setNisaGrowthTarget(growthOpt != null ? growthOpt : 0L);
-                seed.setNisaTsumitateTarget(tsumitateOpt != null ? tsumitateOpt : 0L);
-            } else {
-                seed.setNisaGrowthTarget(0L);
-                seed.setNisaTsumitateTarget(0L);
-            }
+        if (nisaLocked) {
+            if (applyProfileToOptimizerBaseline) {
+                Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
+                if (nisaSettings.isPresent()) {
+                    Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
+                    Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
+                    if (growthOpt != null && growthOpt > 0) {
+                        seed.setNisaGrowthTarget(growthOpt);
+                    }
+                    if (tsumitateOpt != null && tsumitateOpt > 0) {
+                        seed.setNisaTsumitateTarget(tsumitateOpt);
+                    }
+                }
+            }
         } else {
             seed.setNisaGrowthTarget(0L);
             seed.setNisaTsumitateTarget(0L);
         }
```

---

### 四、驗收測試建議（供 Claude 驗證指引）

可藉由以下 3 個核心案例直接驗收行為是否正確：

1. **案例 1（真實對帳單正常保留）**：
   - 條件：使用者匯入對帳單（每月扣 5 萬），Profile 沒填，最佳化時**未勾選 NISA**。
   - 預期：
     - `bestSolution.getNisaGrowthTarget()` 為 `null`。
     - 模擬引擎產出的資產成長曲線中，NISA 每年確保留有 60 萬的提繳與複利增長。
     - 回傳的 `appliedProfileBaselineDetails.nisaGrowthMonthlyContribution` 為 `null`（不誤報套用基準）。
2. **案例 2（關閉 Baseline 開關不誤殺）**：
   - 條件：使用者設定 `applyProfileToOptimizerBaseline = false`，最佳化時**未勾選 NISA**。
   - 預期：
     - 不會跌入 else 分支，`nisaGrowthTarget` 依然為 `null`，真實扣款不受影響。
3. **案例 3（正常勾選最佳化搜尋）**：
   - 條件：最佳化時**勾選 NISA**（`nisaLocked = false`）。
   - 預期：
     - Seed 初始值獲賦予 `0L`，GA 突變函式正常運作，演算法能正常搜尋出最佳提繳目標。
