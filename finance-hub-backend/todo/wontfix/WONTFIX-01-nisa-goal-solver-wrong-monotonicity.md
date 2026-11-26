# [HIGH] `calculateNisaGoal` binary search assumes the wrong monotonicity direction, reporting "no solution" even when a lower, affordable contribution exists

## Severity: High (the tool's core purpose — finding the maximum safe NISA contribution — silently fails for the common default call, right when its answer matters most)

## Note on prior review status
This exact issue was already identified in `todo/asset-simulation-review-round2.md` §2.7
("`calculateNisaGoal` 二分搜尋的單調性假設不成立"). Unlike the many round-2 items marked `[FIXED]` with an
"Agent 補充註記" confirming the fix, §2.7 carries no such tag — it is listed under "次優先" (next
priority backlog) as an explicitly **unfixed** item. Independent re-verification in this review
confirms the bug is still present in the current code exactly as originally described. This is not
a new finding; it is flagged here because it remains open and the user's stated assumption was that
most round-1/round-2 items were "大致上修好" (mostly fixed) — this is a known exception to that.

## Summary
The binary search in `calculateNisaGoal` treats "no bankruptcy" as if it becomes true for *larger*
NISA contributions, but increasing the monthly NISA contribution directly reduces liquid cash
(raising bankruptcy risk) via `IncomeExpenseCalculator`/`FundOverrideApplier`/
`AssetSimulationEngineImpl`'s `remainingCashThisYear` calculation. So for the common default call
(no `safetyNetAmount`), the predicate is monotonically **decreasing** in the contribution amount —
the exact opposite of what the search assumes — causing it to return `null` ("no solution") even
when a valid, smaller contribution amount exists.

## Evidence

[SimulationOptimizerServiceOldImpl.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/SimulationOptimizerServiceOldImpl.java) lines 68-92:
```java
while (low <= high) {
    long mid = low + (high - low) / 2;
    ...
    boolean noBankruptcy = simulation.stream().allMatch(dto ->
        dto.getProjectedLiquidCash() >= 0 && dto.getProjectedNetWorth() >= safetyNet);
    if (noBankruptcy) { best = mid; high = mid - 1; } else { low = mid + 1; }
}
```
This logic assumes `noBankruptcy(mid)` is false-then-true as `mid` increases (so "false" means "go
higher"). But `TaxAdvantagedAccountProjector` (annualNisaContribution added to
`newNisaPrincipal`) and `AssetSimulationEngineImpl.java` line 286
(`remainingCashThisYear = netSavings - contributions.getTotalEmployeeInvestments() - ...`) show that
a larger `mid` (`testNisaMonthlyContribution`) strictly reduces liquid cash each year — i.e.
`noBankruptcy(mid)` is actually true-then-false, not the monotonic-increasing shape the search
assumes.

`SolveNisaGoalRequest.safetyNetAmount` has no `@NotNull`, and `AssetSimulationController` maps
`POST /nisa-goal` with `@RequestBody(required = false)`, so a body-less or
`safetyNetAmount`-omitted call is a legitimate, common path, collapsing `safetyNet` to 0 (line 55)
and making `noBankruptcy` reduce essentially to "liquid cash never goes negative."

## Failure scenario
Call `calculateNisaGoal` with no `safetyNetAmount`. Consider a household whose true affordable NISA
budget is only ~¥40,000/month (any more depletes liquid cash to negative in some future year). The
search starts at `low=0, high=300000`, first `mid=150000`. Since ¥150,000/month is unaffordable,
`noBankruptcy(150000)=false`, so the code executes `low = mid + 1 = 150001`, moving the search into
`[150001, 300000]` — a region that is entirely and permanently bankrupt, since larger contributions
only make cash flow worse. The loop keeps testing ever-larger, ever-more-bankrupt midpoints, `best`
is never set, and the method returns `null` ("no solution"), even though ¥0–40,000/month is a
perfectly valid, bankruptcy-free monthly contribution that the algorithm never tests because it
moved in the wrong direction on the very first probe.

## Suggested direction (not required by this review, noted for scoping a fix)
Invert the search direction for the default (no-safety-net) case — treat `noBankruptcy(mid)==false`
as "go lower", `==true` as "try higher, record as best" — or replace the binary search with a
linear/step scan given the narrow `[0, 300000]` domain, since the predicate's actual shape can
become non-monotonic once a caller-supplied `safetyNetAmount` (which requires `projectedNetWorth >=
safetyNet`, a quantity that *increases* with NISA contribution) is combined with the liquidity
constraint (which decreases with contribution) into a single `allMatch`.

## Agent 補充註記
**[WONTFIX]** 此功能 (`calculateNisaGoal` 與 `SimulationOptimizerServiceOldImpl`) 目前已在 `AssetSimulationController` 標記為即將棄用 (`@SuppressWarnings("deprecation")` 與 `TODO`)。等待新版 `GlobalOptimizerService` 驗證完成後即會刪除，因此決定不花時間修復此舊版邏輯。
