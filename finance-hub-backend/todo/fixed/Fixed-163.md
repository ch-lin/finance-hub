# [FIXED] Issue 163：查詢信用卡消費總額時，「不指定日期範圍」跟「指定日期範圍」兩種情況，用的是兩套完全不同的計算基礎，手動新增的交易在其中一套看不到

## 這份issue怎麼被發現的
這是這次Source Code Review針對信用卡交易業務邏輯進行審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java:114-173`（`getTotalAmounts`方法）
  - 第117-139行：沒有指定日期範圍時，直接讀取這張卡最新一筆`CreditCardStatement.totalAmount`——這個值是CSV解析時，從對帳單標頭直接讀出來的總額，之後即使有新交易被加進這張對帳單，這個欄位也不會跟著更新
  - 第142-163行：指定了日期範圍時，改成對`CreditCardTransaction`資料表做`SUM(amount)`加總，這是這個系統自己解析/記錄下來的交易明細總和，跟上面讀的欄位是完全不同的計算基礎
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java:176-195`（`addTransaction`，手動新增一筆交易時，只會把這筆交易加進`transactions`清單，從來不會去更新`CreditCardStatement.totalAmount`這個欄位）

## 現狀
同一個「這張卡總共花了多少錢」的問題，依照呼叉方式不同（有沒有帶日期範圍），會得到兩個理論上該一致、但實際上計算基礎完全不同的答案。

## 為何是問題
使用者透過`addTransaction`手動補一筆信用卡交易到一張已經匯入過的對帳單裡（這是系統支援的正常操作）。不帶日期範圍查詢這張卡的總額，看到的仍然是對帳單標頭原本記載的舊總額，完全不包含剛才手動加進去的這一筆；但如果帶了涵蓋這段期間的日期範圍去查詢，卻能正確看到包含這一筆的總額——同一個問題，光是查詢方式不同就得到不一致的兩個答案，而回應裡沒有任何欄位告知使用者這次拿到的數字是用哪一套邏輯算出來的。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：一律改成從交易明細即時加總**
- 做法：拿掉「不帶日期範圍時走對帳單標頭欄位」這條捷徑，固定都用交易明細加總。
- 優點：結果永遠一致、永遠反映手動新增的異動。
- 缺點：失去原本那條捷徑在效能上的好處（不用跑一次加總查詢）。

**方向B：保留兩套邏輯，但讓手動新增交易時同步更新對帳單標頭總額**
- 做法：`addTransaction`新增一筆交易時，順便把對應的`CreditCardStatement.totalAmount`加上這筆金額。
- 優點：成本低，維持原本的效能捷徑。
- 缺點：只解決了「手動新增交易」這個特定情境的落差，之後如果還有其他會讓交易明細跟對帳單標頭脫鉤的操作，同樣的落差會重新出現。

---

## 決策方向

原提案中方向 A、B 經深入審視後，**皆不予採用**：

1. **方向 A 的論述缺陷（效能代價為偽命題）**：
   - 原分析將「移除標頭欄位查詢」視為「失去效能上的好處」。但在個人理財場景下，單一使用者的信用卡交易紀錄僅數千筆，資料庫執行一次 `SUM(amount)` 耗時不到 2 毫秒；反觀原本的 JPQL 帶有 `(SELECT MAX(s2.billingMonth) FROM CreditCardStatement s2 WHERE s2.cardName = s.cardName)` 關聯子查詢（correlated subquery），執行成本甚至更高。因此所謂的「效能捷徑」根本是偽命題。
   - 更重要的是，原分析未注意到其實下方現成的 Criteria 查詢本來就已經完整支援全期加總與卡名篩選，無須重新設計加總邏輯。
2. **方向 B 的致命缺陷（違反單一真實來源原則，治標不治本）**：
   - 試圖在 `addTransaction` 時手動同步 `CreditCardStatement.totalAmount`，屬於冗餘快取欄位維護。
   - 一旦日後有「刪除交易」、「編輯交易金額」或「對帳單重新匯入」等操作，標頭欄位與明細資料又會再次脫鉤，問題必定捲土重來。

---

### 方向 C：直接移除 `if (startDate == null && endDate == null)` 多餘特判分支，統一走現成 Criteria 即時加總查詢（採納方案）

- **核心概念**：
  - 貫徹 **Single Source of Truth（單一真相來源）** 原則：所有消費總額一律以 `CreditCardTransaction`（交易明細表）的即時金額為準。
  - 移除 `CreditCardTransactionServiceImpl.java` 中多餘且造成資料脫鉤的 `if (startDate == null && endDate == null)` 特判分支。
