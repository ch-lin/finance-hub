# Fixed 113：前端「時間軸重疊」檢查比後端實際支援的規則更嚴格，會擋掉後端本來能正確處理的合法設定

## 嚴重程度
Medium（不會算出錯誤數字，但會讓使用者完全無法儲存一種後端明明可以正確處理的合理設定，而且跳出的警示訊息會誤導使用者去修改本來就沒有問題的地方）

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:244-333`（`timelineInfo`，第303-315行純粹依小孩年齡算出每個階段的起算月份，完全沒有考慮上一階段實際讀了多久，只要算出來有重疊就判定成`isConflict`）、第347-350行跟第699行（偵測到衝突就停用「儲存」按鈕）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java:102-121`（`calculateStageStart`，後端真正的「順延排積木」演算法：同時算出純依年齡的`standardStart`跟「接續上一階段實際結束月份」的`chainedStart`，取兩者較晚的一個當作這個階段真正的起點——上一階段讀得比標準時間長，下一階段就會自動被推後，不會被判定成任何「衝突」）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java:148-170`（`validateTimeline`，後端唯一的驗證是「求學生涯總年數是否超過40年」，完全沒有逐階段重疊的檢查）

## 現狀
前端畫面上計算每個學制階段的起訖時間時，是「純粹依小孩年齡」去算每個階段該從幾歲開始（完全不管上一個階段實際讀了多久），只要算出來的結果跟上一階段有重疊，就判定成「時間軸重疊」，跳出紅色警示並直接鎖住「儲存」按鈕。但後端真正的排積木演算法，本來就是設計成會自動處理「上一階段讀得比標準時間長」這種情況——會自動把下一階段的起點往後推，這正是後端這套演算法存在的目的，後端本身完全沒有「重疊即錯誤」這個概念。

## 為何是問題
只要使用者透過「進階設定」把某個階段的就讀時長，改得比標準時長更長（例如模擬小孩重讀一年幼稚園、或延長托兒所時間），意圖是想讓下一階段的起點跟著往後遞延——這正是後端排積木演算法設計要處理的情境——前端會顯示「時間軸重疊」的警示，並直接鎖住儲存按鈕，即便完全一樣的資料送到後端，後端可以正確處理、不會有任何錯誤。

## 具體錯誤情境
家長想模擬小孩多讀一年幼稚園或延長托兒所時間（例如把時長從標準的3年改成4年，希望小學入學時間自動往後推一年），在「進階設定」裡把時長改成4年。前端立刻跳出鮮紅色的「⚠️ Timeline Overlap: Start date cannot be earlier than the previous stage's completion date」警示，並鎖住「儲存」按鈕——但這個訊息本身就是誤導的，因為根本沒有東西需要修正，後端的排積木邏輯本來就會正確地把小學起點自動往後推。使用者完全無法儲存這個合理、後端能正確處理的設定。

## 建議修正
這裡有兩個方向，列出優缺點供選擇，不代為決定：

**方向A：前端改用跟後端一致的排積木演算法**
把後端`calculateStageStart`那套「取`standardStart`跟`chainedStart`較晚者」的邏輯，在前端也實作一份對應版本，只用來即時顯示每個階段實際會落在哪個年月（維持現有的「即時起訖徽章」功能），不再把「有重疊」當成錯誤來看待。
- 優點：保留現有的即時預覽體驗（徽章、時間軸視覺化），且顯示的資訊會跟後端真正的計算結果一致。
- 缺點：前端需要維護一份跟後端邏輯對應的複本，未來如果後端演算法調整，前端這份也要記得同步更新。

**方向B：前端完全移除這個自製的「衝突」判斷，只依賴後端既有的40年總長度驗證**
拿掉前端自己發明的「重疊即錯誤」邏輯，改成單純呼叫後端API，後端如果真的判定不合理（超過40年），已經有現成的`InvalidEducationTimelineException`會轉成400錯誤，前端只需要把這個錯誤訊息用toast顯示出來即可。
- 優點：邏輯只維護一份（在後端），前端不會有跟後端不同步的風險。
- 缺點：會失去現有的「即時預覽、存檔前就能看到警示」這個UX優點，使用者要送出後才會知道是否有問題。

---

## 推薦最佳解：方向 C（非阻塞式學制提示 + 解除儲存封鎖 + 順延即時預覽）

### 核心思維
**把「警示（Notice）」與「阻擋儲存（Blocking Validator）」徹底解耦。**
後端排積木演算法本來就能正確處理延長就讀並自動順延後續階段。因此前端不應越權將此情境判定為語法錯誤，而是應該放行儲存，並在超過通常年限時給予友善提示。

### 具體設計
1. **解除儲存封鎖（Save Always Allowed）**：
   - 移除 `handleSaveChild` 與 `DialogFooter` 中基於時間軸的 `disabled={timelineInfo.hasConflict}` 與 `Fix timeline overlap to save` 阻擋邏輯。
   - 使用者隨時可以保存合理的自訂教育規劃。
2. **超出標準年限提示（Informative Notice）**：
   - 前端各階段本來就持有標準時長 `defaultDurationMonths`（如小學 72 個月=6 年、幼兒園 36 個月=3 年、大學 48 個月=4 年）。
   - 當使用者設定的時長大於標準時長（`durationMonths > defaultDurationMonths`）時：
     - **移除鮮紅嚇人的「⚠️ Timeline Overlap: Start date cannot be earlier...」紅牌**。
     - **改為非阻塞的溫馨提示（Informative Alert / Badge）**：
       > 💡 *此階段就讀時長（{實際年數} 年）超過標準學制（{標準年數} 年），後續教育階段將自動依序向後遞延。*
     - 若為不小心誤填（如小學填成 10 年），使用者能立即得知並修正；若為刻意模擬重讀或延長托兒，使用者亦能安心儲存。
3. **徽章起訖年月即時順延（Chained Start Preview）**：
   - 前端計算起點時，引入與後端一致的輕量順延邏輯：
     ```ts
     const chainedStartAbsMonths = previousStageEndAbs !== null ? previousStageEndAbs + 1 : startAbsMonths;
     const actualStartAbsMonths = Math.max(startAbsMonths, chainedStartAbsMonths);
     ```
   - 讓預覽徽章上呈現的起訖時間直接反映順延後的年月（例如幼兒園讀 4 年，小學徽章自動顯示延後至 7 歲入學），直覺且符合真實後端計算結果。

---

## 實作清單（Checklist）
- [x] `use-child-profile.ts`:
  - [x] 擴充 `StageTimelineInfo` 介面，支援 `standardDurationYears`、`isExtendedDuration` 與 `isDelayedByPriorStage` 欄位。
- [x] `child-profile-section.tsx`:
  - [x] 移除 `handleSaveChild` 與 `DialogFooter` 的 `hasConflict` 阻擋儲存條件，合法教育規劃一律放行儲存。
  - [x] 在 `timelineInfo` 計算中，引入與後端一致的 chained start 順延計算，使徽章顯示順延後的真實起訖時間。
  - [x] 階段卡片移除「⚠️ Timeline Overlap」紅字錯誤橫幅，改為超出標準年限時顯示琥珀色非阻塞溫馨提示，因前階段順延時顯示藍色提示。
  - [x] 標題區塊與 Dialog 底部同步將破壞性紅字替換為溫和的順延狀態通知。

---

## 修正結果報告

1. **解鎖合法儲存**：
   徹底移除前端在 `handleSaveChild` 與 `DialogFooter` 中對於時間軸「衝突」的硬性攔截。現在使用者若自訂教育階段延長（例如多讀一年幼稚園），可直接點擊「Save Profile」保存，後端將順暢處理排積木演算法。
2. **非阻塞式教育年限提示**：
   - 當使用者設定的時長超過該學制標準時長（如幼稚園超過 3 年）時，卡片顯示琥珀色溫馨提示，明確告知：`就讀時長（X 年）超過標準學制（Y 年），後續教育階段將自動依序向後順延`。
   - 後續被推遲的階段（如小學）則顯示藍色提示：`受前一階段就讀時長影響，本階段起算時間已自動向後順延至 YYYY/MM`。
3. **即時預覽所見即所得**：
   徽章時間軸已改用與後端 `EducationTimelineProjector` 一致的順延公式，預覽日期即時反映真實排程，UX 體驗大幅提升。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                 10040  12.92G   5.05    14:29:10  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index 060302d..c5d2f6d 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -318,7 +318,6 @@ export function ChildProfileSection() {
 
     const result: Record<string, StageTimelineInfo> = {};
     let previousStageEndAbs: number | null = null;
-    let anyConflict = false;
 
     for (const stage of stagesConfig) {
       if (stage.track === 'NONE') {
@@ -328,25 +327,44 @@ export function ChildProfileSection() {
       const startMonth = stage.override?.manualStartMonth || stage.defaultStartMonth;
       const durationMonths = stage.override?.manualDuration || stage.defaultDurationMonths;
       const cohortYear = computeCohortYear(startMonth);
-      const startYear = cohortYear + stage.standardEntranceAge;
+      const standardStartYear = cohortYear + stage.standardEntranceAge;
+      const standardStartAbsMonths = standardStartYear * 12 + startMonth;
 
-      const startAbsMonths = startYear * 12 + startMonth;
-      const endAbsMonths = startAbsMonths + durationMonths - 1;
+      // Chained start logic: if previous stage ended later, chain to the next available occurrence of startMonth
+      let startAbsMonths = standardStartAbsMonths;
+      if (previousStageEndAbs !== null) {
+        const earliestNextAbsMonths = previousStageEndAbs + 1;
+        const earliestNextYear = Math.floor((earliestNextAbsMonths - 1) / 12);
+        const earliestNextMonth = ((earliestNextAbsMonths - 1) % 12) + 1;
 
-      let isConflict = false;
-      if (previousStageEndAbs !== null && startAbsMonths <= previousStageEndAbs) {
-        isConflict = true;
-        anyConflict = true;
+        let candidateYear = earliestNextYear;
+        if (startMonth < earliestNextMonth) {
+          candidateYear += 1;
+        }
+        const chainedStartAbsMonths = candidateYear * 12 + startMonth;
+        if (chainedStartAbsMonths > standardStartAbsMonths) {
+          startAbsMonths = chainedStartAbsMonths;
+        }
       }
 
+      const endAbsMonths = startAbsMonths + durationMonths - 1;
+      const startYear = Math.floor((startAbsMonths - 1) / 12);
       const endYear = Math.floor((endAbsMonths - 1) / 12);
       const endMonth = ((endAbsMonths - 1) % 12) + 1;
 
+      const durationYears = Math.round((durationMonths / 12) * 10) / 10;
+      const standardDurationYears = Math.round((stage.defaultDurationMonths / 12) * 10) / 10;
+      const isExtendedDuration = durationMonths > stage.defaultDurationMonths;
+      const isDelayedByPriorStage = startAbsMonths > standardStartAbsMonths;
+
       result[stage.key] = {
         startFormatted: `${startYear}/${startMonth.toString().padStart(2, '0')}`,
         endFormatted: `${endYear}/${endMonth.toString().padStart(2, '0')}`,
-        durationYears: Math.round((durationMonths / 12) * 10) / 10,
-        isConflict,
+        durationYears,
+        standardDurationYears,
+        isExtendedDuration,
+        isDelayedByPriorStage,
+        isConflict: false,
         startAbsMonths,
         endAbsMonths
       };
@@ -354,9 +372,14 @@ export function ChildProfileSection() {
       previousStageEndAbs = endAbsMonths;
     }
 
-    return { stages: result, hasConflict: anyConflict };
+    return { stages: result, hasConflict: false };
   }, [editingChild?.birthDate, editingSettings, educationConfig]);
 
+  const hasExtendedStages = useMemo(
+    () => Object.values(timelineInfo.stages).some(s => s.isExtendedDuration),
+    [timelineInfo]
+  );
+
   const handleRemoveChild = async (id: number) => {
     if (!confirm("Are you sure you want to delete this child profile?")) return;
     await removeChild(id);
@@ -369,10 +392,6 @@ export function ChildProfileSection() {
       return;
     }
 
-    if (timelineInfo.hasConflict) {
-      toast.error("Please resolve timeline overlap conflicts before saving.");
-      return;
-    }
 
     // Step 1: Save basic child profile
     const savedChild = await saveChild(editingChild);
@@ -530,9 +549,9 @@ export function ChildProfileSection() {
             <div className="md:col-span-8 space-y-4">
               <h3 className="font-semibold text-sm flex items-center justify-between border-b pb-2">
                 <span>Education Stages & Overrides</span>
-                {timelineInfo.hasConflict && (
-                  <Badge variant="destructive" className="text-xs">
-                    Timeline Conflict Detected
+                {hasExtendedStages && (
+                  <Badge variant="outline" className="text-xs border-amber-500/30 text-amber-600 dark:text-amber-400 bg-amber-500/10">
+                    Custom Stage Duration (Auto-chained)
                   </Badge>
                 )}
               </h3>
@@ -724,16 +743,16 @@ export function ChildProfileSection() {
           </div>
           <DialogFooter className="flex items-center justify-between sm:justify-between">
             <div>
-              {timelineInfo.hasConflict && (
-                <span className="text-xs text-destructive font-medium flex items-center gap-1">
+              {hasExtendedStages && (
+                <span className="text-xs text-amber-600 dark:text-amber-400 font-medium flex items-center gap-1">
                   <AlertTriangle className="w-3.5 h-3.5" />
-                  Fix timeline overlap to save
+                  學制年限自訂延長，後續階段將由系統自動順延排程
                 </span>
               )}
             </div>
             <div className="flex gap-2">
               <Button variant="outline" onClick={() => setIsDialogOpen(false)}>Cancel</Button>
-              <Button onClick={handleSaveChild} disabled={timelineInfo.hasConflict || isSettingsLoading}>
+              <Button onClick={handleSaveChild} disabled={isSettingsLoading}>
                 {isSettingsLoading ? "Loading..." : "Save Profile"}
               </Button>
             </div>
@@ -865,18 +884,18 @@ function StageCard({
   const isNone = trackValue === 'NONE';
 
   return (
-    <div className={`p-3 rounded-lg border transition-all ${
-      timeline?.isConflict
-        ? 'border-destructive/60 bg-destructive/5'
-        : 'border-border/70 bg-card hover:border-border'
-    }`}>
+    <div className="p-3 rounded-lg border transition-all border-border/70 bg-card hover:border-border">
       {/* Header: Title + Timeline Badge + Advanced Toggle */}
       <div className="flex items-center justify-between gap-2 mb-2">
         <div className="flex items-center gap-2 flex-wrap">
           <span className="font-medium text-xs text-foreground">{title}</span>
           {timeline && !isNone && (
             <Badge variant="outline" className={`text-[10px] px-1.5 py-0 font-mono ${
-              timeline.isConflict ? 'border-destructive text-destructive bg-destructive/10' : 'bg-primary/5 text-primary border-primary/20'
+              timeline.isExtendedDuration
+                ? 'border-amber-500/30 text-amber-600 dark:text-amber-400 bg-amber-500/10'
+                : timeline.isDelayedByPriorStage
+                ? 'border-blue-500/30 text-blue-600 dark:text-blue-400 bg-blue-500/10'
+                : 'bg-primary/5 text-primary border-primary/20'
             }`}>
               {timeline.startFormatted} ~ {timeline.endFormatted} ({timeline.durationYears}y)
             </Badge>
@@ -898,11 +917,21 @@ function StageCard({
         )}
       </div>
 
-      {/* Conflict Alert Banner */}
-      {timeline?.isConflict && (
-        <div className="mb-2 p-2 rounded bg-destructive/15 text-destructive text-xs flex items-start gap-1.5 animate-in fade-in">
-          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
-          <span>⚠️ Timeline Overlap: Start date cannot be earlier than the previous stage's completion date. Please check duration or start month.</span>
+      {/* Informative Notice for Extended Duration or Delayed Start */}
+      {timeline?.isExtendedDuration && (
+        <div className="mb-2 p-2 rounded bg-amber-500/10 border border-amber-500/20 text-amber-700 dark:text-amber-300 text-xs flex items-start gap-1.5 animate-in fade-in">
+          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5 text-amber-600 dark:text-amber-400" />
+          <span>
+            就讀時長（{timeline.durationYears} 年）超過標準學制（{timeline.standardDurationYears} 年），後續教育階段將自動依序向後順延。
+          </span>
+        </div>
+      )}
+      {!timeline?.isExtendedDuration && timeline?.isDelayedByPriorStage && (
+        <div className="mb-2 p-2 rounded bg-blue-500/10 border border-blue-500/20 text-blue-700 dark:text-blue-300 text-xs flex items-start gap-1.5 animate-in fade-in">
+          <span className="text-xs mt-0.5">💡</span>
+          <span>
+            受前一階段就讀時長影響，本階段起算時間已自動向後順延至 {timeline.startFormatted}。
+          </span>
         </div>
       )}
 
diff --git a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
index e914fa2..2020db0 100644
--- a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
+++ b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
@@ -36,6 +36,9 @@ export interface StageTimelineInfo {
   startFormatted: string;
   endFormatted: string;
   durationYears: number;
+  standardDurationYears?: number;
+  isExtendedDuration?: boolean;
+  isDelayedByPriorStage?: boolean;
   isConflict: boolean;
   startAbsMonths: number;
   endAbsMonths: number;
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                                            10041  12.93G   5.07    14:29:16  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                    10042  12.97G   5.03    14:29:23  
diff --git a/finance-hub-backend/todo/pending fix/issue-113.md b/finance-hub-backend/todo/pending fix/issue-113.md
index ecf11152..8def7a2b 100644
--- a/finance-hub-backend/todo/pending fix/issue-113.md
+++ b/finance-hub-backend/todo/pending fix/issue-113.md
@@ -29,3 +29,55 @@ Medium（不會算出錯誤數字，但會讓使用者完全無法儲存一種
 拿掉前端自己發明的「重疊即錯誤」邏輯，改成單純呼叫後端API，後端如果真的判定不合理（超過40年），已經有現成的`InvalidEducationTimelineException`會轉成400錯誤，前端只需要把這個錯誤訊息用toast顯示出來即可。
 - 優點：邏輯只維護一份（在後端），前端不會有跟後端不同步的風險。
 - 缺點：會失去現有的「即時預覽、存檔前就能看到警示」這個UX優點，使用者要送出後才會知道是否有問題。
+
+---
+
+## 推薦最佳解：方向 C（非阻塞式學制提示 + 解除儲存封鎖 + 順延即時預覽）
+
+### 核心思維
+**把「警示（Notice）」與「阻擋儲存（Blocking Validator）」徹底解耦。**
+後端排積木演算法本來就能正確處理延長就讀並自動順延後續階段。因此前端不應越權將此情境判定為語法錯誤，而是應該放行儲存，並在超過通常年限時給予友善提示。
+
+### 具體設計
+1. **解除儲存封鎖（Save Always Allowed）**：
+   - 移除 `handleSaveChild` 與 `DialogFooter` 中基於時間軸的 `disabled={timelineInfo.hasConflict}` 與 `Fix timeline overlap to save` 阻擋邏輯。
+   - 使用者隨時可以保存合理的自訂教育規劃。
+2. **超出標準年限提示（Informative Notice）**：
+   - 前端各階段本來就持有標準時長 `defaultDurationMonths`（如小學 72 個月=6 年、幼兒園 36 個月=3 年、大學 48 個月=4 年）。
+   - 當使用者設定的時長大於標準時長（`durationMonths > defaultDurationMonths`）時：
+     - **移除鮮紅嚇人的「⚠️ Timeline Overlap: Start date cannot be earlier...」紅牌**。
+     - **改為非阻塞的溫馨提示（Informative Alert / Badge）**：
+       > 💡 *此階段就讀時長（{實際年數} 年）超過標準學制（{標準年數} 年），後續教育階段將自動依序向後遞延。*
+     - 若為不小心誤填（如小學填成 10 年），使用者能立即得知並修正；若為刻意模擬重讀或延長托兒，使用者亦能安心儲存。
+3. **徽章起訖年月即時順延（Chained Start Preview）**：
+   - 前端計算起點時，引入與後端一致的輕量順延邏輯：
+     ```ts
+     const chainedStartAbsMonths = previousStageEndAbs !== null ? previousStageEndAbs + 1 : startAbsMonths;
+     const actualStartAbsMonths = Math.max(startAbsMonths, chainedStartAbsMonths);
+     ```
+   - 讓預覽徽章上呈現的起訖時間直接反映順延後的年月（例如幼兒園讀 4 年，小學徽章自動顯示延後至 7 歲入學），直覺且符合真實後端計算結果。
+
+---
+
+## 實作清單（Checklist）
+- [x] `use-child-profile.ts`:
+  - [x] 擴充 `StageTimelineInfo` 介面，支援 `standardDurationYears`、`isExtendedDuration` 與 `isDelayedByPriorStage` 欄位。
+- [x] `child-profile-section.tsx`:
+  - [x] 移除 `handleSaveChild` 與 `DialogFooter` 的 `hasConflict` 阻擋儲存條件，合法教育規劃一律放行儲存。
+  - [x] 在 `timelineInfo` 計算中，引入與後端一致的 chained start 順延計算，使徽章顯示順延後的真實起訖時間。
+  - [x] 階段卡片移除「⚠️ Timeline Overlap」紅字錯誤橫幅，改為超出標準年限時顯示琥珀色非阻塞溫馨提示，因前階段順延時顯示藍色提示。
+  - [x] 標題區塊與 Dialog 底部同步將破壞性紅字替換為溫和的順延狀態通知。
+
+---
+
+## 修正結果報告
+
+1. **解鎖合法儲存**：
+   徹底移除前端在 `handleSaveChild` 與 `DialogFooter` 中對於時間軸「衝突」的硬性攔截。現在使用者若自訂教育階段延長（例如多讀一年幼稚園），可直接點擊「Save Profile」保存，後端將順暢處理排積木演算法。
+2. **非阻塞式教育年限提示**：
+   - 當使用者設定的時長超過該學制標準時長（如幼稚園超過 3 年）時，卡片顯示琥珀色溫馨提示，明確告知：`就讀時長（X 年）超過標準學制（Y 年），後續教育階段將自動依序向後順延`。
+   - 後續被推遲的階段（如小學）則顯示藍色提示：`受前一階段就讀時長影響，本階段起算時間已自動向後順延至 YYYY/MM`。
+3. **即時預覽所見即所得**：
+   徽章時間軸已改用與後端 `EducationTimelineProjector` 一致的順延公式，預覽日期即時反映真實排程，UX 體驗大幅提升。
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                         10043  12.97G   5.03    14:29:25  
