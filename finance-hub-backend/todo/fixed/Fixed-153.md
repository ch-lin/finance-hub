# [FIXED] Issue 153：年金PDF解析器把「匯入當天」當成資料日期，不是看文件本身記載的日期，導致重新匯入無法正確覆蓋、也可能讓舊資料蓋過新資料

## 這份issue怎麼被發現的
這是這次Source Code Review針對年金解析邏輯進行商業邏輯審查時發現的問題。這是解析後的資料處理邏輯，不是對PDF版面的猜測。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/parser/NenkinPdfParser.java:52`：`LocalDate recordDate = LocalDate.now();`——完全不去看PDF內容裡有沒有記載日期，直接固定用「現在這一刻」
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionJobServiceImpl.java:99-101,134-137`（`deleteByRecordDate(record.getRecordDate())`再`save`，這個「先刪後存」的設計本來就是為了讓重新匯入能夠正確覆蓋舊資料）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/PensionContextBuilder.java:47-49`（依`recordDate`排序、取最新一筆當作模擬起點）
- 對照：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/`底下DC/iDeCo的解析器，已經有專門的`extractRecordDate(Document, fallbackDate)`機制去處理這個問題（出自[Fixed-60](../fixed/Fixed-60%20Fix%20DC%20Parser%20Future%20Date%20Bug.md)）

## 現狀
跟DC/iDeCo解析器不同，年金PDF解析器完全沒有嘗試從PDF文字內容裡抓出文件本身記載的基準日期，一律用「這個匯入job實際執行的那一天」當作`recordDate`。

## 為何是問題
1. **重新匯入無法正確覆蓋舊資料**：`PensionJobServiceImpl`用「先刪除同一個`recordDate`的舊紀錄、再存新紀錄」的方式來確保重新匯入同一份文件時能正確覆蓋（這也符合這個專案一貫的「事實類欄位重新匯入要覆蓋、不要退回舊值」原則）。但因為`recordDate`是匯入當天、不是文件本身的日期，同一份年金通知書如果在不同天重新匯入兩次（例如第一次匯入失敗後重試、或是修正解析邏輯後重新跑一次），兩次會產生兩個不同的`recordDate`，刪除動作完全刪不到舊的那一筆，結果變成資料庫裡多了一筆重複紀錄，不是正確覆蓋。
2. **「挑最新一筆」可能挑到實際上比較舊的資料**：如果使用者先匯入了一份比較新的年金通知書，之後才想起來要補匯入一份更早之前、一直忘記上傳的舊通知書，舊通知書因為是「比較晚匯入」，`recordDate`反而比較晚，排序時會被誤判成「最新資料」，整個模擬的年金起算基礎就會被這份其實比較舊的通知書資料蓋過去，沒有任何警示。
3. **讓「資料新鮮度」提示功能失去意義**：這個專案已經確立「不幫使用者補算到現在，而是老實顯示這筆資料距今多久」的設計原則（[Fixed-132](../fixed/Fixed-132.md)/[Fixed-105](../fixed/Fixed-105-%20%E9%99%A4%E4%BA%86%E6%88%BF%E8%B2%B8%E4%BB%A5%E5%A4%96%EF%BC%8C%E9%8A%80%E8%A1%8C%E7%8F%BE%E9%87%91%E3%80%81NISA%E3%80%81DC%EF%BC%8FiDeCo%E3%80%81%E5%85%AC%E5%8F%B8%E8%82%A1%E7%A8%AE%E3%80%81%E5%B9%B4%E9%87%91%E7%9A%84%E6%A8%A1%E6%93%AC%E8%B5%B7%E9%BB%9E%EF%BC%8C%E4%B8%80%E5%BE%8B%E7%9B%B4%E6%8E%A5%E6%8E%A1%E4%BF%A1%E6%9C%80%E6%96%B0%E4%B8%80%E6%AC%A1%E5%8C%AF%E5%85%A5%E7%9A%84%E6%95%B8%E5%AD%97%EF%BC%8C%E5%AE%8C%E5%85%A8%E6%B2%92%E6%9C%89%E8%80%83%E6%85%AE%E9%80%99%E7%AD%86%E8%B3%87%E6%96%99%E5%AF%A6%E9%9A%9B%E4%B8%8A%E6%98%AF%E5%A4%9A%E4%B9%85%E4%BB%A5%E5%89%8D%E7%9A%84.md)）——但這個原則的前提是`recordDate`要真實反映資料的時間點，年金這裡的`recordDate`永遠等於「今天」，這個新鮮度提示對年金資料完全失去意義，不是沒做，是這個欄位本身的意義已經被架空了。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：從PDF內容裡抓出文件本身記載的基準日期**
- 做法：仿照DC/iDeCo已經有的`extractRecordDate`模式，去解析ねんきんネット/年金見込額通知書上實際記載的查詢基準日。
- 優點：直接解決根本原因。
- 缺點：這次審查沒有實際的年金PDF樣本可以確認文件裡是否真的有可以抓取的日期欄位、位置在哪裡——這部分需要實際樣本才能動手，不應該憑空猜測版面。

