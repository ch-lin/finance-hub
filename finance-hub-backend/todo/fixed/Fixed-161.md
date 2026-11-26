# [FIXED] Issue 161：報表功能的「購物類」/「樂天」報表類型，實際上完全沒有樂天的對應報表策略，查詢回來的總額會悄悄只有Amazon的部分、沒有任何提示

## 這份issue怎麼被發現的
這是這次Source Code Review針對報表產生邏輯進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/ReportType.java:40-45`（`ALL`、`SHOPPING`這兩種報表類型都把`TransactionSource.RAKUTEN`列進涵蓋範圍；另外還有一個獨立的`RAKUTEN`報表類型可以直接選擇）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/`資料夾底下只有`AmazonReportStrategy.java`跟`CreditCardReportStrategy.java`兩個檔案——沒有`RakutenReportStrategy`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImpl.java:112-121`：找不到對應策略時，只是`logger.warn(...)`記一筆日誌、靜默跳過這個區塊，不會用任何方式告知呼叫這個API的使用者

## 現狀
`ReportType`這個列舉裡，`RAKUTEN`被定義成一種可以選擇的報表類型，`SHOPPING`/`ALL`這兩種綜合類型也都宣告涵蓋樂天——但實際上整個`strategy`資料夾裡從來沒有實作任何`RakutenReportStrategy`。使用者呼叫`type=RAKUTEN`或`type=SHOPPING`的報表API，會拿到一份200成功回應的報表，裡面完全沒有樂天的任何區塊、任何金額、也沒有任何「樂天報表尚未支援」的提示——報表看起來就像是正常產生完成，只是數字剛好不包含樂天而已。

## 為何是問題
使用者如果同時有Amazon跟樂天的訂單資料，呼叫`SHOPPING`或`ALL`報表，直覺上會預期拿到「購物總支出」這種綜合數字——但這個數字實際上從頭到尾都只是Amazon的部分，樂天的每一筆支出完全沒有被計入，而且報表本身沒有任何欄位或文字去區分「這段期間真的沒有樂天訂單」跟「樂天報表這個功能根本還沒做」。如果使用者拿這份報表去核對實際花費（這正是這個報表功能自己說明文件裡宣稱的用途），看到的總額會是一個看起來完整、但實際上結構性缺了一整個類別的數字。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：補上`RakutenReportStrategy`的實作**
- 做法：仿照`AmazonReportStrategy`的結構去實作（`RakutenOrder`的明細、付款方式、`getTotalDeductions()`等結構跟Amazon相似）。
- 優點：直接把API已經宣稱支援的功能補齊，跟Amazon對稱。
- 缺點：是一筆實質的功能開發工作量，不是單純的修正。

**方向B：在方向A完成之前，先讓這個落差變得可見**
- 做法：可以是把`RAKUTEN`從`ReportType`選項裡暫時移除（不讓使用者選到一個做不到的選項），或是在產生報表時，針對找不到策略的來源，明確在報表內容裡加一行「樂天報表目前尚未支援」，取代目前這種只記錄日誌、完全不提示使用者的做法。
- 優點：成本低，馬上避免「看起來完整但實際上缺資料」這種誤導。
- 缺點：沒有真正交付這個功能，只是讓缺口變得透明。

---

## 決策與實作紀錄：採用方向 A（補齊 `RakutenReportStrategy`）

### 一、決策說明
- 審查既有架構後確認：
  1. [`RakutenOrderRepository`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/RakutenOrderRepository.java#L94) 早已提供 `findByOrderDateBetween(LocalDate startDate, LocalDate endDate)` 查詢方法。
  2. [`RakutenOrder`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/rakuten/RakutenOrder.java) 與 [`AmazonOrder`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/amazon/AmazonOrder.java) 均繼承自 `BaseOrder`，具有完全對稱的品項、折抵與支付明細結構。
  3. [`PrivacyMaskingUtil`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/util/PrivacyMaskingUtil.java#L45) 已建置 `RAKUTEN("Rakuten Shopping")` 遮蔽類型。
- 經 PM 指示，直接採用**方向 A**，比照 [`AmazonReportStrategy`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/AmazonReportStrategy.java) 完整補齊樂天報表策略，一次性消除購物報表缺漏。

### 二、實作變更
1. **建立 [`RakutenReportStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/RakutenReportStrategy.java)**：
   - 實作 [`TransactionReportStrategy`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/TransactionReportStrategy.java) 介面，宣告支援 `TransactionSource.RAKUTEN`。
   - 支援 `DATE_ASC`、`DATE_DESC`、`AMOUNT_ASC`、`AMOUNT_DESC` 四種排序規則。
   - 產出結構化 Markdown 區塊：
     - `## Rakuten Shopping Summary`（含訂單總數、總請款金額、點數與優惠券折抵金額）
     - `### Order Summary` 表格（含日期、訂單編號、請款金額、折抵金額、品項摘要，支援過長截斷與表格 pipe 符號轉義）
     - `### Order Details` 清單（含商品名稱、商品超連結 `https://item.rakuten.co.jp/{seller}/{itemId}/`、單價 x 數量、付款方式、以及隱私等級遮蔽）
   - 標註 `@Component`，由 Spring 自動裝配注入 [`ReportServiceImpl`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImpl.java)。

