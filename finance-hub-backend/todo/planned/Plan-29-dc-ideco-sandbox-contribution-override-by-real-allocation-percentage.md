# Plan 29：模擬沙盒新增「假設DC/iDeCo每月總提繳額變動」情境——用parser已經抓到的真實配分比例分配到各基金，不用從現有金額反推比例

## 這份計畫怎麼來的
這是[issue-189](../pending%20fix/issue-189.md)（DC/iDeCo配分比例`allocationPercentage`查證）衍生出來的。issue-189查證「這個欄位現在有沒有用」時，進一步被問到「這個欄位將來有沒有可能用到，不要被現在的既定概念綁住」——查證過程發現兩件事：這個欄位目前在模擬引擎、前端都沒有任何消費者；但模擬引擎裡已經有一個概念相同、服務對象不同的既有功能（NISA的「假設每月提繳額變動」沙盒情境），而DC/iDeCo目前沒有對應版本。這份計畫記錄的是「把這個既有功能模式延伸到DC/iDeCo、用真實配分比例取代臨時反推」這個方向，跟issue-189裡「要不要開放編輯DB」那個更大、风险更高的決定完全獨立。

## 針對什麼問題
`FundOverrideApplier.applyGlobalContribution`（[FundOverrideApplier.java:100-164](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)）現有功能：外部呼叫者（Solver/what-if情境）可以傳入`SimulationOverrides.testNisaGrowthMonthlyContribution`或`testNisaTsumitateMonthlyContribution`（[SimulationOverrides.java:37-38](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)），假設「如果每月NISA提繳額變成X元」這個情境，系統會把這個總額依比例分配到使用者現有的各支NISA基金。

查證確認`SimulationOverrides`裡**沒有**對應DC/iDeCo版本的欄位——`testNisaGrowthMonthlyContribution`/`testNisaTsumitateMonthlyContribution`只服務NISA，DC/iDeCo目前完全沒有「假設每月總提繳額變成X元」這個沙盒情境可以測。這不是一個bug，是一個目前還不存在的能力缺口。

## 為什麼現有NISA的做法，換到DC/iDeCo可以做得更準確
查證`applyGlobalContribution`現有邏輯（[FundOverrideApplier.java:100-129](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)），因為NISA的網頁本身沒有「配分比例」這種欄位可以抓，現有做法是從每支基金**目前的絕對金額**臨時反推比例：
```java
double ratio = (double) fund.getAnnualContribution() / totalCurrentAnnual;
long allocatedMonthly = Math.round(testMonthlyContribution * ratio);
```
最後一支基金吸收四捨五入後的零頭（`testMonthlyContribution - allocatedSoFar`）。這是在「網頁沒給比例，只能从金額反推」的限制下，唯一能做的事。

但DC/iDeCo的情況不同——查證[SompoDcAllocationParserImpl.java:111-124](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)、[SbiIdecoAllocationParserImpl.java:103-116](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoAllocationParserImpl.java)確認，`allocationPercentage`這個欄位本身就是網頁上使用者在券商那邊設定好、parser直接抓下來的**真實配分比例**，不是我們自己算出來的替代值。既然這個真實比例已經存在資料庫裡，DC/iDeCo版本的沙盒情境可以直接用它，不需要像NISA那樣反推——這比反推更準確，因為它反映的是使用者真正設定的分配方式，不是「碰巧現有金額比例長這樣」的影子值。

## 建議方向：新增DC/iDeCo版本的總提繳額覆寫，預設使用資料庫裡的真實`allocationPercentage`分配

### 1. `SimulationOverrides`新增兩個欄位，比照現有NISA欄位的命名模式
```java
public record SimulationOverrides(
        Long testNisaGrowthMonthlyContribution,
        Long testNisaTsumitateMonthlyContribution,
        Long testDcMonthlyContribution,      // 新增：假設企業DC每月總提繳額變成這個金額
        Long testIdecoMonthlyContribution,   // 新增：假設iDeCo每月總提繳額變成這個金額
        ...
) { ... }
```

