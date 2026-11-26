# [Fixed] Phase 1 - 教育費用模型重構 (Domain Models)

本實作計畫針對 `Plan-01.md` 所定義的「階段一：核心領域概念重塑」進行規劃。
目標是建立全新的 `EducationConfig` 與 `ChildEducationSettings` 等資料結構，並放置於獨立的 package 中，**不修改任何現有的程式碼邏輯與舊有實體，確保相容性與邊界清晰。**

## User Review Required

> [!IMPORTANT]
> 1. 新增的 `EducationConfig` 為**獨立的 JPA Entity**，它會有自己的資料表，**絕對不會**放入或關聯到既有的 `FinanceHubConfig` 中。這能確保未來 UI 讀取教育設定時，不會拉出一大包不相關的系統設定。
> 2. `EducationConfig` 將比照 `FinanceHubConfig`，繼承 `AuditableEntity` 並包含 `name` 欄位以支援多組預設設定。
> 3. `ChildEducationSettings` 為獨立的 JPA Entity，與既有的 `ChildProfile` 形成一對一 (`@OneToOne`) 的關聯。
> 4. 各階段與軌道的細部設定將作為 `@Embeddable` Value Objects 實作。
> 5. 所有舊有欄位（`FinanceHubConfig` 與 `ChildProfile` 內的相關欄位）在本次實作中皆維持原封不動。
> 6. 金額相關欄位（包含全域預設費與個人覆寫費）全面採用 `BigDecimal` 以確保後續除法與累加計算的精確度。

## Proposed Changes

所有新增檔案皆位於以下兩個子套件中：
- 全域設定：`ch.lin.finance.hub.backend.api.domain.model.config.education`
- 個別覆寫：`ch.lin.finance.hub.backend.api.domain.model.profile.child.education`

### 1. 全域預設設定 (Global Defaults)

建立獨立的 `EducationConfig` 實體及其階層式的 `@Embeddable` 配置。

#### [NEW] [`TrackConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java)
- 作為 `@Embeddable` Value Object。
- 包含欄位：`tuition` (基準學費/月)、`duration` (基準就讀時間/月)、`startMonth` (基準開學月份)、`termsPerYear` (預設收費期數, 預設為2)、`entranceFee` (一次性入學金)。
- 金額相關欄位（`tuition`, `entranceFee`）採用 `BigDecimal`。

#### [NEW] [`PreschoolConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java)
- `@Embeddable`，包含 `homeTrack`, `daycareTrack`, `intlTrack` (皆為 `TrackConfig`)。

#### [NEW] [`PrimaryConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java)
- `@Embeddable`，包含 `publicTrack`, `privateTrack`, `intlTrack`。

#### [NEW] [`MiddleConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java)
- `@Embeddable`，包含 `publicTrack`, `privateTrack`, `intlTrack`，以及額外預設費 `jukuCost` (補習費, BigDecimal)。

#### [NEW] [`HighConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java)
- `@Embeddable`，包含 `publicTrack`, `privateTrack`, `intlTrack`，以及額外預設費 `jukuCost` (補習費, BigDecimal)。

#### [NEW] [`UniversityConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java)
- `@Embeddable`，包含 `jpNationalTrack`, `jpPrivateTrack`, `jpPrivateArtTrack`, `twNationalTrack`, `twPrivateTrack`, `usTrack`, `ukTrack`, `canadaTrack`, `australiaTrack`，以及留學生活費 `livingCost` (BigDecimal)。

#### [NEW] [`EducationConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/EducationConfig.java)
- `@Entity`，代表全域教育設定，**完全獨立於 `FinanceHubConfig`**。
- 繼承 `AuditableEntity`，帶有自己的 `ID` 與 `name` 欄位。
- 包含上述 `PreschoolConfig`, `PrimaryConfig`, `MiddleConfig`, `HighConfig`, `UniversityConfig`。

---

### 2. 個人化客製與覆寫 (Personal Overrides)

建立 `ChildEducationSettings` 及其相關的覆寫值物件。

#### [NEW] [`StageOverride.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/StageOverride.java)
- 作為 `@Embeddable` Value Object。
- 包含欄位：`manualTuition` (BigDecimal), `manualDuration`, `manualStartMonth`, `manualTermsPerYear`。所有欄位皆可為 null (代表未覆寫)。

#### [NEW] [`ChildEducationSettings.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)
- `@Entity`，與 `ChildProfile` 形成關聯 (透過 `@OneToOne` 或直接記錄 `childProfileId`)。
- 包含各階段軌道選擇 (`preschoolTrack`, `primaryTrack` 等 Enum，使用現有 Enum 或未來搬移)。
- 各階段的 `StageOverride` 覆寫設定。
- 中、高中的 `manualJukuCost` (BigDecimal)。
- 大學階段特有的 `manualLivingCost` (BigDecimal) 與 `isAwayFromHome` (Boolean)。

## Verification Plan

### Automated Tests
- 本階段不涉及既有邏輯變更，因此無需修改現有業務邏輯測試。
- 確認所有新類別編譯通過，且 JPA 映射無錯誤 (如 `@Embeddable` 嵌套、資料表欄位長度限制等)。

### Manual Verification
- 啟動後端應用程式，確認 Spring Data JPA 成功在資料庫建立對應的新表 (`finance_education_config` 與 `finance_child_education_settings`) 與所有欄位。
- 檢查舊有的 API 與試算邏輯皆未受影響。


# Phase 1 Implementation Walkthrough

我們已經完成了 `Plan-01.md` 所定義的「階段一：核心領域概念重塑」的所有領域模型（Domain Model）開發。

> [!NOTE]
> 本次修改僅涉及新增類別，完全不動舊有的 `FinanceHubConfig` 與 `ChildProfile`，確保舊有系統能繼續運作。所有的類別都已配置 JPA 註解，並依照規畫使用了 `BigDecimal` 處理費用。

## 完成的實作項目

### 1. 全域教育設定 (`ch.lin.finance.hub.backend.api.domain.model.config.education`)

建立了一個獨立的 JPA 實體來存放全域教育預設配置：
- [`EducationConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/EducationConfig.java) (獨立 `@Entity`，帶有 `name` 欄位並繼承 `AuditableEntity`)
- 階層化設定 (Value Objects)：
  - [`TrackConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java) (底層通用，金額均使用 `BigDecimal`)
  - [`PreschoolConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java)
  - [`PrimaryConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java)
  - [`MiddleConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java)
  - [`HighConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java)
  - [`UniversityConfig.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java)

這些類別使用 `@AttributeOverrides` 來解決資料表欄位名稱碰撞的問題。

### 2. 個人客製化設定 (`ch.lin.finance.hub.backend.api.domain.model.profile.child.education`)

建立了一個專門綁定單一小孩的設定實體：
- [`ChildEducationSettings.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java) (`@Entity`，紀錄了對應的 `childProfileId`)
- [`StageOverride.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/StageOverride.java) (作為覆寫用的 Value Object，金額採用 `BigDecimal`)

## 後續動作

目前所有的程式碼均已透過 IDE 生成。根據您的嚴格規定，我必須等待您在 IDE 中進行 Diff 審查。當您確認 (Approve) 無誤後，我們才會接著更新 `PROGRESS.md`，為本次任務收尾。
