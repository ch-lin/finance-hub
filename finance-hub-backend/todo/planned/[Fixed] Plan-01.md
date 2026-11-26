# [Fixed] Plan 01：教育費用計算「整個西元年只認一種學制」，換學制那一年金額會算錯，且大學最後幾個月學費會憑空消失，建議整套改成以月為單位計算

## 嚴重程度
High（這是目前正在使用的試算引擎本身的問題，不是畫面上沒人在用的功能。只要小孩的求學生涯中有任何一次「換學制」發生在西元年年中——例如3月讀完高中、4月起上大學——那一整年的教育費金額就會算錯；而且大學最後一年結束前的最後幾個月學費，永遠不會被算進任何一年裡，等於憑空消失。這個問題會直接影響資產模擬（Sandbox）跟自動最佳化（GA/格狀搜尋）的結果，因為兩者最終都是呼叫同一套教育費計算）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/EducationExpenseCalculator.java`（教育費計算邏輯本身，這次要重構的地方）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java`（呼叫上面那個計算，把結果加進每年的總支出——這個檔案本身不需要修改，只是說明教育費算出來的金額會用在這裡）

## 現狀
現在的教育費用計算方式，是「整個西元年只認一種學制」。程式方法是 `EducationExpenseCalculator.calculateAnnualCost`：每次要算某個小孩「某一個西元年」的教育費，只會判斷這個小孩這一年對應到哪一個學制（幼稚園/小學/中學/高中/大學），然後回傳「一整年都算那個學制的費用」。

但小孩實際換學制的時間點（通常是每年4月，或家長自己針對這個小孩設定的月份/日期），跟試算用的西元年（1月到12月）本來就不是同一套時間軸。只要小孩換學制的月份落在年中，同一個西元年裡，前面幾個月其實還在念舊學制、後面幾個月已經在念新學制——理論上這一年應該是「一部分舊學制費用+一部分新學制費用」混在一起，但現在的程式碼整年只能算成其中一種，沒辦法同一年混兩種學制的費用。

用系統目前預設的學費金額實際估算影響：
- 換算成大學（尤其是念英國、加拿大、澳洲等費用較高的路線）：換學制那一年，金額可能會比真實情況多算幾十萬到一百多萬日圓。
- 大學最後一年結束前的最後幾個月學費，因為超出目前程式碼認得的最高學齡，永遠不會被算進任何一年裡——這幾個月的學費不是算錯年份，是真的完全消失，永遠少算一筆。

## 建議修正
把 `EducationExpenseCalculator.calculateAnnualCost` 的計算方式，從「整年只認一種學制」改成「逐月計算」，這是比較大的改寫，不是小修小補：

- 針對要計算的某一個西元年，從1月到12月逐月判斷：用小孩的生日、以及這個小孩自己設定的換學制月份/日期，反推「這個月」小孩在學校是哪個學制/哪個年級。現在的 `calculateAcademicAge` 只能整年判斷出同一個結果，需要改成可以針對特定月份分別判斷。
- 查出這個學制/年級一整年的學費金額（可能是依公立/私立/國際學校等軌道查表得到的金額，也可能是家長手動填寫的覆寫金額——兩種金額來源用同一套邏輯處理就好，不用分開寫），除以這個學制一整年的月數（通常是12個月），得到「這個月的單價」。
- 把1月到12月、每個月各自算出來的單價加總，就是這一年正確的總教育費。這樣一來，只要某個月剛好是換學制的月份，從那個月開始算出來的學制自然就會變成新的，不需要額外再去計算「這一年有幾個月算舊制、幾個月算新制」的比例——邏輯本身就會自動處理換學制的情況。這樣也會順便解決大學最後一年最後幾個月學費消失的問題，因為每個月都是分別判斷，不會因為超過現在整年判斷用的年齡上限而被整個跳過。

需要注意的地方：
1. 每個小孩可能自己設定了不同的換學制月份/日期（不是所有小孩都固定是4月），逐月判斷學制時要吃每個小孩自己的設定，不能只套用預設值。
2. 對外的計算方式（給某一年、回傳這一年的教育費總金額）不需要改變——呼叫這個計算的地方（試算引擎、自動最佳化）不需要跟著修改，重構只發生在教育費計算內部，讓算出來的金額更準確。
3. 目前這部分計算完全沒有任何測試，這次重構請務必補上測試，至少涵蓋：換學制的月份能正確從那個月開始改用新學制的單價；沒有換學制的一般年份，12個月加總的結果要跟現在整年算的結果一樣（不能因為重構讓正常年份反而算錯）；手動填寫金額的小孩換學制時，單價計算是否也一樣正確；大學最後一年結束前的月份是否真的被算進去了（不再消失）；每個小孩各自不同的換學制月份/日期是否有被正確套用。

## 具體實作方向與架構設計 (EducationConfig vs ChildEducationSettings)

為了解決「跨學制算錯」與「年份對不齊」的問題，我們必須從 DDD (領域驅動設計) 的角度重新塑模。
目前的系統設計過於扁平，導致無法表達「小學念日本公立 (4月開學)，國中轉去美國際學校 (9月開學)」的真實情況。

### 階段性重構策略

因為 `FinanceHubConfig` 已經有點龐大，我們將把教育相關的預設設定獨立切分出一個新的 `EducationConfig` 類別來管理。
為避免直接修改舊設定導致影響範圍過大，實作上將分為三階段：
- **第一階段（本次修改目標）**：新增 `EducationConfig` 以及新的資料結構。為避免檔案過多，這些新的配置類別（含各個 `StageConfig` 與 `TrackConfig`）將統一放置於專屬的子套件 `ch.lin.finance.hub.backend.api.domain.model.config.education` 中。**完全不動到舊的 `FinanceHubConfig`**，確保對現有系統的相容性。
- **第二階段（未來計畫）**：實作 `3-Step Pipeline` 計算引擎，並將各項服務切換至讀取新設定。
- **第三階段（未來計畫）**：移除既有舊程式碼，以及 `FinanceHubConfig` 與 `ChildProfile` 中的過期教育欄位。

### 第一階段重構目標：核心領域概念 (DDD 視角)
*(註：本階段的唯一目標，是建立以下全新的 Domain Entity 與 Value Object 類別結構，並放入對應的 package 中。絕對不可刪除或修改舊有的 `FinanceHubConfig` 與 `ChildProfile` 欄位，也不介入計算邏輯的修改，以確保邊界清晰與相容性。)*

**【核心關係說明：`EducationConfig` vs `ChildEducationSettings`】**
- **`EducationConfig`**：屬於**「全域預設值（Global Defaults）」**。負責定義系統中所有學制軌道的「標準規格」（例如：英國大學預設學費多少、讀 3 年、9 月開學）。
- **`ChildEducationSettings`**：屬於**「個人化客製與覆寫（Personal Overrides）」**。與特定小孩（`ChildProfile`）是一對一綁定，用來記錄該小孩「選擇了哪一條預設軌道」，以及「是否有特例要覆寫預設值」（例如：雖然選了英國大學，但手動把學費覆寫為自己查到的真實數字）。
- **計算關係**：未來的計算引擎在算錢時，會以「先看 `ChildEducationSettings` 是否有覆寫值，若沒有則退回 `EducationConfig` 提取標準值」的邏輯來運作。

1. [x] **`TrackConfig` (軌道基準設定 Value Object)**
   在新的 `EducationConfig` (全域菜單) 中，每一種特定的學制軌道（例如：`Primary Public`, `Primary Intl`, `University UK`），都不應該只有一個孤立的「學費」數字，而是必須包含三位一體的定義：
   - **`tuition` (基準學費)**（註：底層改存「月」為單位，理由見下方說明）
   - **`duration` (基準就讀時間)**（註：底層改存「月」為單位，理由見下方說明）
   - **`startMonth` (基準開學月份)**
   - **`termsPerYear` (預設收費期數)**（註：預設為 2，提供無覆寫時的預設值，跟 `StageOverride.manualTermsPerYear` 對稱）
   - **`entranceFee` (一次性入學金)**（註：只放全域預設層，不開放 `ChildEducationSettings` 覆寫。供 Projector 於絕對起點月加入）

   > **💡 為什麼 `tuition` 與 `duration` 要以「月」為單位儲存？**
   > 整套重寫的目的就是「月級精確度」。如果 `duration` 繼續用年當單位，代表 Projector 換算時還是得假設「一年就是剛好 12 個月銜接」。
   > 改成月，現在動工阻力最小，換算方式單純是「年 × 12」（例如大學：英國/澳洲 3 年 → 36，台灣/國內 4 年 → 48，醫學系 6 年 → 72；國中 3 年 → 36；小學 6 年 → 72）。
   > 使用者填寫介面仍然可以維持「填幾年」，存進資料庫前轉成月即可，不影響使用者填寫體感。
   > 差別只在於：以後如果真的出現「這個小孩只讀 2 年半」這種非整年情境，月為單位的欄位存得下（30），年為單位的欄位存不下。

   > **💡 關於 `termsPerYear`（預設收費期數）的設計考量**
   > 為了與 `StageOverride` 中的 `manualTermsPerYear` 覆寫欄位對稱，我們在 `TrackConfig` 中加入了 `termsPerYear`。這樣在「沒覆寫就用預設」的計算規則下，才能有預設值（預設 2）可以退回。
   > 另外，如果之後這個欄位開放家長自由輸入數字，2、3、4、6、12 這些能整除 12 的沒問題，但填入除不盡的數字（例如 5）會讓月份間距算出小數，因此建議 Projector 實作時一併決定要不要限制輸入範圍。

   > **💡 關於 `entranceFee`（一次性入學金）的設計考量**
   > 現在的設定架構完全沒有「入學金」這種一次性費用的概念。日本私立/國際學校常見的一次性入學費用，跟每年固定學費是分開收的兩筆錢，目前的架構都算不出這筆錢。
   > 因為只放在 `TrackConfig` 全域預設層、不開放個人覆寫，所以使用者畫面上不會多看到任何欄位，但估算結果會更準；`EducationTimelineProjector` 只需要在該階段絕對起點的月份，額外加這一筆一次性金額即可，不影響其他月份的計算邏輯。