### 2. `FundOverrideApplier`新增對應的分配方法，改由共用計算機依「目標配分優先、事實比例回退」分配（2026-11-26 同步 [issue-189](../pending%20fix/issue-189.md) 與 [issue-197](../pending%20fix/issue-197.md)）
1. **資料來源優先權（以帳戶類型為單位整組切換）**：
   - 優先讀取 `DefinedContributionUserSettings.getTargetAllocation(type)`（`dc_target_allocation` / `ideco_target_allocation`）。
   - 若目標配分存在，且其 `allocations` 完整涵蓋該帳戶最新快照的全部 `fundCode`、`remainderFundCode` 有效且加總等於 100%，則**整組採用目標配分**。
   - 若尚未設定目標配分，或最新匯入快照的標的清單已變動導致目標配分無法完整對應，則**安全回退（fallback）使用事實表**的 `DefinedContributionFundSnapshot.allocationPercentage` 與 `DefinedContributionAccountSnapshot.remainderFundCode`（若事實端數未設定則回退第一支標的）。
2. **金額分配計算**：
   - 直接呼叫已抽出的 [DefinedContributionAllocationCalculator.calculateMonthlyContributions](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionAllocationCalculator.java)（非端數基金以 `BigDecimal` `RoundingMode.FLOOR` 計算，端數基金吸收剩餘差額），不再各自手寫 `Math.round` 與「最後一支吸收零頭」邏輯，確保模擬沙盒與真實配分算法完全一致。

### 3. `SimulationFund` 與 `SimulationContext` 需補充欄位
查證[SimulationFund.java:38-59](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)確認，這個類別目前尚未攜帶 `fundCode` 與 `allocationPercentage`，實作本計畫時需：
- 在 `SimulationFund` 補上 `fundCode`（供對應 `DcTargetAllocationSetting.allocations` 與 `remainderFundCode`）與事實 `allocationPercentage`。
- 在 `SimulationContext` 補上企業型 DC 與 iDeCo 各自的目標配分設定（或已解析好的 `effectiveAllocation`＋`effectiveRemainderFundCode`），由 [DefinedContributionContextBuilder.java:81-89,152-160](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java) 在建構階段一併帶入。

### 4. 預設行為：沒有傳入覆寫值時，完全不受影響
這個設計跟現有`FundOverrideApplier.applyOverrides`的「只套用非null欄位」原則一致（[FundOverrideApplier.java:51](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)的既有註解）——`testDcMonthlyContribution`/`testIdecoMonthlyContribution`留null時，`DefinedContributionContextBuilder`已經建構好的`SimulationFund.annualContribution`（來自資料庫裡真實的`monthlyContributionAmount × 12`）維持原樣，`allocationRatio`這個新欄位完全不會被用到——這呼應了使用者提出的「預設沒有覆蓋就是採用DB的百分比，不手動去算」這個原則。

## 這份計畫跟issue-189的分工
- issue-189處理的是「要不要開放使用者在app裡直接編輯DB裡的真實`allocationPercentage`」——這會真正改變使用者的DC/iDeCo配置資料，風險較高、牽涉介面設計決定，issue-189把這個決定留給使用者之後再拍板。
- 這份計畫處理的是「模擬沙盒內部的what-if計算，完全不碰真實DB資料」——跟issue-189的決定無關，可以獨立推進，風險遠低於開放真實編輯。

## 這份計畫沒有解決的部分
- `SimulationFund`要新增的欄位命名、`DefinedContributionContextBuilder`實際怎麼讀取並帶入——上面「建議方向」第3點只是示意，實作時的確切命名、要不要額外處理`allocationPercentage`加總不等於100%的防呆（例如parser只抓到半張表格、如WONTFIX-17記錄過的疑慮），留給實作時決定。
- 要不要連同前端一起補上對應的sandbox UI輸入欄位（目前NISA的`testNisaGrowthMonthlyContribution`這類覆寫在前端是否已經有對應輸入介面，這份計畫沒有查證，需要先確認NISA現有UI長什麼樣子再決定DC/iDeCo要不要照搬）。
- 要不要現在就投入實作，還是繼續留在planned狀態等待排入時程，留給使用者決定。
