# [LOW] Family member birth-date guard works client-side, but has zero server-side defense-in-depth

## Severity: Low (the only currently-known path to submit a bad family-member birth date is blocked in the UI; this is a defense-in-depth gap that would matter for a direct API call or a future UI regression)

## Note on prior review status
Tracked as `2.16` in `todo/asset-simulation-review-round2.md`. The frontend fix works as claimed; the
reservation about missing server-side defense-in-depth, previously flagged, is confirmed still
warranted.

## Summary
`saveFamilyMember` in the profile form genuinely blocks adding a family member with no birth date,
toasting an error before the bad entry ever enters form state or gets POSTed. But there is no
equivalent guard anywhere on the backend: the DTO field is not `@Valid`-cascaded, the nested request
type has no `@NotNull` on birth date, and the service layer performs no manual null check either.

## Evidence

Frontend guard works:
[user-profile-form.tsx](finance-hub-ui/hub-ui/src/app/finance/components/user-profile-form.tsx)
lines 67-72:
```tsx
const saveFamilyMember = () => {
  if (!editingMember) return;
  if (!editingMember.birthDate) {
    toast.error("Please provide a birth date");
    return;
  }
  ...
};
```
returns before `onChange({...formData, familyMembers: updated})`, so the bad entry never enters
`formData` and is never POSTed.

No backend guard:
[UserProfileRequest.java](finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java)'s
`List<DependentRequest> familyMembers` field (line 22) has no `@Valid` annotation, so Jakarta Bean
Validation will not cascade into the list elements at all;
[DependentRequest.java](finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentRequest.java)
(`record DependentRequest(DependentType memberType, LocalDate birthDate, Long estimatedAnnualIncome)`)
has no `@NotNull` on `birthDate` either.
[UserProfileServiceImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java)
lines 64-70 maps `command.familyMembers()` directly to `Dependent` entities with
`fm.setBirthDate(cmd.birthDate())` and no null/empty check.

## Failure scenario
A direct API call (bypassing the UI entirely — e.g. a script, a future mobile client, or any UI
regression that stops calling `saveFamilyMember`'s guard) submits a family member with a null birth
date. Nothing on the backend rejects it; the `Dependent` entity is persisted with a null `birthDate`,
which would then need to be handled (or would NPE/misbehave) wherever downstream code assumes every
dependent has a birth date, e.g. age-dependent tax/insurance calculations in the simulation engine.

## Suggested direction (not required by this review, noted for scoping a fix)
Add `@Valid` to `UserProfileRequest.familyMembers` and `@NotNull` to `DependentRequest.birthDate` so
the existing frontend guard is backed by an equivalent server-side rejection.

---
## Gemini 3.1 Pro 修正紀錄 (2026-08-30)
已於後端完成 Defense-in-depth 縱深防禦機制的實作：
1. 於 `UserProfileRequest.java` 中的 `familyMembers` 屬性補上 `@Valid`，確保 Jakarta Bean Validation 會自動級聯進入子物件驗證。
2. 於 `DependentRequest.java` 中的 `birthDate` 補上 `@NotNull` 註解。
修復後，即便繞過前端 UI 發生 API 直連攻擊，或未來前端發生退化 (Regression)，後端也能正確拋出 HTTP 400 阻擋空生日的無效請求，避免污染系統後續的年齡相關模擬計算。
