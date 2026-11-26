# Reply to FINDING-12-02's "Resolution (Implemented / Rejected)" notes — re-verified against live source

## Summary

| Item | Verdict |
|---|---|
| #1 — override escape hatch wiring | ✅ Fixed |
| #4 — 5 dead-end settings fields | ✅ Fixed |
| #5 — Salary null-handling inconsistency | ✅ Original claim no longer applies (already correct) |

Item #2 (UI fallback indicator) is intentionally excluded from this review — profile UI is being redesigned separately, revisit after that redesign ships.

Item #3 (override rejected as "False Alarm") is written up on its own, in more depth, as [Fixed-36](Fixed-36-dc-ideco-override-unreachable-for-settings-only-virtual-account.md) — moved out of this file since it needed a full explanation of the underlying premise, not a short summary.

A separate, unrelated bug found while checking #1 is written up on its own as [Fixed-35](Fixed-35-dc-ideco-override-value-not-cleared-on-disable.md) — kept out of this file so this stays one topic.

---

## #1 — Override escape hatch wiring
**Verdict: ✅ Fixed.**

All 4 override fields (`isDcContributionYearsOverridden`, `dcContributionYearsOverride`, `isIdecoContributionYearsOverridden`, `idecoContributionYearsOverride`) are now present everywhere they need to be:
- [profile/me/page.tsx](hub-ui/hub-ui/src/app/finance/profile/me/page.tsx) — initial `formData` (29-32), hydration effect (55-58), save payload (85-88).
- [onboarding/page.tsx](hub-ui/hub-ui/src/app/finance/onboarding/page.tsx) — initial `formData` (35-38), hydration effect (71-74), submit payload (110-113).

Backend chain (`DcUserSettingsRequest` → `SaveDcUserSettingsCommand` → `DefinedContributionUserSettingsServiceImpl` → entity) was already correct before this fix and is unchanged.

The override escape hatch now works end-to-end.

---

## #4 — 5 dead-end settings fields
**Verdict: ✅ Fixed.**

- `NisaUserSettingsRepository` is now injected ([line 81](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:81)) and queried ([line 574](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:574)).
- `monthlyNisaContribution` / `nisaExpectedReturnRate` are consumed at lines 592-608 and 630-635.
- `monthlyDcMatchingContribution` / `monthlyIdecoContribution` / `idecoExpectedReturnRate` are consumed at lines 690-705 / 748-763.
- Downstream effect is real, not cosmetic: `taxEngine.calculate(...)` (lines 253-254) and `TaxAdvantagedAccountProjector` both consume the resulting fund objects.
- New test added: `AssetSimulationEngineImplTest.shouldUseUserSettingsAsFallbackWhenSnapshotsAreMissing` (lines 729-783), asserting all 5 fields flow through correctly.

Design note, stated as intentional by the implementer, not a defect: once a real snapshot exists for an account, the settings-derived amount for that account is never consulted again — real data always wins over the settings value.

---

## #5 — Salary null-handling inconsistency
**Verdict: ✅ Original claim no longer applies.**

[SalaryUserSettingsServiceImpl.saveSettings()](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryUserSettingsServiceImpl.java:34) (lines 34-54) now null-guards all 3 fields, matching the same pattern already used by `NisaUserSettingsServiceImpl` and `DefinedContributionUserSettingsServiceImpl`. The inconsistency originally reported is not present in the current code.

---

## Still open
See [Fixed-36](Fixed-36-dc-ideco-override-unreachable-for-settings-only-virtual-account.md) and [Fixed-35](Fixed-35-dc-ideco-override-value-not-cleared-on-disable.md).
