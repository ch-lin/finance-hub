# Fixed-93-01 Fix: 導入 Strategy Pattern 修正房貸減稅之住民稅扣抵上限邏輯

本計畫依據 Fixed-93，修正房貸減稅轉扣住民稅時遺漏的「與課稅所得比例（5% 或 7%）取小」的法規邏輯。為了避免核心稅務引擎（`ActiveTaxCalculationEngine`）過度耦合房貸領域知識，本修正將採用 **Strategy Pattern（策略模式）** 進行重構。

## User Review Required

> [!NOTE]
> **新 Package 規劃**
> 為了避免 `core.calculator` 越來越雜亂，本次新增的 Strategy 介面與實作類別，建議統一放進新的 package：
> `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage`
> 未來您使用 IDE 重構時，可以很方便地把整個 `strategy` 資料夾連根拔起移動。請確認這個 package 命名是否合適。

## Open Questions

無。完全依照您指示的 Strategy Pattern 方向進行規劃。您說得對，討論出最適合的架構再動手，才是軟體工程該有的態度。這點我受教了！

## Proposed Changes

---

### 1. 新增 Strategy 介面與實作類別 (New Package)
**Package**: `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.mortgage`

#### [NEW] [InhabitantTaxLimitStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/mortgage/InhabitantTaxLimitStrategy.java)
定義策略介面。為了能讓 `MortgageCalculator` 處理多筆房貸時比較「哪一個策略對使用者最有利（上限最大）」，提供一個取得固定上限的方法以供比較。
```java
public interface InhabitantTaxLimitStrategy {
    long calculateLimit(long taxableIncomeForIncomeTax);
    long getFixedLimit(); // 用於多筆房貸時比較優劣
}
```

#### [NEW] [Pre2022InhabitantTaxLimitStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/mortgage/Pre2022InhabitantTaxLimitStrategy.java)
實作 2021 年（含）以前入住的邏輯（7% / 13.65 萬）。

#### [NEW] [Post2022InhabitantTaxLimitStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/mortgage/Post2022InhabitantTaxLimitStrategy.java)
實作 2022 年（含）以後入住的邏輯（5% / 9.75 萬）。

#### [NEW] [ZeroInhabitantTaxLimitStrategy.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/mortgage/ZeroInhabitantTaxLimitStrategy.java)
當沒有房貸減稅額度時的預設空策略（0% / 0 萬）。

---

### 2. 修改資料載體 (Data Transfer Object)

#### [MODIFY] [MortgageYearlyResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/result/MortgageYearlyResult.java)
- 移除原本的 `long maxInhabitantTaxDeductionThisYear` 欄位。
- 替換成 `InhabitantTaxLimitStrategy inhabitantTaxLimitStrategy`。

---

### 3. 修改核心計算器 (Calculators & Engines)

#### [MODIFY] [MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
- 在計算上限的迴圈處（第 480 行附近），不再比較離散的 `currentLimit`。
- 改為建立對應年份的 `Strategy` 物件，並比較 `currentStrategy.getFixedLimit() > bestStrategy.getFixedLimit()`，保留最大的 Strategy。
- 最終將 `bestStrategy` 放入 `MortgageYearlyResult` 回傳。

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- 修改傳給 `taxEngine.calculate(...)` 的參數。原本是傳入 `mortgageResult.maxInhabitantTaxDeductionThisYear()`，現在改為直接把整個 `mortgageResult`（或是裡面的 strategy）傳進去。

#### [MODIFY] [ActiveTaxCalculationEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)
- 在第 204 行附近，引擎只需要呼叫：
  ```java
  long inhabitantTaxDeductionLimit = mortgageResult.inhabitantTaxLimitStrategy().calculateLimit(taxableIncomeForIncomeTax);
  // 然後繼續原本的 Math.min 取小邏輯
  ```

## Verification Plan

### Automated Tests
您將負責執行所有測試。為確保本次重構正確性，您需要新增/檢查以下測試：

1. **新增 `InhabitantTaxLimitStrategyTest` (單元測試)**
   - 測試 `Pre2022InhabitantTaxLimitStrategy`：給定不同的 `taxableIncome`，驗證回傳值是否正確取 7% 與 13.65 萬的較小值。
   - 測試 `Post2022InhabitantTaxLimitStrategy`：給定不同的 `taxableIncome`，驗證回傳值是否正確取 5% 與 9.75 萬的較小值。

2. **檢查 `ActiveTaxCalculationEngineTest`**
   - 確認傳入 Mock 好的 `MortgageYearlyResult` (包含 Strategy) 時，引擎能正確呼叫並扣抵住民稅。

3. **整合測試 (Integration Test)**
   - 確保 `mvn clean test` 完全通過，驗證改動沒有破壞原本的多筆房貸計算邏輯。

# Fixed-93-01 實作總結：住民稅房貸扣除上限 (Strategy Pattern)

## 變更摘要
我們已將「住民稅可扣抵之房貸上限」的計算邏輯由原本的寫死數字，重構成基於 Strategy Pattern 的架構，徹底解耦法規計算與稅務引擎。

### 1. 新增 Strategy 介面與實作
- **`InhabitantTaxLimitStrategy` 介面**：定義了 `calculateLimit(taxableIncomeForIncomeTax)` 與 `getFixedLimit()` 兩個方法。
- **實作類別**：
  - `Pre2022InhabitantTaxLimitStrategy`：2021年（含）前入住，適用 7% 比例與 136,500 固定上限。
  - `Post2022InhabitantTaxLimitStrategy`：2022年（含）後入住，適用 5% 比例與 97,500 固定上限。
  - `ZeroInhabitantTaxLimitStrategy`：無房貸或是非適用對象的回退策略。

### 2. 封裝計算結果
- **修改 `MortgageYearlyResult`**：移除單純的 `maxInhabitantTaxDeductionThisYear` 數值欄位，改為儲存選定出來的 `inhabitantTaxLimitStrategy` 策略物件。

### 3. 解耦計算流程
- **`MortgageCalculator`**：在計算每年各筆房貸時，不再只是記錄最大固定金額。現在會依照年份動態生成對應的 Strategy，並透過比較 `getFixedLimit()` 來保留對使用者最有利（上限最大）的那個策略，封裝進結果中。
- **`ActiveTaxCalculationEngine`**：參數簽章改為接收整個 `MortgageYearlyResult`，並在實際要計算上限時，直接呼叫 `mortgageResult.inhabitantTaxLimitStrategy().calculateLimit(taxableIncomeForIncomeTax)`，讓百分比的計算能夠順利取用到當時已經算出來的「課稅總所得」。

## 驗證計畫
因為您先前有提到單元測試一律由您那邊跑，請幫我執行系統中相關的 Unit Tests（包含但不限於 `MortgageCalculatorTest` 與 `ActiveTaxCalculationEngineTest`），確認是否有因為這項架構調整而出現需要修復的 Mock/Stub 錯誤（因為原本傳 primitive long 的地方現在改傳物件了）。

如果有測試失敗，請隨時將錯誤訊息貼給我，我會立即修正！
