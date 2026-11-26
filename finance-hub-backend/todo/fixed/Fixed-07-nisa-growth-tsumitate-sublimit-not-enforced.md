# [MEDIUM] NISA contribution cap only enforces a single flat annual/lifetime pool, with no per-account-type (growth vs. tsumitate) sub-limit

## Severity: Medium (overstates tax-free NISA balance / net worth over a multi-decade simulation for any user concentrating contributions in one account type)

## Summary
`TaxAdvantagedAccountProjector` caps aggregate NISA contributions only against
`TaxConstants.NISA_ANNUAL_LIMIT` (a flat ¥3,600,000 combining growth+tsumitate, per the constant's
own javadoc) and `TaxConstants.NISA_LIFETIME_LIMIT` (¥18,000,000), then distributes the capped total
proportionally across whatever funds exist via `nisaScale`. `SimulationFund` has no field
distinguishing a growth-quota fund from a tsumitate-quota fund, so any single fund (or combination)
can legally accumulate up to the full ¥3,600,000/yr even though real NISA rules cap tsumitate
contributions at ¥1,200,000/yr and growth contributions at ¥2,400,000/yr as separate, non-fungible
sub-quotas.

Notably, the real-world data *does* carry this distinction and it is discarded on ingestion:
`NisaSnapshotRecord` (the imported brokerage snapshot entity) has an `accountType` field holding real
values like "NISA (成長)預り" (growth) or "NISA (つみたて)預り" (tsumitate), but when
`AssetSimulationEngineImpl` builds `SimulationFund` objects from these records, `record.getAccountType()`
is never read/copied.

## Evidence

[TaxConstants.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/TaxConstants.java) lines 16-19:
```java
public static final long NISA_LIFETIME_LIMIT = 18_000_000L;
public static final long NISA_ANNUAL_LIMIT = 3_600_000L;
```
Single flat constant, javadoc explicitly states it represents "Growth + Tsumitate" combined — the
code itself documents that it treats these as one pool.

[TaxAdvantagedAccountProjector.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java) lines 45-53:
```java
long originalAnnualNisa = annualNisa;
annualNisa = Math.min(annualNisa, TaxConstants.NISA_ANNUAL_LIMIT);
long maxNisaRemaining = TaxConstants.NISA_LIFETIME_LIMIT - context.getAccumulatedNisaPrincipal();
...
```
Caps are applied to the aggregate across `context.getNisaFunds()` with no per-fund-type
distinction, and `SimulationFund.java` has no type/category field to support one.

`NisaContributionSetting.java` (the parsed contribution-setting entity) likewise has no
`accountType` field, matched purely by fund name — so there is no validation layer anywhere
(DTOs, `FundOverrideApplier`, `SimulationOverrides`) that prevents an unrealistic single-type
contribution from being modeled.

## Failure scenario
A user configures a single NISA fund (representing a "growth"-type account in reality) with
`monthlyContribution = ¥300,000` (¥3,600,000/yr). Real-world NISA rules would reject/redirect
¥1,200,000 of that as exceeding the growth-quota annual sub-limit (only ¥2,400,000/yr allowed in
growth), forcing that excess into a taxable account subject to ~20% capital-gains tax on its growth.
`TaxAdvantagedAccountProjector` only checks the combined ¥3,600,000 annual limit and the
¥18,000,000 lifetime limit — both satisfied — so the full ¥3,600,000 is invested tax-free every
year. Over a multi-decade simulation this compounds into an overstated tax-free NISA balance (and
correspondingly overstated net worth / understated bankruptcy risk), because the simulation never
routes the excess into a taxed account as would happen in reality.

## Suggested direction (not required by this review, noted for scoping a fix)
Copy `NisaSnapshotRecord.accountType` through into `SimulationFund` (the field already exists
upstream and is simply dropped), add a matching field to `NisaContributionSetting`, and apply the
¥2,400,000/yr growth sub-cap and ¥1,200,000/yr tsumitate sub-cap independently before the combined
¥3,600,000 check.

---

## Fix Summary

Fixed-07 has been addressed. The fix introduces a dual-track sub-limit enforcement mechanism into the simulation engine. The following changes were made:

**1. `NisaAccountType` enum (new file)**
A new enum `NisaAccountType` (`GROWTH`, `TSUMITATE`) was created with a `parse()` method that maps raw SBI HTML strings (e.g. `"NISA (成長)預り"`, `"NISA (つみたて)預り"`) to the correct enum constant. Defaults conservatively to `GROWTH` for unrecognized or blank strings, since the growth quota has the tighter ¥12,000,000 lifetime sub-cap.

**2. `TaxConstants` (updated)**
Three new constants were added alongside the existing combined limits:
- `NISA_GROWTH_ANNUAL_LIMIT = 2_400_000L`
- `NISA_GROWTH_LIFETIME_LIMIT = 12_000_000L`
- `NISA_TSUMITATE_ANNUAL_LIMIT = 1_200_000L`

