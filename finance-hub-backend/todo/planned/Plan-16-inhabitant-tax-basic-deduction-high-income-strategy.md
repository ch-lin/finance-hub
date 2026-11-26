# Plan 16：高所得者個人住民稅基礎控除高所得級距遞減實作計畫 (Inhabitant Tax Basic Deduction High Income Strategy)

> **編註（2026-10-09，Review後修正）**：原標題與下文部分段落宣稱本計畫已實作「前年所得課稅時差機制」，查證程式碼後確認這個機制從未真正實作——所得稅跟住民稅兩條查表路徑用的都是「今年」的所得，不是「前一年」的，跟文件下方舉的具體範例行為不符。這個時差機制牽涉的技術範圍已經整批移到[Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md)（含子題[Plan-21-02](Plan-21-02-basic-deduction-prior-year-income.md)），決定暫不投入月級別引擎重寫。本文件標題與下方描述保留原始記錄供回溯參考，但請以此編註為準：**本計畫實際交付、且已確認正確運作的部分，只有「住民稅基礎控除依所得級距遞減（43萬/29萬/15萬/0円）」這個邏輯本身，不包含前年所得課稅時差。**

## 一、背景與問題本質（Why this Plan?）

### 1. 緣起與架構發現
在針對 [Question-118](../pending%20fix/question-118.md)（所得稅基礎控除動態分級）與 [Question-120](../pending%20fix/question-120.md)（配偶者控除雙稅時差）進行深入覆核時，確立了一項關鍵的系統設計現狀：
- **所得稅部分**：已在 Question-118 藉由方案 B 導入 `BasicDeductionStrategy` 策略模式，支援令和 7 年（2025）、令和 8～9 年（2026～2027）特例與令和 10 年（2028+）本則之動態所得階梯查表。
- **住民稅部分**：目前個人住民稅基礎控除（`basicDeductionInhabitantTax`）在資料庫與計算引擎（`ActiveTaxCalculationEngine.java:74`）中**依然寫死為單一常數 `430,000L`**，完全沒有動態分級與策略派發機制。

### 2. 核心使用者畫像與法定斷崖邊界
本系統核心使用者為**年薪 2,500 萬日圓**的高所得受薪階級（Product Manager 兼唯一使用者）：
- 依據《所得稅法》第28條，年薪超過 850 萬日圓時，給與所得控除上限封頂為 **195 萬日圓**。
- 使用者常態基準之**合計所得金額（給與所得）**為：
  $$25,000,000 - 1,950,000 = \mathbf{23,050,000\text{ 円（約 2,305 萬円）}}$$
- **斷崖鄰近性**：此基準所得距離全日本稅法最嚴格的「超高所得者遞減與歸零階梯（2,400 萬円）」僅差 **95 萬日圓**！
  只要納稅人獲得額外年終獎金（Bonus）、調薪、限制型股票（RSU）歸屬或投資所得，年薪達到 **2,595 萬日圓**以上，合計所得將直接踩穿 2,400 萬～2,500 萬的法定遞減斷崖！

---

## 二、官方單一事實來源（SSOT 法定規範）

### 1. 個人住民稅基礎控除之法定離散階梯
依據日本《地方稅法》第314條之2第2項，個人住民稅（所得割）基礎控除並非全體納稅人一律 43 萬円，而是嚴格按合計所得金額離散分級：

| 納税者本人の前年合計所得金額 | 法定個人住民稅基礎控除額 | 系統現行寫死值 | 系統計算誤差（偏差） |
| :--- | :---: | :---: | :---: |
| **2,400 万円以下**（年薪約 $\le$ 2,595 萬） | **43 万円** | 43 万円 | 誤差為 0（完全相符） |
| **2,400 万円超 ～ 2,450 万円以下**（年薪約 2,595萬～2,645萬） | **29 万円** | 43 万円 | **多算 14 萬扣除額**（少算 14,000 円稅金） |
| **2,450 万円超 ～ 2,500 万円以下**（年薪約 2,645萬～2,695萬） | **15 万円** | 43 万円 | **多算 28 萬扣除額**（少算 28,000 円稅金） |
| **2,500 万円超**（年薪約 > 2,695 萬） | **0 円（完全歸零）** | 43 万円 | **多算 43 萬扣除額**（少算 43,000 円稅金） |

### 2. 住民稅「前年所得課稅原則」時差法規
依據日本《地方稅法》第313條第1項，個人住民稅為**翌年課稅（前年所得課稅原則）**：
- 納稅義務人在當年度（$T$ 年）繳納的個人住民稅，其課稅所得標準為**前一年度（$T-1$ 年）的合計所得金額**。
- **跨年度薪資波動保護**：若使用者在 2025 年年薪為 2,500 萬（合計所得 2,305 萬），而在 2026 年因績效獎金年薪衝到 2,700 萬（合計所得 2,505 萬）：
  - **2026 年（當期）住民稅**：依 2025 年前年所得（2,305 萬）課稅 $\longrightarrow$ **法定仍享有 43 萬控除**！
  - **2027 年（次期）住民稅**：才依 2026 年前年所得（2,505 萬）課稅 $\longrightarrow$ **法定基礎控除正式歸零（0 円）**！
