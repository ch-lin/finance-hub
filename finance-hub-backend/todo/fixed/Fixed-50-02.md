# Fixed-50-02：Fixed-50 修正報告驗證結果——薪資重複計算已修復，但共用 `monthsToAdd` 變數導致當月「支出／扣款／年金月數」被連帶少算一個月

## 驗證結論 (Verification Verdict)

**Fixed-50 原本描述的「當月薪資單已入帳卻被 `monthsToAdd` 又補算一次」問題已經確實修復**（`SimulationContext.currentMonthSalaryRecorded` 標記、`SalaryContextBuilder` 偵測邏輯、`YearlySimulationPipeline` 用它來決定 `monthsToAdd` 是否含當月，三者皆已如修正報告所述實作完成）。

但這個修正方式，是直接修改了 `monthsToAdd` 這個被整條 pipeline 共用的變數本身，而不是只修改「薪資推算」這一段自己用的月數。`monthsToAdd` 除了用在薪資推算之外，同時也被支出（生活費、保費、教育費、YEARLY 事件比例分攤）、NISA/DC/iDeCo 提撥、厚生年金/國民年金月數與保費等**完全不同的計算**直接引用。這些計算跟「當月薪資單有沒有登錄」毫無關係，卻因為共用同一個變數，被連帶影響。

## 相關檔案 (Files Involved)

| Class / 檔案 | 檔案路徑 |
|---|---|
| `SimulationContext.java` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java` |
| `SalaryContextBuilder.java` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java` |
| `YearlySimulationPipeline.java` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java` |
| `IncomeExpenseCalculator.java` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java` |
| `TaxAdvantagedAccountProjector.java` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java` |

## 現況說明與問題原因 (Current State & Cause)

修正報告實作的邏輯（`YearlySimulationPipeline.java:86-90`）：
```java
if (age == initialAge) {
    monthsToAdd = context.isCurrentMonthSalaryRecorded()
            ? 12 - currentMonth
            : 12 - currentMonth + 1;
}
```
這個 `monthsToAdd` 接著被直接傳入並用於（節錄，皆為現有既存邏輯，未被本次修正碰過）：

- `IncomeExpenseCalculator.java:138`：`long annualExpenses = context.getBaselineMonthlyExpenses() * monthsToAdd;`（生活費）
- 同檔案 165-171 行（保費比例分攤）、201-202 行（YEARLY 事件比例分攤）、229-231 行（教育費比例分攤）
- `TaxAdvantagedAccountProjector.java` 55-56、78-79、83-84、151、185、205 行：NISA / DC / iDeCo 提撥金額 `fund.getMonthlyContribution() * monthsToAdd`
- `YearlySimulationPipeline.java` 158、232、235、244-246 行：厚生年金月數、國民年金月數與保費、退休後國民健康保險保費，皆以 `monthsToAdd` 累加

這些計算的正確依據應該是「今天實際日期之後，還有幾個月尚未真正發生（尚未實際支出/扣款）」，跟「當月的薪資單有沒有先被登錄進系統」是兩個完全不同的問題。系統中沒有任何 `ytdExpenses`（已實際發生支出）之類的對應欄位可以在這個情境下反向補償——只要 `currentMonthSalaryRecorded` 為 `true`，當月就會同時從「薪資」和「支出/提撥/年金」兩邊一起被跳過，而不是只跳過薪資那一邊。

### 具體失敗情境 (Concrete Scenario)

假設今天是 2026-09-09（`currentMonth = 9`）。使用者在 9 月初拿到並登錄了 9 月薪資單，然後在今天（9 月 9 日）執行模擬。

- `currentMonthSalaryRecorded = true` → `monthsToAdd = 12 - 9 = 3`（僅 10、11、12 月）。
- 薪資面：`ytdBaseSalary` 已含 9 月薪資，`monthsToAdd` 不再補算 9 月 → 這部分完全正確，Fixed-50 原本的 bug 確實已解決。
- 支出面：`annualExpenses = baselineMonthlyExpenses * 3`，9 月的生活費（假設 ¥300,000）完全沒有被計入——但現實中，使用者 9 月的房租、水電、信用卡帳單很可能到 9 月底才會實際扣款，此時尚未發生，`currentLiquidCash`（真實銀行餘額）裡也還沒扣掉這筆錢。這筆 9 月支出就這樣從模擬中整個消失，既不在「已發生」的真實餘額裡被扣過，也不在「剩餘期間推算」裡被算到。
- 同理，9 月的 NISA/DC/iDeCo 提撥、厚生年金/國民年金月數與保費，也會被少算一個月。

淨效應：`netSavings`、`remainingCashThisYear`、`projectedLiquidCash`、`projectedNetWorth` 全部被系統性高估約一個月的生活費（外加較小幅度的提撥與年金低估）。更违反直覺的是：如果使用者只是把模擬執行時間提前一天到 9 月 8 日（薪資單尚未登錄），`monthsToAdd` 就會變成 4，9 月支出反而會被正確計入——單純「有沒有先輸入這個月的薪資單」這個跟支出完全無關的動作，卻決定了這個月的生活費要不要被算進模擬，這正是 Fixed-50 原本想避免的「兩段邏輯假設互相矛盾」問題，只是從薪資面轉移到了支出/提撥/年金面。

這正好命中使用者原始 Review 要求裡特別強調的重點：「不管是任何財務計算，都應比照實際人生現金水位去做準確推算，因此不能少掉任何可能需要檢討的範圍」——這裡就是少掉了當月支出這個範圍。

## 建議方向 (Suggested Fix)

不要讓 `currentMonthSalaryRecorded` 去改動整條 pipeline 共用的 `monthsToAdd`。應該把「薪資當月是否已入帳」這個判斷，侵入範圍限縮在薪資計算本身。

目前 `YearlySimulationPipeline.java:84-104` 的實際程式碼是：
```java
int monthsToAdd = 12;
int currentMonth = nowMonth.getMonthValue();
if (age == initialAge) {
    monthsToAdd = context.isCurrentMonthSalaryRecorded()
            ? 12 - currentMonth
            : 12 - currentMonth + 1;
}

