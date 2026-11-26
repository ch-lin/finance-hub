# [RESOLVED] Re-verification (2026-09-02): `@Valid` cascade and field constraints genuinely added and correctly wired to the controller boundary — CONFIRMED_FIXED (plus a minor semantic note; a sibling-endpoint gap is filed separately as FINDING-40)

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies the "## Fix" section appended to [Fixed-32-assetsimulationrequest-mortgageearlyrepayments-unvalidated.md](Fixed-32-assetsimulationrequest-mortgageearlyrepayments-unvalidated.md), which claimed `@Valid` was added to both `mortgageEarlyRepayments` and `mortgageRateScenarios`, plus constraints on both nested DTO types.

## Independently verified (confirmed real)

**`@Valid` genuinely present on both map fields, correctly cascading into list elements.** `AssetSimulationRequest.java:19,21`:
```java
@Valid Map<Long, List<@Valid MortgageEarlyRepaymentRequest>> mortgageEarlyRepayments,
...
@Valid Map<Long, List<@Valid MortgageRateScenarioRequest>> mortgageRateScenarios) {
```
Import is `jakarta.validation.Valid`, consistent with the rest of the codebase (e.g. `MortgageRecordRequest.java:11`) — no javax/jakarta mixing.

**Correct `MortgageEarlyRepaymentRequest` (disambiguated from the unrelated same-named class), constraints confirmed.** `AssetSimulationRequest.java:35-38`:
```java
public record MortgageEarlyRepaymentRequest(
        @NotNull @PositiveOrZero Integer year,
        @NotNull @Positive Long amount) {
}
```
Verified via `AssetSimulationMapper.java:28` that this is genuinely the type in use here — distinct from `MortgageRecordRequest.MortgageEarlyRepaymentRequest` (fields `id`/`repaymentDate`/`amount`/`repaymentType`/`fee`, already fixed under Fixed-28-02). No cross-contamination between the two same-named classes.

**`MortgageRateScenarioRequest` constraints confirmed present.** `AssetSimulationRequest.java:30-33`: `@NotNull @PositiveOrZero Integer startYear`, `@NotNull @PositiveOrZero Double rate`.

**Controller boundary genuinely wired — the cascade actually takes effect.** `AssetSimulationController.java:44`: `@Valid @RequestBody(required = false) AssetSimulationRequest request` — confirmed the primary `/simulation` endpoint has `@Valid` on the request parameter itself, so this fix's new constraints are genuinely enforced at runtime for that endpoint, not just declared and inert.

## Minor semantic note (low severity, not a functional bug)
`@PositiveOrZero` on `startYear` (and equivalently `year` in `MortgageEarlyRepaymentRequest`) is nearly vacuous: both are used as **absolute calendar years**, not relative offsets (confirmed via `MortgageCalculator.java:63,90` comparing against real `YearMonth` values derived from `context.getYear()`). `@PositiveOrZero` would still admit nonsensical years like `1` or `1899`. A tighter bound (e.g. `@Min(2000)`, or validation relative to the simulation's current year) would be more meaningful — worth a future tightening pass, not urgent. `@PositiveOrZero` on `rate` is defensible but debatable: the codebase's own `MortgageRecordRequest.interestRate` has no lower bound at all, and BOJ-era negative rates aren't implausible for a what-if scenario tool — not clearly wrong, just an inconsistency to be aware of if a negative-rate scenario is ever needed.

## Noted gap (same scope, not required to fix now)
No test (MockMvc/`@WebMvcTest`) verifies a 400 response for a negative `amount`/nonsensical `year` submission — same class of coverage gap as Fixed-31-01.

## New, functionally distinct issue discovered incidentally
While confirming the controller boundary above, found that a **sibling** endpoint (`GlobalOptimizerController`) accepts the exact same `AssetSimulationRequest` type via a wrapper DTO with **zero** `@Valid` anywhere in that path — meaning none of this fix's constraints (or any future ones) are enforced when reached through the optimizer endpoint. This is a different controller/wrapper DTO, not literally part of this finding's scope (which was `AssetSimulationRequest`'s own field annotations, now correctly done) — filed separately as [Fixed-33](Fixed-33-globaloptimizerrequest-baseoverrides-missing-valid-cascade.md).

## On execution
Could not compile/run tests — same sandbox network/`.m2` blockers as other findings in this round. Conclusions rest on direct reading of the DTO, mapper, and controller chain, which found consistent types and imports throughout.

## Verdict
Genuinely fixed for the literal ask. No further action needed on this finding itself; see Fixed-33 for the newly discovered sibling-endpoint gap.
