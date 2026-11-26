# Plan 10: `SimulationOptimizerServiceOldImpl.findBestMortgageEarlyRepaymentYear` 已退化為死碼，待新版驗證完畢後刪除

> 原記錄於 [Issue-08-01.md](../pending%20review/Issue-08-01.md) 稽核過程中的附帶發現，因該 Issue 屬於「pending review」，一旦關閉/存檔後內文通常不會再被回頭查看，故移至 `planned` 資料夾單獨追蹤，避免這個待刪除死碼被永久遺忘。

## 現況

`SimulationOptimizerServiceOldImpl.findBestMortgageEarlyRepaymentYear`（[SimulationOptimizerServiceOldImpl.java:118-173](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/SimulationOptimizerServiceOldImpl.java:118)）因為 `SimulationOverrides.mortgageEarlyRepaymentYear` 欄位已被移除，其搜尋迴圈內組出的 `SimulationOverrides` 已經沒有任何地方帶入候選年份 `testYear`，導致每次迴圈疊代的模擬結果完全相同，`bestYear` 永遠退化成 `currentYear`——這個方法現在無論怎麼呼叫都不會給出有意義的結果。

## 為何目前不是需要立刻處理的 bug

已透過全域 frontend grep 確認：此方法對應的 `GET /simulation/asset-simulation/mortgage-early-repayment-year` endpoint 在前端**沒有任何呼叫者**（不像同一 controller 上的 `/asset-simulation/{targetAge}` 跟 `/nisa-goal`，這兩個都有現行使用中的頁面在呼叫）。該 service 本身的 Javadoc 也已註明是待新版（Global Optimizer）驗證完畢後即刪除的舊版實作。

依照原審查任務的排除規則（死碼、已無人使用的部分不在審查範圍內），這不是一個需要修的 bug。

## 建議動作

待新版 Global Optimizer（`GlobalOptimizerServiceImpl` + Grid Search / GA）確認穩定、不會再回退使用舊版之後，整段刪除：
- `SimulationOptimizerServiceOldImpl.findBestMortgageEarlyRepaymentYear` 方法本身
- 對應的 `GET /simulation/asset-simulation/mortgage-early-repayment-year` controller endpoint

若未來這個舊版 API 遲遲沒被刪除、或有人重新接上前端呼叫它，才需要回頭把「已退化」這件事當成真正的 bug 處理。
