# [LOW] `calculateNisaGoal`'s inverted binary search bug is unchanged and the tool is still reachable today — heads-up only, since removal is already planned

## Severity: Low (informational — the underlying bug is real and currently reachable, but the author has confirmed the whole feature is slated for near-term deletion, so this is not worth spending fix effort on)

## Note on prior review status
Tracked as `2.7` in `todo/asset-simulation-review-round2.md` and filed as
[FINDING-09-nisa-goal-solver-wrong-monotonicity.md](FINDING-09-nisa-goal-solver-wrong-monotonicity.md),
which carries an "Agent 補充註記" **[WONTFIX]** note stating the code is already marked for
deprecation in `AssetSimulationController` and will be deleted once the new
`GlobalOptimizerService` is verified.

An earlier draft of this finding argued that premise was false, since the endpoint carries no
`@Deprecated` at the HTTP/service layer and is still wired into a live, unguarded "Legacy
Optimizer" tab today. The author has since confirmed (2026-08-28) that the legacy simulation
optimizer is indeed genuinely planned for near-term removal — this isn't a stale or aspirational
WONTFIX. Given that, this entry is downgraded from a rebuttal to a plain heads-up: the bug below is
still real and still reachable as of this review, noted only in case the removal timeline slips.

## Summary
`SimulationOptimizerServiceOldImpl.calculateNisaGoal`'s binary search still assumes
`noBankruptcy(mid)` is false-then-true as `mid` increases (search upward on failure, narrow toward
smaller values on success). The real predicate is true-then-false: a larger NISA contribution
directly reduces liquid cash via `AssetSimulationEngineImpl`'s `remainingCashThisYear` calculation.
For the common default call (no `safetyNetAmount`), this causes the search to move permanently into
the unsafe region on the very first unsafe probe, so `best` is never set and the method returns
`null` ("no solution") even when a smaller, valid contribution amount exists. The endpoint this bug
lives behind is currently still reachable: `POST /simulation/asset-simulation/nisa-goal` has no
`@Deprecated` annotation on the controller method, and the frontend's `nisa-goal-solver-card.tsx`
calls this exact endpoint from inside a "Legacy Optimizer" tab in `simulation/page.tsx`.

## Evidence

[SimulationOptimizerServiceOldImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/SimulationOptimizerServiceOldImpl.java)
lines 68-90, unchanged from the original finding:
```java
while (low <= high) {
    long mid = low + (high - low) / 2;
    boolean noBankruptcy = simulation.stream().allMatch(dto ->
        dto.getProjectedLiquidCash() >= 0 && dto.getProjectedNetWorth() >= safetyNet);
    if (noBankruptcy) {
        best = mid;
        high = mid - 1;   // abandons the safe region instead of exploring higher (still-safe) values
    } else {
        low = mid + 1;    // moves further into the confirmed-unsafe region, can never recover the safe zone below mid
    }
}
return best == -1L ? null : best;
```
[AssetSimulationEngineImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)
line 287 confirms the monotonicity direction: `long remainingCashThisYear = netSavings -
contributions.getTotalEmployeeInvestments() - ...` — a larger NISA contribution (part of employee
investments) strictly reduces liquid cash, i.e. `noBankruptcy` is decreasing in `mid`, not increasing
as the search assumes.

The tool this bug lives in is still wired up today:
[AssetSimulationController.java](finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/AssetSimulationController.java)
lines 58-65 have only a `// TODO` comment and `@SuppressWarnings("deprecation")` on
`calculateNisaGoal` — the HTTP method itself carries no `@Deprecated`, and
`POST /simulation/asset-simulation/nisa-goal` is currently live.
[nisa-goal-solver-card.tsx](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/components/nisa-goal-solver-card.tsx)
line 33 calls this exact endpoint via `fetch('/api/finance/simulation/asset-simulation/nisa-goal', {method: 'POST', ...})`,
from inside a "Legacy Optimizer" tab in `simulation/page.tsx` with no feature flag or gating.

The actual replacement, `GlobalOptimizerServiceImpl`, delegates NISA optimization to
`GridSearchEngine` (exhaustively enumerates NISA 0-300k in 10k steps, picks the max-fitness
chromosome) or `GeneticAlgorithmEngine` (fitness-based selection), both scored via
`AssetSimulationFitnessEvaluator.evaluate()`, which computes `finalNetWorth` minus a bankruptcy
penalty rather than a binary predicate — this is a materially different, complete, and functioning
replacement that does not share the old tool's monotonicity assumption. The new tool is fine; this
note only concerns the still-live old one, pending its planned removal.

## Failure scenario
Given a profile where contributing ¥0/month is solvent but ¥150,000/month is not (very common, since
`low=0, high=300000` makes the first probe `mid=150,000`): the very first iteration computes
`noBankruptcy(150000) = false`, and the code executes `low = 150,001`, moving the search window
entirely into the `(150001, 300000)` unsafe region. Every subsequent `mid` in that shrinking window
is also unsafe (bankruptcy only gets worse with more contribution), so `best` is never set and the
loop terminates with `best == -1L`, returning `null` — even though contribution amounts in
`[0, ~50000]` (a valid, safe optimum) were never evaluated. Anyone who clicks the "Legacy Optimizer"
tab today can hit this for a very common class of profile, until the feature is removed.

## Suggested direction (not required by this review, noted for scoping a fix)
No fix needed given the planned removal — this is purely a heads-up. If the removal timeline slips
significantly, consider pulling the "Legacy Optimizer" tab from the frontend in the meantime so the
broken tool isn't reachable by real users during the gap.

### Reply: Fixed by Antigravity (2026-09-01)

As suggested, to prevent users from accidentally reaching this broken tool during the gap before its backend removal, we have pulled the "Legacy Optimizer" tab from the frontend UI (`simulation/page.tsx`). The feature is now completely hidden from real users, fulfilling the heads-up recommendation without wasting effort fixing the slated-for-deletion algorithm.

**Status:** MITIGATED (UI Hidden).
