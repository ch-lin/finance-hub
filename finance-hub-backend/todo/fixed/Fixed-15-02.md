# [RESOLVED] Re-verification (2026-09-02): composite-key (`fundId` + `accountType`) fix genuinely applied to both lookup functions, traced end-to-end against the original collision scenario — CONFIRMED_FIXED

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies the "## Fix" section appended to [Fixed-15-01.md](Fixed-15-01.md), which claimed `getFundOverride` and `updateFundOverride` in `simulation-override-panel.tsx` now match on both `fundId` AND `accountType`, mirroring the backend's already-established composite-key convention.

## Independently verified (confirmed real)

**Composite-key match genuinely present in both functions.** `simulation-override-panel.tsx:87-89` (`getFundOverride`) and `:91-93` (`updateFundOverride`'s `findIndex`) both now require `f.fundId === fundId && f.accountType === accountType`, not `fundId` alone.

**No case-sensitivity gap.** Every call site derives `accountType` from a hardcoded uppercase source (`'NISA'`, `'IDECO'`, `'CORPORATE_DC'`, set at fetch time — lines 56-57, 65, 489) rather than free-form user input, so frontend-internal string comparisons never risk a casing mismatch. The backend's `.toUpperCase()` in `FundOverrideApplier.java:62` remains a safe no-op given this.

**Original failure scenario traced against current code — does not reproduce.** With a NISA fund id=3 and an iDeCo fund id=3: setting the NISA return-rate override then the iDeCo contribution override now produces two separate entries (`{fundId:3, accountType:'NISA', ...}` and `{fundId:3, accountType:'IDECO', ...}`) rather than one merged object — `findIndex` correctly returns `-1` for the second call since `accountType` differs, so it pushes a new entry instead of overwriting.

**Backend-side resolution confirmed still compatible.** The payload is sent verbatim (`JSON.stringify`, no transformation), and `FundOverrideApplier.java:59-63`'s composite-key map (`"NISA:3"`, `"IDECO:3"`) resolves each entry to its correct, distinct fund list — no collision, keys line up exactly as the frontend now produces them.

**No regression found in fallback or delete paths.** The `|| { fundId, accountType }` fallback for a fund with no existing override still carries the correct `accountType` from the caller; the delete path (`splice(idx, 1)` after a composite-key `findIndex`) removes only the specific `(fundId, accountType)` entry, not a same-id entry belonging to a different account type.

## Noted gap (same scope, not required to fix now)
No automated test coverage exists anywhere in the frontend repo for this component or these two functions (confirmed via repo-wide grep) — correctness here rests on this manual trace rather than a regression test. Not spun off as a separate finding since the complete absence of a test harness/config in this frontend repo is a pre-existing, project-wide characteristic rather than a defect introduced by or scoped to this fix.

## Verdict
Genuinely fixed. No further action needed on this finding.
