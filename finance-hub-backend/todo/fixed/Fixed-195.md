# [FIXED] 195：191新增的退休前在職段薪資計算，把`calculateSalaryForYear`既有的「從某月算到12月」窗口語意，誤用成「從1月算到退休月」，方向整段搞反——只在退休年同時有`CareerEvent`時才會顯現，目前所有191測試都因為沒傳career events而完全沒踩到

## 這份issue怎麼來的
這是在191全系列（question-191、191-01~04）落地後,搭配完整git diff做一次全面複核時發現的新bug——這個問題之前完全沒有被任何191文件討論過，複核時才第一次發現，發現後已經親自讀程式碼、手算驗證過具體案例，不是單純轉述查證結果。

## 相關檔案
**以下行號是193、196一輪一輪落地後的最新位置（這兩輪在`YearlySimulationPipeline`裡插入了新的退休月判斷、iDeCo停繳門檻計算，195原本記錄的行號已經整段往後偏移，這裡已重新核對過）：**
- [IncomeExpenseCalculator.java:64-134](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)（`calculateSalaryForYear`方法本身，第81-84行`startMonth`計算、第124行`remainingMonths`計算——這個檔案不受193、196影響，行號本身沒有偏移）
- [YearlySimulationPipeline.java:117-136](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)（191-03新增的退休前在職段薪資計算，問題核心所在：第125-129行是起算年同時退休的分支、第133-134行是非起算年退休的分支；第120-123行`retirementMonth < currentMonth`這個既有防呆分支不受這份issue影響，維持不動）
- [question-191-03.md:62](question-191-03.md)（這份規格文件裡對這個方法的論證，查證確認論證本身就是錯的，詳見下方「現狀」）

## 現狀：`calculateSalaryForYear`的窗口語意，跟191-03挪用這個方法時的需求方向完全相反

查證[IncomeExpenseCalculator.java:81-84](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)：

```java
int startMonth = 1;
if (monthsToAdd != null) {
    startMonth = 12 - monthsToAdd + 1;
}
```

這個方法原本的設計，是服務「模擬起算過渡年」這一種情境——`monthsToAdd`的語意是「今年剩下幾個月」，窗口固定是「**從`startMonth`算到12月（年底）**」，例如`monthsToAdd=6`代表「從7月算到12月」。這個語意在原本唯一的使用場景（過渡年、從現在月份算到年底）裡是對的。

[question-191-03.md:62](question-191-03.md)這份規格文件在設計「退休前在職段」要怎麼呼叫這個方法時，寫了這段論證：

> `monthsToAdd`這個參數本身的語意是「從年底倒數幾個月開始算」（`startMonth = 12 - monthsToAdd + 1`），例如`monthsToAdd=6`代表「從7月開始算到12月」——**這個語意剛好符合「退休月之前有幾個月在職」的需求，只要傳入「退休月-1」這個月數當`monthsToAdd`，這個方法就會正確算出退休月之前這段期間的所得**，不需要修改這個方法本身。

**這段論證本身是錯的**：退休前在職段要算的窗口是「**從1月算到退休月**」，方向跟這個方法既有的「從`startMonth`算到12月」完全相反。`monthsToAdd=6`推出`startMonth = 12-6+1 = 7`，窗口是「7-12月」，不是「1-6月」——論證裡「剛好符合需求」這句話是方向判斷錯誤下的結論，不是真的符合。

查證[YearlySimulationPipeline.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)確認，這個錯誤的呼叫方式被套用在**兩處**：
- 第133-134行（非起算年退休）：`calculateSalaryForYear(context, careerEvents, null, retirementMonth)`，直接把`retirementMonth`當`monthsToAdd`傳入。
- 第125-129行（起算年同時是退休年）：`salaryMonthsToAdd = retirementMonth - currentMonth`（或`+1`），同樣是用「剩餘月數」的語意去傳參數，窗口方向一樣是錯的——這一處還有第二層問題，見下方「建議修正方向」裡對這一處的額外說明，不是單純把窗口方向反過來就夠。

## 為何是問題：只在退休年同時有`CareerEvent`時才會顯現，而這是真實世界完全合理的情境

**沒有`CareerEvent`時，這個bug完全不會顯現**：沒有薪資變動事件時，`calculateSalaryForYear`純粹是「薪資 × 月數 ÷ 12」的乘法，結果只跟月數多寡有關，跟窗口從哪個月開始無關——這正是為什麼191-03、191-04新增的所有測試都傳`Collections.emptyList()`當career events，完全沒有踩到這個問題，測試全部通過不代表這段邏輯是對的。

**但`CareerEvent`可以設定在任意一年的任意月份**，查證[question-191.md:124-130](question-191.md)已經確認「換工作、加薪、減薪」這類事件系統對發生年月沒有限制。「退休前最後一次加薪」是完全合理的真實情境——例如使用者在退休那一年的4月還領到一次調薪，6月才正式退休。

### 具體驗證案例（已親自手算，不是直接採信查證報告）

