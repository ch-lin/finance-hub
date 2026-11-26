# Fixed 111：全域教育設定管理API完全沒有防呆驗證，跟個人小孩覆寫欄位的防呆標準不一致

## 嚴重程度
Medium-High（觸發機率低，需要手動去改全域設定才會發生，但一旦發生，影響範圍是所有沒有個人覆寫的小孩，不只影響單一使用者自己）

## 相關檔案
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java`（`tuition`、`duration`、`startMonth`、`termsPerYear`、`entranceFee`這幾個欄位完全沒有任何`@Min`/`@Max`/白名單驗證註解）
- 對照組：`finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java:51-68`（個人小孩的覆寫欄位，已經正確加上`@Min(1) @Max(12)`（開學月份）、`@Min(1) @Max(240)`（就讀月數）、`@AssertTrue`白名單檢查（收費期數）這幾層防呆）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java`（`calculateStandardStartYearMonth`，若`startMonth`超出1-12範圍，`YearMonth.of(year, month)`會直接拋出`DateTimeException`）
- `finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java`（目前只攔截`IllegalArgumentException`／`MethodArgumentTypeMismatchException`這兩種例外轉成400，沒有攔截`DateTimeException`）

## 現狀
`Plan-01.md`的Phase 3-A明確把「後端API作為核心數據完整性與系統安全的終極防線，杜絕非法值在計算層引發未捕獲的500內部伺服器錯誤」列為驗收標準之一，並在個人小孩的覆寫欄位（`StageOverrideRequest`）上正確做到了這件事。但同一套防呆標準，並沒有同時套用到全域教育設定的管理API（`TrackConfigRequest`）——這個全域設定，正是每個小孩沒有個人覆寫時，實際會拿去計算的預設值來源。

## 為何是問題
如果透過`PATCH /configs/education/{name}`這個端點，對某個全域學制軌道設定一個不合理的值（例如`startMonth=15`），系統不會擋下這個請求，會直接存進資料庫。之後任何一個小孩選到這個軌道、且沒有個人覆寫這個欄位時，系統試算時會呼叫`YearMonth.of(year, 15)`，直接拋出`DateTimeException`；而目前的全域例外處理機制沒有攔截這種例外類型，會讓這個錯誤以未經處理的500錯誤形式往外冒出，而且影響範圍不只是設定這個錯誤值的人，是**所有**選到這個軌道、沒有個人覆寫的小孩，試算或模擬都會連帶失敗。

## 具體錯誤情境
不小心把某個全域學制軌道的`startMonth`設定成15（或`termsPerYear`設定成5這種不在白名單裡的數字），存檔時系統完全不會提出警告，一切看起来正常。之後任何一個小孩試算教育費用預估圖表，或整個模擬引擎在計算年度支出時，只要用到這個軌道，就會直接拋出未處理的例外。

## 建議修正
把`StageOverrideRequest`上已經在用的同一組驗證註解（`@Min(1) @Max(12)`用於`startMonth`、`@Min(1) @Max(240)`用於`duration`、白名單檢查用於`termsPerYear`），套用到`TrackConfigRequest`上；並確認`UpdateEducationConfigRequest`／`CreateEducationConfigRequest`裡，每個階段的巢狀請求物件上都有加`@Valid`，確保這層驗證真的會被觸發。這是把既有、已經驗證過的防呆機制，套用到目前漏掉的另一個位置，不需要重新設計。

---

## 實作清單（Checklist）

1. **`TrackConfigRequest.java` 補齊防呆驗證註解**：
   - [x] [TrackConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java)
     - `tuition`: `@PositiveOrZero`
     - `duration`: `@Min(1) @Max(240)`
     - `startMonth`: `@Min(1) @Max(12)`
     - `termsPerYear`: `@AssertTrue` 驗證白名單 `[1, 2, 3, 4, 6, 12]`
     - `entranceFee`: `@PositiveOrZero`

2. **巢狀層級 `@Valid` 串聯（Validation Cascading）**：
   - [x] [CreateEducationConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/CreateEducationConfigRequest.java)（各階段加上 `@Valid`）
   - [x] [UpdateEducationConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UpdateEducationConfigRequest.java)（各階段加上 `@Valid`）
   - [x] [PreschoolConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PreschoolConfigRequest.java)（各軌道加上 `@Valid`）
   - [x] [PrimaryConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PrimaryConfigRequest.java)（各軌道加上 `@Valid`）
   - [x] [MiddleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java)（各軌道加上 `@Valid`）
   - [x] [HighConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java)（各軌道加上 `@Valid`）
   - [x] [UniversityConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java)（各軌道加上 `@Valid`，`livingCost` 加上 `@PositiveOrZero`）

