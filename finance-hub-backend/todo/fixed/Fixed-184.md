# [FIXED] 184：DC/iDeCo配分頁的雇主提繳金額（`monthlyEmployerContribution`）——Sompo的配分頁parser其實有正確解析出真實數值，但帳戶層級的`mergeAllocationFacts()`完全沒有把這個欄位納入覆蓋清單，重新匯入時這個欄位會卡在舊值，跟同一方法裡其他配分頁欄位（`monthlySubscriberContribution`/`monthlyTotalContribution`）的覆蓋範圍不一致

## 這份issue怎麼來的
這是[issue-180-04](issue-180-04.md)（DC/iDeCo頁面分派merge重構）完成後的二次review發現的新缺口，跟180-04原本記錄的兩個問題（`> 0`猜測、fund層級`fundName`比對）都是獨立的——180-04記載的兩個問題查證確認都已經修好，但重構後的`mergeAllocationFacts()`本身漏掉了一個欄位，是這次重構才產生的新落差，不是舊問題的延續，所以獨立開一份issue記錄，不回頭改180-04。

## 相關檔案
- [SompoDcAllocationParserImpl.java:79-85,96-100](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)（配分頁parser，會從`span#D_Header1_lblKigyoContributionUnit`這個元素解析出真實的雇主提繳金額，不是固定填0）
- [DefinedContributionAccountSnapshot.java:134-159](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)（`mergeAllocationFacts()`，問題所在：只覆蓋`monthlySubscriberContribution`、`monthlyTotalContribution`跟底下fund的配分事實，完全沒有一行處理`monthlyEmployerContribution`）
- 對照：[SbiIdecoAllocationParserImpl.java:90](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoAllocationParserImpl.java)、[SompoDcStatusParserImpl.java:112](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcStatusParserImpl.java)（這兩支parser跟SBI的配分頁parser一樣，`monthlyEmployerContribution`固定填`0L`——只有Sompo的配分頁parser是例外，真的有解析這個欄位）

## 現狀
`SompoDcAllocationParserImpl.parse()`會從頁面上的`span#D_Header1_lblKigyoContributionUnit`元素解析出企業（雇主）提繳金額，建構`DefinedContributionAccountSnapshot`時正確帶入這個非零值（[SompoDcAllocationParserImpl.java:81-99](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)）：
```java
long monthlyEmployerContribution = 0L;
Element kigyoContributionEl = doc.selectFirst("span#D_Header1_lblKigyoContributionUnit");
if (kigyoContributionEl != null) {
    monthlyEmployerContribution = parseAmount(kigyoContributionEl.text());
}
...
DefinedContributionAccountSnapshot accountSnapshot = new DefinedContributionAccountSnapshot(
        recordDate, DefinedContributionType.CORPORATE_DC, 0L, 0L,
        monthlyEmployerContribution,
        monthlySubscriberContribution,
        monthlyEmployerContribution + monthlySubscriberContribution);
```
但[issue-180-04](issue-180-04.md)重構後的`mergeAllocationFacts()`（[DefinedContributionAccountSnapshot.java:134-159](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)）：
```java
public void mergeAllocationFacts(DefinedContributionAccountSnapshot incoming) {
    this.monthlySubscriberContribution = incoming.monthlySubscriberContribution;
    this.monthlyTotalContribution = incoming.monthlyTotalContribription;
    for (DefinedContributionFundSnapshot otherFund : incoming.funds) {
        ...
    }
}
```
完全沒有`this.monthlyEmployerContribution = incoming.monthlyEmployerContribution;`這一行——這個欄位從`existing`（資料庫裡既有的記錄）的角度看，重新匯入配分頁永遠不會更新它，不管`incoming`（這次剛解析出來的新快照）裡的值是多少。

