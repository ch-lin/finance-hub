# [MEDIUM] GA mutation clamps retirementAge to the pension/iDeCo age range (60-75) instead of the intended 50-80 range, silently forcing early-retirement seeds up to 60

## Severity: Medium (early-retirement scenarios are never actually explored/refined by the GA optimizer, producing an inconsistent optimum depending purely on which engine happened to be selected)

## Summary
`Chromosome.mutate()` reuses the same `MIN_AGE=60`/`MAX_AGE=75` constants for `retirementAge`,
`pensionStartAge`, and `idecoStartAge`. But `GridSearchEngine` — the alternate optimizer used for
smaller search spaces — explicitly iterates `retirementAge` over **50-80**, and
`GlobalOptimizerServiceImpl.calculateSearchSpace` explicitly comments "50 to 80 (31 options)" for
retirementAge vs. "60 to 75 (16 options)" for pension/iDeCo — proving the codebase's own intended
range for retirement age differs from the pension/iDeCo range. The GA path's reuse of the narrower
constants silently forces any early-retirement seed value below 60 up to exactly 60 on its very
first mutation.

## Evidence

[Chromosome.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java) lines 15-16, 52:
```java
private static final int MIN_AGE = 60;
private static final int MAX_AGE = 75;
...
retirementAge = Math.min(MAX_AGE, Math.max(MIN_AGE, retirementAge + (random.nextInt(5) - 2)));
```
Lines 55/58 reuse the identical constants for `pensionStartAge`/`idecoStartAge`.

[GridSearchEngine.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java) lines 61-62:
```java
if (!seed.isRetirementAgeLocked()) {
    for (int val = 50; val <= 80; val++) { ... }
}
```
— a distinct, wider bound than the 60-75 used for pension/iDeCo at lines 74/86 in the same file.

`FinanceHubConfig.targetRetirementAge`/`UserProfile.targetRetirementAge` are plain `Integer` fields
with no `@Min`/`@Max`; the only request-level validation (`CreateFinanceHubConfigRequest`) has
`@Min(value = 0)`, so an early-retirement value like 55 is a legitimate, reachable configuration.
`GlobalOptimizerServiceImpl.calculateSearchSpace` returns `Long.MAX_VALUE` whenever mortgage
optimization is enabled ("Force GA if mortgage is involved") — routing execution to
`GeneticAlgorithmEngine` instead of `GridSearchEngine` for a very common combination of optimizer
toggles.

## Failure scenario
A user configures `targetRetirementAge=55` and requests joint optimization of retirement age plus
mortgage (which forces GA outright) or enough other dimensions to exceed the 1000-combination
GridSearch threshold. In `GeneticAlgorithmEngine.initializePopulation`, 49 of 50 initial individuals
clone the seed and call `variant.mutate()` twice. `Chromosome.mutate()` computes
`retirementAge = Math.min(75, Math.max(60, 55 + (random.nextInt(5)-2)))`; since `55+delta` is at
most 57, `Math.max(60, ...)` forces the value to exactly 60 on essentially every mutation. The GA can
therefore never explore or refine any retirement age in the legitimate 50-59 range, even though
`GridSearchEngine` — used for the very same feature when the search space is small — treats 50-80 as
the valid domain, producing an inconsistent, incorrectly narrowed optimum (e.g. recommending
retirement at 60 while never checking whether 55 is also bankruptcy-free) purely depending on which
engine happened to be selected for that request.

## Suggested direction (not required by this review, noted for scoping a fix)
Give `retirementAge` its own `MIN_AGE`/`MAX_AGE` constants (50/80) in `Chromosome.mutate()`,
distinct from the pension/iDeCo age constants, matching `GridSearchEngine`'s already-established
range for the same field.

---

# 修正 GA 引擎退休年齡突變範圍異常 (Fixed-09)

修復 Genetic Algorithm 在突變退休年齡時，誤用年金/iDeCo年齡常數的問題。並依據指示，將原本散落在各處的年齡上下限常數提取出一個獨立的常數類別，供所有 Optimizer 引擎共用。

## Proposed Changes

### 1. 新增共用常數類別

建立一個全新的靜態常數類別，清楚定義每一種年齡的合理搜尋範圍。

#### [NEW] [OptimizationConstants.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java)
```java
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core;

public final class OptimizationConstants {
    private OptimizationConstants() {}

    // 退休年齡的合理範圍
    public static final int MIN_RETIREMENT_AGE = 50;
    public static final int MAX_RETIREMENT_AGE = 80;

    // 年金合法領取年齡範圍
    public static final int MIN_PENSION_AGE = 60;
    public static final int MAX_PENSION_AGE = 75;
    
    // iDeCo 合法領取年齡範圍
    public static final int MIN_IDECO_AGE = 60;
    public static final int MAX_IDECO_AGE = 75;
}
```

### 2. 修正染色體突變邏輯 (GA 引擎)

移除舊有錯誤的共用常數，並改用新定義的各自常數。

#### [MODIFY] [Chromosome.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java)
- **刪除** `private static final int MIN_AGE = 60;` 和 `MAX_AGE = 75;`
- **修改** `mutate` 方法，為不同的屬性套用對應的常數：
  - `retirementAge` 套用 `OptimizationConstants.MIN_RETIREMENT_AGE` 和 `MAX_RETIREMENT_AGE`。
  - `pensionStartAge` 套用 `OptimizationConstants.MIN_PENSION_AGE` 和 `MAX_PENSION_AGE`。
  - `idecoStartAge` 套用 `OptimizationConstants.MIN_IDECO_AGE` 和 `MAX_IDECO_AGE`。

### 3. 將常數套用至 Grid Search 引擎

統一系統常數，消除 `GridSearchEngine` 中的硬編碼 (Hardcode)。

#### [MODIFY] [GridSearchEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)
- **修改** `generateCombinations` 方法中原本寫死的數字 (`50`, `80`, `60`, `75`)：
  - 取代 `for (int val = 50; val <= 80; val++)` 為 `OptimizationConstants` 裡的對應常數。
  - 取代 `for (int val = 60; val <= 75; val++)` 為 `OptimizationConstants` 裡的對應常數。

## Verification Plan

### Manual Verification
透過 code review 確認 `Chromosome.java` 和 `GridSearchEngine.java` 兩邊使用的搜尋範圍上下限，已經一致且正確對應。由於屬於底層演算法常數修正，且已經符合 Claude Review 指出之最佳實踐方向，只要編譯並確認測試即可。

---

## Agent 補充註記
**[FIXED]** 已修復。將所有年齡邊界常數提取至獨立的 `OptimizationConstants` 類別，供所有 Optimizer 引擎共用。現在 GA 引擎在突變時會針對不同欄位正確套用各自專屬的上下限，提早退休 (如 50-59 歲) 的種子不會再被強制拉升到 60 歲。
