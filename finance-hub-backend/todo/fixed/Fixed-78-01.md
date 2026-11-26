# Fixed 78-01：DC 與 NISA 歷史圖表在同日期同基金名稱重複時，聚合規則不一致（覆蓋 vs 加總）

## 嚴重程度
Low

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-historical-chart.tsx:40-49`
- `hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-historical-chart.tsx:36-49`

## 現狀
`dc-historical-chart.tsx` 的 `byDate` reducer 對同一日期下的每個基金採用：

```ts
acc[date][f.fundName] = f.currentMarketValue;
```

單純覆蓋（後寫入者覆蓋前者）。而 `nisa-historical-chart.tsx` 的 `byDate` reducer 對同一日期同基金名稱組合採用：

```ts
if (acc[date][curr.fundName]) {
    acc[date][curr.fundName] += curr.currentMarketValue;
} else {
    acc[date][curr.fundName] = curr.currentMarketValue;
}
```

正確地累加。

## 原因
如果同一個 `recordDate` 底下有多筆快照紀錄，且它們的基金名稱相同——例如 `DefinedContributionAccountSnapshot` 的 `type` 欄位明確允許 IDECO 與 CORPORATE_DC 兩種帳戶類型同時存在，且兩者恰好都持有相同基金名稱——DC 圖表會靜默丟棄/覆蓋其中一個帳戶對該基金市值的貢獻，而不是像 NISA 圖表的姐妹元件那樣正確加總，導致 DC 圖表在此類重疊情況下畫出的數據點被低估。這是兩個處理相似聚合問題的元件之間的不一致。

## 具體錯誤情境
使用者同時持有 iDeCo 帳戶與企業型 DC 帳戶，兩者在同一天的快照中都持有名稱相同的基金（例如同一家資產管理公司的同一支指數基金）。DC 歷史圖表上，該日期該基金的資料點只會顯示其中一個帳戶的市值，另一個帳戶的貢獻被靜默覆蓋消失，導致圖表低估使用者當日 DC 總資產。

## 建議修正
將 `dc-historical-chart.tsx` 的 reducer 改為累加寫法（`acc[date][f.fundName] = (acc[date][f.fundName] || 0) + f.currentMarketValue;`），使其與 `nisa-historical-chart.tsx` 已採用的加總語意一致。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 均未提及 DC/NISA 歷史圖表聚合規則不一致的問題，屬於全新發現的問題。

---

## 修正報告 (Fix Implementation)

這個修正旨在解決 DC 歷史圖表同名基金市值在同日有多筆紀錄時會被錯誤覆蓋的問題。修正後，系統能正確將跨帳戶（例如同時擁有 iDeCo 與企業型 DC）中持有的同名基金市值累加，確保圖表顯示真實的資產總值，並與 NISA 圖表行為保持一致。

### 實際修改內容

#### 1. `dc-historical-chart.tsx`
在 `byDate` reducer 中，將原本直接覆寫的寫法改為累加。

**修改前**：
```typescript
acc[date][f.fundName] = f.currentMarketValue;
```

**修改後**：
```typescript
acc[date][f.fundName] = (acc[date][f.fundName] || 0) + f.currentMarketValue;
```
**目的**：確保同一日期內，若出現多筆相同 `fundName` 的紀錄，其 `currentMarketValue` 將會正確累加計算，避免較晚處理的紀錄吃掉前一筆的數字。
