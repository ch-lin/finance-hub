# WONTFIX 20：生活費基準線的「isSimulationDuplicate」分類旗標預設方向，原提案誤判為雙重計算風險

## 判定：維持現狀，不需修正（2026-09-30，使用者確認）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java:282-314`（`calculateDynamicBaselineMonthlyExpenses`，從銀行/信用卡交易加總必須支出）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/category/TransactionCategory.java:99-102`（`isSimulationDuplicate` 欄位，預設值 `false`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:300-302`（房貸/NISA/DC/iDeCo投入金額的獨立扣款位置）

## 原始提案內容
系統用一個叫 `isSimulationDuplicate` 的分類標籤，來避免「同一筆錢在生活費基準線裡被算一次，又在房貸/iDeCo/NISA的專屬計算裡被扣一次」。這個標籤預設值是 `false`（代表「不排除、會被算進生活費基準線」）。原始提案認為：如果哪一筆房貸扣款或iDeCo扣款，剛好被分類成「必須支出」卻沒有人手動把這個標籤打勾排除，就會被系統同時算兩次——一次在生活費基準線，一次在房貸/iDeCo自己的計算裡。並建議系統應該自動比對银行交易金額跟房貸月付金/iDeCo設定金額，抓出這種可能被漏標的交易並提出警示。

## 為何判定不是問題

### 1. 預設值方向本身沒有問題，反過來才是真正危險的設計
如果把預設值改成「排除」（true），代表任何新出現的交易分類，預設就會從生活費基準線裡消失，使用者必須自己一個一個手動把該算的分類勾選回來，系統才會把它算進生活費。對一個核心目的是「判斷會不會現金破產」的工具而言，讓生活費被系統性低估、使用者誤以為自己有更多餘裕，是遠比「多算一點」更危險的失敗方向。現在的預設值（會被算進去）已經是比較安全的選擇，不需要調整。

### 2. 提案假設的情境不符合真實帳戶的資金流動方式
- 房貸的扣款完全發生在房貸專屬的銀行帳戶裡，不會經過信用卡，也不會經過主要往來銀行（例如MUFG）。這個房貸帳戶的餘額本身就是事實，不需要回頭翻閱歷史交易紀錄去重建或對帳，這不是一個對帳系統。
- iDeCo的扣款100%直接從銀行扣除，不可能透過信用卡進行。

原提案設想的「分類標錯導致雙重計算」情境，前提是這類交易會出現在餵給生活費基準線計算的那些銀行/信用卡交易紀錄裡；但依照真實帳戶的運作方式，這些交易根本不會出現在那個資料來源裡，所以這個情境本身缺乏現實依據。

### 3. 建議的修正方向本質上是先前已經評估、否決過的功能
原提案建議的「系統自動比對银行交易金額跟房貸/NISA設定金額」，本質上就是把房貸、NISA跟銀行帳戶資料做關聯比對（reconciliation/link）的功能。這類功能已經在其他討論中評估過投資報酬率過低而不打算做——這個系統的定位是財務規劃（FP）工具，不是對帳系統，房貸這類確定性高、資料量小的項目，本來就該由使用者自行核對，不需要系統額外建立比對機制。

## 結論
維持現狀，不需修正。
