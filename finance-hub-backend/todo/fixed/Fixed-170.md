# [FIXED] 170：Pre2022TaxStrategy把「2014年4月～2021年底、適用消費税10%特定取得」案件的房貸控除年限，跟其他2022年以前入住案件統一回傳10年——這段期間法定控除年限究竟是10年還是13年，需要查證才能定案

## 這份question怎麼被發現的
這是這次Source Code Review針對日本稅制計算核心進行商業邏輯審查時發現的問題。原本在審查`Pre2022TaxStrategy`跟`Pre2022InhabitantTaxLimitStrategy`時，先注意到這兩個策略把「2014年以前」跟「2014~2021年」兩段可能適用不同法定上限的時期全部混在一起（這部分使用者已確認自己名下房貸不涉及2014年4月以前入住，不受影響，不在這份question的範圍內）；但追查`calculateDurationYears`這個方法時，發現同一支策略裡還藏著一個完全獨立、而且現在就可能有實際影響的問題：控除年限本身。

## 相關檔案
- [Pre2022TaxStrategy.java:56-60](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Pre2022TaxStrategy.java)（`calculateDurationYears`，不管`propertyType`是什麼，固定回傳`10`，程式碼註解寫著「For pre-2022, duration is generally 10 years, though special provisions existed」）
- [MortgageTaxDeductionStrategy.java:48-50](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/MortgageTaxDeductionStrategy.java)（介面定義，`calculateDurationYears`是決定「這筆房貸的抵稅可以持續扣幾年」的方法）
- [MortgageCalculator.java:370-373](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)（`occupancyDate.getYear() >= 2022`才切換成Post2022策略，否則一律用Pre2022策略——代表2014年4月～2021年底入住的案件，走的正是這支固定回傳10年的策略）

## 現狀
`Pre2022TaxStrategy.calculateDurationYears`不管傳進來的`propertyType`是什麼，一律固定回傳`10`。程式碼自己的註解寫著「For pre-2022, duration is generally 10 years, though special provisions existed」——這句註解本身承認「曾經存在特別規定」，但方法實作完全沒有去區分這個特別規定，不管哪一年入住都統一套用10年。

## 不確定的點是什麼、為什麼不確定
依照一般稅務常識的印象，日本消費税在2014年4月從5%調升到8%、2019年10月又調升到10%，為了緩解購屋者在調升前後的稅負落差，政府針對「特定取得」（適用新消費税率購入的住宅）案件，曾經把住宅ローン控除的年限從原本的10年延長到13年。如果這個印象是真的，代表2014年4月1日～2021年12月31日之間入住、適用新消費税率的案件，法定控除年限應該是13年，不是程式碼現在統一回傳的10年。

但這只是依照一般印象推論，這次審查沒有能力查證到可以直接引用的官方原文逐字確認（嘗試抓取國稅廳、總務省等官方網站的查證都失敗），所以不確定：
1. 「特定取得延長到13年」這個規則是否真的存在、適用範圍跟年限是否確實如此。
2. 如果確實存在，程式碼現在固定回傳10年，是否已經造成現在（2026年當下）就會發生的實際計算錯誤——如果某筆這段期間入住的房貸，真實控除年限是13年卻被系統算成10年，會讓這筆房貸的抵稅提早2~3年被切斷，使用者會少拿到本該有的抵稅額。

這裡要特別澄清一點：程式碼註解裡那句「special provisions existed」只是一行文字描述，不能被解讀成「開發者當時已經確認這個規則存在、只是刻意選擇簡化不做」——這是這次審查過程中一度被誤判的推論，沒有任何證據支持「開發者刻意選擇」這個說法，正確的描述只能是「這個方法目前沒有處理這個可能存在的特別規定」。

## 需要查證的問題
請Gemini協助查證：
1. 日本住宅ローン控除「特定取得」制度，是否確實把控除年限從10年延長到13年？適用範圍（例如是否限定一般住宅、是否所有2014年4月1日~2021年12月31日入住案件都適用）跟具體年限數字是多少？
2. 如果確認存在13年規則，程式碼需要怎麼修正：是否要讓`Pre2022TaxStrategy`依`occupancyDate`落在哪個區間（2014年4月以前/2014年4月~2021年底/其他）回傳不同的年限？

## 不建議的處理方式
不建議在沒有查證到可靠資訊的情況下，直接假設10年或13年其中一個答案是對的就修改程式碼——如果查證後確認「特定取得13年」這個規則不存在、或是這個系統目前跟未來會處理的房貸都不會落在這個區間，這份question可以直接結案，程式碼現狀維持不動；如果確認規則存在且會被實際觸發，才需要進一步設計修正方式。

---

## 官方稅法查證結論與現行系統限制說明

經查證日本國稅廳（国税庁）住宅借入金等特別控除法規及國土交通省歷史稅制資料，針對原 Question 提出的疑問，法律事實如下：

