# Fixed 110：海外生活費在換學制年度沒有按月比例分攤，每個跨年學制都會被重複灌爆金額；補習費改為從教育設定移除

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板決定兩件事：
1. **海外生活費**：不是修正現有的「年費÷12×重疊月數」除法邏輯，而是把資料存儲方式本身改成月費——使用者指出，現實中查到的生活費資訊通常都是「一年多少錢」這種年度單位（例如網路上留學資訊常見的「英國留學一年生活費OO萬」），但既然最後存進系統時終究要換算成月，不如就比照學費（`annualTuition`）換算成每期金額存下來的既有模式，直接在使用者輸入年費的當下，就把年費除以12换算成月費存進系統，後續所有計算都直接用這個月費乘以月數，不需要在每次計算時重新做一次「年費÷12」的除法。
2. **補習費（`annualJukuCost`）**：直接從教育設定裡完全移除，不修正比例分攤問題。理由見下方「為什麼補習費跟生活費分開處理」。

## 為什麼補習費跟生活費分開處理（使用者的判斷依據）
補習費跟生活費原本被同一段程式碼（`isActiveInYear`判斷）一起處理，且原提案是兩者都套用同一套按月比例分攤修正。但使用者指出兩者的性質完全不同：

- **補習費**：計費標準因國家而異（使用者是以台灣經驗設想——補習費通常是「一學期/一年繳多少就是多少」，不會因為只讀4個月就打折；但這個假設本身能不能推廣到日本或其他國家的補習班收費慣例，並沒有查證到明確依據，且使用者也已經認知到「我沒在日本補過，是以台灣經驗猜的」），而且要不要補習、補多久，本質上是十幾年後才會知道的變數，系統無法提前預測。這個欄位如果要支援clone到不同國家使用，會因為各國補習費計費標準不同，變成很大的維護問題。
- **生活費**：是「一定會發生的固定支出」（只要小孩在海外讀書，就一定要吃住），性質跟學費/入學金一樣，只是換學制的邊界要抓對（可能畢業就直接離開那個國家，不會延續到下一個階段），不存在「要不要發生」的不確定性，也沒有跨國計費標準不一致的問題。

**結論：既然補習費本身的計費標準跨國不一致、且要不要補習根本無法預測，不如直接把這個欄位從教育設定裡拿掉，改成使用者如果真的需要模擬補習費，可以自行用系統既有的通用支出事件機制（`LifeEvent`）自己設定起訖時間跟金額，不需要教育模組專門為這個高度不確定、跨國標準不一的支出維護一套邏輯。**

## 相關檔案

### 生活費修正部分
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java:212-249`（`resolveUniversity`，`livingCost`目前是「年費」——來自`config.getUniversity().getLivingCost()`這個系統預設值，或`settings.getManualLivingCost()`這個使用者覆寫值，兩者都是使用者以年為單位輸入／設定的金額，**這裡要新增「除以12換算成月費」這一步**，換算後的月費才繼續往下傳進`mergeStage`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java:252-258`（`mergeStage`，目前的參數命名跟`ResolvedStageData`建構子都是`livingCost`直接傳入年費；改成月費後，這裡的變數命名／欄位命名建議一併更新成`monthlyLivingCost`，避免後續維護者誤以為這裡存的還是年費）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java:54`（`annualLivingCost`欄位，改成`monthlyLivingCost`，存的值改成月費）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java:86`（把`stageData.getAnnualLivingCost()`帶進`StageInterval`的地方，改成讀取月費欄位）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java:60`（`StageInterval`這個record裡的`annualLivingCost`欄位，改成`monthlyLivingCost`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java:53-75,90-136`（`calculateAnnualCost`跟`calculateCostForRemainingYear`裡，原本對生活費的處理方式全部改成「月費×重疊月數」的乘法，不再需要`annualLivingCost / 12`這個除法步驟；`calculateAnnualCost`目前完全沒有計算重疊月數的邏輯，這裡要新增跟`calculateCostForRemainingYear`裡`activeMonths`相同的重疊月數計算）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java:160-186`（`buildAnnualCostMap`，第174行目前的判斷條件讓`calculateAnnualCost`跟`calculateCostForRemainingYear`維持兩個獨立方法、依`year == currentYear`與否分別呼叫；改成月費計算後，`calculateAnnualCost`本身就會正確處理重疊月數，兩個方法是否需要繼續維持獨立、還是可以合併，屬於實作細節，不影響最終算出的金額）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImpl.java`（`getEducationProjections()`，使用者畫面上的「Estimate」試算圖表，目前呼叫的是沒有按比例分攤的舊版方法，換成月費計算後這裡自然會一併修正）
- `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java`（測試裡斷言`annualLivingCost`欄位數值的地方，需要同步改成斷言月費數值）

### 補習費移除部分
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java:55`、`HighConfig.java:55`（`jukuCost`欄位，國中/高中兩個學制設定裡的預設補習費）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java:142-161,177-196,308,324`（`annualJukuCost`的解析邏輯：預設值來自`MiddleConfig`/`HighConfig`，使用者可用`middleManualJukuCost`/`highManualJukuCost`覆寫）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java:53`（`annualJukuCost`欄位本身）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java:85`（把`annualJukuCost`帶進`StageInterval`）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/ChildEducationSettings.java`（`middleManualJukuCost`、`highManualJukuCost`兩個使用者可覆寫欄位）
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:566-610`（前端「Manual Annual Juku Cost」輸入框，國中、高中兩處）

## 生活費：現狀
`calculateAnnualCost`方法在處理生活費（`annualLivingCost`）時，判斷邏輯只有「這個學制階段的區間，有沒有跟目標年份重疊到（哪怕只重疊一個月）」——只要重疊到，就把這個階段整整一年的生活費金額，全部加進這個目標年份裡，不管實際重疊了幾個月。

## 生活費：為何是問題
`Plan-01.md`這次重構的兩個核心目標之一，正是「換學制那一年不該整年只算一種學制」——已經正確解決了學費（`tuition`）跟入學金（`entranceFee`）這兩種按月拆分的費用。但生活費這種「整年一筆」的附加費用，走的是另一套判斷邏輯（`isActiveInYear`這種粗略的「有沒有重疊到」判斷），沒有跟著套用同樣精確的月份拆分，導致這次重構解決了學費的算錯問題，卻在生活費上留下一個新的、性質完全相同的問題。這裡要解決的目標不變（換學制年度要按實際重疊月數計算），只是下面「修正方向」改成從資料存儲方式本身下手，不是在既有的判斷邏輯上修補。

## 生活費：具體錯誤情境
一個小孩讀海外學制的大學（英國制3年，年繳生活費¥1,500,000），開學是9月，讀3學年（36個月）。假設這個階段的區間是2030年9月到2033年8月：
- 2030年：這個階段只有9月到12月（4個月）真正在讀，正確應該只算¥1,500,000×4/12=¥500,000，但系統會算成整整¥1,500,000。
- 2031年、2032年：整年都在讀，各算¥1,500,000，這兩年是正確的。
- 2033年：這個階段只有1月到8月（8個月）真正在讀，正確應該只算¥1,500,000×8/12=¥1,000,000，但系統會算成整整¥1,500,000。

真實3年總生活費應該是¥4,500,000（500,000+1,500,000+1,500,000+1,000,000），系統目前會算成¥6,000,000，多算了¥1,500,000，高估了33%。畢業後小孩通常會直接離開那個國家，不會延續到下一個學制階段，所以這兩年的多算金額不會被下一階段的欠帳吸收，是實打實的高估。

## 生活費：修正方向——換算成月費存起來，不是修正除法邏輯本身
`calculateCostForRemainingYear`裡其實已經寫好了正確的按月比例分攤邏輯（`activeMonths`算出實際重疊月數，再用`annualLivingCost / 12 * activeMonths`算出正確的分攤金額），原本的思路是把這套邏輯也套用到`calculateAnnualCost`本身。但使用者提出一個更簡單的替代方向：**既然學費（`annualTuition`）本身也是使用者以年為單位輸入，系統早就已經在`EducationTimelineProjector.generateStageEvents`（第234-254行）把年費換算成每期金額（`termTuition = annualTuition / termsPerYear`）之後才存下來、拿去產生一筆一筆的繳費事件，生活費完全可以比照同一個模式：使用者一樣以年為單位輸入或設定（因為現實中查到的生活費資訊通常是「一年多少錢」這種年度單位），但存進系統的當下，直接除以12換算成月費存下來，後面所有計算都直接用「月費×重疊月數」的乘法，不需要在每一次計算生活費時，都重新去做「年費÷12」這個除法**。

這樣做的好處：
- 不需要在`calculateAnnualCost`跟`calculateCostForRemainingYear`之間維護兩套「要不要按比例分攤」的判斷邏輯（原本的方向仍然要處理`buildAnnualCostMap`裡`year == currentYear`這個判斷條件何時該呼叫哪個方法），換成月費後，兩個方法都只是單純的「月費×月數」乘法，行為天然一致，不會有「只有模擬起始那一年才正確分攤」這類遺漏的風險。
- 跟學費、入學金的處理方式统一——教育模組裡所有跟金額有關的欄位，都是「以使用者好理解的年為單位輸入／顯示，但實際存儲與計算都用月為單位」，不會有「這個欄位存的是年費、那個欄位存的是月費」這種混合心智負擔。

## 補習費：移除範圍
把上方「相關檔案」列出的補習費相關欄位、解析邏輯、前端輸入框全部移除：
- 後端：`MiddleConfig`/`HighConfig`的`jukuCost`預設值欄位、`ChildEducationSettings`的`middleManualJukuCost`/`highManualJukuCost`使用者覆寫欄位、`EducationProfileResolver`裡對應的解析邏輯、`ResolvedStageData`/`StageInterval`裡的`annualJukuCost`欄位、`EducationExpenseCalculator`裡對`annualJukuCost`的加總邏輯。
- 前端：`child-profile-section.tsx`裡兩處「Manual Annual Juku Cost」輸入框。
- 既有資料相容性：如果資料庫裡已經有使用者填過的`middleManualJukuCost`/`highManualJukuCost`數值，這次移除欄位後這些資料會直接消失，屬於預期中的行為（欄位本身被移除），不需要額外的資料遷移或警告機制。

## 跟LifeEvent的關係
使用者原本設想「使用者需要模擬補習費的話，自行用系統既有的`LifeEvent`（通用支出事件）機制設定」。查證後發現`LifeEvent`的`YEARLY`頻率類型目前的比例分攤方式不夠精確，使用者已決定直接把`LifeEvent`升級成月級別計算（見[feature-requirement-130](feature-requirement-130.md)），不是修一個獨立的bug——跟這裡的補習費移除決定是兩件獨立的事，不管`LifeEvent`升級什麼時候做，都不影響這裡「把補習費從教育設定移除」這個決定本身。

---

## 【最終拍板定案：全系統底層「純月費」架構藍圖與分階段實作細節】（2026-10 討論校準定案）

> **⚠️ 閱讀指引（Single Source of Truth）**：
> 上方原文為早期與 Claude 討論時的過程草稿。當時草稿殘留了「在後端 Resolver 做除法」的舊提案痕跡，且遺漏了補習費移除的完整全域鏈路。
> 為避免實作範圍膨脹混淆，**本 Issue 明確拆分為兩個獨立階段：【階段一：海外生活費改純月費】與【階段二：補習費全系統徹底拔除】**。
> **後續所有實作與驗收，一律以本章節為唯一權威依據！**

---

### 一、 核心架構哲學：比照 Plan-01 的 Duration 模式，全系統底層純月費

在 [Plan-01.md](Plan-01.md) 中，就讀時長（Duration）確立了一個極為成功的模式：
- **使用者體感**：前端畫面上填寫直覺的「年」（例如 3 年、4 年、6 年）。
- **前端轉換**：在前端輸入與預覽的當下，自動換算為「月」（`yearsToMonths(years) = years * 12`）送進 API；從 API 讀取後乘回 12 呈現給使用者。
- **後端與底層**：資料庫、Entity、DTO、Resolver、Projector、Calculator **全鏈路 100% 只存「月」，後端完全不存「年」，徹底杜絕單位混亂**。

**海外生活費（Living Cost）完全比照此模式辦理：**
1. **前端（UI & Hook）進行年月轉換**：
   - 使用者在前端看到與填寫的依然是直覺的「每年生活費 (¥/year)」，但前端提供即時的「每月折算預覽」（例如輸入 1,500,000 円/年，下方即時提示「約 ¥125,000 / 月」）。
   - **送出 API 前**：前端自動除以 12 並**無條件進位至整數円**（`manualLivingCost = Math.ceil(annual / 12)`），貫徹財務規劃「保守預算原則（寧可多抓預算，不可低估吃土）」，且日幣實體無小數。
   - **從 API 讀取後**：前端自動乘回 12 呈現給使用者填寫框。
2. **後端全鏈路只存「月生活費」**：
   - 全域設定（`application.properties`）：預設大學生活費直接改設為月費（例如原本 ¥1,500,000/年 改為 **¥125,000/月**）。
   - 後端領域模型（`ChildEducationSettings.manualLivingCost`、`UniversityConfig.livingCost`）全都是月費。
   - `EducationProfileResolver`：全域 Config 是月費，使用者覆寫也是月費，Resolver **直接合併，完全不需要做任何除以 12 的除法**！
3. **計算引擎：看有幾個月就加幾個月的生活費**：
   - `EducationExpenseCalculator`：計算目標西元年時，先計算該年該階段實際重疊的月份數 `activeMonths`，接著直接做單純乘法：
     $$\text{年度生活費} = \text{monthlyLivingCost} \times \text{activeMonths}$$
     不再有任何除以 12，杜絕小數精度遺失，徹底解決跨年多算整年的 Bug！

---

### 二、 分階段實作計畫與精準修改清單

#### 【階段一】生活費：全鏈路「純月費」實作清單（精簡、高內聚）

*範圍說明：生活費實作範圍其實非常輕量，後端核心命名重構已全部完成並編譯通過，僅剩 Calculator 計算乘法、設定檔預設值改為 125000，以及前端 1 個輸入框的換算。*

1. **後端核心 Pipeline（已透過 IDE 重構完成 ✅）**：
   - [x] [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)：`annualLivingCost` 已重命名為 `monthlyLivingCost`。
   - [x] [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)：第 86 行已改為帶入 `stageData.getMonthlyLivingCost()`。
   - [x] [EducationAbsoluteTimeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java)：`StageInterval` record 的欄位已改為 `monthlyLivingCost`。
   - [x] [EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)：`mergeStage` 參數已重命名為 `monthlyLivingCost`，合併時不作除法。
   - [x] [UniversityConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java)：JavaDoc 已明確標註為月費（Monthly Living Cost）。
   - [x] [ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)：JavaDoc 已明確標註為月費。

