# [Fixed] Issue 135：幼稚園單軌/兩段式開關切換時，`manualDuration`殘留值沒有被清空，可能悄悄復活

## 這份issue怎麼被發現的
這是在驗證[feature-requirement-125](feature-requirement-125.md)（幼稚園「前段HOME、後段換軌道」兩段式切換功能）實作完成度時，額外發現的問題。這個問題不是feature-requirement-125這次改動造成的新bug，是查核過程中順便發現的既有缺陷，獨立記錄在這裡。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:693-714`（`onPreschoolSplitChange`，開啟兩段式時只設定`preschoolStage1Duration`/`preschoolTrack2`/`preschoolOverride2`三者的預設值，關閉時只清空這三者，從頭到尾都沒有去動`preschoolOverride`本身）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java:92`（`settings.setPreschoolOverride(...)`這一行，不管`preschoolSplitEnabled`是true還是false都會無條件執行，在後面的if/else判斷之前就先存了）
- 同檔案第93-115行（`preschoolSplitEnabled`為true的分支，只檢查`preschoolTrack2`/`preschoolStage1Duration`是否合法，完全沒有檢查或清空`preschoolOverride`；為false的分支，只檢查`preschoolOverride.manualDuration`是否`< 36`，不會擋下任何大於等於36的殘留值）

## 現狀
假設使用者在單軌模式下，透過進階設定把幼稚園`manualDuration`手動填成48個月（延長托兒），之後打開「兩段式切換」開關——前端不會去清空這個48的殘留值，送出的請求payload裡`preschoolOverride.manualDuration`依然是48。後端第92行無條件把這個值存進資料庫，兩段式分支（第93-104行）完全不會去檢查或清空它。

## 為何是問題
存進資料庫之後，因為計算邏輯目前是兩段式模式下用`stage1Duration`+`stage2`去算、不會讀到這個殘留的`manualDuration`，所以當下看不出任何異狀，使用者在兩段式模式的畫面上也看不到這個殘留值的存在（兩段式模式顯示的是唯讀的「3 Years (2-Stage Total)」文字，不會露出底下還藏著一個48）。但如果使用者之後又把兩段式開關關掉、切回單軌模式，這個殘留的48會立刻重新生效——因為單軌模式的驗證只檢查`manualDuration < 36`，48遠大於36，驗證會通過，使用者會在不知不覺中讀到一個自己可能根本忘記設過、或當初是為了兩段式模式之外的目的填的舊數字。

## 建議修正
在前端`onPreschoolSplitChange`裡，開啟兩段式時應該連同`preschoolOverride`一併清空（或至少清空`manualDuration`這個欄位），避免殘留值在模式之間跨界復活；或者在後端`ChildEducationSettingsServiceImpl.java`的`preschoolSplitEnabled`為true分支裡，明確把`preschoolOverride`存成`null`，確保只要是兩段式模式，單軌專用的覆寫欄位就不該存在資料庫裡。兩種做法選一種即可，核心是「切換模式時，不適用於目前模式的欄位要被清空，不能悄悄留著等下次切換回去時復活」。

---

## 審查回饋與架構深度分析（Review & Architectural Analysis）

經全面檢驗前後端原始碼與業務架構，**本 issue 描述之現象屬實（非幻覺），但 Claude 建議之二選一修正方案存在嚴重業務漏洞，不可全盤採納**。以下為詳細分析與正確方案制定：

