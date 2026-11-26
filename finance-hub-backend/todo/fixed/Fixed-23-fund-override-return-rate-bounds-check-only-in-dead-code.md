# [HIGH] Fund return-rate bounds-check fix exists only in a dead/unimported component; the live "Per-Fund Overrides" UI and its backend sink have zero validation

## Severity: High (a real user can type e.g. 500 intending 5% into the live fund-override UI and have it applied unchecked as a 500% return, materially corrupting the simulation)

## Note on prior review status
The claimed fix (reject fund return-rate overrides outside -100%/+100%) genuinely exists in code, but
in `asset-allocation-drawer.tsx`, a component that has zero importers anywhere in the app (its own
header comment states "it is currently unused (0 imports)... Kept temporarily for reference."). The
live, reachable equivalent for editing fund return-rate overrides was never given the same guard.

## Summary
`asset-allocation-drawer.tsx` (dead code) has real `min="-100" max="100"` plus `onBlur` handlers that
toast an error and abort the update if a return rate is out of range, and its corresponding backend
allocation-update endpoints (`UpdateNisaAllocationRequest`, `DefinedContributionController`) do have
real `@DecimalMin`/`@DecimalMax` validation. But the actual reachable path for editing fund return
rates — `simulation-override-panel.tsx`'s "Per-Fund Overrides" section — has no bounds checking at
all, and its backend sink (`SimulationOverrides.FundOverride` / `FundOverrideApplier`) also carries no
validation annotations, so a value like `500` is applied unconditionally.

## Evidence

Dead-code proof: `grep -rln "AssetAllocationDrawer" src` in `hub-ui` returns only the component's own
definition file
([asset-allocation-drawer.tsx](finance-hub-ui/hub-ui/src/components/asset-allocation-drawer.tsx)), no
importer; the file's own top comment (lines 1-7) states "it is currently unused (0 imports)... Kept
temporarily for reference." Within that dead file the fix is real: lines 217-231 and 270-284 use
`min="-100" max="100"` plus an `onBlur` that does
`if (val !== null && (val < -1.0 || val > 1.0)) { toast.error('Return rate must be between -100% and +100%'); return; }`
before calling `updateNisaAllocation`/`updateDcAllocation`. `grep -rln "updateNisaAllocation|updateDcAllocation" src`
likewise returns only that same dead file — no live caller.

Backend counterpart for the dead path is genuinely validated:
`UpdateNisaAllocationRequest.java` lines 16-17:
`@DecimalMin("-1.0") @DecimalMax("1.0") private Double expectedReturnRate;`, and
`DefinedContributionController.java` line 145 has the same inline constraint.

Live, unguarded path:
[simulation-override-panel.tsx](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
lines 448-457:
```tsx
<Input type="number" step="0.5" ... onChange={(e) => updateFundOverride(fund.id, accType, { returnRateOverride: Number(e.target.value) / 100 })} />
```
has no min/max/onBlur guard at all. Its backend sink,
[SimulationOverrides.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)'s
nested `FundOverride(String accountType, Long fundId, Long contributionOverride, Double returnRateOverride)`
record, carries no validation annotations, and
[FundOverrideApplier.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)
lines 140-143 sets `fund.setExpectedReturnRate(ov.returnRateOverride())` unconditionally — so typing
`500` there is still stored as a 500% return with no rejection anywhere.

## Failure scenario
A user opens the live "Per-Fund Overrides" panel and types `500` into a fund's return-rate override,
intending 5%. There is no client-side guard to catch the mistake, and no server-side validation
either — the simulation silently runs with that fund compounding at 500% annual return, producing a
wildly optimistic (and useless) net-worth/bankruptcy projection with no warning that the input was
almost certainly a typo.

## Suggested direction (not required by this review, noted for scoping a fix)
Port the bounds-check pattern (min/max attributes + onBlur validation) from `asset-allocation-drawer.tsx`
into `simulation-override-panel.tsx`'s fund-override inputs, and add `@DecimalMin("-1.0")
@DecimalMax("1.0")` to `SimulationOverrides.FundOverride.returnRateOverride` with `@Valid` cascading
enabled on the containing request, so the live path gets the same protection the dead file already
has. Separately, consider deleting `asset-allocation-drawer.tsx` if it is genuinely no longer needed,
since its presence made this gap harder to notice (a reviewer could mistake its validation for the
live path's).

> **[FIXED] (2026-08-29)**
> - **Frontend**: Added boundary validation to the active `simulation-override-panel.tsx`. The `Return (%)` inputs now feature `min="-100" max="100"` and intercept values strictly via `onChange`. Any invalid value triggers a `toast.error` ("Return rate must be between -100% and +100%") and forcibly aborts state mutation, actively defending against user typos (e.g., inputting "500").
> - **Backend**: Expanded defense-in-depth by attaching `@Valid` to the `fundOverrides` list in `AssetSimulationRequest.java`. Supplemented `FundOverrideRequest.returnRateOverride` with explicit `@DecimalMin("-1.0")` and `@DecimalMax("1.0")` annotations to rely on the existing robust `WebGlobalExceptionHandler` for gracefully producing 400 Bad Requests upon payload violation.
