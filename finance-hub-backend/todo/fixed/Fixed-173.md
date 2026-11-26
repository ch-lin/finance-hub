# [FIXED] 173：E-Trade持股HTML解析器遇到帳戶金額欄位缺失或格式異常時，會悄悄把金額當成0日圓/0美元儲存，服務層覆寫既有快照前完全沒有做任何合理性檢查，一次解析失誤就足以讓模擬引擎瞬間誤判使用者的公司股票資產歸零

## 這份issue怎麼被發現的
這是這次Source Code Review審查股票服務層時發現的——不是質疑parser的DOM/CSS選取器本身是否正確對應真實頁面（那部分明確排除在審查範圍外），而是追查parser解析失敗時回傳的「失敗訊號」是什麼，服務層有沒有辨識並擋下這個訊號。

## 相關檔案
- [ETradeHoldingsHtmlParserImpl.java:63-79](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImpl.java)（`canParse`，只要頁面文字裡出現「Total Account Value」**或**「Current Account Value」其中一個字串就回傳true——用的是`||`，不是`&&`）
- 同檔案`:95-102`（`parse`方法裡分別呼叫`extractValueAfterLabel`抓這三個金額標籤的值）
- 同檔案`:413-427`（`extractValueAfterLabel`，標籤找不到時回傳`null`）
- 同檔案`:429-448`（`parseCurrencyValue`，收到`null`或空字串時，不是拋例外、也不是回傳`null`，而是直接回傳`MonetaryAmount.of(BigDecimal.ZERO, CurrencyCode.USD)`——金額欄位解析失敗跟「這個帳戶真的記錄0元」在回傳值上完全無法區分）
- 同檔案`:130-132`（`ETradeAccountSnapshot`建構時直接吃進上面可能已經被悄悄歸零的三個金額）
- [CompanyStockJobServiceImpl.java:326-333](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/CompanyStockJobServiceImpl.java)（`handleParsedResult`裡的`ETradeHoldingsParseResult`分支：`repository.deleteByRecordDate(...)`接著`repository.save(...)`，中間沒有任何一行檢查這個新快照的金額是否合理，就直接覆寫掉同一天的既有紀錄）
- 對照：同檔案`:200-204`（`saveExtensionData`，Chrome Extension上傳路徑）——這裡是`command.getTotalAccountValue().toDomain()`，如果`command.getTotalAccountValue()`是null會直接丟`NullPointerException`，等於是「失敗就整個擋下來」，跟HTML解析路徑「失敗就悄悄存0」是兩種完全不同、不一致的失敗處理方式，而且是同一份資料模型（`ETradeAccountSnapshot`）的兩條不同輸入管道
- [SimulationContextFactory.java:147-151](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)（模擬引擎讀這個快照：`etradeRepository.findFirstByOrderByRecordDateDesc().map(snapshot -> ...snapshot.getCurrentAccountValue()...).orElse(0L)`，直接把這個數字當成公司股票資產的起始值餵進整個模擬）

## 現狀
`canParse`判斷「這份檔案該不該交給這個parser處理」時，只要求「Total Account Value」或「Current Account Value」兩個標籤其中一個存在即可通過。真正解析時，`parse`方法會各自獨立去抓這三個金額標籤的值——如果其中一個標籤在頁面上恰好缺失、位置改變或格式跟預期不符（`extractValueAfterLabel`回傳null），`parseCurrencyValue`不會往上層回報「這個欄位解析失敗」，而是直接回傳金額為0的`MonetaryAmount`，跟這個帳戶「真的記錄為0元」在資料層面完全無法區分。這個可能已經被悄悄歸零的快照，接著被`CompanyStockJobServiceImpl.handleParsedResult`不經過任何合理性檢查，直接用`deleteByRecordDate`刪掉當天既有的快照、再存入這筆新的（可能錯誤的）快照。

## 為何是問題
`SimulationContextFactory`在建立模擬情境時，公司股票資產的起始餘額完全來自這個快照的`currentAccountValue`欄位。如果某一次匯入的HTML檔案剛好讓「Current Account Value」這個標籤解析失敗（但「Total Account Value」那個標籤剛好還在，讓`canParse`仍判定為true、不會被導向`archive/failed`），系統就會把使用者原本的公司股票餘額，直接覆寫成0元，而且不會跳出任何錯誤或警示——使用者下次打開模擬結果，會看到淨資產瞬間少了一整筆公司股票的價值，而這個落差跟使用者實際財務狀況完全無關，純粹是一次解析失誤被系統當成「使用者upload的新事實」全盤接受並覆寫掉舊快照。

