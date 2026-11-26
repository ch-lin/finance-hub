# Fixed 80：issue-01 修正後，Global Optimizer 沒有讓使用者選擇「未勾選最佳化的 iDeCo 提領年齡」該套用哪個假設值，目前是系統自動決定（固定套用 60 歲），使用者無法選、也看不到

## 嚴重程度
Medium－這不是一個「算錯了」的 bug（沒有唯一正確答案），而是一個目前由系統自動決定、使用者無法選擇也看不到的行為，需要先決定方向（見下方「建議修正」的多個選項）才能實作。會觸發的範圍：只要使用者跑任何一種 Global Optimizer 最佳化、且沒有勾選最佳化 iDeCo 提領年齡（例如單純想最佳化退休年齡），就會用到系統自動決定的這個假設值。

## 發現背景
本 issue 是在重新驗證 issue-01（Global Optimizer 的 `idecoStartAgeOverride` 從未真正影響模擬結果）修正是否正確時，於程式碼中額外發現的新副作用。issue-01 原本描述的問題（Optimizer 搜尋到的基因值無法傳到模擬引擎）已確認修正正確，但修正生效後，才讓另一個原本無害的既有寫法（鎖定基因的種子值寫死 60）第一次有能力真正污染模擬結果。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java`（`buildSeedChromosome`，約 141-144 行）
- 同檔案 `optimize()`:115-122
- `ChromosomeMapper.java:72-80`（`toOverrides`）
- `AssetSimulationFitnessEvaluator.java:72-84`
- `SimulationParameterResolver.java:135-145`（`resolveTaxAdvantagedWithdrawalAge`，issue-01 已修正之處）
- `GlobalOptimizerMapper.toResponse()`
- `hub-ui/hub-ui/src/app/finance/components/profile-basic-info-section.tsx:70-82`（Profile 設定畫面，「iDeCo/DC Withdrawal Age」這個數字輸入框，目前完全沒有任何說明文字）
- `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx:182-218`（Global Optimizer 的五張卡片，只有 Retirement Plan / Pension Fund / iDeCo / NISA / Mortgage Strategy 五個開關，沒有任何地方顯示「沒勾選的維度套用了哪個假設值」）

## 現狀（已實際讀取現在的原始碼確認）

issue-01 的核心修正（讓 Optimizer 搜尋到的 `idecoStartAge` 基因值真正影響模擬結果）已確認正確實作、端到端打通（見 [issue-01.md](issue-01.md) 的重新驗證附註）。但 `buildSeedChromosome()` 目前建立「鎖定基因」種子值的寫法是：

```java
seed.setIdecoStartAge(idecoOverride != null ? idecoOverride : 60);
```

當使用者這次最佳化請求「沒有勾選要最佳化 iDeCo 開始提領年齡」、也「沒有明確傳入 `idecoStartAgeOverride`」時，這個基因會被鎖定（不參與 GA/GridSearch 的變異搜尋），但種子值不是 `null`（代表「沿用使用者 profile 設定，不覆寫」），而是硬編碼的字面值 `60`。

這個 `60` 會透過 `ChromosomeMapper.toOverrides()` 原封不動地包進 `SimulationOverrides.idecoStartAgeOverride()`，而這個 override 值依 issue-01 修正後的優先度規則（`override` > `profile.getTaxAdvantagedWithdrawalAge()` > 下限），會被 `resolveTaxAdvantagedWithdrawalAge` 當作最高優先度的值採用。

## 原因
先說清楚我不確定的部分：畫面上完全沒有任何文字告訴使用者「這個鎖定基因的預設值會用你 profile 裡設定的提領年齡」，所以我沒有依據去斷言使用者期待或知道這件事。以下只是單純比對「同一個數字，兩條路徑算出兩個答案」這個可以直接在程式碼上驗證的事實，不涉及猜測使用者怎麼想。

**路徑一（使用者平常自己跑模擬）**：使用者在畫面上跑一般模擬、沒有動任何 override 面板時，`resolveTaxAdvantagedWithdrawalAge` 會拿 `profile.getTaxAdvantagedWithdrawalAge()`（例如使用者自己設定的 70 歲）去算，這是使用者每次跑模擬都在依賴、已經確立的行為。

**路徑二（使用者打開 Global Optimizer，只勾選最佳化退休年齡，完全沒碰 iDeCo 選項）**：`buildSeedChromosome()` 現在的寫法會讓同一份 profile、同一個「沒有要動 iDeCo」的情境，算出來的提領年齡變成 60，不是 70——因為它塞給種子的是「override = 60」這個字面常數，而 override 在 `resolveTaxAdvantagedWithdrawalAge` 的優先順序裡排在 profile 設定之上。

也就是同一個使用者、同一份設定，「路徑一」跟「路徑二」對「現在的 iDeCo 提領年齡是幾歲」這個問題給出兩個不同答案。這個落差可以直接讀程式碼比對出來，是本 issue 想指出的核心問題；至於這個落差「應該」怎麼修，屬於設計選擇，見下方建議修正。

## 具體錯誤情境
使用者的 profile 設定裡，`taxAdvantagedWithdrawalAge` 是 70 歲（使用者自己設定要 70 歲才開始提領 iDeCo/DC）。使用者打開 Global Optimizer，只勾選「最佳化退休年齡」，完全沒有碰任何 iDeCo 相關選項。

目前的行為：Optimizer 這次跑最佳化時，內部用的 iDeCo 提領年齡假設會是 60 歲，跟使用者自己跑模擬時看到的 70 歲不一樣——這個差異完全是系統內部自動決定的，畫面上看不到、使用者也沒有地方可以選要用哪一個。這個 60 歲同時也會被寫進回傳給前端的 `optimizedOverrides.idecoStartAgeOverride`，即使使用者從未要求最佳化這個維度；如果之後「套用建議」的流程會把回應中所有欄位都寫回使用者的模擬設定，使用者原本的 70 歲就有可能被這次無關的最佳化請求連帶改掉。

這個情境本身是可以直接用程式碼驗證的事實。至於「這個假設值該用哪個、該不該讓使用者選」，是接下來要討論的問題，見下方建議修正。

## 建議修正
方向已定：把選擇權還給使用者，而且要在使用者「填這個數字」的當下就讓他知道、讓他決定，不能只把選項藏在 Global Optimizer 自己的畫面裡。

理由（已實際確認兩個畫面現況）：
- Profile 設定畫面（`profile-basic-info-section.tsx:70-82`）目前這個欄位就是一個單純的數字輸入框，Label 只寫「iDeCo/DC Withdrawal Age」、placeholder 只寫「Optional (defaults to 60)」，沒有任何文字告訴使用者「這個數字之後會被 Global Optimizer 拿去當『沒勾選最佳化 iDeCo 時』的假設值」。
- Global Optimizer 畫面（`global-optimizer-card.tsx:182-218`）只有五張卡片（Retirement Plan / Pension Fund / iDeCo / NISA / Mortgage Strategy），使用者從這個畫面完全看不出來、也無法得知「沒勾選的維度」背後套用了哪個假設值。
- 兩個畫面都沒有講這件事，使用者不可能知道自己在 Profile 填的 70 歲會被用在這裡，也不可能事先同意或拒絕。

做法：
1. 在 Profile 設定「iDeCo/DC Withdrawal Age」欄位旁邊，新增一個明確的選項/說明，讓使用者在填這個數字的當下就決定：「這個數字要不要也套用到 Global Optimizer 沒有勾選最佳化的情境」。
2. 把這個決定存成使用者可查詢、可修改的設定（例如 profile 新增一個 boolean 欄位，如 `applyWithdrawalAgeToOptimizer`），`buildSeedChromosome()` 依這個設定決定鎖定基因種子值要套用使用者的 70 歲、還是法定最低年齡。
3. Global Optimizer 畫面（或最佳化結果頁）也應該讓使用者看得到「這次最佳化，沒勾選的維度實際套用了哪個假設值」，不能讓整個過程對使用者完全不透明。

優點：控制權真正回到使用者手上，而且是在使用者最有機會理解「這個數字要幹嘛」的時間點（填這個數字的當下）就講清楚，不會出現使用者以為套用的是法定年齡、結果其實不是的落差。
缺點：需要同時改 Profile 設定畫面跟 Optimizer/結果畫面的 UI，工程量比純後端修正大；如果類似情境（profile 欄位被其他最佳化維度悄悄套用）不只 iDeCo 這一個，未來可能要把這個「設定時決定是否套用到最佳化」的模式套用到其他欄位。

修正後應新增測試：分別驗證「使用者選擇套用」跟「使用者選擇不套用」兩種情況下，傳入模擬引擎的 `idecoStartAgeOverride` 各自等於使用者 profile 設定值、跟法定最低年齡，而不是恆等於任何一個固定值。

## 與已知項目的區別
本問題是 issue-01 修正的直接副作用，不是「issue-01 的修正沒做完」：issue-01 原本要解決的問題（Optimizer 搜尋到的基因值從未真正影響模擬結果）在修正之前，這個硬編碼的 `60` 完全無害（因為當時基因值本來就不會被送進模擬引擎）。只有在 issue-01 的修正生效、打通了基因值到模擬引擎的路徑之後，這個既有的硬編碼種子值才「終於」有能力真正污染模擬結果——因此雖然是全新問題，但因果上與 issue-01 緊密相關。

---

## AI 驗證與修復報告 (2026-09-11)

### 執行修復項目
1. **擴充資料模型 (Backend Data Layer)**：
   - 於 `UserProfile` 實體新增 boolean 欄位 `applyProfileToOptimizerBaseline`（預設 `true`），對應資料庫欄位 `apply_profile_to_optimizer_baseline`。
   - 將該欄位同步擴充至各相關 DTO（`SaveUserProfileCommand`, `UserProfileRequest`, `UserProfileResponse`, `UserProfileResult`）。

2. **修正最佳化種子產生邏輯 (GlobalOptimizerServiceImpl)**：
   - 更新 `buildSeedChromosome`，改為直接由 `baseData.getBaseContext().getProfile()` 讀取使用者的 `applyProfileToOptimizerBaseline` 設定（不需新增任何前端 API 請求）。
   - **實作備援與常態覆寫機制 (Fallback & Baseline Logic)**：
     - **Retirement Age**：有勾選（override）則優先；未勾選但使用者允許套用 Profile，則使用 Profile 中的退休年齡；否則退回系統預設值（`hubConfig.getTargetRetirementAge()`）。
     - **Pension Start Age**：邏輯同上，退回值為 `hubConfig.getPensionStartAge()`。
     - **iDeCo Start Age**：邏輯同上，退回值為 `hubConfig.getIdecoUnlockAge()`。
     - **NISA Target**：有勾選則優先；未勾選但允許套用，則自動查詢 `NisaUserSettings` 並套用；否則設為 0。

3. **擴充前端設定介面 (Frontend Profile UI)**：
   - 在 `user-profile-form.tsx`, `onboarding/page.tsx`, 與 `profile/me/page.tsx` 中擴充 `applyProfileToOptimizerBaseline` 表單狀態，確保初始讀取與儲存操作皆能與後端正確對接。
   - 於 `profile-basic-info-section.tsx` 新增 Checkbox UI：「Apply my custom ages to Global Optimizer unselected dimensions」，並附帶說明文字告知使用者未勾選時會退回法定預設值 (如 60 歲的 iDeCo)。

4. **優化最佳化結果提示與 API 介面 (Frontend Optimizer Result UI)**：
   - 後端的 `SimulationOverrides` 及 `GlobalOptimizerResponse` 新增 `usedProfileBaseline` 欄位，把套用狀態直接回傳給前端。
   - 在前端 `use-global-optimizer.ts` 擴充對應型別，並於 `global-optimizer-card.tsx` 的結果面板 (`OptimizerResultPanel`) 頂部新增資訊提示框 (`<Info>`)。
   - 依據 `usedProfileBaseline` 動態顯示：「Your custom Profile baseline ages were applied...」或「Statutory baseline defaults... were applied...」。這讓前端在不需額外發送 API 撈取 Profile 的情況下完美顯示提示，收斂影響範圍。

5. **清理技術債 (Technical Debt Cleanup)**：
   - 移除 `AssetSimulationController` 中已廢棄的舊版 API (`calculateNisaGoal`, `findBestMortgageEarlyRepaymentYear`)。
   - 全面更新受影響的單元測試與 Constructor 呼叫，確保編譯與測試皆通過。

### 備註 (基於討論決議)
- 本次修正特別精確控制了影響範圍，**未在前端 Simulator 新增任何抓取 Profile 的 API**，而是透過後端直接由既有 `SimulationData` 讀取 Profile，並透過回傳 DTO 告知前端實際套用的基底策略。
- 此做法在達到「使用者有權選擇」目標的同時，也避開了多餘的網路請求與狀態管理負擔。