## 為何是問題
`monthlyEmployerContribution`跟同一個`mergeAllocationFacts()`方法裡其他兩個欄位（`monthlySubscriberContribution`、`monthlyTotalContribution`）性質完全相同——都是配分頁上的客觀事實數字，應該在每次重新匯入時被最新事實覆蓋。目前的落差會造成一個矛盾：解析階段正確抓到了新數值，`monthlyTotalContribution`（總提繳額，等於雇主+個人）也正確反映了這個新雇主提繳額（因為是在parser端用`monthlyEmployerContribution + monthlySubscriberContribution`算出來的瞬時值），但資料庫裡實際存起來、`monthlyEmployerContribution`這個獨立欄位本身卻沒有跟著更新——`monthlyTotalContribution`跟`monthlyEmployerContribution`兩個欄位看起來會逐漸不同步（`monthlyTotalContribution`是最新的，`monthlyEmployerContribution`是舊的）。

這重現了[issue-175](issue-175.md)（Rakuten訂單`taxAmount`被`updateFrom`漏掉）同一種錯誤模式——不是「merge邏輯整個不處理某欄位類別」，是「merge方法裡一堆性質相同的欄位，少寫了其中一行」。

## 具體錯誤情境
使用者的企業型DC帳戶，公司每月提繳3萬円（`monthlyEmployerContribution=30000`）。使用者離職、公司停止提繳，使用者重新匯入Sompo配分頁最新存檔，這次頁面上雇主提繳顯示為0。`SompoDcAllocationParserImpl`正確解析出`monthlyEmployerContribution=0`，`mergeAllocationFacts()`被呼叫，但因為這個方法完全沒有處理這個欄位，資料庫裡的`monthlyEmployerContribution`繼續停留在30000——跟[issue-180-04](issue-180-04.md)原本描述的「`> 0`猜測造成停止提繳無法被正確反映」是同一種使用者可感知後果，只是這次的根因是「重構後的新方法漏寫了一行」，不是「舊的`> 0`判斷邏輯本身」。

## 建議修正方向
在`DefinedContributionAccountSnapshot.mergeAllocationFacts()`裡補上一行，跟同一方法裡其他兩個欄位的寫法一致：
```java
public void mergeAllocationFacts(DefinedContributionAccountSnapshot incoming) {
    this.monthlyEmployerContribution = incoming.monthlyEmployerContribution;
    this.monthlySubscriberContribution = incoming.monthlySubscriberContribution;
    this.monthlyTotalContribution = incoming.monthlyTotalContribution;
    for (DefinedContributionFundSnapshot otherFund : incoming.funds) {
        ...
    }
}
```
這是唯一合理的修法，不是多方案選一——`monthlyEmployerContribution`跟同方法裡另外兩個欄位性質完全相同，補齊這一行即可，不需要額外設計。

## 這份文件沒有解決的部分
- SBI的配分頁parser（`SbiIdecoAllocationParserImpl`）跟兩家公司的資產現狀頁parser都固定把`monthlyEmployerContribution`填0，這份issue不去判斷「SBI的iDeCo頁面是否也該提供雇主提繳金額」——查證確認iDeCo（個人型）本質上沒有雇主提繳這個概念（只有企業型DC才有），所以SBI/iDeCo這邊固定填0，可能本來就是正確的業務邏輯，不是bug；這份issue只處理「Sompo配分頁明明解析出了真實值，merge方法卻漏接」這個已經確認的落差。

---

## 實作完成紀錄 (Implementation Complete)