2. **後端計算引擎（已完成調整 ✅）**：
   - [x] [EducationExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java)：
     - `calculateAnnualCost`：補上重疊月份計算，改為 `monthlyLivingCost.multiply(BigDecimal.valueOf(activeMonths))`。
     - `calculateCostForRemainingYear`：移除原本多餘的 `/ 12`，改為直接 `monthlyLivingCost.multiply(BigDecimal.valueOf(activeMonths))`。

3. **後端全域預設值（已完成修改 ✅）**：
   - [x] [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：
     - 將 `finance-hub.education-config.university.living-cost` 預設金額從年費 `1500000` 改為月費 `125000`。
     *(註：`EducationConfigDefaultProperties` 與 `EducationConfigResult` 欄位本來就叫 `livingCost`，純透傳無須改動程式碼)*

4. **後端單元測試（已完成補充 ✅）**：
   - [x] [EducationExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java)：新增生活費依月份重疊數分攤之測試案例。
   - [x] [EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)：mock config 的預設生活費同步配合改為 125,000。

5. **前端 UI 與轉換層（已完成調整 ✅）**：
   - [x] [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)：
     - 大學卡片 `manualLivingCost`：輸入框維持填寫年費（¥/year），並顯示即時換算的月費提示。
     - 讀取時乘回 12 填入，送出前除以 12 並無條件進位（`Math.ceil(annual / 12)`）存為月費。
   - [x] [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)：
     - `ChildEducationSettingsDto` 的 `manualLivingCost` 註解明確為月費。

---

#### 【階段二】補習費（JukuCost）：徹底移除之完整清單（乾淨連根拔除）

*範圍說明：這部分是文件顯得較長的主要原因，因為補習費在系統中曾有全套 CRUD。未來統一改由 LifeEvent 取代，故從教育模組徹底移除。*

1. **後端全域設定（Config DTO / Entity / Factory）（已完成刪除 ✅）**：
   - [x] [MiddleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java)（刪除 `jukuCost`）
   - [x] [HighConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java)（刪除 `jukuCost`）
   - [x] [MiddleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java)（刪除 `jukuCost`）
   - [x] [MiddleConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigResponse.java)（刪除 `jukuCost`）
   - [x] [HighConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java)（刪除 `jukuCost`）
   - [x] [HighConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigResponse.java)（刪除 `jukuCost`）
   - [x] [EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java)（刪除 Middle/High 的 `jukuCost`）
   - [x] [EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java)（移除 `jukuCost` 映射）
   - [x] [EducationConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java)（刪除 `jukuCost`）
   - [x] [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties), [application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties), [.env.base](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.base), [.env.dev](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.dev), [.env.prod](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.prod)（同步刪除 `JUKU_COST` 並校正大學生活費為純月費 125,000）
2. **後端個人設定（Settings DTO / Entity / Service）（已完成刪除 ✅）**：
   - [x] [ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)（刪除 `middleManualJukuCost`, `highManualJukuCost`）
   - [x] [UpdateChildEducationSettingsRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java)（刪除兩欄位）
   - [x] [ChildEducationSettingsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java)（刪除兩欄位）
   - [x] [UpdateChildEducationSettingsCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java)（刪除兩欄位）
   - [x] [ChildEducationSettingsResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java)（刪除兩欄位）
   - [x] [ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)（移除賦值邏輯）
3. **後端計算管道（Pipeline & Engine）（已完成刪除 ✅）**：
   - [x] [EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)（移除 `jukuCost` 合併解析）
   - [x] [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)（刪除 `annualJukuCost` 欄位）
   - [x] [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)（移除傳入 StageInterval）
   - [x] [EducationAbsoluteTimeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java)（`StageInterval` record 移除 `annualJukuCost` 欄位）
   - [x] [EducationExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java)（移除 `annualJukuCost` 加總）
4. **前端介面與型別（UI & Types）（已完成刪除 ✅）**：
   - [x] [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)（移除國中、高中卡片內的「Manual Annual Juku Cost」輸入框）
   - [x] [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)（`ChildEducationSettingsDto` 刪除 `middleManualJukuCost`, `highManualJukuCost`）
5. **單元測試（Unit Tests）（已完成更新 ✅）**：
   - [x] [ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)（刪除補習費相關 mock 與 assert）
   - [x] [EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)（刪除補習費相關設定與 assert）
   - [x] [EducationExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java)（刪除補習費相關計算測試，並適配 4 引數 StageInterval）
   - [x] [ChildProfileServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java)（適配 4 引數 StageInterval）

---

## 三、 【階段一】生活費：全鏈路「純月費」實作報告（2026-10 完成）

### 1. 執行目標達成摘要
- **全鏈路純月費（Monthly Living Cost）架構成功落地**：完全貫徹 [Plan-01.md](Plan-01.md) 的 Duration 模式。後端資料庫、Entity、DTO、Resolver、Projector、Calculator 全程 100% 採用純月費，徹底杜絕年/月單位混淆與重覆除法精度損失。
- **換學制年度精確拆分**：徹底解決換學制或海外就讀邊界時，粗略重疊即灌爆整年生活費的歷史 Bug。
- **財務保守原則落地**：前端輸入維持使用者好理解的年費（¥/year），但在送出時統一透過 $\lceil\text{annual} / 12\rceil$（`Math.ceil`）無條件進位為整數日圓月費，兼顧使用者體驗與實體日圓無小數之特性。

### 2. 各層級具體變更清單

| 模組 / 層次 | 影響檔案 | 變更內容與技術細節 |
| :--- | :--- | :--- |
| **後端領域實體 (Domain Entities)** | [UniversityConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java)<br>[ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java) | - 補充/修正 JavaDoc，明確標註 `livingCost` 與 `manualLivingCost` 儲存單位為純月費（Monthly Living Cost in JPY）。<br>- 保持欄位命名精簡，無違和銜接既有 JPA 映射。 |
| **核心 Pipeline (App Core)** | [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)<br>[EducationAbsoluteTimeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java)<br>[EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)<br>[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java) | - Pipeline 資料結構全面重命名為 `monthlyLivingCost`，維護者可一目了然其單位為月。<br>- `EducationProfileResolver.mergeStage` 參數更新為 `monthlyLivingCost`，純透傳合併，不進行任何除法操作。 |
| **計算引擎 (Calculation Engine)** | [EducationExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java) | - `calculateAnnualCost`：補齊實際重疊月份計算（`activeMonths = stageEndM - stageStartM + 1`），以 `monthlyLivingCost.multiply(BigDecimal.valueOf(activeMonths))` 純乘法計算。<br>- `calculateCostForRemainingYear`：移除原本冗餘的 `/ 12` 除法，直接以 `monthlyLivingCost.multiply(BigDecimal.valueOf(activeMonths))` 計算。<br>- 完美支援 9 月入學（算 4 個月）與 6 月畢業離國（算 6 個月）之精確情境。 |
| **全域屬性設定 (Config Properties)** | [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)<br>[application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties) | - `finance-hub.education-config.university.living-cost` 預設值由年費 `1500000` 修改為月費 `125000`。 |
| **前端介面與轉換 (UI & Types)** | [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)<br>[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx) | - `use-child-profile.ts`：更新英文註解明確欄位單位為月。<br>- `child-profile-section.tsx`：<br>  1. 新增獨立輸入狀態 `annualLivingCostInput` 保持年費呈現。<br>  2. 讀取 API 時自動乘回 12 填入年費輸入框。<br>  3. 使用者輸入時即時顯示動態換算提示（`≈ ¥... / month`）。<br>  4. 儲存時以 `Math.ceil(annual / 12)` 換算成整數月費存入 DTO。<br>  5. 軌道設為 `NONE` 或關閉外宿開關時自動清空與重設為 `null`。 |
| **單元測試 (Unit Tests)** | [EducationExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java)<br>[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java) | - `EducationExpenseCalculatorTest` 新增 `shouldCalculateAnnualCostAndRemainingCostWithMonthlyLivingCost`，覆蓋入學年（4 個月）、完整年（12 個月）、畢業年（6 個月）及模擬起始月過渡期（2 個月）。<br>- `EducationProfileResolverTest` 同步更新 mock config 預設生活費為 125,000 並驗證斷言。 |

### 3. 效益與後續連動
1. **零除法運算**：後端模擬引擎運算過程不再對生活費進行除法運算，徹底杜絕 `BigDecimal` 除不盡或四捨五入的累計誤差。
2. **單純排積木模型**：生活費計算與學費、入學金完全解耦，單純由「在學期間重疊幾個月 $\times$ 每月生活費」決定，邏輯透明直觀。
3. **與後續任務之邊界**：
   - **階段二（JukuCost 徹底移除）**：不受影響，隨時可按清單執行。
   - **[Feature Requirement 131](feature-requirement-131.md)（各國獨立預設生活費）**：已建立在純月費架構之上，後續僅需在 `UniversityConfig` 擴充國別月生活費欄位與解析映射即可，無技術債負擔。

---

## 四、 【階段二】補習費（JukuCost）：徹底移除實作報告（2026-10 完成）

### 1. 執行目標達成摘要
- **JukuCost 徹底拔除**：貫徹補習費由泛用 `LifeEvent` 自行定義之架構方針，從教育模組中連根拔除所有專屬的 `jukuCost`、`middleManualJukuCost`、`highManualJukuCost` 與 `annualJukuCost`。
- **全鏈路零殘留**：包含後端全域設定（MiddleConfig, HighConfig, Controller DTOs, .env, properties）、個人設定（ChildEducationSettings, Commands, DTOs, Service）、核心計算管道（Resolver, ResolvedStageData, Projector, Timeline, Calculator）、前端（TypeScript DTO, Middle/High School StageCard UI）、以及所有對應之單元測試，100% 乾淨移除。

### 2. 各層級具體變更清單

| 模組 / 層次 | 影響檔案 | 變更內容與技術細節 |
| :--- | :--- | :--- |
| **後端全域設定 (Global Config)** | [MiddleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java)<br>[HighConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java)<br>[MiddleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java)<br>[MiddleConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigResponse.java)<br>[HighConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java)<br>[HighConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigResponse.java)<br>[EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java)<br>[EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java)<br>[EducationConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java)<br>[application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)<br>[application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)<br>[.env.base](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.base)<br>[.env.dev](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.dev)<br>[.env.prod](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.prod) | - 移除 MiddleConfig 與 HighConfig 的 `jukuCost` 屬性與 JPA 欄位映射。<br>- 移除全域設定 DTO 中的 `jukuCost` 請求與回應欄位。<br>- 移除工廠類別中的屬性綁定與預設值填充。<br>- 刪除 properties 與所有環境設定檔（base, dev, prod）中的 `JUKU_COST` 設定項。 |
| **後端個人設定 (Personal Settings)** | [ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)<br>[UpdateChildEducationSettingsRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java)<br>[ChildEducationSettingsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java)<br>[UpdateChildEducationSettingsCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java)<br>[ChildEducationSettingsResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java)<br>[ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java) | - 移除子女教育個人設定中的 `middleManualJukuCost` 與 `highManualJukuCost` 覆寫欄位。<br>- 移除 Controller 接收與回應 DTO、Application Command 與 Result 中的兩欄位。<br>- 移除 Service 實作中覆寫賦值及 Track 為 NONE 時的清空邏輯。 |
| **後端計算管道 (Calculation Pipeline)** | [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)<br>[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)<br>[EducationAbsoluteTimeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java)<br>[EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)<br>[EducationExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java) | - 刪除 `ResolvedStageData` 的 `annualJukuCost` 欄位。<br>- 移除 `EducationProfileResolver` 解析 Middle/High 時合併預設與覆寫補習費的邏輯。<br>- 移除 `EducationTimelineProjector` 建立 StageInterval 時傳遞 jukuCost 的參數。<br>- `EducationAbsoluteTimeline.StageInterval` 簡化為 4 引數 record（`stage, startMonth, endMonth, monthlyLivingCost`）。<br>- `EducationExpenseCalculator` 移除全年費用與剩餘費用計算中的 `annualJukuCost` 累計加總。 |
| **前端介面與型別 (Frontend UI & Types)** | [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)<br>[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx) | - `use-child-profile.ts`：從 `ChildEducationSettingsDto`、`MiddleConfigDto`、`HighConfigDto` 介面中移除補習費相關型別宣告。<br>- `child-profile-section.tsx`：移除 Middle School 與 High School 卡片中之 `extraField`（手動填寫年補習費輸入框）。 |
| **單元測試 (Unit Tests)** | [ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)<br>[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)<br>[EducationExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java)<br>[ChildProfileServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java) | - 移除所有測試中的補習費 builder 賦值、手動覆寫設定與斷言。<br>- 全面適配 4 引數之 `StageInterval` record 建構函式。<br>- 測試重構為專注驗證離散事件（學費、入學金）與純月生活費按活躍月份之計算。 |

### 3. 架構收益
1. **單一職責與關注點分離**：教育模組專注於學校學制（Tuition, Entrance Fee, Living Cost abroad/away），才藝班、衝刺班等不規則與自由支出回歸泛用 `LifeEvent`，系統概念高度內聚且職責單一。
2. **零技術債**：無留存 deprecated 欄位或 shim 轉接層，資料庫結構與代碼實現保持極致乾淨。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                            10020  10.97G   6.38    10:10:07  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index 55d4d89..7b199b1 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -161,6 +161,7 @@ export function ChildProfileSection() {
   const [isDialogOpen, setIsDialogOpen] = useState(false);
   const [editingChild, setEditingChild] = useState<ChildProfile | null>(null);
   const [editingSettings, setEditingSettings] = useState<ChildEducationSettingsDto | null>(null);
+  const [annualLivingCostInput, setAnnualLivingCostInput] = useState<string>("");
   const [isSettingsLoading, setIsSettingsLoading] = useState(false);
   const [expandedStages, setExpandedStages] = useState<Record<string, boolean>>({});
   
@@ -185,6 +186,7 @@ export function ChildProfileSection() {
       highTrack: "PUBLIC",
       universityTrack: "JP_NATIONAL",
     });
+    setAnnualLivingCostInput("");
     setExpandedStages({});
     setIsDialogOpen(true);
   };
