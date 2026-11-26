# [Fixed] Feature Requirement 125：幼稚園階段支援「前段HOME、後段換軌道」這種一次性切換，其他階段不需要

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板：幼稚園這個階段的彈性需求是真實存在的（例如K3那一年完全在家不去念、K4/K5才開始去讀國際學校），需要支援。小學、國中、高中、大學這四個階段，不需要支援中途換軌道——使用者已經明確否定「每年都能自由設定」這種細顆粒度方向，理由是這樣做UX會很差、而且人生沒辦法被預先規劃到那個精確度，連使用者自己都不會想每次測試都重新輸入一次。

這裡只需要決定「怎麼做」，不需要決定「要不要做」的方向清單裡，不該出現「維持現狀不支援」這個選項。

## 背景與範圍界定
這是在討論「同一學制階段中途換軌道」這個更大範圍問題時（該討論的判定文件在`todo/wontfix`資料夾裡，內容是：所有階段都支援中途換軌道、以及「延後入學」起點跟長度分開控制，這兩個方向最後都被使用者確認不需要處理、已經結案），使用者進一步縮小範圍後拍板的具體任務。這份文件單獨處理其中已經確定要做的那一小塊：**只有幼稚園這一個階段，支援「切一刀、分成前後兩段、各自選軌道」，且只支援一次切換（不需要支援切兩刀、三刀）**。

## 想要的行為（用白話講清楚，不涉及術語）
幼稚園目前只能整段選一種軌道（HOME在家／DAYCARE托兒所／INTL國際學校），從滿3歲一路到滿6歲小學為止。希望改成：可以選擇「先在家一段時間，之後再換成另一種軌道念到小學為止」——例如K3在家、K4/K5去念國際學校；或者K3/K4在家、K5才去念。這個「什麼時候換」的時間點，家長自己指定即可，不需要系統去猜。

