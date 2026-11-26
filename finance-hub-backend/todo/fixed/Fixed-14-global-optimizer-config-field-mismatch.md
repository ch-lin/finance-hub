# [CRITICAL] Global AI Optimizer frontend sends `config` but backend expects `optimizationConfig` — every toggle is silently dropped

## Severity: Critical (the entire "AI Optimizer" feature is functionally dead — it always returns the user's own baseline dressed up as an AI recommendation, with zero indication that no optimization ever ran)

## Summary
The optimizer request body is built with a field literally named `config`, but the backend's
`GlobalOptimizerRequest` record expects `optimizationConfig`. The mismatch means the user's five
toggle selections (retirement age, pension, iDeCo, NISA, mortgage) never reach the backend, so
nothing is ever actually optimized. Spring Boot's default lenient Jackson deserialization silently
drops the unrecognized `config` key with no error — there is no `@JsonAlias`, no strict
`fail-on-unknown-properties` config, and no proxy/rewrite layer that renames the key in transit.

## Evidence

Frontend
([use-global-optimizer.ts](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts) line 14):
```typescript
export interface GlobalOptimizerRequest {
  config: OptimizationConfig;
  strategy: OptimizationStrategy;
}
// ... body: JSON.stringify(request) // request = { config, strategy }
```
called from `global-optimizer-card.tsx` line 155: `optimize(targetAge, { config, strategy })`.

Backend (`GlobalOptimizerRequest.java`):
```java
public record GlobalOptimizerRequest(
        AssetSimulationRequest baseOverrides,
        OptimizationConfigRequest optimizationConfig,
        OptimizationStrategyRequest strategy) {}
```

Backend (`GlobalOptimizerServiceImpl.buildSeedChromosome`):
```java
OptimizationConfig config = command != null ? command.optimizationConfig() : null;
seed.setRetirementAgeLocked(config == null || !config.optimizeRetirementAge());
...
if (config != null && config.optimizeMortgage()) { seed.setMortgageRepaymentsLocked(false); ... }
else { seed.setMortgageRepaymentsLocked(true); ... }
```
With the payload as sent, `command.optimizationConfig()` is always `null`, so every lock check
resolves to "locked" regardless of what the user selected. A repo-wide grep for the string
`"optimizationConfig"` in the frontend source returns zero occurrences.

## Failure scenario
User opens the "AI Optimizer" tab, turns on all five ToggleCards (Retirement Plan, Pension Fund,
iDeCo, NISA, Mortgage Strategy) and clicks "Run AI Optimizer". The request is sent with `config`
instead of `optimizationConfig`; the backend's `optimizationConfig` field deserializes to `null`;
every dimension (retirement age, pension age, iDeCo age, NISA target, mortgage repayments) is locked
to the seed/baseline value regardless of what the user selected. Grid/GA search then explores a
1-combination space and simply returns the unchanged baseline as `bestSolution`. The UI shows a
green "Analysis Complete" panel with the user's own current settings dressed up as an AI
recommendation, with zero indication that no optimization ever ran.

## Suggested direction (not required by this review, noted for scoping a fix)
Rename the frontend request field from `config` to `optimizationConfig` (or vice versa in the
backend record) so the two sides agree; consider also enabling stricter Jackson deserialization
(`fail-on-unknown-properties`) on this endpoint so a future rename mismatch fails loudly instead of
silently.

---
## 💡 Resolution Status
> [!NOTE]
> **Status:** Fixed ✅
> **Resolution:** Renamed `optimizationConfig` to `config` in `GlobalOptimizerRequest` and `GlobalOptimizationCommand` on the backend to perfectly match the frontend payload name without causing any variable name conflicts. The AI Optimizer now correctly receives the user's toggle selections and explores the real search space.
