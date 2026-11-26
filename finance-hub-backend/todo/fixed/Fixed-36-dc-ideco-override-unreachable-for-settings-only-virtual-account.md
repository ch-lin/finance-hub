# Fixed-36 — Years override is unreachable for the settings-only virtual DC/iDeCo account, even though that account already exists and already grows money

## Agreed premise (starting point for this finding)
**沒有鏡像 = 沒有帳號。系統沒理由讓使用者靠 override 憑空生出免費投資。**
No snapshot means no account. The system has no reason to let a user use the years-override switch to conjure a simulated investment out of nothing.

This is correct, and the current code already enforces it in the one case where it actually applies: if a user has never uploaded a balance snapshot **and** has never filled in a monthly contribution amount in settings, no `SimulationFund` is created at all for that DC/iDeCo type — turning the override switch on has nowhere to attach to, and no money is simulated. That is the right behavior, and it is not what this finding is about.

## Where "no snapshot" stops meaning "no account" in the current code
"鏡像" (a balance snapshot) is only *one* way an account can exist in the current code. There is a second way: [AssetSimulationEngineImpl.java:690-705](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:690) already builds a real, simulated `SimulationFund` for Corporate DC purely from a settings field, with zero snapshot present:

```java
} else if (dcSettingsOpt.isPresent() && dcSettingsOpt.get().getMonthlyDcMatchingContribution() != null
        && dcSettingsOpt.get().getMonthlyDcMatchingContribution() > 0) {
    ...
    var dummyFund = SimulationFund.builder()
            .fundId(0L)
            .fundName("Corporate DC (Profile Setting)")
            .balance(0L)
            .monthlyContribution(dcSettingsOpt.get().getMonthlyDcMatchingContribution())
            .annualContribution(dcSettingsOpt.get().getMonthlyDcMatchingContribution() * 12L)
            .expectedReturnRate(expectedReturn)
            .build();
    context.getDcFunds().add(dummyFund);
}
```

Same for iDeCo at [lines 748-763](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:748), gated on `monthlyIdecoContribution > 0`.

This is not the system giving the user free money. `monthlyDcMatchingContribution`/`monthlyIdecoContribution` are numbers the user typed in themselves, describing their own real, existing monthly contribution. The system is respecting user-provided real data — the same category of input as the years-override number. Once this branch fires, an account demonstrably exists: it has a balance, a monthly contribution, a return rate, and it gets compounded forward in the simulation and fed into tax calculations downstream. By the premise above, **"no account" no longer applies to this branch — an account exists, just without a snapshot backing it.**

## The exact lines where this breaks
The years-override read is gated on the wrong condition. It checks "does a snapshot exist," not "does an account exist":

- DC override is read at [lines 655-658](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:655), which only runs inside `if (latestDcOpt.isPresent())` (line 652) — the *snapshot* branch.
- The settings-only virtual account is built in the `else if` at line 690 — the sibling branch of that same `if`. Because it's an `else if`, the override code at 655-658 can never run at the same time as the fund-creation code at 690-705. The virtual fund gets created with contribution years permanently defaulted to the JVM default `0`, with no code path anywhere that can change it.
- Identical split for iDeCo: override read at [713-716](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:713) (inside the `if (latestIdecoOpt.isPresent())` snapshot branch) vs. the virtual-fund `else if` at 748-763.

## Why it matters
A user who has never uploaded a DC/iDeCo balance screenshot, but has correctly filled in their real monthly contribution amount and turned on the years override with their real years of service, gets an account that is simulated (it grows, it compounds, it affects projected assets) — but its retirement-income tax deduction is always calculated as if they have 0 years of service, with no way to correct it. This understates their deduction and understates their projected assets for exactly the population #4's fix was built to help.

## Suggested fix
Keep this settings-only virtual account (it is real user-declared data, not a fabricated value — removing it would just re-create the original "dead-end fields" problem #4 fixed). Read the override inside the settings-only virtual-account branches too, not only inside the two snapshot-`isPresent()` branches:
- Add the same override check (currently at lines 655-658) to the `else if` at lines 690-705.
- Add the same override check (currently at lines 713-716) to the `else if` at lines 748-763.

This keeps the original premise intact — an override still requires an account to exist (snapshot **or** settings-configured contribution amount) — it just stops using "snapshot exists" as a stand-in for "account exists," which is no longer accurate since the #4 fix.

## Separate naming problem: `dummyFund` is a misleading name
The local variable is literally named `dummyFund` in all three places this pattern is used — [line 598](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:598) (NISA), [line 696](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:696) (DC), [line 754](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:754) (iDeCo) — plus a code comment at line 605 ("`// Default to Growth for dummy`").

Checked: the literal word "dummy" does not currently reach any user-facing string. The `fundName` values actually sent to the frontend are `"NISA (Profile Setting)"`, `"Corporate DC (Profile Setting)"`, `"iDeCo (Profile Setting)"` — not "dummy" anything. So there is no current UI leak.

But "dummy" is still the wrong name for what this is, and it needs to change in the code itself, not just be understood by whoever wrote it:
- It reads as "placeholder / fake / throwaway," when it's actually a real fund model built from a real number the user deliberately entered in their profile settings.
- Right now, the only person who reliably knows what this variable actually represents is whoever wrote that line. Any other engineer — or another AI picking up this file cold in a future session — reading `dummyFund` later has no reason to know it holds real, compounding, tax-relevant user data. That confusion risk exists independent of whether the word ever reaches a UI string.
- A future change (a new field, a debug log, an error message, a raw object dump to the frontend for troubleshooting) could also easily carry this variable name or its terminology into something a user actually sees.

**Suggested fix**: rename `dummyFund` → `profileSettingsFund` (or similarly explicit: `settingsDerivedFund`) in all three occurrences, and update the line-605 comment to match. This costs nothing functionally and removes the risk at the source instead of relying on nobody ever copying the variable name forward.

## Resolution
**Status: Fixed**

The implementation has been corrected. 
1. **Years Override Fix**: We added the missing override check inside the `else if` branches for both Corporate DC and iDeCo virtual accounts in `AssetSimulationEngineImpl.java`. The system now correctly applies the user's manual override years for these settings-derived accounts, resolving the issue of understating the tax deduction.
2. **Variable Renaming**: We renamed the misleading local variable `dummyFund` to `settingsDerivedFund` across NISA, Corporate DC, and iDeCo branches to accurately reflect that the fund holds real, tax-relevant user data. The corresponding comment was also updated.
