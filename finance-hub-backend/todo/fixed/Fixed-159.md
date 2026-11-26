# [FIXED] Issue 159：Sofmap訂單的Repository宣告的ID型別跟實體真正的主鍵型別不一致，導致依訂單編號查詢/更新永遠查不到既有訂單

## 這份issue怎麼被發現的
這是這次Source Code Review針對Sofmap購物業務邏輯進行審查時發現的問題，並且已經用一個獨立的Hibernate+H2最小重現測試實際驗證過行為，不只是從程式碼推論。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/SofmapOrderRepository.java:38`：`public interface SofmapOrderRepository extends JpaRepository<SofmapOrder, String>`
- `SofmapOrder`繼承鏈：`SofmapOrder` → `BaseOrder` → `MultiUserBaseEntity` → `ch.lin.platform.domain.model.BaseEntity`，真正的`@Id`欄位是`BaseEntity`裡的`private Long id`（`GenerationType.IDENTITY`自動產生），`SofmapOrder`本身沒有覆寫`@Id`。
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImpl.java:59`（`saveOrder`呼叫`sofmapOrderRepository.findById(parsedOrder.getOrderId())`，`getOrderId()`回傳的是像`"046174418"`這種業務用的訂單編號字串，不是真正的數字主鍵）、`:80`（`getOrder`同樣用法）
- 對照：`AmazonOrderRepository`/`RakutenOrderRepository`正確宣告成`JpaRepository<AmazonOrder, Long>`/`<RakutenOrder, Long>`，而且對應的Service都是呼叫專門的`findByOrderId(String)`衍生查詢方法，從來不呼叫`findById`——`SofmapOrderRepository`完全沒有對應的`findByOrderId`方法

## 現狀（已實際重現驗證）
`SofmapOrderRepository`的泛型ID型別宣告成`String`，但`SofmapOrder`真正的JPA`@Id`欄位是`Long`型別的自動遞增主鍵。這段程式碼可以正常編譯，因為Spring Data是依照介面宣告的型別去產生查詢方法——但底層Hibernate實際執行查詢時，仍然是依照真正的`id`欄位（Long）去比對。這次審查用一個獨立的Hibernate 6.6.39/H2最小重現環境（相同的實體結構：Long自動遞增`@Id` + 唯一的String業務鍵），直接呼叫跟`SofmapServiceImpl`一樣的方式`em.find(Order.class, "046174418")`，結果：
```
Hibernate: select o1_0.id,o1_0.orderId from test_order o1_0 where o1_0.id=?
RESULT: em.find returned: null
```
查詢是用字串`"046174418"`去比對數字型別的`id`欄位，永遠不會比對成功，回傳`null`，而且不會丟出任何例外。另外也驗證了：如果輸入的字串剛好跟某一筆真實存在、但完全不相關的訂單的數字主鍵一樣（例如字串`"1"`剛好等於某筆訂單的`id=1`），會錯誤地配對到那筆不相關的訂單；如果輸入非數字格式的字串，`em.find`呼叫本身會直接丟出`IllegalArgumentException`。

## 為何是問題
`saveOrder()`的「先查詢既有訂單、有就更新、沒有就新增」(upsert)邏輯，因為`findById`永遠查不到，一律會走向「新增」這一支，嘗試對同一個`orderId`插入第二筆紀錄——而`SofmapOrder`本身有`orderId`欄位的唯一性限制，所以第二次匯入同一張訂單（例如Chrome Extension重新同步同一張訂單頁面，想比對/更新現有資訊），會直接因為違反唯一性限制而失敗。`getOrder(orderId)`這個查詢也是同樣的問題，永遠查不到既有訂單，使得Extension想先抓取既有訂單內容來比對的流程，一律收到空結果。這代表Sofmap的訂單，功能上只能被成功新增一次，之後任何重新同步/更新操作全部會失敗——而且`updateFrom()`這個本來是為了避免重新匯入時洗掉既有核銷金額等會計狀態而設計的保護邏輯，因為根本進不到「更新既有訂單」這條路徑，完全沒有機會被執行到。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：補上`findByOrderId(String orderId)`查詢方法，改用它取代`findById`**
- 做法：仿照Amazon/Rakuten已經驗證過的做法，在`SofmapOrderRepository`新增`findByOrderId`，並把`SofmapServiceImpl`裡的`saveOrder`/`getOrder`/`getOrderWithDetails`全部改成呼叫這個方法。
- 優點：跟另外兩個購物來源完全對稱，改動集中、風險低。
- 缺點：沒有明顯缺點。

