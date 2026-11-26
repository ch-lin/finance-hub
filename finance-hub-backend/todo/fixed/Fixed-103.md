# Fixed 103：配偶扣除完全沒有判斷配偶是否70歲以上，同一個資料欄位在別的地方已經有現成的判斷邏輯

## 嚴重程度
Medium-High（會在配偶年齡跨過70歲那一刻起，低估配偶扣除額度，讓稅金被高估——不是稅制數字滾動追不上修法的問題，是程式碼裡已經寫好的判斷邏輯沒有被套用到這裡）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngine.java:48-93`（整個檔案，`calculateIncomeTaxDeduction`／`calculateInhabitantTaxDeduction`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngine.java:62-75`（對照組：這裡已經有現成、正確運作的年齡判斷邏輯）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java:63`（`birthDate`欄位，兩個引擎都是靠這個欄位取得年齡資訊）

## 現狀
`SpouseDeductionEngine`計算配偶扣除額時，方法簽章只接收`(long userGrossIncome, Dependent spouse)`，函式內部只讀取`spouse.getEstimatedAnnualIncome()`，從頭到尾沒有呼叫`spouse.getBirthDate()`，配偶扣除額永遠只有兩種結果：固定的`SPOUSE_DEDUCTION_INCOME_TAX`（380,000）／`SPOUSE_DEDUCTION_INHABITANT_TAX`（330,000），或這兩個數字的一半，完全不會因為配偶年齡跨過70歲而有任何變化。

## 原因
`Dependent`這個資料模型本身就有`birthDate`欄位，而且同一個檔案家族裡的`DependentDeductionEngine`，已經用這個欄位實作了完全正確、運作中的邏輯：算出年齡後，只要`age >= 70`就切換成適用「老人控除對象」的較高扣除額（`ELDERLY_DEPENDENT_INCOME_TAX` 480,000／`ELDERLY_DEPENDENT_INHABITANT_TAX` 380,000，對照一般扶養親屬的380,000／330,000）。日本稅法對配偶其實有完全對等的規則——配偶只要年滿70歲（以12月31日為基準），一樣可以從一般的「配偶者控除」（380,000／330,000）升級到「老人控除對象配偶者」（480,000／380,000）。`SpouseDeductionEngine`並不是缺少資料，是明明可以直接照抄`DependentDeductionEngine`裡已經寫好、驗證過的同一套判斷方式，卻完全沒有套用。

## 具體錯誤情境
使用者模擬到自己65歲、配偶68歲那一年開始退休，這個工具的核心用途就是把模擬一路推算到退休、甚至更久之後。當配偶模擬到70歲那一年開始，正確的配偶扣除額應該從380,000（所得稅）／330,000（住民稅）提升為480,000／380,000，但系統會持續套用較低的舊金額，往後每一年都低估配偶扣除額100,000（所得稅）／50,000（住民稅），讓稅金被系統性高估，一路延續到模擬結束。因為這個工具的典型用法就是模擬到退休後，這個落差幾乎必然會被觸發，不是邊緣情境。

## 建議修正
在`SpouseDeductionEngine`裡，比照`DependentDeductionEngine.java:67-75`已經驗證正確的寫法（`ChronoUnit.YEARS.between(birthDate, simulationYearEnd)`算出年齡，`age >= 70`時切換較高扣除額），補上配偶年齡70歲以上的判斷分支。這是套用既有、同檔案家族裡已經證明正確的邏輯，不是需要重新設計的新功能。

---

# Issue 103 修復總結報告

## 修復日期
2026-09-30

## 修正概述
本修復解決「配偶扣除計算中遺漏 70 歲以上老人控除對象配偶者之判斷」問題。依據日本所得稅法與地方稅法，補齊配偶滿 70 歲時之提高扣除額邏輯（所得稅 38 萬 $\rightarrow$ 48 萬；住民稅 33 萬 $\rightarrow$ 38 萬），並於程式碼中完整標註法條與官方規範。

## 法規依據
1. **日本所得稅法第2條第1項第33號之2、第83條（国税庁タックスアンサー No.1191 配偶者控除）**：
   - 控除對象配偶者於「該年12月31日現狀」年齡達 70 歲以上者，列為「老人控除対象配偶者」。
   - 所得稅控除額由 380,000 円提高至 **480,000 円**。
2. **日本地方稅法第314條之2第1項第1號（個人住民稅・配偶者控除）**：
   - 老人控除對象配偶者之住民稅控除額由 330,000 円提高至 **380,000 円**。
3. **所得稅法第83條之2（配偶者特別控除）**：
   - 配偶者特別控除（年收超過配偶者控除門檻至 201.6 萬者）依法為按所得遞減之級距設計，**無年齡老人加算**。系統精準保持特別控除之原計算邏輯，不予加成。

## 修改檔案清單
1. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngine.java`
   - 定義法規常數 `ELDERLY_SPOUSE_DEDUCTION_INCOME_TAX = 480_000L` 與 `ELDERLY_SPOUSE_DEDUCTION_INHABITANT_TAX = 380_000L`。
   - 擴充 `calculateIncomeTaxDeduction` 與 `calculateInhabitantTaxDeduction` 簽章接收 `LocalDate simulationYearEnd`，並保留 2 參數版本向後相容。
   - 在配偶者控除條件（`spouseIncome <= 1_500_000L`）下，當 `birthDate != null && simulationYearEnd != null && ChronoUnit.YEARS.between(birthDate, simulationYearEnd) >= 70` 時，切換為老人配偶控除額。
   - 於類別及各判定分支中加入日本所得稅法、地方稅法與國稅廳規範之詳細註解。

2. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java`
   - 將第 98 行已計算之 `simulationYearEnd` 正式傳入 `spouseDeductionEngine.calculateIncomeTaxDeduction` 與 `calculateInhabitantTaxDeduction`。

3. `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngineTest.java`
   - 新增完整單元測試：
     - 未滿 70 歲配偶：一般控除（38 萬 / 33 萬）。
     - 滿 70 歲配偶（含剛好 70 歲及 75 歲）：老人控除（48 萬 / 38 萬）。
     - 無生日者：安全 fallback 一般控除（38 萬 / 33 萬）。
     - 超過配偶者控除門檻適用特別控除（>150萬）：維持特別控除無老人加算。
     - 納稅者收入超標（>11,950,000）：0 控除。

## 效益
- 消除使用者長期人生模擬至退休後（配偶跨過 70 歲），稅金被高估、可支配所得被低估之系統性偏差。
- 程式碼結構與法規註解清晰，並與 `DependentDeductionEngine` 保持完全一致的年齡判定標準。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                    10009  12.96G   17.72    17:32:53  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
index ea8c76a2..31ea1fee 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java
@@ -105,9 +105,10 @@ public class ActiveTaxCalculationEngine {
         if (userProfile != null && userProfile.getFamilyMembers() != null) {
             for (Dependent fm : userProfile.getFamilyMembers()) {
                 if (fm.getMemberType() == DependentType.SPOUSE) {
-                    spouseDeductionIncome += spouseDeductionEngine.calculateIncomeTaxDeduction(grossTotalSalary, fm);
+                    spouseDeductionIncome += spouseDeductionEngine.calculateIncomeTaxDeduction(grossTotalSalary, fm,
+                            simulationYearEnd);
                     spouseDeductionInhabitant += spouseDeductionEngine.calculateInhabitantTaxDeduction(grossTotalSalary,
-                            fm);
+                            fm, simulationYearEnd);
                 } else if (fm.getMemberType() == DependentType.DEPENDENT) {
                     dependentDeductionIncome += dependentDeductionEngine.calculateIncomeTaxDeduction(context, fm,
                             simulationYearEnd);
@@ -141,9 +142,12 @@ public class ActiveTaxCalculationEngine {
                     }
 
                     switch (policy.policyType()) {
-                        case LIFE, ENDOWMENT -> generalLifePremium += premium;
-                        case NURSING_CARE, HEALTH, CANCER, MEDICAL -> nursingCarePremium += premium;
-                        case EARTHQUAKE -> earthquakePremium += premium;
+                        case LIFE, ENDOWMENT ->
+                            generalLifePremium += premium;
+                        case NURSING_CARE, HEALTH, CANCER, MEDICAL ->
+                            nursingCarePremium += premium;
+                        case EARTHQUAKE ->
+                            earthquakePremium += premium;
                         default -> {
                         } // PENSION, FIRE, AUTO, OTHER are ignored for now or handled separately
                     }
@@ -152,12 +156,12 @@ public class ActiveTaxCalculationEngine {
         }
 
         long lifeDeductionIncomeTax = Math.min(120_000L,
-                calculateLifeInsuranceDeductionIncomeTax(generalLifePremium) +
-                        calculateLifeInsuranceDeductionIncomeTax(nursingCarePremium));
+                calculateLifeInsuranceDeductionIncomeTax(generalLifePremium)
+                + calculateLifeInsuranceDeductionIncomeTax(nursingCarePremium));
 
         long lifeDeductionInhabitantTax = Math.min(70_000L,
-                calculateLifeInsuranceDeductionInhabitantTax(generalLifePremium) +
-                        calculateLifeInsuranceDeductionInhabitantTax(nursingCarePremium));
+                calculateLifeInsuranceDeductionInhabitantTax(generalLifePremium)
+                + calculateLifeInsuranceDeductionInhabitantTax(nursingCarePremium));
 
         long eqDeductionIncomeTax = Math.min(50_000L, earthquakePremium);
         long eqDeductionInhabitantTax = Math.min(25_000L, earthquakePremium / 2);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngine.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngine.java
index 0362ff65..3d9380ac 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngine.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngine.java
@@ -23,6 +23,9 @@
  */
 package ch.lin.finance.hub.backend.api.app.service.simulation.core.tax;
 
+import java.time.LocalDate;
+import java.time.temporal.ChronoUnit;
+
 import org.springframework.stereotype.Service;
 
 import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
@@ -30,41 +33,59 @@ import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
 @Service
 public class SpouseDeductionEngine {
 
-    // NTA Constants for 2024
-    // Phase 1: Simple flat deduction for now.
-    // In Phase 2, we can enhance this to actually check the spouse's
-    // estimatedAnnualIncome
-    // against the user's taxable income tiers to calculate 配偶者控除 or 配偶者特別控除
-    // dynamically.
+    /**
+     * 日本所得税法第2条第1項第33号の2、第83条（国税庁タックスアンサー No.1191 配偶者控除）:
+     * - 控除対象配偶者（一般、70歳未満）: 380,000円
+     * - 老人控除対象配偶者（その年12月31日現在の年齢が70歳以上）: 480,000円
+     * ※納税者本人の合計所得金額が900万円以下の場合の金額。
+     */
     private static final long SPOUSE_DEDUCTION_INCOME_TAX = 380_000L;
+    private static final long ELDERLY_SPOUSE_DEDUCTION_INCOME_TAX = 480_000L;
+
+    /**
+     * 日本地方税法第314条の2第1項第1号（個人住民税・配偶者控除）:
+     * - 控除対象配偶者（一般、70歳未満）: 330,000円
+     * - 老人控除対象配偶者（その年12月31日現在の年齢が70歳以上）: 380,000円
+     * ※納税者本人の合計所得金額が900万円以下の場合の金額。
+     */
     private static final long SPOUSE_DEDUCTION_INHABITANT_TAX = 330_000L;
+    private static final long ELDERLY_SPOUSE_DEDUCTION_INHABITANT_TAX = 380_000L;
 
     // For Special Spouse Deduction, income thresholds:
-    // If spouse makes under 1,030,000 JPY -> basic spouse deduction
-    // If spouse makes up to 2,016,000 JPY -> special spouse deduction (tapering
-    // down)
+    // If spouse makes under 1,030,000 JPY (or updated equivalent) -> basic spouse deduction
+    // If spouse makes up to 2,016,000 JPY -> special spouse deduction (tapering down)
     // If user's income is > 10,000,000 -> 0 deduction.
 
     public long calculateIncomeTaxDeduction(long userGrossIncome, Dependent spouse) {
+        return calculateIncomeTaxDeduction(userGrossIncome, spouse, null);
+    }
+
+    public long calculateIncomeTaxDeduction(long userGrossIncome, Dependent spouse, LocalDate simulationYearEnd) {
         if (spouse == null)
             return 0L;
 
         Long annualIncome = spouse.getEstimatedAnnualIncome();
         long spouseIncome = annualIncome != null ? annualIncome : 0L;
 
-        // Simple logic for Phase 1: if spouse makes more than 2,016,000 JPY, no
-        // deduction.
-        // Also if user makes more than 11,950,000 JPY (salary limit corresponding to
-        // 10M income limit), no deduction.
+        // Simple logic for Phase 1: if spouse makes more than 2,016,000 JPY, no deduction.
+        // Also if user makes more than 11,950,000 JPY (salary limit corresponding to 10M income limit), no deduction.
         if (userGrossIncome > 11_950_000L) {
             return 0L;
         }
 
+        // 所得税法第83条: 配偶者控除（給与年収要件内）
         if (spouseIncome <= 1_500_000L) {
+            // 老人控除対象配偶者判定（その年12月31日現在において年齢70歳以上の者）
+            if (spouse.getBirthDate() != null && simulationYearEnd != null) {
+                long age = ChronoUnit.YEARS.between(spouse.getBirthDate(), simulationYearEnd);
+                if (age >= 70) {
+                    return ELDERLY_SPOUSE_DEDUCTION_INCOME_TAX;
+                }
+            }
             return SPOUSE_DEDUCTION_INCOME_TAX;
         } else if (spouseIncome <= 2_016_000L) {
-            // Very simplified tapering.
-            // e.g. (2016000 - spouseIncome) / ...
+            // 所得税法第83条の2: 配偶者特別控除
+            // （注：配偶者特別控除は所得額に応じた逓減措置であり、年齢による老人加算の規定はなし）
             // We just return half for simplicity in this MVP phase
             return SPOUSE_DEDUCTION_INCOME_TAX / 2;
         }
@@ -73,6 +94,10 @@ public class SpouseDeductionEngine {
     }
 
     public long calculateInhabitantTaxDeduction(long userGrossIncome, Dependent spouse) {
+        return calculateInhabitantTaxDeduction(userGrossIncome, spouse, null);
+    }
+
+    public long calculateInhabitantTaxDeduction(long userGrossIncome, Dependent spouse, LocalDate simulationYearEnd) {
         if (spouse == null)
             return 0L;
 
@@ -83,9 +108,18 @@ public class SpouseDeductionEngine {
             return 0L;
         }
 
+        // 地方税法第314条の2第1項第1号: 住民税配偶者控除
         if (spouseIncome <= 1_500_000L) {
+            // 老人控除対象配偶者判定（その年12月31日現在において年齢70歳以上の者）
+            if (spouse.getBirthDate() != null && simulationYearEnd != null) {
+                long age = ChronoUnit.YEARS.between(spouse.getBirthDate(), simulationYearEnd);
+                if (age >= 70) {
+                    return ELDERLY_SPOUSE_DEDUCTION_INHABITANT_TAX;
+                }
+            }
             return SPOUSE_DEDUCTION_INHABITANT_TAX;
         } else if (spouseIncome <= 2_016_000L) {
+            // 住民税配偶者特別控除（年齢による老人加算規定なし）
             return SPOUSE_DEDUCTION_INHABITANT_TAX / 2;
         }
 
diff --git a/finance-hub-backend/todo/pending fix/issue-103.md b/finance-hub-backend/todo/pending fix/issue-103.md
index ba30b19c..deab9f31 100644
--- a/finance-hub-backend/todo/pending fix/issue-103.md
+++ b/finance-hub-backend/todo/pending fix/issue-103.md
@@ -19,3 +19,44 @@ Medium-High（會在配偶年齡跨過70歲那一刻起，低估配偶扣除額
 
 ## 建議修正
 在`SpouseDeductionEngine`裡，比照`DependentDeductionEngine.java:67-75`已經驗證正確的寫法（`ChronoUnit.YEARS.between(birthDate, simulationYearEnd)`算出年齡，`age >= 70`時切換較高扣除額），補上配偶年齡70歲以上的判斷分支。這是套用既有、同檔案家族裡已經證明正確的邏輯，不是需要重新設計的新功能。
+
+---
+
+# Issue 103 修復總結報告
+
+## 修復日期
+2026-09-30
+
+## 修正概述
+本修復解決「配偶扣除計算中遺漏 70 歲以上老人控除對象配偶者之判斷」問題。依據日本所得稅法與地方稅法，補齊配偶滿 70 歲時之提高扣除額邏輯（所得稅 38 萬 $\rightarrow$ 48 萬；住民稅 33 萬 $\rightarrow$ 38 萬），並於程式碼中完整標註法條與官方規範。
+
+## 法規依據
+1. **日本所得稅法第2條第1項第33號之2、第83條（国税庁タックスアンサー No.1191 配偶者控除）**：
+   - 控除對象配偶者於「該年12月31日現狀」年齡達 70 歲以上者，列為「老人控除対象配偶者」。
+   - 所得稅控除額由 380,000 円提高至 **480,000 円**。
+2. **日本地方稅法第314條之2第1項第1號（個人住民稅・配偶者控除）**：
+   - 老人控除對象配偶者之住民稅控除額由 330,000 円提高至 **380,000 円**。
+3. **所得稅法第83條之2（配偶者特別控除）**：
+   - 配偶者特別控除（年收超過配偶者控除門檻至 201.6 萬者）依法為按所得遞減之級距設計，**無年齡老人加算**。系統精準保持特別控除之原計算邏輯，不予加成。
+
+## 修改檔案清單
+1. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngine.java`
+   - 定義法規常數 `ELDERLY_SPOUSE_DEDUCTION_INCOME_TAX = 480_000L` 與 `ELDERLY_SPOUSE_DEDUCTION_INHABITANT_TAX = 380_000L`。
+   - 擴充 `calculateIncomeTaxDeduction` 與 `calculateInhabitantTaxDeduction` 簽章接收 `LocalDate simulationYearEnd`，並保留 2 參數版本向後相容。
+   - 在配偶者控除條件（`spouseIncome <= 1_500_000L`）下，當 `birthDate != null && simulationYearEnd != null && ChronoUnit.YEARS.between(birthDate, simulationYearEnd) >= 70` 時，切換為老人配偶控除額。
+   - 於類別及各判定分支中加入日本所得稅法、地方稅法與國稅廳規範之詳細註解。
+
+2. `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java`
+   - 將第 98 行已計算之 `simulationYearEnd` 正式傳入 `spouseDeductionEngine.calculateIncomeTaxDeduction` 與 `calculateInhabitantTaxDeduction`。
+
+3. `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/SpouseDeductionEngineTest.java`
+   - 新增完整單元測試：
+     - 未滿 70 歲配偶：一般控除（38 萬 / 33 萬）。
+     - 滿 70 歲配偶（含剛好 70 歲及 75 歲）：老人控除（48 萬 / 38 萬）。
+     - 無生日者：安全 fallback 一般控除（38 萬 / 33 萬）。
+     - 超過配偶者控除門檻適用特別控除（>150萬）：維持特別控除無老人加算。
+     - 納稅者收入超標（>11,950,000）：0 控除。
+
+## 效益
+- 消除使用者長期人生模擬至退休後（配偶跨過 70 歲），稅金被高估、可支配所得被低估之系統性偏差。
+- 程式碼結構與法規註解清晰，並與 `DependentDeductionEngine` 保持完全一致的年齡判定標準。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                         10010  12.98G   17.46    17:32:58  