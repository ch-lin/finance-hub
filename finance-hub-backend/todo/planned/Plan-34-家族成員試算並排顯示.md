# Plan 34：家族成員個人試算同頁並排顯示（年金／NISA 試算）

> **前置條件**：完成 Issue 140 v2（[issue-140-v2-01.md](../pending%20fix/issue-140-v2-01.md) → [issue-140-v2-04.md](../pending%20fix/issue-140-v2-04.md)），確保所有多使用者查詢已改採顯式 `ownerUserId` 與 `ScopeResolver`／`OwnershipGuard` 邊界並移除 Hibernate `@Filter`。
>
> **與 [Plan-03-家族財務服務與模擬器整合.md](Plan-03-家族財務服務與模擬器整合.md) 的分工**：
> - **Plan 03** 處理的是「全家庭共用現金流水庫的聯合資產模擬（Engine + GA Optimizer）」，需要按西元年聯合追蹤配偶與本人的現金流、免稅額度與赤字賣出順序。
> - **本計畫**處理的是「單一模組頁面（年金頁、NISA 頁）在 `X-View-Scope: FAMILY` 下，**同頁並排顯示自己與各家族成員（配偶／小孩）各自獨立的試算曲線**」，不將多人的曲線合併成單一條，也不必等待 Plan 03 的聯合模擬完成即可獨立實作。

---

## 一、背景與使用情境

1. **年金試算（Pension Projection）**：
   - 使用者在 `X-View-Scope: FAMILY` 模式下瀏覽年金頁面時，除了看到自己與家人的非 `PRIVATE` 年金歷史紀錄（`GET /assets/pensions`），希望能於同一個頁面同時看到**自己與配偶各自獨立的年金試算曲線**，方便判斷退休當年全家人各自可領的年金水位。
2. **NISA 試算（NISA Projection）**：
   - 使用者在 `X-View-Scope: FAMILY` 模式下瀏覽 NISA 頁面時，除了看到自己、配偶與幫小孩建立的非 `PRIVATE` NISA 快照紀錄（`GET /assets/nisa`），希望能於同一個頁面直接查看**自己、配偶與小孩各自獨立的 NISA 未來試算走勢**，無須反覆切換登入帳號。
3. **隱私前提**：
   - 家族成員設為 `PrivacyLevel.PRIVATE` 的資料絕對不可出現在試算結果中，且不可暴露該成員是否存在私有資料。

---

## 二、現行程式碼現況與缺口

1. **年金試算（`GET /assets/pensions/projection`）**：
   - 入口：[PensionRecordController.java:L67-L78](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java) 直接傳入 `jwt.getSubject()`（僅限登入者本人）呼叫 `PensionProjectionServiceImpl.calculateProjection(String userId, int maxAdditionalYears, Integer pensionStartAgeOverride)`。
   - Service 層：[PensionProjectionServiceImpl.java:L60-L147](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java) 本身已接受顯式 `String userId` 參數，並依該使用者的 `SalaryRecord`、`PensionRecord`、`UserProfile` 計算；但目前尚未檢查「跨成員查詢時排除該成員的 `PRIVATE` 紀錄」。
   - 回應 DTO：[PensionProjectionYearlyResponse.java:L15-L21](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/dto/PensionProjectionYearlyResponse.java) 僅為扁平的單人陣列，沒有 `ownerUserId` 或成員分組資訊。
   - Mapper：[PensionRecordMapper.java:L46-L64](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/dto/PensionRecordMapper.java) 已實作 `toMaskedResponse`，當他人資料為非 `SHARED`（如 `AGGREGATE_ONLY`）時將金額欄位遮罩為 `null`。

2. **NISA 試算（`GET /assets/nisa/projection`）**：
   - 入口：[NisaSnapshotRecordController.java:L84-L90](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaSnapshotRecordController.java) 傳入登入者本人 `currentUserId` 呼叫 `NisaProjectionService.calculateProjection(String userId, int yearsToProject)`（於 Issue 140 v2 Phase 3 調整為接受顯式 `userId`）。
   - Service 層：[NisaProjectionServiceImpl.java:L58-L107](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaProjectionServiceImpl.java) 已接受顯式 `userId` 並按單一使用者取其最新快照日期計算；若要替特定家族成員計算，須新增隱私過濾參數，確保跨成員查詢時僅對該成員取最新一筆**非 `PRIVATE`**（或僅 `SHARED`）快照，避免觸碰他人私有基金。
   - 回應 DTO：[NisaProjectionYearlyResponse.java:L15-L20](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaProjectionYearlyResponse.java) 同樣僅為扁平單人陣列，沒有 `ownerUserId` 或成員分組資訊。
   - Mapper：[NisaSnapshotMapper.java:L50-L67](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaSnapshotMapper.java) 已實作 `toMaskedResponse`。

---

## 三、設計重點與待拍板方案

### 1. API 契約設計（兩種候選方案）

