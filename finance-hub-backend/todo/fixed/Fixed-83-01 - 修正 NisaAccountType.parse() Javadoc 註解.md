# 修正 NisaAccountType.parse() Javadoc 註解 (Fixed 83-01)

根據 `issue-31.md` 的內容，我們需要修正 `NisaAccountType.parse()` 方法上的 Javadoc，將其預設為 GROWTH 類型時在「當年度可投入金額」面向的寬鬆性誠實交代清楚，以避免後續維護或 AI 閱讀時產生誤判。

## User Review Required

這是單純的 Javadoc 說明修正，不涉及任何程式碼邏輯或計算行為的改變。請確認建議的 Javadoc 內容以及修正後的 issue 連結是否符合您的預期。

## Proposed Changes

### Documentation

#### [MODIFY] [NisaAccountType.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/NisaAccountType.java)
- 更新 `parse()` 方法的 Javadoc 註解。
- 採用 `issue-31.md` 中建議的方向 A 文字，明確說明 GROWTH 預設值在「終身額度」面向是保守的，但在「當年度可投入金額」面向是寬鬆的。
- 修正原始文件建議中的錯誤 issue 路徑，將 `todo/wontfix/issue-31.md` 修正為正確的 `todo/pending fix/issue-31.md`。

## Verification Plan

### Manual Verification
- 修改後，您可以直接透過 IDE Diff 檢視 Javadoc 的內容與排版是否符合預期。


# Walkthrough: 修正 NisaAccountType Javadoc 註解 (Issue 31)

## Changes Made
- 更新了 `NisaAccountType.parse()` 方法的 Javadoc 註解。
- 將原本較為簡略的註解，替換為詳細說明 GROWTH 預設值在「終身額度」面向（保守）與「當年度可投入金額」面向（寬鬆）這兩種不同特性的完整文字。
- 將原建議文中標記錯誤的參考路徑（`todo/wontfix/issue-31.md`）修正為實際存在的 `todo/pending fix/issue-31.md`。

## Modified Files
- [`NisaAccountType.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/NisaAccountType.java)

## Verification
- 此變更僅涉及程式碼註解層面的說明補強，不影響任何現有商務邏輯的執行。
- Javadoc 排版使用標準 `<p>` 標籤以確保文件渲染時斷行正確。
