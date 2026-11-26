# 修正 Amazon 育兒支援券折抵漏算問題

將 Amazon 訂單中的「育児支援券」視為「Amazon Gift Card」來處理，以解決 `verifyOrderMath()` 漏算金額導致誤判異常的問題。

## Proposed Changes

### Amazon 解析模組 (Parser)

#### [MODIFY] [AmazonOrderParserImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/amazon/AmazonOrderParserImpl.java)
- 將 `parseOrderSummary` 方法內的解析邏輯進行合併，將 `labelText.contains("育児支援券")` 的條件判斷併入處理 Gift Card 的 `else if` 區塊中。
- 移除原本獨立處理 `育児支援券` 的 `else if` 分支，確保金額會被寫入 `order.setGiftCardAmount()` 中參與公式驗算。

## Verification Plan

### Manual Verification
- 交由您手動執行測試，確保既有的單元測試不會因為這個改動而失敗：
  `mvn test -Dtest=AmazonOrderParserImplTest`

# Amazon 育兒支援券折抵問題修復總結

針對 Fixed 100：Amazon 訂單中的「育児支援券」折抵未被算進金額核對公式，導致訂單被誤判為金額異常的問題，已完成修復。

## Changes Made

### 1. 修改解析邏輯 (AmazonOrderParserImpl.java)
- 在 `parseOrderSummary` 方法中，將針對 `labelText.contains("育児支援券")` 的處理邏輯，併入到處理 `Amazon Gift Card` 的 `else if` 條件判斷式中。
- 移除了原本獨立處理 `育児支援券` 的程式碼區塊。

### 2. 影響範圍與效益
- **自動修正核對公式**：由於併入了 Gift Card 的邏輯，這筆折抵金額現在會正確觸發 `order.setGiftCardAmount()`。因此，現有的 `verifyOrderMath()` 能夠正確扣除此金額，解決了因漏算而導致的假警報 (needsReview = true)。
- **無須動用資料庫**：此改法避免了新增資料庫欄位 (如 `childcareCouponAmount`) 的麻煩與風險。
- **符合實際顯示行為**：根據觀察，Amazon 的網頁與收據本身亦會將此券的金額視作「Amazon商品券」來呈現，因此這個改法在語意上完全吻合現狀。

## Validation
- **[需要手動驗證]** 程式碼修改完成後，您可以透過執行 Maven 測試指令來確保這項更動沒有破壞現有的其他解析邏輯：
  `mvn test -Dtest=AmazonOrderParserImplTest`
- **[手動檢核程式碼]** [AmazonOrderParserImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/amazon/AmazonOrderParserImpl.java#L300-L308) 的邏輯合併已經完成。
