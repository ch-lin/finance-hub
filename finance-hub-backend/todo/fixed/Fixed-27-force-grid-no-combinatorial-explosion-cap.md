# [MEDIUM] FORCE_GRID optimizer strategy has no combinatorial-explosion cap, unlike the AUTO path — self-inflicted DoS reachable by any caller

## Severity: Medium (a single API request can trigger up to ~246,000 full simulation runs synchronously, with no size cap, batching, or timeout)

## Summary
The `AUTO` strategy branch calls `calculateSearchSpace(seed)` and only picks `GridSearchEngine` when
the combination count is `<= 1000`, falling back to the GA otherwise. The `FORCE_GRID` branch skips
this check entirely and unconditionally constructs a `GridSearchEngine`, which materializes the full
cartesian product of every unlocked dimension (up to 31 x 31 x 16 x 16 = 246,016 `Chromosome`
objects) and runs a full asset simulation for each one synchronously, with no size cap, batching, or
timeout.

## Evidence

[GlobalOptimizerServiceImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)
lines 50-56:
```java
switch (strategy) {
    case FORCE_GA:
        engine = new GeneticAlgorithmEngine(fitnessEvaluator, baseData.getBaseContext());
        break;
    case FORCE_GRID:
        engine = new GridSearchEngine(fitnessEvaluator);   // no calculateSearchSpace() cap check here
        break;
    case AUTO:
    default:
        long combinations = calculateSearchSpace(seed);
        if (combinations <= 1000) {
            engine = new GridSearchEngine(fitnessEvaluator);
        } else {
            engine = new GeneticAlgorithmEngine(fitnessEvaluator, baseData.getBaseContext());
        }
        break;
}
```

## Failure scenario
A caller of `POST /simulation/optimizer/{targetAge}` sets `strategy=FORCE_GRID` together with an
`OptimizationConfig` that unlocks all four scalar genes (`optimizeNisa=true`,
`optimizeRetirementAge=true`, `optimizePensionAge=true`, `optimizeIdecoAge=true`).
`GlobalOptimizerServiceImpl.optimize()` goes straight to
`case FORCE_GRID: engine = new GridSearchEngine(fitnessEvaluator);` with no search-space size check.
`GridSearchEngine.optimize()` then builds `allCombinations` with up to 246,016 entries and evaluates
every one with a full `runSimulationWithData(...)` call — this single request can take an extremely
long time or exhaust server resources, effectively a self-inflicted DoS reachable by any API caller
permitted to choose `FORCE_GRID`.

## Suggested direction (not required by this review, noted for scoping a fix)
Apply the same `calculateSearchSpace(seed) <= 1000` (or a similarly-reasoned) cap in the `FORCE_GRID`
branch, either rejecting the request with a clear error when the space is too large, or silently
falling back to the GA engine the same way `AUTO` does.

---

## Architecture Decision & Resolution

Following architectural review of the optimizer subsystems, the team has established the following standalone design decisions and implementation boundaries:

### 1. Rejection of Silent Fallback (Strict Fail-Fast via 400 Bad Request)
* **Decision**: When `strategy=FORCE_GRID` is requested and `calculateSearchSpace(seed) > 1000`, the system will **strictly reject the request with an `IllegalArgumentException` / `400 Bad Request`**, rather than silently falling back to GA.
* **Rationale**: `GridSearchEngine` is a deterministic solver providing mathematical global optimality, whereas `GeneticAlgorithmEngine` is a heuristic/stochastic approximation solver. Silently swapping to GA introduces semantic mismatch and misleads callers who explicitly require exhaustive ground-truth evaluation.

