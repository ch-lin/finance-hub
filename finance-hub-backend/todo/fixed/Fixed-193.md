# [FIXED] 193：退休當年，iDeCo（個人型確定拠出年金）提繳被整年歸零，不會按退休月份拆分；停繳的年齡門檻也沒有對齊使用者自己設定的提領年齡，用的是另一個獨立的`idecoUnlockAge`常數——停繳年齡應該直接看使用者設定的提領年齡（但不超過65歲法定提繳上限），不需要另外發明新的「有效解鎖年齡」或「運用指図者」這類新概念

## 這份issue怎麼來的
這份文件原本是question-193，同時記錄企業型DC、iDeCo、NISA三者在退休當年提繳計算的落差。查證真實世界制度後，確認企業型DC是獨立的bug，拆到[issue-194](issue-194.md)；NISA確認不是bug，記錄在[WONTFIX-34](../wontfix/WONTFIX-34-nisa-contribution-not-zeroed-on-retirement-confirmed-correct.md)；iDeCo部分沿用193這個編號、升級成這份issue。

這個落差最早是在191（退休當年稅務分段計算）落地後的一次回顧審查裡發現的——審查當時查到`TaxAdvantagedAccountProjector.processAccountsForYear`完全沒有跟進191新增的`workingMonths`/`retirementMonth`拆分概念，還在用舊的`context.isRetired()`整年二元判斷。

這份issue的修正方向經過一次定案：退休（包含60歲前提早退休）就停止提繳是目前系統要採用的基準行為，不在這份issue裡額外建模「退休後自費繼續提繳」這種情境；那是[Plan-30](../planned/Plan-30-early-retirement-voluntary-ideco-contribution-for-dividend-tax-offset.md)要處理的獨立功能。停繳的年齡門檻，只要不超過65歲這個法定提繳上限，直接用使用者自己設定的提領年齡即可，不需要另外設計一套「有效解鎖年齡」跟「實際停繳年齡」兩套公式。

