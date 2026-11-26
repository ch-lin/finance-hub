# Fixed 76：DC 基金快照合併邏輯從未持久化已計算出的 expectedReturnRate，每次重新匯入都會被丟棄

## 嚴重程度
Medium

## 相關檔案
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/domain/accounting/dc/DefinedContributionAccountSnapshot.java:100-164`
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/domain/accounting/dc/DefinedContributionFundSnapshot.java`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionJobServiceImpl.java:100-237`

## 現狀
`DefinedContributionJobServiceImpl` 會先呼叫 `applyExpectedReturnRates(snapshot)`（查詢並設定每個基金的 `expectedReturnRate`），接著對既有紀錄呼叫 `existing.merge(snapshot)`。

`DefinedContributionAccountSnapshot.merge` 在建立新基金項目的分支中，呼叫 `DefinedContributionFundSnapshot` 的建構子時完全沒有帶入 `expectedReturnRate` 參數（建構子內部將其設為 null）；而 `DefinedContributionFundSnapshot.merge(other)` 這個方法本身，也完全沒有在任何地方引用 `expectedReturnRate` 欄位。

## 原因
這個模式在兩個呼叫點都重複發生（約第 119/127 行與第 169/177 行）：`applyExpectedReturnRates` 是套用在暫時性的 `snapshot` 物件上，但緊接著的 `existing.merge(snapshot)` 卻沒有任何程式碼路徑把這個剛計算出來的值複製到持久化的基金實體上——導致每次針對既有紀錄的重新匯入之後，持久化的 DC 基金快照的 `expectedReturnRate` 永遠是 null 或過期值，即便剛剛執行過的計算邏輯本應正確填入這個值。

## 具體錯誤情境
使用者的 iDeCo 帳戶每月自動匯入一次最新快照。系統每次都正確計算出各基金的 `expectedReturnRate`，但因為合併邏輯漏掉這個欄位，持久化後的紀錄中該欄位始終是 null——任何後續讀取這筆持久化紀錄、依賴 `expectedReturnRate` 做投影計算的功能，都會拿到空值而非正確的預期報酬率。

## 建議修正
在 `DefinedContributionFundSnapshot.merge(other)` 中補上 `expectedReturnRate` 的複製邏輯（當 `other.getExpectedReturnRate()` 非 null 時複製），並在 `DefinedContributionAccountSnapshot.merge` 的新基金建立分支中，也把該值一併傳入建構子。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 均未提及 DC 基金快照合併遺漏 `expectedReturnRate` 的問題，屬於全新發現的問題。

---

## 修正報告 (Fix Implementation)

這個修正旨在解決 DC 與 iDeCo 帳戶快照重新匯入或更新時，系統沒有將先前繼承自歷史紀錄的 `expectedReturnRate` 持久化，導致該值永遠被覆寫為 null 的 Bug。修復後，使用者自訂的基金報酬率將能正確延續。

### 實際修改內容

#### 1. `DefinedContributionFundSnapshot.java`
在 `merge(DefinedContributionFundSnapshot other)` 方法中新增邏輯：
```java
if (other.getExpectedReturnRate() != null) {
    this.expectedReturnRate = other.getExpectedReturnRate();
}
```
**目的**：確保既有基金在合併新快照時，若有取得上個月的預期報酬率，能正確寫入並保留。

#### 2. `DefinedContributionAccountSnapshot.java`
在 `merge(DefinedContributionAccountSnapshot other)` 方法裡，處理「新增基金」的分支中，於 `newFund` 建立完成後加入：
```java
newFund.updateExpectedReturnRate(otherFund.getExpectedReturnRate());
```
**目的**：確保當同月份再次匯入新基金時，已透過 `applyExpectedReturnRates` 找回的歷史報酬率能被正確設定到新實體中。