2. [x] **`StageOverride` (階段客製化 Value Object)**
   在 `ChildEducationSettings` (個別小孩點菜單) 中，小孩的每一個「教育階段」(如：中學階段)，除了選擇要讀哪個軌道之外，還允許提供以下覆寫值：
   - **`manualTuition`** (如：不按基準，自己輸入真實私校學費)
   - **`manualDuration`** (如：提早跳級只念2年，底層同樣建議存「月」)
   - **`manualStartMonth`** (如：特例入學月份)
   - **`manualTermsPerYear`** (如：國際學校特有的一年三學期制，預設皆為 2 期)

3. [x] **階層式架構 (Hierarchical Structure)**
   捨棄過去扁平式的設定（把幾十個變數攤平在同一個類別中），改採樹狀結構。`EducationConfig` 會依照階段分為 `PreschoolConfig`, `PrimaryConfig` 等。每個階段內部再包含對應的 `TrackConfig`。例如大學階段的結構會長這樣：
   ```java
   public class UniversityConfig {
       private TrackConfig nationalTrack;
       private TrackConfig ukTrack;
       // ... 其他軌道
       private BigDecimal defaultLivingCost; // 階段專屬附加費
   }
   ```
   透過這種階層化設計，計算引擎可以直接取得一個包含「費用、年限、開學月份」的完整 `TrackConfig` 物件，而不需要在扁平的變數海中撈取對應的值。

4. [x] **階段式 Profile 擴充與 One-to-One 映射**
   未來的設計方向會將現有 `ChildProfile` 中所有與「教育」相關的屬性（各階段 Track 選擇、手動覆寫的費用等）抽離，集中至獨立的 `ChildEducationSettings` 中。兩者將保持 One-to-One 的關聯（可透過 `@Embedded` 或獨立實體設計）。為確保過渡期的相容性，第一階段採「只增不減」原則，不會刪除 `ChildProfile` 內原有的舊欄位，讓既有引擎繼續安全運作。新設定將直接放置於新的子套件 `ch.lin.finance.hub.backend.api.domain.model.profile.child.education` 中，以便日後管理與擴充。

5. [x] **Multi-User 架構升級與技術債修復**
   - **全面升級 MultiUserBaseEntity**：由於專案處於開發階段（無 DB Migration 包袱），且核心模擬引擎均未依賴 `userProfileId`，我們將**直接讓既有的 `ChildProfile` 以及新的 `ChildEducationSettings` 雙雙繼承 `MultiUserBaseEntity`**，讓它們直接享有家族共享（`familyFilter`）與權限隔離（`ownerFilter`）的能力。
   - **徹底拔除 userProfileId**：在 `ChildProfile` 升級後，必須直接刪除實體中舊有的 `userProfileId` 欄位，由底層 JPA Auditing 原生接管的 `ownerUserId` 全權取代，一次把技術債清空。
   - **解除 Entity 關聯綁架與 Engine 解耦 (拔除 userProfileId 的必備前置作業)**：
     1. **砍斷 ORM 綁架**：將 `UserProfile` 實體中的 `children` (`@OneToMany`) 關聯徹底刪除，解除舊有外鍵的強依賴。
     2. **改寫 Service 存檔機制**：修改 `ChildProfileServiceImpl`，讓小孩的新增與刪除直接透過 `ChildProfileRepository` 存取，而非透過 `userProfile.getChildren().add()` 來觸發 Cascade。
     3. **重構 Context Builder 與 Calculator**：修改 `SimulationContext` 增加獨立的 `List<ChildProfile>` 欄位。讓外層 Orchestrator (如 `SimulationContextBuilder`) 直接透過 Repository 撈取小孩並塞入 Context；同時修改 `IncomeExpenseCalculator`，讓它直接讀取這個獨立的 List，徹底杜絕 `context.getProfile().getChildren()` 這種 ORM 滲透引擎的寫法。
   - **Controller 架構解耦**：修改現有的 `ChildProfileController`，將所有端點 (`GET`, `POST`, `PATCH`, `DELETE`) 方法簽章中的 `@AuthenticationPrincipal Jwt jwt` 參數徹底拔除。改由 `ChildProfileService` 內部統一呼叫 `SecurityUtil.getCurrentUserId()` 來獲取身分，確保 Web 層乾淨無依賴。

6. [x] **Clean Architecture 的完整垂直切片 (Full Vertical Slice) 與雙軌 API 實作**
   第一階段的目標「絕對不只有」建立 Domain Entity 本身。我們必須伴隨完整的 API 與應用層邊界。除了上述針對 `ChildProfileController` 的身分驗證解耦之外，我們**絕對不能**去污染其現有的 DTO/Mapper 欄位。相反地，我們必須建立一套平行且完全獨立的新 API 結構，並涵蓋以下兩個範圍：
   - **前提：1~5已經的內容都已完成，但需檢視有無落差，有落差需在此Phase 1-6進行修正後，才可進行後續實作**
   - **實作範圍一：系統全域預設 (EducationConfig)**
     - 職責：負責提供系統層級各學制軌道（TrackConfig）的標準預設值。
     - **API (Controller)**：建立 `EducationConfigController`，提供唯讀（或管理員用）端點。請對齊既有 `TaxRuleConfig`, `TaxRuleConfigService`, `TaxRuleDefaultProperties`, `TaxRuleConfigFactory`, `TaxRuleConfigController` 的設計慣例，將 Endpoint 路徑定為 `GET /configs/education`。
     - **配置與工廠模式 (Properties & Factory)**：參考 `TaxRuleDefaultProperties` 的架構，建立 `EducationConfigDefaultProperties` 與 `EducationConfigFactory`。
     - **環境變數綁定**：必須在 `application.properties` 及各環境設定檔 (`.env.base`, `.env.dev` 等) 中建立預設的 Education Config 參數，確保即使資料庫尚無資料，系統在啟動時也必定有一組 Fallback 的預設設定可供計算引擎使用。
   - **實作範圍二：使用者個人設定覆寫 (ChildEducationSettings)**
     - 職責：負責讀寫特定小孩的專屬覆寫設定（StageOverride），與 `childProfileId` 一對一綁定。
     - **API (Controller)**：建立 `ChildEducationSettingsController`。無須在 API 參數中手動宣告 `@AuthenticationPrincipal` 提取 `userId`，底層 Service 會自動透過 `SecurityUtil` 與 JPA Auditing 機制完成權限隔離與寫入歸屬。
   - **通訊與轉換 (DTO, Mapper, Command, Result 嚴格邊界)**
     - 為了對齊現有架構，Controller 接收到 `Request DTO` 後，**絕對不可**直接傳給 Service 或直接組裝 Entity。
     - 必須建立專屬的 Mapper，將 `Request DTO` 轉化為 Application 層的 `Command` 物件（例如 `UpdateChildEducationSettingsCommand`）。
     - Service 層只接收 `Command` 進行業務邏輯與驗證，並回傳 `Result` 物件。
     - Controller 拿到 `Result` 後，再次透過 Mapper 轉為 `Response DTO` 以 `ResponseEntity` 回傳。嚴格阻絕 Domain Entity 滲透到 Web 層。
   - **應用與持久層 (Service & Repository)**
     - 建立對應的 Application Service（例如 `ChildEducationSettingsService`），負責處理資料的合併邏輯與資料庫存取。
     - **跨實體資安防護 (Cross-Entity Validation)**：當前端呼叫 `PUT /profile/children/{childProfileId}/education-settings` 時，Service 只需注入 `ChildProfileRepository` 並單純呼叫 `findById(childProfileId)` 即可。因為 `ChildProfile` 已經升級繼承了 `MultiUserBaseEntity`，底層的 `OwnerFilterInterceptor` 會在每一次 Web Request 中自動掛載 Hibernate Filter。這意味著系統會自動幫我們在 SQL 加上 `WHERE owner_user_id = ?` 的過濾條件。只要查得到這名小孩，就代表他確實屬於當下操作者，可以安全地繼續寫入 `ChildEducationSettings`。

