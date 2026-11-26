# [FIXED] 183：NISA定期定額設定頁「每月提繳金額」欄位——PATCH端點改的`monthlyEquivalentAmount`模擬引擎完全不讀，而且這個端點每次送出都會把同一筆記錄的`expectedReturnRate`悄悄蓋成null；`monthlyEquivalentAmount`這個欄位本身只是`settingAmount`跟`course`的純函數換算值，前端已經同時顯示這兩欄原始資料，不需要額外欄位，建議整欄拔除改成前端現場計算

## 這份issue怎麼來的
這是[issue-177](issue-177.md)的延伸——177本身記錄了「使用者在畫面上編輯Monthly Contribution，模擬結果完全不受影響」這個落差，並在補充報告裡確認了兩個原本提議的方向（反推回`settingAmount`、讓模擬引擎改讀`monthlyEquivalentAmount`）都不可行：前者即使做了也會在下次重新匯入對帳單時被覆蓋回舊值（`NisaContributionSetting.merge()`無條件覆寫`settingAmount`）；後者是重新引入[Fixed-149](../fixed/Fixed-149.md)當年刻意移除的bug（會讓毎週/毎日提繳的年化金額系統性少算7~8%）。

使用者確認採用「這個欄位目前沒有任何合理用途，應該整個拔除」的方向，但要求先確認前端是否真的不需要它——如果前端只是純顯示用途、且有其他欄位可以取代，不需要走WONTFIX；查證後確認確實如此，這份issue記錄完整的查證結果跟建議修法。

