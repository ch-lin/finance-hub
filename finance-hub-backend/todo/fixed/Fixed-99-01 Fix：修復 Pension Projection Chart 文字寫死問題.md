# Fixed-99-01 Fix：修復 Pension Projection Chart 文字寫死問題

## 問題簡述
目前 `PensionProjectionChart` 元件中的說明文字寫死了 `at age 65`，導致當使用者在畫面上使用滑桿 (Slider) 調整請領年齡 (Pension Start Age) 時，雖然圖表的計算結果有更新，但文字依然維持 65 歲，造成 UX 上的困擾與誤導。

## 針對「滑桿顯示數字」的說明
關於您提到的「滑桿可以顯示數字嗎？」，目前程式碼在 `page.tsx` 中其實**已經有實作顯示數字的功能**了：
在滑桿正上方有一個 Label，當使用者拉動滑桿時，右側的文字會即時顯示目前的歲數（例如 `70`）。如果使用者還沒碰過滑桿，則會顯示 `Profile Settings`。

這段現有程式碼如下：
```tsx
<Label className="flex justify-between">
  <span>Pension Start Age</span>
  <span className="text-muted-foreground">{pensionStartAgeOverride || 'Profile Settings'}</span>
</Label>
```
因此，當我們把圖表內會造成誤導的 `at age 65` 刪除後，使用者依然可以透過滑桿上方的 Label 清楚知道「現在到底是幾歲」。

## Proposed Changes

我們將進行純粹的 UI 文字修正，不新增複雜的邏輯判斷。

### Frontend Components

#### [MODIFY] `pension-projection-chart.tsx`(file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx)
- 將 `<CardDescription>` 內部的寫死文字 `Estimated annual pension payout at age 65 based on additional years worked` 
- 修改為單純的描述 `Estimated annual pension payout based on additional years worked`，直接移除 `at age 65` 的字眼，避免與上方滑桿實際的數字產生衝突。

#### [MODIFY] `page.tsx`(file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/pensions/page.tsx)
- 修改 `pensionStartAgeOverride` 顯示的 Label，加上 `years old` 後綴，讓顯示更親切直觀：
  - 將 `{pensionStartAgeOverride || 'Profile Settings'}` 
  - 改為 `{pensionStartAgeOverride ? \`\${pensionStartAgeOverride} years old\` : 'Profile Settings'}`

## Verification Plan
1. 確認編譯通過無語法錯誤。
2. 預期行為：進入 Pension Records 頁面時，圖表說明文字不再包含 65 歲。
3. 預期行為：拉動滑桿時，上方 Label 能正常顯示數字，且圖表說明文字保持客觀描述，不會再發生文字與設定脫節的狀況。

# Walkthrough: Fixed-99-01 Fix

## 變更摘要
本次修改修復了退休金試算圖表 (Pension Projection Chart) 說明文字與實際滑桿數值脫節的問題，並微調了滑桿數值的顯示方式。

## 詳細修改內容

### 1. 移除寫死的 65 歲文字
在 `pension-projection-chart.tsx` 中，我們將原本的文字：
`Estimated annual pension payout at age 65 based on additional years worked`
修改為更客觀、不會造成誤導的描述：
`Estimated annual pension payout based on additional years worked`

這解決了最核心的 UX 混淆問題：無論使用者將滑桿拉到幾歲，圖表的標題都不會再出現錯誤的 `65`。

### 2. 優化滑桿上方的數值顯示
在 `page.tsx` 中，我們針對 Pension Start Age 滑桿右上方的 Label 顯示邏輯做了微調：
原本拉動滑桿時只會顯示純數字（例如 `70`），現在修改為會加上後綴（例如 `70 years old`）。
當尚未調整滑桿時，依然會顯示預設的 `Profile Settings`。這樣讓整體顯示更為親切、容易理解。

## 驗證
所有的程式碼都已完成修改並儲存，後續可透過啟動前端專案來確認畫面上的顯示效果。
