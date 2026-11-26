# [FIXED] Issue 143：扶養親屬扣除沒有區分「同居老親等」跟一般老年扶養親屬，一律套用較低的金額

## 這份issue怎麼被發現的
這是這次Source Code Review針對稅務計算引擎進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java:47-48,94-95,123-124`（`ELDERLY_DEPENDENT_INCOME_TAX = 480_000L`、`ELDERLY_DEPENDENT_INHABITANT_TAX = 380_000L`，70歲以上的扶養親屬一律套用這組數字）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java:40-66`（整個`Dependent`資料結構裡，沒有任何欄位可以記錄「這位扶養親屬是否跟使用者同住」）

## 現狀
系統目前對70歲以上的扶養親屬（父母、祖父母等），一律套用48萬円（所得稅）/38萬円（住民稅）這組金額，不管這位長輩是否跟使用者同住。

## 為何是問題
依照國稅廳No.1180（已查證：`https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1180.htm`），老人扶養親族實際上分兩種金額：「同居老親等以外の者」（不同住）是48萬円，「同居老親等」（同住的直系尊親屬，例如把父母接來一起住）則是較高的58萬円（所得稅部分；住民稅這一側的對應金額，這次沒有查到可信的第一手資料來源確認，需要另外查證）。

系統目前的資料結構完全沒有「是否同住」這個欄位，等於這條較高金額的路徑從一開始就不存在，不是計算錯誤、是這個情境在資料模型裡完全沒辦法表達。對這個工具設定的核心客群而言，「把年邁父母接來同住」是相當常見的真實情境，不是邊緣案例——只要使用者有這種情境，所得稅扣除額就會被低估10萬円，而且是每一年都低估，不是一次性的小誤差。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：在`Dependent`資料結構裡新增「是否同居」欄位**
- 做法：仿照`SpouseDeductionEngine`已經在用的「是否70歲以上」這種布林值穿插邏輯，新增一個類似的`livesWithTaxpayer`欄位，一路傳到扣除額計算策略層。
- 優點：直接對應法規的實際分類方式，之後查到確定的住民稅金額也能直接套進去。
- 缺點：多一個使用者要記得填寫的欄位，如果使用者沒注意到或忘記勾選，可能還是套用錯的（較低的）那組金額。

**方向B：先確認住民稅這一側的確切金額，再決定要不要動**
- 理由：目前只查到所得稅這一側的確切數字（48萬/58萬），住民稅那一側沒查到可信來源，如果要修正應該兩邊一起處理，不要只改一半。

**方向C：維持現狀，記錄為已知落差**
- 理由：如果目前使用者本人沒有同住的年邁扶養親屬，這個落差目前是純理論性的，可以先記錄下來，等真的有這個情境時再處理。

## 決策記錄 (2026-10-11)
決定採用 **方向A**：
1. **法定金額確認**：
   - 所得稅：同居老親等 58 萬円（不同住 48 萬円）。
   - 住民稅：同居老親等 45 萬円（不同住 38 萬円，依據地方稅法第314條之2第1項第9號確認）。
2. **實作範圍**：
   - 在 `Dependent` 實體與所有層級之 DTO / Command / Response 中新增 `isCohabiting` 欄位（預設 `false`）。
   - 擴充 `DependentDeductionStrategy` 與 `AbstractDependentDeductionStrategy`，於 70 歲以上時依據 `isCohabiting` 區分同居老親等與一般老親等扣除額。
   - 前端 Profile Family Dialog 補齊「同居老親等（同居の父母・祖父母）」勾選項目。
   - 補齊對應之扣除額策略與引擎單元測試。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                             10032  13.08G   12.63    14:01:05  
