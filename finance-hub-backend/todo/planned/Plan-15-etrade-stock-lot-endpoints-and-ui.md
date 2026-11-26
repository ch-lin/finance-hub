# Plan 15：E-Trade ESPP 與 RSU 持股明細獨立查詢/刪除端點及前端 UI 實作計畫 (E-Trade Stock Lot Endpoints and Management UI)

## 背景與動機（Why this Plan?）

在 [Question-117](../pending%20fix/question-117.md) 中，系統成功完成了 E-Trade 持股明細的重構：
1. **Domain 層**：將過去混合單一的 `ETradeStockLot` 拆解為 `ETradeEsppStockLot` 與 `ETradeRsuStockLot`，資料表分別歸屬於 `etrade_espp_stock_lot` 與 `etrade_rsu_stock_lot`。
2. **Service 層**：建立多型結果類別 `StockLotResult` 與其子類 `ETradeEsppStockLotResult`、`ETradeRsuStockLotResult`，並修復了 HTML Parser 針對 RSU Vesting Tranches 損益抓取。
3. **Controller/API 層**：將 Response DTO `CompanyStockResponse.StockLot` 多型化，支援 Jackson `@JsonTypeInfo(property = "recordType")`。

目前系統的查詢方式為聚合端點 `GET /assets/company-stock/etrade/lots`（回傳多型清單），且系統**尚未具備針對個別 ESPP / RSU 持股明細進行獨立查詢與手動刪除（Delete）的能力**。

### 為什麼需要此獨立計畫？
* **PR / Commit 單一職責（SRP）**：Question-117 專注於修復 HTML Parser 遺漏每股損益與資料實體分拆；而細粒度的 CRUD 端點與刪除連動邏輯屬於新功能，獨立規劃可保持 Code Review 焦點清晰。
* **精確資料維護與除錯**：當使用者在 E-Trade 匯入或 Extension 同步時產生髒資料、孤兒明細或重複資料時，需要能單獨檢視與刪除特定的 ESPP 或 RSU 明細。
* **前端 UI 操作完整度**：提供專屬的 ESPP 與 RSU 獨立檢視介面，並支援使用者手動刪除單筆資料的防呆確認機制。

---

## 核心設計目標（Goals）

1. **提供 4 個專屬 REST API 端點**：
   - `GET /assets/company-stock/etrade/lots/espp`：查詢所有 ESPP 持股明細
   - `GET /assets/company-stock/etrade/lots/rsu`：查詢所有 RSU 持股明細
   - `DELETE /assets/company-stock/etrade/lots/espp/{id}`：刪除指定的 ESPP 持股明細
   - `DELETE /assets/company-stock/etrade/lots/rsu/{id}`：刪除指定的 RSU 持股明細
2. **維持乾淨架構與隱私控制**：
   - 獨立查詢依然完整支援 `SecurityUtil.getCurrentUserId()` 與隱私遮罩機制（Privacy Masking）。
   - 刪除操作需進行存在性校驗（NotFound 丟出 404）與資料庫交易控制（`@Transactional`）。
3. **前端 UI/UX 升級**：
   - 前端區分 ESPP 與 RSU 兩張獨立管理表格（Tabs 或雙卡片）。
   - 每筆資料列提供操作功能（刪除垃圾桶圖示），點擊後跳出 `AlertDialog` 警示確認。
   - 支援 SWR 樂觀更新（Optimistic UI）或即時快取重新整理（`mutate`）。

---

## 詳細 API 規格設計（API Specification）

### 1. `GET /assets/company-stock/etrade/lots/espp`
* **說明**：取得所有 ESPP 持股明細（支援隱私遮罩）
* **HTTP Method**：`GET`
* **Response**：`200 OK`
  ```json
  [
    {
      "recordType": "ESPP",
      "id": 1,
      "symbol": "NVDA",
      "purchaseDate": "2024-06-30",
      "purchasePrice": { "amount": 105.00, "currency": "USD" },
      "purchasedQty": 10.0,
      "sellableQty": 10.0,
      "estCostBasis": { "amount": 105.00, "currency": "USD" },
      "estMarketValue": { "amount": 1200.00, "currency": "USD" },
      "expectedGainLoss": { "amount": 150.00, "currency": "USD" },
      "expectedGainLossPerShare": { "amount": 15.00, "currency": "USD" },
      "grantDateFmv": { "amount": 110.00, "currency": "USD" },
      "purchaseDateFmv": { "amount": 120.00, "currency": "USD" },
      "discountPercent": 15.0,
      "grantDate": "2024-01-01",
      "capitalGainsStatus": "Long Term"
    }
  ]
  ```

