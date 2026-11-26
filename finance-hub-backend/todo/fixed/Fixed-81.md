# Fixed 81：NISA 匯入 Job 改成批次交易後，檔案搬移與 DB 交易提交順序不一致 + 同批次多檔案沒有真正合併成同一筆 recordDate

## 嚴重程度
Medium（需要「同批次多檔案」且「彙總交易失敗」或「批次內檔案實際日期不同」這類非典型情境才會觸發，但一旦觸發會造成資料庫與檔案系統狀態不一致，需要人工介入才能復原）

## 發現背景
本 issue 是在驗證 Fixed-75-01 Pre-work（修正 NISA 資料寫入與合併邏輯）是否正確實作時，額外發現的新問題。Pre-work 原本要解決的「同一批次處理多檔案時互相覆寫/誤刪」問題（把每個檔案各自的 transaction 改成迴圈跑完後、彙總一次 transaction 再寫入）已確認修正正確，但這個「改成批次寫入」的手法本身引入了另外兩個問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java:114-189`（`execute`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java:52-129, 106-115`（`parse` 如何把傳入的日期寫進每一筆紀錄）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/job/JobOrchestratorImpl.java:151-166`

## 前提（不是本次要討論的方向，先講清楚避免混淆）
這批檔案是分次 SSH 上傳的，實際存檔的日子可能不同天，但代表的是同一次盤點，理論上應該合併成同一筆快照日期。這件事本身是既定需求，以下兩個問題都不是在質疑這件事，而是指出現在的程式碼並沒有正確做到這件事。

## 現狀（已實際讀取現在的原始碼確認）

```java
List<NisaSnapshotRecord> allRecords = new ArrayList<>();
List<NisaContributionSetting> allSettings = new ArrayList<>();
LocalDate commonRecordDate = null;

for (File file : files) {
    try (FileInputStream fis = new FileInputStream(file);
            FileInputStream fisSettings = new FileInputStream(file)) {
        LocalDate recordDate = extractRecordDate(file); // 讀檔案的「最後修改時間」
        if (commonRecordDate == null) {
            commonRecordDate = recordDate;
        }
        List<NisaSnapshotRecord> parsedRecords = sbiNisaParser.parse(fis, recordDate); // 用的是每個檔案自己的日期
        List<NisaContributionSetting> parsedSettings = sbiNisaParser.parseSettings(fisSettings, recordDate);
        allRecords.addAll(parsedRecords);
        allSettings.addAll(parsedSettings);

        String year = String.valueOf(recordDate.getYear());
        if (!dryRun) {
            HtmlArchiveUtils.moveFileAndResources(file, importPath, "archive/success", year); // 逐檔案立即搬移
        }
        successFiles.add(file.getName());
    } catch (Exception e) {
        ...
    }
}