diff --git a/hub-ui/src/app/finance/components/profile-family-section.tsx b/hub-ui/src/app/finance/components/profile-family-section.tsx
index 46d981d..3216cfc 100644
--- a/hub-ui/src/app/finance/components/profile-family-section.tsx
+++ b/hub-ui/src/app/finance/components/profile-family-section.tsx
@@ -21,12 +21,12 @@ export function ProfileFamilySection({ formData, onChange }: Props) {
   const [editingMember, setEditingMember] = useState<FamilyMember | null>(null);
 
   const openAddFamilyMember = () => {
-    setEditingMember({ memberType: "SPOUSE", birthDate: "", estimatedAnnualIncome: null });
+    setEditingMember({ memberType: "SPOUSE", birthDate: "", estimatedAnnualIncome: null, isCohabiting: false });
     setIsFamilyDialogOpen(true);
   };
 
   const openEditFamilyMember = (index: number) => {
-    setEditingMember({ ...formData.familyMembers[index], id: index.toString() }); // use id as index temporarily
+    setEditingMember({ ...formData.familyMembers[index], id: index.toString(), isCohabiting: formData.familyMembers[index].isCohabiting ?? false });
     setIsFamilyDialogOpen(true);
   };
 
@@ -87,6 +87,7 @@ export function ProfileFamilySection({ formData, onChange }: Props) {
                     <div className="font-medium">{fm.memberType}</div>
                     <div className="text-sm text-muted-foreground">
                       Born: {fm.birthDate} | Income: ¥{fm.estimatedAnnualIncome?.toLocaleString() ?? 0}
+                      {fm.isCohabiting && <span className="ml-2 text-xs font-semibold text-primary bg-primary/10 px-1.5 py-0.5 rounded">Cohabiting (同居)</span>}
                     </div>
                   </div>
                   <div className="flex items-center gap-2">
@@ -149,6 +150,18 @@ export function ProfileFamilySection({ formData, onChange }: Props) {
               />
               <p className="text-xs text-muted-foreground">Used for deduction eligibility (e.g. Spousal deduction limits).</p>
             </div>
+            <div className="flex items-center space-x-2 pt-2">
+              <input
+                type="checkbox"
+                id="isCohabiting"
+                className="h-4 w-4 rounded border-gray-300 text-primary focus:ring-primary cursor-pointer"
+                checked={editingMember?.isCohabiting || false}
+                onChange={(e) => setEditingMember(prev => prev ? { ...prev, isCohabiting: e.target.checked } : null)}
+              />
+              <Label htmlFor="isCohabiting" className="cursor-pointer text-sm font-normal">
+                Lives with taxpayer (同居老親等：同居している父母・祖父母等)
+              </Label>
+            </div>
           </div>
           <DialogFooter>
             <Button variant="outline" onClick={() => setIsFamilyDialogOpen(false)}>Cancel</Button>
diff --git a/hub-ui/src/app/finance/components/user-profile-form.tsx b/hub-ui/src/app/finance/components/user-profile-form.tsx
index ac77d97..44d5c38 100644
--- a/hub-ui/src/app/finance/components/user-profile-form.tsx
+++ b/hub-ui/src/app/finance/components/user-profile-form.tsx
@@ -10,6 +10,7 @@ export interface FamilyMember {
   memberType: string;
   birthDate: string;
   estimatedAnnualIncome: number | null;
+  isCohabiting?: boolean;
 }
 
 export type CurrencyCode = "USD" | "JPY" | "TWD" | "EUR" | "UNKNOWN";
diff --git a/hub-ui/src/app/finance/onboarding/page.tsx b/hub-ui/src/app/finance/onboarding/page.tsx
index b14a923..bee537b 100644
--- a/hub-ui/src/app/finance/onboarding/page.tsx
+++ b/hub-ui/src/app/finance/onboarding/page.tsx
@@ -123,6 +123,7 @@ export default function OnboardingPage() {
           memberType: fm.memberType,
           birthDate: fm.birthDate,
           estimatedAnnualIncome: fm.estimatedAnnualIncome,
+          isCohabiting: fm.isCohabiting ?? false,
         })),
         applyProfileToOptimizerBaseline: formData.applyProfileToOptimizerBaseline
       });
