# WONTFIX 13：JCB和MUFG信用卡CSV格式判斷條件文字重疊，對帳單有機率被誤判成另一家格式解析

## 判定：WONTFIX（2026-09-15，使用者確認）
**理由**：使用者確認手上沒有真實JCB對帳單CSV樣本可以核對這個文字重疊是否真的會被踩到，同時確認自己實際匯入的檔案目前運作正常，沒有遇過匯入結果日期/金額/店名錯亂的情況。既然理論上的重疊風險目前沒有在她實際使用中發生過，且沒有真實樣本能進一步驗證，這個問題標記為WONTFIX，不需要現在修正。

---

## 背景
這是在檢查信用卡對帳單CSV匯入功能時發現的。使用者匯入信用卡對帳單CSV檔案後，系統會先自動判斷這張對帳單是哪一家發卡公司的格式（JCB、三菱UFJ/MUFG…等），再用對應那家的欄位位置去讀出日期、金額、店名。判斷方式是只看CSV檔案「第一行」的文字內容裡有沒有出現特定的關鍵字。

檢查JCB和MUFG這兩家的判斷條件時，發現這兩組關鍵字在文字上有重疊：
- MUFG的判斷條件是第一行「同時」出現「ご利用金額」和「お支払日」這兩段文字。
- JCB的判斷條件是第一行出現「今回のお振替日」或「今回のお支払日」。

而「今回のお支払日」這幾個字裡面，本來就已經包含「お支払日」這幾個字。也就是說，如果一張JCB對帳單的第一行剛好同時有「今回のお支払日」和「ご利用金額」這兩段文字，這張對帳單就會同時符合JCB和MUFG兩邊的判斷條件。系統只會挑其中一個來用——如果挑到的是MUFG，這張JCB對帳單就會被誤用MUFG的欄位位置去解讀，抓出來的日期、金額、店名很可能都是錯的，而且畫面上不會跳出任何警告或錯誤訊息，只會安靜地產生一筆內容錯誤的信用卡消費記錄。

## 不確定的地方
1. 這個文字重疊本身是可以直接從程式碼字串比對確認的事實，但實際會不會真的被踩到，取決於「真實的JCB對帳單CSV檔案，第一行內容裡是不是真的會同時出現『ご利用金額』這段文字」。我手上沒有真實的JCB CSV樣本可以核對，只能參考這個專案測試程式裡用的測試資料（JcbCsvParserTest.java）。這份測試資料顯示，JCB對帳單真實的第一行只有「今回のお振替日/今回のお支払日 + 日期」這樣的內容，並沒有「ご利用金額」這幾個字；「ご利用金額(円)」字樣是在第三行的交易表頭列才出現，而系統判斷格式時只會讀取檔案的第一行，看不到第三行。照這份測試資料來看，這個重疊目前「不會」被踩到。但JCB對帳單可能因為不同的合作發卡品牌（程式碼裡就特別處理過像「Seven Card」這種掛在JCB網路下、但卡片本身不是JCB品牌的情況）而有不同版面排列，我沒有辦法排除某些JCB系統下的其他版面剛好把這兩段文字都放進第一行的可能性，所以無法斷言「這件事完全不會發生」，只能說目前已知的樣本顯示風險偏低。
2. 就算真的同時符合兩邊的判斷條件，最後系統會挑哪一個格式去解析，要看程式啟動時，系統內部安排這些判斷邏輯清單的先後順序；而程式碼裡完全沒有明確指定這個順序，所以「JCB一定會排在MUFG前面」或「反過來」，都沒辦法只從程式碼本身確定下來，需要實際觀察系統啟動後的行為才能回答，我目前沒有足夠資訊確認這一點。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/parser/JcbCsvParser.java:62-66`（JCB的格式判斷條件）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/parser/MufgCsvParser.java:63-67`（MUFG的格式判斷條件，與JCB的判斷條件文字重疊）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/CreditCardParserFactory.java:59-66`（依序嘗試每一種判斷條件，選第一個符合的格式去解析，沒有指定優先順序）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/FilePreview.java:67-74`（判斷格式時只會讀取檔案的第一行內容，看不到後面幾行）
- `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/creditcard/parser/JcbCsvParserTest.java:91-134`（測試資料裡JCB對帳單真實第一行的樣子，可以看到裡面沒有出現「ご利用金額」）

## 需要使用者確認的問題
1. 您手上是否有真實的JCB信用卡對帳單CSV檔案（尤其是您實際下載、匯入過的那些），可以幫忙確認一下：這個檔案的「第一行」內容裡，是否曾經同時出現「今回のお支払日」（或「今回のお振替日」）和「ご利用金額」這兩段文字？如果從來沒有同時出現過，這個疑慮基本上可以視為目前不會發生。
2. 您過去在匯入JCB對帳單時，有沒有遇過匯入後產生的消費記錄裡，日期、金額或店名看起來明顯不對（例如日期格式錯亂、店名變成一串奇怪的文字、金額跟帳單對不上）？如果有遇過這種情況，會是這個問題確實發生過的直接證據。
3. 如果之後想確認系統實際上會先選JCB還是先選MUFG（也就是萬一真的同時符合條件，會被哪一邊誤判），需要實際觀察系統啟動後判斷邏輯的執行順序才能確定。這件事您想不想現在就花時間追蹤確認，還是先記錄起來，等真的遇到匯入結果錯誤時再回頭處理？
