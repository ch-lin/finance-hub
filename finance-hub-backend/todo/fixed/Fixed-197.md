# [FIXED] 197：DC/iDeCo基金配分比例`allocationPercentage`的PATCH端點要真正接收新值，而且改比例時要連動重算`monthlyContributionAmount`，不能讓兩個數字各自獨立漂移

## 這份issue怎麼來的
DC/iDeCo基金配分比例`allocationPercentage`有兩件不同的事，分成兩條路分開處理，這是[issue-189](issue-189.md)查證過程中發現的：
1. **基金列表畫面上新增/修改基金，寫進該筆快照的事實欄位**——這份issue要處理的部分，是修好PATCH端點本身：它目前完全沒有真正接收新的`allocationPercentage`值，而且改比例時沒有連動重算金額。
2. **模擬沙盒要用的「目標配分比例」，另外存在使用者設定裡，不受匯入影響**——這是189處理的部分，跟這份issue完全獨立，兩者可以分開落地。

## 相關檔案
- [DefinedContributionController.java:177-187](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java)（`PATCH /assets/dc/funds/{fundId}/allocation`端點，以及內部定義的`UpdateAllocationRequest` record，查證確認只有`expectedReturnRate`、`monthlyContributionAmount`兩個欄位，沒有`allocationPercentage`）
- [DefinedContributionServiceImpl.java:103-116](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)（`updateAllocation`，問題所在：第114行`fund.updateAllocation(amount, fund.getAllocationPercentage())`，第二個參數直接讀自己現有值原樣傳回去）
- [UpdateAllocationCommand.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/command/UpdateAllocationCommand.java)（查證確認同樣只有`expectedReturnRate`、`monthlyContributionAmount`兩個欄位，沒有地方可以帶上新的比例值）
- [DefinedContributionFundSnapshot.java:125-128](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionFundSnapshot.java)（`updateAllocation(long contributionAmount, double allocationPercentage)`，domain方法本身查證確認正常——真的會把傳入的第二個參數賦值給`this.allocationPercentage`，問題不在這裡，是呼叫端沒有傳真正的新值進來）
- [DefinedContributionAccountSnapshot.java:82-83](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)（`monthlyTotalContribution`，這支基金所屬帳戶的每月總提繳額，計算新金額時需要這個值，但目前的PATCH端點完全沒有接觸到這個欄位——它操作的是單一fund，查證確認`DefinedContributionFundSnapshot`雖然有`accountSnapshot`這個關聯（第48行），但Service層目前完全沒有利用它去讀總額）

## 現狀：PATCH端點無法真正改變`allocationPercentage`
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

## 為什麼不能只是「補一個比例參數」就收工——比例跟金額是因果關係，不是兩個各自獨立的欄位
使用者說明了這個欄位對應的真實券商網站操作方式：使用者在網站上設定每月提繳的**總金額**，再決定要投資哪些標的、每個標的分配多少**百分比**，各個標的實際分到的**金額**是網站依總金額跟百分比算出來的；另外還有一個勾選項目，決定百分比換算成金額時除不盡的零頭該算給哪一支基金。

這代表三個數字之間的因果方向是：`總金額 × 配分比例 → 單支基金金額`（加上一個零頭分配規則），不是三個各自獨立、互不相關的事實。

如果只是單純幫`UpdateAllocationRequest`/`UpdateAllocationCommand`加一個`Double allocationPercentage`欄位，比照`monthlyContributionAmount`現有的null防呆寫法套用進去，呼叫端就可以在同一次或分開的請求裡，各自獨立設定某支基金的`monthlyContributionAmount`跟`allocationPercentage`——但系統裡沒有任何程式碼會讓這兩個數字維持一致，使用者改了比例，金額不會跟著變；改了金額，比例也不會跟著變，兩個數字會各自朝不同方向漂移，跟真實網站上「改比例、金額自動跟著變」的行為完全不符。

## 已拍板的設計方向
使用者已經拍板三個決定，不是待討論的選項：
1. **新增端數欄位**，標記帳戶裡哪一支基金負責吸收零頭（對應真實網站上「百分比換算成金額時除不盡的零頭該算給哪一支基金」那個勾選項目）。
2. **非端數基金一律用捨去法（floor），不四捨五入**——這樣每支非端數基金的金額只會等於或小於`總額 × 比例`算出來的理論值，不會偏多；端數基金的金額 = 總額 − 其他所有基金金額加總，吸收所有正數差額，不會出現負數端數需要倒扣的風險。
3. **UI只能輸入比例，金額永遠是算出來的、不給使用者直接改**——避免使用者手動填的金額跟系統依比例算出來的金額互相打架，這跟上面「比例是上游設定、金額是依比例算出來的下游結果」這個因果關係一致。
4. **比例加總必須等於100%，否則拒絕這次PATCH**——後端在重算前（見下方「這個設計把範圍從『單支基金』擴大成『整個帳戶的基金清單一起重算』」一節的第1、2步）先算出這個帳戶裡**套用這次新比例之後**全部基金的比例加總，不等於100%就回應400 Bad Request，不寫入任何欄位、不做後面的捨去法重算；前端在使用者送出前，用同一套「套用這次修改後的全部比例加總」邏輯先檢查一次，不等於100%就直接擋下來並顯示「請設定成加總100%」這樣的錯誤訊息，不送出這次PATCH請求。前後端各自檢查一次，前端是為了即時回饋、不是取代後端驗證——後端的400防呆才是真正擋住錯誤資料寫入資料庫的那一層。