## 具體錯誤情境
使用者這次匯出的E*TRADE holdings頁面，因為頁面某個widget延遲載入或版面微調，導致「Current Account Value」那行文字沒有被完整存進HTML（但「Total Account Value」那行還在）。使用者原本這天的快照記錄`currentAccountValue = ¥5,000,000`。這次匯入後，`canParse`因為Total Account Value那個標籤還在而判定可以處理，`extractValueAfterLabel(bodyText, "Current Account Value")`找不到標籤回傳null，`parseCurrencyValue(null)`回傳0元。系統接著用`deleteByRecordDate`刪掉原本那筆¥5,000,000的快照，存入這筆0元的新快照。使用者下次執行模擬，公司股票資產瞬間從¥5,000,000變成0，淨資產與破產風險評估整個失真，而且畫面上沒有任何提示告知這筆資料可能有問題。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：讓解析失敗明確失敗，而不是悄悄變成0**
- 做法：讓`extractValueAfterLabel`/`parseCurrencyValue`能夠區分「標籤完全找不到」跟「標籤找到但數值剛好是0」這兩種情況，前者改成拋出例外（可以參考`SbiNisaParserImpl`已經在用的`HtmlParsingException`這個既有模式），讓這份檔案直接被導向`archive/failed`、不覆寫既有快照。
- 優點：直接在源頭擋掉錯誤資料，不讓它進到資料庫；重用專案裡已經存在、經過驗證的fail-fast模式，不是發明新機制。
- 缺點：需要處理「這個帳戶真的只是記錄0元」這種合法邊界情況（例如全部股票剛好已經出清），避免把合法的0值也一併擋下來；而且這個修法不會回頭修正已經被悄悄歸零、已經存進資料庫的歷史快照。

**方向B：在覆寫既有快照之前，加一道跟前一筆快照比對的合理性檢查**
- 做法：在`handleParsedResult`儲存新快照之前，跟同一使用者前一筆快照的金額比較，如果新舊落差超過某個比例、又沒有對應的現金交易或持股明細變動可以佐證，就標記為需要人工複核（可以參考`BaseOrder.flagForReview`已經存在的「需要人工複核」設計模式），而不是直接覆寫。
- 優點：這道防線不只能抓到「標籤缺失變成0」這一種情況，任何原因造成的離譜數字都能被攔到，防護範圍更廣；跟專案裡其他地方已經在用的「標記複核」模式一致。
- 缺點：需要訂一個合理的落差門檻，門檻本身也可能訂得不準。

**方向C：維持現狀邏輯，但至少把悄悄歸零這件事記錄下來**
- 做法：`parseCurrencyValue`在回退到0元時，至少印一筆WARNING級別的log，說明是哪個標籤解析失敗。
- 優點：改動成本極低，不改變任何現有行為跟風險。
- 缺點：不會阻止錯誤資料被存進資料庫並直接餵進模擬引擎，只是留下一條被動的診斷線索，仍然仰賴使用者自己去翻log才會發現。

---

## 修正結果（採用方向 A：解析失敗明確拋出例外，Fail-Fast 導向失敗歸檔）

本案已確認並採用 **方向 A** 進行修正。

### 1. 核心修正要點
1. **區分「標籤缺失/格式異常」與「合法的 0 元」**：
   - 在 [ETradeHoldingsHtmlParserImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImpl.java) 新增 `extractRequiredCurrencyValue` 方法，專門用於解析核心必填指標（`Total Account Value` 與 `Current Account Value`）。
   - 若頁面中找不到必填標籤（`val == null`），立即拋出 `HtmlParsingException("Missing mandatory label: " + label)`。
   - 若標籤存在但數值字串缺乏有效數字格式（例如非預期格式或 widget 載入破損），拋出 `HtmlParsingException("Invalid currency value for mandatory label: ...")`。
   - 若頁面標籤正常存在且數值明確為 `$0.00`，則依舊正常解析為 `BigDecimal.ZERO`，不影響出清股票等合法的零資產快照。
2. **服務層異常捕獲與安全歸檔**：
   - 在 [CompanyStockJobServiceImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/CompanyStockJobServiceImpl.java) 的 `parseFileWithAllParsers` 中明確重拋 `HtmlParsingException`，使批次任務的外層 catch 能捕捉具體錯誤訊息。
   - 拋出例外時，檔案被移入 `archive/failed`，終止當次匯入，徹底避免產生錯誤的零元快照並覆寫資料庫既有資料。
