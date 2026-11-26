# Plan 14：全域學制設定 Command 與 @ConfigurationProperties 徹底解耦重構計畫 (Education Config Command Decoupling)

## 背景與問題本質（Why this Plan?）

### 1. 既有架構壞味道（Code Smell）
在早期的全域學制設定實作（[Fixed-110](../fixed/Fixed-110.md)）中，為了開發便利性，應用層（Application Layer）的 Command 物件未獨立定義完整的階層結構，而是**直接複合引用了 Spring Boot 框架級的 `@ConfigurationProperties` 內部類**：

```java
// 目前的 UpdateEducationConfigCommand.java
package ch.lin.finance.hub.backend.api.app.service.config.education.command;

import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.HighConfigProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.MiddleConfigProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.PreschoolConfigProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.PrimaryConfigProperties;
import ch.lin.finance.hub.backend.api.app.config.education.EducationConfigDefaultProperties.UniversityConfigProperties;

@Value
@Builder
public class UpdateEducationConfigCommand {
    PreschoolConfigProperties preschool;
    PrimaryConfigProperties primary;
    MiddleConfigProperties middle;
    HighConfigProperties high;
    UniversityConfigProperties university;
}
```

### 2. 違反乾淨架構（Clean Architecture）與 DDD 準則
1. **職責混淆（Responsibility Leakage）**：
   `EducationConfigDefaultProperties` 原本職責是作為 Spring Boot 從 `application.properties` 與 `.env` 檔案載入預設基線值的配置類（Infrastructure/Config），卻同時被強行賦予了作為 API 操作 Mutation Command Payload 的第二重身分。
2. **語意模糊與架構盲點**：
   - 當開發者或架構覆核者檢視 `UpdateEducationConfigCommand` 時，無法一眼看出軌道包含哪些欄位（如 `standardEntranceAge`, `tuition`, `duration` 等），造成「好像沒有 Command 怎麼寫入資料庫」或「Controller 是否直接接觸 Domain/Properties」的強烈混淆。
   - Presentation 端的 MapStruct Mapper（`EducationConfigMapper.java`）被迫定義了 `toProperties(TrackConfigRequest request)`，形成了 Presentation DTO 映射到 Config Properties 的畸形相依。
3. **變更耦合風險**：
   未來若系統啟動預設配置（Properties）與 API 動態更新（Command）需要支援不同層級的預設值、驗證邏輯或欄位時，兩者將互相掣肘。

---

## 核心設計目標（Architecture Goals）

1. **嚴守乾淨架構邊界**：
   Controller（Presentation） $\longrightarrow$ Command（Application） $\longrightarrow$ Entity（Domain） $\longrightarrow$ Database。
   各層 DTO 職責純粹，絕不跨層共用或復用基礎設施配置類。
2. **建立專屬純淨 Command DTO**：
   在 `ch.lin.finance.hub.backend.api.app.service.config.education.command` 建立一級公民 Command 物件。
3. **雙管道轉換清晰化**：
   - 管道 A（系統啟動 / Properties 預設值）：`EducationConfigDefaultProperties` $\longrightarrow$ `EducationConfig`（Domain Entity）。
   - 管道 B（HTTP API 動態建立 / 更新）：`*ConfigRequest` $\longrightarrow$ `*ConfigCommand` $\longrightarrow$ `EducationConfig`（Domain Entity）。

---

## 詳細類別設計（Detailed Class Design）

### 1. 建立獨立 Command 類別族（`hub-app`）
路徑：`ch.lin.finance.hub.backend.api.app.service.config.education.command`

```
ch.lin.finance.hub.backend.api.app.service.config.education.command
├── CreateEducationConfigCommand.java
├── UpdateEducationConfigCommand.java
├── TrackConfigCommand.java           <-- 新增
├── PreschoolConfigCommand.java       <-- 新增
├── PrimaryConfigCommand.java         <-- 新增
├── MiddleConfigCommand.java          <-- 新增
├── HighConfigCommand.java            <-- 新增
└── UniversityConfigCommand.java      <-- 新增
```

#### A. `TrackConfigCommand.java`
```java
package ch.lin.finance.hub.backend.api.app.service.config.education.command;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TrackConfigCommand {
    BigDecimal tuition;
    Integer duration;
    Integer startMonth;
    Integer termsPerYear;
    BigDecimal entranceFee;
    Integer standardEntranceAge;
}
```

#### B. `PreschoolConfigCommand.java`
```java
package ch.lin.finance.hub.backend.api.app.service.config.education.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PreschoolConfigCommand {
    TrackConfigCommand homeTrack;
    TrackConfigCommand daycareTrack;
    TrackConfigCommand intlTrack;
}
```

#### C. `PrimaryConfigCommand.java` / `MiddleConfigCommand.java` / `HighConfigCommand.java`
```java
package ch.lin.finance.hub.backend.api.app.service.config.education.command;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PrimaryConfigCommand {
    TrackConfigCommand publicTrack;
    TrackConfigCommand privateTrack;
    TrackConfigCommand intlTrack;
}
```

