# [LOW/MEDIUM] `GlobalOptimizerController`'s optimizer endpoint accepts `AssetSimulationRequest` (via `GlobalOptimizerRequest.baseOverrides`) with zero `@Valid` cascade — FINDING-39's fix doesn't reach this call site

## Severity: Low/Medium (same risk profile as Fixed-23/28/32: malformed simulation-override data reaches simulation math with no server-side rejection — scoped to an ad-hoc optimizer request, not persisted account data)

## Note on discovery
Found incidentally while re-verifying [Fixed-32-01.md](Fixed-32-01.md)'s claim that `@Valid` is correctly wired at the controller boundary for `AssetSimulationRequest`. Confirmed it is, for the primary `/simulation` endpoint (`AssetSimulationController.java:44`) — but while checking for other call sites of the same DTO, found that `GlobalOptimizerController`'s endpoint carries the identical `AssetSimulationRequest` type (nested inside `GlobalOptimizerRequest.baseOverrides`) with no validation path anywhere. This is a different controller and a different wrapper DTO (`GlobalOptimizerRequest`), not literally part of Fixed-32's ask (which was scoped to `AssetSimulationRequest`'s own field annotations, already correctly fixed) — filed as a fresh top-level finding per the standing review convention.

## Summary
`GlobalOptimizerRequest.java:4`'s `baseOverrides` field (type `AssetSimulationRequest`) has no `@Valid` annotation, and `GlobalOptimizerController.java:36`'s `@RequestBody GlobalOptimizerRequest request` parameter itself has no `@Valid` either. Since Jakarta Bean Validation only cascades from an `@Valid`-annotated boundary, **none** of `AssetSimulationRequest`'s constraints — including the ones just correctly added under Fixed-32 (`mortgageEarlyRepayments`/`mortgageRateScenarios` field validation) — are ever triggered when a request reaches the app via the optimizer endpoint. This is the same validation-cascade anti-pattern already fixed three times elsewhere (Fixed-23/23-01, Fixed-28/28-01, Fixed-32/32-01), now confirmed at a fourth call site.

## Evidence
[GlobalOptimizerRequest.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/dto/request/GlobalOptimizerRequest.java)
line 4: `baseOverrides` field of type `AssetSimulationRequest` — no `@Valid`.

[GlobalOptimizerController.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/controller/GlobalOptimizerController.java)
line 36: `@RequestBody GlobalOptimizerRequest request` — no `@Valid` on the parameter itself, which is the boundary Spring needs in order to cascade into `baseOverrides` at all.

## Failure scenario
A malformed optimizer request — e.g. a negative `mortgageEarlyRepayments` amount, or a nonsensical `year`/`startYear` — submitted via `POST /simulation/optimizer/{targetAge}` passes validation completely untouched and flows directly into optimizer/simulation math, producing undefined or misleading results with no error surfaced. The identical payload shape submitted via the plain `/simulation` endpoint would now correctly be rejected per Fixed-32's fix — so the same bad data is blocked on one route and silently accepted on another.

## Suggested direction (not required by this review, noted for scoping a fix)
Add `@Valid` to `GlobalOptimizerRequest.baseOverrides` and add `@Valid` to the `@RequestBody GlobalOptimizerRequest request` parameter in `GlobalOptimizerController.java:36`, mirroring the pattern just applied at `AssetSimulationController.java:44`. Worth a final repo-wide sweep for any other controller or wrapper DTO that reuses `AssetSimulationRequest` (or any other previously-fixed nested DTO) without its own `@Valid` cascade, since this is now the fourth confirmed instance of this exact anti-pattern in this codebase.

---

## 修正報告 (Fix Report)

> [!IMPORTANT]
> **主動承擔聲明**
> 這是我在之前的修改中遺漏的伺服器端驗證。我當時沒有考慮到 `GlobalOptimizerRequest` 作為另一個進入點，也需要正確的 `@Valid` 級聯來觸發 `AssetSimulationRequest` 內部的驗證邏輯。這是不完整的實作，我會為此負責並已完成全面修正。

### 完成的修改
1. **[`GlobalOptimizerRequest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerRequest.java)**:
   - 引入了 `jakarta.validation.Valid`。
   - 在 `baseOverrides`、`config`、`strategy` 等所有的巢狀物件欄位上，都加上了 `@Valid` 註解。確保未來任何進一步的內部驗證需求，都會自動向下級聯觸發。

2. **[`GlobalOptimizerController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/GlobalOptimizerController.java)**:
   - 引入了 `jakarta.validation.Valid`。
   - 在 `optimize` 方法的 `@RequestBody GlobalOptimizerRequest request` 參數前方，加上了 `@Valid`，作為啟動 Spring Boot Controller 層級參數驗證的邊界條件。

這些修改確保了 `GlobalOptimizerController` 的所有 `/simulation/optimizer/{targetAge}` 請求，現在會完整且強制地執行在 `AssetSimulationRequest` 中所定義的全部驗證規則（例如之前 Fixed-32 新增的那些約束）。
