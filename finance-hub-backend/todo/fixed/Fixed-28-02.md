# [MEDIUM] Re-verification (2026-09-02): both remaining gaps from FINDING-34-01 are fixed, plus the bonus `MortgageEarlyRepaymentRequest` validation and record refactor — all confirmed correct against current source

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies [Fixed-28-01.md](Fixed-28-01.md)'s appended "Implementation Report (For Claude Review - Follow-up)", which claimed: (1) `page.tsx`'s `handleSave` now explicitly blocks saving with an empty/zero `monthlyPayment`; (2) `@Valid` added to `MortgageRecordRequest.bonusPayments`; (3) bonus fix — `MortgageEarlyRepaymentRequest` given real constraints plus `@Valid` cascade, and frontend loop validation added for both nested arrays; (4) `BonusPaymentRequest` refactored from a standalone file into a nested record.

## What was verified fixed (confirmed real)

**Gap 1 (monthlyPayment guard) — confirmed, and watertight against the empty-string footgun.** `mortgages/page.tsx:97-103`'s `handleSave` now checks `if (!formData.monthlyPayment || formData.monthlyPayment <= 0)` before either `createMortgage`/`updateMortgage` call, showing `toast.error(...)` and returning early. The input's `onChange` always wraps the value with `Number(e.target.value)` before storing (never leaves a raw string in state), and `Number("")` evaluates to `0`, so clearing the field is correctly caught by this check — not just a literal `0`.

**Gap 2 (`@Valid` cascade on `bonusPayments`) — confirmed.** `MortgageRecordRequest.java:30`: `@Valid List<BonusPaymentRequest> bonusPayments`, using `jakarta.validation.Valid` consistent with the rest of the file's imports (no stale `javax` import that would silently no-op). Both `MortgageRecordController.java:68` (create) and `:76` (update) declare `@Valid @RequestBody MortgageRecordRequest` — the cascade is live at both entry points, not just one.

**Bonus fix (`MortgageEarlyRepaymentRequest`) — confirmed, semantically sound.** Nested at `MortgageRecordRequest.java:40-46`: `@NotNull LocalDate repaymentDate`, `@NotNull @Positive Long amount`, `@NotNull EarlyRepaymentType repaymentType`, `@NotNull @Min(0) Long fee` (amount must be `>0`, fee may legitimately be `0`, `id` intentionally left unconstrained for create-vs-update). `@Valid List<MortgageEarlyRepaymentRequest> earlyRepayments` is present at line 38.

**Refactor — confirmed, no dangling references.** `BonusPaymentRequest` is now a nested record inside `MortgageRecordRequest.java:48-51`; a backend-wide grep for `BonusPaymentRequest` returns only the two references inside this file — no leftover standalone `BonusPaymentRequest.java`, no stale imports elsewhere. (A separate, pre-existing, unrelated `MortgageEarlyRepaymentRequest` name-collision candidate in `AssetSimulationRequest.java` was checked and confirmed to be a genuinely different nested record with different fields — not a collision introduced by this refactor; see the new finding filed below.)

**Frontend loop validation — confirmed, range matches backend exactly.** `mortgages/page.tsx:105-137` loops over both arrays before constructing the request: `bonusPayments` month check (`bp.month < 1 || bp.month > 12`) matches the backend's `@Min(1)@Max(12)` exactly (no off-by-one/array-index confusion), and `earlyRepayments` checks date presence, `amount <= 0`, `fee < 0`, and repayment-type presence. Each violation `toast.error`s and `return`s before the network call — genuinely blocking, not just warning.

**Mapper chain intact.** `MortgageRecordMapper.java` (MapStruct) matches by accessor name, not by nesting origin, so the refactor from standalone class to nested record didn't break `BonusPaymentRequest.month()/amount()` → `BonusPaymentCommand` mapping, nor `MortgageEarlyRepaymentRequest`'s five-accessor mapping into `SaveMortgageRecordCommand.MortgageEarlyRepaymentCommand`. Traced through to `MortgageRecordServiceImpl.java:157,197-198`, which builds the domain `BonusPayment` from `b.month()`/`b.amount()` — unaffected by the DTO-layer refactor.

## Verification method note
Could not run `mvn compile` to mechanically confirm compilation (the sandbox blocks writes to the default `~/.m2` for missing plugin artifacts, and offline mode lacks the required plugin jars) — this verdict rests on careful manual source and type-signature tracing (including MapStruct accessor-name matching), not an actual build. Given the specificity and cross-checked nature of the citations above (controller, DTO, mapper, service all independently traced to the same conclusion), this is treated as sufficient for a CONFIRMED_FIXED verdict, but a first-hand build/test run remains the strongest possible confirmation if available outside this sandbox.

## New, functionally-unrelated issue discovered incidentally
While confirming `MortgageEarlyRepaymentRequest` wasn't a naming collision, found that `AssetSimulationRequest.java`'s own (differently-scoped) `mortgageEarlyRepayments` map has neither a `@Valid` cascade nor any field-level constraints on its nested type at all — filed separately as [Fixed-32-assetsimulationrequest-mortgageearlyrepayments-unvalidated.md](Fixed-32-assetsimulationrequest-mortgageearlyrepayments-unvalidated.md), since it's a different feature (Asset Simulation overrides, not Mortgage Record CRUD) untouched by this fix.

## Suggested direction
None required — this closes both remaining Fixed-28-01 gaps and the bonus scope the developer added on their own initiative.
