# [Fixed] Feature Requirement 126：移除`PrimaryTrack`/`MiddleTrack`的`NONE`選項，收尾前端已經做過一半的義務教育防呆

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板：`PrimaryTrack`（小學）與`MiddleTrack`（國中）的`NONE`選項要移除；`HighTrack`（高中）維持現狀不動，不需要一併處理。

## 背景：這不是全新發現，是原開發者已做一半、後端沒收尾的清理工作
這是在討論「幼稚園沒有`NONE`選項」（該討論已結案，判定文件在`todo/wontfix`資料夾裡，內容是：幼稚園不新增`NONE`，維持用`HOME`表達沒讀）時，使用者延伸提出的問題：`NONE`這個選項的語意合理性，應該依照「這個階段是不是義務教育」來判斷。

已查證日本法律依據（憲法第26條第2項、學校教育法第16-17條）：監護人對子女負有具體的「就學義務」，範圍是小學（6年）＋國中（3年），從滿6歲到滿15歲那個學年結束為止；高中不在這個法律義務範圍內，是自願就讀。另外查證台灣「十二年國民基本教育」的法律定位：前九年（國小＋國中）仍為義務教育，後三年（高中）為非強制性——**日本跟台灣在「高中不是義務教育」這一點上結論一致**，`HighTrack`維持現狀不動是合理的，不需要跟著小學/國中一起處理。

深入查證後發現：**這其實不是原開發者沒想過的問題**——`hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:533`已經有這段註解：「Compulsory education stages (Primary, Middle, High) omit "NONE" to prevent...」，證明前端UI早就故意不把`NONE`這個選項放進小學/國中/高中的下拉選單裡，讓使用者透過畫面操作永遠選不到這個值。

問題在於後端沒有跟著收尾：
1. `PrimaryTrack`／`MiddleTrack`這兩個enum本身仍然保留`NONE`這個值。
2. `ChildEducationSettings.java`第67、81行，這兩個欄位的**預設值仍然是`NONE`**（`private PrimaryTrack primaryTrack = PrimaryTrack.NONE;`）——代表任何還沒被使用者透過UI正式填寫過的小孩紀錄，資料庫裡存的初始狀態，就是這個「監護人違反就學義務」的值。
3. `ChildEducationSettingsServiceImpl.java`第99、108行，有針對`NONE`的「防禦性」處理邏輯（選到NONE就清空對應的覆寫值跟附加費用），這是原開發者刻意留下、讓後端能安全處理這個內部狀態的機制，不是遺漏。

也就是說，前端已經把使用者能不能選到`NONE`這條路擋住了，但後端保留了`NONE`這個值本身、還把它設成預設值——這是一個「表面已擋、底層未清」的不徹底狀態，這次任務要做的，就是把這個清理工作收尾做完。

## 想要的行為（用白話講清楚）
小學、國中這兩個階段，不應該存在「沒讀」這個選項——現實中監護人沒有讓孩子讀完小學國中的合法選項，這個系統不該提供一個對應到違法情境的設定值。高中維持現狀，`NONE`繼續保留（代表這個小孩高中畢業後不繼續升學，或選擇不讀高中去工作），這是合法、現實中真實存在的情境。

## 相關檔案（供實作者定位）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/PrimaryTrack.java`（`NONE, PUBLIC, PRIVATE, INTL`，要移除`NONE`）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/MiddleTrack.java`（同上）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java:67, 81, 147, 149`（預設值目前是`NONE`，需要改成一個真實軌道，例如`PUBLIC`——這是最需要謹慎處理的部分，見下方「需要一併考慮的風險」）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java:99-114`（`NONE`的防禦性清空邏輯，移除enum值之後這段判斷本身也要跟著清掉）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java:105-140`（`resolvePrimary`／`resolveMiddle`，目前對`NONE`的`inactiveStage`分支處理，enum值移除後這段也要跟著調整）
- `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java:330-331`、`finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java:183-217`（既有測試裡對`PrimaryTrack.NONE`／`MiddleTrack.NONE`的斷言，enum值移除後這些測試需要跟著更新，不能留著引用已經不存在的enum值）

