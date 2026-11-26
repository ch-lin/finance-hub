# Plan 07：支出搜尋 API 接受 keyword 與 isEssential 篩選參數，但實際上完全沒有套用

## 嚴重程度
Medium

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/expense/ExpenseServiceImpl.java:55-94`

## 現狀
`searchExpenseItems(String keyword, List<String> sources, Boolean isEssential, LocalDate startDate, LocalDate endDate, Pageable pageable)` 建構 `Specification` 時，只針對 `sources`、`startDate`、`endDate` 加入對應的查詢條件（predicate）——`keyword` 與 `isEssential` 這兩個參數在整個方法主體中完全沒有被引用。

## 原因
任何依 `keyword`（例如針對項目描述做文字搜尋）或 `isEssential`（例如「只顯示必要支出」）進行篩選的呼叫，實際上都會靜默拿回這兩個維度上完全未篩選的完整結果集，因為篩選條件根本是空操作（no-op）。這屬於功能性篩選缺陷，而非缺少輸入驗證的問題，因為這兩個參數本身就是已被接受、屬於商務邏輯篩選條件的一部分，只是被完全忽略而已。

## 具體錯誤情境
使用者在支出頁面輸入關鍵字「星巴克」進行搜尋，或勾選「只顯示必要支出」篩選器。無論輸入什麼關鍵字、無論是否勾選必要支出篩選，回傳的結果都是同一份完整未篩選的支出列表——這兩個篩選功能對使用者而言完全無效。

## 建議修正
在 `keyword` 非 null 時加入對應的 predicate（例如針對描述/名稱欄位做 `LIKE` 查詢），在 `isEssential` 非 null 時加入 `cb.equal(root.get("isEssential"), isEssential)` 的 predicate。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 均未提及支出搜尋 API 篩選參數失效的問題，屬於全新發現的問題。

## 決策記錄（2026-09-09，移入 planned）
先前review時漏掉沒寫進計劃：這兩個參數（`keyword`/`isEssential`）本來就知道後端沒接，但前端目前也還沒有對應的搜尋關鍵字輸入框/必要支出篩選勾選（可能只是殼，尚未確認），所以實際上不會被使用者觸發。且此功能不影響購房現金破產模擬的核心目的，暫緩處理。
