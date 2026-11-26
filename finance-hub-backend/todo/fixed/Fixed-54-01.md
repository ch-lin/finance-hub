# Fixed-54-01：Fixed-54 具體化——`GlobalOptimizerMapper` 的 `Long.MAX_VALUE` 洩漏詳細追蹤與修正方向

## 驗證結論 (Verification Verdict)

Fixed-54 描述的根因是真實存在、目前仍未修復的：`GlobalOptimizerMapper.toResponse()` 在算出乾淨的 `displayYear` 之後，把同一個未過濾的原始 `mortgageEarlyRepayments` map 整包透過 MapStruct 產生的 `mapMortgageEarlyRepayments` 方法映射進回應 DTO，導致 `Long.MAX_VALUE` sentinel 原封不動出現在 API response 裡。本文把 Fixed-54 原文的抽象敘述換成目前實際的程式碼、逐行追蹤，並指出一個 Fixed-54 原文沒有考慮到、會讓「照字面直接濾除」變成新 bug 的關鍵事實，最後給出一個不會有這個副作用的具體修正方向。

## 相關檔案 (Files Involved)

| Class / 檔案 | 檔案路徑 |
|---|---|
| `GlobalOptimizerMapper.java` | `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java` |
| `GlobalOptimizerResponse.java` | `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java` |
| `SimulationOverrides.java` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java` |
| `global-optimizer-card.tsx` | `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx` |
| `simulation-override-panel.tsx` | `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx` |

## 現況說明：目前的實際程式碼

Fixed-54 引用的「第 69 行直接映射了整個 map」，目前完整的 `toResponse()`（`GlobalOptimizerMapper.java:48-74`）如下：

```java
default GlobalOptimizerResponse toResponse(SimulationOverrides overrides) {
    if (overrides == null) {
        return null;
    }
    Integer displayYear = null;
    if (overrides.mortgageEarlyRepayments() != null) {
        displayYear = overrides.mortgageEarlyRepayments().values().stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(l -> l.stream())
                .filter(java.util.Objects::nonNull)
                .filter(o -> o.amount() != null && o.amount() == Long.MAX_VALUE)
                .map(o -> o.year())
                .findFirst()
                .orElse(null);
    }
    return new GlobalOptimizerResponse(
            overrides.testNisaMonthlyContribution(),
            displayYear,
            overrides.targetRetirementAgeOverride(),
            overrides.pensionStartAgeOverride(),
            overrides.idecoStartAgeOverride(),
            mapMortgageEarlyRepayments(overrides.mortgageEarlyRepayments()),   // 洩漏點，目前是第 69 行
            overrides.fundOverrides() != null
                    ? overrides.fundOverrides().stream().map(this::toFundOverrideResponse).toList()
                    : null,
            mapMortgageRateScenarios(overrides.mortgageRateScenarios()));
}
```

`mapMortgageEarlyRepayments`（`GlobalOptimizerMapper.java:84-85`）本身沒有方法主體：
```java
java.util.Map<Long, java.util.List<GlobalOptimizerResponse.MortgageEarlyRepaymentResponse>> mapMortgageEarlyRepayments(
        java.util.Map<Long, java.util.List<SimulationOverrides.MortgageEarlyRepaymentOverride>> value);