情境：6月退休（非起算年，`retirementMonth=6`），4月有一筆調薪事件（`ABSOLUTE_OVERRIDE`，年薪300萬→500萬）。

**正確答案**：在職段是1-6月，1-3月領300萬/12個月的月薪、4-6月領500萬/12個月的月薪：
```
1-3月：300萬 × 3/12 = 75萬
4-6月：500萬 × 3/12 = 125萬
合計：200萬
```

**程式碼實際算出來的**（呼叫`calculateSalaryForYear(context, careerEvents, null, 6)`）：
1. `startMonth = 12 - 6 + 1 = 7`（窗口被誤判成「7月起算」）
2. 迴圈走到4月的事件：`eventMonth=4 < startMonth=7`成立，進入[IncomeExpenseCalculator.java:96-104](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)這個分支——這筆事件被判定成「發生在窗口開始之前」，只更新`salaryAtLastMonth`為500萬（視為新的基準薪資），**不計入任何工時**，然後`continue`，`lastMonth`維持在`startMonth=7`不變
3. 迴圈結束，沒有任何一筆事件被正式計入`totalRemainingAnnualized`
4. `remainingMonths = 12 - 7 + 1 = 6`
5. `totalRemainingAnnualized = 500萬 × 6 = 3000萬`
6. `calculatedRemaining = 3000萬 / 12 = 250萬`

**程式碼輸出250萬，正確答案是200萬，誤差50萬日圓，且方向是系統性偏高**——4月那次調薪被誤判成「窗口開始前的基準更新」，導致整個退休前在職段（本該是1-6月）全部套用了調薪後的新薪資，完全沒反映前3個月的舊薪資。

這個誤差會連動影響所得稅、住民稅、社會保險（這些都是用`effectiveAnnualSalary`去算的），也會連動影響191-03新增的`pendingWorkingSegmentTaxAdjustment`（如果同一年還是起算年、需要跟YTD比較），讓整條分段計算鏈路的輸入在這個情境下就已經是錯的。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：新增一個方法，明確表達「從某月算到某月」的窗口語意，內部跟既有方法共用同一個private方法，避免重複程式碼**
- 做法：查證[IncomeExpenseCalculator.java:64-134](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)確認，方法裡只有兩處真正跟「窗口方向」綁死：第81-84行`startMonth`怎麼算、第124行`remainingMonths = 12 - lastMonth + 1`（預設窗口結束點是12月）。其餘部分（第70-78行薪資成長率調整、第88-90行事件排序、第92-122行整個事件迴圈核心邏輯、第129-132行寫回+四捨五入）完全跟窗口方向無關，只要給定`startMonth`/`endMonth`這兩個邊界，邏輯是通用的。可以把第80-131行整段邏輯抽成一個`private`方法（`calculateSalaryForWindow(context, careerEvents, ytdSalary, startMonth, endMonth)`，把第124行的`12`換成傳入的`endMonth`），兩個public方法各自只負責算出自己的`startMonth`/`endMonth`該是多少，再委派給這個共用方法：
  - 既有的`calculateSalaryForYear(..., monthsToAdd)`：算出`startMonth = 12 - monthsToAdd + 1`、`endMonth = 12`——跟現有行為完全一致，不影響任何既有呼叫點或測試。
  - 新方法`calculateSalaryForYearUntilMonth(context, careerEvents, ytdSalary, startMonth, endMonth)`：**`startMonth`跟`endMonth`都由呼叫端明確指定，不是方法內部自己推算成固定值**。這裡跟最初設想（方法內部固定`startMonth=1`）不一樣——理由見下方「需要特別注意的改寫細節」第4點：起算年那個呼叫點如果固定從1月開始算，會跟已經入帳的`ytdBaseSalary`重複計算。
- 優點：不動既有方法的既有行為；新方法的語意清楚、不會再被誤用；兩個public方法不再各自維護一整套重複的事件迴圈邏輯，共用邏輯集中在一處維護。
- 缺點：需要新增一個private方法、調整既有方法的內部實作（委派出去），改動範圍比單純「複製貼上寫一個新方法」略大一點，但換來的是不會再重複維護兩份相似邏輯。

**完整sample code**（逐行對應現有[IncomeExpenseCalculator.java:64-134](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java)，確保每一段既有行為都被保留，不是憑印象重寫）：

