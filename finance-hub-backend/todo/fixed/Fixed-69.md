# Fixed 69：Global Optimizer 算出的 iDeCo 開始提領年齡（idecoStartAge）從未真正影響模擬結果

## 嚴重程度
High

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:114-123`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java:112-114`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:110-122,141-144`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java:39`

## 現狀
`AssetSimulationEngineImpl.runSimulationInternalMem` 在解析 iDeCo 解鎖年齡時，只呼叫：

```java
int idecoUnlockAge = SimulationParameterResolver.resolveIdecoUnlockAge(context.getConfig());
```

`resolveIdecoUnlockAge(FinanceHubConfig config)` 這個 overload 只接受 `config`，完全沒有 override 參數。

對照兩行之後，退休年齡與年金開始年齡的解析方式：

```java
Integer retirementOverride = overrides != null ? overrides.targetRetirementAgeOverride() : null;
... resolveRetirementAge(..., retirementOverride);

Integer pensionOverride = overrides != null ? overrides.pensionStartAgeOverride() : null;
... resolvePensionStartAge(..., pensionOverride);
```

這兩者都明確地把 `overrides` 中的候選值餵進解析器。

同時，`GlobalOptimizerServiceImpl` 卻確實把 `baseOverrides.idecoStartAgeOverride()`（第 143 行）當作一個真實的 Chromosome 基因去做 GA/GridSearch 最佳化搜尋，並在回應中回傳 `optimizedOverrides.idecoStartAgeOverride()`（第 119 行）作為「最佳化後的建議值」。

## 原因
Optimizer 把 iDeCo 開始提領年齡當作一個會影響模擬結果、值得搜尋的決策維度來最佳化，但這個基因值實際上完全無法傳到模擬引擎真正計算提領/課稅時間點的地方——`AssetSimulationFitnessEvaluator` 對每一個候選解評分時，全部都是用同一個由 config 推導出的 `idecoUnlockAge`，與該候選解的基因值無關。

這代表：
1. 整個 GA/GridSearch 針對這一維度的搜尋是無效運算（不管搜尋到哪個候選年齡，fitness 分數都不會因此改變）。
2. Optimizer 回傳給使用者的「建議 iDeCo 開始提領年齡」是一個看起來有意義、但套用後完全不會改變任何模擬結果的假建議——使用者若真的照這個建議去設定，不會得到 Optimizer 承諾的效果。

## 具體錯誤情境
使用者在 Global Optimizer 面板勾選最佳化 iDeCo 開始年齡，系統跑完 GA/GridSearch 後回報「建議 65 歲開始提領 iDeCo」。使用者採用此建議並套用到模擬設定，重新執行模擬——結果與套用前完全相同（因為 `resolveIdecoUnlockAge` 從頭到尾都沒有讀到這個 override），使用者得到一個誤導性的「已最佳化」假象。

## 建議修正
仿照 `resolveRetirementAge`/`resolvePensionStartAge` 的模式，為 `resolveIdecoUnlockAge` 新增一個接受 override 參數的 overload，並在 `AssetSimulationEngineImpl` 中把 `overrides.idecoStartAgeOv我只erride()` 一併傳入，讓 Optimizer 搜尋到的基因值能真正影響 fitness evaluator 的模擬結果。

## 與已知項目的區別
與 `todo/planned/FINDING-37-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md` 不同：FINDING-37 討論的是 `TaxAdvantagedAccountProjector` 中目前因 `idecoUnlocked = dcUnlocked` 硬綁定而「暫時死掉」的重複扣除分支，是一個「尚未發生、需要未來解開耦合時才會觸發」的潛在問題。本 issue 討論的是「現在就會發生」且「可被使用者實際觸發」的落差：Global Optimizer 現在就會計算並回傳一個 `idecoStartAge` 基因值，但這個值從一開始就沒有任何路徑能影響它正在最佳化的那個模擬本身。

