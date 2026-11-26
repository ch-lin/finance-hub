# [FIXED] 181：NISA提繳設定/持股快照的job import查詢既有記錄時，仍然用`fundName`字串比對，沒有接線到已經新增好的`fundCode`——基金名稱解析結果只要跟上次有一點差異，就會被當成全新記錄，連帶讓使用者設定的預期報酬率在新記錄上變回null

## 這份issue怎麼被發現的
這是在核對[issue-180](issue-180.md)（job import merge規則）NISA領域的改動是否完整時發現的——`NisaContributionSetting.merge()`/`NisaSnapshotRecord.merge()`本身已經正確改好（`expectedReturnRate`被strictly preserved，其餘欄位正確覆蓋），但往上一層看`SbiNisaJobServiceImpl`怎麼決定「這次解析出來的記錄，對應DB裡哪一筆既有資料」時，發現查詢邏輯沒有跟著改到`fundCode`，還停留在用`fundName`字串比對。

## 相關檔案
- [SbiNisaJobServiceImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java)（`saveOrMergeRecords`裡的兩段查詢既有記錄邏輯；`inheritHistoricalRate`兩個多載方法）
- [NisaContributionSettingRepository.java:61](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaContributionSettingRepository.java)（`findByRecordDateAndAccountTypeAndFundNameAndCourse`）
- [NisaSnapshotRecordRepository.java:92](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaSnapshotRecordRepository.java)（`findByRecordDateAndAccountTypeAndFundName`）
- [NisaContributionSetting.java:55](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java)（`@EqualsAndHashCode(of = {"recordDate", "accountType", "fundName", "course"})`，仍未改成`fundCode`）
- [NisaSnapshotRecord.java:54](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java)（`@EqualsAndHashCode(of = {"recordDate", "accountType", "fundName"})`，仍未改成`fundCode`）
- [SbiNisaParserImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java)（`extractFundCode`已確認會從頁面基金名稱連結的`href`屬性解析出代碼，`fundCode`欄位本身是有在填的，不是空殼欄位）

## 現狀
`NisaContributionSetting`、`NisaSnapshotRecord`這兩個domain實體都已經新增了`fundCode`欄位，`SbiNisaParserImpl.extractFundCode()`也確認會正確解析出這個代碼（使用者確認這是SBI網頁依URL上的代碼去抓對應基金資訊用的「投信協会コード」，是公開通用代碼，不是SBI自己配的）。

但`SbiNisaJobServiceImpl.saveOrMergeRecords`裡，用來判斷「這次解析出來的記錄對應DB裡哪一筆既有資料」的查詢，仍然是：
```java
nisaRepository.findByRecordDateAndAccountTypeAndFundName(
        incoming.getRecordDate(), incoming.getAccountType(), incoming.getFundName());
```
```java
nisaSettingRepository.findByRecordDateAndAccountTypeAndFundNameAndCourse(
        incoming.getRecordDate(), incoming.getAccountType(), incoming.getFundName(), incoming.getCourse());
```
兩段都是用`fundName`（基金名稱字串）當查詢鍵的一部分，完全沒有用到已經就位的`fundCode`。兩個domain實體上的`@EqualsAndHashCode`也還是`fundName`，沒有改成`fundCode`。

查到既有記錄時呼叫的`merge()`方法本身沒有問題：事實欄位（含`fundCode`、`fundName`）正確覆蓋，使用者欄位`expectedReturnRate`正確保留，這部分已經在issue-180的範圍內改好了。

## 為何是問題
基金名稱是人類可讀的中文/日文字串，比代碼更容易在不同時間點的解析結果之間出現細微差異——網頁改版導致多了一個全角空格、基金名稱本身被發行公司更新過用字、解析器擷取時多切或少切了一個字元，任何一種情況都會讓這次解析出來的`fundName`跟DB裡既有那一筆存的`fundName`不完全相等。只要不完全相等，`findByRecordDateAndAccountTypeAndFundName`就查不到既有記錄，程式碼會判定這是一筆全新記錄，而不是既有記錄的更新。