**方向B：退回使用檔案的最後修改時間**
- 做法：仿照`DefinedContributionJobServiceImpl.java:197-205`已經在用的做法，用上傳檔案本身的最後修改時間當作`recordDate`。
- 優點：不需要新增任何解析風險，直接重用已經驗證過的既有模式。
- 缺點：檔案最後修改時間仍然不是文件本身真正的查詢基準日（反映的是下載時間，不是文件日期），無法完全解決跨天重新匯入的覆蓋問題。

**方向C：維持現狀，記錄為已知限制**
- 理由：如果使用者本人匯入年金PDF的頻率很低、通常同一天內完成，實際踩到這個落差的機率可能不高，可以先記錄下來。
- 缺點：目前完全沒有任何畫面提示會警告使用者這件事，一旦真的踩到，使用者不會意識到問題出在這裡。

---

## 實體樣本檢視與方向 A 具體落實方案（採納方向 A）

### 1. 實體樣本驗證結果
經檢視年金定期便及被保険者記錄照會回答票，確認內部記載有明確的日期欄位：

1. **データ作成日（資料基準日，首選）**：
   - **位置**：定期便第 2 頁右下方說明區塊與表格。
   - **文字與數值**：
     > 「納付記録がデータに反映されるまで日数がかかることがあります。この定期便は、下記データ作成日時点の前々月までの記録をお知らせしています。」  
     > 【データ作成日】 `令和 7  11  25`（換算西元為 **2025-11-25**）
   - **語意**：這是日本年金機構彙整並試算該份定期便加入月數（93月）、老齡年金金額的真正基準日（前々月記錄）。應優先以此作為年金紀錄的真實 `recordDate`。
2. **現在の加入記録（被保険者記錄照會基準日）**：
   - **語意**：回答票記載之「現在の加入記録」基準日（如 `令和 8 6 5` = **2026-06-05**）。
3. **ダウンロード日（下載/交付日，備援）**：
   - **位置**：定期便第 1 頁 QR Code 旁。
   - **語意**：使用者自「ねんきんネット」下載 PDF 的時間（如 `令和 8 6 7` = **2026-06-07**）。

---

### 2. 具體架構與落實規劃

#### A. PDF 解析器邏輯（`NenkinPdfParser`）
仿照 DC/iDeCo 解析機制，加入 `extractRecordDate` 階層式日期判斷：
1. **第一優先（首選資料基準日：データ作成日）**：搜尋「データ作成日」區塊，將和曆轉換為西曆 LocalDate（西元年 = 2018 + 令和年，並支援「元」年）。以 600 字元非貪婪視窗容納 PDF 排版中免責說明與日期數值之間的間距。
2. **第二優先（被保険者記録基準日）**：搜尋「現在の加入記録」標籤，支援日期位於標籤之前或之後（支援跨行與表格排版）。
3. **第三優先（PDF 下載日）**：搜尋「ダウンロード日」日期。
4. **第四優先（檔案屬性 Fallback）**：若 PDF 內容均無法萃取出有效日期，退回讀取檔案的最後修改時間（`Files.getLastModifiedTime`）。（遵循 Fixed-60 原則，嚴格杜絕無上下文語意錨點的全頁日期盲猜，避免將使用者的個人生日或加退保歷程日誤判為基準日）。
5. **第五優先（兜底 Fallback）**：僅在上述皆不可得且無檔案物件時使用 `LocalDate.now()`。