```java
// 既有方法：維持原本「從某月算到12月」的語意，呼叫端行為完全不變
public long calculateSalaryForYear(SimulationContext context, List<CareerEvent> careerEvents, Long ytdSalary,
        Integer monthsToAdd) {
    if (context.isRetired() && (monthsToAdd == null || monthsToAdd == 0)) {
        context.setCurrentAnnualSalary(0L);
        return 0L;
    }
    int startMonth = (monthsToAdd != null) ? (12 - monthsToAdd + 1) : 1;
    return calculateSalaryForWindow(context, careerEvents, ytdSalary, startMonth, 12);
}

// 新方法：191-03退休前在職段要用的窗口，startMonth/endMonth都由呼叫端指定，
// 不在方法內部反推，避免起算年那種「月數反推方向」反而算錯的狀況（見下方呼叫點說明）
public long calculateSalaryForYearUntilMonth(SimulationContext context, List<CareerEvent> careerEvents,
        Long ytdSalary, int startMonth, int endMonth) {
    if (context.isRetired() && endMonth < startMonth) {
        context.setCurrentAnnualSalary(0L);
        return 0L;
    }
    return calculateSalaryForWindow(context, careerEvents, ytdSalary, startMonth, endMonth);
}

// 共用核心邏輯：給定明確的 startMonth/endMonth 窗口，逐段累加事件、算出這段期間的年化所得
// 對應現有方法第70-131行，差異只在第124行的窗口結束點從寫死的12改成傳入的endMonth，
// 以及第96行的判斷簡化成只看startMonth（見下方說明）
private long calculateSalaryForWindow(SimulationContext context, List<CareerEvent> careerEvents,
        Long ytdSalary, int startMonth, int endMonth) {
    long currentSalary = context.getCurrentAnnualSalary();
    if (context.getYear() > context.getCurrentYear()) {
        var salarySettings = salaryUserSettingsRepository.findFirstBy().orElse(null);
        Double profileGrowthRate = salarySettings != null ? salarySettings.getSalaryGrowthRate() : null;
        double salaryGrowthRate = profileGrowthRate != null ? profileGrowthRate : 0.0;
        if (salaryGrowthRate > 0) {
            currentSalary = Math.round(currentSalary * (1.0 + salaryGrowthRate));
        }
    }

    long totalRemainingAnnualized = 0;
    int lastMonth = startMonth;
    long salaryAtLastMonth = currentSalary;

    List<CareerEvent> sortedEvents = careerEvents.stream()
            .sorted(Comparator.comparing(e -> e.getEventYearMonth()))
            .toList();

    for (CareerEvent event : sortedEvents) {
        if (event.getEventYearMonth().getYear() == context.getYear()) {
            int eventMonth = event.getEventYearMonth().getMonthValue();

            if (eventMonth < startMonth) {
                switch (event.getEventType()) {
                    case ABSOLUTE_OVERRIDE ->
                        salaryAtLastMonth = event.getEventValue().longValue();
                    case RELATIVE_CHANGE ->
                        salaryAtLastMonth = (long) (salaryAtLastMonth * event.getEventValue());
                }
                continue;
            }
            if (eventMonth > endMonth) {
                continue; // 事件發生在窗口結束之後，不屬於這段期間，跳過
            }

            int monthsAtOldSalary = eventMonth - lastMonth;
            if (monthsAtOldSalary > 0) {
                totalRemainingAnnualized += salaryAtLastMonth * monthsAtOldSalary;
            }

            long newSalary = salaryAtLastMonth;
            switch (event.getEventType()) {
                case ABSOLUTE_OVERRIDE ->
                    newSalary = event.getEventValue().longValue();
                case RELATIVE_CHANGE ->
                    newSalary = (long) (salaryAtLastMonth * event.getEventValue());
            }

            salaryAtLastMonth = newSalary;
            lastMonth = eventMonth;
        }
    }

    int remainingMonths = endMonth - lastMonth + 1;
    if (remainingMonths > 0) {
        totalRemainingAnnualized += salaryAtLastMonth * remainingMonths;
    }

    context.setCurrentAnnualSalary(salaryAtLastMonth);

    long calculatedRemaining = Math.round(totalRemainingAnnualized / 12.0);
    return ytdSalary != null ? (ytdSalary + calculatedRemaining) : calculatedRemaining;
}
```

**四處需要特別注意的改寫細節，不是單純機械搬動程式碼**：

1. **第96行`monthsToAdd != null && eventMonth < startMonth`這層判斷，共用方法裡簡化成只看`eventMonth < startMonth`，拿掉`monthsToAdd != null`這個條件**——這是刻意的簡化，不是遺漏。原本「整年、沒有窗口限制」的呼叫（`monthsToAdd=null`）會讓`startMonth`固定是1（既有方法第81行的預設值），而月份最小值就是1，`eventMonth < 1`永遠不可能成立——這代表原本那層`monthsToAdd != null`判斷，在`startMonth=1`的情境下本來就是多餘的保護，拿掉它不會改變任何既有行為，只是讓共用方法不需要知道「呼叫者原本有沒有傳`monthsToAdd`」這件事，介面更乾淨。

2. **新增`if (eventMonth > endMonth) { continue; }`這一段，是既有方法裡沒有的**——既有方法的窗口永遠是「算到12月」，所以不存在「事件發生在窗口結束之後」這種情況（最晚12月，不會有13月的事件）。但共用方法的`endMonth`可能是6月這種不是12月的值，如果事件發生在7月（窗口結束之後），必須明確跳過，不能讓它被誤判成「窗口內的事件」而被累加進`totalRemainingAnnualized`，也不能讓它推進`lastMonth`。這是方向A設計時新增的防呆，不是從既有程式碼搬過來的。

