# [FIXED] 175：Rakuten訂單重新匯入時，taxAmount（消費稅金額）這個平台事實欄位沒有被updateFrom更新，跟同一次重新匯入裡其他金額欄位（total/billed/shippingFee/discounts/points）的覆寫範圍不一致

## 這份issue怎麼被發現的
這是這次Source Code Review審查購物三方對帳服務層（Amazon/Rakuten/Sofmap）時發現的。

## 相關檔案
- [RakutenOrder.java:175-188](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/rakuten/RakutenOrder.java)（`updateFrom`方法，第178-182行覆寫`totalAmount`/`billedAmount`/`shippingFee`/`refundAmount`/`needsReview`/`reviewReason`，第186-188行覆寫`pointsUsed`/`discounts`/`shopId`，全程沒有一行處理`taxAmount`）
- 同檔案`:79-82`（`taxAmount`欄位定義，`@Column(name = "tax_amount", precision = 10, scale = 2)`）
- [RakutenOrderParserImpl.java:196-202](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenOrderParserImpl.java)（`parseOrderSummary`每次重新解析HTML時，都會讀取頁面上的消費稅金額，設到剛解析出來的`parsedOrder.taxAmount`上）
- 同檔案`:395-421`（`verifyOrderMath`：用這次新解析出來的`taxAmount`去驗算`Total + Tax + Shipping - Discounts - Points = BilledAmount`這個公式，判斷要不要標記`needsReview`）

## 現狀
`RakutenOrderParserImpl`每次解析HTML頁面，都會重新讀出頁面上顯示的消費稅金額，設定到這次新建立的`RakutenOrder`物件（也就是`parsedOrder`）身上，而且`verifyOrderMath`這個數學驗算，也是拿這個新鮮解析出來的`taxAmount`去驗證金額有沒有兜起來。但當這張訂單已經存在資料庫裡、觸發`updateFrom(parsedOrder)`這條合併路徑時，`updateFrom`方法本身完全沒有一行程式碼去把`parsedOrder.getTaxAmount()`寫回既有訂單——`total`、`billed`、`shippingFee`、`discounts`、`points`這些同樣是「平台對帳單上客觀記載的事實數字」的欄位都有被覆寫，只有`taxAmount`被漏掉。

## 為何是問題
`taxAmount`跟`totalAmount`/`billedAmount`等欄位性質完全相同，都是Rakuten對帳單頁面上客觀記載的數字，照reimport/merge的判斷原則，應該在每次重新匯入時被平台最新事實直接覆寫，不應該留著舊值。目前的實作造成一個矛盾：這次重新匯入時，`verifyOrderMath`拿新鮮的`taxAmount`去驗算金額，判斷這次解析結果需不需要標記`needsReview`——這個判斷本身用的是正確的新值；但驗算完之後，資料庫裡實際存起來、往後被`RakutenOrderResult.getTaxAmount()`回傳給前端或任何後續讀取的那個`taxAmount`，卻仍然是上一次匯入時留下的舊值。這代表同一張訂單，資料庫裡被持久化的`taxAmount`跟訂單本身其他金額欄位會逐漸不同步，形同這張訂單的消費稅金額被凍結在第一次匯入當下，之後不管Rakuten那邊的對帳單內容有沒有更新或修正，都不會再被更新。

## 具體錯誤情境
使用者在某次匯入時，Rakuten對帳單頁面消費稅顯示為¥500；後來因為訂單內容有異動（例如賣家調整了折扣或退款，Rakuten重新產生了對帳單頁面），使用者重新匯入同一張訂單，這次頁面上的消費稅變成¥450（例如因為部分商品被取消，稅額跟著減少）。重新匯入後，`totalAmount`/`billedAmount`/`discounts`等欄位全部正確更新成最新數字，但資料庫裡`taxAmount`欄位仍然停留在¥500這個舊值——任何讀取這張訂單`taxAmount`欄位的畫面或報表，看到的都是跟其他欄位不一致、過期的消費稅金額。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：在`updateFrom`裡補上一行`this.setTaxAmount(parsedOrder.getTaxAmount());`，跟其他平台事實欄位用同一套邏輯處理**
- 優點：改動最小，一行解決，跟其他金額欄位的處理方式完全一致。
- 缺點：無，這本質上是補齊一個明顯被遺漏的欄位。

**方向B：順便檢查`updateFrom`裡其他尚未覆寫的欄位是否也有類似遺漏（這次審查已確認`shopId`有被覆寫，所以範圍只限`taxAmount`這一個欄位）**
- 優點：一次性徹底排查，避免之後又漏掉其他欄位。
- 缺點：需要額外花時間逐一核對`RakutenOrder`的每個欄位，但範圍已經很小。

---

## 結案說明（已於 Issue 180-03 重構中自然修復）

本問題已在 [issue-180-03](issue-180-03.md) 針對購物三平台（Amazon/Rakuten/Sofmap）的合併架構重構中自然解決，無需另外調整程式碼。

### 查證依據
1. **`updateFrom` 重構為 `merge` 並補齊 `taxAmount` 覆寫**：
   - 舊有的 `updateFrom` 方法已在重構時移除，全面改用 `merge(RakutenOrder incoming)`。
   - 在 [RakutenOrder.java:184](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/rakuten/RakutenOrder.java#L184) 中，已明確包含：
     ```java
     this.shopId = incoming.getShopId();
     this.taxAmount = incoming.getTaxAmount();
     ```
2. **匯入進入點皆已對齊**：
   - [RakutenJobServiceImpl.java:106](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenJobServiceImpl.java#L106) 與 [RakutenServiceImpl.java:99](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenServiceImpl.java#L99) 在重新匯入時皆呼叫 `existingOrder.merge(parsedOrder)`，確保最新解析的 `taxAmount` 正確覆蓋既有值。
3. **單元測試驗證**：
   - [RakutenOrderTest.java:88-107](file://finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/rakuten/RakutenOrderTest.java#L88-L107) 的 `testMerge_OverwritesFactsPreservesReconciledAmountAndUserFields` 測試中，已包含 `assertEquals(new BigDecimal("120"), original.getTaxAmount(), "Tax amount must be overwritten");` 之驗證，確認重新匯入時 `taxAmount` 會被正確覆寫。
