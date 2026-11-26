# Fixed-42：過渡年 iDeCo/DC 退休扣除額「僅用剩餘月份」計算——現況邏輯正確，不需修正；僅建議調整變數命名與註解以提高可讀性

## 結論 (Conclusion)

**No issue.** 現況「僅用剩餘月份」計算過渡年 iDeCo/DC 扣除額，是在資料限制下唯一站得住腳的保守做法，不需要修正計算邏輯。本文剩下的內容只是說明「為什麼這樣是對的」，並建議做一次不影響任何計算結果的變數命名/註解調整，方便後續維護者理解，不用重新推導這整套判斷。

## 相關檔案與 Package 對照表 (Files Involved)

| Class | Package | 檔案路徑 | 是否需要修改 |
|---|---|---|---|
| `TaxAdvantagedAccountProjector` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java` | 僅建議變數命名/註解，不改變任何計算結果 |
| `YearlySimulationPipeline` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java` | 否，僅供對照 `monthsToAdd` 的定義（第 76-80 行） |
| `ActiveTaxCalculationEngine` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.tax` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java` | 否，僅供對照 `retirementDeduction` 的組成（第 89-91 行） |
| `DefinedContributionAccountSnapshot` | `ch.lin.finance.hub.backend.api.domain.model.accounting.dc` | `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java` | 否，僅供對照「為什麼 `enrollmentDate` 也無法解決這個問題」 |
| `SalaryParserImpl` | `ch.lin.finance.hub.backend.api.app.service.accounting.salary` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryParserImpl.java` | 否，僅供對照「DC Matching 沒有具名欄位、沒有逐月歷史」 |
| `BankTransaction` / `BankTransactionRepository` | `ch.lin.finance.hub.backend.api.domain.model.accounting.bank` / `ch.lin.finance.hub.backend.api.app.repository.accounting.bank` | `hub-domain/.../bank/BankTransaction.java` / `hub-app/.../bank/BankTransactionRepository.java` | 否，僅供對照「未來如果要做得更準，唯一可能的真實資料源在這裡」 |

## 現況說明 (Current State)

`TaxAdvantagedAccountProjector.processAccountsForYear`（第 58-67 行）計算 iDeCo/DC 全年提繳金額：

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator
// class TaxAdvantagedAccountProjector，method processAccountsForYear，第 58-67 行
long annualIdeco = 0;
for (SimulationFund fund : context.getIdecoFunds()) {
    annualIdeco += (age == initialAge) ? fund.getMonthlyContribution() * monthsToAdd
            : fund.getAnnualContribution();
}
long annualDc = 0;
for (SimulationFund fund : context.getDcFunds()) {
    annualDc += (age == initialAge) ? fund.getMonthlyContribution() * monthsToAdd
            : fund.getAnnualContribution();
}
```

在過渡年（`age == initialAge`），`monthsToAdd` 是「剩餘月份數」（`YearlySimulationPipeline.java` 第 76-80 行），所以這裡的 `annualIdeco`/`annualDc` 只是「剩餘月份的提繳金額」，變數名稱是 annual，內容卻是 partial-year。這筆金額被當成 `retirementDeduction` 的一部分（`ActiveTaxCalculationEngine.calculate` 第 89-91 行），但用來計算應課稅所得的薪資是**全年**等效薪資。

分子（扣除額）用剩餘月份，分母（課稅所得計算的基礎）用全年薪資——理論上會讓 `taxableIncomeForIncomeTax` 被高估，`estimatedIncomeTax`/`estimatedInhabitantTax` 偏高，過渡年的 `remainingIncomeTax`/`netIncome` 因此偏低。

## 為什麼現況是正確的 (Why This Is Correct, Not a Bug)

乍看之下，上面的「分子分母時間基準不一致」像是個 bug，直覺的修法是把 `annualIdeco`/`annualDc` 回補成「全年基準」（例如：模擬時點是 7 月，YTD 已提繳 + 剩餘月份推算 = 湊出 12 個月的金額）。但這個「全年」基準本身就是憑空假設出來的，沒有任何資料支撐：

1. **無法驗證使用者是不是從年初就已經加入**。使用者可能 3 月才有第一份薪資單（到職），DC Matching 要等到公司內部流程跑完（例如 6 月）才開始，年底又可能轉換成 iDeCo（企業型 DC 跟個人型 iDeCo 同一時間只會存在一種，不會同時發生）。「模擬時點在 7 月」不代表「1 月到 6 月都在繳」。
2. **就算確定已經加入，也無法驗證中途有沒有中斷或改金額**。DC/iDeCo 供應商（SBI、Sompo）網站只提供「當下快照」，沒有任何逐月扣款歷史可以下載。這是真實環境的資料限制，不是系統設計上偷懶——不管怎麼查，都只能拿到「現在」這一個時間點的月繳金額，拿不到過去這段期間實際發生了什麼。
3. **`enrollmentDate`（資格取得日）也無法解決第 2 點**。`DefinedContributionAccountSnapshot.enrollmentDate` 是真實、已解析的欄位，用它去限制「今年已經過去幾個月」，能解決第 1 點（排除還沒加入的月份），但解決不了第 2 點——加入之後到現在這段期間，一樣沒有任何方式驗證金額有沒有變動、有沒有中斷。
4. **DC Matching 本身也沒有比 iDeCo 更可靠**。雖然 DC Matching 會反映在真實薪資單的 `netAmount` 總額裡（[Fixed-41.md](Fixed-41.md) 已經利用這一點，用殘值公式正確還原現金流基準），但薪資單解析器（`SalaryParserImpl.mapCoreField`）沒有 DC Matching 的具名欄位，實際金額只落入沒人讀取的 `details` map。要單獨抓出「DC Matching 逐月扣了多少」（這裡跟 Fixed-41 不同——稅務扣除額必須把 DC Matching 的金額單獨抓出來，不能跟其他扣除項混在一起計算殘值，因為 ESPP 不能抵稅、DC Matching 可以抵稅，兩者稅法待遇不同），一樣只能靠「目前月繳金額 × 已經過去月數」外推，一樣無法驗證是否連續、是否從年初就開始。

