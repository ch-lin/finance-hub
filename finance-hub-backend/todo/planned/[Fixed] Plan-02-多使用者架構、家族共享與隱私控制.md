# [Fixed] Plan 02: 多使用者架構、家族共享與隱私控制

> **實作狀態**：✅ 已實作完成（交付待驗證）  
> **關聯後續**：家族模擬器整合見 [Plan-03](Plan-03-家族財務服務與模擬器整合.md)；單筆明細隱私寫入見 [Plan-19](Plan-19-item-level-privacy-level-write-support.md)。

## 🎯 Epic: Phase 1 - 多使用者架構、家族共享與隱私控制

**Epic Scope**:
將系統從單人使用升級為支援家族多成員環境。核心工作包含：建立 `MultiUserBaseEntity` 抽象基底類別統一管理多租戶欄位、透過 Hibernate Filter 實作多租戶隔離、建立家族群組與三級隱私層級共享控制、清除後端與前端中所有 hardcode 的使用者名稱（改用 Auth Service JWT 提供的 userId）、以及停用不適合多用戶環境的排程器掃資料夾功能。

> **前提約束**：Auth Service 不做任何修改。JWT 的 `sub` 欄位已設計為 `String.valueOf(user.getId())`（即數字型 User ID），後端與前端均以此 ID 作為使用者識別依據。

---

### ✅ Task 0: Hardcode "edward" 清除與 JWT userId 串接 (Hardcode Cleanup & JWT Integration)

**概念**：
目前後端多個 Controller 與 Service 將 `"edward"` 作為 hardcode 的使用者 ID 傳入查詢與寫入邏輯，前端也有以 "edward" 命名的靜態路由資料夾。這些都需要在 Phase 5 開始之前先全面清除，改為從 Auth Service 簽發的 JWT 中提取真實的 `userId`（JWT `sub` 欄位）。這是 Phase 5 所有後續工作的必要前置條件。

**後端 Hardcode 清除（`finance-hub-backend`）**：

已知 hardcode 位置：
- `InsuranceJobServiceImpl.java:103` — `findById("edward")`
- `LifeEventController.java:34` — `findByUserIdOrderByStartYearMonthAsc("edward")`
- `CareerEventController.java:34, 49` — `findByUserIdOrderByEventYearMonthAsc("edward")` / `setUserId("edward")`
- `AssetSimulationController.java:45-61` — `runSimulationWithOverrides("edward", ...)` 等三處

清除方式（與最新架構同步）：
1. 我們**沒有**在 Controller 中反覆使用 `@AuthenticationPrincipal` 或自訂的 `@CurrentUserId`。相反地，我們建立了全域工具類 `SecurityUtil.getCurrentUserId()` 來供需要的地方直接提取。
2. 所有原本接受 hardcode `"edward"` 的 Service 方法，我們直接**移除了**它們方法簽章中的 `userId` 參數。在寫入資料時，我們完全依賴後續引入的 JPA Auditing 機制（見 Task 1.5）來自動填入使用者 ID，讓 Service 與 Controller 徹底解耦，不需要再傳遞 user id。
3. 已有 `app.security.enabled=false` 的 dev 模式將透過 `DevMockAuthFilter` 攔截，自動注入一個帶有 `sub="tester"` 的 Mock JWT。這能確保開發環境下依舊有完整的 Security Context，而不需要在程式碼中實作任何危險的 fallback（如 admin）。

**前端 Hardcode 清除（`hub-ui`）**：

已知問題：
- `/finance/profile/edward/` 是靜態資料夾（next.js route），應改為動態路由或重命名
- 目前 Profile 的 tab/頁面以 "edward" 命名，應改為通用名稱（例如 "Basic Profile"）

清除方式：
1. 將 `/finance/profile/edward/` 資料夾重命名為 `/finance/profile/me/`（或改為動態路由 `/finance/profile/[userId]/`，由 layout 從 token 讀取並跳轉）。
2. 前端從 localStorage / sessionStorage 或 Auth Provider 的 context 中取出 JWT decode 後的 `sub` 作為 `currentUserId`，並用於組成 API 請求的路徑或 header，而非 hardcode "edward"。
3. 任何顯示在 UI 上的使用者標籤（Tab 名稱 "edward"）一律替換為動態的 display name（可從 JWT claims 的 `email` 或未來新增的 `name` claim 取得），或先暫以 "Basic Profile" 作為通用標籤。

---

### 📖 Appendix: Architecture & Sequencing Rationale

*Note: The following is preserved as historical context for future AI sessions to understand why the development sequence is structured in this specific interleaved manner.*

如果要讓整個開發流程最平滑、最不會有中斷感，我們採用了「交錯式 (Interleaved) 開發」，把 Task 3 拆成兩半來做：

**最佳化開發順序記錄**

**第一步：Task 3 (前半段) - 擴充底層 Entity 與 Schema (已完成)**
因為我們確立了 `PrivacyOverrideBaseEntity` 與 Hibernate Filter 的概念，這屬於非常底層的架構。
* **做什麼**：新增 Enum `PrivacyLevel`，把它加到 `MultiUserBaseEntity` 與 `PrivacyOverrideBaseEntity` 中，加上 `@FilterDef`，並依賴 Hibernate ddl-auto 更新 DB schema。
* **為什麼先做**：趁著處理「基礎建設」的節奏先把結構改好。預設值不影響當時的現有功能。

