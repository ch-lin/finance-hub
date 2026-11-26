# [CRITICAL] GA/GridSearch-optimized NISA contribution is discarded before being returned to the caller

## Severity: Critical (the AI Optimizer's NISA recommendation is silently thrown away, so "Apply to Sandbox" reinstates the OLD baseline NISA contribution even when the optimizer verified a different one was needed to avoid bankruptcy)

## Summary
`GlobalOptimizerServiceImpl.optimize()` hardcodes the returned
`SimulationOverrides.testNisaMonthlyContribution` to `null`, throwing away the chromosome's
optimized NISA target even when NISA optimization was requested and a non-zero value was found by
the search. Every sibling optimized field (retirement age, pension age, iDeCo age, mortgage early
repayments) is correctly forwarded from the same `optimizedOverrides` object — only the NISA slot is
overwritten with `null`.

## Evidence

[GlobalOptimizerServiceImpl.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java) lines 70-84:
```java
SimulationOverrides optimizedOverrides = chromosomeMapper.toOverrides(bestSolution);
...
return new SimulationOverrides(
        null, // testNisaMonthlyContribution
        null, // mortgageEarlyRepaymentYear (Deprecated)
        optimizedOverrides.targetRetirementAgeOverride(),
        optimizedOverrides.pensionStartAgeOverride(),
        optimizedOverrides.idecoStartAgeOverride(),
        optimizedOverrides.mortgageEarlyRepayments(),
        ...);
```
`ChromosomeMapper.java` line 34 confirms `optimizedOverrides` already correctly contains
`chromosome.getNisaMonthlyTarget()` in that same slot — the value exists, and is discarded one line
later by the hardcoded `null`. No `@Deprecated` marker accompanies this `null` (unlike the adjacent,
intentionally-nulled legacy `mortgageEarlyRepaymentYear` field), suggesting this is an oversight
rather than a deliberate omission.

Downstream, `GlobalOptimizerController` maps this straight through `GlobalOptimizerMapper` into
`GlobalOptimizerResponse.testNisaMonthlyContribution`, which the frontend
([global-optimizer-card.tsx:94](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx))
displays as "N/A", and which `page.tsx`'s `onApplyResult` handler (lines 122-125) uses to decide
whether to copy the optimized NISA target into the Sandbox overrides:
```javascript
if (result.testNisaMonthlyContribution !== null) next.testNisaMonthlyContribution = ...
```
Since the field is always `null`, this branch never fires.

## Failure scenario
Call `POST /simulation/optimizer/{targetAge}` with `OptimizationConfig.optimizeNisa()=true`. The GA
correctly searches `nisaMonthlyTarget` in `[0, 300000]` and finds, say, `180000` as necessary to
avoid a projected cash bankruptcy. The optimizer's own internal chromosome/fitness evaluation
genuinely used `180000` for its bankruptcy-free verdict — but the API response's
`testNisaMonthlyContribution` is always `null`. The UI displays "N/A" for the recommended NISA
contribution. The user clicks "Apply to Sandbox"; the Sandbox re-simulation keeps the OLD/baseline
NISA contribution (e.g. 0), and the displayed bankruptcy-year projection for the "optimized" plan
silently differs from — and can be materially worse than — what the optimizer actually verified
internally to be bankruptcy-free.

## Suggested direction (not required by this review, noted for scoping a fix)
Return `optimizedOverrides.testNisaMonthlyContribution()` in that slot instead of a hardcoded
`null`, matching the pattern already used for every other optimized field.

---
## 💡 Resolution Status
> [!NOTE]
> **Status:** Fixed ✅
> **Resolution:** Updated `GlobalOptimizerServiceImpl` to properly return `optimizedOverrides.testNisaMonthlyContribution()` instead of a hardcoded `null`. The AI optimizer's NISA recommendations are now correctly preserved and returned to the frontend Sandbox.
