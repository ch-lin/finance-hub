# Fix Fixed 86-01: Grid Search Mortgage Combinations Mismatch

This plan addresses the discrepancy between the estimated combinations and actual loop execution bounds in `GlobalOptimizerServiceImpl` and `GridSearchEngine` for single-mortgage grid searches. As identified in Fixed-86, we will adopt **Direction B** to eliminate logic duplication.

## User Review Required

No major architectural changes, but this refactoring involves centralizing the mortgage date range calculation. Please review the proposed `OptimizationUtils` implementation and the test update.

## Proposed Changes

### `ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core`

#### [NEW] [`OptimizationUtils.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java)
Create a new utility class to encapsulate the shared logic for finding the effective search range of mortgage repayment years.
```java
package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core;

import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import java.util.List;

public final class OptimizationUtils {

    private OptimizationUtils() {}

    public record MortgageYearRange(int minYear, int maxYear) {}

    public static MortgageYearRange calculateMortgageYearRange(List<MortgageRecord> mortgages, int currentYear) {
        if (mortgages == null || mortgages.isEmpty()) {
            return null;
        }
        int minYear = currentYear;
        int endYear = currentYear;
        for (MortgageRecord m : mortgages) {
            int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear();
            if (mEndYear > endYear) {
                endYear = mEndYear;
            }
        }
        return new MortgageYearRange(minYear, endYear + 1); // +1 allows paying off at the very end
    }
}
```

#### [MODIFY] [`GridSearchEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)
Update the constructor to consume the new shared utility instead of implementing the loop itself.
```diff
-            this.minMortgageYear = baseData.getBaseContext().getCurrentYear();
-            int endYear = this.minMortgageYear;
-            for (MortgageRecord m : baseData.getAllMortgages()) {
-                int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear();
-                if (mEndYear > endYear) {
-                    endYear = mEndYear;
-                }
-            }
-            this.maxMortgageYear = endYear + 1; // Allows paying off at the very end
+            var range = OptimizationUtils.calculateMortgageYearRange(
+                    baseData.getAllMortgages(), 
+                    baseData.getBaseContext().getCurrentYear());
+            if (range != null) {
+                this.minMortgageYear = range.minYear();
+                this.maxMortgageYear = range.maxYear();
+            }
```

### `ch.lin.finance.hub.backend.api.app.service.simulation.optimizer`

#### [MODIFY] [`GlobalOptimizerServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)
Update `calculateSearchSpace()` to use the shared `MortgageYearRange` and calculate combinations based on the returned bounds.
```diff
-            if (baseData.getAllMortgages() != null && !baseData.getAllMortgages().isEmpty()) {
-                int currentYear = baseData.getBaseContext().getCurrentYear();
-                int endYear = currentYear;
-                for (MortgageRecord m : baseData.getAllMortgages()) {
-                    int mEndYear = m.getStartDate().plusMonths(m.getLoanTermMonths()).getYear();
-                    if (mEndYear > endYear)
-                        endYear = mEndYear;
-                }
-                mortgageOptions = (endYear - currentYear + 1) + 1; // +1 for never repay
-            }
+            if (baseData.getAllMortgages() != null && !baseData.getAllMortgages().isEmpty()) {
+                var range = ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.OptimizationUtils
+                        .calculateMortgageYearRange(baseData.getAllMortgages(), baseData.getBaseContext().getCurrentYear());
+                if (range != null) {
+                    mortgageOptions = (range.maxYear() - range.minYear() + 1) + 1; // +1 for never repay
+                }
+            }
```

### Tests

#### [MODIFY] [`GlobalOptimizerServiceTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java)
Update the comment reflecting the new accurate combination count (2002 becomes 2003) since the pre-check formula now accounts for the `+1` year correctly.
```diff
-        org.mockito.Mockito.when(m.getLoanTermMonths()).thenReturn(12 * 2000); // 2000 years, so combinations = 2002 >
-                                                                               // 1000
+        org.mockito.Mockito.when(m.getLoanTermMonths()).thenReturn(12 * 2000); // 2000 years, so combinations = 2003 >
+                                                                               // 1000
```

## Verification Plan

### Manual Verification
- 使用者會自行執行 `mvn clean test -Dtest=GlobalOptimizerServiceTest` 來確認測試通過。
- 使用者會確認專案順利建置無誤。