**3. `SimulationFund` (updated)**
Added a `nisaAccountType` field (`NisaAccountType`, nullable). `null` indicates a non-NISA fund (DC, iDeCo). The existing `@Builder` and `@AllArgsConstructor` on the class handle this automatically.

**4. `AssetSimulationEngineImpl` (updated)**
When assembling `SimulationFund` objects from `NisaSnapshotRecord`, `NisaAccountType.parse(record.getAccountType())` is now called and passed into the builder. The upstream parser (`SbiNisaParserImpl`) already extracts and persists the `accountType` string — it was simply being discarded at this assembly point.

**5. `SimulationContext` (updated)**
Added `accumulatedGrowthNisaPrincipal` to track the cumulative growth-quota (成長投資枠) principal independently, enabling the ¥12,000,000 growth lifetime sub-cap check. The existing `accumulatedNisaPrincipal` continues to track the combined total; the tsumitate accumulated principal can be derived as `accumulatedNisaPrincipal - accumulatedGrowthNisaPrincipal` without a third variable.

**6. `TaxAdvantagedAccountProjector` (updated)**
The old single-pool `nisaScale` approach was replaced with a three-stage dual-track cap:
1. **Per-type annual + lifetime sub-cap**: Growth funds capped at `Min(¥2.4M, ¥12M - accumulatedGrowthNisaPrincipal)`; tsumitate funds capped at `Min(¥1.2M, ¥18M - accumulatedNisaPrincipal)`.
2. **Combined safety-net cap**: The sum of both groups is further bounded by `Min(¥3.6M, ¥18M - accumulatedNisaPrincipal)`.
3. **Per-fund proportional scale-back**: Each fund's contribution is scaled within its own group's budget (`growthScale` or `tsumitateScale`), preserving the existing proportional distribution logic.
Both `accumulatedNisaPrincipal` and `accumulatedGrowthNisaPrincipal` are updated each year.

**Key design decision**
`NisaContributionSetting` and `SbiNisaParserImpl` (the settings parser) were intentionally left unchanged. Because the simulation assembles `SimulationFund` objects from `NisaSnapshotRecord` (which already carries `accountType`) and then joins contribution amounts via `fundName` matching, the `accountType` information flows correctly without touching the settings layer.

---

## Request for Second Round Review

Please perform a deep source code review of the above fix, focusing on:

1. **Correctness of the dual-track cap logic** in `TaxAdvantagedAccountProjector` — in particular, whether the tsumitate lifetime remaining calculation (`¥18M - accumulatedNisaPrincipal`) is arithmetically sound across multi-decade simulations, including edge cases where one quota is exhausted before the other.
2. **`NisaAccountType.parse()` coverage** — whether the `"GROWTH"` conservative default is appropriate, and whether any other account-type string variants from the SBI HTML could arrive that are not handled.
3. **`accumulatedGrowthNisaPrincipal` initialization** — whether it is correctly seeded at simulation start from the existing snapshot data (or correctly left at `0` if that is the right baseline).
4. **`NisaPrincipalToRecoverNextYear`** — whether the existing recovery logic in `AssetSimulationEngineImpl` that decrements `accumulatedNisaPrincipal` also needs to decrement `accumulatedGrowthNisaPrincipal` when a growth-quota fund sells down.

---

## Second Round Review Report

Following the initial implementation of the dual-track limits, a complete source code review of both the production logic and testing assertions was conducted.

### 1. Business Logic Refinement (Tsumitate Lifetime Limit)
**Issue:** The initial fix mathematically locked the Tsumitate (つみたて投資枠) lifetime cap to ¥6,000,000 (`18M - 12M`). This contradicts Japanese NISA regulations, which state that while the Growth quota has a strict ¥12,000,000 limit, the Tsumitate quota has no individual cap and can legally utilize the entire ¥18,000,000 NISA limit if Growth is unused.
**Resolution:** Updated `TaxAdvantagedAccountProjector` so that `tsumitateLifetimeRemaining` simply tracks against the global `18M - accumulatedNisaPrincipal`.

### 2. Test Assertion Correction (Legal NISA Constraints)
**Issue:** The negative cashflow recovery test (`shouldRecoverNisaPrincipalAcrossYearsOnNegativeCashflow`) mocked an initial principal of ¥18,000,000 using an unknown account type string (`"Type"`). The `NisaAccountType.parse()` method conservatively (and correctly) maps unknown strings to the `GROWTH` quota to protect against over-projection. However, assigning ¥18,000,000 to a Growth account is legally impossible (capped at 12M) and creates mathematically broken boundary states (`12M - 18M = -6M` remaining) during simulation testing.
**Resolution:** Updated the test mock to use `"NISA (つみたて)預り"`. This aligns the test with real-world legality, as only a Tsumitate account can legally carry the full ¥18,000,000 limit, ensuring the negative cash flow recovery logic is tested under a valid maximal boundary state.