**如果硬是回補成全年基準**：一旦估算的月數比真實情況多（例如系統假設 1-6 月都有繳，但使用者其實 9 月才剛加入），會產生**使用者從來沒有真正提繳過的稅務扣除額**，讓估算稅金比真實情況更低、現金水位比真實情況更高——等於是免費現金大放送，使用者可能因此依據虛高的現金水位做出不該做的財務決策（提高當年支出、提前買房等）。

**維持現狀（僅用剩餘月份）**造成的後果則是：模擬顯示的現金比真實情況略低，過渡年稅金理論上略微高估，使用者在真正的年末調整/確定申告時，會拿到比模擬預期更多的退稅——這在現實世界是無害、甚至令人開心的結果。

模擬要 100% 準確，前提是手上有 100% 準確的資料。DC/iDeCo 在資料源上就是只有「現在」這個快照，沒有歷史，無法驗證過去、也無法預測未來（因為 DC Matching 跟 iDeCo 可能中途互轉，加入時點也不固定）。在資料不足以支撐準確計算的情況下，選擇會系統性偏向保守（多繳稅、少現金）的估算方式，優於選擇會系統性偏向樂觀（少繳稅、多現金）的估算方式——兩種誤差在現實世界造成的後果並不對稱：保守誤差的後果是使用者事後得到意外的驚喜，樂觀誤差的後果是使用者可能已經依據虛假的數字做出真實世界的財務決策。因此現況「僅用剩餘月份」是正確、應該保留的設計。

## 建議調整 (Suggested Improvement — Readability Only)

不修改 `annualIdeco`/`annualDc` 的計算邏輯，只做兩件不影響任何計算結果的事，避免未來（不論是人、或是另一次 AI review）誤以為這是尚待修正的 bug：

1. **變數改名**，讓過渡年語境下的變數名稱誠實反映「這是剩餘月份，不是全年」，避免 `annual` 這個字誤導後續維護者。
2. **加上簡短註解**，說明「刻意」不做 YTD 回補的原因，讓後續維護者不需要重新把這整個判斷過程走一遍。

**為什麼不建議現在就去做「銀行交易明細比對」這種更準確的做法**：iDeCo 是直接從銀行帳戶扣款，`BankTransaction`（`hub-domain/.../bank/BankTransaction.java`）確實有逐筆的 `description`/`memo` 欄位，`BankTransactionRepository.findByTransactionDateBetween` 也能撈出某段期間的逐筆交易，理論上如果扣款說明文字有固定格式，是可以比對出真實的 iDeCo 逐月扣款記錄，比現在的「快照外推」更準確。但這需要新增交易描述比對/分類邏輯，屬於新功能開發，不是這次商業邏輯 review 的修正範圍，先不處理。如果之後想做，建議另立 planned 文件追蹤。

## 最小範例程式碼 (Sample Code)

```java
// 檔案: hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
// package: ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator
// class TaxAdvantagedAccountProjector，method processAccountsForYear（僅改名+加註解，計算結果不變）

// 過渡年（age == initialAge）時，這裡故意只計算「剩餘月份」的提繳金額，不去回補 YTD 已提繳金額。
// 原因：DC/iDeCo 供應商（SBI、Sompo）只提供當下快照，沒有逐月扣款歷史，無法驗證使用者是否從年初/到職日
// 就開始提繳、中途有沒有中斷或改金額（DC Matching 跟 iDeCo 甚至可能中途互轉）。任何用「目前月繳金額 × 已過月數」
// 回補全年基準的做法，都建立在無法驗證的假設上，一旦猜錯會產生使用者從未真正提繳過的「免費」稅務扣除額，
// 讓估算稅金/現金雙雙失真在錯誤（樂觀）的方向。維持「僅用剩餘月份」會讓過渡年稅金理論上略微高估，但這是
// 保守方向的誤差，後果只是使用者在真正的年末調整時拿到比預期更多的退稅——見 Fixed-42.md 完整討論。
long remainingMonthsIdecoContribution = 0;
for (SimulationFund fund : context.getIdecoFunds()) {
    remainingMonthsIdecoContribution += (age == initialAge) ? fund.getMonthlyContribution() * monthsToAdd
            : fund.getAnnualContribution();
}
long remainingMonthsDcContribution = 0;
for (SimulationFund fund : context.getDcFunds()) {
    remainingMonthsDcContribution += (age == initialAge) ? fund.getMonthlyContribution() * monthsToAdd
            : fund.getAnnualContribution();
}
```

## 修正報告 (Resolution Report)

已確認現況的計算邏輯是正確且保守的安全設計，**未對計算結果與商業邏輯做任何修改**。
為確保此設計意圖能被未來維護者準確理解，已完成以下優化：

1. **變數精準命名**：
   - 在 `TaxAdvantagedAccountProjector.java` 中，將過渡年可能引起誤會的 `annualIdeco` 與 `annualDc` 重新命名為 `remainingMonthsIdecoContribution` 與 `remainingMonthsDcContribution`。
2. **新增防禦性註解 (英文)**：
   - 於迴圈上方詳細註記了「為何刻意不回補 YTD」的原因。明確指出在缺乏歷史扣款明細下，擅自外推 YTD 會產生未經證實的「免費稅務扣除額」，進而導致現金流被危險地高估。此註解能有效防止未來有人將其誤認為 bug 而進行錯誤修正。

Resolves: Fixed-42
