# [FIXED] Issue 147：Sony銀行房貸解析器計算貸款總期數的方式，在特定日期組合下會系統性少算一個月

## 這份issue怎麼被發現的
這是這次Source Code Review針對房貸解析邏輯進行商業邏輯審查時發現的問題。這不是對HTML頁面結構（tag/class）的猜測，是純粹的日期運算邏輯問題，不需要實際的HTML樣本就能從程式碼本身驗證。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java:100-103`：
  ```java
  if (startDate != null && endDate != null) {
      loanTermMonths = (int) ChronoUnit.MONTHS.between(startDate, endDate);
  }
  ```
- 對照：[WONTFIX-23](../wontfix/WONTFIX-23-mortgage-reimport-fact-fields-no-fallback.md)——`loanTermMonths`被歸類在「重新匯入時沒有解析失敗保留舊值保護」的欄位清單裡，這份文件的立論前提是「銀行對帳單上的這個事實比資料庫裡現有的值更可信」，所以才決定不需要保留舊值的保護機制；但這份文件沒有檢查過「如果解析/計算出來的值本身就是錯的」這個前提是否成立。

## 現狀
`startDate`是貸款動用日（跟交屋/撥款這個事件綁定，日期在月份中是哪一天基本上是隨機的），`endDate`是最終還款日（在日本，銀行通常會把每月還款日固定在某個日子，例如每月27日，跟貸款動用日毫無關係）。`ChronoUnit.MONTHS.between(a, b)`這個Java標準函式，計算整月數時是「往下取整」——如果`b`的日期（幾號）比`a`的日期早，算出來的月數會比直覺上的整數少一個月。因為動用日的日期跟最終還款日的日期是兩個互不相關的隨機數字，只要動用日的日期（幾號）剛好比固定還款日的日期晚，這個計算就會系統性少算一個月（例如實際35年/420個月的貸款，算出來變成419個月）。

## 為何是問題
`loanTermMonths`這個值會往下傳到`MortgageCalculator.java`裡所有跟「剩餘月數」有關的重新計算（5年規則重新攤還、利率變動重新攤還、縮短年限提前還款的重新攤還），全部都是透過`MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal`去用這個總期數反推剩餘月數。少算一個月，代表這些重新計算出來的月付金額，都是用比實際少一個月的基礎去攤還同一筆餘額，算出來的月付金額會系統性偏高（方向固定偏高，不是隨機誤差）。而且依照WONTFIX-23的既定方針，這個欄位解析失敗或解析錯誤時沒有任何保留舊值的保護，每次重新匯入都會直接覆蓋，就算使用者原本手動填入正確的總期數，也會被這個算錯的值蓋過去。

這個問題的實際發生頻率需要查證——這次審查沒有真實的Sony銀行對帳單樣本，無法確認「貸款動用日」跟「最終還款日」在對帳單頁面上實際顯示出來的日期組合，是否真的會出現這種日期不對齊的情況，或者Sony銀行本身在對帳單上顯示的最終還款日,剛好跟動用日的日期對齊（這樣這個問題就不會被觸發）。這點標記為需要查證，不是確定一定會發生，但這個計算邏輯本身的缺陷是確定存在的。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：改用更精確的月數換算方式，並考慮對齊到整數期數**
- 做法：改成更接近日本銀行實務慣例的計算方式（例如把結束日往後推一天再計算，或是在換算結果接近整數——例如420、360——時直接取整數）。
- 優點：直接修正運算本身的缺陷。
- 缺點：「取整數」本身是一種推測性的修正，如果真的有一筆非整數期數的貸款（例如轉貸算出419個月），這樣處理反而會掩蓋掉真實的數字；而且確切該怎麼對齊，仍然需要真實的對帳單樣本才能確認。

**方向B：不要再從這兩個日期去推算總期數，改成跟其他「使用者設定值」欄位一樣的保護方式**
- 做法：只有在既有紀錄完全沒有`loanTermMonths`時才採用解析出來的推算值，否則保留使用者既有的設定。
- 優點：不需要先解決運算本身的精確度問題。
- 缺點：這跟WONTFIX-23當初決定「這個欄位是銀行事實、不需要保留舊值保護」的立場正好相反，如果要採用這個方向，需要重新檢視WONTFIX-23當初那個判斷的前提。

---

## 審查回饋與拍板方案（Gemini Review & Architectural Proposal）

### 一、問題本質確認與責任檢討
經查證，Claude 提出的問題**確實存在且屬實**。
前次我（Gemini）在實作 `SonyBankMortgageHtmlParserImpl.java:101-103` 時，誤用了 Java 的 `LocalDate` 級別方法：
```java
loanTermMonths = (int) ChronoUnit.MONTHS.between(startDate, endDate);
```
此方法會嚴格比對日曆中的「日（Day of Month）」。在日本銀行房貸實務中，「當初借入日（交屋撥款）」是由交屋日決定（例如 25 號），而「最終還款日」則是綁定每月約定扣款日（例如 10 號）。當借款日大於扣款日（25 > 10）時，最後一個月因不滿完整滿月而被 Java 無條件捨去截斷，導致原本 35 年（420 期）的房貸被誤算為 419 期。

---

### 二、對 Claude 建議方向之檢討
Claude 提出的兩個方向均非最佳解：
- **方向 A（猜測取整數）**：在業務層加入模糊取整邏輯（如接近 420 就四捨五入）過於脆弱，容易掩蓋特殊轉貸等真實非標準期數。
- **方向 B（推翻 WONTFIX-23 加上舊值保護）**：違背了 [WONTFIX-23](../wontfix/WONTFIX-23-mortgage-reimport-fact-fields-no-fallback.md)「對帳單事實欄位不應沿用舊值掩蓋失敗」的既定原則，本末倒置。

---

### 三、最佳拍板方案：改用 `YearMonth` 進行月份跨度計算

房貸總期數的領域模型本質是**「年月的期數跨度（YearMonth）」**，而非精確到日的天數滿月。

只要將運算提升至 `YearMonth` 級別，即可徹底根除「因日期天數截斷導致少算一個月」的 Bug，同時完全不需更動 WONTFIX-23 的事實更新方針：

```java
if (startDate != null && endDate != null) {
    loanTermMonths = (int) ChronoUnit.MONTHS.between(
            YearMonth.from(startDate),
            YearMonth.from(endDate));
}
```

#### 驗證效果：
- `startDate = 2025-03-25`，`endDate = 2060-03-10`
- `YearMonth.from(startDate)` = `2025-03`
- `YearMonth.from(endDate)` = `2060-03`
- `ChronoUnit.MONTHS.between(2025-03, 2060-03)` = **精確算出 420 個月**。
- 不受交屋日是 25 號、扣款日是 10 號之天數干擾，邏輯純淨且單純。

---

### 四、實作修復與單元測試驗證（Implementation & Verification）

1. **程式碼修改**：
   - 檔案：[`SonyBankMortgageHtmlParserImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java#L100-L105)
   - 變更：改用 `YearMonth.from(startDate)` 與 `YearMonth.from(endDate)` 呼叫 `ChronoUnit.MONTHS.between` 計算總期數跨度。

