# Plan 27：全領域開放手動編輯——現有UI顯示的欄位，逐一補齊後端PATCH/PUT端點跟前端編輯元件，端點設計上要能被未來Chrome Extension的逐欄位預覽流程直接重用，不要維護兩套

## 這份Plan怎麼來的
這是issue-183（NISA`monthlyEquivalentAmount`該不該保留`expectedReturnRate`編輯能力）討論過程中延伸出來的全域決定。使用者確認：這個系統目前只有使用者本人以管理者身分執行資料夾掃描匯入，重新匯入覆蓋手動編輯值的風險只有使用者自己會承擔；未來一般使用者會透過[Plan-18](Plan-18-chrome-extension-data-extractor.md)的Chrome Extension送資料進來，那條路徑已經有[Plan-18-01](Plan-18-01-extension-preview-reconcile-ui.md)設計好的逐欄位預覽機制處理「這次該聽誰的」。基於這個前提，現在的管理者UI**不需要等待任何「事實欄位vs使用者欄位」的分類工作，可以直接對所有欄位開放編輯**——這跟issue-180系列（保護使用者欄位不被reimport悄悄覆寫）是完全獨立的另一個維度,不是互相取代。

為了弄清楚範圍有多大，這次對全系統所有領域做了一次完整查證：逐一列出domain entity的每個欄位、對應的REST端點、前端是否有接上真正的編輯元件。查證範圍涵蓋：銀行/信用卡交易、分類、房貸、保險、NISA、DC/iDeCo、年金、Amazon/Rakuten/Sofmap購物、E-Trade股票、教育（含ChildProfile/ChildEducationSettings/EducationConfig三層）、薪資、使用者檔案、家庭。

## 核心技術決定：新端點要跟未來Extension共用，不要維護兩套

### 決定本身
任何為這份Plan新增的PATCH/PUT端點，請求格式都必須設計成「整筆記錄的完整欄位值，無條件覆蓋」——不是「只送有改的那幾個欄位，後端自己判斷其他欄位要不要保留」。原因：

1. **現在的管理者UI**：使用者在前端拿到完整現有值，改幾個欄位，送出時應該帶著完整的那包值（包含沒改的欄位），不是只送差異。
2. **之後的Chrome Extension**（Plan-18-01）：使用者在Extension的逐欄位預覽畫面上，針對每個不一樣的欄位勾選「保留現有值」或「採用新值」，勾選完畢後同樣是送出一包「已經決定好的完整最終值」。

兩者的本質相同——**後端只接收「已經決定好的最終值」，不應該在Service/merge層對任何欄位做「這個欄位要不要fallback保留舊值」的判斷**，差別只在「這包最終值是怎麼被決定出來的」。這代表現在設計的端點格式，之後接Extension時可以直接重用，不需要為Extension另外設計一套API。

### 實作上可以直接重用issue-180系列已經拆出來的`merge()`方法
查證確認issue-180系列重構後，幾乎每個領域的domain entity上都已經有了`merge(incoming)`方法（例如`MortgageRecord.merge()`、`InsurancePolicy.merge()`、`BankTransaction.merge()`/`CreditCardTransaction.merge()`、E-Trade 8張表各自的`merge()`、`NisaContributionSetting.merge()`、`BaseOrder.merge()`/`BaseOrderItem.merge()`），而且這些方法本身已經是「無條件覆蓋被要求改的欄位，不做任何猜測判斷」的寫法——目前唯一的呼叫者是各自的`XxxJobServiceImpl`（被鎖在排程/SSH匯入流程後面，一般UI端點碰不到）。

新的PATCH/PUT端點應該直接重用這些既有`merge()`方法，不要在Service層另外刻一套欄位賦值邏輯——這樣兩套入口（job import跟手動編輯）永遠用同一套「這個entity哪些欄位會被覆蓋」的邏輯，不會各自維護、互相漂移不同步。

**注意**：這些`merge()`方法目前全部是無條件賦值，不會跳過null——這代表呼叫端（新端點的Service層）必須先組出一包**完整**的`incoming`物件再呼叫`merge()`，不能讓使用者只送部分欄位就直接拿去建構`incoming`，否則沒碰過的欄位會被蓋成null。這呼應了上一段「端點格式要收完整值」的決定，兩者是同一件事的兩個面向。

