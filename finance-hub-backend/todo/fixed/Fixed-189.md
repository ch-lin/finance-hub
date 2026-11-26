# [FIXED] 189：模擬沙盒的「目標配分比例」要另外存在使用者設定裡，不寫進DC/iDeCo基金的事實欄位`allocationPercentage`，也不受每次重新匯入影響

**DC/iDeCo基金配分比例有兩件不同的事，分成兩條路分開處理：**
1. **基金列表畫面上新增/修改基金，寫進該筆快照的事實欄位**——PATCH端點目前完全沒有真正接收新值進來，這個bug本身要修的部分，已經另開[issue-197](issue-197.md)處理，不在這份issue的範圍。
2. **模擬沙盒要用的「目標配分比例」，另外存在使用者設定裡，不寫進`allocationPercentage`**——這才是這份issue查證的重點：目標比例不受匯入影響，匯入照樣只覆寫事實欄位；Plan-29的沙盒情境可以把這個使用者設定的目標比例，當成比事實欄位優先權更高的覆蓋值。

以下內容是查證「要怎麼存這個使用者設定的目標比例」得到的結果。

## 這份issue怎麼來的
這是Plan-27（全領域開放手動編輯）查證過程中發現的。這個欄位確實有一個domain層的更新方法存在、也確實被Service層呼叫，表面上看起來這條路徑是通的，但實際追進去會發現根本沒有新值能被傳進來，是一個容易被誤判成「已經做好」的陷阱。

這份issue同時記錄了這個欄位在模擬引擎跟前端的完整查證結果（兩邊都沒有任何讀取者），以及這個欄位對應的真實券商網站操作機制。這些查證回答的是一個比「PATCH端點本身能不能動這個值」更完整的問題——這個欄位除了PATCH端點以外，現在有沒有被用在別的地方，將來有沒有可能被用到。

