# Fixed 96：個人設定填NISA月投入超過20萬元時，模擬被誤鎖在成長枠上限、資產成長遭低估

## 嚴重程度
Medium——這不是一次性的顯示錯誤，而是會在每一次模擬中持續發生：只要使用者本身還沒有實際匯入任何NISA基金持股資料、但在個人設定裡填了每月NISA投入金額，且這個金額超過20萬元，系統就會把超過20萬的部分整年度都吃不進去，導致長期資產成長被系統性低估。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/context/NisaContextBuilder.java`（第86行：當使用者沒有匯入NISA持股，只用個人設定的月投入金額建立一筆「虛擬基金」時，把這筆虛擬基金的帳戶類型寫死成 `.nisaAccountType(NisaAccountType.GROWTH)`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java`（第89-121行：依帳戶類型分別套用「成長投資枠」與「定期定額枠」各自的年度上限）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/TaxConstants.java`（`NISA_GROWTH_ANNUAL_LIMIT` = 2,400,000；`NISA_TSUMITATE_ANNUAL_LIMIT` = 1,200,000；`NISA_ANNUAL_LIMIT`（兩者合計）= 3,600,000）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaUserSettingsRequest.java`（第38行：`monthlyNisaContribution` 這個個人設定欄位本身沒有任何上限限制，使用者可以自由填超過20萬的數字）

## 現狀
使用者可以在個人設定裡填寫「每月NISA投入金額」，讓系統在沒有實際匯入任何NISA持股資料的情況下，也能用這個設定值去模擬未來的NISA資產成長。日本NISA制度裡，「成長投資枠」跟「定期定額枠」各自有各自的年度上限：成長投資枠一年最多240萬元，定期定額枠一年最多120萬元，兩者分開來看、可以同時使用，全部加起來一年最多可以投入360萬元（相當於每月30萬元）。

但如果使用者在個人設定裡填的每月投入金額，換算成年度金額後超過240萬元（也就是每月超過20萬元）——例如填每月30萬元、也就是合法上限的360萬元——系統目前完全不會讓這超過240萬元的部分被投入進去，等於把使用者原本合法可以投入的每月30萬元，硬生生砍成每月20萬元，長期下來，NISA資產的成長會被明顯低估。

## 原因
當使用者還沒有實際匯入任何NISA持股資料，只用個人設定的月投入金額去模擬時，系統會依這個設定值建立一筆「虛擬基金」代表這筆投入。問題出在 `NisaContextBuilder.java` 第86行，建立這筆虛擬基金時，直接把它的帳戶類型寫死成「成長投資枠」（`NisaAccountType.GROWTH`），完全沒有考慮到使用者填的金額可能已經超過成長投資枠自己的年度上限（240萬），需要拆一部分到「定期定額枠」（年度上限120萬）才能真正把使用者想投入的金額都算進去。

後續 `TaxAdvantagedAccountProjector.java` 在計算每年實際可以投入多少時（第89-121行），是分別依照基金的帳戶類型去對照自己那個類型的年度上限。因為這筆虛擬基金整筆都被標記成「成長投資枠」，即使使用者原本想投入的金額（例如每月30萬、年度360萬）沒有超過「成長+定期定額」合計的360萬上限，也沒有超過整體終身額度，系統仍然會依照「成長投資枠」自己的240萬年度上限去卡住這筆虛擬基金，把超過240萬的部分整年度都排除在投入之外。

## 具體錯誤情境
使用者還沒有匯入任何NISA持股，在個人設定裡填「每月NISA投入金額」30萬元（年度360萬元，恰好等於成長+定期定額兩枠合計的法定上限，且假設終身額度空間也還足夠）。系統建立虛擬基金時把它標記成「成長投資枠」，套用240萬的年度上限，結果這一年只會模擬投入240萬元，另外120萬元（相當於使用者原本想投入的每月30萬中，超過每月20萬的那部分）完全消失，不會被計入資產成長模擬。長期累積下來，使用者看到的NISA資產成長曲線，會比自己實際照這個設定執行所能達到的成長曲線明顯偏低。

