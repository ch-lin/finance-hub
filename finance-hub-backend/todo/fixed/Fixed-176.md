# [FIXED] 176：Rakuten訂單解析器在無法從頁面擷取出訂單編號時，用「目前時間毫秒數」產生一個幾乎不可能重複的fallback編號，導致同一張訂單如果曾經解析失敗過，之後重新匯入永遠無法被系統識別成「同一張」，只會不斷疊加出重複的訂單記錄

## 這份issue怎麼被發現的
這是這次Source Code Review審查購物三方對帳服務層時發現的。

## 相關檔案
- [RakutenOrderParserImpl.java:129-133](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenOrderParserImpl.java)（`orderId = extractUsingPattern(allText, ORDER_ID_PATTERN); if (orderId == null) { ...; orderId = "UNKNOWN-" + System.currentTimeMillis(); }`）
- [RakutenOrder.java:52-54](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/rakuten/RakutenOrder.java)（`@UniqueConstraint(columnNames = BaseOrder.ORDER_ID_COLUMN)`，`orderId`是資料庫唯一性約束，也是`findByOrderId`判斷「是否為既有訂單」的唯一依據）
- [RakutenJobServiceImpl.java:102-112](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenJobServiceImpl.java)（`rakutenOrderRepository.findByOrderId(parsedOrder.getOrderId())`，判斷既有訂單走`updateFrom`、否則`save`新訂單——這條判斷完全依賴`orderId`這個字串本身能不能穩定重現）
- [RakutenOrderParserImplTest.java:91-114](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenOrderParserImplTest.java)（`testParse_FallbackLogic`：測試本身就刻意驗證`order.getOrderId().startsWith("UNKNOWN-")`這個fallback行為，確認這條分支是故意保留的功能，不是意外遺留的程式碼）

## 現狀
`RakutenOrderParserImpl.parse()`用正規表示式`ORDER_ID_PATTERN`去抓HTML頁面文字裡「注文番号」後面接的數字跟連字號。如果這個正規表示式在整份頁面文字裡找不到符合的內容（例如Rakuten改版了頁面格式、或是某種訂單類型頁面上根本沒有顯示這個標籤文字），解析器不會丟出例外中止匯入，而是用`"UNKNOWN-" + System.currentTimeMillis()`產生一個備用訂單編號，讓解析流程繼續跑完。

## 為何是問題
`orderId`在這個系統裡扮演的角色不只是「一個識別碼」，它是資料庫層級的唯一性約束，也是`findByOrderId`判斷「這次匯入的訂單，之前有沒有存過」的唯一依據——`AmazonOrder`/`SofmapOrder`走的是同一套「依`orderId`找既有訂單、找到了就`updateFrom`合併、找不到才新增」的模式。`System.currentTimeMillis()`這種以毫秒為單位的時間戳記，兩次呼叫幾乎不可能產生相同的值（除非剛好跑在完全相同的那一毫秒內），這代表只要同一張訂單的頁面曾經因為格式問題無法解析出真正的訂單編號，不管這張訂單被重新匯入多少次，`findByOrderId`永遠找不到「上一次用fallback編號存下來的那一筆」，每一次都會被當成全新訂單存入資料庫——同一張真實訂單最終會在資料庫裡累積出多筆各自獨立、各自帶著完整商品明細跟金額的重複記錄。

這直接牴觸雙重計算這個審查重點：這些重複訂單的`totalAmount`/`billedAmount`會在`RakutenReportStrategy`的報表加總、`ExpenseItem`統一視圖的支出清單裡被各自獨立列出、各自計入總額，造成同一筆真實消費被系統重複計算多次。

