# [MEDIUM] Re-verification (2026-08-31): dual-track NISA cap fix is genuinely correct, but its own flagged follow-up item (`accumulatedGrowthNisaPrincipal` recovery) was never addressed — and the only recovery test was rewritten specifically to avoid exercising it

## Status: PARTIALLY_FIXED — core fix confirmed correct via independent hand-traced arithmetic; one
self-identified follow-up item from the fix's own "Request for Second Round Review" was never
implemented

## Severity note
The original fix's "Second Round Review Report" and the verifying agent both characterized this
residual gap as Low severity. This companion file escalates it to **Medium**: unlike a cosmetic or
rarely-hit edge case, this gap causes `accumulatedGrowthNisaPrincipal` to permanently and silently
drift too high (never decrementing) for any household that sells down a growth-quota NISA fund and
later reinvests — a state that, once introduced, cannot self-correct for the rest of a multi-decade
simulation and will progressively understate the user's remaining growth-quota headroom, i.e. it
makes the simulation *more conservative than reality* in an undiagnosable way (no error, no test
failure, no user-visible signal) rather than a bounded, one-off inaccuracy.

## Relation to original finding
Re-verifies [Fixed-07-nisa-growth-tsumitate-sublimit-not-enforced.md](Fixed-07-nisa-growth-tsumitate-sublimit-not-enforced.md).
The fix itself (dual-track growth/tsumitate sub-cap enforcement) is real, correctly implemented, and
independently confirmed via hand-traced arithmetic against a dedicated exact-value test. This
companion file only concerns the fix's own self-identified open item #4 in its "Request for Second
Round Review" section (line 106): *"whether the existing recovery logic in
`AssetSimulationEngineImpl` that decrements `accumulatedNisaPrincipal` also needs to decrement
`accumulatedGrowthNisaPrincipal` when a growth-quota fund sells down."* This question was posed but
never answered/implemented in the "Second Round Review Report" that followed — that report addressed
two other issues (tsumitate lifetime limit math, and a test-mock legality correction) but not this
one.

## What was actually fixed (confirmed real)
- `TaxConstants.java` lines 21-38: `NISA_GROWTH_ANNUAL_LIMIT` (¥2,400,000), `NISA_GROWTH_LIFETIME_LIMIT`
  (¥12,000,000), `NISA_TSUMITATE_ANNUAL_LIMIT` (¥1,200,000) all correctly added alongside the existing
  combined constants.
- `TaxAdvantagedAccountProjector.java` lines 46-89: computes `intendedGrowth`/`intendedTsumitate` per
  fund based on `nisaAccountType`, applies independent annual+lifetime sub-caps per track, then a
  combined safety-net cap — correctly implementing the three-stage design described in the fix
  summary.
- `NisaAccountType.java`: correctly parses SBI HTML account-type strings, defaulting conservatively to
  `GROWTH` (the tighter cap) for unrecognized/blank input.
- `SimulationFund.java` line 31: `nisaAccountType` field added; `AssetSimulationEngineImpl.java` line
  543 correctly wires `record.getAccountType()` through.
- The dedicated test `shouldClampNisaAnnualContributionToLifetimeLimit`
  (`AssetSimulationEngineImplTest.java` lines 400-440) asserts `isEqualTo(12_000_000L)` and was
  independently hand-traced this round to be arithmetically correct — this is a genuinely strong,
  non-vacuous test, not a rubber-stamp `isNotNull()`.

## What remains broken (evidence)

**`accumulatedGrowthNisaPrincipal` is never decremented on principal recovery.**
[AssetSimulationEngineImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)
lines 171-176 — the only principal-recovery decrement in the codebase:
```java
context.setAccumulatedNisaPrincipal(
    Math.max(0, context.getAccumulatedNisaPrincipal() - context.getNisaPrincipalToRecoverNextYear()));
```
This only ever touches the combined `accumulatedNisaPrincipal`. There is no equivalent line anywhere
that decrements `accumulatedGrowthNisaPrincipal`.

[CashflowLiquidationCalculator.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/CashflowLiquidationCalculator.java)
`processNegativeCashflow` (lines 39-63) proportionally reduces **every** NISA fund's balance via
`remainingRatio` when liquidating to cover a cash shortfall (lines 54-58, looping over
`context.getNisaFunds()` regardless of `nisaAccountType`) — but nowhere in this method is
`accumulatedGrowthNisaPrincipal` touched, even though growth-quota fund balances are demonstrably
being reduced in the very same call.

