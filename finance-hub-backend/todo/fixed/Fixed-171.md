# [FIXED] 171：PensionProjectionService.calculateProjection()的targetRetirementAgeOverride參數從DTO、Controller到前端都存在完整的傳遞鏈路，但後端實作完全沒有使用這個參數，使用者在畫面上調整這個設定不會對年金試算結果產生任何效果

## 這份issue怎麼被發現的
這是這次Source Code Review審查年金服務層時發現的。

## 相關檔案
- [PensionProjectionServiceImpl.java:71-72](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java)（`calculateProjection`方法簽章，`targetRetirementAgeOverride`是方法參數——已重新查證確認，整個方法主體裡`targetRetirementAgeOverride`這個識別字只在方法簽章出現過一次）
- [PensionProjectionService.java:46-47](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionService.java)（interface方法簽章，同樣宣告了這個參數）
- [PensionRecordController.java:81-86](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java)（`GET /assets/pensions/projection`，`@RequestParam(required = false) Integer targetRetirementAgeOverride`，從URL query string接收並原樣傳給service）
- 對照（前端已經接好這條傳遞鏈）：[use-pension-projection.ts:17-24](file://hub-ui/hub-ui/src/app/finance/portfolios/pensions/hooks/use-pension-projection.ts)（`usePensionProjection`接受`targetRetirementAgeOverride`參數並組進URL query string）、[pension-projection-chart.tsx:19-28](file://hub-ui/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx)（`PensionProjectionChartProps`宣告了這個prop並往下傳）

## 現狀
從前端元件props、到hook組URL、到Controller的`@RequestParam`、到Service interface方法簽章，`targetRetirementAgeOverride`這個參數在整條鏈路上都正確地被宣告、傳遞——唯獨在真正做計算的`PensionProjectionServiceImpl.calculateProjection()`方法主體裡，完全沒有被讀取或用在任何計算邏輯上。對照同一個方法裡`pensionStartAgeOverride`這個姐妹參數，有被正確地傳進`SimulationParameterResolver.resolvePensionStartAge(...)`參與計算；`targetRetirementAgeOverride`沒有對應的`resolveRetirementAge(...)`呼叫。

比對[Fixed-98](file://finance-hub/finance-hub-backend/todo/fixed/Fixed-98.md)跟`Fixed-98-01`，這兩份文件修正的是同一個方法裡「`yearsInRetirement`該用`retirementAge`還是`pensionStartAge`去算」這個問題，修正後的程式碼（已確認目前程式碼已採用修正後版本：`int yearsInRetirement = Math.max(0, lifeExpectancy - pensionStartAge);`）結果是整個方法裡原本唯一一處會讀取`retirementAge`（來自`resolveRetirementAge(...)`解析出來的本地變數）的地方被移除了，但方法簽章上的`targetRetirementAgeOverride`參數本身留了下來，變成一個完全死掉、從未被讀取的參數——這代表Fixed-98這份修正，順手把`targetRetirementAgeOverride`這個輸入參數變成了死碼，但沒有一併清理掉它、或是沒有意識到前端其實還在傳這個值期待它產生效果。

## 為何是問題
使用者在年金試算頁面如果調整「退休年齡覆寫」這個設定，期待看到年金試算結果反映「如果我提早退休、年金請領年齡不變」這種情境，但`calculateProjectedBasicPension`/`calculateYearlyKouseiBenefitIncrement`/`calculatePensionMultiplier`這三個真正算金額的呼叫，用的全部都是`pensionStartAge`，跟`targetRetirementAgeOverride`完全無關——使用者調整這個設定不會對畫面上顯示的任何年金金額產生任何效果，而且目前沒有任何錯誤訊息或警示提示這個參數已經失效。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：確認targetRetirementAgeOverride在年金試算情境下原本該影響什麼計算，補回對應邏輯**
- 做法：需要先確認「退休年齡」在年金試算的語意上原本該扮演什麼角色——例如如果使用者比請領年金年齡更早退休，在這段「退休但還沒開始領年金」的空窗期，使用者可能期待看到某種跟現有試算不同的呈現方式（例如標示這段空窗期沒有年金收入）。
- 優點：讓這個已經存在、前端也接好傳遞鏈路的參數真正產生效果。
- 缺點：需要先定義清楚這個參數在這個情境下該如何影響計算結果，這點需要產品面判斷。

**方向B：如果這個參數在年金試算情境下本來就不該有效果（例如年金金額計算本質上只跟請領年齡有關、跟退休年齡無關），直接把這個死參數從方法簽章、Controller、前端傳遞鏈路整條移除**
- 做法：清除`PensionProjectionService`/`PensionProjectionServiceImpl`/`PensionRecordController`裡的`targetRetirementAgeOverride`、以及前端對應的prop跟query string組裝邏輯。
- 優點：移除一個會誤導使用者「調整這個設定會改變結果」的死接口，程式碼也更誠實地反映「年金試算只吃請領年齡，不吃退休年齡」這個事實。
- 缺點：需要確認移除後，`AssetSimulationFitnessEvaluator`等其他模組裡同名但實際有效的`targetRetirementAgeOverride`不會被誤刪——這是另一個完全獨立的參數（`SimulationOverrides.targetRetirementAgeOverride`，用在`AssetSimulationEngineImpl`裡確實有效的退休年齡邏輯），只是同名，不要混淆。

## 修正報告（採用方向 B：整條鏈路移除死參數）

1. **修正邏輯**：
   經確認，年金專區的投影功能（Pension Projection）本質上是基於使用者現有之年金事實紀錄，依不同工作增加年數（+0年、+1年、……、+40年）一路推算未來每年可領取之法定年金金額（長條圖各柱狀）。其金額計算完全取決於工作累計年數與「年金開始請領年齡（`pensionStartAge`）」，與退休年齡無關。
   因此，將 Fixed-98 遺留下來的死參數 `targetRetirementAgeOverride` 從後端 Service、Controller 及前端傳遞鏈路整條徹底移除：
   - **後端程式碼**：
     - [`PensionProjectionService.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionService.java#L46)：從介面宣告中移除 `Integer targetRetirementAgeOverride`。
     - [`PensionProjectionServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java#L71)：從方法實作簽章中移除該參數。
     - [`PensionRecordController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java#L79)：從 `@GetMapping("/projection")` 端點移除 `@RequestParam(required = false) Integer targetRetirementAgeOverride`。
   - **前端程式碼**：
     - [`use-pension-projection.ts`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/pensions/hooks/use-pension-projection.ts#L17)：移除 hook 參數宣告及 query string 組裝邏輯。
     - [`pension-projection-chart.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx#L19)：移除 `PensionProjectionChartProps` 中的 prop 定義與往下傳遞，並清理未使用的 `ReferenceLine` 引用。
   - **單元測試**：
     - [`PensionProjectionServiceTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java#L137)：更新兩處測試呼叫簽章（從 4 參數改為 3 參數）。
     - [`PensionRecordControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordControllerTest.java#L109)：更新 mock 驗證簽章。

2. **隔離確認**：
   資產模擬器（Sandbox / GA / Grid）中的 `SimulationOverrides.targetRetirementAgeOverride` 為獨立運作的有效參數，完全未受影響，繼續在資產模擬引擎中正常運行。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                              10025  13.21G   6.24    20:06:38  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionService.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionService.java
index affb2e67..70c2aa30 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionService.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionService.java
@@ -44,6 +44,6 @@ public interface PensionProjectionService {
      *         for 0 up to maxAdditionalYears worked.
      */
     List<PensionProjectionYearlyResult> calculateProjection(String userId, int maxAdditionalYears,
-            Integer targetRetirementAgeOverride, Integer pensionStartAgeOverride);
+            Integer pensionStartAgeOverride);
 
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java
index d758f018..89183249 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java
@@ -69,7 +69,7 @@ public class PensionProjectionServiceImpl implements PensionProjectionService {
     @Override
     @Transactional(readOnly = true)
     public List<PensionProjectionYearlyResult> calculateProjection(String userId, int maxAdditionalYears,
-            Integer targetRetirementAgeOverride, Integer pensionStartAgeOverride) {
+            Integer pensionStartAgeOverride) {
         String activePensionConfigName = pensionRuleConfigService.getAllConfigs().getEnabledConfigName();
         PensionRuleConfigResult pensionRuleConfig = pensionRuleConfigService.getConfig(activePensionConfigName);
         PensionConfigDto pensionConfigDto = new PensionConfigDto(
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
index 94688e6a..bb1a3f15 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
@@ -134,7 +134,7 @@ class PensionProjectionServiceTest {
                 new UserProfileResult("user-1", LocalDate.of(1980, 1, 1), null, 60, 65, null, null, 85, null, false)));
 
         // Act
-        List<PensionProjectionYearlyResult> result = projectionService.calculateProjection("user-1", 2, null, null);
+        List<PensionProjectionYearlyResult> result = projectionService.calculateProjection("user-1", 2, null);
 
         // Assert
         assertThat(result).hasSize(3);
@@ -215,7 +215,7 @@ class PensionProjectionServiceTest {
         when(userProfileService.getUserProfile("user-1")).thenReturn(Optional.empty());
 
         // Act
-        List<PensionProjectionYearlyResult> result = projectionService.calculateProjection("user-1", 2, null, null);
+        List<PensionProjectionYearlyResult> result = projectionService.calculateProjection("user-1", 2, null);
 
         // Assert
         assertThat(result).hasSize(3);
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java
index d6d19256..eb332139 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java
@@ -79,11 +79,10 @@ public class PensionRecordController {
     public ResponseEntity<List<PensionProjectionYearlyResponse>> getProjection(
             @AuthenticationPrincipal Jwt jwt,
             @RequestParam(name = "maxYears", defaultValue = "40") int maxYears,
-            @RequestParam(required = false) Integer targetRetirementAgeOverride,
             @RequestParam(required = false) Integer pensionStartAgeOverride) {
         String userId = jwt.getSubject();
         List<PensionProjectionYearlyResponse> response = pensionProjectionService.calculateProjection(userId, maxYears,
-                targetRetirementAgeOverride, pensionStartAgeOverride).stream()
+                pensionStartAgeOverride).stream()
                 .map(result -> pensionRecordMapper.toMaskedResponse(result, SecurityUtil.getCurrentUserId()))
                 .collect(Collectors.toList());
         return ResponseEntity.ok(response);
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordControllerTest.java
index 1b03c7be..f6ea2c6f 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordControllerTest.java
@@ -87,13 +87,14 @@ class PensionRecordControllerTest {
         mockMvc = MockMvcBuilders.standaloneSetup(controller)
                 .setCustomArgumentResolvers(jwtArgumentResolver)
                 .build();
-        
-        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
+
+        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
+                .withTokenValue("token")
                 .header("alg", "none")
                 .claim("sub", "user-1")
                 .build();
-        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuth = 
-                new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
+        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
+                jwt);
         org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(jwtAuth);
     }
 
@@ -106,7 +107,7 @@ class PensionRecordControllerTest {
         PensionProjectionYearlyResponse response = new PensionProjectionYearlyResponse(0, 795000L, 1200000L, 1995000L,
                 39900000L);
 
-        when(pensionProjectionService.calculateProjection(eq("user-1"), eq(40), eq(null), eq(null)))
+        when(pensionProjectionService.calculateProjection(eq("user-1"), eq(40), eq(null)))
                 .thenReturn(results);
         when(pensionRecordMapper.toMaskedResponse(eq(result), org.mockito.ArgumentMatchers.any())).thenReturn(response);
 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                   10026  13.09G   5.13    20:08:43  

  edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                                                  10027  12.95G   4.79    20:09:36  
diff --git a/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx b/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx
index 6113c62..4762c60 100644
--- a/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx
+++ b/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx
@@ -1,6 +1,6 @@
 "use client"
 
-import { Bar, BarChart, CartesianGrid, XAxis, YAxis, ReferenceLine } from "recharts"
+import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"
 import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
 import { ChartConfig, ChartContainer, ChartTooltip, ChartTooltipContent, ChartLegend, ChartLegendContent } from "@/components/ui/chart"
 import { usePensionProjection } from "../hooks/use-pension-projection"
@@ -17,15 +17,13 @@ const chartConfig = {
 } satisfies ChartConfig
 
 interface PensionProjectionChartProps {
-  targetRetirementAgeOverride?: number;
   pensionStartAgeOverride?: number;
 }
 
 export function PensionProjectionChart({ 
-  targetRetirementAgeOverride, 
   pensionStartAgeOverride 
 }: PensionProjectionChartProps) {
-  const { projection, isLoading, isError } = usePensionProjection(40, targetRetirementAgeOverride, pensionStartAgeOverride)
+  const { projection, isLoading, isError } = usePensionProjection(40, pensionStartAgeOverride)
 
   if (isError) return null;
   if (isLoading) return <div className="animate-pulse h-64 bg-muted rounded-xl" />;
diff --git a/hub-ui/src/app/finance/portfolios/pensions/hooks/use-pension-projection.ts b/hub-ui/src/app/finance/portfolios/pensions/hooks/use-pension-projection.ts
index 666e778..d76ac0b 100644
--- a/hub-ui/src/app/finance/portfolios/pensions/hooks/use-pension-projection.ts
+++ b/hub-ui/src/app/finance/portfolios/pensions/hooks/use-pension-projection.ts
@@ -16,11 +16,9 @@ const fetcher = async (url: string) => {
 
 export function usePensionProjection(
   maxYears: number = 40, 
-  targetRetirementAgeOverride?: number, 
   pensionStartAgeOverride?: number
 ) {
   let url = `/api/finance/assets/pensions/projection?maxYears=${maxYears}`;
-  if (targetRetirementAgeOverride !== undefined) url += `&targetRetirementAgeOverride=${targetRetirementAgeOverride}`;
   if (pensionStartAgeOverride !== undefined) url += `&pensionStartAgeOverride=${pensionStartAgeOverride}`;
 
   const { data, error, isLoading, mutate } = useSWR<PensionProjectionYearlyDto[]>(
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                                                                                                       10028  12.97G   4.70    20:09:51  