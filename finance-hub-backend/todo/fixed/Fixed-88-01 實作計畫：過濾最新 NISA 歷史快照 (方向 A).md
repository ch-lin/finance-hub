# Fixed 88-01 實作計畫：過濾最新 NISA 歷史快照 (方向 A)

為了解決「進階覆寫」面板重複顯示 NISA 歷史快照，導致使用者可能編輯到舊紀錄而使覆寫靜默失效的問題，我們將採用**方向 A**，也就是只在前端 `simulation-override-panel.tsx` 中進行快照篩選。

## 🎯 變更目標
在前端呼叫 `/api/finance/assets/nisa` 取得資料後，將取得的陣列過濾，只保留 `recordDate` 與第一筆（最新日期）相同的快照，以與後端模擬引擎的行為保持一致。

## ⚠️ User Review Required
沒有破壞性變更 (Breaking Changes)。這個修正僅涉及前端資料渲染邏輯，不影響後端資料庫與既有 API 規格。

## ❓ Open Questions
無。這個方向改動範圍最小，非常明確。

## 📝 Proposed Changes

### UI Components

#### [MODIFY] `simulation-override-panel.tsx`(file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
- 在 `fetchFunds()` 函數中，收到 `nisaSnapshots` 陣列後，新增過濾邏輯：
  - 由於後端 API 已確保資料為依日期遞減排序 (`findAllByOrderByRecordDateDesc`)，陣列的第一筆 (`nisaSnapshots[0]`) 即為最新日期。
  - 取出 `latestDate = nisaSnapshots[0].recordDate`。
  - 使用 `.filter(snap => snap.recordDate === latestDate)` 將 `nisaSnapshots` 過濾為 `latestNisaSnapshots`。
- 將原本 `Array.isArray(nisaSnapshots) ? nisaSnapshots.map(...)` 的資料來源替換為過濾後的 `latestNisaSnapshots`。
- 這樣畫面上的「NISA 基金覆寫」清單就只會出現最新一批的基金卡片，而且底層的 `fundId` 將會與模擬引擎實際載入的 ID 完美對齊。

---

## 🧪 Verification Plan

### 自動化測試 (Automated Tests)
- 由於此修正僅涉及純前端 React 組件的內部渲染邏輯，我們不執行後端單元測試。

### 人工驗證 (Manual Verification)
1. 進入 `simulation-override-panel.tsx` 所在的模擬器頁面並打開 "Advanced Overrides" 面板。
2. 檢查 "Per-Fund Overrides" 底下的 NISA 基金列表，確認畫面上不再出現名稱相同但底層快照不同的重複卡片。
3. 隨機調整其中一檔 NISA 基金的 Return (%) 或 Monthly (¥) 並觸發模擬。
4. 觀察前端網路請求 (Network tab)，確認丟給後端的 `fundId` 確實能成功對應到當前最新批次，且模擬結果資產曲線發生了對應的變化。
