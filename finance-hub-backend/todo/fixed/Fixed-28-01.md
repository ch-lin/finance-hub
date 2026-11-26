# [MEDIUM] Re-verification (2026-09-01): "Data is Truth" architecture change is real and the cross-year bonus-payment logic is correct, but the "required manual input" guarantee is decorative-only, and a nested DTO validation is missing its cascade annotation

## Status: PARTIALLY_FIXED — the architectural rewrite (delete frontend math, array-based bonus
payments, backend trusts stored `monthlyPayment`) is genuinely implemented and the highest-risk
correctness question (does a recurring bonus payment still apply in every future simulated year, not
just year 1) is confirmed correct with no regression. Two small, concrete gaps remain.

## Relation to original finding
Re-verifies [Fixed-28-mortgage-monthly-payment-ignores-bonus-payments.md](Fixed-28-mortgage-monthly-payment-ignores-bonus-payments.md)'s
"Implementation Report", which rejected the originally-suggested fix (teach the frontend formula about
bonus payments) in favor of a "Data is Truth" rewrite: delete `calculateEstimatedPayment()`, make
`monthlyPayment` a required manual input, refactor bonus payments into an array, and have the
simulation engine trust the stored `monthlyPayment` outright.

## Prerequisite check: Fixed-02 status
[Fixed-02-mortgage-bonus-payment-double-counted.md](../fixed/Fixed-02-mortgage-bonus-payment-double-counted.md)
is confirmed `Resolution Status: Fixed` (line 314), independently cross-checked against current source:
`MortgageCalculatorUtil.java:57-83` implements the PV/nominal bonus-netting fix, and
`MortgageCalculator.java:37-39` trusts the ground-truth stored payment. Fixed-28's work was correctly
unblocked.

## What was verified fixed (confirmed real)

**Architecture rewrite is genuine.** `calculateEstimatedPayment()` no longer exists anywhere in the
mortgages `page.tsx` (zero grep hits). Bonus payments are correctly bound as an array
(`bonusPayments: {month, amount}[]`) with working add/edit/remove UI, submitted verbatim on save.

**`MortgageRecord` JPA mapping is sound.** Flat fields replaced by
`@ElementCollection` + explicit `@CollectionTable(name = "mortgage_bonus_payment", ...)`, with a
properly `@Embeddable` `BonusPayment` child carrying its own `@NotNull @Min(1) @Max(12)` /
`@NotNull @Min(0)` constraints, and null-guarded constructors.

**`MortgageCalculator.java` trusts the stored value with no fallback at all** (lines 37-39) — stricter
than even the Fixed-02-era behavior, which still had a null-fallback recalculation path.

**Cross-year bonus-payment recurrence — the highest-risk question — is correct, no regression.**
Traced the full chain: the outer per-age simulation loop (`AssetSimulationEngineImpl.java:165,197`)
calls `MortgageCalculator.simulateMortgagesForYear` once per simulated year; inside it, the per-month
loop (line 258) recomputes `currentMonthBonus` **fresh every month, every year** by re-scanning
`m.getBonusPayments()` for an entry matching the current calendar month (lines 350-359) — a stateless
comparison against the persisted array, with nothing marking an entry "consumed" or removing it. The
same pattern is used in `fastForwardMortgagesToPresent`. The only loop-termination condition is
`balance <= 0` (full payoff). A June/December bonus pair therefore recurs identically every year for
the life of the loan, exactly matching the pre-refactor semantics and the finding's own described
intent — no "drops after year 1" or "must re-specify per year" bug exists.

**SonyBank parser correctly adapted.** `SonyBankMortgageHtmlParserImpl.java:75` extracts the real
`次回返済額` into `monthlyPayment`; it passes `Collections.emptyList()` for bonus payments via the new
constructor with no dangling references to the removed flat fields. Known functional limitation (not a
bug): bonus amounts are never auto-scraped, so users must manually add bonus-payment entries after an
import — consistent with the "Data is Truth" philosophy (the bank statement doesn't expose this
figure), just worth being aware of.

## What remains broken (evidence)

**1. `monthlyPayment`'s "required" status is decorative-only on the frontend.**
`page.tsx` gives the `monthlyPayment` input a `required` attribute (line 309), but there is no
`<form>` element wrapping the mortgage dialog anywhere in the component, and the Save action
(lines 636-637) is a plain `onClick` handler, not a form submit — so the browser's native `required`
validation never actually triggers. `handleSave` (lines 96-125) never checks `monthlyPayment` itself,
and `getEmptyForm()` (line 45) defaults it to `0`. A user can currently save a new mortgage with
`monthlyPayment: 0` with no warning at all — the exact opposite of the architecture's stated intent
("the user is responsible for inputting the exact cash that leaves their pocket").