3. **`calculateSalaryForYearUntilMonth`裡的退休防衛判斷，用`endMonth < startMonth`取代既有的`monthsToAdd == null || monthsToAdd == 0`**——這個新方法不會被傳`null`，所以不需要處理null；但因為`startMonth`現在是呼叫端傳入的變數（不再固定是1），「沒有在職月份」這個邊界情況不能再用`endMonth == 0`判斷（`endMonth`是退休月，不會是0），要改成看區間本身是否為空（`endMonth < startMonth`），跟既有方法一致地回傳0並把`currentAnnualSalary`清零。

4. **`startMonth`改成由呼叫端傳入，不在方法內部固定成1**——這是跟最初設想最大的差異。原因：191-03有兩個呼叫點（見下方），其中「起算年同時是退休年」那個呼叫點，`ytdBaseSalary`已經包含1月到當前月份這段的薪資，如果方法內部固定`startMonth=1`去重新計算事件累加，會把1月到當前月份這段**再算一次**，跟`ytdBaseSalary`重複計算，整年薪資會被灌水。所以`startMonth`不能寫死，必須讓呼叫端依照「這段ytdSalary涵蓋到哪個月」自己決定`startMonth`該從哪裡開始，這跟既有`calculateSalaryForYear`裡`monthsToAdd`的既有語意是一致的，只是不再透過「月數反推」這個容易搞錯方向的中間步驟。

**191-03的兩處呼叫點（[YearlySimulationPipeline.java:125-129,133-134](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)）改成呼叫新方法**：
```java
// 第133-134行（非起算年退休）：ytdSalary是null，startMonth固定從1月算到退休月，
// 不存在跟ytdSalary重複計算的問題，這一處是方向A原本就能正確處理的情境：
effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYearUntilMonth(
        context, careerEvents, null, 1, retirementMonth);

// 第125-129行（起算年同時是退休年）：ytdBaseSalary已經涵蓋到currentMonth（或currentMonth-1，
// 視isCurrentMonthSalaryRecorded()而定），所以startMonth必須從「ytdSalary涵蓋範圍之後的下一個月」開始算，
// 不能再固定從1月開始——這跟現行（修195之前）程式碼裡salaryMonthsToAdd的月數語意完全一致，
// 只是不再用「月數」當參數、改用「起訖月份」本身，避免再被誤用成反方向：
int salaryStartMonth = context.isCurrentMonthSalaryRecorded() ? currentMonth + 1 : currentMonth;
effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYearUntilMonth(
        context, careerEvents, context.getYtdBaseSalary(), salaryStartMonth, retirementMonth);
```
這裡沿用的是現行程式碼本來就在算的`salaryMonthsToAdd`（`retirementMonth - currentMonth`或`+1`，取決於`isCurrentMonthSalaryRecorded()`）背後的那個「薪資窗口從哪個月開始」概念，只是改成直接用`startMonth`這個月份本身表達，不再透過「月數」這個中間值，也因此不會再發生195原本那種「把月數塞進一個反方向語意的方法」的誤用。

**方向B：讓既有方法接收一個「窗口方向」的旗標或改成接收明確的起訖月份，而不是「月數」這種容易被誤解方向的參數**
- 做法：把`monthsToAdd`這種「月數」參數，改成直接傳「起始月」「結束月」兩個明確的月份值，呼叫端自己決定要傳什麼區間，方法內部不用再猜測方向。
- 優點：一個方法處理所有窗口情境，不用維護兩份相似邏輯；介面本身更不容易被誤用（因為不再需要「月數反推月份」這種容易搞錯方向的心智負擔）。
- 缺點：改動既有方法的簽名，需要確認所有既有呼叫點（包含過渡年那些）要怎麼跟著改，影響範圍比方向A大。

## 這份issue沒有解決的部分
- 具體要採用方向A還是方向B，留給使用者決定。
- 這個bug修正後，是否需要回頭檢查191-03/191-04的測試案例，補上「退休年同時有`CareerEvent`」這個組合的測試覆蓋——留給實作時處理，這份issue只確認問題存在、不代為規劃測試計畫。
- 191-03文件本身那段錯誤的論證（第62行）要不要回頭修正或標注——使用者對191系列文件有「不回頭修改既有歷史記錄」的既有習慣（例如issue-172、question-191都是保留原文不動、後續討論另開新文件），這份issue不代為決定191-03.md本身要不要被修改，單純記錄這裡發現的程式碼bug。


---

## 修正報告書（2026-11-25 實作完成）

