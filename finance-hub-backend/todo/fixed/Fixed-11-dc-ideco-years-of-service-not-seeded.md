# [HIGH] DC/iDeCo years-of-service counters never seeded from real pre-simulation history, understating the retirement lump-sum tax deduction

## Severity: High (materially overstates tax due at the pivotal DC/iDeCo unlock year for any user with real pre-simulation contribution history, which can push the liquid-cash trajectory into an artificially early bankruptcy)

## Summary
`context.dcContributionYears`/`context.idecoContributionYears` always start at 0 in
`initializeContext()` and are only incremented once per simulated year going forward. Unlike
`accumulatedKousei`/`currentKouseiMonths`/`currentKokuminMonths` (which ARE seeded from
`PensionRecord`) and the DC/iDeCo balances themselves (correctly seeded from
`DefinedContributionAccountSnapshot`), any real-world years of DC/iDeCo enrollment that occurred
before the simulation was run are silently dropped when computing the 退職所得控除 (retirement income
deduction) at the unlock/withdrawal year.

## Evidence

[AssetSimulationEngineImpl.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java) lines 207-211:
```java
if (contributions.annualDcMatchingContribution() > 0) {
    context.setDcContributionYears(context.getDcContributionYears() + 1);
}
if (contributions.annualIdecoContribution() > 0) {
    context.setIdecoContributionYears(context.getIdecoContributionYears() + 1);
}
```
These are the ONLY writers of `dcContributionYears`/`idecoContributionYears` in the whole codebase
(confirmed via grep — no `setDcContributionYears`/`setIdecoContributionYears` call anywhere in
`initializeContext()`, unlike the pension-months fields which ARE seeded from `PensionRecord` at
lines 418-438). `DefinedContributionAccountSnapshot` carries `totalAccumulatedContribution` (parsed
and stored) but it is never used anywhere to derive years-of-service; `UserProfile` has no
hire-date/enrollment-date field; `SimulationOverrides` has no field to inject prior contribution
years.

These fields feed directly into
[TaxAdvantagedAccountProjector.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java) lines 149/158/163:
```java
int totalYears = Math.max(context.getDcContributionYears(), context.getIdecoContributionYears());
...
taxEngine.calculateRetirementTax(context.getDcBalance(), context.getDcContributionYears())
```
`ActiveTaxCalculationEngine.calculateRetirementTax` uses `contributionYears` directly in the
20-year-threshold deduction formula: `deduction = max(800,000, 400,000×years)` if `years≤20`, else
`8,000,000 + 700,000×(years−20)`.

## Failure scenario
A 50-year-old user who has genuinely been contributing to corporate DC for 20 real years (DC
starting balance correctly seeded from `DefinedContributionAccountSnapshot`, e.g. balance =
¥10,000,000) runs a simulation to age 60 with `taxAdvantagedWithdrawalAge=60`. Only the 10 simulated
years (age 50→60) are ever added to `dcContributionYears`, so at the unlock year
`calculateRetirementTax` is called with `contributionYears=10` instead of the true 30.

- True deduction: `8,000,000 + 700,000×(30-20) = ¥15,000,000` (fully covering the balance, near-zero
  tax)
- Buggy deduction: `400,000×10 = ¥4,000,000`, leaving taxable income
  `(10,000,000-4,000,000)/2 = ¥3,000,000` and a materially larger tax bill.

The resulting `dcNet` added to `context.currentLiquidCash` at that pivotal unlock year is therefore
too small, which can push the simulated liquid-cash trajectory negative (an earlier predicted
bankruptcy year) even though the real-world tax bill — and hence real cash available — would be
higher.

## Suggested direction (not required by this review, noted for scoping a fix)
Add a way to seed `dcContributionYears`/`idecoContributionYears` from real history — e.g. an
enrollment-start-date field on `UserProfile`/`DefinedContributionAccountSnapshot`, or a
user-editable "years already contributed" input — and initialize the context counters from it in
`initializeContext()`, the same way the DC/iDeCo balances and pension-months counters already are.

***

### Feedback for Claude (Architectural Advice Requested)

**Status: Needs Architectural Advice**

Thank you for pointing out Fixed-11. We completely agree that defaulting to `0` for `dcContributionYears` heavily distorts the retirement tax simulation by nullifying the tax shield, which renders the cash flow projection unrealistically pessimistic. 

However, after investigating the raw HTML source files from the brokers (SBI Benefit Systems and Sompo Japan DC Securities), we hit a hard data limitation:
1. The profile pages (`加入者情報`) only contain `申込年月日` (Application Date) and `資格取得年月日` (Qualification Date). 
2. They **DO NOT** contain any fields explicitly tracking "Total Contribution Months" (通算加入者月数) or any historical "Suspension/Blank Periods" (運用指図者期間).

**The Dilemma:**
If we implement a new `DefinedContributionParser` to extract the earliest `申込年月日` across all accounts and calculate `(Current Date - Earliest Date)`, we will create a "Gross Duration". This effectively fixes the `0` bug, but if the user had unrecorded suspension periods (blank periods), we will **overestimate** their tax shield, leading to an overly optimistic cash flow projection. 

Since this is a personal bankruptcy simulator, we want to be conservative but not wildly inaccurate (like `0`). We also don't want to rely on manual config properties because users rarely remember their exact blank periods.

**Question for Claude:**
Given that the exact "net contribution months" data is physically absent from the scraped HTML sources, how would you architect this? Should we default to the "Gross Duration" (optimistic but close to reality) and accept the slight risk of overestimating the tax shield, or is there a better architectural pattern to handle this missing data edge case without forcing manual user configuration?