@@ -199,6 +201,11 @@ export function ChildProfileSection() {
       const settings = await fetchEducationSettings(child.id);
       if (settings) {
         setEditingSettings(settings);
+        if (settings.manualLivingCost != null && !isNaN(settings.manualLivingCost)) {
+          setAnnualLivingCostInput(String(settings.manualLivingCost * 12));
+        } else {
+          setAnnualLivingCostInput("");
+        }
       } else {
         // Fallback to baseline default tracks if not existing
         setEditingSettings({
@@ -209,6 +216,7 @@ export function ChildProfileSection() {
           highTrack: "PUBLIC",
           universityTrack: "JP_NATIONAL",
         });
+        setAnnualLivingCostInput("");
       }
       setIsSettingsLoading(false);
     }
@@ -357,9 +365,15 @@ export function ChildProfileSection() {
 
     // Step 2: Save detailed education settings if child has an ID and settings were configured
     if (childId && editingSettings) {
+      const annualNum = annualLivingCostInput ? parseInt(annualLivingCostInput, 10) : null;
+      const monthlyLivingCost = (annualNum !== null && !isNaN(annualNum) && annualNum > 0 && editingSettings.isAwayFromHome)
+        ? Math.ceil(annualNum / 12)
+        : null;
+
       const settingsToSave: ChildEducationSettingsDto = {
         ...editingSettings,
         childProfileId: childId,
+        manualLivingCost: monthlyLivingCost,
       };
 
       const settingsSaved = await saveEducationSettings(childId, settingsToSave);
@@ -561,21 +575,6 @@ export function ChildProfileSection() {
                     ]}
                     override={editingSettings?.middleOverride}
                     onOverrideChange={(field, val) => updateStageOverride('middleOverride', field, val)}
-                    extraField={
-                      <div className="space-y-1">
-                        <Label className="text-xs text-muted-foreground">Manual Annual Juku Cost (¥)</Label>
-                        <Input
-                          type="number"
-                          placeholder="Manual Juku Cost (¥) - Optional"
-                          className="h-8 text-sm"
-                          value={editingSettings?.middleManualJukuCost ?? ""}
-                          onChange={(e) => setEditingSettings(prev => prev ? {
-                            ...prev,
-                            middleManualJukuCost: e.target.value ? parseInt(e.target.value) : null
-                          } : null)}
-                        />
-                      </div>
-                    }
                   />
 
                   {/* 4. High */}
@@ -597,21 +596,6 @@ export function ChildProfileSection() {
                     ]}
                     override={editingSettings?.highOverride}
                     onOverrideChange={(field, val) => updateStageOverride('highOverride', field, val)}
-                    extraField={
-                      <div className="space-y-1">
-                        <Label className="text-xs text-muted-foreground">Manual Annual Juku Cost (¥)</Label>
-                        <Input
-                          type="number"
-                          placeholder="Manual Juku Cost (¥) - Optional"
-                          className="h-8 text-sm"
-                          value={editingSettings?.highManualJukuCost ?? ""}
-                          onChange={(e) => setEditingSettings(prev => prev ? {
-                            ...prev,
-                            highManualJukuCost: e.target.value ? parseInt(e.target.value) : null
-                          } : null)}
-                        />
-                      </div>
-                    }
                   />
 
                   {/* 5. University */}
@@ -631,6 +615,10 @@ export function ChildProfileSection() {
                       const shouldDefaultAwayFromHome = nonDomesticTracks.includes(v);
                       const isNone = v === 'NONE';
 
+                      if (isNone) {
+                        setAnnualLivingCostInput("");
+                      }
+
                       setEditingSettings(prev => prev ? {
                         ...prev,
                         universityTrack: v,
@@ -660,7 +648,12 @@ export function ChildProfileSection() {
                           <Label className="text-xs">Away From Home (Living Abroad/Dorm)</Label>
                           <Switch
                             checked={!!editingSettings?.isAwayFromHome}
-                            onCheckedChange={(checked) => setEditingSettings(prev => prev ? { ...prev, isAwayFromHome: checked } : null)}
+                            onCheckedChange={(checked) => {
+                              setEditingSettings(prev => prev ? { ...prev, isAwayFromHome: checked } : null);
+                              if (!checked) {
+                                setAnnualLivingCostInput("");
+                              }
+                            }}
                           />
                         </div>
                         {editingSettings?.isAwayFromHome && (
@@ -670,12 +663,24 @@ export function ChildProfileSection() {
                               type="number"
                               placeholder="e.g. 1500000"
                               className="h-8 text-sm"
-                              value={editingSettings?.manualLivingCost ?? ""}
-                              onChange={(e) => setEditingSettings(prev => prev ? {
-                                ...prev,
-                                manualLivingCost: e.target.value ? parseInt(e.target.value) : null
-                              } : null)}
+                              value={annualLivingCostInput}
+                              onChange={(e) => {
+                                const val = e.target.value;
+                                setAnnualLivingCostInput(val);
+                                const annualNum = val ? parseInt(val, 10) : null;
+                                setEditingSettings(prev => prev ? {
+                                  ...prev,
+                                  manualLivingCost: (annualNum !== null && !isNaN(annualNum) && annualNum > 0)
+                                    ? Math.ceil(annualNum / 12)
+                                    : null
+                                } : null);
+                              }}
                             />
+                            {annualLivingCostInput && !isNaN(Number(annualLivingCostInput)) && Number(annualLivingCostInput) > 0 && (
+                              <p className="text-[11px] text-muted-foreground">
+                                ≈ ¥{Math.ceil(Number(annualLivingCostInput) / 12).toLocaleString()} / month
+                              </p>
+                            )}
                           </div>
                         )}
                       </div>
@@ -885,7 +890,7 @@ function StageCard({
         </div>
       </div>
 
-      {/* Extra Stage Field (e.g. Juku Cost for Middle/High) */}
+      {/* Extra Stage Field */}
       {extraField && !isNone && (
         <div className="mt-2">
           {extraField}
diff --git a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
index 7da2467..e914fa2 100644
--- a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
+++ b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
@@ -9,7 +9,7 @@ export interface ChildProfile {
 
 export interface StageOverrideDto {
   manualTuition?: number | null;
-  manualDuration?: number | null; // 以月為單位 (Months)
+  manualDuration?: number | null; // In months
   manualStartMonth?: number | null; // 1 ~ 12
   manualTermsPerYear?: number | null; // 1, 2, 3, 4, 6, 12
 }
@@ -23,12 +23,11 @@ export interface ChildEducationSettingsDto {
   primaryOverride?: StageOverrideDto | null;
   middleTrack: string;
   middleOverride?: StageOverrideDto | null;
-  middleManualJukuCost?: number | null;
   highTrack: string;
   highOverride?: StageOverrideDto | null;
-  highManualJukuCost?: number | null;
   universityTrack: string;
   universityOverride?: StageOverrideDto | null;
+  /** Stored as Monthly Living Cost in JPY (converted from user annual inputs via Math.ceil(annual / 12) on client before persisting). */
   manualLivingCost?: number | null;
   isAwayFromHome?: boolean | null;
 }
@@ -66,14 +65,12 @@ export interface MiddleConfigDto {
   publicTrack?: TrackConfigDto;
   privateTrack?: TrackConfigDto;
   intlTrack?: TrackConfigDto;
-  jukuCost?: number | null;
 }
 
 export interface HighConfigDto {
   publicTrack?: TrackConfigDto;
   privateTrack?: TrackConfigDto;
   intlTrack?: TrackConfigDto;
-  jukuCost?: number | null;
 }
 
 export interface UniversityConfigDto {
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../finance-hub/finance-hub-backend                                                          10021  10.96G   6.26    10:10:14  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                        10022  10.92G   6.60    10:10:21  
diff --git a/.env.base b/.env.base
index d44976b1..2f142130 100644
--- a/.env.base
+++ b/.env.base
@@ -312,8 +312,6 @@ FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_ENTRANCE_FEE="300000"
 
-FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_JUKU_COST="300000"
-
 # High Defaults
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TUITION="0"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_DURATION="36"
@@ -333,8 +331,6 @@ FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_ENTRANCE_FEE="300000"
 
-FINANCE_HUB_EDUCATION_CONFIG_HIGH_JUKU_COST="400000"
-
 # University Defaults
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TUITION="500000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_DURATION="48"
@@ -390,5 +386,5 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
 
-FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="1500000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="125000"
 
diff --git a/.env.dev b/.env.dev
index a648ad10..0154a4b2 100644
--- a/.env.dev
+++ b/.env.dev
@@ -311,8 +311,6 @@ FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_ENTRANCE_FEE="300000"
 
-FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_JUKU_COST="300000"
-
 # High Defaults
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TUITION="0"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_DURATION="36"
@@ -332,8 +330,6 @@ FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_ENTRANCE_FEE="300000"
 
-FINANCE_HUB_EDUCATION_CONFIG_HIGH_JUKU_COST="400000"
-
 # University Defaults
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TUITION="500000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_DURATION="48"
@@ -389,5 +385,5 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
 
-FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="1500000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="125000"
 
diff --git a/.env.prod b/.env.prod
index f7b3fce1..dd1cbcbf 100644
--- a/.env.prod
+++ b/.env.prod
@@ -313,8 +313,6 @@ FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_ENTRANCE_FEE="300000"
 
-FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_JUKU_COST="300000"
-
 # High Defaults
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TUITION="0"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_DURATION="36"
@@ -334,8 +332,6 @@ FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_ENTRANCE_FEE="300000"
 
-FINANCE_HUB_EDUCATION_CONFIG_HIGH_JUKU_COST="400000"
-
 # University Defaults
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TUITION="500000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_DURATION="48"
@@ -391,5 +387,5 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
 
-FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="1500000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="125000"
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
index 8d3c0b84..b94cda30 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
@@ -77,7 +77,6 @@ public class EducationConfigDefaultProperties {
         private TrackConfigProperty privateTrack = new TrackConfigProperty();
         @Builder.Default
         private TrackConfigProperty intlTrack = new TrackConfigProperty();
-        private BigDecimal jukuCost;
     }
 
     @Data
@@ -92,7 +91,6 @@ public class EducationConfigDefaultProperties {
         private TrackConfigProperty privateTrack = new TrackConfigProperty();
         @Builder.Default
         private TrackConfigProperty intlTrack = new TrackConfigProperty();
-        private BigDecimal jukuCost;
     }
 
     @Data
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
index 1d6ebc83..c39b087b 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
@@ -56,7 +56,6 @@ public class EducationConfigFactory {
                 .publicTrack(createTrackConfig(props.getPublicTrack()))
                 .privateTrack(createTrackConfig(props.getPrivateTrack()))
                 .intlTrack(createTrackConfig(props.getIntlTrack()))
-                .jukuCost(props.getJukuCost())
                 .build();
     }
 
@@ -68,7 +67,6 @@ public class EducationConfigFactory {
                 .publicTrack(createTrackConfig(props.getPublicTrack()))
                 .privateTrack(createTrackConfig(props.getPrivateTrack()))
                 .intlTrack(createTrackConfig(props.getIntlTrack()))
-                .jukuCost(props.getJukuCost())
                 .build();
     }
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
index fd242560..32dcb444 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
@@ -121,7 +121,6 @@ public class EducationConfigResult {
         TrackConfigResult publicTrack;
         TrackConfigResult privateTrack;
         TrackConfigResult intlTrack;
-        BigDecimal jukuCost;
 
         public static MiddleConfigResult fromEntity(MiddleConfig entity) {
             if (entity == null) {
@@ -131,7 +130,6 @@ public class EducationConfigResult {
                     .publicTrack(TrackConfigResult.fromEntity(entity.getPublicTrack()))
                     .privateTrack(TrackConfigResult.fromEntity(entity.getPrivateTrack()))
                     .intlTrack(TrackConfigResult.fromEntity(entity.getIntlTrack()))
-                    .jukuCost(entity.getJukuCost())
                     .build();
         }
     }
@@ -143,7 +141,6 @@ public class EducationConfigResult {
         TrackConfigResult publicTrack;
         TrackConfigResult privateTrack;
         TrackConfigResult intlTrack;
-        BigDecimal jukuCost;
 
         public static HighConfigResult fromEntity(HighConfig entity) {
             if (entity == null) {
@@ -153,7 +150,6 @@ public class EducationConfigResult {
                     .publicTrack(TrackConfigResult.fromEntity(entity.getPublicTrack()))
                     .privateTrack(TrackConfigResult.fromEntity(entity.getPrivateTrack()))
                     .intlTrack(TrackConfigResult.fromEntity(entity.getIntlTrack()))
-                    .jukuCost(entity.getJukuCost())
                     .build();
         }
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
index 108f4a9f..247038bd 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
@@ -107,10 +107,8 @@ public class ChildEducationSettingsServiceImpl implements ChildEducationSettings
         }
         if (settings.getMiddleTrack() == MiddleTrack.NONE) {
             settings.setMiddleOverride(null);
-            settings.setMiddleManualJukuCost(null);
         } else {
             settings.setMiddleOverride(command.getMiddleOverride() != null ? command.getMiddleOverride().toDomain() : null);
-            settings.setMiddleManualJukuCost(command.getMiddleManualJukuCost());
         }
 
         if (command.getHighTrack() != null) {
@@ -118,10 +116,8 @@ public class ChildEducationSettingsServiceImpl implements ChildEducationSettings
         }
         if (settings.getHighTrack() == HighTrack.NONE) {
             settings.setHighOverride(null);
-            settings.setHighManualJukuCost(null);
         } else {
             settings.setHighOverride(command.getHighOverride() != null ? command.getHighOverride().toDomain() : null);
-            settings.setHighManualJukuCost(command.getHighManualJukuCost());
         }
 
         if (command.getUniversityTrack() != null) {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java
index 65d83d3e..8cd06dcf 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java
@@ -49,11 +49,9 @@ public class UpdateChildEducationSettingsCommand {
 
     private MiddleTrack middleTrack;
     private StageOverrideCommand middleOverride;
-    private BigDecimal middleManualJukuCost;
 
     private HighTrack highTrack;
     private StageOverrideCommand highOverride;
-    private BigDecimal highManualJukuCost;
 
     private UniversityTrack universityTrack;
     private StageOverrideCommand universityOverride;
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java
index 741e5ed4..2f2d4b93 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java
@@ -57,11 +57,9 @@ public class ChildEducationSettingsResult extends BaseResult {
 
     private MiddleTrack middleTrack;
     private StageOverrideResult middleOverride;
-    private BigDecimal middleManualJukuCost;
 
     private HighTrack highTrack;
     private StageOverrideResult highOverride;
-    private BigDecimal highManualJukuCost;
 
     private UniversityTrack universityTrack;
     private StageOverrideResult universityOverride;
@@ -82,10 +80,8 @@ public class ChildEducationSettingsResult extends BaseResult {
                 .primaryOverride(StageOverrideResult.fromDomain(entity.getPrimaryOverride()))
                 .middleTrack(entity.getMiddleTrack())
                 .middleOverride(StageOverrideResult.fromDomain(entity.getMiddleOverride()))
-                .middleManualJukuCost(entity.getMiddleManualJukuCost())
                 .highTrack(entity.getHighTrack())
                 .highOverride(StageOverrideResult.fromDomain(entity.getHighOverride()))
-                .highManualJukuCost(entity.getHighManualJukuCost())
                 .universityTrack(entity.getUniversityTrack())
                 .universityOverride(StageOverrideResult.fromDomain(entity.getUniversityOverride()))
                 .manualLivingCost(entity.getManualLivingCost())
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java
index 11534259..6dcbb5d5 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java
@@ -56,8 +56,7 @@ public class EducationAbsoluteTimeline {
             EducationStage stage,
             YearMonth startMonth,
             YearMonth endMonth,
-            BigDecimal annualJukuCost,
-            BigDecimal annualLivingCost) {
+            BigDecimal monthlyLivingCost) {
 
         public boolean isActiveInYear(int year) {
             YearMonth yearStart = YearMonth.of(year, 1);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java
index f7343165..db1bdb08 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java
@@ -58,15 +58,18 @@ public class EducationExpenseCalculator {
         // 1. Sum discrete payment events (Tuition + Entrance Fee)
         BigDecimal eventTotal = timeline.getTotalEventCostForYear(targetYear);
 
-        // 2. Add annual stage costs (Juku + Living) if active in targetYear
+        // 2. Add stage costs (Living) if active in targetYear
         BigDecimal annualAdditions = BigDecimal.ZERO;
         for (EducationAbsoluteTimeline.StageInterval interval : timeline.getStageIntervals().values()) {
             if (interval.isActiveInYear(targetYear)) {
-                if (interval.annualJukuCost() != null && interval.annualJukuCost().compareTo(BigDecimal.ZERO) > 0) {
-                    annualAdditions = annualAdditions.add(interval.annualJukuCost());
-                }
-                if (interval.annualLivingCost() != null && interval.annualLivingCost().compareTo(BigDecimal.ZERO) > 0) {
-                    annualAdditions = annualAdditions.add(interval.annualLivingCost());
+                int stageStartM = (interval.startMonth().getYear() == targetYear) ? interval.startMonth().getMonthValue() : 1;
+                int stageEndM = (interval.endMonth().getYear() == targetYear) ? interval.endMonth().getMonthValue() : 12;
+                int activeMonths = stageEndM - stageStartM + 1;
+
+                if (activeMonths > 0) {
+                    if (interval.monthlyLivingCost() != null && interval.monthlyLivingCost().compareTo(BigDecimal.ZERO) > 0) {
+                        annualAdditions = annualAdditions.add(interval.monthlyLivingCost().multiply(BigDecimal.valueOf(activeMonths)));
+                    }
                 }
             }
         }
@@ -83,9 +86,8 @@ public class EducationExpenseCalculator {
      *
      * @param targetYear The target calendar year (e.g., 2026).
      * @param fromMonth The starting month (1-12). If <= 1, calculates the
-     * entire year. @param timeline The pro
-     * jected absolute timeline.
-     * @return The total remaining cost for that year as a BigDecimal.
+     * entire year. @param timeline The projected absolute timeline. @return
+     * The total remaining cost for that year as a BigDecimal.
      */
     public BigDecimal calculateCostForRemainingYear(int targetYear, int fromMonth, EducationAbsoluteTimeline timeline) {
         if (timeline == null) {
@@ -108,7 +110,7 @@ public class EducationExpenseCalculator {
             }
         }
 
-        // 2. Add annual stage costs (Juku + Living) prorated for remaining active months in [fromMonth, 12]
+        // 2. Add stage costs (Living) prorated for remaining active months in [fromMonth, 12]
         BigDecimal additions = BigDecimal.ZERO;
         for (EducationAbsoluteTimeline.StageInterval interval : timeline.getStageIntervals().values()) {
             if (interval.isActiveInYear(targetYear)) {
@@ -120,13 +122,8 @@ public class EducationExpenseCalculator {
                 int activeMonths = activeEnd - activeStart + 1;
 
                 if (activeMonths > 0) {
-                    if (interval.annualJukuCost() != null && interval.annualJukuCost().compareTo(BigDecimal.ZERO) > 0) {
-                        BigDecimal monthlyJuku = interval.annualJukuCost().divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
-                        additions = additions.add(monthlyJuku.multiply(BigDecimal.valueOf(activeMonths)));
-                    }
-                    if (interval.annualLivingCost() != null && interval.annualLivingCost().compareTo(BigDecimal.ZERO) > 0) {
-                        BigDecimal monthlyLiving = interval.annualLivingCost().divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
-                        additions = additions.add(monthlyLiving.multiply(BigDecimal.valueOf(activeMonths)));
+                    if (interval.monthlyLivingCost() != null && interval.monthlyLivingCost().compareTo(BigDecimal.ZERO) > 0) {
+                        additions = additions.add(interval.monthlyLivingCost().multiply(BigDecimal.valueOf(activeMonths)));
                     }
                 }
             }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
index 1fb0fba7..7fc6af95 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
@@ -99,7 +99,7 @@ public class EducationProfileResolver {
         }
 
         StageOverride override = settings != null ? settings.getPreschoolOverride() : null;
-        return mergeStage(EducationStage.PRESCHOOL, track.name(), trackConfig, override, BigDecimal.ZERO, BigDecimal.ZERO);
+        return mergeStage(EducationStage.PRESCHOOL, track.name(), trackConfig, override, BigDecimal.ZERO);
     }
 
     private ResolvedStageData resolvePrimary(ChildEducationSettings settings, EducationConfig config) {
@@ -126,7 +126,7 @@ public class EducationProfileResolver {
         }
 
         StageOverride override = settings != null ? settings.getPrimaryOverride() : null;
-        return mergeStage(EducationStage.PRIMARY, track.name(), trackConfig, override, BigDecimal.ZERO, BigDecimal.ZERO);
+        return mergeStage(EducationStage.PRIMARY, track.name(), trackConfig, override, BigDecimal.ZERO);
     }
 
     private ResolvedStageData resolveMiddle(ChildEducationSettings settings, EducationConfig config) {
@@ -139,7 +139,6 @@ public class EducationProfileResolver {
         }
 
         TrackConfig trackConfig = null;
-        BigDecimal defaultJukuCost = BigDecimal.ZERO;
         if (config != null && config.getMiddle() != null) {
             trackConfig = switch (track) {
                 case PUBLIC ->
@@ -151,17 +150,10 @@ public class EducationProfileResolver {
                 case NONE ->
                     null;
             };
-            if (config.getMiddle().getJukuCost() != null) {
-                defaultJukuCost = config.getMiddle().getJukuCost();
-            }
         }
 
-        BigDecimal jukuCost = (settings != null && settings.getMiddleManualJukuCost() != null)
-                ? settings.getMiddleManualJukuCost()
-                : defaultJukuCost;
-
         StageOverride override = settings != null ? settings.getMiddleOverride() : null;
-        return mergeStage(EducationStage.MIDDLE, track.name(), trackConfig, override, jukuCost, BigDecimal.ZERO);
+        return mergeStage(EducationStage.MIDDLE, track.name(), trackConfig, override, BigDecimal.ZERO);
     }
 
     private ResolvedStageData resolveHigh(ChildEducationSettings settings, EducationConfig config) {
@@ -174,7 +166,6 @@ public class EducationProfileResolver {
         }
 
         TrackConfig trackConfig = null;
-        BigDecimal defaultJukuCost = BigDecimal.ZERO;
         if (config != null && config.getHigh() != null) {
             trackConfig = switch (track) {
                 case PUBLIC ->
@@ -186,17 +177,10 @@ public class EducationProfileResolver {
                 case NONE ->
                     null;
             };
-            if (config.getHigh().getJukuCost() != null) {
-                defaultJukuCost = config.getHigh().getJukuCost();
-            }
         }
 
-        BigDecimal jukuCost = (settings != null && settings.getHighManualJukuCost() != null)
-                ? settings.getHighManualJukuCost()
-                : defaultJukuCost;
-
         StageOverride override = settings != null ? settings.getHighOverride() : null;
-        return mergeStage(EducationStage.HIGH, track.name(), trackConfig, override, jukuCost, BigDecimal.ZERO);
+        return mergeStage(EducationStage.HIGH, track.name(), trackConfig, override, BigDecimal.ZERO);
     }
 
     private ResolvedStageData resolveUniversity(ChildEducationSettings settings, EducationConfig config) {
@@ -246,7 +230,7 @@ public class EducationProfileResolver {
         }
 
         StageOverride override = settings != null ? settings.getUniversityOverride() : null;
-        return mergeStage(EducationStage.UNIVERSITY, track.name(), trackConfig, override, BigDecimal.ZERO, livingCost);
+        return mergeStage(EducationStage.UNIVERSITY, track.name(), trackConfig, override, livingCost);
     }
 
     private ResolvedStageData mergeStage(
@@ -254,7 +238,6 @@ public class EducationProfileResolver {
             String trackName,
             TrackConfig defaultTrack,
             StageOverride override,
-            BigDecimal jukuCost,
             BigDecimal livingCost) {
 
         BigDecimal tuition = BigDecimal.ZERO;
@@ -305,8 +288,7 @@ public class EducationProfileResolver {
                 .startMonth(startMonth)
                 .termsPerYear(termsPerYear)
                 .entranceFee(entranceFee)
-                .annualJukuCost(jukuCost != null ? jukuCost : BigDecimal.ZERO)
-                .annualLivingCost(livingCost != null ? livingCost : BigDecimal.ZERO)
+                .monthlyLivingCost(livingCost != null ? livingCost : BigDecimal.ZERO)
                 .paymentMonths(paymentMonths)
                 .build();
     }
@@ -321,8 +303,7 @@ public class EducationProfileResolver {
                 .startMonth(DEFAULT_START_MONTH)
                 .termsPerYear(DEFAULT_TERMS_PER_YEAR)
                 .entranceFee(BigDecimal.ZERO)
-                .annualJukuCost(BigDecimal.ZERO)
-                .annualLivingCost(BigDecimal.ZERO)
+                .monthlyLivingCost(BigDecimal.ZERO)
                 .paymentMonths(Collections.emptyList())
                 .build();
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
index 9a2e60d7..16e032c8 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
@@ -82,8 +82,7 @@ public class EducationTimelineProjector {
                     stage,
                     stageStart,
                     stageEnd,
-                    stageData.getAnnualJukuCost(),
-                    stageData.getAnnualLivingCost()
+                    stageData.getMonthlyLivingCost()
             ));
 
             generateStageEvents(stage, stageData, stageStart, stageEnd, events);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
index 78bc7f4e..8e5333f0 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
@@ -50,8 +50,7 @@ public class ResolvedStageData {
     private final Integer startMonth;
     private final Integer termsPerYear;
     private final BigDecimal entranceFee;
-    private final BigDecimal annualJukuCost;
-    private final BigDecimal annualLivingCost;
+    private final BigDecimal monthlyLivingCost;
 
     @Builder.Default
     private final List<Integer> paymentMonths = Collections.emptyList();
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java
index aae83b37..09bcdc91 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java
@@ -113,7 +113,6 @@ class ChildProfileServiceImplTest {
                 EducationStage.PRIMARY,
                 YearMonth.of(2024, 4),
                 YearMonth.of(2030, 3),
-                BigDecimal.ZERO,
                 BigDecimal.ZERO
         );
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
index eeb0dc9c..72853a6e 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
@@ -173,8 +173,6 @@ class ChildEducationSettingsServiceImplTest {
         ChildEducationSettings existingSettings = new ChildEducationSettings(1L);
         existingSettings.setPrimaryTrack(PrimaryTrack.PUBLIC);
         existingSettings.setPrimaryOverride(StageOverride.builder().manualDuration(72).build());
-        existingSettings.setMiddleManualJukuCost(BigDecimal.valueOf(500000));
-        existingSettings.setHighManualJukuCost(BigDecimal.valueOf(600000));
         existingSettings.setManualLivingCost(BigDecimal.valueOf(1200000));
         existingSettings.setIsAwayFromHome(true);
 
@@ -183,9 +181,7 @@ class ChildEducationSettingsServiceImplTest {
                 .primaryTrack(PrimaryTrack.NONE)
                 .primaryOverride(StageOverrideCommand.builder().manualDuration(72).build())
                 .middleTrack(MiddleTrack.NONE)
-                .middleManualJukuCost(BigDecimal.valueOf(500000))
                 .highTrack(HighTrack.NONE)
-                .highManualJukuCost(BigDecimal.valueOf(600000))
                 .universityTrack(UniversityTrack.NONE)
                 .universityOverride(StageOverrideCommand.builder().manualDuration(48).build())
                 .manualLivingCost(BigDecimal.valueOf(1200000))
@@ -216,11 +212,9 @@ class ChildEducationSettingsServiceImplTest {
 
         assertEquals(MiddleTrack.NONE, saved.getMiddleTrack());
         assertNull(saved.getMiddleOverride());
-        assertNull(saved.getMiddleManualJukuCost());
 
         assertEquals(HighTrack.NONE, saved.getHighTrack());
         assertNull(saved.getHighOverride());
-        assertNull(saved.getHighManualJukuCost());
 
         assertEquals(UniversityTrack.NONE, saved.getUniversityTrack());
         assertNull(saved.getUniversityOverride());
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java
index 097f116c..b185cb71 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java
@@ -45,11 +45,11 @@ class EducationExpenseCalculatorTest {
     }
 
     @Test
-    @DisplayName("Should correctly calculate annual cost combining discrete events and annual stage costs")
-    void shouldCalculateAnnualCostWithJukuAndLiving() {
+    @DisplayName("Should correctly calculate annual cost combining discrete events and stage living cost")
+    void shouldCalculateAnnualCostWithLivingCost() {
         // Timeline in 2026:
         // Discrete events: 2026-04 Tuition 500k, 2026-10 Tuition 500k
-        // Stage interval: High school active in 2026 with 300k juku cost
+        // Stage interval: High school active in 2026 (no living cost)
         EducationPaymentEvent event1 = EducationPaymentEvent.builder()
                 .paymentMonth(YearMonth.of(2026, 4))
                 .amount(new BigDecimal("500000"))
@@ -68,7 +68,6 @@ class EducationExpenseCalculatorTest {
                 EducationStage.HIGH,
                 YearMonth.of(2026, 4),
                 YearMonth.of(2029, 3),
-                new BigDecimal("300000"), // juku
                 BigDecimal.ZERO // living
         );
 
@@ -77,9 +76,9 @@ class EducationExpenseCalculatorTest {
                 .stageIntervals(Map.of(EducationStage.HIGH, highInterval))
                 .build();
 
-        // 2026 Total: 500k + 500k + 300k = 1,300,000
+        // 2026 Total: 500k + 500k = 1,000,000
         BigDecimal cost2026 = calculator.calculateAnnualCost(2026, timeline);
-        assertThat(cost2026).isEqualByComparingTo("1300000");
+        assertThat(cost2026).isEqualByComparingTo("1000000");
 
         // 2025 Total: inactive -> 0
         BigDecimal cost2025 = calculator.calculateAnnualCost(2025, timeline);
@@ -136,7 +135,7 @@ class EducationExpenseCalculatorTest {
         //   - 2026-04 Tuition: 500,000 (past event, should be excluded if simulation starts in Sep)
         //   - 2026-10 Tuition: 500,000 (future event, should be included)
         // Stage interval:
-        //   - High school 2026-04 ~ 2029-03, annual juku: 360,000 (30,000/mo)
+        //   - High school 2026-04 ~ 2029-03, monthly living cost: 30,000
         EducationPaymentEvent entranceFee = EducationPaymentEvent.builder()
                 .paymentMonth(YearMonth.of(2026, 4))
                 .amount(new BigDecimal("300000"))
@@ -162,8 +161,7 @@ class EducationExpenseCalculatorTest {
                 EducationStage.HIGH,
                 YearMonth.of(2026, 4),
                 YearMonth.of(2029, 3),
-                new BigDecimal("360000"), // 30k/month
-                BigDecimal.ZERO
+                new BigDecimal("30000") // 30k/month living cost
         );
 
         EducationAbsoluteTimeline timeline = EducationAbsoluteTimeline.builder()
@@ -173,7 +171,7 @@ class EducationExpenseCalculatorTest {
 
         // When simulation starts in September 2026 (fromMonth = 9):
         // Remaining events: only 2026-10 Tuition (500,000). (4月已繳的80萬不計入)
-        // Juku cost: Sep, Oct, Nov, Dec = 4 active months * 30,000 = 120,000.
+        // Living cost: Sep, Oct, Nov, Dec = 4 active months * 30,000 = 120,000.
         // Total expected = 500,000 + 120,000 = 620,000.
         BigDecimal remaining2026 = calculator.calculateCostForRemainingYear(2026, 9, timeline);
         assertThat(remaining2026).isEqualByComparingTo("620000");
@@ -182,4 +180,52 @@ class EducationExpenseCalculatorTest {
         Map<Integer, Long> costMap = calculator.buildAnnualCostMap(timeline, 2026, 9);
         assertThat(costMap.get(2026)).isEqualTo(620000L);
     }
+
+    @Test
+    @DisplayName("Should accurately calculate annual cost and remaining cost with monthly living cost based on active months")
+    void shouldCalculateAnnualCostAndRemainingCostWithMonthlyLivingCost() {
+        // University interval: 2026-09 ~ 2030-06, monthly living cost = 125,000
+        // Discrete event: 2026-09 Tuition 1,000,000
+        EducationPaymentEvent tuition2026 = EducationPaymentEvent.builder()
+                .paymentMonth(YearMonth.of(2026, 9))
+                .amount(new BigDecimal("1000000"))
+                .type(PaymentType.TUITION)
+                .stage(EducationStage.UNIVERSITY)
+                .build();
+
+        EducationAbsoluteTimeline.StageInterval uniInterval = new EducationAbsoluteTimeline.StageInterval(
+                EducationStage.UNIVERSITY,
+                YearMonth.of(2026, 9),
+                YearMonth.of(2030, 6),
+                new BigDecimal("125000") // 125,000 / month
+        );
+
+        EducationAbsoluteTimeline timeline = EducationAbsoluteTimeline.builder()
+                .events(List.of(tuition2026))
+                .stageIntervals(Map.of(EducationStage.UNIVERSITY, uniInterval))
+                .build();
+
+        // 1. Year 2026 Annual Cost:
+        // Tuition: 1,000,000
+        // Living cost: Sep, Oct, Nov, Dec = 4 active months * 125,000 = 500,000
+        // Total expected = 1,500,000
+        BigDecimal annualCost2026 = calculator.calculateAnnualCost(2026, timeline);
+        assertThat(annualCost2026).isEqualByComparingTo("1500000");
+
+        // 2. Year 2027 Annual Cost (Full year active, no events):
+        // Living cost: 12 active months * 125,000 = 1,500,000
+        BigDecimal annualCost2027 = calculator.calculateAnnualCost(2027, timeline);
+        assertThat(annualCost2027).isEqualByComparingTo("1500000");
+
+        // 3. Year 2030 Annual Cost (Graduation year, Jan ~ Jun = 6 active months):
+        // Living cost: 6 active months * 125,000 = 750,000
+        BigDecimal annualCost2030 = calculator.calculateAnnualCost(2030, timeline);
+        assertThat(annualCost2030).isEqualByComparingTo("750000");
+
+        // 4. Remaining cost in 2026 starting from November (fromMonth = 11):
+        // Past event: 2026-09 Tuition excluded
+        // Remaining active months in [11, 12]: 2 months (Nov, Dec) * 125,000 = 250,000
+        BigDecimal remainingCost2026 = calculator.calculateCostForRemainingYear(2026, 11, timeline);
+        assertThat(remainingCost2026).isEqualByComparingTo("250000");
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
index 00f18d1b..b71c68f2 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
@@ -126,7 +126,6 @@ class EducationProfileResolverTest {
                                 .termsPerYear(3)
                                 .entranceFee(new BigDecimal("500000"))
                                 .build())
-                        .jukuCost(new BigDecimal("300000"))
                         .build())
                 .high(HighConfig.builder()
                         .publicTrack(TrackConfig.builder()
@@ -150,7 +149,6 @@ class EducationProfileResolverTest {
                                 .termsPerYear(3)
                                 .entranceFee(new BigDecimal("500000"))
                                 .build())
-                        .jukuCost(new BigDecimal("400000"))
                         .build())
                 .university(UniversityConfig.builder()
                         .jpNationalTrack(TrackConfig.builder()
@@ -216,7 +214,7 @@ class EducationProfileResolverTest {
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
                                 .build())
-                        .livingCost(new BigDecimal("1500000"))
+                        .livingCost(new BigDecimal("125000"))
                         .build())
                 .build();
     }
@@ -302,23 +300,21 @@ class EducationProfileResolverTest {
         assertThat(primary.getStartMonth()).isEqualTo(4);
         assertThat(primary.getPaymentMonths()).containsExactly(4, 10);
 
-        // Middle (includes default juku cost)
+        // Middle
         ResolvedStageData middle = profile.getStage(EducationStage.MIDDLE);
         assertThat(middle.isActive()).isTrue();
         assertThat(middle.getAnnualTuition()).isEqualByComparingTo("100000");
-        assertThat(middle.getAnnualJukuCost()).isEqualByComparingTo("300000");
 
         // High
         ResolvedStageData high = profile.getStage(EducationStage.HIGH);
         assertThat(high.isActive()).isTrue();
-        assertThat(high.getAnnualJukuCost()).isEqualByComparingTo("400000");
 
         // University (National, awayFromHome = false by default -> livingCost is 0)
         ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);
         assertThat(uni.isActive()).isTrue();
         assertThat(uni.getAnnualTuition()).isEqualByComparingTo("500000");
         assertThat(uni.getEntranceFee()).isEqualByComparingTo("280000");
-        assertThat(uni.getAnnualLivingCost()).isEqualByComparingTo(BigDecimal.ZERO);
+        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo(BigDecimal.ZERO);
     }
 
     @Test
@@ -368,7 +364,6 @@ class EducationProfileResolverTest {
                         .build())
                 .primaryTrack(PrimaryTrack.INTL)
                 .middleTrack(MiddleTrack.PUBLIC)
-                .middleManualJukuCost(new BigDecimal("500000")) // override juku cost
                 .highTrack(HighTrack.PUBLIC)
                 .universityTrack(UniversityTrack.UK)
                 .isAwayFromHome(true)
@@ -387,10 +382,6 @@ class EducationProfileResolverTest {
         // entranceFee falls back to intl default (200000)
         assertThat(preschool.getEntranceFee()).isEqualByComparingTo("200000");
 
-        // Middle juku override
-        ResolvedStageData middle = profile.getStage(EducationStage.MIDDLE);
-        assertThat(middle.getAnnualJukuCost()).isEqualByComparingTo("500000");
-
         // University UK: automatic overseas living cost
         ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);
         assertThat(uni.getAnnualTuition()).isEqualByComparingTo("5000000");
@@ -398,7 +389,7 @@ class EducationProfileResolverTest {
         assertThat(uni.getStartMonth()).isEqualTo(9);
         assertThat(uni.getTermsPerYear()).isEqualTo(3);
         assertThat(uni.getPaymentMonths()).containsExactly(9, 1, 5);
-        assertThat(uni.getAnnualLivingCost()).isEqualByComparingTo("1500000");
+        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("125000");
     }
 
     @Test
@@ -408,13 +399,13 @@ class EducationProfileResolverTest {
                 .childProfileId(1L)
                 .universityTrack(UniversityTrack.JP_NATIONAL)
                 .isAwayFromHome(true)
-                .manualLivingCost(new BigDecimal("1200000"))
+                .manualLivingCost(new BigDecimal("100000"))
                 .build();
 
         ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
         ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);
 
-        assertThat(uni.getAnnualLivingCost()).isEqualByComparingTo("1200000");
+        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("100000");
     }
 
     @Test
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/EducationConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/EducationConfig.java
index bb54426c..d97e8673 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/EducationConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/EducationConfig.java
@@ -16,6 +16,19 @@ import lombok.EqualsAndHashCode;
 import lombok.Getter;
 import lombok.NoArgsConstructor;
 
+/**
+ * Root domain entity representing the complete education cost configuration.
+ *
+ * <p>Contains baseline configurations across all educational stages (Preschool,
+ * Primary, Middle, High, University). Baseline values are loaded from configuration
+ * properties (e.g., {@code application.properties} or environment variables).</p>
+ *
+ * <p><b>Important Unit Conventions:</b></p>
+ * <ul>
+ *   <li>All track tuitions represent <b>Annual Tuition</b> in JPY.</li>
+ *   <li>All track durations represent total study duration strictly in <b>Months</b> (e.g., 36 or 72 months).</li>
+ * </ul>
+ */
 @Entity
 @Table(name = EducationConfig.TABLE_NAME, indexes = {
     @Index(name = EducationConfig.ID_INDEX, columnList = BaseEntity.ID_COLUMN)
@@ -35,18 +48,23 @@ public class EducationConfig extends AuditableEntity {
     @Column(name = NAME_COLUMN, nullable = false, length = 100)
     private String name;
 
+    /** Preschool stage configuration (Track tuitions are annual; track durations are in months, e.g., 36 months). */
     @Embedded
     private PreschoolConfig preschool;
 
+    /** Primary school stage configuration (Track tuitions are annual; track durations are in months, e.g., 72 months). */
     @Embedded
     private PrimaryConfig primary;
 
+    /** Middle school stage configuration (Track tuitions are annual; track durations are in months, e.g., 36 months). */
     @Embedded
     private MiddleConfig middle;
 
+    /** High school stage configuration (Track tuitions are annual; track durations are in months, e.g., 36 months). */
     @Embedded
     private HighConfig high;
 
+    /** University stage configuration (Track tuitions are annual; track durations are in months, e.g., 36–48 months; baseline living cost is monthly). */
     @Embedded
     private UniversityConfig university;
 
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java
index 5393ecee..fb9933c3 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java
@@ -1,7 +1,5 @@
 package ch.lin.finance.hub.backend.api.domain.model.config.education;
 
-import java.math.BigDecimal;
-
 import jakarta.persistence.AttributeOverride;
 import jakarta.persistence.AttributeOverrides;
 import jakarta.persistence.Column;
@@ -13,6 +11,18 @@ import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
 
+/**
+ * Configuration for High / Senior High School tracks.
+ *
+ * <p>Baseline parameters are loaded from application properties (e.g., {@code application.properties})
+ * and can be overridden per child profile.</p>
+ *
+ * <p><b>Important Unit Conventions:</b></p>
+ * <ul>
+ *   <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
+ *   <li>{@code duration} represents total study duration strictly in <b>Months</b> (e.g., 36 months for 3-year high school).</li>
+ * </ul>
+ */
 @Embeddable
 @Getter
 @Setter
@@ -21,6 +31,7 @@ import lombok.Setter;
 @Builder
 public class HighConfig {
 
+    /** Public high school track (e.g., 36 months, ~200,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "high_public_tuition", precision = 19, scale = 4)),
@@ -31,6 +42,7 @@ public class HighConfig {
     })
     private TrackConfig publicTrack;
 
+    /** Private high school track (e.g., 36 months, ~1,000,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "high_private_tuition", precision = 19, scale = 4)),
@@ -41,6 +53,7 @@ public class HighConfig {
     })
     private TrackConfig privateTrack;
 
+    /** International high school track (e.g., 36 months, ~3,000,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "high_intl_tuition", precision = 19, scale = 4)),
@@ -50,7 +63,4 @@ public class HighConfig {
             @AttributeOverride(name = "entranceFee", column = @Column(name = "high_intl_entrance_fee", precision = 19, scale = 4))
     })
     private TrackConfig intlTrack;
-
-    @Column(name = "high_juku_cost", precision = 19, scale = 4)
-    private BigDecimal jukuCost;
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java
index ccd83170..247777a9 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java
@@ -1,7 +1,5 @@
 package ch.lin.finance.hub.backend.api.domain.model.config.education;
 
-import java.math.BigDecimal;
-
 import jakarta.persistence.AttributeOverride;
 import jakarta.persistence.AttributeOverrides;
 import jakarta.persistence.Column;
@@ -13,6 +11,21 @@ import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
 
+/**
+ * Configuration for Middle / Junior High School tracks.
+ *
+ * <p>
+ * Baseline parameters are loaded from application properties (e.g.,
+ * {@code application.properties}) and can be overridden per child profile.</p>
+ *
+ * <p>
+ * <b>Important Unit Conventions:</b></p>
+ * <ul>
+ * <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
+ * <li>{@code duration} represents total study duration strictly in
+ * <b>Months</b> (e.g., 36 months for 3-year middle school).</li>
+ * </ul>
+ */
 @Embeddable
 @Getter
 @Setter
@@ -21,36 +34,45 @@ import lombok.Setter;
 @Builder
 public class MiddleConfig {
 
+    /**
+     * Public middle school track (e.g., 36 months, ~150,000 JPY/year tuition;
+     * configured via properties).
+     */
     @Embedded
     @AttributeOverrides({
-            @AttributeOverride(name = "tuition", column = @Column(name = "middle_public_tuition", precision = 19, scale = 4)),
-            @AttributeOverride(name = "duration", column = @Column(name = "middle_public_duration")),
-            @AttributeOverride(name = "startMonth", column = @Column(name = "middle_public_start_month")),
-            @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_public_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_public_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "tuition", column = @Column(name = "middle_public_tuition", precision = 19, scale = 4)),
+        @AttributeOverride(name = "duration", column = @Column(name = "middle_public_duration")),
+        @AttributeOverride(name = "startMonth", column = @Column(name = "middle_public_start_month")),
+        @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_public_terms_per_year")),
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_public_entrance_fee", precision = 19, scale = 4))
     })
     private TrackConfig publicTrack;
 
+    /**
+     * Private middle school track (e.g., 36 months, ~1,100,000 JPY/year
+     * tuition; configured via properties).
+     */
     @Embedded
     @AttributeOverrides({
-            @AttributeOverride(name = "tuition", column = @Column(name = "middle_private_tuition", precision = 19, scale = 4)),
-            @AttributeOverride(name = "duration", column = @Column(name = "middle_private_duration")),
-            @AttributeOverride(name = "startMonth", column = @Column(name = "middle_private_start_month")),
-            @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_private_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_private_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "tuition", column = @Column(name = "middle_private_tuition", precision = 19, scale = 4)),
+        @AttributeOverride(name = "duration", column = @Column(name = "middle_private_duration")),
+        @AttributeOverride(name = "startMonth", column = @Column(name = "middle_private_start_month")),
+        @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_private_terms_per_year")),
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_private_entrance_fee", precision = 19, scale = 4))
     })
     private TrackConfig privateTrack;
 
+    /**
+     * International middle school track (e.g., 36 months, ~2,800,000 JPY/year
+     * tuition; configured via properties).
+     */
     @Embedded
     @AttributeOverrides({
-            @AttributeOverride(name = "tuition", column = @Column(name = "middle_intl_tuition", precision = 19, scale = 4)),
-            @AttributeOverride(name = "duration", column = @Column(name = "middle_intl_duration")),
-            @AttributeOverride(name = "startMonth", column = @Column(name = "middle_intl_start_month")),
-            @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_intl_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_intl_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "tuition", column = @Column(name = "middle_intl_tuition", precision = 19, scale = 4)),
+        @AttributeOverride(name = "duration", column = @Column(name = "middle_intl_duration")),
+        @AttributeOverride(name = "startMonth", column = @Column(name = "middle_intl_start_month")),
+        @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_intl_terms_per_year")),
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_intl_entrance_fee", precision = 19, scale = 4))
     })
     private TrackConfig intlTrack;
-
-    @Column(name = "middle_juku_cost", precision = 19, scale = 4)
-    private BigDecimal jukuCost;
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java
index 9e874b47..9df5cf78 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java
@@ -11,6 +11,18 @@ import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
 
+/**
+ * Configuration for Preschool / Kindergarten tracks.
+ *
+ * <p>Baseline parameters are loaded from application properties (e.g., {@code application.properties})
+ * and can be overridden per child profile.</p>
+ *
+ * <p><b>Important Unit Conventions:</b></p>
+ * <ul>
+ *   <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
+ *   <li>{@code duration} represents total study duration strictly in <b>Months</b> (e.g., 36 months).</li>
+ * </ul>
+ */
 @Embeddable
 @Getter
 @Setter
@@ -19,6 +31,7 @@ import lombok.Setter;
 @Builder
 public class PreschoolConfig {
 
+    /** Home care / homeschool track (e.g., 36 months, 0 tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "preschool_home_tuition", precision = 19, scale = 4)),
@@ -29,6 +42,7 @@ public class PreschoolConfig {
     })
     private TrackConfig homeTrack;
 
+    /** Daycare track (e.g., 36 months, ~300,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "preschool_daycare_tuition", precision = 19, scale = 4)),
@@ -39,6 +53,7 @@ public class PreschoolConfig {
     })
     private TrackConfig daycareTrack;
 
+    /** International preschool track (e.g., 36 months, ~1,800,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "preschool_intl_tuition", precision = 19, scale = 4)),
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java
index 97148481..3c5e5a1e 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java
@@ -11,6 +11,18 @@ import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
 
+/**
+ * Configuration for Primary / Elementary School tracks.
+ *
+ * <p>Baseline parameters are loaded from application properties (e.g., {@code application.properties})
+ * and can be overridden per child profile.</p>
+ *
+ * <p><b>Important Unit Conventions:</b></p>
+ * <ul>
+ *   <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
+ *   <li>{@code duration} represents total study duration strictly in <b>Months</b> (e.g., 72 months for 6-year primary education).</li>
+ * </ul>
+ */
 @Embeddable
 @Getter
 @Setter
@@ -19,6 +31,7 @@ import lombok.Setter;
 @Builder
 public class PrimaryConfig {
 
+    /** Public elementary school track (e.g., 72 months, ~100,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "primary_public_tuition", precision = 19, scale = 4)),
@@ -29,6 +42,7 @@ public class PrimaryConfig {
     })
     private TrackConfig publicTrack;
 
+    /** Private elementary school track (e.g., 72 months, ~1,000,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "primary_private_tuition", precision = 19, scale = 4)),
@@ -39,6 +53,7 @@ public class PrimaryConfig {
     })
     private TrackConfig privateTrack;
 
+    /** International primary school track (e.g., 72 months, ~2,500,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
             @AttributeOverride(name = "tuition", column = @Column(name = "primary_intl_tuition", precision = 19, scale = 4)),
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java
index 677a3919..0684266d 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java
@@ -10,6 +10,24 @@ import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
 
+/**
+ * Baseline parameters for an educational track (e.g., Public, Private, International).
+ *
+ * <p>Values are populated from application properties (e.g., {@code application.properties}
+ * or environment variables) and can be overridden per child profile.</p>
+ *
+ * <p><b>Important Unit Conventions:</b></p>
+ * <ul>
+ *   <li><b>tuition:</b> Baseline <b>Annual Tuition</b> in JPY (e.g., ~2,500,000 JPY for international primary,
+ *       ~500,000 JPY for national university). In the timeline projector, this annual figure is divided
+ *       by {@code termsPerYear} across the specified payment months.</li>
+ *   <li><b>duration:</b> Total study duration strictly in <b>Months</b> (e.g., 36 months for a 3-year track,
+ *       72 months for a 6-year track).</li>
+ *   <li><b>startMonth:</b> Starting calendar month of the academic year (1–12, e.g., 4 for April, 9 for September).</li>
+ *   <li><b>termsPerYear:</b> Number of payment installments per academic year (e.g., 2 for semester-based payments).</li>
+ *   <li><b>entranceFee:</b> One-time admission fee in JPY charged in the starting month of enrollment.</li>
+ * </ul>
+ */
 @Embeddable
 @Getter
 @Setter
@@ -18,19 +36,38 @@ import lombok.Setter;
 @Builder
 public class TrackConfig {
 
+    /**
+     * Standard baseline annual tuition in JPY (e.g., ~2,500,000 JPY for international primary,
+     * ~500,000 JPY for national university; exact values are configured via application properties).
+     * This represents the full academic year tuition, which is divided by {@code termsPerYear}
+     * into periodic payment installments in {@code EducationTimelineProjector}.
+     */
     @Column(name = "tuition", precision = 19, scale = 4)
     private BigDecimal tuition;
 
+    /**
+     * Standard study duration of this track strictly in MONTHS (e.g., 36 months for a 3-year track,
+     * 72 months for a 6-year track; configured via application properties).
+     */
     @Column(name = "duration")
     private Integer duration;
 
+    /**
+     * Entrance start calendar month (1–12, e.g., 4 for April in Japan, 9 for September in US/UK/TW).
+     */
     @Column(name = "start_month")
     private Integer startMonth;
 
+    /**
+     * Default number of payment installments per academic year (e.g., 2 for semester-based tuition).
+     */
     @Column(name = "terms_per_year")
     @Builder.Default
     private Integer termsPerYear = 2;
 
+    /**
+     * One-time entrance admission fee in JPY, charged once in the starting month of enrollment.
+     */
     @Column(name = "entrance_fee", precision = 19, scale = 4)
     private BigDecimal entranceFee;
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
index 91c5e377..1661869e 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
@@ -13,6 +13,19 @@ import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
 
+/**
+ * Configuration for University / Higher Education tracks and baseline living costs.
+ *
+ * <p>Baseline parameters are loaded from application properties (e.g., {@code application.properties})
+ * and can be overridden per child profile.</p>
+ *
+ * <p><b>Important Unit Conventions:</b></p>
+ * <ul>
+ *   <li>{@code tuition} represents baseline <b>Annual Tuition</b> in JPY.</li>
+ *   <li>{@code duration} represents total study duration strictly in <b>Months</b> (e.g., 36 or 48 months).</li>
+ *   <li>{@code livingCost} represents baseline <b>Monthly Living Cost</b> in JPY for overseas or away-from-home students.</li>
+ * </ul>
+ */
 @Embeddable
 @Getter
 @Setter
@@ -21,6 +34,7 @@ import lombok.Setter;
 @Builder
 public class UniversityConfig {
 
+    /** Japan National University track (e.g., 48 months, ~500,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_jp_national_tuition", precision = 19, scale = 4)),
@@ -31,6 +45,7 @@ public class UniversityConfig {
     })
     private TrackConfig jpNationalTrack;
 
+    /** Japan Private University track (e.g., 48 months, ~1,000,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_jp_private_tuition", precision = 19, scale = 4)),
@@ -41,6 +56,7 @@ public class UniversityConfig {
     })
     private TrackConfig jpPrivateTrack;
 
+    /** Japan Private Art/Medical University track (e.g., 48 months, ~1,800,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_jp_private_art_tuition", precision = 19, scale = 4)),
@@ -51,6 +67,7 @@ public class UniversityConfig {
     })
     private TrackConfig jpPrivateArtTrack;
 
+    /** Taiwan National University track (e.g., 48 months, ~250,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_tw_national_tuition", precision = 19, scale = 4)),
@@ -61,6 +78,7 @@ public class UniversityConfig {
     })
     private TrackConfig twNationalTrack;
 
+    /** Taiwan Private University track (e.g., 48 months, ~500,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_tw_private_tuition", precision = 19, scale = 4)),
@@ -71,6 +89,7 @@ public class UniversityConfig {
     })
     private TrackConfig twPrivateTrack;
 
+    /** United States University track (e.g., 48 months, ~6,000,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_us_tuition", precision = 19, scale = 4)),
@@ -81,6 +100,7 @@ public class UniversityConfig {
     })
     private TrackConfig usTrack;
 
+    /** United Kingdom University track (e.g., 36 months, ~4,500,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_uk_tuition", precision = 19, scale = 4)),
@@ -91,6 +111,7 @@ public class UniversityConfig {
     })
     private TrackConfig ukTrack;
 
+    /** Canada University track (e.g., 48 months, ~3,500,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_canada_tuition", precision = 19, scale = 4)),
@@ -101,6 +122,7 @@ public class UniversityConfig {
     })
     private TrackConfig canadaTrack;
 
+    /** Australia University track (e.g., 36 months, ~4,000,000 JPY/year tuition; configured via properties). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "tuition", column = @Column(name = "uni_australia_tuition", precision = 19, scale = 4)),
@@ -111,6 +133,10 @@ public class UniversityConfig {
     })
     private TrackConfig australiaTrack;
 
+    /**
+     * Standard baseline living cost in JPY per MONTH for students studying abroad or living away from home
+     * (e.g., ~125,000 JPY/month; configured via application properties and stored on a monthly basis).
+     */
     @Column(name = "uni_living_cost", precision = 19, scale = 4)
     private BigDecimal livingCost;
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
index fcfd6a44..302787bf 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
@@ -27,6 +27,16 @@ import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
 
+/**
+ * Child education settings entity representing an individual child's education tracks and overrides.
+ *
+ * <p><b>Important Unit Conventions for Overrides:</b></p>
+ * <ul>
+ *   <li>Stage override {@code manualTuition} is <b>Annual Tuition</b> in JPY (e.g., 1,500,000 JPY/year).</li>
+ *   <li>Stage override {@code manualDuration} is strictly in <b>Months</b> (e.g., 36 months; NOT in years).</li>
+ *   <li>{@code manualLivingCost} is <b>Monthly Living Cost</b> in JPY (converted from user annual inputs by dividing by 12 on the client before persisting).</li>
+ * </ul>
+ */
 @Entity
 @Table(name = ChildEducationSettings.TABLE_NAME, indexes = {
     @Index(name = ChildEducationSettings.ID_INDEX, columnList = BaseEntity.ID_COLUMN)
@@ -48,10 +58,12 @@ public class ChildEducationSettings extends AuditableEntity {
     private Long childProfileId;
 
     // --- Preschool ---
+    /** Preschool track selection (PreschoolTrack). */
     @Enumerated(EnumType.STRING)
     @Column(name = "preschool_track")
     private PreschoolTrack preschoolTrack = PreschoolTrack.HOME;
 
+    /** Preschool custom override (manualTuition is annual tuition; manualDuration is duration in months). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "preschool_manual_tuition", precision = 19, scale = 4)),
@@ -62,10 +74,12 @@ public class ChildEducationSettings extends AuditableEntity {
     private StageOverride preschoolOverride;
 
     // --- Primary ---
+    /** Primary school track selection (PrimaryTrack). */
     @Enumerated(EnumType.STRING)
     @Column(name = "primary_track")
     private PrimaryTrack primaryTrack = PrimaryTrack.NONE;
 
+    /** Primary school custom override (manualTuition is annual tuition; manualDuration is duration in months, e.g., 72). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "primary_manual_tuition", precision = 19, scale = 4)),
@@ -76,10 +90,12 @@ public class ChildEducationSettings extends AuditableEntity {
     private StageOverride primaryOverride;
 
     // --- Middle ---
+    /** Middle school track selection (MiddleTrack). */
     @Enumerated(EnumType.STRING)
     @Column(name = "middle_track")
     private MiddleTrack middleTrack = MiddleTrack.NONE;
 
+    /** Middle school custom override (manualTuition is annual tuition; manualDuration is duration in months, e.g., 36). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "middle_manual_tuition", precision = 19, scale = 4)),
@@ -89,14 +105,13 @@ public class ChildEducationSettings extends AuditableEntity {
     })
     private StageOverride middleOverride;
 
-    @Column(name = "middle_manual_juku_cost", precision = 19, scale = 4)
-    private BigDecimal middleManualJukuCost;
-
     // --- High ---
+    /** High school track selection (HighTrack). */
     @Enumerated(EnumType.STRING)
     @Column(name = "high_track")
     private HighTrack highTrack = HighTrack.NONE;
 
+    /** High school custom override (manualTuition is annual tuition; manualDuration is duration in months, e.g., 36). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "high_manual_tuition", precision = 19, scale = 4)),
@@ -106,14 +121,13 @@ public class ChildEducationSettings extends AuditableEntity {
     })
     private StageOverride highOverride;
 
-    @Column(name = "high_manual_juku_cost", precision = 19, scale = 4)
-    private BigDecimal highManualJukuCost;
-
     // --- University ---
+    /** University track selection (UniversityTrack). */
     @Enumerated(EnumType.STRING)
     @Column(name = "university_track")
     private UniversityTrack universityTrack = UniversityTrack.NONE;
 
+    /** University custom override (manualTuition is annual tuition; manualDuration is duration in months, e.g., 36 or 48). */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "uni_manual_tuition", precision = 19, scale = 4)),
@@ -123,9 +137,14 @@ public class ChildEducationSettings extends AuditableEntity {
     })
     private StageOverride universityOverride;
 
+    /**
+     * University custom MONTHLY living cost in JPY when studying abroad or living away from home.
+     * (Under Issue 110 specification, the frontend converts annual user input to monthly before submission).
+     */
     @Column(name = "uni_manual_living_cost", precision = 19, scale = 4)
     private BigDecimal manualLivingCost;
 
+    /** Whether the student lives away from home or studies abroad during university. */
     @Column(name = "is_away_from_home")
     private Boolean isAwayFromHome = false;
 
@@ -137,8 +156,8 @@ public class ChildEducationSettings extends AuditableEntity {
     public ChildEducationSettings(Long childProfileId,
             PreschoolTrack preschoolTrack, StageOverride preschoolOverride,
             PrimaryTrack primaryTrack, StageOverride primaryOverride,
-            MiddleTrack middleTrack, StageOverride middleOverride, BigDecimal middleManualJukuCost,
-            HighTrack highTrack, StageOverride highOverride, BigDecimal highManualJukuCost,
+            MiddleTrack middleTrack, StageOverride middleOverride,
+            HighTrack highTrack, StageOverride highOverride,
             UniversityTrack universityTrack, StageOverride universityOverride, BigDecimal manualLivingCost,
             Boolean isAwayFromHome) {
         this.childProfileId = Objects.requireNonNull(childProfileId, "childProfileId must not be null");
@@ -148,10 +167,8 @@ public class ChildEducationSettings extends AuditableEntity {
         this.primaryOverride = primaryOverride;
         this.middleTrack = middleTrack != null ? middleTrack : MiddleTrack.NONE;
         this.middleOverride = middleOverride;
-        this.middleManualJukuCost = middleManualJukuCost;
         this.highTrack = highTrack != null ? highTrack : HighTrack.NONE;
         this.highOverride = highOverride;
-        this.highManualJukuCost = highManualJukuCost;
         this.universityTrack = universityTrack != null ? universityTrack : UniversityTrack.NONE;
         this.universityOverride = universityOverride;
         this.manualLivingCost = manualLivingCost;
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/StageOverride.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/StageOverride.java
index dc0a82ee..95d25d12 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/StageOverride.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/StageOverride.java
@@ -10,6 +10,22 @@ import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
 
+/**
+ * Value object representing user-customized overrides for an educational stage.
+ *
+ * <p>When fields are null, the simulation falls back to baseline values
+ * configured in {@link EducationConfig}.</p>
+ *
+ * <p><b>Important Unit Conventions:</b></p>
+ * <ul>
+ *   <li><b>manualTuition:</b> User-specified <b>Annual Tuition</b> in JPY (e.g., 1,500,000 JPY).
+ *       Overrides the baseline track annual tuition.</li>
+ *   <li><b>manualDuration:</b> Total study duration strictly in <b>Months</b> (e.g., 36 months for 3 years;
+ *       must NOT be set as number of years).</li>
+ *   <li><b>manualStartMonth:</b> Entrance calendar month (1–12, e.g., 4 for April, 9 for September).</li>
+ *   <li><b>manualTermsPerYear:</b> Payment installments per academic year (e.g., 2).</li>
+ * </ul>
+ */
 @Embeddable
 @Getter
 @Setter
@@ -18,15 +34,31 @@ import lombok.Setter;
 @Builder
 public class StageOverride {
 
+    /**
+     * User-specified annual tuition in JPY (e.g., 1,500,000 JPY).
+     * If null, falls back to the baseline track annual tuition from configuration properties.
+     */
     @Column(name = "manual_tuition", precision = 19, scale = 4)
     private BigDecimal manualTuition;
 
+    /**
+     * User-specified study duration strictly in MONTHS (e.g., 36 for 3 years, 72 for 6 years; NOT in years).
+     * If null, falls back to the baseline track duration from configuration properties.
+     */
     @Column(name = "manual_duration")
     private Integer manualDuration;
 
+    /**
+     * User-specified entrance calendar month (1–12, e.g., 4 for April, 9 for September).
+     * If null, falls back to the baseline track start month.
+     */
     @Column(name = "manual_start_month")
     private Integer manualStartMonth;
 
+    /**
+     * User-specified payment installments per academic year (e.g., 2).
+     * If null, falls back to the baseline track termsPerYear.
+     */
     @Column(name = "manual_terms_per_year")
     private Integer manualTermsPerYear;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java
index 5a4c9a12..54dc6278 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java
@@ -1,7 +1,5 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
-import java.math.BigDecimal;
-
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -18,5 +16,4 @@ public class HighConfigRequest {
     private TrackConfigRequest publicTrack;
     private TrackConfigRequest privateTrack;
     private TrackConfigRequest intlTrack;
-    private BigDecimal jukuCost;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigResponse.java
index 8ff0141c..f91b03ba 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigResponse.java
@@ -1,7 +1,5 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
-import java.math.BigDecimal;
-
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -18,5 +16,4 @@ public class HighConfigResponse {
     private TrackConfigResponse publicTrack;
     private TrackConfigResponse privateTrack;
     private TrackConfigResponse intlTrack;
-    private BigDecimal jukuCost;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java
index 783c6a4d..3fcb946a 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java
@@ -1,7 +1,5 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
-import java.math.BigDecimal;
-
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -18,5 +16,4 @@ public class MiddleConfigRequest {
     private TrackConfigRequest publicTrack;
     private TrackConfigRequest privateTrack;
     private TrackConfigRequest intlTrack;
-    private BigDecimal jukuCost;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigResponse.java
index 6916191e..76197298 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigResponse.java
@@ -1,7 +1,5 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
-import java.math.BigDecimal;
-
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -18,5 +16,4 @@ public class MiddleConfigResponse {
     private TrackConfigResponse publicTrack;
     private TrackConfigResponse privateTrack;
     private TrackConfigResponse intlTrack;
-    private BigDecimal jukuCost;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java
index fef03b79..ef317058 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java
@@ -58,11 +58,9 @@ public class ChildEducationSettingsResponse {
 
     private MiddleTrack middleTrack;
     private StageOverrideResponse middleOverride;
-    private BigDecimal middleManualJukuCost;
 
     private HighTrack highTrack;
     private StageOverrideResponse highOverride;
-    private BigDecimal highManualJukuCost;
 
     private UniversityTrack universityTrack;
     private StageOverrideResponse universityOverride;
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java
index 6928a682..605ad1c2 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java
@@ -57,14 +57,10 @@ public class UpdateChildEducationSettingsRequest {
     private MiddleTrack middleTrack;
     @Valid
     private StageOverrideRequest middleOverride;
-    @PositiveOrZero(message = "middleManualJukuCost must be greater than or equal to 0")
-    private BigDecimal middleManualJukuCost;
 
     private HighTrack highTrack;
     @Valid
     private StageOverrideRequest highOverride;
-    @PositiveOrZero(message = "highManualJukuCost must be greater than or equal to 0")
-    private BigDecimal highManualJukuCost;
 
     private UniversityTrack universityTrack;
     @Valid
diff --git a/finance-hub-backend/hub/src/main/resources/application-h2.properties b/finance-hub-backend/hub/src/main/resources/application-h2.properties
index 36d32da8..28f88fbd 100644
--- a/finance-hub-backend/hub/src/main/resources/application-h2.properties
+++ b/finance-hub-backend/hub/src/main/resources/application-h2.properties
@@ -357,8 +357,6 @@ finance-hub.education-config.middle.intl-track.start-month=9
 finance-hub.education-config.middle.intl-track.terms-per-year=2
 finance-hub.education-config.middle.intl-track.entrance-fee=300000
 
-finance-hub.education-config.middle.juku-cost=300000
-
 # High Defaults
 finance-hub.education-config.high.public-track.tuition=0
 finance-hub.education-config.high.public-track.duration=36
@@ -378,8 +376,6 @@ finance-hub.education-config.high.intl-track.start-month=9
 finance-hub.education-config.high.intl-track.terms-per-year=2
 finance-hub.education-config.high.intl-track.entrance-fee=300000
 
-finance-hub.education-config.high.juku-cost=400000
-
 # University Defaults
 finance-hub.education-config.university.jp-national-track.tuition=500000
 finance-hub.education-config.university.jp-national-track.duration=48
@@ -435,6 +431,6 @@ finance-hub.education-config.university.australia-track.start-month=2
 finance-hub.education-config.university.australia-track.terms-per-year=2
 finance-hub.education-config.university.australia-track.entrance-fee=0
 
-finance-hub.education-config.university.living-cost=1500000
+finance-hub.education-config.university.living-cost=125000
 
 
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index 10828322..76623f52 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -348,8 +348,6 @@ finance-hub.education-config.middle.intl-track.start-month=9
 finance-hub.education-config.middle.intl-track.terms-per-year=2
 finance-hub.education-config.middle.intl-track.entrance-fee=300000
 
-finance-hub.education-config.middle.juku-cost=300000
-
 # High Defaults
 finance-hub.education-config.high.public-track.tuition=0
 finance-hub.education-config.high.public-track.duration=36
@@ -369,8 +367,6 @@ finance-hub.education-config.high.intl-track.start-month=9
 finance-hub.education-config.high.intl-track.terms-per-year=2
 finance-hub.education-config.high.intl-track.entrance-fee=300000
 
-finance-hub.education-config.high.juku-cost=400000
-
 # University Defaults
 finance-hub.education-config.university.jp-national-track.tuition=500000
 finance-hub.education-config.university.jp-national-track.duration=48
@@ -426,5 +422,5 @@ finance-hub.education-config.university.australia-track.start-month=2
 finance-hub.education-config.university.australia-track.terms-per-year=2
 finance-hub.education-config.university.australia-track.entrance-fee=0
 
-finance-hub.education-config.university.living-cost=1500000
+finance-hub.education-config.university.living-cost=125000
 
diff --git a/finance-hub-backend/todo/pending fix/issue-110.md b/finance-hub-backend/todo/pending fix/issue-110.md
index 7ab9271c..1f459218 100644
--- a/finance-hub-backend/todo/pending fix/issue-110.md
+++ b/finance-hub-backend/todo/pending fix/issue-110.md
@@ -63,3 +63,163 @@
 
 ## 跟LifeEvent的關係
 使用者原本設想「使用者需要模擬補習費的話，自行用系統既有的`LifeEvent`（通用支出事件）機制設定」。查證後發現`LifeEvent`的`YEARLY`頻率類型目前的比例分攤方式不夠精確，使用者已決定直接把`LifeEvent`升級成月級別計算（見[feature-requirement-130](feature-requirement-130.md)），不是修一個獨立的bug——跟這裡的補習費移除決定是兩件獨立的事，不管`LifeEvent`升級什麼時候做，都不影響這裡「把補習費從教育設定移除」這個決定本身。
+
+---
+
+## 【最終拍板定案：全系統底層「純月費」架構藍圖與分階段實作細節】（2026-10 討論校準定案）
+
+> **⚠️ 閱讀指引（Single Source of Truth）**：
+> 上方原文為早期與 Claude 討論時的過程草稿。當時草稿殘留了「在後端 Resolver 做除法」的舊提案痕跡，且遺漏了補習費移除的完整全域鏈路。
+> 為避免實作範圍膨脹混淆，**本 Issue 明確拆分為兩個獨立階段：【階段一：海外生活費改純月費】與【階段二：補習費全系統徹底拔除】**。
+> **後續所有實作與驗收，一律以本章節為唯一權威依據！**
+
+---
+
+### 一、 核心架構哲學：比照 Plan-01 的 Duration 模式，全系統底層純月費
+
+在 [Plan-01.md](Plan-01.md) 中，就讀時長（Duration）確立了一個極為成功的模式：
+- **使用者體感**：前端畫面上填寫直覺的「年」（例如 3 年、4 年、6 年）。
+- **前端轉換**：在前端輸入與預覽的當下，自動換算為「月」（`yearsToMonths(years) = years * 12`）送進 API；從 API 讀取後乘回 12 呈現給使用者。
+- **後端與底層**：資料庫、Entity、DTO、Resolver、Projector、Calculator **全鏈路 100% 只存「月」，後端完全不存「年」，徹底杜絕單位混亂**。
+
+**海外生活費（Living Cost）完全比照此模式辦理：**
+1. **前端（UI & Hook）進行年月轉換**：
+   - 使用者在前端看到與填寫的依然是直覺的「每年生活費 (¥/year)」，但前端提供即時的「每月折算預覽」（例如輸入 1,500,000 円/年，下方即時提示「約 ¥125,000 / 月」）。
+   - **送出 API 前**：前端自動除以 12 並**無條件進位至整數円**（`manualLivingCost = Math.ceil(annual / 12)`），貫徹財務規劃「保守預算原則（寧可多抓預算，不可低估吃土）」，且日幣實體無小數。
+   - **從 API 讀取後**：前端自動乘回 12 呈現給使用者填寫框。
+2. **後端全鏈路只存「月生活費」**：
+   - 全域設定（`application.properties`）：預設大學生活費直接改設為月費（例如原本 ¥1,500,000/年 改為 **¥125,000/月**）。
+   - 後端領域模型（`ChildEducationSettings.manualLivingCost`、`UniversityConfig.livingCost`）全都是月費。
+   - `EducationProfileResolver`：全域 Config 是月費，使用者覆寫也是月費，Resolver **直接合併，完全不需要做任何除以 12 的除法**！
+3. **計算引擎：看有幾個月就加幾個月的生活費**：
+   - `EducationExpenseCalculator`：計算目標西元年時，先計算該年該階段實際重疊的月份數 `activeMonths`，接著直接做單純乘法：
+     $$\text{年度生活費} = \text{monthlyLivingCost} \times \text{activeMonths}$$
+     不再有任何除以 12，杜絕小數精度遺失，徹底解決跨年多算整年的 Bug！
+
+---
+
+### 二、 分階段實作計畫與精準修改清單
+
+#### 【階段一】生活費：全鏈路「純月費」實作清單（精簡、高內聚）
+
+*範圍說明：生活費實作範圍其實非常輕量，後端核心命名重構已全部完成並編譯通過，僅剩 Calculator 計算乘法、設定檔預設值改為 125000，以及前端 1 個輸入框的換算。*
+
+1. **後端核心 Pipeline（已透過 IDE 重構完成 ✅）**：
+   - [x] [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)：`annualLivingCost` 已重命名為 `monthlyLivingCost`。
+   - [x] [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)：第 86 行已改為帶入 `stageData.getMonthlyLivingCost()`。
+   - [x] [EducationAbsoluteTimeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java)：`StageInterval` record 的欄位已改為 `monthlyLivingCost`。
+   - [x] [EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)：`mergeStage` 參數已重命名為 `monthlyLivingCost`，合併時不作除法。
+   - [x] [UniversityConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java)：JavaDoc 已明確標註為月費（Monthly Living Cost）。
+   - [x] [ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)：JavaDoc 已明確標註為月費。
+
+2. **後端計算引擎（已完成調整 ✅）**：
+   - [x] [EducationExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java)：
+     - `calculateAnnualCost`：補上重疊月份計算，改為 `monthlyLivingCost.multiply(BigDecimal.valueOf(activeMonths))`。
+     - `calculateCostForRemainingYear`：移除原本多餘的 `/ 12`，改為直接 `monthlyLivingCost.multiply(BigDecimal.valueOf(activeMonths))`。
+
+3. **後端全域預設值（已完成修改 ✅）**：
+   - [x] [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties) 與 [application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：
+     - 將 `finance-hub.education-config.university.living-cost` 預設金額從年費 `1500000` 改為月費 `125000`。
+     *(註：`EducationConfigDefaultProperties` 與 `EducationConfigResult` 欄位本來就叫 `livingCost`，純透傳無須改動程式碼)*
+
+4. **後端單元測試（已完成補充 ✅）**：
+   - [x] [EducationExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java)：新增生活費依月份重疊數分攤之測試案例。
+   - [x] [EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)：mock config 的預設生活費同步配合改為 125,000。
+
+5. **前端 UI 與轉換層（已完成調整 ✅）**：
+   - [x] [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)：
+     - 大學卡片 `manualLivingCost`：輸入框維持填寫年費（¥/year），並顯示即時換算的月費提示。
+     - 讀取時乘回 12 填入，送出前除以 12 並無條件進位（`Math.ceil(annual / 12)`）存為月費。
+   - [x] [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)：
+     - `ChildEducationSettingsDto` 的 `manualLivingCost` 註解明確為月費。
+
+---
+
+#### 【階段二】補習費（JukuCost）：徹底移除之完整清單（乾淨連根拔除）
+
+*範圍說明：這部分是文件顯得較長的主要原因，因為補習費在系統中曾有全套 CRUD。未來統一改由 LifeEvent 取代，故從教育模組徹底移除。*
+
+1. **後端全域設定（Config DTO / Entity / Factory）（已完成刪除 ✅）**：
+   - [x] [MiddleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java)（刪除 `jukuCost`）
+   - [x] [HighConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java)（刪除 `jukuCost`）
+   - [x] [MiddleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java)（刪除 `jukuCost`）
+   - [x] [MiddleConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigResponse.java)（刪除 `jukuCost`）
+   - [x] [HighConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java)（刪除 `jukuCost`）
+   - [x] [HighConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigResponse.java)（刪除 `jukuCost`）
+   - [x] [EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java)（刪除 Middle/High 的 `jukuCost`）
+   - [x] [EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java)（移除 `jukuCost` 映射）
+   - [x] [EducationConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java)（刪除 `jukuCost`）
+   - [x] [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties), [application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties), [.env.base](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.base), [.env.dev](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.dev), [.env.prod](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.prod)（同步刪除 `JUKU_COST` 並校正大學生活費為純月費 125,000）
+2. **後端個人設定（Settings DTO / Entity / Service）（已完成刪除 ✅）**：
+   - [x] [ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)（刪除 `middleManualJukuCost`, `highManualJukuCost`）
+   - [x] [UpdateChildEducationSettingsRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java)（刪除兩欄位）
+   - [x] [ChildEducationSettingsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java)（刪除兩欄位）
+   - [x] [UpdateChildEducationSettingsCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java)（刪除兩欄位）
+   - [x] [ChildEducationSettingsResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java)（刪除兩欄位）
+   - [x] [ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)（移除賦值邏輯）
+3. **後端計算管道（Pipeline & Engine）（已完成刪除 ✅）**：
+   - [x] [EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)（移除 `jukuCost` 合併解析）
+   - [x] [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)（刪除 `annualJukuCost` 欄位）
+   - [x] [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)（移除傳入 StageInterval）
+   - [x] [EducationAbsoluteTimeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java)（`StageInterval` record 移除 `annualJukuCost` 欄位）
+   - [x] [EducationExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java)（移除 `annualJukuCost` 加總）
+4. **前端介面與型別（UI & Types）（已完成刪除 ✅）**：
+   - [x] [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)（移除國中、高中卡片內的「Manual Annual Juku Cost」輸入框）
+   - [x] [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)（`ChildEducationSettingsDto` 刪除 `middleManualJukuCost`, `highManualJukuCost`）
+5. **單元測試（Unit Tests）（已完成更新 ✅）**：
+   - [x] [ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)（刪除補習費相關 mock 與 assert）
+   - [x] [EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)（刪除補習費相關設定與 assert）
+   - [x] [EducationExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java)（刪除補習費相關計算測試，並適配 4 引數 StageInterval）
+   - [x] [ChildProfileServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java)（適配 4 引數 StageInterval）
+
+---
+
+## 三、 【階段一】生活費：全鏈路「純月費」實作報告（2026-10 完成）
+
+### 1. 執行目標達成摘要
+- **全鏈路純月費（Monthly Living Cost）架構成功落地**：完全貫徹 [Plan-01.md](Plan-01.md) 的 Duration 模式。後端資料庫、Entity、DTO、Resolver、Projector、Calculator 全程 100% 採用純月費，徹底杜絕年/月單位混淆與重覆除法精度損失。
+- **換學制年度精確拆分**：徹底解決換學制或海外就讀邊界時，粗略重疊即灌爆整年生活費的歷史 Bug。
+- **財務保守原則落地**：前端輸入維持使用者好理解的年費（¥/year），但在送出時統一透過 $\lceil\text{annual} / 12\rceil$（`Math.ceil`）無條件進位為整數日圓月費，兼顧使用者體驗與實體日圓無小數之特性。
+
+### 2. 各層級具體變更清單
+
+| 模組 / 層次 | 影響檔案 | 變更內容與技術細節 |
+| :--- | :--- | :--- |
+| **後端領域實體 (Domain Entities)** | [UniversityConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java)<br>[ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java) | - 補充/修正 JavaDoc，明確標註 `livingCost` 與 `manualLivingCost` 儲存單位為純月費（Monthly Living Cost in JPY）。<br>- 保持欄位命名精簡，無違和銜接既有 JPA 映射。 |
+| **核心 Pipeline (App Core)** | [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)<br>[EducationAbsoluteTimeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java)<br>[EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)<br>[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java) | - Pipeline 資料結構全面重命名為 `monthlyLivingCost`，維護者可一目了然其單位為月。<br>- `EducationProfileResolver.mergeStage` 參數更新為 `monthlyLivingCost`，純透傳合併，不進行任何除法操作。 |
+| **計算引擎 (Calculation Engine)** | [EducationExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java) | - `calculateAnnualCost`：補齊實際重疊月份計算（`activeMonths = stageEndM - stageStartM + 1`），以 `monthlyLivingCost.multiply(BigDecimal.valueOf(activeMonths))` 純乘法計算。<br>- `calculateCostForRemainingYear`：移除原本冗餘的 `/ 12` 除法，直接以 `monthlyLivingCost.multiply(BigDecimal.valueOf(activeMonths))` 計算。<br>- 完美支援 9 月入學（算 4 個月）與 6 月畢業離國（算 6 個月）之精確情境。 |
+| **全域屬性設定 (Config Properties)** | [application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)<br>[application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties) | - `finance-hub.education-config.university.living-cost` 預設值由年費 `1500000` 修改為月費 `125000`。 |
+| **前端介面與轉換 (UI & Types)** | [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)<br>[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx) | - `use-child-profile.ts`：更新英文註解明確欄位單位為月。<br>- `child-profile-section.tsx`：<br>  1. 新增獨立輸入狀態 `annualLivingCostInput` 保持年費呈現。<br>  2. 讀取 API 時自動乘回 12 填入年費輸入框。<br>  3. 使用者輸入時即時顯示動態換算提示（`≈ ¥... / month`）。<br>  4. 儲存時以 `Math.ceil(annual / 12)` 換算成整數月費存入 DTO。<br>  5. 軌道設為 `NONE` 或關閉外宿開關時自動清空與重設為 `null`。 |
+| **單元測試 (Unit Tests)** | [EducationExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java)<br>[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java) | - `EducationExpenseCalculatorTest` 新增 `shouldCalculateAnnualCostAndRemainingCostWithMonthlyLivingCost`，覆蓋入學年（4 個月）、完整年（12 個月）、畢業年（6 個月）及模擬起始月過渡期（2 個月）。<br>- `EducationProfileResolverTest` 同步更新 mock config 預設生活費為 125,000 並驗證斷言。 |
+
+### 3. 效益與後續連動
+1. **零除法運算**：後端模擬引擎運算過程不再對生活費進行除法運算，徹底杜絕 `BigDecimal` 除不盡或四捨五入的累計誤差。
+2. **單純排積木模型**：生活費計算與學費、入學金完全解耦，單純由「在學期間重疊幾個月 $\times$ 每月生活費」決定，邏輯透明直觀。
+3. **與後續任務之邊界**：
+   - **階段二（JukuCost 徹底移除）**：不受影響，隨時可按清單執行。
+   - **[Feature Requirement 131](feature-requirement-131.md)（各國獨立預設生活費）**：已建立在純月費架構之上，後續僅需在 `UniversityConfig` 擴充國別月生活費欄位與解析映射即可，無技術債負擔。
+
+---
+
+## 四、 【階段二】補習費（JukuCost）：徹底移除實作報告（2026-10 完成）
+
+### 1. 執行目標達成摘要
+- **JukuCost 徹底拔除**：貫徹補習費由泛用 `LifeEvent` 自行定義之架構方針，從教育模組中連根拔除所有專屬的 `jukuCost`、`middleManualJukuCost`、`highManualJukuCost` 與 `annualJukuCost`。
+- **全鏈路零殘留**：包含後端全域設定（MiddleConfig, HighConfig, Controller DTOs, .env, properties）、個人設定（ChildEducationSettings, Commands, DTOs, Service）、核心計算管道（Resolver, ResolvedStageData, Projector, Timeline, Calculator）、前端（TypeScript DTO, Middle/High School StageCard UI）、以及所有對應之單元測試，100% 乾淨移除。
+
+### 2. 各層級具體變更清單
+
+| 模組 / 層次 | 影響檔案 | 變更內容與技術細節 |
+| :--- | :--- | :--- |
+| **後端全域設定 (Global Config)** | [MiddleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java)<br>[HighConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java)<br>[MiddleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java)<br>[MiddleConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigResponse.java)<br>[HighConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java)<br>[HighConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigResponse.java)<br>[EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java)<br>[EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java)<br>[EducationConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java)<br>[application.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)<br>[application-h2.properties](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)<br>[.env.base](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.base)<br>[.env.dev](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.dev)<br>[.env.prod](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.prod) | - 移除 MiddleConfig 與 HighConfig 的 `jukuCost` 屬性與 JPA 欄位映射。<br>- 移除全域設定 DTO 中的 `jukuCost` 請求與回應欄位。<br>- 移除工廠類別中的屬性綁定與預設值填充。<br>- 刪除 properties 與所有環境設定檔（base, dev, prod）中的 `JUKU_COST` 設定項。 |
+| **後端個人設定 (Personal Settings)** | [ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)<br>[UpdateChildEducationSettingsRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java)<br>[ChildEducationSettingsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java)<br>[UpdateChildEducationSettingsCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java)<br>[ChildEducationSettingsResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java)<br>[ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java) | - 移除子女教育個人設定中的 `middleManualJukuCost` 與 `highManualJukuCost` 覆寫欄位。<br>- 移除 Controller 接收與回應 DTO、Application Command 與 Result 中的兩欄位。<br>- 移除 Service 實作中覆寫賦值及 Track 為 NONE 時的清空邏輯。 |
+| **後端計算管道 (Calculation Pipeline)** | [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)<br>[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)<br>[EducationAbsoluteTimeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationAbsoluteTimeline.java)<br>[EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)<br>[EducationExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java) | - 刪除 `ResolvedStageData` 的 `annualJukuCost` 欄位。<br>- 移除 `EducationProfileResolver` 解析 Middle/High 時合併預設與覆寫補習費的邏輯。<br>- 移除 `EducationTimelineProjector` 建立 StageInterval 時傳遞 jukuCost 的參數。<br>- `EducationAbsoluteTimeline.StageInterval` 簡化為 4 引數 record（`stage, startMonth, endMonth, monthlyLivingCost`）。<br>- `EducationExpenseCalculator` 移除全年費用與剩餘費用計算中的 `annualJukuCost` 累計加總。 |
+| **前端介面與型別 (Frontend UI & Types)** | [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)<br>[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx) | - `use-child-profile.ts`：從 `ChildEducationSettingsDto`、`MiddleConfigDto`、`HighConfigDto` 介面中移除補習費相關型別宣告。<br>- `child-profile-section.tsx`：移除 Middle School 與 High School 卡片中之 `extraField`（手動填寫年補習費輸入框）。 |
+| **單元測試 (Unit Tests)** | [ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)<br>[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)<br>[EducationExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculatorTest.java)<br>[ChildProfileServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java) | - 移除所有測試中的補習費 builder 賦值、手動覆寫設定與斷言。<br>- 全面適配 4 引數之 `StageInterval` record 建構函式。<br>- 測試重構為專注驗證離散事件（學費、入學金）與純月生活費按活躍月份之計算。 |
+
+### 3. 架構收益
+1. **單一職責與關注點分離**：教育模組專注於學校學制（Tuition, Entrance Fee, Living Cost abroad/away），才藝班、衝刺班等不規則與自由支出回歸泛用 `LifeEvent`，系統概念高度內聚且職責單一。
+2. **零技術債**：無留存 deprecated 欄位或 shim 轉接層，資料庫結構與代碼實現保持極致乾淨。
+
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                             10023  10.92G   6.61    10:10:24  