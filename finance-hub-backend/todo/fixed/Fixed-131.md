# [Fixed] Feature Requirement 131：大學階段生活費（Living Cost）按國家/地區提供獨立預設值，支援依升學軌道自動帶入合理的海外生活基準

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板：大學生活費目前在全系統共用單一預設值（約 125,000 円/月），對不同國家的留學規劃非常不親切（例如去美國留學與在台灣或日本外宿的生活開銷差異巨大）。需要擴充大學生活費配置模型，讓系統能夠根據孩子選定的升學軌道（`UniversityTrack`）所屬國家/地區，提供對應的預設月生活費，並在前端提供更直觀友善的建議提示。

---

## 背景與現狀分析

### 1. 資料庫儲存方式查證（非獨立表格）
經查證後端資料模型，生活費目前**並非存在獨立表格**，而是作為嵌入欄位依附於既有實體中：
- **全域預設值**：儲存在 `finance_education_config` 表中，欄位為 `uni_living_cost`（由實體 `UniversityConfig` 內嵌於 `EducationConfig`），透過 `application.properties` / `application-h2.properties` 載入單一數值（目前設定為 125,000 JPY/月）。
- **個別孩子手動覆寫**：儲存在 `finance_child_education_settings` 表中，欄位為 `uni_manual_living_cost`（手動月生活費）與 `uni_is_away_from_home`（是否外宿/出國）。

### 2. 現行解析問題（`EducationProfileResolver.java`）
在 `EducationProfileResolver.resolveUniversityStage` 中：
```java
// 目前不分國家，所有軌道共用同一個 defaultLivingCost
if (config.getUniversity().getLivingCost() != null) {
    defaultLivingCost = config.getUniversity().getLivingCost();
}

BigDecimal livingCost = BigDecimal.ZERO;
if (settings != null && settings.getManualLivingCost() != null) {
    livingCost = settings.getManualLivingCost();
} else if (settings != null && Boolean.TRUE.equals(settings.getIsAwayFromHome())) {
    livingCost = defaultLivingCost; // 無論選 US、UK、JP 或 TW，只要外宿通通套用 125,000 JPY
}
```

### 3. 現實落差與使用者體驗痛點
`UniversityTrack` 目前涵蓋以下國家與地區：
- 日本：`JP_NATIONAL`、`JP_PRIVATE`、`JP_PRIVATE_ART`（生活費基準約 120,000 ~ 150,000 JPY/月）
- 台灣：`TW_NATIONAL`、`TW_PRIVATE`（生活費基準約 50,000 ~ 80,000 JPY/月）
- 美國：`US`（生活費基準約 250,000 ~ 350,000 JPY/月，約 1,600 ~ 2,300 USD）
- 英國：`UK`（生活費基準約 220,000 ~ 320,000 JPY/月，約 1,100 ~ 1,600 GBP）
- 加拿大：`CANADA`（生活費基準約 180,000 ~ 260,000 JPY/月）
- 澳洲：`AUSTRALIA`（生活費基準約 200,000 ~ 280,000 JPY/月）

若全域統一採用單一 125,000 JPY/月：
- **留美/留英情境**：生活費會被嚴重低估 50% 以上（4 年累計低估超過 600 ~ 1,000 萬日圓）。若使用者不清楚各國實際生活水準、沒有手動填寫覆寫金額，模擬器產出的財務健全度將嚴重失真。
- **留台情境**：生活費則會被高估近一倍。

---

## 相關檔案

- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java`（定義大學階段配置實體，目前僅有單一欄位 `livingCost`）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/UniversityTrack.java`（升學軌道 Enum）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java`（解析大學軌道與生活費的核心解析器）
- `finance-hub/finance-hub-backend/hub/src/main/resources/application.properties` 及 `application-h2.properties`（配置各國預設生活費基準）
- 前端孩子教育設定畫面：`hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx` 及相關 DTO / Hook

---

## 想要的行為與設計方案

### 1. 資料模型演進（建議採「內嵌於 `UniversityConfig`」方案）
為了保持與既有 JPA 架構的一致性，並避免額外引入複雜關聯表格：
在 `UniversityConfig` 內針對支援的國家/地區定義獨立的月生活費欄位：
```java
@Column(name = "uni_jp_living_cost", precision = 19, scale = 4)
private BigDecimal jpLivingCost;

@Column(name = "uni_tw_living_cost", precision = 19, scale = 4)
private BigDecimal twLivingCost;

@Column(name = "uni_us_living_cost", precision = 19, scale = 4)
private BigDecimal usLivingCost;

@Column(name = "uni_uk_living_cost", precision = 19, scale = 4)
private BigDecimal ukLivingCost;

@Column(name = "uni_canada_living_cost", precision = 19, scale = 4)
private BigDecimal canadaLivingCost;

