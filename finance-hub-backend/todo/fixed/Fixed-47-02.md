# Fixed-47-02：Fixed-47 修正報告驗證結果——修正不完整，`Long.MAX_VALUE` sentinel 值透過 `mortgageEarlyRepayments` 洩漏並顯示/持久化到前端

## 驗證結論 (Verification Verdict)

**Fixed-47 的修正報告所描述的內容本身是真實已實作的**（`mortgageEarlyRepaymentYear` 欄位已補齊、空陣列卡片已過濾、專屬顯示卡片已新增），但**修正不完整**：Fixed-47 的修正報告完成於 Fixed-44（Grid Search 橋接 Feature A）的架構尚未落地或尚未考慮到的時間點，沒有預見到 Fixed-44 之後，Grid Search 的單一房貸建議會透過 `ChromosomeMapper` 把 `Long.MAX_VALUE` sentinel 值寫入**同一個** `mortgageEarlyRepayments` map（而不是留空陣列）。Fixed-47 修正報告裡加的「過濾空陣列」防呆（`filter(([_, events]) => events.length > 0)`）只能擋掉「有 key 但 value 空陣列」這種情況，**擋不住「有 key 且 value 非空，但金額是 `Long.MAX_VALUE`」的新情況**，因此問題以另一種形式重新出現，而且比原本的「空白卡片」更嚴重：使用者會直接看到、甚至可以編輯一個天文數字金額。

## 相關檔案 (Files Involved)

