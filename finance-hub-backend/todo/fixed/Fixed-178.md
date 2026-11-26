# [FIXED] 178：大學階段的手動覆寫生活費（manualLivingCost）完全不受「是否離家在外」（isAwayFromHome）開關約束，即使使用者把開關關閉，只要這個數值還留著系統就會繼續把它當成每月生活費算進去

## 這份issue怎麼被發現的
這是這次Source Code Review審查教育費服務層時發現的——這是程式碼本身的判斷順序寫錯，不是刻意的產品設計：`[Fixed] Plan-01.md`文件裡明確寫著這個欄位的意圖是「只有在Away From Home開啟時才顯示、才有意義」。

## 相關檔案
- [EducationProfileResolver.java:251-256](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)（`resolveUniversity`裡決定`livingCost`的判斷順序）
- [ChildEducationSettingsServiceImpl.java:147-160](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)（`updateSettings`裡`manualLivingCost`/`isAwayFromHome`的寫入邏輯——已重新查證確認）
- [ChildEducationSettings.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)（`manualLivingCost`、`isAwayFromHome`兩個獨立欄位，沒有任何DB層級約束要求兩者一致）

## 現狀
`EducationProfileResolver.resolveUniversity`決定大學階段生活費的程式碼如下：
```java
BigDecimal livingCost = BigDecimal.ZERO;
if (settings != null && settings.getManualLivingCost() != null) {
    livingCost = settings.getManualLivingCost();
} else if (settings != null && Boolean.TRUE.equals(settings.getIsAwayFromHome())) {
    livingCost = resolveDefaultUniversityLivingCost(track, uniConfig);
}
```
判斷順序是：只要`manualLivingCost`這個欄位有值，就直接採用，完全不檢查`isAwayFromHome`是真是假；只有在`manualLivingCost`是null的情況下，才會去看`isAwayFromHome`決定要不要套用國家預設生活費。這代表`manualLivingCost`這個「手動覆寫值」在判斷順序上完全凌駕於`isAwayFromHome`這個「要不要有生活費」的開關之上。

而`ChildEducationSettingsServiceImpl.updateSettings`裡，只有在大學軌道整個被設成`NONE`時，才會同時清空`manualLivingCost`跟`isAwayFromHome`。當大學軌道維持現狀（不是NONE）、只是`isAwayFromHome`這一個欄位被改成`false`時，`manualLivingCost`完全沒有被連動清空——它會原封不動存進資料庫裡的既有值。

## 為何是問題
`[Fixed] Plan-01.md`裡對這個欄位的設計意圖寫得很清楚：「Manual Living Cost……只有在『Away From Home』開關被打開時，才顯示這一格輸入框；關閉時隱藏，不佔畫面空間」——這代表`manualLivingCost`這個數字，意圖上是附屬在`isAwayFromHome`這個開關底下的子欄位，開關關掉之後，這筆數字理論上就不該再有任何意義。但後端`EducationProfileResolver`的判斷順序完全沒有體現這層從屬關係，`manualLivingCost`是一個獨立生效的欄位，不管`isAwayFromHome`現在是true還是false都照樣套用。

具體情境：使用者一開始幫小孩的大學階段設定「US軌道、Away From Home開啟」，手動填了一個比系統預設更貼近實際租金行情的生活費（例如每月35萬円，比系統預設的28萬円更高）。後來小孩決定改成通勤、不在外面住宿，使用者把「Away From Home」開關關掉——如果這次送出的請求裡`manualLivingCost`這個欄位沒有被同時清空（不管是因為前端畫面隱藏了這個欄位卻沒清空它的值，還是任何其他未來會呼叫這個API的用戶端，例如之後要做的Chrome Extension或是直接呼叫API的情境），系統仍然會每個月把這35萬円生活費繼續算進模擬裡，跟使用者「已經不在外面住」這個明確表達的意圖完全相反。這筆多算的生活費會一路累加進整個大學就讀期間，顯著低估使用者實際的現金流跟淨資產累積速度。

## 建議修正方向
這裡列出兩個可行方向，不代為決定：

