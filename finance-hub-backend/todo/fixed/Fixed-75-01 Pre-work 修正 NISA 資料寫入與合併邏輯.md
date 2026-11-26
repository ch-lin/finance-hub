# 修正 NISA 資料寫入與合併邏輯 (Fixed 75-01 Pre-work)

這份計畫專注於底層資料與解析邏輯的修復，作為後續解決 Fixed-75 的前置作業。為確保單次 Commit 範圍不過度膨脹，**模擬引擎與模擬器前端介面的比對邏輯修正將會拆分到下一次獨立處理**。

為了解決「同名基金在不同預り区分（如成長與積立）下，積立設定的金額配對錯誤」以及「`SbiNisaJobServiceImpl` 迴圈複寫資料」的問題，我們將進行以下修改。

## Open Questions

> [!NOTE]
> 關於你提到的：「比如說使用者只上傳了一個？會不會掛？」
> 
> 如果使用者只上傳了「積立設定」的 HTML，目前的程式會去嘗試解析出 Snapshot（得到 0 筆），然後**無差別地呼叫 `deleteByRecordDate` 並存入 0 筆 Snapshot**，導致前一天或之前上傳的 Snapshot 被誤刪清空！這就是現在邏輯最危險的地方。
> 
> **修正計畫：** 
> 為了避免「單獨上傳某一種檔案卻把另一種資料清空」的慘劇，我們在 `SbiNisaJobServiceImpl` 會這樣改：
> 1. 在迴圈中收集所有檔案的 `parsedRecords` 與 `parsedSettings`。
> 2. **加入防呆判斷**：只有當收集到的 `parsedRecords` 數量大於 0 時，才去執行 Snapshot 的 `deleteByRecordDate` 與 `saveAll`。
> 3. 同理，只有當 `parsedSettings` 數量大於 0 時，才去執行 Setting 的 `deleteByRecordDate` 與 `saveAll`。
> 這樣一來，如果使用者今天只上傳了「積立設定」的網頁，就只會更新 Setting 資料，完全不會去動到原本已經存好的 Snapshot 資料。

## Proposed Changes

---

### Backend: Domain Model

#### [MODIFY] `NisaContributionSetting.java`
- 新增 `String accountType` 欄位與對應的 `@Column(name = "account_type")`。
- 修改 `@UniqueConstraint`，將 `ACCOUNT_TYPE_COLUMN` 加入。
- 更新建構子（Constructor）與 `EqualsAndHashCode` 以包含 `accountType`。

---

### Backend: Parser & Service

#### [MODIFY] `SbiNisaParserImpl.java`
- 在 `parseSettings()` 方法中，增加擷取 `[data-testid='accountType_PC']` 的邏輯。
- 將取得的 `accountType` 傳入 `NisaContributionSetting` 的建構子。

#### [MODIFY] `SbiNisaJobServiceImpl.java`
- 將原本在 `for (File file : files)` 迴圈**內部**的 `transactionTemplate.execute(...)` 移到迴圈**外部**。
- 在迴圈內部使用兩個 `List` 收集所有檔案解析出來的 `NisaSnapshotRecord` 和 `NisaContributionSetting`。
- 收集完畢後，再開啟 Transaction：
  - 若 `allRecords.size() > 0` 才執行 `nisaRepository` 的 delete 與 saveAll。
  - 若 `allSettings.size() > 0` 才執行 `nisaSettingRepository` 的 delete 與 saveAll。

---

### Frontend: UI Data Hooks

#### [MODIFY] `use-nisa-data.ts`
- 修改前端的 `NisaContributionSetting` 介面，加入 `accountType` 屬性。
- 將 `snapshotRecords.map(...)` 內尋找配對 setting 的邏輯，從原本只比對 `s.fundName === r.fundName`，改為：
  `s.fundName === r.fundName && s.accountType === r.accountType`。

## Verification Plan

### Manual Verification
1. 啟動後端應用程式。
2. 放入最新的 SBI 積立設定與 Snapshot HTML 檔案到資料夾。
3. 等待排程（或觸發）執行，觀察 Console Log 確認 Parser 有正確取得 `NISA (成長)` 等 accountType，且 Job Service 保存了**正確數量的兩方資料**，沒有互相覆蓋。
4. 打開前端 UI，確認 `NISA (成長)預り` 和 `NISA (つみたて)預り` 的積立金額（monthlyEquivalentAmount）現在能夠正確分離並顯示。
