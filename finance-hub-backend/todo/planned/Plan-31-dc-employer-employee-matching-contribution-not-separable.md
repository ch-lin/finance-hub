# Plan 31：企業型DC的「公司提繳」跟「員工自己加碼matching」現狀是互斥的二選一，不能同時並存、也不能只關掉其中一半——使用者想模擬「公司繼續提繳、我自己不跟著matching了」這種情境，現在完全無法表達

## 這份計畫怎麼來的
這是在[issue-194](issue-194.md)（企業型DC退休當年提繳整年歸零）查證過程中，使用者主動提出的新問題——「DC支不支援公司提繳跟使用者同時matching，以及公司提繳但使用者不matching加碼」。查證確認現狀完全不支援這兩種情境，不是部分支援或有小缺口，是架構上就寫成互斥的二選一。

## 相關檔案
- [DefinedContributionContextBuilder.java:39-105](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java)（問題核心所在，`if (latestDcOpt.isPresent())` / `else if (dcSettingsOpt...)` 這個互斥結構）
- [DefinedContributionUserSettings.java:51-52](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java)（`monthlyDcMatchingContribution`欄位本身，查證確認存在、有資料庫欄位、使用者檔案設定頁可以填，但這份issue查到的問題是：填了也沒用）
- [DefinedContributionAccountSnapshot.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)、[DefinedContributionFundSnapshot.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionFundSnapshot.java)（真實帳戶快照，`monthlyContributionAmount`這個欄位是雇主+員工提繳的**合計總額**，不是分開存的兩個數字）
- [SompoDcAllocationParserImpl.java:81-100](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)（配分頁parser，查證確認`monthlyEmployerContribution`（雇主）、`monthlySubscriberContribution`（員工）這兩個欄位本身**有分開解析**，只是帳戶快照往下傳給模擬引擎時，`SimulationFund`只拿得到合計後的`monthlyContributionAmount`，分開的這兩個數字在到達模擬引擎之前就被合併，沒有被帶進去）
- [SimulationFund.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)（模擬引擎內部使用的基金物件，查證確認欄位清單裡只有`annualContribution`/`settingAmount`這種單一總額欄位，沒有拆成雇主/員工兩塊的欄位）

## 現狀：查證確認是互斥的二選一架構，不是「部分支援」
查證[DefinedContributionContextBuilder.initializeDefinedContributionContext](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java)（第39-105行）確認，DC這一段是一個`if (latestDcOpt.isPresent())` / `else if (...)`結構：

**分支一：已經有真實DC帳戶快照時**（第39-81行，也是絕大多數有在用這個功能的使用者會走到的分支）——`SimulationFund`的提繳金額（`annualContribution`、`settingAmount`）完全來自`record.getMonthlyContributionAmount()`（第62-64行），這是`DefinedContributionFundSnapshot`上的欄位，查證[SompoDcAllocationParserImpl.java:96-100](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)確認這個數字是`monthlyEmployerContribution + monthlySubscriberContribution`算出來的**合計總額**。`DefinedContributionUserSettings.monthlyDcMatchingContribution`這個使用者自己填的設定值，在這個分支裡**完全不會被讀取**——不是被加總後吃掉，是程式碼結構上這個分支根本沒有任何一行去呼叫`dcSettingsOpt.get().getMonthlyDcMatchingContribution()`。

**分支二：沒有真實DC帳戶快照時**（第82-104行，`else if`）——才會去讀`monthlyDcMatchingContribution`，用使用者自己填的數字，建一個全新的、完全虛構的`SimulationFund`（第95-104行，`fundId=0L`、`fundName="Corporate DC (Profile Setting)"`，餘額固定是0）。這不是跟真實帳戶疊加，是在「完全沒有真實帳戶資料」這個前提下，讓使用者純手動模擬一個假設性的DC帳戶。

這代表：**一旦使用者有真實DC帳戶快照（走分支一），`monthlyDcMatchingContribution`這個欄位就形同廢欄位，填了也不會被模擬引擎看見**；只有完全沒有真實帳戶資料時（走分支二），這個欄位才會被用到，但那時候用的是一個虛構帳戶，不是跟真實提繳疊加。

## 為何是問題
查證確認使用者想模擬的兩種情境，現狀都無法表達：