## 這個設計把範圍從「單支基金」擴大成「整個帳戶的基金清單一起重算」，不是只改PATCH的那一支基金
上面「端數吸收」這個概念，本質上是「同一個帳戶裡所有基金的金額加總要等於`monthlyTotalContribution`」——這是帳戶層級的約束，不是單支基金自己能決定的。這代表原本設想的「PATCH端點只改這一支基金的比例跟金額」不夠完整：

**`allocationPercentage`存的是加總100的百分點數字，不是0到1的小數**——查證兩支parser共用的`parseAmountAsDouble`（[SompoDcAllocationParserImpl.java:144-149](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sompo/SompoDcAllocationParserImpl.java)、[SbiIdecoAllocationParserImpl.java:136-141](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/sbi/SbiIdecoAllocationParserImpl.java)）確認，這個方法只是把文字裡的非數字字元去掉再`Double.parseDouble`，網頁上顯示「40」這個百分比字樣，解析出來存進`allocationPercentage`的就是`40.0`，不是`0.4`。這代表依比例算金額的公式要除以100：

當使用者改某一支基金的比例時，正確的重算順序是：
1. 先確認這支基金所屬帳戶（[DefinedContributionFundSnapshot.java:48](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionFundSnapshot.java)的`accountSnapshot`關聯）裡的**全部**基金，不是只看被PATCH的這一支。
2. 對所有「非端數基金」，用更新後的比例、`monthlyTotalContribution`（[DefinedContributionAccountSnapshot.java:82-83](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)），用捨去法算出各自的金額：**`amount = floor(monthlyTotalContribution * allocationPercentage / 100)`**——分母的`100`不能漏掉，漏掉會把金額放大一百倍。
3. 端數基金的金額 = `monthlyTotalContribution − 所有非端數基金金額加總`，不是自己的比例乘出來的值。
4. 把這個帳戶下**所有**基金（不只是被PATCH的那一支）的`monthlyContributionAmount`一次性寫回。

這代表實際改動範圍不是單純改`DefinedContributionServiceImpl.updateAllocation`這一個方法內部的計算公式，服務層需要能查到同一帳戶的所有基金（透過`fund.getAccountSnapshot().getFunds()`或等效查詢），不能只操作被PATCH的那一個fund物件——這一點是這份issue範圍的修正，不是新增的待討論選項，實作時要照這個順序走。

## 這次PATCH即使帶了`monthlyContributionAmount`，也要忽略它——金額只能由比例重算出來，不能直接寫入
查證[DefinedContributionController.java:185-187](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java)確認，`UpdateAllocationRequest`現有欄位裡本來就有`monthlyContributionAmount`，查證[DefinedContributionServiceImpl.java:103-116](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)確認現有程式碼會把這個欄位直接寫入（`long amount = command.getMonthlyContributionAmount() != null ? command.getMonthlyContributionAmount() : 0L;`）。

新增`allocationPercentage`欄位之後，如果不明確處理，呼叫端還是可以在同一次請求裡同時帶`allocationPercentage`跟`monthlyContributionAmount`，兩條路各寫各的——`DefinedContributionServiceImpl.updateAllocation`接到請求後，**不管請求裡有沒有帶`monthlyContributionAmount`，一律忽略它，金額一律由上面「重算順序」算出來的值決定**。這不是這份issue的新增行為，是上面「比例跟金額是因果關係」那節已經確立的方向——這裡特別寫清楚是因為現有端點本來就有`monthlyContributionAmount`這個獨立可寫欄位，如果實作時沒有注意到要停用它，很容易在改比例的同一支程式碼裡，不小心留下舊的「直接寫入請求帶來的金額」這條路，讓兩條路同時存在、各自寫出不一致的結果。

## 端數基金的指定方式，必須存在帳戶層級、用`fundCode`指向，不能存在`DefinedContributionFundSnapshot`這一列上
理由跟[issue-189](issue-189.md)查證過、否決用id當長期關聯鍵的理由類似：查證[DefinedContributionServiceImpl.saveSnapshot](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)確認，瀏覽器擴充功能這條匯入路徑會先`deleteByRecordDateAndType`把整個帳戶snapshot連同底下所有基金整批刪除、再整批重新建立。這代表：
- 如果端數標記存成`id`，這條路徑跑過一次之後，原本指向的id就不存在了，標記直接失聯——這點跟189的結論一致。
- 如果端數標記存成`DefinedContributionFundSnapshot`這一列上的欄位（例如`isRemainderBearer`這種跟著這支基金一起被刪除重建的欄位），一樣會被這條路徑整列刪掉重建，標記同樣會消失——因為這條路徑刪的是整個帳戶連同底下所有基金的整列資料，不只是id。

所以端數標記**不能存在`DefinedContributionFundSnapshot`這一列上，要存在帳戶層級**（例如`DefinedContributionAccountSnapshot`新增一個指向某個`fundCode`的欄位）——這樣查證[DefinedContributionJobServiceImpl.processFile](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionJobServiceImpl.java)確認的檔案掃描路徑（同一天、同一帳戶類型時走`mergeAllocationFacts`，只逐欄位merge，不會整列刪除重建帳戶本身）就能讓這個標記留著。

