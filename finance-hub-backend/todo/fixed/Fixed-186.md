# [FIXED] 186：使用者檔案頁的家庭成員類型下拉選單提供`SPOUSE`/`CHILD`/`PARENT`/`OTHER`四個選項，但後端`DependentType`這個enum實際上只定義了`SPOUSE`/`DEPENDENT`兩個值——選擇另外三個選項儲存時，Jackson反序列化會直接失敗，回傳400錯誤

## 這份issue怎麼來的
這是Plan-27（全領域開放手動編輯）查證過程中發現的。這不是「缺少編輯介面」的缺口，是現有編輯介面本身就壞掉——四個選項裡只有一個真正能用。

## 相關檔案
- [DependentType.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/DependentType.java)（查證確認enum完整內容：`SPOUSE`、`DEPENDENT`，僅兩個值）
- [profile-family-section.tsx:123-126](file://hub-ui/hub-ui/src/app/finance/components/profile-family-section.tsx)（下拉選單的四個`<SelectItem>`：`value="SPOUSE"`、`value="CHILD"`、`value="PARENT"`、`value="OTHER"`，查證確認逐字相符）
- [profile-family-section.tsx:24](file://hub-ui/hub-ui/src/app/finance/components/profile-family-section.tsx)（新增成員時的初始值固定寫`memberType: "SPOUSE"`）
- [UserProfileServiceImpl.java:76-84](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java)（`saveUserProfile`裡`fm.setMemberType(cmd.memberType())`，這個值最終會被存進`Dependent.memberType`這個`@Enumerated(EnumType.STRING)`欄位）

## 現狀
`DependentType.java`目前完整內容：
```java
public enum DependentType {
    SPOUSE,
    DEPENDENT
}
```
`profile-family-section.tsx`裡，家庭成員類型的`<Select>`元件提供四個選項：
```tsx
<SelectItem value="SPOUSE">Spouse (配偶者)</SelectItem>
<SelectItem value="CHILD">Child (子)</SelectItem>
<SelectItem value="PARENT">Parent (父母)</SelectItem>
<SelectItem value="OTHER">Other (其他扶養親屬)</SelectItem>
```
這個下拉選單的值最終會透過`UserProfileRequest.familyMembers`→`SaveUserProfileCommand.familyMembers`→`UserProfileServiceImpl.saveUserProfile`裡的`fm.setMemberType(cmd.memberType())`寫入`Dependent`這個domain實體。`Dependent.memberType`欄位是`@Enumerated(EnumType.STRING)`對應到`DependentType`這個enum——Jackson在把前端送來的JSON字串反序列化成這個enum時，只認得`"SPOUSE"`、`"DEPENDENT"`這兩個字串，收到`"CHILD"`、`"PARENT"`、`"OTHER"`任何一個都會直接拋出反序列化例外。

## 為何是問題
這不是「選項不夠多」這種功能缺口，是「現有四個選項裡，四分之三會讓使用者的操作失敗」。使用者在家庭成員表單裡，如果這個人是子女、父母，或任何其他扶養親屬關係，介面上明明有對應的選項可以選（`Child`/`Parent`/`Other`，而且附帶中文翻譯讓選項看起來很完整、很值得信賴），選了之後點擊儲存卻會得到一個後端400錯誤。使用者只有選擇`Spouse`這一個選項時，儲存才會成功。

## 具體錯誤情境
使用者想在自己的財務檔案裡新增一位需要扶養的父母，打開家庭成員表單，看到下拉選單裡有「Parent (父母)」這個選項，選了它、填好生日跟預估年收入，點擊儲存——前端送出的請求body裡`memberType: "PARENT"`，後端收到後嘗試把這個字串反序列化成`DependentType`enum時失敗，整個儲存請求以400錯誤收場，使用者完全看不出為什麼儲存失敗，因為表單上的選項明明就在那裡、看起來合法。

## 查證`memberType`在真實稅務計算邏輯裡的用途，確認子女/父母/其他扶養親屬的細分不需要靠這個欄位
查證[AbstractDependentDeductionStrategy.java:54-146](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java)確認，`calculateIncomeTaxDeduction`/`calculateInhabitantTaxDeduction`這兩個真正算扣除額的方法，對`dependent.getMemberType()`的唯一判斷只有一處（第59、71行）：
```java
if (dependent.getMemberType() == DependentType.SPOUSE) {
    return 0L;
}
```
是`SPOUSE`就直接回傳0（配偶的扣除額走另一套獨立計算邏輯，不計入扶養親屬扣除這一類）。一旦不是`SPOUSE`，剩下的所有分級——19-22歲的特定扶養親屬（`SPECIFIC_DEPENDENT_*`）、16歲以下不計入（`age < 16`回傳0）、70歲以上的老年扶養親屬（`ELDERLY_DEPENDENT_*`/`ELDERLY_COHABITING_ASCENDANT_*`，依是否同居再分兩檔）、其餘年齡的一般扶養親屬（`GENERAL_DEPENDENT_*`）——全部都是用`dependent.getBirthDate()`算出來的年齡去判斷（第87-110、122-145行），完全沒有用到`memberType`去區分「這是子女還是父母還是其他」。

這代表系統現有的真實稅務邏輯，從頭到尾只需要知道「這個人是不是配偶」這一個二元區分，子女/父母/其他扶養親屬之間的稅務差異（特定扶養親屬、一般扶養親屬、老年扶養親屬、同居老親）全部是靠年齡自動判斷出來的，跟`memberType`裡「這是CHILD還是PARENT還是OTHER」完全無關。

## 建議修正方向
基於上面的查證結果，方向明確，不是多方案並列：

**前端下拉選單收斂成只剩後端支援的兩個選項（`Spouse`/`Dependent`），`CHILD`/`PARENT`/`OTHER`這三個選項拿掉**
- 做法：`profile-family-section.tsx`的`<SelectItem>`只保留`SPOUSE`、`DEPENDENT`兩個，`DEPENDENT`顯示成類似「扶養親屬（子女/父母/其他）」這種合併語意的中文標籤，讓使用者清楚知道選這個選項時系統會依出生日期自動判斷適用哪一檔扣除額，不需要自己先分類。
- 不需要擴充`DependentType`這個enum——查證已確認現有的真實計算邏輯不依賴這個細分，擴充成四元分類只會多出三個完全沒有任何程式碼會去讀取、純粹裝飾性的選項，徒增enum的維護負擔跟誤導風險（之後有人看到`CHILD`/`PARENT`/`OTHER`存在，可能誤以為系統真的有依這個分類做不同計算）。

## 這份文件沒有解決的部分
- 無（上面「查證」一節已經確認了方向，不是待確認項目）。

---

## 實作完成紀錄 (Implementation Complete)

- **實作時間**：2026-11-05
- **實作內容**：
  1. [`profile-family-section.tsx`](file://hub-ui/hub-ui/src/app/finance/components/profile-family-section.tsx)：
     - 將下拉選單選項收斂為後端支援的 `SPOUSE`（Spouse (配偶者)）與 `DEPENDENT`（Dependent (扶養親屬：子女/父母/其他)），移除會導致後端 400 錯誤的 `CHILD`、`PARENT`、`OTHER`。
     - 增加 `getMemberTypeLabel` 輔助函式，使卡片清單中也能清晰對應並顯示人類可讀的標籤名稱。
- **狀態**：修復完成，前端表單選項已與後端 `DependentType` 完全對齊，不再發生反序列化 400 錯誤。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                       10012  11.88G   4.15    09:32:38  
diff --git a/hub-ui/src/app/finance/components/profile-family-section.tsx b/hub-ui/src/app/finance/components/profile-family-section.tsx
index 3216cfc..04adc08 100644
--- a/hub-ui/src/app/finance/components/profile-family-section.tsx
+++ b/hub-ui/src/app/finance/components/profile-family-section.tsx
@@ -16,6 +16,17 @@ interface Props {
   onChange: (data: UserProfileFormData) => void;
 }
 
+const getMemberTypeLabel = (type: string) => {
+  switch (type) {
+    case "SPOUSE":
+      return "Spouse (配偶者)";
+    case "DEPENDENT":
+      return "Dependent (扶養親屬：子女/父母/其他)";
+    default:
+      return type;
+  }
+};
+
 export function ProfileFamilySection({ formData, onChange }: Props) {
   const [isFamilyDialogOpen, setIsFamilyDialogOpen] = useState(false);
   const [editingMember, setEditingMember] = useState<FamilyMember | null>(null);
@@ -84,7 +95,7 @@ export function ProfileFamilySection({ formData, onChange }: Props) {
               {formData.familyMembers.map((fm, idx) => (
                 <div key={idx} className="flex items-center justify-between p-3 border rounded-md">
                   <div>
-                    <div className="font-medium">{fm.memberType}</div>
+                    <div className="font-medium">{getMemberTypeLabel(fm.memberType)}</div>
                     <div className="text-sm text-muted-foreground">
                       Born: {fm.birthDate} | Income: ¥{fm.estimatedAnnualIncome?.toLocaleString() ?? 0}
                       {fm.isCohabiting && <span className="ml-2 text-xs font-semibold text-primary bg-primary/10 px-1.5 py-0.5 rounded">Cohabiting (同居)</span>}
@@ -121,9 +132,7 @@ export function ProfileFamilySection({ formData, onChange }: Props) {
                 </SelectTrigger>
                 <SelectContent>
                   <SelectItem value="SPOUSE">Spouse (配偶者)</SelectItem>
-                  <SelectItem value="CHILD">Child (子)</SelectItem>
-                  <SelectItem value="PARENT">Parent (父母)</SelectItem>
-                  <SelectItem value="OTHER">Other (其他扶養親屬)</SelectItem>
+                  <SelectItem value="DEPENDENT">Dependent (扶養親屬：子女/父母/其他)</SelectItem>
                 </SelectContent>
               </Select>
             </div>
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                            10013  11.90G   4.17    09:32:44  
 