## 跟issue-180系列、Plan-18-01的分工關係
- **issue-180系列**：job import（排程/SSH批次匯入）重新匯入時，怎麼正確區分「事實欄位該覆蓋」跟「使用者欄位該保留」，避免悄悄覆寫使用者設定。這份Plan完全不影響issue-180系列的結論——job import的merge邏輯維持原樣。
- **這份Plan（27）**：現在的管理者UI，開放所有欄位可以直接編輯，不等待任何欄位分類工作。這是**另一個獨立的觸發路徑**，跟job import的merge規則各自獨立，不衝突——使用者编辑完一個欄位之後，如果之後系統管理者重新匯入同一筆記錄的對帳單，job import那條路徑依然照issue-180系列的既有規則走（事實欄位覆蓋、使用者欄位保留），這份Plan不改變那個行為。
- **Plan-18-01**：未來一般使用者透過Extension送資料進來時，同一欄位兩邊都有資格寫的情況，由逐欄位預覽機制解決。這份Plan的端點設計要能被那個流程直接重用（見上方「核心技術決定」）。

## 查證方式
逐領域讀取domain entity的完整欄位、現有REST Controller的每個端點、前端對應頁面/元件，確認「這個欄位有沒有後端寫入端點」跟「前端有沒有接上真正能呼叫這個端點的編輯元件」兩件事,不是只看UI上有沒有顯示這個欄位。

---

## 領域一覽與缺口總表

### 已經是完整CRUD、只缺零星欄位的領域（修法集中在補幾個具體欄位，不需要新建整套端點）

#### 分類（TransactionCategory）
已有完整PUT端點（`CategoryController.java:105`），`name`/`isEssential`/`isSimulationDuplicate`三個欄位前端都已經接好編輯元件（`isEssential`/`isSimulationDuplicate`是issue-174修好的案例）。唯一缺口：

| 欄位 | 後端 | 前端 | 缺口 |
|---|---|---|---|
| `parentId` | 後端`CategoryServiceImpl.updateCategory`已支援無條件覆寫 | 編輯對話框完全沒有選擇父分類的UI控制項，`parentId`只會在「新增根分類」「新增子分類」這兩個按鈕被點擊時依當時情境寫死，既有分類打開編輯對話框後無法改變這個值 | 無法把既有分類重新掛到另一個父分類下、或把子分類升級成根分類 |

#### 房貸（MortgageRecord）
已有完整PUT端點（`MortgageRecordController.java:73-79`），絕大多數欄位（`loanName`、`totalLoanAmount`、`currentBalance`、`interestRate`、`loanTermMonths`、`startDate`/`endDate`/`balanceRecordDate`、`repaymentMethod`、`monthlyPayment`、`bonusPayments`、`occupancyDate`/`residenceType`/`propertyType`、`taxDeductionEligible`、`hasFiveYearRule`）前端都已經接好編輯元件，`earlyRepayments`子表也有完整的新增/編輯/移除UI（跟主表同一個對話框一起送出，服務層是整批清空重建，不是逐筆patch，但功能上對使用者是有效的）。缺口：

| 欄位 | 後端 | 前端 | 缺口 |
|---|---|---|---|
| `bankName` | 可寫（`MortgageRecordRequest`已包含） | 整個前端`types.ts`跟對話框都沒有這個欄位，完全沒顯示 | 這是issue-180-01用來當商務ID的欄位之一，使用者連看都看不到，更別說改 |
| `contractNumber` | 可寫 | 同上，完全沒顯示 | 同上，商務ID的另一半，一起補 |
| `rateType`（固定/變動利率） | 可寫 | 新增時固定寫死`"FIXED"`，編輯時沿用既有值但沒有任何選擇元件可以改 | 使用者無法把一筆房貸從固定利率改成變動利率（或反過來） |

`taxDeductionRate`/`maxTaxDeductionAmount`/`taxDeductionEndYear`這三個欄位查證確認是後端依`taxDeductionEligible`狀態自動算出來的衍生值（`MortgageRecordServiceImpl.validateAndApplyTaxLogic`），前端對帳單送出這三個欄位的值會被後端忽略——這是刻意的設計，不是缺口，不需要補輸入框。

#### 保險（InsurancePolicy）
已有完整PUT端點（`InsurancePolicyController.java:77-83`），`companyName`/`policyNumber`/`policyName`/`annualPremium`/`endDate`前端都已接好編輯元件。唯一缺口，而且是前後端**雙邊**都卡住、需要一起修才能解開：