## 需要一併考慮的風險：既有資料相容性
如果直接從enum裡刪掉`NONE`這個值，任何資料庫裡已經存著`primaryTrack = 'NONE'`或`middleTrack = 'NONE'`的既有小孩紀錄（不管是因為系統剛好建立過、還是別的原因），在下次讀取時會因為enum反序列化失敗而整筆壞掉。使用者先前已經確認目前系統完全沒有deploy、資料都是測試用的舊資料，這個風險在使用者自己這台機器上影響應該不大，但實作者動手前，建議先確認一次目前資料庫裡有沒有小孩紀錄真的存著`NONE`這個值（例如查一下`primaryTrack`欄位的實際分布），避免改完enum之後才發現資料壞掉。

## 實作方向選項（列出優缺點，不代為決定）

**方向A：直接從enum刪除`NONE`，把`ChildEducationSettings`的預設值改成`PUBLIC`**
- 優點：最乾淨徹底，跟前端UI已經做的事情完全對齊，之後不會再有人誤以為`NONE`是小學/國中的合法選項。
- 缺點：如果既有資料裡真的有`NONE`值，需要先手動清理/轉換這些資料，否則會壞掉（見上方風險說明）。

**方向B：保留enum裡的`NONE`值（避免既有資料反序列化失敗的風險），但把`ChildEducationSettings`的預設值改成`PUBLIC`，並在後端加一層驗證，拒絕任何新的請求把`primaryTrack`/`middleTrack`設成`NONE`（回傳400錯誤）**
- 優點：不用擔心既有資料壞掉，改動風險較低。
- 缺點：enum本身還是留著這個「理論上存在但實際上禁止使用」的值，沒有徹底解決「這個選項存在」這個問題本身，只是多加一層擋在外面，跟`Plan-01.md`一貫追求資料模型乾淨的精神不完全一致。

建議實作者先確認既有資料庫裡`primaryTrack`/`middleTrack`欄位是否真的有`NONE`值存在，如果沒有（或使用者確認可以直接清空重建測試資料），方向A更乾淨；如果有既有資料需要保留、又不想手動處理，方向B更安全。

---

## 拍板決策與實作階段規劃

> [!NOTE]
> 使用者確認：資料庫不需考慮既有資料相容性問題（本機 H2 重建即可），採**方向 A（直接從 enum 刪除 `NONE`，乾淨徹底）**。

### 階段 1：領域模型與 Entity 預設值（Domain Layer）
- [x] **[PrimaryTrack.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/PrimaryTrack.java)**：
  - 移除 `NONE`，保留 `PUBLIC, PRIVATE, INTL`。
- [x] **[MiddleTrack.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/MiddleTrack.java)**：
  - 移除 `NONE`，保留 `PUBLIC, PRIVATE, INTL`。
- [x] **[ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)**：
  - `primaryTrack` 與 `middleTrack` 欄位預設值由 `NONE` 改為 `PUBLIC`。
  - 建構子 fallback 同步改為 `PUBLIC`。
- [x] **[ChildEducationSettingsTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettingsTest.java)**：
  - 更新預設值斷言為 `PUBLIC`。

### 階段 2：服務層與模擬計算引擎（Service & Engine Layer）
- [x] **[ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)**：
  - 移除國中小 `if (track == NONE)` 清空分支（已無此狀態），直接綁定 override（高中與大學仍保留）。
- [x] **[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)**：
  - `resolvePrimary` 與 `resolveMiddle`：
    - Fallback 改為 `PUBLIC`。
    - 移除 `if (track == NONE) return inactiveStage(...)` 與 switch 裡的 `case NONE -> null;`。
    - 確保國小與國中始終為 active stage。

### 階段 3：單元測試重構與全量回歸（Testing Layer）
- [x] **[ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)**：
  - 調整 `updateSettings_WhenTrackIsNone_ClearsOverrides()`，專注測試保留 `NONE` 的階段（`HighTrack.NONE` 與 `UniversityTrack.NONE`）。