## 相關檔案
- [TaxAdvantagedAccountProjector.java:42-48,75-78,136-146,204-221,225-227](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)（iDeCo提繳計算跟停繳判斷，問題核心所在）
- [YearlySimulationPipeline.java:58-61,97-107,156-157](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（`isRetirementYear`、`retirementMonth`、`currentMonth`、`monthsToAdd`這幾個變數都在呼叫`processAccountsForYear`之前已經算好，呼叫端有現成資料可以直接利用，不需要重新發明）
- [SimulationFund.java:95-118](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)（`calculateContribution(boolean isInitialYear, int monthsToAdd)`——查證確認這個方法是純函數，只看傳入的兩個參數跟fund自己的`settingAmount`/`course`，不會去檢查「現在真的是不是模擬起算年」，所以可以放心在「退休年但不是起算年」的情況下，傳`isInitialYear=true`借用它的按月折算邏輯，不是只能在真正的起算年使用）
- [FinanceHubConfig.java:201-217](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java)（`idecoUnlockAge`、`dcUnlockAge`兩個欄位，預設都是60）
- [SimulationParameterResolver.java:106-143](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java)（`resolveTaxAdvantagedWithdrawalAge`——查證確認目前`taxAdvantagedWithdrawalAge`的解析結果一定落在60到75歲之間，下限是`max(dcUnlockAge, idecoUnlockAge)`，不會比60更早；這份文件全長144行，沒有145行以後的內容）
- [AssetSimulationEngineImpl.java:99-104](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)（確認`dcUnlockAge`、`idecoUnlockAge`、`taxAdvantagedWithdrawalAge`這三個數字，在模擬開始前就已經各自解析好，一路往下傳到`TaxAdvantagedAccountProjector`）
- [RetirementMonthResolver.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/RetirementMonthResolver.java)（191新增，退休月份的解析邏輯）
- [UserProfile.java:114-119](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java)（`taxAdvantagedWithdrawalAge`欄位本身，查證確認官方註解寫明「預設`null`，`null`會被解析成法定解鎖年齡」——這代表這個欄位的原始值跟`resolveTaxAdvantagedWithdrawalAge`解析後的值，在使用者沒有設定時代表完全不同的意圖，見下方「停繳年齡門檻」一節）
- [TaxAdvantagedAccountProjector.java:174,193,213-216](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)（`taxAdvantagedWithdrawalAge`這一個數字同時決定「資產還要不要繼續累積」（`age <= taxAdvantagedWithdrawalAge`）跟「哪一年解鎖變現」（`age == taxAdvantagedWithdrawalAge`），DC跟iDeCo共用同一個數字，第216行`idecoUnlocked = dcUnlocked`把兩者的解鎖狀態寫死綁在一起——見下方「連帶發現」一節）
- [Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md](../planned/Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md)（已存在的既有Plan，記錄了為什麼DC/iDeCo解鎖年齡現在故意寫死綁在一起——拆開會讓`ActiveTaxCalculationEngine.calculateRetirementTax`的退職所得控除被重複計算兩次，見下方「連帶發現」一節）

## 現狀
查證[TaxAdvantagedAccountProjector.java:136-142](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)：
```java
boolean idecoAllowed = true;
...
if (isRetired || age >= idecoUnlockAge) {
    remainingMonthsIdecoContribution = 0L;
    idecoAllowed = false;
}
```
`isRetired`（也就是`context.isRetired()`，退休當年全年都是`true`，不管退休月是幾月）直接把這一整年的iDeCo提繳金額歸零。更往前一步，查證[TaxAdvantagedAccountProjector.java:75-78](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)確認，在這個歸零判斷之前，提繳金額本身是這樣算出來的：
```java
long remainingMonthsIdecoContribution = 0;
for (SimulationFund fund : context.getIdecoFunds()) {
    remainingMonthsIdecoContribution += fund.calculateContribution(age == initialAge, monthsToAdd);
}
```
如果退休年剛好不是模擬起算的那一年（`age != initialAge`），`calculateContribution`的`isInitialYear`參數會是`false`——查證[SimulationFund.java:106-110](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)確認，這個方法在`isInitialYear=false`時，不管`monthsToAdd`傳進來是多少，一律直接回傳整年的`annualContribution`，完全沒有按月折算的機制。這代表退休年的iDeCo提繳金額，在被`isRetired`檢查歸零之前，其實已經算出一個「整年全額」的數字，而不是「退休月之前這幾個月」的數字——這個落差疊加`isRetired`的歸零，結果是同一個問題的兩層表現，修的時候要一起處理，不是只改其中一層。

**情境：退休當年，退休月是6月（1-6月在職、7-12月退休）**。按191已經落地的分段計算邏輯，在職段的所得稅/住民稅/社會保險會正確只算1-6月這段真實發生的部分。但iDeCo提繳不會這樣處理——不管退休月是幾月，這一整年的提繳金額都會被算成0。

## 查證確認：真實世界iDeCo的提繳規則
- **未滿60歲退休**（例如FIRE、提早退休）：只要是20歲以上未滿60歲的日本居民，依法是國民年金第1號被保險人、要繳國民年金保費（除非申請免除），這個身分下依然可以持續提繳iDeCo直到滿60歲，而且掛金上限會從上班族（第2號被保險人，每月1.2萬~2.3萬日圓）變成第1號被保險人的每月最高6.8萬日圓。
- **60歲以上未滿65歲**：完全退休（喪失厚生年金第2號被保險人資格）後，除非主動加入國民年金「任意加入被保險人」制度，否則60歲起就不能再提繳iDeCo。
- **滿65歲，或已經開始請領iDeCo老齢給付金**：不管是不是在職，一律停止提繳資格。

**這份issue的範圍**：上面第一點（未滿60歲退休仍可自費繼續提繳到60歲）屬於真實制度裡合法存在的情境，但這份issue刻意不處理——系統目前完全沒有自費提繳這個概念（現有的提繳金額都是跟著在職薪資走的扣款設定），要把這個情境建出來，牽涉的是一個全新的輸入通道（使用者要自己決定退休後要不要繼續扣、扣多少），不是現有程式碼改一兩行判斷式就能處理，獨立放到[Plan-30](../planned/Plan-30-early-retirement-voluntary-ideco-contribution-for-dividend-tax-offset.md)。這份issue要處理的，只是「退休那一年，退休月之前的提繳不該被整年抹除」跟「停繳年齡門檻要對齊使用者實際設定的提領年齡」這兩點。

## 為何是問題
**(a) 退休當年在職月份的提繳被整年歸零**：退休當年在職月份（例如1-6月）的提繳是已經實際發生的既成事實，不該被整年歸零抹除——這跟191已經修好的房貸控除遺失是同一種問題。

**(b) 停繳年齡門檻用的是一個跟使用者實際設定脫勾的獨立常數**：現狀用`idecoUnlockAge`（固定讀`FinanceHubConfig`裡的設定，預設60歲）當停繳門檻，但使用者如果在自己的檔案裡把`taxAdvantagedWithdrawalAge`（提領年齡）設定成比60歲更晚（例如65歲、68歲），現在的程式碼完全不理會這個設定，一律在60歲就停止提繳——即使這個人60-64歲都還在上班、依法可以繼續提繳到65歲，系統也會在60歲就把提繳歸零。這跟使用者自己設定的提領時間規劃脫節。

## 設計原則（已拍板，不是待討論的選項）
1. **退休就停止提繳，不建模部分任意加入延續**：不管是正常退休還是FIRE提早退休，退休當下就停止提繳，是這份issue採用的基準行為。退休後想繼續自費提繳到60歲這個真實存在的制度彈性，刻意不在這份issue裡處理。
2. **退休後想繼續提繳是另一個功能，由Plan-30處理**：[Plan-30](../planned/Plan-30-early-retirement-voluntary-ideco-contribution-for-dividend-tax-offset.md)是這個情境的獨立擴充功能，跟這份issue的修正不耦合，不互相等待。
3. **提領＝停繳，只要沒有違反65歲法定提繳上限即可**：停繳的年齡門檻，直接拿使用者自己設定的提領年齡（`taxAdvantagedWithdrawalAge`）來用，不需要另外發明一套「有效解鎖年齡」或「運用指図者」（停繳但還沒解鎖、資產純滾動的中間狀態）這類新名詞——這個中間狀態，查證[TaxAdvantagedAccountProjector.java:204-221](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)確認，既有的`if (age <= taxAdvantagedWithdrawalAge) { ... }`這個資產滾動分支本來就會處理（提繳金額即使是0，只要還沒到解鎖年齡，資產依然會按既有邏輯繼續複利滾動），不需要新增任何程式碼去專門處理這段空窗期。唯一需要的保護，是不能讓停繳年齡超過65歲這個法定硬上限（如果使用者把提領年齡設定得比65歲更晚，例如68歲，提繳仍然要在65歲停止，65-68歲這三年就是上面說的、既有程式碼已經會處理的資產純滾動期間）。

## 建議修正方向
這裡的改動範圍，侵限在iDeCo這一條路徑內部，不影響企業型DC（DC的對應問題獨立記錄在[issue-194](issue-194.md)）、不影響NISA（[WONTFIX-34](../wontfix/WONTFIX-34-nisa-contribution-not-zeroed-on-retirement-confirmed-correct.md)已確認NISA現狀是對的）。

### 1. 停繳年齡門檻：只有使用者真的自己設定過提領年齡時才拿來當門檻，沒設定時門檻就是單純的65歲法定上限——不能直接拿`taxAdvantagedWithdrawalAge`這個解析後的值來用
一個容易踩到的陷阱：`taxAdvantagedWithdrawalAge`這個既有參數，是`SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge`解析過的結果，查證[UserProfile.java:114-116](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java)的欄位註解確認「使用者沒有自己設定時，預設為`null`，`null`會被解析成法定解鎖年齡（60歲）」——這代表`taxAdvantagedWithdrawalAge`這個數字在「使用者真的填了68歲」跟「使用者什麼都沒填，被退回60歲」這兩種情況下，看起來是同一種「有一個數字」的狀態，但背後的意圖完全不同。

如果直接拿這個解析後的值去當停繳門檻（`Math.min(taxAdvantagedWithdrawalAge, 65)`），會錯誤地把「沒有特別設定、被動退回的60歲」當成「使用者主動要求60歲停繳」，導致一個完全沒去碰這個設定、計畫65歲正常退休的人，60-64歲這五年原本依法可以繼續提繳，卻被這個退回值誤判成60歲就該停。

正確的判斷依據是`context.getProfile().getTaxAdvantagedWithdrawalAge()`這個**原始、未經解析**的值（可能是`null`）——查證[YearlySimulationPipeline.java:98](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認`context.getProfile()`在這個類別裡本來就拿得到（`birthDate`也是這樣讀出來的），不需要新增任何方法參數：
```java
// 在 YearlySimulationPipeline 裡，呼叫 processAccountsForYear 之前先算好這個門檻
Integer userDeclaredWithdrawalAge = (context.getProfile() != null)
        ? context.getProfile().getTaxAdvantagedWithdrawalAge()
        : null;
int idecoContributionCeilingAge = (userDeclaredWithdrawalAge != null)
        ? Math.min(userDeclaredWithdrawalAge, 65)
        : 65;
```
這個`idecoContributionCeilingAge`取代原本`TaxAdvantagedAccountProjector.java:139`的`idecoUnlockAge`：
```java
// 現行（第139行）：
if (isRetired || age >= idecoUnlockAge) {

// 改成：
if ((isRetired && !isRetirementYear) || age >= idecoContributionCeilingAge) {
```
`idecoUnlockAge`這個參數在這個方法裡會因此不再被iDeCo這條路徑使用（DC那一行`if (isRetired || age >= dcUnlockAge)`維持不動，`dcUnlockAge`不受影響）。這份issue不處理模擬沙盒`SimulationOverrides.idecoStartAgeOverride()`這個臨時覆寫值要不要也納入這個判斷——查證[YearlySimulationPipeline.java:58-61](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認`processSimulationYear`本身沒有接收`SimulationOverrides`這個參數，這個what-if覆寫值是在呼叫端（`AssetSimulationEngineImpl`）就已經被解析進`taxAdvantagedWithdrawalAge`這個數字裡，跟使用者檔案的長期設定混在一起，這份issue討論的是檔案層級的真實設定，沙盒覆寫情境維持現狀不特別處理。

### 各種邊界年齡組合下，這個判斷式的實際行為（實作時需要對應的單元測試覆蓋以下情境）
| 情境 | `targetRetirementAge` | 使用者檔案`taxAdvantagedWithdrawalAge` | `idecoContributionCeilingAge` | 60-64歲在職期間iDeCo提繳 | 說明 |
|---|---|---|---|---|---|
| 1. 正常65歲退休、沒特別填提領年齡（最常見、最容易踩坑的情況） | 65 | `null` | 65（退回單純法定上限，不是60） | **正常提繳**，65歲當年才停 | 修正前的bug：如果誤拿`taxAdvantagedWithdrawalAge`解析值（60）當門檻，60-64歲會被錯誤停繳；修正後確認不會發生 |
| 2. 65歲退休、使用者自己設定68歲才要領 | 65 | 68 | `Math.min(68, 65) = 65` | 正常提繳到65歲停（65歲是法定硬上限，68歲的願望不能延後法定停繳） | 65-68歲這三年資產持續複利滾動但不再提繳，是既有`if (age <= taxAdvantagedWithdrawalAge)`資產滾動分支本來就會處理的狀態，這份issue不需要新增程式碼 |
| 3. 62歲提早退休，使用者自己設定62歲就要領 | 62 | 62 | `Math.min(62, 65) = 62` | 提繳到62歲、退休月之前按比例計算，62歲當年退休即停且立刻解鎖 | 退休年齡本身（`isRetired`）會先在62歲讓提繳停止，`idecoContributionCeilingAge=62`這個數字在這個情境下沒有機會被提前觸發（62歲還沒到之前，`age >= idecoContributionCeilingAge`不成立；62歲到了同時也是退休年） |
| 4. 45歲FIRE提早退休，沒有自費提繳（這份issue的基準行為） | 45 | `null`（或任何值，不影響這個情境） | 65（但不影響結果） | 45歲退休當年提繳按退休月拆分、45歲之後整年歸零 | `isRetired`在45歲就是true，`idecoContributionCeilingAge`不管算出多少都不會被先觸發到——40歲/45歲這類提早退休情境，停繳完全是被`isRetired`決定的，跟這個門檻無關，這份issue開頭查到的那個顧慮（提領年齡牽涉到法定解鎖年齡地板）屬於`resolveTaxAdvantagedWithdrawalAge`既有邏輯的範圍，不受這次改動影響 |
| 5. 68歲延後退休，沒特別填提領年齡 | 68 | `null` | 65 | 60-64歲正常提繳，**65-67歲提繳停止、資產複利滾動，68歲退休當年才真正解鎖請領** | 退休年齡（68）比法定提繳上限（65）晚，65歲先到、提繳先停，退休當下（68歲）才解鎖——這是「停繳」跟「解鎖請領」第一次在同一個情境裡分成兩個不同時間點，既有的資產滾動分支會自然處理這段空窗期 |
| 6. 60歲整，使用者自己設定60歲提領（最早可提領年齡） | 60 | 60 | `Math.min(60, 65) = 60` | 60歲當年提繳即停、立刻解鎖 | 跟情境3同樣邏輯，`resolveTaxAdvantagedWithdrawalAge`既有邏輯本身已經保證這個值不會比60更早（60是法定下限），這份issue不需要額外防呆 |

情境1是這次修正真正要解決的bug（原本的`Math.min(taxAdvantagedWithdrawalAge, 65)`寫法會讓這個最常見的情況被誤殺）；情境5證明「停繳」跟「解鎖請領」確實可能發生在不同年份，但這個空窗期不需要新概念或新程式碼，既有的`if (age <= taxAdvantagedWithdrawalAge)`資產滾動分支天然就會處理。

### 2. 退休年要保留退休月之前的真實提繳月份，不能被`isRetired`整年二元判斷蓋回0
上面第1點的`!isRetirementYear`這個條件是關鍵——如果退休年本身也被`isRetired`判斷到、整年歸零，第3點要做的「只算退休月之前」就會前功盡棄。`isRetirementYear`（`age == targetRetirementAge`）這個變數已經存在於[YearlySimulationPipeline.java:97](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)，呼叫`processAccountsForYear`時可以直接傳入，不需要重新計算。

退休年**之後**的年度（`age > targetRetirementAge`，此時`isRetired`為true、`isRetirementYear`為false）維持整年歸零——這些年份本來就沒有任何在職月份，歸零是對的。

### 3. 退休年當年，提繳月份數要按退休月拆分，不是整年或整年歸零二選一
在[YearlySimulationPipeline.java:156-157](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)呼叫`processAccountsForYear`之前，`isRetirementYear`、`retirementMonth`、`currentMonth`、`monthsToAdd`這幾個變數都已經算好（[YearlySimulationPipeline.java:97-107](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)），可以直接算出這年iDeCo該採計的月份數：
```java
int idecoContributionMonths = monthsToAdd;
if (isRetirementYear) {
    if (retirementMonth == null) {
        idecoContributionMonths = 0;
    } else if (age == initialAge) {
        // 模擬起算當年同時也是退休年：只算「起算當月」到「退休月」這段，不回填起算前的月份
        idecoContributionMonths = Math.max(0, retirementMonth - currentMonth + 1);
    } else {
        // 一般退休年（不是起算年）：從1月算到退休月
        idecoContributionMonths = retirementMonth;
    }
}
```
這個公式跟191已經在用、給薪資折算的邏輯概念一致，但iDeCo是個人銀行帳戶直接扣款，不像薪資需要額外判斷「這個月的薪資是不是已經入帳」，所以比薪資版本的公式更單純，不需要額外的分支。

`TaxAdvantagedAccountProjector`內部，把第75-78行的提繳計算，在退休年的情況下強制借用`calculateContribution`的按月折算分支（傳`isInitialYear=true`，不管這年是否真的是模擬起算年）：
```java
boolean idecoProrate = (age == initialAge) || isRetirementYear;
int idecoMonthsForCalc = isRetirementYear ? idecoContributionMonths : monthsToAdd;

long remainingMonthsIdecoContribution = 0;
for (SimulationFund fund : context.getIdecoFunds()) {
    remainingMonthsIdecoContribution += fund.calculateContribution(idecoProrate, idecoMonthsForCalc);
}
```
查證[SimulationFund.java:95-118](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)確認`calculateContribution`是純函數，只看傳入的兩個參數跟fund自己的狀態，借用這個方法來處理「退休年但非起算年」的按月折算，是安全的。

這代表`processAccountsForYear`的方法簽章需要新增兩個參數（`isRetirementYear`、`idecoContributionMonths`，或等價的資訊），具體怎麼傳遞（在`YearlySimulationPipeline`算好再傳進去，還是把`retirementMonth`/`currentMonth`原始值傳進去、在`TaxAdvantagedAccountProjector`內部自己算）留給實作時決定，兩種做法都只涉及新增1-2個int/boolean參數，不需要像「有效解鎖年齡」那樣新增一整套年齡解析邏輯。

## 連帶發現：停繳跟解鎖是兩條不同的既有判斷線，上面「停繳年齡門檻」的修正只處理了其中一條，另一條（資產累積/解鎖）如果不一起修，60-64歲這五年的提繳會變成「稅務上承認有、資產模擬上沒真的存進去」的半套狀態

查證[TaxAdvantagedAccountProjector.java:174,193,213-216](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)確認，`taxAdvantagedWithdrawalAge`這一個數字同時決定兩件事：
- `age <= taxAdvantagedWithdrawalAge`（第174、193行）：DC/iDeCo的資產餘額今年還要不要繼續累積、滾動複利。
- `age == taxAdvantagedWithdrawalAge`（第214行）：今年是不是該把這個帳戶全部變現、清零餘額、課一次退職所得稅。

這兩行判斷用的都是`taxAdvantagedWithdrawalAge`，跟上面「停繳年齡門檻」這一節要修的`idecoUnlockAge`／`idecoContributionCeilingAge`是完全不同的變數——上面的修正只讓**稅務扣除額計算**（`remainingMonthsIdecoContribution`）正確反映60-64歲還有在提繳，不會動到`taxAdvantagedWithdrawalAge`本身。

查證[SimulationParameterResolver.java:133-143](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java)確認，`taxAdvantagedWithdrawalAge`在使用者沒有自己設定提領年齡時（跟上面「停繳年齡門檻」一節查到的同一個`null`情況），退回的是`max(dcUnlockAge, idecoUnlockAge)`，也就是60——跟使用者實際計畫幾歲退休完全無關。

**這代表一個計畫65歲退休、沒特別設定提領年齡的人，修正後會出現這個矛盾**：60-64歲這五年，稅務扣除額計算正確地認列了還有提繳（上面「停繳年齡門檻」修正生效）；但第193行這五年`age <= taxAdvantagedWithdrawalAge`（60）已經不成立，這段提繳金額不會被存進`context.getIdecoFunds()`的資產餘額裡；而且第214行`age == taxAdvantagedWithdrawalAge`在60歲那年就會觸發，把iDeCo資產全部變現清零——65歲真正退休時，這個帳戶早在5年前就已經被模擬引擎結算關閉過一次。這不是理論上的風險，是現有程式碼邏輯推演出來、真實會發生的交互。

### 為什麼不能直接讓DC/iDeCo解鎖年齡各自獨立判斷，必須繼續共用同一個值
一個直覺的修法是把`idecoUnlocked`從`dcUnlocked`這個寫死的別名（第216行）拆開，讓iDeCo用自己的有效解鎖年齡獨立判斷。**這個方向已經被排除，不是這份issue的方案**——查證[Plan-09](../planned/Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md)確認，現在`idecoUnlocked = dcUnlocked`這個寫死的綁定不是疏漏，是故意的：DC/iDeCo在同一年請領時，日本稅法要求兩者的退職金必須合算（`totalBalance = dcBalance + idecoBalance`、`totalYears = Math.max(dcContributionYears, idecoContributionYears)`，只呼叫一次`calculateRetirementTax`），不能各自獨立申報、各自吃一次完整的退職所得控除。`ActiveTaxCalculationEngine.calculateRetirementTax`這個方法本身完全不知道「今年是不是已經幫另一個帳戶算過一次扣除額」，一旦把`dcUnlocked`/`idecoUnlocked`拆成兩個獨立判斷、剛好同一年都為true，現在程式碼裡本來就存在、但因為寫死綁定而從未被執行過的`else`分支會被叫用兩次，讓退職所得控除被重複計算——這正是Plan-09記錄、但刻意標注「現在不用修，等有人要碰這個耦合時才要注意」的那個latent風險。這份issue要修正提領年齡的默認值，但不能順手把這個耦合拆開，否則會把Plan-09描述的風險從「潛伏」變成「真的發生」。

### 修正方向：只調整「沒設定時的默認值」，繼續保留DC/iDeCo同一年解鎖的設計
`SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge`（[SimulationParameterResolver.java:133-143](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java)）沒有使用者設定、也沒有override時，不該退回`max(dcUnlockAge, idecoUnlockAge)`（60），應該退回`targetRetirementAge`（但不早於`max(dcUnlockAge, idecoUnlockAge)`這個法定最早請領年齡）：
```java
// 現行：
public static int resolveTaxAdvantagedWithdrawalAge(UserProfile profile, int dcUnlockAge, int idecoUnlockAge,
        Integer override) {
    int minUnlockAge = Math.max(dcUnlockAge, idecoUnlockAge);
    if (override != null) {
        return Math.max(minUnlockAge, Math.min(75, override));
    }
    if (profile != null && profile.getTaxAdvantagedWithdrawalAge() != null) {
        return Math.max(minUnlockAge, Math.min(75, profile.getTaxAdvantagedWithdrawalAge()));
    }
    return minUnlockAge;
}

// 改成（新增 targetRetirementAge 參數，沒設定時退回這個值而不是 minUnlockAge）：
public static int resolveTaxAdvantagedWithdrawalAge(UserProfile profile, int dcUnlockAge, int idecoUnlockAge,
        Integer override, int targetRetirementAge) {
    int minUnlockAge = Math.max(dcUnlockAge, idecoUnlockAge);
    if (override != null) {
        return Math.max(minUnlockAge, Math.min(75, override));
    }
    if (profile != null && profile.getTaxAdvantagedWithdrawalAge() != null) {
        return Math.max(minUnlockAge, Math.min(75, profile.getTaxAdvantagedWithdrawalAge()));
    }
    return Math.max(minUnlockAge, Math.min(75, targetRetirementAge));
}
```
這個修正**同時影響DC**——DC跟iDeCo繼續共用同一個`taxAdvantagedWithdrawalAge`，解鎖年齡的默認值算法改了，兩者會一起受益（也一起被改變），但`idecoUnlocked = dcUnlocked`這個寫死綁定本身不動，Plan-09描述的風險不會被引入。這跟上面「停繳年齡門檻」那一節（只影響iDeCo、不影響DC）是不同範圍的兩個修正，不要混在一起判斷「這份issue會不會波及DC」——停繳門檻不影響DC，解鎖默認值修正會影響DC，但這是**刻意、經過確認**的影響，不是意外的邊際效應。

**這份issue的呼叫順序前提要先解決**：查證[AssetSimulationEngineImpl.java:99-110](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)確認，現在的呼叫順序是`resolveTaxAdvantagedWithdrawalAge`（第102-104行）**先**被呼叫，`resolveRetirementAge`（第106行）**後**被呼叫——如果要讓前者的默認值依賴後者的結果，必須把這兩行的呼叫順序對調（先算`targetRetirementAge`，再把這個值傳給`resolveTaxAdvantagedWithdrawalAge`）。查證[AssetSimulationFitnessEvaluator.java:50-74](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java)確認`resolveRetirementAge`本身不依賴`idecoUnlockAge`/`dcUnlockAge`/`taxAdvantagedWithdrawalAge`任何一個，調換順序是安全的，不會造成循環依賴；這個GA/Grid Search用的評估路徑也確認完全沒有呼叫`resolveTaxAdvantagedWithdrawalAge`，不受這次修正影響。

### 這個修正也會讓上一輪寫的「情境5」變得真的成立，之前那個版本實際上不會發生
上面「各種邊界年齡組合」表格的情境5（68歲延後退休、沒特別填提領年齡，描述「65-67歲停止提繳、資產複利滾動，68歲才解鎖」）是在只修正「停繳門檻」、沒修正「解鎖默認值」的前提下寫的——在那個前提下，`taxAdvantagedWithdrawalAge`依然會被解析成60（因為沒修正默認值），情境5描述的65-67歲資產滾動根本等不到發生，資產在60歲就已經被第214行的解鎖判斷清空。這份「連帶發現」修正落地後，情境5才會變成真實會發生的狀態——實作時兩個修正（停繳門檻+解鎖默認值）要一起做，不能只做前者。

## 這份issue沒有解決的部分
- **DC/iDeCo各自獨立解鎖是一個已知但刻意不在這份issue處理的方向**——[Plan-09](../planned/Plan-09-dc-ideco-decoupled-unlock-would-double-dip-retirement-deduction.md)已經記錄了這個方向需要先解決的退職所得控除重複計算問題，這份issue的解鎖默認值修正不觸碰這個耦合，維持現狀。
- **實作時必須針對上面「各種邊界年齡組合」表格的六種情境各寫一個單元測試**，情境1（65歲退休、沒特別填提領年齡）是這次修正真正要防止回歸的案例——必須明確斷言60-64歲每一年`remainingMonthsIdecoContribution > 0`，不能只測65歲當年歸零；情境5（68歲延後退休、沒特別填提領年齡）需要斷言65-67歲提繳為0但資產餘額仍按複利公式增長（不是停滯不動），驗證「停繳」跟「解鎖請領」分屬不同年份時既有的資產滾動分支確實有在運作。這份issue不代為寫出測試程式碼，但這六種情境是實作時的最低覆蓋範圍，不是選擇性的。
- **`resolveTaxAdvantagedWithdrawalAge`新增`targetRetirementAge`參數後，也需要獨立的單元測試覆蓋這四種組合**：(1) 使用者沒設定提領年齡、`targetRetirementAge=65`→回傳65（修正後的新行為）；(2) 使用者沒設定、`targetRetirementAge=45`（FIRE）→回傳60（法定下限封頂，不能因為45歲退休就讓解鎖年齡也跟著退到45歲）；(3) 使用者自己設定了提領年齡（例如68）→回傳68，`targetRetirementAge`這個新參數在這個分支完全不生效，不能讓它意外覆蓋使用者的明確設定；(4) 使用者沒設定、`targetRetirementAge=80`（超過75歲法定上限）→回傳75（既有的`Math.min(75, ...)`封頂依然要生效）。這四種組合跟上面「各種邊界年齡組合」表格的六種情境是兩組獨立的測試範圍，分別驗證`SimulationParameterResolver`這個共用方法本身，跟`TaxAdvantagedAccountProjector`消費這個方法之後的行為，不能只測其中一邊。
- 退休年提繳月份拆分的計算，要放在`YearlySimulationPipeline`裡算好再傳參數進去，還是把原始變數傳進去在`TaxAdvantagedAccountProjector`內部算，留給實作時決定。
- 退休年剛好跟`age >= idecoContributionCeilingAge`（65歲或使用者設定的提領年齡）同一年重疊時，兩個停繳條件該怎麼決定最終的提繳月份數，這份issue沒有列出每一種重疊情境的詳細公式，留給實作時依照「上限先到就先停」的原則處理，不是這份issue要窮舉的範圍。
- 企業型DC（[issue-194](issue-194.md)）現狀用的也是`if (isRetired || age >= dcUnlockAge)`同一種寫法，是不是也該比照這份issue改成對齊`taxAdvantagedWithdrawalAge`——這份issue只處理iDeCo，不代為決定194要不要跟進，留給之後查證194時另外判斷。
- Plan-30（退休後自費繼續提繳iDeCo）本身要不要投入實作、具體怎麼設計輸入介面，是Plan-30自己的範圍，這份issue不處理。


---

## 修正報告書（2026-11-25 實作完成）

### 1. 實作摘要與設計對齊
本次實作完全依照本份文件「建議修正方向」與「連帶發現」兩節定案規格落地：
- **嚴守 Plan-09 耦合**：[TaxAdvantagedAccountProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L221) 的 `boolean idecoUnlocked = dcUnlocked;` 與資產滾動條件 `if (age <= taxAdvantagedWithdrawalAge)` **完全維持不動**，未將 DC／iDeCo 解鎖年齡拆開獨立判斷，亦未引入任何額外「有效解鎖年齡」或「運用指図者」新概念。
- **修正解鎖默認值（DC／iDeCo 共用）**：僅修改 `SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge`（並於 `AssetSimulationEngineImpl` 對調呼叫順序），使未設定提領年齡時退回 `targetRetirementAge`（受 `[minUnlockAge, 75]` 約束），不再卡死在 60 歲。
- **修正 iDeCo 停繳門檻與退休當年按月拆分**：讀取 `UserProfile.taxAdvantagedWithdrawalAge` 原始未解析值計算 `idecoContributionCeilingAge`（封頂 65 歲），於 `TaxAdvantagedAccountProjector` 改用 `if ((isRetired && !isRetirementYear) || age >= idecoContributionCeilingAge)`，並於退休年借用 `SimulationFund.calculateContribution` 按退休月折算（同步套用於掛金控除與資產累積兩處）。
- DC 停繳條件（`if (isRetired || age >= dcUnlockAge)`）與 NISA 邏輯均未更動（DC 退休年拆月留待 [issue-194](issue-194.md)）。

---

### 2. 修改檔案清單與變更明細

#### 產品程式碼（4 個檔案）
1. **[SimulationParameterResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java#L128-L143)**
   - `resolveTaxAdvantagedWithdrawalAge` 新增 `int targetRetirementAge` 參數；當 `override == null` 且 `profile.getTaxAdvantagedWithdrawalAge() == null` 時，改回傳 `Math.max(minUnlockAge, Math.min(75, targetRetirementAge))`。
2. **[AssetSimulationEngineImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java#L99-L107)**
   - 對調解析順序：先呼叫 `resolveRetirementAge` 算出 `targetRetirementAge`，再傳入 `resolveTaxAdvantagedWithdrawalAge(..., targetRetirementAge)`。
3. **[YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L156-L176)**
   - 維持 `processSimulationYear` 原 13 參數簽名不變。
   - 讀取 `context.getProfile().getTaxAdvantagedWithdrawalAge()` 原始值算出 `idecoContributionCeilingAge = (userDeclaredWithdrawalAge != null) ? Math.min(userDeclaredWithdrawalAge, 65) : 65`。
   - 依 `isRetirementYear`、`retirementMonth`、`currentMonth`、`age == initialAge` 計算 `idecoContributionMonths`，傳入 `processAccountsForYear`。
4. **[TaxAdvantagedAccountProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L31-L222)**
   - [L31–L39](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L31-L39)：`processAccountsForYear` 改接 `idecoContributionCeilingAge`（取代原 `idecoUnlockAge`），並新增 `isRetirementYear`、`idecoContributionMonths`。
   - [L66–L71](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L66-L71) 與 [L201](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L201)：計算 `idecoProrate = (age == initialAge) || isRetirementYear` 與 `idecoMonthsForCalc = isRetirementYear ? idecoContributionMonths : monthsToAdd`，同步套用於掛金控除額與帳戶本金累積。
   - [L133–L136](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L133-L136)：停繳判斷改為 `if ((isRetired && !isRetirementYear) || age >= idecoContributionCeilingAge)`。
   - [L198](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L198) 與 [L219–L221](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L219-L221)：`if (age <= taxAdvantagedWithdrawalAge)` 與 `boolean idecoUnlocked = dcUnlocked;` 維持原樣不變。

#### 測試程式碼（修改 1 個、新增 2 個）
1. **[YearlySimulationPipelineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java#L102-L105)**
   - 更新 `@Mock TaxAdvantagedAccountProjector.processAccountsForYear(...)` 的 stub 簽名為 8 參數。
2. **[SimulationParameterResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolverTest.java)**（新增）
   - 覆蓋全部 4 組必要組合（`null`+65→65、`null`+45→60、自訂 68→68、`null`+80→75）以及 override 優先權驗證。
3. **[YearlySimulationPipelineIdecoLifecycleTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)**（新增）
   - 覆蓋表格全部 6 種邊界情境與首年退休交集，共 8 個測試案例：
     - 情境 1：65 歲退休、`null` 提領年齡（逐年斷言 60～64 歲提繳 `240,000 > 0`，65 歲停繳且 DC 與 iDeCo 同步解鎖）
     - 情境 2：65 歲退休、自訂 68 歲提領（65 歲停繳、65～67 歲持續複利滾動、68 歲解鎖）
     - 情境 3：62 歲退休、自訂 62 歲提領（60～61 歲正常提繳、62 歲達上限停繳並解鎖）
     - 情境 3 補充（非首年／首年交集）：62 歲退休、`null` 提領年齡（6 月退休按 6 個月折算；4 月起算且 6 月退休按 3 個月折算）
     - 情境 4：45 歲 FIRE（45 歲按退休月折算、46～59 歲提繳為 0 持續滾動、60 歲保底解鎖）
     - 情境 5：68 歲延後退休、`null` 提領年齡（60～64 歲正常提繳、明確斷言 65～67 歲提繳為 0 但餘額持續複利增長、68 歲解鎖）
     - 情境 6：60 歲退休、自訂 60 歲提領（60 歲停繳並立即解鎖）

---

### 3. 驗證結果
- 全數既有測試與新增 `SimulationParameterResolverTest`、`YearlySimulationPipelineIdecoLifecycleTest` 均已於本機執行通過。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    1  INSERT  git --no-pager diff                                    10011  12.57G   9.07    11:41:20  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java
index 61e1b58f..b3c560a1 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java
@@ -98,13 +98,13 @@ public class AssetSimulationEngineImpl implements AssetSimulationEngine {
 
         int dcUnlockAge = SimulationParameterResolver.resolveDcUnlockAge(context.getConfig());
         int idecoUnlockAge = SimulationParameterResolver.resolveIdecoUnlockAge(context.getConfig());
-        Integer idecoStartOverride = overrides != null ? overrides.idecoStartAgeOverride() : null;
-        int taxAdvantagedWithdrawalAge = SimulationParameterResolver
-                .resolveTaxAdvantagedWithdrawalAge(context.getProfile(), dcUnlockAge, idecoUnlockAge,
-                        idecoStartOverride);
         Integer retirementOverride = overrides != null ? overrides.targetRetirementAgeOverride() : null;
         int targetRetirementAge = SimulationParameterResolver.resolveRetirementAge(context.getProfile(),
                 context.getConfig(), retirementOverride);
+        Integer idecoStartOverride = overrides != null ? overrides.idecoStartAgeOverride() : null;
+        int taxAdvantagedWithdrawalAge = SimulationParameterResolver
+                .resolveTaxAdvantagedWithdrawalAge(context.getProfile(), dcUnlockAge, idecoUnlockAge,
+                        idecoStartOverride, targetRetirementAge);
         Integer pensionOverride = overrides != null ? overrides.pensionStartAgeOverride() : null;
         int pensionStartAge = SimulationParameterResolver.resolvePensionStartAge(context.getProfile(),
                 context.getConfig(), pensionOverride);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
index 1892914a..7526d0f0 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
@@ -32,9 +32,11 @@ public class TaxAdvantagedAccountProjector {
             SimulationContext context,
             int initialAge,
             int dcUnlockAge,
-            int idecoUnlockAge,
+            int idecoContributionCeilingAge,
             int taxAdvantagedWithdrawalAge,
-            int monthsToAdd) {
+            int monthsToAdd,
+            boolean isRetirementYear,
+            int idecoContributionMonths) {
 
         int age = context.getAge();
         boolean isRetired = context.isRetired();
@@ -61,9 +63,12 @@ public class TaxAdvantagedAccountProjector {
         // However, this is a conservative error; the only consequence is that the user
         // will receive a larger-than-expected tax refund during the actual year-end tax
         // adjustment.
+        boolean idecoProrate = (age == initialAge) || isRetirementYear;
+        int idecoMonthsForCalc = isRetirementYear ? idecoContributionMonths : monthsToAdd;
+
         long remainingMonthsIdecoContribution = 0;
         for (SimulationFund fund : context.getIdecoFunds()) {
-            remainingMonthsIdecoContribution += fund.calculateContribution(age == initialAge, monthsToAdd);
+            remainingMonthsIdecoContribution += fund.calculateContribution(idecoProrate, idecoMonthsForCalc);
         }
         long remainingMonthsDcContribution = 0;
         for (SimulationFund fund : context.getDcFunds()) {
@@ -125,7 +130,7 @@ public class TaxAdvantagedAccountProjector {
         boolean dcAllowed = true;
         boolean idecoAllowed = true;
 
-        if (isRetired || age >= idecoUnlockAge) {
+        if ((isRetired && !isRetirementYear) || age >= idecoContributionCeilingAge) {
             remainingMonthsIdecoContribution = 0L;
             idecoAllowed = false;
         }
@@ -193,7 +198,7 @@ public class TaxAdvantagedAccountProjector {
             if (age <= taxAdvantagedWithdrawalAge) {
                 long newIdecoBalance = 0;
                 for (SimulationFund fund : context.getIdecoFunds()) {
-                    long intended = fund.calculateContribution(age == initialAge, monthsToAdd);
+                    long intended = fund.calculateContribution(idecoProrate, idecoMonthsForCalc);
                     long annualContribution = idecoAllowed ? intended : 0L;
                     Double expectedRate = fund.getExpectedReturnRate();
                     double returnRate = expectedRate != null ? expectedRate : 0.0;
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index 193c1058..899103c9 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -153,8 +153,27 @@ public class YearlySimulationPipeline {
         MortgageYearlyResult mortgageResult = mortgageSimulationService.simulateMortgagesForYear(context,
                 allMortgages, nowMonth, overrides);
 
+        Integer userDeclaredWithdrawalAge = (context.getProfile() != null)
+                ? context.getProfile().getTaxAdvantagedWithdrawalAge()
+                : null;
+        int idecoContributionCeilingAge = (userDeclaredWithdrawalAge != null)
+                ? Math.min(userDeclaredWithdrawalAge, 65)
+                : 65;
+
+        int idecoContributionMonths = monthsToAdd;
+        if (isRetirementYear) {
+            if (retirementMonth == null) {
+                idecoContributionMonths = 0;
+            } else if (age == initialAge) {
+                idecoContributionMonths = Math.max(0, retirementMonth - currentMonth + 1);
+            } else {
+                idecoContributionMonths = retirementMonth;
+            }
+        }
+
         InvestmentContributions contributions = taxAdvantagedAccountProjector.processAccountsForYear(
-                context, initialAge, dcUnlockAge, idecoUnlockAge, taxAdvantagedWithdrawalAge, monthsToAdd);
+                context, initialAge, dcUnlockAge, idecoContributionCeilingAge, taxAdvantagedWithdrawalAge,
+                monthsToAdd, isRetirementYear, idecoContributionMonths);
 
         YearlySimulationState state = new YearlySimulationState();
         state.setAnnualExpenses(annualExpenses);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java
index b2941fe4..027855cf 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java
@@ -128,10 +128,11 @@ public final class SimulationParameterResolver {
     /**
      * Resolves the Tax-Advantaged (iDeCo/DC) target withdrawal age.
      * It ensures the withdrawal age is not earlier than the statutory unlock ages.
-     * Priority: 1. override, 2. profileValue, 3. minUnlockAge
+     * Priority: 1. override, 2. profileValue, 3. targetRetirementAge (clamped to
+     * [minUnlockAge, 75])
      */
     public static int resolveTaxAdvantagedWithdrawalAge(UserProfile profile, int dcUnlockAge, int idecoUnlockAge,
-            Integer override) {
+            Integer override, int targetRetirementAge) {
         int minUnlockAge = Math.max(dcUnlockAge, idecoUnlockAge);
         if (override != null) {
             return Math.max(minUnlockAge, Math.min(75, override));
@@ -139,6 +140,6 @@ public final class SimulationParameterResolver {
         if (profile != null && profile.getTaxAdvantagedWithdrawalAge() != null) {
             return Math.max(minUnlockAge, Math.min(75, profile.getTaxAdvantagedWithdrawalAge()));
         }
-        return minUnlockAge;
+        return Math.max(minUnlockAge, Math.min(75, targetRetirementAge));
     }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
index 93d5c9da..8aa4b71c 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
@@ -14,6 +14,7 @@ package ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline;
 
 import static org.assertj.core.api.Assertions.assertThat;
 import static org.mockito.ArgumentMatchers.any;
+import static org.mockito.ArgumentMatchers.anyBoolean;
 import static org.mockito.ArgumentMatchers.anyInt;
 import static org.mockito.ArgumentMatchers.anyLong;
 import static org.mockito.ArgumentMatchers.eq;
@@ -100,7 +101,7 @@ class YearlySimulationPipelineTest {
 
         lenient()
                 .when(taxAdvantagedAccountProjector.processAccountsForYear(any(), anyInt(), anyInt(), anyInt(),
-                        anyInt(), anyInt()))
+                        anyInt(), anyInt(), anyBoolean(), anyInt()))
                 .thenReturn(new InvestmentContributions(0L, 0L, 0L, 0L));
         lenient().when(incomeExpenseCalculator.calculateExpensesForYear(any(), any(), anyInt(), anyInt()))
                 .thenReturn(0L);
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    1  INSERT                                                         10012  12.87G   7.46    11:42:57  