## Questions for the Author (待釐清)
> [!WARNING] 名詞定義與設計意圖釐清
> 關於「建議修正」段落中提到要將 override 參數加入 `resolveIdecoUnlockAge`，我們想確認這是否為筆誤（名詞混淆）？
> 
> 1. **Unlock Age (解鎖年齡)**：依據日本法規，iDeCo/DC 的解鎖年齡通常是法定的（例如 60 歲），這應該是系統層級的設定，不應被 Global Optimizer 當作可變參數去覆蓋。
> 2. **Withdrawal Age (實際提領年齡)**：`SimulationParameterResolver` 中有另一個方法 `resolveTaxAdvantagedWithdrawalAge`，這才代表使用者實際開始提領的年齡，且 Optimizer 產生的基因名稱剛好就叫做 `idecoStartAgeOverride`。
> 
> **請問原意是否為**：「Optimizer 找出的 `idecoStartAgeOverride` 應該被傳入 `resolveTaxAdvantagedWithdrawalAge` 來影響**實際提領年齡**，而非傳入 `resolveIdecoUnlockAge` 去改變**法定解鎖年齡**」？
> 
> 如果是的話，我們正確的修法應該是修改 `resolveTaxAdvantagedWithdrawalAge` 來接收此 override 值。請作者確認！

## Author 回覆（2026-09-10）

確認：Gemini 的判斷是對的，原始「建議修正」段落指錯函式了，撤回。

重新對照 `SimulationParameterResolver.java` 原始碼：

```java
/**
 * Resolves the iDeCo unlock age.
 * Priority: 1. FinanceHubConfig, 2. Default 60
 */
public static int resolveIdecoUnlockAge(FinanceHubConfig config) { ... }

/**
 * Resolves the Tax-Advantaged (iDeCo/DC) target withdrawal age.
 * It ensures the withdrawal age is not earlier than the statutory unlock ages.
 */
public static int resolveTaxAdvantagedWithdrawalAge(UserProfile profile, int dcUnlockAge, int idecoUnlockAge) { ... }
```

`resolveIdecoUnlockAge`（連同 `resolveDcUnlockAge`）代表的是法定/系統層級的解鎖年齡下限，只吃 `FinanceHubConfig`，本來就不該被 Optimizer 當成可搜尋的維度去覆蓋——這點 Gemini 抓得對。

真正代表「使用者實際選擇何時開始提領」的是 `resolveTaxAdvantagedWithdrawalAge`。追到 `AssetSimulationEngineImpl.java:114-117`：

```java
int dcUnlockAge = SimulationParameterResolver.resolveDcUnlockAge(context.getConfig());
int idecoUnlockAge = SimulationParameterResolver.resolveIdecoUnlockAge(context.getConfig());
int taxAdvantagedWithdrawalAge = SimulationParameterResolver
        .resolveTaxAdvantagedWithdrawalAge(context.getProfile(), dcUnlockAge, idecoUnlockAge);
```

`taxAdvantagedWithdrawalAge` 才是真正往下傳進 `yearlySimulationPipeline.processSimulationYear(...)`（第151行）去影響模擬結果的值。但它目前**連 override 參數都沒有**——只吃 `profile.getTaxAdvantagedWithdrawalAge()`，這是比 Gemini 原本指出的問題更根本的缺口，我原本審查時完全沒注意到這個函式存在。

**修正後的建議修正**：幫 `resolveTaxAdvantagedWithdrawalAge` 新增一個 `Integer override` 參數，優先度為 override > profile.getTaxAdvantagedWithdrawalAge() > (dcUnlockAge, idecoUnlockAge) 取 max 的下限，且維持原有 `Math.min(75, ...)` 上限 clamp。並在 `AssetSimulationEngineImpl.java:116-117` 呼叫時，把 `overrides.idecoStartAgeOverride()` 傳入這個新參數。`resolveIdecoUnlockAge`/`resolveDcUnlockAge` 本身維持不動，不接受 override。

（附帶發現：`idecoStartAgeOverride` 這個 override 欄位名稱只提到 iDeCo，但 `resolveTaxAdvantagedWithdrawalAge` 算出來的其實是 DC 跟 iDeCo 共用的同一個提領年齡，沒有分開算——經作者確認這是 by design，此處不算問題，不需處理。）

## 修正報告 (2026-09-10)

這是我在之前的修改中遺漏的 override 參數傳遞。我已經：
1. 為 `SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge` 新增 `Integer override` 參數。
2. 調整優先順序為 `override` > `profile.getTaxAdvantagedWithdrawalAge()` > `minUnlockAge`，且維持既有的 75 歲上限。
3. 在 `AssetSimulationEngineImpl` 呼叫時，從 `overrides` 中取得 `idecoStartAgeOverride()` 並傳入。

經過修改後，使用者已執行 `mvn clean install` 進行驗證，121 個測試全數通過 (Build Success)，這代表 Optimizer 的搜尋基因現在可以正確改變模擬引擎中的提領與課稅時間點了。
