# Fixed 92-01: DC/iDeCo 退職所得控除的服務年資進位修正

修正 DC/iDeCo 帳戶在計算退職所得控除的服務年資（勤続年數）時，捨去零頭的問題。依據日本稅法，不滿一年的零頭月數或天數應進位成一整年（1年未満の端数は1年に切り上げます）。

## Proposed Changes

### 1. 新增公用工具類別 (Utility Class)

為避免四處散落相同的進位邏輯，我們將建立一個專用的靜態工具類別來統一處理稅法相關的日期計算。因這個方法目前主要服務於同屬於 `simulation.core` 的 Pipeline 與 Builder，因此將其放置於 `ch.lin.finance.hub.backend.api.app.service.simulation.core.utils` 套件底下，以保持架構直觀且避免不當依賴。

#### [NEW] [TaxDateUtils.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/TaxDateUtils.java)
- 新增 `public static int calculateTaxServiceYears(LocalDate startDate, LocalDate endDate)` 方法。
- 邏輯：因 Java `Period.between` 預設不包含終點（Exclusive），而稅法年資計算必須包含頭尾（Inclusive）。因此需先將 `endDate` 加一天轉換語意，即計算 `Period.between(startDate, endDate.plusDays(1))`。若計算結果的 `getMonths() > 0` 或 `getDays() > 0` 則無條件進位（回傳 `getYears() + 1`），否則回傳 `getYears()`。

#### [NEW] [TaxDateUtilsTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/TaxDateUtilsTest.java)
- 新增單元測試，涵蓋剛好滿整年、多一天、多一個月等邊界情況，確保進位邏輯完全符合稅法。

---

### 2. 套用至現有業務邏輯

#### [MODIFY] [DefinedContributionContextBuilder.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java)
- **變更前**：使用 `Period.between(resolvedDcEnrollmentDate, LocalDate.now()).getYears()` 初始化。若今天是加入週年紀念日，會算出剛好的整數年（零個月零天），導致當天少算一年的免稅額。
- **變更後**：改為呼叫 `TaxDateUtils.calculateTaxServiceYears(resolvedDcEnrollmentDate, LocalDate.now())`。
- **影響範圍**：初始化 DC (第 64-65 行) 與 iDeCo (第 137-138 行) 的初始年資（勤続年數），確保在任何日子登入系統都能獲得精確且符合稅法包含頭尾 (Inclusive) 的進位年資。

#### [MODIFY] [YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
- **變更前**：使用 `Period.between(context.getDcEnrollmentDate(), LocalDate.of(context.getYear(), 12, 31)).getYears()`。若使用者恰好於 `12-31` 加入，會算出整數年（零個月零天）而不進位，導致在年底模擬時被少算一年免稅額。
- **變更後**：改為呼叫 `TaxDateUtils.calculateTaxServiceYears(context.getDcEnrollmentDate(), LocalDate.of(context.getYear(), 12, 31))`。
- **影響範圍**：模擬引擎執行時，每年針對 DC (第 163-165 行) 與 iDeCo (第 172-175 行) 的年資累加邏輯。確保跨年計算或特殊加入日的邊界條件不再發生 off-by-one 誤差。

---

### 3. 更新文件

#### [MODIFY] [issue-48.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/issue-48.md)
- 在實作完成後，於檔案最下方撰寫一份「給 Claude 的 Review 報告」。
- 報告內容需重點說明本次導入 `TaxDateUtils` 的用意，以及使用 `plusDays(1)` 處理 Java `Period.between` 半開區間 (Half-Open) 邊界條件的數學原因與修正邏輯，以供 Claude 進行最終代碼審查與驗證。

## Verification Plan

### Automated Tests
- 新增的 `TaxDateUtilsTest` 將驗證各種邊界日期的行為正確無誤。
- 原有的 Simulation Pipeline 及 Context Builder 的相關整合或單元測試（若有）不應因改用此工具方法而拋出異常。
- （需由 User）透過 IDE 執行整個專案的單元測試進行確認。

# Walkthrough: Fixed 92-01 (DC/iDeCo 服務年資進位修正)

我已經完成了 Fixed 92-01 的實作。以下是本次修復的詳細總結：

## 1. 建立共用工具類別與測試
- 新增了 [`TaxDateUtils.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/TaxDateUtils.java)。
  - 統一實作了 `calculateTaxServiceYears()` 方法。
  - 核心邏輯：使用 `endDate.plusDays(1)` 將 Java 原本的 半開區間 (Half-Open) 轉為 Inclusive 區間，徹底消除了遇到「12/31」或「加入週年紀念日」等邊界日期時，會因剛好滿整數年而少算一天的潛在 Bug。
  - 在計算出正確的日曆落差後，只要有不滿一年的零頭，就會觸發 `getYears() + 1` 的無條件進位，完美符合日本稅法「1年未満の端数は1年に切り上げます」的規定。
- 撰寫了完整的邊界條件單元測試 [`TaxDateUtilsTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/TaxDateUtilsTest.java)。

## 2. 替換既有業務邏輯
將以下兩處原先使用 `Period.between(...).getYears()` 直接捨去零頭的做法，全部重構為呼叫 `TaxDateUtils`：
- [`DefinedContributionContextBuilder.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java)：修正了初始化時（第 64、137 行）的 DC / iDeCo 年資算法。
- [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)：修正了模擬引擎執行時（第 163、172 行）每年的 DC / iDeCo 年資累加邏輯。

## 3. Claude Review 報告
- 已將專門為 Claude 撰寫的 Review 報告附加於 [`issue-48.md`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/issue-48.md) 的最下方，詳細說明了 `plusDays(1)` 與 Half-Open 的數學關係與修正方向。

> [!IMPORTANT]
> 由於 Workspace 嚴格的安全與測試規範，我並未執行編譯與測試腳本。請您親自透過 IDE 執行 `mvn clean test` 或是直接針對 `TaxDateUtilsTest` 進行單元測試的驗證，若有任何未通過的情況，請隨時通知我進行修正。
