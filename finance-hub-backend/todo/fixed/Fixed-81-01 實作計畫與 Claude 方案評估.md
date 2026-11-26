# Fixed 81-01 實作計畫與 Claude 方案評估

## Claude 方案評估（有嚴重的邊際效應）

經過仔細分析原始碼與 Claude 提出的「建議修正」，我確認 Claude 指出的**問題原因（幻覺驗證）是完全正確的**：
1. **代表日期隨機性**：`commonRecordDate` 目前是由 `Files.walk()` 第一個碰到的檔案決定，確實不穩定。
2. **多日期混雜**：解析時傳入的是每個檔案自己的 `recordDate`，導致 DB 內可能混雜多個日期，但最後 `deleteByRecordDate` 卻只清掉其中一天。
3. **搬檔與 DB 脫鉤**：檔案搬移在 DB 寫入之前發生，DB 失敗時檔案已搬到 `success`。

然而，**Claude 提出的「建議修正」程式碼有嚴重的邊際效應，絕對不可直接套用**。

### 🚨 可疑之處與新邊際效應（Critical Flaws in Claude's Code）

1. **破壞了資料庫交易的原子性 (Loss of Transaction Atomicity)**
   - Claude 的程式碼拔除了 `transactionTemplate.execute(...)`，直接在迴圈內呼叫 `nisaRepository.deleteByRecordDate(...)` 和 `saveAll(...)`。
   - 因為這兩個動作不再包在同一個 Transaction 內，如果 `deleteByRecordDate` 成功執行，但緊接著的 `saveAll` 因為資料庫 constraint 或連線問題拋出 Exception，**舊資料已經被刪除了，但新資料沒有存進去**，會造成嚴重的資料遺失。
2. **批次處理變為部分成功 (Partial Batch Updates)**
   - 假設同批次有檔案 A 與檔案 B。檔案 A 解析成功並觸發了 `deleteByRecordDate`，接著存入檔案 A 的資料。
   - 如果檔案 B 解析失敗（拋出 Exception），檔案 B 的資料不會寫入。但因為檔案 A 已經把該日期的**所有舊資料**都刪除了，這會導致原本應該被檔案 B 覆寫的舊資料憑空消失，且檔案 B 的新資料也沒進來。
   - 這完全違背了 Fixed-75-01 要把「同批次檔案彙總成單一 Transaction」的初衷。

## 💡 我的建議方案 (Proposed Solution)

為了解決問題一與問題二，同時保留 Fixed-75-01 修正的 Transaction 原子性，我們應該分為三個階段：

1. **第一階段：掃描代表日期** (與 Claude 相同)
   - 走訪所有檔案，找出最晚的 `lastModifiedTime` 作為整批的 `commonRecordDate`。
2. **第二階段：解析所有檔案（但不搬移、不寫DB）**
   - 走訪所有檔案，統一傳入 `commonRecordDate` 進行解析。
   - 將解析成功的資料暫存在記憶體 (`allRecords`, `allSettings`)。
   - 記錄哪些檔案解析成功、哪些失敗。
3. **第三階段：單一 Transaction 寫入與後續搬移**
   - 如果有解析成功的資料，使用 `transactionTemplate.execute(...)` 包覆 `deleteByRecordDate` 與 `saveAll`，確保原子性。
   - **關鍵**：
     - 若 Transaction **成功**，才將解析成功的檔案搬移到 `archive/success`，解析失敗的搬到 `archive/failed`。
     - 若 Transaction **失敗** (Exception)，則將這批「原本解析成功，但未能寫入 DB」的檔案全部搬移到 `archive/db-error`。這樣可以明確區分「檔案格式錯誤 (failed)」與「系統/資料庫異常 (db-error)」，且不會讓檔案一直卡在 source 資料夾中。

### 為什麼遇到 Key 衝突時必須「整批失敗 (大家一起死)」？
若同批次檔案中含有重複的資料（例如兩份檔案有同一支基金，導致觸發 Unique Constraint 衝突），我們的設計是**整批 Rollback 退件，不允許部分成功**。
這是刻意追求的**交易原子性 (Transaction Atomicity)**：
- **防範資料缺漏**：如果允許部分寫入，後面的檔案會因為衝突被丟棄，但前面成功的檔案已經觸發了 `deleteByRecordDate`，這會導致資料庫中原本無辜的舊資料被誤刪，且系統中只留下不完整的片斷資料。
- **確保資料一致性**：當這批檔案代表「同一次盤點快照」時，它們是生命共同體。整批退件並將檔案移至 `archive/db-error`，能明確警告使用者或開發者「這批檔案之間有資料重疊或矛盾」，保障資料庫的絕對一致性。

---

## 實作範圍 (Scope Lock)

### [MODIFY] `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java`
- 修改 `execute` 方法，導入上述的三階段處理邏輯。
- 移除迴圈內提早搬移檔案的程式碼。
- 確保只有在 `transactionTemplate.execute` 成功後，才執行檔案搬移到 `archive/success`。
- 若 `transactionTemplate.execute` 失敗，將相關檔案搬移至 `archive/db-error`。

## 使用者審查 (User Review Required)

- 對於 DB 寫入失敗時的處置，已依照您的建議，將其搬移至 `archive/db-error`。這個命名是否滿意？
- 若無其他問題，請指示「Approve」或「Proceed」，我將開始動手修改程式碼。
