# 實作計畫：Fixed-62 階段五 (Finance Hub Tax Rule Config UI)

本計畫專注於實作 `Fixed-62.md` 所定義之 Phase 5「前端 UI 實作 (hub-ui)」需求，並涵蓋文件中所提的 6 大防護盲點。

## User Review Required

> [!IMPORTANT]
> 此計畫涉及新增的 API 邏輯與介面操作，因攸關財務數字的精確度（如 `%` 轉換與千分位顯示），請確認以下計畫中提及的 Zod Schema 攔截與表單元件轉換策略是否符合預期。
> 
> **查證結果更新**：
> 1. **Tabs 元件**：已確認 `hub-ui` 使用 `shadcn/ui`，將直接引用 `@/components/ui/tabs` 進行重構。
> 2. **千分位輸入元件**：經查證，目前的 `SystemConfigPanel` 僅使用原生的 `<Input type="number">`。為了解決 Fixed-62 中提到的「千分位盲點」並配合統一 UX 的原則，我將基於現有的 `Input` 元件封裝一個簡單的 `ThousandSeparatorInput` 元件，供後續財務相關欄位統一使用。

## Proposed Changes

以下列出所有預計新增或修改的前端檔案（目錄位於 `hub-ui/hub-ui/src/app/finance/configs/general/`）。

---

### API Hooks 實作

#### [NEW] [`hooks/use-tax-rule-configs.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts)
新增專屬的 React Hook，負責與 `/api/finance/configs/tax-rule` 互動。
- **Cache Invalidation (盲點 6)**：在 `PATCH` 方法中，當 Payload 包含 `enabled: true` 時，必須在 `onSuccess` 呼叫 `queryClient.invalidateQueries({ queryKey: ['tax-rule-configs'] })`（或其他對應的 Key），以觸發全局重新抓取，確保畫面上其他設定檔的「啟用中」狀態能同步消失。

---

### 表單與 UI 元件實作

#### [NEW] [`components/tax-rule-config-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx)
建立稅制設定檔的總成 Panel。
- **Delete 保護 (盲點 5)**：判斷當前載入的 Config name，若是 `default`，則將 Delete 按鈕設定為 `disabled` 並顯示提示（例如 Tooltip），阻止使用者送出刪除請求。
- **千分位輸入 (盲點 4)**：對於基礎控除（`basicDeductionIncomeTax` 等）與受扶養親屬門檻，使用支援千分位的 Input 元件取代原生 `<input type="number">`。

#### [NEW] [`components/tax-bracket-editor.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/tax-bracket-editor.tsx)
建立子元件以處理動態的 `List<TaxBracket>` 表格編輯。
- **全量更新防護 (盲點 1)**：在表單的根層級管理一個整體的 `brackets` 陣列狀態。即使 UI 分為「薪資扣除」、「所得稅率」等不同區塊，Submit 時都是把整個 `brackets` 一併透過 API 傳送，避免後端 `clearTaxBrackets()` 清空未送出的類別。
- **Null Value 處理 (盲點 2)**：對於 `maxSalary` 欄位，增加一個「無上限」的 Checkbox 或判斷空字串邏輯。在送出 Request 前的資料清洗階段，明確將其轉換為 JSON 的 `null`。
- **Multiplier 單位轉換 (盲點 3)**：欄位顯示為 `%`（如顯示 10）。
  - **讀取時**：將 API 取得的 `multiplier` (如 0.10) 乘以 `100`。
  - **寫入時**：在送出 API 前將表單數值除以 `100`。

---

### 頁面整合

#### [MODIFY] [`page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/page.tsx)
- 將現有過長的垂直排列佈局改寫為 Tabs 介面。
- 引入並註冊三個分頁：
  1. System Config (使用既有 `SystemConfigPanel`)
  2. Transaction Source Config (使用既有 `TransactionSourceConfigPanel`)
  3. **Tax Rule Config (使用新增的 `TaxRuleConfigPanel`)**
- 確保各個 Tab 切換時，API 呼叫與狀態不會互相干擾。

## Verification Plan

### Manual Verification
1. **介面驗證**：進入 General Config 頁面，確認已呈現 Tabs 佈局，且可切換至 Tax Rule Config。
2. **千分位與單位驗證**：在表單中輸入 `1950000`，確認顯示為 `1,950,000`。輸入稅率 `10`%，儲存後再讀取，確認介面維持 `10`%，且 Backend 接收到的是 `0.10`。
3. **無上限邏輯驗證**：新增一個級距，將上限設為「無上限」或留空並儲存，確認無發生欄位必填錯誤，且 Backend 正確接收到 `null`。
4. **全量更新防護驗證**：修改「所得稅率」區塊的級距並儲存，重新整理後，確認「住民稅率」等其他區塊的級距資料仍完好無缺。
5. **預設值保護與快取驗證**：
   - 確認 `default` 設定檔的刪除按鈕無法點擊。
   - 點擊其他設定檔的「啟用」按鈕，確認前一個啟用中的設定檔狀態能即時更新為未啟用。

# Phase 5: TaxRuleConfig UI Implementation

The TaxRuleConfig UI has been successfully implemented and integrated into the frontend application according to Fixed-62 Phase 5 specifications.

## What Was Accomplished

1.  **Shared UI Components**
    *   Created `ThousandSeparatorInput` to standardize numeric input handling across the application, converting between `toLocaleString` string values for the user and raw numbers for the form state.

2.  **State Management (Hooks)**
    *   Implemented `use-tax-rule-configs.ts` for managing all state related to tax rules.
    *   Handled the backend conversion of multipliers (converting between decimal for the API and percentage for the UI).
    *   Implemented cache invalidation behavior: After a save or delete operation, the hook refetches the list of profiles so the "active/enabled" state remains consistent across the UI without a manual refresh.

3.  **UI Panels & Editors**
    *   Created `TaxBracketEditor` to handle editing `maxSalary`, `multiplier`, and `offset` for specific bracket types (e.g., Employment Income Deduction, Income Tax Rate). Included "Unlimited" handling (Null values) directly into the UI state.
    *   Created `TaxRuleConfigPanel` which manages the overall form using `react-hook-form` and `zod` for validation.
    *   Safeguarded against deleting the `"default"` tax rule configuration.

4.  **Page Integration**
    *   Refactored `page.tsx` (`app/finance/configs/general/page.tsx`) to use a generic `Tabs` layout. The view is now split into three cleanly separated tabs: "System Config", "Tax Rules", and "Transaction Sources", dramatically improving the layout organization.

## Verification

To verify these changes:
1.  Navigate to the Configurations page on the UI.
2.  Switch to the **Tax Rules** tab.
3.  Try creating a new profile. The form should handle the comma-separated numeric values and correctly parse the percentage inputs.
4.  After saving the profile as "Enabled", notice how it instantly becomes the single active configuration, reflecting the cache invalidation requirement.