| 欄位 | 後端 | 前端 | 缺口 |
|---|---|---|---|
| `policyType` | **不可寫**——DB欄位`updatable = false`，`InsurancePolicyServiceImpl.updatePolicy`完全沒有呼叫對應的setter（這個欄位沒有public setter可呼叫） | 編輯對話框的Select元件被`disabled={!!policy}`鎖死，只有新增時才能選 | 這是兩邊各自獨立都擋住這個欄位的修改，只改前端或只改後端都不夠，需要：(1) 幫`InsurancePolicy`補一個`policyType`的setter並移除`updatable = false`；(2) `updatePolicy`呼叫這個setter；(3) 前端移除`disabled`條件 |

#### 年金（PensionRecord）
查證確認這個領域**沒有缺口**，是目前查到覆蓋率最好的領域——雖然`PensionRecordController`沒有獨立的PUT/PATCH端點，但現有的`POST /assets/pensions`本身就是完整的upsert（帶`id`就更新既有記錄、不帶就新增），前端`PensionRecordDialog`已經把全部14個欄位都接上了可編輯的輸入框（含3個自動加總的衍生欄位，用唯讀顯示，這是合理設計，不是缺口）。唯一可以討論但不影響「使用者現在能不能編輯」的項目：要不要把這個POST upsert改成更符合REST慣例的`PUT /assets/pensions/{id}`，純粹是風格一致性的問題，不是功能缺口，這份Plan不代為決定。

---

### 已有部分編輯能力、但大部分欄位沒有被顯示或接線的領域

#### NISA（NisaContributionSetting / NisaSnapshotRecord / NisaUserSettings）
`NisaUserSettings`（全域NISA投資假設，3個欄位）已在使用者檔案頁完整接好，沒有缺口。

`NisaSnapshotRecord`（持股快照）只有`expectedReturnRate`一個欄位有編輯元件（`PATCH /assets/nisa/{id}/return-rate`，運作正常），其餘9個欄位（`recordDate`、`accountType`、`fundCode`、`fundName`、`units`、`averageCost`、`currentPrice`、`totalPrincipal`、`currentMarketValue`、`unrealizedGainLoss`）前端都只是唯讀顯示，沒有任何編輯元件，也沒有對應的端點。

`NisaContributionSetting`（提繳設定）的現狀跟[issue-183](../pending%20fix/issue-183.md)直接相關——那份issue已經定案要拔除`monthlyEquivalentAmount`跟它的PATCH端點。這代表這張表之後唯一值得開放編輯的欄位是`expectedReturnRate`（issue-183已經記錄這個決定：等這份Plan要做NISA設定編輯UI時，把`expectedReturnRate`的編輯能力一併納入，不要在183裡搶先做一個之後要重新設計的端點）。其餘欄位（`recordDate`、`accountType`、`fundCode`、`fundName`、`course`、`settingAmount`）都是SBI對帳單解析出來的事實欄位。

#### DC/iDeCo（DefinedContributionAccountSnapshot / DefinedContributionFundSnapshot / DefinedContributionUserSettings）
`DefinedContributionUserSettings`（全域DC/iDeCo投資假設，7個欄位）已在使用者檔案頁完整接好，沒有缺口。

Fund層級（`DefinedContributionFundSnapshot`）的`expectedReturnRate`、`monthlyContributionAmount`兩個欄位已經有編輯元件（`PATCH /assets/dc/funds/{id}/allocation`）；`commodityType`、`fundCode`有在TypeScript型別裡宣告，但前端整個元件樹都沒有渲染這兩欄；`units`/`averageCost`/`currentPrice`/`totalPrincipal`/`currentMarketValue`/`unrealizedGainLoss`有顯示但唯讀。`allocationPercentage`是一個容易被誤判成「已經做好」的陷阱——`DefinedContributionServiceImpl.updateAllocation`呼叫`fund.updateAllocation(amount, fund.getAllocationPercentage())`，讀回這支fund**現有**的配分比例再原樣寫回去，實際上從來沒有真正的新值被傳進來，這個欄位看起來已經接線、實際上無法被外部改變，需要補一個真正的`allocationPercentage`參數才能用。