- 若未落實隔年課稅時差，將導致高額薪資躍升當年提前扣繳歸零，破壞現金流模擬的時序真實性。

---

## 三、既有系統架構缺陷與財務風控風險

1. **高所得族群漏算稅金（過度樂觀）**：
   現行系統將 `basicDeductionInhabitantTax` 寫死為 43 萬，當使用者收入跨過 2,400 萬門檻時，系統多算扣除額 14 萬～43 萬円，每年少算 1.4 萬～4.3 萬円住民稅。這違背了財務模擬的「保守與安全邊際原則」，導致手頭可用現金預估偏向樂觀。
2. **與 Question-120 架構不一致（Architectural Inconsistency）**：
   Question-120 已為「配偶者控除」建立了成熟的 `SpouseDeductionStrategyFactory` 雙軌派發架構（`getIncomeTaxStrategy` 與 `getInhabitantTaxStrategy`）；基礎控除卻依然是一半策略（所得稅）、一半寫死（住民稅），破壞了專案代碼的一致性。

---

## 四、核心架構改造方案（比照 Question-120 策略模式）

全面比照 [Question-120](../pending%20fix/question-120.md) 之成功經驗，完全封裝在 `hub-app` 純計算層，**不改動資料庫（DB Migration Free）、不改動 API DTO、不改動前端 UI**。

### 1. 策略介面擴充或雙軌化
套件路徑：`ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator.strategy.tax.deduction.basic`

#### (1) `BasicDeductionStrategy.java`
擴充介面以同時定義所得稅與住民稅基礎控除合約：
```java
public interface BasicDeductionStrategy {
    /**
     * 依合計所得金額計算所得稅基礎控除額（現年所得課稅）
     */
    long calculateIncomeTaxDeduction(long totalIncome);

    /**
     * 依合計所得金額計算住民稅基礎控除額（前年所得課稅）
     */
    long calculateInhabitantTaxDeduction(long totalIncome);
}
```

#### (2) 住民稅基礎控除階梯實作（所有 Strategy 實作類別統一繼承或具備）
依據《地方稅法》第314條之2第2項，所有年度策略之 `calculateInhabitantTaxDeduction` 均實作法定離散階梯：
```java
@Override
public long calculateInhabitantTaxDeduction(long totalIncome) {
    if (totalIncome <= 24_000_000L) {
        return 430_000L;
    } else if (totalIncome <= 24_500_000L) {
        return 290_000L;
    } else if (totalIncome <= 25_000_000L) {
        return 150_000L;
    } else {
        return 0L;
    }
}
```

#### (3) 工廠雙軌分派（`BasicDeductionStrategyFactory.java`）
比照 Question-120 之 `SpouseDeductionStrategyFactory`，提供所得稅與住民稅雙軌派發路由：
```java
@Component
public class BasicDeductionStrategyFactory {

    private final BasicDeductionStrategy legacyStrategy = new LegacyBasicDeductionStrategy();
    private final BasicDeductionStrategy reiwa7Strategy = new Reiwa7BasicDeductionStrategy();
    private final BasicDeductionStrategy reiwa8And9Strategy = new Reiwa8And9BasicDeductionStrategy();
    private final BasicDeductionStrategy reiwa10OnwardsStrategy = new Reiwa10OnwardsBasicDeductionStrategy();

    /**
     * 所得稅策略路由（現年課稅）
     */
    public BasicDeductionStrategy getIncomeTaxStrategy(int year) {
        if (year <= 2024) return legacyStrategy;
        if (year == 2025) return reiwa7Strategy;
        if (year <= 2027) return reiwa8And9Strategy;
        return reiwa10OnwardsStrategy;
    }

    /**
     * 住民稅策略路由（前年課稅時差）
     * 地方稅法各年份維持 43 萬標準階梯；未來若國會通過地方稅本則修法，可在此直接遞延一年分派新法，完全符合 OCP。
     */
    public BasicDeductionStrategy getInhabitantTaxStrategy(int year) {
        if (year <= 2025) return legacyStrategy;
        if (year == 2026) return reiwa7Strategy;
        if (year <= 2028) return reiwa8And9Strategy;
        return reiwa10OnwardsStrategy;
    }
}
```

### 2. 計算核心整合（`ActiveTaxCalculationEngine.java`）
修改 `ActiveTaxCalculationEngine.java`：
1. **移除常數讀取**：刪除 `long basicDeductionInhabitantTax = context.getTaxConfig().getBasicDeductionInhabitantTax();`。
2. **委派策略查表**：
   ```java
   // 給與所得情境
   long basicDeductionIncomeTax = basicDeductionStrategyFactory.getIncomeTaxStrategy(simulationYear)
           .calculateIncomeTaxDeduction(employmentIncome);

   long basicDeductionInhabitantTax = basicDeductionStrategyFactory.getInhabitantTaxStrategy(simulationYear)
           .calculateInhabitantTaxDeduction(employmentIncome);
   ```