**方向A：在EducationProfileResolver.resolveUniversity裡，把isAwayFromHome提升為最外層的判斷條件，manualLivingCost只在isAwayFromHome為true時才生效**
```java
BigDecimal livingCost = BigDecimal.ZERO;
if (settings != null && Boolean.TRUE.equals(settings.getIsAwayFromHome())) {
    livingCost = (settings.getManualLivingCost() != null)
            ? settings.getManualLivingCost()
            : resolveDefaultUniversityLivingCost(track, uniConfig);
}
```
- 優點：修正集中在Resolver這一處，邏輯跟Plan-01原本「manualLivingCost從屬於isAwayFromHome」的設計意圖一致，不需要動到Service層或前端。
- 缺點：如果現有資料庫裡已經有一些「isAwayFromHome=false但manualLivingCost有值」的既存資料（純粹資料殘留，不代表使用者真的想要生活費歸零），這個修正會讓這些資料的生活費從原本的「照樣套用」變成「歸零」，需要先確認這個行為轉變是否可以接受。

**方向B：在ChildEducationSettingsServiceImpl.updateSettings裡，只要isAwayFromHome被明確設成false，就連動把manualLivingCost清空成null，跟大學軌道整個設成NONE時的處理方式一致**
- 優點：在資料寫入的源頭就杜絕「開關關閉、數值卻殘留」這種不一致狀態，資料庫裡永遠不會存在矛盾的組合，Resolver那端完全不需要改。
- 缺點：如果使用者只是暫時把開關關掉、很快又要重新打開（例如先試算「如果通勤」的情境，等等又要切回「住宿」情境做對比），這個做法會讓使用者之前填的手動金額被直接清空、下次打開開關要重新輸入一次；方向A則不會有這個副作用，因為它只是暫時不生效，數值本身還留著。

## 修正報告書

### 1. 修正概述
本 issue 已於 2026-11-01 採納「方向 A」修正完畢。在大學階段的生活費解析邏輯中，將 `isAwayFromHome`（是否離家外宿）提升為最外層守衛條件。只有在 `Boolean.TRUE.equals(isAwayFromHome)` 時，才會進一步判斷是採用手動覆寫值（`manualLivingCost`）還是國家預設生活費；若開關為關閉或 null，生活費嚴格歸零，徹底修復「開關關閉後手動金額仍然生效」的計算缺陷，同時兼顧 UX 體驗（使用者暫時切換開關比對現金流情境時，不必重複輸入生活費數字）。