### 重新盤點後的雙層設定表

| 領域概念 (Domain Concept) | 參數與欄位設計 (Config 預設 vs Settings 覆寫) | 說明與理由 (為何一般FP做不到) |
| :--- | :--- | :--- |
| **幼稚園階段 (Preschool)**<br>*(三軌：Home, Daycare, Intl)* | **`EducationConfig` (預設)**:<br>- `[增]` 針對 Home/Daycare/Intl 分別定義 `tuition`, `duration`, `startMonth`, `termsPerYear`, `entranceFee`。<br><br>**`ChildEducationSettings` (覆寫)**:<br>- 選擇軌道 (`preschoolTrack`)<br>- `[增]` `manualPreschoolTuition`, `manualPreschoolDuration`, `manualPreschoolStartMonth` | **[核心痛點解決]** 允許家長設定「國際幼稚園 (9月開學, 4年)」，上了小學轉回「日本公立小學 (4月開學, 6年)」。計算器只要順著月份走，就能完美銜接跨國學制。 |
| **小學階段 (Primary)**<br>*(三軌：Public, Private, Intl)* | **`EducationConfig`**:<br>- `[增]` 針對 3 個軌道分別定義 `tuition`, `duration`, `startMonth`, `termsPerYear`, `entranceFee`。<br><br>**`ChildEducationSettings`**:<br>- `primaryTrack`<br>- `[增]` `manualPrimaryTuition/Duration/StartMonth` | 同上。一般 FP 系統把開學月份全域寫死，導致跨學制那一年的月份會算錯，甚至遺漏學費。 |
| **中/高中階段 (Middle/High)**<br>*(三軌：Public, Private, Intl)* | **`EducationConfig`**:<br>- `[增]` 分別定義 `tuition`, `duration`, `startMonth`, `termsPerYear`, `entranceFee`。<br>- `[增]` 附加費預設：`jukuCost` (補習費)<br><br>**`ChildEducationSettings`**:<br>- `middleTrack` / `highTrack`<br>- `[增]` `manualTuition/Duration/StartMonth`<br>- `[增]` `manualJukuCost` (補習覆寫) | 在東亞升學主義下，中高中的補習費極高；歐美則有才藝費。將其獨立出 `JukuCost` 才能反應現實。 |
| **大學階段 (University)**<br>*(六軌：National, PrivateArt, UK, Canada, Taiwan, Australia)* | **`EducationConfig`**:<br>- `[增]` 針對 6 個軌道分別定義 `tuition`, `duration`, `startMonth`, `termsPerYear`, `entranceFee`。<br>- `[增]` 留學生活費預設：`livingCost`<br><br>**`ChildEducationSettings`**:<br>- `universityTrack`<br>- `[增]` `manualTuition/Duration/StartMonth`<br>- `[增]` `manualLivingCost`<br>- `[增]` `isAwayFromHome` | **[留學支援]** 英/澳大學通常3年，台/美4年，醫學6年。除了學制長短不一，海外住宿與生活費甚至高於學費。如果不將此綁定在軌道屬性中，模擬將會嚴重失真。 |


### 第二階段重構草案：計算機與依賴解耦 (3-Step Pipeline)

為確保未來的 `EducationExpenseCalculator` 能保持為「無副作用的純計算機」，並優雅解決「一年兩種學制與跨國開學月落差」的難題，未來第二階段將採用以下「三部曲」設計模式，嚴格貫徹單一職責原則 (SRP)：

1. **決策合併：`EducationProfileResolver` (Domain Service)**
   - **任務**：專職負責將 `ChildEducationSettings`（個人客製覆寫）與 `EducationConfig`（全域預設）進行合併 (Merge) 與結構平坦化。
   - **範例 (什麼是 Merge 與平坦化？)**：假設全域 Config 規定預設年學費是 5 萬、9 月開學。而該小孩的 Settings 覆寫年學費為 6 萬、分 3 期繳納 (`manualTermsPerYear` = 3)。Resolver 比較後決定最終年學費為 6 萬、開學月為 9。接著，**Resolver 會自動推算收費月份**：依據期數將 12 個月平分 (間距為 4 個月)，並以 9 月為起點推算出 `[9, 1, 5]` 三個收費月。最後產出的 Profile 裡，該階段就會直接平坦化為 `{finalAnnualTuition: 6萬, finalStartMonth: 9, finalPaymentMonths: [9, 1, 5]}`。透過提早 Merge 與自動推算，全域 Config 就能保持最簡單的設定，而後續的計算機也不必再寫一堆判斷邏輯。
   - **輸出**：`ResolvedEducationProfile`。這是一個單純的平坦對照表，僅記錄該小孩各階段「最終決議」的年學費、讀幾年、開學月，以及明確的收費月份陣列 (`finalPaymentMonths`)。**（此階段不看生日、不牽涉絕對時間）**

2. **時間軸投影：`EducationTimelineProjector` (Domain Service)**
   - **任務**：負責「排積木」。結合小孩的 `birthDate` 與上一步的 `ResolvedEducationProfile`，把相對的就讀年數（轉換為月數）與開學月，展開成一條**絕對的西元年月時間軸**。
     - **補充規則一（起點判定與 NONE 階段處理）**：須明文定義「哪一個階段算小孩人生第一個入學階段」。若某階段（如幼稚園）為 `NONE`（沒讀），則應直接跳過不產生任何 period，並用下一階段（如小學）加上該階段的標準入學年齡與生日回推絕對起點。
     - **補充規則二（一次性入學金）**：於該階段的絕對起點月份，額外加上 `entranceFee`（一次性入學金），不影響其他月份的計算邏輯。
   - **範例 (排積木邏輯與輸出)**：
     - **輸入**：小孩生日為 `2017-08`。Profile 中決議「小學：讀 72 個月，4 月開學」、「國中：讀 36 個月，9 月開學」。
     - **邏輯**：Projector 算出身滿 6 歲的第一個 4 月是 `2024-04`（於此月加入入學金）。往後推 72 個月，小學區間為 `2024-04 ~ 2030-03`。接著，因為國中是 9 月開學，所以下一個起點落在 `2030-09`（於此月加入入學金），往後推 36 個月，國中區間為 `2030-09 ~ 2033-08`。
     - **輸出**：產出的是被打散的離散事件陣列，例如 `[ {2024-03: 入學金 1萬}, {2024-03: 學費 5萬}, {2024-09: 學費 5萬}... ]`。空窗期自然不會有任何事件產生，完美反映跨國學制的真實情況。
   - **輸出**：`EducationAbsoluteTimeline`。這是封裝所有 `EducationPaymentEvent` (離散扣款事件) 的容器物件，負責提供 Calculator 按月查詢的 API。

3. **純粹的計算機：`EducationExpenseCalculator` (單純加總)**
   - **任務**：最純粹的提款機。只接收算好的絕對時間軸與目標年份，嚴禁在內部呼叫 Repository。
   - **簽章設計**：
     ```java
     public BigDecimal calculateAnnualCost(int targetYear, EducationAbsoluteTimeline timeline)
     ```
   - **實作邏輯**：跑一個 1 月到 12 月的迴圈。直接問 timeline：「今年的這一個月份要付多少錢？」，然後無腦加總。

### 架構實作參考指引 (Implementation Guidelines)

以下為針對上述依賴解耦設計所提供的具體實作建議與藍圖。實作者在進行開發前可先以此為基礎進行評估，若發現潛在盲點或有更佳的架構切分建議，皆可提出討論以進一步完善計畫：

1. **核心領域物件的預期內部結構**：
   - **`ResolvedEducationProfile`**：只需包含一個 `Map<EducationStage, ResolvedStageData>`，用來取代原本提供選擇用的巢狀 Track 菜單。
   - **`EducationAbsoluteTimeline`**：這是封裝所有離散事件的「容器 (Container)」，提供如 `getEventsForMonth(YearMonth)` 的查詢方法。內部不應該是「連續區塊」，而是由 Projector 提前攤平的「離散繳費事件 (Discrete Payment Events)」。
     ```java
     enum PaymentType {
         TUITION,       // 定期學費（依據 termsPerYear 分期扣款）
         ENTRANCE_FEE,  // 一次性入學金（僅在入學首期扣款）
         JUKU_COST,     // 補習/才藝費（中、高中軌道特有）
         LIVING_COST    // 留學住宿與生活費（大學海外軌道特有）
     }

     class EducationPaymentEvent {
         YearMonth paymentMonth; // 實際「扣款」的絕對年月（邏輯應繳月 + paymentOffsetMonths）
         BigDecimal amount;      // 該次扣款金額
         PaymentType type;       // 費用種類，可供未來長條圖細項拆分與顏色標示使用
     }
     ```
     **為何採用離散事件設計？ (解決提前繳費與過渡年雙重計算)**
     未來可在 `EducationConfig` 擴充 `paymentOffsetMonths`。`Projector` 排積木時，會將邏輯上的應繳月（如 4 月）加上偏移量。若設定 `-1`（提前1個月），則入學金與該期學費的 `paymentMonth` 都會被精準標記為 **3 月**。當現行模擬器（`YearlySimulationPipeline`）在過渡年（例如從 4 月起始）進入計算迴圈時，它只會處理 `currentMonth` (4) 之後的事件。由於該期學費與入學金已提早落在 3 月，迴圈會自然略過它們，代表「這筆錢在模擬開始前就已從現金快照中扣除，不再重複扣款」，完美相容現行的演算法。
   - **領域物件純粹性原則 (Pure Domain Objects)**：`EducationPaymentEvent` 與 `EducationAbsoluteTimeline` 內**嚴禁**放入與數學計算無關的 Metadata（如 `String trackName`、公立/私立標記等）。UI 顯示所需的軌道名稱，應保留在初期的 `ResolvedEducationProfile` 中。計算機只負責產出總金額，最後由外層 Application Service 負責將「純數字」與「Metadata」一起打包成 Result DTO 交給 Controller。保持計算機與時間軸的絕對乾淨。