2. **新增與更新單元測試**：
   - 新增 [`RakutenReportStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/RakutenReportStrategyTest.java)：涵蓋無訂單、多訂單、四種排序、空品項/null品項、長品名截斷、超連結生成、支付方式呈現與折抵計算。
   - 更新 [`ReportServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImplTest.java)：新增對 `ReportType.SHOPPING` 與 `ReportType.RAKUTEN` 的整合驗證測試。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                              10041  12.30G   15.51    17:15:10  
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImplTest.java
index 8ad19026..865b86ca 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImplTest.java
@@ -52,6 +52,9 @@ class ReportServiceImplTest {
     @Mock
     private TransactionReportStrategy creditCardStrategy;
 
+    @Mock
+    private TransactionReportStrategy rakutenStrategy;
+
     private ReportServiceImpl reportService;
 
     @BeforeEach
@@ -59,6 +62,7 @@ class ReportServiceImplTest {
     void setUp() {
         lenient().when(amazonStrategy.getSupportedSource()).thenReturn(TransactionSource.AMAZON);
         lenient().when(creditCardStrategy.getSupportedSource()).thenReturn(TransactionSource.CREDIT_CARD);
+        lenient().when(rakutenStrategy.getSupportedSource()).thenReturn(TransactionSource.RAKUTEN);
     }
 
     @Test
@@ -160,4 +164,60 @@ class ReportServiceImplTest {
         verify(creditCardStrategy).generateReportSection(any(), any(), any(), any());
         verify(amazonStrategy, never()).generateReportSection(any(), any(), any(), any());
     }
+
+    @Test
+    void testGenerateSummaryReport_ShoppingType() {
+        reportService = new ReportServiceImpl(List.of(amazonStrategy, creditCardStrategy, rakutenStrategy));
+
+        when(amazonStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
+            StringBuilder sb = invocation.getArgument(3);
+            sb.append("Amazon Section\n");
+            return new BigDecimal("100.00");
+        });
+
+        when(rakutenStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
+            StringBuilder sb = invocation.getArgument(3);
+            sb.append("Rakuten Section\n");
+            return new BigDecimal("200.00");
+        });
+
+        GenerateReportCommand command = GenerateReportCommand.builder()
+                .types(Set.of(ReportType.SHOPPING))
+                .build();
+        String report = reportService.generateSummaryReport(command);
+
+        assertTrue(report.contains("Grand Total Spending: **300.00 JPY**"));
+        assertTrue(report.contains("Amazon Section"));
+        assertTrue(report.contains("Rakuten Section"));
+        assertFalse(report.contains("Credit Card Section"));
+
+        verify(amazonStrategy).generateReportSection(any(), any(), any(), any());
+        verify(rakutenStrategy).generateReportSection(any(), any(), any(), any());
+        verify(creditCardStrategy, never()).generateReportSection(any(), any(), any(), any());
+    }
+
+    @Test
+    void testGenerateSummaryReport_RakutenOnly() {
+        reportService = new ReportServiceImpl(List.of(amazonStrategy, creditCardStrategy, rakutenStrategy));
+
+        when(rakutenStrategy.generateReportSection(any(), any(), any(), any())).thenAnswer(invocation -> {
+            StringBuilder sb = invocation.getArgument(3);
+            sb.append("Rakuten Section\n");
+            return new BigDecimal("75.50");
+        });
+
+        GenerateReportCommand command = GenerateReportCommand.builder()
+                .types(Set.of(ReportType.RAKUTEN))
+                .build();
+        String report = reportService.generateSummaryReport(command);
+
+        assertTrue(report.contains("Grand Total Spending: **75.50 JPY**"));
+        assertTrue(report.contains("Rakuten Section"));
+        assertFalse(report.contains("Amazon Section"));
+        assertFalse(report.contains("Credit Card Section"));
+
+        verify(rakutenStrategy).generateReportSection(any(), any(), any(), any());
+        verify(amazonStrategy, never()).generateReportSection(any(), any(), any(), any());
+        verify(creditCardStrategy, never()).generateReportSection(any(), any(), any(), any());
+    }
 }
