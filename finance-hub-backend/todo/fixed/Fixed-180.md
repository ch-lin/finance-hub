# [FIXED] 180：job import（系統管理者透過SSH掃描資料夾、批次匯入CSV/HTML檔案）合併既有資料跟新解析結果時，多個領域的merge邏輯沒有正確區分「parser能拿到的事實欄位」跟「parser拿不到、純使用者設定的欄位」，導致使用者設定值在reimport時被悄悄覆寫掉——這份文件是跨領域的總覽，具體每個領域各自的錯誤情境、修正方式見180-01（房貸）、180-02（銀行/信用卡）、180-03（訂單項目）

## 這份issue怎麼被發現的
這是這次Source Code Review逐一讀過domain實體後，確認「同一個表格裡，有欄位是使用者自己在前端輸入、parser永遠不會寫入；同時也有欄位是parser解析出來的事實值」時發現的系統性問題。最初（2026-10-13）討論時考慮過一個後來沒有採用的替代方案：把每個領域切成「主表（事實欄位）+設定表（使用者欄位）」兩張表，讓使用者欄位活在匯入邏輯完全碰不到的地方。這個替代方案討論到銀行/信用卡交易、房貸、訂單項目等領域都快要定案時，使用者在實際讓Gemini動手改Amazon/Rakuten/Sofmap時，重新評估了整體投資報酬率，決定不採用這個分表方案，改成現在這份文件定案的方向：**不分表，所有領域維持現有的單一張表**；job import合併既有資料跟這次解析結果時，在合併邏輯裡逐欄位判斷：這個欄位parser能拿到真實事實就直接覆蓋資料庫現有值；這個欄位parser拿不到（純使用者自己填的設定值）就保留資料庫現有值，不要被這次合併動作的null值打掉。

以下用「分表方案」指稱那個沒有採用的替代方案、「merge規則」指稱這份文件現在定案的方向，方便後文對照說明兩者的差異與取捨。

**分表方案（沒有採用）**：每個領域切成「主表（事實）+設定表（使用者欄位）」兩張表，兩張表之間沒有商務ID的領域（銀行/信用卡）要靠「日期+金額+店名」鬆散配對在查詢時即時join接回設定表的值；有商務ID的領域（房貸、NISA、訂單項目）可以用商務ID穩定關聯兩張表。

**merge規則（這份文件定案的方向）**：所有領域都維持單一張表，不分表。job import合併既有資料跟這次解析結果時，在merge邏輯裡逐欄位判斷：這個欄位parser能拿到真實事實就直接覆蓋DB現有值；這個欄位parser拿不到（純使用者自己填的設定值）就保留DB現有值，不要被這次合併動作的null值打掉。

## 相關檔案
每個領域各自的檔案見180-01（房貸）、180-02（銀行/信用卡）、180-03（訂單項目）；下方「各領域盤點清單」的「檔案」欄位列出所有涉及的domain實體。

## 現狀：為什麼不採用分表方案——投資報酬率不對稱
分表方案解決的問題，只有「系統管理者自己跑job import整批清空重建主表時，使用者設定會不會跟著消失」這一個情境，而且只有系統管理者一人會觸發這個情境。但分表的代價是永久性的——**所有人**（系統管理者跟一般使用者）每一次查詢交易列表、每一次GET，都要承擔「主表跟設定表要怎麼接回來」這個額外成本：有商務ID的領域要多一層join；沒有商務ID的領域（銀行/信用卡）要承擔鬆散配對本身的不準確性，而且這個不準確性是在**每一次查詢**都可能發生，不是只在reimport那一刻。用一個發生頻率高、受眾廣的永久代價，去解決一個發生頻率低、只影響一個人的問題，投資報酬率不成立。

merge規則把這個不對稱反過來：代價（逐欄位判斷覆蓋或保留）只發生在job import真正執行的那一刻，而job import永遠只有系統管理者會觸發；一般使用者（透過Extension或手動UI操作）完全不會感受到這一層判斷的存在，也不會有任何join或鬆散配對的查詢成本。