2. **「誰」負責串接？ (Orchestration)**：
   **嚴禁在上述三個 Domain Service 內部建立 Repository 依賴。** 負責從 DB 拿資料的責任，應落在**原有的外層 Application Service**。該服務的標準作業流程為：
   - **Step 1**: 從 DB 撈取全域 Config 與該名小孩的 Settings。
   - **Step 2**: 呼叫 `Resolver` 產出 `Profile`。
   - **Step 3**: 呼叫 `Projector` 傳入 Profile 與生日，產出 `AbsoluteTimeline`。
   - **Step 4**: 呼叫 `Calculator` 將 Timeline 轉換為 `Map<Integer, Long>`（年份 -> 該年教育費總額）或 `long[]` 陣列，並直接**快取至 `SimulationContext` 內**。未來的 `IncomeExpenseCalculator` 在 GA 的百萬次迴圈中，**完全不需要再跑任何計算邏輯，只需做 O(1) 的陣列/Map 查表即可**。

   **2.1. 串接細節事實補充 (Orchestration Context Facts)**：
   - **Orchestrator 具體類別**：上述的「外層 Application Service」，在既有系統中對應的類別為 `ChildProfileServiceImpl`（負責單一小孩圖表投影）與 `IncomeExpenseCalculator`（負責模擬引擎收支計算），此二者為調用上述 3-Step Pipeline (`Resolver` -> `Projector` -> `Calculator`) 的核心進入點。
   - **資料撈取範圍**：Step 1 實際撈取範圍須包含該小孩的 `ChildProfile`，以提供 Step 3 Projector 計算絕對時間軸所必需的 `birthDate`。
   - **入學年齡來源**：各階段的標準入學年齡（如小學 6 歲入學），不在 `EducationConfig` 的定義範疇內，屬於 Domain 層的硬編碼常數 (Hardcoded Constants)，由 Projector 內部直接定義並使用。
   - **分期制精準計價與提前繳納 (Term-based Billing & Payment Offset)**：為解決「年中轉學」與「提前繳費」問題，Projector 在轉換時，必須將 `tuition`（年學費）除以「每年收費次數」變成單期學費，並根據 `paymentMonths` 加上 `paymentOffsetMonths` 計算出「實體扣款月」。Calculator 逐月巡覽時，只要單純去 Timeline 查詢當月是否有 `EducationPaymentEvent` 即可。如此不僅能完美應對中途退學、三學期制，也能確保過渡年不會重複扣款。

### 第二階段細部實作分工與執行步驟 (Phase 2 Implementation Roadmap)

為確保計算引擎重構時邏輯無盲點、邊界清晰且不發生過度複雜化（保持 GA 與模擬器之 O(1) 年度查表效能），第二階段採 3 個子階段（Sub-phases）逐步推進。

#### 核心業務規則共識 (Aligned Business Rules)
1. **起點判定與積木伸縮**：
   - 基準起點依賴小孩 `birthDate`。若有讀幼稚園，標準起點為滿 3 歲入學（托兒所滿 1 歲）；若選 `NONE`（沒讀），則直接從小學滿 6 歲入學起算。
   - 若小孩從 K4（4 歲）入學，家長只需將幼稚園 `manualDuration` 覆寫為 24 個月（2 年），積木即自動縮減為 2 年，無須在 Domain 或 Entity 新增額外起點欄位。
   - 後續階段（國中、高中、大學）採「順延排積木」：順著上一階段結束月份，尋找下一個符合該軌道 `startMonth` 的月份作為新積木起點，空窗期自然不產生費用。
2. **學費 vs 生活費／補習費之計算粒度解耦**：
   - **學費 (`tuition`)**：非連續且年中跨學制跳躍，由 `Projector` 依 `paymentMonths` 在西元年月時間軸上展開為離散事件。
   - **生活費 (`livingCost`) 與補習費 (`jukuCost`)**：性質為日常年度支出，不作微觀月份拆分，直接隨就讀年份以「年」計入總支出。模擬引擎核心迴圈（收入、年金、房貸、稅金）**完全保持年級別**，GA 保持 O(1) 極致效能。
3. **收費月份推算與期數白名單**：
   - 所有覆寫欄位均支援 `null`（退回全域 Config 預設）。
   - 支援期數白名單為能整除 12 個月的整數：`{1, 2, 3, 4, 6, 12}`（包含年繳、二學期制、三學期制 Trimester、季制 Quarter、雙月繳、月繳），由 `startMonth` 與期數自動等分計算，使用者無須手動勾選月份。
4. **防意外安全防護機制 (Safety & Risk Mitigation)**：
   - **只增不減原則**：既有的 `EducationExpenseCalculatorOld` 保持原樣不動，在 Phase 2 期間絕不刪除，確保現有模擬運算有 100% 備援。
   - **純粹領域服務 (Zero DB/ORM Dependency)**：`Resolver`、`Projector`、`Calculator` 皆為無狀態純 Java 領域物件，嚴禁注入 Repository 或 Spring Data JPA，完全阻絕 ORM 滲透。
   - **零破壞性**：Phase 2-A 與 Phase 2-B 為純新增程式碼，不改動既有流程；直到 Phase 2-C 才進行 Orchestration 切換。

---

#### 🔹 Phase 2-A：核心領域模型與第一部曲（Resolver 決策合併）
* **涉及檔案與目錄**：
  - 目標目錄：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/`
  - 新增領域模型：
    - `EducationStage.java`：階段枚舉 (`PRESCHOOL`, `PRIMARY`, `MIDDLE`, `HIGH`, `UNIVERSITY`)
    - `PaymentType.java`：扣款性質枚舉 (`TUITION`, `ENTRANCE_FEE`, `JUKU_COST`, `LIVING_COST`)
    - `ResolvedStageData.java`：單一階段合併後的平坦數據（包含 `paymentMonths` 收費月列表）
    - `ResolvedEducationProfile.java`：封裝所有階段之決策容器 (`Map<EducationStage, ResolvedStageData>`)
  - 新增領域服務：
    - `EducationProfileResolver.java`：無依賴純計算機，負責 Merge 全域 `EducationConfig` 與個人 `ChildEducationSettings`
* **待辦項目清單 (Checklist)**：
  - [x] 建立 `EducationStage` 與 `PaymentType` 枚舉
  - [x] 建立 `ResolvedStageData` 與 `ResolvedEducationProfile` 領域物件
  - [x] 實作 `EducationProfileResolver`：
    - [x] 支援各階段軌道比對與 `null` 欄位自動 fallback 至 `EducationConfig`
    - [x] 支援 `NONE` 軌道之跳過標記
    - [x] 實作 `termsPerYear` 白名單驗證 `{1, 2, 3, 4, 6, 12}` 與收費月份推算（`startMonth` + 間距取模）
  - [x] 撰寫毫秒級獨立單元測試 `EducationProfileResolverTest`（不啟動 Spring Context、不連 DB，秒級驗證）
* **驗收標準**：
  - [x] 無覆寫時能 100% 正確 fallback 至預設值。
  - [x] 三學期制（9 月開學、3 期）能自動算出 `[9, 1, 5]`。

---

#### 🔹 Phase 2-B：第二部曲（Projector 時間軸投影）與第三部曲（Calculator 計算機）
* **涉及檔案與目錄**：
  - 目標目錄：同上 `.../education/`
  - 新增領域模型：
    - `EducationPaymentEvent.java`：離散扣款事件（`YearMonth paymentMonth`, `BigDecimal amount`, `PaymentType type`）
    - `EducationAbsoluteTimeline.java`：時間軸容器，提供按月與按年查詢介面
  - 新增領域服務：
    - `EducationTimelineProjector.java`：排積木投影機，依生日與 Profile 展開西元年月時間軸
    - `EducationExpenseCalculator.java`：無副作用純計算機，加總年度事件並提供 `Map<Integer, Long>` 查表轉換
* **待辦項目清單 (Checklist)**：
  - [x] 建立 `EducationPaymentEvent` 與 `EducationAbsoluteTimeline`
  - [x] 實作 `EducationTimelineProjector`：
    - [x] 實作各階段標準入學年齡常數（幼稚園 3 歲、小學 6 歲等）
    - [x] 實作「順延排積木」算法：依據 `durationMonths` 銜接下一階段的 `startMonth`
    - [x] 在起點月加入 `ENTRANCE_FEE` 事件
    - [x] 將年度學費依 `termsPerYear` 拆分並掛入各收費月份之 `TUITION` 事件
  - [x] 實作 `EducationExpenseCalculator`：
    - [x] `calculateAnnualCost(int targetYear, EducationAbsoluteTimeline timeline)`
    - [x] `buildAnnualCostMap(EducationAbsoluteTimeline timeline)` 回傳 `Map<Integer, Long>`
  - [x] 撰寫獨立單元測試 `EducationTimelineProjectorTest` 與 `EducationExpenseCalculatorTest`
* **驗收標準**：
  - [x] 4 月公立轉 9 月國際之年份，上半年與下半年學費能共存且正確計算。
  - [x] 大學最後一年最後幾個月學費如實計入對應年份，不再蒸發。

---

#### 🔹 Phase 2-C：外層服務串接與快取整合（Orchestration & Verification）
* **涉及檔案與目錄**：
  - `hub-app/.../profile/child/ChildProfileServiceImpl.java`
  - `hub-app/.../simulation/core/model/SimulationContext.java`
  - `hub-app/.../simulation/core/factory/SimulationContextFactory.java`
  - `hub-app/.../simulation/core/calculator/IncomeExpenseCalculator.java`
* **待辦項目清單 (Checklist)**：
  - [x] 修改 `ChildProfileServiceImpl.getEducationProjections()`：串接 3-Step Pipeline，圖表估算改吃新時間軸
  - [x] 在 `SimulationContext` 內新增 `educationCostCache` (`Map<Long, Map<Integer, Long>>` 或整合結構)
  - [x] 在 `SimulationContextFactory` 於模擬啟動前預先調用 Pipeline 產出快取並注入 Context
  - [x] 修改 `IncomeExpenseCalculator`：將迴圈內的教育費計算改為 O(1) 查表，並加上就讀年份的年計 `jukuCost` 與 `livingCost`
  - [x] 靜態編譯校驗與模擬流程確認，確保完全相容
* **驗收標準**：模擬引擎執行正常，GA 與 Sandbox 維持極致效能，無效能衰退或計算偏差。


### 第三階段重構草案：使用者設定畫面 (Settings UI Revamp)

**相關檔案**
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx`（小孩教育設定畫面，本階段要修改的地方）
- `hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts`（對應的資料存取 hook，欄位型別需要跟著新架構調整）

