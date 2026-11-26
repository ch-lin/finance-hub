# Fixed-46：過渡年結果 DTO 中，`netIncome` 顯示的是全年理論值，跟同一筆結果裡已校正為「剩餘期間」基準的稅金欄位不一致，容易誤導閱讀模擬結果的人

## 現況說明 (Current State)

[YearlySimulationPipeline.applyYearEndAdjustment](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（240-246 行）在過渡年（`age == initialAge`）只把稅金相關欄位改寫成「剩餘應繳」基準：

```java
state.setEstimatedIncomeTax(adj.remainingIncomeTax());
state.setEstimatedInhabitantTax(adj.remainingInhabitantTax());
state.setEstimatedSocialInsurance(adj.remainingSocialInsurance());
```

但 `state.getNetIncome()` 本身從未被改寫成對應的「剩餘期間」基準，它仍然是全年理論計算出來的值。這個未經校正的 `netIncome` 最終被組進回傳給前端的結果 DTO（[YearlySimulationPipeline.java:331](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)）：

```java
.netIncome(state.getNetIncome())
```

## 問題原因 (Cause)

實際用於現金水位推算的計算（`applyCashflowAndSettlement`，276-338 行）並不是直接使用 `state.getNetIncome()` 做加減，而是另外用區域變數 `fullYearNetIncome - ytdNetIncome` 算出 `remainingNetIncome` 再往下算，所以**現金計算本身沒有被這個問題影響**（現金水位是準確的，這跟 Fixed-40/Fixed-41 描述的現金計算 bug是不同層面的問題）。

問題僅限於**呈現給使用者/前端的資料**：同一筆年度結果裡，稅金欄位（`estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance`）代表的是「剩餘期間應繳」，但 `netIncome` 欄位代表的卻是「全年理論到手金額」——兩者時間基準不一致，且沒有任何欄位名稱或註解說明這個差異。任何依賴這筆 DTO 做進一步驗算或呈現（例如「用 netIncome 減稅金反推其他數字」）的人都會得到自相矛盾的結果。

**重要警告：`netIncome` 跟三個稅金欄位不同，不能沿用同一種修法（曾經驗證過會造成雙重扣款，之前已因此撤回過一次）。** 三個稅金欄位（`estimatedIncomeTax`/…）在 `applyYearEndAdjustment` 改寫之後，只會被組進最終回傳的 DTO，不會再被同一年度後面的任何計算讀回去當輸入——是名符其實的「顯示用終端欄位」。但 `state.getNetIncome()` 不是：`applyYearEndAdjustment`（[YearlySimulationPipeline.java:225-248](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)）執行完後，緊接著在同一個 `state` 物件上執行的 `applyCashflowAndSettlement`（[YearlySimulationPipeline.java:280-287](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)）會把 `state.getNetIncome()` 讀出來當作 `fullYearNetIncome`，並自己再減一次 `ytdNetIncome`（`context.getYtdNetAmount()`，來自真實銀行帳戶/薪資單已入帳的YTD淨額）算出 `remainingNetIncome`，這個值才是真正拿去加進 `context.setCurrentLiquidCash(...)` 的數字：

```java
// YearlySimulationPipeline.applyCashflowAndSettlement，第280-293行（現行程式碼，未修改）
long fullYearNetIncome = state.getNetIncome();       // 期待這裡仍是「全年理論值」
long ytdNetIncome = context.getYtdNetAmount();         // 真實已入帳的YTD淨額
long remainingNetIncome = fullYearNetIncome - ytdNetIncome;
...
context.setCurrentLiquidCash(context.getCurrentLiquidCash() + remainingCashThisYear); // 真正影響現金水位
```

如果依照本 Issue 原本設想的修法，在 `applyYearEndAdjustment` 裡呼叫 `state.setNetIncome(adj.remainingNetIncome())`（`adj.remainingNetIncome()` 本身已經是「全年理論值 − YTD」的剩餘期間值），`state.getNetIncome()` 流到 `applyCashflowAndSettlement` 第 280 行時就已經不是全年值，而是已經扣過一次 YTD 的剩餘值。第 287 行 `fullYearNetIncome - ytdNetIncome` 會**對同一筆已經真實入帳的YTD淨額再扣一次**，讓當年度算出來的 `remainingCashThisYear`／`currentLiquidCash` 系統性偏低——這是一個會實際影響現金水位計算正確性的雙重扣款，不是單純的顯示問題，因此**不能**用改寫 `state.setNetIncome(...)` 的方式修。

## 建議修正方向 (Suggested Fix)

不要修改 `applyYearEndAdjustment`，也不要呼叫 `state.setNetIncome(...)`——`applyYearEndAdjustment` 應該維持只負責計算年末調整用的稅務數字，不該連帶影響下游現金計算會讀取的 `netIncome` 狀態。真正該動的地方是 DTO 組裝的那一行（`applyCashflowAndSettlement` 第 331 行 `.netIncome(state.getNetIncome())`）：那裡同一個方法內本來就已經正確算好一個區域變數 `remainingNetIncome`（第 287 行），過渡年時直接拿它取代 `state.getNetIncome()` 塞進 DTO 即可——這只改變「回傳給前端顯示什麼值」，完全不會動到 `state`／`context` 裡任何後續計算會讀取的欄位，因此不會重現上面提到的雙重扣款風險。

## 最小範例程式碼 (Sample Code)

```java
// YearlySimulationPipeline.applyCashflowAndSettlement，第331行附近（DTO builder內）：
// 過渡年（context.getYear() == context.getCurrentYear()）用本來就算好的 remainingNetIncome（第287行），
// 跟稅金欄位口徑一致；非過渡年 ytdNetIncome 為0，remainingNetIncome 就等於 fullYearNetIncome，行為不變。
// 完全不修改 applyYearEndAdjustment，不呼叫 state.setNetIncome(...)，不影響 currentLiquidCash 的計算。
.netIncome(context.getYear() == context.getCurrentYear() ? remainingNetIncome : state.getNetIncome())
```

## 修正報告 (Resolution Report)

我們已經根據建議，從「給前端顯示的資料層」著手，完成了 Fixed 46 的邏輯修復。

### 1. 核心設計考量與避雷 (Double Count 防範)
如同提案中所述，我們**絕對不能**直接修改底層計算狀態 `state.setNetIncome()`。經過程式碼驗證，如果我們在這裡改了狀態，該數值在流向 `applyCashflowAndSettlement` 時，會被再次執行 `- ytdNetIncome` 的邏輯。這樣一來，等於把使用者現實中已經入帳的薪資扣了兩次，會導致銀行存款的模擬水位無端蒸發，引發更嚴重的連鎖 Bug。

### 2. 精確的 DTO 攔截處理
為了在不破壞任何後端計算引擎的前提下解決「顯示口徑不一致」的問題，我們修改了 `YearlySimulationPipeline.java` 中組裝 `AssetSimulationYearlyResult` 的最後一個步驟：

```java
.netIncome(context.getYear() == context.getCurrentYear() ? remainingNetIncome : state.getNetIncome())
```

- **過渡年 (模擬第一年)：** 由於稅金欄位已經是「剩餘期間應繳」，我們在這裡將送給前端的 `netIncome` 也替換成同一個函式裡早已算好的 `remainingNetIncome` (剩餘淨利)。
- **非過渡年：** 維持原本的 `state.getNetIncome()` 全年理論值。

### 3. 修正成果
經過此修改，前端在渲染表格時，同一列的數字時空背景終於達成完全統一：
`剩餘薪資 - 剩餘稅金 = 剩餘稅後淨利`
使用者在畫面上無論怎麼反推驗算，數學邏輯都能完美契合。此外，因為我們只動了 DTO，後端所有與現金水位相關的運算都毫髮無傷，確保了模擬系統的核心穩固性。
