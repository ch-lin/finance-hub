# 移除舊有過渡欄位 (Cleanup & Removal)

根據 Fixed-95 Phase 3 的規劃，我們將在後端系統中徹底拔除原本用於相容性過渡的 `@Deprecated` 單一 NISA 欄位 (`monthlyNisaContribution` 與 `testNisaMonthlyContribution`)。

## Proposed Changes

### Domain & Core Models

#### [MODIFY] [NisaUserSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaUserSettings.java)
- 刪除 `@Deprecated private Long monthlyNisaContribution;` 實體欄位。

#### [MODIFY] [SimulationOverrides.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)
- 刪除 `SimulationOverrides` 中的 `@Deprecated Long testNisaMonthlyContribution` 屬性。
- 刪除 `ProfileBaselineUsageDetails` 中的 `@Deprecated Long nisaMonthlyContribution` 屬性。

---

### Service DTOs (Commands & Results)

#### [MODIFY] [SaveNisaUserSettingsCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/SaveNisaUserSettingsCommand.java)
- 刪除 `@Deprecated Long monthlyNisaContribution` 屬性。

#### [MODIFY] [NisaUserSettingsResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaUserSettingsResult.java)
- 刪除 `@Deprecated Long monthlyNisaContribution` 屬性。

---

### Controller DTOs (Requests & Responses)

#### [MODIFY] [NisaUserSettingsRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsRequest.java)
- 刪除 `@Deprecated Long monthlyNisaContribution` 屬性。

#### [MODIFY] [NisaUserSettingsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsResponse.java)
- 刪除 `@Deprecated Long monthlyNisaContribution` 屬性。

#### [MODIFY] [AssetSimulationRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java)
- 刪除 `@Deprecated Long testNisaMonthlyContribution` 屬性。

#### [MODIFY] [SolveNisaGoalRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/command/SolveNisaGoalRequest.java)
- 刪除 `@Deprecated Long monthlyNisaContribution` 屬性。

---

### Mappers

#### [MODIFY] [ChromosomeMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java)
- 移除 mapping 到 `testNisaMonthlyContribution` 的 `null` 佔位符。

#### [MODIFY] [GlobalOptimizerMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java)
- 移除從 `SimulationOverrides` 與 `ProfileBaselineUsageDetails` 讀取並轉換舊欄位的邏輯。

## Verification Plan
1. 修改完成後，由於拔除了大量屬性，將使用 IDE 的編譯器檢查機制找出所有因為刪除欄位而編譯失敗的測試案例，並進行修復。
2. 確保修復所有 `AssetSimulationEngineImplTest` 或是 Mapper 測試中所傳遞的無效屬性。
