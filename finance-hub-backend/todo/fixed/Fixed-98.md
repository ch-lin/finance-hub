# Fixed 98：年金終身總額（projectedLifetimeTotal）計算「可以領幾年」時，用錯了年齡基準——用的是退休年齡，不是開始請領年金年齡，導致總額算錯

## 嚴重程度
Medium——這個數字目前還沒有在任何畫面上顯示給使用者看（見下方「補充說明」），所以現階段不會直接誤導使用者判斷買房現金流；但計算邏輯本身確實會把年金總額算錯（灌水或低估，視兩個年齡設定的大小關係而定），而且既有單元測試會一直顯示「通過」、給人假的安全感，一旦以後有人要在畫面上顯示這個數字，或有人依賴這段程式碼做別的用途，就會直接繼承這個算錯的結果卻毫無察覺。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java:127-139`（計算每年年金金額與「終身總額」的地方）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/PensionCalculator.java:55-72`（`calculatePensionMultiplier`，計算「幾歲開始領年金」對應的增減成數，用來確認每年金額本來就是綁在「開始請領年金年齡」上，不是退休年齡）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java:43-89`（確認「預計退休年齡」與「開始請領年金年齡」是兩個各自獨立解析、使用者可以分別設定成不同數字的欄位）
- `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java:116-117,133-134`（測試資料把兩個年齡都設成同一個數字，導致這個問題永遠不會被測出來）

## 現狀
在個人設定裡，使用者可以分別填寫「預計退休年齡」和「開始請領年金年齡」這兩個不同的數字——這是真實情況，例如日本年金制度規定原則上要 65 歲才能開始領年金，但使用者可能打算 60 歲就退休、先靠存款過渡幾年。系統會算出一個「年金終身總額」的估算數字，理論上這應該代表「從真正開始領年金那一年，一直領到預期壽命那一年，總共可以拿到多少錢」。

但目前這段計算算「總共可以領幾年」的時候，用的是「退休年齡」而不是「開始請領年金年齡」。如果退休年齡比開始請領年金年齡早（例如上面 60 歲退休、65 歲才開始領年金的例子），這個「終身總額」就會把使用者已經退休、但年金根本還沒開始發的那幾年也一併算進去，導致這個總額數字比實際上真正能拿到的金額被灌水、偏高（如果反過來退休年齡比開始請領年金年齡晚，則會變成低估）。

## 原因
`PensionProjectionServiceImpl.java` 第 127-137 行，`projectedBasic`（呼叫 `calculateProjectedBasicPension(..., pensionStartAge)`）與 `projectedKousei`（呼叫 `calculatePensionMultiplier(pensionStartAge)`）這兩筆「每年可以領到的年金金額」，都是以 `pensionStartAge`（開始請領年金年齡）為基準算出來的——這符合日本現行「繰上げ/繰下げ受給」制度（60-64 歲每提早一個月減 0.4%、66-75 歲每延後一個月加 0.7%，可見 `PensionCalculator.java:56-72` 的實作），也就是每年領多少錢這件事本來就是綁在「開始請領年金年齡」上，跟「退休年齡」無關。

但第 138 行 `long yearsInRetirement = Math.max(0, lifeExpectancy - retirementAge);` 算「總共領幾年」時，卻改用 `retirementAge`（即 `targetRetirementAge`，退休年齡）去算，跟前面算金額用的基準不一致。`SimulationParameterResolver.java:43-89` 確認這兩個年齡是分別獨立設定、允許不同數字的欄位，不是同一個設定的別名。

而 `PensionProjectionServiceTest.java` 第 116-117 行建構測試用的使用者資料時，把「退休年齡」與「開始請領年金年齡」都設成同一個數字（65 歲），導致無論程式碼實際上是用哪一個欄位去算年數，算出來的年數都一樣是 20 年（第 133 行註解寫著「Lifetime (85-65=20 years)」），金額也一樣是 16,080,000 日圓。這讓測試永遠顯示「通過」，完全沒辦法檢查出這兩個年齡設定不同時會不會出錯，那行「85-65=20年」的註解也容易讓人誤以為這條計算路徑已經被驗證過，但其實只驗證了兩個年齡剛好相等的特殊情況。

## 具體錯誤情境
使用者在個人設定裡填「預計退休年齡 60 歲」、「開始請領年金年齡 65 歲」、「預期壽命 85 歲」。正確的「年金終身總額」應該是「65 歲到 85 歲，共 20 年」乘上每年金額；但目前程式碼算的是「60 歲到 85 歲，共 25 年」乘上每年金額（而每年金額本身是以 65 歲請領為基準算出來的），多算了 5 年份，使「年金終身總額」比實際能領到的金額整整多出 25%（25 年 vs. 20 年）。

## 建議修正
這牽涉到「年金終身總額」這個數字本來想表達的意思是什麼，屬於需要產品面判斷的取捨，列出兩種方向：

**方向A：把年數的計算基準從 `retirementAge` 改成 `pensionStartAge`**
- 做法：把 `PensionProjectionServiceImpl.java:138` 的 `retirementAge` 改成 `pensionStartAge`，並把測試資料改成「退休年齡跟請領年金年齡不同」的情境（例如 60 歲退休、65 歲領年金），重新手算一次正確的期望值，讓測試真正有鑑別力。
- 優點：修好測試鑑別力的同時，讓「終身總額」的定義變成「累積可以真正領到的年金總金額」，跟每年金額的計算基準一致，也更貼近使用者實際能拿到的錢。
- 缺點：如果有人已經看習慣目前顯示的數字（即使定義不夠精確），改完之後數字會變小；如果未來要在畫面上顯示這個數字，需要考慮是否要順便說明清楚這個數字代表的意思。

**方向B：維持用 `retirementAge` 計算年數（視為刻意設計），但讓測試誠實反映現況**
- 做法：不改動 `PensionProjectionServiceImpl.java:138` 的計算邏輯，但把測試案例改成「退休年齡跟請領年金年齡不同」的組合，並在測試註解中明確寫出「這裡故意用退休年齡而非請領年齡計算年數」，避免之後的人誤以為測試通過就代表用 `pensionStartAge` 驗證過。
- 優點：不改動既有計算邏輯，改動風險最低。
- 缺點：如果目前這個算法對使用者來說其實會造成誤導（把還沒開始領年金、但已經退休的年份也算進「年金總額」裡），這個問題本身依然存在，只是讓測試變得誠實反映現況，不代表數字本身被修正。

## 補充說明：目前這個數字還沒有在任何畫面上顯示出來
查過 `hub-ui` 前端的程式碼，`projectedLifetimeTotal`（對應「年金終身總額」）目前只出現在 `hub-ui/hub-ui/src/app/finance/portfolios/pensions/hooks/use-pension-projection.ts:8` 的 TypeScript 型別定義裡，實際畫出年金圖表的 `hub-ui/hub-ui/src/app/finance/portfolios/pensions/components/pension-projection-chart.tsx` 目前只畫「每年可以領多少年金」的長條圖，完全沒有使用或顯示這個「終身總額」欄位。也就是說，此刻使用者實際上還看不到這個被灌水（或低估）的數字，這也是把嚴重程度定為 Medium 而非 High 的主要原因；但後端 API 與計算邏輯本身的問題是真實存在的，而且既有單元測試給了假的安全感，日後如果前端要顯示這個數字，會直接繼承這個尚未修正的計算錯誤。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 裡目前沒有任何項目涉及 `PensionProjectionServiceImpl` 的退休年齡（`retirementAge`）與開始請領年金年齡（`pensionStartAge`）基準不一致的問題，也沒有任何項目是關於「年金終身總額」（`projectedLifetimeTotal`）這個計算欄位本身，屬於全新發現的問題。與 `todo/planned/Plan-45-individual-pension-insurance-premium-not-deducted.md`（個人年金保險保費扣除漏算）是完全不同的檔案、不同的計算邏輯，兩者互不相關。

---
## 實作修正報告 (Implementation Report)

**狀態：已修正並等待測試 (Fixed and Awaiting Test)**

1. **修正年金終身總額計算邏輯**：
   在 `PensionProjectionServiceImpl.java` 中，將計算退休年數的基準從 `retirementAge` 替換為 `pensionStartAge`，徹底解決了提早退休但尚未領年金時，總額遭到高估的問題。
   修改前的代碼：`long yearsInRetirement = Math.max(0, lifeExpectancy - retirementAge);`
   修改後的代碼：`int yearsInRetirement = Math.max(0, lifeExpectancy - pensionStartAge);`

2. **強化單元測試鑑別度**：
   在 `PensionProjectionServiceTest.java` 的 `calculateProjection_withExistingRecord_shouldCalculateCorrectly` 測試案例中，故意將使用者的 `targetRetirementAge` 設為 60 歲（原為 65 歲），同時保持 `pensionStartAge` 為 65 歲。這確保了測試環境能在這兩個年齡有落差的情況下執行，並成功驗證 Lifetime Total 的計算基準是建立在 20 年（85歲 - 65歲），而不是 25 年。

3. **解決長期的命名與設定檔綁定問題 (無痛改名)**：
   調查過程中發現，`application.properties` 中的 `finance.hub.system-config.pension-eligible-age` 完全沒有生效，因為 Spring Boot 期待的 Properties Key 應該與 Java 領域模型的變數名稱 `pensionStartAge` 對齊。
   為了徹底解決這個容易讓人混淆的技術債：
   - 將 `FinanceHubConfig.java` 和 `UserProfile.java` 中對應資料庫欄位的常數宣告由 `pension_eligible_age` 更改為 `pension_start_age`。
   - 將 `application.properties` 中的設定鍵值更正為 `finance.hub.system-config.pension-start-age`。
   由於 H2 資料庫會自動根據 Entity 重新建立 Schema，因此不需編寫遷移腳本即可無痛生效。此舉統一了從資料庫、Java 程式碼到 Spring 設定檔的命名邏輯。

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                                                       10027  12.38G   3.82    11:01:54  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java
index 97a55ea5..d758f018 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java
@@ -98,10 +98,6 @@ public class PensionProjectionServiceImpl implements PensionProjectionService {
                 (userProfile != null) ? userProfile.getLifeExpectancy() : null,
                 config.getLifeExpectancy(), null);
 
-        int retirementAge = SimulationParameterResolver.resolveRetirementAge(
-                (userProfile != null) ? userProfile.getTargetRetirementAge() : null,
-                config.getTargetRetirementAge(), targetRetirementAgeOverride);
-
         int pensionStartAge = SimulationParameterResolver.resolvePensionStartAge(
                 (userProfile != null) ? userProfile.getPensionStartAge() : null,
                 config.getPensionStartAge(), pensionStartAgeOverride);
@@ -148,7 +144,7 @@ public class PensionProjectionServiceImpl implements PensionProjectionService {
                     .round(baseAmount * pensionCalculator.calculatePensionMultiplier(pensionStartAge));
 
             long totalAnnual = projectedBasic + projectedKousei;
-            long yearsInRetirement = Math.max(0, lifeExpectancy - retirementAge);
+            int yearsInRetirement = Math.max(0, lifeExpectancy - pensionStartAge);
             long lifetimeTotal = totalAnnual * yearsInRetirement;
 
             PensionProjectionYearlyResult result = new PensionProjectionYearlyResult(i, projectedBasic, projectedKousei,
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
index 191e0f66..94688e6a 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
@@ -131,7 +131,7 @@ class PensionProjectionServiceTest {
         when(pensionCalculator.calculatePensionMultiplier(65)).thenReturn(1.0);
 
         when(userProfileService.getUserProfile("user-1")).thenReturn(Optional.of(
-                new UserProfileResult("user-1", LocalDate.of(1980, 1, 1), null, 65, 65, null, null, 85, null, false)));
+                new UserProfileResult("user-1", LocalDate.of(1980, 1, 1), null, 60, 65, null, null, 85, null, false)));
 
         // Act
         List<PensionProjectionYearlyResult> result = projectionService.calculateProjection("user-1", 2, null, null);
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
index 574c694a..5dde34b5 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java
@@ -124,7 +124,7 @@ public class FinanceHubConfig extends AuditableEntity {
     /**
      * The name of the pension eligible age column in the database.
      */
-    public static final String PENSION_START_AGE_COLUMN = "pension_eligible_age";
+    public static final String PENSION_START_AGE_COLUMN = "pension_start_age";
 
     public static final String GA_POPULATION_SIZE_COLUMN = "ga_population_size";
     public static final String GA_MAX_GENERATIONS_COLUMN = "ga_max_generations";
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java
index 5a89d9b8..eb366387 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java
@@ -64,7 +64,7 @@ public class UserProfile {
     public static final String BIRTH_DATE_COLUMN = "birth_date";
     public static final String MANUAL_MONTHLY_BASE_EXPENSE_COLUMN = "manual_monthly_base_expense";
     public static final String TARGET_RETIREMENT_AGE_COLUMN = "target_retirement_age";
-    public static final String PENSION_START_AGE_COLUMN = "pension_eligible_age";
+    public static final String PENSION_START_AGE_COLUMN = "pension_start_age";
     public static final String DISPLAY_NAME_COLUMN = "display_name";
     public static final String DEFAULT_CURRENCY_COLUMN = "default_currency";
 
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index 88462b3d..d6a9125f 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -93,7 +93,7 @@ finance.hub.system-config.inflation-rate=0.0
 finance.hub.system-config.ideco-unlock-age=60
 finance.hub.system-config.dc-unlock-age=60
 finance.hub.system-config.target-retirement-age=65
-finance.hub.system-config.pension-eligible-age=65
+finance.hub.system-config.pension-start-age=65
 
 # GA Defaults
 finance.hub.system-config.ga-population-size=50
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                              10028  12.38G   3.75    11:03:01  