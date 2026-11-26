# [FIXED] Issue 162：家族群組只有唯一一位成員（通常是剛建立群組、還沒有人加入的狀態）時，這位成員永遠沒有辦法離開或解散這個群組——例外訊息本身提到的兩個退路都沒有被實作

## 這份issue怎麼被發現的
這是這次Source Code Review針對家族共享業務邏輯進行審查時發現的問題。這跟issue-140討論的IDOR/資料隔離安全性問題是完全不同的面向——這份issue討論的是「正常、合法的操作流程本身是否完整」，不涉及任何未授權存取。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java:116-128`（`removeMember`，成員自己離開的情境：`if (currentUserMembership.getRole() == FamilyRole.ADMIN) { throw new IllegalStateException("ADMIN cannot leave the group. Transfer ownership or disband instead."); }`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyService.java`（整個介面裡，完全沒有`disbandFamily`、`transferOwnership`、或任何角色轉換的方法）
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/family/FamilyController.java`：確認只有get、create、refresh-invite、join、remove-member、list-members六個端點，沒有任何解散/轉移擁有權的端點
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java:69`（`createFamilyGroup`，建立者固定被設成`ADMIN`）

## 現狀
程式碼裡的這段例外訊息，字面上提到「請改用轉移擁有權或解散群組」這兩條路——但這兩個功能，從`FamilyService`介面、到實作、到Controller端點、到Repository，整個專案裡完全不存在。`FamilyRole`只是一個簡單的兩值列舉(`ADMIN`、`MEMBER`)，沒有任何方法可以把這個角色轉移給別人。因為`createFamilyGroup`永遠把建立者設成`ADMIN`，而剛建立的家族群組預設只有建立者一個成員（要等到有人用邀請碼加入才會有第二個成員），這代表**每一個剛建立的家族群組，在只有一位成員的狀態下，這位成員被永久卡住**——沒辦法離開(被`ADMIN`檢查擋住)、沒辦法解散(方法不存在)、也沒辦法把自己降級或把別人升級(方法不存在，而且也沒有別人可以升級)。

## 為何是問題
使用者建立一個家族群組做測試，或者原本以為配偶會加入、但配偶最終沒有加入(或加入後又想退出，讓群組恢復成沒有家族共享的狀態)——建立者會發現自己被永久困在一個無法退出的家族群組裡，而程式碼自己提供的唯一指引(例外訊息裡那段文字)，指向的是從未真正存在的功能。目前除了直接動資料庫，沒有任何自助的方式可以脫離這個狀態。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：實作「解散家族群組」**
- 做法：針對「只有自己一個成員、沒有其他人」的情境，讓`ADMIN`可以直接解散整個群組（刪除`FamilyGroup`，連帶刪除對應的`FamilyMember`紀錄）。
- 優點：直接對應例外訊息裡已經承諾的功能之一，處理最常見的「群組建立後沒人加入」情境。
- 缺點：需要先決定——如果`ADMIN`想解散一個已經有其他成員的群組該怎麼處理（直接拒絕，還是強制把所有成員一起移除）。

**方向B：實作「轉移擁有權」**
- 做法：讓`ADMIN`可以把角色轉移給另一位既有成員，自己降級成`MEMBER`，然後就能用一般成員身分正常離開。
- 優點：處理「群組裡還有其他人，但原建立者想交棒退出」這種情境。
- 缺點：單獨實作這個功能，沒辦法處理「群組裡只有自己一個人、沒有其他人可以轉移」這種最常見的卡住狀態——需要搭配方向A一起才能完整解決。

兩個方向很可能都需要，不是互相替代的選項；具體要不要兩個都做、各自的細節規則如何設計，留給你判斷。

---

## 決策與實作紀錄：採用方向 A（實作解散家族群組 `disbandFamily`）

### 一、決策說明
- 審查既有架構後確認：
  1. 解散家族群組（Disband）之實作難易度極低，資料庫實體 [`FamilyGroup`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/family/FamilyGroup.java) 與 [`FamilyMember`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/family/FamilyMember.java) 並無其他關聯資料表之外鍵約束。
  2. 現行例外提示訊息本就指引使用者：「*ADMIN cannot leave the group. Transfer ownership or disband instead.*」。
  3. 業務規則設計：身為群組 `ADMIN` 即擁有群組最高管理權限，呼叫解散時，系統將群組所有成員關係（`FamilyMember`）一併清除並刪除群組本身（`FamilyGroup`），不論是只有建立者一人、或已有其他成員，皆能乾淨解散並解除所有成員的綁定。
- 經 PM 指示，直接採用**方向 A**進行實作。

