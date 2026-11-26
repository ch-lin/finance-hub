# Fixed 70：2026 年及以後入住的房貸沒有對應的稅務扣除策略，且兩層各自猜測的 fallback 數值互相矛盾

## 嚴重程度
High

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/TaxStrategyFactory.java`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2024To2025TaxStrategy.java`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2022To2023TaxStrategy.java`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Pre2022TaxStrategy.java`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java:75-120`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:443-456`

## 現狀
`TaxStrategyFactory.getStrategy` 的邏輯是：

```java
return strategies.stream()
        .filter(strategy -> strategy.isApplicable(occupancyDate))
        .findFirst()
        .orElse(null); // Return null if no strategy matches (e.g., future dates without rules yet)
```

目前註冊的三個策略中，`Years2024To2025TaxStrategy` 的適用範圍被寫死在：

```java
!occupancyDate.isBefore(2024-01-01) && occupancyDate.isBefore(2026-01-01)
```

只涵蓋到 2025 年 12 月 31 日入住為止。當 `strategy == null` 時：

1. `MortgageRecordServiceImpl.validateAndApplyTaxLogic`（第 93-113 行）會 fallback 成直接採用前端送來的原始 `taxDeductionRate`/`maxTaxDeductionAmount`（對一般新建房貸而言，前端表單這兩個欄位預設值就是 `null`），且扣除年限固定寫死 `duration = 10`。
2. 若這筆記錄的 `taxDeductionRate`/`maxTaxDeductionAmount` 真的存成 `null`，`MortgageCalculator.java:446-451` 又會用另一組完全不同的硬編碼數字頂上去：

```java
Double rateObj = m.getTaxDeductionRate();
double rate = rateObj != null ? rateObj : 0.007;
long deduction = (long) (balance * rate);

Long maxDeductionObj = m.getMaxTaxDeductionAmount();
long maxDeduction = maxDeductionObj != null ? maxDeductionObj : 273_000L;
```

這兩層 fallback 各自猜了一個數字，彼此不知道對方存在，猜出來的結果也對不上任何一個已知策略定義的貸款上限級距（30/35/40/45/50 百萬 × 對應費率）。對照現有策略對「一般住宅、無特殊憑證」情境的實際處理——`Years2024To2025TaxStrategy.calculateMaxAmount` 對 `GENERAL` 明確回傳 `0`，註解寫「return 0 for safety: 高估支出」——`MortgageCalculator` 這組 `0.007`/`273,000` 反而比系統自己認定的保守值更寬鬆，不是一個刻意算出來的數字。

## 原因
問題的根本不是「這個 fallback 數字夠不夠保守」，而是：當 `occupancyDate` 落在任何策略都沒涵蓋的區間時，系統有兩層各自獨立、互不知情的猜測邏輯在補值，猜出來的結果彼此矛盾。這種「找不到規則就每層各自瞎猜一個數字」的寫法，只會讓之後每多一個年份區間，就要同時排查、修改兩個檔案的 if-else，越改越難確認邏輯是否一致。

## 具體錯誤情境
使用者在 2026 年 3 月購入新建住宅並開始入住，將房貸記錄輸入系統。`TaxStrategyFactory.getStrategy(2026-03-XX)` 找不到任何 `isApplicable` 為 true 的策略，回傳 `null`。`MortgageRecordServiceImpl` fallback 使用前端送來的原始值（此案例為 `null`），扣除年限固定為 10 年。因為 `taxDeductionRate`/`maxTaxDeductionAmount` 存成 `null`，模擬引擎的 `MortgageCalculator` 又用 `0.007`/`273,000` 這組跟任何實際規則都對不上的數字去算退稅，讓使用者拿到一個看起來「有算出東西」、但實際上沒有任何法規依據支撐的退稅金額。

## 建議修正（討論後更新，2026-09-10）

方向從「該用哪個保守數字 fallback」改成「不要在找不到規則的地方瞎猜任何數字」，具體做法：

