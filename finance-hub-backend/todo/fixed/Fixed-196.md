# [FIXED] 196：193落地後，iDeCo停繳判斷式在「退休年齡恰好等於停繳上限」這個最常見的情境（65歲退休、沒設定提領年齡）整年歸零，不符合193標題講的「退休當年按月保留提繳」；修法需要三個步驟一起做——把`>=`改成`>`是必要的第一步，但光改這一步不夠，還要搭配「滿上限那年算到生日當月」這個新規則，而且`TaxAdvantagedAccountProjector`消費月份數的兩個變數都要同步改，單獨漏掉任何一步都會留下錯誤；這條生日切月規則只適用於iDeCo，不能套用到194（企業型DC）

## 這份issue怎麎來的
這是193（[issue-193](../fixed/Fixed-193.md)，已結案移到`fixed/`）落地後的二次審查發現的新缺口。這份issue經過三輪定案：第一輪發現現狀用`>=`判斷停繳門檻，會讓「退休年齡剛好等於停繳上限65歲」這個最常見情境被整年錯誤歸零；第二輪查證發現，光把`>=`改成`>`還不夠，必須額外補上「滿上限那年算到生日當月」這個規則，否則會在另外兩種情境（68歲才退休、65歲還在職；退休月跟生日月不是同一個月）造成新的多算問題；第三輪查證發現，第二輪提出的方案本身漏了一個前提——如果停繳判斷式仍然維持`>=`，第二輪算出來的正確月份數根本沒有機會被用到，一樣會被整段清零，`>=`改`>`這一步不能省略，三個步驟（改運算子、新增生日切月計算、讓`TaxAdvantagedAccountProjector`內部的兩個變數都讀到新月份數）要同一次一起做才會真正生效。

