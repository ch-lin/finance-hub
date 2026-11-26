# Fixed 99：退休金試算圖表文字寫死65歲，沒跟著使用者調整的年齡更新

## 嚴重程度
Low-Medium（這不是計算錯誤——底層的退休金試算金額本身是對的，日本公共年金提早/延後請領的加減乘數確實有正確套用；問題只在於畫面上的說明文字沒有跟著更新，會讓使用者誤讀圖表數字的意義，可能導致使用者對自己的退休金額產生錯誤預期，但不會讓存進資料庫的任何金額出錯，也不影響核心的房貸現金流模擬）

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx:38-40`（顯示退休金試算圖表的元件，`CardDescription` 這段說明文字是寫死的固定字串）
- `hub-ui/hub-ui/src/app/finance/portfolios/pensions/page.tsx:71-86`（Pension Records 頁面，使用者可以用滑桿把「Pension Start Age」從 60~75 歲之間任意調整，並把調整後的值往下傳給圖表元件）
- `hub-ui/hub-ui/src/app/finance/portfolios/pensions/hooks/use-pension-projection.ts:17-24`（把使用者調整的請領年齡組進 API 請求送給後端，證實這個設定值真的會影響後端運算，不是單純裝飾用的 UI）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java:92-94,127-135`（後端把使用者設定的請領年齡代入計算公式，正確算出提早/延後請領應有的增減金額）

## 現狀
在「Pension Records」頁面，使用者可以用一個滑桿把「Pension Start Age（開始領退休金的年齡）」從預設的 65 歲調整成 60 到 75 歲之間的任何歲數。調整之後，下方的退休金試算圖表確實會依照新設定的年齡重新算出不同的金額（日本的公共年金制度本來就會依提早或延後請領而增減金額，這部分計算是對的）。

但圖表卡片上方的說明文字永遠固定寫著「Estimated annual pension payout at age 65 based on additional years worked」（以 65 歲請領為基礎試算年度退休金），完全不會隨著使用者調整的年齡更新。如果使用者只看這行說明文字，會誤以為圖表上的金額都是「65 歲請領」的估算，但實際上圖表數字反映的是使用者自己選的年齡（例如選 70 歲延後請領，金額會比較高），文字說明與實際顯示的數字對不上，可能讓使用者對自己的退休規劃產生誤判。

## 原因
`pension-projection-chart.tsx:38-40` 的 `CardDescription` 是寫死的固定字串（內含「...at age 65...」），完全沒有把外部傳入的 `pensionStartAgeOverride`（第 21、26 行接收到的 prop）插入到文字內容裡。

這個設定值本身沒有問題：`page.tsx` 的滑桿會把使用者選的年齡存進 `pensionStartAgeOverride` 這個 state（第 76-80 行），往下傳給圖表元件（第 86 行）；圖表元件再透過 `usePensionProjection` 這個 hook 把這個值組進 API 請求（`use-pension-projection.ts:23-24`）送給後端；後端 `PensionProjectionServiceImpl.java:92-94` 會用 `SimulationParameterResolver.resolvePensionStartAge(...)` 解析出實際生效的請領年齡，並在第 127-128 行、第 134-135 行把這個年齡代入 `calculateProjectedBasicPension` 和 `calculatePensionMultiplier` 兩個計算方法，正確套用日本厚生年金提早/延後請領的乘數，算出增減後的金額。

也就是說：底層數字算對了，只是畫面上解釋這個數字的文字沒有跟著這個已經生效的設定值一起更新。

## 具體錯誤情境
使用者在「Pension Records」頁面把「Pension Start Age」滑桿從預設的 65 歲拉到 70 歲（延後請領）。下方圖表的金額會正確地變大（因為延後請領有加成），但圖表卡片上方仍然顯示「Estimated annual pension payout at age 65 based on additional years worked」。使用者若只看這行文字、不去注意上方滑桿目前停在哪個刻度，會誤以為圖表顯示的是「65 歲請領」時能拿到的金額，進而低估自己延後請領後實際能拿到的年金，或是在跟其他年齡的試算結果比較時搞混基準點。

## 建議修正
把說明文字改成動態顯示實際生效的請領年齡，而不要寫死「65」。這裡有兩種情境需要分別處理，屬於單純的文字顯示調整，風險很低：

- 當使用者有調整滑桿（`pensionStartAgeOverride` 有值）時：文字改成顯示使用者目前選的年齡（例如「以 70 歲開始請領為基礎試算」）。
- 當使用者沒有調整滑桿（`pensionStartAgeOverride` 是 `undefined`，畫面上顯示「Profile Settings」字樣）時，圖表實際上是用個人資料裡的預設請領年齡去試算的（見 `PensionProjectionServiceImpl.java:92-94` 會 fallback 到 `userProfile.getPensionStartAge()`）。這種情況下說明文字可以顯示成「以個人資料設定的請領年齡試算」，而不要顯示成某個猜測出來的具體年齡（因為前端目前並沒有把 `userProfile` 實際解析出來的年齡值傳回頁面，若要顯示具體數字，需要額外一次 API 呼叫或後端把解析後的年齡回傳給前端，這牽涉是否要多做一次資料往返的取捨，留給實作者依現有架構決定）。

不論哪種情境，核心做法都是讓文字內容跟隨畫面上滑桿目前的設定值（或其 fallback 狀態）動態顯示，避免文字與圖表數字不一致。

## 與已知項目的區別
`todo/planned` 與 `todo/wontfix` 目錄下的既有項目（`Plan-45-individual-pension-insurance-premium-not-deducted.md`、`WONTFIX.md`、`PLANNED.md`、各 Epic 文件）都沒有任何一項提到退休金試算圖表（Pension Projection Chart）的說明文字問題，也沒有提到 `pension-projection-chart.tsx` 這個檔案。這是全新發現的問題，且不屬於前端輸入驗證類的問題——這是前端自己顯示的文字說明與其自己顯示的圖表數字不一致，圖表本身的計算邏輯（連同後端請領年齡的加減乘數）都是正確的。

# Walkthrough: Fixed-99 Fix

## 變更摘要
本次修改修復了退休金試算圖表 (Pension Projection Chart) 說明文字與實際滑桿數值脫節的問題，並微調了滑桿數值的顯示方式。

## 詳細修改內容

### 1. 移除寫死的 65 歲文字
在 `pension-projection-chart.tsx` 中，我們將原本的文字：
`Estimated annual pension payout at age 65 based on additional years worked`
修改為更客觀、不會造成誤導的描述：
`Estimated annual pension payout based on additional years worked`

這解決了最核心的 UX 混淆問題：無論使用者將滑桿拉到幾歲，圖表的標題都不會再出現錯誤的 `65`。

### 2. 優化滑桿上方的數值顯示
在 `page.tsx` 中，我們針對 Pension Start Age 滑桿右上方的 Label 顯示邏輯做了微調：
原本拉動滑桿時只會顯示純數字（例如 `70`），現在修改為會加上後綴（例如 `70 years old`）。
當尚未調整滑桿時，依然會顯示預設的 `Profile Settings`。這樣讓整體顯示更為親切、容易理解。

## 驗證
所有的程式碼都已完成修改並儲存，後續可透過啟動前端專案來確認畫面上的顯示效果。
