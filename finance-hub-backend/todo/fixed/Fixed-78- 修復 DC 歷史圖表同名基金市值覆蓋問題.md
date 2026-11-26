# Fixed-78: 修復 DC 歷史圖表同名基金市值覆蓋問題

解決當同一個日期下（例如同時擁有 iDeCo 與企業型 DC），有多筆相同名稱的基金紀錄時，`dc-historical-chart.tsx` 圖表會錯誤地將後一筆紀錄覆蓋前一筆紀錄，導致該基金的總資產被低估的 Bug。修正後，將改為正確地將同名基金的市值累加，與 NISA 圖表的行為保持一致。

## User Review Required

無破壞性變更。這是一個純粹的數值累加修正。

## Open Questions

無。

## Proposed Changes

### Frontend (UI / Charts)

#### [MODIFY] [dc-historical-chart.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-historical-chart.tsx)
在 `byDate` reducer 中，將原本直接覆寫的寫法改為累加：
```typescript
// 修改前
acc[date][f.fundName] = f.currentMarketValue;

// 修改後
acc[date][f.fundName] = (acc[date][f.fundName] || 0) + f.currentMarketValue;
```

## Verification Plan

### Automated Tests
- 無前端單元測試需執行。

### Manual Verification
- 您可以在 VSCode 中透過 Source Control 進行 Diff 審查。
- 儲存後，您可以打開網頁 UI，確認 DC 歷史圖表是否不再漏算重疊基金的市值（圖表線條數值應正確反映總和）。