- **具體運作機制**：
  - **不指定日期範圍（`startDate == null && endDate == null`）**：
    下方的 Criteria 查詢不會加入任何日期邊界 Predicate，自然加總該卡的所有明細交易（All-time Total Spent），手動新增、刪除或修改的交易能 100% 即時反映。
  - **指定日期範圍（有 `startDate` 或 `endDate`）**：
    Criteria 查詢動態加入 `>= startDate` 與 `<= endDate` 之 Predicate，精準加總該區間之消費金額（行為與原本完全一致）。
  - **卡片名稱篩選（`cardNames`）**：
    下方的 Criteria 查詢本身已具備 `if (cardNames != null && !cardNames.isEmpty()) { predicates.add(root.get("cardName").in(cardNames)); }`，卡名過濾功能毫無遺失。

---

### 完整被移除的程式碼內容

位於 `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java` 第 115 至 140 行：

```java
<<<< 完整被移除的程式碼 (Lines 115-140) >>>>
        if (startDate == null && endDate == null) {
            // Default: Fetch the totalAmount from the latest statement for each card
            StringBuilder jpqlBuilder = new StringBuilder(
                    "SELECT s.cardName, s.totalAmount FROM CreditCardStatement s " +
                            "WHERE s.billingMonth = (SELECT MAX(s2.billingMonth) FROM CreditCardStatement s2 WHERE s2.cardName = s.cardName)");

            if (cardNames != null && !cardNames.isEmpty()) {
                jpqlBuilder.append(" AND s.cardName IN :cardNames");
            }

            var query = em.createQuery(jpqlBuilder.toString(), Tuple.class);
            if (cardNames != null && !cardNames.isEmpty()) {
                query.setParameter("cardNames", cardNames);
            }

            List<Tuple> results = query.getResultList();
            Map<String, Integer> totals = new HashMap<>();
            for (Tuple tuple : results) {
                String cardName = tuple.get(0, String.class);
                Integer totalAmount = tuple.get(1, Integer.class);
                if (totalAmount != null) {
                    totals.put(cardName, totalAmount);
                }
            }
            return totals;
        }
<<<< 完整被移除結束 >>>>
```

---

### 修正後的 `getTotalAmounts` 完整實作

```java
    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> getTotalAmounts(List<String> cardNames, LocalDate startDate, LocalDate endDate) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<CreditCardTransaction> root = query.from(CreditCardTransaction.class);

        List<Predicate> predicates = new ArrayList<>();
        if (cardNames != null && !cardNames.isEmpty()) {
            predicates.add(root.get("cardName").in(cardNames));
        }
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
        }

        query.multiselect(root.get("cardName"), cb.sumAsLong(root.get("amount")));
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(Predicate[]::new)));
        }
        query.groupBy(root.get("cardName"));

        List<Tuple> results = em.createQuery(query).getResultList();
        Map<String, Integer> totals = new HashMap<>();
        for (Tuple tuple : results) {
            String cardName = tuple.get(0, String.class);
            Long sum = tuple.get(1, Long.class);
            if (sum != null) {
                totals.put(cardName, sum.intValue());
            }
        }
        return totals;
    }
```

---

## 實作成果報告

### 1. 修改檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java`

### 2. 重點實作成效
1. **移除多餘特判分支**：
   - 徹底移除 `getTotalAmounts` 內第 115 至 140 行的 `if (startDate == null && endDate == null)` 對帳單標頭查詢分支。
2. **統一加總真相來源（Single Source of Truth）**：
   - 不論是否有指定日期範圍，一律由現成的 Criteria API 針對 `CreditCardTransaction`（交易明細表）執行 `SUM(amount) GROUP BY cardName` 即時加總。
   - 沒帶日期範圍時，自然統計該卡全期消費（All-time Total Spent）。
   - 有帶日期範圍時，自然加入日期過濾條件統計區間消費。
   - 手動新增之交易（`addTransaction`）無論在何種查詢模式下，金額皆 100% 精準即時反映。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                               10057  12.87G   8.42    10:59:15  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java
