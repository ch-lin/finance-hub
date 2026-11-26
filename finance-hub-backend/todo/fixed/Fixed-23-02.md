# [LOW→CLOSED] Re-verification (2026-09-01): both the backend `@Valid` cascade and the full frontend/backend routing rewrite are confirmed genuinely fixed end-to-end

## Status: CONFIRMED_FIXED — all four fix claims check out against current source, including the two
routing paths (`nisa-table.tsx`, `dc-table.tsx`) that were the actual root cause of round1-M9/Fixed-23
across two prior rounds. One residual gap: no HTTP-level regression test for the sandbox path.

## Relation to original finding
Re-verifies [Fixed-23-01.md](Fixed-23-01.md)'s "[FIXED] (2026-08-31)" section, which claimed: (1)
`@Valid` added to `AssetSimulationController.runSimulation`; (2) `ExpectedReturnRateEditor` refactored
with real bounds-checking and its fake "5.00" fallback removed; (3) `UpdateNisaReturnRateRequest` given
`@DecimalMin`/`@DecimalMax`; (4) `nisa-table.tsx`/`dc-table.tsx` fetch paths corrected to match real
backend routes; (5) `asset-allocation-drawer.tsx` deleted.

## What was verified fixed

**1. Sandbox `@Valid` cascade — genuinely fires now.**
`AssetSimulationController.runSimulation`'s `request` parameter now carries `@Valid`, and the cascade
chain is real: `AssetSimulationRequest`'s `fundOverrides` list is itself `@Valid`, and
`FundOverrideRequest.returnRateOverride` carries `@DecimalMin("-1.0")`/`@DecimalMax("1.0")`. A direct
API call with an out-of-range `returnRateOverride` is now correctly rejected. **Gap**: no test exists
that exercises this at the HTTP layer — the only test touching this area is a service-level unit test
with no `MockMvc`/`@WebMvcTest` assertion of a 400 response. Recommended (not required): add one
`MockMvc` test posting `{"fundOverrides":[{"returnRateOverride": 5.0}]}` and asserting
`status().isBadRequest()`, since this exact "annotation exists but the cascade point wasn't validated"
failure mode has now bitten this codebase twice (this finding, originally).

**2. `UpdateNisaReturnRateRequest` — confirmed real and cascading.** `@DecimalMin("-1.0")`/
`@DecimalMax("1.0")` present on the return-rate field, and `NisaSnapshotRecordController.updateReturnRate`
has `@Valid @RequestBody UpdateNisaReturnRateRequest request` — the validation actually fires, not
inert.

**3. DC/iDeCo path — confirmed the "no backend change needed" claim was accurate.**
`DefinedContributionController`'s `UpdateAllocationRequest` already carried `@DecimalMin`/`@DecimalMax`
on `expectedReturnRate`, and the consuming controller method already has `@Valid` on the request
parameter — this path was never broken.

**4. `ExpectedReturnRateEditor` — all three sub-claims confirmed.** Fake `"5.00"` fallback removed
(now empty-state init from `initialValue`); `min="-100"`/`max="100"` HTML attributes present;
`handleSave` checks `isNaN(parsed) || parsed < -100 || parsed > 100` and calls `toast.error` with an
early `return`, strictly *before* the `onUpdate` callback (and therefore before any network call) is
ever invoked.

**5. Both previously-broken frontend→backend routes now genuinely connect end-to-end.** This is the
part that had already failed twice before (round1-M9 pointed at a dead component; the first Fixed-23
fix added bounds-checking to a live component but never verified its route). This round independently
traced the full chain for both:
- `nisa-table.tsx`'s fetch to `/api/finance/assets/nisa/{id}/return-rate` → Next.js proxy
  (`src/proxy.ts` + `src/lib/api-proxy.ts`, matching `/^\/api\/finance(\/.*)?\$/` and stripping the
  `/api/finance` prefix) → forwards to the real Spring route
  `NisaSnapshotRecordController`'s `@RequestMapping("/assets/nisa")` + `@PatchMapping("/{id}/return-rate")`
  = `/assets/nisa/{id}/return-rate`. Exact match.
- `dc-table.tsx`'s fetch to `/api/finance/assets/dc/funds/{id}/allocation` → same proxy → real route
  `DefinedContributionController`'s `@RequestMapping("/assets/dc")` + `@PatchMapping("/funds/{fundId}/allocation")`
  = `/assets/dc/funds/{fundId}/allocation`. Exact match, and this endpoint's DTO is the
  already-validated `UpdateAllocationRequest` from item 3.

**6. Dead code cleanly removed.** `asset-allocation-drawer.tsx` no longer exists on disk; zero
remaining references (imports, tests, or otherwise) anywhere in the frontend repo.

## Conclusion
All fix claims are genuine, and — critically, given this exact class of bug (validation annotations
present but never actually reached) recurred across two prior rounds of this same finding — the
routing/cascade wiring was independently traced end-to-end rather than taken on the writeup's word.
Only the missing HTTP-level regression test (item 1) remains as an optional hardening suggestion. No
further companion file is needed for this finding.
