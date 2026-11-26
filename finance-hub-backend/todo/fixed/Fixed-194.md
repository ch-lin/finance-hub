# [FIXED] 194：退休當年，企業型DC（企業型確定拠出年金）提繳會被整年錯誤歸零，不會按退休月份拆分——查證確認是真正的bug，退休前在職月份的提繳是已經實際發生的既成事實，不該被模擬抹除。方向定案：直接沿用iDeCo（issue-193）已經設計好的按月拆分機制；但DC不需要iDeCo那套「停繳門檻對齊使用者設定提領年齡」的修正，因為DC停繳規則本身就是「離職即停」，不是年齡制

## 這份issue怎麼來的
這是在[question-193](question-193.md)（原本同時記錄企業型DC、iDeCo、NISA三者在退休當年提繳計算的落差）查證真實世界制度後，確認企業型DC這部分是真正的bug，從question-193拆出來獨立成issue。iDeCo部分沿用193這個編號、升級成[issue-193](issue-193.md)；NISA部分確認不是bug，記錄在[WONTFIX-34](../wontfix/WONTFIX-34-nisa-contribution-not-zeroed-on-retirement-confirmed-correct.md)。

這個落差最早是在191（退休當年稅務分段計算）落地後的一次回顧審查裡發現的——審查當時查到`TaxAdvantagedAccountProjector.processAccountsForYear`完全沒有跟進191新增的`workingMonths`/`retirementMonth`拆分概念，還在用舊的`context.isRetired()`整年二元判斷。

這份文件後來經歷了一輪定案：iDeCo那邊（issue-193）經過多輪討論，確定了「退休年按退休月拆分提繳」的具體技術方案，並額外發現兩個連帶問題（停繳門檻要不要對齊使用者設定的提領年齡、解鎖默認值卡在60歲的bug）。這份issue逐條比對193的結論，確認哪些可以直接沿用、哪些不適用於DC，不是把193整份複製過來。