if (commonRecordDate != null) {
    applyExpectedReturnRates(allRecords, allSettings);
    if (!dryRun) {
        final LocalDate txDate = commonRecordDate; // 只取第一個處理到的檔案的日期
        transactionTemplate.execute(status -> {     // 迴圈結束後才彙總執行一次，沒有 try/catch
            if (!allRecords.isEmpty()) {
                nisaRepository.deleteByRecordDate(txDate);
                nisaRepository.saveAll(allRecords);
            }
            if (!allSettings.isEmpty()) {
                nisaSettingRepository.deleteByRecordDate(txDate);
                nisaSettingRepository.saveAll(allSettings);
            }
            return null;
        });
    }
}
```

### 問題一：檔案搬移發生在資料庫寫入「之前」，寫入失敗沒有任何補償動作
每處理完一個檔案就立刻把它搬到 `archive/success`，但真正把資料寫進資料庫的交易要等**整個迴圈跑完之後才執行一次**，且這段沒有包 try/catch。如果這次寫入失敗（例如撞到 DB unique constraint、或當下資料庫連線出問題），例外會被上層 `JobOrchestratorImpl.java:151-163` 的 catch 接住、標記整個 job 為 failed，但不會把已經搬走的檔案移回 `source`。

結果：job history 顯示「失敗」，但這批檔案其實都已經被搬到 `archive/success`、資料庫卻完全沒有寫入任何一筆——需要人工把檔案從 `archive/success` 移回 `source` 才能重新處理。

### 問題二：同批次多檔案沒有真正合併成同一筆日期
這裡有兩層問題：

1. **代表日期是誰決定的，不確定。** `commonRecordDate` 會被「這次迴圈第一個處理到的檔案」的日期定住（第137-139行），而「第一個處理到的檔案」是誰，取決於 `HtmlArchiveUtils.findHtmlFiles()` 用 `Files.walk()` 掃資料夾回傳的順序——不是依檔名、也不是依日期排序，是作業系統/檔案系統決定的，不保證每次都一樣。

2. **就算代表日期選對了，紀錄實際存的日期還是各自檔案自己的，不是代表日期。** `sbiNisaParser.parse(fis, recordDate)` 傳進去的 `recordDate` 是**這個檔案自己的**最後修改時間，不是 `commonRecordDate`。`SbiNisaParserImpl.java:106-115` 會把這個日期直接寫進每一筆 `NisaSnapshotRecord`。`commonRecordDate`／`txDate` 從頭到尾只被拿去做一件事：決定 `deleteByRecordDate` 要刪哪一天的舊資料。

   所以如果這批檔案裡真的有兩個不同的日期（例如分兩天 SSH 上傳）：檔案 A 解析出來的紀錄日期是第一天，檔案 B 解析出來的紀錄日期是第二天，迴圈跑完後卻只有一個日期會被拿去刪舊資料。結果資料庫裡最後會同時出現兩個日期的紀錄，不是「合併成一筆」；下次重新處理同一批檔案時，`deleteByRecordDate` 也只會清掉其中一個日期的舊資料，另一個日期的舊資料會留著、也不會被新資料蓋掉，等於悄悄留下重複或過期的資料。

## 建議修正
問題一跟問題二是同一個修正：要讓「合併成同一筆日期」真正生效，本來就得先看過整批檔案的日期才能開始解析，所以拆成兩個階段，各是一個 `for (File file : files)`，合計仍是 O(n)：

```java
// 第一階段：純掃描，只讀 mtime，不開檔案內容，不寫資料庫
LocalDate commonRecordDate = null;
for (File file : files) {
    LocalDate d = extractRecordDate(file);
    if (commonRecordDate == null || d.isAfter(commonRecordDate)) {
        commonRecordDate = d; // 取這批檔案裡最晚的日期，當作這批的代表日期
    }
}