- **實作時間**：2026-11-05
- **實作內容**：
  1. [`DefinedContributionAccountSnapshot.java`](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)：在 `mergeAllocationFacts()` 方法中補上 `this.monthlyEmployerContribution = incoming.monthlyEmployerContribution;`，確保配分切面匯入時無條件覆蓋最新事實值。
  2. [`DefinedContributionAccountSnapshotTest.java`](file://finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java)：
     - 在 `testMergeAllocationFacts_UnconditionalZeroOverwrites` 補齊對 `monthlyEmployerContribution` 歸零的斷言檢查。
     - 新增單元測試 `testMergeAllocationFacts_OverwritesEmployerContribution`，驗證配分頁解析之雇主提繳額度能正確覆蓋進既有快照。
- **狀態**：修復完成，配分切面之三項提繳事實（雇主、個人、合計）已全數一致納入無條件覆蓋。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                          10010  11.72G   7.73    08:51:10  
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java
index 4c26d23a..6a728077 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java
@@ -48,14 +48,14 @@ import lombok.Setter;
 
 @Entity
 @Table(name = DefinedContributionAccountSnapshot.TABLE_NAME, indexes = {
-    @Index(name = DefinedContributionAccountSnapshot.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
+        @Index(name = DefinedContributionAccountSnapshot.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
 }, uniqueConstraints = {
-    @UniqueConstraint(columnNames = {MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
-        DefinedContributionAccountSnapshot.RECORD_DATE_COLUMN,
-        DefinedContributionAccountSnapshot.TYPE_COLUMN})
+        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
+                DefinedContributionAccountSnapshot.RECORD_DATE_COLUMN,
+                DefinedContributionAccountSnapshot.TYPE_COLUMN })
 })
 @Getter
-@EqualsAndHashCode(of = {"recordDate", "type"}, callSuper = false)
+@EqualsAndHashCode(of = { "recordDate", "type" }, callSuper = false)
 @NoArgsConstructor(access = AccessLevel.PROTECTED)
 public class DefinedContributionAccountSnapshot extends MultiUserBaseEntity {
 
@@ -128,10 +128,12 @@ public class DefinedContributionAccountSnapshot extends MultiUserBaseEntity {
 
     /**
      * Merges factual fields from an ALLOCATION page facet.
-     * Directly overwrites monthly contribution amounts and per-fund allocation facts.
+     * Directly overwrites monthly contribution amounts and per-fund allocation
+     * facts.
      * Keeps balance facts intact.
      */
     public void mergeAllocationFacts(DefinedContributionAccountSnapshot incoming) {
+        this.monthlyEmployerContribution = incoming.monthlyEmployerContribution;
         this.monthlySubscriberContribution = incoming.monthlySubscriberContribution;
         this.monthlyTotalContribution = incoming.monthlyTotalContribution;
 
diff --git a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java
index f26c0eba..5cd36bae 100644
--- a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java
+++ b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java
@@ -41,14 +41,12 @@ class DefinedContributionAccountSnapshotTest {
                 800_000L,
                 0L,
                 20_000L,
-                20_000L
-        );
+                20_000L);
 
         DefinedContributionAccountSnapshot memberInfo = new DefinedContributionAccountSnapshot(
                 LocalDate.of(2025, 1, 1),
                 DefinedContributionType.IDECO,
-                0L, 0L, 0L, 0L, 0L
-        );
+                0L, 0L, 0L, 0L, 0L);
         LocalDate enrollmentDate = LocalDate.of(2020, 5, 1);
         memberInfo.setEnrollmentDate(enrollmentDate);
 
@@ -64,38 +62,35 @@ class DefinedContributionAccountSnapshotTest {
     void testMergeAllocationFacts_UnconditionalZeroOverwrites() {
         DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                 LocalDate.of(2025, 1, 1),
-                DefinedContributionType.IDECO,
+                DefinedContributionType.CORPORATE_DC,
                 2_500_000L,
                 1_800_000L,
-                0L,
+                10_000L,
                 23_000L,
-                23_000L
-        );
+                33_000L);
 
         DefinedContributionFundSnapshot fund = new DefinedContributionFundSnapshot(
                 "海外股票", "三菱UFJ純金ファンド", "9200181027",
                 100.0, 15000.0, 16000.0, 1_500_000L, 1_600_000L, 100_000L,
-                23_000L, 100.0
-        );
+                23_000L, 100.0);
         existing.addFund(fund);
 
         // Incoming allocation page facts: contribution stopped (becomes 0)
         DefinedContributionAccountSnapshot incomingAllocation = new DefinedContributionAccountSnapshot(
                 LocalDate.of(2025, 1, 1),
-                DefinedContributionType.IDECO,
+                DefinedContributionType.CORPORATE_DC,
                 0L, 0L, 0L,
-                0L, 0L
-        );
+                0L, 0L);
         DefinedContributionFundSnapshot incomingFund = new DefinedContributionFundSnapshot(
                 "海外股票", "三菱UFJ純金ファンド", "9200181027",
                 0.0, 0.0, 0.0, 0L, 0L, 0L,
-                0L, 0.0
-        );
+                0L, 0.0);
         incomingAllocation.addFund(incomingFund);
 
         existing.mergeAllocationFacts(incomingAllocation);
 
         // Account level contributions must be updated to 0
