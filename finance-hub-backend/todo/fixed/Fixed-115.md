# Fixed 115：已判定維持現狀——淨收入計算不扣除持股信託（ESPP）扣款，這是正確的設計，不是bug

## 這份issue原本的方向已經被推翻，維持現狀
這份issue原本的建議是「`netIncome`應該扣除`esppContribution`這一項」。使用者確認後，這個方向是錯的，現狀（不扣除）才是正確設計。

## 為什麼不該扣除

**第一個理由：ESPP扣款這個數字，不是一個能往未來推算的東西。**
ESPP不像社會保險、所得稅這種每個月必然發生、規則確定的扣款項目——它是使用者自己決定要不要加入、要扣多少的自願行為，而且系統裡根本沒有一個欄位能表達「這個人未來預期會持續扣多少ESPP」。

已確認：`esppContribution`這個欄位（`SalaryParserImpl.java:164`）只存在於`SalaryRecord`這張薪資單解析出來的歷史紀錄裡；整個`simulation`這條計算路徑完全沒有讀取這個欄位，是零命中。系統裡也找不到任何類似`EsppUserSettings`或`monthlyEsppContribution`這種「使用者設定未來ESPP扣款」的欄位——不像薪資有`SalaryUserSettingsRepository`可以設「預期年薪成長率」，ESPP連這種「使用者預期」的立足點都不存在。

**第二個理由：不是每個人的公司都有ESPP，系統連「這個人有沒有ESPP」這件事都沒有地方可以查。**
就算放棄「預測未來金額」這個念頭，想說「那至少假設維持最近一筆扣款金額繼續扣」，這個假設也掛不上去——因為系統裡沒有任何欄位會標記「這個使用者的公司有沒有ESPP」。沒有ESPP的使用者，系統看到的`esppContribution`跟「這個月剛好沒扣」的使用者，長得完全一樣（都是沒有紀錄或0），無法區分「本來就沒有這個東西」跟「這個月剛好沒扣」。

**第三個理由（技術查證補強，不是推翻前兩個理由的理由，是從另一個角度印證同一個結論）：如果硬要扣，會讓資產憑空蒸發。**
已確認`companyStockBalance`（公司股票餘額）只採信使用者匯入的E*Trade快照，模擬未來時完全不會自動增加——這是跟[WONTFIX-22](../wontfix/WONTFIX-22-espp-contribution-not-read-by-simulation.md)（公司股票未來不模擬成長，判定理由是「沒有人能預先知道自己未來會不會繼續扣款買股票」）同一個邏輯下的既有設計。如果在`netIncome`裡扣除ESPP，模擬未來每一年現金都會被扣掉這筆錢，但公司股票餘額完全不會對應增加——這筆錢在模擬裡會直接消失，不是被扣掉去買了股票，是純粹的憑空蒸發，而且模擬年數一拉長，蒸發的金額會隨著年數線性放大。

**第四個查證：過渡年（今年）的YTD基準計算，本來就沒有扣ESPP的問題。**
已確認`computeYtdNetIncomeBaseline`（`YearlySimulationPipeline.java:359-366`）跟全年`netIncome`公式（`ActiveTaxCalculationEngine.java:214`）用的是同一套「不扣ESPP」定義，兩邊基準一致，相減後精準等於「今年剩餘月份的淨收入」，不會因為ESPP而產生任何偏差。使用者今年已經過去的月份，實際扣完ESPP後到手的現金，是透過模擬起點的銀行快照直接反映進去的，不是靠這套`netIncome`公式去反推，兩條路徑互不干擾。

## 跟另一份WONTFIX判定的關係
`todo/wontfix`資料夾裡有一份判定（原始編號issue-74，寫成時的舊編號，之後wontfix資料夾整體重新編號過，這裡不追蹤新編號）講的是「未來會不會繼續買ESPP、公司股票會不會成長」，跟本篇原本想討論的「這個月已經真實扣掉的ESPP，有沒有被正確反映到淨收入」是兩個不同的問題。但查證下來，這兩個問題的根源其實是同一件事——不管是「未來要不要繼續扣」還是「這個月扣了的錢該不該扣除」，都卡在同一個事實：系統沒有任何欄位能表達「這個使用者的ESPP狀態」，兩者最終都指向同一個結論，不衝突。

## 相關檔案（供回溯參考）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryParserImpl.java:100-119, 164`（`esppContribution`欄位的解析，只存在於歷史紀錄）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:214`（`netIncome`公式，維持現狀不扣ESPP）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:359-366`（`computeYtdNetIncomeBaseline`，已查證跟全年公式基準一致）