**但這個標記不會因此就活得過瀏覽器擴充功能這條匯入路徑**：`deleteByRecordDateAndType`刪的是[DefinedContributionAccountSnapshotRepository.java:35](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/dc/DefinedContributionAccountSnapshotRepository.java)確認的帳戶snapshot整列本身（不只是底下的基金），帳戶層級的端數標記跟著這一列一起被刪掉重建，一樣會消失。這跟下面「這份issue要修的bug」之外的另一件事是同一件：[SaveDefinedContributionFundSnapshotCommand.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/command/SaveDefinedContributionFundSnapshotCommand.java)這個命令物件完全沒有`allocationPercentage`、`monthlyContributionAmount`這兩個欄位，瀏覽器擴充功能匯入時新建的基金這兩個值會變成`0`——瀏覽器擴充功能這個資料來源本身還沒做到能像檔案掃描的parser一樣帶入比例、金額，也還沒做到能在重建後保留帳戶層級的額外設定（例如這個端數標記）。這不是這份issue要修的bug，不應該為了遷就這條還沒做的路徑，去改上面已經定案的事實欄位重算邏輯或端數標記的存放位置；這條路徑補齊之後，自然會跟檔案掃描路徑一樣提供真實的比例、金額，也才談得上要怎麼在重建時保留帳戶層級設定，不需要這份issue預先做任何讓步。

## 這份文件沒有解決的部分
- **帳戶層級的端數標記欄位該怎麼命名、要不要在重建後自動驗證「這個fundCode還存在於新匯入的基金清單裡」**——上面已經確定端數標記要存在帳戶層級、用`fundCode`指向，但具體欄位命名、以及如果使用者改變了哪支基金該吸收端數（例如原本的端數基金被刪除、換了一支新基金），切換規則怎麼處理，留給實作時決定。
- **前端對應的配分比例編輯widget**——「UI只能輸入比例、金額唯讀」這個方向已經拍板，但具體畫面佈局（例如要不要即時顯示算出來的金額、端數基金要不要在UI上特別標示）留給之後真正要落地編輯功能時一併設計。
- **跟[issue-189](issue-189.md)的分工維持獨立**：這份issue修好的是事實欄位本身可以被真正編輯；189的「目標配分比例」使用者設定完全不依賴這份issue，兩者可以依任何順序、獨立落地。


---

## 分階段實作規劃（2026-11-25 記錄，供新對話直接接手）