#### B. Domain 與資料庫設計
- 目前 `PensionRecord` 實體已具備 `@NotNull private LocalDate recordDate` 欄位作為快照基準日，主鍵/唯一約束亦為 `(owner_user_id, record_date)`。
- 由 PDF 解析器產生的紀錄以解析出的「データ作成日」作為 `recordDate`，即可達成：
  - 重複匯入同份 PDF 時，`recordDate` 恆定，觸發 `deleteByRecordDate` 正確覆蓋。
  - 補匯入歷史資料時，排序不會錯置。
  - 資料新鮮度指標可如實反映距今時間。

---

## 實作成果報告

### 一、 實作完成項目
1. **年金 PDF 日期解析器實作（`NenkinPdfParser.java`）**：
   - 移除原先寫死的 `LocalDate recordDate = LocalDate.now();`。
   - 實作結構化階層式 `extractRecordDate(text, pdfFile)`：
     - **第 1 優先**：精確匹配「データ作成日」（正確解析定期便之 `2025-11-25`）。
     - **第 2 優先**：雙向匹配被保険者記錄照會「現在の加入記録」基準日（正確解析 `2026-06-05`）。
     - **第 3 優先**：匹配「ダウンロード日」（`2026-06-07`）。
     - **第 4 優先**：退回讀取檔案最後修改時間（`Files.getLastModifiedTime`）。
     - **第 5 優先**：`LocalDate.now()` 兜底。
   - 支援和曆「元」年換算（如令和元年 = 2019）。
   - 遵循 Fixed-60 防禦性設計原則，僅比對具備明確標籤的日期，杜絕無錨點的盲猜比對。
2. **單元測試驗證覆蓋（`NenkinPdfParserTest.java`）**：
   - 建立完備、隔離的純單元測試集，完全不依賴外部檔案或環境：
     - `testExtractRecordDateFromDataCreationDate`：驗證排版長間距之「データ作成日」（2025-11-25）。
     - `testExtractRecordDateFromDownloadDate`：驗證「ダウンロード日」（2026-06-07）。
     - `testExtractRecordDateFromCurrentRecordAfter` / `Before`：驗證「現在の加入記録」雙向排版解析（2026-06-05）。
     - `testExtractRecordDateFromEraGannen`：驗證和曆「元」年解析（2019-05-01）。
     - `testExtractRecordDateFallbackToFileModifiedTime`：驗證無內文標籤時安全退回檔案修改時間。
     - `testExtractRecordDateFallbackToNowWhenNoFile`：驗證無檔案時之兜底。
     - `testNoArbitraryDateMatchingWithoutContext`：驗證遭遇生日與加退保歷程日等非基準日資料時絕不誤抓。

### 二、 風險揭露與後續交接
1. **歷史髒資料清理建議**：
   - 下游 `PensionContextBuilder` 與 `PensionProjectionServiceImpl` 均依 `recordDate DESC` 挑選最新一筆年金紀錄。
   - 若本機資料庫在本次修復前曾匯入過帶有舊 parser 寫入之 `now()` 歷史測試紀錄，修復後匯入的真實資料（`2025-11-25`）在日期排序上會排在其後。建議清理測試環境資料庫之舊紀錄。
2. **範疇界定與分流**：
   - 本次實作嚴格聚焦於修復 Issue 153 之「日期來源與覆蓋時序」問題。
   - 國民年金月數未解析與厚生年金月數重複賦值問題，已妥善分流於 [Issue 154](issue-154.md)。