### 2. 異動檔案與修正內容
1. **後端解析引擎核心判斷**：
   - [EducationProfileResolver.java:251-256](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java#L251-L256)：
     將原本優先檢查 `manualLivingCost != null` 的扁平 `if-else` 改為以 `isAwayFromHome` 為外層約束的巢狀判斷：
     ```java
     BigDecimal livingCost = BigDecimal.ZERO;
     if (settings != null && Boolean.TRUE.equals(settings.getIsAwayFromHome())) {
         livingCost = (settings.getManualLivingCost() != null)
                 ? settings.getManualLivingCost()
                 : resolveDefaultUniversityLivingCost(track, uniConfig);
     }
     ```

2. **單元測試補充與驗證**：
   - [EducationProfileResolverTest.java](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)：
     - 新增 `shouldIgnoreManualLivingCostWhenIsAwayFromHomeIsFalse`：驗證當 `isAwayFromHome` 為 `false` 時，即使設定中仍存有 `manualLivingCost=350000`，最終年度生活費依然精確解析為 `0`。
     - 新增 `shouldAdoptManualLivingCostWhenIsAwayFromHomeIsTrue`：驗證當開關開啟時，能精準採用自訂金額（`350,000 * 12 = 4,200,000`）。
     - 新增 `shouldUseDefaultLivingCostWhenIsAwayFromHomeIsTrueAndManualLivingCostIsNull`：驗證當開關開啟但無手動覆寫時，自動回退到對應國別的預設生活費。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                                                      10020  13.03G   9.39    10:47:22  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
index dd8510c9..0699a89b 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
@@ -62,9 +62,9 @@ public class EducationProfileResolver {
      * Resolves the flattened profile for all education stages.
      *
      * @param settings The child's education settings with optional overrides
-     * (can be null).
-     * @param config The global education configuration with default track
-     * specifications (can be null).
+     *                 (can be null).
+     * @param config   The global education configuration with default track
+     *                 specifications (can be null).
      * @return A flattened ResolvedEducationProfile for all stages.
      */
     public ResolvedEducationProfile resolve(ChildEducationSettings settings, EducationConfig config) {
@@ -83,8 +83,10 @@ public class EducationProfileResolver {
 
     private ResolvedStageData resolvePreschool(ChildEducationSettings settings, EducationConfig config) {
         Objects.requireNonNull(settings);
-        // Preschool defaults to HOME (Home Care = 0 tuition, 36 months) when settings or track is omitted.
-        // HOME represents the zero-cost baseline track for preschool; there is no NONE track.
+        // Preschool defaults to HOME (Home Care = 0 tuition, 36 months) when settings
+        // or track is omitted.
+        // HOME represents the zero-cost baseline track for preschool; there is no NONE
+        // track.
         PreschoolTrack track1 = settings.getPreschoolTrack() != null
                 ? settings.getPreschoolTrack()
                 : PreschoolTrack.HOME;
@@ -96,7 +98,8 @@ public class EducationProfileResolver {
                 && settings.getPreschoolTrack2() != null;
 
         if (!isTwoStage) {
-            ResolvedStageData stage1Data = mergeStage(EducationStage.PRESCHOOL, track1.name(), trackConfig1, override1, BigDecimal.ZERO);
+            ResolvedStageData stage1Data = mergeStage(EducationStage.PRESCHOOL, track1.name(), trackConfig1, override1,
+                    BigDecimal.ZERO);
             // Single-track clamping: duration must be at least 36 months (3 years)
             int clampedDuration = Math.max(36, stage1Data.getDurationMonths());
             if (clampedDuration != stage1Data.getDurationMonths()) {
@@ -112,12 +115,14 @@ public class EducationProfileResolver {
         int d1 = (rawD1 != null && (rawD1 == 12 || rawD1 == 24)) ? rawD1 : 12;
         int d2 = 36 - d1;
 
-        ResolvedStageData stage1Base = mergeStage(EducationStage.PRESCHOOL, track1.name(), trackConfig1, override1, BigDecimal.ZERO);
+        ResolvedStageData stage1Base = mergeStage(EducationStage.PRESCHOOL, track1.name(), trackConfig1, override1,
+                BigDecimal.ZERO);
 
         PreschoolTrack track2 = settings.getPreschoolTrack2();
         TrackConfig trackConfig2 = getPreschoolTrackConfig(config, track2);
         StageOverride override2 = settings.getPreschoolOverride2();
-        ResolvedStageData stage2Base = mergeStage(EducationStage.PRESCHOOL, track2.name(), trackConfig2, override2, BigDecimal.ZERO);
+        ResolvedStageData stage2Base = mergeStage(EducationStage.PRESCHOOL, track2.name(), trackConfig2, override2,
+                BigDecimal.ZERO);
 
         ResolvedStageData stage2Data = stage2Base.toBuilder()
                 .durationMonths(d2)
@@ -249,10 +254,10 @@ public class EducationProfileResolver {
         }
 
         BigDecimal livingCost = BigDecimal.ZERO;
-        if (settings != null && settings.getManualLivingCost() != null) {
-            livingCost = settings.getManualLivingCost();
-        } else if (settings != null && Boolean.TRUE.equals(settings.getIsAwayFromHome())) {
-            livingCost = resolveDefaultUniversityLivingCost(track, uniConfig);
+        if (settings != null && Boolean.TRUE.equals(settings.getIsAwayFromHome())) {
+            livingCost = (settings.getManualLivingCost() != null)
+                    ? settings.getManualLivingCost()
+                    : resolveDefaultUniversityLivingCost(track, uniConfig);
         }
 
         StageOverride override = settings != null ? settings.getUniversityOverride() : null;
@@ -316,7 +321,8 @@ public class EducationProfileResolver {
         }
 
         if (!ALLOWED_TERMS.contains(termsPerYear)) {
-            throw new IllegalArgumentException("Invalid termsPerYear: " + termsPerYear + ". Allowed values: " + ALLOWED_TERMS);
+            throw new IllegalArgumentException(
+                    "Invalid termsPerYear: " + termsPerYear + ". Allowed values: " + ALLOWED_TERMS);
         }
 
         BigDecimal entranceFee = BigDecimal.ZERO;
@@ -361,13 +367,14 @@ public class EducationProfileResolver {
      * Calculates payment months evenly spaced throughout the year starting from
      * startMonth.
      *
-     * @param startMonth 1-12
+     * @param startMonth   1-12
      * @param termsPerYear Whitelisted term count in {1, 2, 3, 4, 6, 12}
      * @return List of 1-based months
      */
     public static List<Integer> calculatePaymentMonths(int startMonth, int termsPerYear) {
         if (!ALLOWED_TERMS.contains(termsPerYear)) {
-            throw new IllegalArgumentException("Invalid termsPerYear: " + termsPerYear + ". Allowed values: " + ALLOWED_TERMS);
+            throw new IllegalArgumentException(
+                    "Invalid termsPerYear: " + termsPerYear + ". Allowed values: " + ALLOWED_TERMS);
         }
         if (startMonth < 1 || startMonth > 12) {
             throw new IllegalArgumentException("Invalid startMonth: " + startMonth + ". Must be between 1 and 12.");
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
index a3e664f5..27f13e6b 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImplTest.java
@@ -196,7 +196,7 @@ class AssetSimulationEngineImplTest {
     private AssetSimulationEngineImpl engine;
 
     @BeforeEach
-    @SuppressWarnings({"unused", "null"})
+    @SuppressWarnings({ "unused", "null" })
     void setUp() {
         incomeExpenseCalculator = new IncomeExpenseCalculator(bankTransactionRepository,
                 creditCardTransactionRepository, transactionLinkRepository,
@@ -406,13 +406,15 @@ class AssetSimulationEngineImplTest {
         // default
         DefinedContributionAccountSnapshot dcSnapshot = new DefinedContributionAccountSnapshot(LocalDate.now(),
                 DefinedContributionType.CORPORATE_DC, 0L, 0L, 0L, 0L, 0L);
-        DefinedContributionFundSnapshot dcFund = new DefinedContributionFundSnapshot("Type", "Fund1", "DC_FUND_1", 0.0, 0.0, 0.0, 0L,
+        DefinedContributionFundSnapshot dcFund = new DefinedContributionFundSnapshot("Type", "Fund1", "DC_FUND_1", 0.0,
+                0.0, 0.0, 0L,
                 1_000_000L, 0L);
         dcSnapshot.addFund(dcFund);
 
         DefinedContributionAccountSnapshot idecoSnapshot = new DefinedContributionAccountSnapshot(LocalDate.now(),
                 DefinedContributionType.IDECO, 0L, 0L, 0L, 0L, 0L);
-        DefinedContributionFundSnapshot idecoFund = new DefinedContributionFundSnapshot("Type", "Fund2", "IDECO_FUND_1", 0.0, 0.0, 0.0,
+        DefinedContributionFundSnapshot idecoFund = new DefinedContributionFundSnapshot("Type", "Fund2", "IDECO_FUND_1",
+                0.0, 0.0, 0.0,
                 0L, 2_000_000L, 0L);
         idecoSnapshot.addFund(idecoFund);
 
@@ -575,6 +577,7 @@ class AssetSimulationEngineImplTest {
         NisaSnapshotRecord nisaSnapshot = Mockito.mock(NisaSnapshotRecord.class);
         lenient().when(nisaSnapshot.getRecordDate()).thenReturn(LocalDate.now());
         lenient().when(nisaSnapshot.getAccountType()).thenReturn("NISA (成長)預り");
+        lenient().when(nisaSnapshot.getFundCode()).thenReturn("03311187");
         lenient().when(nisaSnapshot.getFundName()).thenReturn("Fund1");
         lenient().when(nisaSnapshot.getTotalPrincipal()).thenReturn(11_900_000L);
         lenient().when(nisaSnapshot.getCurrentMarketValue()).thenReturn(11_900_000L);
@@ -617,6 +620,7 @@ class AssetSimulationEngineImplTest {
         NisaSnapshotRecord nisaSnapshot = org.mockito.Mockito.mock(NisaSnapshotRecord.class);
         lenient().when(nisaSnapshot.getRecordDate()).thenReturn(java.time.LocalDate.now());
         lenient().when(nisaSnapshot.getAccountType()).thenReturn("NISA (つみたて)預り");
+        lenient().when(nisaSnapshot.getFundCode()).thenReturn("03311187");
         lenient().when(nisaSnapshot.getFundName()).thenReturn("Fund1");
         lenient().when(nisaSnapshot.getTotalPrincipal()).thenReturn(18_000_000L);
         lenient().when(nisaSnapshot.getCurrentMarketValue()).thenReturn(18_000_000L);
@@ -625,7 +629,8 @@ class AssetSimulationEngineImplTest {
         when(nisaRepository.findByRecordDate(any())).thenReturn(Arrays.asList(nisaSnapshot));
 
         // NISA setting: 100k monthly (1.2M annually), 0% return for simple math
-        NisaContributionSetting setting = new NisaContributionSetting(LocalDate.now(), "NISA (つみたて)預り", "03311187", "Fund1",
+        NisaContributionSetting setting = new NisaContributionSetting(LocalDate.now(), "NISA (つみたて)預り", "03311187",
+                "Fund1",
                 "Monthly", 100_000L,
                 100_000L);
         setting.updateExpectedReturnRate(0.0);
@@ -676,6 +681,7 @@ class AssetSimulationEngineImplTest {
         NisaSnapshotRecord nisaSnapshot = Mockito.mock(NisaSnapshotRecord.class);
         lenient().when(nisaSnapshot.getRecordDate()).thenReturn(java.time.LocalDate.now());
         lenient().when(nisaSnapshot.getAccountType()).thenReturn("NISA (成長)預り");
+        lenient().when(nisaSnapshot.getFundCode()).thenReturn("03311187");
         lenient().when(nisaSnapshot.getFundName()).thenReturn("Fund1");
         lenient().when(nisaSnapshot.getTotalPrincipal()).thenReturn(12_000_000L);
         lenient().when(nisaSnapshot.getCurrentMarketValue()).thenReturn(12_000_000L);
@@ -684,7 +690,8 @@ class AssetSimulationEngineImplTest {
         when(nisaRepository.findByRecordDate(any())).thenReturn(Arrays.asList(nisaSnapshot));
 
         // NISA setting: 200k monthly (2.4M annually), 0% return for simple math
-        NisaContributionSetting setting = new NisaContributionSetting(LocalDate.now(), "NISA (成長)預り", "03311187", "Fund1",
+        NisaContributionSetting setting = new NisaContributionSetting(LocalDate.now(), "NISA (成長)預り", "03311187",
+                "Fund1",
                 "Monthly", 200_000L,
                 200_000L);
         setting.updateExpectedReturnRate(0.0);
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
index f6afeb1c..7029b291 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
@@ -744,4 +744,56 @@ class EducationProfileResolverTest {
         assertThat(stage.getDurationMonths()).isEqualTo(36);
         assertThat(stage.hasStage2()).isFalse();
     }
+
+    @Test
+    @DisplayName("Should ignore manualLivingCost and calculate zero living cost when isAwayFromHome is false")
+    void shouldIgnoreManualLivingCostWhenIsAwayFromHomeIsFalse() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.US)
+                .isAwayFromHome(false)
+                .manualLivingCost(new BigDecimal("350000"))
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData stage = profile.getStage(EducationStage.UNIVERSITY);
+
+        assertThat(stage).isNotNull();
+        assertThat(stage.getMonthlyLivingCost()).isEqualByComparingTo(BigDecimal.ZERO);
+    }
+
+    @Test
+    @DisplayName("Should adopt manualLivingCost when isAwayFromHome is true")
+    void shouldAdoptManualLivingCostWhenIsAwayFromHomeIsTrue() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.US)
+                .isAwayFromHome(true)
+                .manualLivingCost(new BigDecimal("350000"))
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData stage = profile.getStage(EducationStage.UNIVERSITY);
+
+        assertThat(stage).isNotNull();
+        assertThat(stage.getMonthlyLivingCost()).isEqualByComparingTo(new BigDecimal("350000"));
+    }
+
+    @Test
+    @DisplayName("Should use default university living cost when isAwayFromHome is true and manualLivingCost is null")
+    void shouldUseDefaultLivingCostWhenIsAwayFromHomeIsTrueAndManualLivingCostIsNull() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.US)
+                .isAwayFromHome(true)
+                .manualLivingCost(null)
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData stage = profile.getStage(EducationStage.UNIVERSITY);
+
+        assertThat(stage).isNotNull();
+        BigDecimal expectedMonthly = defaultConfig.getUniversity().getUsLivingCost();
+        assertThat(stage.getMonthlyLivingCost()).isEqualByComparingTo(expectedMonthly);
+    }
 }
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                                           10020  12.08G   9.18    10:47:33  