分表方案也沒有解決「哪個欄位歸哪一類」這個分類工作，只是把它搬到另一個地方：不管分不分表，新增一個會被匯入的欄位時，都必須先判斷「這個欄位parser能不能拿到真實事實」——這個分類工作本身逃不掉。分表方案在這個必做的分類之外，額外疊加了一層「兩張表之間要怎麼關聯、新增欄位時要決定它屬於主表還是設定表」的維護成本；對Amazon/Rakuten/Sofmap這種多層繼承（`BaseOrder`/`BaseOrderItem`跨三個平台）的領域，這層成本會被放大成好幾層的跨表比對。merge規則把這個分類工作留在原地——同一張表裡，用註解或文件標明每個欄位屬於哪一類，不需要額外決定「這個欄位該搬去哪張新表」。

## 為何是問題：各領域盤點清單
以下是逐領域讀過domain實體後，確認「同一個表格裡，有欄位是使用者自己在前端輸入、parser永遠不會寫入；同時也有欄位是parser解析出來的事實值」的清單。

| 實體 | 檔案 | 使用者輸入欄位（parser拿不到，merge時要保留DB現有值） | 匯入事實欄位（parser能拿到，merge時直接覆蓋DB現有值） | merge時用什麼比對鍵找到既有那一列 | 注記 |
|---|---|---|---|---|---|
| 房貸 | [MortgageRecord.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/MortgageRecord.java) | `occupancyDate`、`residenceType`、`propertyType`、`taxDeductionEligible`、`taxDeductionEndYear`、`taxDeductionRate`、`maxTaxDeductionAmount`、`hasFiveYearRule`、`earlyRepayments`（提前還款計劃，獨立子表`MortgageEarlyRepayment`，透過外鍵關聯回`MortgageRecord`） | `loanName`、`totalLoanAmount`、`currentBalance`、`balanceRecordDate`、`startDate`、`endDate`、`loanTermMonths`、`interestRate`、`rateType`、`repaymentMethod`、`monthlyPayment`、`bonusPayments` | `bankName + contractNumber`（已確認可用） | `earlyRepayments`這個bug的具體情境跟修法，見[issue-180-01](issue-180-01.md) |
| Amazon/Rakuten/Sofmap訂單項目 | [BaseOrderItem.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/BaseOrderItem.java) | `category`（透過NotebookLM等外部工具批次分類寫入）、`privacyLevel` | `name`、`quantity`、`unitPrice`、`seller`、`itemId` | 訂單層級：平台核發的`orderId`（已有唯一性約束）；訂單底下的項目層級：`name`+`unitPrice` | 具體情境跟修法，見[issue-180-03](issue-180-03.md) |
| 銀行/信用卡交易 | [BankTransaction.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/bank/BankTransaction.java)、[CreditCardTransaction.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/creditcard/CreditCardTransaction.java) | `category` | `bankName`/`cardName`、交易日期、摘要/店名、金額、餘額、`memo` | 沒有商務ID，用「日期+金額+店名」鬆散配對 | 具體情境跟修法，見[issue-180-02](issue-180-02.md)；`memo`現狀問題（見下方）維持獨立追蹤 |
| NISA提繳設定 | [NisaContributionSetting.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java) | `expectedReturnRate` | `accountType`、`fundName`、`course`、`settingAmount`、`monthlyEquivalentAmount` | `fundCode + accountType`（欄位已就位，比對邏輯待接線，見注記） | `fundCode`欄位已新增、`SbiNisaParserImpl.extractFundCode()`已確認會解析，這個代碼是「投信協会コード」（使用者依實際使用經驗提供的資訊）。但`@EqualsAndHashCode`目前仍是`{"recordDate", "accountType", "fundName", "course"}`，`NisaContextBuilder`也仍用`fundName`比對，尚未接線——使用者確認這是正常的待動工項目。`monthlyEquivalentAmount`這個衍生計算值已在[issue-183](issue-183.md)定案整欄拔除，跟這份issue是獨立的問題 |
| NISA持股快照 | [NisaSnapshotRecord.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java) | `expectedReturnRate` | `recordDate`、`accountType`、`fundName`、`units`、`averageCost`、`currentPrice`、`totalPrincipal`、`currentMarketValue`、`unrealizedGainLoss` | `fundCode + accountType`（同上，待接線） | 跟`NisaContributionSetting`同一個狀態 |
| 保單 | [InsurancePolicy.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/insurance/InsurancePolicy.java) | 見注記 | `policyType` | 保單號碼（既有關聯方式，不受這份issue的merge規則影響） | `policyName`/`annualPremium`/`endDate`這三個欄位是「同一欄位、兩種來源都能寫」的特殊案例，不是「使用者欄位vs事實欄位是兩組不同欄位」，merge規則（覆蓋或保留二選一）沒辦法處理這種情況，依然需要[Plan-18-01](../planned/Plan-18-01-extension-preview-reconcile-ui.md)的Extension逐欄位checkbox機制，不屬於這份issue要修的範圍 |
| DC/iDeCo使用者設定 | [DefinedContributionUserSettings.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java) | 整表都是使用者設定 | — | 不適用 | 整表本來就是使用者設定，不涉及merge規則 |
| DC/iDeCo帳戶快照 | [DefinedContributionAccountSnapshot.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)、[DefinedContributionFundSnapshot.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionFundSnapshot.java) | `enrollmentDate`在「年資覆寫」情境下的角色特殊，見[issue-152-01](issue-152-01.md) | 見[issue-180-04](issue-180-04.md) | **帳戶層級**：不適用（這個領域的問題不是「使用者欄位vs事實欄位」分類錯誤，是同一筆記錄的不同事實欄位分別來自三個不同頁面，job import合併時需要知道「這次是哪個頁面」才能正確決定覆蓋哪些欄位）。**Fund層級**：**待確認**——`fundId`只是資料庫自增主鍵，不是商務ID；查證確認四支相關parser都沒有解析任何穩定代碼，目前用`fundName`字串比對，有沒有網頁上可用的代碼需要真實樣本才能確認 | 這個領域有兩個相關但獨立的缺口，都詳見[issue-180-04](issue-180-04.md)：(1) `merge()`目前用「這個欄位的值是不是大於0」去猜測「這次解析結果有沒有填這個欄位」，但0在這個領域是合法的真實業務值（例如停止提繳時月提繳金額真的變成0），不是「沒有資料」的代號；(2) fund層級沒有商務ID，用`fundName`比對的風險跟[issue-181](issue-181.md)NISA領域相同，且會跟(1)的架構問題疊加 |
| 分類 | [TransactionCategory.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/category/TransactionCategory.java) | 整表都是使用者設定 | — | 不適用 | 這張表自己沒有問題，真正的問題在於引用它的那些交易/訂單項目實體被整批重建時，引用關係跟著消失——已經反映在上面「銀行/信用卡交易」「訂單項目」兩列的merge規則裡 |
| 年金 | [PensionRecord.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/pension/PensionRecord.java) | 目前無（見注記：商務ID已就位，待UI做出來即可直接套用merge規則） | 全部（基礎/厚生年金額、加保月數、已繳保費等） | `recordDate`（搭配`ownerUserId`隔離，見[PensionRecord.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/pension/PensionRecord.java)的`@UniqueConstraint(columnNames = {ownerUserId, recordDate})`） | `PensionRecordController`目前只有GET/POST/DELETE，沒有PUT/PATCH，現狀不存在部分欄位被使用者手動改的情況，所以現在不需要merge規則。但`PensionRecord`本質是時間序列快照（同一使用者會累積多筆不同`recordDate`的記錄，模擬引擎`PensionContextBuilder`取`recordDate`最新的一筆），不是單一記錄：`recordDate`在「同一使用者底下」本身就已經是唯一識別這一筆快照的鍵，不需要另外找「年金編號」（基礎年金番号）當商務ID——這個編號對同一人來說固定不變、不會隨匯入的是哪一期通知書而不同，結構上無法用來區分「這是哪一筆記錄」，而且跨使用者辨識身分的問題已經由`ownerUserId`處理掉，不需要parser額外解析。之後如果要做UI讓使用者編輯年金欄位，不需要再找商務ID，直接用`recordDate`接上merge規則即可 |
| 股票（E-Trade，8張表：帳戶快照、ESPP/RSU在庫持股、現金交易、RSU授予/事件/分批排程、ESPP購買歷史） | [BaseStockLot.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/BaseStockLot.java)等8張domain實體 | 無——全部欄位只來自E-Trade解析，沒有PATCH/PUT端點 | 全部欄位 | **原本「grantNumber已確認可靠」的結論被推翻**：使用者指出E-Trade可同時持有多家公司股票（例如CRM、CDNS），`grantNumber`是各公司各自的序號系統，沒有保證跨公司不撞號——`ETradeBenefitRsuGrant`（`grantNumber`）、`ETradeBenefitRsuEvent`（`grantNumber+eventDate+eventType+quantity`）、`ETradeBenefitRsuVestSchedule`（`grantNumber+vestPeriod`）這三張表都缺`symbol`，必須補上才算真正可靠；`ETradeEsppStockLot`/`ETradeRsuStockLot`雖然DB層`@UniqueConstraint`已含`symbol`，但`@EqualsAndHashCode`漏了`symbol`，是現有程式碼的既有bug。使用者決定6張表（含`ETradeAccountSnapshot`、`ETradeCashTransaction`、`ETradeBenefitEsppPurchase`這三張原本不被認為有撞號風險的表）全部補上`symbol`欄位。新發現頁面頂端帳戶切換按鈕（例如`"Stock Plan (CRM) -4258"`）是比`BaseStockLot`現有正則表達式更可靠的symbol來源，兩個頁面共用同一元件、文字隨匯出時檢視的帳戶改變，屬於文件層級一次性解析。**仍待確認**：日文版頁面上`grantNumber`解析邏輯是否會誤判、帳戶切換按鈕的選擇器寫法在不同頁面版本下是否穩定 | 雖然沒有使用者欄位，但這份領域仍然需要merge規則——原因不是保護使用者欄位，是避免同領域內其他批次的既有事實記錄被整表`deleteAll()`誤刪（例如RSU分兩次匯出，第二次匯入會把第一次存的記錄洗掉），以及避免不同公司股票的事實記錄互相撞號覆蓋。詳見[issue-180-05](issue-180-05.md)（8張表完整的欄位層級修正清單，含`symbol`補充、`@EqualsAndHashCode`/`@UniqueConstraint`逐表核對）；原[Plan-22](../planned/Plan-22-etrade-symbol-scoped-import-and-inventory-deletion.md)只處理了在庫持股兩張表、且方案本身有型別跟欄位錯誤，issue-180-05的修法方向也不同（merge不刪除，不是局部刪除重建） |
| 薪資設定 | [SalaryUserSettings.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryUserSettings.java) | 整表都是使用者設定 | — | 不適用 | 這個表本身已經乾淨，不涉及merge規則 |

