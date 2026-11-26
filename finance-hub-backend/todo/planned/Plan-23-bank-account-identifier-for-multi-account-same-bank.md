# Plan 23：銀行交易新增「帳號」欄位，解決同一銀行多帳戶（本人／配偶／小孩）互相覆蓋的問題

## 來源
Source Code Review（2026-10-13）FINDING——審查銀行現金餘額彙總邏輯時發現，`BankTransaction`/`BankStatement`這兩個domain實體從頭到尾都沒有帳號欄位，`bankName`這個字串同時身兼兩個角色：「這是哪家銀行」跟「這是哪個帳戶」。只要同一家銀行底下出現第二個帳戶（不限本人多開一戶，配偶、小孩各自的帳戶都算），系統沒有任何欄位可以區分它們。

## 狀態
PLANNED（使用者已拍板要做，只是現在規劃，不確定何時排入）

## 背景：這不是parser擷取精度問題，是domain model本身缺少欄位
`SmbcBankHtmlParser.java:52`、`MufgBankHtmlParser.java:52`兩支解析器，`bankName`都是程式碼裡寫死的固定常數字串（例如`"SMBC"`），不會從頁面讀取帳號、分行或任何可以區分帳戶的資訊。往上追到`BankTransaction.java`、`BankStatement.java`兩個domain實體，完整欄位清單裡確實沒有帳號——`BankTransaction`只有`bankName`（字串）、交易日期、摘要、存入/提出金額、餘額、備註；`BankStatement`的唯一鍵是`(ownerUserId, bankName, statementMonth)`，一樣只靠`bankName`這個字串撐著「這是哪個帳戶」的身分。

這代表就算現在就去改彙總邏輯，系統也沒有地方可以存「這筆交易屬於哪一個帳戶」這個資訊——必須先擴充domain，彙總邏輯才有東西可以依賴。

## 為何是問題
`getLatestBankBalances()`（`BankTransactionServiceImpl.java:157-169`）跟模擬引擎起始現金餘額的計算（`SimulationContextFactory.java:153-162`的`initialLiquidCash`），邏輯都是：先列出資料庫裡所有不重複的`bankName`，再對每一個名稱各自抓「依交易日期排序最新的那一筆交易」的`balance`欄位，加總成現金總額。這整套邏輯的前提是「一個`bankName`字串對應剛好一個真實銀行帳戶」。

使用者目前的使用情境是：自己名下的帳戶、配偶的帳戶（配偶Link功能見[Plan-03](Plan-03-家族財務服務與模擬器整合.md)，目前尚未完成）、小孩的帳戶，未來都有可能落在同一家銀行底下。如果這三個身分在同一家銀行（例如SMBC）各自開了帳戶，三筆資料的`bankName`會被寫成完全相同的字串，資料庫裡無法分辨。`getLatestBankBalances()`跟`initialLiquidCash`在抓「SMBC」這個名稱的最新一筆交易時，只會抓到這三個帳戶的交易記錄依時間排序後最晚的那一筆——其他兩個帳戶累積的餘額會從模擬起始現金裡完全消失，不是少算一部分，是整個帳戶的餘額從模擬起點就直接不存在，不會有任何錯誤或警告提示使用者這件事發生了。

對照信用卡這一側的設計：10支信用卡parser裡，多數已經針對「同一家發卡公司底下可能有多張卡」做了動態的名稱區分（例如解析出卡號末幾位、持卡人姓名、使用者自訂的卡片別稱），但銀行HTML解析器完全沒有做這件事，是銀行這一側獨有的落差。

## 需求說明
使用者已確認要做的方向，是幫銀行交易加上「帳號」這個獨立欄位，而不是繼續把帳戶身分塞進`bankName`這個字串裡。但在落地前，有幾個設計細節需要先確認：

1. **帳號是不是夠用的唯一識別**：使用者初步認為「同一個銀行裡帳號應該不會重複」，但同時也提出「是不是要搭配分行」的疑慮——這點目前查無頁面樣本可以確認（見下方「無法确定的部分」），需要實際看過SMBC/MUFG網路銀行對帳單頁面上，帳號欄位旁邊有沒有同時顯示分行資訊、以及分行+帳號是否才是真正不會重複的唯一組合，再決定最終的識別鍵設計成「帳號」單獨一個欄位、還是「分行+帳號」兩個欄位的組合。
2. **這個欄位該怎麼跟「這是誰的帳戶」對應起來**：目前系統裡本人、配偶、小孩的身分區分，依賴的是`MultiUserBaseEntity.ownerUserId`跟家族群組機制（見[issue-140](../pending%20fix/issue-140.md)）。但配偶的Link模式（真實帳號連結）跟小�20孩的資料目前都還沒有完整落地（見[Plan-03](Plan-03-家族財務服務與模擬器整合.md)），這代表「帳號」欄位目前的直接目的，是解決「同一個`ownerUserId`底下，同一家銀行開兩個帳戶時彼此互相蓋掉」這個最小範圍的問題；至於配偶/小孩的帳戶要怎麼歸戶、要不要用這個「帳號」欄位去跟Link模式串接，需要等Plan-03的Link模式有具體落地方向時一併確認，不在這份Plan的範圍內搶先設計。

## 無法確定的部分（需要有真實頁面樣本才能決定擷取方式）
兩支現有的銀行HTML解析器（`SmbcBankHtmlParser.java`、`MufgBankHtmlParser.java`）目前完全沒有嘗試解析帳號或分行相關的程式碼，專案裡也沒有找到任何銀行對帳單的測試HTML fixture可以參考真實頁面結構。這代表：
- SMBC/MUFG網路銀行對帳單頁面上，帳號（跟分行代碼，如果需要的話）具體顯示在哪個位置、用什麼格式呈現，目前無法只靠讀現有程式碼判斷，需要有真實頁面樣本才能設計正確的擷取邏輯。
- 落地時這部分擷取邏輯的設計，應該在取得真實樣本後再動手，不要在沒有樣本的情況下猜測欄位位置或格式。

## 建議修改範圍
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/bank/BankTransaction.java`（新增帳號欄位，可能需要同時評估是否要調整`@EqualsAndHashCode`涵蓋的欄位組合）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/bank/BankStatement.java`（唯一鍵`(ownerUserId, bankName, statementMonth)`可能需要納入帳號，避免同一銀行不同帳戶的當月記錄互相衝突）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/bank/parser/SmbcBankHtmlParser.java`、`MufgBankHtmlParser.java`（待有真實樣本後，新增帳號/分行的擷取邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/bank/BankTransactionRepository.java`（`findDistinctBankNames()`、`findFirstByBankNameOrderByTransactionDateDesc(bankName)`這類以`bankName`當分組/身分鍵的查詢方法，需要改成納入帳號）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/bank/BankTransactionServiceImpl.java`（`getLatestBankBalances()`的分組邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java`（`initialLiquidCash`的加總邏輯，第153-162行）
- 前端（`hub-ui/hub-ui/src/app/finance/accounts/banks/`底下對應頁面）：新增帳號欄位的顯示與區分，讓使用者在畫面上能分辨「這是我的SMBC帳戶」「這是老婆的SMBC帳戶」。