### 2. `GET /assets/company-stock/etrade/lots/rsu`
* **說明**：取得所有 RSU 持股明細（支援隱私遮罩）
* **HTTP Method**：`GET`
* **Response**：`200 OK`
  ```json
  [
    {
      "recordType": "RSU",
      "id": 2,
      "symbol": "NVDA",
      "grantNumber": "00136443",
      "grantDate": "2022-11-22",
      "vestPeriod": 1,
      "vestDate": "2023-11-22",
      "releaseDate": "2023-11-22",
      "sellableQty": 28.0,
      "pendingSaleQty": 0.0,
      "estCostBasis": { "amount": 149.25, "currency": "USD" },
      "estMarketValue": { "amount": 4573.24, "currency": "USD" },
      "expectedGainLoss": { "amount": 394.24, "currency": "USD" },
      "expectedGainLossPerShare": { "amount": 14.08, "currency": "USD" },
      "capitalGainsStatus": "Long Term"
    }
  ]
  ```

### 3. `DELETE /assets/company-stock/etrade/lots/espp/{id}`
* **說明**：根據 ID 刪除特定 ESPP 持股明細
* **HTTP Method**：`DELETE`
* **Path Variable**：`id` (Long)
* **Response**：
  - `204 No Content`：刪除成功
  - `404 Not Found`：查無此筆 ID

### 4. `DELETE /assets/company-stock/etrade/lots/rsu/{id}`
* **說明**：根據 ID 刪除特定 RSU 持股明細
* **HTTP Method**：`DELETE`
* **Path Variable**：`id` (Long)
* **Response**：
  - `204 No Content`：刪除成功
  - `404 Not Found`：查無此筆 ID

---

## 系統架構與層級修改（Detailed Architecture）

### 1. 後端 Application / Service 層 (`hub-app`)
* **介面擴充**：`CompanyStockService.java`
  ```java
  List<ETradeEsppStockLotResult> getEsppStockLots();
  List<ETradeRsuStockLotResult> getRsuStockLots();
  void deleteEsppStockLot(Long id);
  void deleteRsuStockLot(Long id);
  ```
* **實作類別**：`CompanyStockJobServiceImpl.java`
  - 注入現有的 `ETradeEsppStockLotRepository` 與 `ETradeRsuStockLotRepository`。
  - `deleteEsppStockLot(Long id)`：
    ```java
    @Override
    @Transactional
    public void deleteEsppStockLot(Long id) {
        if (!esppStockLotRepository.existsById(id)) {
            throw new ResourceNotFoundException("ESPP stock lot not found with id: " + id);
        }
        esppStockLotRepository.deleteById(id);
    }
    ```
  - `deleteRsuStockLot(Long id)`：同上邏輯。

### 2. 後端 Controller 與 Mapper 層 (`hub`)
* **Controller**：`CompanyStockController.java`
  - 新增 `@GetMapping("/etrade/lots/espp")`
  - 新增 `@GetMapping("/etrade/lots/rsu")`
  - 新增 `@DeleteMapping("/etrade/lots/espp/{id}")`
  - 新增 `@DeleteMapping("/etrade/lots/rsu/{id}")`
* **Mapper**：`CompanyStockMapper.java`
  - 新增 `List<CompanyStockResponse.EsppStockLot> toEsppStockLotResponseList(List<ETradeEsppStockLotResult> results, Long currentUserId);`
  - 新增 `List<CompanyStockResponse.RsuStockLot> toRsuStockLotResponseList(List<ETradeRsuStockLotResult> results, Long currentUserId);`