| Class / 檔案 | 檔案路徑 |
|---|---|
| `GlobalOptimizerMapper.java` | `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java` |
| `ChromosomeMapper.java` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java` |
| `global-optimizer-card.tsx` | `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx` |
| `page.tsx` | `hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx` |
| `simulation-override-panel.tsx` | `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx` |

## 現況說明與問題原因 (Current State & Cause)

### 後端：`Long.MAX_VALUE` sentinel 進入 API response 的兩條路徑都沒有被過濾

Fixed-44 落地後，Grid Search 對單一房貸的建議年度不再只寫入 `mortgageEarlyRepaymentYear`，而是也透過 `ChromosomeMapper.java:59-70` 把它「橋接」進 `mortgageEarlyRepayments` map，寫入一筆 `new MortgageEarlyRepaymentOverride(legacyYear, Long.MAX_VALUE)`。

`GlobalOptimizerMapper.toResponse()`（`GlobalOptimizerMapper.java`）確實正確地從這個 map 中提取乾淨的 `displayYear` 給 `mortgageEarlyRepaymentYear` 欄位，但緊接著又把**同一個未過濾的原始 map**透過 `mapMortgageEarlyRepayments(overrides.mortgageEarlyRepayments())` 整個塞進 response 的 `mortgageEarlyRepayments` 欄位：

```java
return new GlobalOptimizerResponse(
        overrides.testNisaMonthlyContribution(),
        displayYear,                                              // 乾淨的年份，OK
        overrides.targetRetirementAgeOverride(),
        overrides.pensionStartAgeOverride(),
        overrides.idecoStartAgeOverride(),
        mapMortgageEarlyRepayments(overrides.mortgageEarlyRepayments()), // 原始 map 直接洩漏，含 Long.MAX_VALUE
        ...
```

結果：對「單一房貸、Grid Search 建議全額結清」這個最常見情境，API response 會同時包含：
```json
{
  "mortgageEarlyRepaymentYear": 2035,
  "mortgageEarlyRepayments": { "1": [{ "year": 2035, "amount": 9223372036854775807 }] }
}
```

### 前端：Fixed-47 的空陣列過濾擋不住這個新情況，天文數字被直接渲染

`global-optimizer-card.tsx:70-71` 的防呆是：
```typescript
const validMortgageRepayments = result.mortgageEarlyRepayments
  ? Object.entries(result.mortgageEarlyRepayments).filter(([_, events]) => events.length > 0)
  : [];
```
這只濾掉「value 是空陣列」的 key，對「value 非空但金額是 sentinel」完全無效。於是 `validMortgageRepayments.length > 0`，`global-optimizer-card.tsx:114-140` 的「Mortgage Early Repayment Strategy」卡片會被渲染出來，並在第 133 行直接顯示：
```tsx
<div className="font-bold text-lg">
  ¥{event.amount.toLocaleString()}
</div>
```
即 `¥9,223,372,036,854,776,000`（`Long.MAX_VALUE` 用 JS `Number` 表示已超過 `Number.MAX_SAFE_INTEGER`，會被四捨五入，但仍是一個毫無意義的天文數字）。

更糟的是，這張「天文數字」卡片會跟 Fixed-47 修正報告新增的「Mortgage Full Repayment / Year 2035」專屬卡片**同時顯示**——使用者會看到同一個建議被顯示了兩次，一次正常（Year 2035），一次是災難數字，完全違反 Fixed-44 原始規劃「絕不能讓前端收到 MAX_VALUE 災難數字」的核心承諾。

### 更嚴重：點擊「Apply to Sandbox」後，天文數字會被寫入可編輯的沙盒表單

`page.tsx:124-126`（`onApplyResult`）：
```typescript
if (result.mortgageEarlyRepayments) {
  next.mortgageEarlyRepayments = result.mortgageEarlyRepayments; // 原始（含 sentinel）map 整包複製進沙盒覆寫狀態
}
```
這個沙盒覆寫狀態接著餵給 `simulation-override-panel.tsx`。該元件的 `flatRepayments`（第 179-191 行）會展開這個 map，並在第 437-446 行渲染成一個**可編輯的數字輸入框**：
```tsx
<Label className="text-[10px] text-muted-foreground">Amount (¥)</Label>
<Input
  type="number"
  step="100000"
  className="h-7 text-xs font-medium text-green-600 dark:text-green-400"
  value={ev.amount || ''}
  onChange={(e) => handleUpdateRepayment(index, 'amount', e.target.value ? Number(e.target.value) : 0)}
/>
```
也就是說，使用者點擊「套用最佳化結果」之後，Sandbox 的「Mortgage Early Repayments」區塊會出現一筆金額欄位顯示天文數字、且可以被直接編輯的紀錄。如果使用者沒注意到就直接調整年份或其他欄位再送出模擬，這筆帶著 `Long.MAX_VALUE`（或其 JS 浮點數近似值）的紀錄就會被當成一筆**真正的手動覆寫**送回後端，混入 `mortgageEarlyRepayments`（而非透過 Grid Search 專用的橋接欄位）。雖然 `MortgageCalculator` 有防呆鎖會把超額還款截斷到剩餘餘額（详見 Fixed-44 相關修正），數學上不會爆炸，但 UI 呈現與使用者心智模型已經被嚴重誤導。

## 新發現：`mortgageEarlyRepaymentYear` 本身在後端是死欄位，且若直接照後端整個濾除的方向修，會讓 Apply to Sandbox 悄悄失效

已直接確認 `SimulationOverrides.java`（`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java:35-42`）目前是一個只有 7 個欄位的 record：`testNisaMonthlyContribution`、`targetRetirementAgeOverride`、`pensionStartAgeOverride`、`idecoStartAgeOverride`、`mortgageEarlyRepayments`、`fundOverrides`、`mortgageRateScenarios`——**完全沒有 `mortgageEarlyRepaymentYear` 這個欄位**。也已用全 repo grep 確認，`mortgageEarlyRepaymentYear` 這個名字在後端唯二出現的地方，是 `GlobalOptimizerResponse.java`（回應 DTO 的顯示欄位）與已確認為死碼的 `SimulationOptimizerServiceOldImpl.java`（見 `todo/planned/FINDING-44-...md`），從未出現在任何模擬「輸入」端的 record 上。

`GlobalOptimizerMapper.toResponse()`（`finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java:52-61`）算出的 `displayYear`，純粹是掃描 `overrides.mortgageEarlyRepayments()` 找 `amount() == Long.MAX_VALUE` 項目取出的「顯示用」衍生值，從來就不對應任何可以被送回模擬引擎的輸入欄位。但前端把它當成一個真正可套用的覆寫值在使用：

- `hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-simulation-data.ts:16`：前端送給後端「執行模擬」用的 `SimulationOverrides` TypeScript 介面，把它宣告成 `mortgageEarlyRepaymentYear?: number | null;`，跟 `mortgageEarlyRepayments` 等其他真正有效的欄位並列。
- `hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx:127-128`（`onApplyResult`）：`if (result.mortgageEarlyRepaymentYear != null) { next.mortgageEarlyRepaymentYear = result.mortgageEarlyRepaymentYear; }`，把這個顯示值原樣寫進沙盒覆寫狀態。
- `use-simulation-data.ts:84`（`fetchSimulation`）：整個 `currentOverrides`（含 `mortgageEarlyRepaymentYear`）透過 `JSON.stringify(currentOverrides)` 送進 `POST /api/finance/simulation/asset-simulation/{age}`。

後端反序列化成 `SimulationOverrides` 時，因為這個 record 沒有對應欄位，且 Spring Boot 預設的 `ObjectMapper` 對未知 JSON 欄位是靜默忽略（全 repo 沒有任何 `FAIL_ON_UNKNOWN_PROPERTIES` 之類的嚴格設定），這個欄位會被直接丟棄，**對後續任何模擬執行完全沒有實際效果**。也就是說，Fixed-47 修正報告新增的「Mortgage Full Repayment (Year X)」卡片與其「Apply to Sandbox」邏輯，單獨這一個欄位從一開始就是死操作。

**這個死欄位目前不會表現出「按了沒用」的明顯症狀，是因為 `onApplyResult`（`page.tsx:124-126`）在同一個函式裡還有另一行，把同一份原始 `result.mortgageEarlyRepayments`（也就是本檔案上方整節在討論、含 `Long.MAX_VALUE` sentinel 的那個 map）整包複製進 `next.mortgageEarlyRepayments`。因為 `mortgageEarlyRepayments` 是 `SimulationOverrides` 上真實存在、`MortgageCalculator` 也真的會正確處理 `Long.MAX_VALUE`（截斷清償至剩餘餘額）的欄位，所以「全額清償在指定年份真正生效」這件事，目前是靠這個會洩漏出天文數字的 `mortgageEarlyRepayments` map 在撐著，不是靠 `mortgageEarlyRepaymentYear`。**

這代表下方「建議方向」不能只是單純把 `mortgageEarlyRepayments` 裡 `amount == Long.MAX_VALUE` 的項目在後端整個濾除——一旦濾除，`result.mortgageEarlyRepayments` 送到前端時全額清償那筆項目就已經不存在，`page.tsx:124-126` 複製進沙盒狀態的 `next.mortgageEarlyRepayments` 也就不會再帶有它，「Apply to Sandbox」對單一房貸全額清償建議會變成**完全沒有效果、且沒有任何錯誤訊息或視覺線索**的空操作，比現在「數字很醜但功能正確」更糟。因此下方「建議方向」改為：後端保留這筆資料不濾除，只在前端顯示層把 sentinel 值隱藏／特殊化，讓 Apply to Sandbox 這條路徑不受影響。

## 建議方向 (Suggested Fix)

**核心原則：後端不要把 `Long.MAX_VALUE` sentinel 從 `mortgageEarlyRepayments` 這個會被送回、且會被用來套用進下一次模擬的欄位中整個濾除**——如上一節所述，它是目前「全額清償」這個建議唯一能正確生效的管道。修正範圍限定在前端顯示層：讓 sentinel 值不要以裸數字出現在畫面或可編輯欄位裡，底層資料原樣保留，`Apply to Sandbox` → 重新執行模擬這條路徑不受影響。`GlobalOptimizerMapper.toResponse()`（`GlobalOptimizerMapper.java:48-74`）維持現狀，不要修改。

**1. `global-optimizer-card.tsx`：optimizer 結果面板不要重複顯示 sentinel 項目**

`OptimizerResultPanel` 裡的 `validMortgageRepayments`（`global-optimizer-card.tsx:70-72`）目前只過濾「空陣列」：
```typescript
const validMortgageRepayments = result.mortgageEarlyRepayments
  ? Object.entries(result.mortgageEarlyRepayments).filter(([_, events]) => events.length > 0)
  : [];
```
改成同時過濾掉 sentinel 事件本身（這種事件已經由既有的第 103-111 行專屬「Mortgage Full Repayment (Year X)」卡片顯示過一次，不該再以裸數字重複出現在第 114-140 行的清單裡）：
```typescript
const isFullPayoffSentinel = (amount: number) => amount >= Number.MAX_SAFE_INTEGER;
const validMortgageRepayments = result.mortgageEarlyRepayments
  ? Object.entries(result.mortgageEarlyRepayments)
      .map(([mortgageId, events]) => [mortgageId, events.filter(e => !isFullPayoffSentinel(e.amount))] as const)
      .filter(([_, events]) => events.length > 0)
  : [];
```
（`Long.MAX_VALUE` 在 JS `Number` 精度下會被四捨五入成一個略大於 `Number.MAX_SAFE_INTEGER` 的浮點數，但一定遠遠超過任何真實房貸餘額，用 `amount >= Number.MAX_SAFE_INTEGER` 判定是安全的。）

**只能改第 70-72 行這個「畫面要逐筆列出哪些項目」的清單，絕對不要去動 `onApplyResult`（`page.tsx:124-126`）實際套用時讀取的來源資料**——它讀的是 `result.mortgageEarlyRepayments` 這個原始、未經上述過濾的 map，過濾只發生在渲染這一層，不能連帶影響套用資料的完整性。

**2. `simulation-override-panel.tsx`：沙盒覆寫面板裡，sentinel 項目不要渲染成可編輯的金額輸入框**

`flatRepayments`（`simulation-override-panel.tsx:179-191`）目前無條件把 `overrides.mortgageEarlyRepayments` 展開成清單，每一筆都會在第 437-446 行渲染成：
```tsx
<div className="space-y-1 flex-1">
  <Label className="text-[10px] text-muted-foreground">Amount (¥)</Label>
  <Input
    type="number"
    step="100000"
    className="h-7 text-xs font-medium text-green-600 dark:text-green-400"
    value={ev.amount || ''}
    onChange={(e) => handleUpdateRepayment(index, 'amount', e.target.value ? Number(e.target.value) : 0)}
  />
</div>
```
在這個區塊前面加一個條件：當 `ev.amount >= Number.MAX_SAFE_INTEGER` 時，不要渲染上述 `<Input type="number">`，改成一個唯讀標籤（例如純文字「Full Payoff」或一個 disabled 的 badge），提示這是「全額清償」而非可任意調整的金額；`year` 欄位（第 429-436 行）與刪除按鈕維持原樣可用，只鎖定金額這一個欄位不讓使用者看到或改到裸數字。

**3. 刪除 `page.tsx:127-128` 這個死程式碼，以及對應的前端型別欄位**

```typescript
if (result.mortgageEarlyRepaymentYear != null) {
  next.mortgageEarlyRepaymentYear = result.mortgageEarlyRepaymentYear;
}
```
已用全 repo grep 確認，`use-simulation-data.ts:16` 上 `SimulationOverrides.mortgageEarlyRepaymentYear` 這個型別欄位，除了這裡的寫入之外沒有任何其他讀取者——純粹寫入一個後端完全沒有對應欄位、送出後會被 Jackson 靜默丟棄的死欄位。應把 `page.tsx:127-128` 這段連同 `use-simulation-data.ts:16` 的型別宣告一併刪除，避免未來的閱讀者誤以為這是一個有效的套用路徑。**注意這跟 `GlobalOptimizerResponse.mortgageEarlyRepaymentYear`（`global-optimizer-card.tsx:86、103、107` 讀取、用於顯示「Mortgage Full Repayment (Year X)」卡片的那個欄位）是兩個不同型別上的同名欄位，後者是合法的顯示用途，不要連帶刪除。** 真正讓「全額清償」生效的，是同一個函式裡緊接著、經過上方第 1 點過濾顯示但保留原始套用資料的 `next.mortgageEarlyRepayments = result.mortgageEarlyRepayments`（`page.tsx:124-126`），不需要新增，只需要不要被破壞。

## 修正實作報告 (Implementation Report)

已依據建議方向完成純前端的防護機制與死碼清理，成功避免 Sentinel (`Long.MAX_VALUE`) 影響使用者體驗，且不破壞原有的套用邏輯。

### 1. 顯示層防護 (過濾與視覺處理)
- **`global-optimizer-card.tsx`**：在 `validMortgageRepayments` 的處理中加入 `amount >= Number.MAX_SAFE_INTEGER` 的檢查。如果事件金額是 Sentinel，則在一般明細清單中予以隱藏，避免與專屬的「Mortgage Full Repayment (Year X)」卡片重複且顯示天文數字。
- **`simulation-override-panel.tsx`**：在 Sandbox 的手動覆寫面板中，針對金額超過 `Number.MAX_SAFE_INTEGER` 的項目，停止渲染 `<Input type="number">`，改為顯示唯讀的 `Full Payoff (AI)` Badge。這樣使用者仍可調整年份與刪除該筆資料，但無法看到或修改底層的天文數字。

### 2. 死碼清理 (Dead Code Removal)
- **`page.tsx`**：移除了 `onApplyResult` 內無用的 `next.mortgageEarlyRepaymentYear = result.mortgageEarlyRepaymentYear` 賦值邏輯。
- **`use-simulation-data.ts`**：自 `SimulationOverrides` 型別宣告中移除了 `mortgageEarlyRepaymentYear?: number | null;`，徹底消除對後端實際上不存在之欄位的依賴。

**狀態**：✅ UI 洩漏問題已徹底修復，且 Grid Search 對單一房貸的「全額清償」套用依然能透過保留了 Sentinel 的 `mortgageEarlyRepayments` map 正常生效。
