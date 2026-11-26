# WONTFIX 17：損保ジャパンDC提繳金額若分兩張表格，匯入可能漏抓或誤判造成現金算錯

## 判定：WONTFIX（2026-09-15，使用者確認）
**理由**：使用者確認「掛金の配分割合」頁面是針對個股的分配金額設定，公司提撥跟員工自己配比的錢是合併在同一張表格裡計算的，不是分成兩張獨立表格。這證實了本文件「不確定的地方」段落裡「也有可能本來就只有一張合併表格」這個假設成立，目前解析器只抓第一張表格、把總金額拿去減公司提撥金額反推員工配比金額，這個算法本身是對的，不是bug。

---

## 背景

這是在檢查「企業型DC（公司提撥制）」在模擬引擎裡怎麼計算「員工自己額外配比金額」時發現的疑問。

模擬引擎需要知道使用者每年除了公司提撥之外，自己還額外拿了多少現金去追加投資（企業型DC底下員工可以自行選擇要不要追加提繳，稱為「配比提繳」），因為這筆錢要從使用者的現金流裡再扣一次。目前程式碼的算法是（`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java:265-270`）：先把「這整年提撥進DC帳戶裡所有基金的總金額」加總起來，再減去「公司自己提撥的金額」，剩下的差額就當作是「員工自己額外配比、要從個人現金流再扣一次」的部分。

問題出在「這整年提撥進DC帳戶裡所有基金的總金額」這個數字是怎麼來的。它完全來自匯入損保ジャパンDC網站「掛金の配分割合」（提繳金額怎麼分配到各個基金）頁面時抓到的資料。目前的匯入程式（`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java:94-116`）只會抓畫面上「第一個」符合特定樣式的表格，把裡面每一列的「配分金額」欄位加總起來，當作各個基金的提撥金額，程式碼裡沒有任何邏輯會去檢查畫面上是不是還有第二張表格、更不會把兩張表格的金額合併起來。

而「公司自己提撥的金額」這個數字，則完全來自頁面上另一個不相關的地方（畫面最上方顯示的一個獨立欄位，`SompoDcAllocationParserImpl.java:72-75`），跟上面那張基金表格是兩個完全不同的畫面來源。這兩個來源分別怎麼被讀進模擬引擎，可以在 `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java:68-93` 看到——確實是兩個互不相關的欄位。

## 不確定的地方

上面這個算法（總金額 減 公司提撥金額 = 員工自己配比金額）能不能算對，關鍵在於一個假設：畫面上抓到的「第一張表格」裡的金額，本來就已經同時包含「公司出的錢」跟「員工自己出的錢」兩者的合計。

這個假設是否成立，完全取決於損保ジャパンDC網站「掛金の配分割合」頁面實際上長什麼樣子——這台機器上沒有這個頁面的真實畫面或存檔可以核對（`finance-hub/finance-hub-backend/hub-app/src/test/resources/mock-data/dc/` 目錄下只找到帳戶首頁跟資產現況兩個頁面的存檔樣本，沒有「掛金の配分割合」頁面的樣本），也沒有任何自動測試涵蓋這支解析程式，所以無法只靠讀程式碼判斷這個假設對不對，一定要實際看過畫面才能確認，不能用猜的。

企業型DC制度常見的情況是：公司提撥的錢跟員工自己配比的錢，可以各自選擇不同的投資組合比例，不一定相同。如果損保ジャパン網站把這兩種來源的錢分成兩張不同的表格顯示（各自列出自己的基金分配跟金額），目前的程式只抓畫面上第一張表，就會出現以下兩種可能的錯誤，哪一種發生要看「第一張表格」實際上裝的是哪一種來源的錢：

- 如果抓到的剛好是「公司提撥」那張表：算出來的「總金額」會少了員工配比那一半，反推出來的「員工自己配比金額」很可能因此變成負數；程式碼裡有一個保護機制會直接把負數歸零，結果就是「完全沒有扣到員工配比這筆現金」，讓使用者的現金流被高估，模擬結果看起來比實際上寬裕。
- 如果抓到的剛好是「員工配比」那張表：反過來，公司出的錢完全沒被記錄到，這時候「總金額」其實只等於員工配比金額本身，用「總金額 減 公司提撥金額」去反推，等於是拿員工自己的錢去減公司的錢，算出來的結果同樣不對，可能讓使用者被多扣一次不該扣的現金，模擬結果看起來比實際上緊。

也有可能損保ジャパン網站上這個頁面本來就只有一張合併表格（公司提撥和員工配比投資同一套基金組合，金額直接合併顯示在同一張表裡），這樣目前的算法其實是對的，完全沒有問題。以上兩種可能（一張表 vs 兩張表）都合理存在，需要實際看過畫面才能判斷是哪一種，我沒有辦法憑猜測認定。

## 相關檔案

- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java:265-270`（用「總提撥金額 減 公司提撥金額」反推員工配比金額的計算公式）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java:72-75`（公司提撥金額的來源：畫面上方一個獨立欄位）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java:94-116`（各基金提撥金額的來源：只抓畫面上第一個表格，沒有處理第二張表格的邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java:68-93`（把上面兩個來源分別讀進模擬引擎的地方，可以看出兩者確實來自完全不同的畫面欄位）
- `finance-hub/finance-hub-backend/hub-app/src/test/resources/mock-data/dc/`（目前只有帳戶首頁跟資產現況頁面的存檔樣本，沒有「掛金の配分割合」頁面的樣本，這也是為什麼這個疑問無法直接用程式碼確認）

## 需要使用者確認的問題

麻煩實際登入損保ジャパンDC網站，打開「掛金の配分割合」這個頁面（顯示提繳金額怎麼分配到各個基金的那一頁），確認一下：

1. 這個頁面上，「公司提撥要怎麼分配投資」跟「員工自己配比要怎麼分配投資」，是顯示在同一張表格裡（金額已經合併在一起），還是分成兩張獨立的表格（各自列出各自的基金分配跟金額）？
2. 如果是分成兩張表格：目前的匯入功能（另存頁面再匯入）實際上抓到、記錄下來的是哪一張表格的資料？兩張表格各自的金額大概是多少（方便對照哪一個被記錄到、哪一個完全沒被記錄到）？

如果方便的話，也麻煩把這個頁面存成檔案提供一份範本，這樣之後如果真的需要修正匯入邏輯，也有真實畫面可以核對，不用再靠猜測。