帳戶快照層級（`DefinedContributionAccountSnapshot`）的6個欄位（`totalAssetValue`、`totalAccumulatedContribution`、`monthlyEmployerContribution`、`monthlySubscriberContribution`、`monthlyTotalContribution`、`enrollmentDate`）**完全沒有被前端顯示**，不是「顯示但不能編輯」，是「連顯示都沒有」——前端元件樹直接從帳戶快照打平到fund陣列，從未把這層快照本身的欄位渲染出來。這裡要特別注意[issue-180-04](../pending%20fix/issue-180-04.md)已經明確點出`monthlyEmployerContribution`這個欄位目前沒有任何parser會填入非零值，「目前只能透過使用者畫面手動輸入維護」——這份Plan要補的帳戶層級編輯UI，正好是issue-180-04那個開放問題的答案，兩份文件要互相對照，不要重複處理。

---

### 完全沒有編輯能力的領域（需要從零建立端點+UI）

#### 銀行交易（BankTransaction）/ 信用卡交易（CreditCardTransaction）
查證確認這兩個領域目前是0%——除了「新增一筆手動交易」這個POST端點之外，完全沒有任何PUT/PATCH，前端表格的每一個欄位（`bankName`/`cardName`、交易日期、摘要/店名、金額、餘額、`memo`）都是純文字`<TableCell>`，沒有一個是可編輯的widget。更誇張的是`category`這個欄位——它完全沒有出現在對外的Response DTO裡，前端根本看不到這個欄位存在,實際上唯一能設定`category`的方式是整個獨立的AI分類CSV匯出/外部LLM處理/匯入迴圈（`/finance/tools/ai-categorization`頁面），不是任何單筆記錄的操作。

這個CSV迴圈本身值得一提——它證明「事實欄位開放外部覆蓋」這件事在這個系統裡其實已經有先例，只是目前做得比較迂迴（要離開App去外部LLM跑一趟），不是這次才第一次要做的新嘗試。

這兩個領域需要從零開始建：新的PATCH端點（直接重用`BankTransaction.merge()`/`CreditCardTransaction.merge()`，見上方「核心技術決定」）、把`category`補進Response DTO、現有「新增」對話框擴充成可以切換「新增/編輯」模式。

#### Amazon / Rakuten / Sofmap購物（BaseOrder / BaseOrderItem / BasePayment / BaseShipment）
三個平台共用同一組base class，缺口完全一樣——沒有任何PATCH/PUT/DELETE端點。現有的`POST /{platform}/extension`端點是設計給Chrome Extension用的，而且被`@CrossOrigin`鎖死在各商家自己的網域（例如Amazon只接受`https://www.amazon.co.jp`），管理者UI的網域打這個端點會被瀏覽器直接擋掉，不能繞過去用。

訂單層級12個欄位（`orderId`、`orderSource`、`orderDate`、`shippingDate`、`totalAmount`、`billedAmount`、`shippingFee`、`refundAmount`、`discounts`、`pointsUsed`，加平台各自的`giftCardAmount`/`dPointsUsed`、`shopId`/`taxAmount`、`earnedPoints`）、項目層級6個欄位（`name`/`quantity`/`unitPrice`/`seller`/`itemId`/`category`）、付款跟物流子表全部欄位，全部零編輯能力。`category`的現狀跟銀行/信用卡一樣，只能靠AI分類CSV迴圈，沒有單筆編輯的UI。Expenses頁面上的「Add Record」按鈕完全沒有接`onClick`，是純裝飾，點了沒有任何反應。

Sofmap比Amazon/Rakuten更特殊：`TransactionSource`這個enum裡完全沒有Sofmap，代表它連資料夾掃描匯入都沒有，唯一的匯入管道就是Extension——這是匯入機制本身的缺口，不是UI編輯缺口，這份Plan不處理，只記錄下來避免跟編輯缺口混淆。

因為三平台共用base class，後端只需要針對`BaseOrder`/`BaseOrderItem`設計一套通用的PATCH端點（用既有的`merge()`方法），前端可以用一個通用的訂單/項目編輯對話框，三個平台共用，不需要各自重新設計三套。

