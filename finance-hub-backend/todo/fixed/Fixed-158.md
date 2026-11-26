# [FIXED] Question 158：E-Trade持股明細重新匯入時會整表清空再重建，如果使用者曾經同時使用過兩個不同的E-Trade帳戶，舊帳戶的持股會被悄悄刪除——需要先確認是否真的有多帳戶情境

## 這份question怎麼被發現的
這是這次Source Code Review針對匯入解析器的業務邏輯進行商業邏輯審查時發現的問題，但觸發條件（使用者是否真的用過不只一個E-Trade帳戶）只有使用者自己知道答案。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/CompanyStockJobServiceImpl.java:336-337`（持股頁面匯入：`esppStockLotRepository.deleteAll(); rsuStockLotRepository.deleteAll();`，先清空整張表，再插入這次解析出來的持股）
- 同檔案`:373-383`（獎酬歷史頁面匯入：同樣對四張相關的表做無條件`deleteAll()`）
- 對照：`ETradeAccountSnapshotRepository`/現金交易相關的匯入，用的是`deleteByRecordDate`，只刪特定日期範圍，不是整表清空——持股/獎酬歷史這兩個頁面跟其他E-Trade資料的刪除範圍寬窄不一致

## 現狀（不涉及爭議的部分，純粹讀程式碼得到的事實）
持股明細、獎酬歷史這兩類頁面，每次重新匯入都是「先把整張表清空、再把這次解析出來的內容整批塞回去」，沒有依任何範圍（股票代號、帳戶）去限定刪除範圍。

## 不確定、需要使用者確認才能回答的地方
如果使用者從頭到尾只用過一個E-Trade帳戶匯入資料，這個做法完全沒有問題——每次匯入本來就該是最新、最完整的狀態。但如果使用者曾經(或現在)同時持有兩個不同E-Trade帳戶的持股（例如換工作後，前公司未出售的RSU還留在舊帳戶、新公司的股票計畫在新帳戶），而這兩個帳戶各自匯出各自的HTML頁面(每次匯入只涵蓋其中一個帳戶)，那麼匯入其中一個帳戶的頁面，會把另一個帳戶原本正確的持股紀錄整批刪除——不是因為任何東西真的改變了，純粹是因為這次匯入沒有提到它。

## 需要查證的問題
使用者是否曾經、或現在，同時透過這個「etrade」匯入來源，匯入過不只一個不同E-Trade帳戶/雇主的資料？如果從來沒有，這份question可以直接結案；如果有，這會是一筆真實發生中的靜默資料遺失。

## 不建議的處理方式
不建議在沒有確認「是否存在多帳戶情境」之前，就直接投入工程改成依帳戶/股票代號範圍限定刪除——如果使用者從未有過這個情境，這類改動的優先度應該排在真正會被觸發的問題之後。

---

## 查證結果與事實確認（真實使用者情境）
- **真實使用情境**：使用者確認**從未想過此多帳戶情境，目前暫無此需求**。
- **背景與生命週期事實**：使用者過去雖曾有前工作經歷之 RSU 帳戶存留，但前公司**規定離職時必須全數賣出變現**。因此在真實世界中，並不具備「跨多個不同雇主之 E-Trade 帳戶同時各自持有在庫股票、且需分別匯入」的長期並存情境。
- **結論**：目前使用者在真實環境中僅持有單一使用中的 E-Trade 帳戶，原審查提出的「靜默資料遺失」在真實世界中並不成立。

---

## 「`deleteAll()`」與「`deleteByRecordDate`」的設計意圖與職責劃分

針對本 issue 提出的「刪除範圍寬窄不一致」疑慮，經查證原始架構與前端 UI 設計，其設計原因與職責切分如下：

### 1. `deleteByRecordDate`（帳戶快照表 `etrade_account_snapshot`）
- **核心目的**：**守護歷史資產走勢折線圖（Time Series Continuity）**。
- 前端頁面中的「E-Trade Value Trend 折線圖」（`stock-value-chart.tsx`），其資料來源**百分之百只讀取快照表**。
- 為了能跨月、跨年累積總資產走勢，快照表**絕對不能使用 `deleteAll()`**，否則過去所有歷史點位將一夕全毀。
- 唯有在同日重複匯入時，透過 `deleteByRecordDate` 進行單日就地更新，滿足 `(owner_user_id, record_date)` 唯一約束並達成幂等性（Idempotency）。

### 2. `deleteAll()`（在庫持股表 `etrade_espp_stock_lot` / `etrade_rsu_stock_lot`）
- **核心目的**：**維護當前在庫庫存（Current Inventory），百分之百與折線圖無關**。
- 這兩張表只負責折線圖下方的「Stock Lots 庫存表格」，呈現當前手中握有的批次與浮動損益。
- E-Trade 的 `holdings.html` 只反映「此時此刻帳戶還剩什麼」。若使用者在實體世界賣出了某批股票，新匯出的網頁就不會再包含該批次。
- 若不用 `deleteAll()` 清空重建，資料庫將無法得知哪些批次已經出售，已賣出的股票會永久殘留為「幽靈持股」。清空再存入是維護當前持股正確性的必要手段，且該操作完全不影響歷史折線圖。

### 3. 獎酬歷史表（`etrade_benefit_*` 4 張表）
- E-Trade 的 Benefit History 頁面每次匯出時，本質上即為「自入職開戶以來的全量歷史紀錄清單」。
- 每次匯入均自帶完整歷史，因此整表清空後重塞並不會遺失任何歷史資料，且這 4 張表目前前端亦無對應折線圖消費。

---

## 後續架構檢討方向（列入 Plan 評估，非本問題範圍）

未來若確實出現跨雇主多帳戶或多股票代號同時在庫之擴充需求，是否應建立以「股票代號（Symbol）」或「帳戶主表（Account ID）」為維度的主表進行 Scope-based 局部刪除與關聯串接：
- **處置**：此屬於長期資料模型正規化與擴充性優化，已正式立案於 [PLANNED.md](../planned/PLANNED.md) / [Plan-22](../planned/Plan-22-etrade-symbol-scoped-import-and-inventory-deletion.md) 中進行架構檢討，**不在本 question 範圍內進行額外代碼修改**。

---

## 結論與處置

- **處置**：**維持現狀，直接結案（Closed - As Designed / No Multi-Account Need）**。
- **說明**：現行實作在使用者單一帳戶之真實使用情境下運作完全正確，且「快照留歷史（`deleteByRecordDate`） vs 庫存留現狀（`deleteAll()`）」之職責邊界清晰合理，無須調整。