## 相關檔案（供實作者定位）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java`（第50-62行，`preschoolTrack`＋`preschoolOverride`，目前幼稚園只有這一組欄位）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java`（`resolvePreschool`，第82-103行，決定幼稚園最終要用哪個軌道跟哪些覆寫值的地方）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java`（`project`第62-100行、`calculateStageStart`第102-121行，目前排積木邏輯是「一個階段對應一段連續區間」，幼稚園如果要分兩段，這裡需要能對同一個階段排出兩個連續但軌道不同的區間）
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx`（幼稚園那張卡片的UI，目前是「選軌道＋手動金額＋進階設定」，需要新增「這個階段要不要分兩段」的切換與第二段的設定入口）

## 實作方向選項（列出優缺點，不代為決定）

**方向A：幼稚園新增一組獨立的「第二段」欄位（例如`preschoolTrack2`＋`preschoolOverride2`＋一個切換月份），其他階段不受影響**
- 優點：改動範圍最小、最集中，只動幼稚園這一個階段的資料結構跟排積木邏輯，`PRIMARY`/`MIDDLE`/`HIGH`/`UNIVERSITY`四個階段完全不用碰。
- 缺點：資料結構上，幼稚園跟其他四個階段變得不對稱（幼稚園有兩組軌道欄位，其他階段只有一組），如果之後又有類似需求出現在其他階段，這個做法沒辦法直接複用，得重新設計。

**方向B：把`EducationStage`的排積木邏輯，改成通用支援「一個階段可以有1到N個子區段」，這次只在幼稚園實際使用第2段，其他階段暫時維持只用第1段**
- 優點：資料結構跟排積木邏輯一次做成通用的，如果之後真的需要在其他階段支援類似需求（不在這次範圍內，但保留可能性），不需要再重新設計一次。
- 缺點：工程量比方向A大很多，等於是先把上述已結案討論裡提到的「架構層級重新設計」做出通用版本，只是這次先只在幼稚園這裡真正用到——如果之後確定其他階段真的不會用到這個彈性，這部分投入可能是超前部署、目前用不到的複雜度。

建議實作者先跟使用者確認：這次要的是「只解決幼稚園這一個具體需求」（傾向方向A，改動小、風險低），還是「順便把架構做成通用、為將來可能的類似需求鋪路」（傾向方向B），這是實作規模的取捨，由使用者決定。

---

## 拍板定案方向與架構設計（Design Decision & Technical Spec）

經過評估與討論，確認採用**方向 A（專為幼稚園擴充兩段式欄位與排程切換，其餘小學至大學階段完全維持現狀）**。

### 一、核心數學模型：瓜分幼稚園 3 年（36 個月）
幼稚園標準入學年齡為滿 3 歲（K3），小學法定入學年齡為滿 6 歲。
在不延後入學的標準情況下，幼稚園總時長為固定 **36 個月（3 年）**。
兩段式之本質為**瓜分此 36 個月**：
$$\text{第一段時長 } D_1 + \text{第二段時長 } D_2 = 36 \text{ 個月}$$

---

### 二、防呆校正與容錯規則（Domain Rules & Normalization）

為避免時間軸排積木時產生「真空斷層（Vacuum Gap）」，且完全符合真實育兒經驗（小孩在 K5 畢業年前夕不會無故中退回家蹲），後端引擎採取以下規則：

#### 1. 單軌模式（未啟用兩段式 / 未指定第二段）
- **儲存層嚴格防呆（Service Validation）**：
  若前端或 API 傳入小於 36 個月的時長（例如誤傳 INTL 24 個月、DAYCARE 12 個月），儲存層直接拋出 `IllegalArgumentException`（400 錯誤），拒絕髒資料入庫。
- **計算層保底原則（Clamping - 縱深防禦）**：
  計算引擎強制 $\text{duration} = \max(36, \text{duration})$，自動念滿 3 年，絕不提前結束讓時間軸產生真空。
- **支援延後入學**：若輸入 $\ge 36$ 個月（例如 48 個月），仍依指定時長延後接軌小學。

#### 2. 兩段模式（啟用兩段式並指定第二段）
- **儲存層嚴格防呆（Service Validation）**：
  - 若 `preschoolSplitEnabled == true` 但 `preschoolTrack2 == null`，直接拋出 `IllegalArgumentException` 拒絕儲存。
  - 第一段時長必須嚴格為 12 或 24 個月，其餘無效值直接拋錯。
- **互補補滿原則**：
  第二段時長自動設為 $36 - D_1$ 個月（即 2 年或 1 年），無縫頂到小學 6 歲入學點。
- **計算層二次防禦**：
  若底層計算遭遇未預期的缺漏值，自動 fallback 為單軌滿 36 個月模式，保護數值模擬引擎絕不拋 Exception。

---

### 三、改動範圍與具體設計（Scope of Work）

#### 1. 資料持久層（Domain & DB）
- [x] **[ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)**
  在 `finance_child_education_settings` 表新增以下欄位（皆為 nullable，100% 向後相容）：
  - `preschoolSplitEnabled` (Boolean)：是否啟用幼兒園兩段式（預設 false）。
  - `preschoolStage1Duration` (Integer)：第一段就讀時長（月數，例如 12 或 24）。
  - `preschoolTrack2` (PreschoolTrack)：第二段就讀軌道（HOME, DAYCARE, INTL）。
  - `preschoolOverride2` (StageOverride embedded)：第二段的自訂學費與覆寫值。
  - 保留既有建構子重載，確保向前與向後 100% 相容。
  - 新增領域單元測試：[ChildEducationSettingsTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettingsTest.java)。


#### 2. 服務層（Command / Result）與 控制層（DTO / Mapper）
- [x] **服務層 Command 與 Result**：
  - **[UpdateChildEducationSettingsCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java)**：新增 `preschoolSplitEnabled`、`preschoolStage1Duration`、`preschoolTrack2`、`preschoolOverride2`（型別為 `StageOverrideCommand`）。
  - **[ChildEducationSettingsResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java)**：新增上述 4 個欄位，並更新 `fromEntity(...)` 工廠方法。
  - **[ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)**：在 `updateSettings(...)` 內將 Command 的新欄位設定進 Entity。
  - 新增服務層單元測試：[ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)。
- [x] **Web 控制層 Request / Response / Mapper**：
  - **[UpdateChildEducationSettingsRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java)**：新增上述 4 個請求欄位。
  - **[ChildEducationSettingsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java)**：新增上述 4 個回應欄位。
  - **[ChildEducationSettingsMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsMapper.java)**：確認 MapStruct 正確自動映射 Request $\to$ Command 以及 Result $\to$ Response。
  - 新增控制層 MockMvc 測試：[ChildEducationSettingsControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java)。


#### 3. 核心排程與計算引擎
- [x] **[ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)**：
  - 新增 `toBuilder = true`、`stage2` 欄位與 `hasStage2()` 輔助方法。
- [x] **[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)**：
  - 在 `resolvePreschool` 中依 `preschoolSplitEnabled` 解析出兩段的軌道與設定。
  - 單軌模式落實 Clamping 保底原則：時長強制至少 36 個月（$\max(36, \text{duration})$）。
  - 兩段模式落實 $D_1 \in \{12, 24\}$ 且 $D_2 = 36 - D_1$，並支援第二段覆寫學費與 fallback 防禦。
  - 新增單元測試：[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)。
- [x] **[EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)**：
  - 幼稚園在 `timeline.getStageIntervals()` 中依然維持單一連續區間（滿 3 歲到滿 6 歲，生活費為 0）。
  - 在產生學費繳款事件（`generateStageEvents`）時：
    - 前 $D_1$ 個月按第一段軌道產生事件（若為 HOME 則金額為 0）。
    - 第 $D_1 + 1$ 個月（切換月）起，按第二段軌道產生一次性入學金（若有）與後續學費事件。
  - 新增時間軸排程單元測試：[EducationTimelineProjectorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java)。
- [x] **下游無感**：
  資產模擬引擎、GA / Grid Search、年度現金流計算完全零改動。

#### 4. 前端 UI 設計（[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)）
- [x] **[use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)**：
  - 擴充 `ChildEducationSettingsDto`，新增 `preschoolSplitEnabled`, `preschoolStage1Duration`, `preschoolTrack2`, `preschoolOverride2` 四個欄位。
- [x] **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)**：
  - 幼兒園（Preschool）卡片新增 Switch「中途更換軌道（Two-stage）」開關。
  - **開關關閉（預設）**：
    - 顯示單一軌道選單（HOME / DAYCARE / INTL），時長固定為 3 年。
    - Advanced 中的 Duration 只能延長（$\ge 3$ 年），不提供小於 3 年的輸入（輸入小於 3 自動 Clamp 為 3）。
  - **開關開啟**：
    - 第一段：軌道選單 + 時長下拉選單（【1 年 (12 個月)】/【2 年 (24 個月)】）+ 第一段自訂學費。
    - 第二段：銜接軌道選單 + 時長唯讀顯示（*「2 年（直到小學）」* 或 *「1 年（直到小學）」*）+ 第二段自訂學費（`preschoolOverride2.manualTuition`）。
    - Advanced 中的 Duration 自動鎖定顯示為 `3 Years (2-Stage Total)`，避免手動輸入與第一段時長產生衝突。
  - **前端嚴格防呆與資料清理**：
    - 切換關閉時自動重設清空第二段軌道與時長欄位為 `null`。
    - 切換開啟時預設第一段時長 12 個月，第二段軌道 INTL。
    - 儲存前雙重防禦檢查：兩段模式必填 track2 與合規時長（12 或 24）；單軌模式若時長 < 36 自動 Clamp 為 36。
- [x] **其他階段隔離**：
  - 小學、國中、高中、大學卡片維持原狀，完全不受干擾。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                 10026  13.18G   5.71    21:42:56  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index c5d2f6d..05eb59e 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -38,10 +38,9 @@ export function resolveTrackDefaults(
   switch (stageKey) {
     case 'preschool': {
       let trackConfig: TrackConfigDto | undefined;
-      let standardEntranceAge = 3;
+      const standardEntranceAge = 3;
       if (track === 'DAYCARE') {
         trackConfig = config?.preschool?.daycareTrack;
-        standardEntranceAge = 1;
       } else if (track === 'INTL') {
         trackConfig = config?.preschool?.intlTrack;
       } else {
@@ -49,7 +48,7 @@ export function resolveTrackDefaults(
       }
       return {
         defaultStartMonth: trackConfig?.startMonth ?? (track === 'INTL' ? 9 : 4),
-        defaultDurationMonths: trackConfig?.duration ?? (track === 'DAYCARE' ? 60 : 36),
+        defaultDurationMonths: Math.max(36, trackConfig?.duration ?? 36),
         standardEntranceAge,
       };
     }
@@ -188,6 +187,10 @@ export function ChildProfileSection() {
     });
     setEditingSettings({
       preschoolTrack: "HOME",
+      preschoolSplitEnabled: false,
+      preschoolStage1Duration: null,
+      preschoolTrack2: null,
+      preschoolOverride2: null,
       primaryTrack: "PUBLIC",
       middleTrack: "PUBLIC",
       highTrack: "PUBLIC",
@@ -228,6 +231,10 @@ export function ChildProfileSection() {
         setEditingSettings({
           childProfileId: child.id,
           preschoolTrack: "HOME",
+          preschoolSplitEnabled: false,
+          preschoolStage1Duration: null,
+          preschoolTrack2: null,
+          preschoolOverride2: null,
           primaryTrack: "PUBLIC",
           middleTrack: "PUBLIC",
           highTrack: "PUBLIC",
@@ -265,6 +272,24 @@ export function ChildProfileSection() {
     });
   };
 
+  const updatePreschoolOverride2 = (
+    field: keyof StageOverrideDto,
+    value: any
+  ) => {
+    setEditingSettings(prev => {
+      if (!prev) return null;
+      const currentOverride = prev.preschoolOverride2 || {};
+      const updatedOverride = {
+        ...currentOverride,
+        [field]: value === "" ? null : value
+      };
+      return {
+        ...prev,
+        preschoolOverride2: updatedOverride
+      };
+    });
+  };
+
   // Live timeline calculation based on backend config & stage overrides
   const timelineInfo = useMemo(() => {
     if (!editingChild?.birthDate) {
@@ -325,7 +350,14 @@ export function ChildProfileSection() {
       }
 
       const startMonth = stage.override?.manualStartMonth || stage.defaultStartMonth;
-      const durationMonths = stage.override?.manualDuration || stage.defaultDurationMonths;
+      let durationMonths = stage.override?.manualDuration || stage.defaultDurationMonths;
+      if (stage.key === 'preschool') {
+        if (editingSettings?.preschoolSplitEnabled) {
+          durationMonths = 36;
+        } else {
+          durationMonths = Math.max(36, durationMonths);
+        }
+      }
       const cohortYear = computeCohortYear(startMonth);
       const standardStartYear = cohortYear + stage.standardEntranceAge;
       const standardStartAbsMonths = standardStartYear * 12 + startMonth;
@@ -406,12 +438,22 @@ export function ChildProfileSection() {
         ? Math.ceil(annualNum / 12)
         : null;
 
+      const splitEnabled = !!editingSettings.preschoolSplitEnabled;
       const settingsToSave: ChildEducationSettingsDto = {
         ...editingSettings,
         childProfileId: childId,
         manualLivingCost: monthlyLivingCost,
+        preschoolSplitEnabled: splitEnabled,
+        preschoolStage1Duration: splitEnabled ? (editingSettings.preschoolStage1Duration ?? 12) : null,
+        preschoolTrack2: splitEnabled ? (editingSettings.preschoolTrack2 ?? "INTL") : null,
+        preschoolOverride2: splitEnabled ? editingSettings.preschoolOverride2 : null,
       };
 
+      // Client-side clamp defense: if split is false and preschool manualDuration was set to < 36, clamp to 36
+      if (!splitEnabled && settingsToSave.preschoolOverride?.manualDuration != null && settingsToSave.preschoolOverride.manualDuration < 36) {
+        settingsToSave.preschoolOverride.manualDuration = 36;
+      }
+
       const settingsSaved = await saveEducationSettings(childId, settingsToSave);
       if (!settingsSaved) {
         // If settings failed due to validation, keep dialog open
@@ -562,7 +604,7 @@ export function ChildProfileSection() {
                 <div className="space-y-4">
                   {/* 1. Preschool */}
                   <StageCard
-                    title="Preschool (0-5)"
+                    title="Preschool (3-5)"
                     stageKey="preschool"
                     timeline={timelineInfo.stages.preschool}
                     isExpanded={!!expandedStages.preschool}
@@ -581,6 +623,39 @@ export function ChildProfileSection() {
                     ]}
                     override={editingSettings?.preschoolOverride}
                     onOverrideChange={(field, val) => updateStageOverride('preschoolOverride', field, val)}
+                    preschoolSplitEnabled={!!editingSettings?.preschoolSplitEnabled}
+                    onPreschoolSplitChange={(checked) => {
+                      setEditingSettings(prev => {
+                        if (!prev) return null;
+                        if (checked) {
+                          return {
+                            ...prev,
+                            preschoolSplitEnabled: true,
+                            preschoolStage1Duration: prev.preschoolStage1Duration ?? 12,
+                            preschoolTrack2: prev.preschoolTrack2 ?? "INTL",
+                            preschoolOverride2: prev.preschoolOverride2 ?? null,
+                          };
+                        } else {
+                          return {
+                            ...prev,
+                            preschoolSplitEnabled: false,
+                            preschoolStage1Duration: null,
+                            preschoolTrack2: null,
+                            preschoolOverride2: null,
+                          };
+                        }
+                      });
+                    }}
+                    preschoolStage1Duration={editingSettings?.preschoolStage1Duration ?? 12}
+                    onPreschoolStage1DurationChange={(duration) => {
+                      setEditingSettings(prev => prev ? { ...prev, preschoolStage1Duration: duration } : null);
+                    }}
+                    preschoolTrack2Value={editingSettings?.preschoolTrack2 || "INTL"}
+                    onPreschoolTrack2Change={(track2) => {
+                      setEditingSettings(prev => prev ? { ...prev, preschoolTrack2: track2 } : null);
+                    }}
+                    preschoolOverride2={editingSettings?.preschoolOverride2}
+                    onPreschoolOverride2Change={(field, val) => updatePreschoolOverride2(field, val)}
                   />
 
                   {/* 2. Primary */}
@@ -746,7 +821,7 @@ export function ChildProfileSection() {
               {hasExtendedStages && (
                 <span className="text-xs text-amber-600 dark:text-amber-400 font-medium flex items-center gap-1">
                   <AlertTriangle className="w-3.5 h-3.5" />
-                  學制年限自訂延長，後續階段將由系統自動順延排程
+                  Custom duration extended; subsequent stages are automatically scheduled later
                 </span>
               )}
             </div>
@@ -865,6 +940,14 @@ interface StageCardProps {
   onOverrideChange: (field: keyof StageOverrideDto, val: any) => void;
   extraField?: React.ReactNode;
   universitySettings?: React.ReactNode;
+  preschoolSplitEnabled?: boolean;
+  onPreschoolSplitChange?: (enabled: boolean) => void;
+  preschoolStage1Duration?: number;
+  onPreschoolStage1DurationChange?: (duration: number) => void;
+  preschoolTrack2Value?: string;
+  onPreschoolTrack2Change?: (val: string) => void;
+  preschoolOverride2?: StageOverrideDto | null;
+  onPreschoolOverride2Change?: (field: keyof StageOverrideDto, val: any) => void;
 }
 
 function StageCard({
@@ -879,9 +962,19 @@ function StageCard({
   override,
   onOverrideChange,
   extraField,
-  universitySettings
+  universitySettings,
+  preschoolSplitEnabled,
+  onPreschoolSplitChange,
+  preschoolStage1Duration,
+  onPreschoolStage1DurationChange,
+  preschoolTrack2Value,
+  onPreschoolTrack2Change,
+  preschoolOverride2,
+  onPreschoolOverride2Change,
 }: StageCardProps) {
   const isNone = trackValue === 'NONE';
+  const stage1DurationMonths = preschoolStage1Duration === 24 ? 24 : 12;
+  const stage2DurationText = stage1DurationMonths === 24 ? "1 Year (Until Primary)" : "2 Years (Until Primary)";
 
   return (
     <div className="p-3 rounded-lg border transition-all border-border/70 bg-card hover:border-border">
@@ -917,12 +1010,31 @@ function StageCard({
         )}
       </div>
 
+      {/* Preschool Two-stage Switch */}
+      {stageKey === 'preschool' && (
+        <div className="flex items-center justify-between p-2 mb-2 rounded-md bg-muted/40 border border-border/50">
+          <div className="space-y-0.5">
+            <Label htmlFor="preschool-split-switch" className="text-xs font-semibold cursor-pointer">
+              Two-stage Track Transition
+            </Label>
+            <p className="text-[11px] text-muted-foreground">
+              Optionally switch education track mid-way at age 4 or 5 during preschool (ages 3–6)
+            </p>
+          </div>
+          <Switch
+            id="preschool-split-switch"
+            checked={!!preschoolSplitEnabled}
+            onCheckedChange={(checked) => onPreschoolSplitChange?.(checked)}
+          />
+        </div>
+      )}
+
       {/* Informative Notice for Extended Duration or Delayed Start */}
       {timeline?.isExtendedDuration && (
         <div className="mb-2 p-2 rounded bg-amber-500/10 border border-amber-500/20 text-amber-700 dark:text-amber-300 text-xs flex items-start gap-1.5 animate-in fade-in">
           <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
           <span>
-            就讀時長（{timeline.durationYears} 年）超過標準學制（{timeline.standardDurationYears} 年），後續教育階段將自動依序向後順延。
+            Duration ({timeline.durationYears}y) exceeds standard curriculum ({timeline.standardDurationYears}y). Subsequent stages are automatically scheduled later.
           </span>
         </div>
       )}
@@ -930,35 +1042,137 @@ function StageCard({
         <div className="mb-2 p-2 rounded bg-blue-500/10 border border-blue-500/20 text-blue-700 dark:text-blue-300 text-xs flex items-start gap-1.5 animate-in fade-in">
           <span className="text-xs mt-0.5">💡</span>
           <span>
-            受前一階段就讀時長影響，本階段起算時間已自動向後順延至 {timeline.startFormatted}。
+            Due to the previous stage duration, this stage's start date has been shifted to {timeline.startFormatted}.
           </span>
         </div>
       )}
 
-      {/* Base Fields: Track Select + Manual Annual Tuition */}
-      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
-        <div>
-          <Select value={trackValue} onValueChange={onTrackChange}>
-            <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
-            <SelectContent>
-              {trackOptions.map(opt => (
-                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
-              ))}
-            </SelectContent>
-          </Select>
+      {/* Base Fields */}
+      {stageKey === 'preschool' && preschoolSplitEnabled ? (
+        <div className="space-y-2.5">
+          {/* Stage 1 Section */}
+          <div className="p-2.5 rounded-md border border-primary/20 bg-primary/5 space-y-2">
+            <div className="flex items-center justify-between">
+              <span className="text-xs font-semibold text-primary flex items-center gap-1.5">
+                <span className="inline-flex items-center justify-center w-4 h-4 rounded-full bg-primary text-primary-foreground text-[10px] font-bold">1</span>
+                Stage 1 (Early Preschool)
+              </span>
+              <Badge variant="secondary" className="text-[10px] font-medium">
+                Duration: {stage1DurationMonths === 24 ? "2 Years (24 mos)" : "1 Year (12 mos)"}
+              </Badge>
+            </div>
+
+            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
+              <div className="space-y-1">
+                <Label className="text-[11px] text-muted-foreground" htmlFor="preschool-stage1-track">Track</Label>
+                <Select value={trackValue} onValueChange={onTrackChange}>
+                  <SelectTrigger className="h-8 text-sm" id="preschool-stage1-track"><SelectValue /></SelectTrigger>
+                  <SelectContent>
+                    {trackOptions.map(opt => (
+                      <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
+                    ))}
+                  </SelectContent>
+                </Select>
+              </div>
+
+              <div className="space-y-1">
+                <Label className="text-[11px] text-muted-foreground" htmlFor="preschool-stage1-duration">Duration</Label>
+                <Select
+                  value={stage1DurationMonths.toString()}
+                  onValueChange={(val) => onPreschoolStage1DurationChange?.(parseInt(val, 10))}
+                >
+                  <SelectTrigger className="h-8 text-sm" id="preschool-stage1-duration">
+                    <SelectValue />
+                  </SelectTrigger>
+                  <SelectContent>
+                    <SelectItem value="12">1 Year (12 months)</SelectItem>
+                    <SelectItem value="24">2 Years (24 months)</SelectItem>
+                  </SelectContent>
+                </Select>
+              </div>
+
+              <div className="space-y-1">
+                <Label className="text-[11px] text-muted-foreground" htmlFor="preschool-stage1-tuition">Manual Tuition (¥/year)</Label>
+                <Input
+                  id="preschool-stage1-tuition"
+                  type="number"
+                  placeholder="Optional"
+                  className="h-8 text-sm"
+                  value={override?.manualTuition ?? ""}
+                  onChange={(e) => onOverrideChange('manualTuition', e.target.value ? parseInt(e.target.value, 10) : null)}
+                />
+              </div>
+            </div>
+          </div>
+
+          {/* Stage 2 Section */}
+          <div className="p-2.5 rounded-md border border-purple-500/20 bg-purple-500/5 space-y-2">
+            <div className="flex items-center justify-between">
+              <span className="text-xs font-semibold text-purple-700 dark:text-purple-300 flex items-center gap-1.5">
+                <span className="inline-flex items-center justify-center w-4 h-4 rounded-full bg-purple-600 text-white text-[10px] font-bold">2</span>
+                Stage 2 (Transition Track)
+              </span>
+              <Badge variant="outline" className="text-[10px] font-medium border-purple-500/30 text-purple-700 dark:text-purple-300 bg-purple-500/10">
+                Duration: {stage2DurationText}
+              </Badge>
+            </div>
+
+            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
+              <div className="space-y-1">
+                <Label className="text-[11px] text-muted-foreground" htmlFor="preschool-stage2-track">Transition Track (Track 2)</Label>
+                <Select
+                  value={preschoolTrack2Value || "INTL"}
+                  onValueChange={(val) => onPreschoolTrack2Change?.(val)}
+                >
+                  <SelectTrigger className="h-8 text-sm" id="preschool-stage2-track"><SelectValue /></SelectTrigger>
+                  <SelectContent>
+                    {trackOptions.map(opt => (
+                      <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
+                    ))}
+                  </SelectContent>
+                </Select>
+              </div>
+
+              <div className="space-y-1">
+                <Label className="text-[11px] text-muted-foreground" htmlFor="preschool-stage2-tuition">Manual Tuition (¥/year)</Label>
+                <Input
+                  id="preschool-stage2-tuition"
+                  type="number"
+                  placeholder="Optional"
+                  className="h-8 text-sm"
+                  value={preschoolOverride2?.manualTuition ?? ""}
+                  onChange={(e) => onPreschoolOverride2Change?.('manualTuition', e.target.value ? parseInt(e.target.value, 10) : null)}
+                />
+              </div>
+            </div>
+          </div>
         </div>
+      ) : (
+        /* Base Fields: Track Select + Manual Annual Tuition (single track) */
+        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
+          <div>
+            <Select value={trackValue} onValueChange={onTrackChange}>
+              <SelectTrigger className="h-8 text-sm"><SelectValue /></SelectTrigger>
+              <SelectContent>
+                {trackOptions.map(opt => (
+                  <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
+                ))}
+              </SelectContent>
+            </Select>
+          </div>
 
-        <div>
-          <Input 
-            type="number" 
-            placeholder="Manual Annual Tuition (¥) - Optional" 
-            className="h-8 text-sm"
-            disabled={isNone}
-            value={override?.manualTuition ?? ""} 
-            onChange={(e) => onOverrideChange('manualTuition', e.target.value ? parseInt(e.target.value) : null)}
-          />
+          <div>
+            <Input 
+              type="number" 
+              placeholder="Manual Annual Tuition (¥) - Optional" 
+              className="h-8 text-sm"
+              disabled={isNone}
+              value={override?.manualTuition ?? ""} 
+              onChange={(e) => onOverrideChange('manualTuition', e.target.value ? parseInt(e.target.value, 10) : null)}
+            />
+          </div>
         </div>
-      </div>
+      )}
 
       {/* Extra Stage Field */}
       {extraField && !isNone && (
@@ -976,35 +1190,51 @@ function StageCard({
 
           <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
             {/* 1. Manual Duration (Years) */}
-            <div className="space-y-1">
-              <Label className="text-[11px] text-muted-foreground">Duration (Years)</Label>
-              <Input
-                type="number"
-                step="0.5"
-                min="0.5"
-                max="20"
-                placeholder="Default"
-                className="h-8 text-sm"
-                value={monthsToYears(override?.manualDuration) ?? ""}
-                onChange={(e) => {
-                  const years = e.target.value ? parseFloat(e.target.value) : null;
-                  onOverrideChange('manualDuration', yearsToMonths(years));
-                }}
-              />
-            </div>
+            {stageKey === 'preschool' && preschoolSplitEnabled ? (
+              <div className="space-y-1">
+                <Label className="text-[11px] text-muted-foreground">Duration (Fixed)</Label>
+                <Input
+                  disabled
+                  className="h-8 text-sm bg-muted text-muted-foreground"
+                  value="3 Years (2-Stage Total)"
+                />
+              </div>
+            ) : (
+              <div className="space-y-1">
+                <Label className="text-[11px] text-muted-foreground">
+                  Duration (Years) {stageKey === 'preschool' && '(Min 3y)'}
+                </Label>
+                <Input
+                  type="number"
+                  step="0.5"
+                  min={stageKey === 'preschool' ? "3" : "0.5"}
+                  max="20"
+                  placeholder={stageKey === 'preschool' ? "3 (Standard)" : "Default"}
+                  className="h-8 text-sm"
+                  value={monthsToYears(override?.manualDuration) ?? ""}
+                  onChange={(e) => {
+                    let years = e.target.value ? parseFloat(e.target.value) : null;
+                    if (stageKey === 'preschool' && years !== null && years < 3) {
+                      years = 3;
+                    }
+                    onOverrideChange('manualDuration', yearsToMonths(years));
+                  }}
+                />
+              </div>
+            )}
 
             {/* 2. Manual Start Month */}
             <div className="space-y-1">
               <Label className="text-[11px] text-muted-foreground">Start Month</Label>
               <Select
                 value={override?.manualStartMonth ? override.manualStartMonth.toString() : "default"}
-                onValueChange={(v) => onOverrideChange('manualStartMonth', v === "default" ? null : parseInt(v))}
+                onValueChange={(v) => onOverrideChange('manualStartMonth', v === "default" ? null : parseInt(v, 10))}
               >
                 <SelectTrigger className="h-8 text-sm"><SelectValue placeholder="Default" /></SelectTrigger>
                 <SelectContent>
                   <SelectItem value="default">Default</SelectItem>
                   {Array.from({ length: 12 }, (_, i) => i + 1).map(m => (
-                    <SelectItem key={m} value={m.toString()}>{m}月 ({new Date(0, m - 1).toLocaleString('en', { month: 'short' })})</SelectItem>
+                    <SelectItem key={m} value={m.toString()}>{new Date(0, m - 1).toLocaleString('en', { month: 'short' })} (Month {m})</SelectItem>
                   ))}
                 </SelectContent>
               </Select>
@@ -1015,7 +1245,7 @@ function StageCard({
               <Label className="text-[11px] text-muted-foreground">Terms / Year</Label>
               <Select
                 value={override?.manualTermsPerYear ? override.manualTermsPerYear.toString() : "default"}
-                onValueChange={(v) => onOverrideChange('manualTermsPerYear', v === "default" ? null : parseInt(v))}
+                onValueChange={(v) => onOverrideChange('manualTermsPerYear', v === "default" ? null : parseInt(v, 10))}
               >
                 <SelectTrigger className="h-8 text-sm"><SelectValue placeholder="Default" /></SelectTrigger>
                 <SelectContent>
diff --git a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
index 2020db0..e5010c2 100644
--- a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
+++ b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
@@ -19,6 +19,10 @@ export interface ChildEducationSettingsDto {
   childProfileId?: number;
   preschoolTrack: string;
   preschoolOverride?: StageOverrideDto | null;
+  preschoolSplitEnabled?: boolean | null;
+  preschoolStage1Duration?: number | null;
+  preschoolTrack2?: string | null;
+  preschoolOverride2?: StageOverrideDto | null;
   primaryTrack: string;
   primaryOverride?: StageOverrideDto | null;
   middleTrack: string;
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                                            10027  12.73G   5.10    21:44:34  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                                                                  10028  12.69G   4.99    21:44:40  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
index 247038bd..6bc867e2 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java
@@ -92,6 +92,29 @@ public class ChildEducationSettingsServiceImpl implements ChildEducationSettings
             settings.setPreschoolTrack(command.getPreschoolTrack());
         }
         settings.setPreschoolOverride(command.getPreschoolOverride() != null ? command.getPreschoolOverride().toDomain() : null);
+        if (Boolean.TRUE.equals(command.getPreschoolSplitEnabled())) {
+            if (command.getPreschoolTrack2() == null) {
+                throw new IllegalArgumentException("preschoolTrack2 must not be null when preschoolSplitEnabled is true");
+            }
+            if (command.getPreschoolStage1Duration() == null
+                    || (command.getPreschoolStage1Duration() != 12 && command.getPreschoolStage1Duration() != 24)) {
+                throw new IllegalArgumentException("preschoolStage1Duration must be either 12 or 24 months when preschoolSplitEnabled is true");
+            }
+            settings.setPreschoolSplitEnabled(true);
+            settings.setPreschoolStage1Duration(command.getPreschoolStage1Duration());
+            settings.setPreschoolTrack2(command.getPreschoolTrack2());
+            settings.setPreschoolOverride2(command.getPreschoolOverride2() != null ? command.getPreschoolOverride2().toDomain() : null);
+        } else {
+            if (command.getPreschoolOverride() != null
+                    && command.getPreschoolOverride().getManualDuration() != null
+                    && command.getPreschoolOverride().getManualDuration() < 36) {
+                throw new IllegalArgumentException("Preschool manualDuration must be at least 36 months in single-track mode");
+            }
+            settings.setPreschoolSplitEnabled(false);
+            settings.setPreschoolStage1Duration(null);
+            settings.setPreschoolTrack2(null);
+            settings.setPreschoolOverride2(null);
+        }
 
         if (command.getPrimaryTrack() != null) {
             settings.setPrimaryTrack(command.getPrimaryTrack());
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java
index 8cd06dcf..83c24f22 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java
@@ -43,6 +43,10 @@ public class UpdateChildEducationSettingsCommand {
 
     private PreschoolTrack preschoolTrack;
     private StageOverrideCommand preschoolOverride;
+    private Boolean preschoolSplitEnabled;
+    private Integer preschoolStage1Duration;
+    private PreschoolTrack preschoolTrack2;
+    private StageOverrideCommand preschoolOverride2;
 
     private PrimaryTrack primaryTrack;
     private StageOverrideCommand primaryOverride;
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java
index 2f2d4b93..f088aabf 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java
@@ -51,6 +51,10 @@ public class ChildEducationSettingsResult extends BaseResult {
 
     private PreschoolTrack preschoolTrack;
     private StageOverrideResult preschoolOverride;
+    private Boolean preschoolSplitEnabled;
+    private Integer preschoolStage1Duration;
+    private PreschoolTrack preschoolTrack2;
+    private StageOverrideResult preschoolOverride2;
 
     private PrimaryTrack primaryTrack;
     private StageOverrideResult primaryOverride;
@@ -76,6 +80,10 @@ public class ChildEducationSettingsResult extends BaseResult {
                 .childProfileId(entity.getChildProfileId())
                 .preschoolTrack(entity.getPreschoolTrack())
                 .preschoolOverride(StageOverrideResult.fromDomain(entity.getPreschoolOverride()))
+                .preschoolSplitEnabled(entity.getPreschoolSplitEnabled())
+                .preschoolStage1Duration(entity.getPreschoolStage1Duration())
+                .preschoolTrack2(entity.getPreschoolTrack2())
+                .preschoolOverride2(StageOverrideResult.fromDomain(entity.getPreschoolOverride2()))
                 .primaryTrack(entity.getPrimaryTrack())
                 .primaryOverride(StageOverrideResult.fromDomain(entity.getPrimaryOverride()))
                 .middleTrack(entity.getMiddleTrack())
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
index 7fc6af95..0e443e70 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
@@ -82,24 +82,60 @@ public class EducationProfileResolver {
     private ResolvedStageData resolvePreschool(ChildEducationSettings settings, EducationConfig config) {
         // Preschool defaults to HOME (Home Care = 0 tuition, 36 months) when settings or track is omitted.
         // HOME represents the zero-cost baseline track for preschool; there is no NONE track.
-        PreschoolTrack track = settings != null && settings.getPreschoolTrack() != null
+        PreschoolTrack track1 = settings != null && settings.getPreschoolTrack() != null
                 ? settings.getPreschoolTrack()
                 : PreschoolTrack.HOME;
 
-        TrackConfig trackConfig = null;
-        if (config != null && config.getPreschool() != null) {
-            trackConfig = switch (track) {
-                case HOME ->
-                    config.getPreschool().getHomeTrack();
-                case DAYCARE ->
-                    config.getPreschool().getDaycareTrack();
-                case INTL ->
-                    config.getPreschool().getIntlTrack();
-            };
+        TrackConfig trackConfig1 = getPreschoolTrackConfig(config, track1);
+        StageOverride override1 = settings != null ? settings.getPreschoolOverride() : null;
+
+        boolean isTwoStage = settings != null
+                && Boolean.TRUE.equals(settings.getPreschoolSplitEnabled())
+                && settings.getPreschoolTrack2() != null;
+
+        if (!isTwoStage) {
+            ResolvedStageData stage1Data = mergeStage(EducationStage.PRESCHOOL, track1.name(), trackConfig1, override1, BigDecimal.ZERO);
+            // Single-track clamping: duration must be at least 36 months (3 years)
+            int clampedDuration = Math.max(36, stage1Data.getDurationMonths());
+            if (clampedDuration != stage1Data.getDurationMonths()) {
+                return stage1Data.toBuilder()
+                        .durationMonths(clampedDuration)
+                        .build();
+            }
+            return stage1Data;
         }
 
-        StageOverride override = settings != null ? settings.getPreschoolOverride() : null;
-        return mergeStage(EducationStage.PRESCHOOL, track.name(), trackConfig, override, BigDecimal.ZERO);
+        // Two-stage preschool track transition
+        Integer rawD1 = settings.getPreschoolStage1Duration();
+        int d1 = (rawD1 != null && (rawD1 == 12 || rawD1 == 24)) ? rawD1 : 12;
+        int d2 = 36 - d1;
+
+        ResolvedStageData stage1Base = mergeStage(EducationStage.PRESCHOOL, track1.name(), trackConfig1, override1, BigDecimal.ZERO);
+
+        PreschoolTrack track2 = settings.getPreschoolTrack2();
+        TrackConfig trackConfig2 = getPreschoolTrackConfig(config, track2);
+        StageOverride override2 = settings.getPreschoolOverride2();
+        ResolvedStageData stage2Base = mergeStage(EducationStage.PRESCHOOL, track2.name(), trackConfig2, override2, BigDecimal.ZERO);
+
+        ResolvedStageData stage2Data = stage2Base.toBuilder()
+                .durationMonths(d2)
+                .build();
+
+        return stage1Base.toBuilder()
+                .durationMonths(d1)
+                .stage2(stage2Data)
+                .build();
+    }
+
+    private TrackConfig getPreschoolTrackConfig(EducationConfig config, PreschoolTrack track) {
+        if (config == null || config.getPreschool() == null || track == null) {
+            return null;
+        }
+        return switch (track) {
+            case HOME -> config.getPreschool().getHomeTrack();
+            case DAYCARE -> config.getPreschool().getDaycareTrack();
+            case INTL -> config.getPreschool().getIntlTrack();
+        };
     }
 
     private ResolvedStageData resolvePrimary(ChildEducationSettings settings, EducationConfig config) {
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
index 16e032c8..30ea162c 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
@@ -76,7 +76,11 @@ public class EducationTimelineProjector {
             }
 
             YearMonth stageStart = calculateStageStart(birthDate, stage, stageData, previousStageEnd);
-            YearMonth stageEnd = stageStart.plusMonths(stageData.getDurationMonths() - 1);
+            int totalDuration = stageData.getDurationMonths();
+            if (stageData.hasStage2()) {
+                totalDuration += stageData.getStage2().getDurationMonths();
+            }
+            YearMonth stageEnd = stageStart.plusMonths(totalDuration - 1);
 
             intervals.put(stage, new EducationAbsoluteTimeline.StageInterval(
                     stage,
@@ -133,6 +137,9 @@ public class EducationTimelineProjector {
     private int getStandardEntranceAge(EducationStage stage, ResolvedStageData stageData) {
         return switch (stage) {
             case PRESCHOOL -> {
+                if (stageData.hasStage2()) {
+                    yield PRESCHOOL_AGE;
+                }
                 if (stageData.getTrackName() != null && stageData.getTrackName().equalsIgnoreCase("DAYCARE")) {
                     yield DAYCARE_AGE;
                 }
@@ -219,11 +226,31 @@ public class EducationTimelineProjector {
             YearMonth stageEnd,
             List<EducationPaymentEvent> events) {
 
-        // 1. One-time entrance fee at stage start
-        BigDecimal entranceFee = stageData.getEntranceFee();
+        if (!stageData.hasStage2()) {
+            generateSegmentEvents(stage, stageData, stageStart, stageEnd, events);
+            return;
+        }
+
+        // Two-stage preschool event generation
+        YearMonth stage1End = stageStart.plusMonths(stageData.getDurationMonths() - 1);
+        generateSegmentEvents(stage, stageData, stageStart, stage1End, events);
+
+        YearMonth stage2Start = stageStart.plusMonths(stageData.getDurationMonths());
+        generateSegmentEvents(stage, stageData.getStage2(), stage2Start, stageEnd, events);
+    }
+
+    private void generateSegmentEvents(
+            EducationStage stage,
+            ResolvedStageData segmentData,
+            YearMonth segmentStart,
+            YearMonth segmentEnd,
+            List<EducationPaymentEvent> events) {
+
+        // 1. One-time entrance fee at segment start
+        BigDecimal entranceFee = segmentData.getEntranceFee();
         if (entranceFee != null && entranceFee.compareTo(BigDecimal.ZERO) > 0) {
             events.add(EducationPaymentEvent.builder()
-                    .paymentMonth(stageStart)
+                    .paymentMonth(segmentStart)
                     .amount(entranceFee)
                     .type(PaymentType.ENTRANCE_FEE)
                     .stage(stage)
@@ -231,15 +258,15 @@ public class EducationTimelineProjector {
         }
 
         // 2. Periodic term tuitions
-        BigDecimal annualTuition = stageData.getAnnualTuition();
-        int termsPerYear = stageData.getTermsPerYear();
-        List<Integer> paymentMonths = stageData.getPaymentMonths();
+        BigDecimal annualTuition = segmentData.getAnnualTuition();
+        int termsPerYear = segmentData.getTermsPerYear();
+        List<Integer> paymentMonths = segmentData.getPaymentMonths();
 
         if (annualTuition != null && annualTuition.compareTo(BigDecimal.ZERO) > 0 && termsPerYear > 0) {
             BigDecimal termTuition = annualTuition.divide(BigDecimal.valueOf(termsPerYear), 4, RoundingMode.HALF_UP);
 
-            YearMonth cursor = stageStart;
-            while (!cursor.isAfter(stageEnd)) {
+            YearMonth cursor = segmentStart;
+            while (!cursor.isAfter(segmentEnd)) {
                 if (paymentMonths.contains(cursor.getMonthValue())) {
                     events.add(EducationPaymentEvent.builder()
                             .paymentMonth(cursor)
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
index 8e5333f0..6b9c6811 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
@@ -38,7 +38,7 @@ import lombok.Getter;
  */
 @Getter
 @AllArgsConstructor
-@Builder
+@Builder(toBuilder = true)
 public class ResolvedStageData {
 
     private final EducationStage stage;
@@ -54,4 +54,17 @@ public class ResolvedStageData {
 
     @Builder.Default
     private final List<Integer> paymentMonths = Collections.emptyList();
+
+    /**
+     * Optional second stage specifically for Preschool two-stage track transition.
+     * Null for all other stages and single-track preschool.
+     */
+    private final ResolvedStageData stage2;
+
+    /**
+     * Checks if this stage has an active second stage (two-stage preschool).
+     */
+    public boolean hasStage2() {
+        return stage2 != null && stage2.isActive();
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
index 72853a6e..6e6a67b5 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java
@@ -33,6 +33,7 @@ import static org.junit.jupiter.api.Assertions.assertFalse;
 import static org.junit.jupiter.api.Assertions.assertNotNull;
 import static org.junit.jupiter.api.Assertions.assertNull;
 import static org.junit.jupiter.api.Assertions.assertThrows;
+import static org.junit.jupiter.api.Assertions.assertTrue;
 import org.junit.jupiter.api.BeforeEach;
 import org.junit.jupiter.api.Test;
 import org.junit.jupiter.api.extension.ExtendWith;
@@ -59,6 +60,7 @@ import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationCon
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
+import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.UniversityTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.education.ChildEducationSettings;
@@ -222,6 +224,139 @@ class ChildEducationSettingsServiceImplTest {
         assertFalse(Boolean.TRUE.equals(saved.getIsAwayFromHome()));
     }
 
+    @Test
+    void updateSettings_WhenPreschoolSplitEnabled_SavesTwoStageFields() {
+        ChildEducationSettings existing = new ChildEducationSettings(1L);
+
+        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
+                .preschoolTrack(PreschoolTrack.HOME)
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(12)
+                .preschoolTrack2(PreschoolTrack.INTL)
+                .preschoolOverride2(StageOverrideCommand.builder().manualTuition(new BigDecimal("1800000")).build())
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
+        assertEquals(12, result.getPreschoolStage1Duration());
+        assertEquals(PreschoolTrack.INTL, result.getPreschoolTrack2());
+        assertNotNull(result.getPreschoolOverride2());
+        assertEquals(new BigDecimal("1800000"), result.getPreschoolOverride2().getManualTuition());
+
+        ArgumentCaptor<ChildEducationSettings> captor = ArgumentCaptor.forClass(ChildEducationSettings.class);
+        verify(settingsRepository).save(captor.capture());
+        ChildEducationSettings saved = captor.getValue();
+        assertTrue(saved.getPreschoolSplitEnabled());
+        assertEquals(12, saved.getPreschoolStage1Duration());
+        assertEquals(PreschoolTrack.INTL, saved.getPreschoolTrack2());
+        assertNotNull(saved.getPreschoolOverride2());
+    }
+
+    @Test
+    void updateSettings_WhenPreschoolSplitDisabled_ClearsTwoStageFields() {
+        ChildEducationSettings existing = new ChildEducationSettings(1L);
+        existing.setPreschoolSplitEnabled(true);
+        existing.setPreschoolStage1Duration(12);
+        existing.setPreschoolTrack2(PreschoolTrack.INTL);
+
+        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
+                .preschoolTrack(PreschoolTrack.HOME)
+                .preschoolSplitEnabled(false)
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
+        assertFalse(result.getPreschoolSplitEnabled());
+        assertNull(result.getPreschoolStage1Duration());
+        assertNull(result.getPreschoolTrack2());
+        assertNull(result.getPreschoolOverride2());
+
+        ArgumentCaptor<ChildEducationSettings> captor = ArgumentCaptor.forClass(ChildEducationSettings.class);
+        verify(settingsRepository).save(captor.capture());
+        ChildEducationSettings saved = captor.getValue();
+        assertFalse(saved.getPreschoolSplitEnabled());
+        assertNull(saved.getPreschoolStage1Duration());
+        assertNull(saved.getPreschoolTrack2());
+        assertNull(saved.getPreschoolOverride2());
+    }
+
+    @Test
+    void updateSettings_WhenPreschoolSplitEnabledAndTrack2IsNull_ThrowsIllegalArgumentException() {
+        when(childProfileRepository.findById(1L)).thenReturn(Optional.of(childProfile));
+        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(new ChildEducationSettings(1L)));
+
+        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(12)
+                .preschoolTrack2(null)
+                .build();
+
+        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
+                IllegalArgumentException.class,
+                () -> service.updateSettings(1L, command));
+        assertTrue(ex.getMessage().contains("preschoolTrack2 must not be null"));
+    }
+
+    @Test
+    void updateSettings_WhenPreschoolSplitEnabledAndInvalidDuration_ThrowsIllegalArgumentException() {
+        when(childProfileRepository.findById(1L)).thenReturn(Optional.of(childProfile));
+        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(new ChildEducationSettings(1L)));
+
+        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(18) // Invalid: neither 12 nor 24
+                .preschoolTrack2(PreschoolTrack.INTL)
+                .build();
+
+        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
+                IllegalArgumentException.class,
+                () -> service.updateSettings(1L, command));
+        assertTrue(ex.getMessage().contains("preschoolStage1Duration must be either 12 or 24"));
+    }
+
+    @Test
+    void updateSettings_WhenSingleTrackAndManualDurationLessThan36_ThrowsIllegalArgumentException() {
+        when(childProfileRepository.findById(1L)).thenReturn(Optional.of(childProfile));
+        when(settingsRepository.findByChildProfileId(1L)).thenReturn(Optional.of(new ChildEducationSettings(1L)));
+
+        UpdateChildEducationSettingsCommand command = UpdateChildEducationSettingsCommand.builder()
+                .preschoolSplitEnabled(false)
+                .preschoolOverride(StageOverrideCommand.builder().manualDuration(24).build())
+                .build();
+
+        IllegalArgumentException ex = org.junit.jupiter.api.Assertions.assertThrows(
+                IllegalArgumentException.class,
+                () -> service.updateSettings(1L, command));
+        assertTrue(ex.getMessage().contains("must be at least 36 months"));
+    }
+
     @Test
     void deleteSettings_Success() {
         when(childProfileRepository.findById(1L)).thenReturn(Optional.of(childProfile));
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
index b71c68f2..0788dd69 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
@@ -374,8 +374,8 @@ class EducationProfileResolverTest {
         // Preschool override
         ResolvedStageData preschool = profile.getStage(EducationStage.PRESCHOOL);
         assertThat(preschool.isActive()).isTrue();
-        assertThat(preschool.getAnnualTuition()).isEqualByComparingTo("1800000");
-        assertThat(preschool.getDurationMonths()).isEqualTo(24);
+        // Preschool single-track duration is clamped to at least 36 months per FR-125
+        assertThat(preschool.getDurationMonths()).isEqualTo(36);
         assertThat(preschool.getStartMonth()).isEqualTo(9);
         assertThat(preschool.getTermsPerYear()).isEqualTo(3);
         assertThat(preschool.getPaymentMonths()).containsExactly(9, 1, 5);
@@ -447,4 +447,141 @@ class EducationProfileResolverTest {
         assertThat(uni.getTermsPerYear()).isEqualTo(2);
         assertThat(uni.getPaymentMonths()).containsExactly(9, 3);
     }
+
+    @Test
+    @DisplayName("Should clamp preschool duration to 36 months in single-track mode when manual duration is less than 36")
+    void shouldClampPreschoolDurationInSingleTrackMode() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .preschoolTrack(PreschoolTrack.INTL)
+                .preschoolOverride(StageOverride.builder()
+                        .manualDuration(12) // Malicious or mistaken 12 months input
+                        .build())
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData preschool = profile.getStage(EducationStage.PRESCHOOL);
+
+        assertThat(preschool.isActive()).isTrue();
+        assertThat(preschool.getDurationMonths()).isEqualTo(36);
+        assertThat(preschool.hasStage2()).isFalse();
+    }
+
+    @Test
+    @DisplayName("Should preserve preschool duration when manual duration is greater than or equal to 36 months")
+    void shouldPreservePreschoolDurationWhenGreaterOrEqualTo36Months() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .preschoolTrack(PreschoolTrack.INTL)
+                .preschoolOverride(StageOverride.builder()
+                        .manualDuration(48) // Delayed entrance / gap year
+                        .build())
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData preschool = profile.getStage(EducationStage.PRESCHOOL);
+
+        assertThat(preschool.isActive()).isTrue();
+        assertThat(preschool.getDurationMonths()).isEqualTo(48);
+        assertThat(preschool.hasStage2()).isFalse();
+    }
+
+    @Test
+    @DisplayName("Should resolve two-stage preschool with HOME (12 mos) and INTL (24 mos)")
+    void shouldResolveTwoStagePreschoolHomeThenIntl() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .preschoolTrack(PreschoolTrack.HOME)
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(12)
+                .preschoolTrack2(PreschoolTrack.INTL)
+                .preschoolOverride2(StageOverride.builder()
+                        .manualTuition(new BigDecimal("1600000"))
+                        .build())
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData stage1 = profile.getStage(EducationStage.PRESCHOOL);
+
+        assertThat(stage1).isNotNull();
+        assertThat(stage1.isActive()).isTrue();
+        assertThat(stage1.getTrackName()).isEqualTo("HOME");
+        assertThat(stage1.getDurationMonths()).isEqualTo(12);
+        assertThat(stage1.getAnnualTuition()).isEqualByComparingTo(BigDecimal.ZERO);
+        assertThat(stage1.hasStage2()).isTrue();
+
+        ResolvedStageData stage2 = stage1.getStage2();
+        assertThat(stage2).isNotNull();
+        assertThat(stage2.isActive()).isTrue();
+        assertThat(stage2.getTrackName()).isEqualTo("INTL");
+        assertThat(stage2.getDurationMonths()).isEqualTo(24);
+        assertThat(stage2.getAnnualTuition()).isEqualByComparingTo("1600000"); // overridden tuition
+        assertThat(stage2.getEntranceFee()).isEqualByComparingTo("200000"); // INTL default entrance fee
+        assertThat(stage2.getStartMonth()).isEqualTo(9);
+        assertThat(stage2.getTermsPerYear()).isEqualTo(3);
+        assertThat(stage2.getPaymentMonths()).containsExactly(9, 1, 5);
+    }
+
+    @Test
+    @DisplayName("Should resolve two-stage preschool with stage 1 duration = 24 months and stage 2 = 12 months")
+    void shouldResolveTwoStagePreschoolWithCustomDuration24Months() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .preschoolTrack(PreschoolTrack.DAYCARE)
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(24)
+                .preschoolTrack2(PreschoolTrack.INTL)
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData stage1 = profile.getStage(EducationStage.PRESCHOOL);
+
+        assertThat(stage1.getTrackName()).isEqualTo("DAYCARE");
+        assertThat(stage1.getDurationMonths()).isEqualTo(24);
+        assertThat(stage1.getAnnualTuition()).isEqualByComparingTo("300000");
+        assertThat(stage1.hasStage2()).isTrue();
+
+        ResolvedStageData stage2 = stage1.getStage2();
+        assertThat(stage2.getTrackName()).isEqualTo("INTL");
+        assertThat(stage2.getDurationMonths()).isEqualTo(12);
+        assertThat(stage2.getAnnualTuition()).isEqualByComparingTo("1500000");
+    }
+
+    @Test
+    @DisplayName("Should default to 12 months when two-stage preschool has invalid stage1 duration")
+    void shouldDefaultTo12MonthsWhenTwoStagePreschoolHasInvalidDuration() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .preschoolTrack(PreschoolTrack.HOME)
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(99) // Invalid value (neither 12 nor 24)
+                .preschoolTrack2(PreschoolTrack.INTL)
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData stage1 = profile.getStage(EducationStage.PRESCHOOL);
+
+        assertThat(stage1.getDurationMonths()).isEqualTo(12);
+        assertThat(stage1.hasStage2()).isTrue();
+        assertThat(stage1.getStage2().getDurationMonths()).isEqualTo(24);
+    }
+
+    @Test
+    @DisplayName("Should fallback to single-track mode when splitEnabled is true but track2 is null")
+    void shouldFallbackToSingleTrackWhenSplitEnabledButTrack2IsNull() {
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .preschoolTrack(PreschoolTrack.HOME)
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(12)
+                .preschoolTrack2(null) // Missing track2
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, defaultConfig);
+        ResolvedStageData stage = profile.getStage(EducationStage.PRESCHOOL);
+
+        assertThat(stage.getTrackName()).isEqualTo("HOME");
+        assertThat(stage.getDurationMonths()).isEqualTo(36);
+        assertThat(stage.hasStage2()).isFalse();
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java
index 639fd1b6..3ff433e6 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java
@@ -237,4 +237,154 @@ class EducationTimelineProjectorTest {
         assertThat(primaryInterval.startMonth()).isEqualTo(YearMonth.of(2024, 4));
         assertThat(primaryInterval.endMonth()).isEqualTo(YearMonth.of(2030, 3));
     }
+
+    @Test
+    @DisplayName("Should project two-stage preschool (HOME 12 mos + INTL 24 mos) with entrance fee at cutover month and seamless primary chaining")
+    void shouldProjectTwoStagePreschoolHomeThenIntl() {
+        LocalDate birthDate = LocalDate.of(2021, 8, 15);
+
+        // Stage 2: INTL (24 mos, entrance fee 200k, tuition 1.5M/yr, payments in Sep & Mar)
+        ResolvedStageData stage2 = ResolvedStageData.builder()
+                .stage(EducationStage.PRESCHOOL)
+                .trackName("INTL")
+                .active(true)
+                .annualTuition(new BigDecimal("1500000"))
+                .durationMonths(24)
+                .startMonth(9)
+                .termsPerYear(2)
+                .entranceFee(new BigDecimal("200000"))
+                .paymentMonths(List.of(9, 3))
+                .build();
+
+        // Stage 1: HOME (12 mos, 0 tuition, 0 entrance fee)
+        ResolvedStageData preschool = ResolvedStageData.builder()
+                .stage(EducationStage.PRESCHOOL)
+                .trackName("HOME")
+                .active(true)
+                .annualTuition(BigDecimal.ZERO)
+                .durationMonths(12)
+                .startMonth(4)
+                .termsPerYear(2)
+                .entranceFee(BigDecimal.ZERO)
+                .paymentMonths(List.of(4, 10))
+                .stage2(stage2)
+                .build();
+
+        // Primary: 72 mos, starts April 2028
+        ResolvedStageData primary = ResolvedStageData.builder()
+                .stage(EducationStage.PRIMARY)
+                .trackName("PUBLIC")
+                .active(true)
+                .annualTuition(new BigDecimal("60000"))
+                .durationMonths(72)
+                .startMonth(4)
+                .termsPerYear(2)
+                .paymentMonths(List.of(4, 10))
+                .build();
+
+        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
+                .stages(Map.of(
+                        EducationStage.PRESCHOOL, preschool,
+                        EducationStage.PRIMARY, primary))
+                .build();
+
+        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);
+
+        // 1. Preschool stage interval: spans full 36 months (2025-04 to 2028-03)
+        EducationAbsoluteTimeline.StageInterval preschoolInterval = timeline.getStageInterval(EducationStage.PRESCHOOL);
+        assertThat(preschoolInterval).isNotNull();
+        assertThat(preschoolInterval.startMonth()).isEqualTo(YearMonth.of(2025, 4));
+        assertThat(preschoolInterval.endMonth()).isEqualTo(YearMonth.of(2028, 3));
+
+        // 2. Primary chains seamlessly at 2028-04
+        EducationAbsoluteTimeline.StageInterval primaryInterval = timeline.getStageInterval(EducationStage.PRIMARY);
+        assertThat(primaryInterval).isNotNull();
+        assertThat(primaryInterval.startMonth()).isEqualTo(YearMonth.of(2028, 4));
+
+        // 3. Stage 1 (2025-04 to 2026-03): HOME has zero payment events
+        for (int m = 1; m <= 12; m++) {
+            YearMonth ym = YearMonth.of(2025, 4).plusMonths(m - 1);
+            assertThat(timeline.getEventsForMonth(ym)).isEmpty();
+        }
+
+        // 4. Cutover month (2026-04): Stage 2 INTL entrance fee (200k)
+        List<EducationPaymentEvent> cutoverEvents = timeline.getEventsForMonth(YearMonth.of(2026, 4));
+        assertThat(cutoverEvents).hasSize(1);
+        assertThat(cutoverEvents.get(0).getType()).isEqualTo(PaymentType.ENTRANCE_FEE);
+        assertThat(cutoverEvents.get(0).getAmount()).isEqualByComparingTo("200000");
+
+        // 5. Stage 2 tuitions: 4 payments (750k each) across 24 months
+        List<EducationPaymentEvent> preschoolEvents = timeline.getEvents().stream()
+                .filter(e -> e.getStage() == EducationStage.PRESCHOOL)
+                .toList();
+        assertThat(preschoolEvents).hasSize(5); // 1 entrance fee + 4 tuitions
+
+        List<EducationPaymentEvent> tuitionEvents = preschoolEvents.stream()
+                .filter(e -> e.getType() == PaymentType.TUITION)
+                .toList();
+        assertThat(tuitionEvents).hasSize(4);
+        assertThat(tuitionEvents.get(0).getPaymentMonth()).isEqualTo(YearMonth.of(2026, 9));
+        assertThat(tuitionEvents.get(0).getAmount()).isEqualByComparingTo("750000");
+        assertThat(tuitionEvents.get(1).getPaymentMonth()).isEqualTo(YearMonth.of(2027, 3));
+        assertThat(tuitionEvents.get(1).getAmount()).isEqualByComparingTo("750000");
+        assertThat(tuitionEvents.get(2).getPaymentMonth()).isEqualTo(YearMonth.of(2027, 9));
+        assertThat(tuitionEvents.get(2).getAmount()).isEqualByComparingTo("750000");
+        assertThat(tuitionEvents.get(3).getPaymentMonth()).isEqualTo(YearMonth.of(2028, 3));
+        assertThat(tuitionEvents.get(3).getAmount()).isEqualByComparingTo("750000");
+    }
+
+    @Test
+    @DisplayName("Should project two-stage preschool with DAYCARE stage 1 (12 mos) and INTL stage 2 (24 mos)")
+    void shouldProjectTwoStagePreschoolDaycareThenIntl() {
+        LocalDate birthDate = LocalDate.of(2021, 8, 15);
+
+        ResolvedStageData stage2 = ResolvedStageData.builder()
+                .stage(EducationStage.PRESCHOOL)
+                .trackName("INTL")
+                .active(true)
+                .annualTuition(new BigDecimal("1500000"))
+                .durationMonths(24)
+                .startMonth(9)
+                .termsPerYear(2)
+                .entranceFee(new BigDecimal("200000"))
+                .paymentMonths(List.of(9, 3))
+                .build();
+
+        ResolvedStageData preschool = ResolvedStageData.builder()
+                .stage(EducationStage.PRESCHOOL)
+                .trackName("DAYCARE")
+                .active(true)
+                .annualTuition(new BigDecimal("400000"))
+                .durationMonths(12)
+                .startMonth(4)
+                .termsPerYear(2)
+                .entranceFee(BigDecimal.ZERO)
+                .paymentMonths(List.of(4, 10))
+                .stage2(stage2)
+                .build();
+
+        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
+                .stages(Map.of(EducationStage.PRESCHOOL, preschool))
+                .build();
+
+        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);
+
+        // Stage 1 DAYCARE tuitions: 2025-04 (200k) and 2025-10 (200k)
+        List<EducationPaymentEvent> april2025 = timeline.getEventsForMonth(YearMonth.of(2025, 4));
+        assertThat(april2025).hasSize(1);
+        assertThat(april2025.get(0).getAmount()).isEqualByComparingTo("200000");
+
+        List<EducationPaymentEvent> oct2025 = timeline.getEventsForMonth(YearMonth.of(2025, 10));
+        assertThat(oct2025).hasSize(1);
+        assertThat(oct2025.get(0).getAmount()).isEqualByComparingTo("200000");
+
+        // Cutover month 2026-04: INTL entrance fee (200k)
+        List<EducationPaymentEvent> cutover = timeline.getEventsForMonth(YearMonth.of(2026, 4));
+        assertThat(cutover).hasSize(1);
+        assertThat(cutover.get(0).getType()).isEqualTo(PaymentType.ENTRANCE_FEE);
+        assertThat(cutover.get(0).getAmount()).isEqualByComparingTo("200000");
+
+        // Total events: 2 Daycare tuitions + 1 INTL entrance fee + 4 INTL tuitions = 7 events
+        assertThat(timeline.getEvents()).hasSize(7);
+    }
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
index 302787bf..80af3925 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java
@@ -73,6 +73,29 @@ public class ChildEducationSettings extends AuditableEntity {
     })
     private StageOverride preschoolOverride;
 
+    /** Whether preschool is split into two distinct stages (e.g. HOME first, then INTL/DAYCARE). */
+    @Column(name = "preschool_split_enabled")
+    private Boolean preschoolSplitEnabled = false;
+
+    /** Duration of stage 1 in months (strictly 12 or 24 months; stage 2 fills the remainder to 36 months). */
+    @Column(name = "preschool_stage1_duration")
+    private Integer preschoolStage1Duration;
+
+    /** Preschool track selection for stage 2 (PreschoolTrack: HOME, DAYCARE, INTL). */
+    @Enumerated(EnumType.STRING)
+    @Column(name = "preschool_track2")
+    private PreschoolTrack preschoolTrack2;
+
+    /** Preschool custom override for stage 2 (manualTuition is annual tuition; manualDuration is duration in months). */
+    @Embedded
+    @AttributeOverrides({
+        @AttributeOverride(name = "manualTuition", column = @Column(name = "preschool_manual_tuition2", precision = 19, scale = 4)),
+        @AttributeOverride(name = "manualDuration", column = @Column(name = "preschool_manual_duration2")),
+        @AttributeOverride(name = "manualStartMonth", column = @Column(name = "preschool_manual_start_month2")),
+        @AttributeOverride(name = "manualTermsPerYear", column = @Column(name = "preschool_manual_terms_per_year2"))
+    })
+    private StageOverride preschoolOverride2;
+
     // --- Primary ---
     /** Primary school track selection (PrimaryTrack). */
     @Enumerated(EnumType.STRING)
@@ -152,9 +175,24 @@ public class ChildEducationSettings extends AuditableEntity {
         this.childProfileId = Objects.requireNonNull(childProfileId, "childProfileId must not be null");
     }
 
+    public ChildEducationSettings(Long childProfileId,
+            PreschoolTrack preschoolTrack, StageOverride preschoolOverride,
+            PrimaryTrack primaryTrack, StageOverride primaryOverride,
+            MiddleTrack middleTrack, StageOverride middleOverride,
+            HighTrack highTrack, StageOverride highOverride,
+            UniversityTrack universityTrack, StageOverride universityOverride, BigDecimal manualLivingCost,
+            Boolean isAwayFromHome) {
+        this(childProfileId, preschoolTrack, preschoolOverride, false, null, null, null,
+                primaryTrack, primaryOverride, middleTrack, middleOverride,
+                highTrack, highOverride, universityTrack, universityOverride,
+                manualLivingCost, isAwayFromHome);
+    }
+
     @Builder
     public ChildEducationSettings(Long childProfileId,
             PreschoolTrack preschoolTrack, StageOverride preschoolOverride,
+            Boolean preschoolSplitEnabled, Integer preschoolStage1Duration,
+            PreschoolTrack preschoolTrack2, StageOverride preschoolOverride2,
             PrimaryTrack primaryTrack, StageOverride primaryOverride,
             MiddleTrack middleTrack, StageOverride middleOverride,
             HighTrack highTrack, StageOverride highOverride,
@@ -163,6 +201,10 @@ public class ChildEducationSettings extends AuditableEntity {
         this.childProfileId = Objects.requireNonNull(childProfileId, "childProfileId must not be null");
         this.preschoolTrack = preschoolTrack != null ? preschoolTrack : PreschoolTrack.HOME;
         this.preschoolOverride = preschoolOverride;
+        this.preschoolSplitEnabled = preschoolSplitEnabled != null ? preschoolSplitEnabled : false;
+        this.preschoolStage1Duration = preschoolStage1Duration;
+        this.preschoolTrack2 = preschoolTrack2;
+        this.preschoolOverride2 = preschoolOverride2;
         this.primaryTrack = primaryTrack != null ? primaryTrack : PrimaryTrack.NONE;
         this.primaryOverride = primaryOverride;
         this.middleTrack = middleTrack != null ? middleTrack : MiddleTrack.NONE;
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java
index ef317058..a83e3879 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java
@@ -52,6 +52,10 @@ public class ChildEducationSettingsResponse {
 
     private PreschoolTrack preschoolTrack;
     private StageOverrideResponse preschoolOverride;
+    private Boolean preschoolSplitEnabled;
+    private Integer preschoolStage1Duration;
+    private PreschoolTrack preschoolTrack2;
+    private StageOverrideResponse preschoolOverride2;
 
     private PrimaryTrack primaryTrack;
     private StageOverrideResponse primaryOverride;
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java
index 605ad1c2..041cc6bf 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java
@@ -49,6 +49,11 @@ public class UpdateChildEducationSettingsRequest {
     private PreschoolTrack preschoolTrack;
     @Valid
     private StageOverrideRequest preschoolOverride;
+    private Boolean preschoolSplitEnabled;
+    private Integer preschoolStage1Duration;
+    private PreschoolTrack preschoolTrack2;
+    @Valid
+    private StageOverrideRequest preschoolOverride2;
 
     private PrimaryTrack primaryTrack;
     @Valid
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java
index d9cbdfc9..3eeeda46 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java
@@ -50,6 +50,9 @@ import ch.lin.finance.hub.backend.api.common.exception.InvalidEducationTimelineE
 import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.ChildEducationSettingsMapper;
 import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.StageOverrideRequest;
 import ch.lin.finance.hub.backend.api.controller.profile.child.education.dto.UpdateChildEducationSettingsRequest;
+import java.math.BigDecimal;
+
+import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PrimaryTrack;
 
 @ExtendWith(MockitoExtension.class)
@@ -172,4 +175,38 @@ class ChildEducationSettingsControllerTest {
                 .andExpect(jsonPath("$.data.code").value("INVALID_EDUCATION_TIMELINE"))
                 .andExpect(jsonPath("$.data.message").value("Timeline Overlap: Start date cannot be earlier than previous stage"));
     }
+
+    @Test
+    void updateSettings_WithPreschoolTwoStage_ReturnsOk() throws Exception {
+        StageOverrideRequest override2 = StageOverrideRequest.builder()
+                .manualStartMonth(4)
+                .manualDuration(24)
+                .manualTuition(new BigDecimal("1800000"))
+                .build();
+
+        UpdateChildEducationSettingsRequest request = UpdateChildEducationSettingsRequest.builder()
+                .preschoolTrack(PreschoolTrack.HOME)
+                .preschoolSplitEnabled(true)
+                .preschoolStage1Duration(12)
+                .preschoolTrack2(PreschoolTrack.INTL)
+                .preschoolOverride2(override2)
+                .build();
+
+        ChildEducationSettingsResult result = new ChildEducationSettingsResult();
+        result.setPreschoolTrack(PreschoolTrack.HOME);
+        result.setPreschoolSplitEnabled(true);
+        result.setPreschoolStage1Duration(12);
+        result.setPreschoolTrack2(PreschoolTrack.INTL);
+
+        when(settingsService.updateSettings(eq(1L), any())).thenReturn(result);
+
+        mockMvc.perform(put("/profile/children/1/education-settings")
+                .contentType(MediaType.APPLICATION_JSON)
+                .content(objectMapper.writeValueAsString(request)))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$.status").value("success"))
+                .andExpect(jsonPath("$.data.preschoolSplitEnabled").value(true))
+                .andExpect(jsonPath("$.data.preschoolStage1Duration").value(12))
+                .andExpect(jsonPath("$.data.preschoolTrack2").value("INTL"));
+    }
 }
diff --git a/finance-hub-backend/todo/pending fix/feature-requirement-125.md b/finance-hub-backend/todo/pending fix/feature-requirement-125.md
index b8a48bb2..9a9576ab 100644
--- a/finance-hub-backend/todo/pending fix/feature-requirement-125.md
+++ b/finance-hub-backend/todo/pending fix/feature-requirement-125.md
@@ -28,3 +28,103 @@
 - 缺點：工程量比方向A大很多，等於是先把上述已結案討論裡提到的「架構層級重新設計」做出通用版本，只是這次先只在幼稚園這裡真正用到——如果之後確定其他階段真的不會用到這個彈性，這部分投入可能是超前部署、目前用不到的複雜度。
 
 建議實作者先跟使用者確認：這次要的是「只解決幼稚園這一個具體需求」（傾向方向A，改動小、風險低），還是「順便把架構做成通用、為將來可能的類似需求鋪路」（傾向方向B），這是實作規模的取捨，由使用者決定。
+
+---
+
+## 拍板定案方向與架構設計（Design Decision & Technical Spec）
+
+經過評估與討論，確認採用**方向 A（專為幼稚園擴充兩段式欄位與排程切換，其餘小學至大學階段完全維持現狀）**。
+
+### 一、核心數學模型：瓜分幼稚園 3 年（36 個月）
+幼稚園標準入學年齡為滿 3 歲（K3），小學法定入學年齡為滿 6 歲。
+在不延後入學的標準情況下，幼稚園總時長為固定 **36 個月（3 年）**。
+兩段式之本質為**瓜分此 36 個月**：
+$$\text{第一段時長 } D_1 + \text{第二段時長 } D_2 = 36 \text{ 個月}$$
+
+---
+
+### 二、防呆校正與容錯規則（Domain Rules & Normalization）
+
+為避免時間軸排積木時產生「真空斷層（Vacuum Gap）」，且完全符合真實育兒經驗（小孩在 K5 畢業年前夕不會無故中退回家蹲），後端引擎採取以下規則：
+
+#### 1. 單軌模式（未啟用兩段式 / 未指定第二段）
+- **儲存層嚴格防呆（Service Validation）**：
+  若前端或 API 傳入小於 36 個月的時長（例如誤傳 INTL 24 個月、DAYCARE 12 個月），儲存層直接拋出 `IllegalArgumentException`（400 錯誤），拒絕髒資料入庫。
+- **計算層保底原則（Clamping - 縱深防禦）**：
+  計算引擎強制 $\text{duration} = \max(36, \text{duration})$，自動念滿 3 年，絕不提前結束讓時間軸產生真空。
+- **支援延後入學**：若輸入 $\ge 36$ 個月（例如 48 個月），仍依指定時長延後接軌小學。
+
+#### 2. 兩段模式（啟用兩段式並指定第二段）
+- **儲存層嚴格防呆（Service Validation）**：
+  - 若 `preschoolSplitEnabled == true` 但 `preschoolTrack2 == null`，直接拋出 `IllegalArgumentException` 拒絕儲存。
+  - 第一段時長必須嚴格為 12 或 24 個月，其餘無效值直接拋錯。
+- **互補補滿原則**：
+  第二段時長自動設為 $36 - D_1$ 個月（即 2 年或 1 年），無縫頂到小學 6 歲入學點。
+- **計算層二次防禦**：
+  若底層計算遭遇未預期的缺漏值，自動 fallback 為單軌滿 36 個月模式，保護數值模擬引擎絕不拋 Exception。
+
+---
+
+### 三、改動範圍與具體設計（Scope of Work）
+
+#### 1. 資料持久層（Domain & DB）
+- [x] **[ChildEducationSettings.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)**
+  在 `finance_child_education_settings` 表新增以下欄位（皆為 nullable，100% 向後相容）：
+  - `preschoolSplitEnabled` (Boolean)：是否啟用幼兒園兩段式（預設 false）。
+  - `preschoolStage1Duration` (Integer)：第一段就讀時長（月數，例如 12 或 24）。
+  - `preschoolTrack2` (PreschoolTrack)：第二段就讀軌道（HOME, DAYCARE, INTL）。
+  - `preschoolOverride2` (StageOverride embedded)：第二段的自訂學費與覆寫值。
+  - 保留既有建構子重載，確保向前與向後 100% 相容。
+  - 新增領域單元測試：[ChildEducationSettingsTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettingsTest.java)。
+
+
+#### 2. 服務層（Command / Result）與 控制層（DTO / Mapper）
+- [x] **服務層 Command 與 Result**：
+  - **[UpdateChildEducationSettingsCommand.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/command/UpdateChildEducationSettingsCommand.java)**：新增 `preschoolSplitEnabled`、`preschoolStage1Duration`、`preschoolTrack2`、`preschoolOverride2`（型別為 `StageOverrideCommand`）。
+  - **[ChildEducationSettingsResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/result/ChildEducationSettingsResult.java)**：新增上述 4 個欄位，並更新 `fromEntity(...)` 工廠方法。
+  - **[ChildEducationSettingsServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)**：在 `updateSettings(...)` 內將 Command 的新欄位設定進 Entity。
+  - 新增服務層單元測試：[ChildEducationSettingsServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImplTest.java)。
+- [x] **Web 控制層 Request / Response / Mapper**：
+  - **[UpdateChildEducationSettingsRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/UpdateChildEducationSettingsRequest.java)**：新增上述 4 個請求欄位。
+  - **[ChildEducationSettingsResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsResponse.java)**：新增上述 4 個回應欄位。
+  - **[ChildEducationSettingsMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/dto/ChildEducationSettingsMapper.java)**：確認 MapStruct 正確自動映射 Request $\to$ Command 以及 Result $\to$ Response。
+  - 新增控制層 MockMvc 測試：[ChildEducationSettingsControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsControllerTest.java)。
+
+
+#### 3. 核心排程與計算引擎
+- [x] **[ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)**：
+  - 新增 `toBuilder = true`、`stage2` 欄位與 `hasStage2()` 輔助方法。
+- [x] **[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)**：
+  - 在 `resolvePreschool` 中依 `preschoolSplitEnabled` 解析出兩段的軌道與設定。
+  - 單軌模式落實 Clamping 保底原則：時長強制至少 36 個月（$\max(36, \text{duration})$）。
+  - 兩段模式落實 $D_1 \in \{12, 24\}$ 且 $D_2 = 36 - D_1$，並支援第二段覆寫學費與 fallback 防禦。
+  - 新增單元測試：[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)。
+- [x] **[EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)**：
+  - 幼稚園在 `timeline.getStageIntervals()` 中依然維持單一連續區間（滿 3 歲到滿 6 歲，生活費為 0）。
+  - 在產生學費繳款事件（`generateStageEvents`）時：
+    - 前 $D_1$ 個月按第一段軌道產生事件（若為 HOME 則金額為 0）。
+    - 第 $D_1 + 1$ 個月（切換月）起，按第二段軌道產生一次性入學金（若有）與後續學費事件。
+  - 新增時間軸排程單元測試：[EducationTimelineProjectorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java)。
+- [x] **下游無感**：
+  資產模擬引擎、GA / Grid Search、年度現金流計算完全零改動。
+
+#### 4. 前端 UI 設計（[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)）
+- [x] **[use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)**：
+  - 擴充 `ChildEducationSettingsDto`，新增 `preschoolSplitEnabled`, `preschoolStage1Duration`, `preschoolTrack2`, `preschoolOverride2` 四個欄位。
+- [x] **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)**：
+  - 幼兒園（Preschool）卡片新增 Switch「中途更換軌道（Two-stage）」開關。
+  - **開關關閉（預設）**：
+    - 顯示單一軌道選單（HOME / DAYCARE / INTL），時長固定為 3 年。
+    - Advanced 中的 Duration 只能延長（$\ge 3$ 年），不提供小於 3 年的輸入（輸入小於 3 自動 Clamp 為 3）。
+  - **開關開啟**：
+    - 第一段：軌道選單 + 時長下拉選單（【1 年 (12 個月)】/【2 年 (24 個月)】）+ 第一段自訂學費。
+    - 第二段：銜接軌道選單 + 時長唯讀顯示（*「2 年（直到小學）」* 或 *「1 年（直到小學）」*）+ 第二段自訂學費（`preschoolOverride2.manualTuition`）。
+    - Advanced 中的 Duration 自動鎖定顯示為 `3 Years (2-Stage Total)`，避免手動輸入與第一段時長產生衝突。
+  - **前端嚴格防呆與資料清理**：
+    - 切換關閉時自動重設清空第二段軌道與時長欄位為 `null`。
+    - 切換開啟時預設第一段時長 12 個月，第二段軌道 INTL。
+    - 儲存前雙重防禦檢查：兩段模式必填 track2 與合規時長（12 或 24）；單軌模式若時長 < 36 自動 Clamp 為 36。
+- [x] **其他階段隔離**：
+  - 小學、國中、高中、大學卡片維持原狀，完全不受干擾。
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                                                                       10029  12.69G   4.99    21:44:42  