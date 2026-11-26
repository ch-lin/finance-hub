# WONTFIX 18：Sony銀行房貸對帳單頁面上，是否有顯示「還款方式」跟「獎金加碼還款」這兩項資訊

## 判定：WONTFIX（2026-09-15）
**理由**：使用者確認，目前手上沒有「本息均等」以外還款方式、也沒有設定過獎金加碼還款的真實對帳單，沒有實際紀錄可以拿來驗證真實頁面上到底有沒有顯示這兩項資訊。另外，這支解析器本身跟`issue-34`／`issue-45`一樣，屬於即將被Chrome extension取代的背景job，之後會整批棄用——即使之後真的取得樣本、查清楚答案，也已經沒有實際要修改的對象了。因此不再繼續等待樣本，直接標記WONTFIX，跟issue-34維持一致的結論。

---

## 背景

這是在檢查 [issue-34.md](../wontfix/issue-34.md)（Sony銀行對帳單重新匯入房貸時，「還款方式」與「獎金加碼還款」這兩個欄位每次都被無條件覆寫）時，發現一個無法只靠程式碼確認、必須實際看過真實網頁才能回答的問題。

程式碼裡（`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java:123` 與 `:125`）目前不論對帳單頁面內容是什麼，都直接回傳固定值：「還款方式」永遠回傳「本息均等」、「獎金加碼還款」永遠回傳空清單，完全沒有嘗試讀取對帳單頁面上對應的欄位。這件事本身是可以直接從程式碼確認的事實。

但這兩個欄位「為什麼」被寫死，有兩種可能，會導向完全不同的正確修法：

1. 真實對帳單頁面上其實有顯示「還款方式」跟／或「獎金加碼還款」這兩項資訊，只是解析器當初沒有寫對應的讀取邏輯，單純是遺漏——這種情況下應該補上解析邏輯，讓解析器真正從頁面內容讀出真實值。
2. 真實對帳單頁面本身就沒有顯示這兩項資訊——這種情況下寫死回傳固定值是「無從得知真實值」下的合理處理，但 `MortgageJobServiceImpl.java`（第137、139行）目前對這兩個欄位完全沒有null保護、每次重新匯入都無條件覆寫，就會把使用者自己手動維護的設定蓋掉，這部分才是真正該修的地方。

## 不確定的地方

我手上沒有任何一份「本息均等」以外還款方式的真實Sony銀行對帳單樣本（也沒有任何顯示獎金加碼還款資訊的樣本），無法判斷上述兩種可能性中哪一種成立，不能憑猜測認定。

## 相關檔案

- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java:123`（還款方式一律寫死回傳「本息均等」）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/sony/SonyBankMortgageHtmlParserImpl.java:125`（獎金加碼還款一律寫死回傳空清單）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageJobServiceImpl.java:137-139`（重新匯入時無條件覆寫這兩個欄位，沒有null保護）
- `finance-hub/finance-hub-backend/hub-app/src/test/resources/mock-data/`（目前找不到任何Sony銀行房貸對帳單的存檔樣本，不論哪種還款方式）

## 需要使用者確認的問題

麻煩實際登入Sony銀行網站，找一筆還款方式不是「本息均等」（例如「本金均等」）的房貸對帳單頁面，或任何一筆有設定「獎金加碼還款」的房貸對帳單頁面，確認一下：

1. 對帳單頁面上，是否有任何地方顯示「還款方式」（本金均等／本息均等）？
2. 對帳單頁面上，是否有任何地方顯示「獎金加碼還款」相關的金額或設定？

如果方便的話，也麻煩把這個頁面存成網頁存檔提供一份範本（個資可先遮掉），這樣之後如果真的需要修正解析邏輯，也有真實畫面可以核對，不用再靠猜測。如果目前使用者名下的Sony銀行房貸剛好就是「本息均等」、也沒有設定獎金加碼還款，這個問題可能需要等到有其他Sony銀行房貸使用者、或您自己未來換了還款方式之後才有辦法確認。