@Column(name = "uni_australia_living_cost", precision = 19, scale = 4)
private BigDecimal australiaLivingCost;
```
*(原有欄位 `uni_living_cost` 可保留作為預設 fallback)*

### 2. 屬性檔預設值配置 (`application.properties`)
提供切合現況的合理各國月生活費基準（以日圓計價）：
```properties
# Education University Default Living Costs (Monthly, in JPY)
finance-hub.education-config.university.living-cost.japan=130000
finance-hub.education-config.university.living-cost.taiwan=70000
finance-hub.education-config.university.living-cost.us=280000
finance-hub.education-config.university.living-cost.uk=250000
finance-hub.education-config.university.living-cost.canada=220000
finance-hub.education-config.university.living-cost.australia=230000
```

### 3. 解析器動態對齊國別生活費 (`EducationProfileResolver.java`)
當計算 `isAwayFromHome = true` 時，依據選取的軌道取得對應國家的預設月生活費：
```java
BigDecimal defaultLivingCost = switch (track) {
    case JP_NATIONAL, JP_PRIVATE, JP_PRIVATE_ART -> config.getUniversity().getJpLivingCost();
    case TW_NATIONAL, TW_PRIVATE -> config.getUniversity().getTwLivingCost();
    case US -> config.getUniversity().getUsLivingCost();
    case UK -> config.getUniversity().getUkLivingCost();
    case CANADA -> config.getUniversity().getCanadaLivingCost();
    case AUSTRALIA -> config.getUniversity().getAustraliaLivingCost();
    case NONE -> BigDecimal.ZERO;
};
if (defaultLivingCost == null) {
    defaultLivingCost = config.getUniversity().getLivingCost(); // fallback to generic
}
```

### 4. 前端 UI 親切度提升
- 在編輯孩子的教育偏好時，當使用者切換升學軌道（例如切換至「美國大學」）並勾選「外宿/留學」：
  - 輸入框 placeholder 或輔助文字動態更新為該國的官方預設月生活費（例如：「建議預設：約 ¥280,000 / 月，可手動修改」）。
  - 使用者如果不自訂，能清楚知道模擬器背後套用了哪一個水準的費用。

---

## 邊界與相依關係

1. **與 Issue 110 的邊界**：
   - [issue-110](issue-110.md) 的核心是將生活費計算模型轉成「純月費儲存與月級別分攤（`monthlyLivingCost * activeMonths`）」以及「移除補習費」。
   - 本任務（FR-131）屬於**資料維度與參數親切度的擴充**，建立在 Issue 110 完成的純月費架構之上，兩者職責分明，可作為 Issue 110 之後的獨立迭代進行。
2. **資料庫遷移考量**：
   - 專案採用 `spring.jpa.hibernate.ddl-auto=update`，新增欄位不會破壞既有資料庫紀錄，既有 `uni_living_cost` 可無痛相容。

---

## 階段性實作規劃（Phased Implementation Plan）

為確保改動品質、避免單次 Context Window 與變更範圍過大，本需求切分為 4 個獨立、具備可驗證性且可循序漸進的階段：

### 階段一：領域實體與預設配置基礎建設（Domain Model & Configuration Foundation）
- **核心目標**：在資料模型與配置層建立 6 個國別月生活費欄位，支援透過環境變數與 Properties 載入。
- **改動項目**：
  1. `finance-hub/.env.base`、`.env.dev`、`.env.prod`：新增 `FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_*_LIVING_COST` 變數。
  2. `application.properties`、`application-h2.properties`：配置 `finance-hub.education-config.university.*-living-cost` 預設值（JP: 130,000、TW: 70,000、US: 280,000、UK: 250,000、CANADA: 220,000、AUSTRALIA: 230,000，並保留通用 fallback: 125,000）。
  3. `UniversityConfig.java`：新增 6 個 `@Column` 內嵌欄位（`uni_jp_living_cost` 等）。
  4. `EducationConfigDefaultProperties.java`：`UniversityConfigProperties` 新增 6 個 `BigDecimal` 屬性。
  5. `EducationConfigFactory.java`：`createUniversityConfig` 補齊各欄位建構映射。
- **驗證方式**：執行後端編譯，確認 JPA Entity 與 Properties 綁定無誤。

### 階段二：應用服務結果與 Web API DTO / Mapper 串接（Service Results & API DTOs）
- **核心目標**：將 6 個國別月生活費延伸至 REST API 的查詢與更新端點。
- **改動項目**：
  1. `EducationConfigResult.java`：`UniversityConfigResult` 增加 6 個國別欄位與 `fromEntity` 轉換。
  2. `UniversityConfigRequest.java`：新增 6 個欄位並標註 `@PositiveOrZero` 驗證。
  3. `UniversityConfigResponse.java`：新增 6 個欄位對應回傳。
  4. `EducationConfigControllerTest.java`：更新 API 整合測試，驗證國別生活費正確包含於 Response 中。
- **驗證方式**：執行 `EducationConfigControllerTest`，確認 API 序列化與驗證均正常通過。

### 階段三：核心模擬解析引擎升級與單元測試（Simulation Engine Resolver & Unit Tests）
- **核心目標**：讓教育階段解析器在孩子選擇大學軌道時，能依據國家自動帶入對應的月生活費基準。
- **改動項目**：
  1. `EducationProfileResolver.java`：在 `resolveUniversity` 階段，依據孩子的 `UniversityTrack` 優先讀取對應國別生活費，若未設定則 fallback 至通用 `livingCost`。
  2. `EducationProfileResolverTest.java`：新增各國大學軌道（日本、美國、台灣、英國等）在 `isAwayFromHome = true` 且未手動覆寫時的生活費代入測試，以及手動覆寫優先權測試。
- **驗證方式**：執行 `EducationProfileResolverTest` 與完整模擬引擎測試，確認邏輯 100% 吻合預期。

### 階段四：前端 UI 型別與建議生活費提示增強（Frontend UI & Hint Enhancements）
- **核心目標**：前端編輯孩子教育偏好時，切換大學軌道能自動呈現該國官方預設生活費與建議提示。
- **改動項目**：
  1. `use-child-profile.ts`：`UniversityConfigDto` 擴充 6 個國別月生活費屬性。
  2. `child-profile-section.tsx`：當勾選「Away From Home」時，依選取的 `universityTrack` 即時動態呈現該國的預設月生活費提示（例如：「建議預設：約 ¥280,000 / 月（¥3,360,000 / 年），未填寫將自動套用此基準」）與 placeholder。
- **驗證方式**：前端 `npm run build` 通過，確認切換不同國別大學時提示動態正確更新。

---

## 階段一實作完成報告（Phase 1 Implementation Report）

### 一、實作狀態
- [x] **階段一已完成**：領域實體與預設配置基礎建設（Domain Model & Configuration Foundation）已完成。6 個國別月生活費已成功引入環境設定檔（`.env.base`, `.env.dev`, `.env.prod`）、Spring 屬性檔（`application.properties`, `application-h2.properties`）、實體模型 `UniversityConfig`、屬性綁定物件 `UniversityConfigProperties` 以及實體建構工廠 `EducationConfigFactory`。

### 二、改動檔案清單
1. **環境變數設定**：
   - `finance-hub/.env.base`
   - `finance-hub/.env.dev`
   - `finance-hub/.env.prod`
   - 新增：`JP_LIVING_COST="130000"`、`TW_LIVING_COST="70000"`、`US_LIVING_COST="280000"`、`UK_LIVING_COST="250000"`、`CANADA_LIVING_COST="220000"`、`AUSTRALIA_LIVING_COST="230000"`，並保留 `LIVING_COST="125000"` 作為通用 fallback。
2. **Spring 全域屬性配置**：
   - `finance-hub-backend/hub/src/main/resources/application.properties`
   - `finance-hub-backend/hub/src/main/resources/application-h2.properties`
   - 新增：`finance-hub.education-config.university.*-living-cost` 6 組國別月生活費。
3. **領域模型與實體**：
   - **[UniversityConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java#L136-L177)**：新增 `jpLivingCost`, `twLivingCost`, `usLivingCost`, `ukLivingCost`, `canadaLivingCost`, `australiaLivingCost` 6 個 `@Column(name = "uni_*_living_cost", precision = 19, scale = 4)` 欄位與 Javadoc。
4. **設定屬性與建構工廠**：
   - **[EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java#L120-L127)**：`UniversityConfigProperties` 新增 6 個 `BigDecimal` 屬性。
   - **[EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java#L87-L94)**：`createUniversityConfig` 補齊這 6 個欄位的映射。

## Diff

```diff
```

---

## 階段二實作完成報告（Phase 2 Implementation Report）

### 一、實作狀態
- [x] **階段二已完成**：應用服務結果與 Web API DTO / Mapper 串接（Service Results & API DTOs）。6 個國別月生活費（`jpLivingCost`, `twLivingCost`, `usLivingCost`, `ukLivingCost`, `canadaLivingCost`, `australiaLivingCost`）已完整串接至 `EducationConfigResult.UniversityConfigResult`、請求 DTO `UniversityConfigRequest`（包含 `@PositiveOrZero` 參數驗證）、回應 DTO `UniversityConfigResponse`，並補強了 Controller 端負數生活費驗證的單元測試。

### 二、改動檔案清單
1. **服務層結果物件**：
   - **[EducationConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java#L159-L200)**：`UniversityConfigResult` 增加 6 個國別月生活費欄位，並於 `fromEntity()` 中正確映射。
2. **Web API 請求與回應 DTO**：
   - **[UniversityConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java#L40-L60)**：新增 6 個國別月生活費欄位，並加上 `@PositiveOrZero` 驗證註解。
   - **[UniversityConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigResponse.java#L28-L35)**：新增 6 個國別月生活費欄位供前端讀取。
3. **Controller 單元測試**：
   - **[EducationConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java#L213-L229)**：新增 `updateConfig_NegativeCountryLivingCost_ReturnsBadRequest` 測試案例，確認國別生活費小於 0 時會被 Validation 正確阻擋並回傳 400 `VALIDATION_FAILED`。

## Diff

```diff
```

---

## 階段三實作完成報告（Phase 3 Implementation Report）

### 一、實作狀態
- [x] **階段三已完成**：核心模擬解析引擎升級與單元測試（Simulation Engine Resolver & Unit Tests）。`EducationProfileResolver` 已升級大學階段生活費解析邏輯：當設定 `isAwayFromHome = true` 且未提供 `manualLivingCost` 時，會根據學生所選的 9 種大學軌道自動帶入對應國別生活費（日本國家/私立/私立美術 -> `jpLivingCost`；台灣國立/私立 -> `twLivingCost`；美國 -> `usLivingCost`；英國 -> `ukLivingCost`；加拿大 -> `canadaLivingCost`；澳洲 -> `australiaLivingCost`），若該國別生活費為 null 則平滑降級 fallback 至通用 `livingCost`。若有填寫 `manualLivingCost` 則享有最高優先權。

### 二、改動檔案清單
1. **模擬解析服務**：
   - **[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java#L215-L282)**：
     - 重構 `resolveUniversity`：改由 `resolveDefaultUniversityLivingCost(track, uniConfig)` 依軌道解析預設國別生活費。
     - 新增 `resolveDefaultUniversityLivingCost` 私有輔助方法：支援 9 種軌道對應 6 國生活費並具備通用 `livingCost` fallback 機制。
2. **單元測試套件**：
   - **[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java#L217-L225)**：
     - 在 `setUp()` 的 `defaultConfig` 補齊 6 國預設月生活費。
     - 更新既有測試 `shouldResolveCompleteEducationTrackWithMixedSettings` 之 UK 軌道生活費斷言為 250,000。
     - 新增 `shouldResolveCountrySpecificLivingCostWhenAwayFromHome` 完整測試 9 種大學軌道對應 6 國的生活費解析。
     - 新增 `shouldFallbackToGenericLivingCostWhenCountryLivingCostIsNull` 測試國別生活費未設定時 fallback 至通用生活費之行為。
     - 新增 `shouldResolveZeroLivingCostWhenNotAwayFromHome` 測試 `isAwayFromHome` 為 false 或 null 時生活費為 0 之行為。

## Diff

```diff
```

---

## 階段四實作完成報告（Phase 4 Implementation Report）

### 一、實作狀態
- [x] **階段四已完成**：前端 UI 型別與建議生活費提示增強（Frontend UI & Hint Enhancements）。前端介面在切換孩子大學教育軌道時，能即時依選取國家動態解析後端配置之官方基準生活費，並呈現對應的建議提示文字與輸入框 Placeholder。

### 二、改動檔案清單
1. **前端型別定義**：
   - **[use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts#L83-L100)**：`UniversityConfigDto` 擴充 6 個國別月生活費欄位（`jpLivingCost`, `twLivingCost`, `usLivingCost`, `ukLivingCost`, `canadaLivingCost`, `australiaLivingCost`）。
2. **孩子教育檔案設定元件**：
   - **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L294-L340)**：
     - 新增 `suggestedLivingCost` 記憶化計算：依據選取的 `universityTrack` 自動抓取對應國別生活費，若無則平滑 fallback 至通用基準生活費。
     - **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L851-L885)**：當勾選「Away From Home」時，生活費輸入框 Placeholder 動態顯示該國預設年/月額度；當未填寫覆寫值時，下方輔助提示明確顯示該國建議基準（如：`Suggested default: ≈ ¥280,000 / month (¥3,360,000 / year). Applied automatically if left blank.`），填寫覆寫值後則顯示手動換算之月額提示。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                 10045  12.73G   9.29    15:21:23  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index 859b250..a0f81f5 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -290,6 +290,55 @@ export function ChildProfileSection() {
     });
   };
 
+  // Dynamic suggested baseline living cost for the currently selected university track
+  const suggestedLivingCost = useMemo(() => {
+    const track = editingSettings?.universityTrack;
+    const uniConfig = educationConfig?.university;
+    if (!track || track === 'NONE' || !uniConfig) {
+      return null;
+    }
+
+    let monthly: number | null | undefined = null;
+    switch (track) {
+      case 'JP_NATIONAL':
+      case 'JP_PRIVATE':
+      case 'JP_PRIVATE_ART':
+        monthly = uniConfig.jpLivingCost;
+        break;
+      case 'TW_NATIONAL':
+      case 'TW_PRIVATE':
+        monthly = uniConfig.twLivingCost;
+        break;
+      case 'US':
+        monthly = uniConfig.usLivingCost;
+        break;
+      case 'UK':
+        monthly = uniConfig.ukLivingCost;
+        break;
+      case 'CANADA':
+        monthly = uniConfig.canadaLivingCost;
+        break;
+      case 'AUSTRALIA':
+        monthly = uniConfig.australiaLivingCost;
+        break;
+      default:
+        break;
+    }
+
+    if (monthly == null && uniConfig.livingCost != null) {
+      monthly = uniConfig.livingCost;
+    }
+
+    if (monthly == null || monthly <= 0) {
+      return null;
+    }
+
+    return {
+      monthly,
+      annual: monthly * 12,
+    };
+  }, [editingSettings?.universityTrack, educationConfig?.university]);
+
   // Live timeline calculation based on backend config & stage overrides
   const timelineInfo = useMemo(() => {
     if (!editingChild?.birthDate) {
@@ -800,11 +849,15 @@ export function ChildProfileSection() {
                           />
                         </div>
                         {editingSettings?.isAwayFromHome && (
-                          <div className="space-y-1 animate-in fade-in duration-200">
+                          <div className="space-y-1.5 animate-in fade-in duration-200">
                             <Label className="text-xs text-muted-foreground">Manual Living Cost (¥/year)</Label>
                             <Input
                               type="number"
-                              placeholder="e.g. 1500000"
+                              placeholder={
+                                suggestedLivingCost
+                                  ? `Default: ¥${suggestedLivingCost.annual.toLocaleString()} / year (¥${suggestedLivingCost.monthly.toLocaleString()} / month)`
+                                  : "e.g. 1500000"
+                              }
                               className="h-8 text-sm"
                               value={annualLivingCostInput}
                               onChange={(e) => {
@@ -819,11 +872,15 @@ export function ChildProfileSection() {
                                 } : null);
                               }}
                             />
-                            {annualLivingCostInput && !isNaN(Number(annualLivingCostInput)) && Number(annualLivingCostInput) > 0 && (
+                            {annualLivingCostInput && !isNaN(Number(annualLivingCostInput)) && Number(annualLivingCostInput) > 0 ? (
+                              <p className="text-[11px] text-muted-foreground">
+                                ≈ ¥{Math.ceil(Number(annualLivingCostInput) / 12).toLocaleString()} / month (Manual override)
+                              </p>
+                            ) : suggestedLivingCost ? (
                               <p className="text-[11px] text-muted-foreground">
-                                ≈ ¥{Math.ceil(Number(annualLivingCostInput) / 12).toLocaleString()} / month
+                                Suggested default: ≈ ¥{suggestedLivingCost.monthly.toLocaleString()} / month (¥{suggestedLivingCost.annual.toLocaleString()} / year). Applied automatically if left blank.
                               </p>
-                            )}
+                            ) : null}
                           </div>
                         )}
                       </div>
diff --git a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
index e5010c2..2513c7b 100644
--- a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
+++ b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
@@ -90,6 +90,12 @@ export interface UniversityConfigDto {
   ukTrack?: TrackConfigDto;
   canadaTrack?: TrackConfigDto;
   australiaTrack?: TrackConfigDto;
+  jpLivingCost?: number | null;
+  twLivingCost?: number | null;
+  usLivingCost?: number | null;
+  ukLivingCost?: number | null;
+  canadaLivingCost?: number | null;
+  australiaLivingCost?: number | null;
   livingCost?: number | null;
 }
 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend                                            10046  12.67G   9.08    15:21:31  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                    10047  12.66G   8.95    15:21:38  
diff --git a/.env.base b/.env.base
index e94e804e..8fcbcb74 100644
--- a/.env.base
+++ b/.env.base
@@ -387,5 +387,11 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
 
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_LIVING_COST="130000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_LIVING_COST="70000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_LIVING_COST="280000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_LIVING_COST="250000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_LIVING_COST="220000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_LIVING_COST="230000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="125000"
 
diff --git a/.env.dev b/.env.dev
index c40cfd30..c4f660f9 100644
--- a/.env.dev
+++ b/.env.dev
@@ -386,5 +386,11 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
 
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_LIVING_COST="130000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_LIVING_COST="70000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_LIVING_COST="280000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_LIVING_COST="250000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_LIVING_COST="220000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_LIVING_COST="230000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="125000"
 
diff --git a/.env.prod b/.env.prod
index a38a6c01..448c4020 100644
--- a/.env.prod
+++ b/.env.prod
@@ -388,5 +388,11 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
 
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_LIVING_COST="130000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_LIVING_COST="70000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_LIVING_COST="280000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_LIVING_COST="250000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_LIVING_COST="220000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_LIVING_COST="230000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_LIVING_COST="125000"
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
index b94cda30..8507a761 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
@@ -117,6 +117,12 @@ public class EducationConfigDefaultProperties {
         private TrackConfigProperty canadaTrack = new TrackConfigProperty();
         @Builder.Default
         private TrackConfigProperty australiaTrack = new TrackConfigProperty();
+        private BigDecimal jpLivingCost;
+        private BigDecimal twLivingCost;
+        private BigDecimal usLivingCost;
+        private BigDecimal ukLivingCost;
+        private BigDecimal canadaLivingCost;
+        private BigDecimal australiaLivingCost;
         private BigDecimal livingCost;
     }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
index c39b087b..9396554e 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
@@ -84,6 +84,12 @@ public class EducationConfigFactory {
                 .ukTrack(createTrackConfig(props.getUkTrack()))
                 .canadaTrack(createTrackConfig(props.getCanadaTrack()))
                 .australiaTrack(createTrackConfig(props.getAustraliaTrack()))
+                .jpLivingCost(props.getJpLivingCost())
+                .twLivingCost(props.getTwLivingCost())
+                .usLivingCost(props.getUsLivingCost())
+                .ukLivingCost(props.getUkLivingCost())
+                .canadaLivingCost(props.getCanadaLivingCost())
+                .australiaLivingCost(props.getAustraliaLivingCost())
                 .livingCost(props.getLivingCost())
                 .build();
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
index 32dcb444..bec3c188 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
@@ -167,6 +167,12 @@ public class EducationConfigResult {
         TrackConfigResult ukTrack;
         TrackConfigResult canadaTrack;
         TrackConfigResult australiaTrack;
+        BigDecimal jpLivingCost;
+        BigDecimal twLivingCost;
+        BigDecimal usLivingCost;
+        BigDecimal ukLivingCost;
+        BigDecimal canadaLivingCost;
+        BigDecimal australiaLivingCost;
         BigDecimal livingCost;
 
         public static UniversityConfigResult fromEntity(UniversityConfig entity) {
@@ -183,6 +189,12 @@ public class EducationConfigResult {
                     .ukTrack(TrackConfigResult.fromEntity(entity.getUkTrack()))
                     .canadaTrack(TrackConfigResult.fromEntity(entity.getCanadaTrack()))
                     .australiaTrack(TrackConfigResult.fromEntity(entity.getAustraliaTrack()))
+                    .jpLivingCost(entity.getJpLivingCost())
+                    .twLivingCost(entity.getTwLivingCost())
+                    .usLivingCost(entity.getUsLivingCost())
+                    .ukLivingCost(entity.getUkLivingCost())
+                    .canadaLivingCost(entity.getCanadaLivingCost())
+                    .australiaLivingCost(entity.getAustraliaLivingCost())
                     .livingCost(entity.getLivingCost())
                     .build();
         }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
index 10995aac..8d6e6385 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
@@ -36,6 +36,7 @@ import org.springframework.stereotype.Component;
 
 import ch.lin.finance.hub.backend.api.domain.model.config.education.EducationConfig;
 import ch.lin.finance.hub.backend.api.domain.model.config.education.TrackConfig;
+import ch.lin.finance.hub.backend.api.domain.model.config.education.UniversityConfig;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.HighTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.MiddleTrack;
 import ch.lin.finance.hub.backend.api.domain.model.profile.child.PreschoolTrack;
@@ -221,46 +222,64 @@ public class EducationProfileResolver {
         }
 
         TrackConfig trackConfig = null;
-        BigDecimal defaultLivingCost = BigDecimal.ZERO;
-        if (config != null && config.getUniversity() != null) {
+        UniversityConfig uniConfig = config != null ? config.getUniversity() : null;
+        if (uniConfig != null) {
             trackConfig = switch (track) {
                 case JP_NATIONAL ->
-                    config.getUniversity().getJpNationalTrack();
+                    uniConfig.getJpNationalTrack();
                 case JP_PRIVATE ->
-                    config.getUniversity().getJpPrivateTrack();
+                    uniConfig.getJpPrivateTrack();
                 case JP_PRIVATE_ART ->
-                    config.getUniversity().getJpPrivateArtTrack();
+                    uniConfig.getJpPrivateArtTrack();
                 case TW_NATIONAL ->
-                    config.getUniversity().getTwNationalTrack();
+                    uniConfig.getTwNationalTrack();
                 case TW_PRIVATE ->
-                    config.getUniversity().getTwPrivateTrack();
+                    uniConfig.getTwPrivateTrack();
                 case US ->
-                    config.getUniversity().getUsTrack();
+                    uniConfig.getUsTrack();
                 case UK ->
-                    config.getUniversity().getUkTrack();
+                    uniConfig.getUkTrack();
                 case CANADA ->
-                    config.getUniversity().getCanadaTrack();
+                    uniConfig.getCanadaTrack();
                 case AUSTRALIA ->
-                    config.getUniversity().getAustraliaTrack();
+                    uniConfig.getAustraliaTrack();
                 case NONE ->
                     null;
             };
-            if (config.getUniversity().getLivingCost() != null) {
-                defaultLivingCost = config.getUniversity().getLivingCost();
-            }
         }
 
         BigDecimal livingCost = BigDecimal.ZERO;
         if (settings != null && settings.getManualLivingCost() != null) {
             livingCost = settings.getManualLivingCost();
         } else if (settings != null && Boolean.TRUE.equals(settings.getIsAwayFromHome())) {
-            livingCost = defaultLivingCost;
+            livingCost = resolveDefaultUniversityLivingCost(track, uniConfig);
         }
 
         StageOverride override = settings != null ? settings.getUniversityOverride() : null;
         return mergeStage(EducationStage.UNIVERSITY, track.name(), trackConfig, override, livingCost);
     }
 
+    private BigDecimal resolveDefaultUniversityLivingCost(UniversityTrack track, UniversityConfig uniConfig) {
+        if (uniConfig == null || track == null || track == UniversityTrack.NONE) {
+            return BigDecimal.ZERO;
+        }
+
+        BigDecimal countryLivingCost = switch (track) {
+            case JP_NATIONAL, JP_PRIVATE, JP_PRIVATE_ART -> uniConfig.getJpLivingCost();
+            case TW_NATIONAL, TW_PRIVATE -> uniConfig.getTwLivingCost();
+            case US -> uniConfig.getUsLivingCost();
+            case UK -> uniConfig.getUkLivingCost();
+            case CANADA -> uniConfig.getCanadaLivingCost();
+            case AUSTRALIA -> uniConfig.getAustraliaLivingCost();
+            case NONE -> null;
+        };
+
+        if (countryLivingCost != null) {
+            return countryLivingCost;
+        }
+        return uniConfig.getLivingCost() != null ? uniConfig.getLivingCost() : BigDecimal.ZERO;
+    }
+
     private ResolvedStageData mergeStage(
             EducationStage stage,
             String trackName,
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
index 4e30761e..3faf11a5 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
@@ -214,6 +214,12 @@ class EducationProfileResolverTest {
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
                                 .build())
+                        .jpLivingCost(new BigDecimal("130000"))
+                        .twLivingCost(new BigDecimal("70000"))
+                        .usLivingCost(new BigDecimal("280000"))
+                        .ukLivingCost(new BigDecimal("250000"))
+                        .canadaLivingCost(new BigDecimal("220000"))
+                        .australiaLivingCost(new BigDecimal("230000"))
                         .livingCost(new BigDecimal("125000"))
                         .build())
                 .build();
@@ -390,7 +396,7 @@ class EducationProfileResolverTest {
         assertThat(uni.getStartMonth()).isEqualTo(9);
         assertThat(uni.getTermsPerYear()).isEqualTo(3);
         assertThat(uni.getPaymentMonths()).containsExactly(9, 1, 5);
-        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("125000");
+        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("250000");
     }
 
     @Test
@@ -409,6 +415,133 @@ class EducationProfileResolverTest {
         assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("100000");
     }
 
+    @Test
+    @DisplayName("Should resolve country-specific living cost for all tracks when away from home")
+    void shouldResolveCountrySpecificLivingCostWhenAwayFromHome() {
+        // Japan National: 130,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.JP_NATIONAL)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("130000");
+
+        // Japan Private: 130,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.JP_PRIVATE)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("130000");
+
+        // Japan Private Art: 130,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.JP_PRIVATE_ART)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("130000");
+
+        // Taiwan National: 70,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.TW_NATIONAL)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("70000");
+
+        // Taiwan Private: 70,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.TW_PRIVATE)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("70000");
+
+        // US: 280,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.US)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("280000");
+
+        // UK: 250,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.UK)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("250000");
+
+        // Canada: 220,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.CANADA)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("220000");
+
+        // Australia: 230,000
+        assertThat(resolver.resolve(ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.AUSTRALIA)
+                .isAwayFromHome(true).build(), defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo("230000");
+    }
+
+    @Test
+    @DisplayName("Should fallback to generic living cost when country-specific living cost is null")
+    void shouldFallbackToGenericLivingCostWhenCountryLivingCostIsNull() {
+        UniversityConfig customUni = UniversityConfig.builder()
+                .usTrack(defaultConfig.getUniversity().getUsTrack())
+                .livingCost(new BigDecimal("125000"))
+                // usLivingCost is null
+                .build();
+        EducationConfig customConfig = EducationConfig.builder()
+                .name("custom")
+                .preschool(defaultConfig.getPreschool())
+                .primary(defaultConfig.getPrimary())
+                .middle(defaultConfig.getMiddle())
+                .high(defaultConfig.getHigh())
+                .university(customUni)
+                .build();
+
+        ChildEducationSettings settings = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.US)
+                .isAwayFromHome(true)
+                .build();
+
+        ResolvedEducationProfile profile = resolver.resolve(settings, customConfig);
+        ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);
+
+        assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo("125000");
+    }
+
+    @Test
+    @DisplayName("Should resolve zero living cost when awayFromHome is false or null and no manual override")
+    void shouldResolveZeroLivingCostWhenNotAwayFromHome() {
+        ChildEducationSettings settingsFalse = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.US)
+                .isAwayFromHome(false)
+                .build();
+        assertThat(resolver.resolve(settingsFalse, defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo(BigDecimal.ZERO);
+
+        ChildEducationSettings settingsNull = ChildEducationSettings.builder()
+                .childProfileId(1L)
+                .universityTrack(UniversityTrack.US)
+                .isAwayFromHome(null)
+                .build();
+        assertThat(resolver.resolve(settingsNull, defaultConfig)
+                .getStage(EducationStage.UNIVERSITY).getMonthlyLivingCost())
+                .isEqualByComparingTo(BigDecimal.ZERO);
+    }
+
     @Test
     @DisplayName("Should resolve default config for JP_PRIVATE university track")
     void shouldResolveJpPrivateUniversityTrack() {
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
index 1661869e..8d13f6db 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
@@ -133,9 +133,45 @@ public class UniversityConfig {
     })
     private TrackConfig australiaTrack;
 
+    /**
+     * Standard baseline living cost in JPY per MONTH for students studying in Japan (away from home).
+     */
+    @Column(name = "uni_jp_living_cost", precision = 19, scale = 4)
+    private BigDecimal jpLivingCost;
+
+    /**
+     * Standard baseline living cost in JPY per MONTH for students studying in Taiwan (away from home).
+     */
+    @Column(name = "uni_tw_living_cost", precision = 19, scale = 4)
+    private BigDecimal twLivingCost;
+
+    /**
+     * Standard baseline living cost in JPY per MONTH for students studying abroad in the United States.
+     */
+    @Column(name = "uni_us_living_cost", precision = 19, scale = 4)
+    private BigDecimal usLivingCost;
+
+    /**
+     * Standard baseline living cost in JPY per MONTH for students studying abroad in the United Kingdom.
+     */
+    @Column(name = "uni_uk_living_cost", precision = 19, scale = 4)
+    private BigDecimal ukLivingCost;
+
+    /**
+     * Standard baseline living cost in JPY per MONTH for students studying abroad in Canada.
+     */
+    @Column(name = "uni_canada_living_cost", precision = 19, scale = 4)
+    private BigDecimal canadaLivingCost;
+
+    /**
+     * Standard baseline living cost in JPY per MONTH for students studying abroad in Australia.
+     */
+    @Column(name = "uni_australia_living_cost", precision = 19, scale = 4)
+    private BigDecimal australiaLivingCost;
+
     /**
      * Standard baseline living cost in JPY per MONTH for students studying abroad or living away from home
-     * (e.g., ~125,000 JPY/month; configured via application properties and stored on a monthly basis).
+     * (generic fallback e.g., ~125,000 JPY/month; configured via application properties and stored on a monthly basis).
      */
     @Column(name = "uni_living_cost", precision = 19, scale = 4)
     private BigDecimal livingCost;
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java
index 4118d8eb..c4267bbe 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java
@@ -37,6 +37,24 @@ public class UniversityConfigRequest {
     @Valid
     private TrackConfigRequest australiaTrack;
 
+    @PositiveOrZero(message = "jpLivingCost must be greater than or equal to 0")
+    private BigDecimal jpLivingCost;
+
+    @PositiveOrZero(message = "twLivingCost must be greater than or equal to 0")
+    private BigDecimal twLivingCost;
+
+    @PositiveOrZero(message = "usLivingCost must be greater than or equal to 0")
+    private BigDecimal usLivingCost;
+
+    @PositiveOrZero(message = "ukLivingCost must be greater than or equal to 0")
+    private BigDecimal ukLivingCost;
+
+    @PositiveOrZero(message = "canadaLivingCost must be greater than or equal to 0")
+    private BigDecimal canadaLivingCost;
+
+    @PositiveOrZero(message = "australiaLivingCost must be greater than or equal to 0")
+    private BigDecimal australiaLivingCost;
+
     @PositiveOrZero(message = "livingCost must be greater than or equal to 0")
     private BigDecimal livingCost;
 }
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigResponse.java
index cd166ade..99630fa8 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigResponse.java
@@ -25,5 +25,11 @@ public class UniversityConfigResponse {
     private TrackConfigResponse ukTrack;
     private TrackConfigResponse canadaTrack;
     private TrackConfigResponse australiaTrack;
+    private BigDecimal jpLivingCost;
+    private BigDecimal twLivingCost;
+    private BigDecimal usLivingCost;
+    private BigDecimal ukLivingCost;
+    private BigDecimal canadaLivingCost;
+    private BigDecimal australiaLivingCost;
     private BigDecimal livingCost;
 }
diff --git a/finance-hub-backend/hub/src/main/resources/application-h2.properties b/finance-hub-backend/hub/src/main/resources/application-h2.properties
index 28f88fbd..4bd9193e 100644
--- a/finance-hub-backend/hub/src/main/resources/application-h2.properties
+++ b/finance-hub-backend/hub/src/main/resources/application-h2.properties
@@ -431,6 +431,12 @@ finance-hub.education-config.university.australia-track.start-month=2
 finance-hub.education-config.university.australia-track.terms-per-year=2
 finance-hub.education-config.university.australia-track.entrance-fee=0
 
+finance-hub.education-config.university.jp-living-cost=130000
+finance-hub.education-config.university.tw-living-cost=70000
+finance-hub.education-config.university.us-living-cost=280000
+finance-hub.education-config.university.uk-living-cost=250000
+finance-hub.education-config.university.canada-living-cost=220000
+finance-hub.education-config.university.australia-living-cost=230000
 finance-hub.education-config.university.living-cost=125000
 
 
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index 58a744ed..9d72ecd7 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -425,5 +425,11 @@ finance-hub.education-config.university.australia-track.start-month=2
 finance-hub.education-config.university.australia-track.terms-per-year=2
 finance-hub.education-config.university.australia-track.entrance-fee=0
 
+finance-hub.education-config.university.jp-living-cost=130000
+finance-hub.education-config.university.tw-living-cost=70000
+finance-hub.education-config.university.us-living-cost=280000
+finance-hub.education-config.university.uk-living-cost=250000
+finance-hub.education-config.university.canada-living-cost=220000
+finance-hub.education-config.university.australia-living-cost=230000
 finance-hub.education-config.university.living-cost=125000
 
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java
index 09519607..a5c71580 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java
@@ -210,6 +210,24 @@ class EducationConfigControllerTest {
                 .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
     }
 
+    @Test
+    @DisplayName("Should return 400 when university country livingCost is negative")
+    void updateConfig_NegativeCountryLivingCost_ReturnsBadRequest() throws Exception {
+        UniversityConfigRequest university = UniversityConfigRequest.builder()
+                .usLivingCost(BigDecimal.valueOf(-500)) // Negative
+                .build();
+
+        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
+                .university(university)
+                .build();
+
+        mockMvc.perform(patch("/configs/education/default")
+                .contentType(MediaType.APPLICATION_JSON)
+                .content(objectMapper.writeValueAsString(request)))
+                .andExpect(status().isBadRequest())
+                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
+    }
+
     @Test
     @DisplayName("Should return 400 when createConfig has invalid startMonth")
     void createConfig_InvalidStartMonth_ReturnsBadRequest() throws Exception {
diff --git a/finance-hub-backend/todo/pending fix/feature-requirement-131.md b/finance-hub-backend/todo/pending fix/feature-requirement-131.md
index 9a4a2849..2b84ccb2 100644
--- a/finance-hub-backend/todo/pending fix/feature-requirement-131.md
+++ b/finance-hub-backend/todo/pending fix/feature-requirement-131.md
@@ -122,3 +122,141 @@ if (defaultLivingCost == null) {
    - 本任務（FR-131）屬於**資料維度與參數親切度的擴充**，建立在 Issue 110 完成的純月費架構之上，兩者職責分明，可作為 Issue 110 之後的獨立迭代進行。
 2. **資料庫遷移考量**：
    - 專案採用 `spring.jpa.hibernate.ddl-auto=update`，新增欄位不會破壞既有資料庫紀錄，既有 `uni_living_cost` 可無痛相容。
+
+---
+
+## 階段性實作規劃（Phased Implementation Plan）
+
+為確保改動品質、避免單次 Context Window 與變更範圍過大，本需求切分為 4 個獨立、具備可驗證性且可循序漸進的階段：
+
+### 階段一：領域實體與預設配置基礎建設（Domain Model & Configuration Foundation）
+- **核心目標**：在資料模型與配置層建立 6 個國別月生活費欄位，支援透過環境變數與 Properties 載入。
+- **改動項目**：
+  1. `finance-hub/.env.base`、`.env.dev`、`.env.prod`：新增 `FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_*_LIVING_COST` 變數。
+  2. `application.properties`、`application-h2.properties`：配置 `finance-hub.education-config.university.*-living-cost` 預設值（JP: 130,000、TW: 70,000、US: 280,000、UK: 250,000、CANADA: 220,000、AUSTRALIA: 230,000，並保留通用 fallback: 125,000）。
+  3. `UniversityConfig.java`：新增 6 個 `@Column` 內嵌欄位（`uni_jp_living_cost` 等）。
+  4. `EducationConfigDefaultProperties.java`：`UniversityConfigProperties` 新增 6 個 `BigDecimal` 屬性。
+  5. `EducationConfigFactory.java`：`createUniversityConfig` 補齊各欄位建構映射。
+- **驗證方式**：執行後端編譯，確認 JPA Entity 與 Properties 綁定無誤。
+
+### 階段二：應用服務結果與 Web API DTO / Mapper 串接（Service Results & API DTOs）
+- **核心目標**：將 6 個國別月生活費延伸至 REST API 的查詢與更新端點。
+- **改動項目**：
+  1. `EducationConfigResult.java`：`UniversityConfigResult` 增加 6 個國別欄位與 `fromEntity` 轉換。
+  2. `UniversityConfigRequest.java`：新增 6 個欄位並標註 `@PositiveOrZero` 驗證。
+  3. `UniversityConfigResponse.java`：新增 6 個欄位對應回傳。
+  4. `EducationConfigControllerTest.java`：更新 API 整合測試，驗證國別生活費正確包含於 Response 中。
+- **驗證方式**：執行 `EducationConfigControllerTest`，確認 API 序列化與驗證均正常通過。
+
+### 階段三：核心模擬解析引擎升級與單元測試（Simulation Engine Resolver & Unit Tests）
+- **核心目標**：讓教育階段解析器在孩子選擇大學軌道時，能依據國家自動帶入對應的月生活費基準。
+- **改動項目**：
+  1. `EducationProfileResolver.java`：在 `resolveUniversity` 階段，依據孩子的 `UniversityTrack` 優先讀取對應國別生活費，若未設定則 fallback 至通用 `livingCost`。
+  2. `EducationProfileResolverTest.java`：新增各國大學軌道（日本、美國、台灣、英國等）在 `isAwayFromHome = true` 且未手動覆寫時的生活費代入測試，以及手動覆寫優先權測試。
+- **驗證方式**：執行 `EducationProfileResolverTest` 與完整模擬引擎測試，確認邏輯 100% 吻合預期。
+
+### 階段四：前端 UI 型別與建議生活費提示增強（Frontend UI & Hint Enhancements）
+- **核心目標**：前端編輯孩子教育偏好時，切換大學軌道能自動呈現該國官方預設生活費與建議提示。
+- **改動項目**：
+  1. `use-child-profile.ts`：`UniversityConfigDto` 擴充 6 個國別月生活費屬性。
+  2. `child-profile-section.tsx`：當勾選「Away From Home」時，依選取的 `universityTrack` 即時動態呈現該國的預設月生活費提示（例如：「建議預設：約 ¥280,000 / 月（¥3,360,000 / 年），未填寫將自動套用此基準」）與 placeholder。
+- **驗證方式**：前端 `npm run build` 通過，確認切換不同國別大學時提示動態正確更新。
+
+---
+
+## 階段一實作完成報告（Phase 1 Implementation Report）
+
+### 一、實作狀態
+- [x] **階段一已完成**：領域實體與預設配置基礎建設（Domain Model & Configuration Foundation）已完成。6 個國別月生活費已成功引入環境設定檔（`.env.base`, `.env.dev`, `.env.prod`）、Spring 屬性檔（`application.properties`, `application-h2.properties`）、實體模型 `UniversityConfig`、屬性綁定物件 `UniversityConfigProperties` 以及實體建構工廠 `EducationConfigFactory`。
+
+### 二、改動檔案清單
+1. **環境變數設定**：
+   - `finance-hub/.env.base`
+   - `finance-hub/.env.dev`
+   - `finance-hub/.env.prod`
+   - 新增：`JP_LIVING_COST="130000"`、`TW_LIVING_COST="70000"`、`US_LIVING_COST="280000"`、`UK_LIVING_COST="250000"`、`CANADA_LIVING_COST="220000"`、`AUSTRALIA_LIVING_COST="230000"`，並保留 `LIVING_COST="125000"` 作為通用 fallback。
+2. **Spring 全域屬性配置**：
+   - `finance-hub-backend/hub/src/main/resources/application.properties`
+   - `finance-hub-backend/hub/src/main/resources/application-h2.properties`
+   - 新增：`finance-hub.education-config.university.*-living-cost` 6 組國別月生活費。
+3. **領域模型與實體**：
+   - **[UniversityConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java#L136-L177)**：新增 `jpLivingCost`, `twLivingCost`, `usLivingCost`, `ukLivingCost`, `canadaLivingCost`, `australiaLivingCost` 6 個 `@Column(name = "uni_*_living_cost", precision = 19, scale = 4)` 欄位與 Javadoc。
+4. **設定屬性與建構工廠**：
+   - **[EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java#L120-L127)**：`UniversityConfigProperties` 新增 6 個 `BigDecimal` 屬性。
+   - **[EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java#L87-L94)**：`createUniversityConfig` 補齊這 6 個欄位的映射。
+
+## Diff
+
+```diff
+```
+
+---
+
+## 階段二實作完成報告（Phase 2 Implementation Report）
+
+### 一、實作狀態
+- [x] **階段二已完成**：應用服務結果與 Web API DTO / Mapper 串接（Service Results & API DTOs）。6 個國別月生活費（`jpLivingCost`, `twLivingCost`, `usLivingCost`, `ukLivingCost`, `canadaLivingCost`, `australiaLivingCost`）已完整串接至 `EducationConfigResult.UniversityConfigResult`、請求 DTO `UniversityConfigRequest`（包含 `@PositiveOrZero` 參數驗證）、回應 DTO `UniversityConfigResponse`，並補強了 Controller 端負數生活費驗證的單元測試。
+
+### 二、改動檔案清單
+1. **服務層結果物件**：
+   - **[EducationConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java#L159-L200)**：`UniversityConfigResult` 增加 6 個國別月生活費欄位，並於 `fromEntity()` 中正確映射。
+2. **Web API 請求與回應 DTO**：
+   - **[UniversityConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigRequest.java#L40-L60)**：新增 6 個國別月生活費欄位，並加上 `@PositiveOrZero` 驗證註解。
+   - **[UniversityConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/UniversityConfigResponse.java#L28-L35)**：新增 6 個國別月生活費欄位供前端讀取。
+3. **Controller 單元測試**：
+   - **[EducationConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java#L213-L229)**：新增 `updateConfig_NegativeCountryLivingCost_ReturnsBadRequest` 測試案例，確認國別生活費小於 0 時會被 Validation 正確阻擋並回傳 400 `VALIDATION_FAILED`。
+
+## Diff
+
+```diff
+```
+
+---
+
+## 階段三實作完成報告（Phase 3 Implementation Report）
+
+### 一、實作狀態
+- [x] **階段三已完成**：核心模擬解析引擎升級與單元測試（Simulation Engine Resolver & Unit Tests）。`EducationProfileResolver` 已升級大學階段生活費解析邏輯：當設定 `isAwayFromHome = true` 且未提供 `manualLivingCost` 時，會根據學生所選的 9 種大學軌道自動帶入對應國別生活費（日本國家/私立/私立美術 -> `jpLivingCost`；台灣國立/私立 -> `twLivingCost`；美國 -> `usLivingCost`；英國 -> `ukLivingCost`；加拿大 -> `canadaLivingCost`；澳洲 -> `australiaLivingCost`），若該國別生活費為 null 則平滑降級 fallback 至通用 `livingCost`。若有填寫 `manualLivingCost` 則享有最高優先權。
+
+### 二、改動檔案清單
+1. **模擬解析服務**：
+   - **[EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java#L215-L282)**：
+     - 重構 `resolveUniversity`：改由 `resolveDefaultUniversityLivingCost(track, uniConfig)` 依軌道解析預設國別生活費。
+     - 新增 `resolveDefaultUniversityLivingCost` 私有輔助方法：支援 9 種軌道對應 6 國生活費並具備通用 `livingCost` fallback 機制。
+2. **單元測試套件**：
+   - **[EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java#L217-L225)**：
+     - 在 `setUp()` 的 `defaultConfig` 補齊 6 國預設月生活費。
+     - 更新既有測試 `shouldResolveCompleteEducationTrackWithMixedSettings` 之 UK 軌道生活費斷言為 250,000。
+     - 新增 `shouldResolveCountrySpecificLivingCostWhenAwayFromHome` 完整測試 9 種大學軌道對應 6 國的生活費解析。
+     - 新增 `shouldFallbackToGenericLivingCostWhenCountryLivingCostIsNull` 測試國別生活費未設定時 fallback 至通用生活費之行為。
+     - 新增 `shouldResolveZeroLivingCostWhenNotAwayFromHome` 測試 `isAwayFromHome` 為 false 或 null 時生活費為 0 之行為。
+
+## Diff
+
+```diff
+```
+
+---
+
+## 階段四實作完成報告（Phase 4 Implementation Report）
+
+### 一、實作狀態
+- [x] **階段四已完成**：前端 UI 型別與建議生活費提示增強（Frontend UI & Hint Enhancements）。前端介面在切換孩子大學教育軌道時，能即時依選取國家動態解析後端配置之官方基準生活費，並呈現對應的建議提示文字與輸入框 Placeholder。
+
+### 二、改動檔案清單
+1. **前端型別定義**：
+   - **[use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts#L83-L100)**：`UniversityConfigDto` 擴充 6 個國別月生活費欄位（`jpLivingCost`, `twLivingCost`, `usLivingCost`, `ukLivingCost`, `canadaLivingCost`, `australiaLivingCost`）。
+2. **孩子教育檔案設定元件**：
+   - **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L294-L340)**：
+     - 新增 `suggestedLivingCost` 記憶化計算：依據選取的 `universityTrack` 自動抓取對應國別生活費，若無則平滑 fallback 至通用基準生活費。
+     - **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L851-L885)**：當勾選「Away From Home」時，生活費輸入框 Placeholder 動態顯示該國預設年/月額度；當未填寫覆寫值時，下方輔助提示明確顯示該國建議基準（如：`Suggested default: ≈ ¥280,000 / month (¥3,360,000 / year). Applied automatically if left blank.`），填寫覆寫值後則顯示手動換算之月額提示。
+
+## Diff
+
+```diff
+```
+
+
+
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                         10048  12.65G   8.95    15:21:40  
