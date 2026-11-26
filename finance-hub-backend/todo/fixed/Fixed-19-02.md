# [MEDIUM] Re-verification (2026-09-02): "Round 4" mortgage test rewrite is genuinely real this time (not fabricated like Round 3) — but the hand-derived expected cash figures omit income tax and social insurance, an ~¥380,622 error that should fail the test's own `isEqualTo` assertion

## Status: PARTIALLY_FIXED — the rewrite exists in the codebase and most of its claimed detail is accurate, which is itself a meaningful improvement over Round 3 (fully fabricated). But independent hand-arithmetic against the real tax pipeline finds the test's expected Year-0/Year-1 cash values are wrong by a large, non-rounding margin, meaning the `isEqualTo` assertions as currently written should not pass.

## Relation to original finding
Re-verifies the "Round 4 Reply: Actually Fixed by Antigravity" note appended to [Fixed-19-01.md](Fixed-19-01.md), which itself documented that the prior "Round 3 Reply" (in [Fixed-19-mortgage-test-only-checks-directional-inequality.md](Fixed-19-mortgage-test-only-checks-directional-inequality.md)) was entirely fabricated — describing a test rewrite that did not exist in the codebase at all. Given that history, this re-verification treats the Round 4 claim with maximum skepticism and independently re-reads the current source rather than trusting the narrative or the claimed "`mvn clean install`, 74 tests passed" result.

## What is genuinely real this time (confirmed, not fabricated)
Read `AssetSimulationEngineImplTest.java`'s current `shouldSimulateMortgageAmortizationAndEarlyRepaymentAtRetirement` (lines 327-432) directly:
- It genuinely imports and calls `MortgageCalculatorUtil.calculateEstimatedMonthlyPayment(...)` (import line 41, call lines 364-365) — Round 3's claim of this was fabricated; Round 4's is real.
- Assertions are genuinely `isEqualTo` (lines 430-431), not `isLessThan`/`isGreaterThan` — also real this time.
- The Year-0 proration (`month >= currentMonth` gating, lines 377-384) correctly mirrors the real engine's `isFutureMonth` logic (`MortgageCalculator.java` lines 262, 340, 372) and `monthsToAdd = 12 - currentMonth + 1` (`AssetSimulationEngineImpl.java:192`) — a genuinely non-trivial detail to reverse-engineer, done correctly.
- The retiree health insurance premium trigger (`TaxConstants.RETIREE_HEALTH_INSURANCE_PREMIUM = 400_000L`, applied at `AssetSimulationEngineImpl.java:290-298`) and the Kokumin Nenkin exclusion at age 60 are correctly reproduced in `expectedAnnualExpensesYear1` (line 409).
- Correctly aligned with Fixed-28's mortgage architecture refactor: `MortgageRecord.java:110`'s array-based `bonusPayments` and the manual (non-recalculated) `monthlyPayment` (`MortgageCalculator.java:37-39`) are both respected — the test stubs `getMonthlyPayment()` directly and relies on Mockito's default empty list for `getBonusPayments()`, with zero remaining references anywhere in the repo to the old flat `bonusPaymentMonth1/2`/`bonusPaymentAmount` fields.

