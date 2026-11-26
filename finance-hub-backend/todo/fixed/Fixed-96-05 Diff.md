 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                                                                                                                      10010  10.93G   4.19    21:07:52  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaUserSettingsServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaUserSettingsServiceImpl.java
index ee5a315c..8e041363 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaUserSettingsServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaUserSettingsServiceImpl.java
@@ -54,16 +54,12 @@ public class NisaUserSettingsServiceImpl implements NisaUserSettingsService {
     @Transactional
     public NisaUserSettingsResult saveSettings(@NonNull SaveNisaUserSettingsCommand command) {
         NisaUserSettings settings = repository.findFirstBy().orElseGet(() -> new NisaUserSettings());
-
-        if (command.monthlyNisaContribution() != null) {
-            settings.setMonthlyNisaContribution(command.monthlyNisaContribution());
-        }
+        settings.setMonthlyGrowthContribution(command.monthlyGrowthContribution());
+        settings.setMonthlyTsumitateContribution(command.monthlyTsumitateContribution());
         if (command.nisaExpectedReturnRate() != null) {
             settings.setNisaExpectedReturnRate(command.nisaExpectedReturnRate());
         }
-
         settings = repository.save(Objects.requireNonNull(settings));
-
         return NisaUserSettingsResult.fromEntity(settings, null);
     }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/SaveNisaUserSettingsCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/SaveNisaUserSettingsCommand.java
index 20e5604c..66aed9ee 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/SaveNisaUserSettingsCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/command/SaveNisaUserSettingsCommand.java
@@ -24,6 +24,7 @@
 package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command;
 
 public record SaveNisaUserSettingsCommand(
-        Long monthlyNisaContribution,
+        Long monthlyGrowthContribution,
+        Long monthlyTsumitateContribution,
         Double nisaExpectedReturnRate) {
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaUserSettingsResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaUserSettingsResult.java
index 06946d2d..a764319f 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaUserSettingsResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/result/NisaUserSettingsResult.java
@@ -36,7 +36,8 @@ import lombok.NoArgsConstructor;
 @AllArgsConstructor
 @EqualsAndHashCode(callSuper = true)
 public class NisaUserSettingsResult extends BaseResult {
-    private Long monthlyNisaContribution;
+    private Long monthlyGrowthContribution;
+    private Long monthlyTsumitateContribution;
     private Double nisaExpectedReturnRate;
 
     public static NisaUserSettingsResult fromEntity(NisaUserSettings entity, String ownerUserId) {
@@ -44,7 +45,8 @@ public class NisaUserSettingsResult extends BaseResult {
             return null;
         }
         NisaUserSettingsResult result = new NisaUserSettingsResult();
-        result.setMonthlyNisaContribution(entity.getMonthlyNisaContribution());
+        result.setMonthlyGrowthContribution(entity.getMonthlyGrowthContribution());
+        result.setMonthlyTsumitateContribution(entity.getMonthlyTsumitateContribution());
         result.setNisaExpectedReturnRate(entity.getNisaExpectedReturnRate());
 
         result.setOwnerUserId(ownerUserId);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java
index a7362fdc..c51c7417 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java
@@ -33,7 +33,8 @@ import java.util.Map;
  * scenarios.
  */
 public record SimulationOverrides(
-        Long testNisaMonthlyContribution,
+        Long testNisaGrowthMonthlyContribution,
+        Long testNisaTsumitateMonthlyContribution,
         Integer targetRetirementAgeOverride,
         Integer pensionStartAgeOverride,
         Integer idecoStartAgeOverride,
@@ -46,10 +47,12 @@ public record SimulationOverrides(
             Integer targetRetirementAge,
             Integer pensionStartAge,
             Integer idecoStartAge,
-            Long nisaMonthlyContribution) {
+            Long nisaGrowthMonthlyContribution,
+            Long nisaTsumitateMonthlyContribution) {
         public boolean anyUsed() {
             return targetRetirementAge != null || pensionStartAge != null || idecoStartAge != null
-                    || nisaMonthlyContribution != null;
+                    || nisaGrowthMonthlyContribution != null
+                    || nisaTsumitateMonthlyContribution != null;
         }
     }
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/SimulationOptimizerServiceOldImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/SimulationOptimizerServiceOldImpl.java
index dab62bc3..07fae710 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/SimulationOptimizerServiceOldImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/SimulationOptimizerServiceOldImpl.java
@@ -97,7 +97,8 @@ public class SimulationOptimizerServiceOldImpl implements SimulationOptimizerSer
             } catch (CloneNotSupportedException e) {
                 throw new IllegalStateException("Failed to clone SimulationContext", e);
             }
-            SimulationOverrides overrides = new SimulationOverrides(mid, null, null, null, null, null, null, null);
+            SimulationOverrides overrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
+                    null);
             List<AssetSimulationYearlyResult> simulation = assetSimulationEngine.runSimulationWithData(
                     clone, targetAge, overrides);
 
@@ -150,7 +151,8 @@ public class SimulationOptimizerServiceOldImpl implements SimulationOptimizerSer
             }
 
             // NOTE: mortgageEarlyRepaymentYear removed; this old optimizer is deprecated.
-            SimulationOverrides overrides = new SimulationOverrides(null, null, null, null, null, null, null, null);
+            SimulationOverrides overrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
+                    null);
             List<AssetSimulationYearlyResult> simulation = assetSimulationEngine.runSimulationWithData(clone, targetAge,
                     overrides);
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java
index b937ff22..93e64ce5 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/FundOverrideApplier.java
@@ -30,6 +30,7 @@ import java.util.stream.Collectors;
 import org.springframework.stereotype.Component;
 
 import ch.lin.finance.hub.backend.api.app.service.simulation.command.SimulationOverrides;
+import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.NisaAccountType;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationFund;
 import lombok.extern.slf4j.Slf4j;
@@ -69,8 +70,13 @@ public class FundOverrideApplier {
         }
 
         // 1. Apply global NISA contribution test goal (if any)
-        if (overrides.testNisaMonthlyContribution() != null) {
-            applyGlobalNisaContribution(context, overrides.testNisaMonthlyContribution());
+        if (overrides.testNisaGrowthMonthlyContribution() != null) {
+            applyGlobalContribution(context.getNisaFunds(), overrides.testNisaGrowthMonthlyContribution(),
+                    NisaAccountType.GROWTH, context.getConfig().getNisaExpectedReturnRate());
+        }
+        if (overrides.testNisaTsumitateMonthlyContribution() != null) {
+            applyGlobalContribution(context.getNisaFunds(), overrides.testNisaTsumitateMonthlyContribution(),
+                    NisaAccountType.TSUMITATE, context.getConfig().getNisaExpectedReturnRate());
         }
 
         // 2. Apply explicit per-fund overrides (if any)
@@ -91,26 +97,28 @@ public class FundOverrideApplier {
         }
     }
 
-    private void applyGlobalNisaContribution(SimulationContext context, long testNisaMonthlyContribution) {
-        List<SimulationFund> nisaFunds = context.getNisaFunds();
+    private void applyGlobalContribution(List<SimulationFund> allNisaFunds, long testMonthlyContribution,
+            NisaAccountType accountType, Double defaultReturnRate) {
+        List<SimulationFund> targetFunds = allNisaFunds.stream()
+                .filter(f -> f.getNisaAccountType() == accountType)
+                .collect(Collectors.toList());
 
-        long totalCurrentNisaMonthly = nisaFunds.stream()
+        long totalCurrentMonthly = targetFunds.stream()
                 .mapToLong(f -> f.getMonthlyContribution())
                 .sum();
 
-        if (totalCurrentNisaMonthly > 0) {
+        if (totalCurrentMonthly > 0) {
             // Distribute proportionally
             long allocatedSoFar = 0;
-            for (int i = 0; i < nisaFunds.size(); i++) {
-                SimulationFund fund = nisaFunds.get(i);
-                if (i == nisaFunds.size() - 1) {
-                    // Last fund absorbs the rounding difference
-                    long monthly = testNisaMonthlyContribution - allocatedSoFar;
+            for (int i = 0; i < targetFunds.size(); i++) {
+                SimulationFund fund = targetFunds.get(i);
+                if (i == targetFunds.size() - 1) {
+                    long monthly = testMonthlyContribution - allocatedSoFar;
                     fund.setMonthlyContribution(monthly);
                     fund.setAnnualContribution(monthly * 12);
                 } else {
-                    double ratio = (double) fund.getMonthlyContribution() / totalCurrentNisaMonthly;
-                    long allocatedMonthly = Math.round(testNisaMonthlyContribution * ratio);
+                    double ratio = (double) fund.getMonthlyContribution() / totalCurrentMonthly;
+                    long allocatedMonthly = Math.round(testMonthlyContribution * ratio);
                     fund.setMonthlyContribution(allocatedMonthly);
                     fund.setAnnualContribution(allocatedMonthly * 12);
                     allocatedSoFar += allocatedMonthly;
@@ -118,25 +126,27 @@ public class FundOverrideApplier {
             }
         } else {
             // Zero current contribution
-            if (nisaFunds.isEmpty()) {
+            if (targetFunds.isEmpty()) {
                 // Create a virtual fund to absorb the test contribution
+                long newFundId = allNisaFunds.stream().mapToLong(f -> f.getFundId()).max().orElse(0L) + 1L;
                 SimulationFund virtualFund = SimulationFund.builder()
-                        .fundId(0L)
-                        .fundName("Simulated NISA (Virtual)")
+                        .fundId(newFundId)
+                        .fundName("Simulated NISA " + accountType.name() + " (Virtual)")
                         .balance(0L)
-                        .monthlyContribution(testNisaMonthlyContribution)
-                        .annualContribution(testNisaMonthlyContribution * 12)
-                        .expectedReturnRate(context.getConfig().getNisaExpectedReturnRate())
+                        .monthlyContribution(testMonthlyContribution)
+                        .annualContribution(testMonthlyContribution * 12)
+                        .expectedReturnRate(defaultReturnRate)
+                        .nisaAccountType(accountType)
                         .build();
-                nisaFunds.add(virtualFund);
+                allNisaFunds.add(virtualFund);
             } else {
                 // Distribute evenly among existing funds
-                long perFund = testNisaMonthlyContribution / nisaFunds.size();
+                long perFund = testMonthlyContribution / targetFunds.size();
                 long allocatedSoFar = 0;
-                for (int i = 0; i < nisaFunds.size(); i++) {
-                    SimulationFund fund = nisaFunds.get(i);
-                    if (i == nisaFunds.size() - 1) {
-                        long monthly = testNisaMonthlyContribution - allocatedSoFar;
+                for (int i = 0; i < targetFunds.size(); i++) {
+                    SimulationFund fund = targetFunds.get(i);
+                    if (i == targetFunds.size() - 1) {
+                        long monthly = testMonthlyContribution - allocatedSoFar;
                         fund.setMonthlyContribution(monthly);
                         fund.setAnnualContribution(monthly * 12);
                     } else {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java
index 09b17f96..14f41109 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java
@@ -69,23 +69,39 @@ public class NisaContextBuilder {
                         Long val = record.getTotalPrincipal();
                         return val != null ? val : 0L;
                     }).sum();
-        } else if (nisaUserSettingsOpt.isPresent() && nisaUserSettingsOpt.get().getMonthlyNisaContribution() != null
-                && nisaUserSettingsOpt.get().getMonthlyNisaContribution() > 0) {
+        } else if (nisaUserSettingsOpt.isPresent()) {
             Double expectedReturn = nisaUserSettingsOpt.get().getNisaExpectedReturnRate();
             if (expectedReturn == null) {
                 expectedReturn = config.getNisaExpectedReturnRate();
             }
-            var settingsDerivedFund = SimulationFund
-                    .builder()
-                    .fundId(0L)
-                    .fundName("NISA (Profile Setting)")
-                    .balance(0L)
-                    .monthlyContribution(nisaUserSettingsOpt.get().getMonthlyNisaContribution())
-                    .annualContribution(nisaUserSettingsOpt.get().getMonthlyNisaContribution() * 12L)
-                    .expectedReturnRate(expectedReturn)
-                    .nisaAccountType(NisaAccountType.GROWTH) // Default to Growth for settings-derived fund
-                    .build();
-            context.getNisaFunds().add(settingsDerivedFund);
+
+            Long growthMonthly = nisaUserSettingsOpt.get().getMonthlyGrowthContribution();
+            if (growthMonthly != null && growthMonthly > 0) {
+                var growthFund = SimulationFund.builder()
+                        .fundId(0L)
+                        .fundName("NISA Growth (Profile Setting)")
+                        .balance(0L)
+                        .monthlyContribution(growthMonthly)
+                        .annualContribution(growthMonthly * 12L)
+                        .expectedReturnRate(expectedReturn)
+                        .nisaAccountType(NisaAccountType.GROWTH)
+                        .build();
+                context.getNisaFunds().add(growthFund);
+            }
+
+            Long tsumitateMonthly = nisaUserSettingsOpt.get().getMonthlyTsumitateContribution();
+            if (tsumitateMonthly != null && tsumitateMonthly > 0) {
+                var tsumitateFund = SimulationFund.builder()
+                        .fundId(1L)
+                        .fundName("NISA Tsumitate (Profile Setting)")
+                        .balance(0L)
+                        .monthlyContribution(tsumitateMonthly)
+                        .annualContribution(tsumitateMonthly * 12L)
+                        .expectedReturnRate(expectedReturn)
+                        .nisaAccountType(NisaAccountType.TSUMITATE)
+                        .build();
+                context.getNisaFunds().add(tsumitateFund);
+            }
         }
         context.setNisaBalance(startingNisaBalance);
         context.setAccumulatedNisaPrincipal(startingNisaPrincipal);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
index 6bd4a231..253026f1 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java
@@ -122,7 +122,8 @@ public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {
         Integer appliedRetirementAge = null;
         Integer appliedPensionAge = null;
         Integer appliedIdecoAge = null;
-        Long appliedNisa = null;
+        Long appliedNisaGrowth = null;
+        Long appliedNisaTsumitate = null;
 
         if (applyProfileToOptimizerBaseline) {
             Objects.requireNonNull(userProfile, "User profile is required for applying profile to optimizer baseline");
@@ -135,21 +136,27 @@ public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {
             if (seed.isIdecoStartAgeLocked() && userProfile.getTaxAdvantagedWithdrawalAge() != null) {
                 appliedIdecoAge = userProfile.getTaxAdvantagedWithdrawalAge();
             }
-            if (seed.isNisaMonthlyTargetLocked()) {
+            if (seed.isNisaGrowthTargetLocked() && seed.isNisaTsumitateTargetLocked()) {
                 Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
-                if (nisaSettings.isPresent() && nisaSettings.get().getMonthlyNisaContribution() != null) {
-                    appliedNisa = nisaSettings.get().getMonthlyNisaContribution();
+                if (nisaSettings.isPresent()) {
+                    Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
+                    Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
+                    long g = growthOpt != null ? growthOpt : 0L;
+                    long t = tsumitateOpt != null ? tsumitateOpt : 0L;
+                    appliedNisaGrowth = g;
+                    appliedNisaTsumitate = t;
                 }
             }
         }
 
         SimulationOverrides.ProfileBaselineUsageDetails appliedProfileBaselineDetails = new SimulationOverrides.ProfileBaselineUsageDetails(
-                appliedRetirementAge, appliedPensionAge, appliedIdecoAge, appliedNisa);
+                appliedRetirementAge, appliedPensionAge, appliedIdecoAge, appliedNisaGrowth, appliedNisaTsumitate);
 
         // Preserve non-optimized fields from baseOverrides (like fund overrides) if
         // necessary
         return new SimulationOverrides(
-                optimizedOverrides.testNisaMonthlyContribution(),
+                optimizedOverrides.testNisaGrowthMonthlyContribution(),
+                optimizedOverrides.testNisaTsumitateMonthlyContribution(),
                 optimizedOverrides.targetRetirementAgeOverride(),
                 optimizedOverrides.pensionStartAgeOverride(),
                 optimizedOverrides.idecoStartAgeOverride(),
@@ -200,17 +207,24 @@ public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {
         }
 
         // NISA Target
-        seed.setNisaMonthlyTargetLocked(config == null || !config.optimizeNisa());
+        boolean nisaLocked = (config == null || !config.optimizeNisa());
+        seed.setNisaGrowthTargetLocked(nisaLocked);
+        seed.setNisaTsumitateTargetLocked(nisaLocked);
 
-        if (seed.isNisaMonthlyTargetLocked() && applyProfileToOptimizerBaseline) {
+        if (nisaLocked && applyProfileToOptimizerBaseline) {
             Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
-            if (nisaSettings.isPresent() && nisaSettings.get().getMonthlyNisaContribution() != null) {
-                seed.setNisaMonthlyTarget(nisaSettings.get().getMonthlyNisaContribution());
+            if (nisaSettings.isPresent()) {
+                Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
+                Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
+                seed.setNisaGrowthTarget(growthOpt != null ? growthOpt : 0L);
+                seed.setNisaTsumitateTarget(tsumitateOpt != null ? tsumitateOpt : 0L);
             } else {
-                seed.setNisaMonthlyTarget(0L);
+                seed.setNisaGrowthTarget(0L);
+                seed.setNisaTsumitateTarget(0L);
             }
         } else {
-            seed.setNisaMonthlyTarget(0L);
+            seed.setNisaGrowthTarget(0L);
+            seed.setNisaTsumitateTarget(0L);
         }
 
         // Mortgage Repayments
@@ -250,12 +264,14 @@ public class GlobalOptimizerServiceImpl implements GlobalOptimizerService {
             combinations *= mortgageOptions;
         }
 
-        if (!seed.isNisaMonthlyTargetLocked()) {
-            // 0 to 300,000, step 10,000 (31 options)
-            long options = (OptimizationConstants.MAX_NISA_MONTHLY_TARGET
-                    - OptimizationConstants.MIN_NISA_MONTHLY_TARGET) / OptimizationConstants.NISA_MONTHLY_TARGET_STEP
-                    + 1;
-            combinations *= options;
+        if (!seed.isNisaGrowthTargetLocked() || !seed.isNisaTsumitateTargetLocked()) {
+            // Growth: 0 to 200,000 (21 options)
+            long growthOptions = OptimizationConstants.MAX_NISA_GROWTH_TARGET
+                    / OptimizationConstants.NISA_MONTHLY_TARGET_STEP + 1;
+            // Tsumitate: 0 to 100,000 (11 options)
+            long tsumitateOptions = OptimizationConstants.MAX_NISA_TSUMITATE_TARGET
+                    / OptimizationConstants.NISA_MONTHLY_TARGET_STEP + 1;
+            combinations *= (growthOptions * tsumitateOptions);
         }
         if (!seed.isRetirementAgeLocked()) {
             // 50 to 80 (31 options)
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java
index 77dc2fd0..a6badbb5 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java
@@ -23,7 +23,9 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core;
 
+import java.util.ArrayList;
 import java.util.HashMap;
+import java.util.List;
 import java.util.Map;
 import java.util.Random;
 
@@ -35,7 +37,6 @@ import lombok.NoArgsConstructor;
 public class Chromosome {
 
     // Global constraints for random generation
-    private static final long MAX_NISA_MONTHLY = 300_000L;
     private static final int MAX_REPAYMENT_COUNT = 5;
 
     // A flag indicating this gene is locked (will not mutate)
@@ -48,8 +49,11 @@ public class Chromosome {
     private boolean idecoStartAgeLocked;
     private Integer idecoStartAge;
 
-    private boolean nisaMonthlyTargetLocked;
-    private Long nisaMonthlyTarget;
+    private boolean nisaGrowthTargetLocked;
+    private Long nisaGrowthTarget;
+
+    private boolean nisaTsumitateTargetLocked;
+    private Long nisaTsumitateTarget;
 
     // 專門給 Grid Search 使用的一維房貸結清年份
     private Integer gridSingleMortgageRepaymentYear;
@@ -70,6 +74,41 @@ public class Chromosome {
         }
     }
 
+    /**
+     * Copy Constructor for deep cloning a Chromosome.
+     */
+    public Chromosome(Chromosome other) {
+        this.retirementAgeLocked = other.retirementAgeLocked;
+        this.retirementAge = other.retirementAge;
+
+        this.pensionStartAgeLocked = other.pensionStartAgeLocked;
+        this.pensionStartAge = other.pensionStartAge;
+
+        this.idecoStartAgeLocked = other.idecoStartAgeLocked;
+        this.idecoStartAge = other.idecoStartAge;
+
+        this.nisaGrowthTargetLocked = other.nisaGrowthTargetLocked;
+        this.nisaGrowthTarget = other.nisaGrowthTarget;
+
+        this.nisaTsumitateTargetLocked = other.nisaTsumitateTargetLocked;
+        this.nisaTsumitateTarget = other.nisaTsumitateTarget;
+
+        this.gridSingleMortgageRepaymentYear = other.gridSingleMortgageRepaymentYear;
+
+        this.mortgageRepaymentsLocked = other.mortgageRepaymentsLocked;
+
+        this.mortgageRepayments = new HashMap<>();
+        if (other.mortgageRepayments != null) {
+            for (Map.Entry<Long, List<MortgageRepaymentGene>> entry : other.mortgageRepayments.entrySet()) {
+                List<MortgageRepaymentGene> genes = new ArrayList<>();
+                for (MortgageRepaymentGene g : entry.getValue()) {
+                    genes.add(new MortgageRepaymentGene(g.getYear(), g.getAmount()));
+                }
+                this.mortgageRepayments.put(entry.getKey(), genes);
+            }
+        }
+    }
+
     public void mutate(Random random, int currentYear, int currentAge, int lifeExpectancy) {
         if (!retirementAgeLocked && retirementAge != null) {
             // mutate by +/- 1 to 2 years
@@ -84,10 +123,15 @@ public class Chromosome {
             idecoStartAge = Math.min(OptimizationConstants.MAX_IDECO_AGE,
                     Math.max(OptimizationConstants.MIN_IDECO_AGE, idecoStartAge + (random.nextInt(5) - 2)));
         }
-        if (!nisaMonthlyTargetLocked && nisaMonthlyTarget != null) {
-            // mutate NISA by steps of 10,000 up to 50,000
+        if (!nisaGrowthTargetLocked && nisaGrowthTarget != null) {
+            long step = (random.nextInt(11) - 5) * 10000L;
+            nisaGrowthTarget = Math.min(OptimizationConstants.MAX_NISA_GROWTH_TARGET,
+                    Math.max(0L, nisaGrowthTarget + step));
+        }
+        if (!nisaTsumitateTargetLocked && nisaTsumitateTarget != null) {
             long step = (random.nextInt(11) - 5) * 10000L;
-            nisaMonthlyTarget = Math.min(MAX_NISA_MONTHLY, Math.max(0L, nisaMonthlyTarget + step));
+            nisaTsumitateTarget = Math.min(OptimizationConstants.MAX_NISA_TSUMITATE_TARGET,
+                    Math.max(0L, nisaTsumitateTarget + step));
         }
 
         if (!mortgageRepaymentsLocked && mortgageRepayments != null) {
@@ -137,14 +181,16 @@ public class Chromosome {
         child.setRetirementAgeLocked(this.retirementAgeLocked);
         child.setPensionStartAgeLocked(this.pensionStartAgeLocked);
         child.setIdecoStartAgeLocked(this.idecoStartAgeLocked);
-        child.setNisaMonthlyTargetLocked(this.nisaMonthlyTargetLocked);
+        child.setNisaGrowthTargetLocked(this.nisaGrowthTargetLocked);
+        child.setNisaTsumitateTargetLocked(this.nisaTsumitateTargetLocked);
         child.setMortgageRepaymentsLocked(this.mortgageRepaymentsLocked);
 
         // Uniform crossover for scalar values
         child.setRetirementAge(random.nextBoolean() ? this.retirementAge : other.retirementAge);
         child.setPensionStartAge(random.nextBoolean() ? this.pensionStartAge : other.pensionStartAge);
         child.setIdecoStartAge(random.nextBoolean() ? this.idecoStartAge : other.idecoStartAge);
-        child.setNisaMonthlyTarget(random.nextBoolean() ? this.nisaMonthlyTarget : other.nisaMonthlyTarget);
+        child.setNisaGrowthTarget(random.nextBoolean() ? this.nisaGrowthTarget : other.nisaGrowthTarget);
+        child.setNisaTsumitateTarget(random.nextBoolean() ? this.nisaTsumitateTarget : other.nisaTsumitateTarget);
 
         // Inherit mortgage genes
         Map<Long, java.util.List<MortgageRepaymentGene>> childMortgages = new HashMap<>();
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngine.java
index fa0c6428..91493974 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GeneticAlgorithmEngine.java
@@ -112,7 +112,7 @@ public class GeneticAlgorithmEngine implements OptimizerEngine {
             ScoredChromosome currentBest = scoredPopulation.get(0);
             if (currentBest.score() > bestOverallScore) {
                 bestOverallScore = currentBest.score();
-                bestOverall = cloneChromosome(currentBest.chromosome());
+                bestOverall = new Chromosome(currentBest.chromosome());
             }
 
             log.debug("Generation {} best score: {}", generation, currentBest.score());
@@ -122,7 +122,7 @@ public class GeneticAlgorithmEngine implements OptimizerEngine {
 
             // Elitism
             for (int i = 0; i < eliteSize; i++) {
-                nextGen.add(cloneChromosome(scoredPopulation.get(i).chromosome()));
+                nextGen.add(new Chromosome(scoredPopulation.get(i).chromosome()));
             }
 
             // Crossover & Mutation
@@ -149,11 +149,11 @@ public class GeneticAlgorithmEngine implements OptimizerEngine {
     private List<Chromosome> initializePopulation(Chromosome seed) {
         List<Chromosome> population = new ArrayList<>();
         // Add the exact seed
-        population.add(cloneChromosome(seed));
+        population.add(new Chromosome(seed));
 
         // Generate mutated variants
         for (int i = 1; i < populationSize; i++) {
-            Chromosome variant = cloneChromosome(seed);
+            Chromosome variant = new Chromosome(seed);
             // High initial mutation to ensure diversity
             variant.mutate(random, currentYear, currentAge, lifeExpectancy);
             variant.mutate(random, currentYear, currentAge, lifeExpectancy);
@@ -171,42 +171,7 @@ public class GeneticAlgorithmEngine implements OptimizerEngine {
                 best = contender;
             }
         }
-        return cloneChromosome(best.chromosome());
-    }
-
-    private Chromosome cloneChromosome(Chromosome source) {
-        // Deep copy by serializing/deserializing or manual copy.
-        // For performance, doing manual copy.
-        Chromosome clone = new Chromosome();
-
-        clone.setRetirementAgeLocked(source.isRetirementAgeLocked());
-        clone.setRetirementAge(source.getRetirementAge());
-
-        clone.setPensionStartAgeLocked(source.isPensionStartAgeLocked());
-        clone.setPensionStartAge(source.getPensionStartAge());
-
-        clone.setIdecoStartAgeLocked(source.isIdecoStartAgeLocked());
-        clone.setIdecoStartAge(source.getIdecoStartAge());
-
-        clone.setNisaMonthlyTargetLocked(source.isNisaMonthlyTargetLocked());
-        clone.setNisaMonthlyTarget(source.getNisaMonthlyTarget());
-
-        clone.setMortgageRepaymentsLocked(source.isMortgageRepaymentsLocked());
-
-        java.util.Map<Long, java.util.List<Chromosome.MortgageRepaymentGene>> childMortgages = new java.util.HashMap<>();
-        if (source.getMortgageRepayments() != null) {
-            for (java.util.Map.Entry<Long, java.util.List<Chromosome.MortgageRepaymentGene>> entry : source
-                    .getMortgageRepayments().entrySet()) {
-                java.util.List<Chromosome.MortgageRepaymentGene> genes = new java.util.ArrayList<>();
-                for (Chromosome.MortgageRepaymentGene g : entry.getValue()) {
-                    genes.add(new Chromosome.MortgageRepaymentGene(g.getYear(), g.getAmount()));
-                }
-                childMortgages.put(entry.getKey(), genes);
-            }
-        }
-        clone.setMortgageRepayments(childMortgages);
-
-        return clone;
+        return new Chromosome(best.chromosome());
     }
 
     private record ScoredChromosome(Chromosome chromosome, double score) {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java
index 718fd21f..da806769 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java
@@ -23,13 +23,7 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core;
 
-import java.util.ArrayList;
-import java.util.HashMap;
-import java.util.List;
-import java.util.Map;
-
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationData;
-import ch.lin.finance.hub.backend.api.app.service.simulation.optimizer.core.Chromosome.MortgageRepaymentGene;
 import lombok.extern.slf4j.Slf4j;
 
 @Slf4j
@@ -57,11 +51,11 @@ public class GridSearchEngine implements OptimizerEngine {
         log.info("Starting Grid Search Engine optimization...");
 
         // 使用包裝陣列傳遞可變的最佳解狀態
-        Chromosome[] bestOverall = { cloneChromosome(seed) };
+        Chromosome[] bestOverall = { new Chromosome(seed) };
         double[] bestOverallScore = { -Double.MAX_VALUE };
         int[] evaluatedCount = { 0 };
 
-        generateCombinations(seed, cloneChromosome(seed), 0, bestOverall, bestOverallScore, evaluatedCount);
+        generateCombinations(seed, new Chromosome(seed), 0, bestOverall, bestOverallScore, evaluatedCount);
 
         log.info("Finished Grid Search. Evaluated {} combinations. Best overall score: {}", evaluatedCount[0],
                 bestOverallScore[0]);
@@ -73,10 +67,14 @@ public class GridSearchEngine implements OptimizerEngine {
         switch (step) {
             case 0 -> {
                 // NISA
-                if (!seed.isNisaMonthlyTargetLocked()) {
-                    for (long val = OptimizationConstants.MIN_NISA_MONTHLY_TARGET; val <= OptimizationConstants.MAX_NISA_MONTHLY_TARGET; val += OptimizationConstants.NISA_MONTHLY_TARGET_STEP) {
-                        current.setNisaMonthlyTarget(val);
-                        generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
+                if (!seed.isNisaGrowthTargetLocked() || !seed.isNisaTsumitateTargetLocked()) {
+                    for (long growth = 0; growth <= OptimizationConstants.MAX_NISA_GROWTH_TARGET; growth += OptimizationConstants.NISA_MONTHLY_TARGET_STEP) {
+                        for (long tsumitate = 0; tsumitate <= OptimizationConstants.MAX_NISA_TSUMITATE_TARGET; tsumitate += OptimizationConstants.NISA_MONTHLY_TARGET_STEP) {
+                            current.setNisaGrowthTarget(growth);
+                            current.setNisaTsumitateTarget(tsumitate);
+                            generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore,
+                                    evaluatedCount);
+                        }
                     }
                 } else {
                     generateCombinations(seed, current, step + 1, bestOverall, bestOverallScore, evaluatedCount);
@@ -138,42 +136,9 @@ public class GridSearchEngine implements OptimizerEngine {
                 double score = evaluator.evaluate(current);
                 if (score > bestOverallScore[0]) {
                     bestOverallScore[0] = score;
-                    bestOverall[0] = cloneChromosome(current);
-                }
-            }
-        }
-    }
-
-    private Chromosome cloneChromosome(Chromosome source) {
-        Chromosome clone = new Chromosome();
-
-        clone.setRetirementAgeLocked(source.isRetirementAgeLocked());
-        clone.setRetirementAge(source.getRetirementAge());
-
-        clone.setPensionStartAgeLocked(source.isPensionStartAgeLocked());
-        clone.setPensionStartAge(source.getPensionStartAge());
-
-        clone.setIdecoStartAgeLocked(source.isIdecoStartAgeLocked());
-        clone.setIdecoStartAge(source.getIdecoStartAge());
-
-        clone.setNisaMonthlyTargetLocked(source.isNisaMonthlyTargetLocked());
-        clone.setNisaMonthlyTarget(source.getNisaMonthlyTarget());
-
-        clone.setMortgageRepaymentsLocked(source.isMortgageRepaymentsLocked());
-        clone.setGridSingleMortgageRepaymentYear(source.getGridSingleMortgageRepaymentYear());
-
-        Map<Long, List<MortgageRepaymentGene>> childMortgages = new HashMap<>();
-        if (source.getMortgageRepayments() != null) {
-            for (Map.Entry<Long, List<MortgageRepaymentGene>> entry : source.getMortgageRepayments().entrySet()) {
-                List<MortgageRepaymentGene> genes = new ArrayList<>();
-                for (MortgageRepaymentGene g : entry.getValue()) {
-                    genes.add(new MortgageRepaymentGene(g.getYear(), g.getAmount()));
+                    bestOverall[0] = new Chromosome(current);
                 }
-                childMortgages.put(entry.getKey(), genes);
             }
         }
-        clone.setMortgageRepayments(childMortgages);
-
-        return clone;
     }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java
index 72c486ac..8ed38b24 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationConstants.java
@@ -41,10 +41,11 @@ public final class OptimizationConstants {
 
     // NISA 每月目標額度範圍
     public static final long MIN_NISA_MONTHLY_TARGET = 0L;
-    public static final long MAX_NISA_MONTHLY_TARGET = 300_000L;
+    public static final long MAX_NISA_GROWTH_TARGET = 200_000L;
+    public static final long MAX_NISA_TSUMITATE_TARGET = 100_000L;
     public static final long NISA_MONTHLY_TARGET_STEP = 10_000L;
 
     // Grid Search 搜尋空間安全閥值
-    public static final long MAX_GRID_SEARCH_SPACE = 1000L;
+    public static final long MAX_GRID_SEARCH_SPACE = 2000L;
     public static final int MAX_GRID_ENGINE_COMBINATIONS = 5000;
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java
index e7228e7f..6661e00e 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java
@@ -70,7 +70,8 @@ public class ChromosomeMapper {
         }
 
         return new SimulationOverrides(
-                chromosome.getNisaMonthlyTarget(),
+                chromosome.getNisaGrowthTarget(),
+                chromosome.getNisaTsumitateTarget(),
                 chromosome.getRetirementAge(),
                 chromosome.getPensionStartAge(),
                 chromosome.getIdecoStartAge(),
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
index bff49d52..76ea81bf 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
@@ -554,8 +554,8 @@ class AssetSimulationEngineImplTest {
         mortgageOverrides.put(1L, Collections.singletonList(
                 new SimulationOverrides.MortgageEarlyRepaymentOverride(retirementCalendarYear, Long.MAX_VALUE)));
 
-        SimulationOverrides overrides = new SimulationOverrides(null, null, null, null, mortgageOverrides, null, null,
-                null);
+        SimulationOverrides overrides = new SimulationOverrides(null, null, null, null, null, mortgageOverrides, null,
+                null, null);
         // When
         List<AssetSimulationYearlyResult> simulation = engine.runSimulationWithOverrides(61, overrides);
 
@@ -828,7 +828,7 @@ class AssetSimulationEngineImplTest {
 
         // But we DO have Profile settings
         NisaUserSettings nisaSettings = new NisaUserSettings();
-        nisaSettings.setMonthlyNisaContribution(30_000L);
+        nisaSettings.setMonthlyGrowthContribution(30_000L);
         nisaSettings.setNisaExpectedReturnRate(0.04);
         when(nisaUserSettingsRepository.findFirstBy()).thenReturn(Optional.of(nisaSettings));
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java
index 3d772bd6..d0927373 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java
@@ -89,8 +89,8 @@ public class GlobalOptimizerServiceTest {
     @Test
     void testOptimize_RunsGridSearch_WhenLowDimensional() {
         // Given AUTO strategy and only 1 unlocked variable (NISA target, 31 options)
-        SimulationOverrides baseOverrides = new SimulationOverrides(
-                10000L, null, null, null, null, null, null, null);
+        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, null, null, null, null, null, null,
+                null);
         OptimizationConfig config = new OptimizationConfig(false, false, false, true, false);
         GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                 OptimizationStrategy.AUTO);
@@ -102,15 +102,14 @@ public class GlobalOptimizerServiceTest {
 
         // Then
         assertNotNull(result);
-        org.mockito.Mockito.verify(fitnessEvaluator, org.mockito.Mockito.times(31)).evaluate(any());
+        org.mockito.Mockito.verify(fitnessEvaluator, org.mockito.Mockito.times(231)).evaluate(any());
     }
 
     @Test
     void testOptimize_RunsGA_WhenHighDimensional() {
         // Given AUTO strategy and multiple unlocked variables (31 * 31 * 16 * 16 >
         // 1000)
-        SimulationOverrides baseOverrides = new SimulationOverrides(
-                10000L, 65, 65, 60, null, null, null, null);
+        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, 65, 65, 60, null, null, null, null);
         OptimizationConfig config = new OptimizationConfig(true, true, true, true, false);
         GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                 OptimizationStrategy.AUTO);
@@ -128,8 +127,7 @@ public class GlobalOptimizerServiceTest {
     @Test
     void testOptimize_ThrowsException_WhenForceGridWithCombinationsExceedingCap() {
         // Given FORCE_GRID strategy and 4 unlocked variables (> 1000 combinations)
-        SimulationOverrides baseOverrides = new SimulationOverrides(
-                10000L, 65, 65, 60, null, null, null, null);
+        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, 65, 65, 60, null, null, null, null);
         OptimizationConfig config = new OptimizationConfig(true, true, true, true, false);
         GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                 OptimizationStrategy.FORCE_GRID);
@@ -144,8 +142,8 @@ public class GlobalOptimizerServiceTest {
     void testOptimize_ThrowsException_WhenForceGridWithMortgageCombinationsExceedCap() {
         // Given FORCE_GRID strategy and mortgage unlocked, and mortgage options are
         // huge (e.g. 50 years)
-        SimulationOverrides baseOverrides = new SimulationOverrides(
-                10000L, null, null, null, null, null, null, null);
+        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, null, null, null, null, null, null,
+                null);
         OptimizationConfig config = new OptimizationConfig(false, false, false, false, true);
         GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                 OptimizationStrategy.FORCE_GRID);
@@ -176,8 +174,8 @@ public class GlobalOptimizerServiceTest {
     @Test
     void testOptimize_Succeeds_WhenForceGridWithinCap() {
         // Given FORCE_GRID strategy and only NISA unlocked (31 combinations <= 1000)
-        SimulationOverrides baseOverrides = new SimulationOverrides(
-                10000L, null, null, null, null, null, null, null);
+        SimulationOverrides baseOverrides = new SimulationOverrides(10000L, 10000L, null, null, null, null, null, null,
+                null);
         OptimizationConfig config = new OptimizationConfig(false, false, false, true, false);
         GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                 OptimizationStrategy.FORCE_GRID);
@@ -194,7 +192,8 @@ public class GlobalOptimizerServiceTest {
     @Test
     void testOptimize_ReturnsNullDetails_WhenAllConfigOptimized() {
         // Given all configs set to true
-        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null);
+        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
+                null);
         OptimizationConfig config = new OptimizationConfig(true, true, true, true, false);
         GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                 OptimizationStrategy.AUTO);
@@ -219,13 +218,15 @@ public class GlobalOptimizerServiceTest {
         assertNull(result.appliedProfileBaselineDetails().targetRetirementAge());
         assertNull(result.appliedProfileBaselineDetails().pensionStartAge());
         assertNull(result.appliedProfileBaselineDetails().idecoStartAge());
-        assertNull(result.appliedProfileBaselineDetails().nisaMonthlyContribution());
+        assertNull(result.appliedProfileBaselineDetails().nisaGrowthMonthlyContribution());
+        assertNull(result.appliedProfileBaselineDetails().nisaTsumitateMonthlyContribution());
     }
 
     @Test
     void testOptimize_ReturnsAppliedValues_WhenConfigNotOptimizedAndProfileHasValue() {
         // Given retirement not optimized, and profile has value
-        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null);
+        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
+                null);
         OptimizationConfig config = new OptimizationConfig(false, true, true, true, false);
         GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                 OptimizationStrategy.AUTO);
@@ -254,7 +255,8 @@ public class GlobalOptimizerServiceTest {
     @Test
     void testOptimize_ReturnsNullDetails_WhenConfigNotOptimizedButProfileIsEmpty() {
         // Given retirement not optimized, but profile has no value (null)
-        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null);
+        SimulationOverrides baseOverrides = new SimulationOverrides(null, null, null, null, null, null, null, null,
+                null);
         OptimizationConfig config = new OptimizationConfig(false, true, true, true, false);
         GlobalOptimizationCommand command = new GlobalOptimizationCommand(baseOverrides, config,
                 OptimizationStrategy.AUTO);
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngineTest.java
index 1fca6ba9..5d13632a 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngineTest.java
@@ -50,9 +50,10 @@ class GridSearchEngineTest {
 
     @Test
     void testOptimize_EvaluatesCombinations_AndReturnsBest() {
-        // Seed with only NISA unlocked (31 combinations)
+        // Seed with only NISA unlocked (231 combinations)
         Chromosome seed = new Chromosome();
-        seed.setNisaMonthlyTargetLocked(false);
+        seed.setNisaGrowthTargetLocked(false);
+        seed.setNisaTsumitateTargetLocked(false);
         seed.setRetirementAgeLocked(true);
         seed.setRetirementAge(65);
         seed.setPensionStartAgeLocked(true);
@@ -63,8 +64,9 @@ class GridSearchEngineTest {
 
         when(fitnessEvaluator.evaluate(any())).thenAnswer(invocation -> {
             Chromosome c = invocation.getArgument(0);
-            // Higher score for 100,000 NISA
-            if (c.getNisaMonthlyTarget() == 100_000L) {
+            // Higher score for Growth=200,000 and Tsumitate=100,000
+            if (c.getNisaGrowthTarget() != null && c.getNisaTsumitateTarget() != null &&
+                    c.getNisaGrowthTarget() == 200_000L && c.getNisaTsumitateTarget() == 100_000L) {
                 return 1000.0;
             }
             return 10.0;
@@ -73,14 +75,16 @@ class GridSearchEngineTest {
         Chromosome best = gridSearchEngine.optimize(seed);
 
         assertNotNull(best);
-        assertEquals(100_000L, best.getNisaMonthlyTarget());
+        assertEquals(200_000L, best.getNisaGrowthTarget());
+        assertEquals(100_000L, best.getNisaTsumitateTarget());
     }
 
     @Test
     void testOptimize_ThrowsException_WhenCombinationsExceedHardCap() {
-        // Seed with all variables unlocked (31 * 31 * 16 * 16 = 246,016 > 5000)
+        // Seed with all variables unlocked (231 * 16 * 16 = 59,136 > 5000)
         Chromosome seed = new Chromosome();
-        seed.setNisaMonthlyTargetLocked(false);
+        seed.setNisaGrowthTargetLocked(false);
+        seed.setNisaTsumitateTargetLocked(false);
         seed.setRetirementAgeLocked(false);
         seed.setPensionStartAgeLocked(false);
         seed.setIdecoStartAgeLocked(false);
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaUserSettings.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaUserSettings.java
index 24ca0823..ef4f6a70 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaUserSettings.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaUserSettings.java
@@ -27,6 +27,9 @@ import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
 import jakarta.persistence.Column;
 import jakarta.persistence.Entity;
 import jakarta.persistence.Table;
+import jakarta.validation.constraints.Max;
+import jakarta.validation.constraints.Min;
+import jakarta.validation.constraints.NotNull;
 import lombok.Getter;
 import lombok.NoArgsConstructor;
 import lombok.Setter;
@@ -38,8 +41,17 @@ import lombok.Setter;
 @NoArgsConstructor
 public class NisaUserSettings extends MultiUserBaseEntity {
 
-    @Column(name = "monthly_nisa_contribution")
-    private Long monthlyNisaContribution = 0L;
+    @Column(name = "monthly_growth_contribution", nullable = false, columnDefinition = "bigint default 0")
+    @NotNull
+    @Min(0)
+    @Max(200000)
+    private Long monthlyGrowthContribution = 0L;
+
+    @Column(name = "monthly_tsumitate_contribution", nullable = false, columnDefinition = "bigint default 0")
+    @NotNull
+    @Min(0)
+    @Max(100000)
+    private Long monthlyTsumitateContribution = 0L;
 
     @Column(name = "nisa_expected_return_rate")
     private Double nisaExpectedReturnRate = 0.05;
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsRequest.java
index c3785971..2e113071 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsRequest.java
@@ -25,6 +25,9 @@ package ch.lin.finance.hub.backend.api.controller.accounting.nisa.dto;
 
 import jakarta.validation.constraints.DecimalMax;
 import jakarta.validation.constraints.DecimalMin;
+import jakarta.validation.constraints.Max;
+import jakarta.validation.constraints.Min;
+import jakarta.validation.constraints.NotNull;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -35,7 +38,15 @@ import lombok.NoArgsConstructor;
 @NoArgsConstructor
 @AllArgsConstructor
 public class NisaUserSettingsRequest {
-    private Long monthlyNisaContribution;
+    @NotNull
+    @Min(0)
+    @Max(200000)
+    private Long monthlyGrowthContribution;
+
+    @NotNull
+    @Min(0)
+    @Max(100000)
+    private Long monthlyTsumitateContribution;
 
     @DecimalMin("-1.0")
     @DecimalMax("1.0")
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsResponse.java
index 245ed42b..c55adb9b 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/dto/NisaUserSettingsResponse.java
@@ -33,6 +33,7 @@ import lombok.NoArgsConstructor;
 @NoArgsConstructor
 @AllArgsConstructor
 public class NisaUserSettingsResponse {
-    private Long monthlyNisaContribution;
+    private Long monthlyGrowthContribution;
+    private Long monthlyTsumitateContribution;
     private Double nisaExpectedReturnRate;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java
index 87fc2504..07886d55 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java
@@ -42,7 +42,6 @@ public record UserProfileRequest(
         @Min(60) @Max(75) Integer pensionStartAge,
         @Valid List<DependentRequest> familyMembers,
         List<InsurancePolicyRequest> insurancePolicies,
-        Boolean onboardingCompleted,
         Integer lifeExpectancy,
         @Min(60) @Max(75) Integer taxAdvantagedWithdrawalAge,
         Boolean applyProfileToOptimizerBaseline) {
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java
index 9c1a63c5..a91e8b25 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java
@@ -39,7 +39,6 @@ public record UserProfileResponse(
         Integer pensionStartAge,
         List<DependentResponse> familyMembers,
         List<InsurancePolicyResponse> insurancePolicies,
-        Boolean onboardingCompleted,
         Integer lifeExpectancy,
         Integer taxAdvantagedWithdrawalAge,
         Boolean applyProfileToOptimizerBaseline) {
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/AssetSimulationController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/AssetSimulationController.java
index 9876ce78..09bb7b44 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/AssetSimulationController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/AssetSimulationController.java
@@ -63,7 +63,7 @@ public class AssetSimulationController {
         if (request != null) {
             overrides = assetSimulationMapper.toOverrides(request);
         } else {
-            overrides = new SimulationOverrides(null, null, null, null, null, null, null, null);
+            overrides = new SimulationOverrides(null, null, null, null, null, null, null, null, null);
         }
 
         List<AssetSimulationYearlyResponse> simulation = assetSimulationEngine.runSimulationWithOverrides(
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java
index bac20c54..d24d4bcc 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationRequest.java
@@ -34,7 +34,8 @@ import jakarta.validation.constraints.Positive;
 import jakarta.validation.constraints.PositiveOrZero;
 
 public record AssetSimulationRequest(
-        Long testNisaMonthlyContribution,
+        Long testNisaGrowthMonthlyContribution,
+        Long testNisaTsumitateMonthlyContribution,
         Integer targetRetirementAgeOverride,
         Integer pensionStartAgeOverride,
         Integer idecoStartAgeOverride,
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java
index 0402d513..c0c2af48 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java
@@ -62,7 +62,8 @@ public interface GlobalOptimizerMapper {
                     .orElse(null);
         }
         return new GlobalOptimizerResponse(
-                overrides.testNisaMonthlyContribution(),
+                overrides.testNisaGrowthMonthlyContribution(),
+                overrides.testNisaTsumitateMonthlyContribution(),
                 displayYear,
                 overrides.targetRetirementAgeOverride(),
                 overrides.pensionStartAgeOverride(),
@@ -84,7 +85,8 @@ public interface GlobalOptimizerMapper {
                 details.targetRetirementAge(),
                 details.pensionStartAge(),
                 details.idecoStartAge(),
-                details.nisaMonthlyContribution());
+                details.nisaGrowthMonthlyContribution(),
+                details.nisaTsumitateMonthlyContribution());
     }
 
     GlobalOptimizerResponse.MortgageEarlyRepaymentResponse toMortgageEarlyRepaymentResponse(
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java
index 5c62332a..c030dcac 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java
@@ -27,7 +27,8 @@ import java.util.List;
 import java.util.Map;
 
 public record GlobalOptimizerResponse(
-        Long testNisaMonthlyContribution,
+        Long testNisaGrowthMonthlyContribution,
+        Long testNisaTsumitateMonthlyContribution,
         Integer mortgageEarlyRepaymentYear,
         Integer targetRetirementAgeOverride,
         Integer pensionStartAgeOverride,
@@ -41,7 +42,8 @@ public record GlobalOptimizerResponse(
             Integer targetRetirementAge,
             Integer pensionStartAge,
             Integer idecoStartAge,
-            Long nisaMonthlyContribution) {
+            Long nisaGrowthMonthlyContribution,
+            Long nisaTsumitateMonthlyContribution) {
     }
 
     public record FundOverrideResponse(
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                                                                                                                           10010  10.90G   4.03    21:08:27  