### 3. 快照資料一致性與業務備忘（Data Consistency Considerations）
* **E-Trade 快照性質**：
  目前 E-Trade 持股是定期「快照更新（Snapshot Import）」，每次上傳 HTML 或 Extension 同步時會整批清除並重新儲存。
* **手動刪除之影響**：
  - 手動刪除單筆 Lot 是為了移除誤匯入或特定調帳需求。
  - 在下次重新上傳 E-Trade HTML 之前，資料庫將保留刪除後的狀態；下次重新上傳檔案時，若檔案內仍包含該筆資料，則會依最新檔案內容重新匯入。
  - 前端 UI 的確認對話框需向使用者明確提示此行為特性。

---

## 前端 UI 介面設計與互動（Frontend UI / UX Design）

### 1. Hook 與 API Client (`hub-ui`)
檔案：`src/app/finance/portfolios/company-stock/hooks/use-company-stock.ts`
* 新增獨立查詢 hook：
  - `useEsppStockLots()`
  - `useRsuStockLots()`
* 新增操作方法：
  - `deleteEsppLot(id: number): Promise<void>`
  - `deleteRsuLot(id: number): Promise<void>`
* 在刪除成功後呼叫 `mutate()` 更新資料列表。

### 2. UI 元件切換與版面配置
檔案：`src/app/finance/portfolios/company-stock/components/stock-lots-table.tsx`
* **版面設計**：
  - 上方提供分頁標籤（Tabs）：`ESPP 持股明細` | `RSU 歸屬明細`
  - **ESPP 表格欄位**：
    `認購日期` | `認購價格` | `認購股數` | `可賣股數` | `每股估算成本` | `估算市值` | `預期損益` | `資本利得狀態` | `操作`
  - **RSU 表格欄位**：
    `Grant 編號` | `Vest 期數` | `歸屬日期` | `Release 日期` | `可賣股數` | `未結股數` | `每股估算成本` | `每股預期損益` | `總預期損益` | `估算市值` | `利得狀態` | `操作`
* **操作與對話框（AlertDialog）**：
  - 每一列右側設置紅色的垃圾桶圖示按鈕（Delete）。
  - 點擊後開啟確認彈窗：
    > **確定要刪除此筆持股紀錄嗎？**
    > 警告：此操作將自資料庫移除此筆持股。請注意，若您未來再次上傳包含此明細的 E-Trade 報表，該筆紀錄將會重新匯入。
  - 包含「取消」與「確認刪除（Destructive）」按鈕。
  - 刪除中顯示 Spinner，完成後透過 Toast 提示「刪除成功」。

---

## 實作階段規劃（Phases & Checklist）

### Phase 1：後端 Service 與 Repository 層擴充
- [ ] 在 `CompanyStockService` 宣告 `getEsppStockLots`、`getRsuStockLots`、`deleteEsppStockLot`、`deleteRsuStockLot`
- [ ] 在 `CompanyStockJobServiceImpl` 實作上述 4 個方法並加上事務控制
- [ ] 撰寫 Service 層單元測試，驗證刪除成功與不存在拋出例外

### Phase 2：後端 Controller 與 API 層實作
- [ ] 在 `CompanyStockMapper` 新增個別型別的 Response List 映射與 Privacy Masking
- [ ] 在 `CompanyStockController` 實作 4 個 REST 端點與 OpenAPI 規格標註
- [ ] 撰寫 `CompanyStockControllerTest`（MockMvc 驗證 200 回傳、204 成功刪除、404 例外捕捉）
- [ ] 執行 `mvn clean install` 確保全模組通過編譯與 JaCoCo 門檻

### Phase 3：前端 Hook 與 API 整合
- [ ] 在 `use-company-stock.ts` 定義 `useEsppStockLots`、`useRsuStockLots` 與 `deleteEsppLot`、`deleteRsuLot`
- [ ] 支援 SWR mutate 與快取自動重整

### Phase 4：前端 UI 與操作互動實作
- [ ] 將持股明細改為 Tabs 或雙區塊卡片展示
- [ ] 為 ESPP 與 RSU 表格加入刪除 Action 按鈕
- [ ] 整合 `AlertDialog` 確認視窗與 Toast 提示
- [ ] 手動或 E2E 驗證前端操作流程