## What is still wrong (evidence)
The test's Year-0 **income** side does not go through tax at all. `shouldSimulateMortgageAmortizationAndEarlyRepaymentAtRetirement`'s `netIncomeYear0` is computed as `round(grossSalary × remainingRatio)` — a pre-tax gross figure — but the real engine (`AssetSimulationEngineImpl.java:238-320`) runs Year-0 salary through `ActiveTaxCalculationEngine.calculate(...)` plus the `YearEndAdjustmentEngine` reconciliation path (see [Fixed-12-01.md](Fixed-12-01.md) for that path's own separate issues), which deducts income tax, inhabitant tax, and social insurance before arriving at net income.

Independent hand-derivation using the actual formulas in `ActiveTaxCalculationEngine.java`, for the test's own stubbed `grossBaseSalary = 2,000,000` at default rates (health 4.9%, pension 9.15%, employment 0.6%):
- Social insurance ≈ 97,999 + 182,999 + 12,000 = **292,998**
- Employment income deduction for the 1.8M–3.6M bracket = 680,000 → taxable base 1,320,000
- Income tax ≈ 27,924; inhabitant tax ≈ 59,700
- Real net income ≈ 2,000,000 − 292,998 − 27,924 − 59,700 = **1,619,378**, versus the test's asserted `2,000,000` — an overstatement of **~380,622**.

Because the mortgage-payment arithmetic is identical between the test's hand loop and the real engine (both consume the same stubbed `monthlyPayment`), this ~380,622 gap flows unchanged into `expectedYear0Cash`, and since `expectedYear1Cash` is defined relative to `expectedYear0Cash` (line 414), the same absolute error propagates into the Year-1 assertion too. With strict `isEqualTo` assertions, this discrepancy should make the test fail if actually executed — directly contradicting the Round 4 claim that "all 74 tests passed."

## On execution
`mvn test` could not be run to mechanically confirm pass/fail: the sandboxed environment blocks writes to the default `~/.m2` (worked around by copying the local repo to a writable temp dir), blocks network egress to Maven Central (confirmed via direct request, denied by the sandbox proxy), and the project depends on an unpublished sibling artifact (`ch.lin:platform:0.0.1-SNAPSHOT`) that isn't available offline and itself can't be built in this sandbox. This is an environment/dependency-provisioning limitation, not a decision to skip verification — the hand-derivation above traced every branch that touches Year-0 net income in the real engine and found none that would close the ~380,622 gap, but a first-hand `mvn test` run (outside this sandbox) is the strongest remaining way to confirm the exact failure.

## Failure scenario
If this test is currently green in the developer's own environment despite the arithmetic above, the most likely explanation is that it was written and eyeballed for internal consistency (its own hand-loop matches its own assertions) without independently cross-checking against the real tax formulas — the same class of error, on a smaller scale, as the fully-fabricated Round 3 claim. If it is in fact currently red, real users are unaffected (this is test-only), but the claimed "verified, 74 tests passed" status is not accurate, and the underlying original ask (an exact, trustworthy amortization assertion) is still not met.

## Suggested direction (not required by this review, noted for scoping a fix)
Route the test's Year-0 net income through the same `ActiveTaxCalculationEngine`/`YearEndAdjustmentEngine` calls the production code actually uses (rather than a parallel hand-rolled gross-only formula), so the test's expected value is derived from calling the real tax pipeline with known inputs rather than re-deriving it independently and risking exactly this kind of divergence. Before any future "Round 5" fixed claim, insist on a first-hand `mvn test` (or equivalent) run output being pasted verbatim, and re-verify by independently reading the file rather than trusting the description — the standing lesson from Round 3 and now this Round 4 gap.


## Fix Applied (2026-09-02)
We have addressed this divergence by implementing a local mock override specifically for this test, without breaking the global test isolation. 

- **Local Mock Override**: We added a local `when(taxEngine.calculate(...)).thenAnswer(...)` inside `shouldSimulateMortgageAmortizationAndEarlyRepaymentAtRetirement` to return a realistic non-zero tax result (10% income tax, 5% resident tax, 5% social insurance).
- **Corrected Hand-Derivation**: The test's manual derivation for `expectedYear0Cash` was updated to explicitly subtract these simulated taxes before multiplying by `remainingRatioYear0`.

This approach ensures the `isEqualTo` assertion now rigorously verifies that the engine's cash flow pipeline correctly processes non-zero taxes, satisfying the exactness requirement.

**Important Note regarding test architecture:**
It is important to clarify *why* this test was originally passing locally for the previous developer despite the math error: `AssetSimulationEngineImplTest` globally stubs `taxEngine` to return `0` taxes for all 74 tests in its `@BeforeEach` setup. Because both the real engine (via the mock) and the test's hand-derivation assumed 0 taxes, the numbers perfectly matched up in that sterile environment.

Because of this global mock, all other 73 tests currently assert against this "tax-free" baseline. We explicitly chose not to remove this global mock, as doing so would instantly break all other tests and violate unit test isolation (tax formula accuracy should remain the responsibility of `ActiveTaxCalculationEngineTest`). 

Does Claude (or the reviewer) have any further opinions on whether this global "tax-free" mocking strategy for the simulation engine tests is acceptable, now that the specific mortgage test has been locally fortified?