這個問題不只是「多新增一筆重複資料」那麼單純，還會連帶觸發`inheritHistoricalRate`：
```java
nisaRepository.findFirstByAccountTypeAndFundNameOrderByRecordDateDesc(
        record.getAccountType(), record.getFundName())
```
這個方法同樣用`fundName`去找「這支基金上一筆記錄設定的`expectedReturnRate`是多少」，然後把這個值繼承到新記錄上。如果`fundName`對不上，這個查詢也會找不到任何歷史記錄，`inheritHistoricalRate`會把新記錄的`expectedReturnRate`設成`null`——即使DB裡其實已經有這支基金過去設定好的預期報酬率，只是因為這次`fundName`解析結果跟歷史記錄的字串不完全一致而找不到。使用者原本辛苦設定好的`expectedReturnRate`，在下一次重新匯入、恰好基金名稱字串有落差時，會悄悄變回null，而不是像issue-180定案的merge規則所保證的那樣被正確保留——因為這筆新記錄從程式的角度看，根本不是「既有記錄的更新」，是一筆全新的、`expectedReturnRate`從未被設定過的記錄。

## 具體錯誤情境
使用者的つみたてNISA持有一支基金，SBI網頁上基金名稱目前顯示「eMAXIS Slim 全世界株式（オール・カントリー）」，使用者已經幫這支基金設定好`expectedReturnRate=5.5`。某次SBI網頁改版或解析器擷取範圍微調，導致這次解析出來的`fundName`多了一個尾端全角空格，變成「eMAXIS Slim 全世界株式（オール・カントリー）　」。重新匯入時：
1. `findByRecordDateAndAccountTypeAndFundName`用這個多了空格的新字串去查，查不到DB裡原本那筆（字串不完全相等），判定為全新記錄。
2. 流程走到`inheritHistoricalRate`，用同樣多了空格的字串去查歷史記錄，一樣查不到，`expectedReturnRate`被設成`null`。
3. 最終結果：資料庫裡同一支基金出現了兩筆記錄——一筆是舊的（`fundName`沒有空格，`expectedReturnRate=5.5`），一筆是新的（`fundName`多了空格，`expectedReturnRate=null`）。模擬引擎（`NisaContextBuilder`）如果讀到的是新的那一筆，會用`null`的預期報酬率去跑模擬，使用者完全不會意識到自己設定的報酬率已經失效，因為畫面上顯示的可能還是舊記錄、或者兩筆記錄同時顯示造成使用者困惑。

## 建議修正方向
應該用`fundCode`，不是`fundName`——基金名稱是人類可讀字串，天生就不穩定（空格、半角全角、發行公司改字用詞都會造成前後不一致），`fundCode`是SBI網頁依URL核發的代碼，這才是這個問題該用的識別方式。這跟issue-180主文件當初盤點NISA領域時就已經定案的方向一致，不是重新討論的開放問題，只是查詢邏輯這一步還沒接線完成。

**做法**：`NisaContributionSettingRepository`/`NisaSnapshotRecordRepository`新增`findByRecordDateAndAccountTypeAndFundCodeAndCourse`/`findByRecordDateAndAccountTypeAndFundCode`這類查詢方法，`SbiNisaJobServiceImpl`的`saveOrMergeRecords`跟`inheritHistoricalRate`都改用這組查詢；兩個domain實體的`@EqualsAndHashCode`也改成包含`fundCode`而不是`fundName`。

**唯一需要先確認的前提**：`fundCode`在所有既有歷史資料裡是否都已經有值——如果有些舊記錄是在`fundCode`欄位還沒新增之前就已經存在資料庫裡，這些舊記錄的`fundCode`會是null，查詢時用`fundCode`比對會查不到這些舊記錄，一樣會被誤判成新記錄。如果存在這種舊資料，需要先補一次資料回填（把舊記錄的`fundCode`透過`fundName`對應關係補上）再切換查詢鍵，不是在查詢邏輯裡長期保留「`fundCode`為null時才退回用`fundName`比對」這種雙軌判斷——雙軌判斷只是把`fundName`不穩定的問題繼續留在程式碼裡，不是真正解決它。

## 修正報告書

### 1. 修正概述
本 issue 已於 2026-11-01 修正完畢。針對 NISA 提繳設定（`NisaContributionSetting`）與持股快照（`NisaSnapshotRecord`）領域，全面將唯一識別鍵、查詢比對鍵與模擬配對鍵由不穩定的基金名稱字串（`fundName`）切換為官方通用代碼（`fundCode`，投信協会コード），徹底消除因網頁改版、全半角空格或名稱微調導致既有記錄被重複新增及預期報酬率丟失的隱患。同時順手移除了 Repository 殘留的批次刪除方法（`deleteByRecordDate`），落實 `issue-180`「Job Import 永遠 merge 不清空」的原則。