**現況（開工前畫面目前的實際行為）**

目前這個畫面是一個 Dialog（彈出視窗），一個小孩對應一筆資料，畫面分兩欄：
- 左欄「Basic Info」：姓名、生日；如果這個小孩任何一個教育階段選了 INTL（國際學校）軌道，會額外出現一個全域的「INTL School Start Date」欄位（月＋日各一個下拉選單），這一個設定會套用到這個小孩「所有」選了 INTL 的階段，不能每個階段分開設定。
- 右欄「Education Track」：五個教育階段（Preschool 幼稚園／Primary 小學／Middle 國中／High 高中／University 大學），每個階段各自只有兩個東西：一個「選軌道」的下拉選單，跟一個「Manual Cost (¥) - Optional」的手動金額輸入框（填了就取代系統預設金額，不填就用系統預設）。
- 另外有一個「Estimate」按鈕，會另外開一個 Dialog，用長條圖顯示這個小孩「每一個年齡」的教育費預估金額：X 軸是年齡，Y 軸是金額，長條顏色依教育階段（幼稚園/小學/國中/高中/大學）上色區分。

**目標**：把這個畫面改成讀寫新架構（`EducationConfig` / `ChildEducationSettings` / `TrackConfig` / `StageOverride`）的資料，同時盡量不增加一般使用者平時會看到的欄位數量。具體設計如下：

1. **每個階段預設顯示的兩個欄位（選軌道、手動金額）維持不變**，行為跟現在完全一樣：選軌道對應新架構的 `trackChoice`，手動金額對應新架構的 `manualTuition`。唯一要改的是手動金額輸入框的 placeholder 文字，從「Manual Cost (¥) - Optional」改成「**Manual Annual Tuition** (¥) - Optional」，明確標示這格填的是「一整年」的金額——因為新架構下，這筆年費之後會依照 `termsPerYear`（收費期數）被拆成好幾期分別收費，如果使用者誤以為這格填的是「一期的金額」，會填錯數字。

2. **每一個已經選好軌道（軌道選項不是 NONE）的階段，在「選軌道」與「手動金額」下方，新增一個預設收起來的「進階設定 (Advanced)」摺疊區塊**（用一個齒輪圖示或文字連結觸發展開／收起，平時收起不佔畫面空間）。點開後才會看到以下三個新欄位，分別對應新架構 `StageOverride` 的三個覆寫欄位：
   - **Manual Duration**：**轉換邏輯統一於前端處理**。畫面上讓使用者填寫直覺的「年」（例如 3、4、6），前端送出 API 前自動乘以 12（`year * 12`）以「月」為單位傳入 `manualDuration`；從 API 讀取時則除以 12 轉回「年」顯示。後端 API、資料庫與 Domain 一律保持唯一的「月」基準，兼顧使用者體驗與系統底層的一致性與擴充性。
   - **Manual Start Month**：1 月到 12 月的下拉選單，對應 `manualStartMonth`。
   - **Manual Terms Per Year**：對應 `manualTermsPerYear`；使用者不填的話，就沿用 `TrackConfig` 全域預設的 `termsPerYear`，不會強迫使用者一定要填。
   這三個欄位平時完全不會被一般使用者看到，只有真的有特殊情況（例如提早入學、跳級、學校收費期數跟系統預設不同）的使用者，才需要點開「進階設定」去填寫。

3. **University（大學）階段的「進階設定」摺疊區塊裡，額外多兩個欄位**（其他四個階段不需要這兩個欄位）：
   - **Away From Home**（開關 Toggle）：對應新架構的 `isAwayFromHome`。
   - **Manual Living Cost**（¥）：對應新架構的 `manualLivingCost`，只有在「Away From Home」開關被打開時，才顯示這一格輸入框；關閉時隱藏，不佔畫面空間。

4. **移除左欄現有的全域「INTL School Start Date」欄位（月＋日兩個下拉選單），新畫面不再保留這個欄位。** 原因：這個欄位現在的作用，是讓一個小孩「所有選了 INTL 的階段」共用同一組開學月／日設定；但新架構下，每一種學制軌道本身（`TrackConfig`，例如「日本國際小學」「英國大學」）就會各自定義自己的 `startMonth`，這個全域欄位原本的功能已經被新架構本身取代掉了。如果某個小孩的某個特定階段，真的需要跟系統預設不一樣的開學月，改用第 2 點「進階設定」裡那個階段自己的 Manual Start Month 覆寫即可，不需要再額外維護一個獨立於教育階段之外的全域欄位。

5. **「Estimate」試算結果 Dialog（年齡長條圖）Tooltip 擴充**：
   - **後端擴充**：在 `EducationProjectionResult` 與 `EducationProjectionDto` 新增 `stageRange`（String，如 `"2024-04 ~ 2030-03"`）或 `stageStartYearMonth` / `stageEndYearMonth` 欄位，由 `ChildProfileServiceImpl` 在比對 `timeline.getStageIntervals()` 時一併帶入。
   - **前端呈現**：滑鼠移到長條圖某一年長條上時，Tooltip 除了顯示金額與階段名稱外，同步顯示該學制階段的絕對起訖年月。使用者可直接驗證排積木時間軸是否精準銜接，顯著提升操作反饋與 UX。

6. **「一次性入學金」（本文件「補充建議」第 4 點提到的 `entranceFee`）不開放使用者覆寫，只存在 `TrackConfig` 全域預設層，因此 Phase 3 不需要為這件事新增任何畫面欄位。**

7. **智能時間軸銜接檢查與防呆警示 (Overlap Detection & Live Feedback)**：
   - **即時起訖時間徽章 (Live Timeline Badge)**：在各階段卡片標題旁顯示由前端根據生日與時長即時推算出的起訖年月徽章（例如 `2024/04 ~ 2030/03 (6年)`），任何時長或月份變更皆連鎖即時反應，使用者無須點開 Estimate 即可直觀確認當前學制時間段。
   - **重疊衝突自動檢查 (Conflict Detection)**：自動校驗相鄰階段之銜接邏輯（若下一階段入學時間早於上一階段畢業時間，即判定為時間軸重疊）。一旦偵測到衝突，該階段立即跳出紅色醒目警示（`⚠️ Timeline Overlap: Start date cannot be earlier than the previous stage's completion date. Please check duration or start month.`），並暫時停用「儲存」按鈕，從源頭徹底杜絕時間矛盾之異常資料寫入。

