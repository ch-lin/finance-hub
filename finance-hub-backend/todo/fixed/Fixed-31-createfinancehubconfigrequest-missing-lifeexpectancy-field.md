# [LOW] `CreateFinanceHubConfigRequest` has no `lifeExpectancy` field — its Javadoc/annotations are orphaned onto the next field, so life expectancy can never be set when creating a new config

## Severity: Low (a create request simply can't carry this value; the field can still presumably be set later via update, so this is a create-time gap, not data corruption)

## Note on discovery
Found incidentally while re-verifying [Fixed-13-01.md](Fixed-13-01.md)'s age-clamp fix — functionally unrelated to that finding (age-clamp validation vs. a missing field entirely), filed as a fresh top-level finding per the standing review convention.

## Summary
`CreateFinanceHubConfigRequest.java` has a Javadoc comment block and `@NotNull`/`@Min(0)` annotations (lines ~90-94) clearly intended for a `lifeExpectancy` field, but there is no field declaration between the annotations and the next actual field — so the annotations fall through and attach to `targetRetirementAge` (line 101) instead. This compiles cleanly (Jakarta Bean Validation's `@NotNull`/`@Min` are repeatable via `NotNull.List`/`Min.List`, so `targetRetirementAge` just ends up with two redundant copies of each), but the net effect is that `CreateFinanceHubConfigRequest` has **no `lifeExpectancy` field at all**, even though `FinanceHubConfigResponse.java:20` returns one and (per the Fixed-13 re-verification agent's reading) `UpdateFinanceHubConfigRequest` presumably still has it.

## Evidence
[CreateFinanceHubConfigRequest.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/dto/request/CreateFinanceHubConfigRequest.java)
lines ~90-101 — the `lifeExpectancy` Javadoc and its `@NotNull`/`@Min(0)` annotations directly precede `targetRetirementAge`'s own declaration, with no field of its own in between.

## Failure scenario
A user creating a brand-new `FinanceHubConfig` has no way to submit a `lifeExpectancy` value in the same request — the field is silently absent from the DTO. If any downstream logic (e.g. bankruptcy-risk projection, `PensionCalculator`) reads `lifeExpectancy` from a freshly-created config before an explicit update call ever sets it, it would see whatever the entity's default/null value is, potentially skewing a new user's very first simulation run until they separately edit their config.

## Suggested direction (not required by this review, noted for scoping a fix)
Add the missing `Long lifeExpectancy` (or appropriate type, matching `UpdateFinanceHubConfigRequest`/`FinanceHubConfigResponse`) field declaration between the orphaned annotations and `targetRetirementAge`, and remove the now-duplicated `@NotNull`/`@Min(0)` off `targetRetirementAge` if they aren't independently intended for that field too.

## Fix
The missing `lifeExpectancy` field has been added across the entire request mapping chain:
- Added `Integer lifeExpectancy` to `CreateFinanceHubConfigRequest` and `UpdateFinanceHubConfigRequest`.
- Added corresponding properties to `CreateFinanceHubConfigCommand` and `UpdateFinanceHubConfigCommand` (as `Optional<Integer>`).
- Mapped the new fields in `FinanceHubConfigController`.
- Set the `lifeExpectancy` value when creating/updating the configuration entity in `FinanceHubConfigServiceImpl`.
