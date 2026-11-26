# Fixed 82-06 Phase 3: Simulation Context 隔離層實作計畫 (欄位擴充)

此計畫將針對 `PensionEngine` 建立隔離層，確保模擬引擎只接觸唯讀的 DTO，避免在模擬迴圈中呼叫資料庫，符合 DDD 原則並確保效能。

> [!NOTE]
> 在先前的實作中，`PensionConfigDto` 與 `SimulationContext` 中已經有了雛形（包含 `maxBasicPensionAmount`）。本次計畫的重點在於**擴充這層隔離層**，將 Phase 1 與 Phase 2 新增的 4 個年金常數也一併納入，確保計算機能取得完整的參數。

## User Review Required

無特別需要確認的業務邏輯。此階段主要是將新增的 4 個欄位正確地封裝進 `PensionConfigDto` 並在 Factory 中完成映射。

## Proposed Changes

### Simulation Core Models (DTO)

擴充不可變的 DTO 以供 Simulation Context 與 Engine 內部使用。

#### [MODIFY] [`PensionConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/pension/PensionConfigDto.java)
- 追加以下純量屬性（並補上 `java.math.BigDecimal` 的 import）：
  - `Long maxBasicPensionMonths;`
  - `BigDecimal kouseiMultiplierPerMille;`
  - `Long kouseiSalaryCapMonthly;`
  - `Long kouseiBonusCapPerPayment;`

---

### Simulation Factory

負責在初始化時從外部 Service 撈取資料並轉型。我們需要擴充 Mapping 的參數。

#### [MODIFY] [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)
- 在 `createContext()` 中，修改建立 `PensionConfigDto` 的建構邏輯，將 `PensionRuleConfigResult` 內的 5 個屬性完整映射進 `PensionConfigDto` 中。

#### [MODIFY] [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)
- 擴充 Mock `PensionRuleConfigService` 的回傳值設定，確保 Result 內含所有新常數。
- 驗證 `SimulationContext` 初始化後，內含的 `pensionConfig` 各項參數（共 5 個）都有正確對應。

---

## Verification Plan

### Automated Tests
- 確認 `SimulationContextFactoryTest` 更新後的斷言完全通過。

### Manual Verification
> [!IMPORTANT]
> (強制規定：所有測試撰寫完成後，絕對不會自動使用命令列執行，將由您親自手動執行與驗證)
> 跑完編譯 (`mvn clean install`) 確認沒有依賴問題，且所有舊有的 Context 測試依然能通過。

# Fixed 82-06 Phase 3: Simulation Context 隔離層實作計畫 (欄位擴充) Walkthrough

我們已經成功完成 Phase 3 的實作，確保年金引擎 (`PensionEngine`) 運作在純函數模式下，完全不接觸 Repository，遵守隔離與 DDD 原則。

## 完成項目

### 1. 建立 Simulation Core Models (DTO)
- [`PensionConfigDto.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/pension/PensionConfigDto.java)
  已擴充此不可變的 `@Value` 類別，用以存放從外部拉取的 `maxBasicPensionAmount`、`maxBasicPensionMonths`、`kouseiMultiplierPerMille`、`kouseiSalaryCapMonthly` 與 `kouseiBonusCapPerPayment`。

### 2. 更新 SimulationContext
- [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
  已確認內含 `pensionConfig` 屬性，供後續 `PensionEngine` 或 Calculator 讀取年金設定，並且處理好 `clone()` 時的淺拷貝參考。

### 3. 實作 SimulationContextFactory 的隔離與轉換
- [`SimulationContextFactory.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)
  - 在每次建立模擬上下文時，會正確將 `PensionRuleConfigResult` 的所有 5 個欄位轉換並封裝為 `PensionConfigDto` 存入 `SimulationContext`。

### 4. 撰寫單元測試
- [`SimulationContextFactoryTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactoryTest.java)
  更新測試來驗證 Factory 在有 Mock 出 `PensionRuleConfigService` 時，能正確將 Result 結構轉換為 Context 內的 DTO，確保這 5 個欄位屬性綁定無誤。

---

## 後續驗證

> [!IMPORTANT]
> 遵照「嚴禁自動執行命令列」以及「由您親自測試」的規定，請您執行下列指令來確保沒有編譯錯誤，並且單元測試能通過：
> 
> ```bash
> mvn clean test
> ```