8. **後端 API 縱深防禦與防呆校驗 (Server-side Validation & 400 Bad Request)**：
   - **縱深防禦原則 (Defense in Depth)**：前端校驗保障即時 UX 體驗，後端 API 則作為核心數據完整性與系統安全的終極防線，不可盲目信任 Client 端請求（防止 curl/Postman/第三方腳本繞過前端直接打 API）。
   - **DTO 規格檢核 (Bean Validation)**：在 `StageOverrideRequest` 增加檢核防禦：
     - `manualStartMonth` 必須介於 1 ~ 12（`@Min(1) @Max(12)`）。
     - `manualTermsPerYear` 必須符合白名單 `{1, 2, 3, 4, 6, 12}`，杜絕非法值在計算層引發未捕獲的 500 內部伺服器錯誤。
     - `manualDuration` 必須為正整數且介於合理區間（例如 `@Min(1) @Max(240)`）。
   - **業務合理性防呆 (Business Timeline Validation)**：
     - 在 `ChildEducationSettingsService.updateSettings()` 寫入資料庫前，將更新後的設定傳入 `EducationTimelineProjector` 試算時間軸。
     - 若檢測到任何時長推移導致學制年齡遠超常規邊界或邏輯異常，立即拋出業務校驗例外，由 Global Exception Handler 轉為標準 **HTTP 400 Bad Request**，並附帶具體錯誤訊息（如 `"Invalid education timeline: Stage duration causes entrance age to exceed allowable limits."`），保證資料庫永遠保持高度一致與潔淨。

### 第三階段細部實作分工與執行步驟 (Phase 3 Implementation Roadmap)

為確保使用者設定畫面的重構與前後端資料串接有條不紊，避免「前端介面未完成時難以驗證後端」、「後端校驗未定案導致前端表單重工」等問題，第三階段比照第二階段切分為 **3 個具體子階段（Sub-phases）**，採「由下而上、由後往前（Contract-First & Bottom-Up）」的順序推進：

#### 執行順序與分工邏輯 (Execution Order Rationale)
1. **為什麼先做 Phase 3-A（後端縱深防禦與 Projection 欄位）？**
   - 先行確立後端 API 資料契約（Contract），包含 Bean Validation 規則、400 Bad Request 例外處理機制，以及圖表估算所需的新欄位（`stageRange`）。
   - 確保無論前端如何改動，後端資料完整性與防呆邊界皆已具備 100% 免疫力，並可透過單元測試秒級驗收。
2. **為什麼接著做 Phase 3-B（前端資料存取層與型別）？**
   - 介於後端 API 與視覺元件之間的黏著層。先於前端定義精確的 TypeScript 介面，並實作「年 ↔ 月」的純邏輯換算函式與 API 呼叫方法。
   - 透過 API 呼叫與 mock 驗證通訊順暢，避免在開發複雜 UI 時同時排查網絡或型別錯誤。
3. **為什麼最後做 Phase 3-C（設定畫面重構與 UX 智能防呆）？**
   - 在後端與資料存取層皆已穩固的前提下，專注於介面佈局（移除舊全域欄位、加入進階收合區塊）以及使用者體驗的極致打磨（即時起訖時間徽章、相鄰重疊英文警告、Estimate Tooltip 呈現）。

#### 原本第三階段規劃 vs 細切子階段對照表 (Requirements Mapping Table)
明確標註原本第三階段的各項目標，分別歸屬至哪一個細切子階段中執行：

| 原本第三階段設計目標 | 核心職責與內容 | 歸屬實作子階段 |
| :--- | :--- | :--- |
| **目標 1：階段預設欄位與 Placeholder 調整** | 維持選軌道、手動金額；placeholder 改為 `Manual Annual Tuition (¥) - Optional` | **Phase 3-C** (UI 介面) |
| **目標 2：進階設定摺疊區塊與換算** | 齒輪展開/收起；時長年↔月換算、開學月份選單、收費期數選單 | **Phase 3-B** (換算函式)<br>**Phase 3-C** (進階區塊 UI) |
| **目標 3：大學專屬外宿生活費欄位** | Away From Home Toggle 與 Manual Living Cost 輸入框（連動顯隱） | **Phase 3-B** (DTO 型別)<br>**Phase 3-C** (大學卡片 UI) |
| **目標 4：移除舊版全域 INTL 月日欄位** | 刪除左欄全域選單，改由各階段獨立設定取代 | **Phase 3-C** (UI 清理) |
| **目標 5：Estimate 圖表 Tooltip 擴充** | 顯示各階段絕對起訖區間（如 `2024-04 ~ 2030-03`） | **Phase 3-A** (後端 DTO/Result)<br>**Phase 3-C** (Tooltip UI) |
| **目標 6：一次性入學金不開放覆寫** | 僅保留於系統全域配置層，畫面無須新增欄位 | （架構確認，無額外工作） |
| **目標 7：即時徽章與重疊英文衝突警示** | 標題旁即時 Badge；相鄰階段重疊跳出英文 Warning 並鎖定儲存 | **Phase 3-C** (互動與 UX) |
| **目標 8：後端 API 縱深防禦與防呆校驗** | Bean Validation 檢核 + 業務時程推算違規回傳 400 Bad Request | **Phase 3-A** (後端校驗防呆) |

---

#### 🔹 Phase 3-A：後端 API 縱深防禦與 Projection 欄位擴充 (Backend Validation & Projection DTO)
* **涉及檔案與目錄**：
  - `hub-common/.../exception/InvalidEducationTimelineException.java`（新增：專屬業務異常）
  - `hub-common/.../advice/WebGlobalExceptionHandler.java`（攔截專屬異常並轉為 400）
  - `hub/.../profile/child/education/dto/UpdateChildEducationSettingsRequest.java`（為各 Override 加上 `@Valid`）
  - `hub/.../profile/child/education/dto/StageOverrideRequest.java`（加上 `@Min` / `@Max` / 白名單）
  - `hub-app/.../profile/child/education/ChildEducationSettingsServiceImpl.java`
  - `hub-app/.../profile/child/result/EducationProjectionResult.java`
  - `hub/.../profile/child/dto/EducationProjectionDto.java`
  - `hub-app/.../profile/child/ChildProfileServiceImpl.java`
  - `hub-app/.../profile/child/education/ChildEducationSettingsServiceImplTest.java`
  - `hub/.../profile/child/education/ChildEducationSettingsControllerTest.java`
  - `hub-app/.../profile/child/ChildProfileServiceImplTest.java`
* **待辦項目清單 (Checklist)**：
  - [x] 建立 `InvalidEducationTimelineException` 業務異常，並在 `WebGlobalExceptionHandler` 加上 `@ExceptionHandler` 映射為 400 Bad Request（錯誤代碼 `INVALID_EDUCATION_TIMELINE`）
  - [x] 在 `UpdateChildEducationSettingsRequest` 為五個階段的 `StageOverrideRequest` 加上 `@Valid`，確保嵌套校驗生效
  - [x] 在 `StageOverrideRequest` 補齊 Bean Validation 註解：
    - `@Min(1) @Max(12)` on `manualStartMonth`
    - `@Min(1) @Max(240)` on `manualDuration`
    - 自訂或列舉校驗 `manualTermsPerYear` 必須符合白名單 `{1, 2, 3, 4, 6, 12}`
  - [x] 在 `ChildEducationSettingsServiceImpl.updateSettings()` 加入業務時序合理性防呆：
    - 儲存前調用 `EducationTimelineProjector` 試算時間軸，若出現異常或違反邊界條件拋出 `InvalidEducationTimelineException`
  - [x] 在 `EducationProjectionResult` 與 `EducationProjectionDto` 新增 `stageRange`（String，如 `"2024-04 ~ 2030-03"`）
  - [x] 修改 `ChildProfileServiceImpl.getEducationProjections()`：在迴圈比對 `timeline.getStageIntervals()` 時填入對應階段的 `stageRange`
  - [x] 撰寫/更新單元測試，驗證非法參數（如 termsPerYear=5 或 startMonth=13）回傳 400，且 Projection 能正確回傳 `stageRange`
* **驗收標準**：
  - [x] 打 API 帶非法參數時回傳 400 Bad Request（不再拋出 500）。
  - [x] `GET /profile/children/{id}/projection` 正確回傳各年份對應學制的起訖區間字串。

---

#### 🔹 Phase 3-B：前端資料存取層與型別串接 (Frontend Hook & Type Refactor)
* **涉及檔案與目錄**：
  - `hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts`
* **待辦項目清單 (Checklist)**：
  - [x] 擴充並定義新架構 TypeScript 型別介面：
    - `StageOverrideDto` (`manualTuition`, `manualDuration`, `manualStartMonth`, `manualTermsPerYear`)
    - `ChildEducationSettingsDto` (五階段軌道、五階段 Override、`middleManualJukuCost`, `highManualJukuCost`, `manualLivingCost`, `isAwayFromHome`)
    - 擴充 `EducationProjectionDto` 增加 `stageRange?: string`
  - [x] 在 `use-child-profile.ts` 實作新 API 串接：
    - `fetchEducationSettings(childProfileId: number): Promise<ChildEducationSettingsDto>` (GET `/profile/children/{childProfileId}/education-settings`)
    - `saveEducationSettings(childProfileId: number, settings: ChildEducationSettingsDto): Promise<boolean>` (PUT `/profile/children/{childProfileId}/education-settings`)
  - [x] 實作就讀時長（Duration）「年 ↔ 月」雙向換算輔助函數：
    - 讀取時：`monthsToYears(months)` (`months / 12`)
    - 提交時：`yearsToMonths(years)` (`years * 12`)
