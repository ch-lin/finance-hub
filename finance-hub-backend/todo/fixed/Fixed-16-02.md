# [RESOLVED] Re-verification (2026-09-02): the remaining `mortgageEarlyRepayments` gap and the "Clear All Overrides" path are both genuinely closed, with no regression to other override fields — CONFIRMED_FIXED

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies the "## Fix" section appended to [Fixed-16-01.md](Fixed-16-01.md), which had confirmed `mortgageRateScenarios` was already fixed but flagged three remaining gaps: `mortgageEarlyRepayments`'s type/clear-handler still used `undefined` instead of `null`, "Clear All Overrides" still reset to `{}` (leaving both mortgage fields *absent* rather than explicitly `null`, so neither persistence effect's removal branch fired), and the "(active)" indicator never reflected either mortgage field. The new "## Fix" section claimed all three were closed.

## Independently verified (confirmed real)

**`mortgageEarlyRepayments` now symmetric with `mortgageRateScenarios`.** `use-simulation-data.ts:20` now includes `| null` in the type, and its localStorage-persistence `useEffect` (lines 67-75) is now structurally identical to the already-fixed `mortgageRateScenarios` effect (lines 57-65): `!== undefined` guard, `=== null` → `removeItem`, else → `setItem`.

**`handleRemoveRepayment` now sets `null`.** `simulation-override-panel.tsx:228`: `update({ mortgageEarlyRepayments: Object.keys(newMap).length > 0 ? newMap : null })` — matches the pattern already used by `handleRemoveScenario` (line 176) exactly.

**"Clear All Overrides" genuinely fixed, with no regression to other fields (the most important thing checked here).** `simulation-override-panel.tsx:598`: `onClick={() => setOverrides({ mortgageRateScenarios: null, mortgageEarlyRepayments: null })}`. Traced `setOverrides` to its source: it is the raw `useState` dispatch (`use-simulation-data.ts:121`), passed through unmodified via `page.tsx` — a full state **replace**, not the panel's local `update()` merge helper. Because it's a full replace, calling it with only these two keys produces a new state object where every other override field (`targetRetirementAgeOverride`, `pensionStartAgeOverride`, `idecoStartAgeOverride`, `fundOverrides`, etc. — enumerated in `use-simulation-data.ts:13-18`) becomes absent, i.e. cleared to `undefined` on read — exactly the same clearing behavior the old `setOverrides({})` produced for those fields. The only change is that the two mortgage fields are now explicitly `null` (needed to fire their persistence effects) instead of absent. Confirmed: this is a genuine fix with no new regression to the "Clear All" semantic for any other field.

**"(active)" indicator now reflects both mortgage fields.** `simulation-override-panel.tsx:241-246` now includes `(overrides.mortgageRateScenarios && Object.keys(...).length > 0)` and the equivalent for `mortgageEarlyRepayments`, alongside the pre-existing checks — both `null` and empty-object states correctly evaluate as inactive.

**Failure scenario traced end-to-end against current code — no longer reproduces.** Add-then-delete of an early repayment: last-item removal now sets `null`, the persistence effect's `=== null` branch fires, `localStorage.removeItem('finance_hub_mortgage_early_repayments')` runs — a reload no longer resurrects it. "Clear All Overrides" now explicitly nulls both mortgage fields, firing both removal branches for both localStorage keys.

## Noted gap (same scope, not required to fix now)
No automated test coverage exists for this hook/panel (confirmed: no test files, `__tests__` directories, or jest/vitest config anywhere in the frontend repo outside `node_modules`) — same project-wide characteristic noted in Fixed-15-02, not specific to this fix.

## Verdict
Genuinely fixed. No further action needed on this finding.
