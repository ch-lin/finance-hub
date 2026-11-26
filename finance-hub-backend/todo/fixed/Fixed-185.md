# [FIXED] 185：新增的「特別特定取得」13年控除策略，把`PropertyType.OTHER`（その他）歸類成新屋待遇（13年），跟其他三個同類年度策略（2022-2023、2024-2025、2026以後）把`OTHER`一律歸類成中古待遇（10年）的做法不一致——`OTHER`這個分類本身沒有明確表示新屋或中古，需要先確認這個分類在稅務判斷上該怎麼處理才能定案

## 這份question怎麼來的
這是[question-170](question-170.md)（房貸控除年限10年/13年查證）的實作查證過程中發現的新落差。question-170本身查證的「2019/10~2021/12入住、消費税10%的特別特定取得案件控除年限應為13年（新屋）或10年（中古）」這個核心問題，查證跟實作都已確認無誤，可以結案；但實作新增的`SpecialSpecificAcquisitionTaxStrategy`在替`PropertyType.OTHER`這個分類做新屋/中古二選一判斷時，跟既有三個同類策略的判斷方式不一致，這份question獨立記錄，不影響question-170本身的結案狀態。

## 相關檔案
- [PropertyType.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/PropertyType.java)（enum本身，5個值：`NEW_DETACHED_HOUSE`新築一戸建て、`USED_DETACHED_HOUSE`中古一戸建て、`NEW_APARTMENT`新築マンション、`USED_APARTMENT`中古マンション、`OTHER`その他——`OTHER`的註解只寫「その他」，沒有任何進一步說明這是指什麼樣的物件類型）
- [SpecialSpecificAcquisitionTaxStrategy.java:98-104](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/SpecialSpecificAcquisitionTaxStrategy.java)（這次question-170新增的策略，`calculateDurationYears`把`USED_DETACHED_HOUSE`跟`USED_APARTMENT`歸10年，**其餘所有值（包含`OTHER`）一律歸13年**）
- 對照三個既有同類策略，`OTHER`的歸類方式完全相反：
  - [Years2022To2023TaxStrategy.java:59-68](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2022To2023TaxStrategy.java)
  - [Years2024To2025TaxStrategy.java:59-68](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2024To2025TaxStrategy.java)
  - [Years2026OnwardsTaxStrategy.java:58-68](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2026OnwardsTaxStrategy.java)
  
  這三支策略都用：
  ```java
  return switch (propertyType) {
      case NEW_DETACHED_HOUSE, NEW_APARTMENT -> 13;
      default -> 10;
  };
  ```
  `OTHER`沒有被明確列在13年那一側的case裡，所以落到`default`，歸類成10年（中古待遇）。

## 現狀
新增的`SpecialSpecificAcquisitionTaxStrategy`跟既有三個策略，對`OTHER`這個分類給出完全相反的判斷結果：
- `SpecialSpecificAcquisitionTaxStrategy`：`OTHER` → 13年（因為寫法是「`USED_*`才10年，其餘全部13年」，`OTHER`不屬於`USED_*`，所以落在13年那一側）。
- 其他三個策略：`OTHER` → 10年（因為寫法是「`NEW_*`才13年，其餘全部10年」，`OTHER`不屬於`NEW_*`，所以落在10年那一側）。

這個落差是寫法本身造成的——兩種寫法都是「正面列舉其中一類，其餘全部歸另一類」，但正面列舉的是不同的一類（一個正面列`USED_*`，一個正面列`NEW_*`），導致`OTHER`這個兩邊都沒被正面提到的模糊分類，被動地分到了不同結果。這次實作的「修正報告」本身沒有提到這個落差，只是文字上寫著「新築住宅（NEW_DETACHED_HOUSE, NEW_APARTMENT, OTHER）回傳13年」——把`OTHER`直接歸入新築，但沒有說明為什麼`OTHER`該算新築，也沒有對照過既有三個策略的做法是否一致。

