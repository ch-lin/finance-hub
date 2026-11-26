# [Fixed-42] 過渡年 iDeCo/DC 扣除額保守估算之註解與命名優化

## 背景與結論

在第一年（過渡年）的稅金估算中，系統目前僅將「剩餘月份」的 iDeCo 與 DC 提繳額計入該年度的稅務扣除額，並未去推算或回補過去幾個月（YTD）可能發生的提繳額。
這是一個**刻意為之的保守設計**，因為在缺乏逐月真實歷史資料（如確定申告書或歷史銀行扣款明細）的情況下，擅自外推 YTD 的提繳額可能會產生憑空的「免費稅務扣除額」，導致系統對現金流給出過度樂觀（高估）的危險預測。

這個做法在商業邏輯上是完全正確且站得住腳的，我們**不需要翻盤，也不需要修改計算邏輯**。
為了避免未來維護者（或其他 AI）誤以為這是一個「忘記乘以 12」的 bug 而擅自修正，我們將依照 Fixed-42 的建議，進行純粹的「防呆」命名重構與加上詳盡註解。

## Proposed Changes

### `TaxAdvantagedAccountProjector.java`

#### [MODIFY] `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java`
- 將 `processAccountsForYear` 方法中的 `annualIdeco` 重新命名為 `remainingMonthsIdecoContribution`（在過渡年語境下更誠實）。
- 將 `annualDc` 重新命名為 `remainingMonthsDcContribution`。
- 在這兩段迴圈上方加上詳盡的註解，闡述為什麼我們「刻意只算剩餘月份，不回補 YTD，以換取保守的安全誤差」。
- 將回傳給 `InvestmentContributions` 的建構子參數同步更新為這兩個新變數名稱。

## Verification Plan

### Manual Verification
由於這純粹是變數重新命名與註解新增，編譯通過即代表重構成功，模擬器的任何數值計算皆不會產生變動。
1. 開發者手動執行 `mvn clean install` 確保重構沒有改壞編譯。