### 1. 稅法名詞與法定年限查證結果
1. **「特定取得」（消費稅 8% 或 10%）**：
   - 期間：2014年4月1日 ～ 2019年9月30日（消費稅 8% 增稅配套）。
   - **法定控除年限：依然是 10 年！**
   - 8% 增稅的減稅手段是**將年末殘高上限提高**（一般住宅從 2,000 萬提高至 4,000 萬，認定住宅提高至 5,000 萬；住民稅轉扣上限提高至 13.65 萬），**並未延長年限**。因此 2014.4~2019.9 入住案件回傳 10 年完全合法且精確。
2. **「特別特定取得」（消費稅 10%）**：
   - 期間：2019年10月1日 ～ 2020年12月31日 入住（後經疫情特例延長至 2021年12月31日，需符合契約期限）。
   - **法定控除年限：這段期間才正式延長為 13 年**。
   - **第 11～13 年控除額特殊公式**：
     第 11～13 年並非直接以「年末殘高 × 1%」計算，而是取以下兩者之**較小值**：
     - ① 年末房貸借入金殘高 × 1.0%
     - ② **建物購入價格（稅拔）× 2% ÷ 3**（將增稅 2% 於 3 年內退還）。
   - **適用限制**：個人間中古買賣（非課稅交易）不適用 13 年，仍為 10 年。

### 2. 現行系統的架構限制
1. **建物價格欄位缺失**：
   [`MortgageRecord.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/MortgageRecord.java#L73) 僅記錄 `totalLoanAmount`（貸款金額），未記錄「建物購入價格（稅拔）」，無法在第 11～13 年計算上述 ② 的法定上限。
2. **交易課稅類別與契約日期缺失**：
   系統目前無從判定個人間中古交易或特例契約期限。

---

## 實作修正報告 (Implementation Report)

採用「**拆分獨立策略 + 標註明確 TODO 與限制**」的工程過渡方案：

1. **調整現有策略區間**：
   - 在 [`Pre2022TaxStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Pre2022TaxStrategy.java#L36)：
     將 `isApplicable` 條件由 `< 2022-01-01` 調整為 `occupancyDate.isBefore(LocalDate.of(2019, 10, 1))`，代表 2019年10月以前入住（含 8% 特定取得），控除年限精確維持 **10 年**。

2. **新增特別特定取得策略**：
   - 建立 [`SpecialSpecificAcquisitionTaxStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/SpecialSpecificAcquisitionTaxStrategy.java)：
     - 適用區間：`2019年10月1日` 至 `2021年12月31日`。
     - 控除年限：新築住宅（`NEW_DETACHED_HOUSE`, `NEW_APARTMENT`, `OTHER`）回傳 **13 年**；中古住宅（`USED_DETACHED_HOUSE`, `USED_APARTMENT`）回傳 **10 年**。
     - 控除率維持 1.0%、殘高上限 4,000 萬（認定 5,000 萬）、所得上限 3,000 萬。
     - 在類別註解中詳細記錄 TODO 說明第 11～13 年建物價格 2%/3 上限公式的依賴缺口，供未來若需擴充欄位時依循。

3. **單元測試驗證**：
   - 新增 [`SpecialSpecificAcquisitionTaxStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/SpecialSpecificAcquisitionTaxStrategyTest.java) 驗證 2019.10～2021.12 區間匹配、新築 13 年與中古 10 年。
   - 新增 [`Pre2022TaxStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Pre2022TaxStrategyTest.java) 驗證 2019.10 以前區間匹配與固定 10 年。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                                                            10029  12.46G   4.20    20:21:46  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Pre2022TaxStrategy.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Pre2022TaxStrategy.java
index 6fe2a0af..5e86f7a3 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Pre2022TaxStrategy.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Pre2022TaxStrategy.java
@@ -34,7 +34,7 @@ public class Pre2022TaxStrategy implements MortgageTaxDeductionStrategy {
 
     @Override
     public boolean isApplicable(LocalDate occupancyDate) {
-        return occupancyDate.isBefore(LocalDate.of(2022, 1, 1));
+        return occupancyDate.isBefore(LocalDate.of(2019, 10, 1));
     }
 
     @Override
@@ -54,13 +54,17 @@ public class Pre2022TaxStrategy implements MortgageTaxDeductionStrategy {
     }
 
     @Override
-    public Integer calculateDurationYears(ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType propertyType) {
-        // For pre-2022, duration is generally 10 years, though special provisions existed
+    public Integer calculateDurationYears(
+            ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType propertyType) {
+        // For occupancy prior to October 2019 (including the 8% consumption tax
+        // "Specific Acquisition"
+        // period between April 2014 and September 2019), statutory deduction duration
+        // is uniformly 10 years.
         return 10;
     }
 
     @Override
     public long getMaxEligibleTotalIncome() {
-        return 30_000_000L; // 令和3年以前入居為 3,000 萬円
+        return 30_000_000L; // 30 million JPY threshold for occupancy prior to Reiwa 4 (2022)
     }
 }
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                                                                 10030  12.40G   4.17    20:21:51  