## 不確定的點是什麼、為什麼不確定
`PropertyType.OTHER`這個分類本身的設計意圖不明確——查證[PropertyType.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/PropertyType.java)確認這個enum只有一行註解「その他 (Other)」，沒有任何進一步的業務邏輯說明文件或使用情境記錄可以參考，無法從現有程式碼或文件判斷：
1. 這個分類原本設計時，是想涵蓋哪一種真實物件類型？（例如：非住宅用途混合的物件、無法歸類到一戸建て/マンション這兩種日本常見房屋形式的其他建物類型、使用者自己手動選填但不確定該選哪個的保留選項？）
2. 這個分類在稅法上，究竟該視為新築還是中古、還是這個分類本身在稅法概念上根本沒有對應的明確答案（例如如果這個分類代表的是「土地」或「增建」這類跟新築/中古二分法本身就不適用的物件）？

這份question不代為猜測答案——如果隨便選一個方向定案（跟既有三策略一致改成10年，或維持新增策略現狀13年），都只是在沒有依據的情況下猜測，跟個案本身無關，純粹是武斷決定。

## 需要查證/確認的問題
1. 使用者名下房貸的`propertyType`目前實際使用的是哪些值？如果使用者的房貸從未用過`OTHER`這個分類（不管是過去的、現在的，還是可預期會用到的），這個不一致目前不會造成任何實際影響，可以單純為了程式碼一致性修正（例如統一所有策略對`OTHER`的處理方式），不需要深入研究這個分類在稅法上的真正含義。
2. 如果`OTHER`確實有、或可能會被使用到，需要先確認這個分類對應的真實物件情境是什麼，才能判斷該歸哪一類、或是否應該要求使用者在`OTHER`之外額外補充資訊（例如一個額外的「是否為新築」欄位，不綁死在`propertyType`這個enum本身的字面值上）。

## 不建議的處理方式
不建議在沒有確認使用情境的前提下，直接選擇「跟既有三策略一致改成10年」或「維持新增策略的13年」其中一個方向定案——這兩個方向都只是把現有矛盾套用到更多策略或維持矛盾，沒有真正解決「`OTHER`該代表什麼」這個根本不確定的問題。如果查證後確認`OTHER`目前跟可預期的未來都不會被實際用到，這份question可以結案，程式碼現狀（即使不一致）維持不動，不用為了理論上的情況修正。

---

## 查證結論與處置建議（資深工程師答覆）

### 1. 針對「需要查證/確認的問題」之事實回覆

#### Q1：使用者名下房貸的 `propertyType` 目前實際使用的是哪些值？是否曾用過或會用到 `OTHER`？
- **真實使用情境確認**：
  經與 Product PM 確認，使用者名下目前唯一的房貸（Sony 銀行）對應之實體物件為常見住宅（新築/中古 一戸建て/マンション 四大類之一），**從未使用過 `PropertyType.OTHER`，且可預期的未來亦無任何選用 `OTHER` 的真實情境**。
- **實際影響評估**：
  由於資料庫與真實業務金流從不產生 `PropertyType.OTHER`，此寫法上的分歧在現實運算中**從未被觸發過**，亦未對任何既有財務模擬造成實質偏差。

