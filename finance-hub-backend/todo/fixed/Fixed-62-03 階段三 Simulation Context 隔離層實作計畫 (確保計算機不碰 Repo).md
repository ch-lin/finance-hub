# Fixed-62 Phase 3: Simulation Context 隔離層實作計畫 (確保計算機不碰 Repo)

此計畫將針對 `ActiveTaxCalculationEngine` 與 `DependentDeductionEngine` 建立隔離層，確保模擬引擎只接觸唯讀的 DTO，避免在模擬迴圈中呼叫資料庫，符合 DDD 原則並確保效能。

> [!IMPORTANT]
> 此計畫遵循您的要求，將相關類別放置於 `SimulationContext` 所屬的 `simulation.core` 模組下。
> `TaxConfigDto` 與 `TaxBracketDto` 為不可變（Immutable）資料載體，方便計算機純函數化使用。

## User Review Required

無特別需要確認的業務邏輯，主要是架構位置與資料夾分類的確認。

## Open Questions

無。

## Proposed Changes

---

### Simulation Core Models (DTO)

建立不可變的 DTO 以供 Simulation Context 與 Engine 內部使用。

#### [NEW] [TaxConfigDto.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)
- 使用 `@Value` (Lombok) 宣告為不可變物件。
- 包含三個純量屬性：
  - `Long basicDeductionIncomeTax;`
  - `Long basicDeductionHabitantTax;`
  - `Long dependentIncomeThreshold;`
- 依據 `TaxBracketType` 先分類好三個 List：
  - `List<TaxBracketDto> employmentIncomeDeductionBrackets;`
  - `List<TaxBracketDto> incomeTaxRateBrackets;`
  - `List<TaxBracketDto> habitantTaxRateBrackets;`

#### [NEW] [TaxBracketDto.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxBracketDto.java)
- 使用 `@Value` 宣告，包含：
  - `Long maxSalary;`
  - `BigDecimal multiplier;`
  - `Long offset;`

---

### Simulation Context (Model)

更新既有的 `SimulationContext` 來容納新的稅務設定。

#### [MODIFY] [SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- 加入 `private TaxConfigDto taxConfig;` 屬性。
- 因為 `TaxConfigDto` 被設計為完全不可變，因此在 `clone()` 方法中只需進行 shallow copy 即可（與 `config` 屬性相同）。

---

### Simulation Factory

負責在初始化時從外部 Service (不會進入 Transaction 迴圈) 撈取資料並轉型。

#### [MODIFY] [SimulationContextFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)
- 注入 `TaxRuleConfigService`。
- 在 `createContext()` 中，呼叫 `taxRuleConfigService.getAllConfigs().getEnabledConfigName()` 取得當前啟用的稅制名稱，並拉取 `TaxRuleConfigResult`。
- 撰寫 Mapping 邏輯，將 `TaxRuleConfigResult` 的資料映射進 `TaxConfigDto`，並使用 stream 根據 `bracketType` 分流轉換為 `TaxBracketDto` 的 List。
- 最後將組裝好的 `TaxConfigDto` 塞進 `context.setTaxConfig(...)` 中。

---

## Verification Plan

### Automated Tests
- 新增/更新 `SimulationContextFactoryTest`：Mock `TaxRuleConfigService` 確保會正確回傳設定，並驗證 `SimulationContext` 初始化後，內含的 `taxConfig` 的各項參數與級距是否有正確分類綁定。
- **(強制規定：所有測試撰寫完成後，絕對不會自動使用命令列執行，將由您親自手動執行與驗證)**

### Manual Verification
- 跑完編譯 (`mvn clean install`) 確認沒有依賴問題，所有舊有的 Context 測試依然能通過。


# Walkthrough: Fixed-62 Phase 3 (Simulation Context 隔離層)

我已經按照計畫完成了 Phase 3 的實作，確保稅務引擎運作在純函數模式下，完全不接觸 Repository。

## 完成項目

### 1. 建立 Simulation Core Models (DTO)
- [TaxConfigDto.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxConfigDto.java)
- [TaxBracketDto.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/tax/TaxBracketDto.java)
  已將上述 DTO 實作為不可變的 `@Value` 類別，並且先一步針對不同的 `TaxBracketType` 設計好三個不同的 List，供未來引擎的動態 `for` 迴圈使用。

### 2. 更新 SimulationContext
- [SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
  加入 `taxConfig` 屬性，供後續 `ActiveTaxCalculationEngine` 讀取稅率設定。

### 3. 實作 SimulationContextFactory 的隔離與轉換
- [SimulationContextFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)
  - 注入 `TaxRuleConfigService`，在每次建立模擬上下文時，呼叫服務層去撈出「當前啟用的設定（Enabled Config）」。
  - 透過 `Stream` 的 `filter` 將級距表分類為薪資所得扣除、所得稅率、住民稅率，封裝為 `TaxConfigDto`。

### 4. 撰寫單元測試
- [SimulationContextFactoryTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)
  建立測試來驗證 Factory 正確將 Service 層回傳的 Result 結構轉換為 Context 內的 DTO，確保分類沒有錯置。

---

## 驗證計畫

> [!IMPORTANT]
> 遵照「嚴禁自動執行命令列」以及「由您親自測試」的規定，請您執行下列指令來確保沒有編譯錯誤，並且單元測試能通過：
> 
> ```bash
> mvn clean install
> ```