3. **退職年金情境同步對齊**：
   在 `calculatePensionTax` 中同步改為呼叫策略計算。

---

## 五、兩階段執行計畫（Two-Phase Execution Plan）

為確保上下文清潔度並防範回歸錯誤，分為兩個獨立階段：

### 階段一（Phase 1）：策略合約擴充、階梯實作與專屬單元測試（純新增/擴充）
- **範圍**：
  1. 擴充 `BasicDeductionStrategy` 介面。
  2. 在 4 個策略實作類別（`Legacy`, `Reiwa7`, `Reiwa8And9`, `Reiwa10Onwards`）中實作 `calculateInhabitantTaxDeduction`。
  3. 在 `BasicDeductionStrategyFactory` 實作 `getIncomeTaxStrategy(year)` 與 `getInhabitantTaxStrategy(year)`。
  4. 擴充 `BasicDeductionStrategyTest.java`，覆蓋：
     - 所得 $\le 24$M（43 萬）
     - 所得 24M～24.5M（29 萬）
     - 所得 24.5M～25M（15 萬）
     - 所得 $> 25$M（0 円）
     - 雙軌工廠年份路由分派測試。
- **影響性**：零破壞性，既有呼叫端仍不受影響。

### 階段二（Phase 2）：計算引擎整合委派與測試校準（整合與驗證）
- **範圍**：
  1. 修改 `ActiveTaxCalculationEngine.java`，將住民稅基礎控除改為委託策略計算。
  2. 更新既有測試案例（`ActiveTaxCalculationEngineTest.java`），新增針對年薪 2,500 萬、2,600 萬、2,700 萬之高所得住民稅階梯扣除整合測試。
  3. 執行 `mvn clean install` 確保全模組綠燈通過。

---

## 六、驗證與測試規格清單（Test Specifications）

| 測試案例 | 輸入條件（年薪 / 合計所得） | 預期所得稅基礎控除 | 預期住民稅基礎控除 | 驗證目標 |
| :--- | :--- | :---: | :---: | :--- |
| **TC-01: 常態高年薪基準** | 年薪 2,500 萬（所得 2,305 萬，2026年） | 620,000 円 | **430,000 円** | 驗證 2,400 萬以下維持 43 萬滿額 |
| **TC-02: 獎金浮動第一階斷崖** | 年薪 2,600 萬（所得 2,405 萬，2026年） | 320,000 円 | **290,000 円** | 驗證跨越 2,400 萬時住民稅降至 29 萬 |
| **TC-03: 獎金浮動第二階斷崖** | 年薪 2,650 萬（所得 2,455 萬，2026年） | 160,000 円 | **150,000 円** | 驗證跨越 2,450 萬時住民稅降至 15 萬 |
| **TC-04: 高所得完全排富歸零** | 年薪 2,700 萬（所得 2,505 萬，2026年） | 0 円 | **0 円** | 驗證跨越 2,500 萬時所得稅與住民稅雙雙歸零 |
| **TC-05: 年金退職雜所得對齊** | 退休年金所得 2,500 萬 | 依年金所得查表 | 依年金所得查表 | 驗證長線 35 年退休期模型計算一致性 |

---

## 七、交付物與後續追蹤

- **新建計畫路徑**：`finance-hub-backend/todo/planned/Plan-16-inhabitant-tax-basic-deduction-high-income-strategy.md`
- **對應關聯**：
  - 解除 [Question-118](../pending%20fix/question-118.md) 之範疇膨脹疑慮，讓 118 號專注於所得稅基礎控除結案歸檔。
  - 對齊 [Question-120](../pending%20fix/question-120.md) 之雙軌策略工廠標準架構。

---

## 八、執行記錄與驗證現況（Execution Status）

- **執行策略**：使用者核定「選項 1（一次性完整實作與覆蓋）」
- **實作狀態**：✅ 已完成（Phase 1 + Phase 2 全部交付）
- **修改檔案清單**：
  1. `BasicDeductionStrategy.java`：擴充 `calculateInhabitantTaxDeduction` 方法契約
  2. `LegacyBasicDeductionStrategy.java`：實作地方稅法第314條之2第2項階梯（43萬/29萬/15萬/0円）
  3. `Reiwa7BasicDeductionStrategy.java`：實作地方稅法第314條之2第2項階梯
  4. `Reiwa8And9BasicDeductionStrategy.java`：實作地方稅法第314條之2第2項階梯
  5. `Reiwa10OnwardsBasicDeductionStrategy.java`：實作地方稅法第314條之2第2項階梯
  6. `BasicDeductionStrategyFactory.java`：支援 `getIncomeTaxStrategy` 與 `getInhabitantTaxStrategy` 前年所得課稅時差雙軌路由
  7. `ActiveTaxCalculationEngine.java`：薪資與退休年金全面切換為動態住民稅基礎控除策略派發
  8. `BasicDeductionStrategyTest.java`：新增住民稅階梯與工廠雙軌路由單元測試
  9. `ActiveTaxCalculationEngineTest.java`：新增高年薪 2,600 萬與 2,700 萬之階梯遞減與完全排富整合測試

