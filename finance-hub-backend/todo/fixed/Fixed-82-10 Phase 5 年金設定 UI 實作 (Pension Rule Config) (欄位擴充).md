# Fixed 82-10 Phase 5 年金設定 UI 實作 (Pension Rule Config) (欄位擴充)

本計畫專注於實作 Phase 5 中前端 UI (hub-ui) 針對年金設定的擴充需求，包含在前端 Hook (`use-pension-rule-configs.ts`) 以及畫面元件 (`pension-rule-config-panel.tsx`) 中，加上新擴充的 4 個年金屬性，並實作必要的驗證與顯示元件 (如 `ThousandSeparatorInput`)。

## Proposed Changes

### API Hooks 實作擴充

#### [MODIFY] [`use-pension-rule-configs.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts)
- **資料驗證 (Zod Schema)**：
  - 在 `pensionRuleConfigSchema` 中加入以下四個欄位定義：
    - `maxBasicPensionMonths: z.number().min(0).default(480)`
    - `kouseiMultiplierPerMille: z.number().min(0).default(5.481)`
    - `kouseiSalaryCapMonthly: z.number().min(0).default(650000)`
    - `kouseiBonusCapPerPayment: z.number().min(0).default(1500000)`
- **狀態管理**:
  - 更新 `handlePensionNewProfileClick` 中初始化資料 (`setPensionConfigDetails`) 的物件，補上這四個新屬性的預設值。

### 表單與 UI 元件實作擴充

#### [MODIFY] [`pension-rule-config-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/pension-rule-config-panel.tsx)
- **增加新欄位表單區塊**:
  - 於 `maxBasicPensionAmount` 欄位下方，依次追加以下輸入框：
    - `Max Basic Pension Months`：使用一般的 `<Input type="number" />`，並搭配 `{...register('maxBasicPensionMonths', { valueAsNumber: true })}`。
    - `Kousei Multiplier Per Mille`：使用 `<Input type="number" step="0.001" />` 支援小數，搭配 `{...register('kouseiMultiplierPerMille', { valueAsNumber: true })}`。
    - `Kousei Salary Cap Monthly`：使用 `<ThousandSeparatorInput>`，透過 `<Controller>` 管理狀態，以支援千分位。
    - `Kousei Bonus Cap Per Payment`：同上使用 `<ThousandSeparatorInput>`。
- **預設值更新**:
  - 在 `useForm` 的 `defaultValues` 補上這四個欄位的預設值。

## Verification Plan

### Manual Verification (由使用者執行)
1. **千分位元件驗證**：進入 General Config 頁面的 Pension Rules 分頁籤，確認 `Kousei Salary Cap Monthly` 與 `Kousei Bonus Cap Per Payment` 欄位支援千分位顯示（如 `650,000`）。
2. **小數點元件驗證**：確認 `Kousei Multiplier Per Mille` 可正常輸入帶有小數的數值（如 `5.481`）。
3. **儲存驗證**：點擊 Save Settings 儲存後，重新整理頁面，確認設定值成功保存並重新載入顯示正確。

# Phase 5 年金設定 UI 實作 (Pension Rule Config) 擴充 Walkthrough

我們已經成功完成 Phase 5 的 UI 欄位擴充實作，在 `Pension Rule Config` 頁面加入了 4 個新的年金設定欄位。

## 完成項目

### 1. API Hooks 狀態管理擴充
- [`use-pension-rule-configs.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts)
  - 於 `pensionRuleConfigSchema` 中成功加入了 4 個新欄位的 Zod 驗證及預設值設定：
    - `maxBasicPensionMonths` (預設 480)
    - `kouseiMultiplierPerMille` (預設 5.481)
    - `kouseiSalaryCapMonthly` (預設 650000)
    - `kouseiBonusCapPerPayment` (預設 1500000)
  - 更新了建立新設定檔 (`handlePensionNewProfileClick`) 時的預設物件。

### 2. UI 元件擴充
- [`pension-rule-config-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/pension-rule-config-panel.tsx)
  - 在既有的 `Max Basic Pension Amount` 下方成功加入 4 個新欄位的 UI 元件。
  - `Max Basic Pension Months` 使用了原生 `<Input type="number" />`。
  - `Kousei Multiplier Per Mille` 支援了小數點輸入 `<Input type="number" step="0.001" />`。
  - `Kousei Salary Cap Monthly` 與 `Kousei Bonus Cap Per Payment` 成功實作並套用了 `<ThousandSeparatorInput>` 來支援千分位顯示與自動驗證。

---

## 後續驗證

> [!IMPORTANT]
> 遵照「嚴禁自動執行命令列」以及「由您親自測試」的規定，請您執行下列手動驗證來確保 UI 正常運作：
> 
> 1. 請重啟或回到前端畫面，重新整理頁面。
> 2. 進入 General Config 頁面的 Pension Rules 分頁籤。
> 3. 確認 `Kousei Salary Cap Monthly` 與 `Kousei Bonus Cap Per Payment` 欄位是否支援千分位顯示（如 `650,000`）。
> 4. 確認 `Kousei Multiplier Per Mille` 是否可正常輸入小數點數值（如 `5.481`）。
> 5. 點擊 Save Settings 進行儲存，並重新載入，確認資料能被成功保存與回填顯示。
