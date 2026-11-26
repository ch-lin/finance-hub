# [FIXED] Issue 144：個人住民稅完全沒有計算「均等割」這筆每人每年固定要繳的金額，只算了「所得割」

## 這份issue怎麼被發現的
這是這次Source Code Review針對稅務計算引擎進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:219-227`（薪資年度路徑）、`:373-380`（年金路徑）、`:319-326`（退職所得路徑）——三個計算住民稅的地方，全部只有「課稅所得 × 10% + offset」這一段，沒有任何額外加上固定金額的邏輯
- `finance-hub/finance-hub-backend/hub/src/main/resources/application.properties:163-166`（目前種在資料庫裡的住民稅級距，就是單純的10%乘數，沒有任何固定金額設定）
- 全專案搜尋「均等割」、per-capita、flat-levy相關字樣，在`TaxConstants.java`跟整個tax package裡都沒有任何對應常數

## 現狀
系統目前算出的「住民稅」，實際上只是法律上「個人住民稅」裡的「所得割」（依所得比例課徵的部分），完全沒有加上「均等割」——這是不管所得高低、每一位達到課稅標準的居民每年都要繳的固定金額部分。這代表只要某一年計算出來的課稅所得剛好是0（例如所得很低、或扣除額剛好完全抵掉所得的那一年），系統算出的住民稅就會是0円——但實際上，只要所得超過均等割本身的免稅門檻，居民依法還是要繳這筆固定金額。

## 為何是問題
依照常見的住民稅說明（都道府縣+市町村合計約5,000円/年的均等割，加上2024年起另外課徵的森林環境稅1,000円/年——這次審查只找到次要來源佐證金額，沒有查到總務省或特定地方政府網站的第一手資料逐一核對確切金額，需要另外查證確切數字），每一位達到課稅標準的居民，依法每年都要繳這筆固定金額，跟所得高低無關。這筆金額雖然相對這個工具慣常處理的數字（房貸抵稅、NISA額度動輒十幾萬到百萬級）小很多，但它是每一年、每一位使用者都會出現的系統性落差，不是邊緣情境——30-40年的模擬期間下來，單純這一項漏算就會累積到15-24萬円等級的落差。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：加上一筆固定金額的均等割常數，並補上免稅門檻判斷**
- 做法：在`TaxConstants.java`新增均等割固定金額常數，在三個計算住民稅的地方，於課稅所得高於免稅門檻時無條件加上這筆錢。
- 優點：補齊這塊法律上真實存在的稅負。
- 缺點：均等割本身也有自己的免稅所得門檻（並非所得等於0才免課徵），需要額外補上這段門檻判斷邏輯，不是單純加一個常數就能做完；確切的門檻跟金額數字都需要再查證第一手資料來源。

**方向B：維持現狀，記錄為刻意簡化**
- 理由：如果使用者判斷這筆每年幾千円等級的固定金額，相對這個工具關注的房貸、NISA、iDeCo等大額結構性扣除項目而言不值得投入查證跟補上免稅門檻邏輯的工程成本，可以明確記錄為刻意不處理的簡化，避免之後又重複被提出。

---

## 覆核結果與查證分析（Review & Statutory Verification Analysis）

已連線日本主管機關官方網站（總務省、國稅廳、東京都主稅局）之最新法定條文與公告，以官方最新法定條文作為唯一的單一事實來源（SSOT），對 Claude 提出的 Issue 144 進行嚴格審核。查證與覆核結果如下：

### 一、同意（Agree）之項目

1. **代碼確實存在漏算缺陷（薪資與年金路徑屬實）**：
   - 審查現行代碼：[`ActiveTaxCalculationEngine.java:237`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java#L237)（現役薪資路徑）與 [`:373`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java#L373)（公的年金路徑），住民稅計算皆委派至 [`InhabitantTaxRateStrategyFactory`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategyFactory.java) $\rightarrow$ [`StandardInhabitantTaxRateStrategy.java:44`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/StandardInhabitantTaxRateStrategy.java#L44)（`taxableIncome * 0.10`）。
   - 在此兩條綜合課稅路徑中，系統僅計算「所得割（10%）」，完全沒有「均等割（Per-capita levy）」與「森林環境稅」之固定金額常數與計算邏輯。
   - Claude 主張「只要某一年計算出來的課稅所得剛好是 0，系統算出的住民稅就會是 0 円，但實際上只要所得超過均等割免稅門檻，居民依法仍須繳納固定金額」——**此項觀察在薪資與年金路徑完全屬實，同意 Claude 之判定**（惟退職所得路徑依法本來即不課均等割，詳見反駁第 3 點）。

2. **均等割具備獨立之非課稅門檻（非課稅限度額概念正確）**：
   - Claude 在原文第 23 行指出「均等割本身也有自己的免稅所得門檻（並非所得等於 0 才免課徵）」，此概念與《地方稅法》第24條之5、第295條完全吻合，**同意此法規架構概念**。

---

### 二、補足（Supplement）之項目

1. **補足官方母法第一手條文依據（SSOT）**：
   Claude 原文坦承「這次審查只找到次要來源佐證金額，沒有查到總務省或特定地方政府網站的第一手資料逐一核對確切金額」。此處補足精確法律條號與法定真值：
   - **《地方稅法》第38條第1項第1號**（道府県民稅均等割標準稅率）：年額 **1,000 円**。
   - **《地方稅法》第310條第1項第1號**（市町村民稅均等割標準稅率）：年額 **3,000 円**。
   - **地方稅標準均等割合計**：$1,000 + 3,000 = \mathbf{4,000\text{ 円/年}}$。
   - **《森林環境稅及び森林環境譲與稅に関する法律》（平成31年法律第3號）第5條、第8條**：自 2024 年度（令和6年度）起開徵國稅「森林環境稅」，第5條明定稅率年額 **1,000 円/人**，第8條明定由市町村隨市町村民稅均等割一併徵收。
   - **《地方稅法》第50條之2（道府県民稅）與第328條（市町村民稅）**：退職所得課稅特例（分離課稅，**明定僅課徵所得割，依法不課徵均等割與森林環境稅**）。
   - **《地方稅法》第295條第3項、第24條之5第3項、施行令第47條之3**（均等割非課稅限度額授權與政令標準，以1級地如東京23區條例為例）：
     - 單身者：前年合計所得金額 $\le \mathbf{45\text{ 萬円}}$（換算給與年收 $\le \mathbf{100\text{ 萬円}}$）非課稅。
     - 有扶養親屬者：前年合計所得金額 $\le 35\text{萬} \times (\text{本人} + \text{同一生計配偶者及扶養親屬數}) + 10\text{萬} + 21\text{萬}$（加算額合計 31 萬円）非課稅。
     - **《森林環境稅法》第4條第1項第3號及施行令第1條**：前年合計所得金額低於政令標準者（準用住民稅均等割非課稅標準），同時免徵森林環境稅。

2. **補足高所得族群（年薪 2,500 萬日圓）之邊界覆蓋與敏感度分析**：
   - 核心使用者為年薪 2,500 萬受薪階級（給與所得控除 195 萬封頂，合計所得金額 2,305 萬日圓），遠高於免稅門檻，依法 **100% 必須繳納均等割 4,000 円 ＋ 森林環境稅 1,000 円 ＝ 5,000 円/年**。
   - 年薪 2,500 萬受薪階級常態住民稅所得割約為 $2,305\text{萬} \times 10\% \approx \mathbf{2,305,000\text{ 円/年}}$。
   - 漏算 5,000 円相對於其總住民稅之相對偏差僅約 **0.217%**（換算每月現金流偏差僅約 **416 円**）。
   - 對長線資產累積與買房現金流破產模擬之邊界影響屬雜訊等級；但在無所得年度或極端大額扣除抵減至課稅所得為 0 的極端情境下，系統會低估稅負 5,000 円。

---

### 三、反駁（Refute）之項目

1. **反駁「均等割約 5,000 円 ＋ 森林環境稅 1,000 円 ＝ 6,000 円/年」之重複加徵幻覺（時效混淆）**：
   - Claude 在原文第 15 行寫道：「都道府縣+市町村合計約5,000円/年的均等割，加上2024年起另外課徵的森林環境稅1,000円/年」，並據此暗示每年定額負擔達到 6,000 円。
   - **反駁依據**：
     - 依據《地方稅の臨時特例に関する法律》第3條及總務省公告，平成26～令和5年度（2014～2023）均等割之所以為 5,000 円，是因為包含「東日本大震災復興臨時加稅（都道府縣 500 円 + 市町村 500 円 = 1,000 円）」。
     - **該復興臨時加稅 1,000 円已於令和5年度（2023年度）末正式屆滿終止！**
     - 2024 年度（令和6年度）起開徵的國稅「森林環境稅 1,000 円」，係**無縫替代**落日的復興臨時加稅。
     - 因此，納稅義務人之實質定額總負擔：
       - 2023 年以前：均等割 4,000 円 ＋ 復興增稅 1,000 円 ＝ **5,000 円/年**
       - 2024 年以後：均等割 4,000 円 ＋ 森林環境稅 1,000 円 ＝ **5,000 円/年**
     - **法定實質負擔始終為 5,000 円/年，從未達到 6,000 円/年**！Claude 在未查核第一手母法下，將「含舊制復興增稅的 5,000 円」疊加「新制森林環境稅 1,000 円」，屬於典型的**稅制年度時效重疊幻覺**。

2. **反駁「40年模擬累積落差達 24 萬円」之算例放大偏差**：
   - Claude 在第 15 行推論：「30-40年的模擬期間下來，單純這一項漏算就會累積到15-24萬円等級的落差。」
   - **反駁依據**：
     - 上限 24 萬円係來自錯誤之 $6,000 \times 40 = 240,000$ 円。
     - 依官方正確年額 5,000 円計算：
       - 30 年累積差額真值：$5,000 \times 30 = \mathbf{150,000\text{ 円}}$（15 萬円）。
       - 40 年累積差額真值：$5,000 \times 40 = \mathbf{200,000\text{ 円}}$（20 萬円）。
     - 累積落差上限為 **20 萬円**，Claude 的算例上限高估了 **4 萬円（偏差幅度達 20%）**。

3. **反駁「退職所得路徑（calculateRetirementTax）亦漏算均等割」之法律盲區（防範重復課稅）**：
   - Claude 在原文第 7 行與第 21 行將退職所得列為「三個計算住民稅的地方之一」，並主張在退職所得路徑也加上均等割。
   - **反駁依據**：
     - 依據**《地方稅法》第50條之2（道府県民稅）及第328條（市町村民稅）**，退職手當等（退休金）之住民稅屬於**「分離課稅」**，法律明文規定：**「退職手当等に係る退職所得に対しては、他の所得と区分して、所得割のみを特別徴収の方法によって課する。」**
     - 法定均等割與森林環境稅是一個人每年隨同常態綜合所得（薪資特別徵收或年金普通徵收）繳納**「一次（5,000 円）」**。退職所得為獨立分離課稅，在源泉代扣 10% 所得割後即結清，**法律嚴禁課徵均等割**！
     - ❌ **若照 Claude 的方案在退職路徑也加上 5,000 円**：納稅人在退休當年度，其年度薪資/年金已被扣了一次 5,000 円均等割，領取退職金時又會被系統多扣一次 5,000 円，**將直接造成違法的「雙重課稅（Double Taxation）」Bug！**
     - 🟢 **事實結論**：現行代碼在 [`ActiveTaxCalculationEngine.java:325`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java#L325) 只計算所得割 10%，**在法律上 100% 正確無誤**，退職所得路徑絕對不應加入均等割！

---

### 四、處置方案架構分析（供 PM 裁決）

- **【方案 A】補齊均等割與森林環境稅（🔴 修改代碼邏輯）**：
  - 在薪資與年金兩條綜合所得路徑（或在 `InhabitantTaxRateStrategy`）中引入固定年額常數 `5_000L`，並加入合計所得金額 $\le 450,000L$（單身1級地標準）之免稅判斷；**退職所得路徑依《地方稅法》第50條之2維持僅課所得割**。
  - **優點**：100% 消除法律體系上的漏項，課稅所得為 0 時不會誤判為免稅。
  - **代價**：自治體間可能存在微幅加徵（如長野縣 500 円森林づくり県民稅等），且需在薪資與年金兩條路徑維護非課稅門檻邏輯。
- **【方案 B】維持現狀，記錄為刻意簡化（🟡 免動代碼，轉入 WONTFIX 歸檔）**：
  - 理由：每年 5,000 円（每月約 416 円）對 30～40 年長線資產累積與房貸破產模擬而言屬雜訊等級，不值得投入工程成本維護各自治體非課稅公式。修正文件後移轉至 `todo/wontfix/` 歸檔。

---

### 五、Claude 原始方案 A 與 Gemini 方案 A 深度架構與法理對比

針對 Claude 於第 20～23 行提出的「方向 A」與 Gemini 於第 93～96 行提出的「方案 A」，兩者在稅法法理與架構落地層次存在三個根本差異：

#### 1. 門檻判斷之所得變數根本不同（Claude 存在嚴重稅法概念混淆）
* **Claude 原文主張（第 21 行）**：
  * *「於**課稅所得**高於免稅門檻時無條件加上這筆錢。」*
  * **致命缺陷與 Bug 分析**：
    「課稅所得（Taxable Income，即代碼中的 `taxableIncome`）」為合計所得金額扣除所有「所得控除（基礎控除、社保、房貸抵扣、撫養控除等）」後的淨額。
    若納稅人年薪 350 萬円（合計所得約 237 萬円），但因大額扣除額抵減至**課稅所得剛好為 0 円**：
    * **法定真實義務**：其合計所得 237 萬円遠超免稅門檻（45 萬円），依《地方稅法》**依法仍必須繳納 5,000 円均等割**。
    * **若照 Claude 邏輯實作**：判定式寫為 `taxableIncome > 450,000`，因 `0 > 450,000` 為 false，**系統依然會輸出住民稅 0 円，均等割依然漏算**！此方案完全無法修復 Issue 144 最核心想解決的「課稅所得為 0 但需繳固定稅額」之盲區。
* **Gemini 法定正確方案（第 94 行）**：
  * *「加入**合計所得金額**（`employmentIncome`）之免稅判斷（若 $\le 450,000L$ 則免徵）。」*
  * **法理依據**：依據《地方稅法》第295條第3項、第24條之5第3項及施行令第47條之3，均等割非課稅限度額是以**「前年合計所得金額（給與所得控除後、各類所得控除前之毛所得）」**為判斷基準，代碼變數必須使用 `employmentIncome` / `miscellaneousIncome`，才能精準覆蓋所有法定課稅情境。

#### 2. 架構落點與物件封裝差異（散落硬編碼 vs 策略模式封裝；誤植退職所得）
* **Claude 原始方案**：
  * 建議在 `TaxConstants.java` 宣告常數，並在 `ActiveTaxCalculationEngine` 的現役薪資（237行）、退職（325行）、年金（373行）三處業務流程各自手寫 `+ TaxConstants.FLAT_LEVY` 與判斷式。
  * **缺點與法理盲區**：
    1. 破壞專案已推動的「稅制策略模式（Strategy Pattern）」，在引擎外層散落硬編碼加法與 `if-else`。
    2. **法理盲區**：誤將退職所得（325行）列入加徵對象。依《地方稅法》第50條之2及第328條，退職金為分離課稅，法律明定僅課所得割、免課均等割；若在退職路徑加上常數將造成退休當年「重複課徵均等割（Double Taxation）」之違法 Bug！
* **Gemini 方案**：
  * 融入 [`InhabitantTaxRateStrategy`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategy.java) 或由策略集中管理固定稅額與所得割；僅套用於薪資與年金綜合課稅路徑，退職所得維持法定分離課稅。
  * **優點**：與專案其他動態策略工廠架構一致，代碼職責內聚且杜絕違法雙重課稅。

#### 3. 數值具體化程度（模糊不確定 vs 官方 SSOT 真值落地）
* **Claude 原始方案**：
  * 原文自述「確切門檻跟金額數字都需要再查證第一手資料來源」，且殘留 5,000+1,000=6,000 円的時效重複加徵疊加幻覺。
* **Gemini 方案**：
  * 經連網調研總務省最新法定條文，直接拍板官方真值：
    * 固定稅額年額：**`5,000L`**（《地方稅法》第38/310條均等割 4,000 円 ＋ 《森林環境稅法》第5條國稅 1,000 円）。
    * 非課稅基準額：**`450,000L`**（《地方稅法》第295條第3項、第24條之5第3項及施行令第47條之3，單身生活保護 1 級地基準）。

#### 對照總結表

| 維度 | Claude 的方向 A 原文（第 20-23 行） | Gemini 校正後的方案 A（第 93-96 行） | 實質影響分析 |
| :--- | :--- | :--- | :--- |
| **判定門檻之所得變數** | 誤用 **課稅所得**（`taxableIncome`） | 法定正確的 **合計所得金額**（`employmentIncome`） | 🔴 **致命邏輯差異**：若照 Claude 的寫法，課稅所得為 0 者依然會漏算均等割，Issue 根本沒修好。 |
| **適用路徑範疇** | 誤植包含 **退職所得**（主張 3 處皆加） | 精準排除退職金，僅適用 **薪資與年金**（2 處） | 🔴 **合規防錯差異**：依地方稅法第50條之2，退職金免課均等割，避免退休當年重復課稅違法 Bug。 |
| **架構落點** | `TaxConstants.java` ＋ 三處呼叫點手寫加總 | 融入 `InhabitantTaxRateStrategy` 或集中引擎 | 🟡 **維護性差異**：策略模式封裝職責更乾淨，避免程式碼散落硬編碼。 |
| **門檻與金額數值** | 未查證，僅寫「需再查第一手資料」 | 明確給出 **5,000 円** 與 **45 萬円** | 🟢 **落地可行性**：具備直接撰寫程式碼的完全規格。 |

---

### 六、Product Manager 裁決與最終實作規格（採納方案 A 走法一）

經 Product Manager（兼 Scrum Master）審閱法定事實與架構分析後，正式拍板裁決：
* **決策**：採納 **【方案 A：補齊均等割與森林環境稅】**，並落實 **【走法一：擴充 `InhabitantTaxRateStrategy` 介面簽名】**。
* **理由**：均等割為日本法律實質存在的稅負，徹底消除「課稅所得被扣除額扣抵至 0 時住民稅誤算為 0 円」之法定盲區；透過 Strategy 封裝維護全系統策略模式的一致性與內聚性，並依《地方稅法》第50條之2杜絕退職所得重複課稅。

#### 1. 策略介面合約重構（[`InhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategy.java)）
擴充方法簽名，同時接收「課稅所得（`taxableIncome`）」與「合計所得金額（`totalIncome`）」：
```java
public interface InhabitantTaxRateStrategy {
    /**
     * 計算個人住民稅總額（包含所得割與法定均等割/森林環境稅）。
     *
     * @param taxableIncome 課稅所得金額（円，經千圓捨去）
     * @param totalIncome   合計所得金額（給與所得/年金雜所得，用於判定均等割免稅限度額）
     * @return 個人住民稅應納稅額（円）
     */
    long calculateInhabitantTax(long taxableIncome, long totalIncome);
}
```

