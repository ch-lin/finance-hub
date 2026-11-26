# Task 3: Dynamic Filters & Data Masking (User Stories)

基於 Epic 的核心精神與目前 Codebase 的真實架構（包含 Step 1 的最終決定：Bank/CreditCard Transaction 繼承 `MultiUserBaseEntity`），我們將 Task 3 拆解為以下四個獨立且聚焦的 User Stories，以便分批實作，避免 PR 過大或 Context 遺失。

---

## 總覽：架構現狀與防護機制釐清

在開始實作前，必須先釐清現有的 Domain Model 繼承關係，這決定了它們在哪一層被防護：

### 1. DB 層防護 (被 `familyFilter` 自動擋下 `PRIVATE`)
**受詞**：任何繼承 `MultiUserBaseEntity` 的 Entity。
**包含**：`AmazonOrder`, `BankStatement`, `BankTransaction`, `CreditCardTransaction`, `LifeEvent`, `CareerEvent`。
**行為**：當啟用 `familyFilter` 時，這些 Entity 若為 `PRIVATE`，會直接在 DB 查詢時被剔除。剩下的只有 `SHARED` 與 `AGGREGATE_ONLY`。
*(註：沒有繼承 `MultiUserBaseEntity` 的附屬明細如 `AmazonItem` 等，完全**不受**此 DB Filter 阻擋，它們會全數交由下方的「受詞 B」在 Java 層進行遮罩。)*

### 2. Java 層防護 (Boring Masking)
**受詞 A (Parent Entities 的 AGGREGATE_ONLY)**：
剛才通過 DB Filter 的 `BankTransaction`, `CreditCardTransaction`。它們的金額必須保留算總帳，但如果是 `AGGREGATE_ONLY`，名稱與細節必須在回傳給 UI 之前被 Masking。
*(註：基於產品設計，`LifeEvent` 與 `CareerEvent` 不參與遮罩，因為遮罩未來的人生規劃反而會引發家庭猜忌。這類事件建議要嘛全公開 `SHARED`，要嘛完全隱藏 `PRIVATE` 交由 DB 剔除。)*

**受詞 B (Child Items 的 PRIVATE & AGGREGATE_ONLY)**：
沒有繼承 `MultiUserBaseEntity` 的附屬明細（如 `AmazonItem`）。它們沒有 DB Filter，會跟著父訂單一起被撈出。若其自身或父訂單為 `PRIVATE` / `AGGREGATE_ONLY`，必須在產生報表或 DTO 時被 Masking。
> **💡 Fallback 判定優先級**：
> 針對附屬明細，判斷是否需要 Masking 的優先順序為：**先看 Item 自身的 `privacyLevel`**。如果 Item 有設定 (非 null)，就以此為準；如果 Item 沒有設定 (為 null)，則 fallback 繼承 **Parent Order 的 `privacyLevel`** 作為最終判定依據。

---

## ✅ User Story 1: 動態攔截器、DB Filter 啟用與資安防堵
**目標**：透過獨立的 Security Interceptor 防堵 Extension API 被惡意利用，並讓 `OwnerFilterInterceptor` 專注於根據 Header 切換資料庫查詢範圍。
**實作細節**：
✅ 1. **Pre-work: 新增 `ExtensionSecurityInterceptor` (Fail-fast 資安防護)**：建立專屬攔截器並限定擴充功能路徑 (如 `/*/extension/**`)。若請求帶有 `X-View-Scope: FAMILY`，直接回傳 HTTP 403 拒絕請求，並確保其執行順序高於 `OwnerFilterInterceptor`。
✅ 2. **新增 `ExtensionSecurityInterceptor` (Fail-fast 資安防護)**：
   - 建立此攔截器並在註冊時限定路徑 (如 `addPathPatterns("/*/extension/**")`)。
   - 檢查請求中是否帶有 `X-View-Scope: FAMILY` Header。若有，這代表越權存取嘗試，**直接回傳 HTTP 403 Forbidden (或拋出 Exception)**，拒絕請求。
   - 將其執行順序 (Order) 設為最高，確保它在所有業務邏輯前執行。
✅ 3. **修改 `OwnerFilterInterceptor` (純粹的查詢切換)**：
   - 移除內部關於 URL 的檢查，回歸單一職責。
   - 讀取 `X-View-Scope` Header。若為 `FAMILY`，注入 `FamilyService` 取得家人 ID 列表，並啟用 `familyFilter`。
   - 若未帶 Header 或不為 `FAMILY`，預設啟用 `personalFilter`，僅查詢自身資料。