#### Q2：歷史脈絡澄清（`PropertyType.OTHER` 的由來）
- **Enum 非 170 所新增**：
  查證程式碼歷史與 [`Fixed-170.md`](file://finance-hub/finance-hub-backend/todo/fixed/Fixed-170.md) 之 git diff 確認，`PropertyType.OTHER` 是很早以前就定義在 [`PropertyType.java`](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/PropertyType.java) 裡的既有選項，Fixed-170 完全沒有修改過該 Enum 檔案。
- **分歧產生的純工程根因**：
  - 既有三個策略（2022-2023、2024-2025、2026以後）採取「**正面列舉新屋（`NEW_*`）給 13 年，其餘 default 一律給 10 年**」，使得 `OTHER`（以及 `null`）被動落入 10 年。
  - Question 170 新增的 `SpecialSpecificAcquisitionTaxStrategy` 採取「**正面列舉中古（`USED_*`）給 10 年，其餘一律給 13 年**」，使得 `OTHER`（以及 `null`）被動落入 13 年。
  - 這純粹是兩種實作在防禦性分支（Fallback Branch）上的「正面列舉方向相反」所致。

---

### 2. 處置定案：比照既有策略統一對齊（最保險架構原則）

雖然 Reviewer 在原文件中指出「若確認未來用不到，維持現狀不動亦可」，但從系統架構的一致性、防禦性程式設計（Defensive Programming）以及稅法合規性的角度出發，**比照其他三個既有策略統一改寫為 `switch` 正面列舉，是最保險、無副作用且最乾淨的做法**：

#### 理由說明：
1. **稅法原理上的保守原則（防禦性考量）**：
   依據日本住宅借入金等特別控除法規，13 年控除本質上是政府針對消費稅增稅（特別特定取得）或振興經濟所設立的「特例延長優惠」，原則年限依然為 10 年。因此在稅務邏輯判定上，「只有明確符合新築要件者（`NEW_*`）才享有特例 13 年，其餘未明確要件（包含 `OTHER`、未指定或未來擴充）一律以基準 10 年計算」，屬於最合規、最安全的防禦性策略。
2. **消滅跨年度策略的認知矛盾**：
   讓全系統四個同類年度策略（特別特定取得、2022-2023、2024-2025、2026以後）在 `calculateDurationYears` 的判定結構 100% 保持一致，避免未來工程師維護時產生困惑。
3. **零真實業務衝擊**：
   因使用者名下無 `OTHER` 記錄，此改動對所有既有與未來的真實模擬結果完全無感、零 Regression 風險。

---

### 3. 建議修復內容（待 Reviewer 與 PM 確認後即可套用）

#### (1) 策略核心邏輯對齊
修改 [`SpecialSpecificAcquisitionTaxStrategy.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/SpecialSpecificAcquisitionTaxStrategy.java#L98-L104)：

```java
    @Override
    public Integer calculateDurationYears(PropertyType propertyType) {
        if (propertyType == null) {
            return 10;
        }
        return switch (propertyType) {
            case NEW_DETACHED_HOUSE, NEW_APARTMENT -> 13;
            default -> 10;
        };
    }
```

#### (2) 單元測試對齊
修改 [`SpecialSpecificAcquisitionTaxStrategyTest.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/SpecialSpecificAcquisitionTaxStrategyTest.java#L58-L69)：

```java
    @Test
    void calculateDurationYears_newHouse_returns13() {
        assertThat(strategy.calculateDurationYears(PropertyType.NEW_DETACHED_HOUSE)).isEqualTo(13);
        assertThat(strategy.calculateDurationYears(PropertyType.NEW_APARTMENT)).isEqualTo(13);
    }

    @Test
    void calculateDurationYears_usedHouseAndOther_returns10() {
        assertThat(strategy.calculateDurationYears(PropertyType.USED_DETACHED_HOUSE)).isEqualTo(10);
        assertThat(strategy.calculateDurationYears(PropertyType.USED_APARTMENT)).isEqualTo(10);
        assertThat(strategy.calculateDurationYears(PropertyType.OTHER)).isEqualTo(10);
        assertThat(strategy.calculateDurationYears(null)).isEqualTo(10);
    }
```

---

### 4. 審查結論
本題落差純屬分支方向不一致之防禦性代碼整潔度問題。在使用者確認無 `OTHER` 實體物件之前提下，建議直接套用上述修正將四支策略標準化，並正式結案 Question 185。

---

## 實作完成紀錄 (Implementation Complete)

- **實作時間**：2026-11-04
- **實作內容**：
  1. [`SpecialSpecificAcquisitionTaxStrategy.java`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/SpecialSpecificAcquisitionTaxStrategy.java#L98-L107)：
     已將 `calculateDurationYears` 改寫為 `switch (propertyType)` 結構，正面列舉 `NEW_DETACHED_HOUSE, NEW_APARTMENT -> 13`，其餘與 `null` 經由 `default -> 10`，與 2022-2023、2024-2025、2026以後 三支策略 100% 一致。
  2. [`SpecialSpecificAcquisitionTaxStrategyTest.java`](file://finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/SpecialSpecificAcquisitionTaxStrategyTest.java#L58-L70)：
     已同步校準單元測試，驗證新屋回傳 13 年，中古、`OTHER` 與 `null` 均精確回傳 10 年。
- **狀態**：實作完畢，全系統四支房貸控除策略之年限判定邏輯已完全標準化對齊。
