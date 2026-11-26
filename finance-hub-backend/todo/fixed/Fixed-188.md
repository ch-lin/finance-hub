# [FIXED] 188：使用者檔案的`displayName`（顯示名稱）整條後端鏈路完全沒有任何寫入路徑——Request/Command/Service/Mapper/Response/Result六個檔案都沒有這個欄位——但家庭頁面會讀取這個欄位來顯示每個成員的名字，沒有值時全部退回「User-」加使用者ID前4碼這種格式

## 這份issue怎麼來的
這是Plan-27（全領域開放手動編輯）查證過程中發現的。這不是「這個欄位還沒開放編輯」這種單純的功能缺口，是「這個欄位從一開始設計時就被其他功能依賴、但整條寫入鏈路從頭到尾沒有人接上」。

## 相關檔案
- [UserProfile.java:109-114](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java)（`displayName`欄位宣告，註解寫著「使用者在finance hub UI上選的顯示名稱（例如用於家庭共享）」——查證確認這段註解本身就說明了這個欄位的設計意圖是給使用者自己設定；class層級有`@Getter @Setter`，所以`setDisplayName`這個方法本身是存在的，不是entity缺setter）
- [UserProfileRequest.java:36-47](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java)（查證確認這個record完整欄位清單裡沒有`displayName`）
- [SaveUserProfileCommand.java:32-42](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveUserProfileCommand.java)（查證確認同樣沒有`displayName`）
- [UserProfileServiceImpl.java:62-106](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java)（`saveUserProfile`完整方法，查證確認裡面對`birthDate`/`defaultCurrency`/`manualMonthlyBaseExpense`/`targetRetirementAge`/`pensionStartAge`/`familyMembers`/`insurancePolicies`/`lifeExpectancy`/`taxAdvantagedWithdrawalAge`/`applyProfileToOptimizerBaseline`這十個欄位都有對應的賦值，完全沒有任何一行提到`displayName`）
- `UserProfileMapper.java`、`UserProfileResponse.java`、`UserProfileResult.java`（查證確認這三個檔案裡也都沒有`displayName`，整條Request→Command→Service→Mapper→Response→Result鏈路，六個檔案一致地沒有這個欄位）
- [FamilyServiceImpl.java:177-180](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java)（讀取`profile.getDisplayName()`來決定家庭頁面上每個成員顯示的名字，沒有值時退回`"User-" + userId前4碼`這種格式）
- `user-profile-form.tsx`、`profile-basic-info-section.tsx`（查證確認`UserProfileFormData`這個TypeScript介面、跟畫面上實際渲染的輸入框，都沒有`displayName`）

## 現狀
`UserProfile.displayName`這個欄位，從資料庫entity到對外暴露的每一層都確認存在，但這條鏈路完全沒有任何環節真正讓這個值被寫入：

- entity本身有這個欄位、有setter（Lombok`@Setter`自動生成），但這不是問題——問題是沒有任何呼叫者會去呼叫`setDisplayName(...)`。
- `UserProfileRequest`（Controller接收前端送來的JSON）沒有這個欄位——代表前端即使想送，後端的請求格式也接不住。
- `SaveUserProfileCommand`（Controller轉給Service層的內部指令物件）也沒有——代表即使`UserProfileRequest`補上了這個欄位，中間這一層還是會把它丟掉。
- `UserProfileServiceImpl.saveUserProfile`這個方法，對其餘十個欄位都有明確的`profile.setXxx(command.getXxx())`這類賦值，但完全沒有`displayName`這一行——代表即使前兩層都補上了，Service層還是不會真的把值寫進entity。
- 回應方向（`UserProfileMapper`/`UserProfileResponse`/`UserProfileResult`）也一致地沒有這個欄位——代表即使資料庫裡某一筆記錄意外地有這個值（例如之後有人直接改資料庫），前端也看不到它。

同時，`FamilyServiceImpl.java:177-180`確認這個欄位被另一個功能依賴：
```java
String displayName = profileOpt
        .map(p -> p.getDisplayName())
        ...
```
查證完整上下文確認，沒有值時會退回`"User-" + userId前4碼`這種格式，用來在家庭頁面的成員列表上顯示每個人的名字。

前端`UserProfileFormData`介面跟`profile-basic-info-section.tsx`（渲染使用者檔案頁其餘所有欄位輸入框的檔案）都確認沒有`displayName`，代表前端也從來沒有嘗試過要送這個欄位。