## 建議修正
這個問題的核心是「使用者填的單一總金額，該怎樣拆分到成長投資枠跟定期定額枠這兩個各自有各自上限的額度裡」，這牽涉到分配規則的選擇，以下三個方向各有取捨，不應由開發端片面決定：

**方向A：系統自動依規則拆分——先把20萬（成長枠上限）用滿，剩下的部分自動歸到定期定額枠**
- 優點：不需要新增任何欄位或UI，使用者完全不用改變填寫方式，就能讓超過20萬的部分被正確模擬進去。
- 缺點：「先用滿成長枠、剩下才給定期定額枠」是系統自己選的一種分配方式，不一定符合使用者真正的投資意圖（例如使用者可能實際上是想要多存定期定額、少買成長股），這個假設要不要讓使用者知道，需要額外考慮。

**方向B：讓使用者自己在個人設定裡分別填「成長投資枠每月投入」跟「定期定額枠每月投入」兩個獨立欄位**
- 優點：最貼近使用者實際的真實配置意圖，不需要系統自己猜測分配規則。
- 缺點：需要新增UI欄位與後端資料結構的改動，且如果使用者原本已經填好單一總金額，還需要考慮怎麼處理既有資料的轉換或詢問使用者重新設定。

**方向C：維持現況，但在使用者填的月投入金額超過20萬元時，於畫面上顯示提示文字，說明超過20萬的部分目前不會被模擬進去**
- 優點：改動範圍最小，風險最低。
- 缺點：沒有真正解決資產成長被低估的問題，使用者如果沒有注意到這個提示，仍然會看到一個比實際可能達到的成長更保守的模擬結果。

## 與已知項目的區別
已檢查 `todo/planned/` 與 `todo/wontfix/` 目前列出的項目：`FINDING-23`（NISA終身額度沒有做到per-fund層級的檢查）討論的是完全不同的議題，跟本問題「單一虛擬基金的帳戶類型該怎麼標記」無關；`FINDING-09`、`FINDING-27` 討論的是舊版NISA目標試算器（legacy NISA goal solver）相關的問題，跟本問題所在的 `NisaContextBuilder`／`TaxAdvantagedAccountProjector` 這條目前實際使用中的計算路徑無關；`issue-21`（NISA匯入狀態文字顯示）也是完全不同的議題。本問題為全新發現的問題。

## 決策與結論 (Decision & Conclusion)
經過確認，決定採用 **方向 B** 作為修復方案。

## 藍圖
以下是基於系統現有架構擴充的具體設計藍圖。由於只是擴充現有的 Profile API 與 Sandbox UI，因此重點在於欄位替換、防呆驗證與前後端串接：

### 1. Domain (實體) 與 DTO 層
- **實體修改 (`NisaUserSettings`)**：
  - **保留並標記棄用**：保留原本單一的 `monthlyNisaContribution` 欄位並加上 `@Deprecated` 標記，待 Fixed-95 最佳化維度拆分完工後再行移除，避免破壞 Optimizer 依賴。
  - 新增 `monthlyGrowthContribution` (成長投資枠每月投入) 與 `monthlyTsumitateContribution` (積立枠每月投入) 兩個欄位。
- **Controller API DTOs**：
  - `NisaUserSettingsRequest` 與 `NisaUserSettingsResponse`：同樣保留舊欄位並標記 `@Deprecated`，並新增雙欄位。
  - `AssetSimulationRequest` (Sandbox Override DTO)：保留 `@Deprecated testNisaMonthlyContribution`，並新增 `testNisaGrowthMonthlyContribution` 與 `testNisaTsumitateMonthlyContribution`。
- **Service 層 DTOs (Command & Result)**：
  - 同步修改 `UpdateNisaUserSettingsCommand` 與 `NisaUserSettingsResult` 等跨層傳遞物件，採「保留舊欄位、新增雙欄位」的模式。
- **引擎傳遞物件 (`SimulationOverrides` 及 `ProfileBaselineUsageDetails`)**：
  - 保留舊屬性並標記 `@Deprecated`，同步擴充新的 Growth 與 Tsumitate 屬性。核心引擎在讀取時，優先採用新欄位，以維持與 Fixed-95 (Global Optimizer) 的向後相容性。

