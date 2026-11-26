# [LOW] Re-verification (2026-09-02): "Legacy Optimizer" tab removal confirmed complete — no residual reachability path, backend correctly left untouched

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies [Fixed-20-nisa-goal-solver-still-live-pending-removal.md](Fixed-20-nisa-goal-solver-still-live-pending-removal.md)'s "Reply: Fixed by Antigravity", which claimed the "Legacy Optimizer" tab was pulled from `simulation/page.tsx`, hiding the buggy `calculateNisaGoal` tool from real users while its backend removal is still pending.

## What was verified fixed (confirmed real)
`simulation/page.tsx`'s current tab configuration (lines 35-42) defines only two tabs, `sandbox` and `optimizer` (rendering `GlobalOptimizerCard`) — no "Legacy Optimizer" tab, no import of `NisaGoalSolverCard`, no reference to `/nisa-goal` anywhere in the file. A repo-wide grep across the entire frontend for `nisa.?goal`/`legacy.?optimizer` returns hits in exactly one file: the now-unused component itself (`components/nisa-goal-solver-card.tsx`, still present but imported nowhere — dead code, not deleted, which is an acceptable form of the requested mitigation).

**No residual reachability path.** A repo-wide grep for `nisa-goal`/`NisaGoal`/`asset-simulation/nisa-goal` surfaces only that one dead file — no other page, dashboard widget, nav entry, or deep link references it. Additionally, this frontend has no `app/api` proxy directory and no rewrites in `next.config.ts` for `/api/finance/...`, so even the dead component's `fetch('/api/finance/simulation/asset-simulation/nisa-goal')` call would 404 client-side today regardless of whether it were ever rendered again.

**No compile-breaking artifacts.** `page.tsx`'s `activeTab` state only ever takes `'sandbox'`/`'optimizer'` — no dangling props/state referencing a removed tab.

**Backend correctly left unchanged, as the finding requested.** `AssetSimulationController.java:60-63` still exposes `POST /nisa-goal`, and `SimulationOptimizerServiceOldImpl.calculateNisaGoal`'s binary search still has the original inverted-monotonicity assumption (`noBankruptcy==true → best=mid; high=mid-1`, else `low=mid+1`). The endpoint is still technically live server-side but is now unreachable from any UI surface.

## Caveat
`hub-ui` is not a git repository in this working copy, so there is no "before" commit to diff against — this verdict rests entirely on current-state analysis (the tab and its only entry point are absent today), not on confirming which specific change removed it.

## Suggested direction
None required — this closes the finding as intended (UI-hidden mitigation, backend deletion still pending per the original removal plan).