- [x] **[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)**：
  - 調整 `shouldHandleNoneTrack()`，驗證 High/University 為 inactive，而 Primary/Middle 保持 active。
- [ ] **全量驗證**：
  - 執行 `mvn clean install` 確保全數測試通過。

### 階段 4：前端確認與收尾（Frontend Check & Wrap-up）
- [x] **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)**：
  - 確認前端選單已無 `NONE`，0 程式碼改動。


## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                    10032  13.41G   5.49    08:01:50  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
index 6bc867e2..856d7b07 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
@@ -41,8 +41,6 @@ import ch.lin.finance.hub.backend.api.common.exception.InvalidEducationTimelineE
 import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
-import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
-import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
 import lombok.RequiredArgsConstructor;
@@ -119,20 +117,12 @@ public class ChildEducationSettingsServiceImpl implements ChildEducationSettings
         if (command.getPrimaryTrack() != null) {
             settings.setPrimaryTrack(command.getPrimaryTrack());
         }
-        if (settings.getPrimaryTrack() == PrimaryTrack.NONE) {
-            settings.setPrimaryOverride(null);
-        } else {
-            settings.setPrimaryOverride(command.getPrimaryOverride() != null ? command.getPrimaryOverride().toDomain() : null);
-        }
+        settings.setPrimaryOverride(command.getPrimaryOverride() != null ? command.getPrimaryOverride().toDomain() : null);
 
         if (command.getMiddleTrack() != null) {
             settings.setMiddleTrack(command.getMiddleTrack());
         }