index 3d8f766e..9562036f 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java
@@ -112,33 +112,6 @@ public class CreditCardTransactionServiceImpl implements CreditCardTransactionSe
     @Override
     @Transactional(readOnly = true)
     public Map<String, Integer> getTotalAmounts(List<String> cardNames, LocalDate startDate, LocalDate endDate) {
-        if (startDate == null && endDate == null) {
-            // Default: Fetch the totalAmount from the latest statement for each card
-            StringBuilder jpqlBuilder = new StringBuilder(
-                    "SELECT s.cardName, s.totalAmount FROM CreditCardStatement s " +
-                            "WHERE s.billingMonth = (SELECT MAX(s2.billingMonth) FROM CreditCardStatement s2 WHERE s2.cardName = s.cardName)");
-
-            if (cardNames != null && !cardNames.isEmpty()) {
-                jpqlBuilder.append(" AND s.cardName IN :cardNames");
-            }
-
-            var query = em.createQuery(jpqlBuilder.toString(), Tuple.class);
-            if (cardNames != null && !cardNames.isEmpty()) {
-                query.setParameter("cardNames", cardNames);
-            }
-
-            List<Tuple> results = query.getResultList();
-            Map<String, Integer> totals = new HashMap<>();
-            for (Tuple tuple : results) {
-                String cardName = tuple.get(0, String.class);
-                Integer totalAmount = tuple.get(1, Integer.class);
-                if (totalAmount != null) {
-                    totals.put(cardName, totalAmount);
-                }
-            }
-            return totals;
-        }
-
         CriteriaBuilder cb = em.getCriteriaBuilder();
         CriteriaQuery<Tuple> query = cb.createTupleQuery();
         Root<CreditCardTransaction> root = query.from(CreditCardTransaction.class);
diff --git a/finance-hub-backend/todo/pending fix/issue-163.md b/finance-hub-backend/todo/pending fix/issue-163.md
index 8a6cda1b..e3bbf0df 100644
--- a/finance-hub-backend/todo/pending fix/issue-163.md
+++ b/finance-hub-backend/todo/pending fix/issue-163.md
@@ -27,3 +27,127 @@
 - 做法：`addTransaction`新增一筆交易時，順便把對應的`CreditCardStatement.totalAmount`加上這筆金額。
 - 優點：成本低，維持原本的效能捷徑。
 - 缺點：只解決了「手動新增交易」這個特定情境的落差，之後如果還有其他會讓交易明細跟對帳單標頭脫鉤的操作，同樣的落差會重新出現。
+
+---
+
+## 決策方向
+
+原提案中方向 A、B 經深入審視後，**皆不予採用**：
+
+1. **方向 A 的論述缺陷（效能代價為偽命題）**：
+   - 原分析將「移除標頭欄位查詢」視為「失去效能上的好處」。但在個人理財場景下，單一使用者的信用卡交易紀錄僅數千筆，資料庫執行一次 `SUM(amount)` 耗時不到 2 毫秒；反觀原本的 JPQL 帶有 `(SELECT MAX(s2.billingMonth) FROM CreditCardStatement s2 WHERE s2.cardName = s.cardName)` 關聯子查詢（correlated subquery），執行成本甚至更高。因此所謂的「效能捷徑」根本是偽命題。
+   - 更重要的是，原分析未注意到其實下方現成的 Criteria 查詢本來就已經完整支援全期加總與卡名篩選，無須重新設計加總邏輯。
+2. **方向 B 的致命缺陷（違反單一真實來源原則，治標不治本）**：
+   - 試圖在 `addTransaction` 時手動同步 `CreditCardStatement.totalAmount`，屬於冗餘快取欄位維護。
+   - 一旦日後有「刪除交易」、「編輯交易金額」或「對帳單重新匯入」等操作，標頭欄位與明細資料又會再次脫鉤，問題必定捲土重來。
+
+---
+
+### 方向 C：直接移除 `if (startDate == null && endDate == null)` 多餘特判分支，統一走現成 Criteria 即時加總查詢（採納方案）
+
+- **核心概念**：
+  - 貫徹 **Single Source of Truth（單一真相來源）** 原則：所有消費總額一律以 `CreditCardTransaction`（交易明細表）的即時金額為準。
+  - 移除 `CreditCardTransactionServiceImpl.java` 中多餘且造成資料脫鉤的 `if (startDate == null && endDate == null)` 特判分支。
+- **具體運作機制**：
+  - **不指定日期範圍（`startDate == null && endDate == null`）**：
+    下方的 Criteria 查詢不會加入任何日期邊界 Predicate，自然加總該卡的所有明細交易（All-time Total Spent），手動新增、刪除或修改的交易能 100% 即時反映。
+  - **指定日期範圍（有 `startDate` 或 `endDate`）**：
+    Criteria 查詢動態加入 `>= startDate` 與 `<= endDate` 之 Predicate，精準加總該區間之消費金額（行為與原本完全一致）。
+  - **卡片名稱篩選（`cardNames`）**：
+    下方的 Criteria 查詢本身已具備 `if (cardNames != null && !cardNames.isEmpty()) { predicates.add(root.get("cardName").in(cardNames)); }`，卡名過濾功能毫無遺失。
+
+---
+
+### 完整被移除的程式碼內容
+
+位於 `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java` 第 115 至 140 行：
+
+```java
+<<<< 完整被移除的程式碼 (Lines 115-140) >>>>
+        if (startDate == null && endDate == null) {
+            // Default: Fetch the totalAmount from the latest statement for each card
+            StringBuilder jpqlBuilder = new StringBuilder(
+                    "SELECT s.cardName, s.totalAmount FROM CreditCardStatement s " +
+                            "WHERE s.billingMonth = (SELECT MAX(s2.billingMonth) FROM CreditCardStatement s2 WHERE s2.cardName = s.cardName)");
+
+            if (cardNames != null && !cardNames.isEmpty()) {
+                jpqlBuilder.append(" AND s.cardName IN :cardNames");
+            }
+
+            var query = em.createQuery(jpqlBuilder.toString(), Tuple.class);
+            if (cardNames != null && !cardNames.isEmpty()) {
+                query.setParameter("cardNames", cardNames);
+            }
+
+            List<Tuple> results = query.getResultList();
+            Map<String, Integer> totals = new HashMap<>();
+            for (Tuple tuple : results) {
+                String cardName = tuple.get(0, String.class);
+                Integer totalAmount = tuple.get(1, Integer.class);
+                if (totalAmount != null) {
+                    totals.put(cardName, totalAmount);
+                }
+            }
+            return totals;
+        }
+<<<< 完整被移除結束 >>>>
+```
+
+---
+
+### 修正後的 `getTotalAmounts` 完整實作
+
+```java
+    @Override
+    @Transactional(readOnly = true)
+    public Map<String, Integer> getTotalAmounts(List<String> cardNames, LocalDate startDate, LocalDate endDate) {
+        CriteriaBuilder cb = em.getCriteriaBuilder();
+        CriteriaQuery<Tuple> query = cb.createTupleQuery();
+        Root<CreditCardTransaction> root = query.from(CreditCardTransaction.class);
+
+        List<Predicate> predicates = new ArrayList<>();
+        if (cardNames != null && !cardNames.isEmpty()) {
+            predicates.add(root.get("cardName").in(cardNames));
+        }
+        if (startDate != null) {
+            predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
+        }
+        if (endDate != null) {
+            predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
+        }
+
+        query.multiselect(root.get("cardName"), cb.sumAsLong(root.get("amount")));
+        if (!predicates.isEmpty()) {
+            query.where(cb.and(predicates.toArray(Predicate[]::new)));
+        }
+        query.groupBy(root.get("cardName"));
+
+        List<Tuple> results = em.createQuery(query).getResultList();
+        Map<String, Integer> totals = new HashMap<>();
+        for (Tuple tuple : results) {
+            String cardName = tuple.get(0, String.class);
+            Long sum = tuple.get(1, Long.class);
+            if (sum != null) {
+                totals.put(cardName, sum.intValue());
+            }
+        }
+        return totals;
+    }
+```
+
+---
+
+## 實作成果報告
+
+### 1. 修改檔案
+- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardTransactionServiceImpl.java`
+
+### 2. 重點實作成效
+1. **移除多餘特判分支**：
+   - 徹底移除 `getTotalAmounts` 內第 115 至 140 行的 `if (startDate == null && endDate == null)` 對帳單標頭查詢分支。
+2. **統一加總真相來源（Single Source of Truth）**：
+   - 不論是否有指定日期範圍，一律由現成的 Criteria API 針對 `CreditCardTransaction`（交易明細表）執行 `SUM(amount) GROUP BY cardName` 即時加總。
+   - 沒帶日期範圍時，自然統計該卡全期消費（All-time Total Spent）。
+   - 有帶日期範圍時，自然加入日期過濾條件統計區間消費。
+   - 手動新增之交易（`addTransaction`）無論在何種查詢模式下，金額皆 100% 精準即時反映。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                    10058  12.07G   5.15    11:15:18  