#### 2. 現行標準策略實作（[`StandardInhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/StandardInhabitantTaxRateStrategy.java)）
```java
@Component
public class StandardInhabitantTaxRateStrategy implements InhabitantTaxRateStrategy {

    private static final long FLAT_LEVY_THRESHOLD = 450_000L; // 地方稅法第295條第3項非課稅限度額（1級地）
    private static final long FLAT_LEVY_AMOUNT = 5_000L;       // 均等割 4,000 円 + 森林環境稅 1,000 円

    @Override
    public long calculateInhabitantTax(long taxableIncome, long totalIncome) {
        long incomeLevy = (taxableIncome <= 0) ? 0L : (long) (taxableIncome * 0.10);
        long flatLevy = (totalIncome > FLAT_LEVY_THRESHOLD) ? FLAT_LEVY_AMOUNT : 0L;
        return incomeLevy + flatLevy;
    }
}
```

#### 3. 舊制策略實作（[`Pre2007InhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/Pre2007InhabitantTaxRateStrategy.java)）
舊制 3 階超額累進所得割，均等割標準稅率為 4,000 円（舊制無森林環境稅與復興特別稅）：
```java
long flatLevy = (totalIncome > 450_000L) ? 4_000L : 0L;
return incomeLevy + flatLevy;
```