**2. Backend nested-array validation doesn't cascade — a recurrence of the Fixed-23 bug pattern.**
`MortgageRecordRequest.java:26` correctly has `@NotNull @Positive` on `monthlyPayment`, and
`BonusPaymentRequest.java:17-23` correctly has `@NotNull @Min(1) @Max(12)` (month) /
`@NotNull @Min(0)` (amount) — but `MortgageRecordRequest.java:27`'s
`List<BonusPaymentRequest> bonusPayments` field itself lacks `@Valid`. Jakarta Bean Validation does not
descend into collection elements unless the collection field is annotated `@Valid`, and the controller
only validates the outer object (`MortgageRecordController.java:68,76`: `@Valid @RequestBody
MortgageRecordRequest`). Concretely: a request body containing `{"month": 99, "amount": -500}` inside
`bonusPayments` currently passes validation and would be persisted as-is. This is the same
"validation-annotation-exists-but-never-cascades" anti-pattern already identified and fixed twice in
Fixed-23/23-01 — worth specifically double-checking for elsewhere in this codebase.

## Suggested direction (not architecturally contentious — both are small, mechanical fixes)
1. In `page.tsx`'s `handleSave`, add an explicit check (mirroring the pattern already used in
   `ExpectedReturnRateEditor`/`simulation-override-panel.tsx`): if `!formData.monthlyPayment ||
   formData.monthlyPayment <= 0`, show a `toast.error` and abort the save, rather than relying on the
   inert `required` HTML attribute.
2. Add `@Valid` to `MortgageRecordRequest.java:27`'s `bonusPayments` field so the already-correct
   `BonusPaymentRequest` constraints actually cascade.

## Failure scenario
A user creates a new mortgage record without filling in the monthly payment field. Nothing on the
frontend blocks the save (the `required` attribute is dead weight with no enclosing `<form>`), so
`monthlyPayment: 0` is submitted. Server-side, `@NotNull @Positive` on `MortgageRecordRequest` should
reject this — so this specific case is actually caught at the backend layer today (the frontend gap is
real but backend `@NotNull @Positive` on `monthlyPayment` itself is not missing `@Valid`, only the
*nested* `bonusPayments` array validation is). The genuinely reachable failure case is: a user adds a
bonus payment entry with a typo'd month like `13` or a negative amount — the frontend UI may or may not
constrain the input widget, but even if it doesn't, the backend's per-entry constraints silently do
nothing due to the missing `@Valid`, so malformed bonus-payment data can be persisted and will feed
directly into `MortgageCalculatorUtil`'s month-matching logic with undefined behavior for an
out-of-range month value.

## Implementation Report (For Claude Review - Follow-up)

The remaining gaps identified in the re-verification have been successfully addressed. Furthermore, additional robustness improvements were made beyond the original findings.

**What was done:**

1. **Frontend Validation for `monthlyPayment`**: 
   - We updated `page.tsx`'s `handleSave` function to explicitly check `if (!formData.monthlyPayment || formData.monthlyPayment <= 0)`.
   - If the condition is met, the UI now displays a `toast.error("Please enter a valid monthly payment amount.")` and immediately aborts the save operation. The `required` attribute is no longer solely relied upon.

2. **Backend Validation Cascade for Nested DTOs**:
   - We added the missing `@Valid` annotation to `List<BonusPaymentRequest> bonusPayments` in `MortgageRecordRequest.java`.
   - This ensures that the `@Min(1) @Max(12)` and `@Min(0)` constraints correctly cascade and reject malformed bonus payment arrays (e.g., negative amounts or month `13`) with a `400 Bad Request`.

3. **Bonus: Validation for `MortgageEarlyRepaymentRequest`**:
   - During the review, we noticed that `MortgageEarlyRepaymentRequest` (which was also missing `@Valid`) lacked validation constraints entirely.
   - We added `@NotNull`, `@Positive`, and `@Min(0)` constraints to the fields inside `MortgageEarlyRepaymentRequest`.
   - We added the `@Valid` cascade annotation to the `earlyRepayments` list in `MortgageRecordRequest.java`.
   - We also added comprehensive loop-based frontend validation in `page.tsx` for both `bonusPayments` and `earlyRepayments`, catching invalid dates, negative amounts, and out-of-range months before the network request is even sent.

4. **Code Structure Clean-up**:
   - `BonusPaymentRequest` was refactored from a standalone external file into a nested `record` within `MortgageRecordRequest`, matching the stylistic choice of `MortgageEarlyRepaymentRequest` for better cohesion and maintainability.

**Review Focus:**
Please re-verify that the frontend now successfully blocks empty/zero `monthlyPayment` inputs, and that backend constraints properly cascade into both nested arrays (`bonusPayments` and `earlyRepayments`).
