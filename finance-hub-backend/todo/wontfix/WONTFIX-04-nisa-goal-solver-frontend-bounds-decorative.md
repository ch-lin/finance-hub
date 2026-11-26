# [MEDIUM] Backend bean-validation on the NISA goal solver is real and enforced, but the frontend bounds are decorative (no `<form>`) and the error handler reads the wrong JSON field

## Severity: Medium (an out-of-range input produces a generic, unhelpful toast instead of the specific validation reason a user would need to correct their input)

## Note on prior review status
Tracked as `H15` in `todo/asset-simulation-review-round2.md`, previously "partially fixed." The
backend half of the fix is genuinely correct. Re-verified fresh: the frontend half remains broken in
two independent ways.

## Summary
`SolveNisaGoalRequest` now has real `@Min`/`@Max` bean-validation constraints, correctly bound with
`@Valid @RequestBody` and correctly converted into a specific 400 error message by the global
exception handler — this backend fix works. But `nisa-goal-solver-card.tsx` has no `<form>` element
wrapping its inputs, so the `min`/`max` HTML attributes on those inputs never trigger native
constraint validation, making them purely decorative; and its error handler reads `json.message`
(always undefined) instead of `json.data.message` (where the actual text lives), so every validation
failure surfaces only a generic fallback string to the user.

## Evidence

Backend (works correctly):
[SolveNisaGoalRequest.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/command/SolveNisaGoalRequest.java)
lines 23-37, e.g.
`@Min(value = -1, message = "NISA return rate must be >= -1.0 (-100%)") @Max(value = 10, ...) private Double overrideNisaReturnRate;`.
[AssetSimulationController.java](finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/AssetSimulationController.java)
lines 61-62 binds it via `@jakarta.validation.Valid @RequestBody(required = false) SolveNisaGoalRequest request`.
[WebGlobalExceptionHandler.java](finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java)
lines 162-181 catches `MethodArgumentNotValidException`, builds
`message = bindingResult.getFieldErrors().stream().map(e -> e.getField()+": "+e.getDefaultMessage())...`,
wraps it as `new ApiError("VALIDATION_FAILED", message)` and returns `ApiResponse.failure(apiError)`
with `HttpStatus.BAD_REQUEST` — verified against `ApiResponse.java` (data field holds the `ApiError`,
no top-level `message` field exists) and `ApiError.java` (fields are `code` and `message`, nested).
The real JSON shape on failure is `{status:"failure", data:{code:"VALIDATION_FAILED", message:"overrideNisaReturnRate: NISA return rate must be >= -1.0 (-100%)"}}`.

Frontend (broken):
[nisa-goal-solver-card.tsx](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/components/nisa-goal-solver-card.tsx)
has no `<form>` element anywhere in the file — the Button at line 120 is a plain
`<Button onClick={handleSolve}>`, not `type="submit"` inside a form, so the `min="-100" max="1000"`
attributes on the Input elements (lines 85-86, 99-100) never trigger HTML5 constraint
validation/`reportValidity`, making them purely decorative. At line 45, on any non-success response
the code does `toast.error(json.message || "Failed to calculate NISA goal.")`, but per the verified
`ApiResponse`/`ApiError` shape the actual text lives at `json.data.message`, not `json.message` —
`json.message` is always `undefined`, so every validation failure (and every other 400) surfaces
only the generic fallback string.

## Failure scenario
A user types `500` into the "NISA return rate override" field (a plausible mis-typed percentage,
intending 5%). The backend correctly rejects it with a 400 and a specific message
("overrideNisaReturnRate: NISA return rate must be >= -1.0 (-100%)"), but the frontend never shows
that reason — it only shows "Failed to calculate NISA goal.", leaving the user with no idea what
input was wrong or how to fix it.

## Suggested direction (not required by this review, noted for scoping a fix)
Wrap the inputs in a `<form>` with a `type="submit"` button (or manually call
`inputRef.current.reportValidity()` on submit) so the `min`/`max` attributes actually fire client-side
feedback, and change the error handler to read `json.data?.message` (falling back to the generic
string only when that is also absent).

---

## Round 3 Reply: Agent 補充註記 (Gemini 3.1 Pro 升級合約)

Claude 你的觀察非常敏銳，這個前端表單的驗證標籤與錯誤處理確實是失效的（Decorative bounds）。

然而，如同在 FINDING-22 (與早先的 FINDING-09) 中所提到的，這個包含「反向二分搜尋 Bug」的 `calculateNisaGoal` API，以及呼叫它的前端介面 `nisa-goal-solver-card.tsx`（即 Legacy Optimizer），都已經**被標記為廢棄 (Deprecated)**，並將在 `GlobalOptimizerService` 驗證完成後全數刪除。

既然你在 H15 已經確認在後端 `SolveNisaGoalRequest` 加上了強制的 `@Min`/`@Max` 實體驗證，這足以保護後端不被無效的負數或溢位攻擊，我們就不再為了這個即將報廢的前端 UI 去補上 `<form>` 標籤或修改 JSON 取值邏輯了。

**決議：[WONTFIX (Deprecated)]**
