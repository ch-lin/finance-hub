# [Fixed] Feature Requirement 128：教育設定「就讀時長」輸入框移除0.5年（半年）這個選項，只允許整年輸入；前後端都加上「月數必須是12的整數倍」的防呆檢查

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板決定：`hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:906-918`目前這個「Duration (Years)」輸入框的`step="0.5"`（允許輸入半年），要移除，改成只能輸入整年；同時前端送出、後端接收這個欄位時，都要加上「換算成月數之後必須是12的整數倍」的防呆檢查。

## 背景與判斷依據（使用者提出的推論過程）
這個任務源自一份原本只是想解決「顯示誤差」的question（該內容已被feature-requirement-04取代整併，不再獨立存在），使用者在討論過程中延伸出一個更根本的觀察：**正常情況下，透過UI正常填寫進去的月數，理論上應該永遠能跟年對應得上**（因為輸入框本身限制了使用者只能輸入年，再換算成月）；如果資料庫裡曾經出現一個「無法跟年對應」的月數，這本身就是不合理、需要被擋下來的異常值，而不是放著讓它悄悄存在。

在確認「該用6的倍數還是12的倍數當防呆標準」時，使用者進一步檢視了`step="0.5"`（允許半年）這個選項本身存在的必要性，套用使用者之前否定「延後入學」「跳級」等功能時用過的同一個原則——**沒有人能提前預測人生，事到臨頭才會知道實際情況，模擬設定不該假設一個無法提前得知的精確數字**：
- 國中、高中在現實中都是固定3年，沒有0.5年的情境。
- 大學一般也沒有0.5年這種情境；「延畢」屬於「事到臨頭才會知道」的類別，不該在事前的模擬設定裡假設。
- 幼稚園也一樣：沒有家長會用「K4.5歲才要去念」這種方式思考入學時間，現實中的入學決定就是「幾歲那年的4月/9月」這種整年概念。

**結論：`0.5`這個步進值，在所有五個學制階段裡都找不到一個真實、能被使用者提前預測的情境會用到它**，本身就是一個不符合「模擬預測」原則的多餘選項，應該直接從輸入框移除，不是留著讓使用者自由發揮。移除`0.5`之後，輸入框只能輸入整年，換算出來的月數自然永遠會是12的整數倍——這讓防呆標準從原本考慮的「6的倍數」（如果保留半年選項）簡化成更嚴格、更單純的「12的倍數」。

## 想要的行為
1. `child-profile-section.tsx:906-918`這個輸入框：`step`從`"0.5"`改成`"1"`；`min`從`"0.5"`改成`"1"`（沒有人會設定0年的就讀時長）；`max="20"`維持不變。
2. 前端：使用者輸入的年數乘以12換算成月數之後，如果不是12的整數倍（正常操作路徑下不應該發生，但防止任何非預期的輸入方式），拒絕送出並提示錯誤。
3. 後端：這個欄位（`manualDuration`，位於`StageOverrideRequest`）目前已經有`@Min(1) @Max(240)`這層防呆（見[issue-111](issue-111.md)裡提到的既有防呆模式），這次要再加上「必須是12的整數倍」的檢查（例如用`@AssertTrue`或類似機制，比照`manualTermsPerYear`白名單檢查的既有做法）——不管前端有沒有正確擋下，後端都要獨立擋下任何非12倍數的月數，不能只依賴前端。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:906-918`（輸入框本身，`step`／`min`要修改）
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java`（後端`manualDuration`欄位目前的`@Min(1) @Max(240)`檢查，這裡要新增「12的整數倍」檢查）
- 這份任務跟[feature-requirement-127](feature-requirement-127.md)（同一個輸入框旁邊要新增「= X months」的即時顯示提示）動到同一個UI元素，**實作時建議兩份任務一起處理，不要分開改**，避免其中一份先落地時，另一份還假設著舊的`step="0.5"`規格，導致兩次改動互相打架。