diff --git a/hub-ui/src/app/finance/profile/me/page.tsx b/hub-ui/src/app/finance/profile/me/page.tsx
index cc621a7..a556c00 100644
--- a/hub-ui/src/app/finance/profile/me/page.tsx
+++ b/hub-ui/src/app/finance/profile/me/page.tsx
@@ -107,6 +107,7 @@ export default function ProfileConfigPage() {
           memberType: fm.memberType,
           birthDate: fm.birthDate,
           estimatedAnnualIncome: fm.estimatedAnnualIncome,
+          isCohabiting: fm.isCohabiting ?? false,
         })),
         applyProfileToOptimizerBaseline: formData.applyProfileToOptimizerBaseline
       });
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                        10033  12.93G   12.33    14:01:17  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                10034  13.11G   12.20    14:01:23  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/dependent/result/DependentResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/dependent/result/DependentResult.java
index 62b1ef87..fb094157 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/dependent/result/DependentResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/dependent/result/DependentResult.java
@@ -43,6 +43,7 @@ public class DependentResult extends BaseResult {
     private DependentType memberType;
     private LocalDate birthDate;
     private Long estimatedAnnualIncome;
+    private Boolean isCohabiting;
 
     public static DependentResult fromEntity(Dependent entity, String ownerUserId) {
         if (entity == null) {
@@ -53,6 +54,7 @@ public class DependentResult extends BaseResult {
         result.setMemberType(entity.getMemberType());
         result.setBirthDate(entity.getBirthDate());
         result.setEstimatedAnnualIncome(entity.getEstimatedAnnualIncome());
+        result.setIsCohabiting(entity.getIsCohabiting());
         result.setOwnerUserId(ownerUserId);
         result.setPrivacyLevel(PrivacyLevel.PRIVATE);
         return result;
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java
index 85341ed5..c622a83a 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java
@@ -79,6 +79,7 @@ public class UserProfileServiceImpl implements UserProfileService {
                 fm.setMemberType(cmd.memberType());
                 fm.setBirthDate(cmd.birthDate());
                 fm.setEstimatedAnnualIncome(cmd.estimatedAnnualIncome());
+                fm.setIsCohabiting(cmd.isCohabiting());
                 return fm;
             }).collect(Collectors.toList());
             profile.getFamilyMembers().addAll(members);
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveDependentCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveDependentCommand.java
index a9bf0eb0..a656c1e8 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveDependentCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveDependentCommand.java
@@ -30,5 +30,11 @@ import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.DependentTy
 public record SaveDependentCommand(
         DependentType memberType,
         LocalDate birthDate,
-        Long estimatedAnnualIncome) {
+        Long estimatedAnnualIncome,
+        Boolean isCohabiting) {
+
+    public SaveDependentCommand(DependentType memberType, LocalDate birthDate, Long estimatedAnnualIncome) {
+        this(memberType, birthDate, estimatedAnnualIncome, false);
+    }
 }
+
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java
index 058e769d..50cdc084 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java
@@ -47,6 +47,10 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
     protected static final long ELDERLY_DEPENDENT_INCOME_TAX = 480_000L;
     protected static final long ELDERLY_DEPENDENT_INHABITANT_TAX = 380_000L;
 
+    // Elderly Cohabiting Ascendant Dependent (同居老親等 70+)
+    protected static final long ELDERLY_COHABITING_ASCENDANT_INCOME_TAX = 580_000L;
+    protected static final long ELDERLY_COHABITING_ASCENDANT_INHABITANT_TAX = 450_000L;
+
     @Override
     public long calculateIncomeTaxDeduction(Dependent dependent, LocalDate simulationYearEnd) {
         if (dependent == null) {
@@ -55,7 +59,8 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
         if (dependent.getMemberType() == DependentType.SPOUSE) {
             return 0L;
         }
-        return calculateIncomeTaxDeduction(dependent.getEstimatedAnnualIncome(), dependent.getBirthDate(), simulationYearEnd);
+        return calculateIncomeTaxDeduction(dependent.getEstimatedAnnualIncome(), dependent.getBirthDate(),
+                simulationYearEnd, dependent.isCohabiting());
     }
 
     @Override
@@ -66,11 +71,18 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
         if (dependent.getMemberType() == DependentType.SPOUSE) {
             return 0L;
         }
-        return calculateInhabitantTaxDeduction(dependent.getEstimatedAnnualIncome(), dependent.getBirthDate(), simulationYearEnd);
+        return calculateInhabitantTaxDeduction(dependent.getEstimatedAnnualIncome(), dependent.getBirthDate(),
+                simulationYearEnd, dependent.isCohabiting());
     }
 
     @Override
     public long calculateIncomeTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd) {
+        return calculateIncomeTaxDeduction(estimatedAnnualIncome, birthDate, simulationYearEnd, false);
+    }
+
+    @Override
+    public long calculateIncomeTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate,
+            LocalDate simulationYearEnd, boolean isCohabiting) {
         long income = estimatedAnnualIncome != null ? estimatedAnnualIncome : 0L;
         Long age = calculateAge(birthDate, simulationYearEnd);
 
@@ -92,7 +104,7 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
         if (age < 16) {
             return 0L;
         } else if (age >= 70) {
-            return ELDERLY_DEPENDENT_INCOME_TAX;
+            return isCohabiting ? ELDERLY_COHABITING_ASCENDANT_INCOME_TAX : ELDERLY_DEPENDENT_INCOME_TAX;
         } else {
             return GENERAL_DEPENDENT_INCOME_TAX;
         }
@@ -100,6 +112,12 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
 
     @Override
     public long calculateInhabitantTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd) {
+        return calculateInhabitantTaxDeduction(estimatedAnnualIncome, birthDate, simulationYearEnd, false);
+    }
+
+    @Override
+    public long calculateInhabitantTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate,
+            LocalDate simulationYearEnd, boolean isCohabiting) {
         long income = estimatedAnnualIncome != null ? estimatedAnnualIncome : 0L;
         Long age = calculateAge(birthDate, simulationYearEnd);
 
@@ -121,7 +139,7 @@ public abstract class AbstractDependentDeductionStrategy implements DependentDed
         if (age < 16) {
             return 0L;
         } else if (age >= 70) {
-            return ELDERLY_DEPENDENT_INHABITANT_TAX;
+            return isCohabiting ? ELDERLY_COHABITING_ASCENDANT_INHABITANT_TAX : ELDERLY_DEPENDENT_INHABITANT_TAX;
         } else {
             return GENERAL_DEPENDENT_INHABITANT_TAX;
         }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategy.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategy.java
index 7fd11553..2abb0df1 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategy.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategy.java
@@ -62,6 +62,19 @@ public interface DependentDeductionStrategy {
      */
     long calculateIncomeTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd);
 
+    /**
+     * Overloaded convenience method to calculate income tax deduction directly from parameters,
+     * including cohabitation status.
+     *
+     * @param estimatedAnnualIncome estimated annual gross wage or pension in JPY
+     * @param birthDate birth date of the dependent
+     * @param simulationYearEnd the simulation year-end date
+     * @param isCohabiting whether the dependent is cohabiting with taxpayer (同居老親等)
+     * @return deduction amount in JPY
+     */
+    long calculateIncomeTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd,
+            boolean isCohabiting);
+
     /**
      * Overloaded convenience method to calculate personal inhabitant tax deduction directly from parameters.
      *
@@ -72,6 +85,19 @@ public interface DependentDeductionStrategy {
      */
     long calculateInhabitantTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd);
 