**第二步：Task 2 (完整) - 建立家族與成員關係 (FamilyGroup CRUD) (已完成)**
* **做什麼**：實作 `FamilyGroup`、`FamilyMembership`，以及建立、發送邀請、接受邀請的 API。
* **為什麼這時候做**：有了這層關係，我們才能在下一步取得真實的 `familyIds` 供 Filter 查詢，且可以寫出完整的 Unit Test。

**第三步：Task 3 (後半段) - 動態 Filter 切換與 DTO 遮罩 (待實作)**
* **做什麼**：實作 `OwnerFilterInterceptor`，讀取前端傳來的 `X-View-Scope: FAMILY`，呼叫 Task 2 寫好的 Service 拿到家屬名單，啟用 `familyFilter`。最後在現有的 Mapper 補上隱私消費的「平淡無奇偽裝術 (Boring Masking)」。
* **為什麼最後做**：萬事俱備！有欄位判斷 (`privacyLevel`)、有名單查詢 (`familyIds`)，把最後這兩塊拼圖拼起來，不需假造 (Mock) 即可順暢地拿真實資料串接到底。

---

### ✅ Task 1: `MultiUserBaseEntity` 抽象基底類別 (Tenant-Aware Base Entity)

**概念**：
為了集中管理「所有者歸屬 (ownerUserId)」與「隱私層級 (privacyLevel)」這兩個在所有多租戶 Domain Entity 中都會重複出現的欄位，我們引入一個新的抽象基底類別 `MultiUserBaseEntity`，繼承自現有的 `BaseEntity`。所有需要多租戶隔離的 Finance Domain Entity 只需改繼承 `MultiUserBaseEntity`，就能自動具備這兩個欄位，以及對應的 Hibernate Filter 標注。

**類別設計**：
```java
// 位置：hub-domain，ch.lin.finance.hub.backend.api.domain.model
@MappedSuperclass
@FilterDef(name = "ownerFilter", parameters = @ParamDef(name = "userId", type = String.class))
@Filter(name = "ownerFilter", condition = "owner_user_id = :userId")
@Getter
public abstract class MultiUserBaseEntity extends BaseEntity {

    public static final String OWNER_USER_ID_COLUMN = "owner_user_id";
    public static final String PRIVACY_LEVEL_COLUMN = "privacy_level";

    @NotNull
    @Column(name = OWNER_USER_ID_COLUMN, nullable = false)
    private String ownerUserId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PRIVATE'")
    @Column(name = PRIVACY_LEVEL_COLUMN, nullable = false)
    @Setter
    private PrivacyLevel privacyLevel = PrivacyLevel.PRIVATE;
}
```

**需要改繼承 `MultiUserBaseEntity` 的 Domain Entity**：
- `BaseOrder`（購物類：`AmazonOrder`, `RakutenOrder`, `SofmapOrder` 自動繼承）
- `BankStatement`（帳戶可見性，Transaction 隨之繼承）
- `CreditCardStatement`（同上）
- `NisaSnapshotRecord`, `PensionRecord`, `SalaryRecord`, `ETradeAccountSnapshot` 及其他 Accounting Domain 記錄

**Transaction/Item 層的 `privacyLevel` 覆蓋**：
- `BankTransaction`、`CreditCardTransaction` 以及購物明細（如 `AmazonItem`）本身**不繼承** `MultiUserBaseEntity`（不需要獨立的 owner），但皆繼承 `PrivacyOverrideBaseEntity`，保留一個**可選的** `privacyLevel` 欄位（nullable），供覆蓋父層設定。
- **REST API 約束**：金融交易（Bank/CreditCard）在資料庫層面支援隱私覆蓋，但 REST API 端**不開放**將其明細設為 `PRIVATE`（預設一律為 `SHARED` / 公開給家族），以確保資產模擬所需資料不會因為手動隱藏而出現斷層。
- 解析規則：若子明細的 `privacyLevel != null`，使用子明細本身的設定；否則回退至所屬父層（Statement/Order）的 `privacyLevel`。

**Hibernate Filter 啟用機制**：
- `@FilterDef` 與 `@Filter` 放在 `MultiUserBaseEntity` 上，所有子類別自動繼承。
- 在 `OwnerFilterInterceptor`（Spring `HandlerInterceptor`）中，對每個 HTTP Request 啟用此 Filter 並注入當前 JWT userId。
- 家族成員間的共享查詢（Task 3 的 PrivacyLevel 機制）是一個額外的查詢層，在此 owner Filter 之外另行處理。

> **注意**：`BaseEntity` 本身位於 `platform` 模組（共用），不在 `hub-domain` 中，因此 `MultiUserBaseEntity` 需建立在 `hub-domain` 內，並繼承自 `platform` 的 `BaseEntity`。