1. **把目前最新的已知規則改成 open-ended，不要寫死上限年份。** `Years2024To2025TaxStrategy` 的 `isApplicable` 目前是 `!before(2024-01-01) && before(2026-01-01)`，建議拿掉上限判斷，只保留 `!before(2024-01-01)`。理由：截至目前為止沒有任何證據顯示 2026 年有新法案取代它，「沿用最後一次已知的合法規則」才是對現狀最忠實的描述，而不是無憑無據地假設它會突然失效。對應地，class 名稱應該改掉（例如 `Years2024OnwardsTaxStrategy`），避免名字暗示一個已經不存在的上限。
2. **`TaxStrategyFactory.getStrategy` 因此永遠不會再回傳 `null`。** 順勢把 `MortgageRecordServiceImpl` 裡「`strategy == null` 就 fallback 到前端原始值」的分支，以及 `MortgageCalculator.java:446-451` 那組對不上任何法規級距的硬編碼 `0.007`/`273,000`，兩處都整段移除——只要策略一定會 resolve，這兩個欄位理論上永遠不會是 `null`，這兩層各自猜測的邏輯就沒有存在的必要。
3. **在帳號建立流程中，明確揭露「本系統稅務規則最後依據的法律基準年」。** 把不確定性講清楚給使用者知道，而不是藏在某個猜出來的數字背後。萬一 2026 年之後真的有新法案通過，使用者至少知道系統目前算出來的數字是套用哪一年的規則，可以自行判斷、自行負責，而不是被系統一個沒有註明依據的數字誤導。

## 交給 Gemini 查證的部分

以下這些邊界/費率屬於日本住宅ローン控除實際法規事實，不是單靠讀 code 能驗證的問題，麻煩 Gemini 對照官方法條／國稅廳公告查證：

1. **Pre-2022 → 2022-2023 這個邊界本身是否正確？** 2022 年住宅ローン控除確實有修法（費率從 1.0% 降到 0.7%，扣除年限規則也有調整），麻煩逐一核對 `Pre2022TaxStrategy`／`Years2022To2023TaxStrategy` 現在寫的費率（1.0% / 0.7%）跟各類型貸款上限金額（40M/50M 對應 400,000/500,000；30M/35M/40M/45M/50M 對應 210,000/280,000/315,000/350,000）是否跟當年條文一致。
2. **2024-2025 之後（2026 年起）是否已有新的立法、或已公告但尚未生效的修法？** 這是本 issue 建議修正能否成立的前提——如果查到 2026 年其實已經有新規則（哪怕只是公告、尚未三讀），**麻煩明確寫進報告，不要略過**，我們會依此另外新增對應的策略區間，而不是繼續沿用 open-ended 的 2024-2025 規則。
3. 查證過程中若發現任何一個現有區間的邊界日期、費率或上限金額跟實際條文不同，**麻煩直接把正確的區間、費率、上限金額明確列在報告裡**，不要只說「有誤」，這樣才能對照著改，不用再往返確認一次。

## [Gemini 執行報告 (2026-09-10)]

**1. 法規查證結論**
- 經查日本稅制改正公告，Pre-2022 到 2022-2023 的邊界與費率 (1.0% -> 0.7%) 以及各級距上限金額完全正確，與當年條文一致。
- 針對 2026 年以後，**令和8年度 (2026年度) 稅制改正確實已頒布新規則**，將住宅ローン控除延長至 2030 年底，並針對中古屋及子育て世帯 (育兒家庭) 有對應的限額調升。
- 結論：不該將 `Years2024To2025TaxStrategy` 直接變成 open-ended，因為 2026 年起已有新法。

**2. 修正措施 (已實作)**
- **新增策略**：建立了 `Years2026OnwardsTaxStrategy`，適用於 2026 年起 (open-ended 確保不會再找不到策略)。因目前系統針對 2026 年並未追蹤配偶年齡與「子育て世帯」資格，此策略暫時保守沿用 2024-2025 級距，並已將子育て世帯支援開成獨立的 Feature Request (`issue-14` / `feature-mortgage-child-rearing-support.md`)。
- **移除 Fallback 瞎猜邏輯**：
  - `MortgageRecordServiceImpl.java` 中移除了找不到策略時 fallback 取用前端欄位的邏輯。現在若找不到策略會拋出 Exception，確保資料庫必存入來自策略的值。
  - `MortgageCalculator.java` 中移除了在讀取舊資料時若為 null，則回退到 `0.007` 與 `273,000` 的邏輯。現在直接讀取，若欄位缺失，系統將透過自動 unboxing 產生 NPE fail fast (因確認新上線前會清空舊資料，故不可能為 null)。
- **補齊單元測試**：撰寫了 `Years2026OnwardsTaxStrategyTest` 確認 2026 年後新策略的各項級距與年限邏輯正確，並由開發者手動執行 `mvn clean install` 驗證通過。