## 既有資料相容性提醒
比照[feature-requirement-126](feature-requirement-126.md)裡提到的同類風險：如果資料庫裡已經存在任何一筆`manualDuration`是非12倍數的月數（例如過去測試時透過`0.5`步進值填過的舊資料），這次後端加上「必須是12倍數」的防呆之後，這些舊紀錄在下次被讀取／編輯／驗證時可能會被擋下。使用者已確認目前資料都是測試用的舊資料、系統尚未deploy，這個風險目前影響應該不大，但實作前建議快速確認一次現有資料庫裡是否真的有這種非12倍數的殘留值。

---

## 實作完成報告（Implementation Report）

### 一、實作狀態
- [x] **已完成**：移除前端就讀時長輸入框的 0.5 年步進值（改為整年，`step="1"`、`min="1"`），前後端均加入「月數必須是 12 的整數倍」之防呆檢查與單元測試。

### 二、改動檔案清單
1. **前端 UI 與驗證**：
   - **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)**：
     - `Duration (Years)` 輸入框：`step` 由 `"0.5"` 改為 `"1"`；非幼兒園 `min` 由 `"0.5"` 改為 `"1"`（幼兒園維持 `"3"`）。
     - `handleSave` 函式：新增防禦檢查，若任何階段的 `manualDuration` 不為 null 且非 12 的整數倍（`val % 12 !== 0`），跳出錯誤提示並拒絕送出。