## 具體錯誤情境
使用者在10月5日重新下載、匯入一張Rakuten訂單頁面，恰好這張訂單屬於Rakuten某種特殊頁面格式（例如數位商品訂單、或促銷活動期間的客製化頁面），導致`ORDER_ID_PATTERN`抓不到訂單編號，解析器產生了`UNKNOWN-1759650000000`這個fallback編號並存入資料庫，裡面包含這張訂單的3個商品、總金額¥15,000。使用者後來發現這筆訂單的分類或某個欄位有誤，想要修正，於是重新下載同一張訂單頁面再次匯入——這次解析依然因為同樣的頁面格式問題抓不到訂單編號，產生了另一個全新的`UNKNOWN-1759736400000`（時間不同，數值必然不同），`findByOrderId`找不到任何既有訂單可以合併，系統因此又新增了一筆同樣3個商品、同樣¥15,000的訂單——資料庫裡現在有兩筆內容幾乎相同、但各自獨立的訂單記錄，報表跑報表時會把這兩筆都算進去，總支出金額被重複計算了一次（多算了¥15,000）。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：訂單編號解析失敗時，直接判定為解析失敗，中止這次匯入並標記需要人工複核（跟Amazon/Sofmap解析器目前「缺少訂單編號就丟例外」的作法一致）**
- 做法：拿掉`UNKNOWN-`fallback，改成跟`AmazonOrderParserImpl.java:94-98`、`SofmapOrderParserImpl.java:84-88`一樣的作法：缺少訂單編號就丟出解析例外，讓這個檔案被移到`archive/failed`資料夾，等待人工確認頁面格式問題。
- 優點：避免產生無法去重的孤兒訂單記錄，統一三個平台解析器對「缺少訂單編號」這個情境的處理方式。
- 缺點：如果某些真實頁面格式本來就不包含訂單編號文字（這是現有測試明確驗證過的既有行為，暗示這條fallback分支當初是為了應付某種真實情境而刻意保留的設計），改成直接失敗會讓這些頁面完全無法匯入，需要先確認目前`UNKNOWN-`fallback實際涵蓋的是哪一種情境。

**方向B：fallback編號改成從頁面裡其他穩定、能重複識別出同一張訂單的內容去推導（例如訂單日期+金額+商品名稱的組合雜湊），而不是用當下時間**
- 優點：至少讓同一張訂單重複匯入時，有機會算出相同的fallback編號，恢復去重能力。
- 缺點：這類推導出來的編號本身仍然不是訂單真正的唯一識別碼，如果恰好有兩張不同訂單日期/金額/商品都相同（例如同一天買了兩次同樣的東西），仍然會誤判成同一張訂單而互相覆蓋，這本身又製造了另一種錯誤。

---

## 修正結果（採用方向 A：對齊 Amazon / Sofmap 標準做法）

本案已確認並採用 **方向 A** 進行修正。

### 1. 核心修正要點
1. **建立專屬解析異常類別**：
   - 建立 [RakutenOrderParseException.java](file://finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/exception/RakutenOrderParseException.java)，架構上與 `AmazonOrderParseException` 及 `SofmapOrderParseException` 保持一致。
2. **移除 `UNKNOWN-` 時間戳 Fallback**：
   - 修改 [RakutenOrderParserImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenOrderParserImpl.java)：當頁面無法解析出有效 `orderId` 時，拋出 `RakutenOrderParseException("Missing mandatory field: Order ID")`。
3. **對齊批次作業異常歸檔流程**：
   - 在 [RakutenJobServiceImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenJobServiceImpl.java) 中，拋出的解析異常會被外層捕獲，記錄為 `Hard fail parsing file` 並將檔案移入 `archive/failed` 目錄，避免將無法去重的孤兒訂單寫入資料庫，徹底杜絕重複計算支出。
4. **單元測試修正與補充**：
   - 在 [RakutenOrderParserImplTest.java](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/rakuten/RakutenOrderParserImplTest.java) 中：
     - 將既有測試 `testParse_FallbackLogic` 補上合法 `orderId`，聚焦驗證日期、金額與支付方式的 fallback 邏輯。
     - 新增單元測試 `testParse_MissingOrderId_ThrowsException`，驗證缺少訂單編號時確實拋出 `RakutenOrderParseException`。
