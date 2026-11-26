# Fixed 112：快速切換編輯不同小孩的教育設定時，較慢的網路回應可能覆蓋掉另一個小孩的資料

## 嚴重程度
High（會造成真實存進資料庫的資料被悄悄覆蓋，不是畫面顯示問題；不需要特殊操作，單純網路回應速度不同就可能觸發）

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:192-215`（`openEditChild`）
- 同檔案第340行起（`handleSaveChild`，把`editingSettings`跟`editingChild.id`打包送出）
- 同檔案第176-190行（`openAddChild`，同樣的模式也會受影響）

## 現狀
`openEditChild(child)`會立刻執行`setEditingChild({ ...child })`並打開編輯視窗，然後才`await fetchEducationSettings(child.id)`去抓這個小孩的教育設定。這個等待網路回應的過程中，完全沒有任何機制去確認「等回應真的回來的時候，使用者是不是還在編輯同一個小孩」——回應回來後，第201行`setEditingSettings(settings)`會直接覆蓋目前畫面上的設定，不管此時`editingChild`實際上是誰。

## 為何是問題
如果使用者先點了A小孩的「編輯」（觸發一次抓資料的請求），還沒等回應回來就改點了B小孩的「編輯」（觸發第二次請求），這兩個請求誰先回應完全取決於網路狀況，不保證跟使用者點擊的順序一致。如果A小孩的回應恰好比B小孩的回應晚回來，等A小孩的回應回來時，畫面已經切換成B小孩的編輯視窗了，但`setEditingSettings(A的設定)`還是會執行，把B小孩的畫面內容悄悄換成A小孩的設定。接著使用者對著這個（其實是A小孩、卻掛著B小孩名字的）畫面按下儲存，`handleSaveChild`會用`editingChild.id`（=B小孩的ID）當目標，把A小孩的教育設定內容，整份存進B小孩的資料裡，真正覆蓋掉B小孩原本存好的教育計畫。整個過程沒有任何錯誤訊息或警示，使用者只會在下次重新打開B小孩的設定時，才會發現內容跟自己記憶中的不一樣。

同樣的模式，也出現在`openAddChild`（新增小孩時）以及唯讀的「Estimate」試算圖表（`viewProjection`）——後者因為資料不會存回後端，最糟只是畫面短暫顯示錯誤的小孩資料，不會造成資料庫層級的損壞，嚴重程度遠低於編輯情境。

## 建議修正
在`openEditChild`裡，記錄「目前這次請求對應的child id」（例如用一個ref變數），等`fetchEducationSettings`的回應真正回來時，先確認這個ref裡記錄的id，跟當下`editingChild?.id`是否還一致，不一致就直接捨棄這次回應、不執行`setEditingSettings`。這是前端處理非同步請求時的標準防護寫法，不需要更動後端或資料結構。

---

## 實作清單（Checklist）

1. **非同步請求序號鎖機制（Async Request ID Lock via useRef）**：
   - [x] 在 `child-profile-section.tsx` 新增 `editRequestIdRef` 與 `projectionRequestIdRef`。
   - [x] `openEditChild`、`openAddChild` 與 Dialog 關閉事件觸發時遞增序號。
   - [x] `fetchEducationSettings` 回傳後驗證序號是否仍然一致，若已過期則直接 discard。

2. **教育設定狀態重置與加載期防護**：
   - [x] 在 `openEditChild` 發起請求前重置 `editingSettings(null)` 與 `setAnnualLivingCostInput("")`，消除舊小孩資料殘影。
   - [x] 在 `DialogFooter` 的 Save 按鈕加上 `disabled={timelineInfo.hasConflict || isSettingsLoading}`，防止設定加載中儲存。

3. **試算圖表非同步請求序號防護**：
   - [x] 在 `viewProjection` 加上 `projectionRequestIdRef` 序號校驗，避免不同小孩的試算結果交錯覆蓋。
   - [x] Projection Dialog 關閉時使請求序號失效。

---

## 修正結果報告

### 1. 非同步序號鎖防護（Sequence Token Protection）
- 在 [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx) 引入 `useRef` 機制：
  - `editRequestIdRef`：追蹤目前最新的編輯請求序號。
  - `projectionRequestIdRef`：追蹤目前最新的教育費用試算請求序號。
- 每次呼叫 `openEditChild`、`openAddChild` 或 Dialog `onOpenChange(false)` 關閉時，均會遞增序號，使任何先前的在途中（in-flight）非同步請求立即過期。
- `fetchEducationSettings` 與 `generateProjection` 完成後，嚴格檢查 `editRequestIdRef.current === requestId` 與 `projectionRequestIdRef.current === requestId`。若使用者已切換小孩或關閉視窗，該筆過期資料將直接被丟棄，絕不呼叫 `setState`，徹底杜絕跨小孩資料偷換與覆蓋的競態條件。

### 2. 狀態即時清空與殘影消除
- 在 `openEditChild` 觸發並等待非同步設定返回前，立即將 `editingSettings` 設為 `null`，並將 `annualLivingCostInput` 清空。
- 避免使用者在切換編輯對象時，畫面短暫閃爍並顯示前一位小孩的舊設定。

### 3. 儲存時機守衛（Save Guard）
- 在 `handleSaveChild` 進入點追加 `if (!editingChild || isSettingsLoading) return;` 防禦檢查。
- 在對話框底部 Save 按鈕綁定 `disabled={timelineInfo.hasConflict || isSettingsLoading}`，並在載入中顯示 `"Loading..."`，防止使用者在非同步教育設定尚未加載完畢前誤按儲存而寫入不完整資料。

## Diff
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                            10026  11.23G   3.72    10:45:17  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index 7b199b1..060302d 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -1,6 +1,6 @@
 "use client";
 
-import { useEffect, useState, useMemo } from "react";
+import { useEffect, useState, useMemo, useRef } from "react";
 import { Card, CardHeader, CardTitle, CardContent, CardDescription } from "@/components/ui/card";
 import { Input } from "@/components/ui/input";
 import { Label } from "@/components/ui/label";
@@ -169,12 +169,19 @@ export function ChildProfileSection() {
   const [isProjectionDialogOpen, setIsProjectionDialogOpen] = useState(false);
   const [isProjectionLoading, setIsProjectionLoading] = useState(false);
 
+  // Sequence counters to prevent async race conditions when switching children or requests
+  const editRequestIdRef = useRef<number>(0);
+  const projectionRequestIdRef = useRef<number>(0);
+
   useEffect(() => {
     fetchChildren();
     fetchEducationConfig();
   }, [fetchChildren, fetchEducationConfig]);
 
   const openAddChild = () => {
+    // Invalidate any pending edit settings request
+    editRequestIdRef.current += 1;
+    setIsSettingsLoading(false);
     setEditingChild({
       name: "",
       birthDate: new Date().toISOString().split('T')[0],
@@ -192,13 +199,23 @@ export function ChildProfileSection() {
   };
 
   const openEditChild = async (child: ChildProfile) => {
+    const requestId = ++editRequestIdRef.current;
     setEditingChild({ ...child });
+    // Reset editing settings and inputs immediately to avoid stale data flash
+    setEditingSettings(null);
+    setAnnualLivingCostInput("");
     setExpandedStages({});
     setIsDialogOpen(true);
     
     if (child.id) {
       setIsSettingsLoading(true);
       const settings = await fetchEducationSettings(child.id);
+
+      // Discard stale response if active edit request ID changed
+      if (editRequestIdRef.current !== requestId) {
+        return;
+      }
+
       if (settings) {
         setEditingSettings(settings);
         if (settings.manualLivingCost != null && !isNaN(settings.manualLivingCost)) {
@@ -346,7 +363,7 @@ export function ChildProfileSection() {
   };
 
   const handleSaveChild = async () => {
-    if (!editingChild) return;
+    if (!editingChild || isSettingsLoading) return;
     if (!editingChild.name || !editingChild.birthDate) {
       toast.error("Please provide name and birth date");
       return;
@@ -387,10 +404,16 @@ export function ChildProfileSection() {
   };
 
   const viewProjection = async (childId: number) => {
+    const requestId = ++projectionRequestIdRef.current;
+    setProjectionData(null);
     setIsProjectionLoading(true);
     setIsProjectionDialogOpen(true);
     
     const data = await generateProjection(childId);
+    if (projectionRequestIdRef.current !== requestId) {
+      return;
+    }
+
     if (data) {
       setProjectionData({ childId, data });
     } else {
@@ -464,7 +487,16 @@ export function ChildProfileSection() {
       </CardContent>
 
       {/* Edit / Add Child Dialog */}
-      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
+      <Dialog
+        open={isDialogOpen}
+        onOpenChange={(open) => {
+          setIsDialogOpen(open);
+          if (!open) {
+            editRequestIdRef.current += 1;
+            setIsSettingsLoading(false);
+          }
+        }}
+      >
         <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
           <DialogHeader>
             <DialogTitle>{editingChild?.id ? "Edit" : "Add"} Child Profile</DialogTitle>
@@ -701,8 +733,8 @@ export function ChildProfileSection() {
             </div>
             <div className="flex gap-2">
               <Button variant="outline" onClick={() => setIsDialogOpen(false)}>Cancel</Button>
-              <Button onClick={handleSaveChild} disabled={timelineInfo.hasConflict}>
-                Save Profile
+              <Button onClick={handleSaveChild} disabled={timelineInfo.hasConflict || isSettingsLoading}>
+                {isSettingsLoading ? "Loading..." : "Save Profile"}
               </Button>
             </div>
           </DialogFooter>
@@ -710,7 +742,16 @@ export function ChildProfileSection() {
       </Dialog>
 
       {/* Projection Dialog */}
-      <Dialog open={isProjectionDialogOpen} onOpenChange={setIsProjectionDialogOpen}>
+      <Dialog
+        open={isProjectionDialogOpen}
+        onOpenChange={(open) => {
+          setIsProjectionDialogOpen(open);
+          if (!open) {
+            projectionRequestIdRef.current += 1;
+            setIsProjectionLoading(false);
+          }
+        }}
+      >
         <DialogContent className="max-w-3xl">
           <DialogHeader>
             <DialogTitle>Education Expense Projection</DialogTitle>
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                                                                                 10027  11.22G   3.68    10:45:23  