### 2. 異動檔案與修正內容
1. **Domain 實體唯一約束與 Equals/HashCode**：
   - [NisaContributionSetting.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java)：
     - `@Table` uniqueConstraints 由 `FUND_NAME_COLUMN` 改為 `FUND_CODE_COLUMN`（`ownerUserId + recordDate + accountType + fundCode + course`）。
     - `@EqualsAndHashCode(of = {"recordDate", "accountType", "fundCode", "course"})`。
   - [NisaSnapshotRecord.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java)：
     - `@Table` uniqueConstraints 由 `FUND_NAME_COLUMN` 改為 `FUND_CODE_COLUMN`（`ownerUserId + recordDate + accountType + fundCode`）。
     - `@EqualsAndHashCode(of = {"recordDate", "accountType", "fundCode"})`。

2. **Repository 查詢方法切換**：
   - [NisaContributionSettingRepository.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaContributionSettingRepository.java)：
     - 移除舊有的 `findFirstByCourseAndFundNameOrderByRecordDateDesc`、`findFirstByAccountTypeAndFundNameOrderByRecordDateDesc`、`findByRecordDateAndAccountTypeAndFundNameAndCourse` 及 `deleteByRecordDate`。
     - 新增 `findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(String accountType, String fundCode)`。
     - 新增 `findByRecordDateAndAccountTypeAndFundCodeAndCourse(LocalDate recordDate, String accountType, String fundCode, String course)`。
   - [NisaSnapshotRecordRepository.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaSnapshotRecordRepository.java)：
     - 移除舊有的 `findFirstByAccountTypeAndFundNameOrderByRecordDateDesc`、`findByRecordDateAndAccountTypeAndFundName` 及 `deleteByRecordDate`。
     - 新增 `findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(String accountType, String fundCode)`。
     - 新增 `findByRecordDateAndAccountTypeAndFundCode(LocalDate recordDate, String accountType, String fundCode)`。

3. **Job Service 匯入與歷史利率繼承**：
   - [SbiNisaJobServiceImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java)：
     - `saveOrMergeRecords()`：持股快照改呼叫 `nisaRepository.findByRecordDateAndAccountTypeAndFundCode(...)`；提繳設定改呼叫 `nisaSettingRepository.findByRecordDateAndAccountTypeAndFundCodeAndCourse(...)`。
     - `inheritHistoricalRate(NisaSnapshotRecord)`：改呼叫 `nisaRepository.findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(...)`。
     - `inheritHistoricalRate(NisaContributionSetting)`：改呼叫 `nisaSettingRepository.findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(...)`。

4. **模擬引擎配對接線**：
   - [NisaContextBuilder.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java)：
     - 在 `initializeNisaContext()` 中，持股與提繳設定的配對從 `s.getFundName().equals(record.getFundName())` 改為 `Objects.equals(s.getFundCode(), record.getFundCode()) && Objects.equals(s.getAccountType(), record.getAccountType())`。

