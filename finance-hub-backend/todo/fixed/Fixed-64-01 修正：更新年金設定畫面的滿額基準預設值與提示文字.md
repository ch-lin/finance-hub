# 修正 Fixed 64：更新年金設定畫面的滿額基準預設值與提示文字

本實作計畫旨在解決 Fixed 64 所指出的問題：
先前在處理 Issue 24 時，後端已經將老年基礎年金滿額從令和6年度的 `816000`（81萬6千元）更新為令和7年度的新制數字 `831700`（83萬1700元）。但前端在「新增設定檔」時帶入的預設值，以及輸入框的提示文字（placeholder）中，仍然遺留著舊的 `816000`。這會導致使用者若未留意手動修改，就會存入過期的數字。

## User Review Required

這是純粹的常數數字更新，不涉及任何邏輯改動。請確認以下要修改的檔案清單與絕對路徑是否正確。

## Open Questions

無。

## Proposed Changes

---

### Frontend (UI)
將前端四個寫死 `816000` 的地方，全部更名為 `831700`。

#### [MODIFY] [use-pension-rule-configs.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts)
- 第 8 行表單驗證規則中，將 `maxBasicPensionAmount` 的 `.default(816000)` 修改為 `.default(831700)`。
- 第 93 行 `handlePensionNewProfileClick` 函式中，新增設定檔時預設帶入的 `maxBasicPensionAmount: 816000` 修改為 `maxBasicPensionAmount: 831700`。

#### [MODIFY] [pension-rule-config-panel.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/pension-rule-config-panel.tsx)
- 第 56 行 React Hook Form 的 `defaultValues` 中，將 `maxBasicPensionAmount: 816000` 修改為 `maxBasicPensionAmount: 831700`。
- 第 149 行 `ThousandSeparatorInput` 元件的 `placeholder` 中，將 `"e.g. 816000"` 修改為 `"e.g. 831700"`。

## Verification Plan

### Manual Verification
1. 啟動前端服務。
2. 前往「年金設定」畫面，點擊「New」（新增設定檔）按鈕。
3. 觀察畫面上「Max Basic Pension Amount (年金滿額基準)」欄位，確認其預設填入的數值是否已變更為 `831,700`。
4. 清空該欄位，確認背景的提示文字（placeholder）是否顯示為 `e.g. 831700`。

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

---

# 修正 Fixed 64 成果報告

我已經完成了 Fixed 64 中關於「年金設定滿額基準數字過期未更新」的修正：

## 將舊數值 816000 更新為 831700
我修改了以下前端檔案中的預設值與輸入框提示文字：
* [`use-pension-rule-configs.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts)：更新了表單驗證的預設值與點擊「新增設定檔」時帶入的預設物件屬性。
* [`pension-rule-config-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/components/pension-rule-config-panel.tsx)：更新了 React Hook Form 初始化的預設值，以及 `ThousandSeparatorInput` 的 `placeholder` 提示文字。

## 後續驗證建議
請開啟前端畫面並前往「年金設定」頁面：
1. 點擊「New」按鈕，確認「Max Basic Pension Amount (年金滿額基準)」欄位是否自動帶入正確的 `831,700`。
2. 刪除該欄位內的數值，確認背景提示字眼是否顯示為 `e.g. 831700`。