**方向B：把Repository的泛型型別改成真正的主鍵型別，另外補一個依業務鍵查詢的方法**
- 做法：把`JpaRepository<SofmapOrder, String>`改成`<SofmapOrder, Long>`，業務鍵查詢另外用方向A的做法補上。
- 優點：讓Repository宣告的型別名實相符。
- 缺點：改動範圍比方向A略大，而且實際效果跟方向A相同。

---

## 審查回饋與拍板方案（Gemini Review & Implementation Report）

### 一、問題確認與責任檢討
經深入查證，Claude 提出的問題**完全屬實且為真實存在的型別錯配 Bug**。
前次我（Gemini）在實作 `SofmapOrderRepository` 時，誤將泛型主鍵型別宣告為 `String`（`JpaRepository<SofmapOrder, String>`），並在 `SofmapServiceImpl` 中誤傳業務字串 `parsedOrder.getOrderId()` 給 `findById`。由於 `SofmapOrder` 繼承自 `BaseEntity`（真實主鍵為 `Long id`），Hibernate 在底層執行 `WHERE id = ?` 時拿業務字串比對數字主鍵，導致 `findById` 永遠查無資料，在 Chrome Extension 重複同步同一筆訂單時無法進入 `updateFrom` 更新邏輯，反而嘗試二度 insert 而撞上 `UNIQUE KEY (order_id)` 唯一性限制。

Product Manager 已拍板定案採行**方向 A（並同步修正 Repository 泛型主鍵為 Long）**，與 Amazon/Rakuten 之架構完全對齊。

---

### 二、實作修復內容

