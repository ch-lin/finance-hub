# Plna 08: No statutory contribution limit is enforced for iDeCo/DC contributions, unlike NISA — a user can configure a legally-impossible contribution and get an inflated tax deduction

## Severity: High (produces a legally impossible income-tax deduction and net-income figure, directly distorting the "cash bankruptcy" projection in the optimistic direction)

## Summary
`TaxAdvantagedAccountProjector` explicitly caps NISA contributions against both an annual limit
(`TaxConstants.NISA_ANNUAL_LIMIT`) and a lifetime limit (`TaxConstants.NISA_LIFETIME_LIMIT`), but
performs no equivalent cap for iDeCo or DC contributions at all. `TaxConstants` has zero constants
for iDeCo/DC monthly or annual limits, and there is no logic anywhere in the codebase implementing
the real-world Japanese rule that a person's iDeCo monthly limit is reduced when they also
participate in a company DC plan (a limit that depends on cross-account state).

## Evidence

[TaxAdvantagedAccountProjector.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)
lines 39-67:
```java
long annualIdeco = 0;
for (SimulationFund fund : context.getIdecoFunds()) {
    annualIdeco += (age == initialAge) ? fund.getMonthlyContribution() * monthsToAdd
            : fund.getAnnualContribution();
}
long annualDc = 0;
for (SimulationFund fund : context.getDcFunds()) {
    annualDc += (age == initialAge) ? fund.getMonthlyContribution() * monthsToAdd
            : fund.getAnnualContribution();
}
// ... only NISA gets: annualNisa = Math.min(annualNisa, TaxConstants.NISA_ANNUAL_LIMIT); + lifetime-limit clamp
// annualIdeco / annualDc are never clamped against any statutory monthly/annual limit anywhere in the codebase
```
Confirmed via grep: no `IDECO_*LIMIT`/`DC_*LIMIT` constants exist in `TaxConstants.java` or anywhere
else in the repo. This unbounded value directly inflates the DC/iDeco balance projection with no
ceiling, and flows into `ActiveTaxCalculationEngine.calculate` as `annualIdecoContribution`/
`annualDcMatchingContribution`, which are added straight into `retirementDeduction`
(`ActiveTaxCalculationEngine.java` lines 49-51) with no cap.

## Failure scenario
A user configures an iDeCo fund with a 500,000 JPY/month contribution (or any amount above the real
~12,000-23,000 JPY/month statutory ceiling) while also having a company DC account. This unbounded
value inflates the DC/iDeco balance projection with no ceiling, and produces an unrealistically large
income-tax deduction and net-income figure — a legally impossible input silently accepted and
compounded over decades, materially overstating projected net worth and understating bankruptcy risk.

## Suggested direction (not required by this review, noted for scoping a fix)
Add `IDECO_MONTHLY_LIMIT`/`DC_MONTHLY_LIMIT` constants (with the cross-account reduction rule when
both a DC plan and iDeCo are present) to `TaxConstants`, and clamp `annualIdeco`/`annualDc` the same
way `annualNisa` is already clamped.

---
## 狀態追蹤 (2026-08-30)
**狀態：已知問題，暫緩執行 (Deferred)**

此項議題為團隊已知事項，目前列於專案計畫中但暫緩處理。由於目前系統仰賴使用者手動輸入 iDeCo/DC 金額，使用者通常清楚自身的職業與對應之法定提繳上限，實務上發生無腦輸入 50 萬的風險極低。團隊預計在未來實作 Global Optimizer (自動搜尋 iDeCo 最佳提繳額) 功能時，再一併導入基於 `employmentType` (職業型態) 的嚴格防呆機制。

**佐證紀錄 (Proof of Planning)：**
- 可於 `finance-hub/finance-hub-backend/hub/data/todo/plan.md` 的 **Task B5: iDeCo/DC 法定月繳上限防呆 (Statutory Contribution Limits)** 中找到相關規劃與暫緩理由。
- 可於 `finance-hub/finance-hub-backend/hub/data/todo/asset-simulation-review-round1.md` 的 **H16. [PLANNED] iDeCo/DC 月繳金額沒有任何法定上限檢查** 中找到此問題的首次記錄。
