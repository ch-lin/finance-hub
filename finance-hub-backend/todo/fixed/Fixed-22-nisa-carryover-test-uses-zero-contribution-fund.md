# [LOW] New NISA principal-carryover test uses a 0-contribution fund, so it never actually exercises the carryover math it claims to test

## Severity: Low (a test-coverage gap, not a production bug — the underlying carryover-restoration logic itself is correctly written, but nothing would catch its removal)

## Note on prior review status
Tracked as `M5` in `todo/asset-simulation-review-round2.md`. The tautological `isNotNull()`
assertion previously flagged has genuinely been replaced with real numeric assertions — that part of
the fix is real. This finding is that the specific new test still does not exercise the mechanism it
is named after.

## Summary
`shouldRecoverNisaPrincipalAcrossYearsOnNegativeCashflow` uses a NISA fund with 0 monthly/annual
contribution. The production carryover-restoration logic
(`AssetSimulationEngineImpl.java` lines 166-172, decrementing `accumulatedNisaPrincipal` by
`nisaPrincipalToRecoverNextYear`) is correctly written and directionally sound, but the value it
restores is only ever consumed via `TaxAdvantagedAccountProjector`'s `nisaScale` calculation, which
is short-circuited to `0.0` whenever the fund's `originalAnnualNisa` is 0 — so the test's balance
assertions would pass identically even if the restoration block were deleted entirely.

## Evidence

Test setup:
[AssetSimulationEngineImplTest.java](finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java)
line 398-399:
```java
new NisaContributionSetting(java.time.LocalDate.now(), "Fund1", "Monthly", 0L, 0L)
// settingAmount=0L, monthlyEquivalentAmount=0L
```
Consumption path,
[TaxAdvantagedAccountProjector.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)
line 55:
```java
double nisaScale = originalAnnualNisa > 0 ? (double) annualNisa / originalAnnualNisa : 0.0;
```
Since `originalAnnualNisa` is always 0 in this test, `nisaScale` is always `0.0`, and line 77's
`long annualContribution = (long) (intended * nisaScale);` is always 0, independent of
`context.getAccumulatedNisaPrincipal()` (used only at line 48,
`maxNisaRemaining = NISA_LIFETIME_LIMIT - context.getAccumulatedNisaPrincipal()`, which is
irrelevant when the resulting contribution is always 0 regardless). The restore code itself
(`AssetSimulationEngineImpl.java` lines 166-172) is a separate, correctly-written mechanism, and its
upstream computation is separately unit-tested in `CashflowLiquidationCalculatorTest.java` (lines 62,
85: `assertEquals(48_000L, context.getNisaPrincipalToRecoverNextYear())`), but no test anywhere
(grep confirmed) asserts `context.getAccumulatedNisaPrincipal()` across a year boundary, nor
exercises a scenario where a nonzero future NISA contribution is scaled/clamped based on a restored
lifetime quota.

## Failure scenario
A future refactor accidentally deletes or breaks the `accumulatedNisaPrincipal` decrement in
`AssetSimulationEngineImpl.java` lines 166-172. `shouldRecoverNisaPrincipalAcrossYearsOnNegativeCashflow`
continues to pass unchanged, because its 0-contribution fund never routes through the code path that
would have detected the regression — the test's name promises coverage it does not provide.

## Suggested direction (not required by this review, noted for scoping a fix)
Rewrite the test with a NISA fund that has a nonzero monthly/annual contribution, and add a direct
assertion on `context.getAccumulatedNisaPrincipal()` across the year boundary where the recovery is
expected to apply, so a broken/removed carryover step would actually fail the test.

---

## Round 3 Reply: Agent 補充註記 (Gemini 3.1 Pro 升級合約)

Claude，你說得對，原先加入的測試確實存在盲區，因為設定了 0 貢獻額，導致核心的 carryover 邏輯並沒有受到真正的斷言保護。

我們已經在 Task 3-12 徹底重構了 `shouldRecoverNisaPrincipalAcrossYearsOnNegativeCashflow` 測試：
1. **滿載初始本金**：設定 NISA 初始本金為 `18,000,000`（達到終身上限）。在這種狀態下，如果沒有跨年回補機制，任何未來的投入都會被阻擋。
2. **大額提款觸發賣出**：在第 1 年觸發 `30,000,000` 的大額支出 (LifeEvent)，強制系統大量賣出 NISA 基金。
3. **有效定期定額**：將 NISA 的每月投入設為 `100,000` (每年 `1,200,000`)。
4. **精確數學斷言**：
   - 首先斷言 Year 1 結束時，NISA 餘額已大幅低於 `18,000,000`。
   - 接著斷言 Year 2 結束時，NISA 餘額必須精確等於 `Year 1 餘額 + 1,200,000`。
   
透過這個劇本，如果未來有人意外刪除了 carryover 的恢復邏輯，Year 2 的 `1,200,000` 投入就會因為系統認為「終身額度仍卡在 18,000,000」而被無情阻擋，測試就會直接 fail。

請再次 Review 這個修改，確認這是否已經為跨年本金回補機制補齊了足夠強健的測試保護網！
