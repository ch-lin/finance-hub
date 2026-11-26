# [LOW-MEDIUM] Salary record `paymentDate` is not actually immutable on update — editing an existing record can silently shift which month its income is attributed to, skewing the simulation's trailing salary/bonus history

## Severity: Low-Medium (retroactively changes which period a salary/bonus record counts toward,
feeding a skewed trailing-12-month income history into `AssetSimulationEngineImpl`'s salary/bonus
extraction with zero test coverage protecting against regressions on this path)

## Provenance note
Discovered while independently re-verifying the three open questions posed at the end of
[Fixed-06-01.md](Fixed-06-01.md)'s "Request for Second Round of Deep Source Code Review" section.
This is **not** a reopening of Fixed-06 — that finding's core dual-cap insurance-calculation fix
(splitting bonus vs. base salary before applying pension/health caps) remains confirmed correct and
unaffected by this. This is a distinct, previously-uninspected bug on the salary-record edit endpoint
that happened to surface only because that review thread's tail-end question asked about it. Filed as
a fresh top-level finding (rather than a Fixed-06 companion) since it has no functional relationship
to Fixed-06's own subject matter, to avoid it being overlooked once Fixed-06 is treated as closed.

## Summary
`SalaryRecord`'s `paymentDate` field determines which month a salary/bonus payment is attributed to,
which in turn is the field `AssetSimulationEngineImpl`'s trailing-12-month salary-history extraction
loop keys off of when deriving `bonusPaymentCount`/`annualBonusAmount`/monthly-income averages
(the same extraction logic Fixed-06-01 introduced). The update (`PUT`) path for an existing
`SalaryRecord` only rejects a `paymentDate` change if it collides with a date already used by a
*different* record — it does not prevent changing the record's own `paymentDate` at all, and no test
exists covering this path.

## Evidence

[SalaryRecordServiceImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryRecordServiceImpl.java)
lines 81-98:
```java
if (!record.getPaymentDate().equals(command.getPaymentDate())) {
    salaryRecordRepository.findByPaymentDate(command.getPaymentDate())
        .ifPresent(existing -> { throw new IllegalArgumentException(...); });
    record.changePaymentDate(command.getPaymentDate());
}
```
The only guard is a **uniqueness check against a different record's date** — there is no check that
blocks changing the current record's own `paymentDate` at all.

`SalaryRecord.changePaymentDate` (`SalaryRecord.java` lines 239-244) sets the field unconditionally,
only null-checked:
```java
public void changePaymentDate(LocalDate paymentDate) {
    if (paymentDate == null) { throw new IllegalArgumentException(...); }
    this.paymentDate = paymentDate;
}
```

`SalaryRecordController.updateRecord` passes the request straight through to the service with no
additional immutability guard of its own.

No test exists exercising this: `find` for `SalaryRecordServiceImplTest.java` /
`SalaryRecordControllerTest.java` returns nothing.

## Failure scenario
A user edits an existing salary record via the UI (e.g. to correct a typo in `baseSalary`) and, by
accident or intentionally, also changes its `paymentDate` to a different month than when it was
actually paid. The backend accepts this silently as long as no other record already occupies that
exact date. This retroactively reassigns which month that income (and, if it's a `BONUS`-type record,
which bonus payment) is attributed to. Since `AssetSimulationEngineImpl`'s trailing-12-month
extraction window keys directly off `paymentDate` to compute `bonusPaymentCount` and the monthly-income
average feeding the dual-cap insurance calculation, this can shift a payment into or out of the
rolling window, or change bonus-payment counting for that period — silently skewing the simulation's
social-insurance-cap and pension-accrual projections with no audit trail of the original date and no
test that would catch a regression here.

## Suggested direction (not required by this review, noted for scoping a fix)
Add an explicit guard in `SalaryRecordServiceImpl`'s update path that rejects changing `paymentDate`
on an existing record entirely (requiring delete-and-recreate instead), or — if intentional
date-correction needs to remain possible — require it to go through a separate, clearly-audited
"correct payment date" action rather than being silently permitted through the general-purpose update
endpoint. Add test coverage for the update endpoint, which currently has none.

## Fix Applied (2026-09-02)
We have fully locked down `paymentDate` immutability across the domain, service, and UI layers, directly addressing the risk of skewed trailing-12-month projections.

- **Domain Layer (`SalaryRecord.java`)**: The `changePaymentDate` method was entirely removed. `paymentDate` can now only be set upon instantiation, enforcing strict immutability at the domain entity level.
- **Service Layer (`SalaryRecordServiceImpl.java`)**: The update path's uniqueness-check-and-replace logic was stripped out. It is now replaced with a strict equality check: if `command.getPaymentDate()` diverges from the existing record, an `IllegalArgumentException` is immediately thrown (`"Payment date cannot be modified on an existing record. Please delete and recreate the record."`).
- **Frontend UI (`add-salary-record-dialog.tsx`)**: In Edit Mode, the `<Input type="date">` is now explicitly set to `disabled={true}`. To ensure a clear UX, a native tooltip and a red warning message (`"Payment date cannot be modified. Delete and recreate the record to change it."`) appear directly below the field, guiding users toward the intended delete-and-recreate workflow.

This cross-stack lockdown completely eliminates the silent mutation risk highlighted in this finding.