long annualizedSalaryRate;
long effectiveAnnualSalary;

if (age == initialAge) {
    effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
            context, careerEvents, context.getYtdBaseSalary(), monthsToAdd);
    annualizedSalaryRate = context.getCurrentAnnualSalary();
} else {
    annualizedSalaryRate = incomeExpenseCalculator.calculateSalaryForYear(context, careerEvents);
    effectiveAnnualSalary = annualizedSalaryRate;
}
long annualExpenses = incomeExpenseCalculator.calculateExpensesForYear(context, lifeEvents, monthsToAdd,
        currentMonth);
```
應改成：
```java
int monthsToAdd = 12;
int currentMonth = nowMonth.getMonthValue();
if (age == initialAge) {
    monthsToAdd = 12 - currentMonth + 1;
}

long annualizedSalaryRate;
long effectiveAnnualSalary;

if (age == initialAge) {
    int salaryMonthsToAdd = context.isCurrentMonthSalaryRecorded()
            ? 12 - currentMonth
            : monthsToAdd;
    effectiveAnnualSalary = incomeExpenseCalculator.calculateSalaryForYear(
            context, careerEvents, context.getYtdBaseSalary(), salaryMonthsToAdd);
    annualizedSalaryRate = context.getCurrentAnnualSalary();
} else {
    annualizedSalaryRate = incomeExpenseCalculator.calculateSalaryForYear(context, careerEvents);
    effectiveAnnualSalary = annualizedSalaryRate;
}
long annualExpenses = incomeExpenseCalculator.calculateExpensesForYear(context, lifeEvents, monthsToAdd,
        currentMonth);
```
差異只有兩處：(1) `monthsToAdd` 本身的賦值不再讀 `context.isCurrentMonthSalaryRecorded()`，永遠是 `12 - currentMonth + 1`——這一行本身完全不變，只是拿掉三元判斷；(2) 在 `age == initialAge` 分支內新增一個只在這個分支內存在的局部變數 `salaryMonthsToAdd`，把原本要塞進 `monthsToAdd` 的「當月薪資單是否已入帳」判斷改塞進這個新變數，然後把 `incomeExpenseCalculator.calculateSalaryForYear(...)` 呼叫的第 4 個參數從 `monthsToAdd` 換成 `salaryMonthsToAdd`。這個呼叫本身（方法名稱、其他 3 個參數 `context`、`careerEvents`、`context.getYtdBaseSalary()`）完全不變，因此 `calculateSalaryForYear` 內部既有的 `CareerEvent` 處理與 `startMonth` 推算邏輯不受影響、完全保留。下面緊接著的 `calculateExpensesForYear(context, lifeEvents, monthsToAdd, currentMonth)`（以及 `monthsToAdd` 的其他既有消費者：`TaxAdvantagedAccountProjector` 的 NISA/DC/iDeCo 提撥、厚生年金/國民年金月數與保費計算）完全不用改，因為它們讀的 `monthsToAdd` 現在已經不受薪資單登錄狀態影響。

**注意：此修正方向不要引入 `SimulationContext.getBaseMonthlySalary()` 或任何手動重算年薪的算式（例如 `context.getYtdBaseSalary() + salaryMonthsToAdd * <月薪>` 這種寫法）——`SimulationContext` 上沒有這個方法，這樣寫不僅無法編譯，也會繞過 `IncomeExpenseCalculator.calculateSalaryForYear` 既有的 `CareerEvent`（年中調薪、獎金變動等）處理邏輯，導致比原始 bug 更嚴重的計算錯誤。唯一要做的改動就是「新增 `salaryMonthsToAdd` 局部變數、把它傳進既有 `calculateSalaryForYear` 呼叫的第 4 個參數」，不要引入任何新的薪資計算公式。**

## 修正實作報告 (Implementation Report)

已根據精確分流的原則完成實作，成功解除了 `monthsToAdd` 變數在薪資與支出/提撥之間的錯誤耦合。

### 核心修改 (Core Changes)
- **`YearlySimulationPipeline.java`**：
  1. 將全域共用的 `monthsToAdd` 在過渡年中無條件還原為 `12 - currentMonth + 1`，讓所有下游（生活費、教育費、NISA、DC、年金、健保等）重新獲得正確且符合物理現實的時間基準。
  2. 在 `age == initialAge` 的薪資計算區塊內，新增專屬的局部變數 `salaryMonthsToAdd`。
  3. 將 `context.isCurrentMonthSalaryRecorded()` 判定移交給 `salaryMonthsToAdd` 處理，並將其作為第四個參數傳入 `calculateSalaryForYear`。

**狀態**：✅ 邊際效應已徹底消除。薪資推算能正確防範 Double Count，同時所有的支出與固定提撥也不會再因為「薪資已經登錄」而憑空消失少算一個月。系統各個層面的現金流與時間基準都完美回歸正確狀態。