#### 4. 計算引擎呼叫點對接（[`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)）
* **現役薪資路徑（第 237 行）**：
  傳入已計算之給與所得 `employmentIncome`：
  ```java
  long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(simulationYear)
          .calculateInhabitantTax(taxableIncomeForInhabitantTax, employmentIncome);
  ```
* **公的年金路徑（第 373 行）**：
  傳入已計算之公的年金雜所得 `miscellaneousIncome`：
  ```java
  long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(context.getYear())
          .calculateInhabitantTax(taxableIncomeForInhabitantTax, miscellaneousIncome);
  ```
* **退職所得路徑（第 325 行）**：
  依據《地方稅法》第50條之2分離課稅原則，傳入 `0L`，確保退休金**絕對不重複課徵均等割**：
  ```java
  long inhabitantTax = inhabitantTaxRateStrategyFactory.getStrategy(context.getYear())
          .calculateInhabitantTax(taxableIncome, 0L);
  ```

#### 5. 自治體級地區分與超過課稅之架構取捨說明（成本效益分析）

本實作規格之所以直接採用「1級地（45萬門檻）＋ 全國標準稅率（5,000円）」，係基於以下稅法現實與系統架構之成本效益裁決：

1. **非課稅門檻因「級地區分（1級地～3級地）」而不同**：
   - 依據《地方稅法》第295條第3項及《地方稅法施行令》第47條之3，日本總務省連動厚生勞動省《生活保護法》的區域生活費用標準，將全日本市町村劃分為 **1級地、2級地、3級地**：
     - **1級地（大都會區，如東京23區、政令指定都市）**：單身合計所得限度額為 **45 萬円**（換算給與年收 100 萬円）。
     - **2級地（中型都市，如各縣廳所在地）**：單身合計所得限度額為 **41.5 萬円**（換算給與年收約 96.5 萬円）。
     - **3級地（地方町村、偏遠地區）**：單身合計所得限度額為 **38 萬円**（換算給與年收約 93 萬円）。
   - 俗稱的打工「100 萬年收壁壘（超過需繳住民稅）」，實為東京23區等 1級地之標準。