### 一、現象屬實查核（Bug 確實存在）
1. **前端狀態殘留**：在 [child-profile-section.tsx:L693-L714](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L693-L714) 的 `onPreschoolSplitChange` 中，切換為兩段式（`checked = true`）時，確實保留了原本單軌的 `prev.preschoolOverride`（含 `manualDuration`，例如 48 個月）。
2. **UI 視覺遮蔽**：在兩段式模式下，UI 將總時長硬性鎖定為唯讀的 `"3 Years (2-Stage Total)"`（= 36 個月，見 [child-profile-section.tsx:L1272](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L1272)），使用者在畫面上完全看不到 48 的存在。
3. **後端持久化髒資料**：後端 [ChildEducationSettingsServiceImpl.java:L92](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java#L92) 在兩段式分支判斷前就無條件存入了 `command.getPreschoolOverride()`，導致資料庫中該筆記錄同時存在 `preschool_split_enabled = true` 與 `preschool_manual_duration = 48`。
4. **悄悄復活**：當使用者日後關閉兩段式開關切回單軌時，該 48 個月會立刻在前端時間軸預覽與後端模擬計算中「重新生效」，確實會造成非預期的學制延長。

---

### 二、Claude 建議修正方案之重大漏洞分析（不可選方案二）

Claude 在原文「建議修正」中提出「二選一」：
- **選項一**：在前端 `onPreschoolSplitChange` 裡，開啟兩段式時應該連同 `preschoolOverride` 一併清空（或至少清空 `manualDuration` 這個欄位）。
- **選項二**：在後端 `ChildEducationSettingsServiceImpl.java` 的 `preschoolSplitEnabled` 為 true 分支裡，明確把 `preschoolOverride` 存成 `null`，確保只要是兩段式模式，單軌專用的覆寫欄位就不該存在資料庫裡。

#### 為什麼選項二（以及選項一前半段的「連同 preschoolOverride 一併清空」）是致命的架構誤解？
1. **`preschoolOverride` 並非「單軌專用」**：
   在系統架構中（見 [EducationProfileResolver.java:L115](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java#L115) 與前端 [child-profile-section.tsx:L1168-L1176](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L1168-L1176)），兩段式幼兒園架構為：
   - **Stage 1（前段幼教）**：使用 `preschoolTrack`（例如 DAYCARE）與 **`preschoolOverride`**（負責前段的 `manualTuition` 自訂學費、`manualStartMonth` 入學月份等）。
   - **Stage 2（後段幼教）**：使用 `preschoolTrack2`（例如 INTL）與 **`preschoolOverride2`**。
2. **若採納選項二（後端把 `preschoolOverride` 存成 `null`）之惡果**：
   使用者在兩段式模式下為前段（Stage 1）填寫的任何自訂學費（例如前段念昂貴私立托兒所每年 60 萬日圓），在存檔時會**被後端無差別清空成 `null`，造成嚴重的學費資料遺失 Bug**！

---

### 三、拍板修正方案：精確靶向清除 `manualDuration`

在兩段式幼教模式下，總時長受 `preschoolStage1Duration`（12 或 24 個月）與剩餘月份約束，**唯一無效且不該殘留的欄位僅有 `manualDuration`**。

#### 1. 前端（UI 交互防護）：[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)
- 在 `onPreschoolSplitChange` 啟用兩段式時（`checked === true`），若 `prev.preschoolOverride` 存在，僅將其 `manualDuration` 設為 `null`，完整保留 `manualTuition`、`manualStartMonth` 等 Stage 1 參數：
  ```typescript
  preschoolOverride: prev.preschoolOverride ? { ...prev.preschoolOverride, manualDuration: null } : null,
  ```

#### 2. 後端（領域資料完整性防禦）：[ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)
- 在 `preschoolSplitEnabled == true` 處理分支中，若 `preschoolOverride != null`，防禦性地執行：
  ```java
  preschoolOverride.setManualDuration(null);
  ```
  確保資料庫在兩段式模式下永遠不會殘留過期的總時長數值。

---

## 四、實作進度與修正報告（Progress & Fix Report）—— 已完成 (Completed)

本任務已完成前後端精準聯防修正與單元測試覆蓋：

### 1. 後端服務層防禦（`hub-app`）：
- [ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)：
  - 在 `preschoolSplitEnabled == true` 分支內，若 `settings.getPreschoolOverride() != null`，主動執行 `settings.getPreschoolOverride().setManualDuration(null)`。
  - 對 `preschoolOverride2` 也同樣執行 `override2.setManualDuration(null)`，確保兩段式模式下任一階段的時長覆寫皆為 `null`，嚴守領域不變量（Domain Invariant）。
  - 同時完整保留 `manualTuition`、`manualStartMonth`、`manualTermsPerYear` 等自訂學費與期數參數，杜絕資料抹殺漏洞。
- [ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)：
  - 新增單元測試 `updateSettings_WhenPreschoolSplitEnabled_ClearsManualDurationOnBothOverrides`：
    - 模擬傳入含有 `manualDuration: 48, manualTuition: 500,000` 的 `preschoolOverride`，以及 `manualDuration: 24, manualTuition: 1,800,000` 的 `preschoolOverride2`。
    - 驗證儲存至資料庫與回傳結果中，`manualDuration` 100% 被清理為 `null`，而 `manualTuition` 100% 完整保留。

### 2. 前端 UI 狀態防禦（`hub-ui`）：
- [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)：
  - 在 `onPreschoolSplitChange` 啟用開關時，將 `preschoolOverride` 的 `manualDuration` 設為 `null`，保留其他 Stage 1 參數。
  - 在 `handleSaveSettings` 構建 payload 的 `settingsToSave` 中建立雙重防禦：當 `splitEnabled === true` 時，將 `preschoolOverride.manualDuration` 與 `preschoolOverride2.manualDuration` 強制清理為 `null`，確保送出的網路請求不含過期時長髒資料。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  cd ../../hub-ui/hub-ui                                      10020  12.30G   9.44    21:42:10  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                      10021  12.14G   9.26    21:42:20  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index 63b7770..54bc59e 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -494,7 +494,10 @@ export function ChildProfileSection() {
         preschoolSplitEnabled: splitEnabled,
         preschoolStage1Duration: splitEnabled ? (editingSettings.preschoolStage1Duration ?? 12) : null,
         preschoolTrack2: splitEnabled ? (editingSettings.preschoolTrack2 ?? "INTL") : null,
-        preschoolOverride2: splitEnabled ? editingSettings.preschoolOverride2 : null,
+        preschoolOverride: splitEnabled && editingSettings.preschoolOverride
+          ? { ...editingSettings.preschoolOverride, manualDuration: null }
+          : editingSettings.preschoolOverride,
+        preschoolOverride2: splitEnabled ? (editingSettings.preschoolOverride2 ? { ...editingSettings.preschoolOverride2, manualDuration: null } : null) : null,
       };
 
       // Client-side clamp defense: if split is false and preschool manualDuration was set to < 36, clamp to 36
@@ -698,6 +701,9 @@ export function ChildProfileSection() {
                             preschoolSplitEnabled: true,
                             preschoolStage1Duration: prev.preschoolStage1Duration ?? 12,
                             preschoolTrack2: prev.preschoolTrack2 ?? "INTL",
+                            preschoolOverride: prev.preschoolOverride
+                              ? { ...prev.preschoolOverride, manualDuration: null }
+                              : null,
                             preschoolOverride2: prev.preschoolOverride2 ?? null,
                           };
                         } else {
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                         10023  12.17G   9.10    21:42:31  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
index 856d7b07..c3bc3d6e 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
@@ -43,6 +43,7 @@ import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
+import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.StageOverride;
 import lombok.RequiredArgsConstructor;
 
 @Service
@@ -98,10 +99,20 @@ public class ChildEducationSettingsServiceImpl implements ChildEducationSettings
                     || (command.getPreschoolStage1Duration() != 12 && command.getPreschoolStage1Duration() != 24)) {
                 throw new IllegalArgumentException("preschoolStage1Duration must be either 12 or 24 months when preschoolSplitEnabled is true");
             }
+            // Domain invariant: In two-stage preschool mode, duration is strictly governed by
+            // preschoolStage1Duration (12 or 24) and stage 2 (36 - stage1).
+            // Sanitize manualDuration on both overrides to prevent stale single-track duration leakage.
+            if (settings.getPreschoolOverride() != null) {
+                settings.getPreschoolOverride().setManualDuration(null);
+            }
             settings.setPreschoolSplitEnabled(true);
             settings.setPreschoolStage1Duration(command.getPreschoolStage1Duration());
             settings.setPreschoolTrack2(command.getPreschoolTrack2());
-            settings.setPreschoolOverride2(command.getPreschoolOverride2() != null ? command.getPreschoolOverride2().toDomain() : null);
+            StageOverride override2 = command.getPreschoolOverride2() != null ? command.getPreschoolOverride2().toDomain() : null;
+            if (override2 != null) {
+                override2.setManualDuration(null);
+            }
+            settings.setPreschoolOverride2(override2);
         } else {
             if (command.getPreschoolOverride() != null
                     && command.getPreschoolOverride().getManualDuration() != null
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
index c88af492..d0f45894 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
@@ -257,6 +257,58 @@ class ChildEducationSettingsServiceImplTest {
         assertNotNull(saved.getPreschoolOverride2());
     }
 
+    @Test
+    void updateSettings_WhenPreschoolSplitEnabled_ClearsManualDurationOnBothOverrides() {
+        ChildEducationSettings existing = new ChildEducationSettings(1L);
+
+        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
+                .preschoolTrack(PreschoolTrack.DAYCARE)
+                .preschoolOverride(StageOverrideCommand.builder()
+                        .manualDuration(48)
+                        .manualTuition(new BigDecimal("500000"))
+                        .build())
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(12)
+                .preschoolTrack2(PreschoolTrack.INTL)
+                .preschoolOverride2(StageOverrideCommand.builder()
+                        .manualDuration(24)
+                        .manualTuition(new BigDecimal("1800000"))
+                        .build())
+                .build();
+
+        ResolvedEducationProfile resolvedProfile = new ResolvedEducationProfile(Collections.emptyMap());
+        EducationAbsoluteTimeline validTimeline = EducationAbsoluteTimeline.builder()
+                .events(Collections.emptyList())
+                .build();
+
+        when(childProfileRepository.findById(1L)).thenReturn(Optional.of(childProfile));
+        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(existing));
+        when(educationConfigService.getDefaultDomainConfig()).thenReturn(defaultConfig);
+        when(educationProfileResolver.resolve(any(), any())).thenReturn(resolvedProfile);
+        when(educationTimelineProjector.project(any(), any())).thenReturn(validTimeline);
+        when(settingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
+
+        ChildEducationSettingsResult result = service.updateSettings(1L, command);
+
+        assertNotNull(result);
+        assertTrue(result.getPreschoolSplitEnabled());
+        assertNotNull(result.getPreschoolOverride());
+        assertNull(result.getPreschoolOverride().getManualDuration(), "Stage 1 manualDuration must be sanitized to null in two-stage mode");
+        assertEquals(new BigDecimal("500000"), result.getPreschoolOverride().getManualTuition(), "Stage 1 manualTuition must be preserved");
+
+        assertNotNull(result.getPreschoolOverride2());
+        assertNull(result.getPreschoolOverride2().getManualDuration(), "Stage 2 manualDuration must be sanitized to null in two-stage mode");
+        assertEquals(new BigDecimal("1800000"), result.getPreschoolOverride2().getManualTuition(), "Stage 2 manualTuition must be preserved");
+
+        ArgumentCaptor<ChildEducationSettings> captor = ArgumentCaptor.forClass(ChildEducationSettings.class);
+        verify(settingsRepository).save(captor.capture());
+        ChildEducationSettings saved = captor.getValue();
+        assertNull(saved.getPreschoolOverride().getManualDuration());
+        assertEquals(new BigDecimal("500000"), saved.getPreschoolOverride().getManualTuition());
+        assertNull(saved.getPreschoolOverride2().getManualDuration());
+        assertEquals(new BigDecimal("1800000"), saved.getPreschoolOverride2().getManualTuition());
+    }
+
     @Test
     void updateSettings_WhenPreschoolSplitDisabled_ClearsTwoStageFields() {
         ChildEducationSettings existing = new ChildEducationSettings(1L);
diff --git a/finance-hub-backend/todo/pending fix/issue-135.md b/finance-hub-backend/todo/pending fix/issue-135.md
index 3e14a009..198b30f8 100644
--- a/finance-hub-backend/todo/pending fix/issue-135.md
+++ b/finance-hub-backend/todo/pending fix/issue-135.md
@@ -16,3 +16,73 @@
 
 ## 建議修正
 在前端`onPreschoolSplitChange`裡，開啟兩段式時應該連同`preschoolOverride`一併清空（或至少清空`manualDuration`這個欄位），避免殘留值在模式之間跨界復活；或者在後端`ChildEducationSettingsServiceImpl.java`的`preschoolSplitEnabled`為true分支裡，明確把`preschoolOverride`存成`null`，確保只要是兩段式模式，單軌專用的覆寫欄位就不該存在資料庫裡。兩種做法選一種即可，核心是「切換模式時，不適用於目前模式的欄位要被清空，不能悄悄留著等下次切換回去時復活」。
+
+---
+
+## 審查回饋與架構深度分析（Review & Architectural Analysis）
+
+經全面檢驗前後端原始碼與業務架構，**本 issue 描述之現象屬實（非幻覺），但 Claude 建議之二選一修正方案存在嚴重業務漏洞，不可全盤採納**。以下為詳細分析與正確方案制定：
+
+### 一、現象屬實查核（Bug 確實存在）
+1. **前端狀態殘留**：在 [child-profile-section.tsx:L693-L714](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L693-L714) 的 `onPreschoolSplitChange` 中，切換為兩段式（`checked = true`）時，確實保留了原本單軌的 `prev.preschoolOverride`（含 `manualDuration`，例如 48 個月）。
+2. **UI 視覺遮蔽**：在兩段式模式下，UI 將總時長硬性鎖定為唯讀的 `"3 Years (2-Stage Total)"`（= 36 個月，見 [child-profile-section.tsx:L1272](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L1272)），使用者在畫面上完全看不到 48 的存在。
+3. **後端持久化髒資料**：後端 [ChildEducationSettingsServiceImpl.java:L92](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java#L92) 在兩段式分支判斷前就無條件存入了 `command.getPreschoolOverride()`，導致資料庫中該筆記錄同時存在 `preschool_split_enabled = true` 與 `preschool_manual_duration = 48`。
+4. **悄悄復活**：當使用者日後關閉兩段式開關切回單軌時，該 48 個月會立刻在前端時間軸預覽與後端模擬計算中「重新生效」，確實會造成非預期的學制延長。
+
+---
+
+### 二、Claude 建議修正方案之重大漏洞分析（不可選方案二）
+
+Claude 在原文「建議修正」中提出「二選一」：
+- **選項一**：在前端 `onPreschoolSplitChange` 裡，開啟兩段式時應該連同 `preschoolOverride` 一併清空（或至少清空 `manualDuration` 這個欄位）。
+- **選項二**：在後端 `ChildEducationSettingsServiceImpl.java` 的 `preschoolSplitEnabled` 為 true 分支裡，明確把 `preschoolOverride` 存成 `null`，確保只要是兩段式模式，單軌專用的覆寫欄位就不該存在資料庫裡。
+
+#### 為什麼選項二（以及選項一前半段的「連同 preschoolOverride 一併清空」）是致命的架構誤解？
+1. **`preschoolOverride` 並非「單軌專用」**：
+   在系統架構中（見 [EducationProfileResolver.java:L115](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java#L115) 與前端 [child-profile-section.tsx:L1168-L1176](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L1168-L1176)），兩段式幼兒園架構為：
+   - **Stage 1（前段幼教）**：使用 `preschoolTrack`（例如 DAYCARE）與 **`preschoolOverride`**（負責前段的 `manualTuition` 自訂學費、`manualStartMonth` 入學月份等）。
+   - **Stage 2（後段幼教）**：使用 `preschoolTrack2`（例如 INTL）與 **`preschoolOverride2`**。
+2. **若採納選項二（後端把 `preschoolOverride` 存成 `null`）之惡果**：
+   使用者在兩段式模式下為前段（Stage 1）填寫的任何自訂學費（例如前段念昂貴私立托兒所每年 60 萬日圓），在存檔時會**被後端無差別清空成 `null`，造成嚴重的學費資料遺失 Bug**！
+
+---
+
+### 三、拍板修正方案：精確靶向清除 `manualDuration`
+
+在兩段式幼教模式下，總時長受 `preschoolStage1Duration`（12 或 24 個月）與剩餘月份約束，**唯一無效且不該殘留的欄位僅有 `manualDuration`**。
+
+#### 1. 前端（UI 交互防護）：[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)
+- 在 `onPreschoolSplitChange` 啟用兩段式時（`checked === true`），若 `prev.preschoolOverride` 存在，僅將其 `manualDuration` 設為 `null`，完整保留 `manualTuition`、`manualStartMonth` 等 Stage 1 參數：
+  ```typescript
+  preschoolOverride: prev.preschoolOverride ? { ...prev.preschoolOverride, manualDuration: null } : null,
+  ```
+
+#### 2. 後端（領域資料完整性防禦）：[ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)
+- 在 `preschoolSplitEnabled == true` 處理分支中，若 `preschoolOverride != null`，防禦性地執行：
+  ```java
+  preschoolOverride.setManualDuration(null);
+  ```
+  確保資料庫在兩段式模式下永遠不會殘留過期的總時長數值。
+
+---
+
+## 四、實作進度與修正報告（Progress & Fix Report）—— 已完成 (Completed)
+
+本任務已完成前後端精準聯防修正與單元測試覆蓋：
+
+### 1. 後端服務層防禦（`hub-app`）：
+- [ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)：
+  - 在 `preschoolSplitEnabled == true` 分支內，若 `settings.getPreschoolOverride() != null`，主動執行 `settings.getPreschoolOverride().setManualDuration(null)`。
+  - 對 `preschoolOverride2` 也同樣執行 `override2.setManualDuration(null)`，確保兩段式模式下任一階段的時長覆寫皆為 `null`，嚴守領域不變量（Domain Invariant）。
+  - 同時完整保留 `manualTuition`、`manualStartMonth`、`manualTermsPerYear` 等自訂學費與期數參數，杜絕資料抹殺漏洞。
+- [ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)：
+  - 新增單元測試 `updateSettings_WhenPreschoolSplitEnabled_ClearsManualDurationOnBothOverrides`：
+    - 模擬傳入含有 `manualDuration: 48, manualTuition: 500,000` 的 `preschoolOverride`，以及 `manualDuration: 24, manualTuition: 1,800,000` 的 `preschoolOverride2`。
+    - 驗證儲存至資料庫與回傳結果中，`manualDuration` 100% 被清理為 `null`，而 `manualTuition` 100% 完整保留。
+
+### 2. 前端 UI 狀態防禦（`hub-ui`）：
+- [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)：
+  - 在 `onPreschoolSplitChange` 啟用開關時，將 `preschoolOverride` 的 `manualDuration` 設為 `null`，保留其他 Stage 1 參數。
+  - 在 `handleSaveSettings` 構建 payload 的 `settingsToSave` 中建立雙重防禦：當 `splitEnabled === true` 時，將 `preschoolOverride.manualDuration` 與 `preschoolOverride2.manualDuration` 強制清理為 `null`，確保送出的網路請求不含過期時長髒資料。
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                              10024  12.19G   9.17    21:42:33  