- **方案 A：新增按成員分組的家族試算端點（或 `FAMILY` 專用聚合回應）**
  - 例如：`GET /assets/pensions/projections/family` 與 `GET /assets/nisa/projections/family`（或擴充回應包成 `List<MemberProjectionResponse>`，包含 `ownerUserId`、成員顯示名稱與各自的 `List<...YearlyResponse>`）。
  - **優點**：前端切至家族視角時一次請求即可取得「自己＋所有具備可見資料的家族成員」各自獨立的曲線陣列，無 N+1 請求，也不破壞原單人端點契約。
  - **缺點**：需新增端點與分組 DTO，前後端須同步實作。

- **方案 B：既有端點加上選填查詢參數 `targetUserId`**
  - 例如：`GET /assets/pensions/projection?targetUserId={memberId}`、`GET /assets/nisa/projection?targetUserId={memberId}`（未帶參數預設查本人；帶他人 ID 時必須同時帶 `X-View-Scope: FAMILY` 且經 `ScopeResolver`／`FamilyService` 驗證同屬家族，否則回傳 `404`）。
  - **優點**：完全沿用既有回應 DTO，後端改動極小。
  - **缺點**：前端需先取得家族成員清單再逐人呼叫（N 次請求），且前端須自行組裝成員名稱標籤。

### 2. 隱私邊界規則（實作時必須明確處理）

1. **`PRIVATE` 嚴格排除**：
   - 查詢他人資料計算試算時，底層查詢必須限定 `ownerUserId = :targetUserId AND privacyLevel <> PrivacyLevel.PRIVATE`（例如：`NisaSnapshotRecord` 只取該成員最新日期的非 `PRIVATE` 基金；`PensionRecord` 與 `SalaryRecord` 只取非 `PRIVATE` 紀錄）。
   - 若某位家族成員完全沒有任何非 `PRIVATE` 資料，應略過該成員（方案 A）或回傳空陣列 `[]`（方案 B），避免暴露該成員是否有隱藏紀錄。
2. **`AGGREGATE_ONLY` 行為確認（實作前拍板）**：
   - **規則 1（僅 `SHARED` 顯示他人個人獨立曲線）**：對齊既有 `PensionRecordMapper.toMaskedResponse` 與 `NisaSnapshotMapper.toMaskedResponse` 的設計——單一成員的獨立試算曲線相當於揭露該成員個人資產規模，僅 `SHARED` 顯示金額，`AGGREGATE_ONLY` 不顯示個人金額（或僅納入全家庭加總卡片）。
   - **規則 2（`AGGREGATE_ONLY` 也允許顯示該成員個人試算總額曲線）**：因試算圖表只呈現年度總額而非單筆基金／薪資明細，允許 `AGGREGATE_ONLY` 納入該成員的總額曲線（須同步調整 Mapper 的遮罩條件，僅對單筆明細遮罩）。
3. **跨成員 `SalaryRecord` 連帶隱私檢查（年金試算專用）**：
   - [PensionProjectionServiceImpl.java:L74-L75,L113](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java) 會同時讀取目標使用者的 `SalaryRecord` 推估未來厚生年金；若家人的 `PensionRecord` 是 `SHARED` 但 `SalaryRecord` 是 `PRIVATE`，跨人試算時不得讀取該家人的 `PRIVATE` 薪資紀錄，應比照無薪資紀錄走系統預設值 `config.getPensionExpectedMonthlySalary()`（或改用明確的隱私 fallback 規則）。

---

## 四、預計實作步驟（拍板方案後細化）

1. **Service 層重構**：
   - `NisaProjectionServiceImpl` 抽出 `calculateProjectionForUser(String targetUserId, boolean includePrivate, int yearsToProject)`，按單一使用者各自取最新可見快照日期計算。
   - `PensionProjectionServiceImpl` 新增對 `includePrivate`（或 `excludePrivate`）的支援，替換跨人查詢時對 `PensionRecordRepository` 與 `SalaryRecordRepository` 的讀取，確保不觸碰家人的 `PRIVATE` 紀錄。
   - 透過 `ScopeResolver` 驗證家族視角與成員資格。
2. **Controller 與 DTO 層**：
   - 依拍板的方案 A 或方案 B 新增／擴充 API 與 Response DTO，並補上成員顯示資訊（如 `ownerUserId` / display name）。
3. **整合測試**：
   - 驗證個人模式僅回傳本人試算；家族模式下正確並排回傳本人、配偶、小孩各自獨立的曲線。
   - 驗證家人 `PRIVATE` 紀錄（含 NISA 快照、年金紀錄與薪資紀錄）完全不影響試算結果且不外洩。
   - 驗證非家族成員即使指定 `targetUserId` 亦無法讀取（回傳 `404`／`403`）。
4. **前端 UI 連動**：
   - 年金頁與 NISA 頁在家族視角下於同一圖表／區塊並排顯示各成員獨立試算曲線與圖例切換。