### `memo`現狀：銀行網頁上的真實事實欄位，但目前的合併邏輯有一個副作用讓它形同不會被更新
`memo`是銀行網頁上真實存在的欄位，不是使用者自己輸入的設定值：
- [SmbcBankHtmlParser.java:149-161](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/bank/parser/SmbcBankHtmlParser.java)、[MufgBankHtmlParser.java:140-157](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/bank/parser/MufgBankHtmlParser.java)——兩支parser都會解析頁面上的memo，解析到非空值才呼叫`tx.setMemo(memo)`。
- 但[BankTransaction.java:54-55](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/bank/BankTransaction.java)的`@EqualsAndHashCode`判斷「兩筆交易算不算同一筆」的六個欄位（銀行名、日期、摘要、存入、提出、餘額）完全不包含`memo`；[BankTransactionServiceImpl.saveStatement:95-104](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/bank/BankTransactionServiceImpl.java)合併時只要這六個欄位相同就判定為既有交易、直接跳過整筆新物件（包含它帶的新memo），既有那筆的`memo`不會被更新。這代表`memo`雖然是事實欄位，卻因為合併邏輯的這個副作用，實際上形同「只有這筆交易第一次被建立時才會被寫入，之後銀行網頁上即使更新了這筆交易的memo內容，重新匯入也不會反映到資料庫裡」。
- 這個「合併邏輯副作用讓事實欄位形同不會更新」的問題，跟這份issue要解決的「使用者欄位被覆寫」是相反方向的問題（這裡是「該更新的事實欄位沒更新」，不是「不該被動的使用者欄位被動了」），不屬於這份issue的範圍，但值得另外追蹤——如果之後要處理，不要跟這份issue的修正混在一起做。

