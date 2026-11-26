# Fixed 82-08 Phase 4 年金計算引擎 (資料驅動實作) 重構計畫 (欄位擴充)

本計畫的目標是針對 `PensionCalculator` 中寫死的 4 個靜態常數（如 `MAX_BASIC_PENSION_MONTHS` 等）進行重構，將其改為從系統動態載入的設定 (`PensionConfigDto`) 取值。這樣不僅核心的年度模擬管線能拿到最新的年金設定，獨立的年金推算服務 (`PensionProjectionServiceImpl`) 也同樣能套用最新的法定基準。

## Proposed Changes

### 年金計算引擎重構 (Pension Calculator)

#### [MODIFY] [`PensionCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/PensionCalculator.java)
- **常數移除**:
  - 徹底移除靜態常數 `MAX_BASIC_PENSION_MONTHS`、`KOUSEI_MULTIPLIER`、`KOUSEI_SALARY_CAP_MONTHLY`、`KOUSEI_BONUS_CAP_PER_PAYMENT`。
- **方法更新**:
  - 在 `calculateProjectedBasicPension` 內，原本寫死的 `MAX_BASIC_PENSION_MONTHS` 替換為 `config.getMaxBasicPensionMonths()`。
  - 將 `calculateYearlyKouseiBenefitIncrement` 增加一個 `PensionConfigDto config` 作為第一個參數，並將內部的常數計算替換為從 `config` 取值。注意 `kouseiMultiplierPerMille` 需轉換為 `double` 並除以 1000.0 才能套用到目前的計算中。

### 年金推算服務重構 (Pension Projection Service)

#### [MODIFY] [`PensionProjectionServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java)
- **方法呼叫更新**:
  - 在呼叫 `pensionCalculator.calculateYearlyKouseiBenefitIncrement(...)` 時，傳入已經動態取得的 `pensionConfigDto`。

### 核心模擬管線更新 (Simulation Pipeline)

#### [MODIFY] [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **方法呼叫更新**:
  - 在呼叫 `calculateYearlyKouseiBenefitIncrement` 時，將 `context.getPensionConfig()` 作為第一個參數傳入。

### 測試修復 (Tests)

#### [MODIFY] `PensionCalculatorTest.java` (若存在相關測試)
- 將測試中呼叫的方法補上假的 `PensionConfigDto`。

#### [MODIFY] [`PensionProjectionServiceTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java)
- 同步修正對 `pensionCalculator.calculateYearlyKouseiBenefitIncrement` 的 `when().thenReturn()` mock 宣告，加上 `any(PensionConfigDto.class)`。

## Verification Plan

### Automated Tests (由使用者執行)
- `mvn test -Dtest=PensionCalculatorTest`：確認帶入 config 後的計算邏輯無誤。
- `mvn test -Dtest=PensionProjectionServiceTest`：驗證依賴注入與 Mock 的參數簽名吻合。
- `mvn test -Dtest=YearlySimulationPipelineTest`：確認管線正常編譯與運作。

# Fixed 82-08 Phase 4 年金計算引擎重構計畫 (欄位擴充) Walkthrough

我們已經成功完成 Phase 4 的實作，將 `PensionCalculator` 中與年金計算相關的硬編碼（寫死）靜態常數移除，改為全面由 `PensionConfigDto` 動態讀取設定。這不僅確保了模擬管線能使用最新配置，也使獨立的年金推算服務 (`PensionProjectionService`) 能夠精確採用使用者的法定年金基準。

## 完成項目

