# Issue 140 v2：全面廢除 Hibernate Filter，改採顯式 `OwnershipGuard` 與 Repository 範圍查詢

> 本文件為 [issue-140.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/issue-140.md) 經重新檢討後定案的替代方案。`issue-140.md` 原文保留不動，問題背景與三種症狀的盤點清單直接沿用該文件。

## 一、為何捨棄 `issue-140.md` 原方案（`BaseRepository` + Hibernate Filter）

`issue-140.md` 原先推薦以 `MultiUserJpaRepositoryImpl` 覆寫 `findById` 走 JPQL，搭配非 `GET` 強制鎖定 `personalFilter` 解決症狀 1，再用明確的 `ownerUserId` 查詢解決症狀 2。經檢討後，該方案有三個結構性風險：

1. **Hibernate Filter 掛載時機與範圍的隱式風險**
   - 專案已關閉 OSIV（`application.properties:33` 設為 `spring.jpa.open-in-view=false`），而 `OwnerFilterInterceptor.java:65` 在進入 Controller、尚未開啟 `@Transactional` 前就呼叫 `entityManager.unwrap(Session.class)` 啟用 Filter。若未在交易開啟當下綁定正確 Session，所有依賴 Filter 的查詢都會靜默放行且難以察覺。
   - 本機開發環境 `app.security.enabled=false`（`application.properties:54`）不會產生 `JwtAuthenticationToken`，導致本機與未帶 JWT 的測試完全不會觸發 `OwnerFilterInterceptor.java:59-86`，漏洞容易在開發期被掩蓋。
   - 手動觸發的背景工作（`CompletableFuture.runAsync`）不會經過 HTTP 攔截器，一樣拿不到 Session Filter。
2. **`BaseRepository` 無法覆蓋所有資料存取路徑**
   - **症狀 3**：`ExpenseServiceImpl.java:109,113,117` 直接呼叫 `entityManager.find()`，且 `AmazonItem`/`RakutenItem`/`SofmapItem` 並未繼承 `MultiUserBaseEntity`，`BaseRepository` 完全攔不到。
   - `save()` 帶主鍵走 `merge()`、關聯物件延遲載入或原生 SQL 查詢，同樣不受 Filter 與 `BaseRepository.findById()` 保護。
   - 非 `GET` 請求若改由 Filter 靜默回傳空值，`deleteById()` 會靜默略過而非報錯，也會改變既有的 `ReadOnlyFamilyRecordException` 錯誤語意（如 `MortgageRecordServiceImpl.java:59-62`）。
3. **長期維護多種機制的分裂成本**
   - 若同時保留「Hibernate Filter 管列表」、「`BaseRepository` 管 `findById`」、「顯式 `ownerUserId` 管模擬引擎」與「獨立修補 `ExpenseServiceImpl`」，未來團隊必須記住四套規則，新增端點極易誤用。

---

## 二、v2 核心決策：長痛不如短痛，全面廢除 Filter，改採全顯式邊界

所有多使用者資料存取一律在程式碼中顯式宣告範圍，不再依賴 Session 上隱式啟用的 Hibernate `@Filter`：

| 存取情境 | 涵蓋症狀 | v2 統一解法 | 生效層級 |
|---|---|---|---|
| **依 ID 的單筆讀寫刪**<br>（`GET`/`PUT`/`PATCH`/`DELETE /{id}`，含 `ExpenseServiceImpl`） | 症狀 1 ＋ 症狀 3 | **`OwnershipGuard`**<br>（`loadOwned` / `requireOwner` / `requireReadable`） | 統一由單一 Guard 元件驗證本人或家族 `SHARED` 權限 |
| **模擬引擎、個人設定、`findFirstBy`、自訂 `@Query`** | 症狀 2 | **Repository 顯式方法**<br>（`findByOwnerUserId` / `findAllByOwnerUserId`） | SQL 層強制鎖定 `owner_user_id = :currentUserId` |
| **一般列表查詢**<br>（支援 `X-View-Scope: FAMILY` 切換） | 取代原 `@Filter` | **`JpaSpecificationExecutor<T>` ＋ 共用 `Specification`**<br>（由 `ScopeResolver` 解析請求 Scope） | SQL 層動態組出個人或家族非 `PRIVATE` 條件，不影響分頁與聚合 |

> **對外 API 契約完全不變**：所有 HTTP 路徑、請求參數、`X-View-Scope` Header 規格、DTO 與前端呼叫方式皆無需修改；變動僅限於後端 Service 與 Repository 內部實作。

---

## 三、分階段落地索引（可獨立執行與驗證）

為避免單次改動範圍過大並方便逐段驗證，實作拆分為三個階段文件：

1. **[issue-140-v2-01.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/issue-140-v2-01.md) — Phase 1：基礎設施與模擬引擎隔離**
   - 建立 `OwnershipGuard`、`ScopeResolver` 與共用 `Specification` 產生器。
   - 修正 `SimulationContextFactory`、四個 `XxxContextBuilder`、`AssetSimulationEngineImpl` 與三個 `UserSettings`（解決症狀 2）。
2. **[issue-140-v2-02.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/issue-140-v2-02.md) — Phase 2：單筆依 ID 讀寫與 `ExpenseServiceImpl` 防護**
   - 將 `issue-140.md` 表一列出的所有單筆 CRUD Service 全面改用 `OwnershipGuard`（解決症狀 1）。
   - 將 `ExpenseServiceImpl.getExpenseDetail` 納入 `OwnershipGuard` 檢查（解決症狀 3）。
3. **[issue-140-v2-03.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/issue-140-v2-03.md) — Phase 3：列表查詢顯式化、防誤用防線與移除 Filter**
   - 將一般列表查詢與統計查詢改為 `JpaSpecificationExecutor` 或明確 `ownerUserId` 查詢。
   - 導入編譯期窄介面或 ArchUnit 架構測試，並補齊跨使用者 404 驗證測試。
   - 移除 `OwnerFilterInterceptor` 的 `enableFilter` 與 `MultiUserBaseEntity` 上的 `@Filter` / `@FilterDef`。
