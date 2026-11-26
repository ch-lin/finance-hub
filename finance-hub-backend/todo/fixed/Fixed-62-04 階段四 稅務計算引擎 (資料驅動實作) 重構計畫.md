# Phase 4 稅務計算引擎 (資料驅動實作) 重構計畫

本計畫的目標是將 `ActiveTaxCalculationEngine` 與 `DependentDeductionEngine` 內部寫死的常數與 `if-else` 邏輯消除，全面改用從 `SimulationContext.getTaxConfig()` 取得的動態級距與資料來進行計算。

## Proposed Changes

### 稅務引擎重構 (Tax Calculation Engines)

#### [MODIFY] `ActiveTaxCalculationEngine.java`
- **注入整理**: 確保 `InsurancePolicyRepository` 目前先不動（留待 Phase 6 處理）。
- **常數替換**:
  - 移除靜態常數 `BASIC_DEDUCTION_INCOME_TAX` (480,000) 與 `BASIC_DEDUCTION_INHABITANT_TAX` (430,000)。
  - 在 `calculate` 與相關計算中，替換為 `context.getTaxConfig().getBasicDeductionIncomeTax()` 與 `context.getTaxConfig().getBasicDeductionHabitantTax()`。
- **消滅 if-else (Table-Driven Design)**:
  - `calculateEmploymentIncomeDeduction`: 增加傳入 `List<TaxBracketDto> brackets`，將 if-else 替換為 `for` 迴圈。
  - `calculateIncomeTax`: 增加傳入 `List<TaxBracketDto> brackets`，將 if-else 替換為 `for` 迴圈。
  - 住民稅率計算: 替換原本寫死的 `0.10`，改為使用 `context.getTaxConfig().getHabitantTaxBrackets()` 透過迴圈計算。
- **方法簽名更新 (確定的方案)**:
  嚴格遵守 Fixed-62 藍圖指示「方法一開始從 context.getTaxConfig() 取得」，因此我們會在 `calculate` 的參數列表中新增 `SimulationContext context` 作為第一個參數，並刪除冗餘的獨立參數：
  
  ```diff
  -    public TaxCalculationResult calculate(long grossBaseSalary, long annualBonus, int bonusPaymentCount,
  -            double healthInsuranceRate, double pensionRate,
  -            double employmentInsuranceRate, UserProfile userProfile,
  -            long annualIdecoContribution, long annualDcMatchingContribution, int simulationYear,
  -            long mortgageTaxDeduction, long maxInhabitantTaxDeduction) {
  +    public TaxCalculationResult calculate(SimulationContext context, long grossBaseSalary, long annualBonus, int bonusPaymentCount,
  +            long annualIdecoContribution, long annualDcMatchingContribution,
  +            long mortgageTaxDeduction, long maxInhabitantTaxDeduction) {
  ```
  ```diff
  -    public TaxCalculationResult calculateRetirementTax(long retirementIncome, int contributionYears) {
  +    public TaxCalculationResult calculateRetirementTax(SimulationContext context, long retirementIncome, int contributionYears) {
  ```
  ```diff
  -    public TaxCalculationResult calculatePensionTax(long pensionIncome, int age) {
  +    public TaxCalculationResult calculatePensionTax(SimulationContext context, long pensionIncome, int age) {
  ```

#### [MODIFY] `DependentDeductionEngine.java`
- **方法簽名更新**: 在方法中加入 `SimulationContext context`。
  ```diff
  -    public long calculateIncomeTaxDeduction(Dependent dependent, LocalDate simulationYearEnd) {
  +    public long calculateIncomeTaxDeduction(SimulationContext context, Dependent dependent, LocalDate simulationYearEnd) {
  ```
  ```diff
  -    public long calculateInhabitantTaxDeduction(Dependent dependent, LocalDate simulationYearEnd) {
  +    public long calculateInhabitantTaxDeduction(SimulationContext context, Dependent dependent, LocalDate simulationYearEnd) {
  ```
- **常數替換**: 將原先寫死的 `1030000L` 替換為 `context.getTaxConfig().getDependentIncomeThreshold()`。

#### [MODIFY] `ActiveTaxCalculationEngineTest.java` / `DependentDeductionEngineTest.java`
- 修改單元測試的 mock setup，建立帶有預設級距的 `TaxConfigDto` 與 `SimulationContext`，以符合新的方法簽名，並確保替換成迴圈邏輯後，測試結果依然不變。

#### [MODIFY] `YearlySimulationPipeline.java` (原註記為 AssetSimulationEngineImpl 的實際呼叫者)
- 配合上述方法簽名的變更，在呼叫 `activeTaxCalculationEngine.calculate(...)` 以及 `calculatePensionTax(...)` 時傳遞簡化後的參數。
  
  ```diff
  -            var taxResult = taxEngine.calculate(
  -                    effectiveAnnualSalary,
  -                    context.getCurrentAnnualBonus(),
  -                    context.getCurrentBonusPaymentCount(),
  -                    context.getHealthInsuranceRate(),
  -                    context.getPensionRate(),
  -                    context.getEmploymentInsuranceRate(),
  -                    context.getProfile(),
  -                    contributions.annualIdecoContribution(),
  -                    contributions.annualDcMatchingContribution(),
  -                    context.getYear(),
  -                    mortgageResult.mortgageTaxDeductionThisYear(),
  -                    mortgageResult.maxInhabitantTaxDeductionThisYear());
  +            var taxResult = taxEngine.calculate(
  +                    context,
  +                    effectiveAnnualSalary,
  +                    context.getCurrentAnnualBonus(),
  +                    context.getCurrentBonusPaymentCount(),
  +                    contributions.annualIdecoContribution(),
  +                    contributions.annualDcMatchingContribution(),
  +                    mortgageResult.mortgageTaxDeductionThisYear(),
  +                    mortgageResult.maxInhabitantTaxDeductionThisYear());
  ```
  ```diff
  -            var taxResult = taxEngine.calculatePensionTax(pensionIncome, age);
  +            var taxResult = taxEngine.calculatePensionTax(context, pensionIncome, age);
  ```

## Verification Plan

### Automated Tests (由使用者執行)
（依照您的指示，測試將交由您來跑，我不主動執行）
- `mvn test -Dtest=ActiveTaxCalculationEngineTest`：驗證資料驅動的級距迴圈邏輯，算出的稅額與原本一致。
- `mvn test -Dtest=DependentDeductionEngineTest`：驗證扶養親屬的門檻條件改為動態取得後，邊界測試維持正確。
- `mvn test -Dtest=YearlySimulationPipelineTest` (與 `AssetSimulationEngineImplTest`)：驗證所有 Engine 銜接無誤，能夠成功編譯。
