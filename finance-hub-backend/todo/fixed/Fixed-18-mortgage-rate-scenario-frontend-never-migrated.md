# [HIGH] Mortgage rate-scenario backend fix (per-mortgage, VARIABLE-only) is correct, but the frontend was never migrated to the new contract — the feature is unusable end-to-end

## Severity: High (a shipped UI control that a user can click and expect to work either silently fails to apply per-mortgage, or fails request deserialization outright)

## Note on prior review status
Tracked as `Fixed-03` /
[Fixed-03-mortgage-rate-scenario-override-global.md](Fixed-03-mortgage-rate-scenario-override-global.md).
The backend half of the fix is genuinely correct and verified below. This finding is that the
frontend half was never updated to match, so the fix is not actually usable from the UI.

## Summary
`MortgageCalculator.getApplicableRate()` now correctly only consults rate-scenario overrides for
`VARIABLE`-rate mortgages, and looks them up per mortgage ID from a
`Map<Long, List<MortgageRateScenarioOverride>>` — this matches the DTO/mapper chain end-to-end on
the backend. But the frontend override panel and its data hook were never migrated to this
per-mortgage contract: they still build and POST a flat, mortgage-ID-less array for
`mortgageRateScenarios`. A JSON array cannot bind into a Java `Map<Long, List<...>>` field, so using
the "Mortgage Rate Stress Test" UI control either fails to apply per-mortgage as intended, or risks
failing the whole request's deserialization.

## Evidence

Backend (correct):
```java
// MortgageCalculator.java line 62
if (m.getRateType() != InterestRateType.VARIABLE) return m.getInterestRate();
// line 68
overrides.mortgageRateScenarios().get(m.getId())
```
against
[SimulationOverrides.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)
line 20: `Map<Long, List<MortgageRateScenarioOverride>> mortgageRateScenarios`, and
[AssetSimulationRequest.java](finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java)
line 14: `Map<Long, List<MortgageRateScenarioRequest>> mortgageRateScenarios`, correctly wired by
`AssetSimulationMapper.java` lines 49-64 (`mapMortgageRateScenarios` builds the Map).

Frontend (not migrated):
[use-simulation-data.ts](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts)
line 19: `mortgageRateScenarios?: { startYear: number; rate: number }[] | null;` — a flat array, no
mortgage id field — is `JSON.stringify`'d directly into the POST body at line 84
(`body: JSON.stringify(currentOverrides)`).
[simulation-override-panel.tsx](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)'s
`handleAddScenario`/`handleUpdateScenario`/`handleRemoveScenario` (lines 115-145) only ever
manipulate that flat array — contrast with the sibling "Mortgage Early Repayments" block a few lines
below (lines 147-197, 332-417), which explicitly builds a `Record<number, ...>` map
(`mortgageEarlyRepayments`) and has a mortgage-ID `<select>` (lines 350-370), proving the correct
per-mortgage pattern already exists in the same file but was never applied to the rate-scenario
feature. A repo-wide `grep -rn mortgageRateScenarios` across the frontend confirms no other file
transforms this into a keyed map before sending.

## Failure scenario
A user with a VARIABLE-rate mortgage opens the simulation override panel, adds a "Mortgage Rate
Stress Test" scenario (e.g. rate jumps to 3% starting year 5), and runs the simulation expecting the
stress test to apply to that specific mortgage. The frontend sends a flat array with no mortgage ID
attached. Because the backend field is a `Map<Long, List<...>>`, Jackson either fails to deserialize
the array into the map (likely surfacing as a 400 or a silently-empty map depending on Spring's
binding leniency) or, if it does bind, does so with no mortgage association, so
`overrides.mortgageRateScenarios().get(m.getId())` never finds the scenario for any real mortgage ID
and the "stress test" the user believes they configured silently never applies at all.

## Suggested direction (not required by this review, noted for scoping a fix)
Migrate `use-simulation-data.ts`'s `mortgageRateScenarios` type and
`simulation-override-panel.tsx`'s `handleAddScenario`/`handleUpdateScenario`/`handleRemoveScenario`
to the same `Record<number, ...>` keyed-by-mortgage-ID pattern already used for
`mortgageEarlyRepayments`, including a mortgage-ID `<select>` in the UI, matching the backend's
`Map<Long, List<MortgageRateScenarioRequest>>` contract.

---

# Reply to Claude (Round 3 Review)

**Status:** Invalid Finding / Code is already correct.

It appears this finding was based on an outdated checkout of the frontend repository (`hub-ui`). The frontend code **was successfully migrated** in the `bugfix/mortgage-rate-scenario-frontend` branch, and the API payload already matches the `Map<Long, List<...>>` (i.e., `Record<number, array>`) structure expected by the backend.

### Proof of Fix (from latest `hub-ui` checkout)

**`use-simulation-data.ts` (Line 19)**:
```typescript
export interface SimulationOverrides {
  // ...
  mortgageRateScenarios?: Record<number, { startYear: number; rate: number }[]> | null;
  // ...
}
```
The type is correctly defined as `Record<number, ...>`.

Please ensure you have pulled the latest changes from the `bugfix/mortgage-rate-scenario-frontend` branch when reviewing the frontend. The feature is fully functional end-to-end.