#### E-Trade股票（8張表：帳戶快照、ESPP/RSU在庫持股、現金交易、RSU授予/事件/分批排程、ESPP購買歷史）
這是目前查到最極端的案例。[issue-180-05](../pending%20fix/issue-180-05.md)已經確認這8張表完全沒有任何使用者欄位、沒有任何PUT/PATCH端點。查證這次進一步確認：前端唯一的E-Trade頁面只抓取並顯示3張表（帳戶快照、ESPP/RSU在庫持股），另外4張「Benefit History」表（RSU授予/事件/分批排程、ESPP購買歷史）即使後端GET端點都存在，前端完全沒有抓取或顯示——這代表不只是「顯示但不能編輯」，是「連顯示都沒有」。頁面上的「Add Record」按鈕同樣沒有接`onClick`。

因為是從零開始，建議第一波優先開放編輯的欄位（判斷依據是「兩次HTML重新匯入之間最容易過期、且錯了會造成連鎖影響的欄位」）：
- `sellableQuantity`（`BaseStockLot`，ESPP/RSU在庫持股都有）：使用者在兩次匯入之間賣出部分持股，這個數字會過期，手動修正能立刻反映現狀，不需要等下次匯入。
- `symbol`、`grantNumber`：這兩個欄位是issue-180-05整套merge比對邏輯的商務ID，解析錯一次不只是顯示錯，會讓下一次重新匯入時比對錯誤（誤判成新記錄而不是更新既有記錄），修正一次能預防後續一連串的重複/孤兒記錄。
- `capitalGainsStatus`（長期/短期資本利得狀態）：這個標籤的正確性只跟匯出當下的時間點有關，持有超過一年的門檻在兩次匯入之間可能被跨過，手動修正能避免等到下次匯入才看到更新的標籤。

其餘欄位（金額類、日期類）之後再逐批補上，不需要一次到位。

#### 教育費用配置（EducationConfig，全域學費/就讀時長基準表）
`ChildProfile`（姓名、生日）、`ChildEducationSettings`（每個小孩各教育階段的個別化覆寫設定）這兩層查證確認已經是接線相當完整的領域，只缺一個小角落（見下方「真實bug」一節的Stage-2補習覆寫缺口）。

但`EducationConfig`——全域的學費/就讀年數/入學月份/每年繳費次數/入學金/標準入學年齡基準表（5個教育階段×最多9條升學路線×6個子欄位）——後端有完整、有validation的CRUD（`GET`/`POST`/`PATCH {name}`/`DELETE`），前端**完全沒有任何頁面**，`/finance/configs/general`這個全域設定頁只有「System Config」「Pension Rules」「Transaction Sources」三個分頁，沒有「Education」分頁。目前這個基準表只能透過直接改資料庫或程式碼調整，管理者在UI上完全碰不到它。這份Plan要在全域設定頁新增一個Education分頁，把這套後端已經做好的CRUD接上。

#### 薪資（SalaryRecord）
`type`、`baseSalary`等12個金額欄位、`grossAmount`/`netAmount`都已經接好編輯元件。缺口集中在兩個查證時發現的真實問題，見下方「真實bug」一節（`note`/`details`會在編輯時被悄悄清空）。另外發現一個相關但不在PATCH/PUT範圍內的缺口：這個entity完全沒有DELETE能力（controller、service、repository、前端都沒有），但編輯對話框裡`paymentDate`欄位鎖定時的提示文字卻寫著「要改日期請刪除這筆記錄重新建立」——這個提示文字指向一個不存在的功能，這份Plan順手記錄，實際要不要補DELETE留給使用者決定。

#### 使用者檔案（UserProfile）/ 家庭（Family）
`UserProfile`的`birthDate`/`targetRetirementAge`/`pensionStartAge`/`lifeExpectancy`/`taxAdvantagedWithdrawalAge`/`applyProfileToOptimizerBaseline`/`defaultCurrency`/`manualMonthlyBaseExpense`這8個欄位都已接好。缺口：`displayName`這個欄位，見下方「真實bug」一節（整條後端鏈路完全沒有寫入路徑）。

家庭成員（`Dependent`）的`birthDate`/`estimatedAnnualIncome`/`isCohabiting`都已接好，`memberType`這個欄位有真實bug（見下方）。