+        assertThat(existing.getMonthlyEmployerContribution()).isEqualTo(0L);
         assertThat(existing.getMonthlySubscriberContribution()).isEqualTo(0L);
         assertThat(existing.getMonthlyTotalContribution()).isEqualTo(0L);
 
@@ -123,27 +118,23 @@ class DefinedContributionAccountSnapshotTest {
                 500_000L,
                 10_000L,
                 10_000L,
-                20_000L
-        );
+                20_000L);
 
         DefinedContributionFundSnapshot fund = new DefinedContributionFundSnapshot(
                 "外国株式", "インデックスファンド海外株式", "009911102C",
                 50.0, 20000.0, 20000.0, 1_000_000L, 1_000_000L, 0L,
-                20_000L, 100.0
-        );
+                20_000L, 100.0);
         existing.addFund(fund);
 
         // Incoming status page facts: balance reset or new holdings
         DefinedContributionAccountSnapshot incomingStatus = new DefinedContributionAccountSnapshot(
                 LocalDate.of(2025, 1, 1),
                 DefinedContributionType.CORPORATE_DC,
-                0L, 0L, 0L, 0L, 0L
-        );
+                0L, 0L, 0L, 0L, 0L);
         DefinedContributionFundSnapshot incomingFund = new DefinedContributionFundSnapshot(
                 "外国株式", "インデックスファンド海外株式", "009911102C",
                 0.0, 0.0, 0.0, 0L, 0L, 0L,
-                0L, 0.0
-        );
+                0L, 0.0);
         incomingStatus.addFund(incomingFund);
 
         existing.mergeStatusFacts(incomingStatus);
@@ -157,7 +148,8 @@ class DefinedContributionAccountSnapshotTest {
         assertThat(existing.getMonthlySubscriberContribution()).isEqualTo(10_000L);
         assertThat(existing.getMonthlyTotalContribution()).isEqualTo(20_000L);
 
-        // Fund holding units must be updated to 0, but allocation percentage remains 100.0
+        // Fund holding units must be updated to 0, but allocation percentage remains
+        // 100.0
         DefinedContributionFundSnapshot mergedFund = existing.getFunds().get(0);
         assertThat(mergedFund.getUnits()).isEqualTo(0.0);
         assertThat(mergedFund.getMonthlyContributionAmount()).isEqualTo(20_000L);
@@ -170,28 +162,24 @@ class DefinedContributionAccountSnapshotTest {
         DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                 LocalDate.of(2025, 1, 1),
                 DefinedContributionType.IDECO,
-                0L, 0L, 0L, 20_000L, 20_000L
-        );
+                0L, 0L, 0L, 20_000L, 20_000L);
 
         // Existing fund from allocation page with formal name
         DefinedContributionFundSnapshot fund = new DefinedContributionFundSnapshot(
                 "海外株式", "三菱ＵＦＪ純金ファンド（愛称：ファインゴールド）", "9200181027",
                 0.0, 0.0, 0.0, 0L, 0L, 0L,
-                20_000L, 100.0
-        );
+                20_000L, 100.0);
         existing.addFund(fund);
 
         // Status page incoming facts with abbreviated name, but identical fundCode
         DefinedContributionAccountSnapshot statusSnapshot = new DefinedContributionAccountSnapshot(
                 LocalDate.of(2025, 1, 1),
                 DefinedContributionType.IDECO,
-                500_000L, 400_000L, 0L, 0L, 0L
-        );
+                500_000L, 400_000L, 0L, 0L, 0L);
         DefinedContributionFundSnapshot statusFund = new DefinedContributionFundSnapshot(
                 "海外株式", "三菱UFJ純金ﾌｧﾝﾄﾞ", "9200181027",
                 30.0, 15000.0, 16666.0, 450_000L, 500_000L, 50_000L,
-                0L, 0.0
-        );
+                0L, 0.0);
         statusSnapshot.addFund(statusFund);
 
         existing.mergeStatusFacts(statusSnapshot);