### 1. 實作摘要與設計選擇
本次實作完全依照本份文件「方向 A」規格落地：
- **新增明確起訖月份的薪資窗口方法**：於 `IncomeExpenseCalculator` 抽取共用的 `private calculateSalaryForWindow(context, careerEvents, ytdSalary, startMonth, endMonth)`，保留既有 `calculateSalaryForYear`（「從某月算到 12 月」）語意完全不變，並新增 `calculateSalaryForYearUntilMonth(context, careerEvents, ytdSalary, startMonth, endMonth)` 服務退休前在職段。
- **保留原縮排以利 Review**：`calculateSalaryForWindow` 刻意保留原 `else` 區塊既有 12 格縮排，使 `git diff` 僅精準呈現 3 處實質邏輯變動（拿掉多餘的 `monthsToAdd != null`、新增 `if (eventMonth > endMonth) { continue; }`、將 `12` 替換為 `endMonth`），待 review 後可再以 IDE 自動排版整理縮排。
- **修正 `YearlySimulationPipeline` 兩處退休年薪資呼叫點**：起算年且退休時以 `salaryStartMonth = isCurrentMonthSalaryRecorded() ? currentMonth + 1 : currentMonth` 計算起訖區間（避免與 `ytdBaseSalary` 重複計入）；非起算年退休時固定傳入 `1` 至 `retirementMonth`。

---

### 2. 修改檔案清單與變更明細

