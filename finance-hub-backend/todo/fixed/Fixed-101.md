# Fixed 101：同一張Amazon訂單可以被重複連結到兩筆不同的信用卡交易

## 嚴重程度
Low（原提案為Medium，經使用者確認後下修——對帳/訂單連結功能本身仍有大量未完成部分未寫入計劃文件，且目前實際跑模擬時是直接採用手動設定的生活費數字，並不依賴這條計算路徑）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java:170-186`（`linkTransaction`）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/BaseOrder.java:211-219, 250-252`（`reconciledAmount`、`getUnreconciledBalance`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java:291-314`（同一張訂單被重複連結後，必須支出金額會被加總兩次的地方）

## 現狀
`linkTransaction` 在建立一筆「信用卡交易↔Amazon訂單」的連結之前，只檢查「這筆信用卡交易本身」有沒有被連過（`findByCreditCardTransactionId`），完全沒有檢查「這張Amazon訂單」有沒有已經被連過。另外，訂單的「已核銷金額」（`reconciledAmount`）目前從來沒有被實際更新過（這部分已經記錄在 [Plan-06](../planned/Plan-06.md)，屬於已知且暫緩的問題），所以訂單的「未核銷餘額」永遠顯示全額——這代表同一張訂單即使已經連過一次，還是會持續被系統當成候選對象，推薦去連結第二筆完全不相關的信用卡交易。

## 原因
如果使用者（不論是自己手誤，或是被系統的模糊比對建議誤導）把同一張訂單連結到兩筆不同的信用卡交易上，這張訂單裡標記為「必須支出」的項目金額，會在生活費基準線的計算裡被加總兩次。

## 具體錯誤情境
使用者有一張¥8,000的Amazon訂單（內含必須支出的日常用品），正確連結到信用卡交易A。之後，另一筆金額同樣是¥8,000、但完全不相關的信用卡交易B出現時，因為這張訂單的「未核銷餘額」從未真正歸零，系統仍然會把它列為候選建議，若使用者再次把它連結到交易B，這¥8,000的必須支出金額就會被算兩次，讓生活費基準線多出¥8,000。

## 建議修正
在 `linkTransaction` 裡，對稱地補上「這張訂單是否已經被連結過」的檢查（目前只檢查信用卡交易那一側），防止同一張訂單被連結兩次。這是一個獨立、低成本的防呆，不需要牽動「已核銷金額該不該被正確更新」這個已知且暫緩的Plan-06問題。

## 與已知項目的區別
與 [Plan-06](../planned/Plan-06.md)（已核銷金額從未被寫入，暫緩處理）是相關但不同的問題：Plan-06談的是「金額有沒有被正確記錄」，本項談的是「同一張訂單有沒有可能被連結超過一次」，兩者可以獨立處理，也可以之後一併考慮。

---

# Issue 101 修復總結報告

## 修復日期
2026-09-30

## 修正概述
本修復針對「同一張 Amazon 訂單可被重複連結到兩筆信用卡交易，導致生活費基準雙重加總」之問題，完成了寫入防呆與查詢過濾，且完全獨立於 [Plan-06](../planned/Plan-06.md)，未動到既有未核銷餘額與金額計算邏輯。

## 修改檔案清單
1. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java`
   - **`linkTransaction` 核心防呆**：補上對稱的 `transactionLinkRepository.findByAmazonOrderId(amazonOrderId)` 檢查。若該訂單已被連結，拋出 `IllegalStateException`，徹底阻斷重複寫入 `TransactionLink`。
   - **`findFuzzyMatches` 推薦過濾**：在 Phase 1（一般訂單推薦）與 Phase 2（預購訂單推薦）的比對流程中，過濾掉已存在於 `transactionLinkRepository` 的訂單，避免已被連結的訂單再次出現在推薦候選清單中。

2. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/AmazonOrderRepository.java`
   - **`searchUnmatchedOrders` 手動搜尋過濾**：比照信用卡交易查詢的防護模式，在 JPQL 查詢中追加 `AND NOT EXISTS (SELECT 1 FROM TransactionLink t WHERE t.amazonOrder.id = o.id)`，確保使用者在前端手動搜尋未核銷訂單時，已被對帳的訂單不會被重複搜出。

3. `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImplTest.java`
   - 新增 `testLinkTransaction_AmazonOrderAlreadyLinked`：驗證當 Amazon 訂單已連結時，`linkTransaction` 會拋出 `IllegalStateException` 且不會儲存新的連結。
   - 新增 `testFindFuzzyMatches_NormalMatch_AlreadyLinkedOrderExcluded`：驗證已被連結的 Amazon 訂單不會被納入模糊比對推薦清單。

## 效益
- **三層防護完整封堵**：
  1. **推薦端**：模糊比對不再推薦已連結訂單。
  2. **搜尋端**：手動搜尋排除已連結訂單。
  3. **寫入端**：`linkTransaction` 拋出防呆例外，防止任何並發或手動請求寫入重複連結。
- **生活費基準安全**：確保 `IncomeExpenseCalculator` 遍歷交易連結時，同一張訂單的商品項目不會被重複計入 `totalEssentialExpenses`。
- **與 Plan-06 乾淨解耦**：不依賴 `reconciledAmount` 的數值更新，在 Plan-06 暫緩實作期間即可完全免疫此 bug。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                                                                         10004  13.41G   4.47    17:01:51  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/AmazonOrderRepository.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/AmazonOrderRepository.java
index bc5e5c10..bd5404da 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/AmazonOrderRepository.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/AmazonOrderRepository.java
@@ -128,6 +128,7 @@ public interface AmazonOrderRepository extends JpaRepository<AmazonOrder, Long>,
      */
     @Query("SELECT o FROM AmazonOrder o " +
             "WHERE (o.billedAmount - o.reconciledAmount) > 0 " +
+            "AND NOT EXISTS (SELECT 1 FROM TransactionLink t WHERE t.amazonOrder.id = o.id) " +
             "AND (:orderId IS NULL OR o.orderId LIKE concat('%', :orderId, '%')) " +
             "AND (:minAmount IS NULL OR (o.billedAmount - o.reconciledAmount) >= :minAmount) " +
             "AND (:maxAmount IS NULL OR (o.billedAmount - o.reconciledAmount) <= :maxAmount) " +
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java
index db15037c..855beae6 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java
@@ -110,6 +110,7 @@ public class ReconciliationServiceImpl implements ReconciliationService {
         LocalDate normalMinDate = maxDate.minusDays(14);
         List<AmazonOrder> normalMatches = amazonOrderRepository.findByOrderDateBetween(normalMinDate, maxDate).stream()
                 .filter(order -> order.getUnreconciledBalance().compareTo(targetAmount) == 0)
+                .filter(order -> transactionLinkRepository.findByAmazonOrderId(order.getId()).isEmpty())
                 .collect(Collectors.toList());
 
         if (!normalMatches.isEmpty()) {
@@ -125,6 +126,7 @@ public class ReconciliationServiceImpl implements ReconciliationService {
         List<AmazonOrder> preOrderMatches = amazonOrderRepository
                 .findByOrderDateBetween(preOrderMinDate, normalMinDate.minusDays(1)).stream()
                 .filter(order -> order.getUnreconciledBalance().compareTo(targetAmount) == 0)
+                .filter(order -> transactionLinkRepository.findByAmazonOrderId(order.getId()).isEmpty())
                 .collect(Collectors.toList());
 
         List<AmazonOrder> sortedPreOrderMatches = sortMatchesByProximity(preOrderMatches, maxDate);
@@ -173,6 +175,10 @@ public class ReconciliationServiceImpl implements ReconciliationService {
             throw new IllegalStateException(
                     "Credit card transaction with ID " + creditCardTransactionId + " is already linked.");
         }
+        if (!transactionLinkRepository.findByAmazonOrderId(amazonOrderId).isEmpty()) {
+            throw new IllegalStateException(
+                    "Amazon order with ID " + amazonOrderId + " is already linked.");
+        }
 
         CreditCardTransaction transaction = creditCardRepository.findById(creditCardTransactionId)
                 .orElseThrow(() -> new IllegalArgumentException(
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImplTest.java
index d8b63e2b..16739f1f 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImplTest.java
@@ -205,6 +205,22 @@ class ReconciliationServiceImplTest {
         assertTrue(matches.isEmpty());
     }
 
+    @Test
+    void testFindFuzzyMatches_NormalMatch_AlreadyLinkedOrderExcluded() {
+        org.springframework.test.util.ReflectionTestUtils.setField(order1, "orderDate", transactionDate.minusDays(5));
+        when(amazonOrderRepository.findByOrderDateBetween(transactionDate.minusDays(14), transactionDate))
+                .thenReturn(List.of(order1));
+        TransactionLink link = new TransactionLink(transaction, order1);
+        when(transactionLinkRepository.findByAmazonOrderId(10L)).thenReturn(List.of(link));
+        when(amazonOrderRepository.findByOrderDateBetween(transactionDate.minusMonths(24),
+                transactionDate.minusDays(15)))
+                .thenReturn(Collections.emptyList());
+
+        List<SuggestedMatchResult> matches = reconciliationService.findFuzzyMatches(transaction);
+
+        assertTrue(matches.isEmpty());
+    }
+
     @Test
     void testLinkTransaction_AlreadyLinked() {
         TransactionLink link = new TransactionLink(transaction, order1);
@@ -217,6 +233,18 @@ class ReconciliationServiceImplTest {
         assertTrue(exception.getMessage().contains("is already linked"));
     }
 
+    @Test
+    void testLinkTransaction_AmazonOrderAlreadyLinked() {
+        when(transactionLinkRepository.findByCreditCardTransactionId(1L)).thenReturn(Collections.emptyList());
+        TransactionLink link = new TransactionLink(transaction, order1);
+        when(transactionLinkRepository.findByAmazonOrderId(10L)).thenReturn(List.of(link));
+
+        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
+            reconciliationService.linkTransaction(1L, 10L);
+        });
+        assertTrue(exception.getMessage().contains("Amazon order with ID 10 is already linked"));
+    }
+
     @Test
     void testLinkTransaction_CreditCardNotFound() {
         when(transactionLinkRepository.findByCreditCardTransactionId(1L)).thenReturn(Collections.emptyList());
diff --git a/finance-hub-backend/todo/pending fix/issue-101.md b/finance-hub-backend/todo/pending fix/issue-101.md
index fc3aaea1..28420b2c 100644
--- a/finance-hub-backend/todo/pending fix/issue-101.md
+++ b/finance-hub-backend/todo/pending fix/issue-101.md
@@ -22,3 +22,33 @@ Low（原提案為Medium，經使用者確認後下修——對帳/訂單連結
 
 ## 與已知項目的區別
 與 [Plan-06](../planned/Plan-06.md)（已核銷金額從未被寫入，暫緩處理）是相關但不同的問題：Plan-06談的是「金額有沒有被正確記錄」，本項談的是「同一張訂單有沒有可能被連結超過一次」，兩者可以獨立處理，也可以之後一併考慮。
+
+---
+
+# Issue 101 修復總結報告
+
+## 修復日期
+2026-09-30
+
+## 修正概述
+本修復針對「同一張 Amazon 訂單可被重複連結到兩筆信用卡交易，導致生活費基準雙重加總」之問題，完成了寫入防呆與查詢過濾，且完全獨立於 [Plan-06](../planned/Plan-06.md)，未動到既有未核銷餘額與金額計算邏輯。
+
+## 修改檔案清單
+1. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java`
+   - **`linkTransaction` 核心防呆**：補上對稱的 `transactionLinkRepository.findByAmazonOrderId(amazonOrderId)` 檢查。若該訂單已被連結，拋出 `IllegalStateException`，徹底阻斷重複寫入 `TransactionLink`。
+   - **`findFuzzyMatches` 推薦過濾**：在 Phase 1（一般訂單推薦）與 Phase 2（預購訂單推薦）的比對流程中，過濾掉已存在於 `transactionLinkRepository` 的訂單，避免已被連結的訂單再次出現在推薦候選清單中。
+
+2. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/AmazonOrderRepository.java`
+   - **`searchUnmatchedOrders` 手動搜尋過濾**：比照信用卡交易查詢的防護模式，在 JPQL 查詢中追加 `AND NOT EXISTS (SELECT 1 FROM TransactionLink t WHERE t.amazonOrder.id = o.id)`，確保使用者在前端手動搜尋未核銷訂單時，已被對帳的訂單不會被重複搜出。
+
+3. `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImplTest.java`
+   - 新增 `testLinkTransaction_AmazonOrderAlreadyLinked`：驗證當 Amazon 訂單已連結時，`linkTransaction` 會拋出 `IllegalStateException` 且不會儲存新的連結。
+   - 新增 `testFindFuzzyMatches_NormalMatch_AlreadyLinkedOrderExcluded`：驗證已被連結的 Amazon 訂單不會被納入模糊比對推薦清單。
+
+## 效益
+- **三層防護完整封堵**：
+  1. **推薦端**：模糊比對不再推薦已連結訂單。
+  2. **搜尋端**：手動搜尋排除已連結訂單。
+  3. **寫入端**：`linkTransaction` 拋出防呆例外，防止任何並發或手動請求寫入重複連結。
+- **生活費基準安全**：確保 `IncomeExpenseCalculator` 遍歷交易連結時，同一張訂單的商品項目不會被重複計入 `totalEssentialExpenses`。
+- **與 Plan-06 乾淨解耦**：不依賴 `reconciledAmount` 的數值更新，在 Plan-06 暫緩實作期間即可完全免疫此 bug。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                                                              10004  13.32G   4.47    17:01:56  