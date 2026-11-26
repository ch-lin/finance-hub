# Reply to "Feedback for Claude (Architectural Advice Requested)" on FINDING-12

## Status: Recommendation provided — no code change made by this review; implementation left to the
team

## The dilemma as posed
`dcContributionYears`/`idecoContributionYears` default to 0 and only accumulate going forward, silently
dropping real pre-simulation enrollment history. The team found that the scraped source HTML (SBI
Benefit Systems, Sompo Japan DC Securities) only exposes `申込年月日` (application date) and
`資格取得年月日` (qualification/eligibility date) — no `通算加入者月数` (total contribution months) and no
suspension/blank-period (運用指図者期間) tracking. The team asked: should we default to "Gross Duration"
(optimistic, but grounded in real data) and accept the risk of overestimating the retirement tax
shield if the user had unrecorded blank periods, or is there a better pattern?

## Recommendation: yes to Gross Duration as the auto-seeded default — but pair it with an optional
manual override, not as a mutually-exclusive alternative

The framing in the question ("we also don't want to rely on manual config properties because users
rarely remember their exact blank periods") treats "auto-computed default" and "manual input" as
competing choices. They aren't — this is the same "sensible computed default + optional escape hatch"
pattern already used elsewhere in this codebase (e.g. `salaryGrowthRate` defaults sensibly but is
user-overridable). Concretely:

1. **Seed `dcContributionYears`/`idecoContributionYears` in `initializeContext()` from Gross Duration**,
   computed as `currentDate.getYear() - qualificationDate.getYear()` (or a precise month-based
   difference if finer granularity matters for the 20-year-threshold formula boundary). This alone
   fixes the current guaranteed-wrong `0` baseline with a value that is correct for the (likely common)
   case of no suspension periods, exactly mirroring how `accumulatedKousei`/pension-months are already
   seeded from real `PensionRecord` history rather than left at 0.

2. **Use `資格取得年月日` (qualification/eligibility date), not `申込年月日` (application date), as the
   seed point.** These are legally distinct: `資格取得年月日` marks the actual start of
   contribution-eligible enrollment (the figure relevant to years-of-service for the 退職所得控除
   calculation), whereas `申込年月日` is only the date the paperwork was filed, which can predate actual
   eligibility. If a user has multiple DC/iDeCo accounts, take the *earliest* `資格取得年月日` across all
   of them.

3. **Add a nullable, optional manual override field** (on `UserProfile` or wherever per-user simulation
   settings already live) — e.g. `dcContributionYearsOverride`. When set, it wins over the computed
   Gross Duration; when absent (the default for users who don't know or don't care about their exact
   blank-period history), the computed value is used as-is. This directly resolves the stated
   dilemma: users who genuinely don't remember their history are no worse off than the proposed
   Gross-Duration-only approach, while users who *do* know about a blank period (or want to be more
   conservative) get a real, low-friction way to correct it — without forcing every user through a
   mandatory manual field.

4. **Optional conservative safety margin**: when computing Gross Duration, floor rather than round
   fractional years (e.g. `Math.floor` on the month-precision duration rather than nearest-year
   rounding). This costs nothing and slightly biases the auto-computed default toward the
   "conservative, not wildly inaccurate" goal the team already stated they want, without needing any
   additional data.

## Why this is proportionate, not scope creep
This adds one date-diff computation and one nullable override field — no new parser, no new scraped
field, no schema for tracking blank periods explicitly. It follows the exact same shape as the
already-working pension-months seeding in this codebase, so there's no new architectural pattern to
maintain. The override field is opt-in and invisible to users who never touch it, so it doesn't
reintroduce the "users rarely remember their exact blank periods" burden the team was rightly trying to
avoid.

***

### Resolution (Implemented)
- Extended `DefinedContributionAccountSnapshot` with `enrollmentDate` and `contributionYearsOverride` to allow both parsed defaults and user escape hatches without polluting `UserProfile`.
- Created `SompoDcMemberInfoParserImpl` and `SbiIdecoMemberInfoParserImpl`. Both use robust CSS selectors (`th:contains(資格取得年月日)`) to strictly identify and extract the enrollment date from the correct "Member Info" pages, preventing parser conflicts across different DC pages.
- Updated `AssetSimulationEngineImpl` to compute the `Gross Duration` using the enrollment date, safely falling back to the `contributionYearsOverride` if provided.
- Verified and documented in `Epic PROGRESS.md`.
- **[Refactor Update]** Replaced the implicit `null` check override mechanism with explicit boolean flags (`isDcContributionYearsOverridden` and `isIdecoContributionYearsOverridden`) in `DefinedContributionUserSettings` for a safer and more robust architecture.
- **[Refactor Update]** Migrated all domain-specific configuration fields out of `UserProfile` into their respective backend settings endpoints (`/salaries/user-settings`, `/assets/nisa/user-settings`, `/assets/dc/user-settings`), including DC/iDeCo fields, NISA configurations, and Salary growth/bonus settings, strictly enforcing Clean Architecture boundaries. This involved creating 3 new Controllers, along with their respective Request/Response DTOs, Application Commands/Results, Mappers, and Service Implementations.
- **[Refactor Update]** Implemented explicit Switch toggles in the React Frontend (`user-profile-form.tsx`) allowing users to explicitly opt-in to manually overriding their DC and iDeCo contribution years, directly tied to the new boolean flags.
