# [Fixed-70] 修正 2026 年後房貸稅務扣除策略缺失與 fallback 數值矛盾

## 查證報告 (Gemini)

針對作者在 `Fixed-70.md` 中提出的疑問，我已查閱日本國稅廳與國土交通省的相關公告與稅制改正大綱：

1. **Pre-2022 → 2022-2023 邊界與費率：完全正確。**
   - 2022年 (令和4年度) 稅制改正確實將控除率從 1.0% 調降為 0.7%。
   - `Pre2022TaxStrategy` 的 1.0% 與 `Years2022To2023TaxStrategy` 的 0.7% 皆正確。
   - `Years2022To2023TaxStrategy` 寫的上限金額 (長期優良 35萬 / ZEH 31.5萬 / 省エネ 28萬 / 一般 21萬) 完全符合當時法規 (對應借入限度額 5,000萬 / 4,500萬 / 4,000萬 / 3,000萬 乘上 0.7%)。
2. **2024-2025 之後 (2026 年起) 是否已有新法案：是的，有新規則。**
   - 根據**令和8年度 (2026年度) 稅制改正**，住宅ローン控除的適用期限已被延長 5 年，至 **2030年 (令和12年) 12月31日**為止。
   - 2026年之後的規則強調中古住宅支援 (如省エネ中古住宅借入限度額提高、控除期間延長為13年) 以及子育て世帯 (育兒家庭) 的上乘措施。
   - 同時規定 2028年以後建築確認的省エネ基準適合住宅原則上排除適用，且災害紅區的住宅自 2028 年起也排除。

**結論：** 因為 2026 年起確實有新的法案與適用期限 (至 2030 年)，所以**不建議**直接將 `Years2024To2025TaxStrategy` 變為 open-ended (無上限)。我們應該新增一個對應 2026 年後規則的新策略區間，並將其設為 open-ended 或設定上限為 2030 年。

## 建議修改內容

基於以上查證，這是我提出的實作計畫：

### 1. 新增 2026 年後的策略類別

#### [NEW] `Years2026OnwardsTaxStrategy.java`
- 建立 `ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.Years2026OnwardsTaxStrategy`
- `isApplicable`: 適用於 `!occupancyDate.isBefore(LocalDate.of(2026, 1, 1))`。
- 此為最新的法案 (有效至2030年)，可暫時做成 open-ended，確保未來系統不會因為年份超出而找不到策略。
- 借入限度額與扣除率 (0.7%) 暫時可先沿用 2024-2025 年的預設級距實作 (因若系統未追蹤子育て世帯，則採用一般/保守上限)。

### 2. 移除矛盾的 fallback 瞎猜邏輯

既然 `TaxStrategyFactory.getStrategy` 有了 open-ended 的最新策略 (`Years2026OnwardsTaxStrategy`)，它就不會再回傳 `null`。因此我們可以安全地移除瞎猜邏輯。

#### [MODIFY] [`MortgageRecordServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java)
- 在 `validateAndApplyTaxLogic` 中，移除 `if (strategy == null)` 這個分支。確保所有貸款紀錄必定套用查找到的稅務策略。
- (註：如果找不到，其實應該拋出 Exception，但因為我們有了 open-ended 策略，所以正常情況必定找得到)。

#### [MODIFY] [`MortgageCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
- 在 `calculateTaxDeduction` 等方法中，移除 hardcode 的 `0.007` 與 `273_000L`。因為 `taxDeductionRate` 與 `maxTaxDeductionAmount` 在寫入 DB 前就必定會被 `MortgageRecordServiceImpl` 填上來自 Strategy 的正確數值，不可能為 null (若舊資料為 null 則視為 0)。
- **[補充電充]**：我們會在相對應的單元測試中（例如 `MortgageCalculatorTest` 或 `MortgageRecordServiceImplTest`）**補上或更新單元測試**，驗證舊資料若出現 `null` 的邊界情況時，系統能正確將其視為 0 並避免拋出 NullPointerException，同時驗證新策略是否正確計算。

## 驗證計畫

- 自動化測試：我將負責**撰寫與更新**單元測試，涵蓋移除 fallback 後的新邏輯及邊界情況，但我**不會親自執行測試**。
- 人工驗證：交由使用者 (您) 來親自執行 `mvn clean install` 或相關測試指令，以確認我撰寫的單元測試與修改的程式碼皆編譯通過並成功執行。

如果您同意，請 Approve 這個計畫，我將開始進行程式碼修改。
