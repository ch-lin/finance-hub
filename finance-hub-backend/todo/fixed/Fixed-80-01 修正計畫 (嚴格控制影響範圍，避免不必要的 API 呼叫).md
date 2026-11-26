# 修正計畫：Fixed 80-01 (嚴格控制影響範圍，避免不必要的 API 呼叫)

> [!IMPORTANT]  
> 針對您指出的第 3 點盲點（Optimizer 頁面目前沒有 fetch Profile），這份修正計畫精確縮小了範圍：**前端 Simulator 完全不需要新增任何 API 去撈 Profile**。我們將透過後端回傳的結果 DTO，將提示無縫接軌顯示在「最佳化結果頁 (Result Panel)」。

## User Review Required

請確認本次修正的 3 個步驟，特別是第 3 點的實作方式，是否完美避開了「擴大範圍」的風險：

---

## Proposed Changes

### 1. Backend (Data Layer)
將決定權存成使用者可查詢、可修改的設定。

#### [MODIFY] `UserProfile.java` 及相關 DTO
- 在 `UserProfile` 實體中新增 boolean 欄位：`applyProfileToOptimizerBaseline`（預設值為 `true`）。
- 在 `UserProfileFormData`、`UserProfileDto`、以及 `UserProfileMapper` 中加入對應欄位。

#### [MODIFY] `GlobalOptimizerResponse.java` (及對應 Mapper / Overrides)
- 在最佳化結果的 DTO 中，新增一個欄位 `boolean usedProfileBaseline;`。
- 這能讓前端收到結果時，順便知道後端剛剛是套用哪一種假設。

#### [MODIFY] [GlobalOptimizerServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/GlobalOptimizerServiceImpl.java)
- 在 `buildSeedChromosome` 中，**直接從現有的 `baseData.getBaseContext().getProfile()` 讀取設定**（完全不需要改動架構，因為 SimulationData 裡面本來就帶有 Profile）。
- 依據 `profile.isApplyProfileToOptimizerBaseline()` 決定鎖定基因種子值是 `null` 還是法定常態。
- 在最後組裝 `SimulationOverrides` 回傳時，把這個 boolean 也一併放進去。

---

### 2. Frontend (Profile 設定畫面)

#### [MODIFY] [profile-basic-info-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/components/profile-basic-info-section.tsx)
- 在「Target Retirement Age / Pension / iDeCo」等輸入框下方，新增一個共用的 Checkbox：
  - **Label**: "Apply my custom ages to Global Optimizer unselected dimensions"
  - **Description**: "When unchecked, AI optimization will use statutory defaults (e.g., 60 for iDeCo) for unselected options."

---

### 3. Frontend (Optimizer 提示畫面 - 控制範圍的解法)
為了不讓 Simulator 頁面多打一支 API 去撈 Profile，我們把提示放在 **Optimizer Result Panel (最佳化結果區)**。

#### [MODIFY] [GlobalOptimizerCard.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)
- 在 `OptimizerResultPanel` 元件中（接收到 `result` 後顯示的區塊）：
  - 若 `result.usedProfileBaseline` 為 true：顯示「*Unselected dimensions used your custom Profile settings.*」
  - 若為 false：顯示「*Unselected dimensions used statutory defaults.*」
- **這樣前端完全不用提早撈 Profile**，完全符合 `Fixed-80.md` 中「或最佳化結果頁也應該讓使用者看得到...」的規劃，範圍精準收斂。

---

## Verification Plan

### Manual Verification
1. **Frontend**: 到 Profile 頁面，確認新增的開關能正常存檔。到 Simulator 頁面執行最佳化，確認**結果面板**能正確顯示提示。
2. **Backend**:
   - 確認執行最佳化時，後端可以從 `baseData` 順利讀取 `applyProfileToOptimizerBaseline`，並正確反映在回傳的 `GlobalOptimizerResponse` 中。