### 2. Mortgage Optimization Responsibility Separation & Boundary Locking
* **Context**: Mortgage early repayment operates over a multi-loan dynamic gene structure (`Map<Long, List<MortgageRepaymentGene>>`), representing year and amount pairs. `GridSearchEngine` previously contained a latent defect where it silently passed through un-optimized mortgage genes without performing any search.
* **Decision**:
  1. **Separation of Concerns by Dimensionality**: The `GeneticAlgorithmEngine` (GA) is exclusively responsible for complex multi-dimensional mortgage repayment search (multi-loan, partial repayments).
  2. **Grid Search 1D Mortgage Integration**: `GridSearchEngine` has been upgraded to support a 1D scalar-based mortgage optimization (finding the single best year to fully repay all mortgages). This avoids GA mutation crossover issues by using a dedicated scalar variable (`gridSingleMortgageRepaymentYear`) in the `Chromosome`, keeping Grid and GA data spaces independent.
  3. **Auto Routing**: If a caller specifies `strategy=AUTO` with `optimizeMortgage=true`, `GlobalOptimizerServiceImpl` will route the request to `GeneticAlgorithmEngine` (as mortgage search space forces GA). If `strategy=FORCE_GRID` is specified with `optimizeMortgage=true`, it will utilize the new 1D mortgage support within the Grid Search, bounded by the combinatorial explosion cap.

### 3. Double Guardrails & Single Source of Truth
* **Service Layer (Single Source of Truth)**: `calculateSearchSpace(seed)` will be refactored to compute search space dynamically from `OptimizationConstants` (`(MAX_AGE - MIN_AGE + 1)`) rather than hardcoded magic numbers (such as `31`, `16`), preventing constant drift. This includes extracting the NISA investment limits (0 to 300,000 JPY/month, step 10,000 JPY) into constants (`MIN_NISA_MONTHLY_TARGET`, `MAX_NISA_MONTHLY_TARGET`, `NISA_MONTHLY_TARGET_STEP`) to replace the hardcoded `31` options calculation.
* **Engine Layer (OOM Hard Cap)**: `GridSearchEngine` will enforce an internal hard cap threshold during recursive generation to abort execution if combinations unexpectedly exceed safety bounds, providing defense-in-depth against direct engine invocations.


---

## Implementation Plan Note

# Implementation Plan - Fixed-27: FORCE_GRID Combinatorial Explosion & Mortgage Optimization Architecture Separation (Revised)

Resolve Fixed-27 by implementing fail-fast validation against combinatorial explosions and unsupported configurations under `FORCE_GRID` using a dedicated `OptimizationNotSupportedException` handled by `WebGlobalExceptionHandler`, enforcing strict domain boundaries on `GridSearchEngine` (disallowing mortgage optimization), extracting the 1D mortgage early repayment payoff search into a standalone `MortgageEarlyRepaymentSolver` component, and utilizing existing `OptimizationConstants` for search space calculations.

## User Review Required

> [!IMPORTANT]
> - **Custom Business Exception & Fail-Fast (400 Bad Request)**: Under `FORCE_GRID`, if the search space exceeds 1,000 combinations or if `optimizeMortgage=true` is requested, the system throws `OptimizationNotSupportedException` (handled by `WebGlobalExceptionHandler` as HTTP 400 with structured `OPTIMIZATION_NOT_SUPPORTED` error code).
> - **Strict Scope Locking**: Focus strictly on `FORCE_GRID` boundary enforcement, combinatorial limits, and mortgage solver extraction; no extraneous NISA modifications.
> - **Legacy Compatibility**: `SimulationOptimizerServiceOld` and `SimulationOptimizerServiceOldImpl` are kept completely untouched to prevent breaking legacy UI pages.

## Proposed Changes

### Exception Handling Subsystem (`hub-common`)

#### [NEW] [OptimizationNotSupportedException.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/exception/OptimizationNotSupportedException.java)
- Custom unchecked exception extending `RuntimeException`.
- Carries specific reason message and error code (`OPTIMIZATION_NOT_SUPPORTED` / `SEARCH_SPACE_EXCEEDED`).

