# [LOW] Re-verification (2026-09-01): `accumulatedGrowthNisaPrincipal` recovery fix is genuinely correct and complete; one pre-existing minor gap remains open

## Status: CONFIRMED_FIXED (the Medium-severity gap escalated in Fixed-07-01 is resolved) —
one small, previously-flagged secondary item remains untouched, downgraded here to informational

## Relation to original finding
Re-verifies [Fixed-07-01.md](Fixed-07-01.md)'s "Resolution (Implemented by Gemini)" section,
which claimed `accumulatedGrowthNisaPrincipal` is now correctly decremented on principal recovery via
a new `growthNisaPrincipalToRecoverNextYear` field, mirroring the existing combined-principal
recovery mechanism.

## What was verified fixed (confirmed real via independent source read + hand-traced arithmetic)

**1. New field exists.** `SimulationContext.java:39-40`:
```java
private long nisaPrincipalToRecoverNextYear;
private long growthNisaPrincipalToRecoverNextYear;
```
Lombok `@Data`-generated accessors, default `0`.

**2. Decrement + reset logic is correct and symmetric.** `AssetSimulationEngineImpl.java:171-183`,
at the top of the per-simulated-year loop:
```java
if (context.getNisaPrincipalToRecoverNextYear() > 0) {
    context.setAccumulatedNisaPrincipal(
        Math.max(0, context.getAccumulatedNisaPrincipal() - context.getNisaPrincipalToRecoverNextYear()));
    context.setNisaPrincipalToRecoverNextYear(0L);
}
if (context.getGrowthNisaPrincipalToRecoverNextYear() > 0) {
    context.setAccumulatedGrowthNisaPrincipal(
        Math.max(0, context.getAccumulatedGrowthNisaPrincipal() - context.getGrowthNisaPrincipalToRecoverNextYear()));
    context.setGrowthNisaPrincipalToRecoverNextYear(0L);
}
```
Both accumulators are floored at 0 and both recovery trackers are reset to `0L` immediately after
being applied — no double-application risk across multiple years. This runs exactly once per
simulated year, in the same place as the pre-existing combined-principal recovery.

**3. New test is a genuine differential test, not vacuous.** `AssetSimulationEngineImplTest.java:505-560`
(`shouldRecoverGrowthNisaPrincipalAcrossYearsOnNegativeCashflow`). Hand-traced: a single Growth-quota
fund with `accumulatedGrowthNisaPrincipal = accumulatedNisaPrincipal = nisaBalance = ¥12,000,000` is
forced into a ≥¥16M deficit in Year 1. In `CashflowLiquidationCalculator.java`, `sellRatio = 1.0`
(the entire ¥12M NISA balance is liquidated), so `growthPrincipalToRestore = 12,000,000 × 1.0 =
12,000,000`. At the start of Year 2, `accumulatedGrowthNisaPrincipal = max(0, 12,000,000 -
12,000,000) = 0`, restoring the full ¥12,000,000 growth headroom and allowing the test's asserted
¥2,400,000 annual growth contribution to succeed in Year 2. Without the fix, this contribution would
have been blocked (headroom would still read 0), so this test would genuinely fail against the
pre-fix code — it is not a rubber-stamp assertion. Minor rigor note: `nisaBalanceYear1` itself is
only checked with a weak `isLessThan(12_000_000L)` (line 550) rather than an exact value, but the
Year-2 assertion that actually exercises the fix is a real, differential exact-value check.

## What remains open (pre-existing, unrelated to this round's fix — split into its own finding)

`SbiNisaParserImpl.java:66-71` silently defaults an unrecognized/absent account-type header to
`"Unknown"`, which `NisaAccountType.parse()` then falls through to the conservative `GROWTH` default
with no warning-level logging anywhere. This was originally flagged as open item #2 in Fixed-07-01,
untouched by this round's principal-recovery fix, and functionally unrelated to it — per this
engagement's convention, it has been split out into its own tracked item rather than kept as a loose
note here: see [Fixed-30](Fixed-30-sbi-nisa-unknown-account-type-silent-fallback-no-logging.md)
(filed at lowest priority; SBI's real HTML has consistently included this header in practice).

## Conclusion
Fixed-07's original Medium-severity concern (dual-track cap enforcement) and Fixed-07-01's
follow-up Medium-severity escalation (growth-principal recovery drift) are both now genuinely,
correctly fixed and independently hand-verified. No further companion file is needed for this
finding — the one residual gap it surfaced is now tracked separately as Fixed-30.