3. **單元測試驗證**：
   - 於 [ETradeHoldingsHtmlParserImplTest.java](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImplTest.java) 中新增：
     - `testParse_missingTotalAccountValue_throwsHtmlParsingException`：驗證缺少 Total Account Value 時拋出 `HtmlParsingException`。
     - `testParse_missingCurrentAccountValue_throwsHtmlParsingException`：驗證缺少 Current Account Value 時拋出 `HtmlParsingException`。
     - `testParse_validZeroValues_parsedSuccessfully`：驗證合法 `$0.00` 金額仍能正常解析為 0，不受例外機制誤傷。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                              10021  13.11G   7.38    15:38:58  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/CompanyStockJobServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/CompanyStockJobServiceImpl.java
index 3ca09a31..caabd72d 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/CompanyStockJobServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/CompanyStockJobServiceImpl.java
@@ -48,6 +48,7 @@ import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.et
 import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeEsppStockLotRepository;
 import ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade.ETradeRsuStockLotRepository;
 import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.command.SaveETradeExtensionCommand;
+import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
 import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.ETradeParser;
 import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeBenefitHistoryParseResult;
 import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeHoldingsParseResult;
@@ -457,6 +458,8 @@ public class CompanyStockJobServiceImpl implements JobExecutor, FileProcessingSt
             if (parser.canParse(file)) {
                 try {
                     return parser.parse(file, recordDate);
+                } catch (HtmlParsingException e) {
+                    throw e;
                 } catch (IllegalArgumentException e) {
                     if (e.getMessage() != null && e.getMessage().contains("Invalid page type")) {
                         // Try the next parser
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImpl.java
index 464de0b0..16625aed 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImpl.java
@@ -44,6 +44,7 @@ import org.jsoup.select.Elements;
 import org.springframework.stereotype.Component;
 
 import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeHoldingsParseResult;
+import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.BaseStockLot;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeAccountSnapshot;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeCashTransaction;
@@ -97,9 +98,8 @@ public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldings
         String bodyText = doc.body().text();
 
         // 1. Extract Snapshot
-        MonetaryAmount totalAccountValue = parseCurrencyValue(extractValueAfterLabel(bodyText, "Total Account Value"));
-        MonetaryAmount currentAccountValue = parseCurrencyValue(
-                extractValueAfterLabel(bodyText, "Current Account Value"));
+        MonetaryAmount totalAccountValue = extractRequiredCurrencyValue(bodyText, "Total Account Value");
+        MonetaryAmount currentAccountValue = extractRequiredCurrencyValue(bodyText, "Current Account Value");
         MonetaryAmount potentialBenefitValue = parseCurrencyValue(
                 extractValueAfterLabel(bodyText, "Potential Benefit Value"));
 
@@ -194,7 +194,8 @@ public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldings
                             getCellValue(cells, currentHeaderMap, "Est. Market Value"));
 
                     MonetaryAmount estCostBasis = parseCurrencyValue(
-                            getCellValue(cells, currentHeaderMap, "Est. Cost Basis(per share)", "Est. Cost Basis", "Cost Basis"));
+                            getCellValue(cells, currentHeaderMap, "Est. Cost Basis(per share)", "Est. Cost Basis",
+                                    "Cost Basis"));
                     if (estCostBasis == null || estCostBasis.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                         estCostBasis = price;
                     }
@@ -208,10 +209,12 @@ public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldings
 
                     MonetaryAmount expectedGainLossPerShare = parseCurrencyValue(
                             getCellValue(cells, currentHeaderMap, "Expected Gain/Loss (per share)"));
-                    if ((expectedGainLossPerShare == null || expectedGainLossPerShare.getAmount().compareTo(BigDecimal.ZERO) == 0)
+                    if ((expectedGainLossPerShare == null
+                            || expectedGainLossPerShare.getAmount().compareTo(BigDecimal.ZERO) == 0)
                             && sellableQty != null && sellableQty > 0 && expectedGainLoss != null
                             && expectedGainLoss.getAmount().compareTo(BigDecimal.ZERO) != 0) {
-                        BigDecimal perShare = expectedGainLoss.getAmount().divide(BigDecimal.valueOf(sellableQty), 2, RoundingMode.HALF_UP);
+                        BigDecimal perShare = expectedGainLoss.getAmount().divide(BigDecimal.valueOf(sellableQty), 2,
+                                RoundingMode.HALF_UP);
                         expectedGainLossPerShare = MonetaryAmount.of(perShare, expectedGainLoss.getCurrency());
                     }
 
@@ -269,11 +272,14 @@ public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldings
                         releaseDate = vestDate;
                     }
 
-                    Double sellableQty = parseDouble(getCellValue(cells, currentHeaderMap, "Sellable Qty.", "Sellable Quantity"));
-                    Double pendingSaleQty = parseDouble(getCellValue(cells, currentHeaderMap, "Pending Sale Qty.", "Pending Qty."));
+                    Double sellableQty = parseDouble(
+                            getCellValue(cells, currentHeaderMap, "Sellable Qty.", "Sellable Quantity"));
+                    Double pendingSaleQty = parseDouble(
+                            getCellValue(cells, currentHeaderMap, "Pending Sale Qty.", "Pending Qty."));
 
                     MonetaryAmount estCostBasis = parseCurrencyValue(
-                            getCellValue(cells, currentHeaderMap, "Est. Cost Basis(per share)", "Est. Cost Basis (per share)", "Est. Cost Basis", "Cost Basis"));
+                            getCellValue(cells, currentHeaderMap, "Est. Cost Basis(per share)",
+                                    "Est. Cost Basis (per share)", "Est. Cost Basis", "Cost Basis"));
                     if (estCostBasis == null || estCostBasis.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                         String costStr = extractValueAfterLabel(rowText, "Est. Cost Basis (per share)");
                         if (costStr == null) {
@@ -289,7 +295,8 @@ public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldings
 
                     MonetaryAmount expectedGainLossPerShare = parseCurrencyValue(
                             getCellValue(cells, currentHeaderMap, "Expected Gain/Loss (per share)"));
-                    if (expectedGainLossPerShare == null || expectedGainLossPerShare.getAmount().compareTo(BigDecimal.ZERO) == 0) {
+                    if (expectedGainLossPerShare == null
+                            || expectedGainLossPerShare.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                         String gainPerShareStr = extractValueAfterLabel(rowText, "Expected Gain/Loss (per share)");
                         if (gainPerShareStr != null) {
                             expectedGainLossPerShare = parseCurrencyValue(gainPerShareStr);
@@ -300,11 +307,13 @@ public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldings
                             getCellValue(cells, currentHeaderMap, "Expected Gain/Loss"));
                     if ((expectedGainLoss == null || expectedGainLoss.getAmount().compareTo(BigDecimal.ZERO) == 0)
                             && expectedGainLossPerShare != null && sellableQty != null && sellableQty > 0) {
-                        BigDecimal totalGain = expectedGainLossPerShare.getAmount().multiply(BigDecimal.valueOf(sellableQty));
+                        BigDecimal totalGain = expectedGainLossPerShare.getAmount()
+                                .multiply(BigDecimal.valueOf(sellableQty));
                         expectedGainLoss = MonetaryAmount.of(totalGain, expectedGainLossPerShare.getCurrency());
                     }
 
-                    String capitalGainsStatus = getCellValue(cells, currentHeaderMap, "Capital Gains Status", "Holding Period");
+                    String capitalGainsStatus = getCellValue(cells, currentHeaderMap, "Capital Gains Status",
+                            "Holding Period");
                     if (capitalGainsStatus == null) {
                         if (rowText.contains("Long Term")) {
                             capitalGainsStatus = "Long Term";
@@ -381,13 +390,15 @@ public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldings
             dropdownBtn = doc.selectFirst("button#sp-account-dropdown");
         }
         if (dropdownBtn != null) {
-            Matcher m = Pattern.compile("Stock Plan \\(([A-Za-z]{1,5})\\)", Pattern.CASE_INSENSITIVE).matcher(dropdownBtn.text());
+            Matcher m = Pattern.compile("Stock Plan \\(([A-Za-z]{1,5})\\)", Pattern.CASE_INSENSITIVE)
+                    .matcher(dropdownBtn.text());
             if (m.find()) {
                 return m.group(1).toUpperCase(Locale.ROOT);
             }
         }
 
-        Matcher fullDocMatcher = Pattern.compile("Stock Plan \\(([A-Za-z]{1,5})\\)", Pattern.CASE_INSENSITIVE).matcher(doc.text());
+        Matcher fullDocMatcher = Pattern.compile("Stock Plan \\(([A-Za-z]{1,5})\\)", Pattern.CASE_INSENSITIVE)
+                .matcher(doc.text());
         if (fullDocMatcher.find()) {
             return fullDocMatcher.group(1).toUpperCase(Locale.ROOT);
         }
@@ -428,6 +439,19 @@ public class ETradeHoldingsHtmlParserImpl implements ETradeParser<ETradeHoldings
         }
     }
 
+    private MonetaryAmount extractRequiredCurrencyValue(String bodyText, String label) {
+        String val = extractValueAfterLabel(bodyText, label);
+        if (val == null) {
+            throw new HtmlParsingException("Missing mandatory label: " + label);
+        }
+        String clean = val.replaceAll("[^0-9\\.-]", "");
+        if (clean.isEmpty() || clean.equals("-") || clean.equals(".")) {
+            throw new HtmlParsingException(
+                    "Invalid currency value for mandatory label: " + label + ", raw value: " + val);
+        }
+        return parseCurrencyValue(val);
+    }
+
     private String extractValueAfterLabel(String fullText, String label) {
         int index = fullText.indexOf(label);
         if (index == -1) {
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImplTest.java
index 887c7ddb..d2bbe8dd 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImplTest.java
@@ -30,6 +30,7 @@ import java.nio.file.Files;
 import java.time.LocalDate;
 
 import static org.assertj.core.api.Assertions.assertThat;
+import static org.assertj.core.api.Assertions.assertThatThrownBy;
 import static org.junit.jupiter.api.Assertions.assertFalse;
 import static org.junit.jupiter.api.Assertions.assertNotNull;
 import static org.junit.jupiter.api.Assertions.assertTrue;
@@ -39,6 +40,7 @@ import org.jsoup.Jsoup;
 import org.jsoup.nodes.Document;
 
 import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result.ETradeHoldingsParseResult;
+import ch.lin.finance.hub.backend.api.common.exception.HtmlParsingException;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeEsppStockLot;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRsuStockLot;
 
@@ -235,4 +237,66 @@ public class ETradeHoldingsHtmlParserImplTest {
         assertThat(result.getLots()).hasSize(1);
         assertThat(result.getLots().get(0).getSymbol()).isEqualTo("CRM");
     }
+
+    @Test
+    void testParse_missingTotalAccountValue_throwsHtmlParsingException() throws Exception {
+        String html = """
+            <!DOCTYPE html>
+            <html>
+            <head><title>E*TRADE - Stock Plan Overview</title></head>
+            <body>
+                <div>Current Account Value $1,000.00</div>
+            </body>
+            </html>
+            """;
+        File tempFile = File.createTempFile("missing_total_val", ".html");
+        tempFile.deleteOnExit();
+        Files.writeString(tempFile.toPath(), html);
+
+        assertThatThrownBy(() -> parser.parse(tempFile, LocalDate.of(2025, 2, 28)))
+                .isInstanceOf(HtmlParsingException.class)
+                .hasMessageContaining("Missing mandatory label: Total Account Value");
+    }
+
+    @Test
+    void testParse_missingCurrentAccountValue_throwsHtmlParsingException() throws Exception {
+        String html = """
+            <!DOCTYPE html>
+            <html>
+            <head><title>E*TRADE - Stock Plan Overview</title></head>
+            <body>
+                <div>Total Account Value $1,000.00</div>
+            </body>
+            </html>
+            """;
+        File tempFile = File.createTempFile("missing_current_val", ".html");
+        tempFile.deleteOnExit();
+        Files.writeString(tempFile.toPath(), html);
+
+        assertThatThrownBy(() -> parser.parse(tempFile, LocalDate.of(2025, 2, 28)))
+                .isInstanceOf(HtmlParsingException.class)
+                .hasMessageContaining("Missing mandatory label: Current Account Value");
+    }
+
+    @Test
+    void testParse_validZeroValues_parsedSuccessfully() throws Exception {
+        String html = """
+            <!DOCTYPE html>
+            <html>
+            <head><title>E*TRADE - Stock Plan Overview</title></head>
+            <body>
+                <div>Total Account Value $0.00</div>
+                <div>Current Account Value $0.00</div>
+            </body>
+            </html>
+            """;
+        File tempFile = File.createTempFile("valid_zero_val", ".html");
+        tempFile.deleteOnExit();
+        Files.writeString(tempFile.toPath(), html);
+
+        ETradeHoldingsParseResult result = parser.parse(tempFile, LocalDate.of(2025, 2, 28));
+        assertThat(result).isNotNull();
+        assertThat(result.getSnapshot().getTotalAccountValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
+        assertThat(result.getSnapshot().getCurrentAccountValue().getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
+    }
 }
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                   10022  13.45G   7.84    15:39:34  