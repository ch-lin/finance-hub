# Plan 09: [LATENT / DEAD CODE TODAY, MEDIUM if ever activated] DC/iDeCo retirement deduction is only correctly combined because withdrawal ages are hardwired together — the existing "separate" code path would double-dip the deduction if that coupling is ever loosened

## Status: OPEN — new finding, discovered incidentally while discussing FINDING-12 (years-of-service
seeding) and the tax-law question of whether DC and iDeCo lump-sum withdrawals must be combined
(合算) or may be calculated separately. Functionally unrelated to FINDING-12 itself — filed as its own
top-level number per this engagement's convention (same as FINDING-36).

## Priority note
Not a live bug today — the vulnerable code path is currently unreachable (dead code), so no
simulation run can hit it as the codebase stands. Filed to be tracked *before* any future change
touches DC/iDeCo unlock-age coupling, so that change doesn't silently reintroduce a real double
counted tax deduction.

## The tax-law premise (why "separate" is wrong)
Under Japanese tax law, when a person receives more than one 退職手当等（company severance pay,
企業型DC lump sum, iDeCo lump sum）within the same year, they are not each entitled to their own,
independent 退職所得控除. They must be combined (合算) into a single retirement-income calculation:
the payout amounts are summed, and the years-of-service figure used is the deduplicated/overlap-aware
longest relevant enrollment period — not each account's own contribution years applied against its
own share of the balance. Exact look-back-year specifics for payouts received in *different* years
have shifted across recent reforms and aren't asserted precisely here — but the core "don't let the
same years of service produce two independent full deductions in the same year" principle is not in
dispute.

## Where the combining currently happens correctly (and why)
[TaxAdvantagedAccountProjector.java:182-199](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java):
```java
boolean dcUnlocked = (age == taxAdvantagedWithdrawalAge)
        || (age == initialAge && age > taxAdvantagedWithdrawalAge);
boolean idecoUnlocked = dcUnlocked; // Both are unlocked at the unified withdrawal age

if (dcUnlocked || idecoUnlocked) {
    long dcNet = 0;
    long idecoNet = 0;

    if (dcUnlocked && idecoUnlocked) {
        long totalBalance = context.getDcBalance() + context.getIdecoBalance();
        int totalYears = Math.max(context.getDcContributionYears(), context.getIdecoContributionYears());
        long totalNet = taxEngine.calculateRetirementTax(totalBalance, totalYears).netIncome();

        if (totalBalance > 0) {
            dcNet = (long) (totalNet * ((double) context.getDcBalance() / totalBalance));
            idecoNet = totalNet - dcNet;
        }
    } else {
        if (dcUnlocked) {
            dcNet = taxEngine.calculateRetirementTax(context.getDcBalance(), context.getDcContributionYears())
                    .netIncome();
        }
        if (idecoUnlocked) {
            idecoNet = taxEngine
                    .calculateRetirementTax(context.getIdecoBalance(), context.getIdecoContributionYears())
                    .netIncome();
        }
    }
    ...
}
```
`idecoUnlocked` is a literal alias of `dcUnlocked` (line 185) — there is only one
`taxAdvantagedWithdrawalAge`, so the two booleans are always equal. This means the guard on line 187
(`dcUnlocked || idecoUnlocked`) being true always implies `dcUnlocked && idecoUnlocked` on line 191 is
also true. **The `else` branch at lines 200-210 — the "calculate each account's deduction
independently" path — is therefore dead code under the current implementation.** Every real
simulation run goes through the combined path: `totalBalance = dcBalance + idecoBalance`,
`totalYears = Math.max(dcContributionYears, idecoContributionYears)`, one single
`calculateRetirementTax` call, then the net result is pro-rated back into `dcNet`/`idecoNet` by
balance share. This happens to match the "combine, don't double-count" tax principle above — but only
as a side effect of the hardwired coupling, not because of any explicit combining safeguard elsewhere.

## Why this coupling is plausible to loosen later
[TaxAdvantagedAccountProjector.java:22-23](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)
already has separate `dcUnlockAge`/`idecoUnlockAge` constructor parameters — today these only gate
whether *contributions* are still being made into each account, not when each account's balance is
*withdrawn*. Withdrawal is currently governed solely by the single `taxAdvantagedWithdrawalAge`. A
plausible, reasonable future feature — letting a user stagger DC and iDeCo withdrawals across
different years for tax planning, or simply modeling that DC and iDeCo can legally have different
unlock ages in some cases — would need to decouple `dcUnlocked`/`idecoUnlocked` from a single shared
boolean into two independently-computed ones. The moment that happens, the dead `else` branch
(lines 200-210) springs to life.

## What happens if that dead branch is ever activated
[ActiveTaxCalculationEngine.java:243-250](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java):
```java
public TaxCalculationResult calculateRetirementTax(long retirementIncome, int contributionYears) {
    long deduction;
    if (contributionYears <= 20) {
        deduction = 400_000L * contributionYears;
        deduction = Math.max(800_000L, deduction);
    } else {
        deduction = 8_000_000L + 700_000L * (contributionYears - 20);
    }
    ...
}
```
This method takes only `(retirementIncome, contributionYears)` — no account-type flag, no "has a
deduction already been claimed this year" flag, no shared/static state, no awareness of any other
call made in the same simulated year. If `dcUnlocked` and `idecoUnlocked` are ever computed
independently and both happen to be true in the same year (e.g., withdrawal ages set equal by the
user, or both thresholds crossed in the same year by coincidence), the `else` branch would call this
method twice, and each call would compute its own full 20-year-threshold deduction from scratch —
a genuine double-dip on the deduction, inflating the modeled net cash received and understating the
true tax bill. This is the same category of risk FINDING-12 originally flagged (tax miscalculation
distorting the liquid-cash trajectory), but on the opposite side: FINDING-12 was about
under-crediting years causing overpaid tax; this is about a latent path that would over-credit
(double-count) the deduction if ever reached.

## Suggested direction (not required now — tracked for when unlock-age coupling is touched)
If `dcUnlocked`/`idecoUnlocked` are ever decoupled into independently-computed booleans:
- Do not let the `else` branch (independent-per-account `calculateRetirementTax` calls) survive as-is.
  Either always route through the combined-calculation path regardless of whether both unlock in the
  same year (i.e., track a running "deduction years already consumed this tax year across all
  DC-type withdrawals" figure at the context level and combine at read time), or, if withdrawals in
  different years are intentionally supported, implement the actual look-back/overlap-adjustment
  logic for cross-year retirement income rather than letting each year's withdrawal claim an
  independent, un-adjusted full deduction.
- At minimum, if the two-call path is intentionally kept for a "genuinely different years" case, add
  a code comment and/or a test asserting that when both booleans happen to be true in the same
  simulated year, the combined path is used — so a future refactor doesn't silently reintroduce the
  double-dip by accident.

## Conclusion
No code change is being requested now — the vulnerable path is unreachable given the current
`idecoUnlocked = dcUnlocked` hardwiring, so today's tax output is not affected. This finding exists so
that any future work decoupling DC/iDeCo unlock ages does not silently reintroduce a real
double-deduction bug through code that already exists but is currently dead.