2. **每年繳納的均等割金額因「地方超過課稅（加徵）」而不同**：
   - **全國標準稅率（本專案基準）**：道府県民稅 1,000 円 ＋ 市町村民稅 3,000 円 ＋ 國稅森林環境稅 1,000 円 ＝ **5,000 円/年**（東京都23區、大阪市等大都會均維持此標準稅率）。
   - **地方超過課稅（上乗せ課税）**：日本約有 37 個道縣及部分市町村自行立法加徵地方目的稅（如水源保全稅、森林綠化稅）：
     - 例如神奈川縣加徵水源稅 300 円（年額 5,300 円）；長野縣加徵森林稅 500 円（年額 5,500 円）；橫濱市除神奈川縣水源稅外另加徵綠色稅 900 円（年額達 6,200 円）。

3. **照地區分級實作不符合成本效益之架構裁決**：
   - **核心使用者覆蓋**：本專案聚焦於受薪階級與資產模擬（含年薪 2,000 萬～3,500 萬日圓高所得族群），多數居住於一線都會區（1級地）。使用者的合計所得金額（數百萬至數千萬）遠高於 38 萬～45 萬的非課稅臨界點，級地門檻差異在實際模擬中 100% 不會被觸發。
   - **工程與維護成本**：若要照地區分級實作，系統必須引入全日本 1,700 多個市町村的行政區代碼、歷史級地異動與個別自治體超過課稅資料庫。每年幾百日圓的加徵差額（換算每月僅約 25～80 円），相對於全生涯 30～40 年的房貸、NISA 及動輒百萬級的現金流波動純屬微小雜訊，投入龐大維護成本缺乏實質工程價值。
   - **符合業界主流標竿**：日本主流商業 FP（Financial Planning）試算系統與房貸模擬器，均統一以「東京都（1級地）之標準稅率 5,000 円 / 45 萬門檻」作為全日本基準模型。

