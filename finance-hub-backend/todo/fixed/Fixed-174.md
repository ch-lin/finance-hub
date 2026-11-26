# [FIXED] 174：分類（TransactionCategory）的PUT更新端點用不可為null的boolean欄位做全量覆寫，一旦呼叫方忘記帶上isSimulationDuplicate，就會把這個專門用來避免銀行/信用卡扣款跟Amazon對帳連結重複計算的旗標悄悄打回false

## 這份issue怎麼被發現的
在對照[WONTFIX-20](file://finance-hub/finance-hub-backend/todo/wontfix/WONTFIX-20-simulation-duplicate-flag-default-direction.md)（記錄`isSimulationDuplicate`這個旗標存在的目的，就是避免「同一筆錢在生活費基準線裡算一次，又在其他計算裡扣一次」）之後，回頭檢查這個旗標在更新路徑上是怎麼被寫入的，發現的資料層面風險。

這個旗標實際運作的場景，已重新查證`IncomeExpenseCalculator`確認：唯三個讀取它的地方分別是銀行交易、信用卡交易、以及透過`TransactionLink`對帳連結到的Amazon訂單商品——跟房貸、NISA、iDeCo完全無關，這些帳戶各自有自己獨立的計算模組，不會經過這個旗標。

## 相關檔案
- [SaveCategoryCommand.java:26-31](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/category/command/SaveCategoryCommand.java)（`SaveCategoryCommand`是一個record，`isEssential`、`isSimulationDuplicate`都宣告成基本型別`boolean`，不是可為null的`Boolean`）
- [CategoryRequest.java:39-44](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/category/dto/CategoryRequest.java)（Controller層的`CategoryRequest`同樣是基本型別`boolean`，同一個缺口在DTO層又重複一次）
- [CategoryServiceImpl.java:100-120](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/category/CategoryServiceImpl.java)（`updateCategory`：`category.setEssential(command.isEssential())`跟`category.setSimulationDuplicate(command.isSimulationDuplicate())`這兩行無條件執行，不管呼叫方這次是不是真的想異動這兩個欄位）
- [TransactionCategory.java:99-102](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/category/TransactionCategory.java)（`isSimulationDuplicate`欄位預設值`false`）
- [IncomeExpenseCalculator.java:286,299,311](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)（三處讀取`isSimulationDuplicate()`，確認這個旗標目前唯一的消費者就是生活費基準線計算，一旦被重置為`false`，原本該被排除的交易就會重新被算進生活費基準線）

## 現狀
Java的`record`跟一般Java物件一樣，基本型別（`boolean`）沒有「未提供」這個狀態可以表達——當這個command/DTO是由Jackson從JSON反序列化出來時，如果呼叫方送出的JSON裡沒有`isSimulationDuplicate`這個欄位，Jackson會直接把它當成預設值`false`（不會拋出任何錯誤或警告）。`CategoryServiceImpl.updateCategory`拿到這個command之後，不分青紅皂白地把`isEssential`、`isSimulationDuplicate`兩個欄位都重新設定一次，等於這個PUT端點永遠是「全量覆寫」語意，沒有任何「只更新我這次真正想改的欄位」的空間。

## 為何是問題
`isSimulationDuplicate`跟一般的分類名稱不一樣，它不是單純的顯示用設定——它是WONTFIX-20明確記錄「用來避免雙重計算」的業務邏輯旗標，正確性直接影響生活費基準線計算的準確度，進而影響整個破產風險模擬的可信度。如果這個分類原本已經被正確標記成`isSimulationDuplicate = true`（例如對應到「透過Amazon對帳連結、已經被歸類成必需消費的信用卡扣款」這類分類——因為這筆錢已經在Amazon訂單商品明細那一側被算過一次，`IncomeExpenseCalculator`才會在信用卡交易這一側用這個旗標把它排除，避免同一筆消費被兩邊各算一次），之後任何一次對這個分類的更新呼叫，只要呼叫端沒有在請求body裡帶上目前的`isSimulationDuplicate`值（不管是因為前端表單設計成「只給改名字」、還是任何其他原因沒有把這個欄位帶上），這個旗標就會悄悄被重置回`false`——而且沒有任何錯誤、警告或日誌可以讓人察覺這件事發生了。這份審查沒有檢查前端程式碼，所以無法確認目前的使用方式是否已經踩到這個情況，但這是後端API設計本身存在、不仰賴前端行為的一個明確缺口。

## 具體錯誤情境
使用者名下有一個信用卡消費分類叫「Amazon購物」，已經被標記`isSimulationDuplicate = true`——因為這張信用卡交易已經透過`TransactionLink`跟一筆Amazon訂單對帳連結上，`IncomeExpenseCalculator`在處理信用卡交易時會檢查這筆連結，改去Amazon訂單底下的商品明細（各自依自己的分類）計入生活費基準線，所以信用卡交易這一側才需要靠這個旗標排除，避免同一筆消費被信用卡交易跟Amazon商品明細兩邊各算一次。如果之後有任何管道（不限於現在的前端畫面，也包括未來任何直接呼叫這個API的方式）送出一次`PUT /transaction-categories/{id}`，而這次的請求body只帶了`name`跟`parentId`、沒有帶`isSimulationDuplicate`，這個欄位就會被悄悄改回`false`——下次模擬時，這筆信用卡消費會被重新算進生活費基準線，跟Amazon訂單商品明細那一側的計算疊加，形成一筆被重複計算的支出。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：把`isEssential`/`isSimulationDuplicate`改成可為null的`Boolean`，並在Service層只在非null時才覆寫**
- 做法：`SaveCategoryCommand`、`CategoryRequest`的這兩個欄位型別改成`Boolean`；`CategoryServiceImpl.updateCategory`改成`if (command.isEssential() != null) { category.setEssential(command.isEssential()); }`這樣的寫法，省略時維持原值不變。
- 優點：不管呼叫端這次送出的JSON裡有沒有帶這兩個欄位，都不會有被悄悄改掉的風險；這個欄位本質上是使用者自己設定的分類屬性（不是銀行/券商對帳單上的客觀事實），維持舊值這個方向完全符合「使用者自己設定的純設定值才該在未提供時fallback保留舊值」這個原則。
- 缺點：這會把這個PUT端點的語意從「全量覆寫」改成「部分更新」，是一個有意義的契約變化；需要確認目前有沒有任何呼叫方正依賴「送出不完整的body就能把其他欄位清空/重置」這種行為（這次審查沒有檢查前端程式碼，無法確認）。

**方向B：維持目前全量覆寫的語意，但要求呼叫端每次都先讀取完整物件再送出完整物件**
- 做法：不改後端，單純要求任何呼叫這個PUT端點的地方，都遵循「先GET拿到目前完整的分類物件，使用者編輯後，連同沒有被使用者碰過的欄位一起整包送出」這個標準做法。
- 優點：不需要異動後端程式碼；這也是很多REST API慣用的呼叫慣例。
- 缺點：這個保護完全依賴呼叫端的紀律，後端沒有任何防線；因為Java的基本型別`boolean`沒辦法分辨「JSON裡沒有這個欄位」跟「這個欄位被明確設成false」兩種情況，之後任何新增的呼叫方式（例如之後要做的局部編輯介面）都可能無意間重新引入這個問題，而且後端不會有任何提示。

**方向C：依據與 PM 討論決議，不同意原 Reviewer 提案之方向 A 與方向 B，採用「強制 `@NotNull` 契約防禦」（定案採納並實作）**
- 評估分析：
  - **不同意方向 A 的原因**：將 PUT 全量更新改為曖昧的部分更新（Partial Update），違反 REST PUT 語意，放任呼叫端不講明要設定什麼，反而掩蓋了呼叫端傳參缺漏的隱患。
  - **不同意方向 B 的原因**：純靠呼叫端自律而後端不設防，由於 Java 基本型別 `boolean` 會讓 Jackson 在缺漏欄位時無聲無息填入 `false`，未來呼叫端只要漏傳依然會覆寫破壞資料庫數值。
- 具體做法：
  - 在 Domain 層，`TransactionCategory` 實體的 `isEssential` 與 `isSimulationDuplicate` 定義皆為 `nullable = false`，代表業務上這兩個欄位本就不存在「不講明」的狀態。
  - 在 DTO 層（`CategoryRequest`），將 `isEssential` 與 `isSimulationDuplicate` 型別由基本型別 `boolean` 改為物件包裝型別 `Boolean`，並明確掛上 `@NotNull` 驗證註解。
  - 在 Service 層（`CategoryServiceImpl`），透過 `Objects.requireNonNull` 確保 Command 內傳入之布林值非空。
  - 契約保證：呼叫端（前端或任何 API 客戶端）在更新或建立分類時，必須明確表態這兩個旗標之布林值；若未提供或傳入 null，後端 `@Valid` 驗證將直接拋出 `400 Bad Request`（Fail-Fast），徹底杜絕被預設值無聲覆寫的風險。目前前端尚未實作分類維護畫面，未來前端串接時遵循「先 GET 當前狀態、連同完整旗標 PUT 送回」即可。

---

## 修正結果（採用方向 C）

本案已確認不同意方向 A 與方向 B，並根據與 PM 討論採用 **方向 C** 進行修正。

### 1. 核心修正要點
1. **DTO 層型別改為 `Boolean` 並加入 `@NotNull`**：
   - 修改 [CategoryRequest.java](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/category/dto/CategoryRequest.java)：將 `isEssential` 與 `isSimulationDuplicate` 宣告為 `@NotNull Boolean`，強制呼叫端明確提供設定值。
2. **Command 與 Service 層同步對齊**：
   - 修改 [SaveCategoryCommand.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/category/command/SaveCategoryCommand.java)：將型別對齊為 `Boolean`。
   - 修改 [CategoryServiceImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/category/CategoryServiceImpl.java)：在 `createCategory` 與 `updateCategory` 中以 `Objects.requireNonNull` 驗證非空，確保非 null 狀態進入實體。
3. **單元測試驗證**：
   - 修改 [CategoryControllerTest.java](file://finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/category/CategoryControllerTest.java)：新增單元測試驗證缺少 `isEssential` 或 `isSimulationDuplicate` 時拒絕請求並回傳 HTTP 400 Bad Request。
4. **前端影響評估**：
   - 前端專案位於平行目錄 [CategoriesPage](file://hub-ui/hub-ui/src/app/finance/configs/categories/page.tsx)。
   - 前端之表單型別 `TransactionCategoryRequest` 本身即已明確定義 `isEssential: boolean` 與 `isSimulationDuplicate: boolean`，在開啟編輯（`openDialogForEdit`）與送出更新時均完整帶上這兩個布林旗標，本就符合後端 `@NotNull Boolean` 之全量契約，因此**前端代碼完全無需任何修改**。