✅ 4. **單元測試 (MockMVC)**：
   - 撰寫測試驗證 `ExtensionSecurityInterceptor` 對於帶有 Header 的 extension API 會正確回傳 403。
   - 撰寫測試驗證 `OwnerFilterInterceptor` 在一般 API 的切換邏輯。

---

## ✅ User Story 2: 統一的遮罩公用程式 (PrivacyMaskingUtil)
**目標**：集中管理「平淡無奇偽裝術 (Boring Masking)」的判斷邏輯，避免散落在各處。
**實作細節**：
✅ 1. 新增 `PrivacyMaskingUtil` (或 Service)。
✅ 2. 提供判斷邏輯：實作上述的 Fallback 機制 (Item 先決，無則 Parent)，若判定結果非本人且為 `AGGREGATE_ONLY` (對於 Item 也包含 `PRIVATE`)，則需要遮罩。
✅ 3. 遮罩行為：回傳通用名稱 ("Bank Transaction", "Amazon Shopping") 並清空細節。
✅ 4. 撰寫單元測試驗證遮罩判斷邏輯。

---

## User Story 3-1: 架構重構基礎 - MapStruct 導入與 Controller/Service 解耦
**目標**：在建立新 API 遮罩機制前，先盤點並解決目前 Codebase 中三種不同程度的 Controller 技術債，將轉換邏輯全面遷移至 MapStruct，並落實 Command Pattern 與 Web DTO 解耦。

### ✅ 3-1-1: 處理狀況一 (有乖乖寫 Web DTO 與 @Component Mapper)
**[現狀更新: Antigravity 越權執行已完成]** 
**現狀調查**：目前僅有 `BankStatement`, `LifeEvent`, `CareerEvent` 模組擁有獨立的 Mapper 類別。
**實作細節**：
1. 確保 `pom.xml` 中 MapStruct (`mapstruct-processor`) 配置正確。
2. 將 `BankStatementMapper`, `LifeEventMapper`, `CareerEventMapper` 從手寫的 `@Component` 改寫為 `@Mapper(componentModel = "spring")` 介面。

### ✅ 3-1-2: 處理狀況二 (有 Web DTO，但轉換邏輯髒在 Controller 內)
**[現狀更新: Antigravity 越權執行已完成]**
**現狀調查**：`ChildProfileController` 有定義 Web DTO，也用了 Command Pattern，但手刻了 `mapToDto`；`FamilyController` 則依賴 `FamilyGroupResponse.fromEntity` 等靜態方法。
**實作細節**：
1. 建立 `ChildProfileMapper`。修改 `ChildProfileController` 移除手動 `mapToDto`，改注入 Mapper 進行轉換。
2. 建立 `FamilyMapper`。修改 `FamilyGroupResponse` 移除 `fromEntity`，修改 `FamilyController` 改注入 Mapper 進行轉換。

### ✅ 3-1-3: 處理狀況三 (核心模組的越權實作收尾)
**[現狀更新: Antigravity 先前越權實作了 Bank, CreditCard, Expense 的 Web DTO，導致編譯與測試錯誤，現已進入收尾修正階段]**
**現狀調查**：
這三個核心 Controller 雖然已經初步被替換為 Web DTO，但尚未建立完整的 Mapper，且測試檔也因 Context 崩潰而中斷。
**實作細節**：
1. 針對 `BankTransaction`, `CreditCardTransaction`, `Expense`，完成 `@Mapper(componentModel = "spring")` 的實作並將 Command Pattern 補齊。
2. 修復 `FamilyControllerTest` 等受影響的單元測試，補上 `@Spy` Mapper。

### ✅ 3-1-4: 處理狀況四 (實體外洩與驗證模型錯置解耦)
**現狀調查**：
- **Type 1 (實體外洩)**：完全沒有 Web DTO，直接把 Domain Entity 當作 Input/Output 暴露給前端。
  - 受影響範圍：`PensionRecordController`, `SalaryRecordController`, `NisaSnapshotRecordController`, `NisaContributionSettingController`, `ReconciliationController`, `CompanyStockController`。
- **Type 2 (驗證模型錯置)**：將帶有 Web Validation (`@NotBlank`, `@DecimalMin` 等) 的 Request DTO 寫在純粹負責業務邏輯的 `hub-app` 層。
  - 受影響範圍：`CategoryController`, `MortgageRecordController`, `UserProfileController`。
