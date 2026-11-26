# Fixed 87-01 實作計畫：最佳化引擎「兩層防護」防堵髒資料

## 🎯 變更目標
為了解決最佳化引擎（GA / Grid Search）不慎讀取前端 Sandbox 專用參數（`baseOverrides`）所導致的邏輯錯誤（如 Fixed-87 所述，一筆房貸卡掉另一筆房貸），我們將實作「兩層防護」機制。從 API 入口層到核心業務層，徹底切斷最佳化引擎對 `baseOverrides` 的依賴。

## 📝 Proposed Changes

### Layer 1: 大門防護 (Controller Mapper)

#### [MODIFY] `GlobalOptimizerMapper.java`(file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java)
- 在 `toCommand` 方法加上 `@Mapping(target = "baseOverrides", ignore = true)`。
- **目的**：在 API 請求轉換成內部 Command 時，直接把 `baseOverrides` 丟掉。這樣可以確保後面的核心引擎連碰到髒資料的機會都沒有，且不會影響 Sandbox 專用的 `AssetSimulationMapper`。

### Layer 2: 核心防護 (Service)

#### [MODIFY] `GlobalOptimizerServiceImpl.java`(file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)
- **清理 `buildSeedChromosome` 中的 5 個維度**：
  - **退休年齡 / 年金 / iDeCo / NISA**：刪除 `xxxOverride != null` 的第一優先權判斷，讓邏輯單純只看「DB Profile 開關」與「系統全域預設值」。
  - **房貸提前還款**：刪除讀取 `baseOverrides` 的邏輯。開啟最佳化時，直接從 `baseData.getAllMortgages()` 建立所有房貸的空地圖；未開啟最佳化時，直接給予空地圖（代表慢慢還到底）。

---

## 🧪 Verification Plan

### 自動化測試 (Automated Tests)
- 確保既有的最佳化測試 `GlobalOptimizerServiceTest` 能夠順利通過。

### 報告產出
- 修正完成後，將實作細節與兩層防護的邏輯總結，附加到 `Fixed-87.md` 底部作為報告。