## 為何是問題
這個欄位的設計意圖（從entity上的註解可以看出）本來就是給使用者自己選一個顯示名稱，供家庭共享情境下其他成員看到。但因為整條寫入鏈路從頭到尾沒有任何一層接上，**現在系統裡沒有任何人、透過任何方式，能夠把自己的顯示名稱設定成一個真正的名字**——所有使用者在家庭頁面上，永遠只會看到那個退回格式（例如「User-a1b2」這種不具名、看起來像除錯訊息的字串）。這不是「這個功能還沒做」，是「這個功期望被某個地方依賴、使用者體驗上明顯需要，但從一開始就沒人把管道接通」。

## 具體錯誤情境
使用者邀請配偶加入自己的家庭群組，配偶接受邀請後，使用者打開家庭頁面想看看誰已經加入了——畫面上配偶的名字顯示成「User-7f3c」這種格式，不是配偶的真實姓名或任何使用者期待看到的可識別名稱。使用者去自己的檔案設定頁面找，想幫自己或配偶設定一個正常的顯示名稱，卻發現整個檔案設定表單裡完全沒有這個欄位可以填——這個功能在UI上完全不存在，不是「藏在某個進階設定裡」，是從頭到尾沒有被建出來。

## 建議修正方向
這是一個端到端補齊既有欄位寫入路徑的工作，不是多方案選一：

1. `UserProfileRequest.java`新增`String displayName`欄位。
2. `SaveUserProfileCommand.java`新增對應欄位。
3. `UserProfileServiceImpl.saveUserProfile`裡補上`profile.setDisplayName(command.displayName())`這一行，跟其餘欄位的寫法一致。
4. `UserProfileMapper`/`UserProfileResponse`/`UserProfileResult`三個檔案補上這個欄位的映射，確保讀取方向也能正確回傳。
5. 前端`UserProfileFormData`介面補上`displayName`，`profile-basic-info-section.tsx`新增一個文字輸入框，跟現有的`birthDate`/`targetRetirementAge`等欄位放在同一個表單區塊裡。

這五步全部完成後，`FamilyServiceImpl`現有的fallback邏輯（沒有值時退回`"User-"`格式）不需要改動，因為使用者一旦設定了`displayName`，這個欄位就不再是null，fallback自然不會被觸發——這代表這份issue不需要碰`FamilyServiceImpl`的程式碼，純粹補齊上游的寫入鏈路即可。

## 這份文件沒有解決的部分
- 無——這是補齊既有設計意圖、已經有明確欄位定義的鏈路缺口，不是需要產品面額外決策的問題。

---

## 實作完成紀錄 (Implementation Complete)