* **驗收標準**：
  - [x] 前端 Hook 能獨立成功調用後端 `/profile/children/{childProfileId}/education-settings` 進行讀取與儲存。
  - [x] 時長單位換算精準（3 年 ↔ 36 個月），型別檢核無 TypeScript 錯誤。

---

#### 🔹 Phase 3-C：設定畫面重構與 UX 智能防呆 (UI Revamp & Interactive Feedback)
* **涉及檔案與目錄**：
  - `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx`
* **待辦項目清單 (Checklist)**：
  - [x] 畫面佈局清理：
    - 移除左欄「INTL School Start Date」全域月日選單
    - 移除舊版直接寫入 `ChildProfile` 的手動費用欄位綁定
    - 手動學費 placeholder 更新為 `Manual Annual Tuition (¥) - Optional`
  - [x] 各階段卡片重構與「進階設定 (Advanced)」摺疊區塊：
    - 軌道非 NONE 時顯示「進階設定」摺疊 Toggle（齒輪圖示或文字連結）
    - 展開後提供：Manual Duration（年）、Manual Start Month（1-12月選單）、Manual Terms Per Year（1, 2, 3, 4, 6, 12 選單）
    - 大學階段專屬：Away From Home 開關，打開時顯現 Manual Living Cost 輸入框
  - [x] 實作「即時起訖時間徽章 (Live Timeline Badge)」：
    - 根據小孩生日與當前各階段設定，在卡片標題旁即時計算並顯示 `2024/04 ~ 2030/03 (6年)`
    - 使用者修改時長或月份時，後續階段徽章連鎖即時更新
  - [x] 實作「智能重疊衝突檢查 (Conflict Detection)」：
    - 即時比對相鄰階段時間軸，若下一階段開始早於上一階段結束，跳出英文紅色警示：
      `⚠️ Timeline Overlap: Start date cannot be earlier than the previous stage's completion date. Please check duration or start month.`
    - 衝突發生時停用「儲存」按鈕
  - [x] 升級「Estimate」圖表 Dialog：
    - 長條圖 Tooltip 同步展示該年份所屬階段的 `stageRange`（如 `Primary: 2024-04 ~ 2030-03`）
* **驗收標準**：
  - [x] 介面整潔現代，進階設定平時收納不干擾一般操作。
  - [x] 智能徽章與重疊警告即時流暢，有效阻擋時間衝突。
  - [x] Estimate Tooltip 完整呈現絕對起訖年月。


### 第四階段重構：清除舊程式碼與過期欄位 (Cleanup & Decoupling)

當第二階段的新計算引擎 (`3-Step Pipeline`) 驗證無誤，並且所有的 API 皆已切換至新的 `EducationConfig` 與 `ChildEducationSettings` 後，進行最後的清理工作，徹底消除架構技術債。

為避免改動範圍過大導致 Context 負擔與隱性錯誤，本階段依序細拆為三個子階段執行：

---

#### 🔹 Phase 4-A：刪除舊版計算器與過渡期 Fallback 邏輯 (Legacy Calculator & Transitional Fallback Removal)
* **核心目標**：移除無人使用的廢棄計算類別，移除 Service 與 Factory 內的舊欄位轉接 bridge。
* **涉及檔案與目錄**：
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/EducationExpenseCalculatorOld.java` (整檔刪除)
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImpl.java`
* **待辦項目清單 (Checklist)**：
  - [x] 徹底刪除已標記 `@Deprecated(forRemoval = true)` 的 `EducationExpenseCalculatorOld.java`
  - [x] 在 `SimulationContextFactory.java` 移除 `createFallbackSettings` 過渡方法；若查無 `ChildEducationSettings`，直接使用預設設定或依賴既有設定
  - [x] 在 `ChildProfileServiceImpl.java` 移除 `createFallbackSettings` 過渡方法，完全依賴 `childEducationSettingsRepository`
  - [x] 執行單元測試確保現有模擬與試算流程不受影響
* **驗收標準**：
  - [x] 無任何類別引用 `EducationExpenseCalculatorOld`
  - [x] Service 與 Factory 不再讀取 `ChildProfile` 上的舊教育軌道與費用欄位
  - [x] `mvn test` 順利通過

---

#### 🔹 Phase 4-B：`ChildProfile` 實體純化與舊欄位刪除 (ChildProfile Entity Pure Model & API Decoupling)
* **核心目標**：讓 `ChildProfile` 徹底回歸純粹的孩童身分實體（僅保留 `name`, `birthDate` 與已繼承之 `MultiUserBaseEntity` 多租戶屬性），所有軌道與費用徹底解耦。
* **涉及檔案與目錄**：
  - `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/ChildProfile.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/command/CreateChildProfileCommand.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/command/UpdateChildProfileCommand.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/result/ChildProfileResult.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImpl.java`
  - `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/dto/CreateChildProfileRequest.java`
  - `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/dto/UpdateChildProfileRequest.java`
  - `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/dto/ChildProfileResponse.java`
  - `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/dto/ChildProfileMapper.java`
  - `hub/src/test/java/ch/lin/finance/hub/backend/api/controller/profile/child/ChildProfileControllerTest.java`
  - `hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImplTest.java`
  - `hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts`
  - `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx`
* **待辦項目清單 (Checklist)**：
  - [x] 從 `ChildProfile.java` 刪除 12 個舊欄位（`intlSchoolCutoffMonth`, `intlSchoolCutoffDay`, 5 個 `xxTrack`, 5 個 `manualXxCost`）
  - [x] 清理後端 DTO / Command / Result / Mapper，移除上述 12 個過期屬性
  - [x] 在 `ChildProfileServiceImpl.java` 建立小孩 (`createChildProfile`) 時，同步自動建立一筆預設的 `ChildEducationSettings`（預設 track 皆為 NONE）
  - [x] 更新後端相關單元測試（`ChildProfileServiceImplTest` 等）
  - [x] 更新前端 `use-child-profile.ts` 的 `ChildProfile` 介面
  - [x] 更新前端 `child-profile-section.tsx`：簡化「新增小孩」Dialog（僅輸入姓名與生日），清單卡片統一由 `ChildEducationSettings` 驅動顯示
* **驗收標準**：
  - [x] `ChildProfile` 實體純淨，不含任何教育與費用欄位
  - [x] 前後端編譯與測試全數通過，新增小孩與維護設定功能運作正常

---

#### 🔹 Phase 4-C：從 `FinanceHubConfig` 刪除 18 個舊教育費用欄位與前端同步 (System Config Legacy Fields Removal)
* **核心目標**：清除 `FinanceHubConfig` 中的歷史教育殘留欄位，使系統全域設定與 `EducationConfig` 職責分明。
* **涉及檔案與目錄**：
  - `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/system/FinanceHubDefaultProperties.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/CreateFinanceHubConfigCommand.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/command/UpdateFinanceHubConfigCommand.java`
  - `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/config/system/result/FinanceHubConfigResult.java`
  - `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/CreateFinanceHubConfigRequest.java`
  - `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/UpdateFinanceHubConfigRequest.java`
  - `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/system/FinanceHubConfigResponse.java`
  - `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigController.java`
  - `hub/src/test/java/ch/lin/finance/hub/backend/api/controller/config/FinanceHubConfigControllerTest.java` (及相關測試)
  - `hub-ui/hub-ui/src/app/finance/configs/general/components/system-config-panel.tsx`
  - `hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts`
* **待辦項目清單 (Checklist)**：
  - [x] 從 `FinanceHubConfig.java` 刪除 18 個 `edu_xxx_cost` 欄位
  - [x] 從 `FinanceHubDefaultProperties.java` 刪除 18 個預設屬性
  - [x] 清理後端系統設定之 Command, Result, DTO 與 Controller 映射
  - [x] 更新系統設定單元測試
  - [x] 從前端 `system-config-panel.tsx` 與 `use-finance-configs.ts` 移除舊的 Education Default Costs 表單區塊與 Zod schema
* **驗收標準**：
  - [x] `FinanceHubConfig` 不再包含任何教育相關欄位
  - [x] 後端全模組 `mvn clean install` 0 Errors, 0 Failures
  - [x] 前端 TypeScript 型別檢查無報錯，系統設定頁面整潔正常

---

### 第五部分：實作覆核、商務邏輯檢驗與 Code Review 答辯手冊 (Implementation Audit & Review Defense Manual)

本章節彙整本次重構的完成度狀態、核心商務邏輯數學驗證，以及提交外部（如 Claude）進行 Code Review 時的潛在質疑點與標準答辯依據。

