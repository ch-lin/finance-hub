# [RESOLVED] Re-verification (2026-09-02): the local `taxEngine` mock override genuinely exercises the tax→cash pipeline and the hand-derivation checks out — CONFIRMED_FIXED, with one strategy note answering the developer's question

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies the "## Fix Applied (2026-09-02)" section appended to [Fixed-19-02.md](Fixed-19-02.md), which claimed a local `when(taxEngine.calculate(...)).thenAnswer(...)` override inside `shouldSimulateMortgageAmortizationAndEarlyRepaymentAtRetirement` now returns a non-zero 10%/5%/5% tax split, and that the test's hand-derived expected cash figures were updated to subtract these simulated taxes — closing the ~¥380,622 gap identified in the prior round. Given this test's history (Round 3 fabricated, Round 4 real-but-wrong), verified with maximum skepticism against actual current source rather than the narrative.

## Independently verified (confirmed real)

**The local mock genuinely exists and is correctly scoped.** `AssetSimulationEngineImplTest.java:365-374`:
```java
when(taxEngine.calculate(anyLong(), anyLong(), anyInt(), anyDouble(), anyDouble(), anyDouble(), any(),
        anyLong(), anyLong(), anyInt(), anyLong(), anyLong()))
        .thenAnswer(invocation -> {
            long salary = invocation.getArgument(0);
            long incomeTax = salary * 10 / 100;
            long residentTax = salary * 5 / 100;
            long socialInsurance = salary * 5 / 100;
            return new TaxCalculationResult(incomeTax, residentTax, socialInsurance,
                    salary - incomeTax - residentTax - socialInsurance);
        });
```
The matcher is unscoped (`any()`-style across all 12 args, matching `ActiveTaxCalculationEngine.calculate`'s real signature), but this test's own scenario (`initialAge=59`, `targetRetirementAge=60`) means Years 1-2 are already retired and call `taxEngine.calculatePensionTax(...)` instead — a different method, untouched by this override and still covered by the pre-existing global zero-mock. So despite the broad matcher, the override only ever fires for Year 0 — no unintended leakage into other years, and `@Mock` freshness per test method (MockitoExtension) means no leakage into other tests either.

**Hand-derivation confirmed to match.** With today's date (2026-09-02, `monthsToAdd=4`) and empty `salaryRecordRepository` (unmodified global stub, YTD=0): production computes `effectiveAnnualSalary = 2,000,000`, mock returns incomeTax=200,000/residentTax=100,000/socialInsurance=100,000, `YearEndAdjustmentEngine` degenerates (YTD=0) to `netIncome = 2,000,000 − 400,000 = 1,600,000`. The test's own independent derivation computes tax on the full `6,000,000` first (`trueAnnualNetIncome = 4,800,000`), then multiplies by `remainingRatioYear0 = 4/12`: `round(4,800,000 × 4/12) = 1,600,000`. **Both converge to 1,600,000** — the `isEqualTo` assertion should pass. The mortgage-amortization loop itself (interest/principal/early-repayment) was separately confirmed arithmetically identical to `MortgageCalculator.java`'s production logic, so this gap is genuinely closed.

## Answering the developer's question (posed at the end of Fixed-19-02.md)
> "Does Claude have any further opinions on whether this global 'tax-free' mocking strategy for the simulation engine tests is acceptable, now that the specific mortgage test has been locally fortified?"

Yes — this is a sound and appropriately-scoped strategy, not a new instance of the "test verifies itself, not reality" anti-pattern. The global zero-tax stub keeps the other 73 tests focused on cash-flow/mortgage-plumbing correctness without coupling them to tax-formula details; verifying the tax *formulas* themselves is correctly left to `ActiveTaxCalculationEngineTest`. The local override in this one test exists specifically because this test's exactness claim (`isEqualTo`) depends on taxes actually being deducted — and it now proves the plumbing (mock → `estimatedIncomeTax`/`InhabitantTax`/`SocialInsurance` → `YearEndAdjustmentEngine` → `netIncome` → cash) is exact, which is what was missing. Keep the global stub; this division of labor is correct.

## One caveat worth flagging (not a bug, not blocking)
The match above only holds because the mocked tax function is a flat, homogeneous percentage of salary — `tax(x)%` commutes with scalar multiplication, so "derive on full annual salary, then multiply by the Year-0 ratio" (the test's method) happens to equal "derive directly on the already-prorated salary" (production's method). A future mock resembling the real engine's bracketed deductions or fixed allowances would **not** commute this way, and this test's hand-derivation order of operations would silently diverge from production without any code change to the test itself. Worth a one-line comment at the mock/derivation site noting this assumption, so a future edit to the mock doesn't quietly break the algebraic equivalence. Not required to fix now.

## On execution
Could not mechanically run the test — same sandbox blockers as prior rounds (`.m2` write denial, Maven Central network denial, unpublished sibling artifact `ch.lin:platform:0.0.1-SNAPSHOT`). Conclusion rests on an independent full hand-trace of both the production code path and the test's own derivation, which converge exactly.

## Verdict
Genuinely fixed. No further action needed on this finding.
