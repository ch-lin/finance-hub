# Fixed-51: 實作 DC/iDeCo 年資覆寫資料一致性三層防護

這份計畫針對 Fixed-51 中所提出的「開關打開但數值為 null」的不一致狀態，實施三層防禦（Frontend、Service、DB），徹底杜絕髒資料進入系統，同時確保良好的使用者體驗（切換開關時保留既有數值）。

## 存檔行為規範 (Validation Rules)

對於 DC 與 iDeCo 各自獨立判斷：

- ✅ **允許存檔 (Allowed)**
  - 開關 = `false`，數值 = `任意值 (包含 null)`：允許存檔。系統會保留該欄位原有的數值不變（方便使用者下次打開開關時不用重填）。
  - 開關 = `true`，數值 = `有效數字`：允許存檔。
- ❌ **拒絕存檔 (Denied)**
  - 開關 = `true`，數值 = `null (或空字串)`：拒絕存檔。在前端顯示錯誤，若打 API 則回傳 400 Bad Request，若繞過則在 DB 層噴錯。

---

## Proposed Changes

### Frontend (UI 層防禦)

這層負責在使用者點擊 Save 時立刻擋下，並給予友善提示，避免發送無效的 API 請求。

#### [MODIFY] page.tsx
(位於 `hub-ui/hub-ui/src/app/finance/profile/me/page.tsx`)
在 `handleSave` 方法送出請求（`updateProfile`）之前，加入表單驗證：
- 如果 `formData.isDcContributionYearsOverridden` 是 `true`，但 `formData.dcContributionYearsOverride` 是空字串，則阻斷執行，並用 `toast.error` 提示使用者。
- iDeCo 執行相同的對稱驗證。

---

### Backend Service (API 商業邏輯層防禦)

這層是系統的守門員，負責擋下未經前端正確驗證直接發過來的 API 請求，並看清楚合併後的實體狀態。

#### [MODIFY] DefinedContributionUserSettingsServiceImpl.java
(位於 `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsServiceImpl.java`)
在 `saveSettings` 方法的末尾，也就是將新資料全部 `set` 進 `settings` 物件之後，呼叫 `repository.save()` 之前：
- 檢查 `settings.getIsDcContributionYearsOverridden() == true` 且 `settings.getDcContributionYearsOverride() == null`，若為真，拋出 `IllegalArgumentException`（Spring 會自動轉為 400 錯誤）。
- iDeCo 執行相同的對稱驗證。

---

### Backend Entity (DB Schema 層防禦)

這是最後的底線保險。即使未來有人修改了 Service 或加了新的入口，這層確保資料庫層級絕對無法寫入髒資料。

#### [MODIFY] DefinedContributionUserSettings.java
(位於 `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java`)
- 在 Class 類別層級加上 Hibernate 的 `@Check` 註解。
- 寫入 SQL 的 CHECK Constraint：`(is_dc_contribution_years_overridden = false OR dc_contribution_years_override IS NOT NULL) AND (is_ideco_contribution_years_overridden = false OR ideco_contribution_years_override IS NOT NULL)`。

## User Review Required
請確認以上的三層防護設計是否符合您的預期，若同意即可授權開始實作。
