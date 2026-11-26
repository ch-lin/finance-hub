# Fixed-77-01: 移除冗餘 onboardingCompleted 與清理不當的 Null 判斷

本計畫旨在解決兩個核心架構問題：
1. **移除冗餘狀態**：既然「Profile 存在即代表完成 Onboarding」，我們將徹底從 API 與實體中拔除高風險的 `onboardingCompleted` 欄位。
2. **忠實反映使用者意圖**：移除 Service 中不當的 `if != null` 防禦性編程與硬編碼預設值（如 85 歲）。若 Payload 傳入 null，將允許直接覆蓋並清空 DB 欄位，把預設值的處理職責交還給下游的模擬引擎，確保使用者能真正清空自己的設定。

## User Review Required

無破壞性變更。前端將改以「是否有取得 Profile」來判斷 onboarding 狀態。

## Open Questions

目前 `UserProfile` entity 對應了 DB 的 `onboarding_completed` 欄位。我們會在 Java 程式碼中將此屬性刪除，但如果您有使用 Flyway / Liquibase 等 DB 遷移工具，後續可能需要手動補一個 `DROP COLUMN` 的 SQL 腳本。

## Proposed Changes

### Backend

#### [MODIFY] [SaveUserProfileCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveUserProfileCommand.java)
- 從 `record` 中移除 `Boolean onboardingCompleted` 欄位。

#### [MODIFY] [UserProfileResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/result/UserProfileResult.java)
- 從 `record` 中移除 `Boolean onboardingCompleted` 欄位。
- 從 `fromEntity()` 映射方法中移除對該欄位的讀取。

#### [MODIFY] [UserProfile.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java)
- 刪除 `onboardingCompleted` 成員變數及其 `@Column` 標註。
- 刪除 Constructor 裡的 `this.onboardingCompleted = false;` 初始化。

#### [MODIFY] [UserProfileServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java)
- **拔除 Onboarding 邏輯**：刪除所有與 `command.onboardingCompleted()` 及 `profile.setOnboardingCompleted()` 相關的程式碼。
- **清理不當 Null 判斷**：
  改為直接賦值，允許前端傳入 `null` 來清空資料。
  ```java
  // 修改前
  Integer le = command.lifeExpectancy();
  profile.setLifeExpectancy(le != null ? le : 85);
  
  if (command.taxAdvantagedWithdrawalAge() != null) {
      profile.setTaxAdvantagedWithdrawalAge(command.taxAdvantagedWithdrawalAge());
  }

  // 修改後
  profile.setLifeExpectancy(command.lifeExpectancy());
  profile.setTaxAdvantagedWithdrawalAge(command.taxAdvantagedWithdrawalAge());
  ```

---

### Frontend

#### [MODIFY] [use-finance-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/hooks/use-finance-profile.ts)
- 將 `isOnboarded: data?.onboardingCompleted === true,` 改為 `isOnboarded: !!data,`。
- 在 `updateProfile` 中的 `handleSubmit` API payload 確保不再傳遞該欄位（原本就沒有傳遞的話則無需修改）。

#### [MODIFY] [onboarding/page.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/onboarding/page.tsx)
- 在 `handleSubmit` 裡呼叫 `updateProfile` 時，移除最後硬塞的 `onboardingCompleted: true` 屬性。

## Verification Plan

### Manual Verification
- 檢視後端邏輯，確認沒有多餘的 `if != null` 檢查。
- 測試前端 UI，確認成功登入後，即便沒有 `onboardingCompleted` 也能正常進入儀表板 (Dashboard/Profile)，不會卡在 Onboarding 頁面。
