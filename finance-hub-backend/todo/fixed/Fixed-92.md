# Fixed 92：DC/iDeCo退職所得控除的服務年資，用Java `Period.getYears()`直接捨去零頭月數，日本稅法規定應該要進位（原question-11）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`（第163-165行：`applyWorkingTaxes()`方法內，每年提繳DC時更新`context.setDcContributionYears(...)`；第172-175行：同方法內，更新`context.setIdecoContributionYears(...)`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java`（第64-65行：`initializeDefinedContributionContext()`方法內，初始化DC年資；第137-138行：同方法內，初始化iDeCo年資）

## 現狀
以上4個地方，全部都是用同一種寫法計算DC/iDeCo的服務年資（勤続年數）：

```java
Period.between(enrollmentDate, someDate).getYears()
```

`Period.getYears()`只會回傳兩個日期之間差了「幾個完整的年」，中間不滿一年的零頭月數（跟天數）會被直接捨去、不計算在內。例如加入日期到現在剛好是10年3個月，這個寫法只會算出10年。

## 原因
經查證確認，日本國稅庁對退職所得控除的服務年資（勤続年數）計算，明確規定「1年未満の端数は1年に切り上げます」——不滿一年的零頭月數，規則上要往上進位成一整年，而不是像現在這樣直接捨去。目前這4個地方都用`getYears()`直接捨去零頭，方向剛好相反，會低估服務年資，也就低估了退職所得控除的免稅額度，讓模擬算出來的退職所得稅比實際上該繳的更多。

## 建議修正
這4個地方都需要改成「只要有零頭月數（或零頭天數）就進位成整年」的計算方式，例如同時取`Period.between(...)`的`getYears()`跟`getMonths()`（以及`getDays()`），只要月數或天數大於0就把年數加1。由於4個地方是同一套進位邏輯，建議抽成一個共用的靜態方法讓這4處都呼叫，避免重複寫4遍、之後又漏改其中一處。放在哪個既有的工具類別、或直接放在`DefinedContributionContextBuilder`裡讓兩邊都能呼叫，這部分屬於實作細節，不影響修正方向本身。

## 實作藍圖 (Implementation Blueprint)
1. **建立共用工具類別**：
   - 於 `ch.lin.finance.hub.backend.api.app.service.simulation.core.utils` 套件下新增 `TaxDateUtils.java`。
   - 實作靜態方法 `public static int calculateTaxServiceYears(LocalDate startDate, LocalDate endDate)`。
   - 邏輯：因 `Period.between` 預設不包含終點（Exclusive），而稅法年資計算必須包含頭尾（Inclusive）。因此需先將 `endDate` 加一天轉換語意，即計算 `Period.between(startDate, endDate.plusDays(1))`。若計算結果的 `getMonths() > 0` 或 `getDays() > 0` 則進位（回傳 `getYears() + 1`），否則回傳 `getYears()`。
2. **替換既有邏輯**：
   - 修改 `DefinedContributionContextBuilder.java` 中的 DC 與 iDeCo 年資初始化邏輯，改為呼叫 `TaxDateUtils.calculateTaxServiceYears()`。
   - 修改 `YearlySimulationPipeline.java` 中的 DC 與 iDeCo 年資累加邏輯，改為呼叫 `TaxDateUtils.calculateTaxServiceYears()`。
3. **單元測試**：
   - 建立 `TaxDateUtilsTest.java` 針對恰好滿年、多一天、多一個月、甚至閏年等邊界條件進行單元測試，確保進位行為符合稅法且與框架解耦。

---

## 給 Claude 的 Review 報告

Claude 您好，我是 Gemini。在實作 Fixed-92 關於「DC/iDeCo 勤続年數（服務年資）進位問題」時，我引入了 `TaxDateUtils.java` 統一處理稅法的年資進位，並特別處理了 `Period.between` 的 Half-Open 邊界條件。請您在審查時特別留意以下細節：

1. **為何使用 `endDate.plusDays(1)`：**
   Java 的 `java.time.Period.between(start, end)` 預設是半開區間（Half-Open, `[start, end)`），也就是**不包含 `endDate` 這一天**。但根據日本稅法，退職所得控除的服務年資是「頭尾包含（Inclusive）」的。如果我們單純拿 `Period.between` 計算，當遇到「剛好跨整數年（如 12/31 加入，算到 12/31）」或是「當天剛好是加入週年紀念日」等極端對齊的情境時，會精準算出 `0 個月 0 天` 而不觸發進位，導致使用者平白無故被少算一年免稅額。
2. **實作邏輯：**
   透過先將 `endDate` 手動加一天（`plusDays(1)`），我們將語意強轉為 Inclusive 區間，算出正確的日曆落差後，只要 `getMonths() > 0 || getDays() > 0` 就無條件將 `years` 進行 `++` 遞增，以完美符合國稅庁「1年未満の端数は1年に切り上げます」的規定。
3. **架構位置：**
   `TaxDateUtils` 建立在 `ch.lin.finance.hub.backend.api.app.service.simulation.core.utils` 中，以貼近目前會呼叫它的 `YearlySimulationPipeline` 與 `DefinedContributionContextBuilder`，避免不當的跨層依賴。單元測試 (`TaxDateUtilsTest.java`) 已涵蓋上述所有邊界條件。

還請您確認此修復邏輯與架構是否符合專案規範！
