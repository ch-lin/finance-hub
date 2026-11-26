# Plan 17: 全系統核心業務邏輯與邊界單元測試全覆蓋計畫 (System-wide Unit Test Coverage & Hardening Plan)

## 🎯 背景與目標 (Background & Goals)

系統在經歷多輪快速功能迭代、日本稅制與年金法規重構、多筆房貸模型擴充後，累積了顯著的測試債（Test Debt）。目前許多核心邏輯缺乏專屬的單元測試（Unit Test），僅依賴少數粗粒度的大型整合測試（如 `AssetSimulationEngineTest`）間接覆蓋。

原在 `asset-simulation-review-round1.md` 標記為 POSTPONED 的 **L2**（`SimulationParameterResolver` 優先順序解析缺乏專屬單元測試），僅是此系統性問題的冰山一角。

**核心執行方針**：
在法規修正與商業邏輯頻繁變動（如所得稅/住民稅基礎控除階梯、配偶控除雙軌時差、房貸減稅年限等）的過渡期間，若貿然撰寫大量細粒度測試，容易因規則調整而產生高昂的測試維護成本與假性紅燈。因此定調：
> **「於所有法規修正與業務邏輯全數完成並定案之後，一併全面補齊整個系統的單元測試，建立堅固的回歸防護網。」**

---

## 📋 涵蓋範圍與模組清單 (Scope & Modules)

本計畫將對後端所有計算核心、解析器與決策邏輯進行無死角的單元測試補齊：

### 1. 參數解析與優先級仲裁模組 (Parameter Resolvers & Fallbacks)
- **`SimulationParameterResolverTest`（原 L2 完整落實）**：
  - 針對三層優先權（`Override` > `UserProfile` > `FinanceHubConfig`）撰寫完整真值表測試：
    - `resolveRetirementAge`：全組合驗證（僅 Override、僅 Profile、僅 Config、三者皆有、皆空時拋出例外等）。
    - `resolvePensionStartAge`：三層優先權與邊界測試。
    - `resolveLifeExpectancy`：三層優先權與系統保底值（85 歲）驗證。
    - `resolveTaxAdvantagedWithdrawalAge`：法定解鎖年齡下限（DC/iDeCo 60~75 歲鉗制）與覆寫優先級驗證。
    - `resolveIdecoUnlockAge` / `resolveDcUnlockAge`：法規下限（60 歲）防呆測試。

### 2. 稅務與社會保險計算引擎 (Tax & Social Insurance Engines)
- **基礎控除與配偶控除工廠 (`BasicDeductionStrategyFactoryTest`, `SpouseDeductionStrategyFactoryTest`)**：
  - 驗證各年度（2020 以前 vs 2020 以後）所得稅與住民稅基礎控除派發。
  - 驗證高所得受薪階級（2,400 萬～2,500 萬）三級階梯遞減歸零邏輯。
  - 驗證住民稅「前年所得課稅時差」雙軌機制。
- **住宅貸款減稅策略 (`MortgageTaxDeductionStrategyTest`)**：
  - 2022 以前（1% / 40萬）、2022~2023（0.7%）、2024~2025、2026 以後之所得稅與住民稅扣抵上限。
  - 育兒家庭與年輕夫婦（子育て世帯）加碼條件判定。
- **退職所得與年金扣除 (`RetirementTaxCalculatorTest`, `PensionTaxCalculatorTest`)**：
  - 勤續年數（20 年以下 40 萬/年、20 年以上 70 萬/年）進位與退職控除金額。
  - 公的年金等控除額（65 歲前後雙軌門檻）計算精確度。

### 3. 房貸與提前還款計算模組 (Mortgage & Repayment Calculators)
- **`MortgageCalculatorTest` & `MortgageCalculatorUtilTest`**：
  - 等額本息（Equal Payment）與等額本金（Equal Principal）月付額公式驗證。
  - 5 年規則與 125% 緩衝上限機制。
  - 獎金加成還款（Bonus Payment）月份金額與利息分攤精確度。
  - 提前還款雙策略驗證：
    - 縮短年限（`REDUCE_TERM`）：月付額不變、期數縮短。
    - 減少月付金額（`REDUCE_PAYMENT`）：剩餘期數不變、重新計算月付額。
  - 多筆房貸 Map 結構下，單筆提前還款不影響其他筆房貸的獨立性測試。

### 4. 資產模擬與帳戶投影模組 (Asset Simulation & Projectors)
- **`TaxAdvantagedAccountProjectorTest`**：
  - NISA 成長投資枠（240 萬/年）、定期定額枠（120 萬/年）、合計（360 萬/年）與終生 1,800 萬上限節流。
  - iDeCo / 企業型 DC 提繳法定上限防呆與節流。
- **`IncomeExpenseCalculatorTest` & `InvestmentReturnCalculatorTest`**：
  - 各年度資產複利成長、現金流收支赤字時之資金處置順序與破產判定條件。

### 5. 最佳化演算法核心與映射模組 (Optimization Engines)
- **`ChromosomeMapperTest`**：
  - 染色體基因值與 `SimulationOverrides` 雙向轉換精確度。
  - 鎖定基因（Locked Genes）不被求解器竄改的不變量測試。
- **`GlobalOptimizerServiceImplTest`**：
  - 搜尋空間組合數估算公式（`calculateSearchSpace`）精確度，確保防禦閥值（`MAX_GRID_SEARCH_SPACE` 1,000 / 2,000）正確阻擋維度爆炸。
  - 多房貸情境下 `FORCE_GRID` 正確拋出 `OptimizationNotSupportedException` 並引導走 GA。

---

## 🛡️ 品質標準與驗收條件 (Acceptance Criteria)

1. **零外部依賴與極速執行**：
   - 所有單元測試皆為純 In-Memory / POJO 測試，禁止掛載完整 Spring Boot Context 或資料庫 round-trip。
   - 全套單元測試套件執行時間需控制在 10 秒以內。
2. **高覆蓋率門檻**：
   - 核心計算服務與工具類別（`calculator/*`, `utils/*`, `strategy/*`）之行覆蓋率（Line Coverage）$\ge 90\%$，分支覆蓋率（Branch Coverage）$\ge 85\%$。
3. **無死角邊界與例外覆蓋**：
   - 涵蓋所有 `null`、負數、極值、法定上下限邊界條件及預期的例外拋出（Fail-Fast）。