---

## 七、實作完成與測試驗證（Implementation & Verification Report）

### 1. 程式碼修改清單
1. **策略介面契約**：
   * [`InhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategy.java)：擴充方法簽名為 `long calculateInhabitantTax(long taxableIncome, long totalIncome)`。
2. **策略實作**：
   * [`StandardInhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/StandardInhabitantTaxRateStrategy.java)：實作 10% 所得割 + `totalIncome > 450_000L ? 5_000L : 0L`（均等割 4,000 + 森林環境稅 1,000）。
   * [`Pre2007InhabitantTaxRateStrategy.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/Pre2007InhabitantTaxRateStrategy.java)：實作舊制三段階所得割 + `totalIncome > 450_000L ? 4_000L : 0L`。
3. **計算核心引擎**：
   * [`ActiveTaxCalculationEngine.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)：
     * 現役路徑：傳入 `employmentIncome`。
     * 年金路徑：傳入 `miscellaneousIncome`。
     * 退職路徑：依法傳入 `0L`（免徵均等割）。

### 2. 單元測試調整與新增
1. **策略單元測試**（[`InhabitantTaxRateStrategyTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/rate/inhabitant/InhabitantTaxRateStrategyTest.java)）：
   * 驗證非課稅門檻（`totalIncome <= 450_000L` 時均等割為 0）。
   * 驗證課稅門檻（`totalIncome > 450_000L` 時課徵 5,000 円 / 4,000 円）。
   * 驗證課稅所得非正數但合計所得超額時純課均等割。
   * 驗證退職所得傳入 `0L` 免徵均等割。
2. **管道整合測試**（[`YearlySimulationPipelineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java)）：
   * 更新 `testScenarioB_exactStatutoryIllustration_refundCappedAt50kAndRollover97500` 預期住民稅（因 4.5M 薪資之給與所得高於 45 萬，基準住民稅納入 5,000 円均等割，折抵 97,500 円後由 109,100 円更新為 114,100 円）。
3. **稅務引擎單元測試**（[`ActiveTaxCalculationEngineTest.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngineTest.java)）：
   * 同步更新既有現役薪資與年金測試之住民稅期望值（+5,000 円均等割）與對應手取り淨額。
   * 保留退職金路徑測試之原斷言（驗證分離課稅均等割維持 0 円）。
   * 新增 3 項邊界單元測試：
     - `calculate_lowIncome_belowFlatLevyThreshold_exemptFromInhabitantTax`：驗證給與所得 $\le$ 45 萬完全免稅（住民稅為 0）。
     - `calculate_incomeAboveFlatLevyThreshold_zeroTaxableIncome_paysOnlyFlatLevy`：驗證所得控除扣至課稅所得為 0，但給與所得 > 45 萬時，僅徵收法定均等割 5,000 円。
     - `calculateRetirementTax_highRetirementIncome_exemptFromFlatLevy`：驗證高額退職所得依據《地方稅法》第50條之2及第328條分離課稅，均等割嚴格為 0 円。