1. **「公司提繳＋我自己也加碼matching，兩者同時算」**：如果使用者已經有真實DC帳戶快照（絕大多數有在用這個功能的人都是這個情況），`monthlyDcMatchingContribution`這個欄位完全不會被讀取——使用者在自己檔案設定頁填了這個數字，以為會疊加到真實帳戶的提繳金額上，但模擬引擎實際上連看都沒看這個欄位一眼。

2. **「公司提繳繼續，但我自己不跟著matching了」**：現在的`SimulationFund`提繳金額是`monthlyContributionAmount`這一個合計總額，從parser解析階段就已經把雇主、員工兩塊數字加總壓扁成一個數字，往下傳到模擬引擎時已經無法拆開——沒有任何欄位可以表達「把員工那一半關掉，雇主那一半繼續算」這種情境。即使查證確認parser本身（[SompoDcAllocationParserImpl.java:81-90](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)）有分開解析出`monthlyEmployerContribution`、`monthlySubscriberContribution`這兩個數字，這兩個獨立數字也只停留在帳戶快照層級（[DefinedContributionAccountSnapshot.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)上的帳戶層級欄位，不是fund層級），`SimulationFund`建構時完全沒有被帶進去。

## 具體錯誤情境
使用者的企業型DC帳戶，公司每月提繳3萬円（`monthlyEmployerContribution`，真實事實），使用者自己原本每月加碼matching 1萬円（`monthlySubscriberContribution`，一樣是真實事實，Sompo配分頁也確實解析出來了）——這兩個數字加總後的`monthlyContributionAmount=40000`正確存進帳戶快照。使用者現在想在模擬器裡測試「如果我不再自己加碼matching，只靠公司提繳，30年後資產會差多少」這個情境，打開`DefinedContributionUserSettings`相關設定頁，卻找不到任何輸入框可以單獨調整「員工那一半」——唯一相關的欄位`monthlyDcMatchingContribution`填了也沒用（因為已經有真實帳戶快照，直接走分支一，這個欄位被完全忽略），使用者沒有任何管道可以表達這個假設性問題。

## 建議修正方向
這裡列出需要先確認的架構決策，不代為決定：

1. **帳戶快照層級要不要把雇主/員工提繳分開存到fund層級**：現狀`monthlyEmployerContribution`/`monthlySubscriberContribution`只存在帳戶快照層級（整個帳戶一個數字），不是每支基金各自的雇主/員工提繳（因為配分是依比例分配到各基金，雇主/員工的提繳比例可能跟基金配分比例不是同一套邏輯——這跟[issue-189](../pending%20fix/issue-189.md)查過的`allocationPercentage`問題性質類似，需要先確認真實網頁上雇主/員工的配分邏輯)。這份Plan不代為假設這個資料結構該怎麼設計。
2. **`SimulationFund`需不需要新增欄位，把`annualContribution`拆成雇主/員工兩塊**：如果要支援「關掉員工那一半、只留雇主那一半」這種sandbox what-if情境，`SimulationFund`至少需要知道這筆提繳裡哪部分是雇主出的、哪部分是員工出的，才能在`FundOverrideApplier`（既有的what-if覆寫元件）裡新增對應的覆寫邏輯。
3. **`monthlyDcMatchingContribution`這個使用者設定欄位的定位要重新想清楚**：現狀這個欄位的設計意圖（從`DefinedContributionContextBuilder`分支二的用法看）是「完全沒有真實帳戶時，讓使用者自己假設一個DC帳戶」，不是「跟真實帳戶疊加的員工自選加碼」。如果使用者想要的是後者（真實帳戶已存在時，額外模擬「如果我把matching金額改成X元」這種情境），正確的做法可能不是修這個欄位的讀取邏輯，是比照[Plan-29](Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md)的模式——新增一個sandbox what-if覆寫（`SimulationOverrides`新增對應欄位），不碰真實帳戶資料，只在模擬沙盒內部套用。

## 這份計畫沒有解決的部分
- 真實網頁上，雇主提繳跟員工matching提繳，在配分到各基金時遵循的是不是同一套比例邏輯——這份Plan沒有查證，需要使用者提供真實樣本確認。
- 上面三個建議方向要選哪一個、要不要疊加多個方向一起做，留給使用者決定。
- `monthlyDcMatchingContribution`這個既有欄位，如果確認定位是「完全沒有真實帳戶時的假設性模擬」，要不要保留現狀不動、只另外新增一個獨立的sandbox覆寫機制——這份Plan不代為判斷要不要同時存在兩套機制。
- 要不要現在就投入實作，還是繼續留在planned狀態等待排入時程，留給使用者決定。