`FamilyGroup`/`FamilyMember`這層查證確認主要是身分/結構性質，不是這份Plan主要的目標（使用者指示過這份Plan聚焦在資料欄位的編輯，不是身分管理流程），但查證時發現兩個小缺口一併記錄：家庭群組沒有改名功能（前後端都沒有）、成員角色（一般成員/管理者）沒有變更功能（前後端都沒有）；另外查到一個後端端點存在但前端完全沒有觸發按鈕的情況——`DELETE /family`（解散家庭群組）這個端點是存在的，但`family/page.tsx`裡管理者畫面只會顯示一個靜態的「Admin cannot leave group」徽章，從頭到尾沒有任何按鈕可以呼叫這個端點。這三點是否要處理，留給使用者決定，不是這份Plan的核心範圍。

---

## 查證時發現的真實bug（不是「缺少編輯介面」，是現有功能本身有問題）

這幾項跟上面「哪些欄位還沒開放編輯」是不同性質的發現——這些是現有程式碼邏輯本身的缺陷，即使不考慮這份Plan的目標，也值得獨立処理。這份Plan只負責記錄，不代為決定優先順序或是否要併入同一批修改：

1. **`Dependent.memberType`下拉選單的選項，3/4會被後端拒絕**：前端`profile-family-section.tsx`的家庭成員類型下拉選單提供`SPOUSE`/`CHILD`/`PARENT`/`OTHER`四個選項，但後端`DependentType`這個enum實際上只定義了`SPOUSE`、`DEPENDENT`兩個值（查證`DependentType.java`確認）。選擇`CHILD`/`PARENT`/`OTHER`任何一個存檔，Jackson反序列化會直接失敗，使用者會得到一個400錯誤。這不是「選項不夠」，是「現有選項裡四分之三會壞」。

2. **`SalaryRecord`編輯對話框每次送出都會把`note`跟`details`這兩欄清空**：查證`add-salary-record-dialog.tsx`確認，`handleSubmit`送出的請求body固定寫`note: null`、`details: null`（第142-143行），不管這筆記錄原本有沒有值。但同一個畫面的唯讀表格（`salary-table.tsx`）明明有顯示這兩欄的內容（透過tooltip），代表這些資料是存在的、很可能是parser解析出來的明細——現在只要使用者編輯這筆記錄的任何一個欄位（例如只是想改`baseSalary`），這次儲存動作會順手把`note`/`details`這兩欄的既有內容永久抹除，是一個會主動破壞資料的副作用，不是單純「少了一個輸入框」。

3. **`UserProfile.displayName`整條後端鏈路完全沒有寫入路徑，但`FamilyServiceImpl`依賴它顯示家庭成員名字**：查證確認這個欄位在`UserProfileRequest`、`UserProfileResponse`、`SaveUserProfileCommand`、`UserProfileServiceImpl`裡完全沒有被讀取或賦值。但`FamilyServiceImpl.java:177-180`會讀取這個欄位來決定家庭頁面上每個成員要顯示什麼名字，沒有值時退回`"User-" + userId前4碼`這種預設格式。這代表現在系統裡**沒有任何人**能把自己的顯示名稱設定成真正的名字，所有使用者在家庭頁面上永遠只會看到這種退回格式——這不是「缺一個輸入框」這麼單純，是一個被其他功能依賴、卻從頭到尾沒有入口可以寫入的欄位。

4. **DC的`allocationPercentage`看起來已經接線、實際上無法被改變**：見上方DC領域小節，`updateAllocation`讀回現有值再原樣寫回去，不是真正的更新路徑。

這四項，第1、3項建議獨立另開issue處理（不等這份Plan排入時程才修，因為是現有功能本身的bug，不是「尚未建置」的新功能缺口）；第2項因為直接涉及資料遺失風險，也建議優先獨立處理；第4項可以併入這份Plan處理DC領域時一起修正，因為本來就要改同一個端點。

## 這份文件沒有決定的部分
- 上面「真實bug」四項要不要立刻獨立開issue處理、還是等這份Plan排入時程時一併修，留給使用者決定。
- 每個領域實際落地的順序（例如先做銀行/信用卡的從零建置，還是先補房貸/保險的零星缺口），這份Plan只盤點缺口，不排優先順序。
- E-Trade、購物三平台這類「從零建置」的領域，具體的前端UI設計（對話框版面、欄位分組方式），這份Plan只列出欄位清單，不做視覺設計。
- 家庭群組改名、成員角色變更、解散家庭按鈕這三個小缺口，是否要併入這份Plan處理，留給使用者決定。
