# Fixed-36-01 — Re-verified: both the override-reachability fix and the naming rename are confirmed fixed

## Summary

| Item | Verdict |
|---|---|
| Override readable in settings-only virtual-account branch — DC | ✅ Fixed |
| Override readable in settings-only virtual-account branch — iDeCo | ✅ Fixed |
| `dummyFund` renamed | ✅ Fixed |

Fixed-36 is fully resolved. No further action needed on this finding.

## Override reachability — DC
[AssetSimulationEngineImpl.java:691-696](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:691):

```java
} else if (dcSettingsOpt.isPresent() && dcSettingsOpt.get().getMonthlyDcMatchingContribution() != null
        && dcSettingsOpt.get().getMonthlyDcMatchingContribution() > 0) {
    if (Boolean.TRUE.equals(dcSettingsOpt.get().getIsDcContributionYearsOverridden())) {
        Integer overrideVal = dcSettingsOpt.get().getDcContributionYearsOverride();
        context.setDcContributionYears(overrideVal != null ? overrideVal : 0);
    }
    ...
```

The override is now read inside this branch, before the settings-derived fund is built below it. A user with no DC balance snapshot, but a settings-declared monthly contribution and the override switch on, now gets their declared years of service applied instead of a permanent 0.

## Override reachability — iDeCo
Identical fix at [lines 755-760](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:755):

```java
} else if (dcSettingsOpt.isPresent() && dcSettingsOpt.get().getMonthlyIdecoContribution() != null
        && dcSettingsOpt.get().getMonthlyIdecoContribution() > 0) {
    if (Boolean.TRUE.equals(dcSettingsOpt.get().getIsIdecoContributionYearsOverridden())) {
        Integer overrideVal = dcSettingsOpt.get().getIdecoContributionYearsOverride();
        context.setIdecoContributionYears(overrideVal != null ? overrideVal : 0);
    }
    ...
```

No new bug was introduced by this change. When the override is off, contribution years correctly stay unset (0) in this no-snapshot branch — there is no enrollment date to fall back to here anyway, so this matches the original premise exactly: no snapshot and no override means no years, and that is the safe-side default.

## Naming — `dummyFund` renamed
All three occurrences (NISA, DC, iDeCo) now use the same name, `settingsDerivedFund` — no divergence between the three:
- [Line 598](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:598) (NISA), used at [line 608](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:608)
- [Line 702](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:702) (DC), used at [line 711](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:711)
- [Line 766](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:766) (iDeCo), used at [line 775](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:775)

The comment that previously read `// Default to Growth for dummy` now reads `// Default to Growth for settings-derived fund` ([line 606](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:606)) — updated to match.

A repo-wide, case-insensitive grep for "dummy" across all non-test Java source (`hub`, `hub-app`, `hub-domain`, `hub-common`) returns zero hits. The remaining hits found are all confined to `src/test/java` test classes and test helper names (e.g. `createDummyFile`, `DummyParser`, local test variables `dummyNisa`/`dummyDc`/`dummyIdeco`), which are outside the scope of this finding and use "dummy" in its legitimate test-fixture sense.