3. **全域例外攔截強化**：
   - [x] [WebGlobalExceptionHandler.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java)（`handleBadRequestExceptions` 擴充攔截 `DateTimeException.class` 轉為 400 Bad Request）

4. **單元測試**：
   - [x] [WebGlobalExceptionHandlerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/test/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandlerTest.java)（測試 `DateTimeException` 攔截）
   - [x] [EducationConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java)（測試非法 `startMonth`、`duration`、`termsPerYear`、`livingCost` 等觸發 400 Bad Request）

---

## 修正結果報告

### 1. TrackConfigRequest 驗證對齊
- 針對 [TrackConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java)，完整對齊 [StageOverrideRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java) 的防呆標準：
  - `tuition`、`entranceFee`: `@PositiveOrZero`
  - `duration`: `@Min(1) @Max(240)`
  - `startMonth`: `@Min(1) @Max(12)`
  - `termsPerYear`: `@JsonIgnore @AssertTrue` 驗證白名單集合 `[1, 2, 3, 4, 6, 12]`

### 2. 巢狀驗證串聯（Cascading Validation）
- 在最外層請求 DTO（`CreateEducationConfigRequest`、`UpdateEducationConfigRequest`）以及中層階段 DTO（`PreschoolConfigRequest`、`PrimaryConfigRequest`、`MiddleConfigRequest`、`HighConfigRequest`、`UniversityConfigRequest`）之所有軌道欄位加上 `@Valid`。
- `UniversityConfigRequest` 的 `livingCost` 亦補齊 `@PositiveOrZero` 驗證，確保多層巢狀物件在 Controller 端均能被 Jakarta Validation 正確遞迴觸發檢查。

### 3. 全域例外攔截（WebGlobalExceptionHandler）
- 在 [WebGlobalExceptionHandler.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java) 的 `handleBadRequestExceptions` 加入 `java.time.DateTimeException.class`。
- 日後若有任何日期或月份越界情況（如 `YearMonth.of`、`Month.of`），不再溢位成為未處理的 500 內部伺服器錯誤，而是統一回傳 HTTP 400 Bad Request 與標準 `VALIDATION_FAILED` / `BAD_REQUEST` 錯誤結構。

### 4. 單元測試完備
- **WebGlobalExceptionHandlerTest**: 新增 `handleBadRequestExceptions_DateTimeException`，驗證 `DateTimeException` 正確被攔截並回傳 400 Bad Request。
- **EducationConfigControllerTest**: 建立獨立 Controller 測試，涵蓋：
  - `startMonth > 12` 觸發 400 `VALIDATION_FAILED`
  - `duration > 240` 觸發 400 `VALIDATION_FAILED`
  - `termsPerYear` 非白名單數值觸發 400 `VALIDATION_FAILED`
  - `tuition` / `entranceFee` 為負數觸發 400 `VALIDATION_FAILED`
  - `university.livingCost` 為負數觸發 400 `VALIDATION_FAILED`
  - `CreateEducationConfigRequest` 巢狀欄位校驗
  - 合法請求更新成功回傳 200 OK

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                                      10024  12.25G   5.46    10:21:39  
diff --git a/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java b/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java
index ed85a68e..3963d345 100644
--- a/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java
+++ b/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java
@@ -23,6 +23,7 @@
  */
 package ch.lin.finance.hub.backend.api.common.advice;
 
+import java.time.DateTimeException;
 import java.util.stream.Collectors;
 
 import org.slf4j.Logger;