> **資料庫重建策略**：由於系統尚在開發階段、尚未部署，直接以 Hibernate DDL auto-create（或刪表重建）重新生成 schema 即可，不需要 Flyway Migration Script。

---

### ✅ Task 1.5: 寫入 (POST/匯入) 時的自動 User ID 綁定 (JPA Auditing)

**概念與實作現況**：
在多用戶架構下，不只查詢需要過濾，**寫入資料時也必須保證 `ownerUserId` 被正確賦值**。

原本的初步構想是「在所有 Controller 的 POST/PUT 方法加上 userId 參數，並手動 `entity.setOwnerUserId(userId)`」。但為了避免程式碼過度污染，我們採用了更現代、更乾淨的 **JPA Auditing 機制** 來解決寫入問題：

1. **實作方式**：我們在 `MultiUserBaseEntity` 加上了 `@EntityListeners(AuditingEntityListener.class)`，並在 `ownerUserId` 欄位加上 `@CreatedBy`。
2. **自動注入**：實作 `SpringSecurityAuditorAware`。當系統呼叫 Repository 的 `save()` 方法時，Hibernate 會自動觸發 AuditorAware，透過 `SecurityContextHolder` 取得目前的 JWT userId（沒有 Context 則回傳 `Optional.empty()`），並自動塞入 `ownerUserId` 欄位中。
3. **成果**：所有的 Controller 與 Service 都不需要再費心去傳遞、設定 `ownerUserId`。開發者只需專注於業務邏輯（建立 Entity -> Save），底層框架會確保每一筆新建立的資料都精準地歸屬於發起 Request 的使用者。

檢討：
- 所有寫入操作已由 JPA Auditing 自動涵蓋，Controller 保持乾淨。
- 前端觸發的 import 一樣能透過 Request 的 Security Context 自動綁定資料。
- cron scheduler 自動掃描資料夾匯入功能在多使用者環境下無法辨識檔案擁有人，因此之後將會直接 disable（目前 out of scope，若觸發會因無 Security Context 且無 fallback 而拋出 Exception，這是符合資安預期的行為）。

---

### ✅ Task 2: 家族群組建立與成員邀請 (FamilyGroup & Member Invitation)

**概念**：
允許多個使用者組成一個「家族」，共同查看彼此授權公開的財務資產。

**✅ 新增 Domain Entity (已實作)**：
- ✅ `FamilyGroup`：家族群組主體，包含 `familyGroupId`, `familyName`, `createdAt`，以及 `inviteCode`（例如自動生成的6碼英數）與 `inviteCodeExpiresAt`（時效限制，如 15 分鐘）。繼承一般 `BaseEntity`（不需要 ownerUserId）。
- ✅ `FamilyMembership`：中介表，記錄 `userId`（來自 JWT sub）與 `familyGroupId` 的關聯，並包含 `role`（`FamilyRole` Enum：`ADMIN`, `MEMBER`）與 `joinedAt`。

**功能細節**：
1. ✅ **建立家族**：任何已登入用戶可建立一個 `FamilyGroup`，建立者自動成為 `ADMIN`，系統同時產生專屬的 `inviteCode` 與過期時間。
2. ✅ **刷新邀請碼**：`ADMIN` 可以隨時手動刷新 `inviteCode`。
3. ✅ **加入家族**：其他用戶輸入 `inviteCode`，驗證未過期即可綁定。一人僅限加入一個家族。
4. ✅ **退出與踢除**：`ADMIN` 可以踢人，`MEMBER` 可以主動退出。
5. ✅ **列出成員**：家族內的任何人都可以取得目前所有成員的 `userId` 清單與身分 (`ADMIN` 或 `MEMBER`)。（直接 ACTIVE，無需 PENDING 狀態）。

---

### ✅ Task 3: 三級隱私層級與共享控制 (PrivacyLevel & Sharing Control - Backend API)

**概念**：
在家族成員之間，不是所有資料都需要完全公開。我們定義三個隱私層級，讓使用者能精細控制「哪些資料讓家人看到什麼程度」。

**隱私層級定義（`PrivacyLevel` Enum）**：

| 層級 | 家人可見總額 | 家人可見明細 | 適用情境 |
|---|---|---|---|
| `PRIVATE` | ✗ | ✗ | 私房錢帳戶、完全個人的購買 |
| `AGGREGATE_ONLY` | ✓（納入家庭總資產計算） | ✗ | 個人信用卡、個人銀行帳戶 |
| `SHARED` | ✓ | ✓ | 共同生活費帳戶、共同房貸 |

**套用粒度**：
- **帳戶/訂單層級（Statement / Order 層）**：`BankStatement`、`CreditCardStatement`、以及 `BaseOrder` (`AmazonOrder`, `RakutenOrder` 等) 皆繼承 `MultiUserBaseEntity` 取得 `ownerUserId` 與預設的 `privacyLevel`。
- **單筆明細層級（Transaction / Item 層）**：因為子明細（如 `BankTransaction`, `AmazonItem`）必定是依附於父實體取得的，受限於父層的存取控制，因此它們不需要（也不該）繼承 `MultiUserBaseEntity`。為了統一管理，我們新增一個 **`PrivacyOverrideBaseEntity`** 抽象類別（繼承自一般 `BaseEntity`），裡面包含一個**可為空**的 `privacyLevel` (nullable) 欄位。所有子明細只需繼承這個類別，即可獲得覆蓋隱私設定的能力。（備註：確定採用此設計，保留子明細覆蓋父層的能力）
- 解析規則：若子明細的 `privacyLevel != null`，使用子明細本身的設定；否則回退至所屬父層（Statement/Order）的 `privacyLevel`。

