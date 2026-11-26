# [MEDIUM] NISA growth (2.4M)/tsumitate (1.2M) sub-limits are still never enforced, and accountType is structurally dropped before it could even be used

## Severity: Medium (overstates tax-free NISA balance / net worth for any user concentrating contributions in one account type — same severity as originally filed)

## Note on prior review status
Tracked as `Fixed-07` /
[Fixed-07-nisa-growth-tsumitate-sublimit-not-enforced.md](Fixed-07-nisa-growth-tsumitate-sublimit-not-enforced.md).
Re-verified fresh with an additional structural finding: `accountType` is not merely "not yet
checked" — it is silently dropped at the exact point `NisaSnapshotRecord` is converted into
`SimulationFund`, so the distinction could not be enforced today even if sub-limit constants
existed.

## Summary
Only the combined 3,600,000 yen annual cap and 18,000,000 yen lifetime cap are enforced. The growth
(2,400,000 yen) and tsumitate (1,200,000 yen) sub-limits required by real NISA rules are never
checked anywhere in the codebase, and `SimulationFund` — the model used inside the simulation engine
— has no `accountType` field at all, so the conversion from `NisaSnapshotRecord` to `SimulationFund`
never reads `accountType` in the first place.

## Evidence

[TaxConstants.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/TaxConstants.java)
lines 15-19 define only `NISA_LIFETIME_LIMIT = 18_000_000L` and `NISA_ANNUAL_LIMIT = 3_600_000L` — no
growth/tsumitate sub-limit constants exist in the repo (grep for `1200000`/`2400000`/`Tsumitate`
across all `.java` files returns zero hits related to NISA; the only `1200000` hits are unrelated
education-cost defaults in `FinanceHubConfig.java`/`FinanceHubDefaultProperties.java`).
[TaxAdvantagedAccountProjector.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)
lines 45-53 apply exactly these two combined checks in a single un-branched block, with no
per-accountType logic anywhere in the method.
[NisaSnapshotRecord.java](finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaSnapshotRecord.java)
line 83 has `private String accountType; // e.g., "NISA (成長)預り" or "NISA (つみたて)預り"` (backed by a
DB column, populated by `SbiNisaParserImpl.java`), but
[SimulationFund.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)
lines 15-21 declares only `fundId`, `fundName`, `balance`, `monthlyContribution`,
`annualContribution`, `expectedReturnRate` — no `accountType` field, and its Lombok `@Builder`
therefore has no `.accountType(...)` method.
[AssetSimulationEngineImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)
lines 464-489 — the loop that converts `nisaRecords` into `context.getNisaFunds()` via
`SimulationFund.builder().fundId(record.getId()).fundName(record.getFundName())...build()` — never
calls `record.getAccountType()` at all; the distinction is silently and structurally dropped at this
exact conversion point.

## Failure scenario
A user holds NISA funds concentrated entirely in one account type (e.g. all in growth/成長 investment
quota, no tsumitate contributions). The simulation applies only the combined 3.6M/18M caps, so it
permits this user's growth-type contributions to grow well beyond the real-world 2.4M annual
sub-limit for that account type, overstating tax-free NISA balance and net worth in later years of
the projection — an optimistic error in the exact figure the simulation exists to get right.

## Suggested direction (not required by this review, noted for scoping a fix)
Add `accountType` to `SimulationFund` and thread it through the
`NisaSnapshotRecord`→`SimulationFund` conversion, then add `NISA_GROWTH_ANNUAL_LIMIT` (2,400,000) and
`NISA_TSUMITATE_ANNUAL_LIMIT` (1,200,000) constants to `TaxConstants`, and branch the cap check in
`TaxAdvantagedAccountProjector` by account type in addition to the existing combined check.
