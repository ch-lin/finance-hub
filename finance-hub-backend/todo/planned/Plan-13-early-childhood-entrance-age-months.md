# Plan 13：支援未滿 3 歲（月級顆粒度，如 18 個月 / 24 個月）托育與早讀學制模型

## 背景與問題本質（為什麼需要這份 Plan？）

### 1. 真實育兒與升學場景
在真實東京或海外大都市的生活圈中，學齡前教育並非全都是「滿 3 歲才上幼稚園」：
- **國際學校幼幼部（Toddler / Nursery）早讀卡位**：
  例如知名國際學校（如 **Aoba-Japan International School 目黑校區**）設有 K2 / Toddler 班級，允許幼兒從 **滿 18 個月（1.5 歲）** 或 2 歲起入學卡位，一路直升。
- **日本認可保育園（Daycare）0～1 歲送托**：
  日本雙薪家庭通常在育嬰假結束時（小孩滿 1 歲的 4 月，甚至 0 歲 6 個月）即進入認可保育園，一路就讀至滿 6 歲銜接公私立小學。

### 2. 現在系統做不到什麼（現有架構缺陷）
1. **年齡刻度無法表達「月」**：
   目前系統底層（包括後端 `EducationTimelineProjector` 與前端 `child-profile-section.tsx`）的標準入學年齡都是以**整數年（int age）**為單位（例如 1 歲、3 歲、6 歲）。系統完全無法表達「18 個月（1.5 歲）」這種非整數歲的開學起點。
2. **時長與小學接軌產生「真空斷層（Vacuum Gap）」**：
   在 [Fixed-125](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/fixed/Fixed-125.md) 中，幼兒園模型被定義為「固定 36 個月（3 年）」。若直接把入學年齡改為 1 歲或 18 個月，而時長依舊是 36 個月：
   $$\text{入學（1 歲）} + 36 \text{ 個月（3 年）} = \text{4 歲畢業}$$
   但小學固定在滿 6 歲入學，這會導致 **4 歲至 6 歲之間出現長達 2 年的教育與費用空白斷層**！
3. **前後端缺乏統一的動態配置源**：
   目前幼兒園與小學的起算年紀是由前後端各自寫死常數，後端配置端點（`GET /configs/education`）只提供了學費（`tuition`）、時長（`duration`）、開學月（`startMonth`），完全沒有回傳「標準入學月齡」，導致學制年齡無法集中由後台設定管理。

---

## 核心設計理念與架構決策（Architecture Decisions）

### 決策一：完全不開新表，在既有 `TrackConfig` 擴充
現有的 `finance_education_config` 表已經採用 `@Embedded TrackConfig` 儲存每個學制軌道的參數。
我們**不需要建立額外的新資料表**，只需在現有的 `TrackConfig` 中新增入學月齡欄位，維持乾淨的資料結構。

### 決策二：時間軸精度統一升級為「月齡（Months）」
- 捨棄粗糙的整數年（`int age`），改以小孩出生後的**累計月齡（Months from Birth）**作為起算標準。
- 統一命名：**`entranceAgeMonths`**（入學月齡）。
  - 標準幼稚園（年少 / Preschool）：`36`（滿 3 歲）
  - 日本認可保育園（DAYCARE）：`12`（滿 1 歲）
  - Aoba 幼幼班（Early Years / Toddler）：`18`（滿 18 個月）
  - 幼幼班（K2）：`24`（滿 2 歲）
  - 法定小學（Primary）：`72`（滿 6 歲）

### 決策三：動態補滿小學公式（Dynamic Chaining to Primary）
小學標準入學年齡在絕大多數國家（日本、台灣、美國）皆為滿 6 歲（即 **72 個月**）。
學前教育（Preschool / Daycare）若從小於 36 個月的月齡入學，其單軌標準時長公式應自動計算為：
$$\text{Standard Duration} = 72 - \text{entranceAgeMonths}$$

- **18 個月入學（如 Aoba Toddler）**：
  $$\text{時長} = 72 - 18 = 54 \text{ 個月（4.5 年）} \implies \text{18 個月念到 72 個月，無縫頂到小學！}$$
- **12 個月入學（日本 1 歲保育園）**：
  $$\text{時長} = 72 - 12 = 60 \text{ 個月（5 年）} \implies \text{1 歲念到 6 歲，無縫銜接小學！}$$
- **36 個月入學（標準幼兒園）**：
  $$\text{時長} = 72 - 36 = 36 \text{ 個月（3 年）} \implies \text{與現行 Fixed-125 100% 完全相容！}$$

### 決策四：支援「兩段式切換（Two-Stage Transition）」
若家長規劃在 18 個月先送托，滿 3 歲（36 個月）時換到國際學校：
- **第一段（Stage 1，早期托育）**：
  $$\text{時長 } D_1 = 36 - \text{entranceAgeMonths} = 36 - 18 = 18 \text{ 個月}$$
- **第二段（Stage 2，幼兒園）**：
  $$\text{時長 } D_2 = 72 - 36 = 36 \text{ 個月}$$
- 總長依然維持 $D_1 + D_2 = 54$ 個月，完美對齊滿 6 歲小學。

---

## 詳細資料模型與介面設計

### 1. 領域層實體擴充（Domain Entities）