> **💡【最新架構更新】(2026-08-11 修改)**
> 為了最佳化效能並確保資料在最底層就受到保護，我們**廢棄**了在 Service 層使用過濾資料的作法，改為採用 **Hibernate `@Filter` + Interceptor + DTO Data Masking** 的機制：
> 1. **資料庫層級過濾 (Hibernate `@Filter`)**：
>    在 `MultiUserBaseEntity` 中定義 `personalFilter` 與 `familyFilter`。透過 `OwnerFilterInterceptor` 攔截 HTTP 請求：
>    - 若帶有 `X-View-Scope: FAMILY`，則查詢家族成員 IDs，啟用 `familyFilter`。
>    - 否則啟用 `personalFilter`，只撈取自己的資料。
> 2. **兩層級的安全防護差異 (DB 阻擋 vs Java 遮罩)**：
>    - **父層 (Order/Account) 級別**：當啟用 `familyFilter` 時，若訂單為 `PRIVATE`，**在資料庫 (DB) 查詢階段就會被完全剔除**，Java 完全拿不到資料。
>    - **子層 (Item/Transaction) 級別**：當一筆訂單通過 DB Filter 後，系統會將底下的所有明細**全部撈出 (不套用 DB Filter)**。若子明細被標記為 `PRIVATE` 或 `AGGREGATE_ONLY`，我們**不再**於查詢時整筆剃除 (以防總額對不攏)，而是在 **DTO Mapper (Java 記憶體中)** 進行遮罩處理：強制將 `itemName` 蓋為通用字眼，並清空 `details`，僅保留金額與日期。
> 3. **單筆明細覆蓋 (`PrivacyOverrideBaseEntity`)**：子明細繼承 `PrivacyOverrideBaseEntity` 來覆蓋隱私設定。在實作上，會讓 DTO Mapper 也能讀取這個 override 值，來決定是否要執行 Data Masking。
> 4. **效能保證**：上層實體本身就帶有 `owner_user_id` 和 `privacy_level`，Hibernate `@Filter` 產生的是最單純的單表條件查詢，搭配複合索引將能達到極致效能。

**預設值與限制**：為了推動家族共享，部分項目的預設值將改為 `SHARED`（即對家族全公開），且 REST API 端會限制金融明細等關鍵資料不可被手動設為 `PRIVATE`。

---

### ✅ Task 4: 家族資訊查詢與群組狀態 API (Family Group Query & State API)

**概念**：
在 Task 2 中，後端已完成家族的建立、邀請碼刷新、加入、成員踢除/退出與成員清單查詢 (`GET /family/members`)。然而在前端進行 UI 整合時，發現缺漏了「查詢當前用戶所屬家族群組本體資訊」的端點。
這導致已加入家族的用戶在開啟介面時無法取得家族名稱（`name`）與當前現有的邀請碼（`inviteCode` 與過期時間）；未加入家族時，呼叫 `GET /family/members` 也會因拋出異常而缺乏優雅的狀態判定。

**功能細節與 API 設計**：
1. **新增端點 `GET /family`**：
   - **已加入家族**：回傳 `200 OK` 與 `FamilyGroupResponse`（包含 `id`, `name`, `inviteCode`, `inviteCodeExpiresAt`）。讓頁面能直接渲染家族名稱，且若具備權限可直接顯示現有邀請碼倒數，無須每次手動重新刷新。
   - **未加入家族**：回傳 `404 Not Found`（或 `204 No Content` / 空物件），提供前端清楚且語意化的是否加入家族判定。
2. **Service 層擴充 (`FamilyService`)**：
   - 新增 `Optional<FamilyGroupResult> getFamilyGroup(String currentUserId)`。
   - `FamilyServiceImpl` 透過 `familyMembershipRepository.findByUserId(currentUserId)` 取得所屬群組並轉為 `FamilyGroupResult`。
3. **Controller 與測試驗證**：
   - 在 `FamilyController` 注入 `GET /family` 端點。
   - 補齊 `FamilyServiceImplTest` 與 `FamilyControllerTest` 的單元測試（包含已存在群組與未加入群組的情境）。

---

#### 改動清單 (Workload & Deliverables)

| 子任務 | 目標 | 改動檔案 | 預估行數 |
|---|---|---|---|
| **Task 4: 家族資訊查詢 API** | 1. 取得當前家族資訊<br>2. 支援未加入狀態判定<br>3. 完整單元測試覆蓋 | • `FamilyService.java`<br>• `FamilyServiceImpl.java`<br>• `FamilyController.java`<br>• `FamilyServiceImplTest.java`<br>• `FamilyControllerTest.java` | ~50 行 |

