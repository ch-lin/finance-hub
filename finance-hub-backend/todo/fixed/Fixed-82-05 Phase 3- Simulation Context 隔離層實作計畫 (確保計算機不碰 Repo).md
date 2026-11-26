# Fixed-82-05 Phase 3: Simulation Context 隔離層實作計畫 (確保計算機不碰 Repo)

此計畫將針對 `PensionEngine` 建立隔離層，確保模擬引擎只接觸唯讀的 DTO，避免在模擬迴圈中呼叫資料庫，符合 DDD 原則並確保效能。

> [!IMPORTANT]
> 此計畫遵循您的要求，將相關類別放置於 `SimulationContext` 所屬的 `simulation.core` 模組下。
> `PensionConfigDto` 為不可變（Immutable）資料載體，方便計算機純函數化使用。

## User Review Required

無特別需要確認的業務邏輯，主要是架構位置與資料夾分類的確認。

## Open Questions

無。

## Proposed Changes

---

### Simulation Core Models (DTO)

建立不可變的 DTO 以供 Simulation Context 與 Engine 內部使用。

#### [NEW] [PensionConfigDto.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/pension/PensionConfigDto.java)
- 使用 `@Value` (Lombok) 宣告為不可變物件。
- 包含純量屬性：
  - `Long maxBasicPensionAmount;` (從 `PensionRuleConfigResult` 映射而來)
  - `Long maxBasicPensionMonths;`
  - `BigDecimal kouseiMultiplierPerMille;`
  - `Long kouseiSalaryCapMonthly;`
  - `Long kouseiBonusCapPerPayment;`

---

### Simulation Context (Model)

更新既有的 `SimulationContext` 來容納新的年金設定。

#### [MODIFY] [SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- 加入 `private PensionConfigDto pensionConfig;` 屬性。
- 因為 `PensionConfigDto` 被設計為完全不可變，因此在 `clone()` 方法中只需進行 shallow copy 即可（與 `config` 屬性相同）。

---

### Simulation Factory

負責在初始化時從外部 Service (不會進入 Transaction 迴圈) 撈取資料並轉型。

#### [MODIFY] [SimulationContextFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)
- 注入 `PensionRuleConfigService`。
- 在 `createContext()` 中，呼叫 `pensionRuleConfigService.getAllConfigs().getEnabledConfigName()` 取得當前啟用的年金設定名稱，並拉取 `PensionRuleConfigResult`。
- 撰寫 Mapping 邏輯，將 `PensionRuleConfigResult` 的資料映射進 `PensionConfigDto`。
- 最後將組裝好的 `PensionConfigDto` 塞進 `context.setPensionConfig(...)` 中。

#### [MODIFY] [SimulationContextFactoryTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)
- Mock `PensionRuleConfigService` 確保會正確回傳設定，並驗證 `SimulationContext` 初始化後，內含的 `pensionConfig` 各項參數都有正確對應。

---

## Verification Plan

### Automated Tests
- 新增/更新 `SimulationContextFactoryTest`。
- **(強制規定：所有測試撰寫完成後，絕對不會自動使用命令列執行，將由您親自手動執行與驗證)**

### Manual Verification
- 跑完編譯 (`mvn clean install`) 確認沒有依賴問題，所有舊有的 Context 測試依然能通過。

# Walkthrough: Fixed-82-05 Phase 3 (Simulation Context 隔離層)

我已經按照計畫完成了 Phase 3 的實作，確保年金引擎 (`PensionEngine`) 運作在純函數模式下，完全不接觸 Repository，遵守隔離與 DDD 原則。

## 完成項目

### 1. 建立 Simulation Core Models (DTO)
- [PensionConfigDto.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/pension/PensionConfigDto.java)
  已建立不可變的 `@Value` 類別，用以存放從外部拉取的 `maxBasicPensionAmount`、`maxBasicPensionMonths`、`kouseiMultiplierPerMille`、`kouseiSalaryCapMonthly` 與 `kouseiBonusCapPerPayment`。

### 2. 更新 SimulationContext
- [SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
  加入 `pensionConfig` 屬性，供後續 `PensionEngine` 或 Calculator 讀取年金設定，並且處理好 `clone()` 時的參考（由於是不可變物件，只需淺拷貝即可）。

### 3. 實作 SimulationContextFactory 的隔離與轉換
- [SimulationContextFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)
  - 注入 `PensionRuleConfigService`。
  - 在每次建立模擬上下文時，呼叫服務層去撈出「當前啟用的設定（Enabled Config）」。
  - 轉換並將其封裝為 `PensionConfigDto` 存入 `SimulationContext`。

### 4. 撰寫單元測試
- [SimulationContextFactoryTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)
  更新測試來驗證 Factory 在有 Mock 出 `PensionRuleConfigService` 時，能正確將 Result 結構轉換為 Context 內的 DTO，確保各項屬性（包含 `maxBasicPensionAmount` 及所有年金常數）綁定無誤。

---

## 驗證計畫

> [!IMPORTANT]
> 遵照「嚴禁自動執行命令列」以及「由您親自測試」的規定，請您執行下列指令來確保沒有編譯錯誤，並且單元測試能通過：
> 
> ```bash
> mvn clean install
> ```
