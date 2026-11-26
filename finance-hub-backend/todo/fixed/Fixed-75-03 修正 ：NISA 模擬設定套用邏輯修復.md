# 修正 Fixed 75-03：NISA 模擬設定套用邏輯修復

## 目標
修復模擬器核心（前後端）在抓取 NISA 定期定額設定時，因缺乏日期排序與帳戶類型比對，導致套用舊資料或跨投資枠（帳戶類型）錯誤套用的 Bug。

## 設計決策與分析
1. **排序邏輯後推至資料庫層（DB Layer）**：
   為了避免在後端 Java 記憶體或前端 TypeScript 內進行多餘的排序操作，本計畫決定在 Repository 層新增 `findAllByOrderByRecordDateDesc()` 方法。此舉確保後端模擬核心與前端呼叫的 API 皆能直接取得已依日期降冪排序的設定清單，簡化上層業務邏輯並提升效能。
2. **導入帳戶類型（Account Type）雙重比對**：
   資料庫中的 `NisaContributionSetting` 紀錄已涵蓋不同投資枠的設定。實務上，使用者可能在「NISA (成長)預り」與「NISA (つみたて)預り」兩種不同的投資枠中，買入完全同名的基金並各自設定不同的每月扣款金額。
   若僅依賴 `fundName` 進行比對，程式可能會錯誤地將「累積投資」的扣款設定套用至「成長投資」的資產上。為確保各個投資枠的設定精準對應到其屬資產，後端與前端的比對邏輯皆必須同時納入 `fundName` 與 `accountType` 雙重條件。

## 預計修改

### Backend: Data Layer

#### [MODIFY] [NisaContributionSettingRepository.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/nisa/NisaContributionSettingRepository.java)
- **變更點**：
  新增一個查詢方法，回傳依日期降冪排序的所有設定：
  `List<NisaContributionSetting> findAllByOrderByRecordDateDesc();`

#### [MODIFY] [NisaContributionSettingServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java)
- **變更點**：
  在 `getAllSettings()` 中，將原本的 `settingRepository.findAll()` 改為 `settingRepository.findAllByOrderByRecordDateDesc()`，確保 API 回傳給前端的資料已經預先排好序。

### Backend: Simulation Core

#### [MODIFY] [NisaContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java)
- **變更點**：
  將取得設定的邏輯改為使用新的 Repository 方法：
  `var nisaSettings = nisaContributionSettingRepository.findAllByOrderByRecordDateDesc();`
  在迴圈內比對時，加入帳戶類型比對：
  `.filter(s -> s.getFundName().equals(record.getFundName()) && s.getAccountType().equals(record.getAccountType()))`

### Frontend: Simulation Override Panel

#### [MODIFY] [simulation-override-panel.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
- **變更點**：
  由於 API 回傳的 `nisaSettings` 已經依照時間降冪排序，前端不再需要手動進行排序。
  直接將原本的比對邏輯改為雙條件比對（確保帳戶類型跟基金名稱皆吻合）：
  ```ts
  const setting = Array.isArray(nisaSettings) ? nisaSettings.find((s: any) => s.fundName === snap.fundName && s.accountType === snap.accountType) : null;
  ```

## 驗證計畫 (Verification Plan)

### 自動化測試
- `mvn test` 確保編譯與既有測試不受影響。（將由你執行）

### 手動驗證
1. 確保重新啟動前後端。
2. 開啟 Sandbox 模擬面板，確認 NISA (成長) 和 NISA (つみたて) 即使持有同名基金，也能正確帶入各自最新的每月扣款設定金額。
3. 檢查送出模擬時的計算與圖表是否符合設定值。