2. **新增單元測試**：
   - 檔案：[`SonyBankMortgageHtmlParserImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImplTest.java)
   - 涵蓋案例：
     - `testParse_LoanTermMonthsCalculatedUsingYearMonth_AvoidsDayTruncation`：針對「借款動用日（25日）> 約定還款日（10日）」的邊界情境，驗證跨 35 年精確算得 `420` 期，杜絕原先截斷為 `419` 期的缺陷。
     - `testParse_AlternativeEndDateKey_UsesYearMonth`：驗證替代欄位 `最終返済予定日` 跨 30 年精確算得 `360` 期。
     - `testCanParse_ExcludesHistoryPagesAndNonSonyPages`：驗證解析過濾規則（排除返濟履歷與非 Sony 頁面）。
     - `testParse_MissingMandatoryFields_ThrowsException`：驗證缺漏必填欄位時的驗證防護。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                10037  12.56G   12.69    15:42:57  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java
index ca3f096d..427f4745 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java
@@ -26,6 +26,7 @@ package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.sony;
 import java.io.File;
 import java.io.IOException;
 import java.time.LocalDate;
+import java.time.YearMonth;
 import java.time.format.DateTimeFormatter;
 import java.time.temporal.ChronoUnit;
 import java.util.Collections;
@@ -99,7 +100,9 @@ public class SonyBankMortgageHtmlParserImpl implements MortgageParser {
 
         Integer loanTermMonths = null;
         if (startDate != null && endDate != null) {
-            loanTermMonths = (int) ChronoUnit.MONTHS.between(startDate, endDate);
+            loanTermMonths = (int) ChronoUnit.MONTHS.between(
+                    YearMonth.from(startDate),
+                    YearMonth.from(endDate));
         }
 
         if (totalLoanAmount == null || interestRate == null) {
diff --git a/finance-hub-backend/todo/pending fix/issue-147.md b/finance-hub-backend/todo/pending fix/issue-147.md
index 8456040d..05a303c7 100644
--- a/finance-hub-backend/todo/pending fix/issue-147.md
+++ b/finance-hub-backend/todo/pending fix/issue-147.md
@@ -32,3 +32,62 @@
 - 做法：只有在既有紀錄完全沒有`loanTermMonths`時才採用解析出來的推算值，否則保留使用者既有的設定。
 - 優點：不需要先解決運算本身的精確度問題。
 - 缺點：這跟WONTFIX-23當初決定「這個欄位是銀行事實、不需要保留舊值保護」的立場正好相反，如果要採用這個方向，需要重新檢視WONTFIX-23當初那個判斷的前提。
+
+---
+
+## 審查回饋與拍板方案（Gemini Review & Architectural Proposal）
+
+### 一、問題本質確認與責任檢討
+經查證，Claude 提出的問題**確實存在且屬實**。
+前次我（Gemini）在實作 `SonyBankMortgageHtmlParserImpl.java:101-103` 時，誤用了 Java 的 `LocalDate` 級別方法：
+```java
+loanTermMonths = (int) ChronoUnit.MONTHS.between(startDate, endDate);
+```
+此方法會嚴格比對日曆中的「日（Day of Month）」。在日本銀行房貸實務中，「當初借入日（交屋撥款）」是由交屋日決定（例如 25 號），而「最終還款日」則是綁定每月約定扣款日（例如 10 號）。當借款日大於扣款日（25 > 10）時，最後一個月因不滿完整滿月而被 Java 無條件捨去截斷，導致原本 35 年（420 期）的房貸被誤算為 419 期。
+
+---
+
+### 二、對 Claude 建議方向之檢討
+Claude 提出的兩個方向均非最佳解：
+- **方向 A（猜測取整數）**：在業務層加入模糊取整邏輯（如接近 420 就四捨五入）過於脆弱，容易掩蓋特殊轉貸等真實非標準期數。
+- **方向 B（推翻 WONTFIX-23 加上舊值保護）**：違背了 [WONTFIX-23](../wontfix/WONTFIX-23-mortgage-reimport-fact-fields-no-fallback.md)「對帳單事實欄位不應沿用舊值掩蓋失敗」的既定原則，本末倒置。
+
+---
+
+### 三、最佳拍板方案：改用 `YearMonth` 進行月份跨度計算
+
+房貸總期數的領域模型本質是**「年月的期數跨度（YearMonth）」**，而非精確到日的天數滿月。
+
+只要將運算提升至 `YearMonth` 級別，即可徹底根除「因日期天數截斷導致少算一個月」的 Bug，同時完全不需更動 WONTFIX-23 的事實更新方針：
+
+```java
+if (startDate != null && endDate != null) {
+    loanTermMonths = (int) ChronoUnit.MONTHS.between(
+            YearMonth.from(startDate),
+            YearMonth.from(endDate));
+}
+```
+
+#### 驗證效果：
+- `startDate = 2025-03-25`，`endDate = 2060-03-10`
+- `YearMonth.from(startDate)` = `2025-03`
+- `YearMonth.from(endDate)` = `2060-03`
+- `ChronoUnit.MONTHS.between(2025-03, 2060-03)` = **精確算出 420 個月**。
+- 不受交屋日是 25 號、扣款日是 10 號之天數干擾，邏輯純淨且單純。
+
+---
+
+### 四、實作修復與單元測試驗證（Implementation & Verification）
+
+1. **程式碼修改**：
+   - 檔案：[`SonyBankMortgageHtmlParserImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java#L100-L105)
+   - 變更：改用 `YearMonth.from(startDate)` 與 `YearMonth.from(endDate)` 呼叫 `ChronoUnit.MONTHS.between` 計算總期數跨度。
+
+2. **新增單元測試**：
+   - 檔案：[`SonyBankMortgageHtmlParserImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImplTest.java)
+   - 涵蓋案例：
+     - `testParse_LoanTermMonthsCalculatedUsingYearMonth_AvoidsDayTruncation`：針對「借款動用日（25日）> 約定還款日（10日）」的邊界情境，驗證跨 35 年精確算得 `420` 期，杜絕原先截斷為 `419` 期的缺陷。
+     - `testParse_AlternativeEndDateKey_UsesYearMonth`：驗證替代欄位 `最終返済予定日` 跨 30 年精確算得 `360` 期。
+     - `testCanParse_ExcludesHistoryPagesAndNonSonyPages`：驗證解析過濾規則（排除返濟履歷與非 Sony 頁面）。
+     - `testParse_MissingMandatoryFields_ThrowsException`：驗證缺漏必填欄位時的驗證防護。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                    10037  12.51G   9.10    15:46:42  