1. **Repository 層修正**：
   - 檔案：[`SofmapOrderRepository.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/SofmapOrderRepository.java#L38-L46)
   - 變更：
     - 將介面宣告修正為 `JpaRepository<SofmapOrder, Long>`，使 JPA 泛型型別與底層 `BaseEntity.id`（`Long`）名實相符。
     - 新增衍生查詢方法 `Optional<SofmapOrder> findByOrderId(String orderId)`，提供正確依業務訂單編號比對之查詢功能。

2. **Service 層呼叫修正**：
   - 檔案：[`SofmapServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImpl.java#L59-L82)
   - 變更：
     - 在 `saveOrder(SofmapOrder parsedOrder)` 中，將 `sofmapOrderRepository.findById(...)` 改為 `sofmapOrderRepository.findByOrderId(...)`，使 Upsert 流程能精確查詢到既有訂單並正確觸發 `updateFrom`。
     - 在 `getOrder(String orderId)` 中，同步將 `sofmapOrderRepository.findById(...)` 改為 `sofmapOrderRepository.findByOrderId(...)`。

3. **單元測試修正與驗證**：
   - 檔案：[`SofmapServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImplTest.java)
   - 變更：
     - 將 `testSaveOrder_NewOrder`、`testSaveOrder_ExistingOrder`、`testGetOrder` 與 `testGetOrder_NotFound` 中原本對 `findById(...)` 的 Mock 與 Verify 全面同步更新為 `findByOrderId(...)`。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                 10038  12.90G   7.02    16:58:49  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/SofmapOrderRepository.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/SofmapOrderRepository.java
index 0f708db4..5d84f969 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/SofmapOrderRepository.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/SofmapOrderRepository.java
@@ -35,7 +35,15 @@ import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.So
  * Repository interface for managing {@link SofmapOrder} entities.
  */
 @Repository
-public interface SofmapOrderRepository extends JpaRepository<SofmapOrder, String> {
+public interface SofmapOrderRepository extends JpaRepository<SofmapOrder, Long> {
+
+    /**
+     * Retrieves an order by its unique business order ID.
+     *
+     * @param orderId The unique order ID.
+     * @return An Optional containing the SofmapOrder if found.
+     */
+    Optional<SofmapOrder> findByOrderId(String orderId);
 
     /**
      * Retrieves an order by its ID, eagerly fetching its associated items,
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImpl.java
index 770e8c6b..f996bd7a 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImpl.java
@@ -56,7 +56,7 @@ public class SofmapServiceImpl implements SofmapService {
     public SofmapOrderResult saveOrder(SofmapOrder parsedOrder) {
         log.debug("Saving Sofmap order: {}", parsedOrder.getOrderId());
 
-        SofmapOrder savedOrder = sofmapOrderRepository.findById(Objects.requireNonNull(parsedOrder.getOrderId())).map(existingOrder -> {
+        SofmapOrder savedOrder = sofmapOrderRepository.findByOrderId(Objects.requireNonNull(parsedOrder.getOrderId())).map(existingOrder -> {
             log.info("Updating existing Sofmap order: {}", existingOrder.getOrderId());
             existingOrder.updateFrom(parsedOrder);
             return sofmapOrderRepository.save(existingOrder);
@@ -77,7 +77,7 @@ public class SofmapServiceImpl implements SofmapService {
     @Override
     @Transactional(readOnly = true)
     public Optional<SofmapOrderResult> getOrder(String orderId) {
-        return sofmapOrderRepository.findById(Objects.requireNonNull(orderId))
+        return sofmapOrderRepository.findByOrderId(Objects.requireNonNull(orderId))
                 .map(SofmapOrderResult::fromEntity);
     }
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImplTest.java
index d144279c..24b25f36 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImplTest.java
@@ -59,7 +59,7 @@ class SofmapServiceImplTest {
     @Test
     void testSaveOrder_NewOrder() {
         SofmapOrder order = new SofmapOrder("123", null);
-        when(sofmapOrderRepository.findById("123")).thenReturn(Optional.empty());
+        when(sofmapOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
         when(sofmapOrderRepository.save(order)).thenReturn(order);
         when(sofmapOrderRepository.findWithDetailsByOrderId("123")).thenReturn(Optional.of(order));
 
@@ -74,7 +74,7 @@ class SofmapServiceImplTest {
     void testSaveOrder_ExistingOrder() {
         SofmapOrder newOrder = new SofmapOrder("123", null);
         SofmapOrder existingOrder = new SofmapOrder("123", null);
-        when(sofmapOrderRepository.findById("123")).thenReturn(Optional.of(existingOrder));
+        when(sofmapOrderRepository.findByOrderId("123")).thenReturn(Optional.of(existingOrder));
         when(sofmapOrderRepository.save(existingOrder)).thenReturn(existingOrder);
         when(sofmapOrderRepository.findWithDetailsByOrderId("123")).thenReturn(Optional.of(existingOrder));
 
@@ -88,23 +88,23 @@ class SofmapServiceImplTest {
     @Test
     void testGetOrder() {
         SofmapOrder order = new SofmapOrder("123", null);
-        when(sofmapOrderRepository.findById("123")).thenReturn(Optional.of(order));
+        when(sofmapOrderRepository.findByOrderId("123")).thenReturn(Optional.of(order));
 
         Optional<SofmapOrderResult> result = service.getOrder("123");
 
         assertTrue(result.isPresent());
         assertEquals("123", result.get().getOrderId());
-        verify(sofmapOrderRepository).findById("123");
+        verify(sofmapOrderRepository).findByOrderId("123");
     }
 
     @Test
     void testGetOrder_NotFound() {
-        when(sofmapOrderRepository.findById("999")).thenReturn(Optional.empty());
+        when(sofmapOrderRepository.findByOrderId("999")).thenReturn(Optional.empty());
 
         Optional<SofmapOrderResult> result = service.getOrder("999");
 
         assertFalse(result.isPresent());
-        verify(sofmapOrderRepository).findById("999");
+        verify(sofmapOrderRepository).findByOrderId("999");
     }
 
     @Test
diff --git a/finance-hub-backend/todo/pending fix/issue-159.md b/finance-hub-backend/todo/pending fix/issue-159.md
index bdb9e678..91bb5221 100644
--- a/finance-hub-backend/todo/pending fix/issue-159.md
+++ b/finance-hub-backend/todo/pending fix/issue-159.md
@@ -32,3 +32,34 @@ RESULT: em.find returned: null
 - 做法：把`JpaRepository<SofmapOrder, String>`改成`<SofmapOrder, Long>`，業務鍵查詢另外用方向A的做法補上。
 - 優點：讓Repository宣告的型別名實相符。
 - 缺點：改動範圍比方向A略大，而且實際效果跟方向A相同。
+
+---
+
+## 審查回饋與拍板方案（Gemini Review & Implementation Report）
+
+### 一、問題確認與責任檢討
+經深入查證，Claude 提出的問題**完全屬實且為真實存在的型別錯配 Bug**。
+前次我（Gemini）在實作 `SofmapOrderRepository` 時，誤將泛型主鍵型別宣告為 `String`（`JpaRepository<SofmapOrder, String>`），並在 `SofmapServiceImpl` 中誤傳業務字串 `parsedOrder.getOrderId()` 給 `findById`。由於 `SofmapOrder` 繼承自 `BaseEntity`（真實主鍵為 `Long id`），Hibernate 在底層執行 `WHERE id = ?` 時拿業務字串比對數字主鍵，導致 `findById` 永遠查無資料，在 Chrome Extension 重複同步同一筆訂單時無法進入 `updateFrom` 更新邏輯，反而嘗試二度 insert 而撞上 `UNIQUE KEY (order_id)` 唯一性限制。
+
+Product Manager 已拍板定案採行**方向 A（並同步修正 Repository 泛型主鍵為 Long）**，與 Amazon/Rakuten 之架構完全對齊。
+
+---
+
+### 二、實作修復內容
+
+1. **Repository 層修正**：
+   - 檔案：[`SofmapOrderRepository.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/SofmapOrderRepository.java#L38-L46)
+   - 變更：
+     - 將介面宣告修正為 `JpaRepository<SofmapOrder, Long>`，使 JPA 泛型型別與底層 `BaseEntity.id`（`Long`）名實相符。
+     - 新增衍生查詢方法 `Optional<SofmapOrder> findByOrderId(String orderId)`，提供正確依業務訂單編號比對之查詢功能。
+
+2. **Service 層呼叫修正**：
+   - 檔案：[`SofmapServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImpl.java#L59-L82)
+   - 變更：
+     - 在 `saveOrder(SofmapOrder parsedOrder)` 中，將 `sofmapOrderRepository.findById(...)` 改為 `sofmapOrderRepository.findByOrderId(...)`，使 Upsert 流程能精確查詢到既有訂單並正確觸發 `updateFrom`。
+     - 在 `getOrder(String orderId)` 中，同步將 `sofmapOrderRepository.findById(...)` 改為 `sofmapOrderRepository.findByOrderId(...)`。
+
+3. **單元測試修正與驗證**：
+   - 檔案：[`SofmapServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/sofmap/SofmapServiceImplTest.java)
+   - 變更：
+     - 將 `testSaveOrder_NewOrder`、`testSaveOrder_ExistingOrder`、`testGetOrder` 與 `testGetOrder_NotFound` 中原本對 `findById(...)` 的 Mock 與 Verify 全面同步更新為 `findByOrderId(...)`。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                      10039  12.18G   5.73    17:01:18  