-        if (settings.getMiddleTrack() == MiddleTrack.NONE) {
-            settings.setMiddleOverride(null);
-        } else {
-            settings.setMiddleOverride(command.getMiddleOverride() != null ? command.getMiddleOverride().toDomain() : null);
-        }
+        settings.setMiddleOverride(command.getMiddleOverride() != null ? command.getMiddleOverride().toDomain() : null);
 
         if (command.getHighTrack() != null) {
             settings.setHighTrack(command.getHighTrack());
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
index 0e443e70..10995aac 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
@@ -29,6 +29,7 @@ import java.util.Collections;
 import java.util.EnumMap;
 import java.util.List;
 import java.util.Map;
+import java.util.Objects;
 import java.util.Set;
 
 import org.springframework.stereotype.Component;
@@ -80,17 +81,17 @@ public class EducationProfileResolver {
     }
 
     private ResolvedStageData resolvePreschool(ChildEducationSettings settings, EducationConfig config) {
+        Objects.requireNonNull(settings);
         // Preschool defaults to HOME (Home Care = 0 tuition, 36 months) when settings or track is omitted.
         // HOME represents the zero-cost baseline track for preschool; there is no NONE track.
-        PreschoolTrack track1 = settings != null && settings.getPreschoolTrack() != null
+        PreschoolTrack track1 = settings.getPreschoolTrack() != null
                 ? settings.getPreschoolTrack()
                 : PreschoolTrack.HOME;
 
         TrackConfig trackConfig1 = getPreschoolTrackConfig(config, track1);
-        StageOverride override1 = settings != null ? settings.getPreschoolOverride() : null;
+        StageOverride override1 = settings.getPreschoolOverride();
 
-        boolean isTwoStage = settings != null
-                && Boolean.TRUE.equals(settings.getPreschoolSplitEnabled())
+        boolean isTwoStage = Boolean.TRUE.equals(settings.getPreschoolSplitEnabled())
                 && settings.getPreschoolTrack2() != null;
 
         if (!isTwoStage) {
@@ -132,20 +133,19 @@ public class EducationProfileResolver {
             return null;
         }
         return switch (track) {
-            case HOME -> config.getPreschool().getHomeTrack();
-            case DAYCARE -> config.getPreschool().getDaycareTrack();
-            case INTL -> config.getPreschool().getIntlTrack();
+            case HOME ->
+                config.getPreschool().getHomeTrack();
+            case DAYCARE ->
+                config.getPreschool().getDaycareTrack();
+            case INTL ->
+                config.getPreschool().getIntlTrack();
         };
     }
 
     private ResolvedStageData resolvePrimary(ChildEducationSettings settings, EducationConfig config) {
         PrimaryTrack track = settings != null && settings.getPrimaryTrack() != null
                 ? settings.getPrimaryTrack()
-                : PrimaryTrack.NONE;
-
-        if (track == PrimaryTrack.NONE) {
-            return inactiveStage(EducationStage.PRIMARY, PrimaryTrack.NONE.name());
-        }
+                : PrimaryTrack.PUBLIC;
 
         TrackConfig trackConfig = null;
         if (config != null && config.getPrimary() != null) {
@@ -156,8 +156,6 @@ public class EducationProfileResolver {
                     config.getPrimary().getPrivateTrack();
                 case INTL ->
                     config.getPrimary().getIntlTrack();
-                case NONE ->
-                    null;
             };
         }
 
@@ -168,11 +166,7 @@ public class EducationProfileResolver {
     private ResolvedStageData resolveMiddle(ChildEducationSettings settings, EducationConfig config) {
         MiddleTrack track = settings != null && settings.getMiddleTrack() != null
                 ? settings.getMiddleTrack()
-                : MiddleTrack.NONE;
-
-        if (track == MiddleTrack.NONE) {
-            return inactiveStage(EducationStage.MIDDLE, MiddleTrack.NONE.name());
-        }
+                : MiddleTrack.PUBLIC;
 
         TrackConfig trackConfig = null;
         if (config != null && config.getMiddle() != null) {
@@ -183,8 +177,6 @@ public class EducationProfileResolver {
                     config.getMiddle().getPrivateTrack();
                 case INTL ->
                     config.getMiddle().getIntlTrack();
-                case NONE ->
-                    null;
             };
         }
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
index 6e6a67b5..c88af492 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
@@ -59,7 +59,6 @@ import ch.lin.finance.hub.backend.api.common.exception.InvalidEducationTimelineE
 import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
-import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
@@ -173,17 +172,15 @@ class ChildEducationSettingsServiceImplTest {
     void updateSettings_WhenTrackIsNone_ClearsOverridesAndExtraCosts() {
         // Pre-existing settings with overrides and extra costs populated
         ChildEducationSettings existingSettings = new ChildEducationSettings(1L);
-        existingSettings.setPrimaryTrack(PrimaryTrack.PUBLIC);
-        existingSettings.setPrimaryOverride(StageOverride.builder().manualDuration(72).build());
+        existingSettings.setHighTrack(HighTrack.PUBLIC);
+        existingSettings.setHighOverride(StageOverride.builder().manualDuration(36).build());
         existingSettings.setManualLivingCost(BigDecimal.valueOf(1200000));
         existingSettings.setIsAwayFromHome(true);
 
         // Command setting tracks to NONE
         UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
-                .primaryTrack(PrimaryTrack.NONE)
-                .primaryOverride(StageOverrideCommand.builder().manualDuration(72).build())
-                .middleTrack(MiddleTrack.NONE)
                 .highTrack(HighTrack.NONE)
+                .highOverride(StageOverrideCommand.builder().manualDuration(36).build())
                 .universityTrack(UniversityTrack.NONE)
                 .universityOverride(StageOverrideCommand.builder().manualDuration(48).build())
                 .manualLivingCost(BigDecimal.valueOf(1200000))
@@ -209,12 +206,6 @@ class ChildEducationSettingsServiceImplTest {
         verify(settingsRepository).save(captor.capture());
         ChildEducationSettings saved = captor.getValue();
 
-        assertEquals(PrimaryTrack.NONE, saved.getPrimaryTrack());
-        assertNull(saved.getPrimaryOverride());
-
-        assertEquals(MiddleTrack.NONE, saved.getMiddleTrack());
-        assertNull(saved.getMiddleOverride());
-
         assertEquals(HighTrack.NONE, saved.getHighTrack());
         assertNull(saved.getHighOverride());
 
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
index 0788dd69..4e30761e 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
@@ -318,13 +318,13 @@ class EducationProfileResolverTest {
     }
 
     @Test
-    @DisplayName("Should mark NONE track as inactive and HOME track as active with zero cost")
+    @DisplayName("Should mark NONE track as inactive and compulsory stages as active")
     void shouldHandleNoneTrack() {
         ChildEducationSettings settings = ChildEducationSettings.builder()
                 .childProfileId(1L)
                 .preschoolTrack(PreschoolTrack.HOME)
-                .primaryTrack(PrimaryTrack.NONE)
-                .middleTrack(MiddleTrack.NONE)
+                .primaryTrack(PrimaryTrack.PUBLIC)
+                .middleTrack(MiddleTrack.PUBLIC)
                 .highTrack(HighTrack.NONE)
                 .universityTrack(UniversityTrack.NONE)
                 .build();
@@ -336,13 +336,14 @@ class EducationProfileResolverTest {
         assertThat(profile.getStage(EducationStage.PRESCHOOL).isActive()).isTrue();
         assertThat(profile.getStage(EducationStage.PRESCHOOL).getAnnualTuition()).isEqualByComparingTo(BigDecimal.ZERO);
 
-        // Stages set to NONE are marked inactive
-        assertThat(profile.hasStage(EducationStage.PRIMARY)).isFalse();
-        assertThat(profile.getStage(EducationStage.PRIMARY).isActive()).isFalse();
+        // Compulsory stages remain active
+        assertThat(profile.hasStage(EducationStage.PRIMARY)).isTrue();
+        assertThat(profile.getStage(EducationStage.PRIMARY).isActive()).isTrue();
 
-        assertThat(profile.hasStage(EducationStage.MIDDLE)).isFalse();
-        assertThat(profile.getStage(EducationStage.MIDDLE).isActive()).isFalse();
+        assertThat(profile.hasStage(EducationStage.MIDDLE)).isTrue();
+        assertThat(profile.getStage(EducationStage.MIDDLE).isActive()).isTrue();
 
+        // Stages set to NONE are marked inactive
         assertThat(profile.hasStage(EducationStage.HIGH)).isFalse();
         assertThat(profile.getStage(EducationStage.HIGH).isActive()).isFalse();
 
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/MiddleTrack.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/MiddleTrack.java
index 3b36ea00..f85ff282 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/MiddleTrack.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/MiddleTrack.java
@@ -24,5 +24,5 @@
 package ch.lin.finance.hub.backend.api.domain.model.profile.child;
 
 public enum MiddleTrack {
-    NONE, PUBLIC, PRIVATE, INTL
+    PUBLIC, PRIVATE, INTL
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/PrimaryTrack.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/PrimaryTrack.java
index 93a433c2..756b256a 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/PrimaryTrack.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/PrimaryTrack.java
@@ -24,5 +24,5 @@
 package ch.lin.finance.hub.backend.api.domain.model.profile.child;
 
 public enum PrimaryTrack {
-    NONE, PUBLIC, PRIVATE, INTL
+    PUBLIC, PRIVATE, INTL
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
index 80af3925..4df627a9 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
@@ -28,13 +28,19 @@ import lombok.NoArgsConstructor;
 import lombok.Setter;
 
 /**
- * Child education settings entity representing an individual child's education tracks and overrides.
+ * Child education settings entity representing an individual child's education
+ * tracks and overrides.
  *
- * <p><b>Important Unit Conventions for Overrides:</b></p>
+ * <p>
+ * <b>Important Unit Conventions for Overrides:</b></p>
  * <ul>
- *   <li>Stage override {@code manualTuition} is <b>Annual Tuition</b> in JPY (e.g., 1,500,000 JPY/year).</li>
- *   <li>Stage override {@code manualDuration} is strictly in <b>Months</b> (e.g., 36 months; NOT in years).</li>
- *   <li>{@code manualLivingCost} is <b>Monthly Living Cost</b> in JPY (converted from user annual inputs by dividing by 12 on the client before persisting).</li>
+ * <li>Stage override {@code manualTuition} is <b>Annual Tuition</b> in JPY
+ * (e.g., 1,500,000 JPY/year).</li>
+ * <li>Stage override {@code manualDuration} is strictly in <b>Months</b> (e.g.,
+ * 36 months; NOT in years).</li>
+ * <li>{@code manualLivingCost} is <b>Monthly Living Cost</b> in JPY (converted
+ * from user annual inputs by dividing by 12 on the client before
+ * persisting).</li>
  * </ul>
  */
 @Entity
@@ -58,12 +64,17 @@ public class ChildEducationSettings extends AuditableEntity {
     private Long childProfileId;
 
     // --- Preschool ---
-    /** Preschool track selection (PreschoolTrack). */
+    /**
+     * Preschool track selection (PreschoolTrack).
+     */
     @Enumerated(EnumType.STRING)
     @Column(name = "preschool_track")
     private PreschoolTrack preschoolTrack = PreschoolTrack.HOME;
 
-    /** Preschool custom override (manualTuition is annual tuition; manualDuration is duration in months). */
+    /**
+     * Preschool custom override (manualTuition is annual tuition;
+     * manualDuration is duration in months).
+     */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "preschool_manual_tuition", precision = 19, scale = 4)),
@@ -73,20 +84,32 @@ public class ChildEducationSettings extends AuditableEntity {
     })
     private StageOverride preschoolOverride;
 
-    /** Whether preschool is split into two distinct stages (e.g. HOME first, then INTL/DAYCARE). */
+    /**
+     * Whether preschool is split into two distinct stages (e.g. HOME first,
+     * then INTL/DAYCARE).
+     */
     @Column(name = "preschool_split_enabled")
     private Boolean preschoolSplitEnabled = false;
 
-    /** Duration of stage 1 in months (strictly 12 or 24 months; stage 2 fills the remainder to 36 months). */
+    /**
+     * Duration of stage 1 in months (strictly 12 or 24 months; stage 2 fills
+     * the remainder to 36 months).
+     */
     @Column(name = "preschool_stage1_duration")
     private Integer preschoolStage1Duration;
 
-    /** Preschool track selection for stage 2 (PreschoolTrack: HOME, DAYCARE, INTL). */
+    /**
+     * Preschool track selection for stage 2 (PreschoolTrack: HOME, DAYCARE,
+     * INTL).
+     */
     @Enumerated(EnumType.STRING)
     @Column(name = "preschool_track2")
     private PreschoolTrack preschoolTrack2;
 
-    /** Preschool custom override for stage 2 (manualTuition is annual tuition; manualDuration is duration in months). */
+    /**
+     * Preschool custom override for stage 2 (manualTuition is annual tuition;
+     * manualDuration is duration in months).
+     */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "preschool_manual_tuition2", precision = 19, scale = 4)),
@@ -97,12 +120,17 @@ public class ChildEducationSettings extends AuditableEntity {
     private StageOverride preschoolOverride2;
 
     // --- Primary ---
-    /** Primary school track selection (PrimaryTrack). */
+    /**
+     * Primary school track selection (PrimaryTrack).
+     */
     @Enumerated(EnumType.STRING)
     @Column(name = "primary_track")
-    private PrimaryTrack primaryTrack = PrimaryTrack.NONE;
+    private PrimaryTrack primaryTrack = PrimaryTrack.PUBLIC;
 
-    /** Primary school custom override (manualTuition is annual tuition; manualDuration is duration in months, e.g., 72). */
+    /**
+     * Primary school custom override (manualTuition is annual tuition;
+     * manualDuration is duration in months, e.g., 72).
+     */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "primary_manual_tuition", precision = 19, scale = 4)),
@@ -113,12 +141,17 @@ public class ChildEducationSettings extends AuditableEntity {
     private StageOverride primaryOverride;
 
     // --- Middle ---
-    /** Middle school track selection (MiddleTrack). */
+    /**
+     * Middle school track selection (MiddleTrack).
+     */
     @Enumerated(EnumType.STRING)
     @Column(name = "middle_track")
-    private MiddleTrack middleTrack = MiddleTrack.NONE;
+    private MiddleTrack middleTrack = MiddleTrack.PUBLIC;
 
-    /** Middle school custom override (manualTuition is annual tuition; manualDuration is duration in months, e.g., 36). */
+    /**
+     * Middle school custom override (manualTuition is annual tuition;
+     * manualDuration is duration in months, e.g., 36).
+     */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "middle_manual_tuition", precision = 19, scale = 4)),
@@ -129,12 +162,17 @@ public class ChildEducationSettings extends AuditableEntity {
     private StageOverride middleOverride;
 
     // --- High ---
-    /** High school track selection (HighTrack). */
+    /**
+     * High school track selection (HighTrack).
+     */
     @Enumerated(EnumType.STRING)
     @Column(name = "high_track")
     private HighTrack highTrack = HighTrack.NONE;
 
-    /** High school custom override (manualTuition is annual tuition; manualDuration is duration in months, e.g., 36). */
+    /**
+     * High school custom override (manualTuition is annual tuition;
+     * manualDuration is duration in months, e.g., 36).
+     */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "high_manual_tuition", precision = 19, scale = 4)),
@@ -145,12 +183,17 @@ public class ChildEducationSettings extends AuditableEntity {
     private StageOverride highOverride;
 
     // --- University ---
-    /** University track selection (UniversityTrack). */
+    /**
+     * University track selection (UniversityTrack).
+     */
     @Enumerated(EnumType.STRING)
     @Column(name = "university_track")
     private UniversityTrack universityTrack = UniversityTrack.NONE;
 
-    /** University custom override (manualTuition is annual tuition; manualDuration is duration in months, e.g., 36 or 48). */
+    /**
+     * University custom override (manualTuition is annual tuition;
+     * manualDuration is duration in months, e.g., 36 or 48).
+     */
     @Embedded
     @AttributeOverrides({
         @AttributeOverride(name = "manualTuition", column = @Column(name = "uni_manual_tuition", precision = 19, scale = 4)),
@@ -161,13 +204,17 @@ public class ChildEducationSettings extends AuditableEntity {
     private StageOverride universityOverride;
 
     /**
-     * University custom MONTHLY living cost in JPY when studying abroad or living away from home.
-     * (Under Issue 110 specification, the frontend converts annual user input to monthly before submission).
+     * University custom MONTHLY living cost in JPY when studying abroad or
+     * living away from home. (Under Issue 110 specification, the frontend
+     * converts annual user input to monthly before submission).
      */
     @Column(name = "uni_manual_living_cost", precision = 19, scale = 4)
     private BigDecimal manualLivingCost;
 
-    /** Whether the student lives away from home or studies abroad during university. */
+    /**
+     * Whether the student lives away from home or studies abroad during
+     * university.
+     */
     @Column(name = "is_away_from_home")
     private Boolean isAwayFromHome = false;
 
@@ -205,9 +252,9 @@ public class ChildEducationSettings extends AuditableEntity {
         this.preschoolStage1Duration = preschoolStage1Duration;
         this.preschoolTrack2 = preschoolTrack2;
         this.preschoolOverride2 = preschoolOverride2;
-        this.primaryTrack = primaryTrack != null ? primaryTrack : PrimaryTrack.NONE;
+        this.primaryTrack = primaryTrack != null ? primaryTrack : PrimaryTrack.PUBLIC;
         this.primaryOverride = primaryOverride;
-        this.middleTrack = middleTrack != null ? middleTrack : MiddleTrack.NONE;
+        this.middleTrack = middleTrack != null ? middleTrack : MiddleTrack.PUBLIC;
         this.middleOverride = middleOverride;
         this.highTrack = highTrack != null ? highTrack : HighTrack.NONE;
         this.highOverride = highOverride;
diff --git a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettingsTest.java b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettingsTest.java
index 9b31787b..4306074d 100644
--- a/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettingsTest.java
+++ b/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettingsTest.java
@@ -31,6 +31,7 @@ import static org.junit.jupiter.api.Assertions.assertNull;
 import static org.junit.jupiter.api.Assertions.assertTrue;
 import org.junit.jupiter.api.Test;
 
+import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
 
@@ -47,7 +48,8 @@ class ChildEducationSettingsTest {
         assertNull(settings.getPreschoolStage1Duration());
         assertNull(settings.getPreschoolTrack2());
         assertNull(settings.getPreschoolOverride2());
-        assertEquals(PrimaryTrack.NONE, settings.getPrimaryTrack());
+        assertEquals(PrimaryTrack.PUBLIC, settings.getPrimaryTrack());
+        assertEquals(MiddleTrack.PUBLIC, settings.getMiddleTrack());
         assertFalse(settings.getIsAwayFromHome());
     }
 
diff --git a/finance-hub-backend/todo/pending fix/feature-requirement-126.md b/finance-hub-backend/todo/pending fix/feature-requirement-126.md
index 9a7fb6ba..db4d15b9 100644
--- a/finance-hub-backend/todo/pending fix/feature-requirement-126.md
+++ b/finance-hub-backend/todo/pending fix/feature-requirement-126.md
@@ -42,3 +42,43 @@
 - 缺點：enum本身還是留著這個「理論上存在但實際上禁止使用」的值，沒有徹底解決「這個選項存在」這個問題本身，只是多加一層擋在外面，跟`Plan-01.md`一貫追求資料模型乾淨的精神不完全一致。
 
 建議實作者先確認既有資料庫裡`primaryTrack`/`middleTrack`欄位是否真的有`NONE`值存在，如果沒有（或使用者確認可以直接清空重建測試資料），方向A更乾淨；如果有既有資料需要保留、又不想手動處理，方向B更安全。
+
+---
+
+## 拍板決策與實作階段規劃
+
+> [!NOTE]
+> 使用者確認：資料庫不需考慮既有資料相容性問題（本機 H2 重建即可），採**方向 A（直接從 enum 刪除 `NONE`，乾淨徹底）**。
+
+### 階段 1：領域模型與 Entity 預設值（Domain Layer）
+- [x] **[PrimaryTrack.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/PrimaryTrack.java)**：
+  - 移除 `NONE`，保留 `PUBLIC, PRIVATE, INTL`。
+- [x] **[MiddleTrack.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/MiddleTrack.java)**：
+  - 移除 `NONE`，保留 `PUBLIC, PRIVATE, INTL`。
+- [x] **[ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)**：
+  - `primaryTrack` 與 `middleTrack` 欄位預設值由 `NONE` 改為 `PUBLIC`。
+  - 建構子 fallback 同步改為 `PUBLIC`。
+- [x] **[ChildEducationSettingsTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettingsTest.java)**：
+  - 更新預設值斷言為 `PUBLIC`。
+
+### 階段 2：服務層與模擬計算引擎（Service & Engine Layer）
+- [x] **[ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)**：
+  - 移除國中小 `if (track == NONE)` 清空分支（已無此狀態），直接綁定 override（高中與大學仍保留）。
+- [x] **[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)**：
+  - `resolvePrimary` 與 `resolveMiddle`：
+    - Fallback 改為 `PUBLIC`。
+    - 移除 `if (track == NONE) return inactiveStage(...)` 與 switch 裡的 `case NONE -> null;`。
+    - 確保國小與國中始終為 active stage。
+
+### 階段 3：單元測試重構與全量回歸（Testing Layer）
+- [x] **[ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)**：
+  - 調整 `updateSettings_WhenTrackIsNone_ClearsOverrides()`，專注測試保留 `NONE` 的階段（`HighTrack.NONE` 與 `UniversityTrack.NONE`）。
+- [x] **[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)**：
+  - 調整 `shouldHandleNoneTrack()`，驗證 High/University 為 inactive，而 Primary/Middle 保持 active。
+- [ ] **全量驗證**：
+  - 執行 `mvn clean install` 確保全數測試通過。
+
+### 階段 4：前端確認與收尾（Frontend Check & Wrap-up）
+- [x] **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)**：
+  - 確認前端選單已無 `NONE`，0 程式碼改動。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                         10033  13.13G   5.49    08:02:02  