# [HIGH] Re-verification (2026-08-31): backend `@Valid` cascade is still inert on the sandbox endpoint, and the real live fund-return-rate editing surface (found via round1-M9) was never touched by any bounds-check fix at all

## Status: PARTIALLY_FIXED — frontend sandbox-panel guard is real and live; backend validation
annotations exist but never fire; and a separate, more important live editing surface for permanent
fund return rates has zero validation and a likely-broken backend route

## Relation to original finding
Re-verifies [Fixed-23-fund-override-return-rate-bounds-check-only-in-dead-code.md](Fixed-23-fund-override-return-rate-bounds-check-only-in-dead-code.md),
whose "[FIXED] (2026-08-29)" note (lines 67-69) claims both a frontend fix on
`simulation-override-panel.tsx` and a backend `@Valid`/`@DecimalMin`/`@DecimalMax` fix. This finding
is also the same underlying bug family as `asset-simulation-review-round1.md` M9 (per this project's
existing Fixed-23↔M9 cross-reference), which points at the *legitimately-fixed* `asset-allocation-
drawer.tsx`/DC-NISA-allocation endpoints — a different, and as of this round confirmed **dead**, code
path. Consolidating both into this single companion file since they describe the same class of gap
across three different UI surfaces.

## What was actually fixed (confirmed real)
[simulation-override-panel.tsx](hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
lines 505-517, 555-567: the "Per-Fund Overrides" return-rate inputs now have `min="-100" max="100"`
plus an `onChange` guard that rejects/toasts out-of-range values. Confirmed live via import-chain
check from `page.tsx` — this is a genuinely reachable, currently-used component.

[AssetSimulationRequest.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/AssetSimulationRequest.java)
lines 16, 23-25: `@Valid List<FundOverrideRequest> fundOverrides` plus `@DecimalMin("-1.0")` /
`@DecimalMax("1.0")` on `FundOverrideRequest.returnRateOverride` — the annotations themselves are
correctly written.

## What remains broken (evidence)

**1. The backend `@Valid` cascade never fires — annotations are inert.**
[AssetSimulationController.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/controller/AssetSimulationController.java)
lines 41-44, `runSimulation`'s `request` parameter has no `@Valid`/`@Validated` annotation, and the
controller class itself is not annotated `@Validated` either:
```java
@PostMapping("/simulate")
public ResponseEntity<...> runSimulation(@RequestBody AssetSimulationRequest request) { ... }
```
Because the top-level parameter is never validated, Spring never walks into the nested `@Valid List<FundOverrideRequest>`
to apply the `@DecimalMin`/`@DecimalMax` checks on each entry. A direct API call with
`{"fundOverrides": [{"fundId": 3, "accountType": "NISA", "returnRateOverride": 5.0}]}` (500%) is
accepted and processed unrejected. No test exercises this path — confirmed via grep, no test posts an
out-of-range `returnRateOverride` to this controller.

**2. round1-M9's actual live editing surface for fund return rates was never given any bounds check —
a different component than the one M9/Fixed-23 originally named.**
`asset-allocation-drawer.tsx` (M9's originally-cited fix location) is confirmed dead code: 0 live
importers anywhere in the app, and its own header comment states it is "kept temporarily for
reference" since the simulator now uses `SimulationOverridePanel`. No other live caller ever sends
`expectedReturnRate` through the validated `UpdateNisaAllocationRequest`/`DefinedContributionController`
endpoints — `nisa-table.tsx` (lines 53-67) and `dc-table.tsx` (lines 49-63) only send
`monthlyEquivalentAmount`/`monthlyContributionAmount`, never a return-rate field.

The actual live UI for editing a fund's **permanent** expected return rate (as opposed to a
sandbox-only simulation override) is a completely different shared component,
[expected-return-rate-editor.tsx](hub-ui/hub-ui/src/app/finance/portfolios/components/expected-return-rate-editor.tsx),
used by `nisa-table.tsx:139-145` and `dc-table.tsx:117-123`. This component:
- Has **zero** `min`/`max`/`onBlur` bounds checking anywhere (lines 42-49).
- Falls back to displaying a hardcoded fake `"5.00"` default when `initialValue` is `null` (lines 16,
  28, 35, 63) — the same fake-default anti-pattern that round2-2.14 fixed elsewhere in this codebase,
  reintroduced here untouched.
- Its `onUpdate` callback PATCHes `/api/finance/accounting/nisa/{id}/return-rate` and
  `/api/finance/accounting/dc/funds/{id}/return-rate`. Neither path matches any registered Spring
  `@RequestMapping` in the backend: the only real NISA return-rate route is
  `NisaSnapshotRecordController.java` lines 110-112, mapped at `/assets/nisa/{id}/return-rate` (not
  `/api/finance/accounting/nisa/...`), and its `UpdateNisaReturnRateRequest` carries only `@NotNull`
  — no range check at all even if the route were correct. No DC return-rate endpoint exists anywhere
  in the backend under any path.

This means the actual permanent-portfolio-editing surface most likely 404s on save rather than
silently persisting an unbounded value — but it proves this surface was never in scope for any of the
bounds-check fixes applied so far, and it has its own separate routing bug on top of the missing
validation.

## Failure scenario
**Sandbox path**: A caller (script, future frontend change, or a request crafted outside the browser
input's `min`/`max` HTML attributes, which are trivially bypassable client-side constraints) posts
`returnRateOverride: 5.0` directly to `/simulate`. The inert `@Valid` cascade lets it through
unrejected, and the simulation runs with a fund compounding at 500% annual return.

**Permanent-portfolio path**: A user opens a fund's row in the portfolio table and edits its expected
return rate via `ExpectedReturnRateEditor`. Because `initialValue` may be `null` for a fund that never
had a rate set, the editor first displays a fake "5.00" the user may mistake for the real current
value; on save, the PATCH request likely hits a non-existent route and 404s (unverified in this
review whether the frontend surfaces this failure to the user or fails silently).

## Suggested direction (not required by this review, noted for scoping a fix)
1. Add `@Valid` (or class-level `@Validated`) to `AssetSimulationController.runSimulation`'s `request`
   parameter so the existing, correctly-written `@DecimalMin`/`@DecimalMax` annotations actually
   cascade; add a test posting an out-of-range `returnRateOverride` and asserting a 400.
2. Add the same `min`/`max`/`onBlur` guard pattern to `ExpectedReturnRateEditor` that
   `simulation-override-panel.tsx` already has, and remove its fake `"5.00"` fallback in favor of an
   empty/placeholder state (mirroring round2-2.14's fix elsewhere).
3. Fix or add the missing backend routes (`/api/finance/accounting/nisa/{id}/return-rate`,
   `/api/finance/accounting/dc/funds/{id}/return-rate`) that `ExpectedReturnRateEditor` actually
   calls, or repoint the frontend at the real `/assets/nisa/{id}/return-rate` route and add an
   equivalent for DC/iDeCo funds, with proper `@DecimalMin`/`@DecimalMax` validation on both.
4. Consider deleting `asset-allocation-drawer.tsx` now that it is confirmed dead across two
   consecutive review rounds, since its presence continues to make reviewers believe the live path is
   protected when it is a different component entirely.

> **[FIXED] (2026-08-31)**
> - **Backend**: Added `@Valid` to the `@RequestBody AssetSimulationRequest request` parameter in `AssetSimulationController.runSimulation()`. This ensures that the existing `@DecimalMin` and `@DecimalMax` annotations on `FundOverrideRequest.returnRateOverride` are properly cascaded and evaluated by Spring Boot.
> - **Frontend**: Refactored the live `ExpectedReturnRateEditor` used by `nisa-table.tsx` and `dc-table.tsx`. Removed the hardcoded `"5.00"` fallback (now defaulting to an empty state with a `0.00` placeholder). Added `min="-100" max="100"` HTML constraints, and implemented an explicit interceptor in `handleSave` to block out-of-range values (<-100 or >100) or `NaN` inputs, triggering a `toast.error` instead of issuing an API call.
> - **Backend (Routing/Validation)**: Added `@DecimalMin("-1.0")` and `@DecimalMax("1.0")` to `UpdateNisaReturnRateRequest.java` to enforce validation bounds for NISA return rate updates. No backend changes were needed for DC/iDeCo since `DefinedContributionController`'s existing `UpdateAllocationRequest` already possesses these annotations.
> - **Frontend (Routing)**: Fixed the API routing mismatches. In `nisa-table.tsx`, updated the fetch path to correctly target `/api/finance/assets/nisa/${id}/return-rate` via proxy. In `dc-table.tsx`, repointed the fetch path to reuse the existing, fully-validated `/api/finance/assets/dc/funds/${id}/allocation` endpoint.
> - **Frontend (Cleanup)**: Deleted the confirmed dead code file `asset-allocation-drawer.tsx` to prevent any future reviewer confusion.
>
> **@Claude: Please re-review the codebase to confirm that:**
> **1. The `@Valid` annotation is now correctly applied in `AssetSimulationController.java`, successfully closing the server-side validation bypass hole for the sandbox endpoint.**
> **2. The actual live editor (`expected-return-rate-editor.tsx`) now contains proper client-side bounds validation and handles empty values correctly without the hardcoded "5.00" fallback.**
> **3. The API routing mismatch (Point 3) has been resolved by correcting the fetch URLs in `nisa-table.tsx` and `dc-table.tsx`, and adding the missing bounds validation to `UpdateNisaReturnRateRequest.java`.**
> **4. The dead component `asset-allocation-drawer.tsx` (Point 4) has been permanently deleted.**
