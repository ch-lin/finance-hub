# Fixed 98-01: 修復年金預估邏輯與命名一致性 (Pension Projection Calculation Fix)

這份計畫書詳細記錄了針對年金預估邏輯錯誤 (Fixed-98) 的修復方案，並包含了後續發現的 Spring Boot 設定檔與資料庫欄位命名不一致的問題。

## 背景與問題描述 (Background)

1. **商業邏輯錯誤 (Business Logic Error)**:
   在 `PensionProjectionServiceImpl` 第 151 行，系統在計算終身預估年金總額時，使用了 `retirementAge` (目標退休年齡) 而不是 `pensionStartAge` (請領年金年齡) 作為起點。這導致在使用者提早退休但晚點領年金的情況下，年金總額會被嚴重高估。
   - 錯誤算式：`LifetimeTotal = 每年年金 * (壽命 - 退休年齡)`
   - 正確算式：`LifetimeTotal = 每年年金 * (壽命 - 請領年金年齡)`

2. **設定檔無效問題 (Invalid Properties Configuration)**:
   由於早期資料庫欄位命名 (`pension_eligible_age`) 與 Java Domain Model (`pensionStartAge`) 不一致，導致在 `application.properties` 中撰寫的 `finance.hub.system-config.pension-eligible-age` 完全無法被 Spring Boot 讀取，系統僅能依賴程式碼中寫死的 `65` 作為 Fallback，這也是測試環境一直無法抓出上述邏輯錯誤的原因。

## User Review Required

(已確認) 無痛改名資料庫欄位 (pension_eligible_age -> pension_start_age) 已獲授權。

## Proposed Changes

---

### Pension Projection Business Logic

修正錯誤的商業邏輯，並修改單元測試，確保退休年齡與請領年齡不同時，預估依然正確。

#### [MODIFY] [PensionProjectionServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java)
- 第 151 行，將 `int yearsInRetirement = Math.max(0, lifeExpectancy - retirementAge);` 修改為 `Math.max(0, lifeExpectancy - pensionStartAge);`。

#### [MODIFY] [PensionProjectionServiceTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java)
- 將 `calculateProjection_withExistingRecord_shouldCalculateCorrectly` 測試案例中，Mock 的 `targetRetirementAge` 從 `65` 改為 `60`，保持 `pensionStartAge` 為 `65`。
- 確保測試能夠在兩者不同的情況下，正確驗證 `LifetimeTotal` 的計算基礎（85 - 65 = 20年）。

---

### Database Schema & Properties Renaming

統一所有地方的命名為 `pensionStartAge` / `pension_start_age`。

#### [MODIFY] [FinanceHubConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java)
- 將 `PENSION_START_AGE_COLUMN` 常數的值由 `pension_eligible_age` 改為 `pension_start_age`。

#### [MODIFY] [UserProfile.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java)
- 將 `PENSION_START_AGE_COLUMN` 常數的值由 `pension_eligible_age` 改為 `pension_start_age`。

#### [MODIFY] [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)
- 將 `finance.hub.system-config.pension-eligible-age` 改為 `finance.hub.system-config.pension-start-age`。

---

### Documentation & Reports

#### [MODIFY] [issue-27.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending fix/issue-27.md)
- 附加最終的修正報告與發現，並標記狀態為完成。

## Verification Plan

### Automated Tests
- 執行 `mvn clean install` 確保所有單元測試皆能通過。
- 重點觀察 `PensionProjectionServiceTest`，這將證明商業邏輯已經正確分離了「退休年齡」與「請領年金年齡」。