- **實作時間**：2026-11-05
- **實作內容**：
  1. [`UserProfileRequest.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java)：
     - 新增 `String displayName` 欄位。
  2. [`SaveUserProfileCommand.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveUserProfileCommand.java)：
     - 新增 `String displayName` 欄位。
  3. [`UserProfileServiceImpl.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java)：
     - `saveUserProfile` 方法中補上 `profile.setDisplayName(command.displayName());`。
  4. [`UserProfileResult.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/result/UserProfileResult.java) 與 [`UserProfileResponse.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java)：
     - `UserProfileResult` 新增 `displayName` 屬性，並於 `fromEntity()` 工廠方法中從 `entity.getDisplayName()` 設值。
     - `UserProfileResponse` 新增 `String displayName` 欄位。
     - MapStruct Mapper [`UserProfileMapper.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileMapper.java) 自動完成雙向對應。
  5. 前端整合：
     - [`user-profile-form.tsx`](file://hub-ui/hub-ui/src/app/finance/components/user-profile-form.tsx)：`UserProfileFormData` 介面新增 `displayName: string;`。
     - [`profile-basic-info-section.tsx`](file://hub-ui/hub-ui/src/app/finance/components/profile-basic-info-section.tsx)：在 Basic Information 區塊頂部新增 `Display Name` 輸入框。
     - [`profile/me/page.tsx`](file://hub-ui/hub-ui/src/app/finance/profile/me/page.tsx) 與 [`onboarding/page.tsx`](file://hub-ui/hub-ui/src/app/finance/onboarding/page.tsx)：
       - `formData` 預設值加入 `displayName: ""`。
       - `useEffect` 載入個人 profile / 快取時將 `displayName` 回填至表單。
       - 儲存送出 payload 時帶上 `displayName: formData.displayName.trim() || null`。
  6. 單元測試：
     - [`UserProfileServiceImplTest.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImplTest.java)：新增儲存與查詢 `displayName` 的單元測試。
     - [`FamilyServiceImplTest.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java)：新增 `getFamilyMembers_resolvesDisplayNameAndFallback` 測試，驗證當使用者有設定 `displayName` 時家庭成員列表顯示設定名稱，無值時正常 fallback 至 `User-xxxx`。
- **狀態**：修復完成，前後端端到端打通，使用者可正常設定顯示名稱且家庭頁面能正確顯示該名稱。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                       10016  12.91G   3.67    11:26:46  
diff --git a/hub-ui/src/app/finance/components/profile-basic-info-section.tsx b/hub-ui/src/app/finance/components/profile-basic-info-section.tsx
index a2e94cc..043882f 100644
--- a/hub-ui/src/app/finance/components/profile-basic-info-section.tsx
+++ b/hub-ui/src/app/finance/components/profile-basic-info-section.tsx
@@ -30,6 +30,18 @@ export function ProfileBasicInfoSection({ formData, onChange }: Props) {
       </CardHeader>
       <CardContent className="space-y-4">
         <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
+          <div className="space-y-2">
+            <Label htmlFor="displayName">Display Name</Label>
+            <Input
+              id="displayName"
+              name="displayName"
+              type="text"
+              placeholder="e.g. Alex (shown in family sharing)"
+              value={formData.displayName}
+              onChange={handleInputChange}
+            />
+          </div>
+
           <div className="space-y-2">
             <Label htmlFor="birthDate">Date of Birth</Label>
             <Input
diff --git a/hub-ui/src/app/finance/components/user-profile-form.tsx b/hub-ui/src/app/finance/components/user-profile-form.tsx
index 44d5c38..fa3e175 100644
--- a/hub-ui/src/app/finance/components/user-profile-form.tsx
+++ b/hub-ui/src/app/finance/components/user-profile-form.tsx
@@ -16,6 +16,7 @@ export interface FamilyMember {
 export type CurrencyCode = "USD" | "JPY" | "TWD" | "EUR" | "UNKNOWN";
 
 export interface UserProfileFormData {
+  displayName: string;
   birthDate: string;
   defaultCurrency: CurrencyCode;
   manualMonthlyBaseExpense: string;
diff --git a/hub-ui/src/app/finance/onboarding/page.tsx b/hub-ui/src/app/finance/onboarding/page.tsx
index bee537b..8fe38ce 100644
--- a/hub-ui/src/app/finance/onboarding/page.tsx
+++ b/hub-ui/src/app/finance/onboarding/page.tsx
@@ -18,6 +18,7 @@ export default function OnboardingPage() {
   const [isSaving, setIsSaving] = useState(false);
 
   const [formData, setFormData] = useState<UserProfileFormData>({
+    displayName: "",
     birthDate: "",
     defaultCurrency: "JPY",
     manualMonthlyBaseExpense: "",
@@ -57,6 +58,7 @@ export default function OnboardingPage() {
     if (profile) {
       initialData = {
         ...initialData,
+        displayName: profile.displayName || initialData.displayName || "",
         birthDate: profile.birthDate || initialData.birthDate || "",
         defaultCurrency: profile.defaultCurrency || initialData.defaultCurrency || "JPY",
         manualMonthlyBaseExpense: profile.manualMonthlyBaseExpense != null ? profile.manualMonthlyBaseExpense.toString() : initialData.manualMonthlyBaseExpense || "",
@@ -99,6 +101,7 @@ export default function OnboardingPage() {
     try {
       await updateProfile({
         id: "me",
+        displayName: formData.displayName.trim() || null,
         birthDate: formData.birthDate,
         defaultCurrency: formData.defaultCurrency || "JPY",
         manualMonthlyBaseExpense: formData.manualMonthlyBaseExpense ? parseInt(formData.manualMonthlyBaseExpense.replace(/,/g, '')) : null,
diff --git a/hub-ui/src/app/finance/profile/me/page.tsx b/hub-ui/src/app/finance/profile/me/page.tsx
index a556c00..3086932 100644
--- a/hub-ui/src/app/finance/profile/me/page.tsx
+++ b/hub-ui/src/app/finance/profile/me/page.tsx
@@ -12,6 +12,7 @@ export default function ProfileConfigPage() {
   const [isInitialized, setIsInitialized] = useState(false);
   const [isSaving, setIsSaving] = useState(false);
   const [formData, setFormData] = useState<UserProfileFormData>({
+    displayName: "",
     birthDate: "",
     defaultCurrency: "JPY",
     manualMonthlyBaseExpense: "",
@@ -41,6 +42,7 @@ export default function ProfileConfigPage() {
 
     if (profile) {
       setFormData({
+        displayName: profile.displayName || "",
         birthDate: profile.birthDate || "",
         defaultCurrency: profile.defaultCurrency || "JPY",
         manualMonthlyBaseExpense: profile.manualMonthlyBaseExpense != null ? profile.manualMonthlyBaseExpense.toString() : "",
@@ -83,6 +85,7 @@ export default function ProfileConfigPage() {
     try {
       await updateProfile({
         id: "me",
+        displayName: formData.displayName.trim() || null,
         birthDate: formData.birthDate,
         defaultCurrency: formData.defaultCurrency || "JPY",
         manualMonthlyBaseExpense: formData.manualMonthlyBaseExpense ? parseInt(formData.manualMonthlyBaseExpense.replace(/,/g, '')) : null,
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                            10017  12.91G   3.46    11:27:27  

  edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                        10020  13.46G   5.62    11:48:37  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java
index cd76b92f..f34a728b 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java
@@ -66,6 +66,7 @@ public class UserProfileServiceImpl implements UserProfileService {
         if (command.birthDate() != null) {
             profile.setBirthDate(command.birthDate());
         }
+        profile.setDisplayName(command.displayName());
         profile.setDefaultCurrency(command.defaultCurrency());
         profile.setManualMonthlyBaseExpense(command.manualMonthlyBaseExpense());
         profile.setTargetRetirementAge(command.targetRetirementAge());
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveUserProfileCommand.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveUserProfileCommand.java
index 30d8eae3..638654cb 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveUserProfileCommand.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/command/SaveUserProfileCommand.java
@@ -30,6 +30,7 @@ import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.command.S
 import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
 
 public record SaveUserProfileCommand(
+        String displayName,
         LocalDate birthDate,
         CurrencyCode defaultCurrency,
         Long manualMonthlyBaseExpense,
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/result/UserProfileResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/result/UserProfileResult.java
index 4a92c308..eed45b13 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/result/UserProfileResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/result/UserProfileResult.java
@@ -45,6 +45,7 @@ import lombok.NoArgsConstructor;
 @EqualsAndHashCode(callSuper = true)
 public class UserProfileResult extends BaseResult {
     private String id;
+    private String displayName;
     private LocalDate birthDate;
     private CurrencyCode defaultCurrency;
     private Long manualMonthlyBaseExpense;
@@ -56,20 +57,13 @@ public class UserProfileResult extends BaseResult {
     private Integer taxAdvantagedWithdrawalAge;
     private boolean applyProfileToOptimizerBaseline;
 
-    public UserProfileResult(String id, LocalDate birthDate, Long manualMonthlyBaseExpense, Integer targetRetirementAge,
-            Integer pensionStartAge, List<DependentResult> familyMembers, List<InsurancePolicyResult> insurancePolicies,
-            Integer lifeExpectancy, Integer taxAdvantagedWithdrawalAge, boolean applyProfileToOptimizerBaseline) {
-        this(id, birthDate, null, manualMonthlyBaseExpense, targetRetirementAge, pensionStartAge,
-                familyMembers, insurancePolicies, lifeExpectancy, taxAdvantagedWithdrawalAge,
-                applyProfileToOptimizerBaseline);
-    }
-
     public static UserProfileResult fromEntity(UserProfile entity, String ownerUserId, List<InsurancePolicy> policies) {
         if (entity == null) {
             return null;
         }
         UserProfileResult result = new UserProfileResult();
         result.setId(entity.getId());
+        result.setDisplayName(entity.getDisplayName());
         result.setBirthDate(entity.getBirthDate());
         result.setDefaultCurrency(entity.getDefaultCurrency());
         result.setManualMonthlyBaseExpense(entity.getManualMonthlyBaseExpense());
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
index bb1a3f15..16a444a2 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java
@@ -131,7 +131,8 @@ class PensionProjectionServiceTest {
         when(pensionCalculator.calculatePensionMultiplier(65)).thenReturn(1.0);
 
         when(userProfileService.getUserProfile("user-1")).thenReturn(Optional.of(
-                new UserProfileResult("user-1", LocalDate.of(1980, 1, 1), null, 60, 65, null, null, 85, null, false)));
+                new UserProfileResult("user-1", null, LocalDate.of(1980, 1, 1), null, null, 60, 65, null, null, 85,
+                        null, false)));
 
         // Act
         List<PensionProjectionYearlyResult> result = projectionService.calculateProjection("user-1", 2, null);
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java
index 02eb58a1..3279a220 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java
@@ -46,10 +46,13 @@ import org.springframework.test.util.ReflectionTestUtils;
 
 import ch.lin.finance.hub.backend.api.app.repository.family.FamilyGroupRepository;
 import ch.lin.finance.hub.backend.api.app.repository.family.FamilyMemberRepository;
+import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
 import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;
+import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyMemberResult;
 import ch.lin.finance.hub.backend.api.domain.model.family.FamilyGroup;
 import ch.lin.finance.hub.backend.api.domain.model.family.FamilyMember;
 import ch.lin.finance.hub.backend.api.domain.model.family.FamilyRole;
+import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
 
 @ExtendWith(MockitoExtension.class)
 class FamilyServiceImplTest {
@@ -60,6 +63,9 @@ class FamilyServiceImplTest {
     @Mock
     private FamilyMemberRepository familyMembershipRepository;
 
+    @Mock
+    private UserProfileRepository userProfileRepository;
+
     @InjectMocks
     private FamilyServiceImpl familyService;
 
@@ -244,4 +250,33 @@ class FamilyServiceImplTest {
                 .isInstanceOf(IllegalStateException.class)
                 .hasMessageContaining("Only an ADMIN can disband the family group.");
     }
+
+    @Test
+    @SuppressWarnings("null")
+    void getFamilyMembers_resolvesDisplayNameAndFallback() {
+        FamilyMember member1 = new FamilyMember("user-alice-1234", group, FamilyRole.ADMIN);
+        FamilyMember member2 = new FamilyMember("user-bob-5678", group, FamilyRole.MEMBER);
+        FamilyMember member3 = new FamilyMember("user-charlie-9999", group, FamilyRole.MEMBER);
+
+        when(familyMembershipRepository.findByUserId("user-alice-1234")).thenReturn(Optional.of(member1));
+        when(familyMembershipRepository.findByFamilyGroupId(group.getId()))
+                .thenReturn(List.of(member1, member2, member3));
+
+        UserProfile profile1 = new UserProfile("user-alice-1234", null, null, null);
+        profile1.setDisplayName("Alice in Wonderland");
+        when(userProfileRepository.findById("user-alice-1234")).thenReturn(Optional.of(profile1));
+
+        UserProfile profile2 = new UserProfile("user-bob-5678", null, null, null);
+        profile2.setDisplayName(""); // blank display name
+        when(userProfileRepository.findById("user-bob-5678")).thenReturn(Optional.of(profile2));
+
+        when(userProfileRepository.findById("user-charlie-9999")).thenReturn(Optional.empty()); // no profile
+
+        List<FamilyMemberResult> results = familyService.getFamilyMembers("user-alice-1234");
+
+        assertThat(results).hasSize(3);
+        assertThat(results.get(0).getDisplayName()).isEqualTo("Alice in Wonderland");
+        assertThat(results.get(1).getDisplayName()).isEqualTo("User-user"); // fallback to first 4 chars of userId
+        assertThat(results.get(2).getDisplayName()).isEqualTo("User-user"); // fallback
+    }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java
index 07886d55..ed016455 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileRequest.java
@@ -35,6 +35,7 @@ import jakarta.validation.constraints.Min;
 
 public record UserProfileRequest(
         String id,
+        String displayName,
         LocalDate birthDate,
         CurrencyCode defaultCurrency,
         Long manualMonthlyBaseExpense,
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java
index a91e8b25..820cf249 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/dto/UserProfileResponse.java
@@ -32,6 +32,7 @@ import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
 
 public record UserProfileResponse(
         String id,
+        String displayName,
         LocalDate birthDate,
         CurrencyCode defaultCurrency,
         Long manualMonthlyBaseExpense,
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                             10021  13.38G   4.02    11:56:05  