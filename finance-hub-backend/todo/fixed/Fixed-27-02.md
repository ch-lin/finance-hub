# [LOW→CLOSED] Re-verification (2026-09-01): dead code deletion confirmed clean; the O(1) streaming refactor is correct and its OOM abort check is verified to still fire before evaluation, not after

## Status: CONFIRMED_FIXED — both the dead-code claim and the memory-refactor claim from the
"[Fixed] Implementation Summary (2026-08-31)" check out against current source

## Relation to original finding
Re-verifies [Fixed-27-01.md](Fixed-27-01.md)'s "[Fixed] Implementation Summary (2026-08-31)",
which claimed (1) `MortgageEarlyRepaymentSolver` and its impl/test were completely deleted, and (2)
`GridSearchEngine` was refactored to on-the-fly/streaming generation (O(1) memory) while preserving
the `MAX_GRID_ENGINE_COMBINATIONS` hard-cap abort.

## What was verified fixed

**1. Dead code deletion is total.** Repo-wide `find`/`grep` for `MortgageEarlyRepaymentSolver` returns
matches only in markdown docs (`Epic PROGRESS.md`, this finding's own files) — zero hits in any `.java`
source or test file. The class, its impl, and its dedicated test are completely gone.

**2. Generation is genuinely O(1)/streaming, not O(N)/list-based.** `GridSearchEngine.java:51-124`,
method `generateCombinations`, is a single recursive function with a `switch(step)` over the 5
dimensions (NISA, retirement age, pension age, ideco age, mortgage year). Each `case` is a `for` loop
that mutates one field on a single, shared, reused `Chromosome` instance in place, then immediately
recurses to `step+1`. There is no `List<Chromosome>` (or any other bulk container) anywhere in the
method — the only `List` usage in the file is unrelated (`cloneChromosome`'s mortgage-gene
bookkeeping). Memory footprint is O(depth) = O(5) stack frames plus one mutable object, not O(N)
combinations.

**3. The OOM abort check fires before evaluation, at the earliest possible point — this is the
highest-priority correctness question and it checks out.** `GridSearchEngine.java:112-115`, the first
statement of the leaf (`default`) case:
```java
if (++evaluatedCount[0] > OptimizationConstants.MAX_GRID_ENGINE_COMBINATIONS) {
    throw new IllegalStateException(...);
}
double score = evaluator.evaluate(current);
```
Because generation and evaluation happen in the same call (there is no separate "build the list, then
iterate" phase), the counter increments and the abort check fires at the exact moment a full
combination is materialized — combination #5001 aborts before its own (expensive)
`evaluator.evaluate()` call even runs, and long before combinations #5002-#246,016 would ever be
constructed. This is not merely "as safe as before" — it is strictly better than a post-hoc
list-size check, since it also skips the costly simulation-evaluation cost for the aborting
combination. `MAX_GRID_ENGINE_COMBINATIONS = 5000` confirmed at `OptimizationConstants.java:26`.

**4. Test coverage is adequate, though not maximally rigorous.** `GridSearchEngineTest.java`:
`testOptimize_EvaluatesCombinations_AndReturnsBest` (lines 51-77) sweeps a 31-value NISA dimension and
asserts the correct optimum is found (proves full single-dimension traversal).
`testOptimize_ThrowsException_WhenCombinationsExceedHardCap` (lines 79-92) unlocks all four scalar
dimensions (31×31×16×16 = 246,016 combinations) and asserts `IllegalStateException` — hand-traced
against the current on-the-fly implementation and confirmed to still exercise the real abort path (not
stale/pre-refactor behavior). A hand-traced small 2×2 example (2 NISA values, all other dimensions
locked) confirms exactly 2 evaluations occur with no skip or duplication. Minor rigor gap: no test
directly asserts the exact total evaluated-combination count or an explicit "no duplicates" invariant
via an exposed counter — the correctness confidence here rests on hand-tracing plus the optimum-value
test, not on a dedicated exhaustiveness assertion. Not required to fix; noted for anyone wanting to
harden this further.

**5. No leftover references.** `GlobalOptimizerServiceImpl.java:65,71` constructs
`new GridSearchEngine(fitnessEvaluator, baseData)`, matching the current constructor signature exactly;
no import or string reference to the deleted solver class remains anywhere in this file.

## Conclusion
Both claims in the "[Fixed] Implementation Summary" are genuine and correctly implemented, including
the trickiest part (verifying the memory-safety refactor didn't silently move the abort check to a
point where it would no longer prevent OOM). No further companion file is needed for this finding.
