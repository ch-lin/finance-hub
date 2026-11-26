# [HIGH] Re-verification (2026-08-31): fund-override fundId collision is still fully unaddressed — zero remediation attempted

## Status: STILL_BROKEN — no fix, no partial fix, no Resolution Status section ever appended

## Relation to original finding
Re-verifies [Fixed-15-fund-override-fundid-collision.md](Fixed-15-fund-override-fundid-collision.md)
against current source as part of this round's strict source-code re-review. Unlike most other
FINDING files in this folder, Fixed-15's own file has **no appended "Fix Summary" / "Resolution
Status" / "Round N Reply" section at all** — it appears to have never been picked up for remediation
in any of the intervening fix rounds (round2.md, the various "Antigravity"/"Gemini 3.1 Pro" reply
rounds, or Epic PROGRESS.md).

## What was actually fixed
Nothing. This is a pure re-confirmation, not a partial-fix writeup.

## What remains broken (evidence, current source as of 2026-08-31)

[simulation-override-panel.tsx](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
lines 87-97 — both lookup functions are still `fundId`-only, exactly as originally reported:
```javascript
const getFundOverride = (fundId: number, accountType: string) => {
  return overrides.fundOverrides?.find(f => f.fundId === fundId) || { fundId, accountType };
};
const updateFundOverride = (fundId: number, accountType: string, partial: Partial<FundOverride>) => {
  const existing = overrides.fundOverrides || [];
  const idx = existing.findIndex(f => f.fundId === fundId);
  ...
```
`accountType` is accepted as a parameter and even written into the merged object, but it is never
part of the *lookup* predicate in either function.

[FundOverrideApplier.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java)
lines 61-62 — unchanged, still keys by the composite `accountType:fundId`:
```java
Map<String, SimulationOverrides.FundOverride> overrideMap = overrides.fundOverrides().stream()
        .collect(Collectors.toMap(fo -> fo.accountType().toUpperCase() + ":" + fo.fundId(), fo -> fo));
```

The frontend/backend key mismatch described in the original finding is therefore still fully live:
whenever a NISA fund and a DC/iDeCo fund share the same numeric `id` (the normal case, since each
comes from its own auto-increment PK sequence), editing one fund's override after the other's is
already applied will silently merge them into a single frontend object, corrupting both.

## Failure scenario
Identical to the original finding's failure scenario — re-confirmed to still reproduce exactly as
described: setting a NISA fund's return-rate override followed by editing an iDeCo fund's
contribution override (where both funds happen to share `id=3`) merges into one object tagged with
whichever `accountType` was written last, so the backend's composite-key lookup applies both
overrides to the wrong fund and silently drops the other.

## Suggested direction (unchanged from original)
Change both `getFundOverride` and `updateFundOverride` to match on the composite key (`fundId` AND
`accountType`), mirroring the backend's already-established `accountType:fundId` convention. Given
this bug has now gone at least two full review rounds without being triaged at all, it should be
prioritized for the next fix pass — it is the highest-severity item among this round's confirmed-open
findings that has received zero remediation attempts.

## Fix
- Updated `getFundOverride` and `updateFundOverride` in `hub-ui/.../simulation-override-panel.tsx` to match on both `fundId` AND `accountType` (`f.fundId === fundId && f.accountType === accountType`), preventing cross-table primary key collisions between NISA and DC/iDeCo overrides.
