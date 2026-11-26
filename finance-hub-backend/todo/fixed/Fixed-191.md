# [FIXED] 191：年中退休與退休當年的稅金計算失真問題——是否要支援`retirementMonth`這類混合年度模型，持續討論

## 這份question怎麼來的
這份文件的內容是從[issue-172](issue-172.md)（退休當年薪資YTD被雙重扣減）的「延伸探討」段落原封不動複製過來的，作為獨立討論的起點。issue-172本身維持原文不動（純文字檔案在沒有git環境下無法追蹤變更歷史，所以保留172原文當作歷史記錄），後續討論都在這份文件裡繼續，不回頭修改172。

以下為複製過來的原文（包含issue-172二次review後更新過的版本）：

---

## 延伸探討：年中退休與退休當年的稅金計算失真問題（供後續 Reviewer 討論）

在修復 Issue 172 過程中，進一步梳理出模擬管線在「退休當年」（無論是過渡年退休，或是未來某一年的年中退休）所存在的結構性稅金計算失真現象，整理如下供架構與產品方向評估。**二次review查證重新評估過這部分的實際發生範圍跟金額規模，結論比下方原始文字描述的更嚴重，見本節最後一段的更新。**

### 1. 現行引擎對「退休」的二元化抽象假定
- 目前整個模擬迴圈以「年」為粒度推進，退休狀態由 [`context.setRetired(age >= targetRetirementAge)`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java) 決定。
- 這種二元化設計（整年皆在職 vs. 整年皆退休）意味著系統缺乏「年中某個月份退休」的混合年度概念：
  - 一旦標記為退休，`applyWorkingTaxes` 完全跳過，全年度有效薪資所得 `effectiveAnnualSalary` 視為 0。
  - 當年稅務完全由 `applyRetireeTaxes` 接管，僅計算公的年金雜所得稅。