**實作細節**：
1. **Input 解耦 (Command Pattern)**：在 `hub` (Controller 層) 建立對應的 Web Request DTO，並透過 Mapper 將其轉換為定義在 `hub-app` 的 **Command 物件**。
2. **Output 解耦 (Response DTO)**：在 `hub` 層建立專屬的 Web Response DTO，隱藏 Entity 內部細節。
3. **職責分離 (Mappers)**：全面建立 `@Mapper(componentModel = "spring")` 介面，取代原本耦合的設計。

### ✅ 3-1-5: 處理狀況五 (職責混淆與手動組裝解耦)
**現狀調查**：
- **Type 3 (職責混淆)**：未透過 Mapper/Service 解耦，直接在 Controller 內部寫數十行程式碼手動解析 Request 並組裝 Domain Entity。
  - 受影響範圍：`DefinedContributionController`, `AmazonExtensionController` (及其他 Extension Controllers)。
**實作細節**：
1. **職責分離 (Mappers)**：將 Controller 內部的骯髒組裝邏輯抽離，移至 `@Mapper` 或是 Service 內部。Controller 僅負責接收請求並交由 Mapper 轉換。

### ✅ 3-1-x: 驗證基礎建設
1. 執行 `mvn clean compile -pl hub` 確保所有 MapStruct 的 `*MapperImpl` 成功產生。
2. 執行現有單元測試確保替換為 MapStruct 後行為與原本一致。特別確認 `FinanceHubApplicationTest` 的 Context 加載成功，確保不再有 NPE。

---

## User Story 3-2: UI REST API 遮罩 - 核心交易與購物紀錄 (Bank, Credit Card & Shopping)
**目標**：確保核心交易與購物紀錄 API 不會洩漏 `AGGREGATE_ONLY` / `PRIVATE` 的敏感文字，並套用 MapStruct 進行轉換。
**實作細節**：
1. **Bank API**：於 `BankTransactionMapper` 套用遮罩（名稱改為預設文字並清空備註）。
2. **CreditCard API**：於 `CreditCardTransactionMapper` 套用遮罩。
3. **Shopping / Reconciliation API**：於 `ReconciliationMapper` (或其他會回傳購物實體的 Mapper) 中實作遮罩邏輯。注意子項目需繼承父層的隱私等級進行判斷。

*(註釋：`AmazonExtensionController` 與 `BankExtensionController` 等 Extension API 是專供使用者的 Chrome 擴充功能同步自己的資料。基於資安考量，`ExtensionSecurityInterceptor` 會主動丟棄這些路徑的 `X-View-Scope: FAMILY` Header，強制套用個人資料過濾。這塊 API 只會拿到自己的資料，無需實作遮罩邏輯。)*

---

## User Story 3-3: UI REST API 遮罩 - 購物平台與對帳系統 (Amazon, Rakuten, Reconciliation)
**目標**：確保對帳系統 (`ReconciliationController`) 與購物平台的 UI API 能正確套用資料遮罩。
**實作細節**：
1. **Reconciliation API**：其回傳的 `AmazonOrder`、`CreditCardTransaction` (或包裝在 `SuggestedMatchDto` 內的物件) 需轉換為對應的 Response DTO，並套用 `PrivacyMaskingUtil` 遮罩。
2. **其他 Shopping API**：盤點其他暴露給 UI 的購物車 API，統一轉換為 DTO 並遮罩。

---

## User Story 3-4: UI REST API 遮罩 - 其他財務紀錄 (Expense)
**目標**：確保剩餘的財務紀錄 (`ExpenseController` 等) 也能正確遮罩。
**實作細節**：
1. 建立對應的 Response DTO 與 Mapper。
2. 在 Controller 層套用轉換與遮罩。



## User Story 4: Markdown 報表遮罩 (Report Strategies)
**目標**：確保在家族儀表板中，購物明細的敏感名稱與網址被隱藏。
**實作細節**：
1. 修改 `ReportServiceImpl` 及其對應的 Strategies (如 `AmazonReportStrategy`)。
2. 在組裝 Markdown 字串跑迴圈遍歷 `AmazonItem` 時，呼叫 `PrivacyMaskingUtil`。
3. 若需遮罩，則輸出不含 URL 的通用名稱 (如 `"Amazon 購物"` 或 `"Rakuten 購物"`)，但保留原本的單價與數量以利總額核對。

