# [HIGH] NISA/iDeCo return-rate bounds-check fix exists only in the same dead component; the live onboarding/profile form has no client-side bounds checking

## Severity: High (a real user filling out onboarding or their profile can type a nonsensical return rate with zero client-side feedback, caught only by the backend as a late safety net)

## Note on prior review status
Same underlying dead-code issue as
[Fixed-23-fund-override-return-rate-bounds-check-only-in-dead-code.md](Fixed-23-fund-override-return-rate-bounds-check-only-in-dead-code.md),
applied to the fallback-mode NISA/iDeCo return-rate inputs. Filed separately because it affects a
different, also-live surface (`user-profile-form.tsx`, used on both `/finance/onboarding` and
`/finance/profile/me`).

## Summary
`asset-allocation-drawer.tsx`'s fallback-mode branch has real `min="-100" max="100"` plus `onBlur`
bounds checking for NISA/iDeCo return rate inputs — but that entire component has zero importers
(dead code, see Fixed-23). The actual live form for entering these values,
`user-profile-form.tsx`, has no client-side bounds checking at all; the only protection is the
backend's `@DecimalMin`/`@DecimalMax` on `UserProfileRequest`, enforced only after the whole profile
form is submitted.

## Evidence

Dead component: `asset-allocation-drawer.tsx` lines 162-172 and 179-189 (`isFallback` branch) have
`min="-100" max="100"` plus `onBlur` handlers that reset the value and
`toast.error('Return rate must be between -100% and +100%')` when out of range — but this whole file
has 0 importers (confirmed via `grep -rln "asset-allocation-drawer" src`, returns only the file
itself).

Live form: `grep -rln "UserProfileForm"` returns
[user-profile-form.tsx](finance-hub-ui/hub-ui/src/app/finance/components/user-profile-form.tsx),
`finance/profile/me/page.tsx`, and `finance/onboarding/page.tsx`. In `user-profile-form.tsx` lines
256-266 (NISA) and 288-298 (iDeCo):
```tsx
<Input type="number" step="0.01" ... onChange={handleInputChange} />
```
— no `min`/`max` attribute, no `onBlur`, `handleInputChange` just stores the raw string.

Backend safety net (works, but is the only protection):
`UserProfileRequest.java` line 18: `@DecimalMin("-1.0") @DecimalMax("1.0") Double nisaExpectedReturnRate`
and line 20 the same for `idecoExpectedReturnRate`; `UserProfileController.java` lines 39-41 binds it
via `@PostMapping("/me") ... @RequestBody @Valid UserProfileRequest request` — this `@Valid`
genuinely triggers validation, and the percent/fraction scale is confirmed consistent
(`profile/me/page.tsx` line 62: `nisaExpectedReturnRate: formData.nisaExpectedReturnRate ? parseFloat(...) / 100.0 : 0.05`).

## Failure scenario
A new user goes through onboarding and types `500` into "NISA Expected Annual Return Rate" (intending
5%). There is no client-side feedback at all — the field accepts it silently. Only after submitting
the entire onboarding form does the backend's `@DecimalMax(1.0)` reject it with a 400, at which point
the user has to re-discover which of the several fields on the form was the problem, since (per
[FINDING-27](FINDING-27-nisa-goal-solver-frontend-bounds-decorative.md)'s sibling issue in a related
form) validation error messages from this endpoint are not reliably surfaced with field-level detail
in every consuming component.

## Suggested direction (not required by this review, noted for scoping a fix)
Port the same `min`/`max` + `onBlur` bounds-check pattern from `asset-allocation-drawer.tsx`'s
fallback branch into `user-profile-form.tsx`'s NISA/iDeCo return-rate inputs, so users get immediate
feedback instead of relying solely on the post-submit backend check.

---
## Gemini 3.1 Pro 修正紀錄 (2026-08-30)
已將 `user-profile-form.tsx` 中 NISA 與 iDeCo 的報酬率輸入框加上 `min="-100"` 與 `max="100"` 屬性，並實作 `onBlur` 事件：
當使用者輸入超過範圍的數值（如 `500`）移開焦點時，會自動觸發 UX 友善的夾擊（Clamping）邏輯退回 `100`（若小於 `-100` 則退回 `-100`），並同時跳出 `toast.error` 提示使用者數值已被重置，徹底補齊了前端的即時防呆機制。