@@ -227,7 +228,8 @@ public class WebGlobalExceptionHandler extends BaseGlobalExceptionHandler {
      */
     @ExceptionHandler({
         IllegalArgumentException.class,
-        MethodArgumentTypeMismatchException.class
+        MethodArgumentTypeMismatchException.class,
+        DateTimeException.class
     })
     public ResponseEntity<ApiResponse<ApiError>> handleBadRequestExceptions(Exception ex) {
         Throwable cause = ex.getCause();
diff --git a/finance-hub-backend/hub-common/src/test/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandlerTest.java b/finance-hub-backend/hub-common/src/test/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandlerTest.java
index 8f0f4326..6b6a5e3f 100644
--- a/finance-hub-backend/hub-common/src/test/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandlerTest.java
+++ b/finance-hub-backend/hub-common/src/test/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandlerTest.java
@@ -23,6 +23,7 @@
  */
 package ch.lin.finance.hub.backend.api.common.advice;
 
+import java.time.DateTimeException;
 import java.util.List;
 import java.util.Objects;
 
@@ -213,6 +214,19 @@ class WebGlobalExceptionHandlerTest {
         assertEquals("Mismatch message", java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
     }
 
+    @Test
+    void handleBadRequestExceptions_DateTimeException() {
+        DateTimeException ex = new DateTimeException("Invalid value for MonthOfYear: 15");
+
+        ResponseEntity<ApiResponse<ApiError>> response = handler.handleBadRequestExceptions(ex);
+
+        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
+        assertNotNull(response.getBody());
+        assertEquals("BAD_REQUEST", java.util.Objects.requireNonNull(response.getBody()).getData().getCode());
+        assertEquals("Invalid value for MonthOfYear: 15",
+                java.util.Objects.requireNonNull(response.getBody()).getData().getMessage());
+    }
+
     @Test
     void handleResponseStatusException_ReturnsStandardFormat() {
         org.springframework.web.server.ResponseStatusException ex = new org.springframework.web.server.ResponseStatusException(
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/CreateEducationConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/CreateEducationConfigRequest.java
index 7a8e2d75..79ff6570 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/CreateEducationConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/CreateEducationConfigRequest.java
@@ -1,5 +1,6 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
+import jakarta.validation.Valid;
 import jakarta.validation.constraints.NotBlank;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
@@ -16,9 +17,14 @@ import lombok.NoArgsConstructor;
 public class CreateEducationConfigRequest {
     @NotBlank
     private String name;
+    @Valid
     private PreschoolConfigRequest preschool;
+    @Valid
     private PrimaryConfigRequest primary;
+    @Valid
     private MiddleConfigRequest middle;
+    @Valid
     private HighConfigRequest high;
+    @Valid
     private UniversityConfigRequest university;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java
index 54dc6278..f16e4ad2 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java
@@ -1,5 +1,6 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
+import jakarta.validation.Valid;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -13,7 +14,10 @@ import lombok.NoArgsConstructor;
 @NoArgsConstructor
 @AllArgsConstructor
 public class HighConfigRequest {
+    @Valid
     private TrackConfigRequest publicTrack;
+    @Valid
     private TrackConfigRequest privateTrack;
+    @Valid
     private TrackConfigRequest intlTrack;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java
index 3fcb946a..8dc909cd 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java
@@ -1,5 +1,6 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
+import jakarta.validation.Valid;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -13,7 +14,10 @@ import lombok.NoArgsConstructor;
 @NoArgsConstructor
 @AllArgsConstructor
 public class MiddleConfigRequest {
+    @Valid
     private TrackConfigRequest publicTrack;
+    @Valid
     private TrackConfigRequest privateTrack;
+    @Valid
     private TrackConfigRequest intlTrack;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PreschoolConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PreschoolConfigRequest.java
index cd70614b..451d6384 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PreschoolConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PreschoolConfigRequest.java
@@ -1,5 +1,6 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
+import jakarta.validation.Valid;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -13,7 +14,10 @@ import lombok.NoArgsConstructor;
 @NoArgsConstructor
 @AllArgsConstructor
 public class PreschoolConfigRequest {
+    @Valid
     private TrackConfigRequest homeTrack;
+    @Valid
     private TrackConfigRequest daycareTrack;
+    @Valid
     private TrackConfigRequest intlTrack;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PrimaryConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PrimaryConfigRequest.java
index 0c2e17f8..e0ee7f4b 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PrimaryConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PrimaryConfigRequest.java
@@ -1,5 +1,6 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
+import jakarta.validation.Valid;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -13,7 +14,10 @@ import lombok.NoArgsConstructor;
 @NoArgsConstructor
 @AllArgsConstructor
 public class PrimaryConfigRequest {
+    @Valid
     private TrackConfigRequest publicTrack;
+    @Valid
     private TrackConfigRequest privateTrack;
+    @Valid
     private TrackConfigRequest intlTrack;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java
index 1fcf078b..b673b453 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java
@@ -1,7 +1,14 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
 import java.math.BigDecimal;
+import java.util.Set;
 
+import com.fasterxml.jackson.annotation.JsonIgnore;
+
+import jakarta.validation.constraints.AssertTrue;
+import jakarta.validation.constraints.Max;
+import jakarta.validation.constraints.Min;
+import jakarta.validation.constraints.PositiveOrZero;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -15,9 +22,28 @@ import lombok.NoArgsConstructor;
 @NoArgsConstructor
 @AllArgsConstructor
 public class TrackConfigRequest {
+
+    private static final Set<Integer> ALLOWED_TERMS = Set.of(1, 2, 3, 4, 6, 12);
+
+    @PositiveOrZero(message = "tuition must be greater than or equal to 0")
     private BigDecimal tuition;
+
+    @Min(value = 1, message = "duration must be at least 1 month")
+    @Max(value = 240, message = "duration must not exceed 240 months")
     private Integer duration;
+
+    @Min(value = 1, message = "startMonth must be between 1 and 12")
+    @Max(value = 12, message = "startMonth must be between 1 and 12")
     private Integer startMonth;
+
     private Integer termsPerYear;
+
+    @PositiveOrZero(message = "entranceFee must be greater than or equal to 0")
     private BigDecimal entranceFee;
+
+    @JsonIgnore
+    @AssertTrue(message = "termsPerYear must be one of [1, 2, 3, 4, 6, 12]")
+    public boolean isTermsPerYearValid() {
+        return termsPerYear == null || ALLOWED_TERMS.contains(termsPerYear);
+    }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java
index c18f2611..4118d8eb 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java
@@ -2,6 +2,8 @@ package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
 import java.math.BigDecimal;
 
+import jakarta.validation.Valid;
+import jakarta.validation.constraints.PositiveOrZero;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -16,14 +18,25 @@ import lombok.NoArgsConstructor;
 @AllArgsConstructor
 public class UniversityConfigRequest {
 
+    @Valid
     private TrackConfigRequest jpNationalTrack;
+    @Valid
     private TrackConfigRequest jpPrivateTrack;
+    @Valid
     private TrackConfigRequest jpPrivateArtTrack;
+    @Valid
     private TrackConfigRequest twNationalTrack;
+    @Valid
     private TrackConfigRequest twPrivateTrack;
+    @Valid
     private TrackConfigRequest usTrack;
+    @Valid
     private TrackConfigRequest ukTrack;
+    @Valid
     private TrackConfigRequest canadaTrack;
+    @Valid
     private TrackConfigRequest australiaTrack;
+
+    @PositiveOrZero(message = "livingCost must be greater than or equal to 0")
     private BigDecimal livingCost;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UpdateEducationConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UpdateEducationConfigRequest.java
index 6f192f2f..c9ddb113 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UpdateEducationConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UpdateEducationConfigRequest.java
@@ -1,5 +1,6 @@
 package ch.lin.finance.hub.backend.api.controller.config.dto.education;
 
+import jakarta.validation.Valid;
 import lombok.AllArgsConstructor;
 import lombok.Builder;
 import lombok.Data;
@@ -13,9 +14,14 @@ import lombok.NoArgsConstructor;
 @NoArgsConstructor
 @AllArgsConstructor
 public class UpdateEducationConfigRequest {
+    @Valid
     private PreschoolConfigRequest preschool;
+    @Valid
     private PrimaryConfigRequest primary;
+    @Valid
     private MiddleConfigRequest middle;
+    @Valid
     private HighConfigRequest high;
+    @Valid
     private UniversityConfigRequest university;
 }
diff --git a/finance-hub-backend/todo/pending fix/issue-111.md b/finance-hub-backend/todo/pending fix/issue-111.md
index c03e47e0..7490b14c 100644
--- a/finance-hub-backend/todo/pending fix/issue-111.md
+++ b/finance-hub-backend/todo/pending fix/issue-111.md
@@ -20,3 +20,61 @@ Medium-High（觸發機率低，需要手動去改全域設定才會發生，但
 
 ## 建議修正
 把`StageOverrideRequest`上已經在用的同一組驗證註解（`@Min(1) @Max(12)`用於`startMonth`、`@Min(1) @Max(240)`用於`duration`、白名單檢查用於`termsPerYear`），套用到`TrackConfigRequest`上；並確認`UpdateEducationConfigRequest`／`CreateEducationConfigRequest`裡，每個階段的巢狀請求物件上都有加`@Valid`，確保這層驗證真的會被觸發。這是把既有、已經驗證過的防呆機制，套用到目前漏掉的另一個位置，不需要重新設計。
+
+---
+
+## 實作清單（Checklist）
+
+1. **`TrackConfigRequest.java` 補齊防呆驗證註解**：
+   - [x] [TrackConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java)
+     - `tuition`: `@PositiveOrZero`
+     - `duration`: `@Min(1) @Max(240)`
+     - `startMonth`: `@Min(1) @Max(12)`
+     - `termsPerYear`: `@AssertTrue` 驗證白名單 `[1, 2, 3, 4, 6, 12]`
+     - `entranceFee`: `@PositiveOrZero`
+
+2. **巢狀層級 `@Valid` 串聯（Validation Cascading）**：
+   - [x] [CreateEducationConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/CreateEducationConfigRequest.java)（各階段加上 `@Valid`）
+   - [x] [UpdateEducationConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UpdateEducationConfigRequest.java)（各階段加上 `@Valid`）
+   - [x] [PreschoolConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PreschoolConfigRequest.java)（各軌道加上 `@Valid`）
+   - [x] [PrimaryConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/PrimaryConfigRequest.java)（各軌道加上 `@Valid`）
+   - [x] [MiddleConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/MiddleConfigRequest.java)（各軌道加上 `@Valid`）
+   - [x] [HighConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/HighConfigRequest.java)（各軌道加上 `@Valid`）
+   - [x] [UniversityConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java)（各軌道加上 `@Valid`，`livingCost` 加上 `@PositiveOrZero`）
+
+3. **全域例外攔截強化**：
+   - [x] [WebGlobalExceptionHandler.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java)（`handleBadRequestExceptions` 擴充攔截 `DateTimeException.class` 轉為 400 Bad Request）
+
+4. **單元測試**：
+   - [x] [WebGlobalExceptionHandlerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/test/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandlerTest.java)（測試 `DateTimeException` 攔截）
+   - [x] [EducationConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java)（測試非法 `startMonth`、`duration`、`termsPerYear`、`livingCost` 等觸發 400 Bad Request）
+
+---
+
+## 修正結果報告
+
+### 1. TrackConfigRequest 驗證對齊
+- 針對 [TrackConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java)，完整對齊 [StageOverrideRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java) 的防呆標準：
+  - `tuition`、`entranceFee`: `@PositiveOrZero`
+  - `duration`: `@Min(1) @Max(240)`
+  - `startMonth`: `@Min(1) @Max(12)`
+  - `termsPerYear`: `@JsonIgnore @AssertTrue` 驗證白名單集合 `[1, 2, 3, 4, 6, 12]`
+
+### 2. 巢狀驗證串聯（Cascading Validation）
+- 在最外層請求 DTO（`CreateEducationConfigRequest`、`UpdateEducationConfigRequest`）以及中層階段 DTO（`PreschoolConfigRequest`、`PrimaryConfigRequest`、`MiddleConfigRequest`、`HighConfigRequest`、`UniversityConfigRequest`）之所有軌道欄位加上 `@Valid`。
+- `UniversityConfigRequest` 的 `livingCost` 亦補齊 `@PositiveOrZero` 驗證，確保多層巢狀物件在 Controller 端均能被 Jakarta Validation 正確遞迴觸發檢查。
+
+### 3. 全域例外攔截（WebGlobalExceptionHandler）
+- 在 [WebGlobalExceptionHandler.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/advice/WebGlobalExceptionHandler.java) 的 `handleBadRequestExceptions` 加入 `java.time.DateTimeException.class`。
+- 日後若有任何日期或月份越界情況（如 `YearMonth.of`、`Month.of`），不再溢位成為未處理的 500 內部伺服器錯誤，而是統一回傳 HTTP 400 Bad Request 與標準 `VALIDATION_FAILED` / `BAD_REQUEST` 錯誤結構。
+
+### 4. 單元測試完備
+- **WebGlobalExceptionHandlerTest**: 新增 `handleBadRequestExceptions_DateTimeException`，驗證 `DateTimeException` 正確被攔截並回傳 400 Bad Request。
+- **EducationConfigControllerTest**: 建立獨立 Controller 測試，涵蓋：
+  - `startMonth > 12` 觸發 400 `VALIDATION_FAILED`
+  - `duration > 240` 觸發 400 `VALIDATION_FAILED`
+  - `termsPerYear` 非白名單數值觸發 400 `VALIDATION_FAILED`
+  - `tuition` / `entranceFee` 為負數觸發 400 `VALIDATION_FAILED`
+  - `university.livingCost` 為負數觸發 400 `VALIDATION_FAILED`
+  - `CreateEducationConfigRequest` 巢狀欄位校驗
+  - 合法請求更新成功回傳 200 OK
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                                           10025  11.58G   2.75    10:30:55  