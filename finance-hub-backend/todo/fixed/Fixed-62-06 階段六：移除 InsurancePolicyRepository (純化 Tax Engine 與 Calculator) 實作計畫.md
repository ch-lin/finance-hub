# 階段六：移除 InsurancePolicyRepository (純化 Tax Engine 與 Calculator) 實作計畫

本次實作旨在貫徹「計算機純化（Pure Function）」原則，將 `ActiveTaxCalculationEngine` 與 `IncomeExpenseCalculator` 中對資料庫（`InsurancePolicyRepository`）的直接相依性拔除。保單的抓取與 DTO 轉換將移至模擬起點 `SimulationContextFactory` 一次性完成，確保計算機的輸入全數由 `SimulationContext` 供給。

## User Review Required

> [!IMPORTANT]
> - 本次重構會影響現有核心計算機的依賴注入（DI），請確認是否同意修改 `SimulationContextFactory` 以及各計算機與相關測試的 Mock 設定。
> - `InsurancePolicyDto` 將被設計為不可變（Immutable）的 Java Record（或 `@Value`），因此在 `SimulationContext.clone()` 時，只需要淺層複製 List 即可。

## Proposed Changes

---

### Core Models

#### [NEW] `InsurancePolicyDto.java`
- **路徑**：`/Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/InsurancePolicyDto.java`
- **內容**：建立唯讀的 DTO，包含 `policyType`, `annualPremium`, 與 `endDate` 欄位。

#### [MODIFY] `SimulationContext.java`
- **路徑**：`/Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java`
- **變更**：
  - 新增屬性 `private List<InsurancePolicyDto> insurancePolicies = new ArrayList<>();`。
  - 修改 `clone()` 方法，確保對 `insurancePolicies` 進行 List 淺拷貝以避免物件參照污染。

---

### Simulation Factory

#### [MODIFY] `SimulationContextFactory.java`
- **路徑**：`/Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java`
- **變更**：
  - 注入 `InsurancePolicyRepository`。
  - 在 `createContext()` 方法中呼叫 `insurancePolicyRepository.findAll()`，將取回的 Entity 轉換為 `InsurancePolicyDto` 後存入 `SimulationContext`。

---

### Calculators / Engines

#### [MODIFY] `ActiveTaxCalculationEngine.java`
- **路徑**：`/Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java`
- **變更**：
  - 從 `@RequiredArgsConstructor` 中移除 `InsurancePolicyRepository`。
  - 修改保費計算邏輯，從 `context.getInsurancePolicies()` 讀取保單資料進行計算。

#### [MODIFY] `IncomeExpenseCalculator.java`
- **路徑**：`/Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java`
- **變更**：
  - 從 `@RequiredArgsConstructor` 中移除 `InsurancePolicyRepository`。
  - 在 `calculateExpensesForYear` 迴圈中，改由 `context.getInsurancePolicies()` 讀取保單資料。

---

### Unit Tests

#### [MODIFY] `SimulationContextFactoryTest.java`
- 補充 `InsurancePolicyRepository` 的 mock，並驗證 `SimulationContext` 內成功載入了 `InsurancePolicyDto`。

#### [MODIFY] `ActiveTaxCalculationEngineTest.java`
- 拔除原先針對 `InsurancePolicyRepository` 的 mock。
- 在 Setup 階段，改為直接塞假資料到 `SimulationContext` 裡的 `insurancePolicies` 列表。

#### [MODIFY] `AssetSimulationEngineImplTest.java`
- 此處可能也會將 `InsurancePolicyRepository` 注入至被測試的 Engine 或 Calculator 中，一併移除並修正相關 DI 傳參。

## Verification Plan

### Automated Tests
- 將由開發者在 IDE 中手動執行相關的 Unit Tests：
  - `ActiveTaxCalculationEngineTest`
  - `IncomeExpenseCalculatorTest` (若有)
  - `SimulationContextFactoryTest`
  - `AssetSimulationEngineImplTest`

### Manual Verification
- 確保編譯成功，所有的 Spring Beans DI 都順利接上，不再報 `UnsatisfiedDependencyException`。

# Phase 6: Simulation Engine Dependency Refactoring & DI Update

The simulation engine dependency refactoring and DI update have been successfully completed according to Phase 6 specifications.

## What Was Accomplished

- **Model & Factory Refactoring**:
  - Created a read-only `InsurancePolicyDto`.
  - Added `insurancePolicies` list to `SimulationContext` and implemented shallow cloning.
  - Injected `InsurancePolicyRepository` into `SimulationContextFactory` to map entity data to DTOs during simulation setup.
- **Engine Purification**:
  - Removed `InsurancePolicyRepository` from `ActiveTaxCalculationEngine` and `IncomeExpenseCalculator`. Both calculation engines now solely rely on `SimulationContext` for policy data.
- **Test Updates**:
  - Removed repository mocks from `ActiveTaxCalculationEngineTest` and `AssetSimulationEngineImplTest`.
  - Added repository mock to `SimulationContextFactoryTest`.

## Verification (User Action Required)

根據您在實作計畫中的指示 (`手動跑`)，請您在 IDE 中手動執行並驗證以下測試，以確保所有 Spring Beans DI 都順利接上，並且計算邏輯正確：
- `ActiveTaxCalculationEngineTest`
- `SimulationContextFactoryTest`
- `AssetSimulationEngineImplTest`
- `IncomeExpenseCalculatorTest` (如果有)

如果在執行測試時有任何問題，請讓我知道，我會進行修正。
