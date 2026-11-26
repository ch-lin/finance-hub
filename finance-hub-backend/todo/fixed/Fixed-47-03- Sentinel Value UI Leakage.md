# Fix Fixed-47-02: Sentinel Value UI Leakage

Fix the frontend leakage and persistence of the `Long.MAX_VALUE` sentinel value (used by Grid Search to signal a full mortgage repayment). The fix restricts the sentinel value from displaying as a massive raw number or being editable as an input, without breaking the backend execution pipeline.

## User Review Required

This plan modifies frontend logic only, leaving the backend `GlobalOptimizerMapper` untouched as recommended in the report. Please review the changes before I proceed.

## Proposed Changes

---

### Frontend Components

#### [MODIFY] [use-simulation-data.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts)
- **Action**: Delete the `mortgageEarlyRepaymentYear?: number | null;` property from the `SimulationOverrides` interface. This is a dead field in the backend API and only creates confusion in the frontend simulation payload.

#### [MODIFY] [page.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx)
- **Action**: Remove the dead logic inside `onApplyResult` that attempts to persist `mortgageEarlyRepaymentYear` into the sandbox overrides:
  ```typescript
  // REMOVE:
  if (result.mortgageEarlyRepaymentYear != null) {
    next.mortgageEarlyRepaymentYear = result.mortgageEarlyRepaymentYear;
  }
  ```

#### [MODIFY] [global-optimizer-card.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)
- **Action**: Update the `validMortgageRepayments` filter in `OptimizerResultPanel` to remove elements where `amount >= Number.MAX_SAFE_INTEGER`. This stops the "Astronomical Number" card from rendering alongside the dedicated "Full Repayment (Year X)" card.
  ```typescript
  const isFullPayoffSentinel = (amount: number) => amount >= Number.MAX_SAFE_INTEGER;
  const validMortgageRepayments = result.mortgageEarlyRepayments
    ? Object.entries(result.mortgageEarlyRepayments)
        .map(([mortgageId, events]) => [mortgageId, events.filter(e => !isFullPayoffSentinel(e.amount))] as const)
        .filter(([_, events]) => events.length > 0)
    : [];
  ```

#### [MODIFY] [simulation-override-panel.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
- **Action**: In the mortgage early repayment rendering loop (around line 437), add a conditional check for `ev.amount >= Number.MAX_SAFE_INTEGER`.
- If true, do not render the editable `<Input type="number">`. Instead, render a read-only badge indicating "Full Payoff (AI)" to prevent users from accidentally modifying the raw sentinel value while keeping the intent clear.

## Verification Plan

### Manual Verification
1. Run a simulation and execute the Grid Search optimization on a single-mortgage scenario.
2. Confirm the optimizer results card no longer shows the `¥9,223...` repayment card, but correctly shows the Year X full payoff card.
3. Click **Apply to Sandbox**.
4. Check the "Advanced Overrides" panel. Verify that the amount input is replaced with a "Full Payoff (AI)" read-only badge.
5. Manually trigger a sandbox simulation and confirm the full payoff still takes effect in the projected yearly data.