### 2. Service 與 Controller 層 (Clean Architecture)
- **Profile 儲存邏輯 (`NisaUserSettingsController` / Service)**：
  - API 路徑與邏輯不變，僅需更新 Mapper 以轉換新的雙欄位。
- **Sandbox 邊界防護與超額處理 (Native Scale-down)**：
  - 不在 `AssetSimulationEngineImpl` 或 `Controller` 進行超額攔截，以確保 Sandbox 能真實模擬「達到終身額度天花板」的時間軸現象（即前期正常投入，後期被截斷為 0）。
  - 完全依賴核心引擎 `TaxAdvantagedAccountProjector` 內建的比例縮退機制 (Proportional Scale-down) 來處理任何超額的投入參數，確保架構簡潔並與 Optimizer (GA/Grid) 完美相容。

### 3. 計算引擎修正 (確保虛擬基金正確建立)
- **Profile 初始化 (`NisaContextBuilder`)**：
  - 讀取 DB 設定時，不再建立單筆沒有 Type 的虛擬基金。
  - 改為：若 `monthlyGrowthContribution > 0`，建立一筆 `NisaAccountType.GROWTH` 的虛擬基金；若 `monthlyTsumitateContribution > 0`，建立一筆 `NisaAccountType.TSUMITATE` 的虛擬基金。
- **模擬引擎覆蓋邏輯 / Simulation Overrides (`FundOverrideApplier`)**：
  - *(註：此為核心共用路徑，無論是前端 Sandbox 手動測試，或是 Fixed-95 Optimizer 自動產生的測試組合，都會統一進入此邏輯處理)*
  - 處理傳入的測試值時，將 `testNisaGrowthMonthlyContribution` 按比例分配給既有的「真實成長基金」；若無真實成長基金，則精準建立一筆 `GROWTH` 的虛擬基金。
  - 針對 `testNisaTsumitateMonthlyContribution`，按比例分配給既有的「真實積立基金」；若無真實積立基金，則精準建立一筆 `NisaAccountType.TSUMITATE` 的虛擬基金。

### 4. 前端 UI 實作 (hub-ui)
- **視覺與 Data Type 脫鉤 (過渡期相容策略)**：
  - **畫面顯示**：將原先單一的 NISA 投入欄位移除，畫面上**只保留兩個獨立的新輸入框**：「NISA 成長投資枠 (月額)」與「NISA つみたて投資枠 (月額)」。
  - **資料結構 (Data Type)**：前端 API Payload 與 State 仍須保留舊有單一欄位，作為銜接 Fixed-95 (Global Optimizer) 的過渡期橋樑。
- **表單元件與盲點防護**：
  - **統一使用既有元件**：請使用 `@/components/ui/thousand-separator-input` (ThousandSeparatorInput) 以維持金額輸入體驗的一致性（自動千分位逗號）。
  - **卡控 (Client-side Validation)**：限制成長枠輸入最大值為 `200,000`，積立枠為 `100,000`。
- **資料自動同步與 Optimizer 相容**：
  - **送出時 (Payload 同步)**：前端發送 Request 之前，自動將「成長枠 + 積立枠」相加，寫入舊的 `@Deprecated` 單一欄位一併送出，確保 Optimizer 引擎有基準值可讀。
  - **接收時 (Optimizer 拆分降級)**：當使用者點擊 Optimizer 的 "Apply to Sandbox" 時，針對其回傳的單一 `testNisaMonthlyContribution` 進行自動拆分：**優先填滿積立枠 (最高 10萬)，剩餘金額溢流至成長枠 (最高 20萬)**，以此將單一推薦值正確回填到兩個新欄位中。
- **Sandbox 邊界體驗 (真實呈現撞牆期)**：
  - 前端無需特別處理超額報錯。
  - 當使用者輸入超額年化金額時，UI 允許直接送出，圖表將依賴後端引擎的縮退機制，如實呈現該資產在達到 1800 萬上限後的「水平折斷 (平移)」現象，反映真實世界的扣款限制。