---

### ✅ Task 5: 前端架構升級與家族隱私 UI 實作 (hub-ui Frontend Integration)

**概念**：
配合後端完成的多用戶與隱私遮罩機制，升級前端架構並提供家族管理的完整介面。

**原始功能細節**：
1. **Family Group Management UI**: 新增 `/finance/profile/family` 頁面，供使用者建立家族、加入家族、檢視成員、產生邀請碼。
2. **View Scope Toggle**: 在全域導覽列新增「個人 / 家族」檢視切換器，自動在 API Header 帶入 `X-View-Scope: FAMILY`。
3. **Privacy Level Selection**: 針對 Bank, CreditCard, Shopping Order 提供 UI 選項，允許使用者切換 `PrivacyLevel` (`SHARED`, `AGGREGATE_ONLY`, `PRIVATE`)。

**現狀邊界與演進說明**：
1. **去 Hardcode 已完成**：前端在 Task 0 已全面遷移至 `/finance/profile/me`，Profile 標籤已改為 "Basic Profile"，前端程式碼中已無任何 hardcode "edward"（原第 4 點已移除）。
2. **個別項目隱私設定 UI（暫緩）**：第 3 點因後端各業務實體寫入 API 目前尚未開放 `privacyLevel` 參數（見下方技術債章節），第一階段維持全域預設 `SHARED`，暫不在個別明細提供下拉選單。
3. **Task 5 真正核心**：聚焦於第 1 點「家族群組管理頁面 UI」與第 2 點「全域 View Scope 切換（個人 vs 家族）」。

---

#### 💡 架構減量設計：利用 Next.js Cookie + `api-proxy.ts` 自動穿透

為了避免在前端現存 40+ 個 API Hook 與呼叫點手動逐一加上 `X-View-Scope` Header，採用全域代理自動穿透方案：

1. **Cookie 狀態記錄**：
   - 使用者切換「個人 / 家族」模式時，寫入 Cookie（`finance_view_scope=PERSONAL` 或 `FAMILY`）。
2. **`api-proxy.ts` 自動轉發 Header**：
   - 瀏覽器發出的所有 `/api/finance/...` 請求都會自動帶上 Cookie。
   - 在 [`api-proxy.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/lib/api-proxy.ts) 加上 3 行代碼，自動解析 Cookie 並注入 `X-View-Scope` Header 轉發給後端：
     ```typescript
     const viewScope = req.cookies.get("finance_view_scope")?.value || req.headers.get("x-view-scope");
     if (viewScope) {
       headers.set("X-View-Scope", viewScope);
     }
     ```
   - **成果**：所有現存的 SWR / fetch 呼叫**零修改**即可自動具備家族過濾能力！
3. **全域快取刷新**：
   - 切換模式時，觸發 SWR 全域重新驗證 `mutate(() => true)`，所有頁面立即帶新 Scope 重新撈取。
4. **Sidebar 權限與上下文條件隔離（方案 B 側邊欄收納）**：
   - 因 `AppSidebar` 為 `youtube-hub` 與 `finance-hub` 共用，切換開關不置於全域最上方，而是收納於 `SidebarGroup label="Finance Hub"` 區塊內部。
   - **顯示防護條件**：僅在 `hasFinanceProfile === true`（代表用戶有 Finance 權限且已完成 Onboarding）且 `pathname.startsWith("/finance")` 時才渲染。
   - **成果**：若使用者無 Finance 權限或正在使用 YouTube Hub 時，切換開關自動隱藏，達成完全的無權限隔離與零視覺干擾。

---

#### 子任務規劃與改動清單 (Workload & Deliverables)

| 子任務 | 目標 | 改動檔案 | 預估行數 |
|---|---|---|---|
| **Task 5.1: 全域 View Scope 切換** | 1. 代理自動穿透<br>2. 全域 Scope 狀態 Context<br>3. Sidebar 條件切換開關（限 Finance 啟用狀態） | • [`api-proxy.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/lib/api-proxy.ts)（轉發 Header）<br>• `view-scope-context.tsx`（Cookie + SWR mutate）<br>• [`app-sidebar.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/components/app-sidebar.tsx)（於 Finance 區塊條件渲染開關） | ~60 行 |
| **Task 5.2: 家族管理頁面 UI** | 1. Profile 導覽分頁<br>2. 家族管理介面（建立、加入、邀請碼倒數、成員清單、退出） | • [`ProfileLayout.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/layout.tsx)（新增 Family Tab）<br>• `/finance/profile/family/page.tsx`（家族管理頁） | ~120 行 |

**總結**：整體改動僅需約 **5 個檔案**，程式碼總量約 180 行，可完美在單次對話與 Context Window 內高保真完成。

---

### ✅ Task 6: 排程器停用與 Tools 頁面手動匯入保留 (Scheduler Deactivation & Manual Import via Tools)

**概念**：
原本的排程器功能是依賴 Docker Volume 掛載目錄自動掃描並匯入檔案。這在多用戶環境下有根本性的問題——系統無法知道資料夾裡的檔案屬於哪個使用者。因此，排程器的自動掃描功能需要被停用，但程式碼保留以供未來重新評估方向。Tools 頁面的手動批次匯入功能則予以保留，並補上使用者身份綁定邏輯。

