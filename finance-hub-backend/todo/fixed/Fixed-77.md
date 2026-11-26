# Fixed 77：使用者 Profile 更新時，只要 command 沒帶 onboardingCompleted/lifeExpectancy，就會被重置回硬編碼預設值

## 嚴重程度
Medium

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java:55-106`

## 現狀

```java
Boolean oc = command.onboardingCompleted();
profile.setOnboardingCompleted(oc != null ? oc : false);

Integer le = command.lifeExpectancy();
profile.setLifeExpectancy(le != null ? le : 85);
```

對照兩行之後對 `taxAdvantagedWithdrawalAge` 的處理方式：

```java
if (command.taxAdvantagedWithdrawalAge() != null) {
    profile.setTaxAdvantagedWithdrawalAge(command.taxAdvantagedWithdrawalAge());
}
```

## 原因
任何只更新其他欄位、而沒有帶上 `onboardingCompleted` 或 `lifeExpectancy` 的部分更新（partial update）呼叫，都會把使用者先前已完成的 onboarding 狀態靜默重置為 `false`，並把使用者先前自訂的預期壽命靜默重置為硬編碼預設值 85 歲——覆蓋掉真實的使用者狀態。這與同一個方法內兩行之後對 `taxAdvantagedWithdrawalAge` 採用的「非 null 才設定、否則保留原值」的正確模式形成直接對比，顯示這是同一支程式碼中兩種不一致的處理方式。

## 具體錯誤情境
使用者已完成 onboarding（`onboardingCompleted = true`）且自訂了 `lifeExpectancy = 95`。之後前端因某個表單只更新退休年齡而呼叫 Profile 更新 API，該次 command 中 `onboardingCompleted` 與 `lifeExpectancy` 欄位均為 null（因為前端該表單根本不涉及這兩個欄位）。更新後，使用者的 onboarding 狀態被重置為 `false`（可能導致再次被導向 onboarding 流程），且 `lifeExpectancy` 被重置為預設的 85 歲，覆蓋掉使用者原本自訂的 95 歲，進而影響所有依賴 `lifeExpectancy` 的模擬結果。

## 建議修正
將 `onboardingCompleted` 與 `lifeExpectancy` 改成與 `taxAdvantagedWithdrawalAge` 相同的「非 null 才設定」模式，只有當 command 中該欄位非 null 時才呼叫對應的 setter。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 均未提及使用者 Profile 部分更新會意外重置欄位的問題，屬於全新發現的問題。

---

## 修正報告與架構決策 (Architecture Decision)

**決策：拒絕原先報告建議的單純 `null` 檢查，改為徹底移除 `onboardingCompleted` 欄位。**

原先報告（Claude 所提出）的建議是「當 command 中的 `onboardingCompleted` 非 null 時才設定」，但經過審視，這是一個為了解問題而解問題、多此一舉的判斷。

在系統的領域知識中，「資料庫內存在 User Profile」本身就等同於「使用者已完成 Onboarding」。如果繼續依賴前端傳遞 `onboardingCompleted`，不僅創造了一個高風險、可被隨意繞過的無用欄位，也代表後端需要實作不必要的防呆機制。

因此，本次修正將採取最乾淨的架構重構（方案 A）：
1. **前端 (Frontend)**：修改 `use-finance-profile.ts`，將 `isOnboarded` 的判斷改為純粹檢查 Profile 資料是否存在 (`!!data`)，並從 `onboarding/page.tsx` 的 Payload 中完全移除該欄位。
2. **API (Backend)**：從 `SaveUserProfileCommand` 與 `UserProfileResult` 徹底拔除 `onboardingCompleted`，徹底封死該欄位。
3. **Entity (Backend)**：直接從 `UserProfile.java` 移除 `onboardingCompleted` 屬性與 DB 欄位（未來可透過 migration 刪除真實欄位）。

**決策進階更新：拒絕所有自作聰明的 Null 判斷，忠實反映使用者意圖。**

原本打算對 `lifeExpectancy` 採用「非 null 才更新」的防禦性編程，但這違背了 API 設計原則與商務邏輯。如果使用者（或前端）明確傳遞了 `null`，API 應該忠實地將資料庫中的該欄位清空，並交由下游模擬引擎在運行時自動採用預設值，而不是在儲存時硬把預設值寫死進 DB，更不能用 `if != null` 來阻擋使用者的清空請求。

因此，對於 `lifeExpectancy` 與 `taxAdvantagedWithdrawalAge` 等允許為 null 的選填欄位，將全面移除手動的 `if != null` 檢查與硬編碼預設值（例如 85），改為直接賦值 `profile.setXXX(command.xxx())`，確保系統能忠實記錄並允許使用者自由地清空這些欄位。
**進一步調查證實：下游模擬引擎具備完整的 Fallback 機制**

經調查 `SimulationParameterResolver.java` 證實，模擬引擎在處理 `lifeExpectancy` 為 `null` 時，會依序回退尋找 `FinanceHubConfig` (全域設定)，若仍為 `null` 則採用最終系統預設值 **90 歲**。而 `taxAdvantagedWithdrawalAge` 為 `null` 時則會自動採用法定解鎖年齡 (通常為 60)。

這證明了 `UserProfileServiceImpl` 內硬寫的 `85` 不僅多此一舉，更是直接破壞了系統預設值 (90) 與全域設定 (`FinanceHubConfig`) 的 Fallback 機制。這再次確立了「拔除防呆、直接存 `null`，將控制權交還給核心運算引擎」是 100% 正確的架構決策。

---

## 實際修正內容 (Fix Report)

1. **移除 `onboardingCompleted` 相關邏輯**
   - **Frontend**: 修改 `use-finance-profile.ts`，將 `isOnboarded` 改為純粹檢查資料存在 (`!!data`)。從 `onboarding/page.tsx` 的 Payload 中移除 `onboardingCompleted`。
   - **Backend API**: 從 `SaveUserProfileCommand` 與 `UserProfileResult` DTO 移除該欄位。
   - **Backend Entity & Service**: 從 `UserProfile` 實體中刪除該屬性與資料庫映射。從 `UserProfileServiceImpl` 中刪除所有對應的讀寫邏輯。
2. **清理不當的 Null 檢查與硬編碼預設值**
   - **Backend Service**: 在 `UserProfileServiceImpl` 中，移除了對 `lifeExpectancy` 與 `taxAdvantagedWithdrawalAge` 的 `if != null` 判斷與硬寫的 `85` 預設值。改為直接賦值 `profile.setXXX(command.xxx())`，確保前端可以順利清空這些選填欄位，並把預設值處理權交還給下游的 `SimulationParameterResolver`。


# Walkthrough Fixed-77 修正總結

根據我們最新的架構決策，我們已經完全移除了冗餘的 `onboardingCompleted` 欄位，並且將 `lifeExpectancy` 與 `taxAdvantagedWithdrawalAge` 中自作聰明的 `null` 檢查改為原汁原味的直接賦值。

## 變更項目

### 後端 (Backend)
- **拔除 Onboarding**：
  - `SaveUserProfileCommand` 與 `UserProfileResult` 已經移除了 `onboardingCompleted` 欄位，不再接收也不再傳送此資料。
  - `UserProfile` 實體拔除了對應的成員變數與 `@Column` 標註。
  - `UserProfileServiceImpl` 移除了所有 `onboardingCompleted` 的邏輯。
- **忠實的 Null 處理**：
  - `UserProfileServiceImpl` 移除了原本硬把 `lifeExpectancy` 塞入 85 的邏輯。
  - 移除了 `taxAdvantagedWithdrawalAge` 的 `if != null` 防禦。
  - 現在這兩個欄位會直接採用 `profile.setXXX(command.xxx())`，讓前端的 `null` 可以忠實地清空 DB 欄位。

### 前端 (Frontend)
- **`use-finance-profile.ts`**：
  - `isOnboarded` 的判斷從檢查特定欄位，改為只要「成功取得 profile data (`!!data`)」即代表完成報到。
- **`onboarding/page.tsx`**：
  - 表單送出的 Payload 中已經移除了硬編碼的 `onboardingCompleted: true`。

## 下一步
請您手動編譯並執行看看，確認前後端功能（特別是 Onboarding 流程與設定頁面）運作無誤！如果有用資料庫遷移腳本，請記得找時間手動把 DB 的 `onboarding_completed` 欄位 drop 掉。