#### 產品程式碼（2 個檔案）
1. **[IncomeExpenseCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java#L53-L140)**
   - `calculateSalaryForYear`（[L53–L60](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java#L53-L60)）：維持原 `isRetired && (monthsToAdd == null || monthsToAdd == 0)` 防衛，算出 `startMonth` 後委派 `calculateSalaryForWindow(..., startMonth, 12)`。
   - `calculateSalaryForYearUntilMonth`（[L66–L73](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java#L66-L73)）：以 `isRetired && endMonth < startMonth` 處理空區間回傳 `0L`，並委派 `calculateSalaryForWindow(..., startMonth, endMonth)`。
   - `calculateSalaryForWindow`（[L75–L140](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java#L75-L140)）：新增 `if (eventMonth > endMonth) { continue; }` 略過退休後事件，並將結算月數改為 `endMonth - lastMonth + 1`。

2. **[YearlySimulationPipeline.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L124-L137)**
   - 起算年退休分支（[L124–L132](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L124-L132)）：改傳 `salaryStartMonth` 與 `retirementMonth` 呼叫 `calculateSalaryForYearUntilMonth`。
   - 非起算年退休分支（[L133–L137](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java#L133-L137)）：改傳 `1` 與 `retirementMonth` 呼叫 `calculateSalaryForYearUntilMonth`。

#### 測試程式碼（4 個檔案）
1. **[IncomeExpenseCalculatorTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculatorTest.java#L176-L241)**（新增 4 個測試案例）：
   - `testCalculateSalaryForYearUntilMonth_aprilRaiseBeforeJuneRetirement_proratesCorrectly`（300 萬→500 萬於 4 月調薪、6 月退休：驗證 1–3 月 + 4–6 月 = `2,000,000L`）
   - `testCalculateSalaryForYearUntilMonth_eventAfterEndMonth_ignoredInWindow`（8 月退休後事件不影響 1–6 月窗口）
   - `testCalculateSalaryForYearUntilMonth_initialYearWithYtdSalary_startsAfterYtdMonth`（起算年含 `ytdSalary` 時從 `startMonth=4` 算起、不重複計入 1–3 月）
   - `testCalculateSalaryForYearUntilMonth_emptyWindowWhenRetired_returnsZero`（`endMonth < startMonth` 回傳 `0`）
2. **[YearlySimulationPipelineTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java#L388-L726)**：更新 5 處退休年 mock stub 為 `calculateSalaryForYearUntilMonth(...)`。
3. **[YearlySimulationPipelineIdecoLifecycleTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java#L130-L134)** 與 **[YearlySimulationPipelineDcLifecycleTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineDcLifecycleTest.java#L134-L138)**：補上 `calculateSalaryForYearUntilMonth(...)` 的 lenient stub。

---

### 3. 驗證結果
- 全數 940 筆以上測試（含新增 4 筆 `IncomeExpenseCalculatorTest` 與既有管線測試）均已於本機執行通過。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                            10017  12.97G   6.79    21:12:50  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
index 62611050..83df7ba5 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java
@@ -55,71 +55,88 @@ public class IncomeExpenseCalculator {
         if (context.isRetired() && (monthsToAdd == null || monthsToAdd == 0)) {
             context.setCurrentAnnualSalary(0L);
             return 0L;
-        } else {
-            long currentSalary = context.getCurrentAnnualSalary();
-            if (context.getYear() > context.getCurrentYear()) {
-                var salarySettings = salaryUserSettingsRepository.findFirstBy().orElse(null);
-                Double profileGrowthRate = salarySettings != null ? salarySettings.getSalaryGrowthRate() : null;
-                double salaryGrowthRate = profileGrowthRate != null ? profileGrowthRate : 0.0;
-                if (salaryGrowthRate > 0) {
-                    currentSalary = Math.round(currentSalary * (1.0 + salaryGrowthRate));
-                }
-            }
+        }
+        int startMonth = (monthsToAdd != null) ? (12 - monthsToAdd + 1) : 1;
+        return calculateSalaryForWindow(context, careerEvents, ytdSalary, startMonth, 12);
+    }
 
-            long totalRemainingAnnualized = 0;
-            int startMonth = 1;
-            if (monthsToAdd != null) {
-                startMonth = 12 - monthsToAdd + 1;
+    /**
+     * Calculates salary for an explicit [startMonth, endMonth] window (1-12),
+     * used for the pre-retirement working segment in a split retirement year.
+     */
+    public long calculateSalaryForYearUntilMonth(SimulationContext context, List<CareerEvent> careerEvents,
+            Long ytdSalary, int startMonth, int endMonth) {
+        if (context.isRetired() && endMonth < startMonth) {
+            context.setCurrentAnnualSalary(0L);
+            return 0L;
+        }
+        return calculateSalaryForWindow(context, careerEvents, ytdSalary, startMonth, endMonth);
+    }
+
+    private long calculateSalaryForWindow(SimulationContext context, List<CareerEvent> careerEvents,
+            Long ytdSalary, int startMonth, int endMonth) {
+        long currentSalary = context.getCurrentAnnualSalary();
+        if (context.getYear() > context.getCurrentYear()) {
+            var salarySettings = salaryUserSettingsRepository.findFirstBy().orElse(null);
+            Double profileGrowthRate = salarySettings != null ? salarySettings.getSalaryGrowthRate() : null;
+            double salaryGrowthRate = profileGrowthRate != null ? profileGrowthRate : 0.0;
+            if (salaryGrowthRate > 0) {
+                currentSalary = Math.round(currentSalary * (1.0 + salaryGrowthRate));
             }
-            int lastMonth = startMonth;
-            long salaryAtLastMonth = currentSalary;
-
-            List<CareerEvent> sortedEvents = careerEvents.stream()
-                    .sorted(Comparator.comparing(e -> e.getEventYearMonth()))
-                    .toList();
-
-            for (CareerEvent event : sortedEvents) {
-                if (event.getEventYearMonth().getYear() == context.getYear()) {
-                    int eventMonth = event.getEventYearMonth().getMonthValue();
-
-                    if (monthsToAdd != null && eventMonth < startMonth) {
-                        switch (event.getEventType()) {
-                            case ABSOLUTE_OVERRIDE ->
-                                salaryAtLastMonth = event.getEventValue().longValue();
-                            case RELATIVE_CHANGE ->
-                                salaryAtLastMonth = (long) (salaryAtLastMonth * event.getEventValue());
-                        }
-                        continue;
-                    }
+        }
 
-                    int monthsAtOldSalary = eventMonth - lastMonth;
-                    if (monthsAtOldSalary > 0) {
-                        totalRemainingAnnualized += salaryAtLastMonth * monthsAtOldSalary;
-                    }
+        long totalRemainingAnnualized = 0;
+        int lastMonth = startMonth;
+        long salaryAtLastMonth = currentSalary;
 
-                    long newSalary = salaryAtLastMonth;
+        List<CareerEvent> sortedEvents = careerEvents.stream()
+                .sorted(Comparator.comparing(e -> e.getEventYearMonth()))
+                .toList();
+
+        for (CareerEvent event : sortedEvents) {
+            if (event.getEventYearMonth().getYear() == context.getYear()) {
+                int eventMonth = event.getEventYearMonth().getMonthValue();
+
+                if (eventMonth < startMonth) {
                     switch (event.getEventType()) {
                         case ABSOLUTE_OVERRIDE ->
-                            newSalary = event.getEventValue().longValue();
+                            salaryAtLastMonth = event.getEventValue().longValue();
                         case RELATIVE_CHANGE ->
-                            newSalary = (long) (salaryAtLastMonth * event.getEventValue());
+                            salaryAtLastMonth = (long) (salaryAtLastMonth * event.getEventValue());
                     }
+                    continue;
+                }
+                if (eventMonth > endMonth) {
+                    continue;
+                }
 
-                    salaryAtLastMonth = newSalary;
-                    lastMonth = eventMonth;
+                int monthsAtOldSalary = eventMonth - lastMonth;
+                if (monthsAtOldSalary > 0) {
+                    totalRemainingAnnualized += salaryAtLastMonth * monthsAtOldSalary;
                 }
-            }
 
-            int remainingMonths = 12 - lastMonth + 1;
-            if (remainingMonths > 0) {
-                totalRemainingAnnualized += salaryAtLastMonth * remainingMonths;
-            }
+                long newSalary = salaryAtLastMonth;
+                switch (event.getEventType()) {
+                    case ABSOLUTE_OVERRIDE ->
+                        newSalary = event.getEventValue().longValue();
+                    case RELATIVE_CHANGE ->
+                        newSalary = (long) (salaryAtLastMonth * event.getEventValue());
+                }
 
-            context.setCurrentAnnualSalary(salaryAtLastMonth);
+                salaryAtLastMonth = newSalary;
+                lastMonth = eventMonth;
+            }
+        }
 
-            long calculatedRemaining = Math.round(totalRemainingAnnualized / 12.0);
-            return ytdSalary != null ? (ytdSalary + calculatedRemaining) : calculatedRemaining;
+        int remainingMonths = endMonth - lastMonth + 1;
+        if (remainingMonths > 0) {
+            totalRemainingAnnualized += salaryAtLastMonth * remainingMonths;
         }
+
+        context.setCurrentAnnualSalary(salaryAtLastMonth);
+
+        long calculatedRemaining = Math.round(totalRemainingAnnualized / 12.0);
+        return ytdSalary != null ? (ytdSalary + calculatedRemaining) : calculatedRemaining;
     }
 
     public long calculateExpensesForYear(SimulationContext context, List<LifeEvent> lifeEvents, int monthsToAdd,
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
index 190ec556..c2306fbc 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java
@@ -122,16 +122,16 @@ public class YearlySimulationPipeline {
                     effectiveAnnualSalary = 0L;
                     annualizedSalaryRate = 0L;
                 } else {
-                    int salaryMonthsToAdd = context.isCurrentMonthSalaryRecorded()
-                            ? retirementMonth - currentMonth
-                            : retirementMonth - currentMonth + 1;
-                    effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
-                            context, careerEvents, context.getYtdBaseSalary(), Math.max(0, salaryMonthsToAdd));
+                    int salaryStartMonth = context.isCurrentMonthSalaryRecorded()
+                            ? currentMonth + 1
+                            : currentMonth;
+                    effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYearUntilMonth(
+                            context, careerEvents, context.getYtdBaseSalary(), salaryStartMonth, retirementMonth);
                     annualizedSalaryRate = context.getCurrentAnnualSalary();
                 }
             } else {
-                effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
-                        context, careerEvents, null, retirementMonth);
+                effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYearUntilMonth(
+                        context, careerEvents, null, 1, retirementMonth);
                 annualizedSalaryRate = context.getCurrentAnnualSalary();
             }
         } else {
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculatorTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculatorTest.java
index a17e7298..4b7e1aaa 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculatorTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculatorTest.java
@@ -21,6 +21,8 @@ import org.junit.jupiter.api.Test;
 
 import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
 import ch.lin.finance.hub.backend.api.domain.model.config.FinanceHubConfig;
+import ch.lin.finance.hub.backend.api.domain.model.event.CareerEvent;
+import ch.lin.finance.hub.backend.api.domain.model.event.CareerEventType;
 import ch.lin.finance.hub.backend.api.domain.model.event.LifeEvent;
 import ch.lin.finance.hub.backend.api.domain.model.event.LifeEventFrequency;
 
@@ -120,7 +122,8 @@ class IncomeExpenseCalculatorTest {
                 1_200_000L);
         List<LifeEvent> events = List.of(event);
 
-        // In 2026, months 4..8 already occurred; active remaining months are 9..12 (4 months)
+        // In 2026, months 4..8 already occurred; active remaining months are 9..12 (4
+        // months)
         long expenses = calculator.calculateExpensesForYear(context, events, 4, 9);
         assertThat(expenses).isEqualTo(400_000L);
     }
@@ -169,4 +172,71 @@ class IncomeExpenseCalculatorTest {
         long expenses2033 = calculator.calculateExpensesForYear(context2033, events, 12, 1);
         assertThat(expenses2033).isEqualTo(800_000L);
     }
+
+    @Test
+    void testCalculateSalaryForYearUntilMonth_aprilRaiseBeforeJuneRetirement_proratesCorrectly() {
+        SimulationContext context = createContext(2026, 2026);
+        context.setCurrentAnnualSalary(3_000_000L);
+        context.setRetired(true);
+
+        CareerEvent aprilRaise = new CareerEvent(
+                YearMonth.of(2026, 4),
+                "April Raise",
+                CareerEventType.ABSOLUTE_OVERRIDE,
+                5_000_000.0);
+
+        // Jan-Mar (3 months) at 3,000,000 = 750,000; Apr-Jun (3 months) at 5,000,000 =
+        // 1,250,000
+        long salary = calculator.calculateSalaryForYearUntilMonth(context, List.of(aprilRaise), null, 1, 6);
+        assertThat(salary).isEqualTo(2_000_000L);
+        assertThat(context.getCurrentAnnualSalary()).isEqualTo(5_000_000L);
+    }
+
+    @Test
+    void testCalculateSalaryForYearUntilMonth_eventAfterEndMonth_ignoredInWindow() {
+        SimulationContext context = createContext(2026, 2026);
+        context.setCurrentAnnualSalary(3_000_000L);
+        context.setRetired(true);
+
+        CareerEvent augustEvent = new CareerEvent(
+                YearMonth.of(2026, 8),
+                "Post-Retirement Event",
+                CareerEventType.ABSOLUTE_OVERRIDE,
+                6_000_000.0);
+
+        // Jan-Jun (6 months) at 3,000,000 = 1,500,000; August event must be ignored
+        long salary = calculator.calculateSalaryForYearUntilMonth(context, List.of(augustEvent), null, 1, 6);
+        assertThat(salary).isEqualTo(1_500_000L);
+        assertThat(context.getCurrentAnnualSalary()).isEqualTo(3_000_000L);
+    }
+
+    @Test
+    void testCalculateSalaryForYearUntilMonth_initialYearWithYtdSalary_startsAfterYtdMonth() {
+        SimulationContext context = createContext(2026, 2026);
+        context.setCurrentAnnualSalary(3_000_000L);
+        context.setRetired(true);
+
+        CareerEvent aprilRaise = new CareerEvent(
+                YearMonth.of(2026, 4),
+                "April Raise",
+                CareerEventType.ABSOLUTE_OVERRIDE,
+                5_000_000.0);
+
+        // YTD already covers Jan-Mar (1,000,000); window starts at April (startMonth=4,
+        // endMonth=6)
+        // Apr-Jun (3 months) at 5,000,000 = 1,250,000 -> total 2,250,000
+        long salary = calculator.calculateSalaryForYearUntilMonth(context, List.of(aprilRaise), 1_000_000L, 4, 6);
+        assertThat(salary).isEqualTo(2_250_000L);
+    }
+
+    @Test
+    void testCalculateSalaryForYearUntilMonth_emptyWindowWhenRetired_returnsZero() {
+        SimulationContext context = createContext(2026, 2026);
+        context.setCurrentAnnualSalary(3_000_000L);
+        context.setRetired(true);
+
+        long salary = calculator.calculateSalaryForYearUntilMonth(context, List.of(), 1_500_000L, 7, 6);
+        assertThat(salary).isZero();
+        assertThat(context.getCurrentAnnualSalary()).isZero();
+    }
 }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineDcLifecycleTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineDcLifecycleTest.java
index 4f3049ec..d3e8c90f 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineDcLifecycleTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineDcLifecycleTest.java
@@ -133,6 +133,9 @@ class YearlySimulationPipelineDcLifecycleTest {
                 .thenReturn(3_000_000L);
         lenient().when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt()))
                 .thenReturn(3_000_000L);
+        lenient()
+                .when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), anyInt(), anyInt()))
+                .thenReturn(3_000_000L);
         lenient().when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any()))
                 .thenReturn(new MortgageYearlyResult(0L, 0L, 0L, 0L, null));
     }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
