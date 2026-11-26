# Fixed-96-01 Fix: Phase 1 (Domain 與 DTO 層擴充)

此計畫為落實 `Fixed-96.md` 藍圖的第一階段，專注於擴充與替換 Domain Entity 以及 Controller/Service/Engine 的邊界傳遞物件 (DTO/Command/Result)，將原本單一的 NISA 投入金額，拆分為「成長投資枠 (Growth)」與「積立枠 (Tsumitate)」雙欄位。

> [!IMPORTANT]
> 根據藍圖指示，**必須保留舊有單一欄位並加上 `@Deprecated` 標籤**，這對於維持後續 Optimizer (Fixed-95) 實作前的向後相容性至關重要。

## Proposed Changes

---

### Domain (Entity)

#### [MODIFY] [NisaUserSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaUserSettings.java)
- 保留 `monthlyNisaContribution` 屬性，並加上 `@Deprecated` 標註（包含其對應的 `@Column(name = "monthly_nisa_contribution")`）。
- 新增 `monthlyGrowthContribution = 0L`，對應 `@Column(name = "monthly_growth_contribution")`。
- 新增 `monthlyTsumitateContribution = 0L`，對應 `@Column(name = "monthly_tsumitate_contribution")`。
- 修改 Getter/Setter 支援新欄位。
- *註記：目前使用 H2 In-Memory DB，每次都會重建 Schema，因此不需建立任何 Flyway Migration 腳本。*

---

### API Layer (Controller DTOs)

#### [MODIFY] [NisaUserSettingsRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsRequest.java)
- 在 `monthlyNisaContribution` 加上 `@Deprecated`。
- 新增 `monthlyGrowthContribution` 與 `monthlyTsumitateContribution`。

#### [MODIFY] [NisaUserSettingsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsResponse.java)
- 在 `monthlyNisaContribution` 加上 `@Deprecated`。
- 新增 `monthlyGrowthContribution` 與 `monthlyTsumitateContribution`。

#### [MODIFY] [AssetSimulationRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java)
- 在 `Long testNisaMonthlyContribution` 加上 `@Deprecated`。
- 新增 `Long testNisaGrowthMonthlyContribution` 與 `Long testNisaTsumitateMonthlyContribution`。

---

### Service Layer (Command & Result)

#### [MODIFY] [SaveNisaUserSettingsCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/SaveNisaUserSettingsCommand.java)
- 在 `monthlyNisaContribution` 加上 `@Deprecated`。
- 新增 `monthlyGrowthContribution` 與 `monthlyTsumitateContribution`。

#### [MODIFY] [NisaUserSettingsResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaUserSettingsResult.java)
- 在 `monthlyNisaContribution` 加上 `@Deprecated`。
- 新增 `monthlyGrowthContribution` 與 `monthlyTsumitateContribution` 屬性。
- 修改 `fromEntity(...)` 方法，從 `entity` 讀取並 mapping 這兩個新屬性。

---

### Engine Boundary (Overrides)

#### [MODIFY] [SimulationOverrides.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)
- 替換外層 `SimulationOverrides` Record：將 `testNisaMonthlyContribution` 標記為 `@Deprecated`，並新增 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution`。
- 替換內層 `ProfileBaselineUsageDetails` Record：將 `nisaMonthlyContribution` 標記為 `@Deprecated`，並新增 `nisaGrowthMonthlyContribution` 與 `nisaTsumitateMonthlyContribution`。
- 修改 `anyUsed()` 的判斷邏輯，將前述兩個新欄位一併納入是否不為 null 的判斷。

## Verification Plan

### 自動化測試 (Automated Tests)
> [!WARNING]
> **預期內的編譯錯誤 (Expected Compile Errors)**
> Phase 1 僅修改了資料結構（包含 Java Record 的建構子參數增加），**專案必定會處於無法編譯成功的狀態**。
> 因為 MapStruct 的 Mapper 介面（如 `NisaUserSettingsMapper`）以及所有呼叫 Record 建構子的測試、實作檔，都尚未提供新增的兩個參數。這將統一在後續的 Phase 2 (Service/Controller) 與 Phase 3 (Engine) 修復。

- 使用者將負責手動執行 `mvn test` 或透過 IDE 來驗證編譯狀態與單元測試。
- 本階段不主動執行任何修正測試的動作，直到完成所有 Phase 結構修改。

# Fixed-96-01 Fix: Phase 1 (Domain 與 DTO 層擴充) 實作總結

我已經完成了 Fixed-96 第一階段的資料結構擴充任務。

## 完成的變更

- **[MODIFY]** [`NisaUserSettings.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaUserSettings.java)
  - 新增了 `monthlyGrowthContribution` 與 `monthlyTsumitateContribution`。
  - 將既有的 `monthlyNisaContribution` 標記為 `@Deprecated`。
- **[MODIFY]** [`NisaUserSettingsRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsRequest.java) & [`NisaUserSettingsResponse.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsResponse.java)
  - 在 API 請求與回應的 payload 中增加了成長與積立欄位，並將舊欄位標記棄用。
- **[MODIFY]** [`AssetSimulationRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java)
  - 擴充了 Sandbox 測試使用的 Request，支援雙欄位輸入。
- **[MODIFY]** [`SaveNisaUserSettingsCommand.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/SaveNisaUserSettingsCommand.java) & [`NisaUserSettingsResult.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaUserSettingsResult.java)
  - 擴充了 Service 層的通訊物件，並更新了從 Entity Mapping 的邏輯。
- **[MODIFY]** [`SimulationOverrides.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)
  - 在外層 Override 與內層 `ProfileBaselineUsageDetails` 都新增了雙欄位，並擴充了 `anyUsed()` 的判斷條件。

> [!WARNING]
> **專案目前無法編譯 (Expected State)**
> 由於我們剛剛修改了多個 Java Record 的建構子參數 (如 `SimulationOverrides` 等)，但尚未修改呼叫端 (Service, Mapper, Controller, Engine, Tests)，所以如果您現在執行 `mvn compile` 或 `mvn test` 必定會出現編譯錯誤。
> 
> 這是架構重構的正常過渡狀態，所有錯誤都會在接下來的 Phase 2 與 Phase 3 中被逐一收斂修復。

目前處於等待狀態，請指示是否可以準備進入 Phase 2 (Service 與 Controller 層)。
