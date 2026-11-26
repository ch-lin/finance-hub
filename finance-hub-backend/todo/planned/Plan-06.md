# Plan 06：信用卡交易與 Amazon 訂單完成對帳後，從未更新訂單的已核銷金額

## 嚴重程度
High

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java`
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/domain/accounting/shopping/BaseOrder.java:195-260`
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/domain/accounting/shopping/amazon/AmazonOrder.java`

## 現狀
`linkTransaction(creditCardTransactionId, amazonOrderId)` 只用 `transactionLinkRepository.findByCreditCardTransactionId(...).isEmpty()` 做重複檢查，接著建立並儲存一筆 `TransactionLink`，但從未呼叫 `order.setReconciledAmount(...)`。

`BaseOrder` 定義了：

```java
@Setter private BigDecimal reconciledAmount = BigDecimal.ZERO;
public BigDecimal getUnreconciledBalance() { return billedAmount.subtract(reconciledAmount); }
```

`getUnreconciledBalance()` 是完全依賴 `reconciledAmount` 推導出來的欄位。`AmazonOrder.updateFrom` 的程式碼註解甚至明確寫著：「It intentionally does NOT overwrite the reconciledAmount to preserve accounting state」，印證 `reconciledAmount` 本應由對帳流程本身維護，而不是由重新匯入的流程維護。

## 原因
由於整個對帳連結流程從未真正設定過 `reconciledAmount`，`getUnreconciledBalance()` 永遠無法反映已經被連結/核銷的交易——無論實際上已經連結了多少筆信用卡交易，每一筆 Amazon 訂單永遠顯示為「完全未核銷」。另外，目前唯一的重複檢查只防止同一筆信用卡交易被重複連結，卻沒有防止多筆交易被連結到同一張訂單上、累積超過該訂單 `billedAmount` 的情況。

## 具體錯誤情境
使用者將一筆 ¥15,000 的信用卡交易連結到一張 Amazon 訂單進行對帳。連結完成後查看該訂單，`getUnreconciledBalance()` 仍然顯示全額未核銷（因為 `reconciledAmount` 從未被更新），對帳儀表板無法正確反映使用者實際已完成的對帳進度，且系統理論上允許同一張訂單被多筆交易過度連結而無任何金額上限檢查。

## 建議修正
在 `linkTransaction` 儲存 `TransactionLink` 之後，補上 `order.setReconciledAmount(order.getReconciledAmount().add(transaction.getAmount()))`（或相應的已連結金額），並在訂單端加上防止過度核銷（累積連結金額超過 `billedAmount`）的檢查。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 均未提及對帳流程未更新已核銷金額的問題，屬於全新發現的問題。