index a14c442d..34c32806 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineIdecoLifecycleTest.java
@@ -129,6 +129,8 @@ class YearlySimulationPipelineIdecoLifecycleTest {
                 .thenReturn(3_000_000L);
         lenient().when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt()))
                 .thenReturn(3_000_000L);
+        lenient().when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), anyInt(), anyInt()))
+                .thenReturn(3_000_000L);
         lenient().when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any()))
                 .thenReturn(new MortgageYearlyResult(0L, 0L, 0L, 0L, null));
     }
diff --git a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
index 8aa4b71c..b20fb8d0 100644
--- a/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
+++ b/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipelineTest.java
@@ -385,7 +385,8 @@ class YearlySimulationPipelineTest {
         context.setYtdPension(270_000L);
         context.setYtdEmploymentInsurance(18_000L);
 
-        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), anyLong(), anyInt())).thenReturn(0L);
+        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), anyLong(), anyInt(), anyInt()))
+                .thenReturn(0L);
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
 
@@ -437,7 +438,8 @@ class YearlySimulationPipelineTest {
         context.setConfig(config);
         context.getProfile().setBirthDate(LocalDate.of(1966, 6, 15)); // Month 6
 
-        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), eq(6))).thenReturn(3_000_000L);
+        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), eq(1), eq(6)))
+                .thenReturn(3_000_000L);
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
 