## 相關檔案
**以下行號是193實作落地後的最新位置（193新增了`idecoContributionCeilingAge`/`idecoContributionMonths`兩組變數，所有行號都比193實作前往後偏移，這裡已重新核對過）：**
- [TaxAdvantagedAccountProjector.java:31-39,66-76,130-140,179-215,218-221,262-267](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)（DC/iDeCo提繳計算、停繳判斷、資產累積跟解鎖，DC/iDeCo兩條路徑在這個檔案裡大量並排寫法一致，問題核心所在；iDeCo這條路徑193已經改好，可以直接對照著寫DC那一半；第262-267行是雇主提繳/員工matching拆分計算，這段**沒有**iDeCo對照組可以照抄，是DC專屬、容易被漏改的地方，見下方「逐條比對」第6點）
- [SimulationFund.java:95-118](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)（`calculateContribution(boolean isInitialYear, int monthsToAdd)`——純函數，[issue-193](issue-193.md)已確認可以安全借用它的按月折算分支處理「退休年但非起算年」的情況，DC可以照搬同一個方法）
- [ActiveTaxCalculationEngine.java:61-113](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)（`calculate`方法，查證確認`annualDcMatchingContribution`會被當成`dcMatchingDeduction`直接用來算當年所得稅的扣除額——這代表下面第5、6點的缺口不只影響顯示的提繳金額，會直接影響退休年的稅額計算結果）
- [FinanceHubConfig.java:210-212](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java)（`dcUnlockAge`欄位官方註解確認是「DC解鎖請領年齡」的設計，不是停繳年齡——下面「逐條比對」第2點說明這個欄位被現狀的停繳判斷式誤用）
- [AssetSimulationEngineImpl.java:104-107](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)（`idecoStartOverride`查證確認可以讓`taxAdvantagedWithdrawalAge`被設定在60-75歲之間、不保證跟`targetRetirementAge`一致——下面「逐條比對」第5點說明這個獨立設定的請領年齡會造成的新缺口）
- [InvestmentContributions.java:20-21](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/InvestmentContributions.java)（`getTotalEmployeeInvestments()`，查證確認`annualDcMatchingContribution`會被算進現金流扣款，不只影響稅額，也影響現金流模擬——同樣是下面第5點缺口的下游影響範圍）
- [YearlySimulationPipeline.java:58-61,97-107,156-176](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（`isRetirementYear`、`retirementMonth`、`currentMonth`、`monthsToAdd`這幾個變數都已經算好；第156-176行是193新增的`idecoContributionCeilingAge`/`idecoContributionMonths`計算跟`processAccountsForYear`呼叫，DC要新增的`dcContributionMonths`應該寫在同一個區塊裡，不需要DC/iDeCo各自重算`isRetirementYear`/`retirementMonth`）
- [issue-193](issue-193.md)（iDeCo對應問題的完整技術方案，這份issue逐條比對它的四個修正部分，分別判斷DC的適用性——**這份issue寫作時引用的是193實作前的設計草稿，193正式落地後實際的程式碼寫法跟文件描述的簡化公式不完全一樣，下面「建議修正方向」已經改成對照193實際程式碼的版本**）
- [Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md](../planned/Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md)（DC/iDeCo解鎖年齡為什麼故意寫死綁在一起，這份issue的修正不觸碰這個耦合）

## 現狀
查證[TaxAdvantagedAccountProjector.java:130-140](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)：
```java
boolean dcAllowed = true;
...
if (isRetired || age >= dcUnlockAge) {
    remainingMonthsDcContribution = 0L;
    dcAllowed = false;
}
```
`isRetired`（也就是`context.isRetired()`，退休當年全年都是`true`，不管退休月是幾月）直接把這一整年的DC提繳金額歸零——這個判斷完全不知道退休月是幾月，不管退休月是1月還是12月，整年提繳都是0。

**情境：退休當年，退休月是6月（1-6月在職、7-12月退休）**。按191已經落地的分段計算邏輯，在職段的所得稅/住民稅/社會保險會正確只算1-6月這段真實發生的部分。但DC提繳不會這樣處理——`isRetired`這個旗標本身是191之前就存在的整年二元判斷，191沒有改這個旗標本身，只是在它之外新增了`workingMonths`/`retirementMonth`這套更精確的月份拆分，但`TaxAdvantagedAccountProjector`完全沒有接上這套新機制，導致1-6月這段「真實還在職、理論上還能提繳DC」的月份，提繳金額被直接算成0。

## 查證確認：真實世界企業型DC的提繳規則，跟iDeCo的關鍵差異在哪裡
企業型DC（企業型確定拠出年金）的加入資格綁定企業員工身分（連動厚生年金被保險人資格）。員工離職時，公司掛金跟員工自願的マッチング拠出（Matching Contribution）只算到資格喪失日前一個月（或依企業規約算到離職當月）——**離職不會溯及既往把同年度前幾個月已經提繳的金額歸零，但離職當下就立刻停繳，不像iDeCo那樣有「未滿60歲可以繼續自費提繳」這種制度彈性**。這是DC跟iDeCo最根本的差異：DC的提繳資格直接綁定「現在是不是這家公司的員工」，不是像iDeCo那樣看年齡。退休月是6月的話，1-6月公司跟員工的提繳是已經實際發生的既成事實，而且這段期間的マッチング拠出全額屬於可申報的「小規模企業共済等掛金控除」。

## 為何是問題
現狀`remainingMonthsDcContribution = 0L`這個整年歸零的寫法，會把1-6月這段真實發生的提繳跟對應的稅前扣除額整段抹除。使用者退休當年如果1-6月本來有在提繳DC（公司掛金、員工自己的マッチング拠出），這段期間真實發生的資產增加跟稅前扣除額會被模擬忽略，讓模擬算出來的稅負比真實情況更高、DC資產累積比真實情況更少，現金流模擬結果偏向悲觀，不符合真實世界的既成事實。

這個性質跟191已經修好的房貸控除遺失（退休當年`applyWorkingTaxes`整段被跳過，連帶讓房貸控除額連建立記錄的機會都沒有）是同一種「忘記拆分在職段」的問題，只是發生在`TaxAdvantagedAccountProjector`這個191範圍之外的平行模組。

## 逐條比對issue-193的四個修正部分，確認DC的適用性

### 1. 退休月按月拆分——完全適用，照著193已經落地的實際程式碼寫法套用，不是文件草稿裡的簡化公式
193落地後，`YearlySimulationPipeline.java:163-172`算出`idecoContributionMonths`的公式是：
```java
int idecoContributionMonths = monthsToAdd;
if (isRetirementYear) {
    if (retirementMonth == null) {
        idecoContributionMonths = 0;
    } else if (age == initialAge) {
        idecoContributionMonths = Math.max(0, retirementMonth - currentMonth + 1);
    } else {
        idecoContributionMonths = retirementMonth;
    }
}
```
DC要做的事情完全平行：在同一個區塊裡新增一組`dcContributionMonths`，算法跟上面一字不差（只是變數名稱換成DC），`isRetirementYear`/`retirementMonth`/`currentMonth`這三個變數已經在同一個方法裡算好，不需要重算，也不需要跟iDeCo共用同一個變數（iDeCo跟DC的`idecoContributionCeilingAge`/停繳時機不同，有可能某一年iDeCo已經停繳但DC還沒退休，兩者月份數不保證一樣，要各自獨立算）。

但`TaxAdvantagedAccountProjector`內部怎麼消費這個月份數，不是直接丟給`calculateContribution`就結束——查證[TaxAdvantagedAccountProjector.java:66-67](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)確認，iDeCo這條路徑193實際落地的寫法多了一層中間變數：
```java
boolean idecoProrate = (age == initialAge) || isRetirementYear;
int idecoMonthsForCalc = isRetirementYear ? idecoContributionMonths : monthsToAdd;
```
`idecoProrate`決定要不要借用`calculateContribution`的按月折算分支（起算年或退休年才要折算，其他年份走整年固定值）；`idecoMonthsForCalc`決定折算時要用哪個月份數（退休年用`idecoContributionMonths`，其他年份用`monthsToAdd`）——DC要新增一組平行的`dcProrate`/`dcMonthsForCalc`，邏輯跟這兩行完全一致，只是全部換成DC的變數。然後在[TaxAdvantagedAccountProjector.java:73-75](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)（提繳計算）、[TaxAdvantagedAccountProjector.java:182](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)（資產累積）這兩處，把`fund.calculateContribution(age == initialAge, monthsToAdd)`換成`fund.calculateContribution(dcProrate, dcMonthsForCalc)`，跟iDeCo現在的寫法（第71行、第201行）對照著改即可。

**跟194原本「方向A」的差異**：194原本的方向A只說「接上191的`workingMonths`概念」，沒有具體公式；這份文件原本引用的是193實作前的設計草稿裡的簡化公式（單層`monthsForCalc`），193正式落地時多加了`idecoProrate`/`idecoMonthsForCalc`這層中間變數，上面已經改成對照193實際程式碼的版本，不是文件草稿裡的簡化版。

### 2. 停繳年齡門檻對齊使用者設定——不適用（DC不需要193那套「對齊使用者提領年齡」的修正），但`isRetired`判斷本身有兩處需要訂正，否則194第1點的按月拆分實際上發揮不了作用
193這部分的前提是「iDeCo制度上，使用者未滿60歲可以持續提繳到60歲，所以停繳門檻不該卡死在`idecoUnlockAge`，要看使用者自己設定的提領年齡」。但DC的規則完全不是這樣——上面「查證確認」一節已經說明，DC的提繳資格直接綁定「現在還是不是這家公司員工」，離職當下就停，沒有「未滿某個年齡可以自費繼續提繳」這種制度彈性。這代表DC不需要193那套「對齊使用者設定的提領年齡」的修正方向——這個結論本身沒有錯。

**但這份issue原本的版本在這裡漏了兩處實際的程式碼問題，不處理的話194第1點的按月拆分機制實質上不會真的生效**：

**(a) `isRetired`在退休當年整年都是`true`，沒有比照193排除`isRetirementYear`，退休月之前的拆分月份一樣會被歸零**：查證[YearlySimulationPipeline.java:64](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認`context.setRetired(age >= targetRetirementAge)`，退休當年（`age == targetRetirementAge`）從年初開始`isRetired`就已經是`true`。現狀`if (isRetired || age >= dcUnlockAge)`（[TaxAdvantagedAccountProjector.java:137](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)）如果不改，退休當年單靠`isRetired`這一半就會把`remainingMonthsDcContribution`直接歸零——跟上面第1點新增的`dcContributionMonths`/`dcProrate`/`dcMonthsForCalc`完全無關，這段按月拆分出來的數字會在這一行被整段蓋掉。193解決這個問題的方式是把iDeCo對應的判斷式改成`(isRetired && !isRetirementYear)`（[TaxAdvantagedAccountProjector.java:133](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)），DC這裡要做同樣的事：`if ((isRetired && !isRetirementYear) || ...)`。

**(b) `dcUnlockAge`查證確認是「DC解鎖請領年齡」的設計，不是停繳年齡，被現狀的判斷式誤用，會讓60歲以上退休的使用者從60歲起就被提前停繳**：查證[FinanceHubConfig.java:210-212](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java)確認這個欄位的官方註解寫的是「The age at which Corporate DC is **unlocked**」——跟`idecoUnlockAge`原本的設計意圖完全對稱（iDeCo那邊同樣的誤用，193已經把它從停繳判斷式裡拿掉，改用`idecoContributionCeilingAge`）。現狀`age >= dcUnlockAge`（預設60歲）如果不拿掉，65歲退休（最常見的案例）的使用者，60-64歲在職期間、以及65歲退休當年1月到退休月之前，都會被這個條件直接判定停繳——194第1點新增的拆分機制，在這些年齡完全沒有機會被觸發到，因為`age >= dcUnlockAge`這個條件比`isRetirementYear`更早成立。

**這個問題的第一步修法——把現狀`if (isRetired || age >= dcUnlockAge)`裡的`age >= dcUnlockAge`整個拿掉**：因為DC離職即停、沒有任何「未到某個年齡前都不能停」或「未到某個年齡前都不能繼續提繳」的制度彈性，`isRetired`（搭配`!isRetirementYear`排除退休當年整年歸零）是判斷「有沒有退休」這件事唯一需要的依據，`dcUnlockAge`不需要疊加在這裡。

**但`if (isRetired && !isRetirementYear)`還不是這一行最終的樣子**——這個判斷式只處理了「有沒有退休」這一半，還漏了「有沒有已經請領」這一半，查證確認漏掉後會製造下面第5點描述的空提繳缺口（請領年齡可以單獨設定、可能早於退休年齡）。這一行**最終**要同時覆蓋這兩個各自獨立的終止條件，完整公式見下面第5點的`if ((isRetired && !isRetirementYear) || age > taxAdvantagedWithdrawalAge)`——第2點跟第5點說的是同一次修改，不是兩個互相獨立、可以分開做的步驟。

**查證確認拿掉`dcUnlockAge`不會製造「還在工作卻被提前當作已請領」的風險**：決定「哪一年把DC資產變現、清零、課退職所得稅」的是`dcUnlocked`這個獨立判斷（[TaxAdvantagedAccountProjector.java:219-220](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)，`age == taxAdvantagedWithdrawalAge`），用的是`taxAdvantagedWithdrawalAge`，跟停繳判斷式裡原本的`dcUnlockAge`是完全不同的兩個變數——拿掉`dcUnlockAge`，不會改變`dcUnlocked`觸發的時間點，「提領」這件事完全不受這次修改影響，只有「停繳」這件事的判斷依據會改變。

### 3. 解鎖默認值的連帶發現——不需要194做任何事，193落地時DC自動一起修正
查證[issue-193](issue-193.md)「連帶發現」一節，`resolveTaxAdvantagedWithdrawalAge`這個解析方法，`dcUnlockAge`/`idecoUnlockAge`兩個輸入在前端（[system-config-panel.tsx:166-188](file://hub-ui/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx)）是完全平行的欄位，`TaxAdvantagedAccountProjector`裡決定「DC資產何時停止累積、何時解鎖變現」（[TaxAdvantagedAccountProjector.java:179,219-220](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)）用的也是DC/iDeCo共用的同一個`taxAdvantagedWithdrawalAge`。**這部分已經查證確認193真的落地了**——`resolveTaxAdvantagedWithdrawalAge`現在新增了`targetRetirementAge`參數（[SimulationParameterResolver.java:134-144](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java)），沒設定提領年齡時真的改回傳`targetRetirementAge`（封頂75歲、下限是法定解鎖年齡），不再卡死在60歲；`AssetSimulationEngineImpl`（第99-110行）也確認把`resolveRetirementAge`的呼叫順序調到`resolveTaxAdvantagedWithdrawalAge`前面，讓後者能拿到前者算出來的值。DC不需要194做任何事，已經自動跟著受益，這份issue不需要重複處理。

### 4. 各自獨立解鎖、Plan-09的耦合限制——適用，是同一個限制，這份issue一樣不能碰
查證[Plan-09](../planned/Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md)確認，`idecoUnlocked = dcUnlocked`這個寫死的綁定是故意設計成這樣的——DC/iDeCo同一年請領時，退職所得控除依法要合算，不能各自獨立申報。這份issue的按月拆分修正（上面第1點）只動提繳計算，完全不觸碰解鎖判斷，不會引入Plan-09警告的重複扣除風險。

### 5. 請領年齡可以單獨設定、且可能早於退休年齡——如果停繳判斷式只改成`(isRetired && !isRetirementYear)`，請領之後、退休之前這幾年會出現「matching的現金跟稅務扣除持續發生，但錢沒有被存進DC餘額」的空提繳

上面第2點把停繳判斷式改成`(isRetired && !isRetirementYear)`，只處理了「DC何時該停繳」這個問題的一半——這個條件只看「有沒有退休」，但`taxAdvantagedWithdrawalAge`（DC/iDeCo共用的請領年齡）是可以獨立設定的，而且查證[AssetSimulationEngineImpl.java:104-107](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)確認，`overrides.idecoStartAgeOverride()`（沙盒what-if覆寫、optimizer也會用到同一個欄位）可以讓這個值被設定在60-75歲之間、**不保證跟`targetRetirementAge`一致，可以早於退休年齡**。

**具體情境**：使用者設定請領年齡60歲、退休年齡65歲。60歲那年，DC依法解鎖變現、資產清零並課一次退職所得稅（`dcUnlocked`在`age == taxAdvantagedWithdrawalAge`時觸發）。但使用者**還沒退休**，61-64歲依然在職、依然每個月被扣款matching——如果停繳判斷式只看`(isRetired && !isRetirementYear)`，61-64歲`isRetired`是`false`（還沒到65歲），這個條件整體是`false`，`dcAllowed`會維持`true`，`remainingMonthsDcContribution`會被算成全年金額，不會被歸零。

**問題出在資產累積那一段跟提繳計算那一段，現在用的是兩個不同的年齡界線，彼此不同步**：資產累積的既有判斷式是`if (age <= taxAdvantagedWithdrawalAge)`（[TaxAdvantagedAccountProjector.java:179](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)）——61-64歲（`age > 60`）這個條件已經不成立，提繳金額不會被真的存進`newDcBalance`。但第262-267行算`annualEmployerContribution`/`annualDcMatching`這段，完全不受`age <= taxAdvantagedWithdrawalAge`這個限制（這段程式碼在`if (age >= initialAge)`大區塊外面，不受資產累積那段的`if`管轄），所以`annualDcMatching`依然會算出一個非零值，一路往下傳：查證[ActiveTaxCalculationEngine.java:63,112](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認它會被當成`dcMatchingDeduction`去扣稅；查證[InvestmentContributions.java:20-21](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/InvestmentContributions.java)的`getTotalEmployeeInvestments()`確認它也會被算進現金流扣款。這代表61-64歲這幾年，使用者的現金會被扣走matching的金額、稅前扣除額也會被認列，但這筆錢實際上**憑空消失**，沒有進任何資產餘額——因為60歲那年DC帳戶已經被清零結算過，61-64歲的提繳不會有任何帳戶可以接住它。

**修法**：停繳判斷式要同時覆蓋「已經退休」跟「已經請領」這兩個各自獨立的終止條件，不能只看前者。改成`if ((isRetired && !isRetirementYear) || age > taxAdvantagedWithdrawalAge)`——`age > taxAdvantagedWithdrawalAge`（嚴格大於，不是`>=`）這個新增條件，確保請領那一年（`age == taxAdvantagedWithdrawalAge`）依然可以正常提繳（不會被這個新條件擋住），**請領之後的年份**才真正停止。這個嚴格大於的細節很關鍵：如果寫成`age >= taxAdvantagedWithdrawalAge`，請領年齡剛好等於退休年齡的案例（194最常見、最優先要修的情境）會在退休當年就被這個條件提前擋住，194第1點的按月拆分機制就完全派不上用場。

**「請領那一年依然可以正常提繳」不等於「請領那一年一定按退休月拆分」，這是兩件不同的事，不要混為一談**：真正決定要不要按月拆分的，是上面第1點新增的`dcProrate`（跟現在iDeCo的`idecoProrate`邏輯一致——查證[TaxAdvantagedAccountProjector.java:66](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)確認`idecoProrate = (age == initialAge) || isRetirementYear`，只看「是不是模擬起算年」或「是不是退休當年」，跟請領年齡完全無關）。這代表：
- 如果請領年齡**早於**退休年齡（例如請領60、退休65），請領那一年（60歲）既不是起算年也不是退休年，`dcProrate`是`false`，這一年走的是整年12個月的固定值，不是按退休月折算——因為這一年使用者還在職、還沒退休，沒有任何理由要折算月份。
- 按退休月折算只會發生在`isRetirementYear`那一年（不管請領年齡設定在幾歲）——如果請領年齡剛好等於退休年齡（194最常見的情境），這一年同時是「請領年」也是「退休年」，才會同時具備「不被新條件擋住」跟「按月折算」兩個效果；如果請領年齡跟退休年齡不一樣，這兩件事會分別發生在不同的年份。

手算驗證四個邊界情境，確認停繳判斷式的修法正確：
- 請領60、退休65，`age=60`（請領當年，同時人還在職，不是退休年）：`isRetired=false`，第一項`false`；`60 > 60`為`false`；整體`false`——提繳正常算（走整年12個月，`dcProrate`為`false`，不是按月拆分），符合請領當年依然在職、理應正常提繳的事實。
- 請領60、退休65，`age=61~64`（請領後、退休前）：`isRetired=false`，第一項`false`；`61~64 > 60`為`true`；整體`true`——提繳正確停止，這正是上面要修的缺口。
- 請領65、退休65（跟194第1點最常見的案例對齊），`age=65`（退休當年，同時也是請領年）：`isRetired=true`，`isRetirementYear=true`，第一項`true && false = false`；`65 > 65`為`false`；整體`false`——退休當年可以按退休月拆分（`dcProrate`為`true`，因為`isRetirementYear`成立），不會被誤擋。
- 請領65、退休65，`age=66`（退休後）：`isRetired=true`，`isRetirementYear=false`，第一項`true`；整體`true`——正確停止。

**解鎖判斷（`dcUnlocked`）本身不受這次修正影響，不需要改**：`age == taxAdvantagedWithdrawalAge`這個既有判斷式，決定的是「哪一年把DC資產變現、課退職所得稅」，跟停繳判斷式是完全獨立的兩個邏輯——這次只修停繳判斷式，解鎖時間點維持現狀。

### 6. 雇主提繳/員工matching拆分計算——DC專屬的新缺口，iDeCo沒有對照組，上面第1點的「照著iDeCo改」清單沒有涵蓋這一段，必須額外處理
查證[TaxAdvantagedAccountProjector.java:262-267](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)：
```java
long annualEmployerContribution = context.getDcMonthlyEmployerContribution() * monthsToAdd;
if (age > initialAge) {
    annualEmployerContribution = context.getDcMonthlyEmployerContribution() * 12;
}

long annualDcMatching = Math.max(0, remainingMonthsDcContribution - annualEmployerContribution);
```
這段邏輯把DC的總提繳金額（`remainingMonthsDcContribution`）扣掉雇主提繳（`annualEmployerContribution`），算出員工自己加碼matching的部分（`annualDcMatching`）——**這整段只存在DC這條路徑，iDeCo完全沒有對應的程式碼**，上面第1、2、5點「照著iDeCo現在的寫法改DC」的建議完全沒有提到這裡，如果只照著那幾點改完就結案，這段會被漏掉。

**漏掉的後果**：`annualEmployerContribution`這個變數現在只有「起算年用`monthsToAdd`、其他年份固定乘12」兩種算法（第262-265行），**完全沒有讀上面第1點新增的`dcContributionMonths`**。如果退休年只改了`remainingMonthsDcContribution`（分子，提繳計算那段）讓它正確反映退休月之前的拆分金額，但`annualEmployerContribution`（被減去的雇主提繳）沒有同步改成用`dcContributionMonths`去算，退休年當年就會出現分子跟減項的月份基準不一致：分子是「只算退休月之前」的小數字，減項卻還是「整年」的大數字，相減後`annualDcMatching`會被不合理地壓低、甚至被第267行的`Math.max(0, ...)`直接夾到0。

**這不是顯示問題，會直接影響退休年的稅額計算結果**：查證[ActiveTaxCalculationEngine.java:63,112-113](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)確認，`annualDcMatchingContribution`會被直接當成`dcMatchingDeduction`拿去算當年的所得稅扣除額（`retirementDeduction = idecoDeduction + dcMatchingDeduction`）。如果這個值在退休年被錯誤壓低，退休年在職段算出來的稅前扣除額會比真實情況更少，稅負會被高估——跟194「為何是問題」這一節描述的核心症狀（稅負比真實情況更高）是同一種性質的bug，只是藏在提繳計算之後、資產累積之前都沒有觸碰到的這一段，容易在只核對「提繳」「資產累積」這兩段都改完就結案時被漏掉。

## 建議修正方向
不是多方案並列，193這輪討論已經把技術路徑定案，DC直接沿用：

1. `TaxAdvantagedAccountProjector`接上191已經確立的`workingMonths`/`retirementMonth`拆分，公式跟上面「逐條比對」第1點完全一致。
2. **`isRetirementYear`這個參數已經存在，不需要再新增**——查證[TaxAdvantagedAccountProjector.java:31-39](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)確認，193實作時已經把`isRetirementYear`新增進`processAccountsForYear`的方法簽章（第38行），DC可以直接讀這個現成的參數，不需要重複新增。`processAccountsForYear`真正還缺的，是一個**DC專屬的月份參數**（類似iDeCo的`idecoContributionMonths`，但要給DC用，例如`dcContributionMonths`）——這個參數現在完全不存在，實作時要新增；對應的，呼叫端`YearlySimulationPipeline.processSimulationYear`（第156-176行，193新增的這個區塊）裡要跟著新增一組`dcContributionMonths`的計算，並在呼叫`processAccountsForYear`時多傳這一個參數進去。
3. **停繳判斷式本身要修正，不是只接上拆分月份就結束**——查證上面「逐條比對」第2點確認，`if (isRetired || age >= dcUnlockAge)`（[TaxAdvantagedAccountProjector.java:137](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)）要改成`if ((isRetired && !isRetirementYear) || age > taxAdvantagedWithdrawalAge)`——**完全拿掉`age >= dcUnlockAge`這個條件**（`dcUnlockAge`是解鎖請領年齡、不是停繳年齡，繼續留著會讓60歲以上退休的使用者從60歲起就被提前擋住），**但不是單純拿掉就結束，要換成`age > taxAdvantagedWithdrawalAge`這個新條件**——理由跟具體公式見下方「逐條比對」新增的第5點（請領年齡可以單獨設定、且可能早於退休年齡，這個新條件是處理這種情況必要的補強，不是可省略的細節）。拿掉`age >= dcUnlockAge`後，`dcUnlockAge`這個參數在`processAccountsForYear`的簽章裡會變成不再被這個方法使用——要不要連同簽章一起拿掉這個參數，還是保留參數但方法內部不再讀它，留給實作時決定（拿掉簽章牽動呼叫端`YearlySimulationPipeline`/`AssetSimulationEngineImpl`，保留簽章不拿掉則是留一個目前沒用的參數，兩種做法都不影響正確性，是風格選擇）。
4. **`dcAllowed`旗標跟DC餘額投資成長計算之間的互動細節**（[TaxAdvantagedAccountProjector.java:179-196](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)）：退休當年在職月份算出來的提繳金額，要怎麼跟這段既有的`if (age <= taxAdvantagedWithdrawalAge)`資產累積邏輯銜接——這部分193的iDeCo版本用的是同一套`if (age <= taxAdvantagedWithdrawalAge)`區塊（第198-215行），DC（第179-196行）結構完全平行，可以用同樣的方式銜接，不需要另外設計。
5. **`age > taxAdvantagedWithdrawalAge`這個條件必須跟上面第3點的`(isRetired && !isRetirementYear)`同一次合併進停繳判斷式**——不是獨立的另一次修改，兩者要寫在同一個`if`裡：`if ((isRetired && !isRetirementYear) || age > taxAdvantagedWithdrawalAge)`。詳細理由、四個邊界情境的驗證，見上面「逐條比對」第5點。
6. **`annualEmployerContribution`（[TaxAdvantagedAccountProjector.java:262-265](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)）必須同步改用`dcContributionMonths`，不能維持現狀的`monthsToAdd`/固定乘12**：
```java
// 現行：
long annualEmployerContribution = context.getDcMonthlyEmployerContribution() * monthsToAdd;
if (age > initialAge) {
    annualEmployerContribution = context.getDcMonthlyEmployerContribution() * 12;
}

// 改成（跟上面第2點新增的 dcContributionMonths、dcMonthsForCalc 保持同一個月份基準）：
long annualEmployerContribution = context.getDcMonthlyEmployerContribution() * dcMonthsForCalc;
```
這樣退休年`remainingMonthsDcContribution`（分子，已經按退休月拆分）跟`annualEmployerContribution`（減項）會用同一個月份基準，`annualDcMatching`才不會被不合理地壓低或夾到0。這一步跟上面第1、2、5點的改動必須在同一次實作裡一起做，不是獨立於按月拆分之外的另一個問題——漏了這一步，上面幾點做完也無法讓退休年的稅額計算真正正確。

## 這份issue沒有解決的部分
- 193已經落地，`processAccountsForYear`現在的簽章（[TaxAdvantagedAccountProjector.java:31-39](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)）已經是193改完後的最終版本，不會再被193動第二次——194要做的是在這個現成的簽章基礎上，額外新增一個DC專屬的月份參數，不是跟193搶同一次修改視窗，這份issue現在可以獨立排入時程，不需要特別等193或跟193合併實作。
- 企業型DC的「公司提繳+員工自己加碼matching」能不能同時並存、「公司提繳但員工不加碼」能不能單獨關掉員工那一半——查證確認現狀完全不支援這兩種情境（`DefinedContributionContextBuilder`裡`monthlyDcMatchingContribution`這個使用者設定值，在已有真實DC帳戶快照時完全不會被讀取，整段邏輯是互斥的if/else if，不是可以疊加的設計），這是一個獨立於「退休當年提繳歸零」之外的缺口，已經寫成獨立的[Plan-31-dc-employer-employee-matching-contribution-not-separable.md](../planned/Plan-31-dc-employer-employee-matching-contribution-not-separable.md)，不屬於這份issue的範圍。


---

## 修正報告書（2026-11-25 實作完成）

### 1. 實作摘要與設計對齊
本次實作完全依照本份文件「逐條比對」與「建議修正方向」定案規格落地：
- **退休當年按月拆分（沿用 193 模式）**：於 `YearlySimulationPipeline` 算出 `dcContributionMonths`，並於 `TaxAdvantagedAccountProjector` 透過 `dcProrate` / `dcMonthsForCalc` 同步套用於 DC 提繳計算與資產累積。
- **停繳判斷式修正（移除誤用的 `dcUnlockAge` 並補齊請領早於退休防呆）**：將 `if (isRetired || age >= dcUnlockAge)` 改為 `if ((isRetired && !isRetirementYear) || age > taxAdvantagedWithdrawalAge)`，使 60 歲以上在職／退休當年不再被 `dcUnlockAge` 誤殺，同時防止請領早於退休時在請領後產生空提繳。
- **雇主提繳與員工 Matching 基準對齊**：將 `annualEmployerContribution` 改為 `context.getDcMonthlyEmployerContribution() * dcMonthsForCalc`，確保退休年 `annualDcMatching` 與總提繳使用相同的月份基準。
- **簽章精簡與 Plan-09 耦合維持**：將 `processAccountsForYear` 簽章中不再使用的 `int dcUnlockAge` 替換為 `int dcContributionMonths`（維持 8 參數）；外層 `YearlySimulationPipeline.processSimulationYear` 13 參數簽名不變，且 `boolean idecoUnlocked = dcUnlocked;` 與 `age <= taxAdvantagedWithdrawalAge` 完全未更動。

---

### 2. 修改檔案清單與變更明細

#### 產品程式碼（2 個檔案）
1. **[YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L174-L187)**
   - 新增 `dcContributionMonths` 計算：非退休年為 `monthsToAdd`；退休年若 `retirementMonth == null` 為 `0`，`age == initialAge` 時為 `Math.max(0, retirementMonth - currentMonth + 1)`，非首年為 `retirementMonth`。
   - 呼叫 `taxAdvantagedAccountProjector.processAccountsForYear(...)` 時以 `dcContributionMonths` 取代原 `dcUnlockAge`。

2. **[TaxAdvantagedAccountProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L31-L270)**
   - **[L31–L39](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L31-L39)**：`processAccountsForYear` 簽章將 `int dcUnlockAge` 替換為 `int dcContributionMonths`。
   - **[L73–L79](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L73-L79) 與 [L185](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L185)**：計算 `dcProrate = (age == initialAge) || isRetirementYear` 與 `dcMonthsForCalc = isRetirementYear ? dcContributionMonths : monthsToAdd`，將兩處 `fund.calculateContribution(...)` 改為傳入 `(dcProrate, dcMonthsForCalc)`。
   - **[L140–L143](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L140-L143)**：停繳判斷改為 `if ((isRetired && !isRetirementYear) || age > taxAdvantagedWithdrawalAge)`。
   - **[L265–L267](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L265-L267)**：`annualEmployerContribution` 改為 `context.getDcMonthlyEmployerContribution() * dcMonthsForCalc`。

#### 測試程式碼（修改 1 個、新增 1 個）
1. **[YearlySimulationPipelineIdecoLifecycleTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java#L85-L101)**
   - 同步更新內部 `RecordingProjector.processAccountsForYear` override 的第 3 個參數名稱為 `dcContributionMonths`。
2. **[YearlySimulationPipelineDcLifecycleTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineDcLifecycleTest.java)**（新增）
   - 以真實 `TaxAdvantagedAccountProjector` + `InvestmentReturnCalculator` + `ActiveTaxCalculationEngine` 驗證 4 個測試案例：
     - `standardRetirementAt65_contributesThrough64_proratesTotalAndMatchingAt65_zeroAfter`（60～64 歲正常提繳、65 歲 6 月退休按 6 個月拆分總提繳與 Matching 扣除額並解鎖、66 歲歸零）
     - `initialYearIsRetirementYear_proratesRemainingWorkingMonthsForBothTotalAndMatching`（模擬首年即退休年交集，4 月起算、6 月退休按 3 個月拆分總提繳與 Matching）
     - `retirementYearWithMissingBirthDate_zeroesTotalAndMatching`（`BIRTH_MONTH` 模式下缺少 `birthDate` 時總提繳與 Matching 皆為 `0`）
     - `withdrawalEarlierThanRetirement_fullYearAtWithdrawalAge_zeroAfterWithdrawal`（60 歲請領、65 歲退休：60 歲當年正常滿額提繳並解鎖、61～64 歲總提繳與 Matching 皆為 `0`）

---

### 3. 驗證結果
- 既有測試與新增 `YearlySimulationPipelineDcLifecycleTest` 均已於本機執行通過。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                        10013  12.90G   4.73    20:44:00  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
index 7526d0f0..721c8525 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
@@ -31,7 +31,7 @@ public class TaxAdvantagedAccountProjector {
     public InvestmentContributions processAccountsForYear(
             SimulationContext context,
             int initialAge,
-            int dcUnlockAge,
+            int dcContributionMonths,
             int idecoContributionCeilingAge,
             int taxAdvantagedWithdrawalAge,
             int monthsToAdd,
@@ -70,9 +70,12 @@ public class TaxAdvantagedAccountProjector {
         for (SimulationFund fund : context.getIdecoFunds()) {
             remainingMonthsIdecoContribution += fund.calculateContribution(idecoProrate, idecoMonthsForCalc);
         }
+        boolean dcProrate = (age == initialAge) || isRetirementYear;
+        int dcMonthsForCalc = isRetirementYear ? dcContributionMonths : monthsToAdd;
+
         long remainingMonthsDcContribution = 0;
         for (SimulationFund fund : context.getDcFunds()) {
-            remainingMonthsDcContribution += fund.calculateContribution(age == initialAge, monthsToAdd);
+            remainingMonthsDcContribution += fund.calculateContribution(dcProrate, dcMonthsForCalc);
         }
 
         // ── NISA dual-track sub-limit enforcement ───────────────────────────────────
@@ -134,7 +137,7 @@ public class TaxAdvantagedAccountProjector {
             remainingMonthsIdecoContribution = 0L;
             idecoAllowed = false;
         }
-        if (isRetired || age >= dcUnlockAge) {
+        if ((isRetired && !isRetirementYear) || age > taxAdvantagedWithdrawalAge) {
             remainingMonthsDcContribution = 0L;
             dcAllowed = false;
         }
@@ -179,7 +182,7 @@ public class TaxAdvantagedAccountProjector {
             if (age <= taxAdvantagedWithdrawalAge) {
                 long newDcBalance = 0;
                 for (SimulationFund fund : context.getDcFunds()) {
-                    long intended = fund.calculateContribution(age == initialAge, monthsToAdd);
+                    long intended = fund.calculateContribution(dcProrate, dcMonthsForCalc);
                     long annualContribution = dcAllowed ? intended : 0L;
                     Double expectedRate = fund.getExpectedReturnRate();
                     double returnRate = expectedRate != null ? expectedRate : 0.0;
@@ -259,10 +262,7 @@ public class TaxAdvantagedAccountProjector {
             }
         }
 
-        long annualEmployerContribution = context.getDcMonthlyEmployerContribution() * monthsToAdd;
-        if (age > initialAge) {
-            annualEmployerContribution = context.getDcMonthlyEmployerContribution() * 12;
-        }
+        long annualEmployerContribution = context.getDcMonthlyEmployerContribution() * dcMonthsForCalc;
 
         long annualDcMatching = Math.max(0, remainingMonthsDcContribution - annualEmployerContribution);
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index 899103c9..5ef0a2af 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -171,8 +171,19 @@ public class YearlySimulationPipeline {
             }
         }
 
+        int dcContributionMonths = monthsToAdd;
+        if (isRetirementYear) {
+            if (retirementMonth == null) {
+                dcContributionMonths = 0;
+            } else if (age == initialAge) {
+                dcContributionMonths = Math.max(0, retirementMonth - currentMonth + 1);
+            } else {
+                dcContributionMonths = retirementMonth;
+            }
+        }
+
         InvestmentContributions contributions = taxAdvantagedAccountProjector.processAccountsForYear(
-                context, initialAge, dcUnlockAge, idecoContributionCeilingAge, taxAdvantagedWithdrawalAge,
+                context, initialAge, dcContributionMonths, idecoContributionCeilingAge, taxAdvantagedWithdrawalAge,
                 monthsToAdd, isRetirementYear, idecoContributionMonths);
 
         YearlySimulationState state = new YearlySimulationState();
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
index df929ce9..2d291841 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
@@ -86,14 +86,14 @@ class YearlySimulationPipelineIdecoLifecycleTest {
         public InvestmentContributions processAccountsForYear(
                 SimulationContext context,
                 int initialAge,
-                int dcUnlockAge,
+                int dcContributionMonths,
                 int idecoContributionCeilingAge,
                 int taxAdvantagedWithdrawalAge,
                 int monthsToAdd,
                 boolean isRetirementYear,
                 int idecoContributionMonths) {
             InvestmentContributions result = super.processAccountsForYear(
-                    context, initialAge, dcUnlockAge, idecoContributionCeilingAge,
+                    context, initialAge, dcContributionMonths, idecoContributionCeilingAge,
                     taxAdvantagedWithdrawalAge, monthsToAdd, isRetirementYear, idecoContributionMonths);
             contributionsByAge.put(context.getAge(), result);
             return result;
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                             10014  12.84G   4.37    20:44:36  
 