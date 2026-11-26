# Fixed 57: 防止「生日」欄位被空值覆蓋

修復儲存個人資料時，如果未傳入 `birthDate` 導致使用者生日被清空，進而破壞所有後續退休模擬計算（以年齡為基礎）的風險。

## Proposed Changes

### Backend

#### [MODIFY] [UserProfileServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java)
- 在第 66 行的 `profile.setBirthDate(command.birthDate());` 加上 `if (command.birthDate() != null)` 保護。
- **嚴格遵守 Issue 限制**：絕對不會動到同個方法內的 `defaultCurrency`、`manualMonthlyBaseExpense`、`targetRetirementAge` 與 `pensionStartAge` 欄位，保留它們原本「可被清空回預設值」的行為。

## Verification Plan

### Manual Verification
- 由開發者在本地執行測試 `mvn clean test` 確保既有行為與編譯皆無損壞。
- 測試完成後會將修復報告補上 `Fixed-57.md` 的底部。
