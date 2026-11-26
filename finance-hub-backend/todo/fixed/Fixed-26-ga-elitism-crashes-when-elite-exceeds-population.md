# [MEDIUM] GA elitism throws IndexOutOfBoundsException when gaEliteSize > gaPopulationSize — both freely user-configurable with no cross-field validation

## Severity: Medium (an unhandled exception fails the entire optimizer request for any config combination where elite size exceeds population size, with no validation preventing that combination from being saved)

## Summary
`GeneticAlgorithmEngine` reads `gaPopulationSize` and `gaEliteSize` directly from `FinanceHubConfig`
with no validation that `eliteSize <= populationSize`. The elitism step loops
`for (int i = 0; i < eliteSize; i++) nextGen.add(cloneChromosome(scoredPopulation.get(i)...))`, but
`scoredPopulation` only ever has `populationSize` entries. If `gaEliteSize` exceeds
`gaPopulationSize`, this throws `IndexOutOfBoundsException` and the whole `optimize()` call fails.

## Evidence

[GeneticAlgorithmEngine.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngine.java)
lines 99-102:
```java
// Elitism
for (int i = 0; i < eliteSize; i++) {
    nextGen.add(cloneChromosome(scoredPopulation.get(i).chromosome()));
}
```
`FinanceHubConfig` exposes `gaPopulationSize`/`gaEliteSize` as freely settable `Integer` fields (see
`FinanceHubConfigController`/`CreateFinanceHubConfigRequest`/`UpdateFinanceHubConfigRequest`) with
zero validation constraints or cross-field checks.

## Failure scenario
A config is saved with `gaPopulationSize=5` and `gaEliteSize=10` (or the default `gaEliteSize=5`
combined with a user-supplied `gaPopulationSize=3`). On the first generation of
`GeneticAlgorithmEngine.optimize()`, `scoredPopulation` has only 3-5 entries, and the elitism loop
calls `scoredPopulation.get(i)` for `i` up to `eliteSize-1`, throwing
`IndexOutOfBoundsException` and causing the entire `/simulation/optimizer/{targetAge}` request
(strategy `AUTO` with a large search space, or `FORCE_GA`) to fail with an unhandled exception.

## Suggested direction (not required by this review, noted for scoping a fix)
Add a validation constraint on `FinanceHubConfig` (e.g. a cross-field check rejecting
`gaEliteSize > gaPopulationSize` at save time), and/or clamp `eliteSize` to
`Math.min(eliteSize, scoredPopulation.size())` defensively inside `GeneticAlgorithmEngine` before the
elitism loop.

---
## 狀態追蹤 (2026-08-30)
**狀態：已決策，準備實作中 (Planned for Implementation)**

**決策方向與架構思維：**
團隊決議**不採用** Claude 建議的「單純報錯阻擋 (Cross-field validation)」或「背後鉗制數值 (Clamping)」，因為這兩種做法都有架構與演算法上的缺陷：
1. 若只做 API 報錯，會產生不良的 UX 摩擦，且使用者可能不了解設定的意義。
2. 若做背後鉗制 (例如強制讓 Elite = Population)，會導致下一代沒有空間進行交配與突變，直接扼殺基因多樣性，讓 GA 演算法癱瘓為盲猜。

**最終解決方案 (改為 Ratio 控制)：**
根據 GA 學界實務 (最佳菁英比例通常在 1%~10%)，我們將從資料庫實體 (`FinanceHubConfig`) 到前端 UI，全面將 `gaEliteSize` (絕對數量) 棄用並重構為 **`gaEliteRatio` (百分比)**。
- 系統將在 Domain 與 UI 加上強型別限制：`min: 1, max: 50`。
- `GeneticAlgorithmEngine` 將在運行時動態計算：`eliteSize = Math.max(1, populationSize * (gaEliteRatio / 100))`。

這不僅從根本上保證了 Elite 永遠不可能大於 Population (解決了 Bug 崩潰問題)，同時也保護了演算法的多樣性，防止使用者設定出無效的 GA 參數。

---
## [Fixed] 實作總結與意圖說明 (Completed)

為了貫徹上述「改用百分比控制」的決策，我們在前後端進行了以下防護縱深與重構，每個修改都有其明確的架構意圖：

1. **底層資料完整性防護 (Domain / Database)**
   - **意圖**：確保無論是透過 API 還是直接改動資料庫，都無法寫入不合理的數值。
   - **實作**：於 `FinanceHubConfig.java` 的 `gaEliteRatio` 欄位加上 `@Min(1)` 與 `@Max(50)`。這不僅讓 Hibernate Validator 在寫入前攔截錯誤，更能透過 DDL 自動在 SQL 層級建立 Check Constraint，達成最乾淨的 Domain-Driven Design 防護。

2. **核心演算法動態計算 (Application Logic)**
   - **意圖**：在數學與邏輯上徹底根除 `IndexOutOfBoundsException`，同時保護 GA 多樣性。
   - **實作**：修改 `GeneticAlgorithmEngine`，使其在初始化時利用 `populationSize * (gaEliteRatio / 100.0)` 動態推算絕對菁英數量。這在根本上保證了 `eliteSize` 永遠小於或等於 `populationSize`。

3. **環境變數與設定檔全面升級 (DevOps / Configs)**
   - **意圖**：避免遺留的舊設定 (`gaEliteSize`) 導致系統啟動失敗或套用錯誤參數，維持整體部署環境的一致性 (Big Picture)。
   - **實作**：全面檢索並替換 `application*.properties` 與 `.env.*` 檔案，將所有 `GA_ELITE_SIZE` 替換為 `GA_ELITE_RATIO` 並將預設值改為 10(%)。

4. **第一道防線建立 (Frontend UI / Zod)**
   - **意圖**：阻擋惡意或手滑的輸入，提供即時的 UX 回饋，避免無效請求浪費後端資源。
   - **實作**：在 `hub-ui` 的 `system-config-panel.tsx` 與 `use-finance-configs.ts` 中，將輸入欄位替換為 Ratio，並套用 Zod 的 `.min(1).max(50)` 強驗證。
