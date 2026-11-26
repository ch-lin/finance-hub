# [Fixed] Issue 134：DAYCARE軌道，前端即時預覽跟後端真實計算的入學年齡對不上

## 這份issue怎麼被發現的
這是在驗證[feature-requirement-125](feature-requirement-125.md)（幼稚園「前段HOME、後段換軌道」兩段式切換功能）實作完成度時，額外發現的問題。這個問題不是feature-requirement-125這次改動造成的新bug，是查核過程中順便發現的既有缺陷，獨立記錄在這裡。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:41`（`resolveTrackDefaults`函式裡，`preschool`這個case不管track是HOME、DAYCARE還是INTL，`standardEntranceAge`一律寫死`3`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java:46,143-144`（`DAYCARE_AGE = 1`這個常數；`calculateStandardStartYearMonth`裡，track名稱是`DAYCARE`時會用這個1歲常數，不是3歲）

## 現狀
前端畫面上即時預覽小孩的教育時間軸時，不管使用者選的是HOME、DAYCARE還是INTL軌道，起算年齡一律當作3歲去算。但後端真正模擬計算時，如果這個階段的track是`DAYCARE`，起算年齡是1歲，不是3歲。

## 為何是問題
使用者在編輯小孩教育設定的對話框裡，如果把幼稚園軌道選成「Daycare」，畫面上即時顯示的時間軸徽章（例如顯示「2028/04 ~ 2031/03」這種起訖年月），跟使用者按下「Estimate」或存檔後，後端真正模擬計算出來的時間軸，兩者會對不上——前端預覽永遠假設從3歲開始，後端實際是從1歲開始，整整差2年。使用者會看到畫面上顯示的試算結果，跟實際儲存後系統真正用來模擬的時間軸不一致，容易誤判小孩實際的入學排程。

## 這個落差是什麼時候出現的（已查證，不是這次新引入的）
已確認：`child-profile-section.tsx`裡`standardEntranceAge`寫死`3`這一行，在[Fixed-113](../fixed/Fixed-113.md)（前端時間軸重疊檢查改成非阻塞式提示）的diff裡就已經存在，Fixed-113當時改動的重點是「順延計算」跟「拿掉阻擋儲存按鈕」，完全沒有去動`standardEntranceAge`這個數字本身是否正確。這是一個比feature-requirement-125更早就存在的既有落差，不是這次新增的兩段式切換功能造成的，只是這次查核教育模組時被連帶發現。

## 建議修正
在`resolveTrackDefaults`的`preschool`這個case裡，比照後端`EducationTimelineProjector.java`的邏輯，針對`track === 'DAYCARE'`的情況，把`standardEntranceAge`也改成1，跟後端`DAYCARE_AGE`常數的數字一致。這是單純的數字校正，不是需要討論方向的設計選擇。

---

## 審查回饋與深度架構分析（為何不可照上述「建議修正」改前端為 1 歲）

經過系統架構與業務模型全面覆核，**上述「建議修正」方向存在嚴重缺陷，不可照做**。以下說明前因後果、實作風險與最終拍板之修正方案：

### 一、照原文修正的重大業務邏輯風險：產生 2 年「真空斷層（Vacuum Gap）」
1. **時長與學制嚴重脫節**：
   - 目前全系統（包括資料庫與 `application.properties`）中，幼兒園軌道（包含 DAYCARE）的標準就讀時長為 **36 個月（3 年）**。
   - 小學法定入學年齡為 **滿 6 歲（72 個月）**（見 `PRIMARY_AGE = 6`）。
   - 若照原建議將前端入學年齡改為 1 歲：
     $$\text{入學（1 歲）} + 36 \text{ 個月（3 年）} = \text{滿 4 歲結束就讀}$$
     但小學要到 6 歲才開始，這會導致 **4 歲至 6 歲之間完全沒有任何就讀階段與教育費用**，形成長達 2 年的「真空斷層（Vacuum Gap）」，直接違反 [Fixed-125](../fixed/Fixed-125.md) 明確定義的「避免時間軸產生真空斷層」之最高原則。
