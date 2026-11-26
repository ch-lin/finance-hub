# [RESOLVED] Re-verification (2026-09-02): `paymentDate` immutability genuinely locked down domain/service/frontend, no loophole found — CONFIRMED_FIXED

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies the "## Fix Applied (2026-09-02)" section appended to [Fixed-29-salary-record-paymentdate-mutable-on-update.md](Fixed-29-salary-record-paymentdate-mutable-on-update.md), which claimed `SalaryRecord.changePaymentDate` was removed entirely, the service update path now rejects any `paymentDate` divergence with an `IllegalArgumentException`, and the frontend edit dialog disables the date input with a warning.

## Independently verified (confirmed real)

**Domain layer — `changePaymentDate` genuinely removed, no setter/builder loophole.** `SalaryRecord.java` (`hub-domain/.../model/accounting/salary/SalaryRecord.java`), the `paymentDate` field carries no `@Setter` while every other field on the class explicitly does — the only assignment anywhere in the file is in the constructor (`this.paymentDate = paymentDate`). No `@Builder`, no class-level `@Data`/`@Setter`. The `@NoArgsConstructor(access = PROTECTED)` is Hibernate's hydration hook (field-level reflection, not a setter call), so it isn't an application-reachable mutation path. Repo-wide grep for `changePaymentDate` returns zero hits — the method is fully gone, and nothing was left calling it (which also means the module still compiles logically; a dangling caller would be a hard compile error).

**Service layer — strict equality check confirmed, correctly ordered.** `SalaryRecordServiceImpl.java:79-94`: the update method now does `if (!record.getPaymentDate().equals(command.getPaymentDate())) throw new IllegalArgumentException("Payment date cannot be modified on an existing record. Please delete and recreate the record.")` **before** the only mutating call (`applyCommandToEntity`, line 92). Uses `LocalDate.equals()` (value equality, not reference), and the method is `@Transactional` regardless, so no partial-mutation risk even if the check were positioned differently.

**Legitimate edit path unaffected.** Confirmed the normal case — editing `baseSalary` or other fields while `paymentDate` is unchanged — still flows through to `applyCommandToEntity` and saves normally; only a genuine date mismatch throws.

**Frontend — disabled state and warning confirmed, no null/omission risk.** `add-salary-record-dialog.tsx`: `isEditMode = !!record` drives `disabled={isEditMode}` on the date input plus a tooltip and a red warning `<p>` beneath it. The submit path builds its request body directly from React state (pre-populated from `record.paymentDate` when the edit dialog opens), not from the DOM input's value, so disabling the input does not risk omitting or nulling the field on submit. Backend-side, `SalaryRecordRequest.paymentDate` is `@NotNull` and the PUT endpoint is `@Valid @RequestBody`, so even a hypothetical null would be rejected by bean validation before reaching the service's equality check — belt-and-suspenders, no gap.

## Noted gaps (not required to fix now)
- **No test coverage added.** `SalaryRecordTest.java` still only covers construction/setters/equals — no test asserts the absence of a `paymentDate` setter or exercises the new `IllegalArgumentException` path. No `SalaryRecordServiceImplTest` exists at all in the repo. Same project-wide test-coverage gap noted repeatedly elsewhere this round; worth a follow-up test asserting (a) update with an unchanged `paymentDate` succeeds, (b) update with a changed `paymentDate` throws.
- **Minor UX gap, not a correctness bug**: the dialog's error handler on a failed update shows only a generic "Failed to update salary record." toast, not the backend's specific "delete and recreate" message — a user hitting this new guard via some other path (e.g. a stale form re-submit) wouldn't see why. Not spun off as a separate finding; trivial to fold into a future toast-message pass if this endpoint is touched again.

## On execution
Could not compile — same sandbox `~/.m2` write-denial and parent-POM resolution blocker as every other backend finding this round. Repo-wide grep for the removed method (returning zero hits) is the strongest available substitute confirming no dangling caller.

## Verdict
Genuinely fixed, cross-stack, with no regression to the legitimate edit path. No further action needed on this finding.
