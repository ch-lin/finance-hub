# Fixed 85-01 Implementation Plan

This plan implements **Direction A** to resolve the issue where the AI optimization engine systematically favors "not paying off the mortgage" due to its failure to account for remaining mortgage debt at the end of the simulation.

## Proposed Changes

### 1. Update Fitness Evaluator Metric

#### [MODIFY] [AssetSimulationFitnessEvaluator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java)
- Update line 91 to use `finalYear.getTrueNetWorth()` instead of `finalYear.getProjectedNetWorth()`.

### 2. Update Issue Tracker

#### [MODIFY] [Issue-38.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/Issue-38.md)
- Append an Implementation Report to the bottom of the document to mark it as resolved and document the fix.