### 5. 受影響的單元測試 (Unit Tests Impacted)
- **`GlobalOptimizerServiceTest`**：
  - 此測試目前會驗證 `result.appliedProfileBaselineDetails().nisaMonthlyContribution()`。由於該欄位已標記棄用且新增了雙欄位，測試案例需擴充以斷言 (Assert) 新的 `growth` 與 `tsumitate` 欄位是否正確從 Profile 中被提取與加總。
- **`AssetSimulationEngineImplTest`** (及相關核心測試)：
  - 需要新增測試案例，驗證當傳入新的雙欄位覆蓋值 (Simulation Overrides) 時，是否能正確觸發 `FundOverrideApplier` 分配到兩筆獨立的虛擬基金，而不是以前的一筆。
  - 驗證 `NisaContextBuilder` 初始化時，是否能根據 Profile 設定的兩個新欄位，正確建立對應的 `GROWTH` 與 `TSUMITATE` 虛擬基金。
- **`NisaUserSettingsServiceImplTest` / Controller 測試**：
  - 需驗證新的雙欄位 DTO 能正確被 Mapper 轉換並存入資料庫，且能相容舊有 `@Deprecated` 欄位的讀取情境。

---

# Review Handover Report (For Claude)

Hi Claude! We have successfully completed all phases of **Fixed-96 (NISA Dual-Dimension Implementation)** and integrated the corresponding NISA split logic required for **Fixed-95 (Global Optimizer)**.

Please review the following key areas to ensure code quality, architecture integrity, and edge-case handling:

## 1. Backend Core & API (Java/Spring Boot)
- **`NisaContextBuilder.java`**: Review the `initializeNisaContext` method. We removed the hardcoded `GROWTH` account type and replaced it with logic to create two distinct virtual funds (Growth & Tsumitate) based on `monthlyGrowthContribution` and `monthlyTsumitateContribution` from Profile Settings.
- **`FundOverrideApplier.java`**: Review how we distribute Sandbox overrides (`testNisaGrowthMonthlyContribution` & `testNisaTsumitateMonthlyContribution`) to existing NISA funds or create precise virtual funds if none exist.
- **Engine Robustness**: 
  - Verify that `AssetSimulationEngineImpl.java` correctly delegates oversized inputs to `TaxAdvantagedAccountProjector`, allowing the native proportional scale-down mechanism to handle limits without throwing exceptions.
- **DTOs**: Ensure `NisaUserSettingsRequest`, `NisaUserSettingsResult`, `UpdateNisaUserSettingsCommand`, and `AssetSimulationRequest` correctly retain `@Deprecated` legacy fields for backward compatibility while properly integrating the new dual fields.

## 2. Unit Tests
- **`AssetSimulationEngineImplTest.java`**: Verify the new test cases that ensure `FundOverrideApplier` properly distributes dual overrides into two separate virtual funds.
- **`GlobalOptimizerServiceTest.java`**: Confirm that the assertions check for the sum/split of the new dual fields instead of only relying on the deprecated field.
- **`NisaUserSettingsServiceImplTest.java`**: Ensure Mapper tests correctly map both the new dual fields and the legacy fallback fields to and from the database.

## 3. Frontend (hub-ui / React / Next.js)
- **`profile-contributions-section.tsx`**: Review the UI swap. We replaced the single NISA field with two `<ThousandSeparatorInput>` components and implemented client-side max limits (Growth: 200k, Tsumitate: 100k).
- **`use-simulation-data.ts`**: Review the Sandbox override state integration and the `try-catch` block that parses the backend JSON string inside the 400 error message (extracting `totalRemaining` & `growthRemaining`) to display a user-friendly `toast.error()`.
- **`use-global-optimizer.ts` & `global-optimizer-card.tsx`**: Check the response type updates and the UI display split. We now show two optimal NISA recommendations instead of one.
- **`page.tsx`**: Review the "Apply to Sandbox" logic to ensure the dual fields returned by the Optimizer are correctly mapped into the Sandbox overrides state.

The system should now smoothly handle user profile settings, sandbox simulations, and AI Optimizer recommendations without ever underestimating the legal NISA limits!