**排程器停用（不刪除）**：
1. `SchedulingConfig` 相關 UI（前端的排程設定頁面）維持顯示但所有控制項標記為停用（disabled），並顯示說明文字 `"已在多用戶模式下停用，保留設定供未來使用"`。
2. **不刪除**任何 `JobSchedulerService`, `SchedulerController`, `SchedulingConfigController`, `SchedulingConfig` 等相關程式碼與資料表。

**Tools 頁面手動匯入保留與使用者歸屬**：
1. Tools 頁面現有的「勾選來源後一次匯入」功能維持不變，可正常選擇要匯入的來源（Amazon, Rakuten, Sofmap, Bank, CreditCard 等）並觸發匯入。
2. **使用者歸屬（Owner Attribution）**：當 Tools 頁面的匯入 API 被呼叫時，後端從當前登入的 Security Context 取得 `userId`，並將此 `userId` 作為本次匯入所有資料的 `ownerUserId`。「誰按下匯入，資料就歸屬於誰」，簡單直觀。
3. 後端匯入 Service（如 `AmazonServiceImpl`, `BankServiceImpl` 等）的相關方法簽章新增 `String ownerUserId` 參數，由 Controller 層注入，而不是 hardcode 或由 Service 自行查詢。

---

## 📝 發現的設計與技術債 (Design Debt)

**發現時間**: 2026-08-17 (於討論 Task 4 前端實作期間)
**議題**: 個別項目 (Item-level) 的隱私層級 (Privacy Level) 缺乏 API 寫入支援

**現狀調查**:
雖然 `MultiUserBaseEntity` 已經在資料庫層級加上了 `privacyLevel` (預設為 `SHARED`)，且後端 Mapper 已經實作了根據此層級的遮蔽邏輯，但是目前 Controller 的 Request DTO (如 `BankTransactionRequest`, `AmazonOrderRequest`, `SalaryRecordRequest` 等) 以及 Service 的 Command 中，**完全沒有開放 `privacyLevel` 欄位讓前端傳入更新**。

**深度影響與分析**:
* 目前前端無法讓使用者個別設定特定帳戶、特定交易或特定訂單的隱私層級 (如設為 `PRIVATE` 或 `AGGREGATE_ONLY`)。
* 由於資料庫預設值統一為 `SHARED`，導致在正常生產環境下，所有使用者寫入的資料一律都是 `SHARED`。後端耗費大量工時在 12+ 個領域建立的 MapStruct 脫敏防線，在實務執行期**幾乎永遠不會被觸發**（除非手動直接改 DB 或執行單元測試）。
* 若要實作此功能，必須「逐一」在所有需要的 Domain (Bank, CreditCard, Shopping, Salary, Pension, etc.) 修改 Request DTO、Command 與 Service 的 Save/Update 邏輯，並在前端補上選擇 UI。
* **短期妥協**: 為了快速驗證家族共享機制，第一階段的「前端架構升級 (Task 4)」將暫緩實作個別隱私設定，先直接採用全域預設的 `SHARED` 行為，僅提供前端全域的「個人 / 家族」切換開關與極簡的家族綁定機制。
* **後續行動**: 已正式立項為 **[Plan-19：個別項目隱私層級寫入支援與前端設定 UI](Plan-19-item-level-privacy-level-write-support.md)**，待未來排程補齊所有領域的 Request DTO、Command 與前端設定選單。

---

**發現時間**: 2026-08-12 (於 User Story 3-1-4 Type 2 重構期間)
**議題**: 家族功能引入後造成的領域模型不一致與冗餘資料 (Linked FamilyMember vs Hardcoded Dependent)

**現狀調查**:
在實作多用戶架構與家族共享後，系統中出現了兩套「家族成員」資料：
1. **關聯的真實用戶 (`ch.lin.finance.hub.backend.api.domain.model.family.FamilyMember`)**: 代表在系統中透過邀請碼互相綁定、共享財務數據的真實系統帳號。
2. **手動輸入的依附型家人 (`ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent`)**: 寫死綁定在 `UserProfile` 之下（原名稱 `FamilyMember`，在 User Story 3-1-4 中為避免名稱衝突而更名為 `Dependent`），用於記錄配偶/子女的出生年月日與估算收入，供財務模擬與稅務計算使用。