```
它是 MapStruct 在編譯期自動產生實作的抽象方法（宣告在 `@Mapper(componentModel = "spring", uses = { AssetSimulationMapper.class })` 這個 interface 底下），不是手寫程式碼。這一點在挑選修正手法時很重要，見下一節。

## 為什麼「照字面直接在這裡加一個過濾」不是完整答案

`toResponse()` 是這個 `@Mapper` interface 裡唯一一個手寫的 `default` 方法；它呼叫的 `mapMortgageEarlyRepayments(...)` 是**呼叫另一個 MapStruct 生成的方法**，不是「MapStruct 內部驅動的映射鏈的一部分」。這代表：

- **不能用 MapStruct 的 `@BeforeMapping`／`@AfterMapping` 掛在 `mapMortgageEarlyRepayments` 上做過濾**——這兩個 annotation 只對 MapStruct 自己驅動的映射方法有效，而 `toResponse()` 呼叫 `mapMortgageEarlyRepayments(...)` 這一步，是手寫 `default` 方法裡的一般 Java 方法呼叫，MapStruct 不會在這裡插入任何攔截點。
- 唯一可行的做法，是在 `toResponse()` 內部、呼叫 `mapMortgageEarlyRepayments(...)` **之前**，用一個手寫的 helper method 把要傳入的參數本身先處理過。這個方向本身沒問題，但接下來這件事，是 Fixed-54 原文沒有考慮到、會讓「直接把 sentinel 濾除掉」整個變成新 bug 的關鍵事實：

**`mortgageEarlyRepayments` 這個 map 不是只有「顯示」用途，它同時是使用者點擊「套用最佳化結果」之後，會被原樣寫回前端 Sandbox 覆寫狀態、並在下一次執行模擬時真正送回後端、被 `MortgageCalculator` 讀取來決定「這筆房貸要不要清償、清償多少」的真實輸入資料。** 已確認：`SimulationOverrides.mortgageEarlyRepayments`（`SimulationOverrides.java:40`）是這個 record 上真實存在的欄位；`MortgageCalculator` 對 `earlyPrincipal > balance` 的情況本來就會把清償金額 clamp 到剩餘餘額。也就是說，把 `year=2035, amount=Long.MAX_VALUE` 這筆資料原封不動送回去，目前是唯一能讓「這筆房貸在 2035 年全額清償」這個 Grid Search 建議真正生效的方式。

如果直接照字面，在 `toResponse()` 回傳前把 `mortgageEarlyRepayments` 裡 `amount == Long.MAX_VALUE` 的項目整個濾除掉，會產生一個更隱蔽的新問題：使用者之後點「套用最佳化結果」，把這份已經被濾除、不再含有該筆全額清償資料的 `mortgageEarlyRepayments` 寫進 Sandbox，再執行模擬——**這筆房貸最終完全不會被清償，而且沒有任何錯誤訊息或視覺線索告訴使用者哪裡出了問題**，比現在「數字很醜但至少功能正確」的現狀更糟。

## 建議方向 (Suggested Fix)

修正必須限定在「顯示層」，不能碰觸會被送回去套用的實際資料本身：

**1. 後端 `GlobalOptimizerMapper.toResponse()` 維持現狀，不要修改。** `displayYear` 的計算邏輯（第 52-61 行）與 `mapMortgageEarlyRepayments(overrides.mortgageEarlyRepayments())` 的呼叫（第 69 行）都不需要更動——這個 map 本來就需要原封不動送到前端，才能讓「套用最佳化結果」正確生效。

**2. 前端顯示這份 `mortgageEarlyRepayments` 資料的兩個地方，都需要對 sentinel 值做特殊處理，讓使用者永遠看不到裸數字：**

- `global-optimizer-card.tsx` 的 `OptimizerResultPanel` 元件裡，`validMortgageRepayments`（目前邏輯：`result.mortgageEarlyRepayments ? Object.entries(result.mortgageEarlyRepayments).filter(([_, events]) => events.length > 0) : []`，第 70-72 行）在組出這份「要逐筆列出的清單」時，要先把每筆房貸底下 `amount` 大於等於 `Number.MAX_SAFE_INTEGER` 的事件也濾除掉——這筆全額清償建議已經有專屬的「Mortgage Full Repayment (Year X)」卡片顯示 `displayYear`（第 103-111 行），不需要在下方逐筆清單（第 114-140 行）裡以裸數字重複顯示一次。**只能改「畫面要列出哪些項目」這份清單，不要改「套用時實際讀取、寫入 Sandbox 狀態」的資料來源**——兩者目前讀的是同一個 `result.mortgageEarlyRepayments`，過濾必須只發生在渲染這一層，否則會導致上一節說明的「套用後全額清償悄悄消失」問題。
- 使用者「套用最佳化結果」之後，`simulation-override-panel.tsx` 把 `mortgageEarlyRepayments` 展開成一筆一筆可編輯欄位的地方（`flatRepayments`，第 179-191 行；金額輸入框渲染於第 437-446 行的 `<Input type="number">`），同樣需要對 `amount` 大於等於 `Number.MAX_SAFE_INTEGER` 的項目特殊處理：不要渲染成可編輯的金額輸入框讓使用者看到、甚至能改動這個天文數字，改成一個唯讀的「Full Payoff」標籤或類似提示；年份欄位與刪除按鈕維持原樣可用，只鎖定金額這一格。

**3. 判斷門檻統一使用 `amount >= Number.MAX_SAFE_INTEGER`（即 `9007199254740991`），不要用 `amount === Long.MAX_VALUE` 或字面數字 `9223372036854775807` 做精確比較**——`Long.MAX_VALUE` 序列化成 JSON 數字送到前端、被 JS `Number` 解析之後精度已經丟失，不會精確等於原始值；用「遠大於任何真實房貸餘額」的門檻判斷才是安全、對浮點捨入不敏感的寫法。

## 範圍邊界（不要做的事）

- 不要修改 `ChromosomeMapper.toOverrides()` 橋接 Grid Search 結果的邏輯，也不要修改 `mortgageEarlyRepayments` 這個欄位在 `SimulationOverrides`／`MortgageCalculator` 端「`Long.MAX_VALUE` 代表全額清償」的既有語意——這是既有、且運作正確的機制，本次只處理「怎麼讓使用者看不到裸數字」，不是「怎麼避免產生這個 sentinel 值」。
- 不要在後端加入任何會改變 `mortgageEarlyRepayments` 回傳內容（濾除、改值、換算）的程式碼，理由見上方「為什麼...不是完整答案」一節。
- 不要為此新增額外的 API 欄位（例如額外回傳一個「這筆是不是 sentinel」的 boolean 旗標）——前端用金額大小門檻判斷即可，不需要後端額外提供資訊，避免不必要的 API 契約變動。
