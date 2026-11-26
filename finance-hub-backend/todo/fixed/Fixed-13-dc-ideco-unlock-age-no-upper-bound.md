# [LOW] No upper-bound (statutory max age 75) clamp on DC/iDeCo unlock age or tax-advantaged withdrawal age

## Severity: Low (data-integrity/business-rule gap; requires an unusual config value to manifest, but has no guard anywhere in the pipeline)

## Summary
`resolveIdecoUnlockAge`/`resolveDcUnlockAge` only enforce a lower bound of 60
(`Math.max(60, config value)`), and `resolveTaxAdvantagedWithdrawalAge` only enforces
`Math.max(minUnlockAge, profile value)` — none of them cap the value at the statutory maximum age of
75 for iDeCo, unlike `pensionStartAge`, which IS clamped both ways via
`PensionCalculator.getValidPensionStartAge` (`Math.max(60, Math.min(75, requestedAge))`).

## Evidence

[SimulationParameterResolver.java](finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java) lines 89-105:
```java
public static int resolveIdecoUnlockAge(FinanceHubConfig config) {
    if (config != null && config.getIdecoUnlockAge() != null) {
        return Math.max(60, config.getIdecoUnlockAge());
    }
    return 60;
}
```
(same pattern for `resolveDcUnlockAge`), and lines 111-117:
```java
public static int resolveTaxAdvantagedWithdrawalAge(...) {
    int minUnlockAge = Math.max(dcUnlockAge, idecoUnlockAge);
    if (profile != null && profile.getTaxAdvantagedWithdrawalAge() != null) {
        return Math.max(minUnlockAge, profile.getTaxAdvantagedWithdrawalAge());
    }
    return minUnlockAge;
}
```
Both only clamp the lower bound. `CreateFinanceHubConfigRequest`/`UpdateFinanceHubConfigRequest`
only have `@Min(value = 0, ...)` on `idecoUnlockAge`/`dcUnlockAge` — no `@Max`.
`UserProfileRequest.taxAdvantagedWithdrawalAge` has no validation annotations at all.
`AssetSimulationEngineImpl`'s guard checks only validate `targetAge`/`targetRetirementAge`/
`pensionStartAge` — nothing about DC/iDeCo unlock-age upper bounds.

## Failure scenario
If `FinanceHubConfig.idecoUnlockAge` (or a user's `taxAdvantagedWithdrawalAge`) is set above 75 —
e.g. 90 — `TaxAdvantagedAccountProjector` keeps `idecoAllowed = true` and DC/iDeCo contributions
keep compounding tax-deferred (age ≤ `taxAdvantagedWithdrawalAge` stays true), and the forced
unlock/retirement-tax event only fires at the exact age matching that value — which may never occur
within the simulated horizon (targetAge/life expectancy). The simulation then models an iDeCo/DC
balance growing past the legally mandated age-75 withdrawal deadline instead of being forced into
(taxed) liquid cash at 75, distorting the liquid-cash/net-worth trajectory used to determine the
bankruptcy year for that scenario, with no validation error raised anywhere.

## Suggested direction (not required by this review, noted for scoping a fix)
Add `Math.min(75, ...)` clamps to `resolveIdecoUnlockAge`/`resolveDcUnlockAge`/
`resolveTaxAdvantagedWithdrawalAge`, mirroring the two-sided clamp `PensionCalculator.getValidPensionStartAge`
already applies to `pensionStartAge`.

---

## Resolution Report (Gemini AI)
**Status:** ✅ Fixed (Defense in Depth)

### 1. 根本原因 (Root Cause)
DC/iDeCo 的提領年齡在 `SimulationParameterResolver` 中只有設定 `Math.max(60, age)` 的下限，漏掉了法定的 75 歲上限。此外，API DTO 驗證層也缺乏嚴格的上下限把關，導致髒資料可能進入系統並扭曲長期的避稅複利模擬。

### 2. 修正方案 (Fix)
我們採取了「縱深防禦 (Defense in Depth)」策略，一次補齊從前端到核心引擎的防護網，且一併涵蓋了國民年金：
- **核心引擎層**：在 `SimulationParameterResolver.java` 中，為 `resolveIdecoUnlockAge`、`resolveDcUnlockAge` 以及 `resolveTaxAdvantagedWithdrawalAge` 補上 `Math.min(75, ...)`，確保核心運算的絕對防線。
- **後端 API 層**：在 `CreateFinanceHubConfigRequest`、`UpdateFinanceHubConfigRequest` 與 `UserProfileRequest` 中，為 `pensionStartAge`、`idecoUnlockAge`、`dcUnlockAge`、`taxAdvantagedWithdrawalAge` 全面加上 `@Min(60)` 與 `@Max(75)` 的驗證標籤。
- **前端 UI 層**：更新了 Zod 驗證（`use-finance-configs.ts`）並在 HTML 表單元件（`user-profile-form.tsx`、`system-config-panel.tsx`）加上 `min={60} max={75}`，讓使用者在畫面上就能得到正確的法規引導。