**設計債影響與與 Plan-03 的關係**:
既然系統已經具備了關聯真實家族成員的能力 (FamilyGroup)，在 `UserProfile` 中繼續依賴手動填寫的「依附型家人數據」會造成嚴重的領域割裂：
- **資料不一致**：家人明明在系統中有真實帳號與真實帳單，但在模擬計算時卻依賴 UserProfile 裡手動輸入的靜態副本。
- **影響模擬準確度**：家人的實際收入、年金與資產應該直接從其關聯的真實帳號中提取，而非手動填寫。
- **與 Plan-03 的關聯及停擺原因**：
  - 此問題在規劃中已被明確列入 **[Plan-03：家族財務服務與模擬器整合](Plan-03-家族財務服務與模擬器整合.md)**，並設計了 **「Hybrid Family Member Architecture (Linked Account Model)」**：規劃在 `Dependent` 增加 `linked_user_id`，並透過 `FamilyFinancialService` 實作「真實帳號優先 (Real Data First)」的動態解析策略。
  - **停擺現況**：在完成 `FamilyMember` / `Dependent` 實體重構解耦後，開發進程因優先處理教育費用計算引擎缺陷（即 [Plan-01：教育費用計算與模型重構](Plan-01.md) 的全套四階段重構）以及資產模擬器 GA 全域最佳化演算法，導致 **Plan-03 及其串接全面停擺（尚未實作）**。目前兩套模型依然處於完全互不連通的孤島狀態（`FamilyService` 僅用於 HTTP Filter，模擬器完全無法讀取真實家族數據）。

---

## 🔍 實作檢驗與架構審查發現 (Audit Findings & Discrepancies)

**檢驗時間**: 2026-09-28 (對比 Phase 1 實際程式碼與原始規劃)

### 1. ✅ [已於 2026-09-28 修復] 隱私層級 `PRIVATE` 在 Filter 執行期未過濾問題
* **修復說明**：
  * 已在 `MultiUserBaseEntity` 與 `ExpenseItem` 擴充 `@FilterDef(name = "familyFilter")`，加入 `currentUserId` 參數。
  * 過濾條件更新為：`owner_user_id in (:familyUserIds) and (owner_user_id = :currentUserId or privacy_level != 'PRIVATE')`。
  * `OwnerFilterInterceptor` 在啟用 `familyFilter` 時同步綁定 `currentUserId` 與 `familyUserIds`。
  * **成果**：在家族檢視模式下，自己依然能看見自己的所有資料（含私房錢）；而其他家庭成員的 `PRIVATE` 紀錄則在資料庫 SQL 層級直接被剔除，徹底消除了機密外洩隱患。

### 2. ✅ [已於 2026-09-28 修復] 資料庫唯一約束 (Unique Constraint) 缺少 `owner_user_id`（多用戶同機構撞庫風險）
* **問題根源**：
  * 過去系統為單用戶模式時，為確保匯入冪等性與防呆，在各實體上設定了業務鍵的唯一約束（Unique Constraint）。
  * 在 Phase 1 導入多用戶架構後，實體繼承了 `MultiUserBaseEntity`，Application Service 亦透過 Hibernate `ownerFilter` 依用戶進行資料隔離；然而實體類別頂部的 `@Table(uniqueConstraints = ...)` 卻**遺漏更新**，未將 `owner_user_id` 納入複合唯一約束。
* **致命影響**：
  * 導致 DB 約束與 Service 層隔離邏輯衝突。當 User A 已儲存某月份帳單或投資快照時，User B 經由 Service 層檢核通過，但在執行 `save()` 寫入 DB 時會觸發全表唯一約束衝突（`DuplicateKeyException` / `DataIntegrityViolationException`），導致多用戶或家庭成員無法並行記錄相同機構、日期或標的的財務資料。
* **修復說明**：
  * 已在 `MultiUserBaseEntity` 定義靜態欄位常數 `OWNER_USER_ID_COLUMN = "owner_user_id"` 與 `PRIVACY_LEVEL_COLUMN = "privacy_level"`。
  * 已將下列全數 13 個實體的 `@Table(uniqueConstraints = ...)` 補齊 `MultiUserBaseEntity.OWNER_USER_ID_COLUMN`，使約束粒度精確落實為 **Per-User 唯一性**，徹底消除多用戶與家庭撞庫風險。
