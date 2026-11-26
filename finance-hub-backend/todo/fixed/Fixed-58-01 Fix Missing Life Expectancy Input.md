# Fix Missing Life Expectancy Input (Fixed 58)

The issue report accurately identifies that the `lifeExpectancy` field is missing from the user profile UI, preventing users from customizing this crucial simulation parameter. Additionally, the backend has a hardcoded fallback (`90`) when the `FinanceHubConfig` value is inexplicably missing.

## User Review Required

> [!IMPORTANT]
> Based on your request, I will **remove** the hardcoded fallback (`90`) for `lifeExpectancy` in the backend and instead throw an error if the system configuration is null, as a system default must always exist.

## Open Questions

## Removed Fallbacks
Based on your confirmation, since `targetRetirementAge` and `pensionStartAge` also exist in `FinanceHubConfig` with system defaults (65), I will remove their hardcoded fallbacks in `SimulationParameterResolver.java` alongside `lifeExpectancy` to keep the behavior consistent.

## Proposed Changes

### Frontend (UI)

#### [MODIFY] [user-profile-form.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/components/user-profile-form.tsx)
- Add `lifeExpectancy: string;` to the `UserProfileFormData` interface.

#### [MODIFY] [profile-basic-info-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/components/profile-basic-info-section.tsx)
- Add a new input field for `lifeExpectancy` within the `Basic Information` section.
- The input will be optional, with a placeholder like "Optional (defaults to 85)", matching the style of `taxAdvantagedWithdrawalAge`.

#### [MODIFY] [onboarding/page.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/onboarding/page.tsx)
- Initialize `lifeExpectancy` in `formData` from the fetched `profile` (if any), converting it to a string.
- Parse `formData.lifeExpectancy` to an integer (or send `null` if empty) in the `handleSubmit` payload.

#### [MODIFY] [profile/me/page.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/me/page.tsx)
- Apply the exact same initialization and submit logic changes for `lifeExpectancy` as in the onboarding page.

---

### Backend

#### [MODIFY] [SimulationParameterResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java)
- Update `resolveLifeExpectancy`, `resolveRetirementAge`, and `resolvePensionStartAge` to remove their hardcoded fallbacks (90, 65, and 65 respectively).
- Replace them with a null check that throws an `IllegalArgumentException` if `configValue` is null.

## Verification Plan

### Manual Verification
- The user will manually run unit tests (`mvn clean install`) to verify backend compilation and regression.
- The user will verify the UI locally by running the frontend and checking if the "Life Expectancy" input appears in the Profile Settings and Onboarding pages, and that values can be saved and cleared.