### 二、實作變更
1. **介面與實作**：
   - 在 [`FamilyService.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyService.java) 中新增 `void disbandFamily(String currentUserId);`。
   - 在 [`FamilyServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java) 中實作 `disbandFamily`：
     - 驗證當前使用者是否加入群組（若無則拋出 `IllegalStateException`）。
     - 驗證當前使用者是否具備 `FamilyRole.ADMIN` 權限（非 ADMIN 則拋出 `IllegalStateException`）。
     - 透過 `familyMembershipRepository.findByFamilyGroupId(groupId)` 找出所有成員，使用 `familyMembershipRepository.deleteAll(...)` 清除。
     - 透過 `familyGroupRepository.delete(group)` 刪除群組本身。
2. **REST API 端點**：
   - 在 [`FamilyController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/family/FamilyController.java) 中新增 `@DeleteMapping` 端點（對應 `DELETE /family`），符合 RESTful 設計規範。
3. **單元測試**：
   - 在 [`FamilyServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java) 中新增 `disbandFamily_success`、`disbandFamily_fail_notInFamily`、`disbandFamily_fail_notAdmin` 測試案例。
   - 在 [`FamilyControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/family/FamilyControllerTest.java) 中新增 `disbandFamily_success` 端點驗證測試。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                 10042  12.60G   9.00    17:23:48  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyService.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyService.java
index b7a043b4..03bffc01 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyService.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyService.java
@@ -64,6 +64,14 @@ public interface FamilyService {
      */
     void removeMember(String currentUserId, String targetUserId);
 
+    /**
+     * Disband the family group. Only the ADMIN can perform this action.
+     * Removes all members and deletes the family group entity.
+     *
+     * @param currentUserId the ID of the user requesting disbandment
+     */
+    void disbandFamily(String currentUserId);
+
     /**
      * Get a list of family members for the current user's family group.
      *
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java
index bd8ec586..970fbfe5 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java
@@ -144,6 +144,24 @@ public class FamilyServiceImpl implements FamilyService {
                 currentUserMembership.getFamilyGroup().getId());
     }
 
+    @Override
+    @Transactional
+    public void disbandFamily(String currentUserId) {
+        FamilyMember currentUserMembership = familyMembershipRepository.findByUserId(currentUserId)
+                .orElseThrow(() -> new IllegalStateException("Current user does not belong to a family group."));
+
+        if (currentUserMembership.getRole() != FamilyRole.ADMIN) {
+            throw new IllegalStateException("Only an ADMIN can disband the family group.");
+        }
+
+        FamilyGroup group = currentUserMembership.getFamilyGroup();
+        List<FamilyMember> members = familyMembershipRepository.findByFamilyGroupId(group.getId());
+        familyMembershipRepository.deleteAll(Objects.requireNonNull(members));
+        familyGroupRepository.delete(group);
+
+        log.info("Disbanded family group '{}' (id: {}) by admin {}", group.getName(), group.getId(), currentUserId);
+    }
+
     @Override
     @Transactional(readOnly = true)
     public List<FamilyMemberResult> getFamilyMembers(String currentUserId) {
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java
index b778ab47..02eb58a1 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java
@@ -24,6 +24,8 @@
 package ch.lin.finance.hub.backend.api.app.service.family;
 
 import java.time.LocalDateTime;
+import java.util.List;
+import java.util.Objects;
 import java.util.Optional;
 
 import static org.assertj.core.api.Assertions.assertThat;
@@ -208,4 +210,38 @@ class FamilyServiceImplTest {
 
         assertThat(result).isEmpty();
     }
+
+    @Test
+    void disbandFamily_success() {
+        FamilyMember adminMembership = new FamilyMember("admin1", group, FamilyRole.ADMIN);
+        FamilyMember otherMember = new FamilyMember("user2", group, FamilyRole.MEMBER);
+        List<FamilyMember> members = List.of(adminMembership, otherMember);
+
+        when(familyMembershipRepository.findByUserId("admin1")).thenReturn(Optional.of(adminMembership));
+        when(familyMembershipRepository.findByFamilyGroupId(group.getId())).thenReturn(members);
+
+        familyService.disbandFamily("admin1");
+
+        verify(familyMembershipRepository).deleteAll(Objects.requireNonNull(members));
+        verify(familyGroupRepository).delete(Objects.requireNonNull(group));
+    }
+
+    @Test
+    void disbandFamily_fail_notInFamily() {
+        when(familyMembershipRepository.findByUserId("user1")).thenReturn(Optional.empty());
+
+        assertThatThrownBy(() -> familyService.disbandFamily("user1"))
+                .isInstanceOf(IllegalStateException.class)
+                .hasMessageContaining("Current user does not belong to a family group.");
+    }
+
+    @Test
+    void disbandFamily_fail_notAdmin() {
+        FamilyMember member = new FamilyMember("user2", group, FamilyRole.MEMBER);
+        when(familyMembershipRepository.findByUserId("user2")).thenReturn(Optional.of(member));
+
+        assertThatThrownBy(() -> familyService.disbandFamily("user2"))
+                .isInstanceOf(IllegalStateException.class)
+                .hasMessageContaining("Only an ADMIN can disband the family group.");
+    }
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/family/FamilyController.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/family/FamilyController.java
index 66393187..daac7692 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/family/FamilyController.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/family/FamilyController.java
@@ -92,6 +92,13 @@ public class FamilyController {
         return ResponseEntity.ok().build();
     }
 
+    @DeleteMapping
+    public ResponseEntity<Void> disbandFamily() {
+        String currentUserId = SecurityUtil.getCurrentUserId();
+        familyService.disbandFamily(currentUserId);
+        return ResponseEntity.ok().build();
+    }
+
     @GetMapping("/members")
     public ResponseEntity<List<FamilyMemberResponse>> getFamilyMembers() {
         String currentUserId = SecurityUtil.getCurrentUserId();
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/family/FamilyControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/family/FamilyControllerTest.java
index 7bca5dd5..ed24ffe8 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/family/FamilyControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/family/FamilyControllerTest.java
@@ -213,4 +213,15 @@ class FamilyControllerTest {
 
         verify(familyService).getFamilyMembers("user1");
     }
+
+    @Test
+    void disbandFamily_success() throws Exception {
+        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
+                .thenReturn("user1");
+
+        mockMvc.perform(delete("/family"))
+                .andExpect(status().isOk());
+
+        verify(familyService).disbandFamily("user1");
+    }
 }
diff --git a/finance-hub-backend/todo/pending fix/issue-162.md b/finance-hub-backend/todo/pending fix/issue-162.md
index f037a09c..7019058b 100644
--- a/finance-hub-backend/todo/pending fix/issue-162.md
+++ b/finance-hub-backend/todo/pending fix/issue-162.md
@@ -29,3 +29,29 @@
 - 缺點：單獨實作這個功能，沒辦法處理「群組裡只有自己一個人、沒有其他人可以轉移」這種最常見的卡住狀態——需要搭配方向A一起才能完整解決。
 
 兩個方向很可能都需要，不是互相替代的選項；具體要不要兩個都做、各自的細節規則如何設計，留給你判斷。
+
+---
+
+## 決策與實作紀錄：採用方向 A（實作解散家族群組 `disbandFamily`）
+
+### 一、決策說明
+- 審查既有架構後確認：
+  1. 解散家族群組（Disband）之實作難易度極低，資料庫實體 [`FamilyGroup`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/family/FamilyGroup.java) 與 [`FamilyMember`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/family/FamilyMember.java) 並無其他關聯資料表之外鍵約束。
+  2. 現行例外提示訊息本就指引使用者：「*ADMIN cannot leave the group. Transfer ownership or disband instead.*」。
+  3. 業務規則設計：身為群組 `ADMIN` 即擁有群組最高管理權限，呼叫解散時，系統將群組所有成員關係（`FamilyMember`）一併清除並刪除群組本身（`FamilyGroup`），不論是只有建立者一人、或已有其他成員，皆能乾淨解散並解除所有成員的綁定。
+- 經 PM 指示，直接採用**方向 A**進行實作。
+
+### 二、實作變更
+1. **介面與實作**：
+   - 在 [`FamilyService.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyService.java) 中新增 `void disbandFamily(String currentUserId);`。
+   - 在 [`FamilyServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImpl.java) 中實作 `disbandFamily`：
+     - 驗證當前使用者是否加入群組（若無則拋出 `IllegalStateException`）。
+     - 驗證當前使用者是否具備 `FamilyRole.ADMIN` 權限（非 ADMIN 則拋出 `IllegalStateException`）。
+     - 透過 `familyMembershipRepository.findByFamilyGroupId(groupId)` 找出所有成員，使用 `familyMembershipRepository.deleteAll(...)` 清除。
+     - 透過 `familyGroupRepository.delete(group)` 刪除群組本身。
+2. **REST API 端點**：
+   - 在 [`FamilyController.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/family/FamilyController.java) 中新增 `@DeleteMapping` 端點（對應 `DELETE /family`），符合 RESTful 設計規範。
+3. **單元測試**：
+   - 在 [`FamilyServiceImplTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/family/FamilyServiceImplTest.java) 中新增 `disbandFamily_success`、`disbandFamily_fail_notInFamily`、`disbandFamily_fail_notAdmin` 測試案例。
+   - 在 [`FamilyControllerTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/family/FamilyControllerTest.java) 中新增 `disbandFamily_success` 端點驗證測試。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                      10043  12.72G   8.81    17:23:57  