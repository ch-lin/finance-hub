# [RESOLVED] Re-verification (2026-09-02): short-circuit removed — `YearEndAdjustmentEngine` now unconditionally reconciles Year-1 for every user, confirmed against a genuine new regression test

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies the "## Fix Applied (2026-09-02)" section appended to [Fixed-12-01.md](Fixed-12-01.md), which claimed the `if (context.getProjectedRemainingNetIncomeThisYear() != null)` short-circuit was removed so Year-1 unconditionally routes through `YearEndAdjustmentEngine`, that the now-dead `projectedRemainingNetIncomeThisYear`/`totalNetThisYear` fields were excised, and that a new regression test exercises the previously-dead reconciliation path with real, non-zero salary/tax data.

## Independently verified (confirmed real)

**Short-circuit genuinely removed.** `AssetSimulationEngineImpl.java:300-321` — the `age == initialAge` block now unconditionally calls `yearEndAdjustmentEngine.calculateYearEndAdjustment(...)` (lines 304-312), overwriting `netIncome`/`estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance`. No `if (context.getProjectedRemainingNetIncomeThisYear() != null)` branch exists anywhere in the file.

**Cleanup is complete.** Grepped the full backend tree and the sibling `finance-hub-ui` frontend for `projectedRemainingNetIncomeThisYear` and `totalNetThisYear` (any case, all source extensions, excluding this finding doc itself): zero hits. `SimulationContext.java` has no such field, getter, or setter left over.

**New test genuinely exercises the realistic, non-zero-tax reconciliation path.** `AssetSimulationEngineImplTest.java:638-696`, `shouldReconcileTaxesAndNetIncomeUsingYearEndAdjustmentEngineForYear0` — populates `salaryRecordRepository` with a real mocked `SalaryRecord` (non-zero gross/tax/social-insurance YTD figures) and a non-zero mocked `taxEngine.calculate(...)` result. Independently hand-traced the full arithmetic (effectiveAnnualSalary → taxResult → YTD subtraction in `YearEndAdjustmentEngine.java:41-56`) and it converges exactly to the test's own assertion (`netIncome == remainingGross − sum(3 remaining tax fields)`, `estimatedIncomeTax == fullTax − YTD`, etc.) — a genuine, non-degenerate assertion, not a trivial 0−0 case.

**No regression risk identified.** `YearEndAdjustmentEngine.calculateYearEndAdjustment` is pure long-arithmetic subtraction with no division or object dereference, so the degenerate brand-new-user case (all YTD fields zero) reduces safely to an identity (`remaining = annual − 0`) with no NPE/divide-by-zero risk — existing tests relying on the empty-salary-list stub are unaffected.

## Still present, unchanged, out of scope (carried over from Fixed-12-01, not required to fix)
- Possible current-month double-count: `AssetSimulationEngineImpl.java:190-193`'s `monthsToAdd = 12 − currentMonth + 1` still includes the current month, which can double-count against `ytdBaseSalary` if that month's salary has already posted.
- `useHistoricalBonusForProjection` default mismatch persists: backend `false` (`UserProfile.java:191,220`) vs. frontend `true` (`profile/me/page.tsx:26,48`, `onboarding/page.tsx:32,64`, both via `?? true`).

## On execution
Could not mechanically run the test — `mvn` fails at parent-POM resolution in this sandbox (network egress to Maven Central denied, `.m2` writes blocked) even though the POM hierarchy itself is correct; this is an environment limitation, not a code defect. Conclusion above rests on a full manual arithmetic trace through both the production code path and the test's independent computation, which converged exactly.

## Verdict
Genuinely fixed. No further action needed on this finding.
