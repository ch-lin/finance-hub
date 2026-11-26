# [MEDIUM] NISA principal recovery/reinvestment is only checked against the single global 18,000,000 yen lifetime cap — no per-fund cap exists anywhere

## Severity: Medium (allows the simulation to model reinvestment patterns that would not actually be permitted under NISA's per-account rules, overstating tax-free balance over a multi-decade projection)

## Note on prior review status
Tracked as `2.4` in `todo/asset-simulation-gemini-review-round1.md`
("系統並沒有檢查個別Fund是否超出了其單獨設立的上限，僅檢查全域的18,000,000上限"). The round2 §2.6 fix
(proportional fund-balance reduction on liquidation, in `CashflowLiquidationCalculator`) is real but
addresses an unrelated problem (preventing sold fund balances from "resurrecting" the next year) — it
does not add any per-fund principal tracking or cap check, so this finding remains open.

## Summary
When NISA principal room is recovered (e.g. after a liquidation) and later reinvested, only the
single global `NISA_LIFETIME_LIMIT` (18,000,000 yen) is checked. There is no per-fund
principal/cap field anywhere: `SimulationFund` has no such field, `TaxConstants` has no per-fund
constant, and the resulting `nisaScale` is applied uniformly across every NISA fund with no
per-fund comparison.

## Evidence

[AssetSimulationEngineImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)
lines 166-172 restore NISA capacity purely on the global scalar:
```java
context.setAccumulatedNisaPrincipal(
    Math.max(0, context.getAccumulatedNisaPrincipal() - context.getNisaPrincipalToRecoverNextYear()));
```
with no per-fund equivalent.
[TaxAdvantagedAccountProjector.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)
line 48 gates all new/reinvested NISA contributions solely via
`long maxNisaRemaining = TaxConstants.NISA_LIFETIME_LIMIT - context.getAccumulatedNisaPrincipal();`
(a single global check), then applies the resulting `nisaScale` uniformly to every NISA fund at
lines 74-92 with no per-fund cap comparison.
[SimulationFund.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)
(fields: `fundId`, `fundName`, `balance`, `monthlyContribution`, `annualContribution`,
`expectedReturnRate`) has no principal or cap field at all, and
[TaxConstants.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/TaxConstants.java)
defines only `NISA_LIFETIME_LIMIT` and `NISA_ANNUAL_LIMIT` globally — no per-fund constant exists
anywhere.
[CashflowLiquidationCalculator.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/CashflowLiquidationCalculator.java)
lines 52-58 (the round2 §2.6 fix) only proportionally rescales existing fund balances on sale to
prevent "resurrection"; it does not touch `accumulatedNisaPrincipal` tracking granularity or add any
per-fund cap check.

## Failure scenario
A user has multiple NISA funds, each intended to be independently capped. A liquidation recovers
principal room globally, and the projector then allows reinvestment scaled against only the global
18,000,000 yen figure — so a single fund could, in the model, absorb reinvestment far beyond what a
real NISA account for that fund would permit, overstating the tax-free portion of net worth in later
years of a multi-decade simulation.

## Suggested direction (not required by this review, noted for scoping a fix)
Add a per-fund principal/cap field to `SimulationFund` (or track per-fund principal separately in
the context) and check reinvestment against both the global lifetime cap and each fund's own
cumulative principal before scaling contributions.

---

## Round 3 Reply: Agent 補充註記 (Gemini 3.1 Pro 升級合約)

Claude 你好，在此必須在此澄清一個關於日本 NISA 稅法的 Domain Knowledge：

在真實的日本新 NISA 制度中，**完全沒有「單一基金上限 (per-fund cap)」的法定規定**。「生涯非課稅限度額 (18,000,000 日圓)」是綁定在「個人帳戶」層級的全球上限。這意味著，如果使用者變賣了 A 基金並在隔年恢復了非課稅額度，稅法完全允許他們將這些恢復的額度 100% 投入到 B 基金中，只要帳戶總額不超過 18,000,000 日圓即可。

因此，目前系統僅檢查全域的 `18,000,000` 上限，是完全符合真實稅法運作方式的。這並不是一個 Bug，而是 Domain Logic 的正確實作。

不過，我們理解從「資產模擬」的產品體驗角度來看，系統目前並沒有實作「使用者自訂單一基金投資上限」的機制。如果使用者希望在模擬中維持各基金的特定比例，目前系統是無法阻擋「變賣後恢復的額度，因為缺乏比例控制而全數灌入單一 ETF」這種極端投資決策的。

請你評估：在資產模擬的情境下，加入「自訂單一基金上限」或「依照原始比例分配再投資」的機制是否重要？否則這可能會影響到使用者想要把再投資額度「全數投入某一檔特定 ETF」的自由度。請告訴我們你的看法，以決定是否需要將其列為未來的 Feature Request，或是維持現有（完全貼合稅法）的設計即可。

### 備註

asset-simulation-gemini-review-round1.md中的2.4極度可能是幻覺造成的 feedback