diff --git a/finance-hub-backend/todo/pending fix/issue-161.md b/finance-hub-backend/todo/pending fix/issue-161.md
index 6b1f5753..162c53c8 100644
--- a/finance-hub-backend/todo/pending fix/issue-161.md
+++ b/finance-hub-backend/todo/pending fix/issue-161.md
@@ -26,3 +26,29 @@
 - 做法：可以是把`RAKUTEN`從`ReportType`選項裡暫時移除（不讓使用者選到一個做不到的選項），或是在產生報表時，針對找不到策略的來源，明確在報表內容裡加一行「樂天報表目前尚未支援」，取代目前這種只記錄日誌、完全不提示使用者的做法。
 - 優點：成本低，馬上避免「看起來完整但實際上缺資料」這種誤導。
 - 缺點：沒有真正交付這個功能，只是讓缺口變得透明。
+
+---
+
+## 決策與實作紀錄：採用方向 A（補齊 `RakutenReportStrategy`）
+
+### 一、決策說明
+- 審查既有架構後確認：
+  1. [`RakutenOrderRepository`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/shopping/RakutenOrderRepository.java#L94) 早已提供 `findByOrderDateBetween(LocalDate startDate, LocalDate endDate)` 查詢方法。
+  2. [`RakutenOrder`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/rakuten/RakutenOrder.java) 與 [`AmazonOrder`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/amazon/AmazonOrder.java) 均繼承自 `BaseOrder`，具有完全對稱的品項、折抵與支付明細結構。
+  3. [`PrivacyMaskingUtil`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/util/PrivacyMaskingUtil.java#L45) 已建置 `RAKUTEN("Rakuten Shopping")` 遮蔽類型。
+- 經 PM 指示，直接採用**方向 A**，比照 [`AmazonReportStrategy`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/AmazonReportStrategy.java) 完整補齊樂天報表策略，一次性消除購物報表缺漏。
+
+### 二、實作變更
+1. **建立 [`RakutenReportStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/RakutenReportStrategy.java)**：
+   - 實作 [`TransactionReportStrategy`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/TransactionReportStrategy.java) 介面，宣告支援 `TransactionSource.RAKUTEN`。
+   - 支援 `DATE_ASC`、`DATE_DESC`、`AMOUNT_ASC`、`AMOUNT_DESC` 四種排序規則。
+   - 產出結構化 Markdown 區塊：
+     - `## Rakuten Shopping Summary`（含訂單總數、總請款金額、點數與優惠券折抵金額）
+     - `### Order Summary` 表格（含日期、訂單編號、請款金額、折抵金額、品項摘要，支援過長截斷與表格 pipe 符號轉義）
+     - `### Order Details` 清單（含商品名稱、商品超連結 `https://item.rakuten.co.jp/{seller}/{itemId}/`、單價 x 數量、付款方式、以及隱私等級遮蔽）
+   - 標註 `@Component`，由 Spring 自動裝配注入 [`ReportServiceImpl`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImpl.java)。
+
+2. **新增與更新單元測試**：
+   - 新增 [`RakutenReportStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/report/strategy/RakutenReportStrategyTest.java)：涵蓋無訂單、多訂單、四種排序、空品項/null品項、長品名截斷、超連結生成、支付方式呈現與折抵計算。
+   - 更新 [`ReportServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/report/ReportServiceImplTest.java)：新增對 `ReportType.SHOPPING` 與 `ReportType.RAKUTEN` 的整合驗證測試。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                   10041  12.60G   14.72    17:15:35  