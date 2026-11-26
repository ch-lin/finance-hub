# [Fixed] Question 136：住宅ローン控除 首年確定申告遞延機制之法規合規性與架構審查請求（Code Review）

## 背景說明

在進行住宅ローン控除相關修復（參見 `question-123.md`）與程式碼檢視過程中，我們針對 [MortgageCalculator.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) 中的首年確定申告邏輯（第 354-361 行）及其在整個模擬引擎（Pipeline 與 Tax Engine）中的端到端運作機制進行了深入探討：

```java
LocalDate refDate = m.getOccupancyDate() != null ? m.getOccupancyDate() : m.getStartDate();
boolean isFirstEligibleYear = (refDate != null && year == refDate.getYear());

if (isFirstEligibleYear) {
    // For the first year, deduction requires Kakutei Shinkoku (tax return) next year.
    // Do not apply it to this year's income tax via year-end adjustment.
    context.setPendingNextYearTaxRefund(context.getPendingNextYearTaxRefund() + deduction);
    deduction = 0L;
}

mortgageTaxDeductionThisYear = deduction;
```

> **備註（範圍收斂說明）**：
> 先前曾對「多筆房貸累加」與「住民稅策略比大小」有所疑慮，但經確認，在 [Fixed-04-01.md](file://finance-hub/finance-hub-backend/todo/fixed/Fixed-04-01.md) 架構下，後端已在 Service 層保證單一使用者僅能有一筆房貸具備減稅資格（`taxDeductionEligible == true`），且計算器內部已調整為單一賦值。因此該部分已收斂為系統已知的規格限制（不支援土地+建物雙重貸款）。
>
> **目前唯一需要核心審查的，是當初為了解決「首年時間差」所實作的確定申告機制（關聯 `Fixed-45-01`）及其跨模組連鎖效應。**

---

## 提請 Code Review 之核心議題：首年確定申告遞延邏輯對「住民稅轉扣」與「退稅現金流」之連鎖影響

### 1. 法定依據（SSOT）
- **國稅廳 No.1211-1、No.1213**：
  受薪階級入居第 1 年雖然無法透過公司「年末調整」辦理，而必須於次年 2/16~3/15 自行向國稅局辦理「確定申告」。
  確定申告之本質，係申報並抵扣**「入居年（第 1 年）當年度之所得稅」**，國稅局退還的款項為納稅人該年度預扣之稅款（過納付還付）。**因此退稅金額（還付金）嚴格以「納稅人入居年當年度之所得稅額（源泉徴収税額）」為上限，不可超過納稅額。**
- **總務省・《地方税法附則》第5條之4（個人住民税の住宅借入金等特別税額控除）**：
  入居第 1 年若所得稅額不足以完全扣除（所得税から控除しきれなかった額がある場合），**其扣除未盡之餘額，依法自動轉至入居次年度（第 2 年）之個人住民稅中進行稅額抵扣**（依入居年份適用 97,500 円或 136,500 円之上限）。

---

### 2. 現有系統之實作與連鎖反應質疑

現行系統在 [MortgageCalculator.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) 中，將首年的 `deduction` 強行設為 `0L`，並將計算所得之扣除額暫存於 `context.pendingNextYearTaxRefund`。這引發了以下兩個連鎖問題：

#### 質疑 A：依法享有的第 2 年住民稅轉扣是否被「實質沒收」？
- 在 [ActiveTaxCalculationEngine.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java) 中，計算引擎依據當年度傳入的 `mortgageTaxDeduction` 計算轉扣額：
  ```java
  actualMortgageDeduction = Math.min(baseIncomeTax, mortgageTaxDeduction);
  remainingMortgageDeduction = mortgageTaxDeduction - actualMortgageDeduction;
  ```
- 因為第 1 年 `mortgageTaxDeduction` 被強行歸 0，計算引擎判定 `remainingMortgageDeduction == 0`。
- **結果**：系統判定第 1 年完全沒有剩餘扣除額，導致納稅人依法可在第 2 年享有的**住民稅轉扣額（最高 9.75 萬 ~ 13.65 萬円）直接被歸零**。

#### 質疑 B：退稅款現金流是否脫離「已納稅額為限」的法定鐵律，造成「憑空超發」？
- 現有系統在 [YearlySimulationPipeline.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L80) 於次年度開頭，直接將 `context.pendingNextYearTaxRefund` 以現金全額發放：
  ```java
  context.setCurrentLiquidCash(context.getCurrentLiquidCash() + context.getPendingNextYearTaxRefund());
  ```
- **潛在問題**：此處發放的金額為房貸計算出的全額扣除額（例如 21 萬或 31.5 萬），**完全沒有檢驗使用者在第 1 年實際應繳納/已預扣的所得稅額**。
  - *範例情境*：若納稅人第 1 年應繳所得稅僅 5 萬円，其確定申告實質退稅款最多僅能領回 5 萬円（其餘 16 萬應轉扣住民稅）。
  - 現行實作直接發給納稅人 21 萬円現金，相當於憑空多發了 16 萬円流動現金，造成現金流與真實稅法脫節。

---

## 諮詢 Claude 之審查目標與問題清單

請 Claude 針對整個後端專案進行 Code Review（重點涵蓋 `MortgageCalculator.java`、`ActiveTaxCalculationEngine.java`、`YearlySimulationPipeline.java`、`SimulationContext.java` 及相關資料結構）：

1. **問題確認（Existence of Issues）**：
   - 上述質疑 A（首年 deduction 歸零導致住民稅轉扣遺失）與質疑 B（次年退稅現金未卡已繳所得稅上限），在端到端（E2E）執行流程中是否確實存在？
   - 抑或是在現有年度模擬粒度下，這屬於當初設計時已知且可接受的簡化（Trade-off）？
2. **影響範疇評估（Impact Assessment）**：
   - 若問題確實存在，對模擬引擎長期推估的「流動現金（Liquid Cash）」與「稅負平衡」影響幅度為何？
3. **建議處置方案（Recommendation）**：
   - Claude 建議維持現狀並註記為 Known Limitation，還是建議進行系統性重構？
   - 若需重構，如何在保持「首年退稅時間差」特性的同時，正確聯動所得稅實繳上限與第 2 年住民稅抵減？

---

## 歷史已結案關聯任務紀錄（Prior Closed Task）

- **[Fixed-45-01- 住宅ローン控除首年時間差修復計畫.md](file://finance-hub/finance-hub-backend/todo/fixed/Fixed-45-01-%20%E4%BD%8F%E5%AE%85%E3%83%AD%E3%83%BC%E3%83%B3%E6%8E%A7%E9%99%A4%E9%A6%96%E5%B9%B4%E6%99%82%E9%96%93%E5%B7%AE%E4%BF%AE%E5%BE%A9%E8%A8%88%E7%95%AB.md)**：
  - **當初之設計與結案理由**：
    此任務專門設計了「首年確定申告退稅時間差」。為了模擬「受薪階級首年無法年末調整、需次年確定申告」的現象，實作了：在 `MortgageCalculator` 中將首年 `deduction` 強制設為 `0L`，並暫存至 `context.setPendingNextYearTaxRefund(...)`；再於 `YearlySimulationPipeline` 次年開頭執行 `context.setCurrentLiquidCash(context.getCurrentLiquidCash() + context.getPendingNextYearTaxRefund())`。
  - **本次質疑待 Claude 解釋重點**：
    - 當初將首年 `deduction` 歸 0 時，是否有評估過對次年住民稅抵減計算的影響？
    - 當初未卡所得稅上限直接將扣除額發放為流動現金，是刻意的現金流近似處理，還是設計時的未竟之處？