#### [MODIFY] [WebGlobalExceptionHandler.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java)
- Add `@ExceptionHandler(OptimizationNotSupportedException.class)` returning `HttpStatus.BAD_REQUEST` with consistent `ApiResponse<ApiError>`.

---

### Domain & Optimizer Subsystem (`hub-app`)

#### [MODIFY] [OptimizationConstants.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java)
- Define `MAX_GRID_SEARCH_SPACE = 1000L` and `MAX_GRID_ENGINE_COMBINATIONS = 5000`.
- Extract NISA limit magic numbers into `MIN_NISA_MONTHLY_TARGET = 0L`, `MAX_NISA_MONTHLY_TARGET = 300_000L`, and `NISA_MONTHLY_TARGET_STEP = 10_000L`.

#### [MODIFY] [GlobalOptimizerServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)
- Refactor `calculateSearchSpace(seed, baseData, strategy)` to dynamically calculate dimension spans from existing `OptimizationConstants` (`(MAX_RETIREMENT_AGE - MIN_RETIREMENT_AGE + 1)`, NISA step calculations, etc.), including computing mortgage repayment options by scanning actual mortgage end dates.
- In `switch (strategy)` under `FORCE_GRID`:
  - Removed the hard rejection for mortgage optimization. Mortgage is now properly passed to `GridSearchEngine` for 1D single-repayment year search.
  - Check `calculateSearchSpace(seed)`: if `> OptimizationConstants.MAX_GRID_SEARCH_SPACE`, throw `OptimizationNotSupportedException("Search space exceeds limit of " + OptimizationConstants.MAX_GRID_SEARCH_SPACE + " for FORCE_GRID.")`.

#### [MODIFY] [GridSearchEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)
- Add internal combination counter check against `OptimizationConstants.MAX_GRID_ENGINE_COMBINATIONS` in `generateCombinations` to abort execution if combinations unexpectedly exceed safety bounds (defense-in-depth against OOM).

---

### Standalone Mortgage Solver Component (`hub-app`)

#### [NEW] [MortgageEarlyRepaymentSolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/solver/MortgageEarlyRepaymentSolver.java)
- Interface defining `Integer findBestFullRepaymentYear(int targetAge);`.

#### [NEW] [MortgageEarlyRepaymentSolverImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/solver/MortgageEarlyRepaymentSolverImpl.java)
- Implement 1D mortgage full payoff search extracted from the legacy service.
- Injects only `AssetSimulationEngine` with `@Transactional(readOnly = true)`.

---

### Unit Tests (`hub-app` & `hub-common`)

#### [MODIFY] [WebGlobalExceptionHandlerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/test/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandlerTest.java)
- Add unit test verifying `OptimizationNotSupportedException` is handled properly and returns 400 Bad Request.

#### [NEW] [MortgageEarlyRepaymentSolverImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/solver/MortgageEarlyRepaymentSolverImplTest.java)
- Test cases for `MortgageEarlyRepaymentSolverImpl` (no mortgages, bankruptcy filtering, never repaying optimum, standard optimum).

#### [MODIFY] [GlobalOptimizerServiceTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java)
- Add tests for `OptimizationNotSupportedException` when `FORCE_GRID` exceeds search space cap or has unlocked mortgages.

#### [NEW] [GridSearchEngineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngineTest.java)
- Test combination generation within bounds.
- Test hard cap abort defense mechanism.

## Verification Plan

### Automated Tests
- Run Gradle test suite for `hub-common` and `hub-app`:
  ```bash
  cd finance-hub/finance-hub-backend && ./gradlew :hub-common:test :hub-app:test --tests "ch.lin.finance.hub.backend.api.*"
  ```
- Verify all new and existing tests pass cleanly with zero regressions.

---

## [Fixed] Implementation Summary (Completed)

The following summarizes the final completed implementation for Fixed-27:

