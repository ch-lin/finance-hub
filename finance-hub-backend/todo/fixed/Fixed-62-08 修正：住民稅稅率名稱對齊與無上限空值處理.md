# 修正 Fixed 62-07：住民稅稅率名稱對齊與無上限空值處理

本實作計畫旨在解決 Fixed 62-07 所指出的兩個問題：
1. 前後端對於「住民稅稅率」的分類名稱不一致（前端為 `RESIDENT_TAX_RATE`，後端為 `INHABITANT_TAX_RATE`）。
2. 後端 `TaxBracketRequest` 嚴格限制 `maxSalary` 不能為空值（`@NotNull`），導致前端傳送「無上限（`null`）」時被拒絕。

## User Review Required

無破壞性變更，純粹修正前端列舉值與後端驗證規則，以符合原本的業務邏輯設計。請確認以下要修改的檔案清單與絕對路徑是否正確。

## Open Questions

無。

## Proposed Changes

---

### Frontend (UI)
將前端三個使用到 `RESIDENT_TAX_RATE` 的地方，全部更名為 `INHABITANT_TAX_RATE` 以與後端保持一致。

#### [MODIFY] [use-tax-rule-configs.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts)
- 將 `z.enum` 內的 `'RESIDENT_TAX_RATE'` 修改為 `'INHABITANT_TAX_RATE'`。

#### [MODIFY] [tax-bracket-editor.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/tax-bracket-editor.tsx)
- 將 `TaxBracketEditorProps` 的 `bracketType` 型別定義中的 `'RESIDENT_TAX_RATE'` 修改為 `'INHABITANT_TAX_RATE'`。

#### [MODIFY] [tax-rule-config-panel.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx)
- 在傳遞給住民稅率 `TaxBracketEditor` 的 `bracketType` 屬性時，將 `"RESIDENT_TAX_RATE"` 改為 `"INHABITANT_TAX_RATE"`。

---

### Backend (API)
允許 `maxSalary` 接收空值（`null`），以支援「無上限」的級距設定。

#### [MODIFY] [TaxBracketRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketRequest.java)
- 移除 `maxSalary` 屬性上方的 `@NotNull` 註解。

## Verification Plan

### Manual Verification
1. 啟動前後端服務。
2. 前往「稅制設定」畫面，測試新增一筆「住民稅稅率」的級距表，並勾選「無上限」。
3. 點擊「Save Settings」，確認可以成功儲存，且重整後資料能正確對應並顯示。

# 修正 Fixed 62-07 成果報告

我已經完成了 Fixed 62-07 中所列出的兩項修正。以下是修改的細節：

## 1. 統一「住民稅稅率」前後端分類名稱
前端原本定義為 `RESIDENT_TAX_RATE`，後端定義為 `INHABITANT_TAX_RATE`，造成資料不匹配無法儲存。

我修改了以下三個前端檔案，將 `RESIDENT_TAX_RATE` 替換為 `INHABITANT_TAX_RATE`，使其與後端列舉（Enum）完全對齊：
* [`use-tax-rule-configs.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts) 的表單驗證 `z.enum`
* [`tax-bracket-editor.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/tax-bracket-editor.tsx) 的型別宣告
* [`tax-rule-config-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx) 實際呼叫編輯器元件時傳入的參數

## 2. 允許級距表設定「無上限」
前端在使用者勾選「無上限」時，會將上限金額（`maxSalary`）設為 `null` 傳給後端。原本後端 API 接收端不允許空值，會整批拒絕。

我移除了後端資料傳輸物件的 `@NotNull` 驗證：
* 移除 [`TaxBracketRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketRequest.java) 中 `maxSalary` 屬性上的 `@NotNull` 註解。

這樣後端的計算邏輯（將空值視為無上限）就能正常被觸發了。

## 後續驗證建議
請重新啟動前後端服務，並開啟「稅制設定」畫面，嘗試修改「住民稅稅率」以及將任一級距設為「無上限」並儲存，確認新數字能否成功套用並顯示。
