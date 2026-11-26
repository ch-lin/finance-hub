# WONTFIX 05：NISA 匯入資料庫寫入完全失敗時，紀錄裡的狀態卻寫成「部分成功」，不是「失敗」

## 判定：WONTFIX（2026-09-11）
**理由**：NISA 匯入這個功能會在正式上線前整個刪除，只有能夠 SSH 進去容器裡放檔案的人（目前只有負責人自己）才有辦法使用這個功能，特地修正這個問題沒有實際意義。

---

## 嚴重程度
Low-Medium（目前沒有任何畫面會顯示這筆紀錄，所以使用者現在看不到這個錯誤；但資料庫裡存的狀態文字本身是錯的，一旦以後有人真的去查這筆紀錄，會被誤導）

## 相關檔案
- `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/job/JobHistoryServiceImpl.java:90`（決定要寫入哪種狀態文字的那一行程式碼）
- `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/job/JobHistoryServiceImpl.java:108-119`（`failJob`方法，會正確寫成「FAILED」，但目前這個方法完全不會被 NISA 匯入呼叫到）
- `finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/history/ImportJobHistory.java:145-153`（狀態欄位的說明，列出「PROCESSING、SUCCESS、FAILED、PARTIAL_SUCCESS、INTERRUPTED」五種狀態，但目前程式碼實際只會用到其中三種，而「FAILED」這個狀態對 NISA 匯入來說永遠不會被寫入）
- `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java:196-218`（NISA 匯入寫資料庫失敗時，只是把錯誤攔截起來記錄，不會把錯誤往外丟出去）
- `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/job/JobOrchestratorImpl.java:151-163`（呼叫 NISA 匯入的地方；因為上面那個方法不會把錯誤往外丟，這裡第159行的錯誤攔截永遠抓不到 NISA 的資料庫失敗，所以163行「寫入失敗狀態」的程式碼永遠不會被執行到）

## 現狀
NISA 匯入每次執行完，都會在資料庫留一筆紀錄，記錄這次匯入「總共幾個檔案、幾個成功、幾個失敗」，並附上一個狀態文字（目前系統裡設計的狀態文字包括：處理中、成功、失敗、部分成功）。

決定要寫哪個狀態的程式碼很簡單：只要「失敗檔案數 = 0」就寫「成功」，只要「失敗檔案數 > 0」（不管是失敗 1 個還是失敗全部）都寫「部分成功」。

問題是：如果這次匯入的檔案，全部都在「存進資料庫」這一步失敗（例如資料庫連線問題、資料衝突），變成「成功檔案數 = 0，失敗檔案數 = 全部」，程式碼還是會寫「部分成功」，不會寫「失敗」。因為「失敗」這個狀態，只有在程式整個匯入流程發生沒有預期到的例外狀況、直接中斷執行時才會被寫入；但 NISA 匯入現在的設計是「存資料庫失敗的話，會自己把錯誤攔截下來、把檔案搬到專門放失敗檔案的資料夾，然後正常把結果回報回去」，並不會讓錯誤往外丟出去中斷流程，所以「失敗」這個狀態對 NISA 匯入來說，實際上永遠不會被用到。

## 補充說明：目前沒有任何畫面會顯示這個問題
查過 `hub-ui` 前端全部的程式碼，目前沒有任何頁面會呼叫「查詢匯入歷史紀錄」這個功能，也沒有任何畫面會顯示這次匯入是成功、部分成功還是失敗。

## 與已知項目的區別
這是在驗證 issue-16（NISA 匯入檔案搬移時機與日期判斷邏輯的修正）過程中，額外發現的全新問題，跟 issue-16 原本描述的問題無關（issue-16 已確認修好）。