## 相關檔案
- [DefinedContributionServiceImpl.java:114-128](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)（`updateAllocation`，問題所在：第125行`fund.updateAllocation(amount, fund.getAllocationPercentage())`）
- [DefinedContributionFundSnapshot.java:136-139](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionFundSnapshot.java)（`updateAllocation(long contributionAmount, double allocationPercentage)`，domain方法本身查證確認正常——真的會把傳入的第二個參數賦值給`this.allocationPercentage`，問題不在這裡，是呼叫端沒有傳真正的新值進來）
- [DefinedContributionController.java:188-199](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java)（`PATCH /assets/dc/funds/{fundId}/allocation`端點，以及內部定義的`UpdateAllocationRequest` record，查證確認只有`expectedReturnRate`、`monthlyContributionAmount`兩個欄位，沒有`allocationPercentage`）
- [UpdateAllocationCommand.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/command/UpdateAllocationCommand.java)（查證確認同樣只有`expectedReturnRate`、`monthlyContributionAmount`兩個欄位）
- [SompoDcAllocationParserImpl.java:111-124](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)、[SbiIdecoAllocationParserImpl.java:103-116](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoAllocationParserImpl.java)（兩支parser都從同一列表格裡，分別把配分比例（第2欄）跟提繳金額（第3欄）當兩個獨立數字各自抓下來）
- [DefinedContributionContextBuilder.java:70-92,137-163](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java)（模擬引擎建構DC/iDeCo基金物件時讀的欄位清單，查證確認沒有`getAllocationPercentage()`）
- [SimulationFund.java:38-59](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)（模擬引擎內部使用的基金物件，查證確認欄位清單裡根本沒有`allocationPercentage`這個欄位——不是「有欄位沒讀」，是「連欄位本身都不存在」）
- [FundOverrideApplier.java:100-164](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)（`applyGlobalContribution`，NISA的「假設性總提繳額變動」沙盒情境既有實作，查證確認全文沒有引用`allocationPercentage`）
- [use-dc-data.ts:3-16](file://hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/hooks/use-dc-data.ts)（前端`DefinedContributionFundSnapshot` TypeScript介面，查證確認沒有宣告`allocationPercentage`欄位）
- [dc-table.tsx:88-99](file://hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-table.tsx)（前端DC表格欄位清單，查證確認沒有配分比例這一欄）

## PATCH端點無法真正改變這個欄位
`DefinedContributionFundSnapshot.updateAllocation`這個domain方法的簽章是：
```java
public void updateAllocation(long contributionAmount, double allocationPercentage) {
    this.monthlyContributionAmount = contributionAmount;
    this.allocationPercentage = allocationPercentage;
}
```
這個方法本身沒有任何問題——呼叫時傳入的第二個參數，會被正確賦值給這支基金的`allocationPercentage`欄位。

但`DefinedContributionServiceImpl.updateAllocation`（PATCH端點背後真正執行的Service方法）呼叫這個domain方法時：
```java
long amount = command.getMonthlyContributionAmount() != null ? command.getMonthlyContributionAmount() : 0L;
fund.updateAllocation(amount, fund.getAllocationPercentage());
```
第二個參數傳的是`fund.getAllocationPercentage()`——先從這支基金**現有**的狀態讀出目前的配分比例，再原樣傳回去給同一個物件的`updateAllocation`方法。這代表不管這次PATCH請求的意圖是什麼，`allocationPercentage`在這次呼叫前後永遠是同一個值，從來沒有真正被改變過。

往回追查`command`（`UpdateAllocationCommand`）跟對應的`UpdateAllocationRequest`（Controller層的請求record），兩者都只定義了`expectedReturnRate`、`monthlyContributionAmount`兩個欄位——代表這個請求格式本身也沒有地方可以帶上新的`allocationPercentage`值。

## 真實網頁機制：配分比例是上游設定，提繳金額是依比例算出來的下游結果，不是兩個互相獨立的事實
使用者說明了這個欄位對應的真實券商網站操作方式：使用者在網站上設定每月提繳的**總金額**，再決定要投資哪些標的、每個標的分配多少**百分比**，各個標的實際分到的**金額**是網站依總金額跟百分比算出來的；另外還有一個勾選項目，決定百分比換算成金額時除不盡的零頭該算給哪一支基金。

這代表三個數字之間的因果方向是：`總金額 × 配分比例 → 單支基金金額`（加上一個零頭分配規則），不是三個各自獨立、互不相關的事實。

這也解釋了為什麼parser要把比例跟金額當兩個獨立數字各自抓下來，而不是只抓一個再自己換算另一個——因為零頭分配規則讓「金額」無法單純用「總額 × 比例」精確還原（會有捨去/四捨五入的誤差），兩個數字都是網頁上實際顯示出來的既定事實，各自抓取是對的設計：
```java
// SompoDcAllocationParserImpl.java:111-124（SBI的iDeCo parser也是同樣結構）
if (tds.size() >= 4) {
    String commodityType = tds.get(0).text().trim();
    String fundName = tds.get(1).text().trim();
    String fundCode = extractFundCode(tds.get(1));
    double ratio = parseAmountAsDouble(tds.get(2).text());      // 配分比例，獨立抓取
    long contribution = parseAmount(tds.get(3).text());         // 提繳金額，獨立抓取

    DefinedContributionFundSnapshot newFund = new DefinedContributionFundSnapshot(
            commodityType, fundName, fundCode,
            0.0, 0.0, 0.0, 0L, 0L, 0L,
            contribution, ratio);
    accountSnapshot.addFund(newFund);
}
```

## 模擬引擎跟前端目前都沒有任何地方讀取這個欄位
**模擬引擎**：`DefinedContributionContextBuilder`建構DC/iDeCo的`SimulationFund`物件時，只讀`getCurrentMarketValue()`、`getMonthlyContributionAmount()`、`getExpectedReturnRate()`、`getFundName()`、`getId()`，從未呼叫`getAllocationPercentage()`。更根本的是，模擬引擎內部使用的`SimulationFund`這個DTO本身的欄位清單裡，完全沒有`allocationPercentage`這個欄位——不是「有欄位、沒被讀」，是「這個概念在模擬引擎裡連容身的地方都還沒有」。`FundOverrideApplier`（負責套用所有沙盒what-if覆寫的元件）全文查過一遍，同樣沒有任何引用。

**前端**：`use-dc-data.ts`裡定義的`DefinedContributionFundSnapshot` TypeScript介面，以及`dc-table.tsx`的表格欄位清單，都確認沒有這個欄位——不是「有資料、沒顯示」，是從型別宣告開始就沒有這個欄位的存在。

**這個「目前沒有消費者」的事實，不等於這個欄位沒有未來用途，兩者是分開的問題**：這個欄位實際上已經找到一個合理的未來使用情境，記錄在獨立另開的[Plan-29](../planned/Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md)——模擬沙盒的what-if情境可以用這個真實比例分配金額，不用像NISA現有做法那樣從現有金額反推比例。下一節說明為什麼這個「目標配分比例」要另外存，不能直接寫進`allocationPercentage`這個事實欄位本身。

## 另外存一份「目標配分比例」，跟事實欄位分開存放

模擬沙盒要用的「目標配分比例」不應該寫進`allocationPercentage`這個事實欄位，不管[issue-197](issue-197.md)有沒有把這個事實欄位修好，目標比例都要另外存，理由是比照這個專案裡已經存在的「事實欄位 vs 使用者設定欄位分開放」這個既有慣例（類比薪資欄位匯入覆寫跟薪資使用者設定分開放的既有模式）——目標比例是使用者在沙盒情境裡想測試的假設值，不是這支基金目前真實的配置，兩者意義不同，不能共用同一個欄位。具體要新增一張表還是加在既有表上，見下方「兩個方案」。

### 查證確認：沒有現成的per-fund使用者設定表可以直接拿來用

重新查證整個`hub-domain`/`hub-app`模組，確認目前已經存在的「使用者設定」表只有三張，都是**帳戶/全域層級**的單筆設定（用`repository.findFirstBy()`這種「整個帳戶只有一筆設定」的模式讀取，沒有任何一張是跟單支基金一對一或多對一關聯的）：
- [SalaryUserSettings.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryUserSettings.java)（`salary_user_settings`）
- [NisaUserSettings.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaUserSettings.java)（`nisa_user_settings`）
- [DefinedContributionUserSettings.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java)（`dc_user_settings`）——這張表最接近這份issue的主題，但查證它完整的欄位清單（`isDcContributionYearsOverridden`、`dcContributionYearsOverride`、`isIdecoContributionYearsOverridden`、`idecoContributionYearsOverride`、`monthlyIdecoContribution`、`idecoExpectedReturnRate`、`monthlyDcMatchingContribution`）確認，這些都是**帳戶層級**的全域設定（例如「整個iDeCo帳戶每月提繳多少、預期報酬率是多少」），完全沒有任何欄位對應到「某一支特定基金的目標配分比例」。

**結論：沒有任何一張現有的表可以直接PATCH出單支基金的目標比例，這份issue要處理的是「要用一張全新的表、還是在既有`dc_user_settings`上加一欄」，不是去改`DefinedContributionFundSnapshot`這張事實表本身。**

### 關聯鍵一定要用`fundCode`，不能用`DefinedContributionFundSnapshot.id`——這點跟下面選哪個方案無關，兩個方案都適用

**查證確認匯入有兩條路徑，不是每條路徑都會讓`DefinedContributionFundSnapshot.id`改變**：
- **瀏覽器擴充功能路徑**（[DefinedContributionServiceImpl.saveSnapshot](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)）：會先用`dcRepository.deleteByRecordDateAndType(command.getRecordDate(), command.getType())`把同一天、同一帳戶類型的既有snapshot整批刪除，再整批重新建立——這條路徑下`id`確實會變。
- **檔案掃描路徑**（[DefinedContributionJobServiceImpl.processFile](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionJobServiceImpl.java)）：如果同一天、同一帳戶類型已經有既有snapshot，走的是`mergeAllocationFacts`（[DefinedContributionAccountSnapshot.java:123-140](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)），內部用`findFundByCode(otherFund.getFundCode())`找到既有的那一列基金直接更新欄位，這條路徑下`id`不會變。

**結論不變，但理由要修正：`id`不能當長期關聯鍵，不是因為「每次匯入都會整批刪除重建」（這只在瀏覽器擴充功能這一條路徑成立），是因為至少有一條真實存在的匯入路徑（瀏覽器擴充功能）會讓`id`改變——只要有一條路徑會讓id失去穩定性，就不能拿它當長期關聯鍵，不需要「每次」都如此才成立。**

**另外查證確認，瀏覽器擴充功能這條路徑目前根本不會傳入配分比例或金額**：[SaveDefinedContributionFundSnapshotCommand.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/command/SaveDefinedContributionFundSnapshotCommand.java)這個命令物件完全沒有`allocationPercentage`、`monthlyContributionAmount`這兩個欄位；[DefinedContributionServiceImpl.saveSnapshot](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)建構新基金時沒有傳這兩個值，會沿用entity預設值`0`。這不是這份issue要修的bug，是瀏覽器擴充功能這個資料來源本身還沒做到位——原則上它以後也該像檔案掃描的parser一樣，從網頁上抓每支基金的比例跟金額。189跟197都不應該為了遷就這條還沒做的路徑，去改現在的事實欄位或使用者設定的設計；這條路徑補齊之後，自然會跟檔案掃描路徑一樣提供真實的比例跟金額，不需要189或197預先做任何讓步。

真正穩定、兩條匯入路徑都不會變動的識別方式，是查證[DefinedContributionFundSnapshot.java:53-56](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionFundSnapshot.java)確認存在的`fundCode`（券商給的基金代碼，`@NotNull`，不管走哪條匯入路徑，同一支基金都會是同一個代碼）。不管用哪個方案，都要用`fundCode`當key，不要用snapshot的自增id。

### 唯一鍵不能只用`(使用者, fundCode)`——還要加上帳戶類型，否則企業型DC跟iDeCo的基金代碼可能互相撞到

查證[DefinedContributionType.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionType.java)確認這個枚舉只有`IDECO`、`CORPORATE_DC`兩個值，而`DefinedContributionAccountSnapshot`（[第61-63行](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)）用這個`type`欄位分流企業型DC跟iDeCo兩種帳戶，但`DefinedContributionUserSettings`（`dc_user_settings`）這張表本身並沒有依帳戶類型分成兩列——查證它的欄位清單確認，同一張表裡`monthlyIdecoContribution`（iDeCo專屬）跟`monthlyDcMatchingContribution`（企業型DC專屬）是並列的兩個欄位，代表這張表設計上就是「一個使用者一列，裡面同時裝企業型DC跟iDeCo兩邊的設定」，不是「一個使用者+一個帳戶類型一列」。

這代表如果新表（或新欄位）只用`(ownerUserId, fundCode)`當唯一鍵，企業型DC的某支基金跟iDeCo的某支基金如果剛好用了同一個`fundCode`（不同券商/不同帳戶類型完全可能發生代碼撞在一起，這不是一個可以排除的情境），兩者的目標比例會被誤判成同一筆設定、互相覆蓋。唯一鍵必須是`(ownerUserId, defineContributionType, fundCode)`三者一起，不能只有兩者。

### 兩個方案，不代為決定

**方案一：新增一張獨立的per-fund使用者設定表**
```java
@Entity
@Table(name = "dc_fund_user_settings")
@Getter
@Setter
@NoArgsConstructor
public class DcFundUserSettings extends MultiUserBaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private DefinedContributionType type;

    @Column(name = "fund_code", nullable = false)
    private String fundCode;

    @Column(name = "target_allocation_percentage")
    private Double targetAllocationPercentage;
}
```
用`(ownerUserId, type, fundCode)`當複合唯一鍵——比上一版草稿多了`type`這一欄，理由見上方「唯一鍵不能只用`(使用者, fundCode)`」一節。一個使用者對同一帳戶類型下的同一支基金只會有一筆目標比例設定。
- 優點：跟`SalaryUserSettings`/`NisaUserSettings`/`DefinedContributionUserSettings`這些現有表一樣，是獨立的entity，一支基金一列，日後要加其他per-fund設定（例如零頭歸屬規則）時，加欄位就好，不用動JSON結構。
- 缺點：需要新增entity、repository、service三層，改動範圍比方案二大。

**方案二：在既有`dc_user_settings`上加一個JSON欄位，key要同時帶帳戶類型跟`fundCode`**

查證[SalaryRecord.java:192-198](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryRecord.java)確認，這個專案已經有「單一entity上用一個`Map`型JSON欄位，裝數量不固定的key-value資料」的既有先例（`details`欄位：`@JdbcTypeCode(SqlTypes.JSON)` + `Map<String, Long>`）。`DefinedContributionUserSettings`本身也是`findFirstBy()`這種「整個帳戶只有一筆設定」的既有表，可以在上面加一欄：
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "fund_target_allocation_percentage", columnDefinition = "json")
private Map<String, Double> fundTargetAllocationPercentage = new HashMap<>();
```
**這裡的key不能只用`fundCode`，要用`type + ":" + fundCode`這種組合字串（例如`"CORPORATE_DC:ABC123"`）**，理由同上——這張表本身是企業型DC跟iDeCo共用的一列，單用`fundCode`當key會讓兩種帳戶類型剛好代碼相同的基金互相覆蓋。
- 優點：不用新增entity/repository/service，只要在既有的`DefinedContributionUserSettingsServiceImpl`（查證確認這個service已經存在，問題1已查過路徑）上加讀寫這個Map的邏輯，改動範圍最小；跟專案裡`details`欄位的既有先例一致，不是新發明一套模式。
- 缺點：這個欄位混在一張本來裝「整個帳戶層級設定」的表裡，語意上比較不直觀（這張表其餘欄位都是標量、這一欄卻是per-fund的字典）；key必須自己組合字串才能同時表達帳戶類型跟基金代碼，不如方案一用兩個獨立欄位直觀；日後如果要在「目標比例」之外再加其他per-fund設定（例如零頭歸屬規則），要麼再加一個Map欄位、要麼把value從`Double`換成一個小物件，不如方案一的獨立entity那樣直接加欄位。

### 新的PATCH端點該長什麼樣子

不管選哪個方案，都不是改動現有[DefinedContributionController.java](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java)裡那個操作`allocationPercentage`事實欄位的端點，而是新增一個獨立的端點（例如`PATCH /assets/dc/{type}/funds/{fundCode}/target-allocation`，路徑裡要帶上帳戶類型，不能只用`fundCode`識別），方案一的話寫入新表的`targetAllocationPercentage`、方案二的話寫入`dc_user_settings`那個Map欄位裡對應組合key的那一項——跟現有`PATCH /assets/dc/funds/{fundId}/allocation`（操作`expectedReturnRate`、`monthlyContributionAmount`這兩個事實/使用者欄位混合的既有端點）完全分開，不要混在一起改。

不管選哪個方案，這個新欄位都只存「使用者想要的目標比例」，完全不去動`DefinedContributionFundSnapshot.allocationPercentage`這個事實欄位——事實欄位被覆寫的方式（匯入時：檔案掃描路徑走`mergeAllocationFacts`逐欄位merge、瀏覽器擴充功能路徑走`deleteByRecordDateAndType`整批刪除重建；以及[issue-197](issue-197.md)落地後的基金列表PATCH）這份issue不去改動，使用者設定欄位完全不受這些既有/未來的事實欄位寫入路徑影響，這跟finance-hub既有「bank-fact欄位不能被使用者設定覆蓋、只能在重新匯入時被覆寫，使用者設定是獨立欄位」這個慣例是同一個方向，只是方向相反——這裡是「這份issue的目標比例，不該寫進事實欄位；事實欄位本身要不要能被PATCH編輯，是197的範圍，跟這份issue的使用者設定表無關」。

### 這張新表要怎麼被使用，留給Plan-29

這張表存在之後，要怎麼在模擬引擎裡被讀取、套用到沙盒what-if情境，已經有獨立的[Plan-29](../planned/Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md)在規劃——但Plan-29目前的草稿（第58行`fund.getAllocationRatio()`）寫的是直接讀事實欄位`allocationPercentage`，不是這份issue討論的「使用者設定的目標比例」。如果使用者確認要新增這張使用者設定表，Plan-29那一段也需要同步更新成優先讀這張新表（使用者有自己設定目標比例時用使用者設定、否則fallback到事實欄位的現有比例），不能讓兩份文件各自假設不同的資料來源。這份issue不代為更新Plan-29，只在這裡記錄這個需要同步的點。

### 目標比例同樣需要加總=100%、同樣需要端數歸屬，但不能直接套用197那組事實設定

[issue-197](issue-197.md)已經決定「事實欄位`allocationPercentage`這次PATCH後的加總必須等於100%，否則前端擋下來、後端回400」，以及「新增端數欄位，標記哪支基金吸收零頭」。這份issue的「目標配分比例」本質上是同一種東西（配分比例），同樣的問題跑不掉——但資料跟197是兩組完全獨立的數字，不能直接共用。

**加總=100%這個驗證規則要重做一次，不是共用同一次驗證**：197驗證的是「事實欄位`allocationPercentage`這次PATCH後的加總」，這份issue要驗證的是「使用者設定的目標比例這次PATCH後的加總」——這是兩組完全獨立的百分比數字（事實比例是這支基金目前真實配置多少，目標比例是使用者在沙盒情境裡想測試的假設值，兩者不保證相同，也不需要相同）。不能拿事實欄位算出的加總結果去判斷目標比例合不合法，兩邊各自要有自己的「加總=100%，否則拒絕」檢查，用的是同一套規則（跟197一樣：前端先擋、後端400 Bad Request），但是兩次獨立執行的驗證，不是一次共用的驗證結果。

**端數歸屬要不要直接沿用197已經決定的事實端數設定，是一個留給使用者決定的開放問題，這份issue不代為決定**：
- 直接沿用197的端數吸收者設定（用`fundCode`識別哪支基金吸收事實端數），好處是不用再多開一組「使用者設定的端數歸屬」欄位，省掉重複設計。
- 但缺點是少了自由度：使用者在這份issue設定的目標比例，不保證跟197指定的事實端數基金對得上——使用者在沙盒情境裡想測試的目標配置，可能完全沒有涵蓋到197那支端數基金，或者想在沙盒情境裡換成另一支基金吸收端數；而197那組端數設定是給「基金列表畫面上的真實事實編輯」用的，不是為沙盒情境設計的，兩者的使用情境不保證一致。
- 要沿用197那組設定、還是這份issue另外獨立存一份端數歸屬設定，這個取捨留給使用者決定。

## 這份文件沒有解決的部分
- **要不要真的新增這張per-fund使用者設定表，留給使用者決定**——上面已經確認「新增獨立使用者設定表、跟事實欄位分開存放」是跟既有慣例一致的正確方向，但要不要現在就動手做、新表的欄位要不要比草稿更豐富（例如要不要也讓使用者設定零頭歸屬規則），留給使用者決定。
- **目標比例的端數歸屬要不要沿用197事實端數吸收者的設定，還是另外獨立存一份**——上面「目標比例同樣需要加總=100%、同樣需要端數歸屬」一節已經說明這個取捨，留給使用者決定。
- **前端對應的配分比例編輯widget**——留給之後真正要落地編輯功能時一併設計，不是這份issue的範圍。
- **如果這張新表定案，Plan-29需要同步更新成優先讀它**——Plan-29目前的草稿直接讀事實欄位`allocationPercentage`，上面已經指出這個待辦，但實際怎麼改Plan-29的草稿程式碼，留給之後兩份文件一起定案時處理。

---

## 定案與實作紀錄（2026-11-26）

1. **儲存方案定案（改良版方案二：結構化 JSON 欄位）**
   - 新增值物件 [DcTargetAllocationSetting.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DcTargetAllocationSetting.java)（包含 `remainderFundCode` 與 `Map<String, Double> allocations`）。
   - 於 [DefinedContributionUserSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java) 新增兩個獨立 JSON 欄位：`dc_target_allocation`（`CORPORATE_DC`）與 `ideco_target_allocation`（`IDECO`），天然隔離兩種帳戶類型，不會產生 `fundCode` 碰撞，也不需字串拼接 key。
   - 目標端數基金（`remainderFundCode`）獨立存於各目標配分物件內，與 [issue-197](issue-197.md) 的事實端數設定完全解耦。

2. **驗證與 API 設計**
   - 端點改為帳戶類型層級批次操作，避免單支 PATCH 卡 100% 死結：
     - `PUT /assets/dc/user-settings/{type}/target-allocations`：整組驗證並更新目標配分。
     - `DELETE /assets/dc/user-settings/{type}/target-allocations`：清除該帳戶類型的目標配分（回退至事實比例）。
   - 直接複用抽離後的 [DefinedContributionAllocationCalculator.validateAndExtractPercentages](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionAllocationCalculator.java)，並傳入該帳戶類型最新快照的全部 `fundCode` 作為 `requiredFundCodes` 嚴格檢核。
   - 既有 `PUT /assets/dc/user-settings` 更新標量設定時不會覆寫這兩個目標配分欄位。

3. **文件與前端後續**
   - 已同步更新 [Plan-29](../planned/Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md) 改為優先讀取 `dc_user_settings` 目標配分並複用 `DefinedContributionAllocationCalculator`。
   - 前端目標配分設定 UI 尚未實作（留待後續決定放於 `/finance/configs` 獨立 Tab 或 `/finance/portfolios/defined-contribution`；確定不放於 `/finance/profile/me` 基本資料表單，亦不於純記憶體的 `/finance/simulator` 沙盒寫入 DB）。

## Diff frontend

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment    INSERT  git --no-pager diff                                                                                       10034  12.43G   3.21    09:38:45  
diff --git a/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-allocation-dialog.tsx b/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-allocation-dialog.tsx
index 60f886f..872a82c 100644
--- a/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-allocation-dialog.tsx
+++ b/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-allocation-dialog.tsx
@@ -6,6 +6,11 @@ import { toast } from "sonner";
 import {
   DefinedContributionAccountSnapshot,
 } from "../hooks/use-dc-data";
+import {
+  PERCENTAGE_TOLERANCE,
+  computePreviewAmounts,
+  formatAccountType,
+} from "../utils/allocation-calc";
 import { Button } from "@/components/ui/button";
 import {
   Dialog,
@@ -38,29 +43,6 @@ interface DcAllocationDialogProps {
   onUpdated: () => void;
 }
 
-const PERCENTAGE_TOLERANCE = 1e-6;
-
-function formatAccountType(type: string): string {
-  if (type === "IDECO") return "iDeCo";
-  if (type === "CORPORATE_DC") return "企業型DC";
-  return type;
-}
-
-/**
- * Computes floor(totalContribution * pct / 100) using scaled BigInt arithmetic
- * to match backend BigDecimal RoundingMode.FLOOR without IEEE-754 precision loss.
- */
-function calculateFlooredAmount(totalContribution: number, pct: number): number {
-  if (!Number.isFinite(pct) || pct <= 0 || totalContribution <= 0) {
-    return 0;
-  }
-  const normalized = pct.toFixed(6);
-  const [intPart, decPart = ""] = normalized.split(".");
-  const scaledPct = BigInt(intPart + decPart);
-  const denominator = BigInt(100) * BigInt(1_000_000);
-  return Number((BigInt(Math.trunc(totalContribution)) * scaledPct) / denominator);
-}
-
 export function DcAllocationDialog({ accounts, onUpdated }: DcAllocationDialogProps) {
   const editableAccounts = useMemo(
     () =>
@@ -153,26 +135,21 @@ export function DcAllocationDialog({ accounts, onUpdated }: DcAllocationDialogPr
     }
 
     const totalContribution = selectedAccount.monthlyTotalContribution ?? 0;
-    let nonRemainderSum = 0;
-
-    for (const fund of selectedAccount.funds) {
-      const code = fund.fundCode ?? "";
-      if (code !== remainderFundCode) {
-        const amount = calculateFlooredAmount(totalContribution, parsed[code] ?? 0);
-        previews[code] = amount;
-        nonRemainderSum += amount;
-      }
-    }
-
-    if (remainderFundCode) {
-      previews[remainderFundCode] = totalContribution - nonRemainderSum;
-    }
+    const fundCodes = selectedAccount.funds
+      .map((fund) => fund.fundCode)
+      .filter((code): code is string => Boolean(code));
+    const computedPreviews = computePreviewAmounts(
+      totalContribution,
+      fundCodes,
+      parsed,
+      remainderFundCode
+    );
 
     return {
       parsedPercentages: parsed,
       hasInvalidInput: invalid,
       totalPercentage: sum,
-      previewAmounts: previews,
+      previewAmounts: computedPreviews,
     };
   }, [selectedAccount, percentages, remainderFundCode]);
 
diff --git a/hub-ui/src/app/finance/profile/layout.tsx b/hub-ui/src/app/finance/profile/layout.tsx
index 950f820..f187d76 100644
--- a/hub-ui/src/app/finance/profile/layout.tsx
+++ b/hub-ui/src/app/finance/profile/layout.tsx
@@ -3,13 +3,14 @@
 import { usePathname, useRouter } from "next/navigation";
 import { useEffect } from "react";
 import { AnimatedTabs, TabItem } from "@/components/ui/animated-tabs";
-import { User, Users, Calendar, Home } from "lucide-react";
+import { User, Users, Calendar, Home, SlidersHorizontal } from "lucide-react";
 
 const TABS: TabItem[] = [
   { id: "me", label: "Basic Profile", icon: User },
   { id: "family", label: "Family Group", icon: Home },
   { id: "children", label: "Children & Education", icon: Users },
   { id: "timeline", label: "Timeline Events", icon: Calendar },
+  { id: "allocations", label: "Target Allocations", icon: SlidersHorizontal },
 ];
 
 export default function ProfileLayout({
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment    INSERT                                                                                                            10035  11.67G   2.26    09:43:06  

 ## Diff backend

  edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                                                          10037  12.91G   7.67    09:45:28  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java
index 1ec2c942..5d2fe2ee 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java
@@ -12,14 +12,12 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.accounting.dc;
 
-import java.math.BigDecimal;
-import java.math.RoundingMode;
 import java.time.LocalDate;
-import java.util.HashMap;
-import java.util.LinkedHashMap;
+import java.util.HashSet;
 import java.util.List;
 import java.util.Map;
 import java.util.Objects;
+import java.util.Set;
 
 import org.slf4j.Logger;
 import org.slf4j.LoggerFactory;
@@ -31,7 +29,6 @@ import org.springframework.web.server.ResponseStatusException;
 
 import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
 import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionFundSnapshotRepository;
-import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionSnapshotCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAllocationCommand;
@@ -107,9 +104,6 @@ public class DefinedContributionServiceImpl implements DefinedContributionServic
         }
     }
 
-    private static final double PERCENTAGE_SUM_TARGET = 100.0;
-    private static final double PERCENTAGE_TOLERANCE = 1e-6;
-
     @Override
     @Transactional
     public DefinedContributionFundSnapshotResult updateAllocation(@NonNull Long fundId,
@@ -143,77 +137,20 @@ public class DefinedContributionServiceImpl implements DefinedContributionServic
                     "Account snapshot has no funds");
         }
 
-        Map<String, DefinedContributionFundSnapshot> fundsByCode = new HashMap<>();
+        Set<String> accountFundCodes = new HashSet<>();
         for (DefinedContributionFundSnapshot fund : accountFunds) {
-            fundsByCode.put(fund.getFundCode(), fund);
-        }
-
-        Map<String, Double> requestedPercentages = new LinkedHashMap<>();
-        double percentageSum = 0.0;
-        for (FundAllocationCommand item : command.getAllocations()) {
-            if (item == null || item.getFundCode() == null || item.getFundCode().isBlank()
-                    || item.getAllocationPercentage() == null) {
-                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
-                        "Each allocation must include fundCode and allocationPercentage");
-            }
-            double pct = item.getAllocationPercentage();
-            if (Double.isNaN(pct) || Double.isInfinite(pct) || pct < 0.0 || pct > PERCENTAGE_SUM_TARGET) {
-                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
-                        "allocationPercentage must be between 0 and 100");
-            }
-            String fundCode = item.getFundCode();
-            if (!fundsByCode.containsKey(fundCode)) {
-                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
-                        "Unknown fundCode for this account: " + fundCode);
-            }
-            if (requestedPercentages.putIfAbsent(fundCode, pct) != null) {
-                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
-                        "Duplicate fundCode in allocations: " + fundCode);
-            }
-            percentageSum += pct;
-        }
-
-        if (requestedPercentages.size() != fundsByCode.size()) {
-            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
-                    "Allocations must include all funds in the account");
+            accountFundCodes.add(fund.getFundCode());
         }
 
         String remainderFundCode = command.getRemainderFundCode();
-        if (!fundsByCode.containsKey(remainderFundCode)) {
-            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
-                    "remainderFundCode does not exist in this account: " + remainderFundCode);
-        }
 
-        if (Math.abs(percentageSum - PERCENTAGE_SUM_TARGET) > PERCENTAGE_TOLERANCE) {
-            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
-                    "Allocation percentages must sum to 100%");
-        }
-
-        long totalContribution = account.getMonthlyTotalContribution();
-        long nonRemainderSum = 0L;
-        Map<String, Long> computedAmounts = new HashMap<>();
-        BigDecimal totalDecimal = BigDecimal.valueOf(totalContribution);
-        BigDecimal hundred = BigDecimal.valueOf(100);
-
-        for (DefinedContributionFundSnapshot fund : accountFunds) {
-            String code = fund.getFundCode();
-            if (!code.equals(remainderFundCode)) {
-                double pct = requestedPercentages.get(code);
-                long amount = totalDecimal
-                        .multiply(BigDecimal.valueOf(pct))
-                        .divide(hundred, 0, RoundingMode.FLOOR)
-                        .longValue();
-                computedAmounts.put(code, amount);
-                nonRemainderSum += amount;
-            }
-        }
+        Map<String, Double> requestedPercentages = DefinedContributionAllocationCalculator
+                .validateAndExtractPercentages(command.getAllocations(),
+                        accountFundCodes, remainderFundCode);
 
-        long remainderAmount = totalContribution - nonRemainderSum;
-        if (remainderAmount < 0) {
-            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
-                    "Computed contribution amounts exceed account monthly total contribution");
-        }
-        computedAmounts.put(remainderFundCode, remainderAmount);
+        Map<String, Long> computedAmounts = DefinedContributionAllocationCalculator.calculateMonthlyContributions(
+                account.getMonthlyTotalContribution(),
+                requestedPercentages, remainderFundCode);
 
         account.updateRemainderFundCode(remainderFundCode);
         for (DefinedContributionFundSnapshot fund : accountFunds) {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsService.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsService.java
index 9188510f..d96c26fe 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsService.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsService.java
@@ -17,7 +17,9 @@ import java.util.Optional;
 import org.springframework.lang.NonNull;
 
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDcUserSettingsCommand;
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSettingsResult;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
 
 public interface DefinedContributionUserSettingsService {
 
@@ -25,4 +27,9 @@ public interface DefinedContributionUserSettingsService {
 
     DcUserSettingsResult saveSettings(@NonNull SaveDcUserSettingsCommand command);
 
+    DcUserSettingsResult updateTargetAllocations(@NonNull DefinedContributionType type,
+            UpdateAccountAllocationsCommand command);
+
+    Optional<DcUserSettingsResult> clearTargetAllocations(@NonNull DefinedContributionType type);
+
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsServiceImpl.java
index 146cebef..5fc1cf93 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsServiceImpl.java
@@ -12,16 +12,28 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.accounting.dc;
 
+import java.util.HashSet;
+import java.util.List;
+import java.util.Map;
 import java.util.Objects;
 import java.util.Optional;
+import java.util.Set;
 
+import org.springframework.http.HttpStatus;
 import org.springframework.lang.NonNull;
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
+import org.springframework.web.server.ResponseStatusException;
 
+import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
 import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionUserSettingsRepository;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDcUserSettingsCommand;
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSettingsResult;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DcTargetAllocationSetting;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionUserSettings;
 import lombok.RequiredArgsConstructor;
 
@@ -30,6 +42,7 @@ import lombok.RequiredArgsConstructor;
 public class DefinedContributionUserSettingsServiceImpl implements DefinedContributionUserSettingsService {
 
     private final DefinedContributionUserSettingsRepository repository;
+    private final DefinedContributionAccountSnapshotRepository dcAccountRepository;
 
     @Override
     @Transactional(readOnly = true)
@@ -84,4 +97,52 @@ public class DefinedContributionUserSettingsServiceImpl implements DefinedContri
 
         return DcUserSettingsResult.fromEntity(settings, null);
     }
+
+    @Override
+    @Transactional
+    public DcUserSettingsResult updateTargetAllocations(@NonNull DefinedContributionType type,
+            UpdateAccountAllocationsCommand command) {
+        if (command == null || command.getRemainderFundCode() == null || command.getRemainderFundCode().isBlank()
+                || command.getAllocations() == null || command.getAllocations().isEmpty()) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                    "remainderFundCode and non-empty allocations are required");
+        }
+
+        DefinedContributionAccountSnapshot latestSnapshot = dcAccountRepository
+                .findFirstByTypeOrderByRecordDateDesc(type)
+                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                        "No snapshot found for account type: " + type));
+
+        List<DefinedContributionFundSnapshot> snapshotFunds = latestSnapshot.getFunds();
+        if (snapshotFunds == null || snapshotFunds.isEmpty()) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                    "Latest snapshot has no funds for account type: " + type);
+        }
+
+        Set<String> requiredFundCodes = new HashSet<>();
+        for (DefinedContributionFundSnapshot fund : snapshotFunds) {
+            requiredFundCodes.add(fund.getFundCode());
+        }
+
+        String remainderFundCode = command.getRemainderFundCode();
+        Map<String, Double> validatedPercentages = DefinedContributionAllocationCalculator
+                .validateAndExtractPercentages(command.getAllocations(), requiredFundCodes, remainderFundCode);
+
+        DefinedContributionUserSettings settings = repository.findFirstBy()
+                .orElseGet(DefinedContributionUserSettings::new);
+        settings.updateTargetAllocation(type, new DcTargetAllocationSetting(remainderFundCode, validatedPercentages));
+
+        settings = repository.save(Objects.requireNonNull(settings));
+        return DcUserSettingsResult.fromEntity(settings, null);
+    }
+
+    @Override
+    @Transactional
+    public Optional<DcUserSettingsResult> clearTargetAllocations(@NonNull DefinedContributionType type) {
+        return repository.findFirstBy().map(settings -> {
+            settings.updateTargetAllocation(type, null);
+            DefinedContributionUserSettings saved = repository.save(Objects.requireNonNull(settings));
+            return DcUserSettingsResult.fromEntity(saved, null);
+        });
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/result/DcUserSettingsResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/result/DcUserSettingsResult.java
index b1c876da..fc425f44 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/result/DcUserSettingsResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/result/DcUserSettingsResult.java
@@ -13,6 +13,7 @@
 package ch.lin.finance.hub.backend.api.app.service.accounting.dc.result;
 
 import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DcTargetAllocationSetting;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionUserSettings;
 import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
 import lombok.AllArgsConstructor;
@@ -34,6 +35,9 @@ public class DcUserSettingsResult extends BaseResult {
     private Boolean isIdecoContributionYearsOverridden;
     private Integer idecoContributionYearsOverride;
 
+    private DcTargetAllocationSetting dcTargetAllocation;
+    private DcTargetAllocationSetting idecoTargetAllocation;
+
     public static DcUserSettingsResult fromEntity(DefinedContributionUserSettings entity, String ownerUserId) {
         if (entity == null) {
             return null;
@@ -48,6 +52,9 @@ public class DcUserSettingsResult extends BaseResult {
         result.setIsIdecoContributionYearsOverridden(entity.getIsIdecoContributionYearsOverridden());
         result.setIdecoContributionYearsOverride(entity.getIdecoContributionYearsOverride());
 
+        result.setDcTargetAllocation(entity.getDcTargetAllocation());
+        result.setIdecoTargetAllocation(entity.getIdecoTargetAllocation());
+
         result.setOwnerUserId(ownerUserId);
         result.setPrivacyLevel(PrivacyLevel.PRIVATE);
 
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java
index 8f70e8d8..f499b1d9 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java
@@ -13,6 +13,8 @@
 package ch.lin.finance.hub.backend.api.domain.model.accounting.dc;
 
 import org.hibernate.annotations.Check;
+import org.hibernate.annotations.JdbcTypeCode;
+import org.hibernate.type.SqlTypes;
 
 import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
 import jakarta.persistence.Column;
@@ -51,4 +53,32 @@ public class DefinedContributionUserSettings extends MultiUserBaseEntity {
     @Column(name = "monthly_dc_matching_contribution")
     private Long monthlyDcMatchingContribution = 0L;
 
+    @JdbcTypeCode(SqlTypes.JSON)
+    @Column(name = "dc_target_allocation", columnDefinition = "json")
+    private DcTargetAllocationSetting dcTargetAllocation;
+
+    @JdbcTypeCode(SqlTypes.JSON)
+    @Column(name = "ideco_target_allocation", columnDefinition = "json")
+    private DcTargetAllocationSetting idecoTargetAllocation;
+
+    public DcTargetAllocationSetting getTargetAllocation(DefinedContributionType type) {
+        if (type == null) {
+            return null;
+        }
+        return switch (type) {
+            case CORPORATE_DC -> this.dcTargetAllocation;
+            case IDECO -> this.idecoTargetAllocation;
+        };
+    }
+
+    public void updateTargetAllocation(DefinedContributionType type, DcTargetAllocationSetting setting) {
+        if (type == null) {
+            throw new IllegalArgumentException("DefinedContributionType must not be null");
+        }
+        switch (type) {
+            case CORPORATE_DC -> this.dcTargetAllocation = setting;
+            case IDECO -> this.idecoTargetAllocation = setting;
+        }
+    }
+
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionUserSettingsController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionUserSettingsController.java
index e799a86d..a6a1e84f 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionUserSettingsController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionUserSettingsController.java
@@ -12,10 +12,14 @@
  */
 package ch.lin.finance.hub.backend.api.controller.accounting.dc;
 
+import java.util.List;
 import java.util.Objects;
 
 import org.springframework.http.ResponseEntity;
+import org.springframework.lang.NonNull;
+import org.springframework.web.bind.annotation.DeleteMapping;
 import org.springframework.web.bind.annotation.GetMapping;
+import org.springframework.web.bind.annotation.PathVariable;
 import org.springframework.web.bind.annotation.PutMapping;
 import org.springframework.web.bind.annotation.RequestBody;
 import org.springframework.web.bind.annotation.RequestMapping;
@@ -27,9 +31,15 @@ import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSet
 import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DcUserSettingsMapper;
 import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DcUserSettingsRequest;
 import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DcUserSettingsResponse;
+import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionType;
 import io.swagger.v3.oas.annotations.Operation;
 import io.swagger.v3.oas.annotations.tags.Tag;
 import jakarta.validation.Valid;
+import jakarta.validation.constraints.DecimalMax;
+import jakarta.validation.constraints.DecimalMin;
+import jakarta.validation.constraints.NotBlank;
+import jakarta.validation.constraints.NotEmpty;
+import jakarta.validation.constraints.NotNull;
 import lombok.RequiredArgsConstructor;
 
 @RestController
@@ -57,4 +67,34 @@ public class DefinedContributionUserSettingsController {
         DcUserSettingsResult result = dcUserSettingsService.saveSettings(Objects.requireNonNull(command));
         return ResponseEntity.ok(mapper.toResponse(result));
     }
+
+    @Operation(summary = "Update Target Allocations for a DC/iDeCo Account Type")
+    @PutMapping("/{type}/target-allocations")
+    public ResponseEntity<DcUserSettingsResponse> updateTargetAllocations(
+            @PathVariable @NonNull DefinedContributionType type,
+            @Valid @RequestBody UpdateTargetAllocationsRequest request) {
+        var command = mapper.toUpdateAccountAllocationsCommand(request);
+        DcUserSettingsResult result = dcUserSettingsService.updateTargetAllocations(type, command);
+        return ResponseEntity.ok(mapper.toResponse(result));
+    }
+
+    @Operation(summary = "Clear Target Allocations for a DC/iDeCo Account Type")
+    @DeleteMapping("/{type}/target-allocations")
+    public ResponseEntity<DcUserSettingsResponse> clearTargetAllocations(
+            @PathVariable @NonNull DefinedContributionType type) {
+        return dcUserSettingsService.clearTargetAllocations(type)
+                .map(mapper::toResponse)
+                .map(ResponseEntity::ok)
+                .orElse(ResponseEntity.noContent().build());
+    }
+
+    public record UpdateTargetAllocationsRequest(
+            @NotBlank String remainderFundCode,
+            @NotEmpty @Valid List<TargetFundAllocationRequest> allocations) {
+    }
+
+    public record TargetFundAllocationRequest(
+            @NotBlank String fundCode,
+            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double allocationPercentage) {
+    }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DcUserSettingsMapper.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DcUserSettingsMapper.java
index 31203a64..f6c13243 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DcUserSettingsMapper.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DcUserSettingsMapper.java
@@ -14,12 +14,20 @@ package ch.lin.finance.hub.backend.api.controller.accounting.dc.dto;
 
 import org.mapstruct.Mapper;
 
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDcUserSettingsCommand;
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DcUserSettingsResult;
+import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionUserSettingsController.TargetFundAllocationRequest;
+import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionUserSettingsController.UpdateTargetAllocationsRequest;
 
 @Mapper(componentModel = "spring")
 public interface DcUserSettingsMapper {
     DcUserSettingsResponse toResponse(DcUserSettingsResult result);
 
     SaveDcUserSettingsCommand toCommand(DcUserSettingsRequest request);
+
+    UpdateAccountAllocationsCommand toUpdateAccountAllocationsCommand(UpdateTargetAllocationsRequest request);
+
+    FundAllocationCommand toFundAllocationCommand(TargetFundAllocationRequest request);
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DcUserSettingsResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DcUserSettingsResponse.java
index 010d876a..bb69dd08 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DcUserSettingsResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DcUserSettingsResponse.java
@@ -12,6 +12,7 @@
  */
 package ch.lin.finance.hub.backend.api.controller.accounting.dc.dto;
 
+import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DcTargetAllocationSetting;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -30,4 +31,7 @@ public class DcUserSettingsResponse {
     private Integer dcContributionYearsOverride;
     private Boolean isIdecoContributionYearsOverridden;
     private Integer idecoContributionYearsOverride;
+
+    private DcTargetAllocationSetting dcTargetAllocation;
+    private DcTargetAllocationSetting idecoTargetAllocation;
 }
diff --git a/finance-hub-backend/todo/planned/Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md b/finance-hub-backend/todo/planned/Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md
index 16a24057..086f2c12 100644
--- a/finance-hub-backend/todo/planned/Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md
+++ b/finance-hub-backend/todo/planned/Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md
@@ -31,43 +31,18 @@ public record SimulationOverrides(
 ) { ... }
 ```
 
-### 2. `FundOverrideApplier`新增對應的分配方法，用真實`allocationPercentage`取代反推比例
-```java
-public void applyOverrides(SimulationContext context, SimulationOverrides overrides) {
-    ...
-    if (overrides.testDcMonthlyContribution() != null) {
-        applyGlobalContributionByRealAllocation(context.getDcFunds(), overrides.testDcMonthlyContribution());
-    }
-    if (overrides.testIdecoMonthlyContribution() != null) {
-        applyGlobalContributionByRealAllocation(context.getIdecoFunds(), overrides.testIdecoMonthlyContribution());
-    }
-    ...
-}
-
-private void applyGlobalContributionByRealAllocation(List<SimulationFund> targetFunds, long testMonthlyContribution) {
-    // 比照 applyGlobalContribution 現有的零頭處理方式：最後一支基金吸收四捨五入後的差額
-    long allocatedSoFar = 0;
-    for (int i = 0; i < targetFunds.size(); i++) {
-        SimulationFund fund = targetFunds.get(i);
-        if (i == targetFunds.size() - 1) {
-            long monthly = testMonthlyContribution - allocatedSoFar;
-            fund.setAnnualContribution(monthly * 12);
-            fund.setCourse("毎月");
-            fund.setSettingAmount(monthly);
-        } else {
-            double ratio = fund.getAllocationRatio(); // 真實配分比例，見下方第3點
-            long allocatedMonthly = Math.round(testMonthlyContribution * ratio);
-            fund.setAnnualContribution(allocatedMonthly * 12);
-            fund.setCourse("毎月");
-            fund.setSettingAmount(allocatedMonthly);
-            allocatedSoFar += allocatedMonthly;
-        }
-    }
-}
-```
-
-### 3. `SimulationFund`需要先能攜帶真實配分比例——目前這個欄位在模擬引擎內部完全不存在
-查證[SimulationFund.java:38-59](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)確認，這個類別目前的欄位清單（`fundId`、`fundName`、`balance`、`annualContribution`、`expectedReturnRate`、`nisaAccountType`、`course`、`settingAmount`）裡沒有配分比例的容身之處——這不是「有欄位沒傳」，是這個概念在模擬引擎的DTO層一開始就不存在，需要新增一個欄位（例如`allocationRatio`），並在[DefinedContributionContextBuilder.java:81-89,152-160](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java)建構`SimulationFund`時，把`record.getAllocationPercentage()`帶進來。
+### 2. `FundOverrideApplier`新增對應的分配方法，改由共用計算機依「目標配分優先、事實比例回退」分配（2026-11-26 同步 [issue-189](../pending%20fix/issue-189.md) 與 [issue-197](../pending%20fix/issue-197.md)）
+1. **資料來源優先權（以帳戶類型為單位整組切換）**：
+   - 優先讀取 `DefinedContributionUserSettings.getTargetAllocation(type)`（`dc_target_allocation` / `ideco_target_allocation`）。
+   - 若目標配分存在，且其 `allocations` 完整涵蓋該帳戶最新快照的全部 `fundCode`、`remainderFundCode` 有效且加總等於 100%，則**整組採用目標配分**。
+   - 若尚未設定目標配分，或最新匯入快照的標的清單已變動導致目標配分無法完整對應，則**安全回退（fallback）使用事實表**的 `DefinedContributionFundSnapshot.allocationPercentage` 與 `DefinedContributionAccountSnapshot.remainderFundCode`（若事實端數未設定則回退第一支標的）。
+2. **金額分配計算**：
+   - 直接呼叫已抽出的 [DefinedContributionAllocationCalculator.calculateMonthlyContributions](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionAllocationCalculator.java)（非端數基金以 `BigDecimal` `RoundingMode.FLOOR` 計算，端數基金吸收剩餘差額），不再各自手寫 `Math.round` 與「最後一支吸收零頭」邏輯，確保模擬沙盒與真實配分算法完全一致。
+
+### 3. `SimulationFund` 與 `SimulationContext` 需補充欄位
+查證[SimulationFund.java:38-59](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)確認，這個類別目前尚未攜帶 `fundCode` 與 `allocationPercentage`，實作本計畫時需：
+- 在 `SimulationFund` 補上 `fundCode`（供對應 `DcTargetAllocationSetting.allocations` 與 `remainderFundCode`）與事實 `allocationPercentage`。
+- 在 `SimulationContext` 補上企業型 DC 與 iDeCo 各自的目標配分設定（或已解析好的 `effectiveAllocation`＋`effectiveRemainderFundCode`），由 [DefinedContributionContextBuilder.java:81-89,152-160](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java) 在建構階段一併帶入。
 
 ### 4. 預設行為：沒有傳入覆寫值時，完全不受影響
 這個設計跟現有`FundOverrideApplier.applyOverrides`的「只套用非null欄位」原則一致（[FundOverrideApplier.java:51](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)的既有註解）——`testDcMonthlyContribution`/`testIdecoMonthlyContribution`留null時，`DefinedContributionContextBuilder`已經建構好的`SimulationFund.annualContribution`（來自資料庫裡真實的`monthlyContributionAmount × 12`）維持原樣，`allocationRatio`這個新欄位完全不會被用到——這呼應了使用者提出的「預設沒有覆蓋就是採用DB的百分比，不手動去算」這個原則。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                                                               10038  11.38G   4.00    09:53:03  
 