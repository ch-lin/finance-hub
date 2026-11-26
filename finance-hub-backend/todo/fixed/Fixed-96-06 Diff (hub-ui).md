 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper     main    INSERT  cd hub-ui                                                                                                                                                                                           10010  11.00G   3.64    21:10:21  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd hub-ui                                                                                                                                                 10011  11.10G   3.63    21:10:26  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                                                                                10011  11.12G   3.58    21:10:32  
diff --git a/hub-ui/src/app/finance/components/profile-contributions-section.tsx b/hub-ui/src/app/finance/components/profile-contributions-section.tsx
index 74dcfb1..abab530 100644
--- a/hub-ui/src/app/finance/components/profile-contributions-section.tsx
+++ b/hub-ui/src/app/finance/components/profile-contributions-section.tsx
@@ -7,6 +7,7 @@ import { Switch } from "@/components/ui/switch";
 import { toast } from "sonner";
 import { UserProfileFormData } from "./user-profile-form";
 import { Info } from "lucide-react";
+import { ThousandSeparatorInput } from "@/components/ui/thousand-separator-input";
 
 interface Props {
   formData: UserProfileFormData;
@@ -44,18 +45,37 @@ export function ProfileContributionsSection({ formData, onChange }: Props) {
 
         <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
           <div className="space-y-2">
-            <Label htmlFor="monthlyNisaContribution">Expected Monthly NISA (¥)</Label>
-            <Input
-              id="monthlyNisaContribution"
-              name="monthlyNisaContribution"
-              type="text"
-              placeholder="e.g. 100,000"
-              value={formData.monthlyNisaContribution}
-              onChange={(e) => {
-                const val = e.target.value.replace(/,/g, '');
-                if (!isNaN(Number(val))) {
-                  onChange({ ...formData, monthlyNisaContribution: val ? Number(val).toLocaleString() : '' });
+            <Label htmlFor="monthlyGrowthContribution">NISA 成長投資枠 (月額)</Label>
+            <ThousandSeparatorInput
+              id="monthlyGrowthContribution"
+              name="monthlyGrowthContribution"
+              placeholder="Max 200,000"
+              value={formData.monthlyGrowthContribution}
+              onChange={(val) => {
+                let parsed = val || 0;
+                if (parsed > 200000) {
+                  parsed = 200000;
+                  toast.error("NISA 成長投資枠 (月額) cannot exceed 200,000 ¥.");
+                }
+                onChange({ ...formData, monthlyGrowthContribution: parsed ? parsed.toString() : '' });
+              }}
+            />
+          </div>
+
+          <div className="space-y-2">
+            <Label htmlFor="monthlyTsumitateContribution">NISA つみたて投資枠 (月額)</Label>
+            <ThousandSeparatorInput
+              id="monthlyTsumitateContribution"
+              name="monthlyTsumitateContribution"
+              placeholder="Max 100,000"
+              value={formData.monthlyTsumitateContribution}
+              onChange={(val) => {
+                let parsed = val || 0;
+                if (parsed > 100000) {
+                  parsed = 100000;
+                  toast.error("NISA つみたて投資枠 (月額) cannot exceed 100,000 ¥.");
                 }
+                onChange({ ...formData, monthlyTsumitateContribution: parsed ? parsed.toString() : '' });
               }}
             />
           </div>
diff --git a/hub-ui/src/app/finance/components/user-profile-form.tsx b/hub-ui/src/app/finance/components/user-profile-form.tsx
index 1d95b62..ac77d97 100644
--- a/hub-ui/src/app/finance/components/user-profile-form.tsx
+++ b/hub-ui/src/app/finance/components/user-profile-form.tsx
@@ -21,7 +21,8 @@ export interface UserProfileFormData {
   targetRetirementAge: string;
   pensionStartAge: string;
   lifeExpectancy: string;
-  monthlyNisaContribution: string;
+  monthlyGrowthContribution: string;
+  monthlyTsumitateContribution: string;
   nisaExpectedReturnRate: string;
   monthlyIdecoContribution: string;
   idecoExpectedReturnRate: string;
diff --git a/hub-ui/src/app/finance/onboarding/page.tsx b/hub-ui/src/app/finance/onboarding/page.tsx
index 0e49702..b14a923 100644
--- a/hub-ui/src/app/finance/onboarding/page.tsx
+++ b/hub-ui/src/app/finance/onboarding/page.tsx
@@ -24,7 +24,8 @@ export default function OnboardingPage() {
     targetRetirementAge: "65",
     pensionStartAge: "65",
     lifeExpectancy: "",
-    monthlyNisaContribution: "",
+    monthlyGrowthContribution: "",
+    monthlyTsumitateContribution: "",
     nisaExpectedReturnRate: "5.0",
     monthlyIdecoContribution: "",
     idecoExpectedReturnRate: "4.0",
@@ -62,7 +63,8 @@ export default function OnboardingPage() {
         targetRetirementAge: profile.targetRetirementAge != null ? profile.targetRetirementAge.toString() : initialData.targetRetirementAge || "65",
         pensionStartAge: profile.pensionStartAge != null ? profile.pensionStartAge.toString() : initialData.pensionStartAge || "65",
         lifeExpectancy: profile.lifeExpectancy != null ? profile.lifeExpectancy.toString() : initialData.lifeExpectancy || "",
-        monthlyNisaContribution: profile.monthlyNisaContribution != null ? profile.monthlyNisaContribution.toString() : initialData.monthlyNisaContribution || "",
+        monthlyGrowthContribution: profile.monthlyGrowthContribution != null ? profile.monthlyGrowthContribution.toString() : initialData.monthlyGrowthContribution || "",
+        monthlyTsumitateContribution: profile.monthlyTsumitateContribution != null ? profile.monthlyTsumitateContribution.toString() : initialData.monthlyTsumitateContribution || "",
         nisaExpectedReturnRate: profile.nisaExpectedReturnRate != null ? (profile.nisaExpectedReturnRate * 100).toString() : initialData.nisaExpectedReturnRate || "5.0",
         monthlyIdecoContribution: profile.monthlyIdecoContribution != null ? profile.monthlyIdecoContribution.toString() : initialData.monthlyIdecoContribution || "",
         idecoExpectedReturnRate: profile.idecoExpectedReturnRate != null ? (profile.idecoExpectedReturnRate * 100).toString() : initialData.idecoExpectedReturnRate || "4.0",
@@ -103,7 +105,8 @@ export default function OnboardingPage() {
         targetRetirementAge: formData.targetRetirementAge ? parseInt(formData.targetRetirementAge) : 65,
         pensionStartAge: formData.pensionStartAge ? parseInt(formData.pensionStartAge) : 65,
         lifeExpectancy: formData.lifeExpectancy ? parseInt(formData.lifeExpectancy) : null,
-        monthlyNisaContribution: formData.monthlyNisaContribution ? parseInt(formData.monthlyNisaContribution.replace(/,/g, '')) : null,
+        monthlyGrowthContribution: formData.monthlyGrowthContribution ? parseInt(formData.monthlyGrowthContribution.replace(/,/g, '')) : null,
+        monthlyTsumitateContribution: formData.monthlyTsumitateContribution ? parseInt(formData.monthlyTsumitateContribution.replace(/,/g, '')) : null,
         nisaExpectedReturnRate: formData.nisaExpectedReturnRate ? parseFloat(formData.nisaExpectedReturnRate) / 100.0 : 0.05,
         monthlyIdecoContribution: formData.monthlyIdecoContribution ? parseInt(formData.monthlyIdecoContribution.replace(/,/g, '')) : null,
         idecoExpectedReturnRate: formData.idecoExpectedReturnRate ? parseFloat(formData.idecoExpectedReturnRate) / 100.0 : 0.04,
diff --git a/hub-ui/src/app/finance/profile/hooks/use-finance-profile.ts b/hub-ui/src/app/finance/profile/hooks/use-finance-profile.ts
index 5d60bc6..9f21939 100644
--- a/hub-ui/src/app/finance/profile/hooks/use-finance-profile.ts
+++ b/hub-ui/src/app/finance/profile/hooks/use-finance-profile.ts
@@ -46,7 +46,8 @@ export function useFinanceProfile() {
     delete profilePayload.salaryGrowthRate;
     delete profilePayload.useHistoricalBonusForProjection;
     delete profilePayload.expectedAnnualBonus;
-    delete profilePayload.monthlyNisaContribution;
+    delete profilePayload.monthlyGrowthContribution;
+    delete profilePayload.monthlyTsumitateContribution;
     delete profilePayload.nisaExpectedReturnRate;
     delete profilePayload.monthlyIdecoContribution;
     delete profilePayload.idecoExpectedReturnRate;
@@ -66,7 +67,8 @@ export function useFinanceProfile() {
 
     // 3. NISA Settings Payload
     const nisaPayload = {
-      monthlyNisaContribution: profileData.monthlyNisaContribution,
+      monthlyGrowthContribution: profileData.monthlyGrowthContribution,
+      monthlyTsumitateContribution: profileData.monthlyTsumitateContribution,
       nisaExpectedReturnRate: profileData.nisaExpectedReturnRate
     };
 
diff --git a/hub-ui/src/app/finance/profile/me/page.tsx b/hub-ui/src/app/finance/profile/me/page.tsx
index 6d98434..cc621a7 100644
--- a/hub-ui/src/app/finance/profile/me/page.tsx
+++ b/hub-ui/src/app/finance/profile/me/page.tsx
@@ -18,7 +18,8 @@ export default function ProfileConfigPage() {
     targetRetirementAge: "65",
     pensionStartAge: "65",
     lifeExpectancy: "",
-    monthlyNisaContribution: "",
+    monthlyGrowthContribution: "",
+    monthlyTsumitateContribution: "",
     nisaExpectedReturnRate: "5.0",
     monthlyIdecoContribution: "",
     idecoExpectedReturnRate: "4.0",
@@ -46,7 +47,8 @@ export default function ProfileConfigPage() {
         targetRetirementAge: profile.targetRetirementAge != null ? profile.targetRetirementAge.toString() : "65",
         pensionStartAge: profile.pensionStartAge != null ? profile.pensionStartAge.toString() : "65",
         lifeExpectancy: profile.lifeExpectancy != null ? profile.lifeExpectancy.toString() : "",
-        monthlyNisaContribution: profile.monthlyNisaContribution != null ? profile.monthlyNisaContribution.toString() : "",
+        monthlyGrowthContribution: profile.monthlyGrowthContribution != null ? profile.monthlyGrowthContribution.toString() : "",
+        monthlyTsumitateContribution: profile.monthlyTsumitateContribution != null ? profile.monthlyTsumitateContribution.toString() : "",
         nisaExpectedReturnRate: profile.nisaExpectedReturnRate != null ? (profile.nisaExpectedReturnRate * 100).toString() : "5.0",
         monthlyIdecoContribution: profile.monthlyIdecoContribution != null ? profile.monthlyIdecoContribution.toString() : "",
         idecoExpectedReturnRate: profile.idecoExpectedReturnRate != null ? (profile.idecoExpectedReturnRate * 100).toString() : "4.0",
@@ -87,7 +89,8 @@ export default function ProfileConfigPage() {
         targetRetirementAge: formData.targetRetirementAge ? parseInt(formData.targetRetirementAge) : 65,
         pensionStartAge: formData.pensionStartAge ? parseInt(formData.pensionStartAge) : 65,
         lifeExpectancy: formData.lifeExpectancy ? parseInt(formData.lifeExpectancy) : null,
-        monthlyNisaContribution: formData.monthlyNisaContribution ? parseInt(formData.monthlyNisaContribution.replace(/,/g, '')) : null,
+        monthlyGrowthContribution: formData.monthlyGrowthContribution ? parseInt(formData.monthlyGrowthContribution.replace(/,/g, '')) : null,
+        monthlyTsumitateContribution: formData.monthlyTsumitateContribution ? parseInt(formData.monthlyTsumitateContribution.replace(/,/g, '')) : null,
         nisaExpectedReturnRate: formData.nisaExpectedReturnRate ? parseFloat(formData.nisaExpectedReturnRate) / 100.0 : 0.05,
         monthlyIdecoContribution: formData.monthlyIdecoContribution ? parseInt(formData.monthlyIdecoContribution.replace(/,/g, '')) : null,
         idecoExpectedReturnRate: formData.idecoExpectedReturnRate ? parseFloat(formData.idecoExpectedReturnRate) / 100.0 : 0.04,
diff --git a/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx b/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
index b7049a3..5dda8b5 100644
--- a/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
+++ b/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
@@ -77,7 +77,7 @@ function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelP
 
   const allOptimized = config.optimizeRetirementAge && config.optimizePensionAge && config.optimizeIdecoAge && config.optimizeNisa && config.optimizeMortgage;
   const appliedDetails = result.appliedProfileBaselineDetails;
-  const anyProfileApplied = appliedDetails && (appliedDetails.targetRetirementAge || appliedDetails.pensionStartAge || appliedDetails.idecoStartAge || appliedDetails.nisaMonthlyContribution != null);
+  const anyProfileApplied = appliedDetails && (appliedDetails.targetRetirementAge || appliedDetails.pensionStartAge || appliedDetails.idecoStartAge || appliedDetails.nisaGrowthMonthlyContribution != null || appliedDetails.nisaTsumitateMonthlyContribution != null);
 
   return (
     <div className="mt-8 relative overflow-hidden rounded-xl border border-primary/20 bg-gradient-to-b from-primary/10 to-background p-6 shadow-2xl">
@@ -101,7 +101,8 @@ function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelP
                     appliedDetails.targetRetirementAge ? `Retirement (${appliedDetails.targetRetirementAge})` : null,
                     appliedDetails.pensionStartAge ? `Pension (${appliedDetails.pensionStartAge})` : null,
                     appliedDetails.idecoStartAge ? `iDeCo (${appliedDetails.idecoStartAge})` : null,
-                    appliedDetails.nisaMonthlyContribution != null ? `NISA (¥${appliedDetails.nisaMonthlyContribution.toLocaleString()}/mo)` : null
+                    appliedDetails.nisaGrowthMonthlyContribution != null ? `NISA Growth (¥${appliedDetails.nisaGrowthMonthlyContribution.toLocaleString()}/mo)` : null,
+                    appliedDetails.nisaTsumitateMonthlyContribution != null ? `NISA Tsumitate (¥${appliedDetails.nisaTsumitateMonthlyContribution.toLocaleString()}/mo)` : null
                   ].filter(Boolean).join(', ')
                 }.`
               : "Statutory baseline defaults (e.g. 60 for iDeCo, 65 for Pension) were applied to dimensions you didn't optimize (either because Profile overriding is disabled or the specific Profile fields are empty)."}
@@ -109,7 +110,7 @@ function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelP
         </div>
       )}
 
-      <div className={`relative z-10 grid grid-cols-1 ${result.mortgageEarlyRepaymentYear != null ? 'md:grid-cols-3' : 'md:grid-cols-2'} gap-4 mb-6`}>
+      <div className={`relative z-10 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-${result.mortgageEarlyRepaymentYear != null ? '4' : '3'} gap-4 mb-6`}>
         <Card className="p-5 border-primary/20 bg-background/50 backdrop-blur">
           <p className="text-sm text-muted-foreground mb-2">Recommended Retirement Age</p>
           <div className="text-4xl font-bold text-primary mb-1">
@@ -119,11 +120,19 @@ function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelP
         </Card>
         
         <Card className="p-5 border-primary/20 bg-background/50 backdrop-blur">
-          <p className="text-sm text-muted-foreground mb-2">Optimal NISA Contribution</p>
-          <div className="text-4xl font-bold text-primary mb-1">
-            {result.testNisaMonthlyContribution ? `¥${result.testNisaMonthlyContribution.toLocaleString()}/mo` : 'N/A'}
+          <p className="text-sm text-muted-foreground mb-2">Optimal NISA Growth</p>
+          <div className="text-3xl font-bold text-primary mb-1">
+            {result.testNisaGrowthMonthlyContribution != null ? `¥${result.testNisaGrowthMonthlyContribution.toLocaleString()}/mo` : 'N/A'}
+          </div>
+          <p className="text-xs text-muted-foreground">Growth Monthly</p>
+        </Card>
+        
+        <Card className="p-5 border-primary/20 bg-background/50 backdrop-blur">
+          <p className="text-sm text-muted-foreground mb-2">Optimal NISA Tsumitate</p>
+          <div className="text-3xl font-bold text-primary mb-1">
+            {result.testNisaTsumitateMonthlyContribution != null ? `¥${result.testNisaTsumitateMonthlyContribution.toLocaleString()}/mo` : 'N/A'}
           </div>
-          <p className="text-xs text-muted-foreground">Monthly Contribution</p>
+          <p className="text-xs text-muted-foreground">Tsumitate Monthly</p>
         </Card>
 
         {result.mortgageEarlyRepaymentYear != null && (
diff --git a/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts b/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts
index 941c2b5..2f91132 100644
--- a/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts
+++ b/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts
@@ -20,7 +20,8 @@ export interface GlobalOptimizerResponse {
   targetRetirementAgeOverride: number | null;
   pensionStartAgeOverride: number | null;
   idecoStartAgeOverride: number | null;
-  testNisaMonthlyContribution: number | null;
+  testNisaGrowthMonthlyContribution: number | null;
+  testNisaTsumitateMonthlyContribution: number | null;
   mortgageEarlyRepaymentYear?: number;
   mortgageEarlyRepayments: Record<number, { year: number; amount: number }[]> | null;
   fundOverrides: any[] | null;
@@ -29,7 +30,8 @@ export interface GlobalOptimizerResponse {
     targetRetirementAge?: number;
     pensionStartAge?: number;
     idecoStartAge?: number;
-    nisaMonthlyContribution?: number;
+    nisaGrowthMonthlyContribution?: number;
+    nisaTsumitateMonthlyContribution?: number;
   } | null;
 }
 
diff --git a/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts b/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts
index 5afa533..b461618 100644
--- a/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts
+++ b/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts
@@ -13,7 +13,8 @@ export interface SimulationOverrides {
   targetRetirementAgeOverride?: number | null;
   pensionStartAgeOverride?: number | null;
   idecoStartAgeOverride?: number | null;
-  testNisaMonthlyContribution?: number | null;
+  testNisaGrowthMonthlyContribution?: number | null;
+  testNisaTsumitateMonthlyContribution?: number | null;
   fundOverrides?: FundOverride[] | null;
   mortgageRateScenarios?: Record<number, { startYear: number; rate: number }[]> | null;
   mortgageEarlyRepayments?: Record<number, { year: number; amount: number }[]> | null;
diff --git a/hub-ui/src/app/finance/simulator/simulation/page.tsx b/hub-ui/src/app/finance/simulator/simulation/page.tsx
index 5817cd1..cc26c7c 100644
--- a/hub-ui/src/app/finance/simulator/simulation/page.tsx
+++ b/hub-ui/src/app/finance/simulator/simulation/page.tsx
@@ -119,7 +119,8 @@ export default function AssetSimulatorPage() {
                 if (result.targetRetirementAgeOverride !== null) next.targetRetirementAgeOverride = result.targetRetirementAgeOverride;
                 if (result.pensionStartAgeOverride !== null) next.pensionStartAgeOverride = result.pensionStartAgeOverride;
                 if (result.idecoStartAgeOverride !== null) next.idecoStartAgeOverride = result.idecoStartAgeOverride;
-                if (result.testNisaMonthlyContribution !== null) next.testNisaMonthlyContribution = result.testNisaMonthlyContribution;
+                if (result.testNisaGrowthMonthlyContribution !== null) next.testNisaGrowthMonthlyContribution = result.testNisaGrowthMonthlyContribution;
+                if (result.testNisaTsumitateMonthlyContribution !== null) next.testNisaTsumitateMonthlyContribution = result.testNisaTsumitateMonthlyContribution;
                 
                 if (result.mortgageEarlyRepayments) {
                   next.mortgageEarlyRepayments = result.mortgageEarlyRepayments;
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                                                                                                                                     10012  11.12G   3.58    21:10:34  