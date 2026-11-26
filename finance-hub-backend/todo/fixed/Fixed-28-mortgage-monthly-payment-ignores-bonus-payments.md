# [MEDIUM] Client-computed mortgage `monthlyPayment` ignores bonus payments, persisting and displaying a wrong figure

## Severity: Medium (overstates the displayed/persisted monthly payment for any mortgage with bonus repayments configured, misleading the user about actual monthly cash-flow burden)

## Summary
`calculateEstimatedPayment()`, which supplies the read-only "monthlyPayment" shown in the mortgage
dialog and is sent verbatim to the backend on save, uses only `totalLoanAmount`, `interestRate`,
`loanTermMonths` (and `currentBalance` for `EQUAL_PRINCIPAL`). It never accounts for
`bonusPaymentAmount`/`bonusPaymentMonth1`/`bonusPaymentMonth2`, even though the form has a full
bonus-payment section.

## Evidence

[page.tsx](hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx)
lines 100-127:
```tsx
const calculateEstimatedPayment = () => {
  const L = Number(formData.totalLoanAmount);
  const r = Number(formData.interestRate) / 100 / 12;
  const n = Number(formData.loanTermMonths);
  ...
};
// handleSave(): monthlyPayment: calculateEstimatedPayment(),  // bonusPayment* fields never referenced
```

## Failure scenario
A user enables bonus (semi-annual) repayments, e.g. ¥300,000 extra each June and December, which in a
real mortgage reduces the required monthly installment. `calculateEstimatedPayment()` computes the
standard amortized payment as if no bonus payments existed, overstating the true monthly cost. This
wrong value is both shown to the user as the payment and persisted via `createMortgage`/
`updateMortgage` as `monthlyPayment`, then displayed as-is in the mortgages table, misleading the
user about their actual monthly cash-flow burden — a figure they may reasonably use to sanity-check
the simulation's mortgage cash-flow assumptions.

## Suggested direction (not required by this review, noted for scoping a fix)
Extend `calculateEstimatedPayment()` to account for the bonus-payment amount and months (reducing the
regular installment proportionally, matching whatever formula `MortgageCalculatorUtil` uses
server-side for bonus-payment amortization), so the displayed/persisted figure matches reality.

## Note
Will do after the review of Fixed-02 fixing

## Developer Feedback (Gemini & Developer)

We are rejecting the suggested direction to "fix" the frontend's `calculateEstimatedPayment()` math. Instead, we are completely changing the architectural approach to align with the core philosophy of this system: **it is a cash-flow simulator, not a bank ledger.** 

The principle is **"Data is Truth" (What You See Is What You Get / Parse)**:
1. **Remove Frontend Math**: We will delete `calculateEstimatedPayment()` entirely. `monthlyPayment` will become a required manual input. The system should not try to "play bank" and calculate amortizations because it risks being wrong. The user has their bank statement; they are responsible for inputting the exact cash that leaves their pocket. If they input it wrong, it's on them.
2. **Refactor Bonus Payments to Array**: We will change the bonus payment schema from flat fields (`bonusPaymentMonth1`, `bonusPaymentMonth2`, `bonusPaymentAmount`) to an Array of objects (e.g., `[{month: 6, amount: 300000}, {month: 12, amount: 300000}]`). This reflects reality, where bonuses aren't strictly limited to two identical payments.
3. **Simulation Simplification**: The backend simulator (`MortgageCalculator`) will no longer use `MortgageCalculatorUtil` to try and recalculate a missing `monthlyPayment`. It will strictly run a cash-flow loop using the provided `monthlyPayment` and the `bonusPayments` array.
4. **Parser Philosophy**: Parsers (like Sony Bank) will extract the most real number available on the screen (e.g., `次回返済額`) and save it exactly as `monthlyPayment`. If the page lacks bonus details, they remain empty. If the parser happens to run during a bonus month and extracts an unusually high `次回返済額`, that is the "truth" of the data at that moment. The user can manually correct it via the UI if they notice a spike in their simulation. No artificial fallback calculations will be done.

## Implementation Report (For Claude Review)

The implementation for Fixed-28 has been successfully completed according to the Developer Feedback above.

**What was done:**
1. **Frontend UI**: Removed the automatic `calculateEstimatedPayment()` fallback. `monthlyPayment` is now a strictly manual, required input field.
2. **Bonus Payment Structure**: Refactored the legacy flat fields (`bonusPaymentMonth1`, `bonusPaymentMonth2`, `bonusPaymentAmount`) into a dynamic Array (`List<BonusPayment>`) across the entire stack (Frontend, Request/Response DTOs, and the Database `@ElementCollection`).
3. **Simulation Engine**: 
   - `MortgageCalculator` now accepts and strictly uses the user-provided `monthlyPayment` as the initial truth, resolving the bug where bonus payments were ignored.
   - `MortgageCalculatorUtil.java` has been updated to support iterating over the new `List<BonusPayment>` array instead of hardcoded months. The simulator continues to rely on this utility class **only** for dynamic future recalculations (e.g., when the simulation loop encounters an interest rate change or triggers the 5-year rule), ensuring long-term predictive accuracy.

**Review Focus:**
Please verify that:
- The frontend correctly binds and submits the dynamic `bonusPayments` array.
- The `MortgageRecord` entity properly handles the `@ElementCollection`.
- The math in `MortgageCalculatorUtil.java` correctly processes the variable array to ensure the simulation loop calculates accurate balances during future rate adjustments.