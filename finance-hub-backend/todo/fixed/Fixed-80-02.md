# Fixed 80-02：issue-18 修正後，最佳化結果畫面上「有沒有套用你自訂年齡」的提示文字，跟這次最佳化實際發生的事情不一致，會誤導使用者

## 嚴重程度
Medium

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:117-118`（決定要不要顯示「已套用」提示的判斷邏輯，只看使用者 Profile 裡的開關）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:133-198`（`buildSeedChromosome`，實際決定每個項目要不要套用 Profile 年齡的邏輯，跟上面第117-118行的判斷邏輯是分開、互不相關的兩段程式碼）
- `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx:89-96`（最佳化結果畫面上顯示提示文字的地方）

## 現狀（已實際讀取現在的原始碼確認）
Fixed-80 要求的其中一項修正是：「Global Optimizer 畫面（或最佳化結果頁）也應該讓使用者看得到『這次最佳化，沒勾選的維度實際套用了哪個假設值』」。

目前的實作方式是：畫面上會顯示一句固定的提示文字，二選一：
- 「已經套用了你自訂的年齡到你沒有勾選最佳化的項目」
- 「因為你的設定關閉了套用，改用系統的法定預設年齡（如 iDeCo 60 歲、年金 65 歲）」

但決定顯示哪一句的判斷邏輯（`GlobalOptimizerServiceImpl.java:117-118`），只是單純檢查「使用者在 Profile 設定裡，有沒有打開『套用我的年齡到未勾選的項目』這個開關」而已，完全沒有去確認「這次最佳化，實際上有沒有任何一個項目真的因為沒勾選而套用了 Profile 的年齡」。

而「每個項目實際上是不是套用了 Profile 年齡」是另外一段完全獨立的邏輯（`buildSeedChromosome()`，第133-198行）：每個項目（退休年齡、年金請領年齡、iDeCo提領年齡、NISA目標）各自要同時滿足「這次沒勾選最佳化」跟「使用者開關是打開的」跟「Profile裡這個欄位真的有填值」三個條件，才會真的套用 Profile 年齡；只要其中一個條件不成立，就會改用系統預設值。這段邏輯跟決定「要不要顯示已套用」提示文字的判斷邏輯，彼此獨立、沒有互相對照。

## 具體會發生的情境
**情境一**：使用者打開最佳化畫面，五個項目（退休年齡、年金請領年齡、iDeCo提領年齡、NISA目標、房貸）全部維持勾選要最佳化的狀態，一個都沒取消勾選，直接按下執行——這其實就是使用者第一次打開這個畫面、什麼都還沒調整過的預設狀態。這種情況下，因為全部都勾選了要最佳化，程式邏輯上根本沒有任何項目會用到「套用 Profile 年齡」這條路徑（那條路徑的前提就是「這個項目沒有勾選要最佳化」）。但只要使用者的 Profile 開關是打開的，畫面還是會顯示「已經套用了你自訂的年齡到你沒有勾選最佳化的項目」——事實上一個項目都沒有套用，這句話跟實際發生的事完全對不上。

**情境二**：使用者確實取消勾選了某一項（例如退休年齡）不參與最佳化，開關也是打開的，但他在 Profile 那一項年齡欄位剛好是空白沒填。這時候系統會退回去用系統內建的預設年齡，不是使用者自己填的年齡。但畫面一樣會顯示「已套用你自訂的年齡」，同樣是誤導使用者。

這兩種情況都可以直接從程式碼邏輯推導出來，不需要猜測，也不是罕見的邊界情況。

## 原因
顯示提示文字的判斷邏輯，跟真正決定「這個項目套用了什麼值」的判斷邏輯，是兩段互相獨立寫的程式碼：一段只看「使用者的開關」，另一段才是真正逐項目判斷「有沒有套用」。這兩段邏輯本來就沒有互相參照對方的結果，所以會出現「開關打開了＝顯示已套用，但實際上這次可能一個項目都沒套用」的落差。這正好跟 Fixed-80 原本想解決的「讓使用者清楚知道這次套用了哪個假設值」的目的互相矛盾——現在顯示的內容，反映的是「使用者的開關設定」，不是「這次真正發生的事」。

