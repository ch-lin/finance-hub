# Fixed 85：AI選房貸策略時看的財富數字沒扣掉房貸餘額，會誤導成「不要還房貸」

## 嚴重程度
High——這直接違背「房貸策略最佳化」這個功能本身應該達到的目的。系統的AI最佳化引擎（無論是遺傳演算法還是窮舉搜尋）在評分排名各種還款策略的優劣時，用的財富數字沒有扣掉房貸餘額，會系統性地偏向「保留現金、不要提前還房貸」的策略，即使依照畫面上使用者實際看到、被標示為「真實淨資產」的那個數字來看，提前還房貸的方案其實可能才是真正讓使用者更富有的選擇。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/AssetSimulationFitnessEvaluator.java`（第90-91行、第107行：遺傳演算法／窮舉搜尋的評分邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`（第315-348行：同時計算「未扣房貸的淨資產」與「扣掉房貸的真實淨資產」這兩個獨立數字的地方）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/domain/simulation/AssetSimulationYearlyResult.java`（第46、59、61行：`projectedNetWorth`（未扣房貸）、`remainingMortgageBalance`（房貸剩餘餘額）、`trueNetWorth`（已扣房貸）這三個獨立欄位）
- `hub-ui/hub-ui/src/app/finance/simulation/asset-simulation-table.tsx`（第71-72行、第96-97行：畫面上把 `trueNetWorth` 標示成粗體、主要顯示的「真實淨資產（True Net Worth）」，`projectedNetWorth` 標示成灰色、次要顯示的「未扣房貸淨資產（Raw Net Worth）」）

## 現狀
系統的模擬結果裡，本來就已經同時算出兩個不同的「淨資產」數字：一個是「未扣房貸淨資產」（現金、NISA、退休帳戶、公司股票等資產加總，沒有減掉房貸還沒還完的餘額），另一個是「真實淨資產」（前者再減掉房貸剩餘餘額）。畫面上這兩個數字並列顯示時，產品本身已經明確決定「真實淨資產」才是主要、應該被視為代表真正財富的數字（用粗體字顯示、標題就叫「True Net Worth」），「未扣房貸淨資產」則是次要、輔助參考的數字（用灰色字顯示、標題是「Raw Net Worth」）。

但AI最佳化功能（不論是遺傳演算法或窮舉搜尋，用來自動比較不同還款策略、找出最佳方案）在評分、排名各種策略優劣時，用的卻是次要的「未扣房貸淨資產」，而不是產品自己認定為主要指標的「真實淨資產」。這會讓最佳化引擎系統性地偏好「保留現金在手上、不要提前還房貸」的策略，因為評分時完全看不到房貸這筆負債。

## 原因
`AssetSimulationFitnessEvaluator.java` 裡，沒有破產的情境分支（第90-91行、第107行）：

```java
AssetSimulationYearlyResult finalYear = simulation.get(simulation.size() - 1);
double finalNetWorth = finalYear.getProjectedNetWorth();   // 第91行
...
return finalNetWorth;                                       // 第107行
```

而 `YearlySimulationPipeline.java`（第315-348行）實際上已經算出了兩個獨立數字：

```java
long currentNetWorth = context.getCurrentLiquidCash() + context.getNisaBalance()
        + currentTotalDcBalance + context.getCompanyStockBalance();   // 對應 projectedNetWorth，沒有扣掉負債
...
long trueNetWorth = currentNetWorth - remainingMortgageBalance;        // 扣掉房貸剩餘餘額
```

也就是說，計算引擎本身完全有能力提供「扣掉房貸的真實淨資產」，但評分邏輯選用的是另一個沒有扣掉房貸的數字，跟畫面上已經確立的「以真實淨資產為主」的產品決定不一致。

需要特別說明：這跟已經修復的「遺傳演算法破產情境評分扭曲」的bug是不同的問題——那個bug是關於第92-105行、在「破產」情境下的 `deficit`／`BANKRUPTCY_DEATH_PENALTY_BASE` 罰分邏輯；本問題是第107行、在「正常未破產」情境下選錯了淨資產欄位，兩者是完全不重疊的程式路徑。

## 具體錯誤情境
方案A：提前還一大筆房貸（現金變少，房貸餘額從2000萬元降到500萬元）。方案B：完全不提前還款（現金比較多，房貸餘額維持在2000萬元不變）。如果方案B的「現金+NISA+退休帳戶+公司股票」加總金額，剛好比方案A的加總金額更高，評分邏輯（用 `projectedNetWorth`）就會判定方案B比較好，向使用者建議「不要提前還房貸」——即使依照畫面上同時顯示、且被標示為主要指標的「真實淨資產」來看，方案A可能其實才是真正讓使用者更富有的選擇。AI給出的建議，跟畫面上自己顯示的「真實財富」排名結果直接矛盾。

## 建議修正
以下兩個方向都是純技術實作層面的選擇，不涉及產品／UX決策：

**方向A：把 `evaluate()` 裡的 `finalYear.getProjectedNetWorth()` 直接改成 `finalYear.getTrueNetWorth()`**
- 優點：跟畫面上已經確立的主要指標一致，改動最小、風險最低，能讓「房貸策略最佳化」這個開關真正發揮設計上應有的作用。
- 缺點：對相同的輸入條件，之後算出來的建議結果可能會跟目前有明顯差異（甚至完全反過來），需要讓使用者知道這個改變；另外也應該確認目前 `trueNetWorth` 扣掉的負債範圍（目前只扣房貸）是否已經涵蓋所有應該考慮的負債，未來如果有其他類型的負債，可能需要再擴充。

**方向B：不直接依賴既有的 `trueNetWorth` 欄位，而是在 `evaluate()` 裡自己獨立重新計算「`finalYear.getProjectedNetWorth() - finalYear.getRemainingMortgageBalance()`」**
- 優點：讓最佳化引擎自己決定要扣掉哪些負債，跟 `trueNetWorth` 這個欄位未來的定義變化脫鉤。
- 缺點：等於把同一套「淨資產減負債」的邏輯在兩個地方各寫一份，如果未來有人更新了 `trueNetWorth` 的定義（例如納入更多種負債類型）卻忘了同步更新這裡，會產生跟目前這個bug同一類的維護風險，長期來看比方向A更容易再次出問題。

## 與已知項目的區別
已檢查 `todo/planned/` 與 `todo/wontfix/`（iDeCo/DC法定上限、個人年金保費、NISA相關項目、Grid Search多筆房貸限制等），沒有任何一項提到「評分函式用錯淨資產欄位，導致系統性偏向不還房貸」的問題，屬於全新發現的問題。

## 實作報告 (Implementation Report)
- **Status**: Resolved
- **Action**: Adopted **Direction A**. Updated `AssetSimulationFitnessEvaluator.java` to use `getTrueNetWorth()` instead of `getProjectedNetWorth()`.
- **Reasoning**: Ensures that the AI optimization engine accurately accounts for remaining mortgage debt at the end of the simulation, especially in scenarios where the simulation (`targetAge`) ends before the mortgage is fully paid off. This aligns the AI's recommendations with the "True Net Worth" metric shown on the UI.