## 建議修正方向
核心修法是：逐領域把現有job import的merge方法改寫成「查到既有列就覆蓋事實欄位、保留使用者欄位；查不到就新增」這個固定模式，不需要新增任何表格或改動domain的表結構。這個修法本身不代為選擇，但有一條落地規則不應該被違反：

**job import的merge邏輯本身永遠不包含清空動作。** 系統管理者習慣在跑job import前先確保資料庫是乾淨的（不清空就不安心），但這是**系統管理者專屬**的個人工作習慣，不是job import這個功能本身需要具備的正確性保證。正確的做法是把這個潔癖獨立成一個只影響系統管理者自己資料的刪除端點（例如「清空我自己ownerUserId下的所有資料」），跟job import的merge邏輯完全脫鉤。這樣一來，job import的merge邏輯本身永遠只做「找到既有列就覆蓋事實欄位、保留使用者欄位；找不到就新增」，不包含任何清空動作，對所有情境都安全；系統管理者想要的「跑之前先清乾淨」，透過另外呼叫那個獨立的刪除端點達成，兩個動作各自獨立、各自簡單，不會互相牽連。

具體每個領域的修正方式，見下面三份附屬issue，建議的處理順序：
1. [issue-180-01](issue-180-01.md)（房貸）：商務ID已接線、改法最單純，只是補一行null-fallback。
2. [issue-180-02](issue-180-02.md)（銀行/信用卡）：鬆散配對邏輯需要新寫。
3. [issue-180-03](issue-180-03.md)（訂單項目）：牽涉三平台共用程式碼、範圍最大。

