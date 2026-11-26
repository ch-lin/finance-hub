# [LOW] Re-verification (2026-08-31): the combinatorial-explosion cap fix is genuinely live, but the "fully utilizing the extracted MortgageEarlyRepaymentSolver" bonus claim is false — that component is dead code

## Status: PARTIALLY_FIXED — the primary DoS fix is real and correctly implemented; a secondary claim
in the fix's own writeup does not match current source

## Relation to original finding
Re-verifies [Fixed-27-force-grid-no-combinatorial-explosion-cap.md](Fixed-27-force-grid-no-combinatorial-explosion-cap.md),
whose "[Fixed] Implementation Summary" (lines 157-193) claims two things: (1) a hard search-space cap
on `FORCE_GRID`, and (2) that `GridSearchEngine` was "upgraded... fully utilizing the extracted
`MortgageEarlyRepaymentSolver`" for 1D mortgage optimization (final parenthetical note, line 193).

## What was actually fixed (confirmed real — primary claim)
[GlobalOptimizerServiceImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)
lines 58-64: `FORCE_GRID` now throws `OptimizationNotSupportedException` when
`calculateSearchSpace(seed) > OptimizationConstants.MAX_GRID_SEARCH_SPACE` (1000), correctly mapped to
a 400 by `WebGlobalExceptionHandler`.

[GridSearchEngine.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)
lines 127-131: a second, independent hard abort at `MAX_GRID_ENGINE_COMBINATIONS` (5000) inside
`generateCombinations` itself — genuine defense-in-depth, exercised by
`GridSearchEngineTest.testOptimize_ThrowsException_WhenCombinationsExceedHardCap`. The original
~246,016-combination self-inflicted-DoS scenario is now blocked by two independent layers. This part
of the fix is solid.

## What remains false (evidence)

The Implementation Summary's closing note claims:
> "we also discovered and fixed a latent defect where multi-dimensional mortgage optimizations were
> silently bypassed by the grid search. We actively closed this loophole by upgrading
> `GridSearchEngine` to support a dedicated 1D mortgage full-repayment search... fully utilizing the
> extracted `MortgageEarlyRepaymentSolver`."

A repo-wide grep for `MortgageEarlyRepaymentSolver`/`MortgageEarlyRepaymentSolverImpl` finds these
classes referenced **only** by their own definition files and by
`MortgageEarlyRepaymentSolverImplTest.java` — they are never injected into
`GlobalOptimizerServiceImpl`, `GridSearchEngine`, or any Spring-managed controller/service. There is
no `@Autowired`/constructor-injection site anywhere outside the class's own test. The classes exist,
compile, and have their own passing unit tests, but they are orphaned — nothing in the live request
path ever calls them.

Instead, `GridSearchEngine`'s constructor (lines 20-34) and its `generateCombinations` method (step 4,
lines 111-126) reimplement the same min/max-mortgage-repayment-year logic **inline**, independently of
the extracted solver class. The 1D mortgage grid-search capability the summary describes is real and
does exist in `GridSearchEngine` — it just isn't implemented via the claimed extracted component; it's
duplicated logic sitting alongside a second, unused implementation of the same thing.

A minor, immaterial secondary note: `calculateSearchSpace`'s mortgage-option estimate (line 180,
`(endYear - currentYear + 1) + 1`) undercounts by one relative to what `GridSearchEngine` actually
generates. This has no practical impact given the hard 5000-combination backstop in
`GridSearchEngine` itself independently catches any undercount.

## Failure scenario
No user-facing failure scenario follows from this specific gap — the DoS-prevention behavior works
correctly regardless of which code path performs the 1D mortgage search. The risk here is purely
maintenance/architectural: a future developer relying on the writeup's claim that
`MortgageEarlyRepaymentSolver` is "fully utilized" by the grid engine would look in the wrong place
when debugging or extending 1D mortgage search behavior, and the dead `MortgageEarlyRepaymentSolver`/
`MortgageEarlyRepaymentSolverImpl` classes (plus their dedicated test file) are unreferenced
maintenance burden that will silently drift out of sync with the inline logic they were meant to
replace.

## Suggested direction (not required by this review, noted for scoping a fix)
Either (a) actually wire `GridSearchEngine` to delegate to `MortgageEarlyRepaymentSolver` for its 1D
mortgage search step, removing the duplicated inline logic, or (b) delete the orphaned
`MortgageEarlyRepaymentSolver`/`MortgageEarlyRepaymentSolverImpl`/their test if the inline
implementation in `GridSearchEngine` is intended to be the permanent home for this logic. Either way,
correct the Implementation Summary's claim so future reviewers don't rely on it.

## [Fixed] Implementation Summary (2026-08-31)
Status updated to **FULLY_FIXED**. We chose **Option (b)**: deleting the orphaned `MortgageEarlyRepaymentSolver` entirely.

### Architectural Decision
Upon deeper analysis, we determined that wiring the 1D `MortgageEarlyRepaymentSolver` into the engine (Option a) would be mathematically flawed. The solver attempts a greedy, 1D sequential optimization for the mortgage repayment year by holding all other variables constant. However, early mortgage repayment is tightly coupled with other financial decisions (e.g., maximizing NISA contributions—cash used to repay a mortgage cannot be invested). 

To find the true global optimum, the mortgage repayment year *must* be evaluated combinatorially alongside NISA and retirement age inside the N-dimensional `GridSearchEngine`. The inline logic currently in `GridSearchEngine.generateCombinations()` correctly treats the mortgage year as a combinatorial dimension, rather than prematurely resolving it.

Therefore, the extracted `MortgageEarlyRepaymentSolver` was not just unused, but represented an incorrect greedy strategy for this domain. 

### Actions Taken
1. **Deleted Dead Code**: Completely removed `MortgageEarlyRepaymentSolver`, `MortgageEarlyRepaymentSolverImpl`, and `MortgageEarlyRepaymentSolverImplTest.java`.
2. **Preserved Combinatorial Grid**: Kept the inline mortgage boundary generation logic inside `GridSearchEngine` as its permanent and mathematically correct home.
3. **Corrected Claim**: The original Implementation Summary's claim that the solver was "fully utilized" was indeed false and has been superseded by this architectural correction.
4. **Grid Search Performance Refactoring**: While retaining the combinatorial logic, we completely refactored `GridSearchEngine` to use **on-the-fly evaluation** rather than storing all combinations in memory. This reduces the memory footprint from O(N) to O(1) and significantly reduces Garbage Collection overhead during large searches.

**@Claude: Please re-review the codes to confirm:**
**1. The dead code has been eliminated and the architectural direction is sound.**
**2. The `GridSearchEngine.java` memory optimization (O(1) on-the-fly evaluation and array-wrapper state passing) is implemented correctly and safely.**
