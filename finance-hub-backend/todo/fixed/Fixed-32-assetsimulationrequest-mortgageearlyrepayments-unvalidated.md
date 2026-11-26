# [LOW/MEDIUM] `AssetSimulationRequest.mortgageEarlyRepayments` has neither a `@Valid` cascade nor any field-level constraints on its nested type — the same anti-pattern class as FINDING-28/34, at a different call site

## Severity: Low/Medium (malformed simulation-override data can be submitted and processed with no server-side rejection; scoped to an ad-hoc simulation request, not persisted account data, but still feeds directly into mortgage-simulation math)

## Note on discovery
Found incidentally while re-verifying [Fixed-28-02.md](Fixed-28-02.md)'s claim that `MortgageRecordRequest`'s nested `MortgageEarlyRepaymentRequest` now has real validation — while confirming that record wasn't a naming collision with another type of the same name, found this second, genuinely different, unrelated `MortgageEarlyRepaymentRequest` (Asset Simulation feature, not Mortgage Record CRUD) with no validation at all. Filed as a fresh top-level finding since it's a different feature/DTO, not part of Fixed-28's mortgage-management scope, even though it's the same validation-cascade anti-pattern already fixed twice elsewhere (Fixed-23/23-01, Fixed-28/28-01).

## Summary
`AssetSimulationRequest.java:15` declares `Map<Long, List<MortgageEarlyRepaymentRequest>> mortgageEarlyRepayments` with no `@Valid` annotation, and its nested `MortgageEarlyRepaymentRequest` (lines ~33-36, fields `year`/`amount` — distinct from the `MortgageRecordRequest`-nested type of the same simple name, which has `repaymentDate`/`amount`/`repaymentType`/`fee`/`id`) has zero field-level constraints of its own. Even if `@Valid` were added to the map, there would be nothing yet for it to cascade into.

## Evidence
[AssetSimulationRequest.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/dto/request/AssetSimulationRequest.java)
line 15: `private Map<Long, List<MortgageEarlyRepaymentRequest>> mortgageEarlyRepayments;` — no `@Valid`.
lines ~33-36: nested `MortgageEarlyRepaymentRequest(Long year, Long amount)` (or equivalent) — no `@NotNull`/`@Positive`/range constraints on either field.

## Failure scenario
A simulation-override request submitting `mortgageEarlyRepayments` with a negative `amount` or a nonsensical `year` value passes validation untouched (there's nothing to reject it), and flows directly into whatever mortgage-simulation logic consumes `AssetSimulationRequest`, producing undefined or misleading simulated cash-flow output for that ad-hoc run. Lower severity than the Fixed-23/28-style persisted-data cases since this is a per-request simulation override rather than data written to the database, but it can still silently corrupt a user's simulation results with no error surfaced.

## Suggested direction (not required by this review, noted for scoping a fix)
Add `@Valid` to the `mortgageEarlyRepayments` map field, and add sensible constraints to its nested `MortgageEarlyRepaymentRequest` (e.g. `@NotNull @Positive` on `amount`, a sane bound on `year` — mirroring the constraints already correctly applied to the differently-scoped `MortgageRecordRequest`-nested type of the same name per Fixed-28-02). Worth a repo-wide sweep for any other nested-collection DTOs missing `@Valid`, since this is now the third confirmed instance of this exact pattern in this codebase.

## Fix
- Added `@Valid` to `mortgageEarlyRepayments` and `mortgageRateScenarios` map fields in `AssetSimulationRequest.java`.
- Added `@NotNull` and `@PositiveOrZero` constraints to `MortgageRateScenarioRequest` (`startYear`, `rate`).
- Added `@NotNull`, `@PositiveOrZero` (for `year`), and `@Positive` (for `amount`) constraints to `MortgageEarlyRepaymentRequest`.
