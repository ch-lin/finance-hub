# WONTFIX 15：iDeco對帳單解析器沒抓公司加碼提繳金額，全算成使用者自付款

## 判定：WONTFIX（2026-09-15，使用者確認）
**理由**：使用者直接確認並澄清一個關鍵事實：個人iDeCo從制度開始就不存在「公司加碼提繳」這件事，能有公司出錢加碼提繳的只有企業型DC。這代表本文件「不確定的地方」第1點的前提（使用者的iDeCo背後公司透過iDeCo+加碼提繳）從制度設計上就不成立，兩支iDeCo解析器把「公司提繳」欄位寫死成0，其實就是正確答案，不是bug。第2點（SBI對帳單頁面上這個欄位存不存在）因此也不需要再查證。

---

## 背景

這是在檢查「企業年金／iDeCo這類退休帳戶的對帳單匯入邏輯，跟後續稅務／現金流試算之間有沒有對得起來」時發現的。

系統裡有兩種退休金帳戶會匯入對帳單：企業型DC（公司幫員工開的退休金帳戶）跟iDeCo（使用者自己去銀行/證券商開的退休金帳戶）。這兩種帳戶都有可能出現「公司額外幫忙加碼提繳」的情況：企業型DC本來就是公司出錢；iDeCo則是透過「iDeCo+（中小事業主掛金納付制度）」，讓中小企業主自願幫員工在iDeCo帳戶裡加碼提繳一筆錢。

比對兩種帳戶的對帳單解析程式碼，發現企業型DC的解析器有特別去對帳單畫面上找「公司出的錢」跟「使用者自己出的錢」這兩個獨立欄位，分開記錄下來；但兩支iDeCo對帳單解析器，完全沒有嘗試去找「公司加碼提繳」對應的欄位，直接把這個欄位的值寫死成0。

**企業型DC解析器（有分開處理公司提繳／個人提繳）：**
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java:71-81` — 分別去對帳單畫面上找「企業提繳」欄位（`span#D_Header1_lblKigyoContributionUnit`）跟「個人提繳」欄位（`span#D_Header1_lblKojinContributionUnit`），兩個金額各自記錄，不會混在一起。

**兩支iDeCo解析器（都沒有嘗試找公司加碼提繳的欄位）：**
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoStatusParserImpl.java:95-103` — 這支解析器（對帳單裡的「資產狀況」頁面）完全沒有嘗試解析任何一筆提繳金額，「公司提繳」「使用者提繳」「合計提繳」三個欄位全部直接寫死成0。
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoAllocationParserImpl.java:69-83` — 這支解析器有去對帳單上找「使用者自己提繳」的欄位（`span#D_Header1_lblKojinTotalContributionUnit`），但「公司提繳」欄位同樣直接寫死成0，完全沒有嘗試去對帳單上找對應的欄位。

值得注意的是，`SbiIdecoAllocationParserImpl` 抓「使用者提繳」欄位時用的命名方式（`D_Header1_lblKojinTotalContributionUnit`），跟企業DC解析器抓「個人提繳」欄位的命名方式（`D_Header1_lblKojinContributionUnit`）幾乎一樣，這代表SBI網站在命名這類欄位時可能有一致的規則，但目前iDeCo這兩支解析器都沒有依照同樣規則去嘗試找「公司提繳」對應的欄位。

這筆被寫死成0的金額，後續會流到兩個地方，都可能被使用者實際看到、影響到判斷：
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:89` — 試算「可以抵稅多少錢」時，iDeCo提繳金額（`idecoDeduction`）直接等於整筆iDeCo提繳的金額，沒有先扣掉公司加碼的部分。
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/InvestmentContributions.java:31-33` — 試算「使用者自己每年花了多少現金去投資」時，也是把整筆iDeCo提繳金額都算成使用者自己出的錢。

也就是說：如果使用者的iDeCo帳戶背後真的有公司透過iDeCo+幫忙加碼提繳，系統目前的匯入流程會把公司出的那部分錢，全部誤判成「使用者自己出的錢」，可能讓試算出來的可抵稅金額跟現金流數字失真（因為公司出的那部分並不是真的從使用者自己銀行帳戶流出去）。

## 不確定的地方

這件事情能不能成立，關鍵在於兩個我目前無法確認的前提，都不適合我自己猜測下結論：

1. **使用者的iDeCo帳戶背後，公司是否真的有透過iDeCo+幫忙加碼提繳？** 一般人自己去證券商開的iDeCo，大多是自己單獨提繳，沒有公司加碼這件事；只有在使用者任職的公司（通常是中小企業）有另外去申請「iDeCo+」制度時，才會出現這筆公司加碼的錢。這牽涉到使用者實際的職場福利狀況，我不知道也不該替使用者假設。

2. **就算真的有iDeCo+加碼提繳，SBI的iDeCo對帳單網頁上，是否真的會顯示對應的「公司提繳」欄位、顯示在畫面的哪個位置？** 我手上沒有真實的SBI iDeCo對帳單網頁樣本可以核對，所以無法確認這個欄位在SBI的頁面上到底存不存在、DOM結構是什麼樣子。如果SBI的iDeCo對帳單頁面本身就沒有顯示這筆金額（例如iDeCo+的錢是透過完全不同的管道處理，不會顯示在個人對帳單上），那麼現在解析器寫死成0其實就是唯一能做的處理方式，不算問題。

如果上面兩個前提中有一個不成立（使用者的iDeCo沒有公司加碼提繳，或SBI對帳單網頁上本來就沒有這個欄位），那麼這整件事就不是問題，寫死成0反而是對的；只有在兩個前提都成立時，才會真的造成試算失真。

## 相關檔案

- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoStatusParserImpl.java:95-103`（「公司提繳」「使用者提繳」「合計提繳」三個欄位全部寫死成0，完全沒有嘗試解析任何提繳金額）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoAllocationParserImpl.java:69-83`（有去抓「使用者提繳」欄位，但「公司提繳」欄位一樣寫死成0，沒有嘗試找對應欄位）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java:71-81`（對照組：企業型DC解析器確實有分開找「公司提繳」跟「個人提繳」兩個欄位）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:89`（試算可抵稅金額時，iDeCo提繳金額整筆拿去計算，沒有先扣掉公司加碼的部分）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/InvestmentContributions.java:31-33`（試算「使用者自己花了多少現金投資」時，iDeCo整筆金額被算成使用者自付款）

## 需要使用者確認的問題

1. 您（或未來會用這個功能的家人/其他使用者）名下的iDeCo帳戶，背後的公司是否有透過「iDeCo+（中小事業主掛金納付制度）」幫忙加碼提繳過一筆錢？如果從來沒有，這個問題就不需要處理。

2. 如果有的話，您在SBI證券的iDeCo對帳單網頁上，是否曾經看到過「公司提繳／事業主提繳」這樣獨立顯示的一筆金額？大概顯示在畫面的哪個位置（例如跟「個人提繳」欄位在同一個表格裡，還是在別的地方）？如果方便的話，能否提供一份把個資（姓名、帳號等）遮掉之後的對帳單網頁截圖或HTML，讓後續能實際核對這個欄位存不存在、該用什麼方式去抓。