// 第二階段：逐檔案解析、存檔、搬移，全部用同一個 commonRecordDate
// 兩張表（snapshot records / settings）各自獨立判斷「這批有沒有新資料、刪過沒」，不能共用同一個條件或同一個布林值，
// 否則會出現「這個檔案只有 settings、沒有 records」時，records 那個 flag 沒被觸發、settings 卻連帶把 records 表也刪了的誤刪風險（反之亦然）。
boolean deletedRecordsThisRun = false;
boolean deletedSettingsThisRun = false;
for (File file : files) {
    try (FileInputStream fis = new FileInputStream(file);
            FileInputStream fisSettings = new FileInputStream(file)) {
        List<NisaSnapshotRecord> parsedRecords = sbiNisaParser.parse(fis, commonRecordDate);
        List<NisaContributionSetting> parsedSettings = sbiNisaParser.parseSettings(fisSettings, commonRecordDate);

        if (!parsedRecords.isEmpty()) {
            if (!deletedRecordsThisRun) {
                nisaRepository.deleteByRecordDate(commonRecordDate); // 整批只刪這一次，且只在真的有新 records 時才刪
                deletedRecordsThisRun = true;
            }
            nisaRepository.saveAll(parsedRecords);
        }

        if (!parsedSettings.isEmpty()) {
            if (!deletedSettingsThisRun) {
                nisaSettingRepository.deleteByRecordDate(commonRecordDate); // 整批只刪這一次，且只在真的有新 settings 時才刪
                deletedSettingsThisRun = true;
            }
            nisaSettingRepository.saveAll(parsedSettings);
        }

        moveFileAndResources(file, ..., "archive/success", ...);
    } catch (Exception e) {
        moveFileAndResources(file, ..., "archive/failed", ...);
    }
}
```

關鍵點：
- 第一階段選最晚的日期，理由：存檔時不用去想檔名規則或帳戶類型，是最小改動；而且這個掃資料夾找 HTML 檔案的匯入方式之後會整個被棄用，現在只需要把這個小 bug 堵住。額外好處：如果資料夾裡混入上次沒清乾淨的舊檔案，舊檔案日期通常比較早，用「最晚」比用「最早」更不容易被舊檔案誤導。
- 第二階段的「解析、存檔、搬移」都在同一個檔案的迴圈裡完成，所以「檔案搬到哪裡」跟「資料庫有沒有真的寫入」永遠是同一件事的兩個結果，不會再出現「檔案都搬完了、DB 卻整批沒寫入」的情況。
- `deleteByRecordDate` 不能在第二階段一開始就無條件做一次：現在的程式碼本來就有 `if (!allRecords.isEmpty())` / `if (!allSettings.isEmpty())` 這兩個各自獨立的保護，確保「這批完全沒有新的 records（或完全沒有新的 settings）」時，不會去刪資料庫裡原本那張表的資料。所以改成「延遲到這張表第一次真的有新資料要存的那一刻才刪一次」，而且 records 跟 settings 要各自用自己的布林值（`deletedRecordsThisRun` / `deletedSettingsThisRun`）獨立判斷，不能共用一個條件——否則就會出現「這批只有 settings、沒有 records」時，records 表被牽連清空（或反過來，settings 表該清卻沒清）的誤刪風險。

修正後應新增測試：
1. 同一批次放兩個「最後修改時間」不同的檔案，跑完後資料庫裡只會出現一個日期（最晚的那個）的紀錄，不會同時出現兩個日期。
2. 整批檔案全部解析失敗時，資料庫裡原本的資料不會被清空。
3. 批次中有檔案解析失敗、也有檔案成功時，成功的檔案會被存進資料庫並搬到 `archive/success`，失敗的檔案搬到 `archive/failed`，兩者互不影響。
4. 批次中某個檔案解析出來只有 settings、沒有 snapshot records（或反過來），跑完後資料庫裡原本那個「這批完全沒有新資料」的表，不會被誤刪。

---

## 實作修復報告與架構調整 (Implementation Report)

經由深入分析與實作後，已確認本 Issue 描述之三大問題（代表日期隨機性、多日期混雜、搬檔與 DB 脫鉤）**完全正確**。

### 🚨 Claude 建議方案的嚴重缺陷
原先 Claude 所提出的「直接把 DB 寫入與搬移整合進同一個 `for` 迴圈」的改法，雖然表面上解決了搬檔順序問題，但因為**移除了 `transactionTemplate.execute(...)`**，導致發生兩個致命的邊際效應：
1. **破壞交易原子性 (Loss of Transaction Atomicity)**：若單一檔案寫入 DB 失敗，雖然檔案會被移至 `failed`，但上一行執行的 `deleteByRecordDate` 卻已經生效無法復原，導致原本使用者的舊資料被永遠刪除，新資料也沒有寫入。
2. **部分更新導致資料缺漏 (Partial Batch Updates)**：同批次檔案中，若 A 成功而 B 失敗，A 觸發的 `deleteByRecordDate` 會把 B 對應的舊資料一併清空，而 B 因為寫入失敗，導致系統中徹底遺失了 B 的資料。

### 💡 最終實作方案：三階段處理架構
為了徹底解決此問題並保證資料庫絕對安全，我們導入了「三階段處理」架構（參見 `SbiNisaJobServiceImpl.java` 的最新修改）：

1. **第一階段：統一代表日期**
   - 掃描整批檔案，找出最晚的修改日期作為 `commonRecordDate`。
2. **第二階段：純解析不動作**
   - 走訪檔案並統一寫入 `commonRecordDate`。解析成功的資料放進記憶體總清單 `allRecords` 中等待，檔案**暫不搬移**。
   - 解析失敗（格式錯誤）的檔案，直接搬移至 `archive/failed`。
3. **第三階段：單一交易寫入與成功確認**
   - 使用 `transactionTemplate.execute` 將總清單 `allRecords` 一次性安全寫入 DB。
   - **若寫入成功**：才將剛剛所有解析成功的檔案實體搬移至 `archive/success`。
   - **若寫入失敗（如 Key 衝突、斷線等）**：因交易保護機制，舊資料會完美 Rollback 不遺失。此時我們不允許部分成功（大家一起死），將原本排隊等待搬移的檔案全部移至 **`archive/db-error`** 資料夾。

透過獨立出 `archive/db-error`，未來即可明確區分「檔案解析失敗 (failed)」與「資料庫/系統層級異常 (db-error)」，大幅提升維護體驗與資料安全性。

### 🔧 附加重構：`execute` 方法職責分離
為了解決加入三階段處理後導致 `execute` 邏輯過長（God Method）的問題，我們額外進行了程式碼結構的重構，將三個階段抽取為獨立的私有輔助方法：
- `determineCommonRecordDate` (階段一：決定基準日期)
- `parseFiles` (階段二：解析檔案並隔離錯誤)
- `executeDatabaseTransaction` (階段三：執行資料庫交易)
- `handleFileMoving` (階段三：根據交易結果搬移檔案)

重構後，主流程 `execute` 變得非常簡潔，職責切割也更為明確，且並未改變任何核心商業邏輯。經過編譯與測試，確保程式碼純粹為結構優化，無任何副作用。