## 建議修正
在 `buildSeedChromosome()` 逐項目判斷的過程中（第133-198行），把每個項目「這次是否真的套用了 Profile 年齡」的結果記錄下來（例如記錄四個布林值：退休年齡、年金請領年齡、iDeCo提領年齡、NISA目標，各自是否真的套用了 Profile 值），然後用這個「這次真正發生的結果」來決定最佳化結果畫面要顯示哪一句提示文字，而不是像現在一樣只看使用者的開關設定去推測結果。

修正後應該新增測試，至少涵蓋以下三種情況：
1. 「全部項目都勾選要最佳化」時，畫面不應該顯示「已套用你的自訂年齡」。
2. 「取消勾選某一項，且 Profile 該欄位有填值」時，才應該顯示「已套用」。
3. 「取消勾選某一項，但 Profile 該欄位是空白」時，畫面顯示的訊息不應該說套用了使用者自訂的值。

## 與已知項目的區別
這是 Fixed-80 修正後，針對「畫面要讓使用者看得到套用了哪個假設值」這一項要求，重新驗證時發現尚未正確達成，屬於已知問題（Fixed-80）的延續，不是全新問題。

Fixed-80 原本描述的核心問題（Global Optimizer 種子基因值寫死套用 60 歲、不讓使用者選擇）已確認修正正確——四個維度（退休年齡、年金請領年齡、iDeCo提領年齡、NISA目標）的「有勾選最佳化→優先；沒勾選但允許套用→用Profile值；否則→系統預設值」判斷邏輯（`GlobalOptimizerServiceImpl.java:145-198`）皆已核對正確無誤，Profile 設定畫面新增的勾選框（`hub-ui/hub-ui/src/app/finance/components/profile-basic-info-section.tsx`）與後端欄位串接也已確認正確；唯獨「畫面顯示套用了哪個假設值」這一項要求，實作出來的判斷邏輯本身有缺陷，是本文件要指出的問題。


# Walkthrough: Fixed 80-02 (Optimizer Profile Values)

## Overview
This issue has been resolved by returning the exact values applied from the user's Profile directly to the frontend, instead of a confusing single boolean flag.

## Changes Made

### Backend
1. **[SimulationOverrides.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)**
   - Replaced `Boolean usedProfileBaseline` with a new record: `ProfileBaselineUsageDetails appliedProfileBaselineDetails`.
   - The new record explicitly holds the values applied: `targetRetirementAge`, `pensionStartAge`, `idecoStartAge`, and `nisaMonthlyContribution`.
2. **[GlobalOptimizerServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)**
   - Updated the end of the `optimize()` method to evaluate the seed locks and properly extract which Profile fields were actually used during optimization baseline construction.
   - Passed these actual values into the `SimulationOverrides`.
3. **[GlobalOptimizerServiceTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java)**
   - Updated all old constructor usages.
   - Added 3 new unit tests ensuring that when configurations are skipped, the proper Profile details are accurately mapped and returned.

### Frontend
1. **[use-global-optimizer.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts)**
   - Replaced `usedProfileBaseline` in the `GlobalOptimizerResponse` interface with `appliedProfileBaselineDetails`.
2. **[global-optimizer-card.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)**
   - Now checks if all optimization configs are checked (`allOptimized`); if so, completely hides the info block.
   - If not all are optimized, it dynamically checks if `appliedProfileBaselineDetails` contains any data and prints exactly which dimensions fell back to custom profile values.

## Verification
I have intentionally left the actual unit test execution to you, as requested (`單元測試我跑，改掉`).
You can verify the backend correctness by running:
```bash
cd finance-hub/finance-hub-backend && ./gradlew :hub-app:test --tests "ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.GlobalOptimizerServiceTest"
```