> **背景對齊**：經確認，模擬引擎（[DefinedContributionContextBuilder.java:62-64,129-131](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java#L62-L64)）目前讀取的是 `DefinedContributionFundSnapshot.monthlyContributionAmount`。因此 197 透過比例重算並寫回 `monthlyContributionAmount` 後，現有計算機完全不會壞，在 `Plan-29` 落地前會無縫使用算出的金額進行模擬。

### 為什麼必須改為「帳戶層級批次 PATCH」（解決單支 PATCH 死結）
- 單支端點 `PATCH /assets/dc/funds/{fundId}/allocation` 一次只能送一支基金的比例。當帳戶原本總和為 100%（例如 A=60%、B=40%），單獨修改其中一支一定會讓加總暫時不等於 100% 而被後端 400 擋掉。
- **解法**：
  - 既有單支端點 `PATCH /assets/dc/funds/{fundId}/allocation`：保留給單純修改 `expectedReturnRate` 使用，不再寫入 `monthlyContributionAmount`。
  - 新增帳戶層級批次端點（建議 `PATCH /assets/dc/accounts/{accountId}/allocations`）：一次接收該帳戶全部基金的 `{ fundCode, allocationPercentage }` 清單與 `remainderFundCode`。

---

### Phase 1：後端（Domain + DTO + Service + Controller + 單元測試）

1. **Domain 層**
   - [DefinedContributionAccountSnapshot.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)：新增 `remainderFundCode`（`String`）欄位及更新方法；`mergeAllocationFacts` 不覆寫此欄位，讓檔案掃描路徑可保留設定。
2. **Result / Response DTO 與 Mapper**
   - 確認 `DefinedContributionAccountSnapshotResult`／`Response` 回傳 `remainderFundCode`，`DefinedContributionFundSnapshotResult`／`Response` 回傳 `allocationPercentage`。
3. **Service 層**
   - [DefinedContributionServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)：
     - 既有 `updateAllocation(fundId, command)`：僅更新 `expectedReturnRate`，不再覆寫 `monthlyContributionAmount` 或傳入假比例。
     - 新增 `updateAccountAllocations(accountId, command)`：
       1. 檢核傳入基金清單涵蓋該帳戶所有基金（或比例加總嚴格等於 `100.0`，容許浮點誤差 `1e-6`），否則回 `400 Bad Request`。
       2. 檢核 `remainderFundCode` 有效性（見下方待拍板點）。
       3. 非端數基金以 `Math.floor(monthlyTotalContribution * allocationPercentage / 100.0)` 計算 `monthlyContributionAmount`。
       4. 端數基金金額 = `monthlyTotalContribution - 其他所有基金金額加總`。
       5. 寫回帳戶 `remainderFundCode` 與全部基金的 `allocationPercentage`、`monthlyContributionAmount`。
4. **Controller 層**
   - [DefinedContributionController.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java)：新增 `PATCH /assets/dc/accounts/{accountId}/allocations` 端點與對應 Request DTO。
5. **單元測試**
   - 覆蓋：加總 ≠ 100% 回 400、`floor` 捨去法＋端數吸收總和精確等於 `monthlyTotalContribution`、`remainderFundCode` 無效／缺失邊界、單支 PATCH 不再誤改金額。

---

### Phase 2：前端（Types + BFF Route + UI 表格與配分彈窗，待 Phase 1 完成後接續）

1. **[use-dc-data.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/hooks/use-dc-data.ts)**：於 `DefinedContributionFundSnapshot` 補上 `allocationPercentage?: number | null`，於 `DefinedContributionAccountSnapshot` 補上 `remainderFundCode?: string | null`。
2. **Next.js BFF Route**：補上 `/api/finance/assets/dc/accounts/[accountId]/allocations` 轉發（若尚未有 catch-all proxy）。
3. **[dc-table.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-table.tsx)** ＋ **新配分編輯對話框**：
   - 表格新增 `Allocation (%)` 欄位，並將 `Monthly Contrib.` 從 `InlineCurrencyEditor` 改回**唯讀**（符合「金額只能由比例算出，不給直接改」原則）。
   - 新增「Edit Allocation」按鈕開啟帳戶層級編輯彈窗：調整各基金比例、單選端數吸收基金、即時試算並預覽金額、加總 ≠ 100% 時擋下送出並提示錯誤。

---

### 已確認的端數規格（2026-11-25 拍板）
- **UI 控制項與預設值**：配分彈窗中使用 **Radio Button（單選）** 指定端數基金。帳戶有基金時，若後端已有有效 `remainderFundCode` 則預設選中該基金；若為 `null`（首次設定或原基金已不在清單中）則預設選中第一支基金；帳戶無基金時不開放編輯。
- **後端驗證**：`remainderFundCode` 為 `null`／空白或不在該帳戶的基金 `fundCode` 清單中時，一律回傳 `400 Bad Request`。

## Diff frontend

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment    INSERT  git --no-pager diff                                                                                       10028  12.90G   7.68    08:06:31  
diff --git a/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-snapshot-viewer.tsx b/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-snapshot-viewer.tsx
index 89dc062..1d34b6c 100644
--- a/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-snapshot-viewer.tsx
+++ b/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-snapshot-viewer.tsx
@@ -3,6 +3,7 @@
 import { useState, useEffect } from "react";
 import { useDcData } from "../hooks/use-dc-data";
 import { DcTable } from "./dc-table";
+import { DcAllocationDialog } from "./dc-allocation-dialog";
 import {
   Select,
   SelectContent,
@@ -89,11 +90,22 @@ export function DcSnapshotViewer() {
   // Combine all funds from all accounts on this date
   const allFunds = currentRecords.flatMap((record) => record.funds || []);
 
+  const remainderFundIds = new Set<number>();
+  currentRecords.forEach((record) => {
+    if (!record.remainderFundCode) return;
+    record.funds?.forEach((fund) => {
+      if (fund.fundCode === record.remainderFundCode) {
+        remainderFundIds.add(fund.id);
+      }
+    });
+  });
+
   return (
     <div className="space-y-6 tw-animate-fade-in">
       <div className="flex items-center justify-between">
         <h2 className="text-2xl font-semibold tracking-tight">Portfolio Snapshot</h2>
         <div className="flex items-center gap-4">
+          <DcAllocationDialog accounts={currentRecords} onUpdated={() => mutate()} />
           <span className="text-sm text-muted-foreground">Snapshot Date</span>
           <Select value={selectedDate} onValueChange={setSelectedDate}>
             <SelectTrigger className="w-[200px]">
@@ -134,7 +146,7 @@ export function DcSnapshotViewer() {
       </div>
 
       <div className="tw-animate-slide-up-fade">
-        <DcTable funds={allFunds} onRateUpdated={() => mutate()} />
+        <DcTable funds={allFunds} remainderFundIds={remainderFundIds} onRateUpdated={() => mutate()} />
       </div>
     </div>
   );
diff --git a/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-table.tsx b/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-table.tsx
index 09f2b6e..ce26526 100644
--- a/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-table.tsx
+++ b/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-table.tsx
@@ -10,16 +10,17 @@ import {
   TableRow,
 } from "@/components/ui/table";
 import { ExpectedReturnRateEditor } from "@/app/finance/portfolios/components/expected-return-rate-editor";
-import { InlineCurrencyEditor } from "@/app/finance/portfolios/components/inline-currency-editor";
+import { Badge } from "@/components/ui/badge";
 import { toast } from "sonner";
 import { useFundColors } from "@/app/finance/portfolios/hooks/use-fund-colors";
 
 interface DcTableProps {
   funds: DefinedContributionFundSnapshot[];
+  remainderFundIds?: Set<number>;
   onRateUpdated: () => void;
 }
 
-export function DcTable({ funds, onRateUpdated }: DcTableProps) {
+export function DcTable({ funds, remainderFundIds, onRateUpdated }: DcTableProps) {
   const { getFundColor, setFundColor } = useFundColors();
 
   if (!funds || funds.length === 0) {
@@ -56,27 +57,16 @@ export function DcTable({ funds, onRateUpdated }: DcTableProps) {
     }
   };
 
-  const handleUpdateMonthlyContribution = async (id: number | string, newValue: number) => {
-    try {
-      const response = await fetch(`/api/finance/assets/dc/funds/${id}/allocation`, {
-        method: "PATCH",
-        headers: { "Content-Type": "application/json" },
-        body: JSON.stringify({ monthlyContributionAmount: newValue }),
-      });
-      if (!response.ok) throw new Error("Failed to update");
-      toast.success("Monthly contribution updated successfully.");
-      onRateUpdated();
-    } catch (error) {
-      toast.error("Failed to update monthly contribution.");
-      throw error;
-    }
-  };
-
   const formatCurrency = (val: number | null | undefined) => {
     if (val == null) return "****";
     return `¥${val.toLocaleString()}`;
   };
 
+  const formatPercentage = (val: number | null | undefined) => {
+    if (val == null) return "****";
+    return `${val}%`;
+  };
+
   const formatUnits = (val: number | null | undefined) => {
     if (val == null) return "****";
     return val.toLocaleString();
@@ -94,6 +84,7 @@ export function DcTable({ funds, onRateUpdated }: DcTableProps) {
             <TableHead className="text-right">Principal</TableHead>
             <TableHead className="text-right">Market Value</TableHead>
             <TableHead className="text-right">Unrealized P&L</TableHead>
+            <TableHead className="text-right">Allocation (%)</TableHead>
             <TableHead className="text-right">Monthly Contrib.</TableHead>
             <TableHead className="text-right">Expected Return</TableHead>
           </TableRow>
@@ -101,6 +92,7 @@ export function DcTable({ funds, onRateUpdated }: DcTableProps) {
         <TableBody>
           {funds.map((fund) => {
             const isMasked = fund.currentMarketValue == null;
+            const isRemainderFund = !isMasked && Boolean(remainderFundIds?.has(fund.id));
             return (
               <TableRow key={fund.id}>
                 <TableCell className="font-medium max-w-[200px] truncate" title={fund.fundName}>
@@ -139,15 +131,25 @@ export function DcTable({ funds, onRateUpdated }: DcTableProps) {
                     ? "****"
                     : `${fund.unrealizedGainLoss >= 0 ? "+" : ""}¥${fund.unrealizedGainLoss.toLocaleString()}`}
                 </TableCell>
+                <TableCell className="text-right">
+                  {isMasked ? (
+                    <span className="text-muted-foreground text-xs">****</span>
+                  ) : (
+                    <div className="inline-flex items-center justify-end gap-1.5">
+                      <span>{formatPercentage(fund.allocationPercentage)}</span>
+                      {isRemainderFund && (
+                        <Badge variant="secondary" className="text-[10px] px-1.5 py-0">
+                          端數
+                        </Badge>
+                      )}
+                    </div>
+                  )}
+                </TableCell>
                 <TableCell className="text-right">
                   {isMasked || fund.monthlyContributionAmount == null ? (
                     <span className="text-muted-foreground text-xs">****</span>
                   ) : (
-                    <InlineCurrencyEditor
-                      id={fund.id}
-                      initialValue={fund.monthlyContributionAmount}
-                      onUpdate={handleUpdateMonthlyContribution}
-                    />
+                    formatCurrency(fund.monthlyContributionAmount)
                   )}
                 </TableCell>
                 <TableCell className="text-right">
@@ -183,6 +185,7 @@ export function DcTable({ funds, onRateUpdated }: DcTableProps) {
             </TableCell>
             <TableCell></TableCell>
             <TableCell></TableCell>
+            <TableCell></TableCell>
           </TableRow>
         </TableBody>
       </Table>
diff --git a/hub-ui/src/app/finance/portfolios/defined-contribution/hooks/use-dc-data.ts b/hub-ui/src/app/finance/portfolios/defined-contribution/hooks/use-dc-data.ts
index 173dce0..ddd6a68 100644
--- a/hub-ui/src/app/finance/portfolios/defined-contribution/hooks/use-dc-data.ts
+++ b/hub-ui/src/app/finance/portfolios/defined-contribution/hooks/use-dc-data.ts
@@ -13,6 +13,7 @@ export interface DefinedContributionFundSnapshot {
   unrealizedGainLoss?: number | null;
   expectedReturnRate?: number | null;
   monthlyContributionAmount?: number | null;
+  allocationPercentage?: number | null;
 }
 
 export interface DefinedContributionAccountSnapshot {
@@ -24,6 +25,7 @@ export interface DefinedContributionAccountSnapshot {
   monthlyEmployerContribution?: number | null;
   monthlySubscriberContribution?: number | null;
   monthlyTotalContribution?: number | null;
+  remainderFundCode?: string | null;
   funds: DefinedContributionFundSnapshot[];
 }
 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment    INSERT                                                                                                            10029  12.66G   7.49    08:06:56  

## Diff backend

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                                                          10030  12.63G   7.43    08:07:01  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionService.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionService.java
index 7934a7a4..6f08f91c 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionService.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionService.java
@@ -18,6 +18,7 @@ import java.util.List;
 import org.springframework.lang.NonNull;
 
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionSnapshotCommand;
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionFundSnapshotResult;
@@ -45,16 +46,26 @@ public interface DefinedContributionService {
     void saveSnapshot(SaveDefinedContributionSnapshotCommand command, boolean dryRun);
 
     /**
-     * Updates the allocation (expected return rate and monthly contribution) for a
-     * specific DC fund snapshot.
+     * Updates the expected return rate for a specific DC fund snapshot.
      *
-     * @param fundId                    The ID of the DC fund snapshot.
-     * @param expectedReturnRate        The new expected return rate.
-     * @param monthlyContributionAmount The new monthly contribution amount.
+     * @param fundId  The ID of the DC fund snapshot.
+     * @param command The update command (only expectedReturnRate is applied).
      * @return The updated fund record.
      */
     DefinedContributionFundSnapshotResult updateAllocation(@NonNull Long fundId, UpdateAllocationCommand command);
 
+    /**
+     * Batch-updates allocation percentages and the remainder-absorbing fund for
+     * an entire DC account snapshot, recalculating each fund's monthly
+     * contribution amount from {@code monthlyTotalContribution}.
+     *
+     * @param accountId The ID of the DC account snapshot.
+     * @param command   The batch allocation update command.
+     * @return The updated account snapshot record.
+     */
+    DefinedContributionAccountSnapshotResult updateAccountAllocations(@NonNull Long accountId,
+            UpdateAccountAllocationsCommand command);
+
     /**
      * Deletes a specific DC snapshot by its ID.
      *
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java
index a6c68ff3..1ec2c942 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java
@@ -12,8 +12,13 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.accounting.dc;
 
+import java.math.BigDecimal;
+import java.math.RoundingMode;
 import java.time.LocalDate;
+import java.util.HashMap;
+import java.util.LinkedHashMap;
 import java.util.List;
+import java.util.Map;
 import java.util.Objects;
 
 import org.slf4j.Logger;
@@ -26,7 +31,9 @@ import org.springframework.web.server.ResponseStatusException;
 
 import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
 import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionFundSnapshotRepository;
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionSnapshotCommand;
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionFundSnapshotResult;
@@ -100,6 +107,9 @@ public class DefinedContributionServiceImpl implements DefinedContributionServic
         }
     }
 
+    private static final double PERCENTAGE_SUM_TARGET = 100.0;
+    private static final double PERCENTAGE_TOLERANCE = 1e-6;
+
     @Override
     @Transactional
     public DefinedContributionFundSnapshotResult updateAllocation(@NonNull Long fundId,
@@ -108,14 +118,113 @@ public class DefinedContributionServiceImpl implements DefinedContributionServic
                 .orElseThrow(() -> new ResponseStatusException(
                         HttpStatus.NOT_FOUND, "Fund not found"));
 
-        fund.updateExpectedReturnRate(command.getExpectedReturnRate());
-
-        long amount = command.getMonthlyContributionAmount() != null ? command.getMonthlyContributionAmount() : 0L;
-        fund.updateAllocation(amount, fund.getAllocationPercentage());
+        fund.updateExpectedReturnRate(command != null ? command.getExpectedReturnRate() : null);
 
         return DefinedContributionFundSnapshotResult.fromEntity(dcFundRepository.save(fund));
     }
 
+    @Override
+    @Transactional
+    public DefinedContributionAccountSnapshotResult updateAccountAllocations(@NonNull Long accountId,
+            UpdateAccountAllocationsCommand command) {
+        if (command == null || command.getRemainderFundCode() == null || command.getRemainderFundCode().isBlank()
+                || command.getAllocations() == null || command.getAllocations().isEmpty()) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                    "remainderFundCode and non-empty allocations are required");
+        }
+
+        DefinedContributionAccountSnapshot account = dcRepository.findById(accountId)
+                .orElseThrow(() -> new ResponseStatusException(
+                        HttpStatus.NOT_FOUND, "Account snapshot not found"));
+
+        List<DefinedContributionFundSnapshot> accountFunds = account.getFunds();
+        if (accountFunds == null || accountFunds.isEmpty()) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                    "Account snapshot has no funds");
+        }
+
+        Map<String, DefinedContributionFundSnapshot> fundsByCode = new HashMap<>();
+        for (DefinedContributionFundSnapshot fund : accountFunds) {
+            fundsByCode.put(fund.getFundCode(), fund);
+        }
+
+        Map<String, Double> requestedPercentages = new LinkedHashMap<>();
+        double percentageSum = 0.0;
+        for (FundAllocationCommand item : command.getAllocations()) {
+            if (item == null || item.getFundCode() == null || item.getFundCode().isBlank()
+                    || item.getAllocationPercentage() == null) {
+                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                        "Each allocation must include fundCode and allocationPercentage");
+            }
+            double pct = item.getAllocationPercentage();
+            if (Double.isNaN(pct) || Double.isInfinite(pct) || pct < 0.0 || pct > PERCENTAGE_SUM_TARGET) {
+                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                        "allocationPercentage must be between 0 and 100");
+            }
+            String fundCode = item.getFundCode();
+            if (!fundsByCode.containsKey(fundCode)) {
+                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                        "Unknown fundCode for this account: " + fundCode);
+            }
+            if (requestedPercentages.putIfAbsent(fundCode, pct) != null) {
+                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                        "Duplicate fundCode in allocations: " + fundCode);
+            }
+            percentageSum += pct;
+        }
+
+        if (requestedPercentages.size() != fundsByCode.size()) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                    "Allocations must include all funds in the account");
+        }
+
+        String remainderFundCode = command.getRemainderFundCode();
+        if (!fundsByCode.containsKey(remainderFundCode)) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                    "remainderFundCode does not exist in this account: " + remainderFundCode);
+        }
+
+        if (Math.abs(percentageSum - PERCENTAGE_SUM_TARGET) > PERCENTAGE_TOLERANCE) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                    "Allocation percentages must sum to 100%");
+        }
+
+        long totalContribution = account.getMonthlyTotalContribution();
+        long nonRemainderSum = 0L;
+        Map<String, Long> computedAmounts = new HashMap<>();
+        BigDecimal totalDecimal = BigDecimal.valueOf(totalContribution);
+        BigDecimal hundred = BigDecimal.valueOf(100);
+
+        for (DefinedContributionFundSnapshot fund : accountFunds) {
+            String code = fund.getFundCode();
+            if (!code.equals(remainderFundCode)) {
+                double pct = requestedPercentages.get(code);
+                long amount = totalDecimal
+                        .multiply(BigDecimal.valueOf(pct))
+                        .divide(hundred, 0, RoundingMode.FLOOR)
+                        .longValue();
+                computedAmounts.put(code, amount);
+                nonRemainderSum += amount;
+            }
+        }
+
+        long remainderAmount = totalContribution - nonRemainderSum;
+        if (remainderAmount < 0) {
+            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
+                    "Computed contribution amounts exceed account monthly total contribution");
+        }
+        computedAmounts.put(remainderFundCode, remainderAmount);
+
+        account.updateRemainderFundCode(remainderFundCode);
+        for (DefinedContributionFundSnapshot fund : accountFunds) {
+            String code = fund.getFundCode();
+            fund.updateAllocation(computedAmounts.get(code), requestedPercentages.get(code));
+        }
+
+        return DefinedContributionAccountSnapshotResult.fromEntity(
+                dcRepository.save(Objects.requireNonNull(account)));
+    }
+
     @Override
     @Transactional
     public void deleteSnapshotById(@NonNull Long id) {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/command/UpdateAllocationCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/command/UpdateAllocationCommand.java
index 83fa6e83..0db28675 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/command/UpdateAllocationCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/command/UpdateAllocationCommand.java
@@ -17,5 +17,4 @@ import lombok.Data;
 @Data
 public class UpdateAllocationCommand {
     private Double expectedReturnRate;
-    private Long monthlyContributionAmount;
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/result/DefinedContributionAccountSnapshotResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/result/DefinedContributionAccountSnapshotResult.java
index 018ed20b..23bce86d 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/result/DefinedContributionAccountSnapshotResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/result/DefinedContributionAccountSnapshotResult.java
@@ -34,6 +34,7 @@ public class DefinedContributionAccountSnapshotResult extends BaseResult {
     private Long monthlyEmployerContribution;
     private Long monthlySubscriberContribution;
     private Long monthlyTotalContribution;
+    private String remainderFundCode;
     private List<DefinedContributionFundSnapshotResult> funds = new ArrayList<>();
 
     public static DefinedContributionAccountSnapshotResult fromEntity(DefinedContributionAccountSnapshot entity) {
@@ -51,7 +52,8 @@ public class DefinedContributionAccountSnapshotResult extends BaseResult {
         result.setMonthlyEmployerContribution(entity.getMonthlyEmployerContribution());
         result.setMonthlySubscriberContribution(entity.getMonthlySubscriberContribution());
         result.setMonthlyTotalContribution(entity.getMonthlyTotalContribution());
-        
+        result.setRemainderFundCode(entity.getRemainderFundCode());
+
         if (entity.getFunds() != null) {
             result.setFunds(entity.getFunds().stream()
                     .map(DefinedContributionFundSnapshotResult::fromEntity)
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java
index 8c0b17d6..a1b39deb 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java
@@ -86,6 +86,9 @@ public class DefinedContributionAccountSnapshot extends MultiUserBaseEntity {
     @Column(name = "enrollment_date")
     private LocalDate enrollmentDate;
 
+    @Column(name = "remainder_fund_code")
+    private String remainderFundCode;
+
     @SuppressWarnings("FieldMayBeFinal")
     @OneToMany(mappedBy = "accountSnapshot", cascade = CascadeType.ALL, orphanRemoval = true)
     private List<DefinedContributionFundSnapshot> funds = new ArrayList<>();
@@ -107,6 +110,10 @@ public class DefinedContributionAccountSnapshot extends MultiUserBaseEntity {
         fund.setAccountSnapshot(this);
     }
 
+    public void updateRemainderFundCode(String remainderFundCode) {
+        this.remainderFundCode = remainderFundCode;
+    }
+
     /**
      * Merges factual fields from a MEMBER_INFO page facet.
      * Updates enrollment date without touching financial amounts.
@@ -119,7 +126,7 @@ public class DefinedContributionAccountSnapshot extends MultiUserBaseEntity {
      * Merges factual fields from an ALLOCATION page facet.
      * Directly overwrites monthly contribution amounts and per-fund allocation
      * facts.
-     * Keeps balance facts intact.
+     * Keeps balance facts and remainder fund code intact.
      */
     public void mergeAllocationFacts(DefinedContributionAccountSnapshot incoming) {
         this.monthlyEmployerContribution = incoming.monthlyEmployerContribution;
diff --git a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java
index b1b8a97c..53d135b5 100644
--- a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java
+++ b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshotTest.java
@@ -243,4 +243,32 @@ class DefinedContributionAccountSnapshotTest {
         assertThat(existing.getMonthlyTotalContribution()).isEqualTo(40_000L);
         assertThat(existing.getTotalAssetValue()).isEqualTo(1_000_000L);
     }
+
+    @Test
+    @DisplayName("updateRemainderFundCode: should set and preserve remainderFundCode across allocation/status merges")
+    void testRemainderFundCodePreservedAcrossMerges() {
+        DefinedContributionAccountSnapshot existing = new DefinedContributionAccountSnapshot(
+                LocalDate.of(2025, 1, 1),
+                DefinedContributionType.IDECO,
+                1_000_000L,
+                800_000L,
+                0L,
+                23_000L,
+                23_000L);
+        existing.updateRemainderFundCode("9200181027");
+
+        DefinedContributionAccountSnapshot incomingAllocation = new DefinedContributionAccountSnapshot(
+                LocalDate.of(2025, 1, 1),
+                DefinedContributionType.IDECO,
+                0L, 0L, 0L, 23_000L, 23_000L);
+        existing.mergeAllocationFacts(incomingAllocation);
+
+        DefinedContributionAccountSnapshot incomingStatus = new DefinedContributionAccountSnapshot(
+                LocalDate.of(2025, 1, 1),
+                DefinedContributionType.IDECO,
+                1_100_000L, 823_000L, 0L, 0L, 0L);
+        existing.mergeStatusFacts(incomingStatus);
+
+        assertThat(existing.getRemainderFundCode()).isEqualTo("9200181027");
+    }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java
index 76705ffa..978b2e06 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java
@@ -46,7 +46,9 @@ import ch.lin.finance.hub.backend.api.controller.accounting.dc.dto.DefinedContri
 import jakarta.validation.Valid;
 import jakarta.validation.constraints.DecimalMax;
 import jakarta.validation.constraints.DecimalMin;
-import jakarta.validation.constraints.Min;
+import jakarta.validation.constraints.NotBlank;
+import jakarta.validation.constraints.NotEmpty;
+import jakarta.validation.constraints.NotNull;
 
 /**
  * REST controller for managing Defined Contribution (iDeCo, Corporate DC)
@@ -167,23 +169,49 @@ public class DefinedContributionController {
     }
 
     /**
-     * Updates the allocation (expected return rate and monthly contribution) for a
-     * specific DC fund snapshot.
-     * 
+     * Updates the expected return rate for a specific DC fund snapshot.
+     *
      * @param fundId  The ID of the DC fund snapshot.
-     * @param request The request containing the new allocation.
+     * @param request The request containing the new expected return rate.
      * @return The updated fund record.
      */
     @PatchMapping("/funds/{fundId}/allocation")
     public DefinedContributionFundSnapshotResponse updateAllocation(@PathVariable @NonNull Long fundId,
-            @RequestBody @Valid UpdateAllocationRequest request) {
+            @RequestBody @Valid UpdateExpectedReturnRateRequest request) {
 
         var command = dcMapper.toUpdateAllocationCommand(request);
         return dcMapper.toResponse(dcService.updateAllocation(fundId, command), SecurityUtil.getCurrentUserId());
     }
 
-    public record UpdateAllocationRequest(
-            @DecimalMin("-1.0") @DecimalMax("1.0") Double expectedReturnRate,
-            @Min(0) Long monthlyContributionAmount) {
+    /**
+     * Batch-updates allocation percentages and the remainder-absorbing fund for
+     * a DC account snapshot, recalculating monthly contribution amounts.
+     *
+     * @param accountId The ID of the DC account snapshot.
+     * @param request   The batch allocation request.
+     * @return The updated account snapshot record.
+     */
+    @PatchMapping("/accounts/{accountId}/allocations")
+    public DefinedContributionAccountSnapshotResponse updateAccountAllocations(
+            @PathVariable @NonNull Long accountId,
+            @RequestBody @Valid UpdateAccountAllocationsRequest request) {
+
+        var command = dcMapper.toUpdateAccountAllocationsCommand(request);
+        return dcMapper.toResponse(dcService.updateAccountAllocations(accountId, command),
+                SecurityUtil.getCurrentUserId());
+    }
+
+    public record UpdateExpectedReturnRateRequest(
+            @DecimalMin("-1.0") @DecimalMax("1.0") Double expectedReturnRate) {
+    }
+
+    public record UpdateAccountAllocationsRequest(
+            @NotBlank String remainderFundCode,
+            @NotEmpty @Valid List<FundAllocationRequest> allocations) {
+    }
+
+    public record FundAllocationRequest(
+            @NotBlank String fundCode,
+            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") Double allocationPercentage) {
     }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DefinedContributionAccountSnapshotResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DefinedContributionAccountSnapshotResponse.java
index b27cc686..de20e65c 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DefinedContributionAccountSnapshotResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DefinedContributionAccountSnapshotResponse.java
@@ -28,5 +28,6 @@ public class DefinedContributionAccountSnapshotResponse {
     private Long monthlyEmployerContribution;
     private Long monthlySubscriberContribution;
     private Long monthlyTotalContribution;
+    private String remainderFundCode;
     private List<DefinedContributionFundSnapshotResponse> funds;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DefinedContributionMapper.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DefinedContributionMapper.java
index bef869b9..2758ec46 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DefinedContributionMapper.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/dto/DefinedContributionMapper.java
@@ -20,13 +20,17 @@ import org.mapstruct.Mapper;
 import org.mapstruct.Mapping;
 import org.mapstruct.MappingTarget;
 
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.FundAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionFundSnapshotCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionSnapshotCommand;
+import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAllocationCommand;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
 import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionFundSnapshotResult;
 import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;
-import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionController.UpdateAllocationRequest;
+import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionController.FundAllocationRequest;
+import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionController.UpdateAccountAllocationsRequest;
+import ch.lin.finance.hub.backend.api.controller.accounting.dc.DefinedContributionController.UpdateExpectedReturnRateRequest;
 
 @Mapper(componentModel = "spring")
 public interface DefinedContributionMapper {
@@ -36,7 +40,11 @@ public interface DefinedContributionMapper {
 
     SaveDefinedContributionFundSnapshotCommand toFundSnapshotCommand(DefinedContributionFundRequest request);
 
-    UpdateAllocationCommand toUpdateAllocationCommand(UpdateAllocationRequest request);
+    UpdateAllocationCommand toUpdateAllocationCommand(UpdateExpectedReturnRateRequest request);
+
+    UpdateAccountAllocationsCommand toUpdateAccountAllocationsCommand(UpdateAccountAllocationsRequest request);
+
+    FundAllocationCommand toFundAllocationCommand(FundAllocationRequest request);
 
     DefinedContributionAccountSnapshotResponse toResponse(DefinedContributionAccountSnapshotResult result,
             @Context String currentUserId);
@@ -56,6 +64,7 @@ public interface DefinedContributionMapper {
             response.setMonthlyEmployerContribution(null);
             response.setMonthlySubscriberContribution(null);
             response.setMonthlyTotalContribution(null);
+            response.setRemainderFundCode(null);
         }
     }
 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                                                               10031  12.62G   7.08    08:07:27  
 