* **修復對象檔案清單 (Target Entities)**：
  1. [`BankStatement.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/bank/BankStatement.java)：`{ bank_name, statement_month }` ➔ 補上 `owner_user_id`
  2. [`CreditCardStatement.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/creditcard/CreditCardStatement.java)：`{ card_name, billing_month }` ➔ 補上 `owner_user_id`
  3. [`SalaryRecord.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryRecord.java)：`{ payment_date }` ➔ 補上 `owner_user_id`
  4. [`PensionRecord.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/pension/PensionRecord.java)：`{ record_date }` ➔ 補上 `owner_user_id`
  5. [`DefinedContributionAccountSnapshot.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)：`{ record_date, type }` ➔ 補上 `owner_user_id`
  6. [`NisaSnapshotRecord.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java)：`{ record_date, account_type, fund_name }` ➔ 補上 `owner_user_id`
  7. [`NisaContributionSetting.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java)：`{ record_date, account_type, fund_name, course }` ➔ 補上 `owner_user_id`
  8. [`ETradeAccountSnapshot.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeAccountSnapshot.java)：`{ record_date }` ➔ 補上 `owner_user_id`
  9. [`ETradeBenefitEsppPurchase.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeBenefitEsppPurchase.java)：`{ purchase_date }` ➔ 補上 `owner_user_id`
  10. [`ETradeCashTransaction.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeCashTransaction.java)：`{ transaction_date, description, amount }` ➔ 補上 `owner_user_id`
  11. [`ETradeBenefitRsuGrant.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeBenefitRsuGrant.java)：`{ grant_number }` ➔ 補上 `owner_user_id`
  12. [`ETradeBenefitRsuVestSchedule.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeBenefitRsuVestSchedule.java)：`{ grant_number, vest_period }` ➔ 補上 `owner_user_id`
  13. [`ETradeBenefitRsuEvent.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeBenefitRsuEvent.java)：`{ grant_number, event_date, event_type, quantity }` ➔ 補上 `owner_user_id`

### 3. 🟡 [架構設計釐清] 父子層所有權模型 (Ownership Model) 雙軌之反正規化取捨（非 Bug）
* **現狀對比**：
  * 購物領域 (`Shopping`)：Parent (`BaseOrder`) 繼承 `MultiUserBaseEntity`（擁有 `owner_user_id`），Child (`BaseOrderItem`) 繼承 `PrivacyOverrideBaseEntity`（無 `owner_user_id`，所有權繼承父層）。
  * 金融領域 (`Bank` / `CreditCard`)：Parent (`Statement`) 與 Child (`Transaction`) **兩者均直接繼承 `MultiUserBaseEntity`**，各自維護獨立的 `owner_user_id` 與 `privacy_level`。
* **架構決策原因剖析（非 Bug 原因）**：
  1. **REST API 單獨操作與一等公民實體 (First-Class Entity)**：
     * 金融領域中，前端有直接呼叫 `POST /accounting/bank/transactions` 或 `POST /accounting/credit-card/transactions` 單獨手動新增交易、修改交易分類與 Memo 的業務需求。
  2. **高頻直接分頁查詢與 Hibernate Filter 效能 (Denormalized Tenant Key)**：
     * 前端交易明細頁面直接對 `transactionRepository.findAll(spec, pageable)` 進行分頁與多維度動態過濾。
     * Hibernate `@Filter` 是直接在實體對應的資料表附加 `WHERE owner_user_id = :userId`。若 `BankTransaction` 未保留 `owner_user_id`，每一次查詢均必須強制 `JOIN bank_statement`，對分頁效能與 JPQL 維護均是巨大負擔。因此此處採用多租戶架構常見的 **Tenant Key 反正規化（Denormalization）**，以微量儲存換取直接查詢的高效與安全性。
  3. **細粒度隱私控制 (Fine-Grained Privacy Override)**：
     * `BankTransaction` 擁有獨立的 `privacy_level`，允許在家庭檢視模式下整份對帳單設為 `SHARED`，但其中特定私房花費可個別標記為 `PRIVATE`，直接在 SQL 查詢層被過濾。
  4. **購物領域的對比差異**：
     * 購物明細（`OrderItem`）從未有獨立新增或單獨分頁查詢需求，生命週期永遠綁定訂單整筆匯入；其個別支出查詢是透過 `@Subselect` 的 [`ExpenseItem.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/expense/ExpenseItem.java) 視圖以 SQL `JOIN amazon_order o` 動態撈出 `owner_user_id`，因此不需在實體表中冗餘儲存。
* **結論與維護規範**：
  * **維持現狀，不予修改**：金融交易保留 `MultiUserBaseEntity` 符合工程實踐與查詢效能考量。
  * **一致性保證**：Service 層在建立單筆交易時（如 `BankTransactionServiceImpl.addTransaction`），會自動將交易掛載至當前使用者的 Statement，並由 JPA Auditing (`SecurityContextHolder`) 確保父子實體之 `owner_user_id` 完全一致。

### 4. ✅ [已於 2026-09-28 修復] `PrivacyMaskingUtil` 採用 Fail-Open（預設洩漏）反模式
* **問題根源**：
  * 原 `PrivacyMaskingUtil.java` 在 `currentUserId == null || ownerUserId == null` 時直接回傳 `false`（不遮罩）。若未來有任何 Controller、非同步背景工作或新端點在調用 Mapper 時漏傳 `currentUserId`（傳入 `null`），系統會預設為擁有者特權，直接將未脫敏的機密資料（`PRIVATE` / `AGGREGATE_ONLY`）以明文回傳給前端。
* **修復說明**：
  * 已將 `PrivacyMaskingUtil.shouldMask` 改為嚴格的 **Fail-Closed（預設安全）** 三段式防禦實作：
    1. **本人驗證**：只有在 `currentUserId != null && currentUserId.equals(ownerUserId)` 確認為資料擁有者時，才放行明細（`return false`）。
    2. **全家共享**：若資料明確設定為 `SHARED`，非本人與訪客亦可檢視明文（`return false`）。
    3. **Fail-Closed 防禦**：其餘所有情況（非本人、身份不明 `currentUserId == null`、未認證請求），遇到 `AGGREGATE_ONLY`、`PRIVATE` 或未定義層級，**一律強制執行遮罩（`return true`）**！
  * 同步修正單元測試 [`PrivacyMaskingUtilTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/util/PrivacyMaskingUtilTest.java)，確保漏傳身分時嚴格遮罩，徹底杜絕機密外洩風險。