+    /**
+     * Overloaded convenience method to calculate personal inhabitant tax deduction directly from parameters,
+     * including cohabitation status.
+     *
+     * @param estimatedAnnualIncome estimated annual gross wage or pension in JPY
+     * @param birthDate birth date of the dependent
+     * @param simulationYearEnd the simulation year-end date
+     * @param isCohabiting whether the dependent is cohabiting with taxpayer (同居老親等)
+     * @return deduction amount in JPY
+     */
+    long calculateInhabitantTaxDeduction(Long estimatedAnnualIncome, LocalDate birthDate, LocalDate simulationYearEnd,
+            boolean isCohabiting);
+
     /**
      * Returns the statutory wage income threshold (給与収入門檻) for this era.
      *
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java
index e4b2b3b0..22d04545 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyTest.java
@@ -191,6 +191,25 @@ class DependentDeductionStrategyTest {
             assertThat(strategy.calculateIncomeTaxDeduction(senior, yearEnd)).isEqualTo(480_000L);
             assertThat(strategy.calculateInhabitantTaxDeduction(senior, yearEnd)).isEqualTo(380_000L);
         }
+
+        @Test
+        void shouldReturnElderlyCohabitingAscendantDeductionForAge70AndAboveWhenLivingTogether() {
+            // Age 72 (Born 1954-05-10) with isCohabiting = true (同居老親等)
+            Dependent cohabitingSenior = Dependent.builder()
+                    .memberType(DependentType.DEPENDENT)
+                    .birthDate(LocalDate.of(1954, 5, 10))
+                    .estimatedAnnualIncome(1_500_000L)
+                    .isCohabiting(true)
+                    .build();
+            assertThat(strategy.calculateIncomeTaxDeduction(cohabitingSenior, yearEnd)).isEqualTo(580_000L);
+            assertThat(strategy.calculateInhabitantTaxDeduction(cohabitingSenior, yearEnd)).isEqualTo(450_000L);
+
+            // Also test 4-arg overload directly
+            assertThat(strategy.calculateIncomeTaxDeduction(1_500_000L, LocalDate.of(1954, 5, 10), yearEnd, true))
+                    .isEqualTo(580_000L);
+            assertThat(strategy.calculateInhabitantTaxDeduction(1_500_000L, LocalDate.of(1954, 5, 10), yearEnd, true))
+                    .isEqualTo(450_000L);
+        }
     }
 
     @Nested
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java
index 52ee0d3b..57b22c9a 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/DependentDeductionEngineTest.java
@@ -119,4 +119,27 @@ class DependentDeductionEngineTest {
         // Income tax qualifies under 1.72M threshold -> 480,000 JPY
         assertThat(engine.calculateIncomeTaxDeduction(context, senior, yearEnd2026)).isEqualTo(480_000L);
     }
+
+    @Test
+    @DisplayName("Should differentiate cohabiting and non-cohabiting elderly dependent deduction")
+    void calculate_elderlyCohabitingAscendant_differentiatesAmounts() {
+        // Senior age 72 with 1,500,000 JPY pension (qualifies for both income tax and inhabitant tax in all eras)
+        Dependent senior = Dependent.builder()
+                .memberType(DependentType.DEPENDENT)
+                .birthDate(LocalDate.of(1954, 5, 1))
+                .estimatedAnnualIncome(1_500_000L)
+                .build();
+
+        SimulationContext context = new SimulationContext();
+        context.setYear(2026);
+
+        // Non-cohabiting: 480,000 JPY income tax, 380,000 JPY inhabitant tax
+        assertThat(engine.calculateIncomeTaxDeduction(context, senior, yearEnd2026)).isEqualTo(480_000L);
+        assertThat(engine.calculateInhabitantTaxDeduction(context, senior, yearEnd2026)).isEqualTo(380_000L);
+
+        // Cohabiting (同居老親等): 580,000 JPY income tax, 450,000 JPY inhabitant tax
+        senior.setIsCohabiting(true);
+        assertThat(engine.calculateIncomeTaxDeduction(context, senior, yearEnd2026)).isEqualTo(580_000L);
+        assertThat(engine.calculateInhabitantTaxDeduction(context, senior, yearEnd2026)).isEqualTo(450_000L);
+    }
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java
index 7683d777..cd1630a4 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java
@@ -64,4 +64,12 @@ public class Dependent {
 
     @Column(name = "estimated_annual_income")
     private Long estimatedAnnualIncome;
+
+    @Column(name = "is_cohabiting")
+    private Boolean isCohabiting;
+
+    public boolean isCohabiting() {
+        return Boolean.TRUE.equals(isCohabiting);
+    }
 }
+
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentRequest.java
index 0d1ad969..5d0bb491 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentRequest.java
@@ -31,5 +31,11 @@ import jakarta.validation.constraints.NotNull;
 public record DependentRequest(
         DependentType memberType,
         @NotNull LocalDate birthDate,
-        Long estimatedAnnualIncome) {
+        Long estimatedAnnualIncome,
+        Boolean isCohabiting) {
+
+    public DependentRequest(DependentType memberType, LocalDate birthDate, Long estimatedAnnualIncome) {
+        this(memberType, birthDate, estimatedAnnualIncome, false);
+    }
 }
+
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentResponse.java
index 2804d9f1..db76f5ae 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/dependent/dto/DependentResponse.java
@@ -31,5 +31,11 @@ public record DependentResponse(
         Long id,
         DependentType memberType,
         LocalDate birthDate,
-        Long estimatedAnnualIncome) {
+        Long estimatedAnnualIncome,
+        Boolean isCohabiting) {
+
+    public DependentResponse(Long id, DependentType memberType, LocalDate birthDate, Long estimatedAnnualIncome) {
+        this(id, memberType, birthDate, estimatedAnnualIncome, false);
+    }
 }
+
diff --git a/finance-hub-backend/todo/pending fix/issue-143.md b/finance-hub-backend/todo/pending fix/issue-143.md
index d70310c2..7c14faf3 100644
--- a/finance-hub-backend/todo/pending fix/issue-143.md
+++ b/finance-hub-backend/todo/pending fix/issue-143.md
@@ -28,3 +28,15 @@
 
 **方向C：維持現狀，記錄為已知落差**
 - 理由：如果目前使用者本人沒有同住的年邁扶養親屬，這個落差目前是純理論性的，可以先記錄下來，等真的有這個情境時再處理。
+
+## 決策記錄 (2026-10-11)
+決定採用 **方向A**：
+1. **法定金額確認**：
+   - 所得稅：同居老親等 58 萬円（不同住 48 萬円）。
+   - 住民稅：同居老親等 45 萬円（不同住 38 萬円，依據地方稅法第314條之2第1項第9號確認）。
+2. **實作範圍**：
+   - 在 `Dependent` 實體與所有層級之 DTO / Command / Response 中新增 `isCohabiting` 欄位（預設 `false`）。
+   - 擴充 `DependentDeductionStrategy` 與 `AbstractDependentDeductionStrategy`，於 70 歲以上時依據 `isCohabiting` 區分同居老親等與一般老親等扣除額。
+   - 前端 Profile Family Dialog 補齊「同居老親等（同居の父母・祖父母）」勾選項目。
+   - 補齊對應之扣除額策略與引擎單元測試。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                     10035  13.09G   12.20    14:01:25  