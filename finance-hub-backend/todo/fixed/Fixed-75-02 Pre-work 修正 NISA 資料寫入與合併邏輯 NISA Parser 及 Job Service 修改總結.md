# NISA Parser 及 Job Service 修改總結

我們已經完成了針對 Fixed-75 的 Pre-work（底層資料結構與解析邏輯修復），主要修改了四個檔案：

## 1. 資料模型層 (`NisaContributionSetting.java`)
- 新增了 `accountType` 欄位（對應資料表欄位 `account_type`）。
- 重新定義 `@UniqueConstraint` 與 `@EqualsAndHashCode`，將 `account_type` 加入為唯一鍵的一部分，確保同一基金在「成長投資」與「積立投資」的設定能夠獨立並存。

## 2. 爬蟲解析層 (`SbiNisaParserImpl.java`)
- 在 `parseSettings()` 邏輯中，加入了針對 `[data-testid='accountType_PC']` 的抓取。
- 將抓取到的 `accountType` 正確傳遞給 `NisaContributionSetting`，使每筆積立設定都能夠知道自己屬於哪個投資枠。

## 3. 排程寫入層 (`SbiNisaJobServiceImpl.java`)
- 修正了原本「每個檔案單獨處理並直接清空當日資料再儲存」的危險邏輯。
- 現在會先利用 `allRecords` 與 `allSettings` 兩個 List，在迴圈內**集中收集**所有檔案解析出的資料。
- 收集完畢後，再使用 `!allRecords.isEmpty()` 與 `!allSettings.isEmpty()` 進行防呆檢查。只有在解析出該實體的資料時，才去執行該實體的 `deleteByRecordDate` 與 `saveAll`，完美避免「單獨上傳設定檔卻誤刪 Snapshot」的問題。

## 4. 前端資料聚合層 (`use-nisa-data.ts`)
- 前端對應新增了 `accountType` 屬性。
- 在 `settingsByKey` 的合併邏輯中，將原本單純依賴 `fundName` 的配對方式，改為 `accountType + fundName` 的複合鍵，確保畫面上能正確對應到對應投資枠的積立設定。

---

接下來，只要你的前端與後端重新啟動，且 Hibernate 成功更新 Schema 後，就可以放心地處理之後的 Fixed-75（模擬器邏輯）了！