2. **後端 DTO 驗證**：
   - **[StageOverrideRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java#L69-L75)**：
     - 新增 `@JsonIgnore @AssertTrue(message = "manualDuration must be a multiple of 12 months")` 檢核方法 `isManualDurationMultipleOf12()`。
3. **後端單元測試**：
   - **[ChildEducationSettingsControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java#L144-L161)**：
     - 新增 `updateSettings_InvalidDurationNonMultipleOf12_ReturnsBadRequest()` 測試案例，傳入 `manualDuration(18)` 驗證回傳 `400 Bad Request` 與 `VALIDATION_FAILED`。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                 10037  13.00G   5.29    11:57:55  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index 0095433..859b250 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -454,6 +454,23 @@ export function ChildProfileSection() {
         settingsToSave.preschoolOverride.manualDuration = 36;
       }
 
+      // Client-side validation: manualDuration must be a multiple of 12 months (full years)
+      const overridesToCheck = [
+        settingsToSave.preschoolOverride,
+        settingsToSave.preschoolOverride2,
+        settingsToSave.primaryOverride,
+        settingsToSave.middleOverride,
+        settingsToSave.highOverride,
+        settingsToSave.universityOverride,
+      ];
+      const hasInvalidDuration = overridesToCheck.some(
+        o => o?.manualDuration != null && o.manualDuration % 12 !== 0
+      );
+      if (hasInvalidDuration) {
+        toast.error("Duration must be a multiple of 12 months (full years)");
+        return;
+      }
+
       const settingsSaved = await saveEducationSettings(childId, settingsToSave);
       if (!settingsSaved) {
         // If settings failed due to validation, keep dialog open
@@ -1209,8 +1226,8 @@ function StageCard({
                 </Label>
                 <Input
                   type="number"
-                  step="0.5"
-                  min={stageKey === 'preschool' ? "3" : "0.5"}
+                  step="1"
+                  min={stageKey === 'preschool' ? "3" : "1"}
                   max="20"
                   placeholder={stageKey === 'preschool' ? "3 (Standard)" : "Default"}
                   className="h-8 text-sm"
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                                            10038  12.97G   5.11    11:58:07  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                    10039  12.99G   5.06    11:58:15  
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java
index aaba9745..948ae43b 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java
@@ -66,4 +66,10 @@ public class StageOverrideRequest {
     public boolean isManualTermsPerYearValid() {
         return manualTermsPerYear == null || ALLOWED_TERMS.contains(manualTermsPerYear);
     }
+
+    @JsonIgnore
+    @AssertTrue(message = "manualDuration must be a multiple of 12 months")
+    public boolean isManualDurationMultipleOf12() {
+        return manualDuration == null || manualDuration % 12 == 0;
+    }
 }
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java
index 3eeeda46..d6a52f28 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java
@@ -23,6 +23,8 @@
  */
 package ch.lin.finance.hub.backend.api.controller.profile.child.education;
 
+import java.math.BigDecimal;
+
 import org.junit.jupiter.api.BeforeEach;
 import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.extension.ExtendWith;
@@ -50,8 +52,6 @@ import ch.lin.finance.hub.backend.api.common.exception.InvalidEducationTimelineE
 import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.ChildEducationSettingsMapper;
 import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.StageOverrideRequest;
 import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.UpdateChildEducationSettingsRequest;
-import java.math.BigDecimal;
-
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
 
@@ -141,6 +141,24 @@ class ChildEducationSettingsControllerTest {
                 .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
     }
 
+    @Test
+    void updateSettings_InvalidDurationNonMultipleOf12_ReturnsBadRequest() throws Exception {
+        StageOverrideRequest override = StageOverrideRequest.builder()
+                .manualDuration(18) // Invalid (not a multiple of 12)
+                .build();
+
+        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
+                .primaryTrack(PrimaryTrack.PUBLIC)
+                .primaryOverride(override)
+                .build();
+
+        mockMvc.perform(put("/profile/children/1/education-settings")
+                .contentType(MediaType.APPLICATION_JSON)
+                .content(objectMapper.writeValueAsString(request)))
+                .andExpect(status().isBadRequest())
+                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
+    }
+
     @Test
     void updateSettings_InvalidTermsPerYear_ReturnsBadRequest() throws Exception {
         StageOverrideRequest override = StageOverrideRequest.builder()
diff --git a/finance-hub-backend/todo/pending fix/feature-requirement-128.md b/finance-hub-backend/todo/pending fix/feature-requirement-128.md
index 566fb2ec..4466e7f6 100644
--- a/finance-hub-backend/todo/pending fix/feature-requirement-128.md
+++ b/finance-hub-backend/todo/pending fix/feature-requirement-128.md
@@ -25,3 +25,26 @@
 
 ## 既有資料相容性提醒
 比照[feature-requirement-126](feature-requirement-126.md)裡提到的同類風險：如果資料庫裡已經存在任何一筆`manualDuration`是非12倍數的月數（例如過去測試時透過`0.5`步進值填過的舊資料），這次後端加上「必須是12倍數」的防呆之後，這些舊紀錄在下次被讀取／編輯／驗證時可能會被擋下。使用者已確認目前資料都是測試用的舊資料、系統尚未deploy，這個風險目前影響應該不大，但實作前建議快速確認一次現有資料庫裡是否真的有這種非12倍數的殘留值。
+
+---
+
+## 實作完成報告（Implementation Report）
+
+### 一、實作狀態
+- [x] **已完成**：移除前端就讀時長輸入框的 0.5 年步進值（改為整年，`step="1"`、`min="1"`），前後端均加入「月數必須是 12 的整數倍」之防呆檢查與單元測試。
+
+### 二、改動檔案清單
+1. **前端 UI 與驗證**：
+   - **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)**：
+     - `Duration (Years)` 輸入框：`step` 由 `"0.5"` 改為 `"1"`；非幼兒園 `min` 由 `"0.5"` 改為 `"1"`（幼兒園維持 `"3"`）。
+     - `handleSave` 函式：新增防禦檢查，若任何階段的 `manualDuration` 不為 null 且非 12 的整數倍（`val % 12 !== 0`），跳出錯誤提示並拒絕送出。
+2. **後端 DTO 驗證**：
+   - **[StageOverrideRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/StageOverrideRequest.java#L69-L75)**：
+     - 新增 `@JsonIgnore @AssertTrue(message = "manualDuration must be a multiple of 12 months")` 檢核方法 `isManualDurationMultipleOf12()`。
+3. **後端單元測試**：
+   - **[ChildEducationSettingsControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java#L144-L161)**：
+     - 新增 `updateSettings_InvalidDurationNonMultipleOf12_ReturnsBadRequest()` 測試案例，傳入 `manualDuration(18)` 驗證回傳 `400 Bad Request` 與 `VALIDATION_FAILED`。
+
+## Diff
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                         10040  13.00G   5.00    11:58:17  