### 1. 年金計算引擎重構
- [`PensionCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/PensionCalculator.java)
  - 移除了 `MAX_BASIC_PENSION_MONTHS`、`KOUSEI_MULTIPLIER`、`KOUSEI_SALARY_CAP_MONTHLY`、`KOUSEI_BONUS_CAP_PER_PAYMENT` 等 4 個靜態常數。
  - 將 `calculateProjectedBasicPension` 方法內的常數計算改為使用 `config.getMaxBasicPensionMonths()`。
  - 修改 `calculateYearlyKouseiBenefitIncrement` 方法的簽名，增加 `PensionConfigDto config` 參數，並將其中的厚生年金計算邏輯改由 `config` 中取得各項上限與乘數（包含將 BigDecimal 乘數除以 1000）。

### 2. 年金推算服務重構
- [`PensionProjectionServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java)
  - 更新 `PensionConfigDto` 的實例化過程，將從 `PensionRuleConfigResult` 取得的 5 個新舊屬性全部帶入建構子。
  - 呼叫 `calculateYearlyKouseiBenefitIncrement` 時將 `pensionConfigDto` 正確傳入。

### 3. 核心模擬管線更新
- [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
  - 於 `applyWorkingTaxes` 階段呼叫 `calculateYearlyKouseiBenefitIncrement` 時，直接帶入 `context.getPensionConfig()`。

### 4. 測試修復
- [`PensionProjectionServiceTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java)
  - 同步更新了 Mock 宣告，針對 `pensionCalculator.calculateYearlyKouseiBenefitIncrement` 加入 `any(PensionConfigDto.class)` 及對應的 `eq()` 匹配器，確保測試維持正常運作。

---

## 後續驗證

> [!IMPORTANT]
> 遵照「嚴禁自動執行命令列」以及「由您親自測試」的規定，請您執行下列指令來確保沒有編譯錯誤，並且所有的測試依然能順利通過：
> 
> ```bash
> mvn clean test
> ```


# Fixed 82-08 Phase 4 年金計算引擎重構計畫 (欄位擴充) Walkthrough

我們已經成功完成 Phase 4 的實作，將 `PensionCalculator` 中與年金計算相關的硬編碼（寫死）靜態常數移除，改為全面由 `PensionConfigDto` 動態讀取設定。這不僅確保了模擬管線能使用最新配置，也使獨立的年金推算服務 (`PensionProjectionService`) 能夠精確採用使用者的法定年金基準。

## 完成項目

### 1. 年金計算引擎重構
- [`PensionCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/PensionCalculator.java)
  - 移除了 `MAX_BASIC_PENSION_MONTHS`、`KOUSEI_MULTIPLIER`、`KOUSEI_SALARY_CAP_MONTHLY`、`KOUSEI_BONUS_CAP_PER_PAYMENT` 等 4 個靜態常數。
  - 將 `calculateProjectedBasicPension` 方法內的常數計算改為使用 `config.getMaxBasicPensionMonths()`。
  - 修改 `calculateYearlyKouseiBenefitIncrement` 方法的簽名，增加 `PensionConfigDto config` 參數，並將其中的厚生年金計算邏輯改由 `config` 中取得各項上限與乘數（包含將 BigDecimal 乘數除以 1000）。

### 2. 年金推算服務重構
- [`PensionProjectionServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java)
  - 更新 `PensionConfigDto` 的實例化過程，將從 `PensionRuleConfigResult` 取得的 5 個新舊屬性全部帶入建構子。
  - 呼叫 `calculateYearlyKouseiBenefitIncrement` 時將 `pensionConfigDto` 正確傳入。

### 3. 核心模擬管線更新
- [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
  - 於 `applyWorkingTaxes` 階段呼叫 `calculateYearlyKouseiBenefitIncrement` 時，直接帶入 `context.getPensionConfig()`。

### 4. 測試修復
- [`PensionProjectionServiceTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java)
  - 同步更新了 Mock 宣告，針對 `pensionCalculator.calculateYearlyKouseiBenefitIncrement` 加入 `any(PensionConfigDto.class)` 及對應的 `eq()` 匹配器，確保測試維持正常運作。

---

## 後續驗證

> [!IMPORTANT]
> 遵照「嚴禁自動執行命令列」以及「由您親自測試」的規定，請您執行下列指令來確保沒有編譯錯誤，並且所有的測試依然能順利通過：
> 
> ```bash
> mvn clean test
> ```