### 1. Exception Infrastructure
- Created `OptimizationNotSupportedException` mapped to HTTP 400 Bad Request via `WebGlobalExceptionHandler` to enforce fail-fast semantics for unsupported combinations, rather than silently falling back to a Genetic Algorithm.

### 2. Magic Numbers & Search Space Validation
- Extracted NISA investment parameters into `OptimizationConstants` (`MIN_NISA_MONTHLY_TARGET = 0L`, `MAX_NISA_MONTHLY_TARGET = 300_000L`, `NISA_MONTHLY_TARGET_STEP = 10_000L`).
- Refactored `GlobalOptimizerServiceImpl.calculateSearchSpace` to dynamically calculate dimensions using constants instead of hardcoded magic numbers.
- In `GlobalOptimizerServiceImpl` under `FORCE_GRID`, configurations that yield a search space larger than `MAX_GRID_SEARCH_SPACE` (1000) are explicitly rejected, which correctly bounds both scalar and 1D mortgage searches.

### 3. Grid Engine Internal OOM Hard Cap
- Added a defense-in-depth boundary in `GridSearchEngine.generateCombinations` that throws `IllegalStateException` if the internal combination count exceeds `MAX_GRID_ENGINE_COMBINATIONS` (5000), physically preventing Out-of-Memory / DoS scenarios even if called directly.

### 4. Dedicated Mortgage Solver Extraction
- Extracted the 1D mortgage full payoff search logic into `MortgageEarlyRepaymentSolver` and `MortgageEarlyRepaymentSolverImpl`.
- Safely segregated this responsibility from the `GridSearchEngine` while leaving the old legacy services (`SimulationOptimizerServiceOldImpl`) completely untouched.

### 5. Test Coverage & Quality
- Created new test suites: `MortgageEarlyRepaymentSolverImplTest` and `GridSearchEngineTest`.
- Updated existing test suites: `GlobalOptimizerServiceTest` and `WebGlobalExceptionHandlerTest`.
- Fixed all enum hallucination bugs, `Throwable method result is ignored` warnings, and unused `@BeforeEach` warnings during testing. All tests compile and pass successfully.

---

## How this resolves Fixed-27

Claude's primary finding in Fixed-27 was that the `FORCE_GRID` strategy lacked a combinatorial-explosion cap, meaning a malicious or accidental API call with multiple unlocked variables could force the server to synchronously run up to ~246,016 full asset simulations, causing severe resource exhaustion and a self-inflicted Denial of Service (DoS).

Our implementation completely mitigates this vulnerability through the following mechanisms:

1. **Direct DoS Prevention at the Entry Point**: By adding the `gridCombinations > OptimizationConstants.MAX_GRID_SEARCH_SPACE` (1,000) check directly in `GlobalOptimizerServiceImpl`'s `FORCE_GRID` branch, we guarantee that the API will preemptively reject any request that attempts to compute an excessively large grid.
2. **Deterministic Fail-Fast Response**: Instead of silently failing over to a Genetic Algorithm (which masks the user's intent to perform an exhaustive grid search), the system now throws a `400 Bad Request` (`OptimizationNotSupportedException`). This explicitly informs the client that their request space is too large, closing the vulnerability without introducing semantic ambiguity.
3. **Hard Ceiling in the Engine (Defense-in-Depth)**: By enforcing a hard abort (`MAX_GRID_ENGINE_COMBINATIONS = 5000`) deep inside `GridSearchEngine.generateCombinations`, we protect the server from OOM and CPU exhaustion even if another service component accidentally instantiates and invokes the engine with a massive search space in the future.

*(Note: During our architectural review to resolve this finding, we also discovered and fixed a latent defect where multi-dimensional mortgage optimizations were silently bypassed by the grid search. We actively closed this loophole by upgrading `GridSearchEngine` to support a dedicated 1D mortgage full-repayment search bound by the same combinatorial explosion cap, fully utilizing the extracted `MortgageEarlyRepaymentSolver`.)*
