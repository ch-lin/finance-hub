# Phase 4 年金計算引擎 (資料驅動實作) 重構計畫

本計畫的目標是針對 `PensionCalculator` 中寫死的 `MAX_BASIC_PENSION_AMOUNT` (老年基礎年金滿額基準) 進行重構，將其改為從系統動態載入的設定 (`PensionConfigDto`) 取值。這樣不僅核心的年度模擬管線能拿到最新的年金設定，獨立的年金推算服務 (`PensionProjectionServiceImpl`) 也同樣能套用最新的法定基準。

## Proposed Changes

### 年金計算引擎重構 (Pension Calculator)

#### [MODIFY] `PensionCalculator.java`
- **常數移除**:
  - 徹底移除靜態常數 `public static final long MAX_BASIC_PENSION_AMOUNT = 816000L;`。
  - 徹底移除靜態常數 `MAX_BASIC_PENSION_MONTHS`、`KOUSEI_MULTIPLIER`、`KOUSEI_SALARY_CAP_MONTHLY`、`KOUSEI_BONUS_CAP_PER_PAYMENT`。
- **方法簽名更新**:
  - 將原本的 `calculateProjectedBasicPension` 增加一個 `PensionConfigDto config` 作為第一個參數，以便從中讀取 `maxBasicPensionAmount`。
  - **為何不用 SimulationContext？** 因為此方法不僅被核心模擬管線 (`YearlySimulationPipeline`) 呼叫，也被獨立服務 (`PensionProjectionServiceImpl`) 呼叫，後者並沒有完整的 `SimulationContext`。因此直接傳入隔離層的 `PensionConfigDto` 是最乾淨的設計。
  
  ```diff
  -    public long calculateProjectedBasicPension(int currentKokuminMonths, int currentKouseiMonths, int additionalMonths,
  -            int pensionStartAge) {
  +    public long calculateProjectedBasicPension(PensionConfigDto config, int currentKokuminMonths, int currentKouseiMonths, int additionalMonths,
  +            int pensionStartAge) {
  ```
- **計算邏輯更新**:
  - 在計算 `baseAmount` 時，將原本乘上的常數替換為 `config.getMaxBasicPensionAmount()` 及 `config.getMaxBasicPensionMonths()`。
  - 在計算厚生年金時，將原本使用的常數替換為 `config.getKouseiMultiplierPerMille()`（需除以 1000）、`config.getKouseiSalaryCapMonthly()` 與 `config.getKouseiBonusCapPerPayment()`。

### 年金推算服務重構 (Pension Projection Service)

#### [MODIFY] `PensionProjectionServiceImpl.java`
- **相依注入**:
  - 注入 `PensionRuleConfigService`。
- **取得動態設定**:
  - 在 `calculateProjection` 方法的一開始，呼叫 `PensionRuleConfigService` 取得系統目前啟用的最新年金設定 (Entity/Result)，並將其轉換為不可變的 `PensionConfigDto`。
- **方法呼叫更新**:
  - 在迴圈內呼叫 `pensionCalculator.calculateProjectedBasicPension(...)` 時，將取得的 `PensionConfigDto` 傳入。
  
  ```diff
  -            long projectedBasic = pensionCalculator.calculateProjectedBasicPension(currentKokuminMonths,
  -                    currentKouseiMonths, additionalMonths, pensionStartAge);
  +            long projectedBasic = pensionCalculator.calculateProjectedBasicPension(pensionConfigDto, currentKokuminMonths,
  +                    currentKouseiMonths, additionalMonths, pensionStartAge);
  ```

### 核心模擬管線更新 (Simulation Pipeline)

#### [MODIFY] `YearlySimulationPipeline.java`
- **方法呼叫更新**:
  - 在呼叫 `calculateProjectedBasicPension` 時，將 `context.getPensionConfig()` 作為第一個參數傳入。
  
  ```diff
  -                basicPension = pensionCalculator.calculateProjectedBasicPension(
  -                        context.getInitialKokuminTotalMonths(),
  -                        context.getInitialKouseiTotalMonths(),
  ...
  +                basicPension = pensionCalculator.calculateProjectedBasicPension(
  +                        context.getPensionConfig(),
  +                        context.getInitialKokuminTotalMonths(),
  +                        context.getInitialKouseiTotalMonths(),
  ```

### 測試修復 (Tests)

#### [MODIFY] `PensionCalculatorTest.java` (若有)
- 建立假的 `PensionConfigDto` 實體傳入測試方法中。

#### [MODIFY] `PensionProjectionServiceTest.java`
- Mock `PensionRuleConfigService` 使其在測試時回傳一組固定的年金設定，確保 `PensionProjectionServiceImpl` 的行為維持預期。
- 同步修正對 `pensionCalculator.calculateProjectedBasicPension` 的 `when().thenReturn()` mock 宣告，加上 `any(PensionConfigDto.class)`。

#### [MODIFY] `YearlySimulationPipelineTest.java` (與 `AssetSimulationEngineImplTest` 若有牽連)
- 確認 mock 的 `SimulationContext` 能正確回傳預設的 `PensionConfigDto`，並修正對應的 mock 宣告。

## Verification Plan

### Automated Tests (由使用者執行)
- `mvn test -Dtest=PensionCalculatorTest`：確認帶入 config 後的計算邏輯無誤。
- `mvn test -Dtest=PensionProjectionServiceTest`：驗證依賴注入與 Mock 的參數簽名吻合。
- `mvn test -Dtest=YearlySimulationPipelineTest`：確認管線正常編譯與運作。

---

## Walkthrough: Fixed-82-07 Phase 4 實作完成

### Changes Made
1. **PensionCalculator.java**
   - 移除了寫死的 `MAX_BASIC_PENSION_AMOUNT` 等 5 個靜態常數。
   - `calculateProjectedBasicPension` 與其他相關計算方法現在接受 `PensionConfigDto` 作為第一個參數，並透過 `config` 取得最新的法定年金基準來計算結果。

2. **PensionProjectionServiceImpl.java**
   - 透過建構子注入 `PensionRuleConfigService`。
   - 在 `calculateProjection` 執行時，主動查詢目前啟用的年金設定 (Enabled Config Name -> Config Result) 並轉換為 `PensionConfigDto`。
   - 將動態取得的 `PensionConfigDto` 傳入 `calculateProjectedBasicPension`。

3. **YearlySimulationPipeline.java**
   - 針對退休階段的計算 (`applyRetireeTaxes`)，原本呼叫 `calculateProjectedBasicPension` 的地方已加上 `context.getPensionConfig()`。

4. **PensionProjectionServiceTest.java**
   - 補上了 `PensionRuleConfigService` 的 Mock，確保測試執行時能回傳預設的設定值 (`816_000L`)。
   - 修正了 `when(pensionCalculator.calculateProjectedBasicPension(...))` 的 Mock 簽名，使其包含 `any(PensionConfigDto.class)`，以避免參數不符導致的 Mocking 失敗。

### Validation
- 程式碼已成功修改完成。
- 依照您的指示，測試的編譯與執行將交由您後續手動驗證。若在測試執行過程中有發現任何 Mock 不吻合的地方，可依此脈絡補上對應的 Mock。