@@ -213,32 +201,57 @@ class DefinedContributionAccountSnapshotTest {
         DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
                 LocalDate.of(2025, 1, 1),
                 DefinedContributionType.IDECO,
-                500_000L, 400_000L, 0L, 0L, 0L
-        );
+                500_000L, 400_000L, 0L, 0L, 0L);
 
         DefinedContributionFundSnapshot fund1 = new DefinedContributionFundSnapshot(
                 "海外株式", "三菱UFJ純金ファンド", "9200181027",
                 30.0, 15000.0, 16666.0, 450_000L, 500_000L, 50_000L,
-                0L, 0.0
-        );
+                0L, 0.0);
         existing.addFund(fund1);
 
         DefinedContributionAccountSnapshot allocationSnapshot = new DefinedContributionAccountSnapshot(
                 LocalDate.of(2025, 1, 1),
                 DefinedContributionType.IDECO,
-                0L, 0L, 0L, 30_000L, 30_000L
-        );
+                0L, 0L, 0L, 30_000L, 30_000L);
         DefinedContributionFundSnapshot fund2 = new DefinedContributionFundSnapshot(
                 "海外株式", "ＤＣニッセイ外国株式インデックス", "9200194887",
                 0.0, 0.0, 0.0, 0L, 0L, 0L,
-                30_000L, 100.0
-        );
+                30_000L, 100.0);
         allocationSnapshot.addFund(fund2);
 
         existing.mergeAllocationFacts(allocationSnapshot);
 
         assertThat(existing.getFunds()).hasSize(2);
-        assertThat(existing.getFunds()).extracting(c -> c.getFundCode()).containsExactlyInAnyOrder("9200181027", "9200194887");
+        assertThat(existing.getFunds()).extracting(c -> c.getFundCode()).containsExactlyInAnyOrder("9200181027",
+                "9200194887");
     }
-}
 
+    @Test
+    @DisplayName("mergeAllocationFacts: should correctly update monthlyEmployerContribution from incoming allocation")
+    void testMergeAllocationFacts_OverwritesEmployerContribution() {
+        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
+                LocalDate.of(2025, 1, 1),
+                DefinedContributionType.CORPORATE_DC,
+                1_000_000L,
+                500_000L,
+                0L,
+                0L,
+                0L);
+
+        DefinedContributionAccountSnapshot incoming = new DefinedContributionAccountSnapshot(
+                LocalDate.of(2025, 1, 1),
+                DefinedContributionType.CORPORATE_DC,
+                0L,
+                0L,
+                30_000L,
+                10_000L,
+                40_000L);
+
+        existing.mergeAllocationFacts(incoming);
+
+        assertThat(existing.getMonthlyEmployerContribution()).isEqualTo(30_000L);
+        assertThat(existing.getMonthlySubscriberContribution()).isEqualTo(10_000L);
+        assertThat(existing.getMonthlyTotalContribution()).isEqualTo(40_000L);
+        assertThat(existing.getTotalAssetValue()).isEqualTo(1_000_000L);
+    }
+}
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                               10010  11.91G   5.47    08:55:34  
 