#### D. `UniversityConfigCommand.java`
```java
package ch.lin.finance.hub.backend.api.app.service.config.education.command;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UniversityConfigCommand {
    TrackConfigCommand jpNationalTrack;
    TrackConfigCommand jpPrivateTrack;
    TrackConfigCommand jpPrivateArtTrack;
    TrackConfigCommand twNationalTrack;
    TrackConfigCommand twPrivateTrack;
    TrackConfigCommand usTrack;
    TrackConfigCommand ukTrack;
    TrackConfigCommand canadaTrack;
    TrackConfigCommand australiaTrack;
    BigDecimal livingCost;
    BigDecimal twLivingCost;
    BigDecimal usLivingCost;
    BigDecimal ukLivingCost;
    BigDecimal canadaLivingCost;
    BigDecimal australiaLivingCost;
}
```

#### E. 重構 `CreateEducationConfigCommand` & `UpdateEducationConfigCommand`
移除所有 `EducationConfigDefaultProperties.*` import，改全面引用上述純 Command：
```java
@Value
@Builder
public class UpdateEducationConfigCommand {
    PreschoolConfigCommand preschool;
    PrimaryConfigCommand primary;
    MiddleConfigCommand middle;
    HighConfigCommand high;
    UniversityConfigCommand university;
}
```

---

### 2. 表現層映射重構（`hub` - `EducationConfigMapper.java`）
拔除所有 `toProperties` 方法，替換為標準的 `toCommand` 體系：

```java
// --- Request to Command ---

CreateEducationConfigCommand toCommand(CreateEducationConfigRequest request);

UpdateEducationConfigCommand toCommand(UpdateEducationConfigRequest request);

PreschoolConfigCommand toCommand(PreschoolConfigRequest request);

PrimaryConfigCommand toCommand(PrimaryConfigRequest request);

MiddleConfigCommand toCommand(MiddleConfigRequest request);

HighConfigCommand toCommand(HighConfigRequest request);

UniversityConfigCommand toCommand(UniversityConfigRequest request);

TrackConfigCommand toCommand(TrackConfigRequest request);
```

---

### 3. 領域工廠與服務層適配（`hub-app`）
在 `EducationConfigFactory.java`（或重構為專屬的領域建構方法）新增支援 Command 的建構重載：
```java
public EducationConfig createFromCommand(String name, UpdateEducationConfigCommand command) {
    return EducationConfig.builder()
            .name(name)
            .preschool(createPreschoolConfig(command.getPreschool()))
            .primary(createPrimaryConfig(command.getPrimary()))
            .middle(createMiddleConfig(command.getMiddle()))
            .high(createHighConfig(command.getHigh()))
            .university(createUniversityConfig(command.getUniversity()))
            .build();
}
```
如此一來，`EducationConfigServiceImpl` 在處理 `createConfig` 與 `updateConfig` 時：
- 完全不需再宣告暫存的 `EducationConfigDefaultProperties tempProps = new ...`！
- 直接使用 `EducationConfigFactory.createFromCommand(name, command)` 產出實體並 `repository.save(...)` 寫入資料庫！

---

## 實施步驟（Implementation Phases）

1. **【步驟一】Command 類別建立**：
   在 `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/education/command` 建立 6 個全新純 Command 類別。
2. **【步驟二】更新 Command 容器**：
   重構 `CreateEducationConfigCommand.java` 與 `UpdateEducationConfigCommand.java`。
3. **【步驟三】工廠與服務層適配**：
   在 `EducationConfigFactory.java` 支援 Command 參數，並移除 `EducationConfigServiceImpl.java` 中利用 Properties 轉傳的過渡寫法。
4. **【步驟四】Mapper 更新**：
   在 `EducationConfigMapper.java` 移除 `toProperties`，統一收斂為 `toCommand`。
5. **【步驟五】測試驗收**：
   執行 `EducationConfigControllerTest` 與相關整合測試，確認所有 MockMvc 與服務調用行為 100% 保持一致且無回歸問題。

---

## 效益評估（Impact & Benefits）

1. **徹底消滅架構壞味道**：
   `@ConfigurationProperties` 回歸純粹的設定檔綁定職責；Command 回歸純粹的應用層用例輸入。
2. **符合 Clean Architecture 最高規範**：
   Controller 與 Application 層的通訊契約（Contract）100% 透明，杜絕任何隱式轉換或職責洩漏。
3. **維護與擴充安全性大增**：
   未來若教育學制擴充任何屬性（如 [Plan-13](Plan-13-early-childhood-entrance-age-months.md) 的入學月齡 `entranceAgeMonths`），能從 Request $\to$ Command $\to$ Entity 一氣呵成，不會再有任何屬性在 Command 結構中「隱形」。