2. **UI 視覺自相矛盾**：
   - 前端卡片標題為 `Preschool (3-5)`（[child-profile-section.tsx#L673](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L673)），且內文明確說明為 `during preschool (ages 3–6)`（[child-profile-section.tsx#L1095](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L1095)）。
   - 若預覽徽章顯示 1 歲（如 1~4 歲），會與整張卡片的學制大標題直接打架。

---

### 二、前因後果與歷史事實澄清（非 Fixed-113 遺留，而是 Fixed-125 前後端同步遺漏）
原報告提及*「此落差在 Fixed-113 即存在，Fixed-125 未動過」*，經查證 Git Diff，**此考證有誤**：
1. **Fixed-125 之前的現狀**：前端原先確實設有 `standardEntranceAge = 1` 且對應 `duration: 60`（即 1 歲入學念 5 年剛好接 6 歲小學）。
2. **Fixed-125 的改動**：
   - Fixed-125 依據使用者決策，將幼兒園核心模型定調為「瓜分 3 年（36 個月），3 歲入學至 6 歲接小學」。
   - 因此 Fixed-125 在前端刪除了 `standardEntranceAge = 1` 與 60 個月，統一收斂為 3 歲與 36 個月。
   - 但在後端實作時，Fixed-125 僅在 [EducationTimelineProjector.java#L801-L806](../fixed/Fixed-125.md) 針對「兩段式」加上了 `if (stageData.hasStage2()) yield PRESCHOOL_AGE;`（兩段式回傳 3 歲），**卻漏掉了單軌 DAYCARE 還殘留著早期 Plan-01 的 `yield DAYCARE_AGE;` (1 歲)**。
3. **結論**：落差不是前端的問題，而是**後端殘留了舊常數**，甚至造成後端內部「單軌 DAYCARE 算 1 歲，兩段式 DAYCARE 算 3 歲」的邏輯分裂。

---

### 三、關於未滿 3 歲早讀（如 18 個月 / 24 個月）需求的分流
對於如 **Aoba-Japan International School 目黑校區** 幼幼部（滿 18 個月 / 1.5 歲 K2 早讀卡位）或認可保育園 1 歲送托等，需要以「月齡（`entranceAgeMonths`）」為精確刻度且「時長動態補滿至小學 6 歲（如 54 個月、60 個月）」的新學制需求：
- **已正式立案獨立規劃至 [Plan-13-early-childhood-entrance-age-months.md](../planned/Plan-13-early-childhood-entrance-age-months.md)**。
- 該需求包含月級計算與時長動態連動，屬於重大 Feature 擴充，不在本 Issue 134 混合處理。

---

### 四、拍板修正方案：後端收斂為單一真相來源（Single Source of Truth）

本 Issue 134 聚焦於「消除前後端數值分裂與硬寫常數」，採取以下解法：

#### 1. 後端修正（清除舊殘留）
- 修改 [EducationTimelineProjector.java#L137-L147](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java#L137-L147)：
  移除 `DAYCARE_AGE = 1` 舊分支，將 `PRESCHOOL` 階段的標準入學年齡統一為 `PRESCHOOL_AGE = 3`。
  - 單軌與兩段式在後端計算時皆為滿 3 歲入學。
  - 搭配 36 個月時長，無縫銜接滿 6 歲小學，徹底消滅 4~6 歲教育真空斷層。

#### 2. 架構升級：後端 API 統一發派學制年齡（消除前端 Hardcode）
- 目前前端各階段入學年齡（3、6、12、15、18 歲）寫死在 switch-case 裡。
- 在後端全域設定 API [TrackConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java) 擴充回傳 `standardEntranceAge`：
  - Preschool 各軌道：`3`
  - Primary 各軌道：`6`
  - Middle 各軌道：`12`
  - High 各軌道：`15`
  - University 各軌道：`18`
- 前端 [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx) 的 `resolveTrackDefaults` 改由 API 動態取得 `standardEntranceAge`，拔除前端的 hardcode 邏輯。

#### 3. 預期效益
1. 前端即時預覽與後端模擬計算 100% 精準吻合。
2. 保持幼兒園 3~6 歲連續學制，無真空斷層。
3. 前端學制參數全部由後端單一真相來源集中管理，未來維護與演進更具彈性。

---

## 五、分階段實作規劃（Phased Implementation Plan - 避免 Context Window 超載與幻覺）

由於本任務屬於**由內而外穿透整套乾淨架構（Vertical Slice）**的實作，跨越 `hub-domain` $\to$ `hub-app` $\to$ `hub` $\to$ `hub-ui` 四個子模組。為避免單次 Context Window 膨脹與注意力發散，採取「依相依順序推進、每階段封閉閉環、即時驗收」之 4 階段推進策略：

### 【階段一】領域實體與資料結構擴充（`hub-domain`）
- **核心目標**：在核心領域模型建立 `standardEntranceAge`，確保 JPA Embeddable 結構合法，成為領域層一級公民。
- **改動檔案**（集中在 `hub-domain`）：
  1. [TrackConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java)：新增 `@Column(name = "standard_entrance_age") private Integer standardEntranceAge;`。
  2. [PreschoolConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java)、[PrimaryConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java)、`MiddleConfig.java`、`HighConfig.java`、`UniversityConfig.java`：在每個 `@AttributeOverrides` 補齊對應軌道的 `*_standard_entrance_age` 欄位映射。
- **驗收標準**：`hub-domain` 編譯通過，JPA Entity 與 Embeddable 結構 100% 合法（專案依賴 `spring.jpa.hibernate.ddl-auto=update`，啟動時將自動 alter table 補齊對應欄位）。

---

### 【階段二】後台預設配置與核心計算引擎動態化（`hub-app`）
- **核心目標**：讓系統啟動時能透過 Properties 載入標準入學年齡，且投影計算器**徹底丟棄硬寫常數，直接從領域物件取值**。
- **改動檔案**（集中在 `hub-app` 與設定檔）：
  1. `finance-hub/.env.base`、`.env.dev`、`.env.prod` 以及 `application.properties` / `application-h2.properties`：補上各軌道的 `standard-entrance-age`（Preschool=3, Primary=6, Middle=12, High=15, University=18）。
  2. [EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java)：`TrackConfigProperty` 加上 `standardEntranceAge`。
  3. [EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java)：工廠將 Properties 映射入 `TrackConfig`。
  4. [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java) & [EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)：將 `standardEntranceAge` 解析進舞台數據。
  5. [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)：**拔除所有寫死常數與 `DAYCARE_AGE = 1` 舊邏輯**，統一改讀 `stageData.getStandardEntranceAge()`！
- **驗收標準**：執行 `EducationTimelineProjectorTest` 與 `EducationProfileResolverTest`，確認單軌與兩段式計算 100% 綠燈。

---

### 【階段三】REST 控制層與 DTO 派發（`hub`）
- **核心目標**：讓外部 API 能將領域層的 `standardEntranceAge` 序列化並回傳給前端。
- **改動檔案**（集中在 `hub`）：
  1. [TrackConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java) & [TrackConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java)：增加 `standardEntranceAge`。
  2. [EducationConfigMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/mapper/EducationConfigMapper.java)：更新映射邏輯。
- **驗收標準**：執行 `EducationConfigControllerTest`，MockMvc 驗證 `GET /configs/education` 正確吐出各軌道的入學年齡。

---

### 【階段四】前端 UI 動態接軌（`hub-ui`）
- **核心目標**：拔除前端的 hardcode switch-case，改吃後台領域資料。
- **改動檔案**（集中在 `hub-ui`）：
  1. [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)：`TrackConfigDto` 補上 `standardEntranceAge`。
  2. [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)：`resolveTrackDefaults` 改成優先使用 `trackConfig?.standardEntranceAge`。
- **驗收標準**：前端靜態型別檢查通過，預覽時間軸與後端真實模擬零誤差。

---

## 六、實作進度與修正報告（Progress & Fix Report）

### 1. 【階段一】領域實體與資料結構擴充（`hub-domain`）—— 已完成 (Completed)
- **領域實體升級**：
  - [TrackConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java)：
    新增 `@Column(name = "standard_entrance_age") private Integer standardEntranceAge;` 及其 Getter/Setter/Builder 屬性。
  - **JPA 欄位重寫映射（AttributeOverrides）完整對齊**：
    - [PreschoolConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java)：為 `homeTrack`、`daycareTrack`、`intlTrack` 補齊 `home_standard_entrance_age`、`daycare_standard_entrance_age`、`intl_standard_entrance_age`。
    - [PrimaryConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java)：為 `publicTrack`、`privateTrack`、`intlTrack` 補齊 `public_standard_entrance_age`、`private_standard_entrance_age`、`intl_standard_entrance_age`。
    - [MiddleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java)：為 `publicTrack`、`privateTrack`、`intlTrack` 補齊對應欄位映射。
    - [HighConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java)：為 `publicTrack`、`privateTrack`、`intlTrack` 補齊對應欄位映射。
    - [UniversityConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java)：為 9 條國別大學軌道（`jpNationalTrack`, `jpPrivateTrack`, `jpPrivateArtTrack`, `twNationalTrack`, `twPrivateTrack`, `usTrack`, `ukTrack`, `canadaTrack`, `australiaTrack`）完整補齊對應欄位映射。
- **單元測試建立**：
  - [TrackConfigTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfigTest.java)：建立實體建構與欄位存取之測試。

---

### 2. 【階段二】後台預設配置與核心計算引擎動態化（`hub-app`）—— 已完成 (Completed)
- **屬性配置與工廠模式動態化**：
  - [EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java)：在 `TrackConfigProperty` 新增 `standardEntranceAge`。
  - [EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java)：在 `createTrackConfig` 將屬性設定映射注入領域物件 `TrackConfig`。
- **計算引擎動態化與舊常數拔除**：
  - [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)：擴充 `standardEntranceAge` 欄位並啟用 `@Builder(toBuilder = true)`。
  - [EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)：在 `mergeStage` 將 `TrackConfig` 的 `standardEntranceAge` 帶入解析後的 `ResolvedStageData`。
  - [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)：
    - **正式刪除寫死常數 `DAYCARE_AGE = 1`**。
    - `getStandardEntranceAge` 升級為優先讀取 `stageData.getStandardEntranceAge()`，若無設定則 fallback 至階段預設基準年齡（`PRESCHOOL`: 3, `PRIMARY`: 6, `MIDDLE`: 12, `HIGH`: 15, `UNIVERSITY`: 18），幼教所有軌道統一預設為 3 歲，徹底杜絕 4~6 歲 2 年真空斷層。
- **環境設定檔與 Properties 全面擴充（5 檔同步補齊 21 條軌道）**：
  - [`.env.base`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.base)、[`.env.dev`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.dev)、[`.env.prod`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.prod)：加入各階段各軌道的 `FINANCE_HUB_EDUCATION_CONFIG_<STAGE>_<TRACK>_TRACK_STANDARD_ENTRANCE_AGE`。
  - [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)、[`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：加入 `finance-hub.education-config.<stage>.<track>-track.standard-entrance-age`。
  - 數值精確校準：Preschool（HOME=3, DAYCARE=3, INTL=3）、Primary（6）、Middle（12）、High（15）、University（18）。
- **單元測試更新與擴充**：
  - [EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)：更新 `setUp` 配置並驗證解析後的各階段 `standardEntranceAge` 正確無誤。
  - [EducationTimelineProjectorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java)：
    - 新增 `shouldProjectSingleTrackDaycareAtAge3ByDefault` 測試：確認單軌 DAYCARE 預設在滿 3 歲（2025-04）起算入學，而非舊有的 1 歲。
    - 新增 `shouldRespectDynamicStandardEntranceAgeFromStageData` 測試：確認自訂入學年齡時，時序起算點完全由該數值動態驅動。

---

### 3. 【階段三】REST 控制層與 DTO 派發（`hub`）—— 已完成 (Completed)
- **應用層結果與 DTO 欄位擴充**：
  - [EducationConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java)：
    在應用層結果 DTO `TrackConfigResult` 增加 `Integer standardEntranceAge`，並於 `fromEntity` 完整映射 `entity.getStandardEntranceAge()`。
  - [TrackConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java)：
    新增 `private Integer standardEntranceAge;` 欄位，自動讓所有階段（Preschool, Primary, Middle, High, University）之各軌道的回傳 JSON 均包含此屬性。
  - [TrackConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java)：
    新增 `private Integer standardEntranceAge;` 並加上 `@Min(0)` 與 `@Max(30)` 參數驗證，確保 API 雙向設定之健全性。
- **Command 與寫入資料庫流程澄清（方案 B）**：
  - **Controller 嚴守 Clean Architecture，絕不使用 Domain**：
    Controller 僅接收 `UpdateEducationConfigRequest`，透過 `configMapper.toCommand()` 轉為應用層 `UpdateEducationConfigCommand`，再交由 `EducationConfigService` 處理。
  - **資料庫寫入路徑（Database Write Path）**：
    依據 [Fixed-110](../fixed/Fixed-110.md) 既有設計，`UpdateEducationConfigCommand` 內部複合引用 `EducationConfigDefaultProperties.TrackConfigProperty`（階段二已擴充 `standardEntranceAge`）。`TrackConfigRequest` 的年齡透過 MapStruct 映射注入 Command，並在 `EducationConfigServiceImpl.updateConfig` 經由 `EducationConfigFactory.create` 產出 `TrackConfig` 領域實體，最後透過 `repository.save` 成功寫入資料庫 `finance_education_config` 表中。
  - **架構解耦重構分流**：
    鑑於 Command 復用 Properties 內部類屬於過渡設計，已正式立案並規劃獨立重構計畫至 **[Plan-14-education-config-command-decoupling.md](../planned/Plan-14-education-config-command-decoupling.md)**。
- **控制器單元測試**：
  - [EducationConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java)：
    - 新增 `getDefaultConfig_ReturnsStandardEntranceAge` 測試：透過 MockMvc 驗證 `GET /configs/education` 成功回傳 `preschool.daycareTrack.standardEntranceAge == 3`。
    - 新增 `updateConfig_NegativeStandardEntranceAge_ReturnsBadRequest` 測試：驗證小於 0 時回傳 400 `VALIDATION_FAILED`。
    - 新增 `updateConfig_ExceedMaxStandardEntranceAge_ReturnsBadRequest` 測試：驗證大於 30 時回傳 400 `VALIDATION_FAILED`。

---

### 4. 【階段四】前端 UI 動態接軌（`hub-ui`）—— 已完成 (Completed)
- **DTO 定義擴充**：
  - [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)：
    在 `TrackConfigDto` 介面增加 `standardEntranceAge?: number | null;`，完整對齊後端 `TrackConfigResponse` API 規格。
- **預覽計算動態化與寫死入學年齡全面拔除**：
  - [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)：
    - `resolveTrackDefaults` 函式回傳型別擴充 `standardEntranceAge: number`。
    - 各學制階段全面升級為優先採用後端 API 配置值（`trackConfig?.standardEntranceAge`），僅在後端配置未備妥時才 fallback 至各階段預設基準年齡：
      - `preschool`: `trackConfig?.standardEntranceAge ?? 3`
      - `primary`: `trackConfig?.standardEntranceAge ?? 6`
      - `middle`: `trackConfig?.standardEntranceAge ?? 12`
      - `high`: `trackConfig?.standardEntranceAge ?? 15`
      - `university`: `trackConfig?.standardEntranceAge ?? 18`
    - 在 `timelineInfo` 即時預覽計算邏輯中：`const standardStartYear = cohortYear + stage.standardEntranceAge;` 完全由後端領域資料動態驅動。
    - 無論使用者切換單軌托嬰（DAYCARE）、幼兒園（HOME）或雙軌分段（DAYCARE + INTL），前端預覽起算入學年齡均與後端計算引擎 `EducationTimelineProjector` 100% 同步為 3 歲入學，完全消除了「前端顯示 3 歲入學 vs 後端按 1 歲起算留下 2 年真空斷層」的系統性矛盾。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  cd ../../hub-ui                                           10011  12.41G   6.44    21:26:57  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                             10012  12.27G   6.24    21:27:13  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index a0f81f5..63b7770 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -38,7 +38,6 @@ export function resolveTrackDefaults(
   switch (stageKey) {
     case 'preschool': {
       let trackConfig: TrackConfigDto | undefined;
-      const standardEntranceAge = 3;
       if (track === 'DAYCARE') {
         trackConfig = config?.preschool?.daycareTrack;
       } else if (track === 'INTL') {
@@ -49,7 +48,7 @@ export function resolveTrackDefaults(
       return {
         defaultStartMonth: trackConfig?.startMonth ?? (track === 'INTL' ? 9 : 4),
         defaultDurationMonths: Math.max(36, trackConfig?.duration ?? 36),
-        standardEntranceAge,
+        standardEntranceAge: trackConfig?.standardEntranceAge ?? 3,
       };
     }
     case 'primary': {
@@ -64,7 +63,7 @@ export function resolveTrackDefaults(
       return {
         defaultStartMonth: trackConfig?.startMonth ?? (track === 'INTL' ? 9 : 4),
         defaultDurationMonths: trackConfig?.duration ?? 72,
-        standardEntranceAge: 6,
+        standardEntranceAge: trackConfig?.standardEntranceAge ?? 6,
       };
     }
     case 'middle': {
@@ -79,7 +78,7 @@ export function resolveTrackDefaults(
       return {
         defaultStartMonth: trackConfig?.startMonth ?? (track === 'INTL' ? 9 : 4),
         defaultDurationMonths: trackConfig?.duration ?? 36,
-        standardEntranceAge: 12,
+        standardEntranceAge: trackConfig?.standardEntranceAge ?? 12,
       };
     }
     case 'high': {
@@ -94,7 +93,7 @@ export function resolveTrackDefaults(
       return {
         defaultStartMonth: trackConfig?.startMonth ?? (track === 'INTL' ? 9 : 4),
         defaultDurationMonths: trackConfig?.duration ?? 36,
-        standardEntranceAge: 15,
+        standardEntranceAge: trackConfig?.standardEntranceAge ?? 15,
       };
     }
     case 'university': {
@@ -137,7 +136,7 @@ export function resolveTrackDefaults(
       return {
         defaultStartMonth: trackConfig?.startMonth ?? (isTwOrWestern ? 9 : isAus ? 2 : 4),
         defaultDurationMonths: trackConfig?.duration ?? (isThreeYear ? 36 : 48),
-        standardEntranceAge: 18,
+        standardEntranceAge: trackConfig?.standardEntranceAge ?? 18,
       };
     }
   }
diff --git a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
index 2513c7b..79317d6 100644
--- a/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
+++ b/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts
@@ -54,6 +54,7 @@ export interface TrackConfigDto {
   startMonth?: number | null;
   termsPerYear?: number | null;
   entranceFee?: number | null;
+  standardEntranceAge?: number | null;
 }
 
 export interface PreschoolConfigDto {
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../finance-hub/finance-hub-backend           10013  12.31G   6.08    21:27:21  
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff                                       10014  12.39G   6.08    21:27:29  
diff --git a/.env.base b/.env.base
index 8fcbcb74..157a9f6f 100644
--- a/.env.base
+++ b/.env.base
@@ -262,18 +262,21 @@ FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_TUITION="400000"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_TUITION="1500000"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 # Primary Defaults
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_TUITION="0"
@@ -281,18 +284,21 @@ FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_TUITION="1000000"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_TUITION="2500000"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 # Middle Defaults
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_TUITION="0"
@@ -300,18 +306,21 @@ FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TUITION="3000000"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 # High Defaults
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TUITION="0"
@@ -319,18 +328,21 @@ FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TUITION="3000000"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 # University Defaults
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TUITION="500000"
@@ -338,54 +350,63 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_ENTRANCE_FEE="280000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_TUITION="900000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_TUITION="300000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_TUITION="600000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_TUITION="6000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_TUITION="5000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_TUITION="4000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TUITION="4500000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_LIVING_COST="130000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_LIVING_COST="70000"
diff --git a/.env.dev b/.env.dev
index c4f660f9..65661a88 100644
--- a/.env.dev
+++ b/.env.dev
@@ -261,18 +261,21 @@ FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_TUITION="400000"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_TUITION="1500000"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 # Primary Defaults
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_TUITION="0"
@@ -280,18 +283,21 @@ FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_TUITION="1000000"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_TUITION="2500000"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 # Middle Defaults
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_TUITION="0"
@@ -299,18 +305,21 @@ FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TUITION="3000000"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 # High Defaults
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TUITION="0"
@@ -318,18 +327,21 @@ FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TUITION="3000000"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 # University Defaults
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TUITION="500000"
@@ -337,54 +349,63 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_ENTRANCE_FEE="280000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_TUITION="900000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_TUITION="300000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_TUITION="600000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_TUITION="6000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_TUITION="5000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_TUITION="4000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TUITION="4500000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_LIVING_COST="130000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_LIVING_COST="70000"
diff --git a/.env.prod b/.env.prod
index 448c4020..029a0a6c 100644
--- a/.env.prod
+++ b/.env.prod
@@ -263,18 +263,21 @@ FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_HOME_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_TUITION="400000"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_DAYCARE_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_TUITION="1500000"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_PRESCHOOL_INTL_TRACK_STANDARD_ENTRANCE_AGE="3"
 
 # Primary Defaults
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_TUITION="0"
@@ -282,18 +285,21 @@ FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_TUITION="1000000"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_TUITION="2500000"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_DURATION="72"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_PRIMARY_INTL_TRACK_STANDARD_ENTRANCE_AGE="6"
 
 # Middle Defaults
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_TUITION="0"
@@ -301,18 +307,21 @@ FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TUITION="3000000"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_MIDDLE_INTL_TRACK_STANDARD_ENTRANCE_AGE="12"
 
 # High Defaults
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TUITION="0"
@@ -320,18 +329,21 @@ FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_PUBLIC_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TUITION="3000000"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_HIGH_INTL_TRACK_STANDARD_ENTRANCE_AGE="15"
 
 # University Defaults
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TUITION="500000"
@@ -339,54 +351,63 @@ FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_ENTRANCE_FEE="280000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_NATIONAL_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_TUITION="900000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_ENTRANCE_FEE="200000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_TUITION="1200000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_START_MONTH="4"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_ENTRANCE_FEE="300000"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_PRIVATE_ART_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_TUITION="300000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_NATIONAL_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_TUITION="600000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_PRIVATE_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_TUITION="6000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_US_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_TUITION="5000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_UK_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_TUITION="4000000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_DURATION="48"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_START_MONTH="9"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_CANADA_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TUITION="4500000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_DURATION="36"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_START_MONTH="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_TERMS_PER_YEAR="2"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_ENTRANCE_FEE="0"
+FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_AUSTRALIA_TRACK_STANDARD_ENTRANCE_AGE="18"
 
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_JP_LIVING_COST="130000"
 FINANCE_HUB_EDUCATION_CONFIG_UNIVERSITY_TW_LIVING_COST="70000"
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
index 8507a761..020147c9 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java
@@ -35,6 +35,7 @@ public class EducationConfigDefaultProperties {
         @Builder.Default
         private Integer termsPerYear = 2;
         private BigDecimal entranceFee;
+        private Integer standardEntranceAge;
     }
 
     @Data
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
index 9396554e..d8a80eef 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java
@@ -104,6 +104,7 @@ public class EducationConfigFactory {
                 .startMonth(props.getStartMonth())
                 .termsPerYear(props.getTermsPerYear())
                 .entranceFee(props.getEntranceFee())
+                .standardEntranceAge(props.getStandardEntranceAge())
                 .build();
     }
 }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
index bec3c188..61bbaf20 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java
@@ -59,6 +59,7 @@ public class EducationConfigResult {
         Integer startMonth;
         Integer termsPerYear;
         BigDecimal entranceFee;
+        Integer standardEntranceAge;
 
         public static TrackConfigResult fromEntity(TrackConfig entity) {
             if (entity == null) {
@@ -70,6 +71,7 @@ public class EducationConfigResult {
                     .startMonth(entity.getStartMonth())
                     .termsPerYear(entity.getTermsPerYear())
                     .entranceFee(entity.getEntranceFee())
+                    .standardEntranceAge(entity.getStandardEntranceAge())
                     .build();
         }
     }
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
index 8d6e6385..dd8510c9 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java
@@ -325,6 +325,7 @@ public class EducationProfileResolver {
         }
 
         List<Integer> paymentMonths = calculatePaymentMonths(startMonth, termsPerYear);
+        Integer standardEntranceAge = defaultTrack != null ? defaultTrack.getStandardEntranceAge() : null;
 
         return ResolvedStageData.builder()
                 .stage(stage)
@@ -337,6 +338,7 @@ public class EducationProfileResolver {
                 .entranceFee(entranceFee)
                 .monthlyLivingCost(livingCost != null ? livingCost : BigDecimal.ZERO)
                 .paymentMonths(paymentMonths)
+                .standardEntranceAge(standardEntranceAge)
                 .build();
     }
 
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
index 30ea162c..7e1e8b65 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java
@@ -43,7 +43,6 @@ import org.springframework.stereotype.Component;
 @Component
 public class EducationTimelineProjector {
 
-    public static final int DAYCARE_AGE = 1;
     public static final int PRESCHOOL_AGE = 3;
     public static final int PRIMARY_AGE = 6;
     public static final int MIDDLE_AGE = 12;
@@ -135,16 +134,12 @@ public class EducationTimelineProjector {
     }
 
     private int getStandardEntranceAge(EducationStage stage, ResolvedStageData stageData) {
+        if (stageData != null && stageData.getStandardEntranceAge() != null) {
+            return stageData.getStandardEntranceAge();
+        }
         return switch (stage) {
-            case PRESCHOOL -> {
-                if (stageData.hasStage2()) {
-                    yield PRESCHOOL_AGE;
-                }
-                if (stageData.getTrackName() != null && stageData.getTrackName().equalsIgnoreCase("DAYCARE")) {
-                    yield DAYCARE_AGE;
-                }
-                yield PRESCHOOL_AGE;
-            }
+            case PRESCHOOL ->
+                PRESCHOOL_AGE;
             case PRIMARY ->
                 PRIMARY_AGE;
             case MIDDLE ->
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
index 6b9c6811..85ebc6cc 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java
@@ -51,6 +51,7 @@ public class ResolvedStageData {
     private final Integer termsPerYear;
     private final BigDecimal entranceFee;
     private final BigDecimal monthlyLivingCost;
+    private final Integer standardEntranceAge;
 
     @Builder.Default
     private final List<Integer> paymentMonths = Collections.emptyList();
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
index 3faf11a5..f6afeb1c 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java
@@ -65,6 +65,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(3)
                                 .build())
                         .daycareTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("300000"))
@@ -72,6 +73,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(3)
                                 .build())
                         .intlTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("1500000"))
@@ -79,6 +81,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(3)
                                 .entranceFee(new BigDecimal("200000"))
+                                .standardEntranceAge(3)
                                 .build())
                         .build())
                 .primary(PrimaryConfig.builder()
@@ -88,6 +91,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(6)
                                 .build())
                         .privateTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("800000"))
@@ -95,6 +99,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(new BigDecimal("300000"))
+                                .standardEntranceAge(6)
                                 .build())
                         .intlTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("2000000"))
@@ -102,6 +107,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(3)
                                 .entranceFee(new BigDecimal("500000"))
+                                .standardEntranceAge(6)
                                 .build())
                         .build())
                 .middle(MiddleConfig.builder()
@@ -111,6 +117,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(12)
                                 .build())
                         .privateTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("1000000"))
@@ -118,6 +125,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(new BigDecimal("300000"))
+                                .standardEntranceAge(12)
                                 .build())
                         .intlTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("2200000"))
@@ -125,6 +133,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(3)
                                 .entranceFee(new BigDecimal("500000"))
+                                .standardEntranceAge(12)
                                 .build())
                         .build())
                 .high(HighConfig.builder()
@@ -134,6 +143,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(15)
                                 .build())
                         .privateTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("1200000"))
@@ -141,6 +151,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(new BigDecimal("350000"))
+                                .standardEntranceAge(15)
                                 .build())
                         .intlTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("2500000"))
@@ -148,6 +159,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(3)
                                 .entranceFee(new BigDecimal("500000"))
+                                .standardEntranceAge(15)
                                 .build())
                         .build())
                 .university(UniversityConfig.builder()
@@ -157,6 +169,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(new BigDecimal("280000"))
+                                .standardEntranceAge(18)
                                 .build())
                         .jpPrivateTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("900000"))
@@ -164,6 +177,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(new BigDecimal("200000"))
+                                .standardEntranceAge(18)
                                 .build())
                         .jpPrivateArtTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("1200000"))
@@ -171,6 +185,7 @@ class EducationProfileResolverTest {
                                 .startMonth(4)
                                 .termsPerYear(2)
                                 .entranceFee(new BigDecimal("300000"))
+                                .standardEntranceAge(18)
                                 .build())
                         .twNationalTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("300000"))
@@ -178,6 +193,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(18)
                                 .build())
                         .twPrivateTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("600000"))
@@ -185,6 +201,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(18)
                                 .build())
                         .usTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("6000000"))
@@ -192,6 +209,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(18)
                                 .build())
                         .ukTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("5000000"))
@@ -199,6 +217,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(3)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(18)
                                 .build())
                         .canadaTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("4000000"))
@@ -206,6 +225,7 @@ class EducationProfileResolverTest {
                                 .startMonth(9)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(18)
                                 .build())
                         .australiaTrack(TrackConfig.builder()
                                 .tuition(new BigDecimal("4500000"))
@@ -213,6 +233,7 @@ class EducationProfileResolverTest {
                                 .startMonth(2)
                                 .termsPerYear(2)
                                 .entranceFee(BigDecimal.ZERO)
+                                .standardEntranceAge(18)
                                 .build())
                         .jpLivingCost(new BigDecimal("130000"))
                         .twLivingCost(new BigDecimal("70000"))
@@ -297,6 +318,7 @@ class EducationProfileResolverTest {
         assertThat(preschool.getDurationMonths()).isEqualTo(36); // 3 years -> 36 months
         assertThat(preschool.getStartMonth()).isEqualTo(4);
         assertThat(preschool.getPaymentMonths()).containsExactly(4, 10);
+        assertThat(preschool.getStandardEntranceAge()).isEqualTo(3);
 
         // Primary
         ResolvedStageData primary = profile.getStage(EducationStage.PRIMARY);
@@ -305,15 +327,18 @@ class EducationProfileResolverTest {
         assertThat(primary.getDurationMonths()).isEqualTo(72); // 6 years -> 72 months
         assertThat(primary.getStartMonth()).isEqualTo(4);
         assertThat(primary.getPaymentMonths()).containsExactly(4, 10);
+        assertThat(primary.getStandardEntranceAge()).isEqualTo(6);
 
         // Middle
         ResolvedStageData middle = profile.getStage(EducationStage.MIDDLE);
         assertThat(middle.isActive()).isTrue();
         assertThat(middle.getAnnualTuition()).isEqualByComparingTo("100000");
+        assertThat(middle.getStandardEntranceAge()).isEqualTo(12);
 
         // High
         ResolvedStageData high = profile.getStage(EducationStage.HIGH);
         assertThat(high.isActive()).isTrue();
+        assertThat(high.getStandardEntranceAge()).isEqualTo(15);
 
         // University (National, awayFromHome = false by default -> livingCost is 0)
         ResolvedStageData uni = profile.getStage(EducationStage.UNIVERSITY);
@@ -321,6 +346,7 @@ class EducationProfileResolverTest {
         assertThat(uni.getAnnualTuition()).isEqualByComparingTo("500000");
         assertThat(uni.getEntranceFee()).isEqualByComparingTo("280000");
         assertThat(uni.getMonthlyLivingCost()).isEqualByComparingTo(BigDecimal.ZERO);
+        assertThat(uni.getStandardEntranceAge()).isEqualTo(18);
     }
 
     @Test
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java
index 3ff433e6..60dff782 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java
@@ -387,4 +387,63 @@ class EducationTimelineProjectorTest {
         // Total events: 2 Daycare tuitions + 1 INTL entrance fee + 4 INTL tuitions = 7 events
         assertThat(timeline.getEvents()).hasSize(7);
     }
+
+    @Test
+    @DisplayName("Should project single-track DAYCARE at age 3 by default instead of legacy age 1")
+    void shouldProjectSingleTrackDaycareAtAge3ByDefault() {
+        LocalDate birthDate = LocalDate.of(2021, 8, 15);
+
+        ResolvedStageData daycare = ResolvedStageData.builder()
+                .stage(EducationStage.PRESCHOOL)
+                .trackName("DAYCARE")
+                .active(true)
+                .annualTuition(new BigDecimal("400000"))
+                .durationMonths(36)
+                .startMonth(4)
+                .termsPerYear(2)
+                .entranceFee(BigDecimal.ZERO)
+                .paymentMonths(List.of(4, 10))
+                .build();
+
+        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
+                .stages(Map.of(EducationStage.PRESCHOOL, daycare))
+                .build();
+
+        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);
+
+        // Academic birth year for 2021-08-15 (startMonth=4) is 2022.
+        // Entrance age 3 -> starts 2025-04 (NOT 2023-04).
+        assertThat(timeline.getStageIntervals()).containsKey(EducationStage.PRESCHOOL);
+        EducationAbsoluteTimeline.StageInterval interval = timeline.getStageIntervals().get(EducationStage.PRESCHOOL);
+        assertThat(interval.startMonth()).isEqualTo(YearMonth.of(2025, 4));
+        assertThat(interval.endMonth()).isEqualTo(YearMonth.of(2028, 3));
+    }
+
+    @Test
+    @DisplayName("Should dynamically use standardEntranceAge configured on ResolvedStageData")
+    void shouldRespectDynamicStandardEntranceAgeFromStageData() {
+        LocalDate birthDate = LocalDate.of(2021, 8, 15);
+
+        ResolvedStageData customStage = ResolvedStageData.builder()
+                .stage(EducationStage.PRIMARY)
+                .trackName("CUSTOM_TRACK")
+                .active(true)
+                .annualTuition(new BigDecimal("100000"))
+                .durationMonths(36)
+                .startMonth(4)
+                .termsPerYear(1)
+                .standardEntranceAge(7) // custom entrance age: 7 instead of standard 6
+                .paymentMonths(List.of(4))
+                .build();
+
+        ResolvedEducationProfile profile = ResolvedEducationProfile.builder()
+                .stages(Map.of(EducationStage.PRIMARY, customStage))
+                .build();
+
+        EducationAbsoluteTimeline timeline = projector.project(birthDate, profile);
+
+        // Academic birth year 2022 + 7 = 2029-04
+        EducationAbsoluteTimeline.StageInterval interval = timeline.getStageIntervals().get(EducationStage.PRIMARY);
+        assertThat(interval.startMonth()).isEqualTo(YearMonth.of(2029, 4));
+    }
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java
index fb9933c3..be50adcf 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java
@@ -38,7 +38,8 @@ public class HighConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "high_public_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "high_public_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "high_public_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "high_public_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "high_public_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "high_public_standard_entrance_age"))
     })
     private TrackConfig publicTrack;
 
@@ -49,7 +50,8 @@ public class HighConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "high_private_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "high_private_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "high_private_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "high_private_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "high_private_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "high_private_standard_entrance_age"))
     })
     private TrackConfig privateTrack;
 
@@ -60,7 +62,8 @@ public class HighConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "high_intl_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "high_intl_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "high_intl_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "high_intl_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "high_intl_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "high_intl_standard_entrance_age"))
     })
     private TrackConfig intlTrack;
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java
index 247777a9..0306abb2 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java
@@ -44,7 +44,8 @@ public class MiddleConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "middle_public_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "middle_public_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_public_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_public_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_public_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "middle_public_standard_entrance_age"))
     })
     private TrackConfig publicTrack;
 
@@ -58,7 +59,8 @@ public class MiddleConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "middle_private_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "middle_private_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_private_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_private_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_private_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "middle_private_standard_entrance_age"))
     })
     private TrackConfig privateTrack;
 
@@ -72,7 +74,8 @@ public class MiddleConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "middle_intl_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "middle_intl_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "middle_intl_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_intl_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "middle_intl_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "middle_intl_standard_entrance_age"))
     })
     private TrackConfig intlTrack;
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java
index 9df5cf78..583c481d 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java
@@ -38,7 +38,8 @@ public class PreschoolConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "preschool_home_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "preschool_home_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "preschool_home_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_home_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_home_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "preschool_home_standard_entrance_age"))
     })
     private TrackConfig homeTrack;
 
@@ -49,7 +50,8 @@ public class PreschoolConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "preschool_daycare_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "preschool_daycare_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "preschool_daycare_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_daycare_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_daycare_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "preschool_daycare_standard_entrance_age"))
     })
     private TrackConfig daycareTrack;
 
@@ -60,7 +62,8 @@ public class PreschoolConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "preschool_intl_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "preschool_intl_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "preschool_intl_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_intl_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "preschool_intl_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "preschool_intl_standard_entrance_age"))
     })
     private TrackConfig intlTrack;
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java
index 3c5e5a1e..b88cdff0 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java
@@ -38,7 +38,8 @@ public class PrimaryConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "primary_public_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "primary_public_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "primary_public_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_public_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_public_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "primary_public_standard_entrance_age"))
     })
     private TrackConfig publicTrack;
 
@@ -49,7 +50,8 @@ public class PrimaryConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "primary_private_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "primary_private_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "primary_private_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_private_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_private_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "primary_private_standard_entrance_age"))
     })
     private TrackConfig privateTrack;
 
@@ -60,7 +62,8 @@ public class PrimaryConfig {
             @AttributeOverride(name = "duration", column = @Column(name = "primary_intl_duration")),
             @AttributeOverride(name = "startMonth", column = @Column(name = "primary_intl_start_month")),
             @AttributeOverride(name = "termsPerYear", column = @Column(name = "primary_intl_terms_per_year")),
-            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_intl_entrance_fee", precision = 19, scale = 4))
+            @AttributeOverride(name = "entranceFee", column = @Column(name = "primary_intl_entrance_fee", precision = 19, scale = 4)),
+            @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "primary_intl_standard_entrance_age"))
     })
     private TrackConfig intlTrack;
 }
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java
index 0684266d..a7374f73 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java
@@ -70,4 +70,12 @@ public class TrackConfig {
      */
     @Column(name = "entrance_fee", precision = 19, scale = 4)
     private BigDecimal entranceFee;
+
+    /**
+     * Standard statutory or curriculum entrance age in years (e.g., 3 for preschool, 6 for primary,
+     * 12 for middle, 15 for high, 18 for university; configured via application properties).
+     */
+    @Column(name = "standard_entrance_age")
+    private Integer standardEntranceAge;
 }
+
diff --git a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
index 8d13f6db..512b8de1 100644
--- a/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
+++ b/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java
@@ -41,7 +41,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_jp_national_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_jp_national_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_jp_national_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_national_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_national_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_jp_national_standard_entrance_age"))
     })
     private TrackConfig jpNationalTrack;
 
@@ -52,7 +53,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_jp_private_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_jp_private_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_jp_private_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_private_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_private_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_jp_private_standard_entrance_age"))
     })
     private TrackConfig jpPrivateTrack;
 
@@ -63,7 +65,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_jp_private_art_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_jp_private_art_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_jp_private_art_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_private_art_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_jp_private_art_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_jp_private_art_standard_entrance_age"))
     })
     private TrackConfig jpPrivateArtTrack;
 
@@ -74,7 +77,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_tw_national_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_tw_national_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_tw_national_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_tw_national_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_tw_national_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_tw_national_standard_entrance_age"))
     })
     private TrackConfig twNationalTrack;
 
@@ -85,7 +89,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_tw_private_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_tw_private_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_tw_private_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_tw_private_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_tw_private_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_tw_private_standard_entrance_age"))
     })
     private TrackConfig twPrivateTrack;
 
@@ -96,7 +101,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_us_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_us_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_us_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_us_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_us_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_us_standard_entrance_age"))
     })
     private TrackConfig usTrack;
 
@@ -107,7 +113,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_uk_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_uk_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_uk_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_uk_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_uk_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_uk_standard_entrance_age"))
     })
     private TrackConfig ukTrack;
 
@@ -118,7 +125,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_canada_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_canada_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_canada_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_canada_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_canada_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_canada_standard_entrance_age"))
     })
     private TrackConfig canadaTrack;
 
@@ -129,7 +137,8 @@ public class UniversityConfig {
         @AttributeOverride(name = "duration", column = @Column(name = "uni_australia_duration")),
         @AttributeOverride(name = "startMonth", column = @Column(name = "uni_australia_start_month")),
         @AttributeOverride(name = "termsPerYear", column = @Column(name = "uni_australia_terms_per_year")),
-        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_australia_entrance_fee", precision = 19, scale = 4))
+        @AttributeOverride(name = "entranceFee", column = @Column(name = "uni_australia_entrance_fee", precision = 19, scale = 4)),
+        @AttributeOverride(name = "standardEntranceAge", column = @Column(name = "uni_australia_standard_entrance_age"))
     })
     private TrackConfig australiaTrack;
 
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java
index b673b453..4dbd51d2 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java
@@ -41,6 +41,10 @@ public class TrackConfigRequest {
     @PositiveOrZero(message = "entranceFee must be greater than or equal to 0")
     private BigDecimal entranceFee;
 
+    @Min(value = 0, message = "standardEntranceAge must be greater than or equal to 0")
+    @Max(value = 30, message = "standardEntranceAge must not exceed 30")
+    private Integer standardEntranceAge;
+
     @JsonIgnore
     @AssertTrue(message = "termsPerYear must be one of [1, 2, 3, 4, 6, 12]")
     public boolean isTermsPerYearValid() {
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java
index 3c313f68..6c2cc62c 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java
@@ -20,4 +20,5 @@ public class TrackConfigResponse {
     private Integer startMonth;
     private Integer termsPerYear;
     private BigDecimal entranceFee;
+    private Integer standardEntranceAge;
 }
diff --git a/finance-hub-backend/hub/src/main/resources/application-h2.properties b/finance-hub-backend/hub/src/main/resources/application-h2.properties
index 4bd9193e..a2217f39 100644
--- a/finance-hub-backend/hub/src/main/resources/application-h2.properties
+++ b/finance-hub-backend/hub/src/main/resources/application-h2.properties
@@ -306,18 +306,21 @@ finance-hub.education-config.preschool.home-track.duration=36
 finance-hub.education-config.preschool.home-track.start-month=4
 finance-hub.education-config.preschool.home-track.terms-per-year=2
 finance-hub.education-config.preschool.home-track.entrance-fee=0
+finance-hub.education-config.preschool.home-track.standard-entrance-age=3
 
 finance-hub.education-config.preschool.daycare-track.tuition=400000
 finance-hub.education-config.preschool.daycare-track.duration=36
 finance-hub.education-config.preschool.daycare-track.start-month=4
 finance-hub.education-config.preschool.daycare-track.terms-per-year=2
 finance-hub.education-config.preschool.daycare-track.entrance-fee=0
+finance-hub.education-config.preschool.daycare-track.standard-entrance-age=3
 
 finance-hub.education-config.preschool.intl-track.tuition=1500000
 finance-hub.education-config.preschool.intl-track.duration=36
 finance-hub.education-config.preschool.intl-track.start-month=9
 finance-hub.education-config.preschool.intl-track.terms-per-year=2
 finance-hub.education-config.preschool.intl-track.entrance-fee=200000
+finance-hub.education-config.preschool.intl-track.standard-entrance-age=3
 
 # Primary Defaults
 finance-hub.education-config.primary.public-track.tuition=0
@@ -325,18 +328,21 @@ finance-hub.education-config.primary.public-track.duration=72
 finance-hub.education-config.primary.public-track.start-month=4
 finance-hub.education-config.primary.public-track.terms-per-year=2
 finance-hub.education-config.primary.public-track.entrance-fee=0
+finance-hub.education-config.primary.public-track.standard-entrance-age=6
 
 finance-hub.education-config.primary.private-track.tuition=1000000
 finance-hub.education-config.primary.private-track.duration=72
 finance-hub.education-config.primary.private-track.start-month=4
 finance-hub.education-config.primary.private-track.terms-per-year=2
 finance-hub.education-config.primary.private-track.entrance-fee=200000
+finance-hub.education-config.primary.private-track.standard-entrance-age=6
 
 finance-hub.education-config.primary.intl-track.tuition=2500000
 finance-hub.education-config.primary.intl-track.duration=72
 finance-hub.education-config.primary.intl-track.start-month=9
 finance-hub.education-config.primary.intl-track.terms-per-year=2
 finance-hub.education-config.primary.intl-track.entrance-fee=300000
+finance-hub.education-config.primary.intl-track.standard-entrance-age=6
 
 # Middle Defaults
 finance-hub.education-config.middle.public-track.tuition=0
@@ -344,18 +350,21 @@ finance-hub.education-config.middle.public-track.duration=36
 finance-hub.education-config.middle.public-track.start-month=4
 finance-hub.education-config.middle.public-track.terms-per-year=2
 finance-hub.education-config.middle.public-track.entrance-fee=0
+finance-hub.education-config.middle.public-track.standard-entrance-age=12
 
 finance-hub.education-config.middle.private-track.tuition=1200000
 finance-hub.education-config.middle.private-track.duration=36
 finance-hub.education-config.middle.private-track.start-month=4
 finance-hub.education-config.middle.private-track.terms-per-year=2
 finance-hub.education-config.middle.private-track.entrance-fee=200000
+finance-hub.education-config.middle.private-track.standard-entrance-age=12
 
 finance-hub.education-config.middle.intl-track.tuition=3000000
 finance-hub.education-config.middle.intl-track.duration=36
 finance-hub.education-config.middle.intl-track.start-month=9
 finance-hub.education-config.middle.intl-track.terms-per-year=2
 finance-hub.education-config.middle.intl-track.entrance-fee=300000
+finance-hub.education-config.middle.intl-track.standard-entrance-age=12
 
 # High Defaults
 finance-hub.education-config.high.public-track.tuition=0
@@ -363,18 +372,21 @@ finance-hub.education-config.high.public-track.duration=36
 finance-hub.education-config.high.public-track.start-month=4
 finance-hub.education-config.high.public-track.terms-per-year=2
 finance-hub.education-config.high.public-track.entrance-fee=0
+finance-hub.education-config.high.public-track.standard-entrance-age=15
 
 finance-hub.education-config.high.private-track.tuition=1200000
 finance-hub.education-config.high.private-track.duration=36
 finance-hub.education-config.high.private-track.start-month=4
 finance-hub.education-config.high.private-track.terms-per-year=2
 finance-hub.education-config.high.private-track.entrance-fee=300000
+finance-hub.education-config.high.private-track.standard-entrance-age=15
 
 finance-hub.education-config.high.intl-track.tuition=3000000
 finance-hub.education-config.high.intl-track.duration=36
 finance-hub.education-config.high.intl-track.start-month=9
 finance-hub.education-config.high.intl-track.terms-per-year=2
 finance-hub.education-config.high.intl-track.entrance-fee=300000
+finance-hub.education-config.high.intl-track.standard-entrance-age=15
 
 # University Defaults
 finance-hub.education-config.university.jp-national-track.tuition=500000
@@ -382,54 +394,63 @@ finance-hub.education-config.university.jp-national-track.duration=48
 finance-hub.education-config.university.jp-national-track.start-month=4
 finance-hub.education-config.university.jp-national-track.terms-per-year=2
 finance-hub.education-config.university.jp-national-track.entrance-fee=280000
+finance-hub.education-config.university.jp-national-track.standard-entrance-age=18
 
 finance-hub.education-config.university.jp-private-track.tuition=900000
 finance-hub.education-config.university.jp-private-track.duration=48
 finance-hub.education-config.university.jp-private-track.start-month=4
 finance-hub.education-config.university.jp-private-track.terms-per-year=2
 finance-hub.education-config.university.jp-private-track.entrance-fee=200000
+finance-hub.education-config.university.jp-private-track.standard-entrance-age=18
 
 finance-hub.education-config.university.jp-private-art-track.tuition=1200000
 finance-hub.education-config.university.jp-private-art-track.duration=48
 finance-hub.education-config.university.jp-private-art-track.start-month=4
 finance-hub.education-config.university.jp-private-art-track.terms-per-year=2
 finance-hub.education-config.university.jp-private-art-track.entrance-fee=300000
+finance-hub.education-config.university.jp-private-art-track.standard-entrance-age=18
 
 finance-hub.education-config.university.tw-national-track.tuition=300000
 finance-hub.education-config.university.tw-national-track.duration=48
 finance-hub.education-config.university.tw-national-track.start-month=9
 finance-hub.education-config.university.tw-national-track.terms-per-year=2
 finance-hub.education-config.university.tw-national-track.entrance-fee=0
+finance-hub.education-config.university.tw-national-track.standard-entrance-age=18
 
 finance-hub.education-config.university.tw-private-track.tuition=600000
 finance-hub.education-config.university.tw-private-track.duration=48
 finance-hub.education-config.university.tw-private-track.start-month=9
 finance-hub.education-config.university.tw-private-track.terms-per-year=2
 finance-hub.education-config.university.tw-private-track.entrance-fee=0
+finance-hub.education-config.university.tw-private-track.standard-entrance-age=18
 
 finance-hub.education-config.university.us-track.tuition=6000000
 finance-hub.education-config.university.us-track.duration=48
 finance-hub.education-config.university.us-track.start-month=9
 finance-hub.education-config.university.us-track.terms-per-year=2
 finance-hub.education-config.university.us-track.entrance-fee=0
+finance-hub.education-config.university.us-track.standard-entrance-age=18
 
 finance-hub.education-config.university.uk-track.tuition=5000000
 finance-hub.education-config.university.uk-track.duration=36
 finance-hub.education-config.university.uk-track.start-month=9
 finance-hub.education-config.university.uk-track.terms-per-year=2
 finance-hub.education-config.university.uk-track.entrance-fee=0
+finance-hub.education-config.university.uk-track.standard-entrance-age=18
 
 finance-hub.education-config.university.canada-track.tuition=4000000
 finance-hub.education-config.university.canada-track.duration=48
 finance-hub.education-config.university.canada-track.start-month=9
 finance-hub.education-config.university.canada-track.terms-per-year=2
 finance-hub.education-config.university.canada-track.entrance-fee=0
+finance-hub.education-config.university.canada-track.standard-entrance-age=18
 
 finance-hub.education-config.university.australia-track.tuition=4500000
 finance-hub.education-config.university.australia-track.duration=36
 finance-hub.education-config.university.australia-track.start-month=2
 finance-hub.education-config.university.australia-track.terms-per-year=2
 finance-hub.education-config.university.australia-track.entrance-fee=0
+finance-hub.education-config.university.australia-track.standard-entrance-age=18
 
 finance-hub.education-config.university.jp-living-cost=130000
 finance-hub.education-config.university.tw-living-cost=70000
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index 9d72ecd7..3659fb71 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -300,18 +300,21 @@ finance-hub.education-config.preschool.home-track.duration=36
 finance-hub.education-config.preschool.home-track.start-month=4
 finance-hub.education-config.preschool.home-track.terms-per-year=2
 finance-hub.education-config.preschool.home-track.entrance-fee=0
+finance-hub.education-config.preschool.home-track.standard-entrance-age=3
 
 finance-hub.education-config.preschool.daycare-track.tuition=400000
 finance-hub.education-config.preschool.daycare-track.duration=36
 finance-hub.education-config.preschool.daycare-track.start-month=4
 finance-hub.education-config.preschool.daycare-track.terms-per-year=2
 finance-hub.education-config.preschool.daycare-track.entrance-fee=0
+finance-hub.education-config.preschool.daycare-track.standard-entrance-age=3
 
 finance-hub.education-config.preschool.intl-track.tuition=1500000
 finance-hub.education-config.preschool.intl-track.duration=36
 finance-hub.education-config.preschool.intl-track.start-month=9
 finance-hub.education-config.preschool.intl-track.terms-per-year=2
 finance-hub.education-config.preschool.intl-track.entrance-fee=200000
+finance-hub.education-config.preschool.intl-track.standard-entrance-age=3
 
 # Primary Defaults
 finance-hub.education-config.primary.public-track.tuition=0
@@ -319,18 +322,21 @@ finance-hub.education-config.primary.public-track.duration=72
 finance-hub.education-config.primary.public-track.start-month=4
 finance-hub.education-config.primary.public-track.terms-per-year=2
 finance-hub.education-config.primary.public-track.entrance-fee=0
+finance-hub.education-config.primary.public-track.standard-entrance-age=6
 
 finance-hub.education-config.primary.private-track.tuition=1000000
 finance-hub.education-config.primary.private-track.duration=72
 finance-hub.education-config.primary.private-track.start-month=4
 finance-hub.education-config.primary.private-track.terms-per-year=2
 finance-hub.education-config.primary.private-track.entrance-fee=200000
+finance-hub.education-config.primary.private-track.standard-entrance-age=6
 
 finance-hub.education-config.primary.intl-track.tuition=2500000
 finance-hub.education-config.primary.intl-track.duration=72
 finance-hub.education-config.primary.intl-track.start-month=9
 finance-hub.education-config.primary.intl-track.terms-per-year=2
 finance-hub.education-config.primary.intl-track.entrance-fee=300000
+finance-hub.education-config.primary.intl-track.standard-entrance-age=6
 
 # Middle Defaults
 finance-hub.education-config.middle.public-track.tuition=0
@@ -338,18 +344,21 @@ finance-hub.education-config.middle.public-track.duration=36
 finance-hub.education-config.middle.public-track.start-month=4
 finance-hub.education-config.middle.public-track.terms-per-year=2
 finance-hub.education-config.middle.public-track.entrance-fee=0
+finance-hub.education-config.middle.public-track.standard-entrance-age=12
 
 finance-hub.education-config.middle.private-track.tuition=1200000
 finance-hub.education-config.middle.private-track.duration=36
 finance-hub.education-config.middle.private-track.start-month=4
 finance-hub.education-config.middle.private-track.terms-per-year=2
 finance-hub.education-config.middle.private-track.entrance-fee=200000
+finance-hub.education-config.middle.private-track.standard-entrance-age=12
 
 finance-hub.education-config.middle.intl-track.tuition=3000000
 finance-hub.education-config.middle.intl-track.duration=36
 finance-hub.education-config.middle.intl-track.start-month=9
 finance-hub.education-config.middle.intl-track.terms-per-year=2
 finance-hub.education-config.middle.intl-track.entrance-fee=300000
+finance-hub.education-config.middle.intl-track.standard-entrance-age=12
 
 # High Defaults
 finance-hub.education-config.high.public-track.tuition=0
@@ -357,18 +366,21 @@ finance-hub.education-config.high.public-track.duration=36
 finance-hub.education-config.high.public-track.start-month=4
 finance-hub.education-config.high.public-track.terms-per-year=2
 finance-hub.education-config.high.public-track.entrance-fee=0
+finance-hub.education-config.high.public-track.standard-entrance-age=15
 
 finance-hub.education-config.high.private-track.tuition=1200000
 finance-hub.education-config.high.private-track.duration=36
 finance-hub.education-config.high.private-track.start-month=4
 finance-hub.education-config.high.private-track.terms-per-year=2
 finance-hub.education-config.high.private-track.entrance-fee=300000
+finance-hub.education-config.high.private-track.standard-entrance-age=15
 
 finance-hub.education-config.high.intl-track.tuition=3000000
 finance-hub.education-config.high.intl-track.duration=36
 finance-hub.education-config.high.intl-track.start-month=9
 finance-hub.education-config.high.intl-track.terms-per-year=2
 finance-hub.education-config.high.intl-track.entrance-fee=300000
+finance-hub.education-config.high.intl-track.standard-entrance-age=15
 
 # University Defaults
 finance-hub.education-config.university.jp-national-track.tuition=500000
@@ -376,54 +388,63 @@ finance-hub.education-config.university.jp-national-track.duration=48
 finance-hub.education-config.university.jp-national-track.start-month=4
 finance-hub.education-config.university.jp-national-track.terms-per-year=2
 finance-hub.education-config.university.jp-national-track.entrance-fee=280000
+finance-hub.education-config.university.jp-national-track.standard-entrance-age=18
 
 finance-hub.education-config.university.jp-private-track.tuition=900000
 finance-hub.education-config.university.jp-private-track.duration=48
 finance-hub.education-config.university.jp-private-track.start-month=4
 finance-hub.education-config.university.jp-private-track.terms-per-year=2
 finance-hub.education-config.university.jp-private-track.entrance-fee=200000
+finance-hub.education-config.university.jp-private-track.standard-entrance-age=18
 
 finance-hub.education-config.university.jp-private-art-track.tuition=1200000
 finance-hub.education-config.university.jp-private-art-track.duration=48
 finance-hub.education-config.university.jp-private-art-track.start-month=4
 finance-hub.education-config.university.jp-private-art-track.terms-per-year=2
 finance-hub.education-config.university.jp-private-art-track.entrance-fee=300000
+finance-hub.education-config.university.jp-private-art-track.standard-entrance-age=18
 
 finance-hub.education-config.university.tw-national-track.tuition=300000
 finance-hub.education-config.university.tw-national-track.duration=48
 finance-hub.education-config.university.tw-national-track.start-month=9
 finance-hub.education-config.university.tw-national-track.terms-per-year=2
 finance-hub.education-config.university.tw-national-track.entrance-fee=0
+finance-hub.education-config.university.tw-national-track.standard-entrance-age=18
 
 finance-hub.education-config.university.tw-private-track.tuition=600000
 finance-hub.education-config.university.tw-private-track.duration=48
 finance-hub.education-config.university.tw-private-track.start-month=9
 finance-hub.education-config.university.tw-private-track.terms-per-year=2
 finance-hub.education-config.university.tw-private-track.entrance-fee=0
+finance-hub.education-config.university.tw-private-track.standard-entrance-age=18
 
 finance-hub.education-config.university.us-track.tuition=6000000
 finance-hub.education-config.university.us-track.duration=48
 finance-hub.education-config.university.us-track.start-month=9
 finance-hub.education-config.university.us-track.terms-per-year=2
 finance-hub.education-config.university.us-track.entrance-fee=0
+finance-hub.education-config.university.us-track.standard-entrance-age=18
 
 finance-hub.education-config.university.uk-track.tuition=5000000
 finance-hub.education-config.university.uk-track.duration=36
 finance-hub.education-config.university.uk-track.start-month=9
 finance-hub.education-config.university.uk-track.terms-per-year=2
 finance-hub.education-config.university.uk-track.entrance-fee=0
+finance-hub.education-config.university.uk-track.standard-entrance-age=18
 
 finance-hub.education-config.university.canada-track.tuition=4000000
 finance-hub.education-config.university.canada-track.duration=48
 finance-hub.education-config.university.canada-track.start-month=9
 finance-hub.education-config.university.canada-track.terms-per-year=2
 finance-hub.education-config.university.canada-track.entrance-fee=0
+finance-hub.education-config.university.canada-track.standard-entrance-age=18
 
 finance-hub.education-config.university.australia-track.tuition=4500000
 finance-hub.education-config.university.australia-track.duration=36
 finance-hub.education-config.university.australia-track.start-month=2
 finance-hub.education-config.university.australia-track.terms-per-year=2
 finance-hub.education-config.university.australia-track.entrance-fee=0
+finance-hub.education-config.university.australia-track.standard-entrance-age=18
 
 finance-hub.education-config.university.jp-living-cost=130000
 finance-hub.education-config.university.tw-living-cost=70000
diff --git a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java
index a5c71580..be06991c 100644
--- a/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java
+++ b/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java
@@ -38,6 +38,7 @@ import static org.mockito.Mockito.when;
 import org.mockito.junit.jupiter.MockitoExtension;
 import org.springframework.http.MediaType;
 import org.springframework.test.web.servlet.MockMvc;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
 import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
 import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
@@ -53,8 +54,10 @@ import ch.lin.finance.hub.backend.api.common.advice.WebGlobalExceptionHandler;
 import ch.lin.finance.hub.backend.api.controller.config.dto.education.CreateEducationConfigRequest;
 import ch.lin.finance.hub.backend.api.controller.config.dto.education.EducationConfigMapper;
 import ch.lin.finance.hub.backend.api.controller.config.dto.education.EducationConfigResponse;
+import ch.lin.finance.hub.backend.api.controller.config.dto.education.PreschoolConfigResponse;
 import ch.lin.finance.hub.backend.api.controller.config.dto.education.PrimaryConfigRequest;
 import ch.lin.finance.hub.backend.api.controller.config.dto.education.TrackConfigRequest;
+import ch.lin.finance.hub.backend.api.controller.config.dto.education.TrackConfigResponse;
 import ch.lin.finance.hub.backend.api.controller.config.dto.education.UniversityConfigRequest;
 import ch.lin.finance.hub.backend.api.controller.config.dto.education.UpdateEducationConfigRequest;
 
@@ -280,4 +283,81 @@ class EducationConfigControllerTest {
                 .andExpect(status().isOk())
                 .andExpect(jsonPath("$.status").value("success"));
     }
+
+    @Test
+    @DisplayName("Should return 200 with standardEntranceAge for tracks when getting default config")
+    void getDefaultConfig_ReturnsStandardEntranceAge() throws Exception {
+        TrackConfigResponse daycareResponse = TrackConfigResponse.builder()
+                .tuition(BigDecimal.valueOf(600000))
+                .entranceFee(BigDecimal.valueOf(50000))
+                .duration(36)
+                .startMonth(4)
+                .termsPerYear(1)
+                .standardEntranceAge(3)
+                .build();
+
+        PreschoolConfigResponse preschool = PreschoolConfigResponse.builder()
+                .daycareTrack(daycareResponse)
+                .build();
+
+        EducationConfigResponse response = EducationConfigResponse.builder()
+                .name("default")
+                .preschool(preschool)
+                .build();
+
+        EducationConfigResult mockResult = mock(EducationConfigResult.class);
+        when(configService.getDefaultConfig()).thenReturn(mockResult);
+        when(configMapper.toResponse(mockResult)).thenReturn(response);
+
+        mockMvc.perform(get("/configs/education")
+                .contentType(MediaType.APPLICATION_JSON))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$.status").value("success"))
+                .andExpect(jsonPath("$.data.preschool.daycareTrack.standardEntranceAge").value(3));
+    }
+
+    @Test
+    @DisplayName("Should return 400 when standardEntranceAge is negative")
+    void updateConfig_NegativeStandardEntranceAge_ReturnsBadRequest() throws Exception {
+        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
+                .standardEntranceAge(-1)
+                .build();
+
+        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
+                .publicTrack(invalidTrack)
+                .build();
+
+        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
+                .primary(primary)
+                .build();
+
+        mockMvc.perform(patch("/configs/education/default")
+                .contentType(MediaType.APPLICATION_JSON)
+                .content(objectMapper.writeValueAsString(request)))
+                .andExpect(status().isBadRequest())
+                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
+    }
+
+    @Test
+    @DisplayName("Should return 400 when standardEntranceAge exceeds 30")
+    void updateConfig_ExceedMaxStandardEntranceAge_ReturnsBadRequest() throws Exception {
+        TrackConfigRequest invalidTrack = TrackConfigRequest.builder()
+                .standardEntranceAge(31)
+                .build();
+
+        PrimaryConfigRequest primary = PrimaryConfigRequest.builder()
+                .publicTrack(invalidTrack)
+                .build();
+
+        UpdateEducationConfigRequest request = UpdateEducationConfigRequest.builder()
+                .primary(primary)
+                .build();
+
+        mockMvc.perform(patch("/configs/education/default")
+                .contentType(MediaType.APPLICATION_JSON)
+                .content(objectMapper.writeValueAsString(request)))
+                .andExpect(status().isBadRequest())
+                .andExpect(jsonPath("$.data.code").value("VALIDATION_FAILED"));
+    }
 }
+
diff --git a/finance-hub-backend/todo/pending fix/issue-134.md b/finance-hub-backend/todo/pending fix/issue-134.md
index 3a22d9f2..b3b228fe 100644
--- a/finance-hub-backend/todo/pending fix/issue-134.md
+++ b/finance-hub-backend/todo/pending fix/issue-134.md
@@ -18,3 +18,189 @@
 
 ## 建議修正
 在`resolveTrackDefaults`的`preschool`這個case裡，比照後端`EducationTimelineProjector.java`的邏輯，針對`track === 'DAYCARE'`的情況，把`standardEntranceAge`也改成1，跟後端`DAYCARE_AGE`常數的數字一致。這是單純的數字校正，不是需要討論方向的設計選擇。
+
+---
+
+## 審查回饋與深度架構分析（為何不可照上述「建議修正」改前端為 1 歲）
+
+經過系統架構與業務模型全面覆核，**上述「建議修正」方向存在嚴重缺陷，不可照做**。以下說明前因後果、實作風險與最終拍板之修正方案：
+
+### 一、照原文修正的重大業務邏輯風險：產生 2 年「真空斷層（Vacuum Gap）」
+1. **時長與學制嚴重脫節**：
+   - 目前全系統（包括資料庫與 `application.properties`）中，幼兒園軌道（包含 DAYCARE）的標準就讀時長為 **36 個月（3 年）**。
+   - 小學法定入學年齡為 **滿 6 歲（72 個月）**（見 `PRIMARY_AGE = 6`）。
+   - 若照原建議將前端入學年齡改為 1 歲：
+     $$\text{入學（1 歲）} + 36 \text{ 個月（3 年）} = \text{滿 4 歲結束就讀}$$
+     但小學要到 6 歲才開始，這會導致 **4 歲至 6 歲之間完全沒有任何就讀階段與教育費用**，形成長達 2 年的「真空斷層（Vacuum Gap）」，直接違反 [Fixed-125](../fixed/Fixed-125.md) 明確定義的「避免時間軸產生真空斷層」之最高原則。
+2. **UI 視覺自相矛盾**：
+   - 前端卡片標題為 `Preschool (3-5)`（[child-profile-section.tsx#L673](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L673)），且內文明確說明為 `during preschool (ages 3–6)`（[child-profile-section.tsx#L1095](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L1095)）。
+   - 若預覽徽章顯示 1 歲（如 1~4 歲），會與整張卡片的學制大標題直接打架。
+
+---
+
+### 二、前因後果與歷史事實澄清（非 Fixed-113 遺留，而是 Fixed-125 前後端同步遺漏）
+原報告提及*「此落差在 Fixed-113 即存在，Fixed-125 未動過」*，經查證 Git Diff，**此考證有誤**：
+1. **Fixed-125 之前的現狀**：前端原先確實設有 `standardEntranceAge = 1` 且對應 `duration: 60`（即 1 歲入學念 5 年剛好接 6 歲小學）。
+2. **Fixed-125 的改動**：
+   - Fixed-125 依據使用者決策，將幼兒園核心模型定調為「瓜分 3 年（36 個月），3 歲入學至 6 歲接小學」。
+   - 因此 Fixed-125 在前端刪除了 `standardEntranceAge = 1` 與 60 個月，統一收斂為 3 歲與 36 個月。
+   - 但在後端實作時，Fixed-125 僅在 [EducationTimelineProjector.java#L801-L806](../fixed/Fixed-125.md) 針對「兩段式」加上了 `if (stageData.hasStage2()) yield PRESCHOOL_AGE;`（兩段式回傳 3 歲），**卻漏掉了單軌 DAYCARE 還殘留著早期 Plan-01 的 `yield DAYCARE_AGE;` (1 歲)**。
+3. **結論**：落差不是前端的問題，而是**後端殘留了舊常數**，甚至造成後端內部「單軌 DAYCARE 算 1 歲，兩段式 DAYCARE 算 3 歲」的邏輯分裂。
+
+---
+
+### 三、關於未滿 3 歲早讀（如 18 個月 / 24 個月）需求的分流
+對於如 **Aoba-Japan International School 目黑校區** 幼幼部（滿 18 個月 / 1.5 歲 K2 早讀卡位）或認可保育園 1 歲送托等，需要以「月齡（`entranceAgeMonths`）」為精確刻度且「時長動態補滿至小學 6 歲（如 54 個月、60 個月）」的新學制需求：
+- **已正式立案獨立規劃至 [Plan-13-early-childhood-entrance-age-months.md](../planned/Plan-13-early-childhood-entrance-age-months.md)**。
+- 該需求包含月級計算與時長動態連動，屬於重大 Feature 擴充，不在本 Issue 134 混合處理。
+
+---
+
+### 四、拍板修正方案：後端收斂為單一真相來源（Single Source of Truth）
+
+本 Issue 134 聚焦於「消除前後端數值分裂與硬寫常數」，採取以下解法：
+
+#### 1. 後端修正（清除舊殘留）
+- 修改 [EducationTimelineProjector.java#L137-L147](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java#L137-L147)：
+  移除 `DAYCARE_AGE = 1` 舊分支，將 `PRESCHOOL` 階段的標準入學年齡統一為 `PRESCHOOL_AGE = 3`。
+  - 單軌與兩段式在後端計算時皆為滿 3 歲入學。
+  - 搭配 36 個月時長，無縫銜接滿 6 歲小學，徹底消滅 4~6 歲教育真空斷層。
+
+#### 2. 架構升級：後端 API 統一發派學制年齡（消除前端 Hardcode）
+- 目前前端各階段入學年齡（3、6、12、15、18 歲）寫死在 switch-case 裡。
+- 在後端全域設定 API [TrackConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java) 擴充回傳 `standardEntranceAge`：
+  - Preschool 各軌道：`3`
+  - Primary 各軌道：`6`
+  - Middle 各軌道：`12`
+  - High 各軌道：`15`
+  - University 各軌道：`18`
+- 前端 [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx) 的 `resolveTrackDefaults` 改由 API 動態取得 `standardEntranceAge`，拔除前端的 hardcode 邏輯。
+
+#### 3. 預期效益
+1. 前端即時預覽與後端模擬計算 100% 精準吻合。
+2. 保持幼兒園 3~6 歲連續學制，無真空斷層。
+3. 前端學制參數全部由後端單一真相來源集中管理，未來維護與演進更具彈性。
+
+---
+
+## 五、分階段實作規劃（Phased Implementation Plan - 避免 Context Window 超載與幻覺）
+
+由於本任務屬於**由內而外穿透整套乾淨架構（Vertical Slice）**的實作，跨越 `hub-domain` $\to$ `hub-app` $\to$ `hub` $\to$ `hub-ui` 四個子模組。為避免單次 Context Window 膨脹與注意力發散，採取「依相依順序推進、每階段封閉閉環、即時驗收」之 4 階段推進策略：
+
+### 【階段一】領域實體與資料結構擴充（`hub-domain`）
+- **核心目標**：在核心領域模型建立 `standardEntranceAge`，確保 JPA Embeddable 結構合法，成為領域層一級公民。
+- **改動檔案**（集中在 `hub-domain`）：
+  1. [TrackConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java)：新增 `@Column(name = "standard_entrance_age") private Integer standardEntranceAge;`。
+  2. [PreschoolConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java)、[PrimaryConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java)、`MiddleConfig.java`、`HighConfig.java`、`UniversityConfig.java`：在每個 `@AttributeOverrides` 補齊對應軌道的 `*_standard_entrance_age` 欄位映射。
+- **驗收標準**：`hub-domain` 編譯通過，JPA Entity 與 Embeddable 結構 100% 合法（專案依賴 `spring.jpa.hibernate.ddl-auto=update`，啟動時將自動 alter table 補齊對應欄位）。
+
+---
+
+### 【階段二】後台預設配置與核心計算引擎動態化（`hub-app`）
+- **核心目標**：讓系統啟動時能透過 Properties 載入標準入學年齡，且投影計算器**徹底丟棄硬寫常數，直接從領域物件取值**。
+- **改動檔案**（集中在 `hub-app` 與設定檔）：
+  1. `finance-hub/.env.base`、`.env.dev`、`.env.prod` 以及 `application.properties` / `application-h2.properties`：補上各軌道的 `standard-entrance-age`（Preschool=3, Primary=6, Middle=12, High=15, University=18）。
+  2. [EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java)：`TrackConfigProperty` 加上 `standardEntranceAge`。
+  3. [EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java)：工廠將 Properties 映射入 `TrackConfig`。
+  4. [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java) & [EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)：將 `standardEntranceAge` 解析進舞台數據。
+  5. [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)：**拔除所有寫死常數與 `DAYCARE_AGE = 1` 舊邏輯**，統一改讀 `stageData.getStandardEntranceAge()`！
+- **驗收標準**：執行 `EducationTimelineProjectorTest` 與 `EducationProfileResolverTest`，確認單軌與兩段式計算 100% 綠燈。
+
+---
+
+### 【階段三】REST 控制層與 DTO 派發（`hub`）
+- **核心目標**：讓外部 API 能將領域層的 `standardEntranceAge` 序列化並回傳給前端。
+- **改動檔案**（集中在 `hub`）：
+  1. [TrackConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java) & [TrackConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java)：增加 `standardEntranceAge`。
+  2. [EducationConfigMapper.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/mapper/EducationConfigMapper.java)：更新映射邏輯。
+- **驗收標準**：執行 `EducationConfigControllerTest`，MockMvc 驗證 `GET /configs/education` 正確吐出各軌道的入學年齡。
+
+---
+
+### 【階段四】前端 UI 動態接軌（`hub-ui`）
+- **核心目標**：拔除前端的 hardcode switch-case，改吃後台領域資料。
+- **改動檔案**（集中在 `hub-ui`）：
+  1. [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)：`TrackConfigDto` 補上 `standardEntranceAge`。
+  2. [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)：`resolveTrackDefaults` 改成優先使用 `trackConfig?.standardEntranceAge`。
+- **驗收標準**：前端靜態型別檢查通過，預覽時間軸與後端真實模擬零誤差。
+
+---
+
+## 六、實作進度與修正報告（Progress & Fix Report）
+
+### 1. 【階段一】領域實體與資料結構擴充（`hub-domain`）—— 已完成 (Completed)
+- **領域實體升級**：
+  - [TrackConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java)：
+    新增 `@Column(name = "standard_entrance_age") private Integer standardEntranceAge;` 及其 Getter/Setter/Builder 屬性。
+  - **JPA 欄位重寫映射（AttributeOverrides）完整對齊**：
+    - [PreschoolConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PreschoolConfig.java)：為 `homeTrack`、`daycareTrack`、`intlTrack` 補齊 `home_standard_entrance_age`、`daycare_standard_entrance_age`、`intl_standard_entrance_age`。
+    - [PrimaryConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/PrimaryConfig.java)：為 `publicTrack`、`privateTrack`、`intlTrack` 補齊 `public_standard_entrance_age`、`private_standard_entrance_age`、`intl_standard_entrance_age`。
+    - [MiddleConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/MiddleConfig.java)：為 `publicTrack`、`privateTrack`、`intlTrack` 補齊對應欄位映射。
+    - [HighConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/HighConfig.java)：為 `publicTrack`、`privateTrack`、`intlTrack` 補齊對應欄位映射。
+    - [UniversityConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/UniversityConfig.java)：為 9 條國別大學軌道（`jpNationalTrack`, `jpPrivateTrack`, `jpPrivateArtTrack`, `twNationalTrack`, `twPrivateTrack`, `usTrack`, `ukTrack`, `canadaTrack`, `australiaTrack`）完整補齊對應欄位映射。
+- **單元測試建立**：
+  - [TrackConfigTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/test/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfigTest.java)：建立實體建構與欄位存取之測試。
+
+---
+
+### 2. 【階段二】後台預設配置與核心計算引擎動態化（`hub-app`）—— 已完成 (Completed)
+- **屬性配置與工廠模式動態化**：
+  - [EducationConfigDefaultProperties.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigDefaultProperties.java)：在 `TrackConfigProperty` 新增 `standardEntranceAge`。
+  - [EducationConfigFactory.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/education/EducationConfigFactory.java)：在 `createTrackConfig` 將屬性設定映射注入領域物件 `TrackConfig`。
+- **計算引擎動態化與舊常數拔除**：
+  - [ResolvedStageData.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/ResolvedStageData.java)：擴充 `standardEntranceAge` 欄位並啟用 `@Builder(toBuilder = true)`。
+  - [EducationProfileResolver.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)：在 `mergeStage` 將 `TrackConfig` 的 `standardEntranceAge` 帶入解析後的 `ResolvedStageData`。
+  - [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)：
+    - **正式刪除寫死常數 `DAYCARE_AGE = 1`**。
+    - `getStandardEntranceAge` 升級為優先讀取 `stageData.getStandardEntranceAge()`，若無設定則 fallback 至階段預設基準年齡（`PRESCHOOL`: 3, `PRIMARY`: 6, `MIDDLE`: 12, `HIGH`: 15, `UNIVERSITY`: 18），幼教所有軌道統一預設為 3 歲，徹底杜絕 4~6 歲 2 年真空斷層。
+- **環境設定檔與 Properties 全面擴充（5 檔同步補齊 21 條軌道）**：
+  - [`.env.base`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.base)、[`.env.dev`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.dev)、[`.env.prod`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/.env.prod)：加入各階段各軌道的 `FINANCE_HUB_EDUCATION_CONFIG_<STAGE>_<TRACK>_TRACK_STANDARD_ENTRANCE_AGE`。
+  - [`application.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application.properties)、[`application-h2.properties`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties)：加入 `finance-hub.education-config.<stage>.<track>-track.standard-entrance-age`。
+  - 數值精確校準：Preschool（HOME=3, DAYCARE=3, INTL=3）、Primary（6）、Middle（12）、High（15）、University（18）。
+- **單元測試更新與擴充**：
+  - [EducationProfileResolverTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolverTest.java)：更新 `setUp` 配置並驗證解析後的各階段 `standardEntranceAge` 正確無誤。
+  - [EducationTimelineProjectorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjectorTest.java)：
+    - 新增 `shouldProjectSingleTrackDaycareAtAge3ByDefault` 測試：確認單軌 DAYCARE 預設在滿 3 歲（2025-04）起算入學，而非舊有的 1 歲。
+    - 新增 `shouldRespectDynamicStandardEntranceAgeFromStageData` 測試：確認自訂入學年齡時，時序起算點完全由該數值動態驅動。
+
+---
+
+### 3. 【階段三】REST 控制層與 DTO 派發（`hub`）—— 已完成 (Completed)
+- **應用層結果與 DTO 欄位擴充**：
+  - [EducationConfigResult.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/result/EducationConfigResult.java)：
+    在應用層結果 DTO `TrackConfigResult` 增加 `Integer standardEntranceAge`，並於 `fromEntity` 完整映射 `entity.getStandardEntranceAge()`。
+  - [TrackConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java)：
+    新增 `private Integer standardEntranceAge;` 欄位，自動讓所有階段（Preschool, Primary, Middle, High, University）之各軌道的回傳 JSON 均包含此屬性。
+  - [TrackConfigRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigRequest.java)：
+    新增 `private Integer standardEntranceAge;` 並加上 `@Min(0)` 與 `@Max(30)` 參數驗證，確保 API 雙向設定之健全性。
+- **Command 與寫入資料庫流程澄清（方案 B）**：
+  - **Controller 嚴守 Clean Architecture，絕不使用 Domain**：
+    Controller 僅接收 `UpdateEducationConfigRequest`，透過 `configMapper.toCommand()` 轉為應用層 `UpdateEducationConfigCommand`，再交由 `EducationConfigService` 處理。
+  - **資料庫寫入路徑（Database Write Path）**：
+    依據 [Fixed-110](../fixed/Fixed-110.md) 既有設計，`UpdateEducationConfigCommand` 內部複合引用 `EducationConfigDefaultProperties.TrackConfigProperty`（階段二已擴充 `standardEntranceAge`）。`TrackConfigRequest` 的年齡透過 MapStruct 映射注入 Command，並在 `EducationConfigServiceImpl.updateConfig` 經由 `EducationConfigFactory.create` 產出 `TrackConfig` 領域實體，最後透過 `repository.save` 成功寫入資料庫 `finance_education_config` 表中。
+  - **架構解耦重構分流**：
+    鑑於 Command 復用 Properties 內部類屬於過渡設計，已正式立案並規劃獨立重構計畫至 **[Plan-14-education-config-command-decoupling.md](../planned/Plan-14-education-config-command-decoupling.md)**。
+- **控制器單元測試**：
+  - [EducationConfigControllerTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/EducationConfigControllerTest.java)：
+    - 新增 `getDefaultConfig_ReturnsStandardEntranceAge` 測試：透過 MockMvc 驗證 `GET /configs/education` 成功回傳 `preschool.daycareTrack.standardEntranceAge == 3`。
+    - 新增 `updateConfig_NegativeStandardEntranceAge_ReturnsBadRequest` 測試：驗證小於 0 時回傳 400 `VALIDATION_FAILED`。
+    - 新增 `updateConfig_ExceedMaxStandardEntranceAge_ReturnsBadRequest` 測試：驗證大於 30 時回傳 400 `VALIDATION_FAILED`。
+
+---
+
+### 4. 【階段四】前端 UI 動態接軌（`hub-ui`）—— 已完成 (Completed)
+- **DTO 定義擴充**：
+  - [use-child-profile.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts)：
+    在 `TrackConfigDto` 介面增加 `standardEntranceAge?: number | null;`，完整對齊後端 `TrackConfigResponse` API 規格。
+- **預覽計算動態化與寫死入學年齡全面拔除**：
+  - [child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)：
+    - `resolveTrackDefaults` 函式回傳型別擴充 `standardEntranceAge: number`。
+    - 各學制階段全面升級為優先採用後端 API 配置值（`trackConfig?.standardEntranceAge`），僅在後端配置未備妥時才 fallback 至各階段預設基準年齡：
+      - `preschool`: `trackConfig?.standardEntranceAge ?? 3`
+      - `primary`: `trackConfig?.standardEntranceAge ?? 6`
+      - `middle`: `trackConfig?.standardEntranceAge ?? 12`
+      - `high`: `trackConfig?.standardEntranceAge ?? 15`
+      - `university`: `trackConfig?.standardEntranceAge ?? 18`
+    - 在 `timelineInfo` 即時預覽計算邏輯中：`const standardStartYear = cohortYear + stage.standardEntranceAge;` 完全由後端領域資料動態驅動。
+    - 無論使用者切換單軌托嬰（DAYCARE）、幼兒園（HOME）或雙軌分段（DAYCARE + INTL），前端預覽起算入學年齡均與後端計算引擎 `EducationTimelineProjector` 100% 同步為 3 歲入學，完全消除了「前端顯示 3 歲入學 vs 後端按 1 歲起算留下 2 年真空斷層」的系統性矛盾。
+
diff --git a/finance-hub-backend/todo/planned/PLANNED.md b/finance-hub-backend/todo/planned/PLANNED.md
index 0b4b83d4..f68f75b5 100644
--- a/finance-hub-backend/todo/planned/PLANNED.md
+++ b/finance-hub-backend/todo/planned/PLANNED.md
@@ -127,6 +127,18 @@
 
 
 
+### Plan 13 — 支援未滿 3 歲（月級顆粒度，如 18 個月 / 24 個月）托育與早讀學制模型
+- **來源**：Issue 134 討論與早讀托育需求拆分
+- **文件**：[Plan-13-early-childhood-entrance-age-months.md](Plan-13-early-childhood-entrance-age-months.md)
+- **狀態**：PLANNED
+- **簡述**：支援如 Aoba 目黑校區 K2（18 個月）卡位早讀或 1 歲認可保育園送托，以月齡（`entranceAgeMonths`）為基準動態銜接至滿 6 歲（72 個月）小學，消除教育真空斷層。
+
+### Plan 14 — 全域學制設定 Command 與 @ConfigurationProperties 徹底解耦重構計畫
+- **來源**：Issue 134 架構覆核發現之壞味道（Fixed-110 遺留）
+- **文件**：[Plan-14-education-config-command-decoupling.md](Plan-14-education-config-command-decoupling.md)
+- **狀態**：PLANNED
+- **簡述**：將 `CreateEducationConfigCommand` / `UpdateEducationConfigCommand` 徹底與 Spring Boot 的 `EducationConfigDefaultProperties` 解耦，建立專屬純淨的 `TrackConfigCommand`、`PreschoolConfigCommand` 等類別族，消除跨層依賴壞味道，回歸 Clean Architecture 最高規範。
+
 ---
 
 ## 快速查表
@@ -142,3 +154,7 @@
 | FINDING-45 | 無 | — |
 | issue-06 | 無 | — |
 | L2 | 無 | — |
+| Plan 13 | 有 | [Plan-13-early-childhood-entrance-age-months.md](Plan-13-early-childhood-entrance-age-months.md) |
+| Plan 14 | 有 | [Plan-14-education-config-command-decoupling.md](Plan-14-education-config-command-decoupling.md) |
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT                                                            10015  12.39G   6.00    21:27:32  