5. **測試覆蓋與驗證**：
   - [NisaContributionSettingTest.java](file://finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java)：更新 `equals` 與 `hashCode` 測試，驗證以 `fundCode` 為主鍵、基金名稱變更不影響相等性。
   - [NisaSnapshotRecordTest.java](file://finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecordTest.java)：更新 `equals` 與 `hashCode` 測試，驗證相同 `fundCode` 但不同 `fundName` 判定為同一實體。
   - [NisaContextBuilderTest.java](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java)：新增單元測試 `shouldMatchSettingByFundCodeWhenFundNameDiffers`，驗證持股名稱若包含全形空白差異，仍能透過 `fundCode` 正確配對設定並帶入預期報酬率。
   - [SbiNisaJobServiceImplTest.java](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImplTest.java)：新增完整 Service 測試，驗證解析出不同基金名稱字串時，仍能藉由 `fundCode` 命中既有列進行 merge，且歷史報酬率繼承正常。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                                                      10017  14.37G   5.29    10:17:02  
diff --git a/.gitignore b/.gitignore
index 1598c883..289cf01a 100644
--- a/.gitignore
+++ b/.gitignore
@@ -77,3 +77,6 @@ target/
 
 # virtual machine crash logs
 hs_err_pid*
+
+# Runner
+*Runner.java
\ No newline at end of file
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaContributionSettingRepository.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaContributionSettingRepository.java
index d4222dea..039c0a17 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaContributionSettingRepository.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaContributionSettingRepository.java
@@ -41,23 +41,19 @@ public interface NisaContributionSettingRepository extends JpaRepository<NisaCon
 
     List<NisaContributionSetting> findAllByOrderByRecordDateDesc();
 
-    void deleteByRecordDate(LocalDate recordDate);
-
-    Optional<NisaContributionSetting> findFirstByCourseAndFundNameOrderByRecordDateDesc(String course, String fundName);
-
-    Optional<NisaContributionSetting> findFirstByAccountTypeAndFundNameOrderByRecordDateDesc(String accountType,
-            String fundName);
+    Optional<NisaContributionSetting> findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(String accountType,
+            String fundCode);
 
     /**
      * Finds a NISA contribution setting matching the unique business key for a
      * given date.
      *
-     * @param recordDate The record date.
+     * @param recordDate  The record date.
      * @param accountType The account type.
-     * @param fundName The fund name.
-     * @param course The course.
+     * @param fundCode    The official fund code (投信協会コード).
+     * @param course      The course.
      * @return The matching contribution setting, if any.
      */
-    Optional<NisaContributionSetting> findByRecordDateAndAccountTypeAndFundNameAndCourse(
-            LocalDate recordDate, String accountType, String fundName, String course);
+    Optional<NisaContributionSetting> findByRecordDateAndAccountTypeAndFundCodeAndCourse(
+            LocalDate recordDate, String accountType, String fundCode, String course);
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaSnapshotRecordRepository.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaSnapshotRecordRepository.java
index 68651877..b44b2279 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaSnapshotRecordRepository.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaSnapshotRecordRepository.java
@@ -28,9 +28,7 @@ import java.time.LocalDate;
 import java.util.List;
 import java.util.Optional;
 import org.springframework.data.jpa.repository.JpaRepository;
-import org.springframework.data.jpa.repository.Modifying;
 import org.springframework.stereotype.Repository;
-import org.springframework.transaction.annotation.Transactional;
 
 /**
  * Repository interface for managing NISA snapshot records.
@@ -38,16 +36,6 @@ import org.springframework.transaction.annotation.Transactional;
 @Repository
 public interface NisaSnapshotRecordRepository extends JpaRepository<NisaSnapshotRecord, Long> {
 
-    /**
-     * Deletes all records associated with a specific record date. Useful for
-     * overwriting an existing snapshot for the same date.
-     *
-     * @param recordDate The date to delete records for.
-     */
-    @Modifying
-    @Transactional
-    void deleteByRecordDate(LocalDate recordDate);
-
     /**
      * Finds all NISA snapshot records ordered by record date descending.
      *
@@ -63,14 +51,15 @@ public interface NisaSnapshotRecordRepository extends JpaRepository<NisaSnapshot
     Optional<NisaSnapshotRecord> findFirstByOrderByRecordDateDesc();
 
     /**
-     * Finds the most recent snapshot record for a specific fund and account
+     * Finds the most recent snapshot record for a specific fund code and account
      * type.
      *
      * @param accountType The account type.
-     * @param fundName The fund name.
+     * @param fundCode    The official fund code (投信協会コード).
      * @return The latest snapshot record for this fund, if any.
      */
-    Optional<NisaSnapshotRecord> findFirstByAccountTypeAndFundNameOrderByRecordDateDesc(String accountType, String fundName);
+    Optional<NisaSnapshotRecord> findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(String accountType,
+            String fundCode);
 
     /**
      * Finds all NISA snapshot records for a specific date.
@@ -84,10 +73,11 @@ public interface NisaSnapshotRecordRepository extends JpaRepository<NisaSnapshot
      * Finds a NISA snapshot record matching the unique business key for a given
      * date.
      *
-     * @param recordDate The record date.
+     * @param recordDate  The record date.
      * @param accountType The account type.
-     * @param fundName The fund name.
+     * @param fundCode    The official fund code (投信協会コード).
      * @return The matching snapshot record, if any.
      */
-    Optional<NisaSnapshotRecord> findByRecordDateAndAccountTypeAndFundName(LocalDate recordDate, String accountType, String fundName);
+    Optional<NisaSnapshotRecord> findByRecordDateAndAccountTypeAndFundCode(LocalDate recordDate, String accountType,
+            String fundCode);
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java
index b5b76a82..d02260ef 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java
@@ -157,7 +157,8 @@ public class SbiNisaJobServiceImpl implements JobExecutor, FileProcessingStrateg
             List<NisaSnapshotRecord> allRecords, List<NisaContributionSetting> allSettings,
             List<File> successFilesToMove, Map<String, String> failedFiles) {
         for (File file : files) {
-            try (FileInputStream fis = new FileInputStream(file); FileInputStream fisSettings = new FileInputStream(file)) {
+            try (FileInputStream fis = new FileInputStream(file);
+                    FileInputStream fisSettings = new FileInputStream(file)) {
 
                 List<NisaSnapshotRecord> parsedRecords = sbiNisaParser.parse(fis, commonRecordDate);
                 List<NisaContributionSetting> parsedSettings = sbiNisaParser.parseSettings(fisSettings,
@@ -250,7 +251,8 @@ public class SbiNisaJobServiceImpl implements JobExecutor, FileProcessingStrateg
     public FileProcessingResult processFile(File tempFile, boolean dryRun, String ownerUserId) throws Exception {
         logger.info("Processing single file: {}, dryRun: {}", tempFile.getName(), dryRun);
 
-        try (FileInputStream fis = new FileInputStream(tempFile); FileInputStream fisSettings = new FileInputStream(tempFile)) {
+        try (FileInputStream fis = new FileInputStream(tempFile);
+                FileInputStream fisSettings = new FileInputStream(tempFile)) {
             LocalDate recordDate = extractRecordDate(tempFile);
             List<NisaSnapshotRecord> parsedRecords = sbiNisaParser.parse(fis, recordDate);
             List<NisaContributionSetting> parsedSettings = sbiNisaParser.parseSettings(fisSettings, recordDate);
@@ -273,18 +275,18 @@ public class SbiNisaJobServiceImpl implements JobExecutor, FileProcessingStrateg
         if (records != null && !records.isEmpty()) {
             for (NisaSnapshotRecord incoming : records) {
                 Optional<NisaSnapshotRecord> existingOpt = nisaRepository
-                        .findByRecordDateAndAccountTypeAndFundName(
-                                incoming.getRecordDate(), incoming.getAccountType(), incoming.getFundName());
+                        .findByRecordDateAndAccountTypeAndFundCode(
+                                incoming.getRecordDate(), incoming.getAccountType(), incoming.getFundCode());
                 if (existingOpt.isPresent()) {
                     NisaSnapshotRecord existing = existingOpt.get();
                     existing.merge(incoming);
-                    logger.info("Updating existing NISA snapshot record for {} - {}", existing.getAccountType(),
-                            existing.getFundName());
+                    logger.info("Updating existing NISA snapshot record for {} - {} [{}]", existing.getAccountType(),
+                            existing.getFundName(), existing.getFundCode());
                     nisaRepository.save(existing);
                 } else {
                     inheritHistoricalRate(incoming);
-                    logger.info("Saving new NISA snapshot record for {} - {}", incoming.getAccountType(),
-                            incoming.getFundName());
+                    logger.info("Saving new NISA snapshot record for {} - {} [{}]", incoming.getAccountType(),
+                            incoming.getFundName(), incoming.getFundCode());
                     nisaRepository.save(incoming);
                 }
             }
@@ -293,19 +295,19 @@ public class SbiNisaJobServiceImpl implements JobExecutor, FileProcessingStrateg
         if (settings != null && !settings.isEmpty()) {
             for (NisaContributionSetting incoming : settings) {
                 Optional<NisaContributionSetting> existingOpt = nisaSettingRepository
-                        .findByRecordDateAndAccountTypeAndFundNameAndCourse(
-                                incoming.getRecordDate(), incoming.getAccountType(), incoming.getFundName(),
+                        .findByRecordDateAndAccountTypeAndFundCodeAndCourse(
+                                incoming.getRecordDate(), incoming.getAccountType(), incoming.getFundCode(),
                                 incoming.getCourse());
                 if (existingOpt.isPresent()) {
                     NisaContributionSetting existing = existingOpt.get();
                     existing.merge(incoming);
-                    logger.info("Updating existing NISA setting for {} - {} ({})", existing.getAccountType(),
-                            existing.getFundName(), existing.getCourse());
+                    logger.info("Updating existing NISA setting for {} - {} [{}] ({})", existing.getAccountType(),
+                            existing.getFundName(), existing.getFundCode(), existing.getCourse());
                     nisaSettingRepository.save(existing);
                 } else {
                     inheritHistoricalRate(incoming);
-                    logger.info("Saving new NISA setting for {} - {} ({})", incoming.getAccountType(),
-                            incoming.getFundName(), incoming.getCourse());
+                    logger.info("Saving new NISA setting for {} - {} [{}] ({})", incoming.getAccountType(),
+                            incoming.getFundName(), incoming.getFundCode(), incoming.getCourse());
                     nisaSettingRepository.save(incoming);
                 }
             }
@@ -314,8 +316,8 @@ public class SbiNisaJobServiceImpl implements JobExecutor, FileProcessingStrateg
 
     private void inheritHistoricalRate(NisaSnapshotRecord record) {
         Double rate = nisaRepository
-                .findFirstByAccountTypeAndFundNameOrderByRecordDateDesc(
-                        record.getAccountType(), record.getFundName())
+                .findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(
+                        record.getAccountType(), record.getFundCode())
                 .map(r -> r.getExpectedReturnRate())
                 .orElse(null);
         record.updateExpectedReturnRate(rate);
@@ -323,8 +325,8 @@ public class SbiNisaJobServiceImpl implements JobExecutor, FileProcessingStrateg
 
     private void inheritHistoricalRate(NisaContributionSetting setting) {
         Double rate = nisaSettingRepository
-                .findFirstByAccountTypeAndFundNameOrderByRecordDateDesc(
-                        setting.getAccountType(), setting.getFundName())
+                .findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(
+                        setting.getAccountType(), setting.getFundCode())
                 .map(s -> s.getExpectedReturnRate())
                 .orElse(null);
         setting.updateExpectedReturnRate(rate);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java
index 0b67cb2b..f49dcc26 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java
@@ -24,6 +24,7 @@
 package ch.lin.finance.hub.backend.api.app.service.simulation.core.factory;
 
 import java.util.ArrayList;
+import java.util.Objects;
 
 import org.springframework.stereotype.Component;
 
@@ -115,10 +116,10 @@ public class NisaContextBuilder {
             Long cmv = record.getCurrentMarketValue();
             long currentBalance = cmv != null ? cmv : 0L;
 
-            // Find contribution setting
+            // Find contribution setting by fundCode and accountType
             var setting = nisaSettings.stream()
-                    .filter(s -> s.getFundName().equals(record.getFundName())
-                            && s.getAccountType().equals(record.getAccountType()))
+                    .filter(s -> Objects.equals(s.getFundCode(), record.getFundCode())
+                            && Objects.equals(s.getAccountType(), record.getAccountType()))
                     .findFirst();
 
             long annualContribution = 0L;
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java
index 58b3b14e..b7826191 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java
@@ -171,4 +171,31 @@ class NisaContextBuilderTest {
         SimulationFund fund = context.getNisaFunds().get(0);
         assertThat(fund.getAnnualContribution()).isEqualTo(0L);
     }
+
+    @Test
+    @DisplayName("NisaContextBuilder should match setting by fundCode even when fundName differs slightly")
+    void shouldMatchSettingByFundCodeWhenFundNameDiffers() {
+        LocalDate today = LocalDate.now();
+        NisaSnapshotRecord record = new NisaSnapshotRecord(
+                today, "NISA (つみたて)預り", "0331418A", "eMAXIS Slim 全世界株式（オール・カントリー）　",
+                100.0, 10000.0, 12000.0, 1_000_000L, 1_200_000L, 200_000L);
+
+        NisaContributionSetting setting = new NisaContributionSetting(
+                today, "NISA (つみたて)預り", "0331418A", "eMAXIS Slim 全世界株式（オール・カントリー）",
+                "毎月", 50_000L, 50_000L);
+        setting.updateExpectedReturnRate(0.065);
+
+        when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
+        when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
+        when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(List.of(setting));
+        when(nisaUserSettingsRepository.findFirstBy()).thenReturn(Optional.empty());
+
+        SimulationContext context = new SimulationContext();
+        nisaContextBuilder.initializeNisaContext(context, config);
+
+        assertThat(context.getNisaFunds()).hasSize(1);
+        SimulationFund fund = context.getNisaFunds().get(0);
+        assertThat(fund.getAnnualContribution()).isEqualTo(600_000L);
+        assertThat(fund.getExpectedReturnRate()).isEqualTo(0.065);
+    }
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java
index 02867932..ef35dddb 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java
@@ -44,15 +44,15 @@ import lombok.NoArgsConstructor;
  */
 @Entity
 @Table(name = NisaContributionSetting.TABLE_NAME, indexes = {
-    @Index(name = NisaContributionSetting.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
+        @Index(name = NisaContributionSetting.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
 }, uniqueConstraints = {
-    @UniqueConstraint(columnNames = {MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
-        NisaContributionSetting.RECORD_DATE_COLUMN,
-        NisaContributionSetting.ACCOUNT_TYPE_COLUMN, NisaContributionSetting.FUND_NAME_COLUMN,
-        NisaContributionSetting.COURSE_COLUMN})
+        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
+                NisaContributionSetting.RECORD_DATE_COLUMN,
+                NisaContributionSetting.ACCOUNT_TYPE_COLUMN, NisaContributionSetting.FUND_CODE_COLUMN,
+                NisaContributionSetting.COURSE_COLUMN })
 })
 @Getter
-@EqualsAndHashCode(of = {"recordDate", "accountType", "fundName", "course"}, callSuper = false)
+@EqualsAndHashCode(of = { "recordDate", "accountType", "fundCode", "course" }, callSuper = false)
 @NoArgsConstructor(access = AccessLevel.PROTECTED)
 public class NisaContributionSetting extends MultiUserBaseEntity {
 
@@ -120,7 +120,8 @@ public class NisaContributionSetting extends MultiUserBaseEntity {
     @Column(name = "expected_return_rate")
     private Double expectedReturnRate;
 
-    public NisaContributionSetting(LocalDate recordDate, String accountType, String fundCode, String fundName, String course,
+    public NisaContributionSetting(LocalDate recordDate, String accountType, String fundCode, String fundName,
+            String course,
             Long settingAmount,
             Long monthlyEquivalentAmount) {
         this.recordDate = recordDate;
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java
index 6cfbaff5..64cb5e49 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java
@@ -44,14 +44,14 @@ import lombok.NoArgsConstructor;
  */
 @Entity
 @Table(name = NisaSnapshotRecord.TABLE_NAME, indexes = {
-    @Index(name = NisaSnapshotRecord.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
+        @Index(name = NisaSnapshotRecord.ID_INDEX, columnList = MultiUserBaseEntity.ID_COLUMN)
 }, uniqueConstraints = {
-    @UniqueConstraint(columnNames = {MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
-        NisaSnapshotRecord.RECORD_DATE_COLUMN, NisaSnapshotRecord.ACCOUNT_TYPE_COLUMN,
-        NisaSnapshotRecord.FUND_NAME_COLUMN})
+        @UniqueConstraint(columnNames = { MultiUserBaseEntity.OWNER_USER_ID_COLUMN,
+                NisaSnapshotRecord.RECORD_DATE_COLUMN, NisaSnapshotRecord.ACCOUNT_TYPE_COLUMN,
+                NisaSnapshotRecord.FUND_CODE_COLUMN })
 })
 @Getter
-@EqualsAndHashCode(of = {"recordDate", "accountType", "fundName"}, callSuper = false)
+@EqualsAndHashCode(of = { "recordDate", "accountType", "fundCode" }, callSuper = false)
 @NoArgsConstructor(access = AccessLevel.PROTECTED)
 public class NisaSnapshotRecord extends MultiUserBaseEntity {
 
@@ -179,7 +179,8 @@ public class NisaSnapshotRecord extends MultiUserBaseEntity {
 
     /**
      * Merges incoming snapshot record facts into this record:
-     * - Overwrites all parsed holding facts unconditionally using direct assignment.
+     * - Overwrites all parsed holding facts unconditionally using direct
+     * assignment.
      * - Strictly preserves user-defined fields (expectedReturnRate).
      *
      * @param incoming The parsed snapshot record containing updated facts.
diff --git a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java
index 6d1fe714..c16eaa0f 100644
--- a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java
+++ b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java
@@ -41,8 +41,7 @@ class NisaContributionSettingTest {
                 "eMAXIS Slim S&P 500",
                 "毎月",
                 50_000L,
-                50_000L
-        );
+                50_000L);
 
         assertEquals(date, setting.getRecordDate());
         assertEquals("NISA (成長)預り", setting.getAccountType());
@@ -70,8 +69,7 @@ class NisaContributionSettingTest {
                 "eMAXIS Slim S&P 500",
                 "毎月",
                 null,
-                null
-        );
+                null);
 
         assertNull(setting.getFundCode());
         assertEquals(0L, setting.getSettingAmount());
@@ -83,13 +81,18 @@ class NisaContributionSettingTest {
         LocalDate date = LocalDate.of(2025, 1, 1);
 
         NisaContributionSetting s1 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎月", 1000L, 1000L);
-        NisaContributionSetting s2 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎月", 2000L, 2000L);
-        NisaContributionSetting s3 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎日", 1000L, 20000L);
+        NisaContributionSetting s2 = new NisaContributionSetting(date, "NISA", "03311187", "FundA Renamed", "毎月", 2000L,
+                2000L);
+        NisaContributionSetting s3 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎日", 1000L,
+                20000L);
+        NisaContributionSetting s4 = new NisaContributionSetting(date, "NISA", "0331418A", "FundA", "毎月", 1000L, 1000L);
 
         assertEquals(s1, s2);
         assertNotEquals(s1, s3);
+        assertNotEquals(s1, s4);
         assertEquals(s1.hashCode(), s2.hashCode());
         assertNotEquals(s1.hashCode(), s3.hashCode());
+        assertNotEquals(s1.hashCode(), s4.hashCode());
     }
 
     @Test
diff --git a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecordTest.java b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecordTest.java
index 564003f3..d395ce4b 100644
--- a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecordTest.java
+++ b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecordTest.java
@@ -44,8 +44,7 @@ class NisaSnapshotRecordTest {
                 200.0,
                 15000L,
                 20000L,
-                5000L
-        );
+                5000L);
 
         assertEquals(date, record.getRecordDate());
         assertEquals("NISA", record.getAccountType());
@@ -62,7 +61,8 @@ class NisaSnapshotRecordTest {
     @Test
     void testConstructorWithNulls() {
         LocalDate date = LocalDate.of(2025, 1, 1);
-        NisaSnapshotRecord record = new NisaSnapshotRecord(date, "NISA", null, "S&P 500", null, null, null, null, null, null);
+        NisaSnapshotRecord record = new NisaSnapshotRecord(date, "NISA", null, "S&P 500", null, null, null, null, null,
+                null);
 
         assertNull(record.getFundCode());
         assertEquals(0.0, record.getUnits());
@@ -76,15 +76,22 @@ class NisaSnapshotRecordTest {
     @Test
     void testEqualsAndHashCode() {
         LocalDate date = LocalDate.of(2025, 1, 1);
-        
-        NisaSnapshotRecord r1 = new NisaSnapshotRecord(date, "NISA", "03311187", "S&P 500", null, null, null, null, null, null);
-        NisaSnapshotRecord r2 = new NisaSnapshotRecord(date, "NISA", "03311187", "S&P 500", null, null, null, null, null, null);
-        NisaSnapshotRecord r3 = new NisaSnapshotRecord(date, "NISA2", "03311187", "S&P 500", null, null, null, null, null, null);
-        
+
+        NisaSnapshotRecord r1 = new NisaSnapshotRecord(date, "NISA", "03311187", "S&P 500", null, null, null, null,
+                null, null);
+        NisaSnapshotRecord r2 = new NisaSnapshotRecord(date, "NISA", "03311187", "S&P 500 Renamed", null, null, null,
+                null, null, null);
+        NisaSnapshotRecord r3 = new NisaSnapshotRecord(date, "NISA2", "03311187", "S&P 500", null, null, null, null,
+                null, null);
+        NisaSnapshotRecord r4 = new NisaSnapshotRecord(date, "NISA", "0331418A", "S&P 500", null, null, null, null,
+                null, null);
+
         assertEquals(r1, r2);
         assertNotEquals(r1, r3);
+        assertNotEquals(r1, r4);
         assertEquals(r1.hashCode(), r2.hashCode());
         assertNotEquals(r1.hashCode(), r3.hashCode());
+        assertNotEquals(r1.hashCode(), r4.hashCode());
     }
 
     @Test
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                                                                                         10018  13.12G   8.35    10:28:39  