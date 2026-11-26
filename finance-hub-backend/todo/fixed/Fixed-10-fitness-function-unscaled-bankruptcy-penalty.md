# [MEDIUM] Fixed, unscaled bankruptcy-penalty constant in the GA fitness function can be outweighed by a large net-worth gap between candidates

## Severity: Medium (the optimizer can select and recommend a plan it internally knows goes bankrupt, over a genuinely solvent alternative)

## Summary
`AssetSimulationFitnessEvaluator.evaluate()` penalizes each bankrupt year by a flat
`1,000,000,000` plus 2x the deficit, with no scaling relative to the simulation's actual net-worth
magnitude. Because `finalNetWorth` is unbounded (driven by user-configurable growth rates, existing
asset holdings, salary, NISA/iDeCo contributions, and simulation horizon — none of which are capped
anywhere in `AssetSimulationEngineImpl`), candidate chromosomes can differ in final net worth by well
over that penalty amount. A chromosome with one small late-horizon bankruptcy year can therefore
score strictly higher than a fully solvent lower-net-worth chromosome, and both
`GeneticAlgorithmEngine`'s elitism/tournament selection and `GridSearchEngine`'s best-tracking select
purely by this unguarded numeric score.

This directly contradicts the fitness evaluator's own documented contract:
`FitnessEvaluator`'s javadoc states "A score that leads to bankruptcy should return a large negative
number" — the current implementation can instead return a strongly *positive* score for a bankrupt
chromosome. It is also a behavioral regression from the predecessor implementation
(`SimulationOptimizerServiceOldImpl`, still present with a pending-deletion TODO), which treated any
negative `projectedLiquidCash` as a **hard disqualifier**, not a soft penalty.

## Evidence

[AssetSimulationFitnessEvaluator.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java) lines 62-71:
```java
double penalty = 0;
for (AssetSimulationYearlyResult yearly : simulation) {
    if (yearly.getProjectedLiquidCash() < 0) {
        penalty += 1_000_000_000L + Math.abs(yearly.getProjectedLiquidCash()) * 2;
    }
}
return finalNetWorth - penalty;
```
`finalNetWorth` (`AssetSimulationYearlyResult.projectedNetWorth`) is a raw, uncapped yen value
computed purely from summed balances.

## Failure scenario
Two candidate chromosomes are evaluated in the same GA run. Chromosome A retires very late,
accumulating decades of extra salary/investment growth so `finalNetWorth = 1,300,000,000` yen, but
has exactly one late-horizon year where liquid cash dips to `-200,000` (e.g. from a one-off life
event) — its score is `1,300,000,000 - (1,000,000,000 + 400,000) = 299,600,000`. Chromosome B
retires earlier and is fully solvent every year with `finalNetWorth = 250,000,000` — its score is
`250,000,000` (penalty=0). Because `299,600,000 > 250,000,000`, the GA's tournament selection and
elitism will prefer and can ultimately return Chromosome A — a plan the fitness function itself
knows goes bankrupt — over the genuinely solvent Chromosome B, purely because the flat penalty
constant is small relative to the achievable net-worth spread.

## Suggested direction (not required by this review, noted for scoping a fix)
Make any bankrupt-year penalty dominate regardless of net-worth magnitude — e.g. return a large
negative sentinel score (or `-Double.MAX_VALUE`-scale value) whenever any year is bankrupt, rather
than `finalNetWorth - penalty`, restoring the disqualification semantics the interface's own javadoc
already documents and that the predecessor implementation already enforced.

---

# Fix Unscaled Bankruptcy Penalty in Fitness Function

## Goal
Fix a logical flaw in the genetic algorithm's fitness evaluator where a fixed bankruptcy penalty can be outweighed by an uncapped final net worth. We will implement a "death penalty" (a hard negative score) for any plan that goes bankrupt, ensuring that any solvent plan always outscores a bankrupt plan, while still allowing the genetic algorithm to differentiate between bankrupt plans by adding the deficit to the negative score.

## Proposed Changes

### [MODIFY] [AssetSimulationFitnessEvaluator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java)

**1. 新增常數 (Death Penalty Base)**
在類別最上方新增一個 `private static final long` 常數，並附上註解說明其目的：
```java
    // A massive negative base score (death penalty) applied to any plan that results in bankruptcy.
    // This guarantees that any solvent plan will strictly outscore a bankrupt plan, preventing the 
    // optimizer from recommending plans that run out of cash.
    private static final long BANKRUPTCY_DEATH_PENALTY_BASE = -1_000_000_000_000L;
```

**2. 修改 `evaluate()` 方法**
取代原本的 62-71 行邏輯：
```java
        double deficit = 0;

        for (AssetSimulationYearlyResult yearly : simulation) {
            if (yearly.getProjectedLiquidCash() < 0) {
                // 累加破產缺口
                deficit += Math.abs(yearly.getProjectedLiquidCash());
            }
        }

        if (deficit > 0) {
            // 只要有破產，就回傳極大的負分（一票否決）減去缺口
            return BANKRUPTCY_DEATH_PENALTY_BASE - deficit;
        }

        return finalNetWorth;
```

---

## Resolution Report (For Claude Review)
Hello Claude,

The issue regarding the unscaled bankruptcy penalty has been fixed. We have implemented your exact recommendation of enforcing a hard disqualifier for bankrupt plans ("Death Penalty" pattern). 

### Changes Implemented:
1. **Introduced a "Death Penalty" Constant**: 
   Added `BANKRUPTCY_DEATH_PENALTY_BASE = -1_000_000_000_000L` to `AssetSimulationFitnessEvaluator.java`. This extremely large negative value acts as an absolute disqualifier, guaranteeing it cannot be outweighed by any realistic positive `finalNetWorth`.
   
2. **Refactored `evaluate()` logic**:
   Instead of the soft penalty subtraction (`finalNetWorth - penalty`), the method now evaluates as follows:
   - It accumulates the total deficit across any bankrupt years.
   - If `deficit > 0`, it returns `BANKRUPTCY_DEATH_PENALTY_BASE - deficit`. 
   - We subtracted the deficit rather than returning a flat negative score so that the Genetic Algorithm retains a continuous fitness gradient to evolve out of deep bankruptcy towards solvency (a plan with a smaller deficit is mathematically strictly better than a plan with a larger deficit, but both are still vastly worse than any solvent plan).
   - If no bankruptcy occurs (`deficit == 0`), it simply returns `finalNetWorth`.

### Code snippet of the fix:
```java
    private static final long BANKRUPTCY_DEATH_PENALTY_BASE = -1_000_000_000_000L;

    // ... inside evaluate() ...
    
    double finalNetWorth = finalYear.getProjectedNetWorth();
    double deficit = 0;

    for (AssetSimulationYearlyResult yearly : simulation) {
        if (yearly.getProjectedLiquidCash() < 0) {
            // Accumulate bankruptcy deficit
            deficit += Math.abs(yearly.getProjectedLiquidCash());
        }
    }

    if (deficit > 0) {
        // Any bankruptcy results in a massive negative fitness score (death penalty).
        return BANKRUPTCY_DEATH_PENALTY_BASE - deficit;
    }

    return finalNetWorth;
```

**Please review the above changes and confirm if this successfully resolves the vulnerability.**
