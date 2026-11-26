# [RESOLVED] Re-verification (2026-09-02): `lifeExpectancy` genuinely wired end-to-end as `Integer`, correct Optional handling on update — CONFIRMED_FIXED

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies the "## Fix" section appended to [Fixed-31-createfinancehubconfigrequest-missing-lifeexpectancy-field.md](Fixed-31-createfinancehubconfigrequest-missing-lifeexpectancy-field.md), which claimed `Integer lifeExpectancy` was added to both Create/Update request DTOs and Commands, mapped in the controller, and applied in the service.

## Independently verified (confirmed real)

**Field genuinely declared, duplicate annotations cleaned up.** `CreateFinanceHubConfigRequest.java:90-95`:
```java
@NotNull(message = "Life expectancy cannot be null")
@Min(value = 0, message = "Life expectancy must be positive")
private Integer lifeExpectancy = 85;
```
`targetRetirementAge` immediately following now has exactly one `@NotNull` and one `@Min` — the duplication described in the original finding is gone. `UpdateFinanceHubConfigRequest.java:70-73` has `private Integer lifeExpectancy;`, consistent with sibling partial-update fields.

**Type consistent as `Integer` at every layer.** `CreateFinanceHubConfigRequest.java:95` → `UpdateFinanceHubConfigRequest.java:73` → `CreateFinanceHubConfigCommand.java:81` → `UpdateFinanceHubConfigCommand.java:88-89` (`Optional<Integer>`) → entity `FinanceHubConfig.java:247-251` (`@NotNull @Column(nullable=false) private Integer lifeExpectancy = 85;`) → `FinanceHubConfigResult.java:28` → `FinanceHubConfigResponse.java:20`. No mismatch anywhere — the original finding's speculation that the response type might differ is refuted.

**Wiring complete for both create and update, correct Optional semantics.**
- Controller create: `FinanceHubConfigController.java:120` — `.lifeExpectancy(request.getLifeExpectancy())`.
- Controller update: `FinanceHubConfigController.java:199` — `.lifeExpectancy(Optional.ofNullable(request.getLifeExpectancy()))`.
- Service create: `FinanceHubConfigServiceImpl.java:183-185` — sets the value if present, otherwise keeps the entity's default (85).
- Service update: `FinanceHubConfigServiceImpl.java:335` — `command.getLifeExpectancy().ifPresent(config::setLifeExpectancy)`; omitting the field on update preserves the existing DB value, exactly mirroring the pattern used for every other optional field in the same method (e.g. `defaultCurrency` at line 330). No field-specific bug.

## Noted gap (same scope, not required to fix now)
Zero test coverage anywhere for `lifeExpectancy`: `FinanceHubConfigControllerTest.java`, `FinanceHubConfigServiceImplTest.java`, `CreateFinanceHubConfigCommandTest.java`, `UpdateFinanceHubConfigCommandTest.java`, and the entity test all exist but none mention this field. A future regression on the create-default or update-preserve-on-omit path would go undetected. Worth a follow-up test asserting: (a) create without `lifeExpectancy` yields the entity default; (b) update omitting `lifeExpectancy` preserves the existing value; (c) update with an explicit value overwrites it.

## On execution
Could not compile — sandbox denies network egress needed for Maven's parent-POM resolution, independent of this change (root and module `<parent>` blocks match exactly). Conclusions rest on direct reading of all 7 touched files, which found no mismatches.

## Verdict
Genuinely fixed. No further action needed on this finding.
