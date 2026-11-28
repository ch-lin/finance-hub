# Plan 35：家族協作對帳（Family Collaborative Reconciliation）

> **前置條件**：完成 Issue 140 v2（[issue-140-v2-01.md](../pending%20fix/issue-140-v2-01.md) → [issue-140-v2-04.md](../pending%20fix/issue-140-v2-04.md)），確保對帳模組先以單人 `ownerUserId` 完成安全隔離並消除 Hibernate `@Filter`。

---

## 一、背景與使用情境

實務上家庭記帳常見兩種跨成員對帳需求，無法僅靠「各自登入自己帳號對帳」滿足：
1. **單人代管全家對帳**：家庭內由同一位成員統一整理帳務，在 `X-View-Scope: FAMILY` 下協助核對配偶的信用卡交易與配偶的 Amazon 訂單。
2. **跨人代刷對帳**：例如先生的信用卡（或家庭附卡）刷了太太 Amazon 帳號下的訂單，需將「成員 A 的刷卡紀錄」與「成員 B 的 Amazon 訂單」建立 `TransactionLink`。

在 Issue 140 v2 Phase 3 中，為維持全系統「家族成員唯讀、寫入限本人」的現行安全邊界並修補 IDOR，對帳模組先以單人 `ownerUserId` 實作；本計畫負責在家族權限與隱私模型明確後，補齊跨成員協作對帳能力。

---

## 二、現行架構須解決的三個核心問題

1. **家族代寫入（Delegated Write）授權模型**：
   - 現行 [OwnershipGuard.java:L30-L31](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/security/OwnershipGuard.java) 與 Phase 2 所有單筆寫入端點皆採「同家族唯讀、異動僅限本人」。
   - `POST /reconciliation/link`（[ReconciliationServiceImpl.java:L161-L181](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java)）會寫入 `TransactionLink` 改變雙方對帳狀態，屬於異動操作，須先明確定義授權條件：
     - **選項 A**：凡 `X-View-Scope: FAMILY` 下同家族成員皆可對彼此 `SHARED` 的交易與訂單執行對帳（與家族共享帳本定位一致）。
     - **選項 B**：需具備特定家族角色（如 `FamilyRole.ADMIN`）或成員明確授權「允許家人代為記帳／對帳」開關才可執行跨成員連結。
2. **隱私等級須嚴格收緊至 `PrivacyLevel.SHARED`**：
   - 核對 [ReconciliationMapper.java:L44-L58](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/reconciliation/dto/ReconciliationMapper.java)，他人的 `AGGREGATE_ONLY` 交易會遮罩 `storeName` 與 `memo`（無法辨識是否為 Amazon 消費與對應商品）。
   - 因此家族對帳的讀取與連結查詢**不可**比照一般列表只排除 `PRIVATE`，必須明確限定**本人全部紀錄，或家族成員中 `privacyLevel = PrivacyLevel.SHARED` 的紀錄**，杜絕他人 `PRIVATE` 與 `AGGREGATE_ONLY` 紀錄進入對帳候選清單或被連結。
   - 同時須拍板模糊比對（`findFuzzyMatches`）的匹配範圍：
     - 僅允許同擁有者配對（A 的卡 ↔ A 的訂單、B 的卡 ↔ B 的訂單），或
     - 開放跨人配對（A 的卡 ↔ 家族成員 `SHARED` 訂單，涵蓋家庭卡／代刷情境）。
3. **`TransactionLink.ownerUserId` 歸屬與後續解除連結（Unlink）語意**：
   - [TransactionLink.java:L50](../../hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/reconciliation/TransactionLink.java) 繼承 `MultiUserBaseEntity`（`@CreatedBy` 自動記錄操作者）。
   - 若成員 A 幫成員 B 建立連結，`TransactionLink.ownerUserId` 會變成 A，但被連結的刷卡與訂單屬於 B（或分屬 A 與 B）。未來實作查看已連結紀錄與解除連結（unlink）功能時，須定義權限以「連結任一端資源的擁有者（或連結建立者）皆可視／可解除」為準，避免依 `TransactionLink.ownerUserId` 過濾時出現歸屬錯亂。

---

## 三、預計實作範圍（上述設計拍板後細化）

1. **Repository 層**：
   - `CreditCardTransactionRepository`：新增家族 `SHARED` 專用未對帳查詢 `findUnmatchedAmazonTransactionsForFamily(currentUserId, familyMemberIds, PrivacyLevel.SHARED, pageable)`。
   - `AmazonOrderRepository`：新增家族 `SHARED` 專用日期區間查詢與未對帳搜尋 `@Query`。
2. **Service 與權限守衛層**：
   - 於 `OwnershipGuard`（或對帳專用守衛）補上 `loadReconcilable`（個人模式同 `loadOwned`；家族模式允許本人或同家族且 `privacyLevel == SHARED`，並套用拍板的代寫入角色／開關檢查）。
   - `ReconciliationServiceImpl` 四支方法依 `ScopeResolver.resolveReadableScope()` 分流，並對 `TransactionLink` 補上跨人連結時的歸屬與稽核記錄。
3. **跨使用者整合測試**：
   - 驗證個人模式僅可看到與連結本人資料。
   - 驗證家族模式下可對家人 `SHARED` 資料進行建議比對與連結；對家人 `PRIVATE` 或 `AGGREGATE_ONLY` 資料一律不可見且連結回傳 `404`。
   - 驗證非家族成員操作仍嚴格回傳 `404`。