#### (1) [TrackConfig.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/TrackConfig.java)
在 `@Embeddable TrackConfig` 新增入學月齡：
```java
/**
 * Standard baseline entrance age in MONTHS from birth
 * (e.g., 36 for age 3 kindergarten, 18 for Aoba toddler, 12 for daycare, 72 for primary).
 */
@Column(name = "entrance_age_months")
@Builder.Default
private Integer entranceAgeMonths = 36;
```

#### (2) [StageOverride.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/StageOverride.java)
小孩個別進階覆寫支援自訂起始月齡：
```java
/**
 * Optional user override for entrance age in MONTHS (e.g., 18 for early enrollment).
 * If null, falls back to track default entranceAgeMonths.
 */
@Column(name = "manual_entrance_age_months")
private Integer manualEntranceAgeMonths;
```

---

### 2. DTO 與 REST API 擴充

#### (1) 全域學制設定端點：`GET /configs/education`
在 [TrackConfigResponse.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/education/TrackConfigResponse.java) 加上回傳欄位：
```java
public class TrackConfigResponse {
    private BigDecimal tuition;
    private Integer duration;
    private Integer startMonth;
    private Integer termsPerYear;
    private BigDecimal entranceFee;
    private Integer entranceAgeMonths; // <-- 新增：回傳標準入學月齡（如 18, 36, 72）
}
```

#### (2) 小孩教育設定端點：`PUT /api/finance/profile/children/{id}/education-settings`
在 [StageOverrideDto](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts) 中增加 `manualEntranceAgeMonths?: number | null;`。

---

### 3. 排程與投影引擎改動（Simulation Core）

#### [EducationTimelineProjector.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)
將依賴整數年份的計算：
```java
// 舊邏輯：
int targetAge = getStandardEntranceAge(stage, stageData);
return YearMonth.of(academicBirthYear + targetAge, startMonth);
```
升級為基於月齡的精確計算：
```java
// 新邏輯：
int entranceMonths = stageData.getEntranceAgeMonths() != null 
    ? stageData.getEntranceAgeMonths() 
    : getDefaultEntranceAgeMonths(stage);

// 依出生日期直接加上精確月齡，再尋找相應學年度開學月份
LocalDate targetDate = birthDate.plusMonths(entranceMonths);
// 計算出精確的入學學年度 YearMonth
YearMonth standardStart = alignToAcademicCalendar(targetDate, startMonth);
```

---

### 4. 前端 UI 與時間軸預覽（[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx)）

1. **動態解析**：
   `resolveTrackDefaults` 不再用寫死的 `standardEntranceAge = 3`，而是優先讀取 API 回傳的 `trackConfig.entranceAgeMonths`：
   ```ts
   const standardEntranceAgeMonths = trackConfig?.entranceAgeMonths ?? (stageKey === 'primary' ? 72 : 36);
   const standardEntranceAgeYears = Math.round((standardEntranceAgeMonths / 12) * 10) / 10;
   ```
2. **小數點年份徽章顯示**：
   - 時間軸徽章在未滿整歲時，支援顯示例如 `(4.5y)`、`(1.5y)`。
   - 預覽起訖日期精準反映加總月數（如 `2025/10 ~ 2030/03`）。
3. **Advanced 彈性開放**：
   在幼兒園卡片的 Advanced 面板中，允許家長設定「提前入學月齡（如 18 個月）」，並動態提示接軌小學所需的總時長。

---

## 邊界情況與防呆規則（Edge Cases & Normalization）

1. **入學月齡下限檢查**：
   - 幼兒入學月齡不得低於 `6` 個月（新生兒極限），低於 6 個月拋出 400 錯誤。
   - 幼兒入學月齡不得大於等於小學入學月齡（必須 `< 72` 個月）。
2. **未設定時的向後相容**：
   - 現有歷史資料庫中若 `entrance_age_months` 為 `null`，一律 fallback：
     - `PRESCHOOL` $\to 36$
     - `PRIMARY` $\to 72$
     - `MIDDLE` $\to 144$
     - `HIGH` $\to 180$
     - `UNIVERSITY` $\to 216$
   - 既有使用者的模擬結果 100% 完全不受影響。
3. **學費繳款期數計算**：
   - 若就讀時長包含非整數年（例如 54 個月 = 4.5 年）：
     - 年繳學費依比例精確計算（4 年整期學費 + 1 次半期學費），不漏算任何一期。

---

## 驗證測試清單（Test Plan）

- [ ] **單元測試 1（Aoba 目黑校區 18 個月 Toddler 早讀）**：
  - 生日：2023-04-15
  - 設定：Preschool INTL 軌道，`entranceAgeMonths = 18`，時長 = 54 個月。
  - 斷言：入學為 2024-10，畢業為 2029-03；小學於 2029-04 無縫接軌，中間零天差斷層。
- [ ] **單元測試 2（日本 1 歲認可保育園送托）**：
  - 設定：Daycare 軌道，`entranceAgeMonths = 12`，時長 = 60 個月。
  - 斷言：1 歲 4 月入學，就讀滿 5 年後於 6 歲 4 月銜接小學。
- [ ] **單元測試 3（兩段式早讀銜接）**：
  - 設定：Stage 1 (Daycare, 18 個月至 36 個月，時長 18 個月) $\to$ Stage 2 (INTL, 36 個月至 72 個月，時長 36 個月)。
  - 斷言：兩段連續排程，學費分別依兩段軌道正確收取，小學準時於 72 個月入學。
- [ ] **API 契約測試**：
  - 驗證 `GET /configs/education` 正確回傳每個軌道的 `entranceAgeMonths`。
