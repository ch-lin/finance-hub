# RESTful API 標準化：DC Snapshot Endpoint 擴充計畫

目前的 `DefinedContributionController` 中，有一個專門用來接收 JSON 快照資料並建立紀錄的 Endpoint 被命名為 `POST /assets/dc/extension`。根據 RESTful API 的設計原則，URL 應該代表被操作的「資源 (Resource)」，而不應該綁定呼叫的「來源端 (Client)」。此外，目前系統缺乏刪除 DC 紀錄的功能，因此將一併新增刪除相關的 RESTful Endpoints。

## User Review Required

> [!WARNING]
> **關於「Reset 自動產生的 ID」**
> 在關聯式資料庫與 Spring Data JPA 的架構中，單純透過 `repository.deleteAll()` 刪除資料時，流水號 (Auto-increment ID) 預設是不會歸零的。
> 
> 如果要在 API 呼叫「全刪」時一併歸零 ID，必須在 Repository 中宣告 Native SQL。因為目前的專案使用 MySQL 資料庫架構 (同 `Item` 表設計)，負責實作的 Agent 必須在 Repository 補上類似以下的重置邏輯：
> `ALTER TABLE dc_account_snapshot AUTO_INCREMENT = 1`
> `ALTER TABLE dc_fund_snapshot AUTO_INCREMENT = 1`
> 請確保實作與 Review 時有涵蓋這段 Native Query。

> [!TIP]
> **關於「刪除資料的 Cascade 處理」**
> 經查閱 Domain Model，`DefinedContributionAccountSnapshot` 與底下的明細 `DefinedContributionFundSnapshot` 已設定為 `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)`。
> 因此，在實作刪除功能時，**只需要刪除主表 (`AccountSnapshot`) 的紀錄即可**，底下的明細表會被 Hibernate 自動安全地一併刪除。

## Proposed Changes

---

### Backend API (Defined Contribution Controller & Service & Repository)

#### 1. 標準化新增紀錄的 Endpoint
- **目標檔案**：`DefinedContributionController.java`
- **修改內容**：
  - 將 `receiveDcFromExtension` 方法上方的 `@PostMapping("/extension")` 註解修改為 `@PostMapping`。
  - 這將使得該 Endpoint 的路徑對應到標準的 `POST /api/finance/assets/dc`。
  - (Optional) 將方法重新命名為 `createSnapshotFromJson` 以去除命名上的來源限制。

#### 2. 新增「條件刪除/全刪除」Endpoint
- **路徑**：`DELETE /api/finance/assets/dc`
- **Query Parameter**：`?recordDate=yyyy-MM-dd` (非必填)
- **實作邏輯**：
  - 如果有傳入 `recordDate`，則呼叫 Service 刪除該日期的主表快照紀錄。
  - 如果沒有傳入 `recordDate`，則視為「全部刪除」。此時將呼叫 Service 執行清空資料表的操作，並呼叫 Repository 的 Native Query (`ALTER TABLE ... AUTO_INCREMENT = 1`) 執行重置流水號的邏輯。
  - *(註：刪除與重置邏輯僅需針對 `AccountSnapshot` 與 `FundSnapshot` 兩張表操作，明細資料的刪除會依賴 Hibernate Cascade 自動處理。但 ID 歸零指令需要兩張表都下 `ALTER TABLE`)*

#### 3. 新增「刪除指定 ID 快照」Endpoint
- **路徑**：`DELETE /api/finance/assets/dc/{id}`
- **Path Variable**：`id` (快照紀錄的主鍵)
- **實作邏輯**：
  - 根據傳入的 `id`，呼叫 Service 刪除特定的主表快照紀錄。
  - *(註：同上，僅需刪除 `AccountSnapshot` 即可)*

## Verification Plan

### Manual Verification
1. 透過 Postman 或 API 測試工具對 `POST /api/finance/assets/dc` 發送 JSON payload，確認新增成功。
2. 測試 `DELETE /api/finance/assets/dc/{id}`，確認可以單獨刪除一筆主表與關聯明細。
3. 測試 `DELETE /api/finance/assets/dc?recordDate={date}`，確認能刪除特定日期的所有主表與關聯明細。
4. 測試 `DELETE /api/finance/assets/dc`（不帶參數），確認所有資料被清空，且下一筆新增的資料 ID 確實被 Reset 回 1 (包含主表與明細表的 ID 皆歸零)。