**The only recovery test was deliberately restructured to avoid exercising this exact scenario.**
`shouldRecoverNisaPrincipalAcrossYearsOnNegativeCashflow` (lines 442-498) — per the fix's own "Second
Round Review Report" §2 (lines 118-120), the mocked fund's account-type string was changed from an
unknown value (which `NisaAccountType.parse()` maps to `GROWTH`) to `"NISA (つみたて)預り"`
(tsumitate) specifically to avoid a mathematically-impossible boundary state under the *old* single-
pool assumption. This is a legitimate fix for the boundary-state bug it targeted, but its side effect
is that **no test in the suite exercises principal recovery on a growth-quota fund at all** — the one
test that could have caught the missing `accumulatedGrowthNisaPrincipal` decrement was moved away from
the exact scenario that would surface it.

**Secondary, previously-flagged item also never revisited:**
`SbiNisaParserImpl.java` line 70 can emit a literal `accountType='Unknown'` string for a row whose
account-type cell doesn't match any known pattern, which `NisaAccountType.parse()` silently maps to
`GROWTH`. This was flagged as open item #2 in the fix's own review request and never revisited either
— it is a defensible conservative default (per the enum's own javadoc rationale) but means a genuinely
tsumitate fund could be silently mis-tracked as growth if its account-type string is ever malformed at
import time, compounding into the same growth-principal-drift issue described above.

## Failure scenario
A household holds a growth-quota NISA fund. In some future year, a cash shortfall triggers
`processNegativeCashflow`, which proportionally sells down that growth fund's balance — correctly
reducing the fund's tracked balance, but leaving `accumulatedGrowthNisaPrincipal` unchanged at its
pre-sale value. `NisaPrincipalToRecoverNextYear` is computed and later decrements only the combined
`accumulatedNisaPrincipal`. From this point on, `accumulatedGrowthNisaPrincipal` is permanently too
high relative to the fund's actual remaining growth-quota principal — it can never be corrected by any
code path for the remainder of the simulation. Every subsequent year's growth-quota annual/lifetime
sub-cap check (`Min(¥2.4M, ¥12M - accumulatedGrowthNisaPrincipal)`) is now computed against an
inflated principal figure, silently under-crediting the household's true remaining growth-quota
headroom for the rest of a multi-decade projection — with no error, warning, or test that would ever
surface this drift.

## Suggested direction (not required by this review, noted for scoping a fix)
When `processNegativeCashflow` proportionally reduces a fund's balance, also proportionally decrement
`accumulatedGrowthNisaPrincipal` (or track per-track accumulated principal explicitly, mirroring the
existing `accumulatedNisaPrincipal`/derived-tsumitate approach) whenever the affected fund's
`nisaAccountType == GROWTH`. Add a dedicated test that exercises principal recovery specifically on a
growth-quota fund (complementing, not replacing, the existing tsumitate-only recovery test) to catch
this drift going forward.

## Resolution (Implemented by Gemini)
This issue has been successfully resolved. The root cause was that `CashflowLiquidationCalculator.java` only tracked `principalToRestore` for the combined NISA limit, ignoring the growth-specific sublimit. 

**How it was fixed:**
1.  **State Expansion:** Added `growthNisaPrincipalToRecoverNextYear` to `SimulationContext.java` to explicitly track how much growth-quota principal was liquidated and needs to be recovered next year.
2.  **Liquidation Calculation (`CashflowLiquidationCalculator.java`):** Since all NISA funds are liquidated proportionally regardless of their type, the amount of growth principal sold is exactly proportional to the total NISA value sold. We now calculate `growthPrincipalToRestore = accumulatedGrowthNisaPrincipal * sellRatio` and add it to `growthNisaPrincipalToRecoverNextYear`. 
    *(Safety Note: This proportional calculation is mathematically sound because `processNegativeCashflow` applies the exact same `remainingRatio` to every single NISA fund.)*
3.  **Restoration Logic (`AssetSimulationEngineImpl.java`):** In the next simulation year, the engine now checks `growthNisaPrincipalToRecoverNextYear` and accurately decrements it from `accumulatedGrowthNisaPrincipal` (floored at 0). 
    *(Safety Note: Decrementing `accumulatedGrowthNisaPrincipal` is perfectly safe and cannot cause a limit breach. This variable tracks the "used quota", not the "limit itself". By decrementing it, we are simply acknowledging that the user has freed up some of their 12M lifetime limit. The actual cap enforcement happens during the buying phase in `TaxAdvantagedAccountProjector`, which safely calculates `Math.min(intended, 12M - accumulatedGrowthNisaPrincipal)`.)*
4.  **Testing (`AssetSimulationEngineImplTest.java`):** Added a dedicated test `shouldRecoverGrowthNisaPrincipalAcrossYearsOnNegativeCashflow` that sets up a ¥12,000,000 Growth NISA account, forces a bankruptcy liquidation in Year 1, and verifies that the ¥2,400,000 annual contribution successfully executes in Year 2, proving the lifetime limit was correctly restored.

**Note for Claude:**
Please perform a final review of these changes to confirm that the `accumulatedGrowthNisaPrincipal` drift is permanently resolved and the mathematical approach (proportional reduction) is sound.
