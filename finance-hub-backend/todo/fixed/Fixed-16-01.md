# [MEDIUM] Re-verification (2026-08-31): fix only covers `mortgageRateScenarios`, not the sibling `mortgageEarlyRepayments` field or the global reset button — asymmetric partial fix

## Status: PARTIALLY_FIXED — one of two affected fields fixed; the other field, and the global reset
path, remain exactly as originally reported

## Relation to original finding
Re-verifies [Fixed-16-mortgage-override-clear-undefined-not-null.md](Fixed-16-mortgage-override-clear-undefined-not-null.md).
The original finding reported that clearing either `mortgageRateScenarios` or
`mortgageEarlyRepayments` sets the field to `undefined` instead of `null`, so the localStorage
persistence effect's `!== undefined` guard never fires and the stale override silently survives a
reload. A fix was applied to Epic PROGRESS.md under "Task 3-2" (migrating `mortgageRateScenarios` to
`Record<number, ...>`), but it only touched one of the two affected code paths.

## What was actually fixed
[use-simulation-data.ts](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts)
line 19-20 — the `mortgageRateScenarios` type now includes `| null`.

[simulation-override-panel.tsx](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
`handleRemoveScenario` (lines 168-177) now correctly nulls the field on last-item removal:
```javascript
update({ mortgageRateScenarios: Object.keys(newMap).length > 0 ? newMap : null });
```
This path is genuinely fixed: deleting the last mortgage-rate-scenario override now correctly clears
its localStorage key on the next mount.

## What remains broken (evidence, current source as of 2026-08-31)

1. **`mortgageEarlyRepayments` type still lacks `| null`.**
   `use-simulation-data.ts` line 20 — unlike `mortgageRateScenarios` on the line directly above it,
   this field's type was never widened.

2. **`handleRemoveRepayment` still sets `undefined`, not `null`.**
   `simulation-override-panel.tsx` lines 220-229 — unchanged from the original finding:
   ```javascript
   update({ mortgageEarlyRepayments: Object.keys(newMap).length > 0 ? newMap : undefined });
   ```
   The persistence effect's `!== undefined` guard is still unreachable for this field, so deleting the
   last early-repayment override still leaves the stale entry in
   `finance_hub_mortgage_early_repayments` to be silently reloaded on the next mount/reload.

3. **"Clear All Overrides" still resets to `{}`, nulling neither field.**
   `simulation-override-panel.tsx` lines 591-599:
   ```tsx
   <Button ... onClick={() => setOverrides({})}>Clear All Overrides</Button>
   ```
   Setting the whole overrides object to `{}` makes both fields *absent* (not `null`), so neither
   persistence effect's removal branch fires for either field — a full "Clear All" still leaves both
   `finance_hub_mortgage_rate_scenarios` and `finance_hub_mortgage_early_repayments` stale in
   localStorage, resurrecting both on the next mount.

4. **The "(active)" indicator still never reflects either override field.**
   Lines 241-245 — unchanged; still only checks `targetRetirementAgeOverride`,
   `pensionStartAgeOverride`, and `fundOverrides.length`. A user has no visual cue that a
   supposedly-cleared mortgage override is still silently active.

## Failure scenario
A user adds an early-repayment override, deletes it via the trash icon (or clicks "Clear All
Overrides"), sees the panel show no active repayment, and believes the override is gone. On the next
page reload (or component remount, e.g. navigating away and back), the stale
`finance_hub_mortgage_early_repayments` localStorage entry is read back into `overrides`, and the
debounced fetch effect silently re-runs the simulation with the deleted early repayment reinstated —
with no "(active)" indicator ever surfacing this to the user. The `mortgageRateScenarios` half of the
original bug is fixed; this exact failure mode is fully preserved for `mortgageEarlyRepayments` and
for the global reset button regardless of field.

## Suggested direction (unchanged from original, scoped to the remaining gap)
Apply the identical pattern already used for `handleRemoveScenario` to `handleRemoveRepayment`
(`null` instead of `undefined`, widen the type in `use-simulation-data.ts` line 20), and change
"Clear All Overrides" to explicitly set `mortgageRateScenarios: null, mortgageEarlyRepayments: null`
rather than resetting to `{}`. Also add both fields to the panel's "(active)" indicator check.

## Fix
- Added `| null` to `mortgageEarlyRepayments` type in `use-simulation-data.ts`.
- Changed `handleRemoveRepayment` in `simulation-override-panel.tsx` to set `null` instead of `undefined` when removing the last early repayment.
- Changed "Clear All Overrides" button to explicitly set `{ mortgageRateScenarios: null, mortgageEarlyRepayments: null }`, ensuring both fields trigger `localStorage.removeItem(...)` while gracefully unsetting all other in-memory overrides.
- Updated the `(active)` indicator condition to illuminate if there are active `mortgageRateScenarios` or `mortgageEarlyRepayments`.