### 2. 稅金與年末調整在不同情境下的失真細節
#### 情境一：過渡年（起算年）即為退休年
- **已繳稅款與全年稅負的不對稱**：
  若使用者於年中 7 月跑模擬，設定今年 65 歲退休，系統在 1~6 月已產生實際薪資（例如 YTD 毛薪 300 萬，並已源泉徵收所得稅、住民稅與社保）。
  然而因起算年整年被判定為退休，在 [`YearlySimulationPipeline.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java) 的 `applyYearEndAdjustment` 中：
  `annualGross = effectiveAnnualSalary (0) + bonus (0) = 0`。
  當 `annualGross = 0`，年末調整引擎計算得出的全年應繳所得稅與社保為 0。
- **房貸抵稅額（住宅ローン控除）遺失**：
  在職期間若原本符合房貸抵稅資格，由於 `applyWorkingTaxes` 未被呼叫，`mortgageResult` 中的控除額完全無法被帶入所得稅折抵或隔年住民稅抵免通道，造成該年度稅負無法享受到抵稅利益。
- **稅款退補與結算失真**：
  在真實日本稅法中，年中退休退職者在年底若未再就職，應透過退職時之源泉徵收票於次年 2~3 月辦理「確定申告」，將上半年已繳納之過高源泉稅進行退稅結算。現有管線若將全年薪水視為 0，且跳過工作所得稅引擎，則無法準確反映該筆退稅（Kakutei Shinkoku Refund）之現金流回補。

#### 情境二：未來模擬年度於「年中退休」
- 若未來某一年使用者達到 `targetRetirementAge`，引擎在該整年度一律將其視為已退休（薪資收入為 0，只領年金）。
- 若真實生活情境為特定月份（如滿 65 歲當月或會計年度末 3 月底）退休，該年應有數個月薪資所得與數個月年金所得之混合申報，但現行系統會直接在該年切換為整年退休模式，使該年度的住民稅（依前一年度所得課徵）與所得稅無法反映部分月份薪資之社保扣除與薪資所得扣除額。

### 3. 建議 Reviewer 討論面向
1. **短期（現行架構內）**：
   方向 A 已阻斷流動現金遭負向扣減的核心資產錯誤，維持現狀已能確保大部分長期退休資產路徑不受致命扭曲。
2. **中長期（是否支援混合年度）**：
   是否需要在 `targetRetirementAge` 年度支援 `retirementMonth` 參數？
   若支援，則該年度需分段計算：
   - 退休前月份：按薪資所得、源泉扣繳及社保計算。
   - 退休後月份：按國民年金/國保自負額或公的年金計算。
   - 年末結算：統一透過確定申告或年末調整進行所得合併課稅結算與住宅ローン控除抵免。

### 4. 二次review更新：房貸抵稅額遺失的實際範圍比上面「情境一」描述的更廣，金額規模可能更大
上面「情境一」的文字只描述了「起算年因為被判定整年退休，房貸抵稅額在那一年遺失」。二次review查證重新追蹤了完整的呼叫鏈路，發現這個限制的實際範圍比這段文字呈現的更嚴重：

查證確認[MortgageCalculator.simulateMortgagesForYear](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)本身完全不檢查退休狀態（整份檔案查無`isRetired`），只要房貸還在法定控除年限窗口內（現行規則下通常是10~13年），每一年都會照常算出`yearEndDeduction`/`kakuteiDeduction`這兩個控除額。但這兩個值目前**唯一**的消費者是`ActiveTaxCalculationEngine.calculate(...)`，而這個方法**只會從`applyWorkingTaxes`被呼叫**——`applyRetireeTaxes`呼叫的是另一條完全不同的路徑（`calculatePensionTax`），這個方法完全不接收、也不參照任何房貸控除額參數。

這代表只要`isRetired()`判定為true，不管是起算年還是未來任何一年，`applyWorkingTaxes`永遠被整段跳過——房貸控除額不是「起算年那一年」才遺失，是**從退休那一年開始，往後只要這個人在模擬裡維持退休狀態，房貸只要還在控除窗口內，每一年的控除額都會被完全丟棄，直到控除窗口本身到期為止**。例如一個人50歲買房（13年控除窗口），60歲退休，模擬會讓他在退休後剩下的3年控除窗口裡，每年都遺失這筆控除——不是只遺失起算年那一年的部分金額。

查證依現有程式碼的控除額計算方式（`年末殘高×控除率`，並受`maxTaxDeductionAmount`跟實際所得稅負上限雙重封頂）估算，典型每年控除額規模落在20萬~46萬日圓之間；疊加「退休後維持退休直到控除窗口到期」這個更廣的發生範圍，多年加總起來的淨資產低估規模可能達到數十萬到百萬日圓等級——比原本「情境一」文字只強調起算年單一年份的描述，影響金額大得多。

這個限制維持原本「延伸探討」定位——記錄為已知但尚未決定要不要處理的架構限制，不代為決定是否要現在就投入`retirementMonth`這類中長期架構支援，但這份issue更新後明確反映「這不是起算年專屬的小問題，是退休後每年持續發生、影響範圍會隨退休年數疊加」這個更接近真實情況的事實。

### 5. 查證確認：社保義務已有正確處理；住民稅前一年所得是Plan-21已知範圍、不重複記錄；年金請領時間點是獨立輸入、目前精度只到年
討論過程中問到「退休了住民稅跟社會保險還是要繳，不繳可能會有問題」跟「退休後是不是馬上開始領年金，年金要算入住民稅/社保/所得稅嗎」這兩個問題，查證結果如下：

**社保義務（國民年金、國民健康保險）——系統已經正確處理，不是bug**：查證[YearlySimulationPipeline.java:249-273](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)的`applyNationalInsuranceOutOfPocket`方法確認，退休或無薪資時：20-60歲會自動計入國民年金（國民年金）自付保費；20-75歲且當年還沒有年金所得時，會自動計入國民健康保險自付保費。這兩項負擔不會因為退休而被遺漏。

**住民稅依前一年所得課徵、6月切換——這已經是另一份文件涵蓋的已知架構限制，這份question不重複記錄**：查證確認[Plan-21（模擬引擎月級別時間精度）](../planned/Plan-21-inhabitant-tax-prior-year-income-engine.md)已經完整討論過「住民稅該用前一年所得、6月切換課稅」這整套機制，狀態是open（等雙引擎架構評估），而且那份文件裡**已經**專門分析過「退休、換工作」這類所得劇烈變動年份的影響：單年可能有幾十萬日圓等級的落差，但因為發生頻率低、且「所得暴漲年少算了控除」跟「次年所得暴跌年又多算了控除」兩個方向的誤差會互相抵消，在30-40年的模擬尺度下量不出實質差異。這份question不重複討論這一塊，相關的後續技術分析留在Plan-21裡進行。

**年金請領時間點——這才是跟這份question直接相關、還沒被講清楚的部分**：查證[AssetSimulationEngineImpl.java:132](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)確認，`pensionStartAge`（年金開始請領年齡）是一個**完全獨立於`targetRetirementAge`（退休年齡）的使用者輸入參數**，系統只驗證`pensionStartAge`不能早於`targetRetirementAge`（否則拋例外），不會驗證、也不會假設兩者相等或接近——換句話說，「退休當月就開始領年金」在系統裡**不是自動發生的事情**，是使用者自己決定要不要把這兩個年齡設成一樣（甚至可以差到10年，例如65歲退休、75歲才開始領年金）。

查證[PensionCalculator.java:49-51](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/PensionCalculator.java)的`getValidPensionStartAge`確認合法範圍是60-75歲（對應真實的繰上げ/繰下げ受給制度，60-64歲提前請領每月減0.4%、66-75歲延後請領每月增0.7%，這部分乘數計算邏輯查證確認正確）。在退休但還沒到請領年齡的這段空窗期，`applyRetireeTaxes`裡`age < validPensionStartAge`時`pensionIncome`會是0，所得稅/住民稅因此正確算成0，同時上面確認過的`applyNationalInsuranceOutOfPocket`會補上國民年金/國保自付——這個「空窗期沒年金所得、但仍要負擔社保」的邏輯組合起來是正確的。

但這裡還是有一個跟這份question核心討論一致的限制：`pensionStartAge`這整套判斷都是以「歲」為單位（`age >= validPensionStartAge`），不是「退休後第幾個月」。這代表即使使用者把`pensionStartAge`跟`targetRetirementAge`設成同一個年齡（例如都設65歲，意圖是「退休後馬上開始領年金」），系統目前也沒有「5月退休、6月當月就開始領年金」這種精確到月的銜接判斷能力——這不是一個全新的、無關的問題，是上面「現行引擎對退休的二元化抽象假定」這整節已經記錄的「系統只有年單位、沒有月單位」這個根本限制，在「年金請領銜接」這個子情境下的又一次體現。如果之後真的要支援`retirementMonth`，年金請領的月層級銜接精度，應該跟薪資/年金稅務的月層級銜接精度一起納入同一次設計，不需要分開處理。

### 6. 補充：房貸確定申告的真實時序——第一年一定要自己辦，第二年起才輪到公司年末調整
這點是在討論這份question時由使用者補充的真實世界細節，記錄下來補足上面對`kakuteiDeduction`/`PreviousYearTaxRecord`機制的理解：房貸抵稅額（住宅ローン控除）在**第一年**（入住當年）不管使用者是不是有在職、年底是不是在公司任職，制度上都是**使用者自己在次年辦確定申告**才能申請到這筆扣除，不會透過在職公司的年末調整處理；從**第二年**開始，如果使用者仍在職，才會改成由在職公司在年末調整裡直接套用這筆扣除，不需要使用者每年自己辦。

查證[ActiveTaxCalculationEngine.java:222-262](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認，目前程式碼完全沒有區分「這是入住第一年（一定要確定申告）」還是「入住第二年以後（看在職狀態決定走年末調整或確定申告）」這兩種情境——`yearEndDeduction`/`kakuteiDeduction`這兩個值從`MortgageCalculator`算出來之後，不管是第幾年，都是同一套`actualYearEndDeduction`/`actualKakuteiRefund`去扣減。這點是否要納入這份question的範圍（或是否已經被其他地方處理掉，需要另外查證`MortgageCalculator`的程式碼邏輯本身），留待之後討論。

---

## 另一個已經獨立拆出的相關發現，以及跟這份question的確切關係
二次review同時在`YearlySimulationPipeline.applyYearEndAdjustment`裡挖到一個同根因、但影響範圍不同的獨立bug（退休當年三個預估稅額顯示欄位會變負數），已經拆成[issue-190](issue-190.md)獨立記錄跟修正。這兩份文件容易被誤會成同一件事（都跟「退休當年的稅額調整」有關），這裡查證清楚兩者的確切關係：

**不是同一套數字，但共享同一個根因，退休當年會同時失效**。查證確認系統裡有兩套完全獨立的稅額調整機制，都只掛在`applyWorkingTaxes`底下（退休時這整個方法被跳過）：

1. **issue-190的`applyYearEndAdjustment`**：算的是「薪資所得稅/住民稅/社會保險的YTD調整」，公式是單純的`全年應繳-YTD已繳`（[YearEndAdjustmentEngine.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/YearEndAdjustmentEngine.java)）。這個方法本身的設計意圖本來就允許結果是負數——程式碼註解明確寫著「負數代表多繳了、該退稅」（`// negative means we overpaid and get a refund`），這在正常在職過渡年是合理行為。issue-190的bug是：退休當年這個方法沒被跳過（缺了防衛條件），拿退休後才幾萬塊的小額年金稅去減退休前幾十萬的真實YTD薪資稅，結果這個「合法的負數機制」被套用在一個不該套用的情境上，算出一個不合理的巨大負值，而且這個負值沒有被轉成任何退稅現金流，只是原樣存進顯示欄位。

2. **這份question討論的「確定申告退稅」**：指的是房貸抵稅額（`kakuteiDeduction`）這條完全不同的路——查證[ActiveTaxCalculationEngine.java:231,257-262](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認，把當年沒用完的房貸抵稅額包成`PreviousYearTaxRecord`，**下一年**才會被領出來當現金發放（[YearlySimulationPipeline.java:80-91](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)）。這條路的問題不是「算出負數」，是「退休當年`applyWorkingTaxes`整段被跳過，這套房貸退稅/結轉的記錄根本沒機會被建立」——退休那一年完全沒有產生`PreviousYearTaxRecord`，不是產生了卻飄在那邊沒被兌現，是壓根沒被算出來。

**共同根因**：兩者都是因為只有`applyWorkingTaxes`（不是`applyRetireeTaxes`）才會執行這些計算，退休當年整段被跳過，各自用不同方式表現出問題——issue-190是「另一個沒被跳過的方法，用了不該用在這裡的負數邏輯」；這份question討論的房貸退稅遺失，是「該被跳過的那整組退稅/結轉機制，連建立記錄的機會都沒有」。

這代表**如果之後真的做了這份question討論的`retirementMonth`混合年度模型**（讓退休當年的在職月份部分，正確地跑過`applyWorkingTaxes`的簡化版），issue-190的負數問題也會自然消失——因為退休當年根本不會再進到`applyYearEndAdjustment`錯誤套用的那個分支。但這不代表issue-190現在不用單獨修——issue-190有一行就能解決的獨立修法，不需要等這份question的架構級決定才能堵住，兩份文件的處理時程可以互相獨立。

## 查證確認：退休後年金的稅務處理機制——「年金受給者の確定申告不要制度」，以及現行`calculatePensionTax`的「理論全年稅負」設計為什麼足夠
這點牽涉到日本稅制裡「年金受給者の確定申告不要制度」這個專門機制，查證結果如下：

**年金機構沒有年末調整機制，預扣跟多繳/少繳的差額不會從年金自動退還**：公的年金每兩個月（偶數月）發放一次，未滿65歲且年額≥108萬日圓、或滿65歲以上且年額≥158萬日圓，發放當下就會被源泉徵收。年金機構每年秋季會寄送「公的年金等の受給者の扶養親族等申告書」，提出者可以扣除基本控除/配偶控除/扶養控除後再乘以5.105%扣繳；未提出者只能套用最低限度的控除，稅基被墊高、扣繳金額較高。不管哪種情況，如果因為申報不全或其他原因被多扣稅，年金機構都無權、也無法在下一期年金中退還——唯一的取回管道是本人向稅務署提出「還付申告」（確定申告的一種）。

**「確定申告不要制度」的免申告門檻**：同時滿足「全年公的年金收入≤400萬日圓」跟「年金以外其他所得≤20萬日圓」兩個條件，依法可以免辦所得稅的確定申告（但這只免「國稅」，如果有≤20萬日圓的其他所得，仍須另外辦理地方的「住民稅申告」）。超出任一門檻（年金超過400萬日圓、其他所得超過20萬日圓、有未經日本國內源泉徵收的外國年金），就是法定必須申告的情況。

**即使符合免申告門檻，仍有幾種情況需要主動申告才能拿回退稅**，其中跟這份question直接相關的是：**退休後如果房貸仍在控除窗口內，年金機構完全無法代為折抵房貸控除額**，受給者必須自己辦確定申告才能把被年金多扣的所得稅拿回來。其他情況（醫療費控除、忘記提出扶養申告書、社保自繳、故鄉納稅超過5個自治體）跟這份question的範圍無關，不展開。

**結論：現行`calculatePensionTax`的「理論全年稅負」設計足夠，不需要重新引入源泉徵收時間落差這個新維度**——公的年金的給付額跟扣除額全年固定（不像在職薪資會因為獎金、加班費波動），只要受給者每年交出扶養申告書，年金機構預扣的金額幾乎等同於全年理論稅負，兩者不存在實質的時間落差。符合免申告門檻時，受給者當年的最終實質稅負，本來就等於年金機構依稅表算出來的數字，跟`calculatePensionTax`算出的全年度理論稅負一致，不需要額外模擬「年金每月源泉徵收 vs 隔年確定申告」的雙軌金流。

真正存在時間落差、需要模擬的只有兩處邊界情境，而且都已經在這份question的討論範圍內：
- **情境A（退休當年的在職段）**：這份question已經決定的「選項B：隔年結清」（見上面「決定：採用選項B」一節），就是處理這個情境。
- **情境B（退休後任何一年，房貸仍在控除窗口內）**：這是上面確認的新發現，見下一節。

## 查證確認：房貸確定申告的復活要擴大到退休後每一年，不是只有退休當年；真實世界裡退休的人完全沒有年末調整，每一年都要靠這套機制
上面「二次review更新」這一節（第46-55行）已經記錄過房貸控除額遺失的根因跟範圍：`calculatePensionTax`「完全不接收、也不參照任何房貸控除額參數」，範圍是「退休後只要維持退休狀態，房貸只要還在控除窗口內，每一年都會被完全丟棄，直到控除窗口本身到期為止」。這點進一步確認：真實世界裡退休的人完全沒有年末調整這個環節，不管退休當年還是退休後第幾年，房貸控除額能不能扣抵、扣不完的部分要不要結轉到隔年確定申告，每一年都要處理，不是只在退休當年處理一次就結束。

查證確認現有機制裡，「發放」這一半（讀取`PreviousYearTaxRecord`、把`pendingKakuteiShinkokuRefund`當現金發放，[YearlySimulationPipeline.java:80-97](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)）完全不檢查退休狀態，本來就能年與年之間無限串接，不需要修改；缺的是「產生」這一半——`PreviousYearTaxRecord`唯一的產生點在`ActiveTaxCalculationEngine.calculate(...)`內部，只會從`applyWorkingTaxes`被呼叫，退休之後整年只會呼叫`calculatePensionTax(...)`，這個方法完全不接收房貸參數，也沒有任何地方打包這筆記錄。這個缺口同樣影響`unusedMortgageDeductionForInhabitantTax`（結轉抵未來年度住民稅）這條線，不只影響`kakuteiDeduction`。

**修法方向已經確定，見[question-191-01](question-191-01.md)「具體方向三」最新版本**：查證[MortgageCalculator.java:355-363](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)確認，`MortgageCalculator`把控除額分類成`yearEndDeduction`還是`kakuteiDeduction`，判斷依據只有「今年是不是入住第一年」，完全不知道使用者是不是退休——一個已經退休超過一年、入住房屋也超過一年的人，控除會被歸類成`yearEndDeduction`（代表系統預期這筆會被公司年末調整吃掉），但退休的人沒有公司可以辦這件事。修法方向是直接修正這個分類判斷本身：退休時不管入不入住第一年，控除額一律歸進`kakuteiDeduction`，`yearEndDeduction`固定是0——這樣`ActiveTaxCalculationEngine.calculate(...)`既有的扣抵邏輯完全不需要修改，第一步（用`yearEndDeduction`扣`baseIncomeTax`）因為輸入是0什麼都不會發生，直接用完整的當年`baseIncomeTax`幫`kakuteiDeduction`封頂（`actualKakuteiRefund = Math.min(baseIncomeTax, kakuteiDeduction)`），超過封頂的部分轉進`unusedMortgageDeductionForInhabitantTax`抵未來年度住民稅——跟在職段是同一套算法，只是輸入換成退休段自己算出來的年金所得稅。`calculatePensionTax`不需要接收房貸參數去改變年金稅負本身，`applyRetireeTaxes`需要額外呼叫一次房貸控除額，用當年年金稅負封頂後才打包進`PreviousYearTaxRecord`。

## 架構判斷：支援`retirementMonth`很可能不需要把整個模擬迴圈改成月單位
討論過程中提出一個疑慮：為了解決退休當年這個相對侷限的落差，把整個模擬引擎的主迴圈從「一年跑一次」改成「一個月跑一次」感覺本末倒置,但目前還沒想清楚正確的解法方向。這裡給出查證後的獨立判斷：

查證[YearlySimulationPipeline.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)整體架構確認，系統已經有`monthsToAdd`這個「本年度剩餘月份數」的概念，在好幾個地方被用來做「比例縮小」這種事後補正（例如第168-170行厚生年金提繳比例折算、第232-238行退休當年年金稅額比例折算）——這代表系統並不是完全沒有「部分年度」的處理能力，只是目前這個能力僅限於「把整年的結果乘上一個比例係數」這種粗略近似，不是真正逐月分別計算薪資跟年金兩種所得、分別計算對應的稅負跟社保後再加總。

這代表`retirementMonth`這個需求，**很可能不需要把整個主迴圈從年單位改成月單位**——更合理的方向可能是：在`calculateTaxesAndBuildResult`這個方法內部（這是目前`applyWorkingTaxes`/`applyRetireeTaxes`/`applyYearEndAdjustment`/`applyCashflowAndSettlement`四個方法共同的呼叫者），針對「這一年退休月份介於年中」這個特定情境，新增一段「先用在職月份算一段簡化的薪資稅負、再用退休月份算一段簡化的年金稅負、兩段結果加總」的邏輯，而不是把整個模擬從「迴圈跑12次（一年一次）」改成「迴圈跑12×N次（一個月一次）」。後者的改動量會波及`SimulationContext`裡大量目前以「年度累計」為語意的欄位、以及可能數十個現有單元測試的假設,這不是為了解決這份question討論的落差該付出的規模。

這個判斷留給之後正式設計時驗證，這份文件先記錄下來作為方向性的提示，不是最終設計定案。

### 補充查證：系統裡其實已經有一套真正的月單位收入變動引擎，只是沒有接上退休這種收入性質轉變
討論過程中問到「收入在年中改變，現在的引擎有沒有考慮？」——這點查證後發現系統的現狀比「只有比例折算的粗略近似」更進一步，值得補充進上面的架構判斷。

查證[IncomeExpenseCalculator.calculateSalaryForYear](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)確認，系統裡已經有一套`CareerEvent`機制，處理「換工作、加薪、減薪」這類年中收入改變，而且是**真正逐月分段計算**，不是比例折算：這個方法會把一年拆成好幾段（例如1-3月舊薪水、4月起新薪水），每段薪水乘上那段實際月數再加總（第92-127行的`for (CareerEvent event : sortedEvents)`迴圈，逐個事件推進`lastMonth`跟`salaryAtLastMonth`，`totalRemainingAnnualized`加總每一段），不是像`applyRetireeTaxes`裡那種「整年結果乘一個比例係數」的近似做法。

但這套月分段引擎完全沒有延伸到退休——查證同方法第66-68行確認，`if (context.isRetired())`這個判斷是整個方法最先執行的檢查，只要整年被標記成退休就直接回傳0，後面那整段`CareerEvent`月分段邏輯完全不會被執行。根因是`CareerEvent`需要使用者明確指定「哪個月生效」跟「新薪水數值」（`ABSOLUTE_OVERRIDE`或`RELATIVE_CHANGE`兩種事件類型），而系統裡目前沒有對應的「退休事件」可以指定「這個月開始退休、薪水歸零、改成另一套（年金）收入來源」——退休只有`targetRetirementAge`這一個以「歲」為單位的年齡輸入，沒有月份精度，也完全不走`CareerEvent`這條路。

**這代表上面的架構判斷可以更精確一步**：很可能不需要新建一套月單位引擎，現有的`CareerEvent`分段邏輯已經是現成、經過驗證的月單位收入變動處理方式，`retirementMonth`這個需求的其中一種可能解法，是讓「退休」也能被視為一種特殊的`CareerEvent`（或緊鄰`CareerEvent`機制旁的一個新事件類型），把「這個月起薪資歸零、改算年金」這件事交給現有這套分段邏輯去處理，而不是現在這種「整年二元`isRetired()`旗標、完全繞過`CareerEvent`」的做法。這個方向比「在`calculateTaxesAndBuildResult`內部另外寫一段退休專用的分段邏輯」更一致——等於是重用系統裡已經存在、已經在處理類似問題的機制，不是另外發明一套平行的解法。這個判斷同樣留給之後正式設計時驗證，不是定案。

**這段原本判斷「退休沒有月份精度，使用者得額外輸入一筆CareerEvent才能表達」——這點後來被討論推翻了一半，見下面「補充查證：退休月份其實可以直接從既有的birthDate推算」。**

### 補充查證：退休月份其實可以直接從既有的`birthDate`推算，不一定需要使用者額外輸入一筆`CareerEvent`
討論過程中提出一個更輕量的替代想法：如果`UserProfile`本來就有出生年月日，加上使用者設定的`targetRetirementAge`（例如55歲退休），系統理論上可以直接推算出「55歲生日那個月」或「55歲那一年的12月」當作退休月份，不需要使用者另外手動輸入一筆指定月份的`CareerEvent`。這點查證後確認技術上完全可行，而且揭露了一個現有程式碼裡被丟棄的既有資訊：

查證[SimulationContextFactory.java:127-130](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)確認，`currentAge`目前是這樣算出來的：
```java
if (profile != null && profile.getBirthDate() != null) {
    currentAge = Period.between(profile.getBirthDate(), LocalDate.now()).getYears();
}
context.setCurrentAge(currentAge);
```
`profile.getBirthDate()`這個欄位本身是完整的年月日（`LocalDate`），**月份資訊本來就存在於資料庫裡**，只是`Period.between(...).getYears()`這一步只取了年數部分，月份在轉換成`currentAge`的這一刻就被丟棄了——查證確認[AssetSimulationEngineImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)主迴圈完全不讀`birthDate`，只讀這個已經丟失月份精度的`currentAge`。

這代表技術上不需要新建`CareerEvent`，只需要在這個轉換點旁邊，額外算出並保留一個「使用者生日是幾月」的資訊（或者直接算出「`targetRetirementAge`歲生日那個月」），退休月份就能自動推算出來，不需要使用者手動維護一筆事件記錄——比「把退休包裝成一種`CareerEvent`」更輕量，因為不需要使用者多做一步輸入。

**但「滿65歲的月份就是公司規定的退休月」這個規則本身，只能當作合理的預設假設，不能當作系統唯一允許的行為**：這點是討論過程中使用者提出的真實世界經驗（日本企業常見的定年退職慣例，多數公司確實是「滿60歲/65歲那個月」或「那個會計年度/月份」生效），但這份question沒有查證過這是不是日本職場普遍到可以當作系統唯一假設的慣例——不同公司的定年退職規則可能不同（有些是生日當月、有些是生日所在的會計年度底、有些可能完全不是依生日），而且使用者也可能不是因為公司規定退休（自願提早離職、被資遣等），退休月份不一定剛好對齊生日月。比較穩妥的設計方向是：**用「生日月」當作合理的預設值/建議值，但讓使用者可以手動覆寫成任何月份**——這樣兼顧「大多數情況下不需要使用者額外輸入」跟「使用者的實際情況跟預設假設不同時仍然可以精確表達」兩個需求，不是只能二選一。

這個方向跟上面「讓退休變成一種`CareerEvent`」的方案不是互斥的——可以是「系統預設用生日月自動推算，但底層實作仍然透過產生（或等效於）一筆`CareerEvent`記錄去驅動現有的月份分段邏輯，使用者如果要覆寫月份，就是直接編輯這筆自動產生的事件」，這樣兩個方向可以合併成一套设計，不需要二選一。這個判斷同樣留給之後正式設計時驗證，不是定案。

### 補充設計決定：用一個系統設定開關（12月 vs 生日月）取代「允許使用者任意覆寫月份」，不用大費周章查證各國定年退職規定
接續上面「生日月不能當唯一假設」的討論，使用者進一步簡化了這個開放問題：不需要讓使用者逐筆輸入精確月份、也不需要查證各國（或各公司）定年退職慣例的細節差異，改成**在系統設定層級提供一個二選一開關**——「退休月份一律是`targetRetirementAge`歲那年的12月」或「退休月份一律是`targetRetirementAge`歲生日當月」，使用者自己選一個當全域假設。

這個簡化收斂了原本「生日月當預設值、允許任意覆寫」這個方向可能延伸出去的複雜度——不用再去研究「這個使用者的退休月份到底該怎麼精確判斷」這種因國家/公司規定而異、容易沒完沒了的細節，兩個選項都是全域、固定的規則，不需要為每個使用者的退休事件單獨判斷或輸入，複雜度被壓縮成一個布林開關。

### 補充查證：實作這個設計不需要幫`SimulationContext`新增欄位，通道已經存在
討論過程中提出一個疑慮：`SimulationContext`這個物件（模擬過程中沿著主迴圈一路往下傳的核心物件）的欄位已經不少，要讓生日月份這個資訊傳到需要它的地方，會不會需要再幫這個已經很肥大的物件加欄位。查證後確認不需要：

查證[SimulationContext.java:49](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)確認，這個物件本身共有55個欄位宣告，但其中第49行`private UserProfile profile;`這一個欄位，就已經持有完整的`UserProfile`物件參考（不是只抽出`currentAge`這一個欄位）——這代表`profile.getBirthDate()`這個完整日期（含月份），理論上早就隨著`context`一路傳到任何讀取`context`的地方，不需要為了這次的設計另外新增一條傳遞路徑。查證確認`context.getProfile()`目前已經被`IncomeExpenseCalculator.calculateExpensesForYear`（計算保費那一段，第150行）跟`ActiveTaxCalculationEngine`讀取——也就是說，退休月份真正需要生效的`calculateSalaryForYear`（薪資計算）這個方法所在的同一個類別（`IncomeExpenseCalculator`），本來就已經在用`context.getProfile()`，只要在`calculateSalaryForYear`裡用`context.getProfile().getBirthDate().getMonthValue()`就能直接拿到生日月份，完全不需要碰`SimulationContext`那55個欄位的清單，也不用新增任何新的傳遞路徑——這是目前查到侵入性最小的做法。

### 補充查證：全系統「月單位 vs 年單位」完整盤點，確認模式不是「系統沒有月單位能力」，是「月單位能力目前只侵限在起算過渡年這個特例」
討論過程中逐一盤點了系統裡所有主要計算模組的時間精度，確認結果比上面只分析薪資一項更完整，列在這裡供之後設計`retirementMonth`時參考：

**已經是真正月單位（逐月分段計算，不是整年比例近似）：**
- **薪資**：`CareerEvent`機制，上面已經討論過。
- **生活費**：查證[IncomeExpenseCalculator.calculateExpensesForYear](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)確認，`baselineMonthlyExpenses`本身就是月單位欄位，`LifeEvent`（生活事件，例如搬家、小孩出生這類會改變每月開銷的事件）用`startMonth`/`endMonth`算實際生效月數再換算回整年金額，不是整年近似。
- **保險費**：同方法確認，`activeMonths`依保單`endDate`落在哪個月、再跟`monthsToAdd`取較小值，到期當月之後就不再把保費算進去。
- **房貸**：`MortgageCalculator`逐月攤還計算（`monthlyRate`、每月分別算本金跟利息），這是房貸模擬本來就該有的精度。
- **學費**：`EducationTimelineProjector`用「絕對日曆時間軸」（`YearMonth`精度）把每個教育階段投影到小孩的真實生日上，真正拆區間，不是年度近似。

**所得稅、住民稅**：這兩個稅本身在真實世界就是以「年」為單位結算，沒有「月單位所得稅」這種東西，程式碼的分工是先用上面這些月單位算出來的所得（薪資、年金等），再把結果餵進年度稅率表——這個分工本身沒有問題，不是這份question要處理的落差。

**還沒接上月單位、只在「起算過渡年」這一個特例情況下有折算、未來任意一年都整年一次性計算到底的：**
- **NISA、DC、iDeCo定期定額提繳**：查證[TaxAdvantagedAccountProjector.processAccountsForYear](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)確認，這個方法確實有接收`monthsToAdd`，而且方法裡有一大段詳細的設計說明（第57-74行的註解）解釋「過渡年為什麼只算剩餘月份、不回填YTD提繳」——理由是券商對帳單只提供當下一筆快照、沒有逐月扣款歷史，硬去推算YTD提繳金額的風險太高，寧可讓過渡年的稅負被保守高估，也不要猜一個可能完全錯誤的數字。但查證[SimulationFund.calculateContribution:106-109](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)確認，這段月份折算邏輯只在`isInitialYear`（模擬起算的過渡年）為true時才會執行——`if (!isInitialYear) { return annualContribution; }`，只要不是起算年，不管那一年是不是退休、是不是發生了任何`CareerEvent`，NISA/DC/iDeCo的提繳金額永遠直接回傳整年固定值，完全不會因為退休而在年中停止扣款。

這個盤點確認的模式，跟上面薪資/`CareerEvent`那段發現一致：**不是「系統完全沒有月單位處理能力」，是「月單位能力目前集中在模擬起算的過渡年這一個特例，沒有被推廣到未來任意一年會發生的事件」**——薪資可以透過`CareerEvent`在未來任何一年指定月份生效的事件，但NISA/DC/iDeCo的月份折算邏輯被寫死只認`isInitialYear`，連這一塊既有的月份能力都沒有被推廣到「未來某一年退休」這個情境。如果之後真的要支援`retirementMonth`，這些還停留在「只有起算年才折算」的模組（目前只找到NISA/DC/iDeCo這一項），需要一併檢視是否也要擴展到未來任意一年。

### 補充說明：上面「只差延伸到整個人生」指的是領域覆蓋範圍，不是程式碼改動量；也不需要讓主迴圈的介面知道月份存在
討論過程中澄清了一個用詞上的歧義，記錄下來避免之後誤讀：上面「這代表上面的架構判斷可以更精確一步」「這個盤點確認的模式」這幾段講的「只差延伸」「只差把現在鎖在過渡年延伸到整個人生」，指的是**領域覆蓋範圍**——目前只有薪資（`CareerEvent`）有能力在未來任意一年處理月份層級的事件，NISA/DC/iDeCo還停留在只認起算年這一個特例；要補齊這個落差，需要讓NISA/DC/iDeCo（以及任何之後發現有同樣限制的領域）的提繳計算，也接上「這個事件發生在第幾個月」這個概念。這不是說「只差一行程式碼」——`CareerEvent`目前的事件類型（`ABSOLUTE_OVERRIDE`/`RELATIVE_CHANGE`）設計給薪資用，退休要共用這套時間軸語言，或是讓NISA/DC/iDeCo各自接上類似的月份判斷邏輯，都是需要重新設計介面的中等工作量，不是微調既有程式碼。

另外一個重要的補充設計方向：**受影響的那一年，可以在計算這一年份時臨時借用月的顆粒度去分段算，算完把結果摺回成一個整年的數字，再交還給外層完全不知道月份存在的年單位主迴圈**——跟年份無關的其他39年，不需要被改動，也不需要知道「月」這個概念存在。這代表`AssetSimulationEngineImpl`主迴圈本身的介面（傳入/傳出的都是「一整年的結果」）完全不用變，`retirementMonth`這個功能可以被侵限在「某一年內部怎麼算」這一層，不需要往外擴散成「整個模擬系統現在都要用月做單位」。這跟上面「不需要把整個主迴圈從年單位改成月單位」的判斷是同一個方向，這裡進一步確認：連「主迴圈要不要意識到月份」這件事本身都不需要改變，月份的概念可以完全侵限在某一年內部的計算細節裡，不需要往外暴露。

### 補充查證：支援`retirementMonth`不需要把`AssetSimulationEngineImpl`主迴圈從年改成月，對Grid Search的計算量影響也遠比直覺以為的小
討論過程中問到「這樣架構不就已經漸漸到位了嗎」跟「計算量會不會因此暴增，Grid Search是不是本來就已經在某處卡梯度了」，查證結果如下：

**主迴圈本身不會變。**查證[AssetSimulationEngineImpl.java:146](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)確認，主迴圈是`for (int age = context.getCurrentAge(); age <= targetAge; age++)`，一歲呼叫一次`processSimulationYear`。`retirementMonth`這個需求影響的不是這個主迴圈的疊代次數，是「在退休發生的那一年內部，要不要把這一年拆成在職段跟退休段分別計算」這個更局部的問題——主迴圈還是一年跑一次，只是其中一次（退休那一年）的內部計算變得比其他年份複雜一點。

**Grid Search的呼叫次數早就被一個跟搜尋空間大小無關的硬上限鎖住，不會真的被計算量影響。**查證[OptimizationConstants.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java)確認：
- NISA維度本身（成長/積立兩個金額，各0~20萬/0~10萬，每次跳1萬）就有`(200,000/10,000+1) × (100,000/10,000+1) = 21 × 11 = 231`種組合。
- 退休年齡`50-80`歲（31種）、年金請領年齡`60-75`歲（16種）、iDeCo開始年齡`60-75`歲（16種），這幾層（查證[GridSearchEngine.java:70-141](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)確認是五層巢狀迴圈：NISA、退休年齡、年金請領年齡、iDeCo開始年齡、房貸全額還款年份）光是前四層乘起來就已經遠遠超過`MAX_GRID_ENGINE_COMBINATIONS = 5000`這個寫在程式碼裡的硬上限（`GridSearchEngine.java`裡`if (++evaluatedCount[0] > MAX_GRID_ENGINE_COMBINATIONS) throw new IllegalStateException(...)`）。

這代表現在的Grid Search，實際執行`evaluator.evaluate(...)`的次數早就被夾在5000這個量級，不是真的把搜尋空間窮舉完——這跟「Grid search光是第一年就報梯度」這句話描述的現象方向不完全一樣：真正的情況不是「某一次模擬算太久所以提早放棄」，是「候選組合本身太多，系統用一個固定次數的硬上限去截斷，不管每次`evaluate()`花多久」。

**所以`retirementMonth`對Grid Search的影響，是讓這5000次裡的每一次`evaluate()`稍微變貴一點，不是讓outer loop的次數變多。**因為這個額外的複雜度（判斷「這一年要不要拆成兩段算」）只發生在「退休那一年」這一個特定的年份上（例如40年模擬裡的某一年），剩下39年完全不受影響——這是一個侵限在單一年份的局部增量，不是整體計算量乘以12倍。真正會讓Grid Search/GA計算量暴增到無法接受的情況，是把`AssetSimulationEngineImpl`主迴圈本身從「一年跑一次」改成「一個月跑一次」（這樣outer loop次數會從40次變成480次），但上面已經確認這個做法不是必要的，重用`CareerEvent`分段邏輯的做法不會動到主迴圈的疊代次數。

## 大架構方向已確認，回頭逐一拆解「2. 稅金與年末調整在不同情境下的失真細節」這一節的4個子問題——查證結果：有辦法解，而且比預期乾淨，因為核心稅務引擎本身是純函數設計
上面已經確認「1. 現行引擎對退休的二元化抽象假定」可以靠退休年齡+退休月開關解決。這一節回頭處理原本「2. 稅金與年末調整在不同情境下的失真細節」這段列出的4個子問題：(1) 所得稅該在退休月停止、不是整年視為0；(2) 沒有年末調整的人，房貸控除要怎麼復活到確定申告；(3) 退休年的收入計算；(4) 社會保險看起來沒問題，但要確認為什麼。

查證核心結論：**`ActiveTaxCalculationEngine.calculate(SimulationContext, grossBaseSalary, ...)`這個方法本身是一個純函數——給它多少所得，它就依照年度級距規則重新算出對應的扣除額跟稅負，不是先假設一定是12個月的所得再做任何特殊處理。這代表要讓退休當年的在職月份正確納稅，不需要修改這個方法本身，只需要改「呼叫它的人傳了什麼所得進去」。**

### (1) 所得稅該在退休月停止——不是「整年打折」，是「用正確的部分年所得重新算」
查證[ActiveTaxCalculationEngine.java:95-178](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認，`employmentIncomeDeduction`（給与所得控除）、`basicDeductionIncomeTax`/`basicDeductionInhabitantTax`（基礎控除）、配偶/扶養控除、保險費控除，全部都是從傳入的`grossBaseSalary`/`grossTotalSalary`這個所得金額，透過`employmentIncomeDeductionStrategyFactory`/`basicDeductionStrategyFactory`等策略類別，依照年度級距規則**重新查表算出**——不是先算出整年稅負再乘比例。

這很重要，因為所得稅的扣除額跟稅率是非線性的（級距式、不是跟所得等比例變化）：如果簡單把「整年稅負」乘以`6/12`去打折，算出來的數字會跟「用半年的真實所得重新跑一次這套級距規則」得到的結果不一致，產生失真。但查證確認的做法剛好不是打折，是**直接把退休前這段期間在職月份的真實所得，當作`grossBaseSalary`餵給這個方法，讓它自己重新跑一次完整的扣除額/稅率計算**——這樣算出來的就是這段部分年所得「本來就該繳多少稅」的正確答案，不是近似值。

目前的障礙純粹是`applyWorkingTaxes`外層的`if (!context.isRetired())`這個全有全無的判斷擋住了呼叫——只要把這層判斷改成「退休當年，如果退休月之前有在職月份，就用這段月份的所得呼叫一次`calculate(...)`」，這個方法內部完全不用改。

### (4) 社會保險「好像沒問題」——這個直覺部分對，原因是社保費率結構剛好是線性的，但現狀其實還是有bug（完全沒被算）
查證[ActiveTaxCalculationEngine.java:83-105](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認，健康保険/厚生年金/雇用保険這三項社保費用的算法是`monthlySalary = grossBaseSalary / 12`、再乘上固定費率（`healthInsuranceRate`/`pensionRate`/`employmentInsuranceRate`，均有法定上限封頂）——這是**線性關係**（月薪× 固定費率），跟所得稅的級距扣除額、累進稅率完全不同性質。這代表「社會保險好像沒問題」這個直覺背後的真正原因是：**社保費率結構本身的線性特性，讓『直接餵入在職月份的真實所得』這個做法天然就會得到正確的部分年結果，不需要額外的特殊處理或打折邏輯**——跟所得稅需要重新查表、不能打折的理由不同，但剛好殊途同歸，同一個「傳入正確的部分年所得」的改法，對兩者都是正確解。

但現狀確實還是有bug，跟「好像沒問題」的語感不完全一致：因為`calculate(...)`整個方法目前被`applyWorkingTaxes`的`if (!context.isRetired())`擋在外面，退休當年**完全不會被呼叫**——這不是「算出來的社保數字不準」，是「退休前這幾個月在職期間，本該繳的厚生年金/健康保険保費完全沒有被計入這一年的稅負計算」。這跟(1)是同一個根因、同一個修法會一併解決，不是需要獨立處理的另一個問題。

### (2) 沒有年末調整的人，房貸控除怎麼復活到確定申告——機制已經存在，只差「被正確呼叫一次」
查證[YearlySimulationPipeline.java:80-97,257-262](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（上面「跟issue-172、issue-190共享的根因」一節已經查過的`PreviousYearTaxRecord`/`pendingKakuteiShinkokuRefund`機制）確認，`actualKakuteiRefund`（這一年沒用完、要結轉到確定申告的房貸控除額）是在`ActiveTaxCalculationEngine.calculate(...)`內部算出來的（第231行`actualKakuteiRefund = Math.min(baseIncomeTax, kakuteiDeduction)`），並在同一個方法裡打包進`PreviousYearTaxRecord`（第257-262行），下一年由`YearlySimulationPipeline`的另一段程式碼（第80-97行）領出來當現金發放。

這整套機制本身已經存在、不需要重新設計——只要(1)的修法讓`calculate(...)`在退休當年的在職月份也會被呼叫一次，`kakuteiDeduction`自然會被正確算出並包進`PreviousYearTaxRecord`，下一年的現金發放邏輯完全不需要修改就能正確運作。這點完全呼應上面「跟issue-172、issue-190共享的根因」一節的分析：房貸退稅遺失的根因是「`applyWorkingTaxes`整段被跳過，這套機制連建立記錄的機會都沒有」，不是這套機制本身設計錯誤——(1)的修法正是直接解除這個根因。

### (3) 退休年的收入計算——這是前三點的「總裝」，不是獨立的新問題
把(1)(2)(4)串起來，退休當年完整的計算流程會是：
1. 如果退休月之前有在職月份，用這段期間的真實所得呼叫一次`ActiveTaxCalculationEngine.calculate(...)`，正確算出這段期間的所得稅、住民稅、社保、以及（如果有房貸）`kakuteiDeduction`。
2. 用退休月之後剩餘月份的年金所得呼叫`calculatePensionTax(...)`——這部分`applyRetireeTaxes`裡已經有，而且已經有比例折算邏輯（第232-238行），不需要修改。
3. 兩段的`netIncome`、各項稅額加總成這一年最終要回傳的結果。
4. 在職段算出來的`kakuteiDeduction`（如果沒用完）會自動包進`PreviousYearTaxRecord`（見上面(2)），下一年自動發放現金，不需要額外處理。

**這代表真正要修改的範圍，侵限在`calculateTaxesAndBuildResult`這個方法內部的分流邏輯**——判斷「這一年有沒有發生退休事件」（用上面已經確認的退休年齡+退休月開關去判斷），如果有，就把`applyWorkingTaxes`跟`applyRetireeTaxes`都各自呼叫一次（各自帶上正確的部分年所得跟月份數，取代現在互斥的`if/else`寫法）；沒有發生退休事件的年份，維持現狀完全不變。`ActiveTaxCalculationEngine.calculate`、`calculatePensionTax`這兩個核心計算方法都不需要被修改，因為它們本來就是能接受任意所得金額、自己重新算出正確結果的純函數設計——這也呼應了上面已經確認的效能判斷：改動侵限在單一年份的內部分流邏輯，不會讓Grid Search/GA的計算量暴增。

這個判斷同樣留給之後正式設計時驗證，不是定案，但目前查證下來，技術路徑是清楚、可行的，沒有發現任何需要大規模重寫的障礙。

## 討論過程中釐清的真實世界稅制機制，揭露出一個比上面文字描述的更深層缺口

### 真實世界：年中離職時，源泉徵收多繳的部分要怎麼拿回來
日本的年末調整（年末調整，通常在11-12月進行）**只會由「12月31日當天還在職的那家公司」幫在職員工辦理**。如果使用者在年中（例如5月）離職、年底前沒有被其他公司雇用，5月那家公司**不會**幫這個人辦年末調整——離職時只會拿到一張記錄到離職月份的「源泉徴収票」。

因為每月薪資的源泉徵收稅額，本來就是公司依照「這個薪資水準會持續領一整年」這個假設去查稅額表扣繳的，一旦年中離職、全年收入比假設的少，真實全年所得稅負（用實際在職月份的所得、套用全年完整的基礎扣除額等計算）幾乎必然比已經扣繳的金額少，會有溢繳。要拿回這筆溢繳的稅款，**使用者本人必須在次年2月中到3月中自己辦確定申告**，申報全年所得（包含這份薪資所得，如果同年還有年金所得也要一併申報）才能領到退稅。如果年底前被另一家公司重新雇用，新公司可以拿舊公司的源泉徴収票幫忙在12月的年末調整裡一次結算，這種情況下才不需要使用者自己辦確定申告。

### 查證確認系統目前完全沒有模擬過這件事——而且系統裡「確定申告」這個詞已經被另一個完全不同的情境占用，容易混淆
查證[YearlySimulationPipeline.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)跟相關計算邏輯，整個模擬系統裡「確定申告」（`kakutei`/`KakuteiShinkoku`）這個詞只出現在一個地方：房貸抵稅額（`kakuteiDeduction`，[ActiveTaxCalculationEngine.java:231](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)）——這指的是買房頭幾年，房貸特別扣除需要辦理一次性確定申告才能拿到的那部分退稅，跟這裡討論的「薪資源泉徵收多繳、年中離職沒有年末調整資格、需要自行確定申告拿回溢繳稅款」完全是**兩個不同的真實世界情境**，只是中文/日文詞彙剛好相同，容易被誤認成同一回事。系統裡沒有任何程式碼處理過後者。

更根本的問題是：`YearEndAdjustmentEngine.calculateYearEndAdjustment`（issue-190討論過的那個方法）從頭到尾**沒有任何地方檢查「這個人12/31這一天到底還在不在職」**——這個方法的設計隱性假設是「這個人會做滿整個模擬起算年、年底還在職、會被現在這家公司辦年末調整」，不管模擬起算年使用者實際上是不是在職到年底。目前因為`applyWorkingTaxes`（唯一會呼叫這個方法的路徑）本身的進入條件是`!context.isRetired()`，所以只要使用者今年沒有被判定成退休，系統就會假設他做滿整年、年底會被公司辦年末調整——但「今年沒有退休」跟「今年年底還在同一家公司在職」並不是同一件事：使用者也可能是在模擬當下的月份換工作、或是今年某個月離職後暫時沒有工作直到年底（不是正式退休，只是career break），這些情境下一樣會有「源泉徵收多繳、需要自己辦確定申告才能拿回退稅」的落差，但系統完全沒有模擬過，模擬出來的全年稅負會比真實情況更低（因為系統假設的是「公司幫辦好年末調整」這個最理想情況，而不是「自己要等到次年2-3月才能拿回退稅」這個真實時間落差）。

**這代表這份question討論的範圍，可能比原本「退休當年」這個單一情境更廣**——任何「年中有一段時間沒有被雇用、年底也沒有重新被雇用」的情境（不限於退休），都可能觸發同樣的「源泉徵收多繳沒被正確模擬」問題，只是退休是最常見、最容易被使用者實際拿來跑模擬的情境，所以最先被注意到。

### 範圍要不要擴大到任何年中離職情境——不擴大，使用者真正會想模擬的「不工作」情境只有退休這一種
上面指出的理論缺口是：系統用`isRetired()`這一個旗標判斷「今年底有沒有雇主辦年末調整」，但「沒有退休」跟「今年年底仍在職」理論上不是同一件事——使用者也可能在某一年離職後一段時間沒有工作，還沒到退休年齡。這個理論缺口要不要補、要不要把這份question的範圍擴大到涵蓋這種情境，答案是不擴大：

**非自願性失業不是使用者會主動設定去模擬的輸入**：被裁員、公司倒閉這類情境沒有人能在模擬當下預先指定「第幾年會發生」，不像退休年齡是使用者自己決定、自己輸入的規劃性參數——沒有人會說「我想看看第20年如果失業，錢夠不夠用」，這不是一個有意義的模擬情境。而且一旦真的進入這種情境，使用者在真實生活裡要做的決策（大幅砍支出、可能賣房應急）已經超出這個模擬器原本要回答的問題（「照這個計畫過日子，資產夠不夠」）——到了那個情境，不管稅務計算多精確，算出來的數字都沒有實際意義。

**如果使用者真的想模擬「提早一段時間不工作」，現有的退休機制已經足以表達這個意圖**：不管真實原因是規劃性的career break還是單純提早退休，把這段不工作的時間設成`targetRetirementAge`，就能直接套用這次已經確定的退休當年稅務設計（在職段用真實所得重新計算、差額遞延到隔年確定申告結清，見[question-191-01](question-191-01.md)）——不需要再建一套「失業但沒退休」的第三種狀態去平行處理同一件事。

**這代表`YearEndAdjustmentEngine`不需要新增「這個人年底是否在職」這個獨立判斷維度**——`isRetired()`這一個旗標已經足夠承擔「這個人今年有沒有被雇主辦年末調整」的判斷，因為任何「不工作」的情境，使用者都應該、也已經可以透過設定退休年齡來表達，不會有需要另外處理的第三種狀態存在。

## 跟Plan-21的關係：這份question的「夾擊法」解不掉Plan-21，兩者是不同形狀的問題，但這份question討論的所有子問題都不需要Plan-21就能解
討論過程中問到：既然這份question設想的解法（只在「有名字的事件年」臨時展開成月、其他年份維持年單位）看起來已經讓系統「幾乎」支援月單位，那Plan-21（模擬引擎月級別時間精度，目前open狀態等雙引擎架構評估）是不是因此失去存在價值、可以被這份question一起解掉？反過來問：這份question自己討論的所有子問題，是不是其實也需要Plan-21的月單位引擎才能真正解掉？

**兩個問題的答案都是否定的——Plan-21不會被這份question解掉，但這份question目前討論的所有子問題，也都不需要等Plan-21才能解。**關鍵判準是「觸發這個問題的那一年，能不能被預先知道」：

- **這份question目前討論的所有子問題**（issue-172核心bug、issue-190、房貸確定申告時序、年金請領銜接精度、擴大到career break的範圍），全部綁在使用者**明確輸入**的事件上——退休綁在`targetRetirementAge`，career break可以用`CareerEvent`表達（查證[CareerEvent.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/event/CareerEvent.java)確認`eventYearMonth`是使用者自己指定的欄位，`CareerEventType.ABSOLUTE_OVERRIDE`可以把薪水設成0來表達留職停薪，不需要系統自動偵測）。這些情境的共同點是：**系統在模擬開始之前，就已經知道「哪一年」需要被特別處理**，所以「只在那一年臨時展開成月、其他年份維持年單位」這個夾擊法，技術上足以覆蓋這份question討論的全部範圍。
- **Plan-21的「需求二」（現金流年中波動偵測）性質完全不同**：查證[Plan-21](../planned/Plan-21-inhabitant-tax-prior-year-income-engine.md)確認，這個需求描述的是「**任何**一年都可能發生」的現象——固定資產稅、保險費集中在某幾個月、獎金入帳跟月付支出之間的時間差——這些不是使用者會提前告知系統的「事件」，是系統要在模擬過程中去偵測「這一年有沒有月中見底又回正」的現金流形狀。**沒有辦法預先知道哪一年需要被特別處理，所以沒辦法用「只展開特定年份」這招去解**——要解決這個需求，必須讓**每一年**都用月的顆粒度去追蹤現金流，這就是Plan-21裡估算的「12倍計算量」代價的真正來源。

兩者的差異不是「問題大小」，是「觸發條件能不能被預先知道」——這份question的子問題全部屬於「可預知、可指名哪一年」的類型，Plan-21需求二屬於「不可預知、每一年都要防」的類型，兩者需要的解法形狀完全不同，不能用這份question的夾擊法去頂替Plan-21，Plan-21也不需要為了這份question而被牽動。

至於Plan-21的「需求一」（住民稅前一年所得、6月切換）——上面第62行已經確認這是適用於**所有年份**的課稅時序規則（不是侵限在特定事件年），跟這份question「可預知事件年」的性質也不同，維持原本記錄在Plan-21裡的狀態，這份question不重複處理。

## 退休當年在職段算出來的差額（追繳或退稅）該在今年結清還是隔年結清，連帶牽動issue-190要不要跟著改
上面已經確認退休當年在職段要用這段期間的真實所得重新呼叫`ActiveTaxCalculationEngine.calculate(...)`，正確算出這段期間的所得稅/住民稅/社保/房貸控除（見[question-191-01](question-191-01.md)的「具體方向二」）。這段真實計算出來的理論正確稅負，跟這段期間已經被公司實際扣繳的金額（YTD）之間，很可能有落差——可能是正的（預扣太多、該退稅）也可能是負的（預扣不足、該追繳）。這筆落差要怎麼結清，有兩個選項，還沒決定：

**選項A：維持現狀，讓`applyYearEndAdjustment`在退休當年也被呼叫，差額今年立刻結清**。查證[YearlySimulationPipeline.java:277-294](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認，這個方法目前的行為是把算出來的差額直接覆寫回`state.setEstimatedIncomeTax(adj.remainingIncomeTax())`這類欄位，立刻反映在「今年」的現金流結算裡，沒有存進任何跨年記錄。這個行為模擬的是「公司幫你辦好年末調整，今年薪水帳戶裡的差額已經結清」。

**選項B：讓這筆差額跟房貸的`kakuteiDeduction`一樣，包進`PreviousYearTaxRecord`，隔年才透過確定申告結清**。理由是退休的人真實世界裡沒有公司幫忙辦年末調整，不管差額是正是負，都要等到隔年2-3月自己辦確定申告才會真正發生現金流動，選項A「今年立刻結清」的時間點跟真實世界不一致。

**這個選擇會不會影響issue-190，查證如下**：issue-190描述的場景（起算年同時是退休年、`applyYearEndAdjustment`算出不合理的巨大負值）的根因，是這個方法拿「退休後幾個月的小額年金稅」去跟「退休前真實的YTD薪資稅」相減——不管選項A還是選項B，在職段都已經改用真實所得重新計算，這個根因本身都會被消除，190描述的那個不合理巨大負值不會再出現。差別在於：選項A的話，算出來的（修正後、合理的）差額還是會顯示在「今年」這一列的`estimatedIncomeTax`欄位，繼續經過`TaxAdjustmentCell`這個既有的負數提示元件；選項B的話，查證[YearlySimulationPipeline.java:81-82](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認，現有房貸退稅隔年發放時是直接加進`context.setCurrentLiquidCash(...)`這個現金欄位，不會寫回`estimatedIncomeTax`這個稅額欄位——如果所得稅/住民稅的差額也照搬這套發放方式，退休當年跟隔年的`estimatedIncomeTax`這一列都不會再顯示這筆差額，`TaxAdjustmentCell`的負數提示機制不會被觸發，使用者要透過別的畫面（例如現金流那一列有一筆入帳）才能注意到這筆退稅/追繳。

換句話說，選項A跟選項B都解決了190的根因（錯誤的巨大負值），但對190現有的UI提示機制（負數+提示框告訴使用者這是退稅信號）會不會繼續在「正確」的場景下發生作用，結果不同——選項A讓這個機制繼續生效、只是顯示的數字變正確；選項B讓這個機制不再被觸發，差額改成透過現金流入帳、沒有專屬的提示說明。這兩個選項該怎麼選，需要往下討論。

### 決定：採用選項B（隔年確定申告結清），追繳跟退稅都遞延，UI要不要顯示預告是獨立的新功能，不影響這個決定
選項B確定採用，理由是「今年底有沒有雇主」才是這個機制存在的真正依據，跟差額的正負號無關——退休的人今年底沒有雇主可以幫忙結清，不管是該退稅還是該追繳，都沒有「當年由公司結清」這個選項，兩個方向都要遞延到隔年確定申告才真正發生現金流動，保持處理方式對稱。

**要不要讓使用者在「今年」就看到一筆「隔年會有退稅/追繳」的預告訊息，是跟這個決定無關的另一件事，算新功能**：查證確認現有資料結構（`PreviousYearTaxRecord`、`AssetSimulationYearlyResult`、前端`TaxAdjustmentCell`）完全沒有任何欄位或元件可以承載這種預告——`PreviousYearTaxRecord`這個記錄從頭到尾只存在於後端`SimulationContext`內部,從來沒有被複製進任何年度輸出結果或API response，前端也查無任何相關的文字或Tooltip。這代表現有的房貸確定申告退稅機制，其實一直都有這個「使用者在算出的那一年完全看不到任何預告，只會在隔年發現現金池數字多了一筆、沒有任何說明」的透明度缺口——這個決定不需要連帶解決這個缺口，要不要做、什麼時候做，留給之後當一個獨立功能來評估。

**連帶需要注意的一點**：Grid Search/GA判斷「破產」的方式是逐年檢查`projectedLiquidCash`有沒有瞬間變成負數（查證[AssetSimulationFitnessEvaluator.java:116-127](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java)），不是只看模擬結束時的總資產；現金一旦變負會立刻觸發[CashflowLiquidationCalculator.processNegativeCashflow](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/CashflowLiquidationCalculator.java)強制變賣股票/NISA去補足赤字。這代表選項B把退稅遞延到隔年，可能讓退休當年的現金看起來比真實情況更緊繃（如果使用者手頭本來就偏緊）；把追繳遞延到隔年，則反過來讓退休當年看起來比真實情況更寬裕、隔年才突然變緊——不管哪個方向，「哪一年的現金健康程度被系統看到」都會因為選項B而改變，這點在實作驗證時需要留意，但不影響已經決定要採用選項B這件事本身。

**結論：issue-190會因為這個決定自然消失，不需要照它原本提的方案（依isRetired分流Tooltip文案）去修**。190的觸發條件（模擬起算年同時是退休年）跟這份文件討論的YTD落差，是同一個條件——只有起算年才有真實的YTD扣繳事實可以比較。選項B底下，這個比較動作完全不會被算進「今年」的`estimatedIncomeTax`等欄位，今年這一列只會顯示在職段用真實所得重新算出來的正常稅負，不會有任何因為跟YTD比較而產生的負數——190原本要處理的「這一列顯示不合理巨大負值、Tooltip文案對不上退休情境」，這個前提本身在選項B底下不會發生。隔年也不會重生同樣的問題：遞延的差額是直接加進`currentLiquidCash`現金欄位（跟現有房貸退稅同一套路徑），不經過`estimatedIncomeTax`這三個稅額欄位，不會觸發`TaxAdjustmentCell`。issue-190.md的最終狀態要不要標成「由191解決、非獨立修復」，留給之後決定。

## 查證確認：確定申告退稅/追繳的真實時間落差，以及為什麼年金不可能代為結清這筆差額
上面已經決定退休當年在職段的追繳/退稅差額要遞延到隔年才結清（選項B）。這裡進一步查證真實世界這筆差額是透過什麼機制、在什麼時間點結清，確認結果如下：

**年金機構不可能代為結清這筆差額，不是選項之一**：公的年金（日本年金機構）的源泉徵收是完全獨立封閉的體系——年金機構只依照年金請領金額套用公的年金等控除跟標準源泉稅率去預扣年金所得稅，沒有管道讀取使用者上半年的在職薪資源泉徵收記錄，也沒有任何機制可以在發放年金時幫忙多扣或退還在職薪資的稅款差額。年金機構後續可能從年金裡代扣的，只有住民稅（滿65歲且年金達一定門檻者，隔年10月起地方政府會把住民稅改成直接從年金代扣），這是地方稅的徵收手段，跟這裡討論的在職所得稅追繳/退稅差額是完全不同的機制，不會互相替代。這代表`PreviousYearTaxRecord`這類記錄要結清時，發放對象只能是現金池（使用者自己的帳戶），不能也不需要考慮「從年金裡扣」這個選項。

**選項A（今年結清、由公司年末調整處理）在真實世界被制度封死，不是設計選擇**：日本國稅廳規定，公司辦理年末調整的法定前提是員工在12月31日當天仍受雇在職。退休當年沒有年底雇主可以辦這件事，選項A從頭到尾不是真實世界裡退休者可以選的路——唯一能在今年結清的例外，是年底前被另一家公司重新雇用、新公司拿舊公司的源泉徵收票合併結算，但這種情況下使用者已經不是「退休」，是換工作，不適用這裡的討論。這確認了選項B不是兩個同樣可行的選項裡比較合理的一個，是真實世界裡唯一存在的路徑。

**真實世界的時間點是隔年2月16日至3月15日由本人辦確定申告，稅務署核算後約3-5月撥款**：時序是退休當年年底先收到公司的源泉徵收票（如果當年已開始領年金，翌年1月會再收到年金機構的源泉徵收票），翌年2-3月本人合併申報薪資所得跟年金所得，連同全年基礎控除、配偶/扶養控除、社保控除、房貸抵稅額一起結算，約3-5月完成核算、差額撥款。這比現有`pendingKakuteiShinkokuRefund`機制「隔年整年算一次、不分月」的簡化模式更精細——真實世界這筆錢是隔年**第一季**才到帳，不是隔年任何時候都可能到帳。

**真實世界幾乎必然是退稅、幾乎不會是追繳**：公司每月源泉徵收是假設「這筆薪水會連續領12個月」去查稅額表扣繳的，年中退休讓全年所得驟降，但基礎控除/扶養控除/社保控除仍按全年計算，幾乎必然造成上半年預扣過多；如果原本有房貸抵稅資格，少了公司年末調整的管道，這筆扣除只能在確定申告一併申報，進一步放大退稅金額。這跟前面討論「追繳跟退稅都遞延、保持對稱」這個設計決定不衝突——只是真實世界裡退休當年的追繳情境理論上存在但極罕見，不影響系統設計上仍然用同一套機制處理兩個方向。

**沿用`pendingKakuteiShinkokuRefund`的「隔年算一次、不分月」設計模式是合理的簡化，不需要為了這3個月的落差另外設計一套**：現有機制本來就是「隔年才發放」的整年粒度，跟真實世界「隔年第一季」比起來已經是同一個量級的時間落差（都是「不在今年、在隔年」），沒有必要為了精確到「隔年哪個月」去新增月份層級的設計——這類精確到月份的現金流時間點，本來就是[Plan-21](../planned/Plan-21-inhabitant-tax-prior-year-income-engine.md)需求二（現金流年中波動偵測）在處理的題目範疇，不屬於這份question要解決的範圍。

## 接下來要討論的問題
以下六點已經有結論，不再是開放問題：
- 分段計算（退休前月份照在職計算、退休後月份照退休計算、年末統一結算）這個設計方向本身是否合理——合理，對應[question-191-01](question-191-01.md)的「具體方向二」，查證過程沒有發現需要推翻這個方向的障礙。
- 退休當年在職段算出來的追繳/退稅差額要選「今年結清」還是「隔年結清」——已決定選隔年（選項B），見上面「決定：採用選項B」一節。
- 範圍要不要從「退休當年」擴大成「任何年中離職且年底未被重新雇用的情境」——不擴大，非自願性失業不是使用者會主動設定的模擬輸入，任何規劃性的「提早一段時間不工作」都可以直接用現有的退休年齡設定來表達，不需要新增獨立狀態，見上面「範圍要不要擴大到任何年中離職情境」一節。
- 要不要投入實作、排在什麼時程——這是使用者自行安排的排程決定，不在這份文件的討論範圍內。
- 自己辦確定申告拿回溢繳稅款，時間點上要怎麼反映「次年2-3月才能真正拿到現金」這個落差——沿用現有`pendingKakuteiShinkokuRefund`「隔年算一次、不分月」的簡化模式即可，不需要為了這3個月的落差另外設計一套，也確認了年金機構不可能代為結清這筆差額，見上面「查證確認：確定申告退稅/追繳的真實時間落差」一節。
- 退休後年金的稅務處理機制（年金受給者の確定申告不要制度的免申告門檻、現行`calculatePensionTax`的「理論全年稅負」設計要不要重新引入源泉徵收時間落差）——不需要重新設計，公的年金給付額全年固定、沒有實質時間落差，現行設計已經足夠，見上面「查證確認：退休後年金的稅務處理機制」一節。

以下一點原本列在開放問題，設計方向已經定案（修改`MortgageCalculator`的分類判斷，讓退休時一律歸進`kakuteiDeduction`，見[question-191-01](question-191-01.md)「具體方向三」最新版本），不再是開放問題，但衍生出一個更小的介面細節還沒決定：

1. 要讓退休段的房貸控除額用「當年年金`baseIncomeTax`」封頂，呼叫端需要拿到這個數字，但`calculatePensionTax`的回傳物件`TaxCalculationResult`目前沒有回傳封頂前的`baseIncomeTax`——要幫`TaxCalculationResult`新增欄位，還是讓`calculatePensionTax`直接接收房貸參數在內部封頂（這個選項會跟「不修改`calculatePensionTax`簽名」的既有決定衝突），兩個選項還沒有查證清楚取捨，見[question-191-04](question-191-04.md)「修改2」。
