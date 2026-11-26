# Fixed-81-02 續篇：重構 `SbiNisaJobServiceImpl` 的 `execute` 方法

目前的 `execute(boolean dryRun)` 方法因為加入了三階段處理的邏輯，已經變成一個龐大且難以閱讀的「上帝方法 (God Method)」。我們將透過抽取 private helper methods 的方式來重構，讓主流程變得乾淨好讀。

## 使用者審查確認

請確認以下即將抽取的輔助方法設計。確認後，我將立即開始重構程式碼。

## 提議的變更

### [元件] ch.lin.finance.hub.backend.api.app.service.accounting.nisa.sbi

#### [MODIFY] [SbiNisaJobServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java)

我們將把 `execute` 方法內的三個階段抽取為以下四個私有輔助方法 (private helper methods)：

1. **`determineCommonRecordDate`** (第一階段：決定基準日期)
   ```java
   private LocalDate determineCommonRecordDate(List<File> files)
   ```

2. **`parseFiles`** (第二階段：解析檔案)
   ```java
   private void parseFiles(List<File> files, LocalDate commonRecordDate, boolean dryRun,
                           List<NisaSnapshotRecord> allRecords,
                           List<NisaContributionSetting> allSettings,
                           List<File> successFilesToMove,
                           Map<String, String> failedFiles)
   ```

3. **`executeDatabaseTransaction`** (第三階段：資料庫交易)
   ```java
   private boolean executeDatabaseTransaction(LocalDate commonRecordDate,
                                              List<NisaSnapshotRecord> allRecords,
                                              List<NisaContributionSetting> allSettings,
                                              boolean dryRun)
   ```

4. **`handleFileMoving`** (第三階段：處理檔案搬移)
   ```java
   private void handleFileMoving(boolean dbSuccess, boolean dryRun, LocalDate commonRecordDate,
                                 List<File> successFilesToMove,
                                 Map<String, String> failedFiles,
                                 List<String> successFileNames)
   ```

重構後，原本的 `execute` 方法本身將會變成一個簡潔的流程控制器 (Orchestrator)，僅負責依序呼叫這四個方法。

## 驗證計畫

### 自動化測試
- **由使用者手動執行**：請使用者協助執行現有的 NISA Job 相關單元測試，確認重構（純結構調整）後並未改變任何原有邏輯。

### 人工驗證
- 透過 IDE Diff 檢閱程式碼，確保邏輯完整且沒有遺漏。
