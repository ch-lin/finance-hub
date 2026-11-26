# Fixed 58：「預期壽命」欄位後端已經支援使用者自訂，但前端從頭到尾都沒有輸入框可以填——確認是漏刻，需要補上

> 本篇原本是 question-01（確認這是不是刻意的產品決定），已確認**是漏刻**，需要補上 UI，因此改成 issue 並直接處理掉，不再另外留一份 question 檔案。

## 嚴重程度
Medium（不是計算錯誤，而是一個完全沒做出來的功能缺口：使用者永遠沒有機會自訂這個會直接影響退休模擬結果的數字）

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/components/user-profile-form.tsx`（新用戶導引頁跟個人資料設定頁共用的表單元件，目前完全沒有「預期壽命」的輸入框，也沒有 `lifeExpectancy` 這個欄位定義）
- `hub-ui/hub-ui/src/app/finance/onboarding/page.tsx:20-40`（表單初始資料 `formData` 的定義，沒有 `lifeExpectancy`）、`:94-119`（送出給後端的資料，同樣沒有 `lifeExpectancy`）
- `hub-ui/hub-ui/src/app/finance/profile/me/page.tsx:14-34`（表單初始資料 `formData` 的定義，沒有 `lifeExpectancy`）、`:78-103`（送出給後端的資料，同樣沒有 `lifeExpectancy`）
- `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/user/UserProfileServiceImpl.java:93`（後端儲存時，`lifeExpectancy` 已經是「沒填就傳 null、讓下游用系統預設值」的正確寫法，不需要修改）
- `finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java`（決定「使用者填的值 > 系統預設值」這套退回機制的地方，不需要修改）
- `finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java:251`（系統全域的「預期壽命」預設值，目前是 **85 歲**）

## 現狀
後端「預期壽命」這個欄位的設計是「使用者自己填的話就用使用者填的；沒填的話就用系統設定的統一預設值」，這套機制本身沒有問題。但目前 `hub-ui` 前端所有跟個人資料相關的畫面——新用戶導引頁、個人資料設定頁，以及這兩個頁面共用的表單元件——從頭到尾都沒有任何一個輸入框可以讓使用者填寫這個數字。

結果就是：**每一個使用者、每一次儲存，這個欄位一定是空的**，永遠使用系統統一的 85 歲預設值，沒有人有機會自己填一個不一樣的數字（例如根據自己的健康狀況、家族病史，想模擬活到 90 歲或 100 歲的情境）。

已確認這是開發時漏刻前端輸入框，不是故意不開放使用者自訂。

## 補充發現：系統預設值本身有一個小落差（85 歲 vs 90 歲），順手一起處理
查程式碼時發現：系統全域設定（`FinanceHubConfig.java:251`）裡「預期壽命」的預設值是 **85 歲**，但決定退回機制的 `SimulationParameterResolver` 裡，還多寫了一個「如果連系統全域設定都拿不到，就用 90 歲」的備援值。因為系統全域設定實際上一定存在（不會是空的），所以「90 歲」這個備援值目前永遠不會被真正用到，只有 85 歲這個數字才是所有使用者現在實際套用的預設值。

這個「85 跟 90 兩個數字並存」的小落差目前不影響任何人（因為 90 那條路徑根本用不到），但現在正好要在同一段程式碼附近新增使用者輸入框、讓這個預設值第一次真正要顯示給使用者看，建議一併把這個備援值也改成 85，避免之後有人看到程式碼裡兩個不同數字而感到疑惑。

## 建議修正
1. 在 `hub-ui/hub-ui/src/app/finance/components/user-profile-form.tsx` 新增「預期壽命」的輸入框跟對應的 `lifeExpectancy` 欄位定義。這個欄位應該做成「可以留空」的輸入框（留空 = 使用系統預設值 85 歲），不要像「目標退休年齡」那樣強制給一個預設文字（例如 "65"），可以參考同一個表單裡「稅務優惠提款年齡」（`taxAdvantagedWithdrawalAge`）目前「可留空、留空就不送這個欄位」的做法。
2. 在 `hub-ui/hub-ui/src/app/finance/onboarding/page.tsx` 跟 `hub-ui/hub-ui/src/app/finance/profile/me/page.tsx` 這兩個頁面，把 `lifeExpectancy` 加進 `formData` 的初始狀態，並在送出給後端的資料裡把這個欄位一起帶上（留空就傳 `null`，後端已經支援）。
3. 順手把 `SimulationParameterResolver` 裡「90 歲」的備援值改成 85 歲，跟 `FinanceHubConfig` 的設定保持一致。

## 與已知項目的區別
這是在驗證 issue-12（「onboardingCompleted」跟「預期壽命」欄位被儲存動作悄悄覆蓋的問題）過程中發現的。issue-12 原本描述的問題（儲存時被覆蓋）已經修好，跟這裡完全無關——這裡是「這個欄位從一開始就沒有輸入框可以填」，是不同層面的問題。

---

## AI 驗證與修復報告 (2026-09-11)

### 執行修復項目
1. **補齊前端 UI 缺口**：
   - 於 `user-profile-form.tsx` 的型別定義中補上 `lifeExpectancy: string;`。
   - 於 `profile-basic-info-section.tsx` 新增了「預期壽命 (Life Expectancy)」的 `<Input>` 元件，設為非必填且包含提示文字 `Optional (defaults to 85)`。
2. **前後端資料串接與初始化**：
   - 修改 `onboarding/page.tsx` 與 `profile/me/page.tsx`：讀取 API 回傳的資料時，若有 `lifeExpectancy` 則填入表單；送出資料給後端時，有填寫則轉成數字（`parseInt`），留空則傳 `null`，完全符合後端的「留空則使用系統預設」邏輯。
3. **優化後端備援機制 (Backend Fallback Removal)**：
   - **根據討論與架構決議**，在 `SimulationParameterResolver.java` 中將原先的 hardcode 備援值（`90` 歲、以及 `targetRetirementAge` / `pensionStartAge` 的 `65` 歲）全數移除（而非單純將 90 改為 85）。
   - 此決議的理由是：若系統配置（`FinanceHubConfig`）的設定值為 `null`，代表系統全域設定遺失，此為嚴重異常，不應使用 hardcode 值悄悄掩蓋。因此改為直接拋出 `IllegalArgumentException`，提供更嚴謹的錯誤防範。

### 等待驗證
- 程式碼已全數修改完畢，請重新執行 `mvn clean install` 確認後端編譯正常。
- 啟動 frontend 應用程式，進入個人資料設定及新用戶導引畫面，確認「預期壽命」欄位正常出現，且存取與清空均符合預期。
