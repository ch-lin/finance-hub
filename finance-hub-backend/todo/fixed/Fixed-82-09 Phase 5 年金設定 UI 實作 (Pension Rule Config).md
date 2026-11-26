# Phase 5 年金設定 UI 實作 (Pension Rule Config)

本計畫專注於實作 `Fixed-82.md` 所定義之 Phase 5「前端 UI 實作 (hub-ui)」需求，參考 `Issue-36.md` 的實作脈絡，將年金設定 (Pension Rule) 整合進既有的前端架構中，並實作必要的防護機制。

## User Review Required

> [!IMPORTANT]
> 此計畫將會在 `hub-ui` 新增對應的 API Hooks 與表單元件。請確認下方預計的 Zod Schema 驗證邏輯與 API 互動行為（尤其是 Cache Invalidation）是否符合您的預期。
> 若無其他問題，點擊 Proceed 按鈕後我將開始實作。

## Proposed Changes

以下列出所有預計新增或修改的前端檔案（目錄位於 `hub-ui/hub-ui/src/app/finance/configs/general/`）。

---

### API Hooks 實作

#### [NEW] [`hooks/use-pension-rule-configs.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts)
新增專屬的 React Hook，負責與 `/api/finance/configs/pension-rule` 互動。
- **資料驗證 (Zod Schema)**：
  - 定義 `pensionRuleConfigSchema`，包含 `name` (字串)、`enabled` (布林值)、`maxBasicPensionAmount`、`maxBasicPensionMonths`、`kouseiMultiplierPerMille`、`kouseiSalaryCapMonthly`、`kouseiBonusCapPerPayment` 等數字欄位。
- **狀態管理與 Cache Invalidation**：
  - 在 `POST` / `PATCH` / `DELETE` 成功後，呼叫 `fetchPensionRuleProfiles()` 以觸發全局重新抓取，確保畫面上「啟用中」狀態 (`enabledConfigName`) 能即時同步，防範快取與狀態不一的問題。

---

### 表單與 UI 元件實作

#### [NEW] [`components/pension-rule-config-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/pension-rule-config-panel.tsx)
建立年金設定檔的總成 Panel。
- **Delete 保護 (預設值刪除保護)**：判斷當前載入的 Config name，若是 `default`，則將 Delete 按鈕設定為 `disabled` 並隱藏或顯示提示，阻止使用者送出刪除請求。
- **千分位輸入 (Thousand Separators)**：針對 `maxBasicPensionAmount`、`kouseiSalaryCapMonthly` 與 `kouseiBonusCapPerPayment` 欄位，使用共用的 `ThousandSeparatorInput` 元件，讓使用者輸入時能帶有千分位遮罩，防範金額輸入錯誤。

---

### 頁面整合

#### [MODIFY] [`page.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/page.tsx)
- 在現有基於 `Tabs` 佈局的頁面中，引入 `usePensionRuleConfigs` hook。
- 在 `TabsList` 中增加一個新的標籤 `<TabsTrigger value="pension-rules">Pension Rules</TabsTrigger>`。
- 增加對應的 `<TabsContent value="pension-rules">`，並在其中置入 `<PensionRuleConfigPanel configsHook={pensionRuleConfigs} />`。
- 確保切換到此 Tab 時，狀態獨立且不與 System、Tax、Transaction 發生干擾。

## Verification Plan

### Manual Verification (由使用者執行)
1. **介面整合驗證**：進入 General Config 頁面，確認可看到新的 "Pension Rules" 分頁籤。
2. **千分位元件驗證**：切換至 Pension Rules 並選擇或建立配置，確認 `Max Basic Pension Amount` 欄位支援千分位顯示（如 `816,000`），且儲存後正確寫入後端。
3. **預設值保護驗證**：選擇名為 `default` 的設定檔，確認其刪除 (Delete) 按鈕處於反灰禁用狀態。
4. **Cache Invalidation 驗證**：點擊其他設定檔並設為 Enable 後儲存，確認原先 Enable 的設定檔狀態自動變為未啟用。

---

# Phase 5: PensionRuleConfig UI Implementation

The PensionRuleConfig UI has been successfully implemented and integrated into the frontend application according to Fixed-82-09 Phase 5 specifications.

## What Was Accomplished

1.  **API Hooks (`use-pension-rule-configs.ts`)**
    *   建立了專屬於 Pension Rule Config 的自訂 React Hook。
    *   使用 Zod 定義了 `pensionRuleConfigSchema`，包含 `name`、`enabled`、`maxBasicPensionAmount` 及其他四個新年金基準欄位。
    *   實作了完整的 CRUD 功能 (fetch profiles, fetch details, save, delete)。
    *   **Cache Invalidation 防護**：在 Save 與 Delete 成功後，主動呼叫 `fetchPensionRuleProfiles()` 重新抓取全局的 Profile 清單，確保「啟用中 (`enabledConfigName`)」的狀態能即時更新，防範其他分頁狀態不一致的盲點。

2.  **表單與 UI 元件 (`pension-rule-config-panel.tsx`)**
    *   建立了新的 `PensionRuleConfigPanel`，採用與 `TaxRuleConfigPanel` 一致的排版佈局。
    *   **Delete 刪除防護**：判斷若選中的是 `default` 配置，則禁用並以 Tooltip 隱藏 Delete 按鈕，避免預設值被誤刪。
    *   **Thousand Separators 防護**：對於 `maxBasicPensionAmount`、`kouseiSalaryCapMonthly` 等大額數字欄位，導入了原先實作的 `ThousandSeparatorInput` 元件，讓使用者輸入時能即時看到千分位（例如輸入 `816000` 會顯示為 `816,000`），避免輸入錯誤。

3.  **頁面整合 (`page.tsx`)**
    *   在 `app/finance/configs/general/page.tsx` 中引入了 `usePensionRuleConfigs` hook。
    *   在 Tabs 元件中新增了 `pension-rules` 分頁籤 (TabTrigger)。
    *   嵌入 `PensionRuleConfigPanel`，並確保狀態與其他設定 (System / Tax / Transaction) 完全隔離。

## Verification

To verify these changes:
1.  Navigate to the Configurations page on the UI.
2.  Switch to the **Pension Rules** tab.
3.  Check that the "Max Basic Pension Amount" field supports thousands separator (e.g., displays 816,000).
4.  Select the `default` profile and verify the "Delete" button is grayed out and disabled.
5.  Create a new profile, set it to "Enabled" and save. Verify that the cache invalidation triggers and the new profile becomes the active one correctly.