@@ -487,7 +489,8 @@ class YearlySimulationPipelineTest {
 
         // Working segment salary for remaining months (e.g. 3,000,000)
         context.setYtdBaseSalary(1_500_000L);
-        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), anyInt())).thenReturn(3_000_000L);
+        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), anyInt(), anyInt()))
+                .thenReturn(3_000_000L);
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
 
@@ -571,7 +574,8 @@ class YearlySimulationPipelineTest {
         context.setConfig(config);
         context.getProfile().setBirthDate(LocalDate.of(1966, 4, 10)); // Birthday in April, but DECEMBER mode
 
-        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), eq(12))).thenReturn(salary);
+        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), eq(1), eq(12)))
+                .thenReturn(salary);
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                 new MortgageYearlyResult(0L, 0L, 0L, 0L, new Post2022InhabitantTaxLimitStrategy()));
 
@@ -717,7 +721,8 @@ class YearlySimulationPipelineTest {
         context.setConfig(config);
         context.getProfile().setBirthDate(LocalDate.of(1966, 6, 15)); // Month = 6 (split year: 6 working, 6 pension)
 
-        when(incomeExpenseCalculator.calculateSalaryForYear(any(), any(), any(), eq(6))).thenReturn(3_000_000L);
+        when(incomeExpenseCalculator.calculateSalaryForYearUntilMonth(any(), any(), any(), eq(1), eq(6)))
+                .thenReturn(3_000_000L);
         when(mortgageSimulationService.simulateMortgagesForYear(any(), any(), any(), any())).thenReturn(
                 new MortgageYearlyResult(0L, 0L, 250_000L, 20_000_000L, new Post2022InhabitantTaxLimitStrategy()));
 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                 10018  11.99G   3.95    21:18:51  
 