#### 一、各階段實作落地狀態總結 (Implementation Status)
- [x] **Phase 1 (領域模型與 MultiUser 升級)**：建立 [`EducationConfig`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/education/EducationConfig.java) 與 [`ChildEducationSettings`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/education/ChildEducationSettings.java)；[`ChildProfile`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/ChildProfile.java) 繼承 `MultiUserBaseEntity`，拔除 `UserProfile.children` 關聯與外鍵滲透。
- [x] **Phase 2 (3-Step Pipeline 純領域服務)**：
  - Step 1: [`EducationProfileResolver`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationProfileResolver.java)（決策合併與收費月份推算）。
  - Step 2: [`EducationTimelineProjector`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java)（順延排積木投影絕對時間軸、法定學年年齡計算、入學金與離散學費生成）。
  - Step 3: [`EducationExpenseCalculator`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java)（年度純計算與快取表生成）。
  - Orchestration: [`SimulationContextFactory`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java) 預算快取，[`IncomeExpenseCalculator`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java) 於百萬次模擬維持 $O(1)$ 極致查表速度。
- [x] **Phase 3 (前端畫面與防呆校驗)**：[`child-profile-section.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx) 支援進階設定齒輪收合、即時時間徽章、Tooltip 絕對起訖區間（`stageRange`）；後端提供 [`InvalidEducationTimelineException`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/exception/InvalidEducationTimelineException.java) 轉 400 Bad Request。
- [x] **Phase 4 (技術債徹底清除)**：舊版 `EducationExpenseCalculatorOld` 徹底刪除；[`ChildProfile`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/ChildProfile.java) 與 [`FinanceHubConfig`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/FinanceHubConfig.java) 歷史殘留欄位徹底清空。

---

#### 二、商務邏輯核心演算法深度檢驗 (Deep-Dive Business Logic Verification)

1. **日本「早生まれ (Hayaumare)」與法定學年切齊規則**：
   - 依據日本《學校教育法施行規則》及民法第 143 條年齡計算法，4 月 1 日生者於 3 月 31 日 24:00 滿 6 歲，屬於當屆學年；4 月 2 日生者屬於次一學年。
   - [`EducationTimelineProjector.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationTimelineProjector.java#L193-L203) 的 `computeAcademicBirthYear` 透過 `cutoffDay = 1` 判定：4 月 1 日出生精準歸入 `birthYear`、4 月 2 日歸入 `birthYear + 1`；台灣與美制 9 月開學軌道亦精準遵循 9 月 1 日法定切齊日。
2. **順延排積木演算法 (Sequential Block Chaining)**：
   - 採 `chainedStart.isAfter(standardStart) ? chainedStart : standardStart` 雙向保護：
     - **跨國切換（日本公立小學 4 月開學 72 個月 $\to$ 國際中學 9 月開學）**：小學於 `2030-03` 畢業，Projector 自動接續推算次一開學月 `2030-09`，4 月至 8 月形成合理的暑期空窗期，空窗期不產生任何教育事件。
     - **跳級／階段跳過（如 HighTrack = NONE）**：若跳過高中直接讀大學，因受 `standardStart`（年滿 18 歲）下限保護，大學仍會精確在滿 18 歲當年度開學，不會提前在 15 歲開學。
     - **南半球澳洲學制（2 月開學）**：高中 3 月畢業後，順延至次年 2 月開學，自動反映 10 個月的留學等待期。
3. **分期學費等分推算與零小數耗損**：
   - 僅允許除盡 12 個月的白名單期數 `{1, 2, 3, 4, 6, 12}`。三學期制（9 月開學、3 期）自動推算收費月為 `[9, 1, 5]`。
   - 每期學費以 `annualTuition.divide(termsPerYear, 4, RoundingMode.HALF_UP)` 保留 4 位小數，年度總結算 `setScale(0, RoundingMode.HALF_UP)` 時精準還原整數，無分毫誤差。
4. **過渡年（Transition Year）現金快照防重複扣款**：
   - 模擬器進入起始年度時，使用者的流動現金快照已扣除「當月之前已繳納的支出」。
   - [`EducationExpenseCalculator.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/education/EducationExpenseCalculator.java#L92-L133) 的 `calculateCostForRemainingYear` 只加總 `paymentMonth.getMonthValue() >= fromMonth` 之離散事件，完全排除上半年已付學費與入學金，與 [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L87) 的剩餘月數口徑嚴格對齊。
5. **留學生活費按月精確折算 (Monthly Living Cost)**：
   - 留學生活費以 `monthlyLivingCost` 儲存，年度加總時計算 `activeMonths = stageEndM - stageStartM + 1`。開學當年（如 9~12 月）僅計 4 個月，畢業當年僅計至離校月份，徹底消滅換學制年度金額被灌爆一整年的歷史問題。

---

#### 三、Claude Review 答辯預備手冊 (Defensive Briefing)

以下整理外部 AI（如 Claude）進行代碼審查時，最容易因比對歷史規劃文件而提出的質疑點，以及專案的真實架構依據：

##### 質疑點 1：為什麼在中學與高中配置中找不到 `jukuCost`（補習費）？
- **Claude 可能的質疑**：在 `Plan-01.md` 前半部的規劃表格中有列出中高中的 `jukuCost` 與 `manualJukuCost`，為什麼最終實作的實體與計算機中完全沒有？
- **標準答辯依據（[Fixed-110.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/fixed/Fixed-110.md)）**：
  在後續深度架構評估中，確認各國升學與補習體制差異極大（計費模式無法標準化），且未來十幾年是否補習具高度個人不確定性。因此在 `Fixed-110` 中正式拍板將補習費**從教育專用模組中拔除**。使用者若需要模擬補習費用，統一利用系統既有的通用支出事件機制（[`LifeEvent`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/event/LifeEvent.java)）自訂起訖年月與金額即可。

##### 質疑點 2：單軌幼兒園（Preschool）為什麼強制 duration 必須 $\ge 36$ 個月？不是說可以自訂 24 個月（K4 入學）嗎？
- **Claude 可能的質疑**：`Plan-01 Phase2.md` 曾提及家長若要 K4 入學只需覆寫為 24 個月，但目前後端 Service 會拒絕小於 36 個月的覆寫，且 Resolver 做了 `Math.max(36, duration)`？
- **標準答辯依據（[Fixed-125.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/fixed/Fixed-125.md)）**：
  若單軌時長直接縮短為 24 個月，小孩在滿 5 歲畢業到滿 6 歲入小學之間會在時間軸上留下整整 1 年的「真空斷層（Vacuum Gap）」。為此在 `Fixed-125` 中正式建立了**「幼兒園兩段式（Preschool Split）」**機制：
  - 家長若要 4 歲（K4）才入學，應啟用兩段式：第一段設為 `HOME`（12 個月，0元）、第二段設為 `INTL`/`DAYCARE`（24 個月），以確保能無縫銜接小學。
  - 單軌模式必須保底 36 個月。至於未滿 3 歲提早托育（如 18 個月進托兒所），則已獨立排入 [Plan 13](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/planned/Plan-13-early-childhood-entrance-age-months.md) 專案進程。

##### 質疑點 3：`StageOverrideRequest.isManualDurationMultipleOf12` 為什麼強制時長必須是 12 的整數倍？
- **Claude 可能的質疑**：如果小孩念 2 年半（30 個月），會被後端 Bean Validation 擋下？
- **標準答辯依據（[Fixed-128.md](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/fixed/Fixed-128.md)）**：
  前端 UI 的設計考量到使用者體驗，讓家長以直覺的「年（Years）」填寫，底層透過 `yearsToMonths(year * 12)` 換算為月數儲存。為防止零碎非整年月份導致跨階段排積木梯隊錯位，後端在 `Fixed-128` 增加了整除 12 的校驗防禦，符合常規學制梯次運作邏輯。

##### 質疑點 4：`SimulationContextFactory` 在建立快取時使用 `YearMonth.now()`，是否缺少時鐘注入（Clock Injection）？
- **Claude 可能的質疑**：在 `SimulationContextFactory.java` 中呼叫 `buildAnnualCostMap(timeline, currentYear, YearMonth.now().getMonthValue())`，若進行時間旅行測試（模擬過去歷史年份）可能不夠彈性？
- **標準答辯依據**：
  當前的金融模擬器定位是以「使用者當下此刻的真實資產快照」作為模擬 T=0 起點，因此取當前西元年與月份完全符合產品業務需求。若未來系統需要支援回測歷史時間軸，只需將 `Clock` 抽成可注入 Bean 即可，不影響現行模擬的精確性。

##### 質疑點 5：過渡年計算使用 `>= fromMonth` 是否會包含當月已繳款項？
- **Claude 可能的質疑**：如果模擬當天是 10 月 15 日，10 月份的學費若已扣款，是否會重複計入？
- **標準答辯依據**：
  整個模擬引擎是以「整月」為最小時間顆粒度。在 [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L87) 中，首年模擬月數公式為 `monthsToAdd = 12 - currentMonth + 1`，這代表 `currentMonth`（當月）本身就屬於模擬剩餘區間的第一個月。因此 discrete events 與生活費折算均採用包含當月的閉區間（`>= fromMonth`），前後各模組之邊界口徑 100% 嚴格一致。


