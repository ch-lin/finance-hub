# Fix DC Parser Future Date Bug

這是一個針對 Defined Contribution Parser 抓取到未來日期 (如 9/25, 9/11) 導致模擬器資料為 0 的修正計畫。這是我在先前修改時遺漏的全域驗證邏輯，導致 SBI 與 Sompo 解析器都有抓到錯誤日期的缺陷，我將負起責任進行徹底的修復。

## 發現與確認 (SBI iDeCo)
沒錯，正如你所懷疑的，**SBI iDeCo 也有一模一樣的問題**！
- `SbiIdecoMemberInfoParserImpl` 完全沒有覆寫 `extractRecordDate`。
- `SbiIdecoStatusParserImpl` 與 `SbiIdecoAllocationParserImpl` 雖然有嘗試從特定的 `div.headLv2-txt h2` 抓日期，但在迴圈的最後一行，它們都寫了 `return DefinedContributionParser.super.extractRecordDate(doc, fallbackDate);` 作為 fallback。
- 這意味著，一旦它們在特定的 Title 裡找不到日期，就會直接掉進父介面的「全頁掃描最大日期」陷阱中。

## 發生原因核心
1. `DefinedContributionParser` 介面的預設 `extractRecordDate` 實作會掃描整份 HTML 的日期，並**取最大值 (maxDate)**。
2. 只要網頁裡有「下次扣款日」或是任何未來的排程日期，都會被抓成該 Snapshot 的建立日期。

## Proposed Changes

與其一個個去修補各個 Parser，最治本的方法是直接把 `DefinedContributionParser` 裡危險的預設行為拔除，並統一各個實作的 Fallback 邏輯。

### Base Interface

#### [MODIFY] [DefinedContributionParser.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionParser.java)
- **變更內容**：完全移除 `extractRecordDate` 裡的 `Pattern` 全頁掃描與尋找 `maxDate` 邏輯。
- **具體實作**：將預設行為改為直接 `return fallbackDate;`。這表示當實作沒有覆寫，或是特定節點找不到日期時，我們只信任安全且合理的 `fallbackDate`（通常是檔案被匯入或建立的時間點）。

### Sompo Parsers

#### [MODIFY] [SompoDcMemberInfoParserImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcMemberInfoParserImpl.java)
- **變更內容**：原本未覆寫 `extractRecordDate`，導致會去呼叫 Base Interface。現在加入該方法。
- **具體實作**：實作 `extractRecordDate`，不掃描全文，而是只回傳 `fallbackDate`，因為 Member Info 頁面並沒有準確的快照基準日，依賴檔案日期最安全。

#### [MODIFY] [SompoDcStatusParserImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcStatusParserImpl.java)
- **變更內容**：將最後的 Fallback 移除對父類別危險方法的依賴。
- **具體實作**：尋找 `div.titleDate span`，如果解析失敗，不再呼叫 `DefinedContributionParser.super.extractRecordDate(doc, fallbackDate)`，而是直接 `return fallbackDate;`。

#### [MODIFY] [SompoDcAllocationParserImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)
- **變更內容**：將最後的 Fallback 移除對父類別危險方法的依賴。
- **具體實作**：尋找 `div.titleDate span`，如果解析失敗，不再呼叫 `DefinedContributionParser.super.extractRecordDate(doc, fallbackDate)`，而是直接 `return fallbackDate;`。

### SBI Parsers

#### [MODIFY] [SbiIdecoMemberInfoParserImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoMemberInfoParserImpl.java)
- **變更內容**：原本未覆寫 `extractRecordDate`。現在加入該方法。
- **具體實作**：實作 `extractRecordDate`，直接 `return fallbackDate;` 作為基準，避免進行任何容易出錯的 HTML 日期推測。

#### [MODIFY] [SbiIdecoStatusParserImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoStatusParserImpl.java)
- **變更內容**：將最後的 Fallback 移除對父類別危險方法的依賴。
- **具體實作**：尋找 `div.headLv2-txt h2`，如果未找到或解析失敗，原本是 `return DefinedContributionParser.super.extractRecordDate(doc, fallbackDate);`，現在改為 `return fallbackDate;`。

#### [MODIFY] [SbiIdecoAllocationParserImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoAllocationParserImpl.java)
- **變更內容**：將最後的 Fallback 移除對父類別危險方法的依賴。
- **具體實作**：尋找 `div.headLv2-txt h2`，如果未找到或解析失敗，原本是 `return DefinedContributionParser.super.extractRecordDate(doc, fallbackDate);`，現在改為 `return fallbackDate;`。

## Verification Plan
1. 程式碼修改完成後，請先在本地執行 `mvn test` 或確保服務可正常編譯。
2. 啟動服務後，使用者需要自行將資料庫中錯誤的 9/25, 9/11 等紀錄刪除（或是透過檔案夾重新觸發 Parser 進行資料覆寫）。
3. 重新檢視 UI 模擬器，確認 DC / iDeCo 的帳戶價值數字正確呈現，不會因為未來日期的空紀錄而變成 0。
