# Plan 22：E-Trade 股票匯入依股票代號限定刪除與多公司持股隔離架構計畫 (E-Trade Symbol-Scoped Import & Inventory Deletion)

## 背景與問題本質（Background & Problem Statement）

### 1. 問題來源
本計畫源自 [Question 158](../pending%20fix/question-158.md) 之架構審查與業務邏輯探討。
在現行 E-Trade 資料匯入服務 [`CompanyStockJobServiceImpl.java:335-337, 373-385`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/CompanyStockJobServiceImpl.java#L335-L337) 中，對於在庫持股（`etrade_espp_stock_lot` / `etrade_rsu_stock_lot`）以及獎酬歷史（`etrade_benefit_*` 4 張表）採用了無條件全表清空策略：

```java
// CompanyStockJobServiceImpl.java (現行程式碼)
case ETradeHoldingsParseResult parsed -> {
    // 快照表依日期更新，守護折線圖
    if (parsed.getSnapshot() != null) {
        repository.deleteByRecordDate(parsed.getSnapshot().getRecordDate());
        repository.save(parsed.getSnapshot());
    }

    // 在庫持股明細：無條件整表清空！
    logger.info("Overwriting E-Trade stock lots ({} lots parsed)", parsed.getLots().size());
    esppStockLotRepository.deleteAll();
    rsuStockLotRepository.deleteAll();
    ...
}

case ETradeBenefitHistoryParseResult parsed -> {
    // 獎酬歷史：無條件整表清空！
    esppRepository.deleteAll();
    rsuGrantRepository.deleteAll();
    rsuEventRepository.deleteAll();
    rsuVestScheduleRepository.deleteAll();
    ...
}
```

### 2. 現實使用情境與潛在架構隱患
* **現狀事實**：目前系統主要使用者在實體世界中僅持有單一使用中之 E-Trade 帳戶與單一公司股票；過去雖曾有前公司經歷之 RSU 存留，但因離職時公司政策要求全數賣出變現，因此在當前環境下暫未觸發資料覆蓋問題。
* **架構盲點（靜默資料清空風險）**：
  若使用者或其家庭成員（多使用者架構）具備以下常見職涯或投資情境：
  1. **跨雇主持股並存**：使用者從前公司（如 Google，代號 `GOOG`）離職但依法保留既得之在庫股票，隨後進入新公司（如 Nvidia，代號 `NVDA`）並定期取得新公司之 ESPP/RSU。
  2. **分檔匯入不同標的**：使用者分別自 E-Trade 匯出前公司持股明細與新公司持股明細進行分批匯入。
  * **觸發後果**：當使用者匯入新公司（`NVDA`）檔案時，現行的 `deleteAll()` 會將資料庫中既有的前公司（`GOOG`）在庫持股整批無聲抹除（Silent Data Wipeout）！

### 3. 核心洞察（Core Architectural Insight）
如使用者與 PM 指出：**重新匯入時的清空與重建範圍，不應是粗暴的全域 `deleteAll()`，而應精確鎖定在「本次匯入檔案所涉及的特定股票代碼（Symbol）」維度！**

- **快照表（Snapshot）**：依日期覆寫（`deleteByRecordDate`），負責守護歷史資產連續性與折線圖。
- **在庫庫存（Stock Lots）**：依「使用者 + 股票代號」局部覆寫（`deleteByOwnerUserIdAndSymbolIn`），確保該股票代號下已出售的幽靈批次能被清除（反映最新在庫狀態），同時**完全不誤傷其他股票代號的在庫資產**。
- **獎酬歷史（Benefit History）**：同樣依股票代碼維度局部更新或依業務識別鍵進行冪等性維護。

---

## 核心設計目標（Architecture & Design Goals）

1. **依股票代碼（Symbol-Scoped）限定刪除範圍**：
   動態提取上傳解析結果中所包含的 `symbol` 清單，僅針對目標 `symbol` 執行批次清理與覆寫，保障跨公司持股的持久化隔離。
2. **多租戶安全防護（Multi-Tenant Isolation）**：
   刪除操作必須嚴格綁定當前使用者的 `ownerUserId`（配合 Hibernate Filter 與 MultiUserBaseEntity），防止誤刪同家庭其他成員之持股。
3. **庫存有效性保證（Inventory Correctness）**：
   維持庫存表的「當前狀態語意」——在特定 `symbol` 範圍內重新匯入時，實體世界已賣出之批次能正確自庫存移除，杜絕幽靈持股殘留。
4. **零破壞性與向下相容**：
   若匯入檔案為單一公司股票（現況），行為與原先全覆蓋結果 100% 一致；若為空資料，則執行安全防呆防護，不觸發破壞性清空。

---

## 詳細實作方案（Implementation Plan）

### Phase 1: Repository 層新增 Symbol 範圍限定刪除方法

路徑：`finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/company_stock/etrade/`

#### 1. 在庫持股 Repository 擴充
* **`ETradeEsppStockLotRepository`**
  ```java
  @Modifying
  @Query("DELETE FROM ETradeEsppStockLot e WHERE e.ownerUserId = :ownerUserId AND e.symbol IN :symbols")
  void deleteByOwnerUserIdAndSymbolIn(@Param("ownerUserId") Long ownerUserId, @Param("symbols") Collection<String> symbols);
  ```
* **`ETradeRsuStockLotRepository`**
  ```java
  @Modifying
  @Query("DELETE FROM ETradeRsuStockLot r WHERE r.ownerUserId = :ownerUserId AND r.symbol IN :symbols")
  void deleteByOwnerUserIdAndSymbolIn(@Param("ownerUserId") Long ownerUserId, @Param("symbols") Collection<String> symbols);
  ```

#### 2. 獎酬歷史 Repository 擴充
* **`ETradeEsppPurchaseRepository`**：新增 `deleteByOwnerUserIdAndSymbolIn(...)`
* **`ETradeRsuGrantRepository`**：新增 `deleteByOwnerUserIdAndSymbolIn(...)`
* **`ETradeRsuEventRepository`**：新增 `deleteByOwnerUserIdAndSymbolIn(...)`
* **`ETradeRsuVestScheduleRepository`**：新增關聯 `grantNumber` 或 `symbol` 之局部刪除方法

---

### Phase 2: Service 層解析結果處理邏輯重構

路徑：`ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.CompanyStockJobServiceImpl.java`

#### 1. 持股在庫（`ETradeHoldingsParseResult`）處理重構
```java
case ETradeHoldingsParseResult parsed -> {
    Long currentUserId = SecurityUtil.getCurrentUserId();

    // 1. 帳戶歷史快照：維持依日期冪等更新，保護折線圖
    if (parsed.getSnapshot() != null) {
        logger.info("Saving E-Trade snapshot record for date: {}", parsed.getSnapshot().getRecordDate());
        repository.deleteByRecordDate(parsed.getSnapshot().getRecordDate());
        repository.save(Objects.requireNonNull(parsed.getSnapshot()));
    }

    // 2. 在庫持股明細：依本次上傳檔案中的股票代碼動態局部更新
    List<BaseStockLot> lots = parsed.getLots();
    if (lots != null && !lots.isEmpty()) {
        Set<String> targetSymbols = lots.stream()
                .map(BaseStockLot::getSymbol)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        logger.info("Scoped overwrite for E-Trade stock lots for symbols: {} ({} lots parsed)",
                targetSymbols, lots.size());

        // 僅刪除本次匯入所涵蓋之股票代碼！
        esppStockLotRepository.deleteByOwnerUserIdAndSymbolIn(currentUserId, targetSymbols);
        rsuStockLotRepository.deleteByOwnerUserIdAndSymbolIn(currentUserId, targetSymbols);

        List<ETradeEsppStockLot> esppLots = new ArrayList<>();
        List<ETradeRsuStockLot> rsuLots = new ArrayList<>();
        for (BaseStockLot lot : lots) {
            switch (lot) {
                case ETradeEsppStockLot espp -> esppLots.add(espp);
                case ETradeRsuStockLot rsu -> rsuLots.add(rsu);
                default -> {}
            }
        }
        if (!esppLots.isEmpty()) {
            esppStockLotRepository.saveAll(esppLots);
        }
        if (!rsuLots.isEmpty()) {
            rsuStockLotRepository.saveAll(rsuLots);
        }
    } else {
        logger.warn("Parsed lots are empty, skipping stock lots deletion to prevent accidental wipeout");
    }

    // 3. 現金交易：維持依存在性防重複插入
    ...
}
```

#### 2. 獎酬歷史（`ETradeBenefitHistoryParseResult`）處理重構
* 自 `parsed.getRsuGrants()`、`parsed.getEsppPurchases()` 等集合動態提煉 `symbols`。
* 僅針對涉及之 `symbols` 進行歷史紀錄清空重寫，避免跨雇主歷史被刷除。

---

### Phase 3: 前端 UI 跨公司持股檢視體驗（Future UI Integration）

配合 [Plan 15](Plan-15-etrade-stock-lot-endpoints-and-ui.md) 的持股管理介面，前端進行多 Symbol 支援：
1. **Symbol 篩選器（Filter Tabs / Select）**：
   在 Stock Lots 頁面中，頂部提供 `All Symbols`、`NVDA`、`GOOG` 等標籤切換，清晰呈現不同公司的持股分布。
2. **總資產分項摘要**：
   依 Symbol 分組彙總當前市值與浮動損益，支援多雇主資產健康度檢視。

---

## 單元測試與驗證策略（Testing Strategy）

### 1. 核心業務測試案例
1. **跨公司持股保留驗證（Cross-Symbol Isolation）**：
   - 資料庫原先存有使用者持有之 `GOOG` ESPP 與 RSU 批次。
   - 匯入一份只包含 `NVDA` 的 holdings 檔案。
   - **斷言**：匯入後資料庫中 `NVDA` 批次正確寫入，而原有的 `GOOG` 批次完整無缺、未被刪除。
2. **同公司庫存更新驗證（In-Symbol Inventory Refresh）**：
   - 資料庫原先存有 `NVDA` 批次 A 與 批次 B。
   - 在實體世界中賣出批次 A，重新匯入只包含批次 B 的 `NVDA` 檔案。
   - **斷言**：匯入後批次 A 被成功清除，批次 B 保留，無幽靈庫存。
3. **多租戶隔離驗證（Multi-Tenant Safety）**：
   - 使用者 A 與使用者 B 皆持有 `NVDA` 股票。
   - 使用者 A 重新匯入 `NVDA`。
   - **斷言**：使用者 B 之 `NVDA` 批次不受任何影響。
4. **空檔防呆測試（Empty Safeguard）**：
   - 傳入空 lots 之結果，確保不執行破壞性全刪除。

---

## 結論與價值評估（Value & Impact）

* **架構健全性**：消除「全域 `deleteAll()`」帶來的潛在資料抹除風險，使庫存同步模型符合真正企業級多帳戶/多股票之業務語意。
* **開發時機評估**：因目前使用者實際生活情境僅持有單一公司股票，本計畫屬於**防禦型架構升級（Defensive Architectural Upgrade）**，評估於多公司持股情境浮現或配合 Plan 15 UI 重構時推進實作。
