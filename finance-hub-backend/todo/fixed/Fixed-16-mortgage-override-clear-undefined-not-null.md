# [MEDIUM] Clearing mortgage rate scenarios / early repayments sets state to `undefined` instead of `null`, so stale overrides never leave localStorage and are silently resurrected on reload

## Severity: Medium (a user-deleted mortgage stress-test override can silently reappear on the next page load/remount, re-running the simulation with a scenario the user believed they removed, with no visible indicator that it's still active)

## Summary
The localStorage-sync effects in the Sandbox hook only remove a persisted key when the corresponding
override field is explicitly `null`. But the override panel's delete-last-item and "Clear All
Overrides" actions set the fields to `undefined` (the `mortgageEarlyRepayments` type itself has no
`| null` at all, unlike every sibling field that does), which the effects' `!== undefined` guard
treats as "nothing to do" — leaving the old value in localStorage to be reloaded on the next mount.
The panel's "(active)" indicator also never checks these two fields, so there is no UI cue that a
supposedly-cleared override is still live.

## Evidence

[use-simulation-data.ts](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts):
```typescript
useEffect(() => {
  if (typeof window !== 'undefined' && overrides.mortgageEarlyRepayments !== undefined) {
    if (overrides.mortgageEarlyRepayments === null) {
      localStorage.removeItem('finance_hub_mortgage_early_repayments');
    } else {
      localStorage.setItem('finance_hub_mortgage_early_repayments', JSON.stringify(overrides.mortgageEarlyRepayments));
    }
  }
}, [overrides.mortgageEarlyRepayments]);
```

`simulation-override-panel.tsx` `handleRemoveRepayment`:
```javascript
update({ mortgageEarlyRepayments: Object.keys(newMap).length > 0 ? newMap : undefined });
```

and the global reset button: `<Button ... onClick={() => setOverrides({})}>Clear All Overrides</Button>`.

Both paths yield `undefined`, never `null`, for `mortgageEarlyRepayments` and
`mortgageRateScenarios`, so the removal branch of the persistence effect is unreachable for them. A
repo-wide grep for the two localStorage keys
(`finance_hub_mortgage_rate_scenarios`, `finance_hub_mortgage_early_repayments`) found no other code
path that clears them.

## Failure scenario
User adds a mortgage early-repayment event, then deletes it via the trash icon.
`handleRemoveRepayment` sets `mortgageEarlyRepayments: undefined`. The persistence effect's
`!== undefined` guard never fires, so `localStorage.removeItem(...)` never runs and the old
repayment stays in localStorage. Likewise, "Clear All Overrides" resets the whole overrides object,
making both `mortgageRateScenarios` and `mortgageEarlyRepayments` `undefined` (property absent), so
neither persistence effect fires and both stale localStorage entries survive.

The in-session simulation is briefly correct (no repayment applied), but as soon as the user reloads
the page or the component remounts, the mount-time effect reads the stale
`finance_hub_mortgage_early_repayments`/`finance_hub_mortgage_rate_scenarios` keys back into
`overrides`, and the debounced fetch effect immediately re-runs the simulation with the
supposedly-deleted early repayment or rate-shock scenario reinstated — with no visible cue, since
the panel's "(active)" indicator only checks `targetRetirementAgeOverride`, `pensionStartAgeOverride`,
and `fundOverrides.length`, never `mortgageRateScenarios` or `mortgageEarlyRepayments`.

## Suggested direction (not required by this review, noted for scoping a fix)
Change `handleRemoveRepayment`/`handleRemoveScenario`/"Clear All Overrides" to set these fields to
`null` (and widen the `mortgageEarlyRepayments` type to include `| null`) instead of `undefined`, so
the existing `=== null` removal branch actually fires; also add these two fields to the panel's
"(active)" indicator check.
