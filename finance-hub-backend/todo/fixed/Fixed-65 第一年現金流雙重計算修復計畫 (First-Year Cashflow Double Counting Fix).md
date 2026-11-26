# 第一年現金流雙重計算修復計畫 (First-Year Cashflow Double Counting Fix)

這份計畫針對「模擬器第一年（當年）將已發生的 YTD (Year-To-Date) 淨利重複存入銀行餘額」的商業邏輯缺陷進行修正。此缺陷導致模擬器在第一年底的現金結餘被高估。

## 背景與問題回顧

在先前的修復中，我們成功修正了「第一年預估總薪水與稅金」的計算方式：
- 系統會抓取 `YTD 已經發生的薪資` + `預估剩下月份的薪資` = **今年度總薪水**。
- 這個總薪水確保了**年末調整 (Year-end Tax Adjustment)** 與所得稅級距的計算是完全正確的。

**殘留的商業邏輯缺陷 (Double Counting)**：
系統計算出「今年度總稅後淨利 (Full-Year Net Income)」後，在 `applyCashflowAndSettlement` 階段，直接將這整筆淨利扣除剩餘月份的生活費與房貸後，加進了「現在的銀行餘額」。
然而，銀行餘額**在模擬器啟動的當下，就已經包含了 YTD 期間所存下來的錢**。將 YTD 淨利再次加入銀行餘額，造成了嚴重的雙重計算。

## 修正策略與邏輯設計 (採用薪資單稅後淨利)

為了保留正確的稅務計算，同時避免現金流重複計算，並且採用最精準的數字，我們將邏輯拆分為兩層：

1. **Tax & Income View (稅務與年度帳本)**：維持現狀。`state.getNetIncome()` 代表「全年度預估稅後淨利」，這是經過完美稅率公式與年末調整預估算出的數字。
2. **Cashflow Settlement View (現金流結算)**：要加進銀行存款的「淨現金流」，只能是**未來剩下的幾個月**。
   公式為：`剩下的真實淨利 = 預估全年度稅後淨利 - YTD 已經真實領到的稅後淨利 (Take-home Pay)`

**精準度提升 (User 的絕佳提議)**：
我們不手動去減各項稅金，而是直接讀取 `SalaryRecord` 上的 `netAmount` 欄位。這代表我們用「真實匯入銀行的數字」來抵銷預估淨利，這樣就算薪資單上有其他特殊扣除額，邏輯也不會出錯！

---

## 預計修改檔案 (Proposed Changes)

嚴格限制修改以下檔案，其餘檔案絕對不予更動。

### Simulator Core

#### [MODIFY] [SimulationContext.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
- **修改內容**：
  新增一個變數 `private long ytdNetAmount;`，用來儲存今年度已經真實領到的稅後淨利。Lombok `@Data` 會自動產生 getter/setter。

#### [MODIFY] [SalaryContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java)
- **修改內容**：
  在計算 YTD 薪資的迴圈中，新增加總 `netAmount`，並寫入 `SimulationContext`。

  ```java
  // 1. 宣告 ytdNetAmount
  long ytdNetAmount = 0L;
  
  for (SalaryRecord sr : salaryRecords) {
      if (sr.getPaymentDate() != null && sr.getPaymentDate().getYear() == currentYear) {
          // ... 原本的加總邏輯 (ytdGross 等)
          
          // 2. 爬取薪資單上的稅後淨利
          Long netAmount = sr.getNetAmount();
          ytdNetAmount += (netAmount != null ? netAmount : 0L);
      }
      // ...
  }
  
  // 3. 在迴圈結束後，將其寫入 Context
  context.setYtdNetAmount(ytdNetAmount);
  ```

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **目標方法**：`applyCashflowAndSettlement`
- **修改內容**：
  ```java
  long fullYearNetIncome = state.getNetIncome();
  long ytdNetIncome = 0;

  if (context.getYear() == context.getCurrentYear()) {
      ytdNetIncome = context.getYtdNetAmount();
  }

  long remainingNetIncome = fullYearNetIncome - ytdNetIncome;
  long netSavings = remainingNetIncome - state.getAnnualExpenses();
  ```
  這取代了原本單純 `state.getNetIncome() - state.getAnnualExpenses()` 的算法。

---

## 驗證計畫 (Verification Plan)

### 手動驗證 (Manual Verification)
1. **編譯與啟動**：確認後端專案可成功編譯並啟動無錯誤。
2. **前端資料驗證**：進入 Sandbox 模擬器介面，觀察第一年（2026年）結算後的 Net Worth 曲線。曲線應該明顯修正，不再出現因雙重計算導致的資金異常暴漲。
