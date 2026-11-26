# [LOW] Re-verification (2026-09-02): "Defense in Depth" age-75 upper-bound clamps confirmed correct across engine, DTO validation, and frontend layers

## Status: CONFIRMED_FIXED — all three claimed layers independently verified against current source; one latent (non-exploitable today) robustness gap noted, plus one unrelated pre-existing DTO defect discovered incidentally.

## Relation to original finding
Re-verifies [Fixed-13-dc-ideco-unlock-age-no-upper-bound.md](Fixed-13-dc-ideco-unlock-age-no-upper-bound.md)'s "Resolution Report", which claimed: (1) `Math.min(75, ...)` added to `SimulationParameterResolver`'s three resolver methods; (2) `@Min(60)`/`@Max(75)` added to the relevant fields on `CreateFinanceHubConfigRequest`/`UpdateFinanceHubConfigRequest`/`UserProfileRequest`; (3) matching frontend Zod/HTML constraints.

## What was verified fixed (confirmed real)

**Engine layer.** `SimulationParameterResolver.java:89-94` (`resolveIdecoUnlockAge`) and `:100-105` (`resolveDcUnlockAge`) both now read `Math.max(60, Math.min(75, config.getXUnlockAge()))` — correctly clamps into `[60,75]`. `resolveTaxAdvantagedWithdrawalAge` (`:111-117`) computes `minUnlockAge = Math.max(dcUnlockAge, idecoUnlockAge)` from its (already-clamped) inputs, then for a profile override: `Math.max(minUnlockAge, Math.min(75, profile.getTaxAdvantagedWithdrawalAge()))` — an override of e.g. `200` is clipped to `75` by the inner `Math.min` before the outer `Math.max`, so no uncapped value can pass through. The only production call site, `AssetSimulationEngineImpl.java:133-136`, always pipes `dcUnlockAge`/`idecoUnlockAge` through the two resolver methods first, so the current production behavior is fully bounded.

**DTO validation, with real cascade.** `CreateFinanceHubConfigRequest.java:106-125` and `UpdateFinanceHubConfigRequest.java:79-92` both have `@Min(60)`/`@Max(75)` on `pensionStartAge`/`idecoUnlockAge`/`dcUnlockAge` (update DTO correctly omits `@NotNull`, preserving optional-on-patch semantics). `UserProfileRequest.java:21,32` has the same on `pensionStartAge`/`taxAdvantagedWithdrawalAge`, also without `@NotNull` (Jakarta's `@Min`/`@Max` validators pass `null` through, so optionality is preserved). Confirmed `@Valid` cascade is actually wired at all three entry points: `FinanceHubConfigController.java:107-109` (create), `:185-187` (patch), `UserProfileController.java:39-41` — so this is not the inert-annotation anti-pattern seen elsewhere in this codebase.

**Frontend.** `use-finance-configs.ts:23-25` has matching Zod `.min(60).max(75)`; `system-config-panel.tsx:155-182` and `user-profile-form.tsx:136-151` have `min={60} max={75}` on the corresponding inputs.

**No regression.** No dedicated unit tests exist for the three resolver methods; the one integration test that sets these values (`AssetSimulationEngineImplTest.java:279-280`, `dcUnlockAge=60`/`idecoUnlockAge=60`) is well within the new bound. No sentinel/placeholder value (e.g. `999` for "no unlock") was found anywhere in source or tests that the new clamp would break.

## Latent gap (not currently exploitable, noted for awareness)
`resolveTaxAdvantagedWithdrawalAge` takes raw `int dcUnlockAge, int idecoUnlockAge` rather than a config object, and trusts the caller to have already clamped them — it does not itself re-clamp `minUnlockAge`. Today's single call site always pre-clamps, so this is safe in production, but the method has no defense-in-depth of its own if a future caller (e.g. a new test or a different service) invokes it directly with unclamped ints.

## New, functionally-unrelated issue discovered incidentally
While reading `CreateFinanceHubConfigRequest.java` to verify the age fields, found that the file is missing a `lifeExpectancy` field entirely — filed separately as [Fixed-31-createfinancehubconfigrequest-missing-lifeexpectancy-field.md](Fixed-31-createfinancehubconfigrequest-missing-lifeexpectancy-field.md), since it has no relation to the age-clamp bug this finding is about.

## Suggested direction (not required, optional hardening)
Have `resolveTaxAdvantagedWithdrawalAge` clamp its own inputs (or accept a `FinanceHubConfig` and call the other two resolvers internally) so its correctness doesn't depend on caller discipline.
