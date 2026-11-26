# 修正 Fixed 74-01：房貸獎金月付款彙總視窗漏算當月

## 問題簡述與邊際效應分析
這個修正的確只要移動兩行程式碼（將遞增月份的邏輯移到迴圈最後）就能完美解決。

**有沒有邊際效應？**
**沒有負面邊際效應。** 
這兩個方法 (`countRemainingBonusPaymentsAmount` 與 `presentValueOfBonusStream`) 都是 `private static`，只被同類別的 `calculateFixedPaymentOrPrincipal` 呼叫。而 `calculateFixedPaymentOrPrincipal` 在 `MortgageCalculator` 中被呼叫時，傳入的 `currentMonthValue` 確實對應到「當期（k=1）」的年金計算。將遞增移到迴圈尾部，完美讓 `k=1` 對應到 `currentMonthValue`，`k=remainingMonths` 對應到正確的最後一個月，修正了原本視窗整體位移一個月的 Bug，並讓折現期數回歸正確的年金公式定義。

## User Review Required
請確認以下計畫清單。一旦您核准（Approve），我將嚴格依照清單使用 IDE API 修改檔案，絕不觸碰清單外的任何程式碼。

## Proposed Changes

### MortgageCalculatorUtil

- 修正 `countRemainingBonusPaymentsAmount` 的迴圈內邏輯順序。
- 修正 `presentValueOfBonusStream` 的迴圈內邏輯順序。

#### [MODIFY] [MortgageCalculatorUtil.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java)

## Verification Plan

### Automated Tests
- 將執行相關的 Unit Tests 以確認計算邏輯在修改後仍符合預期。
```bash
./gradlew :hub-app:test --tests "*Mortgage*"
```

### Manual Verification
- 請在修改完成後，透過 IDE Diff 介面審查修改內容是否正確。
