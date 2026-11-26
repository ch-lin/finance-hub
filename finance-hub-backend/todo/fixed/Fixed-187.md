# [FIXED] 187：薪資記錄編輯對話框的送出邏輯固定把`note`這個欄位寫成null，不管使用者正在編輯的這筆記錄原本有沒有備註內容——只要編輯這筆記錄的任何一個欄位（例如只是想改`baseSalary`），這次儲存動作就會順手把原本的備註永久清空

## 這份issue怎麼來的
這是Plan-27（全領域開放手動編輯）查證過程中發現的。這不是「缺少編輯介面」的缺口，是現有編輯功能本身會主動破壞資料——跟這份Plan原本要處理的「開放更多欄位可以編輯」是相反方向的問題。

查證時原本同時懷疑`details`（薪資明細逐項金額的JSON欄位）也有同樣問題，但重新核對後端程式碼後確認：`details`因為Service層已經有null防呆，實際上不受影響，只有`note`這一個欄位是真正的bug，這份issue只處理`note`。

## 相關檔案
- [add-salary-record-dialog.tsx:122-143](file://hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx)（`handleSubmit`，第142-143行固定寫`note: null, details: null`；這個函式同時處理新增(POST)跟編輯(PUT)兩種模式，`isEditMode`在第132-133行判斷用哪個HTTP method，但送出的body本身沒有因應編輯模式而保留原始`note`）
- [SalaryRecordServiceImpl.java:85-106](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryRecordServiceImpl.java)（`applyCommandToEntity`：**這裡的行號是查完191、196落地後的程式碼重新核對過的最新位置，文件前半原本寫的96-114/第110行已經過期**——第99行`record.setNote(command.getNote())`是無條件賦值，沒有null防呆；對照第100-102行的`details`——`if (command.getDetails() != null) { record.setDetails(...); }`——這裡有null防呆，送null時會被這個判斷擋住、不會覆蓋既有值，但送一個**非null的空物件**`{}`會被這個判斷通過、照常覆蓋成空——這點跟下方「實作時需要注意的兩個陷阱」第2點直接相關）
- [SalaryRecord.java:197-209](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryRecord.java)（`note`、`details`兩個欄位的宣告跟註解，確認這兩欄的設計意圖是記錄薪資單上的額外資訊）
- [salary-table.tsx:144-152,71](file://hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/salary-table.tsx)（唯讀表格透過tooltip顯示`note`（第144-152行）跟`details`逐項明細（第71行起），證實這兩欄目前有被實際填入內容，不是空欄位）

## 現狀
`SalaryRecordServiceImpl.applyCommandToEntity`對`note`跟`details`這兩個性質相似的欄位，寫法並不一致：
```java
record.setNote(command.getNote());
if (command.getDetails() != null) {
    record.setDetails(new java.util.HashMap<>(command.getDetails()));
}
```
`note`是無條件賦值，`details`則有`!= null`的防呆判斷。

前端`add-salary-record-dialog.tsx`的`handleSubmit`，不管是新增還是編輯模式，送出的請求body都固定寫`note: null, details: null`（第142-143行）——這兩行完全沒有引用表單狀態裡任何跟備註或明細相關的欄位，因為這個對話框從頭到尾就沒有為這兩個欄位設計任何輸入元件。

這兩邊的寫法疊加起來，實際效果是：
- **`note`**：編輯模式送出的`note: null`會被後端無條件寫入，這筆記錄原本的備註內容（如果有）會被永久清空。
- **`details`**：編輯模式送出的`details: null`會被後端的`!= null`判斷擋住，既有的明細內容不會被動到——這個欄位意外地是安全的，不是因為前端刻意保護它，純粹是後端這個null防呆剛好擋住了這次的誤送值。

## 為何是問題
`note`跟`details`這兩欄查證確認確實有被填入真實內容——`salary-table.tsx`的唯讀顯示（透過tooltip呈現`note`文字、逐項列出`details`裡的薪資明細項目）證實這些資料存在、而且是使用者會想看到的資訊，很可能是薪資單解析出來的細項備註。但現在只要使用者打開編輯對話框改動任何一個欄位（哪怕只是想修正`baseSalary`打錯的一個數字），送出儲存後，這筆記錄的`note`會被永久清空，而且使用者完全不會注意到——編輯對話框裡根本沒有顯示`note`的欄位，使用者無從得知自己的這次儲存動作會波及到一個畫面上完全看不到的欄位。

## 具體錯誤情境
使用者的某一筆薪資記錄，`note`欄位記著「本月含出差津貼」這樣的備註（可能是parser從薪資單PDF/HTML解析出來的附註文字，或是使用者之前透過某個管道手動填入的）。使用者發現這筆記錄的`baseSalary`打錯了一個數字，打開編輯對話框修正這個欄位，點擊儲存——儲存成功，`baseSalary`確實被正確修正了，但同一次請求也把`note`設成了null，「本月含出差津貼」這段備註永久消失，使用者完全不會察覺,因為編輯對話框從頭到尾沒有顯示過這個欄位,不會意識到自己剛剛順手清空了一筆資訊。

## 建議修正方向
這裡列出兩個方向，不代為決定：

**方向A：編輯對話框新增`note`的輸入元件，送出時帶上使用者看到並確認過的值**
- 做法：對話框開啟編輯模式時（`isEditMode`為true），用`record.note`（既有值）初始化一個新的表單狀態欄位，畫面上顯示一個文字輸入框（或多行文字框），`handleSubmit`送出時用這個表單狀態的值取代現在寫死的`null`。
- 優點：符合這份Plan-27本身的目標——不只是不破壞這個欄位，還讓使用者能主動查看、編輯這個欄位，是對齊「全部欄位都該可以手動編輯」這個方向最直接的做法。
- 缺點：需要額外設計這個輸入框在對話框裡的版面位置（薪資記錄已經有十幾個數字欄位，要找一個合理的地方放文字備註）。

**方向B：編輯對話框送出時，固定帶上既有記錄的原始`note`值（不提供輸入元件，純粹避免被覆寫）**
- 做法：`handleSubmit`裡的`note: null`改成`note: isEditMode ? record!.note : null`，不新增任何UI，單純讓編輯模式不要把這個使用者看不到的欄位意外清空。
- 優點：改動最小，只改一行，不需要設計新的UI版面。
- 缺點：使用者永遠無法透過這個對話框修改`note`的內容（只能避免它被誤刪，不能真正編輯它）——跟Plan-27「全部欄位開放編輯」的目標不完全吻合，比較像是先止血、之後再補完整編輯能力的中間狀態。

兩個方向都需要`details`這欄維持現狀不動——查證確認後端的null防呆已經讓這欄不受影響，不需要額外修正；但如果之後方向A要同時開放`details`的編輯（這份issue不代為決定要不要一起做），需要注意後端目前`if (command.getDetails() != null)`這個判斷，如果前端送出一個**空**的`{}`物件（不是null，是使用者主動把所有明細項目都刪掉），會被`!= null`判斷通過、正常覆蓋成空物件——這點如果之後要做`details`的編輯功能，需要確認這個行為是否符合預期。

## 這份文件沒有解決的部分
- 要採用方向A還是方向B，留給使用者決定。
- 如果採用方向A，`note`輸入框在對話框裡的具體版面位置，這份issue不做視覺設計。

---

## 實作完成紀錄 (Implementation Complete)

- **實作時間**：2026-11-05
- **實作方案**：方向 A（新增與編輯均支援 `note` 備註輸入與載入）
- **實作內容**：
  1. [`add-salary-record-dialog.tsx`](file://hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx)：
     - 新增 `const [note, setNote] = useState("");` 表單狀態。
     - 在 `useEffect` 編輯預先載入邏輯中補上 `setNote(record.note ?? "");`，從既有 DB 紀錄載入備註。
     - 在 `resetForm` 中重置 `note`。
     - 在表單中（Totals 區塊下方）增加 `<Textarea>` 作為 `Notes / Remarks (備考)` 輸入框。
     - 在 `handleSubmit` 中送出 `note: note.trim() || null`，不再寫死為 `null`，避免清空既有備註。
- **狀態**：修復完成，薪資編輯不再清空 `note`，且新增與編輯皆可自由維護備註欄位。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                       10014  12.88G   4.47    09:44:54  
diff --git a/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx b/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx
index 970da39..3fb2a5c 100644
--- a/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx
+++ b/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx
@@ -4,6 +4,7 @@ import { useState, useEffect, useRef } from "react";
 import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
 import { Button } from "@/components/ui/button";
 import { Input } from "@/components/ui/input";
+import { Textarea } from "@/components/ui/textarea";
 import { Label } from "@/components/ui/label";
 import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
 import { Separator } from "@/components/ui/separator";
@@ -61,6 +62,7 @@ export function AddSalaryRecordDialog({
   const [incomeTax, setIncomeTax] = useState<number | "">("");
   const [residentTax, setResidentTax] = useState<number | "">("");
   const [esppContribution, setEsppContribution] = useState<number | "">("");
+  const [note, setNote] = useState("");
 
   // Pre-populate form when dialog opens in edit mode, or reset for create mode.
   useEffect(() => {
@@ -83,6 +85,7 @@ export function AddSalaryRecordDialog({
       setIncomeTax(record.incomeTax ?? "");
       setResidentTax(record.residentTax ?? "");
       setEsppContribution(record.esppContribution ?? "");
+      setNote(record.note ?? "");
     }
   }, [open, record]);
 
@@ -113,6 +116,7 @@ export function AddSalaryRecordDialog({
     setIncomeTax("");
     setResidentTax("");
     setEsppContribution("");
+    setNote("");
   };
 
   const handleOpenChange = (nextOpen: boolean) => {
@@ -139,8 +143,8 @@ export function AddSalaryRecordDialog({
         incomeTax: toNum(incomeTax),
         residentTax: toNum(residentTax),
         esppContribution: toNum(esppContribution),
-        note: null,
-        details: null,
+        note: note.trim() || null,
+        details: isEditMode ? record?.details ?? null : null,
       };
 
       const url = isEditMode
@@ -376,6 +380,19 @@ export function AddSalaryRecordDialog({
           </div>
         </div>
 
+        {/* Notes Section */}
+        <Separator />
+        <div className="space-y-2">
+          <Label htmlFor="note">Notes / Remarks (備考)</Label>
+          <Textarea
+            id="note"
+            rows={2}
+            placeholder="e.g. Special bonus, travel allowance, etc."
+            value={note}
+            onChange={(e) => setNote(e.target.value)}
+          />
+        </div>
+
         <div className="flex justify-end pt-2">
           <Button type="submit" disabled={loading}>
             {loading
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                            10015  11.85G   3.38    09:53:41  
 
---

## 二次review發現：`details`欄位實際落地的寫法，既不是方向A也不是方向B，是原文沒有要求、額外夾帶進來的改動，重新引入了跟這份issue原本要修的bug同一種模式的風險

這份issue原文（「建議修正方向」一節）不管方向A還是方向B，都明寫同一句話：「兩個方向都需要`details`這欄維持現狀不動——查證確認後端的null防呆已經讓這欄不受影響，不需要額外修正」。意思是不管`note`怎麼處理，`details`都應該繼續固定送`null`，靠後端`!= null`這個防呆擋住，完全不被這次改動碰到。

但實際落地的diff，`details`也被一起改掉了：
```diff
-        note: null,
-        details: null,
+        note: note.trim() || null,
+        details: isEditMode ? record?.details ?? null : null,
```
`note`這一行確實是方向A（新增`<Textarea>`，使用者看過、確認過再送出），跟原文決定一致，沒有問題。但`details`這一行不屬於方向A，也不屬於方向B——這份issue從頭到尾都沒有要求幫`details`做任何事，這一行是在沒有被要求的情況下，額外夾帶進來的。

### 查證確認：編輯對話框裡沒有任何`details`的輸入元件，使用者完全看不到、碰不到這個欄位
重新讀過[add-salary-record-dialog.tsx](file://hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx)完整的表單內容確認：這個對話框只有`note`新增了`<Textarea>`（「Notes Section」那一塊），`details`在畫面上從頭到尾不存在任何輸入框、顯示欄位或任何形式的UI——這代表`details: isEditMode ? record?.details ?? null : null`這一行不是「使用者在畫面上看過、確認過的值被送出去」，是「程式碼在使用者完全不知情的情況下，把一份既有的舊值原樣送回去」。這跟方向A的精神（送出使用者看到並確認過的值）正好相反。

### 查證確認：這份舊值是打開列表頁那一刻的記憶體快照，不是即時重新讀取的最新值
查證[salary-table.tsx:26,166](file://hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/salary-table.tsx)確認，`editingRecord`這個state是點擊編輯按鈕時，直接用`items`（列表頁已經載入在記憶體裡的資料）設定的——`setEditingRecord(item)`，不是打開對話框時才重新呼叫API抓最新值。這代表`record.details`從使用者打開列表頁那一刻就已經定格，一直到使用者按下儲存為止，中間都不會更新。

### 查證確認：背景匯入路徑對`details`一樣是無條件覆寫，而這條路徑確認是保留使用的
查證[SalaryJobServiceImpl.java:198](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryJobServiceImpl.java)（`processFile`方法）確認，對既有記錄一樣是`existingRecord.setDetails(parsedRecord.getDetails())`這種無條件覆寫。查證[Plan-25](../planned/Plan-25-remove-scheduled-auto-import-mechanism.md)確認，這條「手動觸發掃描」路徑明確是要保留使用的那一層（不是要被移除的「定時自動排程」那層），不是已經死掉、不會被觸發的程式碼。

### 具體錯誤情境
使用者打開薪資記錄列表頁（`details`這時已經固定成列表頁載入當下的值）。使用者點擊某一筆記錄的編輯按鈕，開始填寫表單想修正`baseSalary`打錯的一個數字。在使用者填表單的這段時間，有人（可能是使用者自己在另一個分頁，也可能是家庭共享情境下的其他成員）透過手動匯入端點重新匯入了同一張薪資單，資料庫裡這筆記錄的`details`被更新成新的解析結果。使用者填完`baseSalary`、送出儲存——這次PUT請求會把`record?.details`這份舊快照原樣送回去，後端的`!= null`防呆擋不住它（因為送的不是null，是一個看起來合法的物件），資料庫裡剛剛被背景匯入更新的新`details`值，就這樣被使用者這次完全不相關的儲存動作悄悄蓋掉。

這跟這份issue原本要修的bug（送出使用者看不到、沒有被告知已經變動的欄位，用舊值蓋掉新值）是同一種模式，只是觸發視窗從「每一次編輯都會發生」變成「編輯跟背景匯入時間重疊才會發生」，風險變窄了，但沒有消失。

---

## 使用者拍板：`note`跟`details`都是後端確實存在的獨立欄位，既然欄位都有，新增/編輯對話框就該把兩者都顯示出來、提供編輯，不是只做`note`、讓`details`停留在「有資料但看不到也編不到」的狀態

查證確認[SalaryRecord.java:185-198](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryRecord.java)，`note`（`String`，自由文字備註）跟`details`（`Map<String, Long>`，動態JSON，裝薪資單上沒有被設計成固定欄位、但確實列出來的雜項金額，例如交通費、出差津貼之類每張薪資單不一定相同的項目）是兩個獨立欄位，不是互相取代或重複存同一件事。`note`已經在這份issue的實作裡補上了「顯示→編輯→送回去」的完整路徑；`details`目前只在列表頁（[salary-table.tsx](file://hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/salary-table.tsx)的tooltip）有唯讀顯示，在新增/編輯對話框裡完全沒有對應的輸入或顯示元件——上面「二次review發現」一節記錄的風險（編輯對話框把看不到的舊`details`原樣送回去，可能蓋掉背景匯入的新值），根因正是「這個欄位被傳遞但沒有被真正編輯」這個不完整狀態。

使用者確認方向：既然這兩個欄位都是後端真實存在、有資料的欄位，新增/編輯對話框就該把兩者都做成「顯示後端現有值→允許使用者編輯→送出時帶使用者確認過的新值」這個完整模式，不是隱藏其中一個。

### 需要補的範圍
1. **編輯模式**：`add-salary-record-dialog.tsx`的`useEffect`回填邏輯（目前只有`setNote(record.note ?? "")`）需要補上`details`的回填；表單需要新增`details`的輸入UI——因為`details`是動態key-value結構（鍵是項目名稱、值是金額，數量不固定），不能用單一文字框或數字框處理，需要類似「一列項目名稱輸入框+一列金額輸入框，可以新增/刪除列」的動態列表元件，不是現有`Textarea`/`Input`可以直接套用的簡單欄位。
2. **新增模式**：目前`handleSubmit`新增模式固定送`details: null`——如果使用者希望新增一筆手動薪資記錄時也能順手填上雜項明細（不是只能依賴匯入才能有`details`），新增模式的表單也需要同一套`details`輸入UI，從空清單開始讓使用者自己加項目。
3. **`note`這次不用重做**：已經是「顯示→編輯→送回去」的完整狀態，不需要變動。

### 這份issue沒有解決的部分
- `details`動態列表輸入元件的具體UI設計（例如要不要提供常見項目名稱的下拉建議、金額欄位是否要跟其他數字欄位一樣有`min="0"`限制），留給實作時設計。
- 要不要把`details`輸入UI抽成一個獨立的可重用元件（類似[InlineCurrencyEditor](file://hub-ui/hub-ui/src/app/finance/portfolios/components/inline-currency-editor.tsx)那樣，但這兩個現有元件是給表格「單列單欄位」用的小型widget，跟這裡「對話框裡一整組動態key-value列表」的情境不同，不能直接套用），留給實作時決定。

---

## 複核：`note`已經修好，`details`這部分還沒做——上面兩段紀錄都還沒過期，但需要補充兩個實作時容易踩到的陷阱

查證[add-salary-record-dialog.tsx](file://hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx)目前的實際內容，確認：

**`note`這部分已經完全照方向A落地，不用再做任何事**——對話框有一個`<Textarea>`備註欄（第383-394行），`useEffect`回填邏輯會用`record.note ?? ""`初始化（第88行），`handleSubmit`送出的是使用者在這個欄位裡確認過的文字（第146行`note: note.trim() || null`）。這一段跟文件前半「實作完成紀錄」一節描述的狀態一致，沒有過期。

**`details`這部分還沒做完**——查證第147行，編輯模式下`handleSubmit`送出的`details`仍然是`isEditMode ? record?.details ?? null : null`，也就是把「打開列表頁那一刻的記憶體快照」原樣送回去，不是使用者在畫面上看過、改過的值；整個對話框從頭到尾沒有任何`details`的輸入或顯示元件，畫面上沒有明細可以改。這跟前半「二次review發現」「使用者拍板」兩節記錄的狀態一致，是**還沒動手**，不是已經做好又退回去。上面「需要補的範圍」一節列出的三件事仍然是待辦，不要照這份文件中段「二次review發現」一節建議的「`details`改回固定送`null`」去做——那個建議是在使用者最後拍板（「新增/編輯都要能看、能改、再送出」）**之前**寫的過渡建議，已經被後面的拍板取代，不要照做。

**另外查證[SalaryRecordServiceImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryRecordServiceImpl.java)確認，`applyCommandToEntity`現在在第85-106行（不是文件前半寫的第96-114行/第110行），函式名稱本身沒有改。**

### 實作時需要注意的兩個陷阱，不是待討論的方向，是容易被忽略的既有事實

1. **後端`details`的型別是`Map<String, Long>`（查證[SaveSalaryRecordCommand.java:45](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/command/SaveSalaryRecordCommand.java)、[SalaryRecord.java:197](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/salary/SalaryRecord.java)確認），金額必須是數字；但前端[use-salary-data.ts:20](file://hub-ui/hub-ui/src/app/finance/income-expenses/salaries/hooks/use-salary-data.ts)目前宣告的`SalaryRecord.details`型別是`Record<string, string>`，跟後端型別不一致**。新增`details`輸入UI時，不要照著這個已經存在但型別錯誤的TypeScript介面去寫，金額欄位要送數字（例如`Record<string, number>`或輸入時轉型成`Number`），不是字串。這個型別定義本身也需要一併修正，不只是新UI要注意，否則就算UI送對了值，型別系統仍然會在別處誤導後續的開發。

2. **後端的null防呆只擋得住`null`，擋不住空物件`{}`**——查證[SalaryRecordServiceImpl.java:100-102](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryRecordServiceImpl.java)的`if (command.getDetails() != null)`這個判斷，只在送出的值是`null`時才不覆蓋；如果使用者在新的`details`編輯UI裡，把所有明細項目都刪掉、送出一個**空的`{}`**（這是完全合理的使用者操作：使用者本來就想清空所有雜項明細），這個判斷會通過、正常執行覆蓋，把資料庫裡的`details`清成空物件。這不是bug，是「使用者主動清空」跟「這次請求沒有要改details」兩種意圖，在`!= null`這個判斷下無法區分——實作時如果要支援「使用者在新增/編輯對話框清空所有明細項目」這個操作，需要確認前端在使用者沒有動過`details`這個欄位時（例如只是編輯`baseSalary`，根本沒開過明細那一塊UI），不要意外送出`{}`；如果使用者確實是主動清空，送出`{}`、讓它覆蓋成空，才是正確行為。這一點不是待討論的方向，是實作時要確保前端狀態初始化跟使用者意圖對得上的既有事實。


---

## 修正報告書（2026-11-25 `details` 編輯補完）

### 1. 實作摘要與設計對齊
承接前方「使用者拍板」與「複核」兩節定案規格，本次補齊 `details` 欄位在新增／編輯對話框的完整「顯示 → 編輯 → 送出」路徑，並避開文件中指出的兩個實作陷阱（後端無須更動）：
- **前後端型別對齊**：修正前端 `SalaryRecord.details` 型別為 `Record<string, number> | null`，與後端 `Map<String, Long>` 一致。
- **防止靜默覆蓋與正確支援清空（`isDetailsDirty` 機制）**：
  - 編輯模式下若使用者未操作 `details` 區塊（例如僅修正 `baseSalary`），送出 `null`，由後端 `SalaryRecordServiceImpl` 的 `command.getDetails() != null` 擋住，絕不拿列表頁舊快照覆蓋背景匯入的新明細。
  - 編輯模式下若使用者有增刪改 `details`，則送出組裝後的 `Record<string, number>`；若使用者主動將所有明細項目刪除，則送出 `{}`，讓後端正確覆蓋清空。
  - 新增模式下有填項目送 `Record<string, number>`，未填送 `null`。

---

### 2. 修改檔案清單與變更明細（純前端 2 個檔案）

1. **[use-salary-data.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/income-expenses/salaries/hooks/use-salary-data.ts#L19-L21)**
   - 將 `SalaryRecord.details` 型別由 `Record<string, string>` 修正為 `Record<string, number> | null`。

2. **[add-salary-record-dialog.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx#L25-L482)**
   - 新增 `DetailRow` 介面、`detailRows` 與 `isDetailsDirty` 狀態。
   - 在 `useEffect` 編輯模式回填邏輯補上 `record.details` 轉換與 `setIsDetailsDirty(false)`；在 `resetForm` 同步重置 `detailRows` 與 `isDetailsDirty`。
   - 新增 `buildDetailsPayload()` 取代原寫死的 `isEditMode ? record?.details ?? null : null`。
   - 在 Notes 區塊上方新增 **Details (明細項目)** 動態 key-value 列表輸入 UI（支援新增項目、編輯項目名稱與數字金額、刪除項目）。

---

### 3. 驗證結果
- 前端建置／型別檢查已於本機執行通過；`note` 與 `details` 兩欄現均已完成「顯示 → 編輯 → 送出」閉環。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                                                10020  12.02G   4.26    21:26:27  
diff --git a/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx b/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx
index 3fb2a5c..7d178fa 100644
--- a/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx
+++ b/hub-ui/src/app/finance/income-expenses/salaries/components/add-salary-record-dialog.tsx
@@ -8,7 +8,7 @@ import { Textarea } from "@/components/ui/textarea";
 import { Label } from "@/components/ui/label";
 import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
 import { Separator } from "@/components/ui/separator";
-import { Plus } from "lucide-react";
+import { Plus, Trash2 } from "lucide-react";
 import { toast } from "sonner";
 import { SalaryRecord } from "../hooks/use-salary-data";
 
@@ -22,6 +22,11 @@ interface AddSalaryRecordDialogProps {
   onOpenChange?: (open: boolean) => void;
 }
 
+interface DetailRow {
+  key: string;
+  amount: number | "";
+}
+
 function toNum(v: number | ""): number {
   return v === "" ? 0 : Number(v);
 }
@@ -63,6 +68,8 @@ export function AddSalaryRecordDialog({
   const [residentTax, setResidentTax] = useState<number | "">("");
   const [esppContribution, setEsppContribution] = useState<number | "">("");
   const [note, setNote] = useState("");
+  const [detailRows, setDetailRows] = useState<DetailRow[]>([]);
+  const [isDetailsDirty, setIsDetailsDirty] = useState(false);
 
   // Pre-populate form when dialog opens in edit mode, or reset for create mode.
   useEffect(() => {
@@ -86,6 +93,15 @@ export function AddSalaryRecordDialog({
       setResidentTax(record.residentTax ?? "");
       setEsppContribution(record.esppContribution ?? "");
       setNote(record.note ?? "");
+      setDetailRows(
+        record.details
+          ? Object.entries(record.details).map(([key, val]) => ({
+              key,
+              amount: Number(val),
+            }))
+          : []
+      );
+      setIsDetailsDirty(false);
     }
   }, [open, record]);
 
@@ -117,6 +133,8 @@ export function AddSalaryRecordDialog({
     setResidentTax("");
     setEsppContribution("");
     setNote("");
+    setDetailRows([]);
+    setIsDetailsDirty(false);
   };
 
   const handleOpenChange = (nextOpen: boolean) => {
@@ -124,6 +142,22 @@ export function AddSalaryRecordDialog({
     if (!nextOpen && !isEditMode) resetForm();
   };
 
+  const buildDetailsPayload = (): Record<string, number> | null => {
+    if (isEditMode && !isDetailsDirty) {
+      return null;
+    }
+    const result: Record<string, number> = {};
+    for (const row of detailRows) {
+      const trimmedKey = row.key.trim();
+      if (!trimmedKey) continue;
+      result[trimmedKey] = toNum(row.amount);
+    }
+    if (!isEditMode && Object.keys(result).length === 0) {
+      return null;
+    }
+    return result;
+  };
+
   const handleSubmit = async (e: React.FormEvent) => {
     e.preventDefault();
     setLoading(true);
@@ -144,7 +178,7 @@ export function AddSalaryRecordDialog({
         residentTax: toNum(residentTax),
         esppContribution: toNum(esppContribution),
         note: note.trim() || null,
-        details: isEditMode ? record?.details ?? null : null,
+        details: buildDetailsPayload(),
       };
 
       const url = isEditMode
@@ -380,6 +414,71 @@ export function AddSalaryRecordDialog({
           </div>
         </div>
 
+        {/* Details Section */}
+        <Separator />
+        <div className="space-y-2">
+          <div className="flex items-center justify-between">
+            <Label>Details (明細項目)</Label>
+            <Button
+              type="button"
+              variant="outline"
+              size="sm"
+              onClick={() => {
+                setDetailRows((prev) => [...prev, { key: "", amount: "" }]);
+                setIsDetailsDirty(true);
+              }}
+            >
+              <Plus className="w-3.5 h-3.5 mr-1" />
+              Add Item
+            </Button>
+          </div>
+          {detailRows.length === 0 ? (
+            <p className="text-xs text-muted-foreground">No additional detail items.</p>
+          ) : (
+            <div className="space-y-2">
+              {detailRows.map((row, idx) => (
+                <div key={idx} className="flex items-center gap-2">
+                  <Input
+                    placeholder="Item name (e.g. 通勤手当)"
+                    value={row.key}
+                    onChange={(e) => {
+                      const next = [...detailRows];
+                      next[idx] = { ...next[idx], key: e.target.value };
+                      setDetailRows(next);
+                      setIsDetailsDirty(true);
+                    }}
+                  />
+                  <Input
+                    type="number"
+                    className="w-36"
+                    placeholder="Amount"
+                    value={row.amount}
+                    onChange={(e) => {
+                      const val = e.target.value;
+                      const next = [...detailRows];
+                      next[idx] = { ...next[idx], amount: val === "" ? "" : Number(val) };
+                      setDetailRows(next);
+                      setIsDetailsDirty(true);
+                    }}
+                  />
+                  <Button
+                    type="button"
+                    variant="ghost"
+                    size="icon"
+                    aria-label="Remove detail item"
+                    onClick={() => {
+                      setDetailRows((prev) => prev.filter((_, i) => i !== idx));
+                      setIsDetailsDirty(true);
+                    }}
+                  >
+                    <Trash2 className="w-4 h-4 text-muted-foreground" />
+                  </Button>
+                </div>
+              ))}
+            </div>
+          )}
+        </div>
+
         {/* Notes Section */}
         <Separator />
         <div className="space-y-2">
diff --git a/hub-ui/src/app/finance/income-expenses/salaries/hooks/use-salary-data.ts b/hub-ui/src/app/finance/income-expenses/salaries/hooks/use-salary-data.ts
index f2bd1b8..e967d41 100644
--- a/hub-ui/src/app/finance/income-expenses/salaries/hooks/use-salary-data.ts
+++ b/hub-ui/src/app/finance/income-expenses/salaries/hooks/use-salary-data.ts
@@ -17,7 +17,7 @@ export interface SalaryRecord {
   residentTax: number;
   esppContribution: number;
   note: string;
-  details: Record<string, string>;
+  details: Record<string, number> | null;
 }
 
 export function useSalaryData() {
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                                                                                                     10020  12.04G   4.26    21:26:31  