## 相關檔案
- [NisaContributionSetting.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java)（`monthlyEquivalentAmount`欄位定義、`updateMonthlyEquivalentAmount()`、`merge()`）
- [NisaContributionSettingServiceImpl.java:53-64](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java)（`updateAllocation`，PATCH端點背後的邏輯）
- [NisaContributionSettingController.java:81-88](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingController.java)（`PATCH /assets/nisa/settings/{id}/allocation`）
- [UpdateNisaAllocationRequest.java](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/UpdateNisaAllocationRequest.java)、[UpdateNisaAllocationCommand.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/UpdateNisaAllocationCommand.java)
- [SbiNisaParserImpl.java:173-188](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java)（`monthlyEquivalentAmount`的計算公式：`settingAmount`依`course`乘上毎日×20/毎週×4/毎月×1）
- 前端三個消費者：
  - [nisa-contribution-settings.tsx](file://hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx)（Active Contribution Plan表格，顯示用途+壞掉的編輯框）
  - [simulation-override-panel.tsx:60-77,494-501](file://hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)（Sandbox模擬覆寫面板，`monthlyEquivalentAmount`被當成覆寫輸入框的預設值）
  - [use-nisa-data.ts:111-126](file://hub-ui/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts)（`updateMonthlyContribution`，PATCH呼叫本身）

## 現狀

### 第一層：`monthlyEquivalentAmount`完全是`settingAmount`+`course`的純函數換算值
查證[SbiNisaParserImpl.java:173-180](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java)確認，這個欄位的計算方式是：
```java
long monthlyEquivalent = settingAmount;
if ("毎日".equals(course) || "every day".equalsIgnoreCase(course)) {
    monthlyEquivalent = settingAmount * 20; // Approx 20 trading days
} else if ("毎週".equals(course) || "every week".equalsIgnoreCase(course)) {
    monthlyEquivalent = settingAmount * 4; // Approx 4 weeks
}
```
這個公式不依賴任何額外的外部資料，只需要`settingAmount`跟`course`兩個值就能算出來——這兩個值本身已經是`NisaContributionSetting`的事實欄位，而且前端在Active Contribution Plan表格上已經同時顯示「Setting Amount」「Course」這兩欄（[nisa-contribution-settings.tsx:63-64](file://hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx)）。

### 第二層：前端查證確認的三個消費者，全部都只是「顯示/預設值」用途，沒有一個是驅動模擬結果的必要欄位
1. **Active Contribution Plan表格顯示**（[nisa-contribution-settings.tsx:108-114](file://hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx)）：用`InlineCurrencyEditor`顯示並允許編輯這個值，畫面文案宣稱「Values configured here directly drive 30-year projections and cash flow simulation」——這句文案本身就是錯的，模擬引擎（`NisaContextBuilder`）完全不讀這個欄位，一律用`settingAmount`+`course`重新算（詳見issue-177）。
2. **Sandbox模擬覆寫面板的預設值**（[simulation-override-panel.tsx:72,500](file://hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)）：`mergedNisa`物件把`setting.monthlyEquivalentAmount`併入每筆NISA基金資料，第500行`const defaultMonthly = fund.monthlyEquivalentAmount ?? 0`把它當成「這支基金覆寫輸入框」的初始顯示值；使用者真正能改變模擬結果的是`ovr.contributionOverride`這個另外存在獨立狀態裡的覆寫值，`monthlyEquivalentAmount`本身只是畫面初次渲染時的起始數字。**注意**：這個物件目前沒有把`settingAmount`、`course`一起併入（只挑了`monthlyEquivalentAmount`、`expectedReturnRate`兩欄），如果要改成現場計算，需要在這段`mergedNisa`組裝邏輯裡額外把這兩個欄位也併進去。
3. **PATCH端點本身**（[use-nisa-data.ts:111-126](file://hub-ui/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts)）：`updateMonthlyContribution`送出`{ monthlyEquivalentAmount }`，背後只改這一個欄位，不影響模擬結果（issue-177已確認）。

### 第三層：這個PATCH端點每次送出，都會把同一筆記錄的`expectedReturnRate`悄悄蓋成null
查證[UpdateNisaAllocationRequest.java](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/UpdateNisaAllocationRequest.java)確認`expectedReturnRate`欄位只有`@DecimalMin`/`@DecimalMax`，沒有`@NotNull`——Bean Validation對null值不會擋。[NisaContributionSettingServiceImpl.java:60](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java)呼叫`setting.updateExpectedReturnRate(command.getExpectedReturnRate())`，這是無條件賦值（[NisaContributionSetting.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java)的`updateExpectedReturnRate`沒有null防呆），而前端`updateMonthlyContribution`送出的body**只有**`monthlyEquivalentAmount`這一個欄位（[use-nisa-data.ts:116](file://hub-ui/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts)），從來不會帶上`expectedReturnRate`——這代表每一次使用者點擊Active Contribution Plan表格的編輯框，送出的PATCH都會把這筆記錄的`expectedReturnRate`強制改成null，不管它原本是什麼值。

查證確認這個欄位**不保證永遠是null**：[SbiNisaJobServiceImpl.java:326-332](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java)的`inheritHistoricalRate(NisaContributionSetting setting)`方法，會在重新匯入對帳單時，從同一支基金過去的歷史記錄繼承舊的`expectedReturnRate`、寫回新記錄——代表資料庫裡確實可能存在一筆`expectedReturnRate`已經是非null值的`NisaContributionSetting`記錄，一旦使用者在Active Contribution Plan表格編輯過這筆記錄的Monthly Contribution，這個繼承下來的報酬率設定就會被悄悄清空。

**容易混淆的地方**：`NisaSnapshotRecord`（持股快照）跟`NisaContributionSetting`（提繳設定）各自有自己獨立的`expectedReturnRate`欄位，是兩個不同實體上同名的欄位。前端`nisa-table.tsx`裡呼叫`updateReturnRate`、打`PATCH /assets/nisa/{id}/return-rate`（[NisaSnapshotRecordController.java:110](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaSnapshotRecordController.java)），改的是`NisaSnapshotRecord`那一份，跟這裡討論的`NisaContributionSetting.expectedReturnRate`完全無關、不會互相救援或抵銷——這份issue討論的null覆蓋風險依然成立。

## 為何是問題
1. **`monthlyEquivalentAmount`欄位、PATCH端點、前端編輯框三者加總起來，只會製造維護負擔跟誤導，沒有任何正面作用**：這個欄位不驅動模擬結果（issue-177已確認），不是對帳單上的原始事實（它本身是衍生計算值，`settingAmount`才是事實），留著只會讓下一個讀這段程式碼的人誤以為編輯它有意義。
2. **`expectedReturnRate`被無聲清空是這個PATCH端點本身的設計缺陷**：這是典型的「用全量覆寫語意的PATCH，只送部分欄位」反模式——即使做了issue-177的其他修正，只要這個端點還同時收兩個欄位、前端還只送一個，這個清空風險就會一直存在，之後任何新UI只要不小心踩到同一個端點，就會重蹈覆轍。

## 建議修正方向
不是多方案並列，這裡是一套對齊事實的修法（使用者已確認方向）：

### 第一步：`monthlyEquivalentAmount`欄位、PATCH端點整個拔除
- 從`NisaContributionSetting.java`移除`monthlyEquivalentAmount`欄位、`updateMonthlyEquivalentAmount()`方法，`merge()`裡對應的那一行一併移除。
- `SbiNisaParserImpl.java`移除換算`monthlyEquivalent`那段邏輯跟建構子參數。
- `NisaContributionSettingController.java`移除`PATCH /assets/nisa/settings/{id}/allocation`這個端點；`NisaContributionSettingServiceImpl.updateAllocation`、`UpdateNisaAllocationCommand`、`UpdateNisaAllocationRequest`一併移除或簡化（如果移除整個端點，這三個類別都不再需要存在）。
- `NisaContributionSettingResult.java`移除`monthlyEquivalentAmount`這個回應欄位。
- 這個專案目前只有測試用的H2資料庫（查證確認[application.properties:36](file://finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)用`ddl-auto=update`），使用者確認資料庫整包可以直接刪掉重建，不需要寫`ALTER TABLE ... DROP COLUMN`或任何migration腳本——拿掉`monthlyEquivalentAmount`欄位後，重建資料庫時Hibernate自然就不會再建這個column。

### 第二步：前端三個消費者改成用`settingAmount`+`course`現場計算
- 把`SbiNisaParserImpl`裡那段換算公式（毎日×20、毎週×4、毎月×1）搬到前端（例如一個共用的`calculateMonthlyEquivalent(settingAmount, course)`函式），`nisa-contribution-settings.tsx`的「Monthly Contribution」欄改成呼叫這個函式即時算出來的唯讀顯示值，拿掉`InlineCurrencyEditor`，同時修正「Values configured here directly drive 30-year projections and cash flow simulation」這句錯誤文案。
- `simulation-override-panel.tsx`的`mergedNisa`物件（第66-75行）目前只挑了`setting.monthlyEquivalentAmount`跟`setting.expectedReturnRate`兩欄，需要額外把`setting.settingAmount`、`setting.course`也併進去，`defaultMonthly`那一行改成呼叫同一個共用換算函式現場算，不是依賴後端欄位。
- `use-nisa-data.ts`的`updateMonthlyContribution`函式、`NisaContributionSetting`介面裡的`monthlyEquivalentAmount`欄位宣告，連同第二步要移除的後端端點一起拔除。

## 拔除`/allocation`端點後，`expectedReturnRate`暫時失去手動設定管道——這是刻意接受的暫時缺口，不另開端點補
查證確認`expectedReturnRate`是這個欄位唯一的寫入管道（`NisaSnapshotRecord`上有自己獨立的`expectedReturnRate`欄位跟對應的`/return-rate`端點，兩者互不相通，不能互相救援）。拔除`/allocation`後，使用者會暫時失去「針對單一NISA基金設定個別預期報酬率（沒設定就退回`NisaUserSettings`全域值，再沒有就退回系統全域值，詳見[NisaContextBuilder.java:143-151](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java)）」這個能力，只能靠`inheritHistoricalRate`被動繼承舊值延續。

查證確認`expectedReturnRate`不可能被parser爬出來——[SbiNisaParserImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java)裡完全沒有任何這個欄位的解析邏輯，這個欄位的本質是使用者對未來的主觀估算，不是對帳單頁面上會顯示的客觀事實，天生就不可能用parser補上。

使用者確認：`NisaContributionSetting`這張表除了`expectedReturnRate`之外，其餘欄位（`recordDate`、`accountType`、`fundCode`、`fundName`、`course`、`settingAmount`）全部是parser解析出來的事實欄位——`expectedReturnRate`是這張表上唯一的使用者輸入欄位。這代表之後如果要做一個NISA設定手動編輯UI（不管是為了補完這張表哪些欄位的編輯能力，或任何其他需求），`expectedReturnRate`作為唯一的使用者欄位，理所當然會被一起納入那個畫面跟對應端點——不需要現在單獨為它搶先做一個`/return-rate`端點，那樣反而是提前做了一個之後可能要重新設計的半成品。

**這份issue的決定**：先接受這個能力暫時消失（跟拔除`monthlyEquivalentAmount`一起做），不補新端點；等之後真的要做NISA設定編輯UI時，把`expectedReturnRate`的編輯能力一併納入那次設計。


---

## 實作完成紀錄 (Backend Implementation Complete)

- **實作時間**：2026-11-04
- **實作內容（後端清理）**：
  1. [`NisaContributionSetting.java`](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java)：拔除 `monthlyEquivalentAmount` 欄位、getter、`updateMonthlyEquivalentAmount()` 方法，並在建構子與 `merge()` 方法中移除該欄位。
  2. [`NisaContributionSettingResult.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaContributionSettingResult.java)：移除 `monthlyEquivalentAmount` 欄位與 `fromEntity` 賦值。
  3. [`NisaContributionSettingResponse.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingResponse.java)：移除 `monthlyEquivalentAmount` 回應欄位。
  4. [`SbiNisaParserImpl.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java)：移除冗餘的月等值計算邏輯，簡化建構子傳參與日誌輸出。
  5. [`NisaContributionSettingController.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingController.java)：移除無效且有副作用的 `PATCH /{id}/allocation` 端點。
  6. [`NisaContributionSettingService.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingService.java) & [`NisaContributionSettingServiceImpl.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java)：移除 `updateAllocation` 介面與實作。
  7. [`NisaContributionSettingMapper.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingMapper.java)：移除 `toCommand` 映射。
  8. [`UpdateNisaAllocationCommand.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/UpdateNisaAllocationCommand.java) & [`UpdateNisaAllocationRequest.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/UpdateNisaAllocationRequest.java)：標註為 `@Deprecated(forRemoval = true)`。
  9. [`SbiNisaParserImplTest.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImplTest.java)：更新測試斷言，移除對 `monthlyEquivalentAmount` 的檢查。
  10. [`NisaContextBuilderTest.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java)：校準 5 處單元測試中 `NisaContributionSetting` 的建構呼叫。
  11. [`NisaContributionSettingTest.java`](file://finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java)：更新單元測試，移除 `monthlyEquivalentAmount` 相關斷言、setter 呼叫與建構子引數。
  12. [`SbiNisaJobServiceImplTest.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImplTest.java)：更新 `NisaContributionSetting` 測試建構子引數。
  13. [`NisaContributionSettingControllerTest.java`](file://finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingControllerTest.java)：移除 Controller 測試中對 `monthlyEquivalentAmount` 的賦值與 jsonPath 斷言。
  14. [`AssetSimulationEngineImplTest.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java)：更新 3 處 `NisaContributionSetting` 測試建構子引數為 6 個參數。
  15. [`SbiNisaParserRunner.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserRunner.java)：移除對 `s.getMonthlyEquivalentAmount()` 的呼叫，改為使用現場換算函式輸出與計算。
- **狀態**：後端代碼與測試整頓完畢，無效欄位與副作用端點已徹底清除。

## 前端實作完成紀錄 (Frontend Implementation Complete)

- **實作時間**：2026-11-04
- **實作內容（前端現場計算與介面修復）**：
  1. [`use-nisa-data.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts)：
     - 從 `NisaContributionSetting` 介面移除 `monthlyEquivalentAmount` 欄位（對齊後端 DTO）。
     - 導出共用換算函式 `calculateMonthlyEquivalent(settingAmount?: number | null, course?: string | null): number`（每日×20、每週×4、每月/其他×1）。
     - 在 `mergedHistory` 中改為現場動態計算快照紀錄的 `monthlyEquivalentAmount`。
     - 移除 `updateMonthlyContribution` 函式（不再向後端發送無效且具有覆蓋副作用的 PATCH 請求）。
  2. [`nisa-contribution-settings.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx)：
     - 移除 `InlineCurrencyEditor` 及其引發的 PATCH 更新邏輯，改為只讀格式化顯示 `calculateMonthlyEquivalent(setting.settingAmount, setting.course)`。
     - 每月投資合計總額（`totalMonthly`）改用 `calculateMonthlyEquivalent` 計算。
     - 修正說明文字，由誤導性的「Values configured here directly drive 30-year projections...」更正為客觀事實描述：「Current active regular investment allocations parsed from broker statements. Values are displayed with estimated monthly equivalents.」。
  3. [`simulation-override-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)：
     - 引入 `calculateMonthlyEquivalent`。
     - 在組裝 `mergedNisa` 時，改為使用 `setting ? calculateMonthlyEquivalent(setting.settingAmount, setting.course) : 0` 初始化預設每月提繳額，不再依賴後端 DTO 欄位。
- **狀態**：前端三個消費者已全數轉為現場計算，破壞性 PATCH 調用與冗餘欄位已全數清除。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                       10004  13.33G   6.92    07:55:11  
diff --git a/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx b/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx
index d613bf1..ea4b052 100644
--- a/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx
+++ b/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx
@@ -52,6 +52,7 @@ export function SystemConfigPanel({ configsHook }: Props) {
     defaultValues: {
       fileProcessingTimeoutSeconds: 120,
       simulationEarlyRepaymentMonth: 12,
+      retirementMonthMode: 'DECEMBER',
     },
   });
 
@@ -265,6 +266,26 @@ export function SystemConfigPanel({ configsHook }: Props) {
               {errors.simulationEarlyRepaymentMonth && <p className="text-xs text-destructive mt-1">{errors.simulationEarlyRepaymentMonth.message}</p>}
             </div>
 
+            <div className="space-y-2 pt-2 md:col-span-1">
+              <Label htmlFor="retirementMonthMode">Retirement Month Mode</Label>
+              <Controller
+                name="retirementMonthMode"
+                control={control}
+                render={({ field }) => (
+                  <Select onValueChange={field.onChange} value={field.value || 'DECEMBER'}>
+                    <SelectTrigger id="retirementMonthMode">
+                      <SelectValue placeholder="Select retirement month mode" />
+                    </SelectTrigger>
+                    <SelectContent>
+                      <SelectItem value="DECEMBER">December (Year-End)</SelectItem>
+                      <SelectItem value="BIRTH_MONTH">Birth Month</SelectItem>
+                    </SelectContent>
+                  </Select>
+                )}
+              />
+              {errors.retirementMonthMode && <p className="text-xs text-destructive mt-1">{errors.retirementMonthMode.message}</p>}
+            </div>
+
           </div>
         )}
       </CardContent>
diff --git a/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts b/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
index bc60d9a..4421574 100644
--- a/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
+++ b/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
@@ -29,6 +29,7 @@ export const systemConfigSchema = z.object({
   gaMutationRate: z.coerce.number().min(0, "Must be >= 0").max(1, "Must be <= 1").default(0.2),
   gaEliteRatio: z.coerce.number().min(1, "Must be >= 1").max(50, "Must be <= 50").default(10),
   simulationEarlyRepaymentMonth: z.coerce.number().min(1, "Must be between 1 and 12").max(12, "Must be between 1 and 12").default(12),
+  retirementMonthMode: z.enum(['DECEMBER', 'BIRTH_MONTH']).default('DECEMBER'),
 });
 
 export type SystemConfigFormValues = z.infer<typeof systemConfigSchema>;
@@ -324,6 +325,7 @@ export function useFinanceConfigs() {
       gaMutationRate: 0.2,
       gaEliteRatio: 10,
       simulationEarlyRepaymentMonth: 12,
+      retirementMonthMode: "DECEMBER",
     });
   };
 
diff --git a/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx b/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx
index 9c91831..e0c5ef6 100644
--- a/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx
+++ b/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx
@@ -13,13 +13,12 @@ import {
 } from "@/components/ui/table";
 import { Badge } from "@/components/ui/badge";
 import { formatCurrency } from "@/lib/utils";
-import { InlineCurrencyEditor } from "@/app/finance/portfolios/components/inline-currency-editor";
 import { useFundColors } from "@/app/finance/portfolios/hooks/use-fund-colors";
-import { useNisaData } from "../hooks/use-nisa-data";
-import { CalendarClock, Sparkles } from "lucide-react";
+import { useNisaData, calculateMonthlyEquivalent } from "../hooks/use-nisa-data";
+import { CalendarClock } from "lucide-react";
 
 export function NisaContributionSettings() {
-  const { activeContributionSettings, isLoading, updateMonthlyContribution } = useNisaData();
+  const { activeContributionSettings, isLoading } = useNisaData();
   const { getFundColor, setFundColor } = useFundColors();
 
   if (isLoading && activeContributionSettings.length === 0) {
@@ -31,7 +30,7 @@ export function NisaContributionSettings() {
   }
 
   const totalMonthly = activeContributionSettings.reduce(
-    (sum, setting) => sum + (setting.monthlyEquivalentAmount || 0),
+    (sum, setting) => sum + calculateMonthlyEquivalent(setting.settingAmount, setting.course),
     0
   );
 
@@ -45,11 +44,11 @@ export function NisaContributionSettings() {
               Active Contribution Plan
             </h2>
             <Badge variant="outline" className="text-xs font-normal bg-primary/5 text-primary border-primary/20">
-              Future Simulation Input
+              Active Fact
             </Badge>
           </div>
           <p className="text-xs text-muted-foreground mt-1">
-            Manage currently active regular investment allocations. Values configured here directly drive 30-year projections and cash flow simulation.
+            Current active regular investment allocations parsed from broker statements. Values are displayed with estimated monthly equivalents.
           </p>
         </div>
       </div>
@@ -63,10 +62,7 @@ export function NisaContributionSettings() {
               <TableHead className="text-center w-[120px]">Course</TableHead>
               <TableHead className="text-right w-[150px]">Setting Amount</TableHead>
               <TableHead className="text-right w-[180px]">
-                <div className="flex items-center justify-end gap-1">
-                  <span>Monthly Contribution</span>
-                  <Sparkles className="w-3 h-3 text-amber-500" />
-                </div>
+                Monthly Equivalent
               </TableHead>
             </TableRow>
           </TableHeader>
@@ -105,12 +101,8 @@ export function NisaContributionSettings() {
                   <TableCell className="text-right tabular-nums text-muted-foreground text-xs">
                     {setting.settingAmount ? formatCurrency(setting.settingAmount) : "-"}
                   </TableCell>
-                  <TableCell className="text-right font-medium">
-                    <InlineCurrencyEditor
-                      id={setting.id}
-                      initialValue={setting.monthlyEquivalentAmount}
-                      onUpdate={updateMonthlyContribution}
-                    />
+                  <TableCell className="text-right font-medium tabular-nums text-sm">
+                    {formatCurrency(calculateMonthlyEquivalent(setting.settingAmount, setting.course))}
                   </TableCell>
                 </TableRow>
               );
diff --git a/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts b/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts
index b015e66..63aa5f7 100644
--- a/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts
+++ b/hub-ui/src/app/finance/portfolios/nisa/hooks/use-nisa-data.ts
@@ -27,7 +27,6 @@ export interface NisaContributionSetting {
   fundName: string;
   course: string;
   settingAmount: number;
-  monthlyEquivalentAmount: number;
   expectedReturnRate?: number;
 }
 
@@ -38,6 +37,25 @@ export interface NisaProjectionYearlyDto {
   projectedTotalGain: number;
 }
 
+/**
+ * Calculates the approximate monthly contribution equivalent from setting amount and course.
+ * - 毎日: settingAmount * 20
+ * - 毎週: settingAmount * 4
+ * - 毎月 / others: settingAmount * 1
+ */
+export function calculateMonthlyEquivalent(settingAmount?: number | null, course?: string | null): number {
+  if (!settingAmount) return 0;
+  if (!course) return settingAmount;
+  const c = course.trim().toLowerCase();
+  if (c === "毎日" || c === "every day") {
+    return settingAmount * 20;
+  }
+  if (c === "毎週" || c === "every week") {
+    return settingAmount * 4;
+  }
+  return settingAmount;
+}
+
 const fetcher = (url: string) => fetch(url).then((res) => {
   if (!res.ok) throw new Error("Failed to fetch NISA data");
   return res.json();
@@ -76,7 +94,7 @@ export function useNisaData() {
     const setting = settingsByKey[key];
     return {
       ...record,
-      monthlyEquivalentAmount: setting?.monthlyEquivalentAmount,
+      monthlyEquivalentAmount: calculateMonthlyEquivalent(setting?.settingAmount, setting?.course),
       contributionSettingId: setting?.id,
     };
   });
@@ -108,23 +126,6 @@ export function useNisaData() {
     return a.fundName.localeCompare(b.fundName);
   });
 
-  const updateMonthlyContribution = useCallback(async (id: number | string, monthlyEquivalentAmount: number) => {
-    try {
-      const response = await fetch(`/api/finance/assets/nisa/settings/${id}/allocation`, {
-        method: "PATCH",
-        headers: { "Content-Type": "application/json" },
-        body: JSON.stringify({ monthlyEquivalentAmount }),
-      });
-      if (!response.ok) throw new Error("Failed to update monthly contribution");
-      toast.success("Monthly contribution updated successfully.");
-      mutateAll();
-    } catch (error) {
-      console.error("Failed to update monthly contribution", error);
-      toast.error("Failed to update monthly contribution.");
-      throw error;
-    }
-  }, [mutateAll]);
-
   const updateReturnRate = useCallback(async (id: number, expectedReturnRate: number) => {
     try {
       const response = await fetch(`/api/finance/assets/nisa/${id}/return-rate`, {
@@ -152,7 +153,6 @@ export function useNisaData() {
     isLoading: historyLoading || projLoading || settingsLoading,
     isError: historyError || projError || settingsError,
     mutate: mutateAll,
-    updateMonthlyContribution,
     updateReturnRate,
   };
 }
diff --git a/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx b/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx
index 9466d7a..b6db763 100644
--- a/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx
+++ b/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx
@@ -13,6 +13,7 @@ import { Button } from "@/components/ui/button";
 import { ChevronDown, ChevronUp, Settings2, Loader2, Plus, Trash2 } from "lucide-react";
 import type { SimulationOverrides, FundOverride } from '../hooks/use-simulation-data';
 import { toast } from 'sonner';
+import { calculateMonthlyEquivalent } from "@/app/finance/portfolios/nisa/hooks/use-nisa-data";
 
 interface SimulationOverridePanelProps {
   overrides: SimulationOverrides;
@@ -69,7 +70,7 @@ export function SimulationOverridePanel({
           ...snap,
           displayType: snap.accountType, // Keep the original "NISA (成長)預り" for UI display
           accountType: "NISA", // Force to 'NISA' to match backend override key
-          monthlyEquivalentAmount: setting ? setting.monthlyEquivalentAmount : 0,
+          monthlyEquivalentAmount: setting ? calculateMonthlyEquivalent(setting.settingAmount, setting.course) : 0,
           expectedReturnRate: snap.expectedReturnRate ?? (setting ? setting.expectedReturnRate : null)
         };
       });
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                            10005  13.77G   6.06    07:57:39  

  edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                          10008  12.91G   5.25    08:16:54  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingService.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingService.java
index 58803236..73c9f265 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingService.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingService.java
@@ -25,12 +25,9 @@ package ch.lin.finance.hub.backend.api.app.service.accounting.nisa;
 
 import java.util.List;
 
-import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.UpdateNisaAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaContributionSettingResult;
 
 public interface NisaContributionSettingService {
-    
+
     List<NisaContributionSettingResult> getAllSettings();
-    
-    NisaContributionSettingResult updateAllocation(String id, UpdateNisaAllocationCommand command);
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java
index c25b450a..d10ce91c 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java
@@ -24,16 +24,13 @@
 package ch.lin.finance.hub.backend.api.app.service.accounting.nisa;
 
 import java.util.List;
-import java.util.Objects;
 import java.util.stream.Collectors;
 
 import org.springframework.stereotype.Service;
 import org.springframework.transaction.annotation.Transactional;
 
 import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaContributionSettingRepository;
-import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.UpdateNisaAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaContributionSettingResult;
-import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
 import lombok.RequiredArgsConstructor;
 
 @Service
@@ -49,17 +46,4 @@ public class NisaContributionSettingServiceImpl implements NisaContributionSetti
                 .map(NisaContributionSettingResult::fromEntity)
                 .collect(Collectors.toList());
     }
-
-    @Override
-    @Transactional
-    public NisaContributionSettingResult updateAllocation(String id, UpdateNisaAllocationCommand command) {
-        NisaContributionSetting setting = settingRepository.findById(Objects.requireNonNull(id))
-                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
-                        org.springframework.http.HttpStatus.NOT_FOUND, "NISA setting not found"));
-
-        setting.updateExpectedReturnRate(command.getExpectedReturnRate());
-        setting.updateMonthlyEquivalentAmount(command.getMonthlyEquivalentAmount());
-
-        return NisaContributionSettingResult.fromEntity(settingRepository.save(setting));
-    }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/UpdateNisaAllocationCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/UpdateNisaAllocationCommand.java
deleted file mode 100644
index 0b967450..00000000
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/UpdateNisaAllocationCommand.java
+++ /dev/null
@@ -1,34 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command;
-
-import lombok.Builder;
-import lombok.Data;
-
-@Data
-@Builder
-public class UpdateNisaAllocationCommand {
-    private Double expectedReturnRate;
-    private Long monthlyEquivalentAmount;
-}
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaContributionSettingResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaContributionSettingResult.java
index 082a4988..db3e623b 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaContributionSettingResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaContributionSettingResult.java
@@ -40,7 +40,6 @@ public class NisaContributionSettingResult extends BaseResult {
     private String fundName;
     private String course;
     private Long settingAmount;
-    private Long monthlyEquivalentAmount;
     private Double expectedReturnRate;
     private String accountType;
 
@@ -55,7 +54,6 @@ public class NisaContributionSettingResult extends BaseResult {
         result.setFundName(entity.getFundName());
         result.setCourse(entity.getCourse());
         result.setSettingAmount(entity.getSettingAmount());
-        result.setMonthlyEquivalentAmount(entity.getMonthlyEquivalentAmount());
         result.setExpectedReturnRate(entity.getExpectedReturnRate());
         result.setAccountType(entity.getAccountType());
         result.setOwnerUserId(entity.getOwnerUserId());
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java
index f1814467..17081c2b 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java
@@ -170,26 +170,17 @@ public class SbiNisaParserImpl implements SbiNisaParser {
                 }
                 long settingAmount = extractLong(amountEl != null ? amountEl.text() : "0");
 
-                // Monthly Equivalent Calculation
-                long monthlyEquivalent = settingAmount;
-                if ("毎日".equals(course) || "every day".equalsIgnoreCase(course)) {
-                    monthlyEquivalent = settingAmount * 20; // Approx 20 trading days
-                } else if ("毎週".equals(course) || "every week".equalsIgnoreCase(course)) {
-                    monthlyEquivalent = settingAmount * 4; // Approx 4 weeks
-                }
-
                 NisaContributionSetting setting = new NisaContributionSetting(
                         recordDate,
                         accountType,
                         fundCode,
                         fundName,
                         course,
-                        settingAmount,
-                        monthlyEquivalent);
+                        settingAmount);
                 settings.add(setting);
 
-                logger.debug("Parsed NISA setting: {} [{}] - {} - {} (Monthly Eq: {})",
-                        fundName, fundCode, course, settingAmount, monthlyEquivalent);
+                logger.debug("Parsed NISA setting: {} [{}] - {} - {}",
+                        fundName, fundCode, course, settingAmount);
 
             } catch (HtmlParsingException e) {
                 throw e;
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImplTest.java
index d4b0f004..46b0e655 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImplTest.java
@@ -108,13 +108,13 @@ class SbiNisaJobServiceImplTest {
         NisaContributionSetting existingSetting = new NisaContributionSetting(
                 LocalDate.now(), "NISA (つみたて)", "0331418A",
                 "eMAXIS Slim 全世界株式（オール・カントリー）",
-                "毎月", 50_000L, 50_000L);
+                "毎月", 50_000L);
         existingSetting.updateExpectedReturnRate(0.06);
 
         NisaContributionSetting incomingSetting = new NisaContributionSetting(
                 LocalDate.now(), "NISA (つみたて)", "0331418A",
                 "eMAXIS Slim 全世界株式（オール・カントリー）　",
-                "毎月", 100_000L, 100_000L);
+                "毎月", 100_000L);
 
         when(sbiNisaParser.parse(any(InputStream.class), any(LocalDate.class)))
                 .thenReturn(List.of(incomingRecord));
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImplTest.java
index 245985c5..19abc088 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImplTest.java
@@ -98,7 +98,7 @@ class SbiNisaParserImplTest {
 
             assertEquals(2, settings.size());
 
-            // Setting 1: Daily course (5,000 * 20 = 100,000)
+            // Setting 1: Daily course
             NisaContributionSetting s1 = settings.get(0);
             assertEquals(recordDate, s1.getRecordDate());
             assertEquals("NISA (成長)", s1.getAccountType());
@@ -106,9 +106,8 @@ class SbiNisaParserImplTest {
             assertEquals("ｅＭＡＸＩＳ　Ｓｌｉｍ　米国株式（Ｓ＆Ｐ５００）", s1.getFundName());
             assertEquals("毎日", s1.getCourse());
             assertEquals(5000L, s1.getSettingAmount());
-            assertEquals(100000L, s1.getMonthlyEquivalentAmount());
 
-            // Setting 2: Monthly course (50,000 * 1 = 50,000)
+            // Setting 2: Monthly course
             NisaContributionSetting s2 = settings.get(1);
             assertEquals(recordDate, s2.getRecordDate());
             assertEquals("NISA (成長)", s2.getAccountType());
@@ -116,13 +115,12 @@ class SbiNisaParserImplTest {
             assertEquals("ｅＭＡＸＩＳ　Ｓｌｉｍ　全世界株式（オール・カントリー）", s2.getFundName());
             assertEquals("毎月", s2.getCourse());
             assertEquals(50000L, s2.getSettingAmount());
-            assertEquals(50000L, s2.getMonthlyEquivalentAmount());
         }
     }
 
     @Test
-    @DisplayName("Should correctly calculate weekly course monthly equivalent amount (4x multiplier)")
-    void parseSettings_WeeklyCourse_Calculates4xMultiplier() throws Exception {
+    @DisplayName("Should correctly parse weekly course setting")
+    void parseSettings_WeeklyCourse_ParsesCorrectly() throws Exception {
         String html = "<li class=\"table-row\">"
                 + "<div data-testid=\"fundName\"><a href=\"https://member.c.sbisec.co.jp/fund/detail/2931113C\"><p>Fund Weekly</p></a></div>"
                 + "<div data-testid=\"accountType_PC\">NISA (つみたて)</div>"
@@ -137,7 +135,6 @@ class SbiNisaParserImplTest {
             assertEquals("2931113C", setting.getFundCode());
             assertEquals("毎週", setting.getCourse());
             assertEquals(10000L, setting.getSettingAmount());
-            assertEquals(40000L, setting.getMonthlyEquivalentAmount()); // 10,000 * 4
         }
     }
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
index f080e9fd..ec3e5300 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
@@ -592,7 +592,7 @@ class AssetSimulationEngineImplTest {
         // Mock NisaContributionSettingRepository to provide 200k monthly contribution
         // (2.4M annually)
         NisaContributionSetting setting = new NisaContributionSetting(
-                LocalDate.now(), "NISA (成長)預り", "03311187", "Fund1", "Monthly", 200_000L, 200_000L);
+                LocalDate.now(), "NISA (成長)預り", "03311187", "Fund1", "Monthly", 200_000L);
         setting.updateExpectedReturnRate(0.0); // 0% return for easier math
 
         when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(Arrays.asList(setting));
@@ -635,8 +635,7 @@ class AssetSimulationEngineImplTest {
         // NISA setting: 100k monthly (1.2M annually), 0% return for simple math
         NisaContributionSetting setting = new NisaContributionSetting(LocalDate.now(), "NISA (つみたて)預り", "03311187",
                 "Fund1",
-                "Monthly", 100_000L,
-                100_000L);
+                "Monthly", 100_000L);
         setting.updateExpectedReturnRate(0.0);
         when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(Arrays.asList(setting));
 
@@ -696,8 +695,7 @@ class AssetSimulationEngineImplTest {
         // NISA setting: 200k monthly (2.4M annually), 0% return for simple math
         NisaContributionSetting setting = new NisaContributionSetting(LocalDate.now(), "NISA (成長)預り", "03311187",
                 "Fund1",
-                "Monthly", 200_000L,
-                200_000L);
+                "Monthly", 200_000L);
         setting.updateExpectedReturnRate(0.0);
         when(nisaSettingRepository.findAllByOrderByRecordDateDesc()).thenReturn(Arrays.asList(setting));
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java
index b7826191..80a7af04 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilderTest.java
@@ -75,7 +75,7 @@ class NisaContextBuilderTest {
 
         NisaContributionSetting setting = new NisaContributionSetting(
                 today, "NISA (つみたて)預り", "0331418A", "eMAXIS Slim All Country",
-                "毎週", 10_000L, 40_000L);
+                "毎週", 10_000L);
 
         when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
         when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
@@ -103,7 +103,7 @@ class NisaContextBuilderTest {
 
         NisaContributionSetting setting = new NisaContributionSetting(
                 today, "NISA (成長)預り", "03311187", "TOPIX Fund",
-                "毎日", 1_000L, 20_000L);
+                "毎日", 1_000L);
 
         when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
         when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
@@ -130,7 +130,7 @@ class NisaContextBuilderTest {
 
         NisaContributionSetting setting = new NisaContributionSetting(
                 today, "NISA (成長)預り", "03311187", "S&P 500 Fund",
-                "毎月", 50_000L, 50_000L);
+                "毎月", 50_000L);
 
         when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
         when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
@@ -157,7 +157,7 @@ class NisaContextBuilderTest {
 
         NisaContributionSetting setting = new NisaContributionSetting(
                 today, "NISA (成長)預り", "03311187", "Unknown Fund",
-                "不明", 0L, 50_000L);
+                "不明", 0L);
 
         when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
         when(nisaRepository.findByRecordDate(today)).thenReturn(List.of(record));
@@ -182,7 +182,7 @@ class NisaContextBuilderTest {
 
         NisaContributionSetting setting = new NisaContributionSetting(
                 today, "NISA (つみたて)預り", "0331418A", "eMAXIS Slim 全世界株式（オール・カントリー）",
-                "毎月", 50_000L, 50_000L);
+                "毎月", 50_000L);
         setting.updateExpectedReturnRate(0.065);
 
         when(nisaRepository.findFirstByOrderByRecordDateDesc()).thenReturn(Optional.of(record));
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java
index ef35dddb..c94242ef 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java
@@ -105,14 +105,6 @@ public class NisaContributionSetting extends MultiUserBaseEntity {
     @Column(name = "setting_amount")
     private Long settingAmount = 0L;
 
-    /**
-     * The calculated monthly equivalent amount (e.g. daily 5000 * 20 = 100000).
-     * This is useful for the simulation engine.
-     */
-    @ColumnDefault("0")
-    @Column(name = "monthly_equivalent_amount")
-    private Long monthlyEquivalentAmount = 0L;
-
     /**
      * The expected annual return rate for this specific fund holding. Null
      * means it falls back to the global/user default.
@@ -122,15 +114,13 @@ public class NisaContributionSetting extends MultiUserBaseEntity {
 
     public NisaContributionSetting(LocalDate recordDate, String accountType, String fundCode, String fundName,
             String course,
-            Long settingAmount,
-            Long monthlyEquivalentAmount) {
+            Long settingAmount) {
         this.recordDate = recordDate;
         this.accountType = accountType;
         this.fundCode = fundCode;
         this.fundName = fundName;
         this.course = course;
         this.settingAmount = settingAmount != null ? settingAmount : 0L;
-        this.monthlyEquivalentAmount = monthlyEquivalentAmount != null ? monthlyEquivalentAmount : 0L;
         this.expectedReturnRate = null;
     }
 
@@ -138,10 +128,6 @@ public class NisaContributionSetting extends MultiUserBaseEntity {
         this.expectedReturnRate = expectedReturnRate;
     }
 
-    public void updateMonthlyEquivalentAmount(Long amount) {
-        this.monthlyEquivalentAmount = amount != null ? amount : 0L;
-    }
-
     /**
      * Merges incoming contribution setting facts into this setting:
      * - Overwrites all parsed facts unconditionally using direct assignment.
@@ -159,7 +145,6 @@ public class NisaContributionSetting extends MultiUserBaseEntity {
         this.fundName = incoming.getFundName();
         this.course = incoming.getCourse();
         this.settingAmount = incoming.getSettingAmount();
-        this.monthlyEquivalentAmount = incoming.getMonthlyEquivalentAmount();
         // expectedReturnRate is user-defined and strictly preserved
     }
 }
diff --git a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java
index c16eaa0f..4f873ff7 100644
--- a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java
+++ b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSettingTest.java
@@ -40,7 +40,6 @@ class NisaContributionSettingTest {
                 "03311187",
                 "eMAXIS Slim S&P 500",
                 "毎月",
-                50_000L,
                 50_000L);
 
         assertEquals(date, setting.getRecordDate());
@@ -49,14 +48,10 @@ class NisaContributionSettingTest {
         assertEquals("eMAXIS Slim S&P 500", setting.getFundName());
         assertEquals("毎月", setting.getCourse());
         assertEquals(50_000L, setting.getSettingAmount());
-        assertEquals(50_000L, setting.getMonthlyEquivalentAmount());
         assertNull(setting.getExpectedReturnRate());
 
         setting.updateExpectedReturnRate(0.06);
         assertEquals(0.06, setting.getExpectedReturnRate());
-
-        setting.updateMonthlyEquivalentAmount(60_000L);
-        assertEquals(60_000L, setting.getMonthlyEquivalentAmount());
     }
 
     @Test
@@ -68,24 +63,21 @@ class NisaContributionSettingTest {
                 null,
                 "eMAXIS Slim S&P 500",
                 "毎月",
-                null,
                 null);
 
         assertNull(setting.getFundCode());
         assertEquals(0L, setting.getSettingAmount());
-        assertEquals(0L, setting.getMonthlyEquivalentAmount());
     }
 
     @Test
     void testEqualsAndHashCode() {
         LocalDate date = LocalDate.of(2025, 1, 1);
 
-        NisaContributionSetting s1 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎月", 1000L, 1000L);
-        NisaContributionSetting s2 = new NisaContributionSetting(date, "NISA", "03311187", "FundA Renamed", "毎月", 2000L,
+        NisaContributionSetting s1 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎月", 1000L);
+        NisaContributionSetting s2 = new NisaContributionSetting(date, "NISA", "03311187", "FundA Renamed", "毎月",
                 2000L);
-        NisaContributionSetting s3 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎日", 1000L,
-                20000L);
-        NisaContributionSetting s4 = new NisaContributionSetting(date, "NISA", "0331418A", "FundA", "毎月", 1000L, 1000L);
+        NisaContributionSetting s3 = new NisaContributionSetting(date, "NISA", "03311187", "FundA", "毎日", 1000L);
+        NisaContributionSetting s4 = new NisaContributionSetting(date, "NISA", "0331418A", "FundA", "毎月", 1000L);
 
         assertEquals(s1, s2);
         assertNotEquals(s1, s3);
@@ -99,12 +91,12 @@ class NisaContributionSettingTest {
     void testMerge_OverwritesFactsAndPreservesExpectedReturnRate() {
         LocalDate date1 = LocalDate.of(2025, 1, 1);
         NisaContributionSetting existing = new NisaContributionSetting(
-                date1, "NISA (成長)預り", "03311187", "eMAXIS Slim S&P 500", "毎月", 50_000L, 50_000L);
+                date1, "NISA (成長)預り", "03311187", "eMAXIS Slim S&P 500", "毎月", 50_000L);
         existing.updateExpectedReturnRate(0.06);
 
         LocalDate date2 = LocalDate.of(2025, 1, 2);
         NisaContributionSetting incoming = new NisaContributionSetting(
-                date2, "NISA (成長)預り", "03311188", "eMAXIS Slim All Country", "毎日", 3_000L, 60_000L);
+                date2, "NISA (成長)預り", "03311188", "eMAXIS Slim All Country", "毎日", 3_000L);
 
         existing.merge(incoming);
 
@@ -115,7 +107,6 @@ class NisaContributionSettingTest {
         assertEquals("eMAXIS Slim All Country", existing.getFundName());
         assertEquals("毎日", existing.getCourse());
         assertEquals(3_000L, existing.getSettingAmount());
-        assertEquals(60_000L, existing.getMonthlyEquivalentAmount());
 
         // User-defined expectedReturnRate strictly preserved
         assertEquals(0.06, existing.getExpectedReturnRate());
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingController.java
index d74f3508..93ca3630 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingController.java
@@ -26,21 +26,13 @@ package ch.lin.finance.hub.backend.api.controller.accounting.nisa;
 import java.util.List;
 import java.util.stream.Collectors;
 
-import org.springframework.lang.NonNull;
 import org.springframework.web.bind.annotation.GetMapping;
-import org.springframework.web.bind.annotation.PatchMapping;
-import org.springframework.web.bind.annotation.PathVariable;
-import org.springframework.web.bind.annotation.RequestBody;
 import org.springframework.web.bind.annotation.RequestMapping;
 import org.springframework.web.bind.annotation.RestController;
 
 import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.NisaContributionSettingService;
-import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.UpdateNisaAllocationCommand;
-import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaContributionSettingResult;
 import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaContributionSettingMapper;
 import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.NisaContributionSettingResponse;
-import ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto.UpdateNisaAllocationRequest;
-import jakarta.validation.Valid;
 
 /**
  * REST controller for managing NISA contribution settings (fund allocations).
@@ -69,21 +61,4 @@ public class NisaContributionSettingController {
                 .map(settingMapper::toResponse)
                 .collect(Collectors.toList());
     }
-
-    /**
-     * Updates the allocation (expected return rate and monthly contribution) for a
-     * specific NISA setting.
-     * 
-     * @param id      The ID of the NISA contribution setting.
-     * @param request The request containing the new allocation values.
-     * @return The updated setting record.
-     */
-    @PatchMapping("/{id}/allocation")
-    public NisaContributionSettingResponse updateAllocation(@PathVariable @NonNull String id,
-            @RequestBody @Valid UpdateNisaAllocationRequest request) {
-
-        UpdateNisaAllocationCommand command = settingMapper.toCommand(request);
-        NisaContributionSettingResult updated = settingService.updateAllocation(id, command);
-        return settingMapper.toResponse(updated);
-    }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingMapper.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingMapper.java
index 017abc9a..5c730f4d 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingMapper.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingMapper.java
@@ -25,13 +25,10 @@ package ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto;
 
 import org.mapstruct.Mapper;
 
-import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.UpdateNisaAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaContributionSettingResult;
 
 @Mapper(componentModel = "spring")
 public interface NisaContributionSettingMapper {
 
-    UpdateNisaAllocationCommand toCommand(UpdateNisaAllocationRequest request);
-
     NisaContributionSettingResponse toResponse(NisaContributionSettingResult result);
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingResponse.java
index 14405232..be3ba1b4 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaContributionSettingResponse.java
@@ -37,7 +37,6 @@ public class NisaContributionSettingResponse {
     private String fundName;
     private String course;
     private Long settingAmount;
-    private Long monthlyEquivalentAmount;
     private Double expectedReturnRate;
     private String accountType;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/UpdateNisaAllocationRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/UpdateNisaAllocationRequest.java
deleted file mode 100644
index d5b5ba92..00000000
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/UpdateNisaAllocationRequest.java
+++ /dev/null
@@ -1,44 +0,0 @@
-/*
- * The MIT License (MIT)
- *
- * Copyright (c) 2026 Che-Hung Lin
- *
- * Permission is hereby granted, free of charge, to any person obtaining a copy
- * of this software and associated documentation files (the "Software"), to deal
- * in the Software without restriction, including without limitation the rights
- * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
- * copies of the Software, and to permit persons to whom the Software is
- * furnished to do so, subject to the following conditions:
- *
- * The above copyright notice and this permission notice shall be included in
- * all copies or substantial portions of the Software.
- *
- * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
- * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
- * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
- * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
- * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
- * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
- * THE SOFTWARE.
- */
-package ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto;
-
-import jakarta.validation.constraints.DecimalMax;
-import jakarta.validation.constraints.DecimalMin;
-import jakarta.validation.constraints.Min;
-import lombok.AllArgsConstructor;
-import lombok.Builder;
-import lombok.Data;
-import lombok.NoArgsConstructor;
-
-@Data
-@Builder
-@NoArgsConstructor
-@AllArgsConstructor
-public class UpdateNisaAllocationRequest {
-    @DecimalMin("-1.0") @DecimalMax("1.0") 
-    private Double expectedReturnRate;
-    
-    @Min(0) 
-    private Long monthlyEquivalentAmount;
-}
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingControllerTest.java
index 579719f5..76c0efb8 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingControllerTest.java
@@ -71,14 +71,12 @@ class NisaContributionSettingControllerTest {
         result.setId(1L);
         result.setFundName("eMAXIS Slim 全世界株式");
         result.setAccountType("NISA（つみたて投資枠）");
-        result.setMonthlyEquivalentAmount(50000L);
 
         // Arrange: Mapper correctly maps it to response
         NisaContributionSettingResponse response = NisaContributionSettingResponse.builder()
                 .id("1")
                 .fundName("eMAXIS Slim 全世界株式")
                 .accountType("NISA（つみたて投資枠）")
-                .monthlyEquivalentAmount(50000L)
                 .build();
 
         when(settingService.getAllSettings()).thenReturn(List.of(result));
@@ -88,7 +86,6 @@ class NisaContributionSettingControllerTest {
         mockMvc.perform(get("/assets/nisa/settings"))
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$[0].fundName").value("eMAXIS Slim 全世界株式"))
-                .andExpect(jsonPath("$[0].accountType").value("NISA（つみたて投資枠）"))
-                .andExpect(jsonPath("$[0].monthlyEquivalentAmount").value(50000));
+                .andExpect(jsonPath("$[0].accountType").value("NISA（つみたて投資枠）"));
     }
 }
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                               10008  12.71G   5.23    08:17:11  