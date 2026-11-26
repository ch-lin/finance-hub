# Fixed-35 — Turning off the DC/iDeCo years override does not clear the stored override number

## Problem
Disabling the DC or iDeCo "manual override" switch and saving does not erase the previously-saved override number from the database. Only the boolean flag (`isDcContributionYearsOverridden` / `isIdecoContributionYearsOverridden`) is updated to `false`. The number itself (`dcContributionYearsOverride` / `idecoContributionYearsOverride`) stays whatever it was last set to — permanently, with no way to clear it through the save endpoint.

## Evidence
[DefinedContributionUserSettingsServiceImpl.saveSettings()](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsServiceImpl.java:30) (lines 30-63). The relevant setters, lines 46-58:

```java
if (command.dcContributionYearsOverride() != null) {
    settings.setDcContributionYearsOverride(command.dcContributionYearsOverride());
}
...
if (command.idecoContributionYearsOverride() != null) {
    settings.setIdecoContributionYearsOverride(command.idecoContributionYearsOverride());
}
```

Every setter in this method is guarded by `!= null`, with no `else` branch. This method can only ever write a new number into these two fields — it has no code path that writes `null` back into them.

## Why it matters
1. User turns on override, enters 15 years, saves. DB now holds `dcContributionYearsOverride = 15`, `isDcContributionYearsOverridden = true`.
2. User turns the switch off, saves. DB now holds `isDcContributionYearsOverridden = false`, but `dcContributionYearsOverride` is still `15` — unchanged.
3. Months later, the user turns the switch back on without re-entering a number (or the input still shows the old value). The old `15` — a number the user may have forgotten and never re-confirmed — silently comes back into effect.

While the switch is off, this has no effect on simulation output, since override is only read when the flag is `true`. The problem is the stale number sitting in the database with no way to clear it, and step 3 being a realistic path to a wrong, unreviewed number quietly reactivating.

## Suggested fix
When a save request sets `isDcContributionYearsOverridden` / `isIdecoContributionYearsOverridden` to `false`, also unconditionally write `null` into the corresponding override number field in that same save call, instead of leaving that field to the generic `!= null` guard.

## Resolution
**Status: Won't Fix (By Design)**

We have decided not to implement this change due to UX considerations. This behavior is intentionally designed to preserve the user's previous input. 

If a user accidentally toggles the manual override switch off, they should be able to simply toggle it back on without losing their entered value. Forcing the value to clear would require the user to manually re-enter their contribution years after an accidental toggle, which is an annoying and disruptive user experience. 

Therefore, retaining the stale number in the database while the flag is `false` is the intended behavior and does not impact the simulation output since the override is only applied when the flag is `true`.