一個仍待使用者拍板、不屬於這份issue要修的開放問題：
1. **保單`policyName`/`annualPremium`/`endDate`這類「同一欄位、兩種來源都能寫」的情況，merge規則（覆蓋或保留二選一）處理不了**：這份issue的merge規則假設的是「使用者欄位」跟「匯入事實欄位」是兩組完全不同的欄位，可以乾淨地二選一；但保單這三個欄位是同一組欄位，使用者可以用PUT端點手動改，重新匯入也會覆寫同一組欄位，不是「這個欄位parser拿不到就保留」能處理的情況。這個案例依然需要[Plan-18-01](../planned/Plan-18-01-extension-preview-reconcile-ui.md)的Extension逐欄位checkbox機制。

~~「`NisaContributionSetting.monthlyEquivalentAmount`這類衍生計算值該算哪一類」~~——已在[issue-183](issue-183.md)定案：這個欄位本身沒有任何合理用途（不驅動模擬結果、純粹是`settingAmount`+`course`的衍生顯示值），直接整欄拔除，不需要分類。

## 關聯文件
- [WONTFIX-23](../wontfix/WONTFIX-23-mortgage-reimport-fact-fields-no-fallback.md)（最早的欄位層級判斷原則——逐欄位merge判斷。這份issue最終定案的方向跟WONTFIX-23的原則相同，差別是這份issue系統性地逐領域盤點，不是遇到一個案例才補一個if）
- [issue-180-01](issue-180-01.md)——房貸領域的具體案例（提前還款計劃被清空）
- [issue-180-02](issue-180-02.md)——銀行/信用卡交易領域的具體落地設計
- [issue-180-03](issue-180-03.md)——Amazon/Rakuten/Sofmap訂單項目領域的具體落地設計
- [issue-180-04](issue-180-04.md)——DC/iDeCo帳戶快照領域的具體案例（同一筆記錄的事實欄位分別來自三個不同頁面，merge邏輯目前用數值大小猜測頁面來源）
- [issue-180-05](issue-180-05.md)——E-Trade（股票/RSU/ESPP）領域的具體案例，兩個頁面共8張表定案改為merge；`grantNumber`類商務ID在多公司股票情境下的撞號風險、6張表補`symbol`欄位的決策、`@EqualsAndHashCode`與DB約束不一致的既有bug，皆已記錄於該文件；日文頁面`grantNumber`解析疑慮、帳戶切換按鈕選擇器穩定性列為待確認項目
- [Plan-18-01](../planned/Plan-18-01-extension-preview-reconcile-ui.md)——保單這類「同一欄位、兩種來源都能寫」情況的補充機制，留在planned/，不屬於這份issue的修正範圍
