# [FIXED] Question 166：NISA/DC歷史趨勢圖表，是依「基金名稱」合併加總，不分帳戶類型/制度——這是刻意要顯示「同一檔基金的總曝險」，還是應該像表格一樣分開顯示每個帳戶各自的趨勢？

## 這份question怎麼被發現的
這是這次Source Code Review針對前端業務邏輯進行審查時發現的問題。這份question不是程式碼本身有誤，而是這個分類顆粒度的選擇是否符合產品本來的意圖——依照既有的「granularity是產品範疇決定、不是架構bug」的審查原則，這類問題應該先詢問、不應該自己腦補答案。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-historical-chart.tsx:32-49`
- `hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-historical-chart.tsx:32-49`
- 對照：同資料夾裡的`nisa-table.tsx`/`dc-table.tsx`——這兩個表格元件，每一筆紀錄都獨立顯示成自己的一列，而且NISA表格額外有一欄顯示`accountType`（成長枠/つみたて枠），說明資料本身是有能力區分這個維度的，只是圖表沒有用到這個維度

## 現狀（不涉及爭議的部分，純粹讀程式碼得到的事實）
兩個歷史趨勢圖表，依照`recordDate`分組之後，繪製每一條圖例線的方式，純粹是依照`fundName`(基金名稱)去加總同一天、同名稱的所有紀錄——完全沒有把NISA的`accountType`（成長枠/つみたて枠）或DC的`type`（iDeCo/企業型DC）納入分組的依據。這代表如果使用者持有同一檔基金商品（例如同一支指數型基金），但分別放在不同帳戶/制度下（例如NISA成長枠也買、つみたて枠也買同一支；或是iDeCo跟企業型DC都配置了同一支基金），圖表會把這些原本獨立的部位，合併畫成同一條線。

## 不確定、需要詢問才能回答的地方
這個「依基金名稱合併」的做法，可能是刻意的設計——如果使用者真正想看的是「我總共對這檔基金商品有多少曝險，不分它放在哪個帳戶底下」，目前的做法完全正確，不需要修正。但這也可能只是沒有特別考慮到「同一檔基金可能同時存在於不同帳戶類型底下」這個情境而留下的疏漏——如果使用者實際上想看的是「NISA成長枠的這支基金」跟「NISA つみたて枠的這支基金」各自獨立的趨勢（這正是旁邊的表格元件已經在做的事），目前的圖表會讓這兩條原本該分開看的趨勢線混在一起，看不出「iDeCo這部分這檔基金的部位下滑了，但企業型DC這部分同一檔基金的部位其實在成長」這種對比。

## 需要查證的問題
這兩個歷史趨勢圖表，原本設計的意圖是哪一種？
1. 顯示「不分帳戶類型，這檔基金商品的總曝險趨勢」（目前的實作方式）
2. 顯示「每個帳戶類型/制度底下，各自獨立的趨勢線」（跟旁邊的表格元件一致的顆粒度）

## 不建議的處理方式
不建議在沒有確認這個分類顆粒度的意圖之前，就直接假設是「疏漏」並動手把圖表改成依帳戶類型分開繪製——如果這其實是使用者想要的「總曝險」視角，這樣的修改反而會讓圖表變得比現在更瑣碎、更難一眼看出整體狀況。

## 產品經理與架構決策（2026-10-13）
- **定案決策**：**選項 2（選項 B：各帳戶類型／制度各自獨立顯示趨勢線）**。
- **決策理由與產品目標**：
  1. **維持資訊顆粒度一致性（Component Consistency）**：歷史圖表應與旁邊的表格元件（`nisa-table.tsx`、`dc-table.tsx`）保持同等顆粒度，避免使用者在表格看到成長枠與つみたて枠分列，在圖表卻找不到對應走勢的認知落差。
  2. **反映制度別資產配置與定期定額成效**：NISA 的成長枠與つみたて枠（或 DC 的 iDeCo 與企業型 DC）具有完全不同的稅制上限與扣款頻率，使用者需能清晰掌握各制度獨立部位隨時間的真實成長動態。
- **後續前端實作規格**：
  1. **NISA 歷史圖表 ([nisa-historical-chart.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-historical-chart.tsx))**：
     - 折線分組鍵（Key）與圖例名稱由單純的 `fundName` 升級為包含帳戶別的複合鍵，例如：`${r.fundName} (${r.accountType})`。
     - 日期分組加總（`reduce`）僅在「同日期、同帳戶類別、同基金」時才加總，不同 `accountType` 獨立繪製折線。
  2. **DC 歷史圖表 ([dc-historical-chart.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-historical-chart.tsx))**：
     - 折線分組鍵與圖例名稱納入 DC 制度類別，例如：`${f.fundName} (${curr.type})`（區分 iDeCo / 企業型DC）。
  3. **顏色分配 ([use-fund-colors.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/hooks/use-fund-colors.ts))**：
     - 確保複合鍵能取得獨立且具對比度的圖例線條色彩。
- **狀態**：**已完成修正（Fixed / Implemented）**。