## 相關檔案
- [TaxAdvantagedAccountProjector.java:133-136](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)（iDeCo停繳判斷式，問題核心所在：`if ((isRetired && !isRetirementYear) || age >= idecoContributionCeilingAge)`，用的是`>=`）
- [YearlySimulationPipeline.java:156-176](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（`idecoContributionCeilingAge`的計算，第159-161行：沒設定提領年齡時固定是65，跟`taxAdvantagedWithdrawalAge`是完全不同的兩個變數——這個變數本身沒有問題，問題是上面消費它的判斷式用的比較運算子）
- [YearlySimulationPipelineIdecoLifecycleTest.java:192-222](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)（`scenario1_retireAt65_nullWithdrawalAge_contributesThrough64_unlocksTogetherAt65`，第219行`assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(0L)`——這個斷言把下面「現狀」一節描述的錯誤行為鎖成了測試要求的正確答案，測試本身用`LocalDate.of(1967, 6, 15)`跟`RetirementMonthMode.BIRTH_MONTH`，代表退休月確實是6月，不是12月）
- [YearlySimulationPipelineIdecoLifecycleTest.java:269-291](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)（`scenario3_retireAt62_withdrawalAt62_contributesThrough61_stopsAndUnlocksAt62`，第289行同樣的錯誤斷言——這個情境是使用者自訂提領年齡跟退休年齡設成一樣，一樣會踩中同一個bug）
- [YearlySimulationPipelineIdecoLifecycleTest.java:399-430](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)（`scenario5_delayedRetirementAt68_nullWithdrawalAge_stopsAt65_compounds65To67_unlocksAt68`——這個情境確認會被單純改運算子的修法破壞：現狀斷言65-67歲都是`0L`，如果只把`>=`改`>`，68歲才退休、65歲還在職（`isRetirementYear=false`）時，65歲會從`0`變成整年12個月——這是錯的，因為生日6月之後依法已經不能再提繳iDeCo，見下方「建議修正方向」）
- [issue-194](issue-194.md)（企業型DC對應修正，查證確認「逐條比對」第5點用的是`age > taxAdvantagedWithdrawalAge`這個嚴格大於寫法——這份issue的生日切月規則**不適用於**194，DC看的是在不在職、不是生日月，見下方「建議修正方向」結尾的排除說明）

## 現狀：親自手算驗證193落地後的實際行為，確認Grok指出的bug真實存在
查證[TaxAdvantagedAccountProjector.java:133](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)：
```java
if ((isRetired && !isRetirementYear) || age >= idecoContributionCeilingAge) {
    remainingMonthsIdecoContribution = 0L;
    idecoAllowed = false;
}
```

**情境：65歲退休、沒有自訂提領年齡、生日6月（退休月是6月）**。查證[YearlySimulationPipeline.java:159-161](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認`idecoContributionCeilingAge`在沒設定提領年齡時固定是65。手算`age=65`這一年：
- `isRetired = (age >= targetRetirementAge) = (65 >= 65) = true`
- `isRetirementYear = (age == targetRetirementAge) = (65 == 65) = true`
- `idecoContributionMonths`：因為`isRetirementYear`成立、且不是起算年，走`idecoContributionMonths = retirementMonth = 6`這條路——先正確算出了6個月的提繳金額（假設月提繳2萬，算出12萬）。
- 停繳判斷式第一項：`isRetired && !isRetirementYear` = `true && false` = `false`
- 停繳判斷式第二項：`age >= idecoContributionCeilingAge` = `65 >= 65` = **`true`**
- 整體：`false || true` = **`true`**——`remainingMonthsIdecoContribution`被直接清成`0L`，剛剛按退休月正確算出的12萬被整段蓋掉。

這代表193標題宣稱修好的「退休當年按月保留提繳」，在這個最常見的情境（65歲退休、沒有特別去設定提領年齡）下完全沒有生效——1月到6月（生日當月為止）這段使用者真實還在職、理應繼續提繳的事實，一樣被整年歸零抹除，跟193修正之前的行為一模一樣。

**額外驗證：使用者自訂提領年齡跟退休年齡設成一樣時，同樣會踩到**。62歲退休、自訂提領年齡也設62——`idecoContributionCeilingAge = Math.min(62, 65) = 62`。`age=62`這一年：`isRetirementYear=true`，第一項`false`；`62 >= 62`為`true`；整體`true`——一樣整年歸零。

**對照：62歲退休、沒有自訂提領年齡（退休年齡早於停繳上限）這個情境確實是正常的**——`idecoContributionCeilingAge`固定是65，`age=62`時`62 >= 65`為`false`，第一項也是`false`（`isRetirementYear=true`），整體`false`，按退休月拆分的12萬正確保留。這代表最初發現的`0L`錯誤不是全面性的，只精確發生在「退休年齡（或自訂提領年齡）恰好等於停繳上限65歲」這個邊界重疊的情境——但這個情境正好是最常見、最多使用者會踩到的標準退休案例（65歲退休、沒有特別調整任何進階設定）。

**查證確認：把`>=`改成`>`是必要的第一步，但光改這一個字元還不夠，會在另外兩種情境留下新的多算問題——這兩種情境不是反對改運算子，是證明改運算子之外還要補一個新規則**：

**(a) 68歲才退休、65歲還在職（[scenario5](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)的情境）**：`age=65`時`isRetirementYear = (65 == 68) = false`（還沒到退休年）。手算：改成`>`後，`age > idecoContributionCeilingAge`為`65 > 65 = false`，第一項`isRetired && !isRetirementYear`也是`false`（`isRetired=(65>=68)=false`）——整體`false`，不會被清零。但查證[TaxAdvantagedAccountProjector.java:66-67](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)確認，`idecoProrate = (age==initialAge) || isRetirementYear`在這個情境下是`false`，`idecoMonthsForCalc`會走`monthsToAdd`這個整年固定值——這代表改完之後，65歲這一年會變成全年12個月的提繳。但這個人6月已經滿65歲，依法已經不能再提繳iDeCo了（65歲以上一律停止提繳資格）——全年12個月是錯的多算。

**(b) 退休月跟生日月不是同一個月（使用者設定`RetirementMonthMode.DECEMBER`，但生日是6月）**：如果退休年齡剛好等於65歲、退休月被系統固定成12月（DECEMBER模式），`idecoContributionMonths`會算出`retirementMonth=12`，改成`>`後這一年會被算成12個月——但這個人6月已經滿65歲，7-12月依法不能再提繳，12個月一樣是錯的多算。

這兩個情境證實：**滿停繳上限那一年的正確提繳月份數，不是單純看「有沒有被停繳判斷式清零」這個二元問題，是要另外算出「這一年該算到幾月」**——現有的`idecoContributionMonths`只服務「退休拆分」這一個目的（算到退休月），完全沒有考慮「年中達到停繳年齡上限」這第二個獨立的裁斷點，兩者在「退休年齡恰好等於停繳上限」的情境下恰好重疊、才讓單純改運算子看起來好像夠用，但一旦退休年齡晚於停繳上限（情境a）或退休月跟生日月脫鉤（情境b），這個簡化就不成立。

## 查證確認：新增的測試把這個錯誤行為鎖成了「正確答案」，不是漏了測試覆蓋，是測試本身斷言錯了
查證[YearlySimulationPipelineIdecoLifecycleTest.java:192-222](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)的`scenario1_retireAt65_nullWithdrawalAge_contributesThrough64_unlocksTogetherAt65`——這個測試方法名稱本身的語意（`contributesThrough64`，暗示「提繳一路持續到64歲」）已經隱含了「65歲當年不會有提繳」這個假設，而第219行的斷言`assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(0L)`，把這個假設寫成了明確的驗收標準。但測試本身用的`LocalDate.of(1967, 6, 15)`跟`RetirementMonthMode.BIRTH_MONTH`確認退休月確實是6月——如果193真的修好了，這個案例65歲當年正確答案是算到生日當月（6月）的6個月提繳，不是0。

[YearlySimulationPipelineIdecoLifecycleTest.java:269-291](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)的`scenario3_retireAt62_withdrawalAt62_contributesThrough61_stopsAndUnlocksAt62`，第289行同樣的錯誤斷言模式。

這代表193實作落地時，查證過程（包含這次對話之前的複核）確認了`idecoUnlocked = dcUnlocked`沒被拆開、`resolveTaxAdvantagedWithdrawalAge`新增`targetRetirementAge`參數這兩個關鍵修正確實做對了，但沒有重新手算驗證「退休年齡恰好等於停繳上限」這個邊界——新增的測試反而把這個未被發現的bug，用看起來合理的方法名稱、正常通過的斷言，偽裝成「已經驗證過的正確行為」，讓後續的複核更容易被誤導成「有測試覆蓋、應該沒問題」。

## 為何是問題
這不是一個邊緣情境——65歲退休、沒有特別調整提領年齡設定，是整個系統裡最基本、最多使用者會直接套用的標準退休案例。現狀下，這群使用者退休當年（例如6月生日滿65歲）1月到6月這段真實還在職、依然有在提繳iDeCo的事實，會被整年歸零抹除，稅前扣除額跟資產累積雙雙被低估，模擬出來的結果比真實情況更悲觀——跟193、194「為何是問題」這兩節描述的核心症狀（稅負比真實情況更高、資產累積比真實情況更少）是同一種性質的bug，只是193自己落地時又重新把它引入了一次。

## 建議修正方向
不是多方案並列，規則已經收斂成一句話：**滿停繳上限（65歲，或使用者自訂的提領年齡）那一年，iDeCo只算到生日當月為止；如果同一年退休、而且退休月比生日月更早，就算到退休月為止；兩個條件都沒有的月份一律不提繳。沒有生日資料時，這一年維持現狀的`0`，不去猜月份。**

**第一步：停繳判斷式還是要把`>=`改成`>`，這一步不能省略**——即使下面第二步正確算出「滿上限那年該算幾個月」，只要這個判斷式本身還是`age >= idecoContributionCeilingAge`，在`age == idecoContributionCeilingAge`（滿上限那一年）這個判斷式就已經成立，`remainingMonthsIdecoContribution`一樣會被整段清成`0L`，蓋掉第二步算出來的月份數——第二步的計算結果根本沒有機會被用到。改成：
```java
// 現行：
if ((isRetired && !isRetirementYear) || age >= idecoContributionCeilingAge) {

// 改成：
if ((isRetired && !isRetirementYear) || age > idecoContributionCeilingAge) {
```
滿上限那一年（`age == idecoContributionCeilingAge`）不會被這一行清零，`age > idecoContributionCeilingAge`（上限之後的年份）才會被清零——已經在更早年齡退休的人，仍然會被前半段的`isRetired && !isRetirementYear`清成0，不會在滿上限那年意外補提繳。

**第二步：在`YearlySimulationPipeline`裡，新增一段判斷「今年是不是滿停繳上限的那一年」，並算出這一年該提繳到幾月——起算年疊加這個情境時，要扣掉模擬範圍之外已經過去的月份**：
```java
// 在既有的 idecoContributionCeilingAge、idecoContributionMonths 計算之後，新增：
boolean isIdecoCeilingYear = (age == idecoContributionCeilingAge);
if (isIdecoCeilingYear) {
    Integer birthMonth = (birthDate != null) ? birthDate.getMonthValue() : null;
    if (birthMonth == null) {
        idecoContributionMonths = 0; // 沒有生日資料，不猜月份，維持現狀的保守值
    } else {
        int cappedMonth = (isRetirementYear && retirementMonth != null)
                ? Math.min(birthMonth, retirementMonth)
                : birthMonth;
        idecoContributionMonths = (age == initialAge)
                ? Math.max(0, cappedMonth - currentMonth + 1) // 起算年：扣掉模擬開始之前已經過去的月份
                : cappedMonth;
    }
}
```
這段邏輯要放在既有的`idecoContributionMonths`計算（[YearlySimulationPipeline.java:163-171](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)）之後、傳進`processAccountsForYear`之前。`age == initialAge`這個分支是必要的——如果模擬從4月開始、使用者6月滿65歲，不能直接用`birthMonth=6`當月份數，因為1-3月不屬於這次模擬覆蓋的範圍，要用`Math.max(0, 6 - 4 + 1) = 3`，跟現有`idecoContributionMonths`公式裡`age == initialAge`那條分支（[YearlySimulationPipeline.java:167-168](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)）用的是同一種考量。`birthDate`這個資訊查證[YearlySimulationPipeline.java:98](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認在這個方法裡本來就讀得到，不需要新增參數。

**第三步：`TaxAdvantagedAccountProjector`內部也要配合，`idecoProrate`跟`idecoMonthsForCalc`兩個變數都要改，不是只改前者**：現狀[TaxAdvantagedAccountProjector.java:66-67](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)：
```java
boolean idecoProrate = (age == initialAge) || isRetirementYear;
int idecoMonthsForCalc = isRetirementYear ? idecoContributionMonths : monthsToAdd;
```
`idecoMonthsForCalc`這一行本身也只在`isRetirementYear`為`true`時才讀`idecoContributionMonths`——68歲才退休、65歲還在職這種情境（`isRetirementYear=false`），即使第二步已經把`idecoContributionMonths`正確覆寫成6，`idecoMonthsForCalc`這一行完全不會讀到這個被覆寫後的值，直接走`monthsToAdd`（整年12個月）。這代表`idecoProrate`、`idecoMonthsForCalc`兩個判斷式都要新增涵蓋「今年是滿停繳上限的那一年」這個新情境（例如新增一個`isIdecoCeilingYear`參數，兩個判斷式都改成`(age == initialAge) || isRetirementYear || isIdecoCeilingYear`），只改`idecoProrate`、漏改`idecoMonthsForCalc`，一樣會讓第二步算出來的月份數被`monthsToAdd`蓋掉，scenario5那樣的情境仍然會算成整年。

**四個案例驗證**（手算確認這個規則的正確性，比上一輪多了`scenario2`這個原本遺漏的情境）：
- 65歲退休、6月生日、退休月也是6月（跟現有`scenario1`一致）：`isIdecoCeilingYear=true`，`isRetirementYear=true`且`retirementMonth=6`，`Math.min(6, 6) = 6`，不是起算年所以不用再扣——算6個月，這正是193原本要修的情境。
- 65歲退休、6月生日、退休月是12月（DECEMBER模式）：`isIdecoCeilingYear=true`，`isRetirementYear=true`且`retirementMonth=12`，`Math.min(6, 12) = 6`——算6個月，不是12個月，正確避開了「退休月跟生日月脫鉤」這個多算陷阱。
- 68歲才退休、65歲還在職、6月生日（`scenario5`的65歲那一年）：`isIdecoCeilingYear=true`，`isRetirementYear=false`（還沒到真正的退休年，`targetRetirementAge=68`），走`birthMonth=6`這條路，不是起算年——算6個月，不是全年12個月也不是0。
- 65歲退休、68歲才領、6月生日（`scenario2`的65歲那一年）：**這一年`isRetirementYear=true`（`targetRetirementAge=65`，退休年就是65歲，不是像`scenario5`那樣還沒到退休年）**，走`Math.min(birthMonth=6, retirementMonth=6) = 6`這條路，跟`scenario1`是同一種路徑，不是跟`scenario5`同一種路徑——只是因為這個測試的生日月跟退休月剛好都是6月，算出來的月份數（6）、金額（120,000）跟`scenario5`的65歲恰好一樣，不代表兩者走的是同一個判斷分支。
- 模擬起算年剛好是滿上限那年，4月開算、6月生日：`isIdecoCeilingYear=true`，`age==initialAge`，`Math.max(0, 6-4+1)=3`——算3個月，不是6個月，因為1-3月不在這次模擬範圍內。

**對應的測試斷言要同步修正，不是只改程式碼，而且不只影響當年的提繳金額，也會連動影響後續年份的複利起點**：
- `scenario1_retireAt65_nullWithdrawalAge_contributesThrough64_unlocksTogetherAt65`第219行，65歲的斷言要從`isEqualTo(0L)`改成`isEqualTo(120_000L)`——查證[YearlySimulationPipelineIdecoLifecycleTest.java:67,169-171](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)確認`MONTHLY_IDECO = 20_000L`、course是`"毎月"`，`calculateContribution(true, 6)`會算出`20_000 × 6 = 120_000`，跟其他年齡斷言的`240_000L`（12個月）剛好是一半。這個情境65歲當年同時解鎖變現（`dcUnlocked`/`idecoUnlocked`在`age==taxAdvantagedWithdrawalAge`成立），所以餘額最終還是會在同一年清零，不需要額外處理複利連動。
- `scenario3_retireAt62_withdrawalAt62_contributesThrough61_stopsAndUnlocksAt62`第289行，62歲的斷言同樣要從`isEqualTo(0L)`改成`isEqualTo(120_000L)`——這個測試也是6月生日、退休月6月（`idecoContributionCeilingAge = Math.min(62, 65) = 62`，`isRetirementYear=true`且`retirementMonth=6`，`Math.min(6,6)=6`個月），跟`scenario1`同一套算法。同樣在62歲當年解鎖變現，不需要額外處理複利連動。
- `scenario5_delayedRetirementAt68_nullWithdrawalAge_stopsAt65_compounds65To67_unlocksAt68`第415-423行，這個情境**不是當年解鎖**（解鎖在68歲）。65歲的提繳斷言（`annualIdecoContribution()`）要改成`isEqualTo(120_000L)`；65歲的餘額斷言（`context.getIdecoBalance()`）則是另一個數字，不是`120_000L`，不能把這兩件事混在一起——餘額要套用`projectInvestmentBalance`這條既有公式重算，不是先把12萬加進舊餘額、再整筆乘1.10。查證[InvestmentReturnCalculator.java:44-55](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/InvestmentReturnCalculator.java)確認真實公式是舊餘額、新提繳分別乘不同係數：`round(舊餘額 × (1+rate) + 提繳 × (1+rate/2))`，`rate=0.10`時就是`round(舊餘額 × 1.10 + 120_000 × 1.05)`，不是`round((舊餘額+120_000) × 1.10)`。66、67歲再拿這個新算出來的65歲餘額，依既有公式（提繳為0）每年乘1.10往下推。
- `scenario2_retireAt65_withdrawalAt68_stopsAt65_compoundsThrough67_unlocksAt68`（上一輪沒有列到這個情境，是真實遺漏）：查證[YearlySimulationPipelineIdecoLifecycleTest.java:247-254](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java)確認，現狀用`for (int age = 65; age <= 67; age++)`一個迴圈把65、66、67歲三年合併斷言成同一組（每年都是`0L`、每年`expected`乘1.10）。修正後65歲要改成`isEqualTo(120_000L)`、餘額要用上面`scenario5`同一條`projectInvestmentBalance`公式（提繳跟舊餘額分別乘不同係數，不是先加總再整筆乘）重算，66、67歲才是0——這個合併迴圈的寫法本身需要拆開，不能直接在同一個迴圈裡只改斷言數字，第一次迭代（65歲）跟後兩次迭代（66、67歲）的預期行為已經不一樣。
- 測試方法名稱裡「`contributesThrough64`」「`contributesThrough61`」「`stopsAt65`」「`compoundsThrough67`」這類暗示「上限那年沒有提繳」的命名，修正後都不再準確，實作時建議一併檢視是否要重新命名。

**這條生日切月規則只適用於iDeCo，不要加進194（企業型DC）**——企業型DC的停繳依據是「現在是不是這家公司員工」（`isRetired`），不是年齡，查證[issue-194](issue-194.md)「逐條比對」第2點已經確認DC沒有年齡制彈性：65歲如果還在職、做到年底，全年提繳本來就是對的，不需要在65歲那年另外切出生日之前跟之後兩段——DC這邊維持現狀的`age > taxAdvantagedWithdrawalAge`不動，這份issue的修正範圍完全侵限在`TaxAdvantagedAccountProjector`/`YearlySimulationPipeline`裡iDeCo那一條路徑，不觸碰DC那一半。

## 這份issue沒有解決的部分
- **「今年是不是滿停繳上限的那一年」這個新資訊，要怎麼從`YearlySimulationPipeline`傳進`TaxAdvantagedAccountProjector`**——上面「建議修正方向」第三步已經確認`idecoProrate`、`idecoMonthsForCalc`兩個判斷式都要新增涵蓋這個情境，但沒有決定具體要新增一個布林參數（類似`isRetirementYear`、`isIdecoCeilingYear`），還是用其他方式讓`TaxAdvantagedAccountProjector`知道這件事，留給實作時設計，但兩個變數都要改這一點不能漏。
- **`isIdecoCeilingYear`跟`isRetirementYear`、`age == initialAge`三者同一年重疊時的優先順序**——例如模擬起算年剛好就是滿停繳上限的那一年，這份issue只給出四個互不重疊的驗證案例，沒有窮舉所有組合，留給實作時依照「生日月跟退休月兩者取較早、起算年再扣掉已經過去的月份」這個核心原則去處理重疊情境，不是這份issue要窮舉的範圍。
- 四個測試斷言修正後的期望值（`120_000L`）已經在上面手算驗證過，但測試方法名稱要不要重新命名、`scenario2`/`scenario5`裡合併斷言的迴圈要怎麼拆開重寫、要不要新增額外的測試案例（例如模擬起算年剛好疊加滿停繳上限年的組合，這份issue的第四個驗證案例涵蓋了這個情境，但沒有對應的既有測試檔案可以直接改，需要新增），留給實作時決定。
- 要不要連同194一起排入下一輪實作時程——查證確認194完全不需要套用這條生日切月規則（DC看在職狀態不看生日），兩份issue修正範圍不重疊，但要不要在同一輪工作時程裡一起處理，留給使用者決定。


---

## 修正報告書（2026-11-25 實作完成）

### 1. 實作摘要與設計選擇
本次實作完全依照本份文件「建議修正方向」三步驟落地，並針對「如何讓 `TaxAdvantagedAccountProjector` 知道今年是滿停繳上限年」這項留待實作決定的設計點，採用了**不擴增方法簽章**的精簡寫法：
- **不改動 8 參數方法簽章（設計小技巧）**：`TaxAdvantagedAccountProjector.processAccountsForYear` 原本就已經擁有 `age`（`context.getAge()`）與 `idecoContributionCeilingAge` 兩個參數，且外層 `YearlySimulationPipeline` 定義 `isIdecoCeilingYear` 的條件即為 `age == idecoContributionCeilingAge`（兩者在呼叫前後不會變動）。因此直接在 `TaxAdvantagedAccountProjector` 內部計算 `boolean isIdecoCeilingYear = (age == idecoContributionCeilingAge);` 當作開關，即可與傳入第 9 個布林參數達到 **100% 等價效果**，同時避免更動 8 參數簽章及所有既有測試 mock／override。
- **三步驟同步落地**：
  1. 停繳判斷式由 `age >= idecoContributionCeilingAge` 改為 `age > idecoContributionCeilingAge`。
  2. `YearlySimulationPipeline` 於滿停繳上限年依 `birthDate.getMonthValue()`（同為退休年時與 `retirementMonth` 取較小值，起算年再扣掉已過去的月份，無 `birthDate` 時保守回退 `0`）覆寫 `idecoContributionMonths`。
  3. `TaxAdvantagedAccountProjector` 內部的 `idecoProrate` 與 `idecoMonthsForCalc` 兩個變數同步納入 `isIdecoCeilingYear`，確保無論是否為退休年（例如 68 歲退休、65 歲仍在職）皆正確消費覆寫後的月份數。
- **DC／iDeCo 邊界嚴格隔離**：本條生日切月規則僅套用於 iDeCo 路徑，企業型 DC（Issue 194）與 `idecoUnlocked = dcUnlocked`（Plan-09）均完全未動。

---

### 2. 修改檔案清單與變更明細

#### 產品程式碼（2 個檔案）
1. **[YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L173-L186)**
   - 在既有的 `idecoContributionMonths` 計算之後，新增 `boolean isIdecoCeilingYear = (age == idecoContributionCeilingAge);` 區塊：
     - `birthMonth == null` 時 `idecoContributionMonths = 0`。
     - 有 `birthMonth` 時，取 `cappedMonth = (isRetirementYear && retirementMonth != null) ? Math.min(birthMonth, retirementMonth) : birthMonth`；若 `age == initialAge` 則採 `Math.max(0, cappedMonth - currentMonth + 1)`，否則採 `cappedMonth`。

2. **[TaxAdvantagedAccountProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L66-L140)**
   - **[L66–L68](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L66-L68)**：新增 `boolean isIdecoCeilingYear = (age == idecoContributionCeilingAge);`，同步更新：
     - `boolean idecoProrate = (age == initialAge) || isRetirementYear || isIdecoCeilingYear;`
     - `int idecoMonthsForCalc = (isRetirementYear || isIdecoCeilingYear) ? idecoContributionMonths : monthsToAdd;`
   - **[L137–L140](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L137-L140)**：停繳判斷改為 `if ((isRetired && !isRetirementYear) || age > idecoContributionCeilingAge)`。

#### 測試程式碼（1 個檔案）
- **[YearlySimulationPipelineIdecoLifecycleTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java#L184-L520)**
  - 更新 `scenario1`、`scenario3`、`scenario6`：滿上限／退休年斷言改為 `120_000L` 並更新方法名稱。
  - 拆開 `scenario2`（65 退／68 領）與 `scenario5`（68 退／65 滿上限）的 65～67 歲合併迴圈：65 歲斷言 `120_000L` 並依 `projectInvestmentBalance` 公式重算複利餘額，66～67 歲斷言 `0L` 並接續複利滾動。
  - 新增 3 個邊界測試案例：
    - `decemberRetirementModeAt65_capsIdecoAtBirthMonth`（65 歲退休、`DECEMBER` 模式、6 月生日 → `120_000L`）
    - `initialYearIsCeilingYear_proratesFromCurrentMonthToBirthMonth`（起算年即滿上限年，4 月起算、6 月生日 → `60_000L`）
    - `ceilingYearWithMissingBirthDate_zeroesContribution`（無 `birthDate` 時滿上限年回退 `0L`）

---

### 3. 驗證結果
- 既有測試與更新／新增後的 `YearlySimulationPipelineIdecoLifecycleTest` 均已於本機執行通過。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                          10015  13.07G   5.21    20:52:19  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
index 721c8525..0aba7781 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java
@@ -63,8 +63,9 @@ public class TaxAdvantagedAccountProjector {
         // However, this is a conservative error; the only consequence is that the user
         // will receive a larger-than-expected tax refund during the actual year-end tax
         // adjustment.
-        boolean idecoProrate = (age == initialAge) || isRetirementYear;
-        int idecoMonthsForCalc = isRetirementYear ? idecoContributionMonths : monthsToAdd;
+        boolean isIdecoCeilingYear = (age == idecoContributionCeilingAge);
+        boolean idecoProrate = (age == initialAge) || isRetirementYear || isIdecoCeilingYear;
+        int idecoMonthsForCalc = (isRetirementYear || isIdecoCeilingYear) ? idecoContributionMonths : monthsToAdd;
 
         long remainingMonthsIdecoContribution = 0;
         for (SimulationFund fund : context.getIdecoFunds()) {
@@ -133,7 +134,7 @@ public class TaxAdvantagedAccountProjector {
         boolean dcAllowed = true;
         boolean idecoAllowed = true;
 
-        if ((isRetired && !isRetirementYear) || age >= idecoContributionCeilingAge) {
+        if ((isRetired && !isRetirementYear) || age > idecoContributionCeilingAge) {
             remainingMonthsIdecoContribution = 0L;
             idecoAllowed = false;
         }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index 5ef0a2af..190ec556 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -170,6 +170,20 @@ public class YearlySimulationPipeline {
                 idecoContributionMonths = retirementMonth;
             }
         }
+        boolean isIdecoCeilingYear = (age == idecoContributionCeilingAge);
+        if (isIdecoCeilingYear) {
+            Integer birthMonth = (birthDate != null) ? birthDate.getMonthValue() : null;
+            if (birthMonth == null) {
+                idecoContributionMonths = 0;
+            } else {
+                int cappedMonth = (isRetirementYear && retirementMonth != null)
+                        ? Math.min(birthMonth, retirementMonth)
+                        : birthMonth;
+                idecoContributionMonths = (age == initialAge)
+                        ? Math.max(0, cappedMonth - currentMonth + 1)
+                        : cappedMonth;
+            }
+        }
 
         int dcContributionMonths = monthsToAdd;
         if (isRetirementYear) {
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
index 2d291841..a14c442d 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
@@ -182,14 +182,14 @@ class YearlySimulationPipelineIdecoLifecycleTest {
     }
 
     /**
-     * Scenario 1: Retire at 65, userDeclaredWithdrawalAge = null.
-     * - Ages 60..64: annualIdecoContribution > 0 (240,000 each year) and balance
+     * Scenario 1: Retire at 65, userDeclaredWithdrawalAge = null, June birthday.
+     * - Ages 60..64: annualIdecoContribution = 240,000 each year and balance
      * accumulates.
-     * - Age 65: contribution stops (0) and DC & iDeCo unlock together at 65
-     * (Plan-09 preserved).
+     * - Age 65: ceiling & retirement year (June) -> prorated 6 months (120,000),
+     * and DC & iDeCo unlock together at 65.
      */
     @Test
-    void scenario1_retireAt65_nullWithdrawalAge_contributesThrough64_unlocksTogetherAt65() {
+    void scenario1_retireAt65_nullWithdrawalAge_contributesThrough64_proratesAt65AndUnlocks() {
         int initialAge = 59;
         int targetRetirementAge = 65;
         SimulationContext context = createContextWithIdeco(
@@ -212,23 +212,25 @@ class YearlySimulationPipelineIdecoLifecycleTest {
         // 6 years (59..64) * 240,000 + 1,000,000 = 2,440,000
         assertThat(context.getIdecoBalance()).isEqualTo(2_440_000L);
 
-        // Age 65: stops contributing and unlocks both DC and iDeCo together
+        // Age 65: prorated to June (6 months = 120,000) and unlocks both DC and iDeCo
+        // together
         pipeline.processSimulationYear(
                 context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                 YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);
-        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(0L);
+        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(120_000L);
         assertThat(context.getIdecoBalance()).isEqualTo(0L);
         assertThat(context.getDcBalance()).isEqualTo(0L);
     }
 
     /**
-     * Scenario 2: Retire at 65, userDeclaredWithdrawalAge = 68.
-     * - Ages 60..64: normal contribution.
-     * - Ages 65..67: 0 contribution, balance continues compounding.
+     * Scenario 2: Retire at 65, userDeclaredWithdrawalAge = 68, June birthday.
+     * - Age 65 (ceiling & retirement year): prorated 6 months (120,000), balance
+     * compounds.
+     * - Ages 66..67: 0 contribution, balance continues compounding.
      * - Age 68: 0 contribution, unlocks at 68.
      */
     @Test
-    void scenario2_retireAt65_withdrawalAt68_stopsAt65_compoundsThrough67_unlocksAt68() {
+    void scenario2_retireAt65_withdrawalAt68_proratesAt65_compoundsThrough67_unlocksAt68() {
         int initialAge = 64;
         int targetRetirementAge = 65;
         SimulationContext context = createContextWithIdeco(
@@ -244,7 +246,16 @@ class YearlySimulationPipelineIdecoLifecycleTest {
         assertThat(projector.contributionsByAge.get(64).annualIdecoContribution()).isEqualTo(240_000L);
         long expected = context.getIdecoBalance();
 
-        for (int age = 65; age <= 67; age++) {
+        // Age 65: prorated 6 months (120,000)
+        pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);
+        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(120_000L);
+        expected = Math.round(expected * 1.10 + 120_000L * 1.05);
+        assertThat(context.getIdecoBalance()).isEqualTo(expected);
+
+        // Ages 66..67: 0 contribution, pure compounding
+        for (int age = 66; age <= 67; age++) {
             pipeline.processSimulationYear(
                     context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                     YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, age);
@@ -261,12 +272,13 @@ class YearlySimulationPipelineIdecoLifecycleTest {
     }
 
     /**
-     * Scenario 3: Retire at 62, userDeclaredWithdrawalAge = 62.
+     * Scenario 3: Retire at 62, userDeclaredWithdrawalAge = 62, June birthday.
      * - Ages 60, 61: normal contribution.
-     * - Age 62: ceiling reached (62 >= 62), stops contributing and unlocks at 62.
+     * - Age 62: ceiling & retirement year (June) -> prorated 6 months (120,000) and
+     * unlocks at 62.
      */
     @Test
-    void scenario3_retireAt62_withdrawalAt62_contributesThrough61_stopsAndUnlocksAt62() {
+    void scenario3_retireAt62_withdrawalAt62_contributesThrough61_proratesAndUnlocksAt62() {
         int initialAge = 60;
         int targetRetirementAge = 62;
         SimulationContext context = createContextWithIdeco(
@@ -286,7 +298,7 @@ class YearlySimulationPipelineIdecoLifecycleTest {
         pipeline.processSimulationYear(
                 context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                 YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 62);
-        assertThat(projector.contributionsByAge.get(62).annualIdecoContribution()).isEqualTo(0L);
+        assertThat(projector.contributionsByAge.get(62).annualIdecoContribution()).isEqualTo(120_000L);
         assertThat(context.getIdecoBalance()).isEqualTo(0L);
     }
 
@@ -389,14 +401,15 @@ class YearlySimulationPipelineIdecoLifecycleTest {
     }
 
     /**
-     * Scenario 5: Delayed retirement at 68, userDeclaredWithdrawalAge = null.
-     * - Ages 60..64: normal contribution.
-     * - Ages 65..67: 0 contribution (statutory 65 ceiling), balance still grows via
-     * compounding.
+     * Scenario 5: Delayed retirement at 68, userDeclaredWithdrawalAge = null, June
+     * birthday.
+     * - Age 65: ceiling year (not retirement year) -> prorated to birth month (6
+     * months = 120,000), compounds.
+     * - Ages 66..67: 0 contribution, balance continues compounding.
      * - Age 68: unlocks at 68.
      */
     @Test
-    void scenario5_delayedRetirementAt68_nullWithdrawalAge_stopsAt65_compounds65To67_unlocksAt68() {
+    void scenario5_delayedRetirementAt68_nullWithdrawalAge_proratesAt65_compounds65To67_unlocksAt68() {
         int initialAge = 64;
         int targetRetirementAge = 68;
         SimulationContext context = createContextWithIdeco(
@@ -412,7 +425,16 @@ class YearlySimulationPipelineIdecoLifecycleTest {
         assertThat(projector.contributionsByAge.get(64).annualIdecoContribution()).isEqualTo(240_000L);
         long expected = context.getIdecoBalance();
 
-        for (int age = 65; age <= 67; age++) {
+        // Age 65: ceiling year -> prorated 6 months (120,000)
+        pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, 65);
+        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(120_000L);
+        expected = Math.round(expected * 1.10 + 120_000L * 1.05);
+        assertThat(context.getIdecoBalance()).isEqualTo(expected);
+
+        // Ages 66..67: 0 contribution, pure compounding
+        for (int age = 66; age <= 67; age++) {
             long prevBalance = context.getIdecoBalance();
             pipeline.processSimulationYear(
                     context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
@@ -431,12 +453,13 @@ class YearlySimulationPipelineIdecoLifecycleTest {
 
     /**
      * Scenario 6: Retire at 60, userDeclaredWithdrawalAge = 60 (earliest unlock
-     * age).
+     * age), June birthday.
      * - Age 59: contributes normally.
-     * - Age 60: 0 contribution and unlocks immediately at 60.
+     * - Age 60: ceiling & retirement year (June) -> prorated 6 months (120,000) and
+     * unlocks at 60.
      */
     @Test
-    void scenario6_retireAt60_withdrawalAt60_stopsAndUnlocksAt60() {
+    void scenario6_retireAt60_withdrawalAt60_proratesAndUnlocksAt60() {
         int initialAge = 59;
         int targetRetirementAge = 60;
         SimulationContext context = createContextWithIdeco(
@@ -454,7 +477,67 @@ class YearlySimulationPipelineIdecoLifecycleTest {
         pipeline.processSimulationYear(
                 context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                 YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 60);
-        assertThat(projector.contributionsByAge.get(60).annualIdecoContribution()).isEqualTo(0L);
+        assertThat(projector.contributionsByAge.get(60).annualIdecoContribution()).isEqualTo(120_000L);
         assertThat(context.getIdecoBalance()).isEqualTo(0L);
     }
+
+    /**
+     * Issue 196: Retire at 65 under DECEMBER mode with June birthday -> capped at
+     * birthMonth (6 months = 120,000).
+     */
+    @Test
+    void decemberRetirementModeAt65_capsIdecoAtBirthMonth() {
+        int initialAge = 65;
+        int targetRetirementAge = 65;
+        SimulationContext context = createContextWithIdeco(
+                2026, initialAge, 1_000_000L, 0L, 0.0,
+                LocalDate.of(1961, 6, 15), RetirementMonthMode.DECEMBER, null);
+        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
+                context.getProfile(), 60, 60, null, targetRetirementAge);
+
+        pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 65, initialAge, 65);
+        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(120_000L);
+    }
+
+    /**
+     * Issue 196: Initial year == ceiling year (age 65, April start, June birthday)
+     * -> 6 - 4 + 1 = 3 months (60,000).
+     */
+    @Test
+    void initialYearIsCeilingYear_proratesFromCurrentMonthToBirthMonth() {
+        int initialAge = 65;
+        int targetRetirementAge = 68;
+        SimulationContext context = createContextWithIdeco(
+                2026, initialAge, 1_000_000L, 0L, 0.0,
+                LocalDate.of(1961, 6, 15), RetirementMonthMode.BIRTH_MONTH, null);
+        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
+                context.getProfile(), 60, 60, null, targetRetirementAge);
+
+        pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 4), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, 65);
+        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(60_000L);
+    }
+
+    /**
+     * Issue 196: Ceiling year with missing birthDate -> conservatively zeroes
+     * contribution.
+     */
+    @Test
+    void ceilingYearWithMissingBirthDate_zeroesContribution() {
+        int initialAge = 65;
+        int targetRetirementAge = 68;
+        SimulationContext context = createContextWithIdeco(
+                2026, initialAge, 1_000_000L, 0L, 0.0,
+                null, RetirementMonthMode.BIRTH_MONTH, null);
+        int withdrawalAge = SimulationParameterResolver.resolveTaxAdvantagedWithdrawalAge(
+                context.getProfile(), 60, 60, null, targetRetirementAge);
+
+        pipeline.processSimulationYear(
+                context, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
+                YearMonth.of(2026, 1), 60, 60, withdrawalAge, targetRetirementAge, 68, initialAge, 65);
+        assertThat(projector.contributionsByAge.get(65).annualIdecoContribution()).isEqualTo(0L);
+    }
 }
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                               10016  12.50G   4.93    20:54:20  