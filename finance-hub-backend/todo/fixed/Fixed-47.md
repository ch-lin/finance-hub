# Fixed-47：全局最佳化器（Grid Search）算出的建議房貸提前還款年度，後端算完後被前端整條資料流無聲丟棄，使用者完全看不到這個建議

## 現況說明 (Current State)

後端 Grid Search 演算法會針對「單一房貸、無複雜多房貸情境」算出一個建議的提前還款年度，並把它一路往上傳遞到 API 回應：

- [Chromosome.java:55](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java)：`private Integer gridSingleMortgageRepaymentYear;`
- [GridSearchEngine.java:120-133](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)：計算並設定這個值。
- [ChromosomeMapper.java:59-66](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java)：只在 `!hasComplexMortgages` 時，把它映射成 `SimulationOverrides.mortgageEarlyRepaymentYear`。
- [SimulationOverrides.java:37](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java)：欄位標註 `@Deprecated`，但依然被實際傳遞使用。
- [GlobalOptimizerServiceImpl.java:111](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)：原樣往下傳遞。
- [GlobalOptimizerResponse.java:31](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java)：作為 API 回應 DTO 的欄位。
- [GlobalOptimizerMapper.java:42](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java)：MapStruct 自動映射進最終回應。
- [GlobalOptimizerController.java:57-67](../../hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/GlobalOptimizerController.java)：`POST /simulation/optimizer/{targetAge}` 實際把這個欄位回傳給前端。

但前端完全沒有接住這個欄位：

- [use-global-optimizer.ts:19-27](../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts)：TypeScript 的 `GlobalOptimizerResponse` interface 只宣告了 `mortgageEarlyRepayments`（複數、以房貸 ID 為 key 的 map，第 24 行），完全沒有對應 `mortgageEarlyRepaymentYear` 的屬性。
- [global-optimizer-card.tsx:64-136](../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx)（`OptimizerResultPanel`）從未讀取或顯示這個欄位。
- [page.tsx:116-129](../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/page.tsx)（`onApplyResult`）套用最佳化結果到模擬沙盒狀態時，也從未把這個欄位寫進去。

## 問題原因 (Cause)

後端與前端對「單一房貸最佳提前還款年度」這個資料，各自維護了一套不同形狀的資料模型：後端用單一 `mortgageEarlyRepaymentYear`（純量，且標註 `@Deprecated` 但仍在用），前端的型別定義只認得 `mortgageEarlyRepayments`（複數 map）。這造成一個中間資料流斷點——後端算出來的建議值進了 HTTP 回應 body，但前端的型別/解析/渲染邏輯完全不認識這個欄位，等同於資料到了前端就直接消失。

實際影響：對於「單一房貸、非複雜情境」的使用者（很可能是最常見的使用情境），Grid Search 演算法辛苦算出來的「建議在第幾年提前還款房貸」這個核心最佳化建議，使用者在 UI 上完全看不到、也無法一鍵套用，等於這個功能對這群使用者是無效的。

另外附帶發現一個相關的次要問題：[GlobalOptimizerServiceImpl.java:150-157](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java) 在 Grid Search 情境下，會預先把 `mortgageEarlyRepayments` map 用「每筆房貸對應一個空陣列」的方式初始化，但 Grid Search 本身從不寫入這個 map（它只寫 `gridSingleMortgageRepaymentYear`），所以這個 map 最終會是「有 key、但 value 是空陣列」的狀態。前端因為 map 本身非空（有 key），會誤判「有提前還款策略資料」而渲染出一張策略卡片，但卡片內容是空的——使用者看到一張空白卡片，同時真正的建議年度資料卻藏在完全沒被讀取的 `mortgageEarlyRepaymentYear` 欄位裡。

## 建議修正方向 (Suggested Fix)

1. 前端 `GlobalOptimizerResponse` TypeScript interface 補上 `mortgageEarlyRepaymentYear?: number` 欄位，`OptimizerResultPanel` 與 `onApplyResult` 對應補上讀取/顯示/套用邏輯（單一房貸情境下顯示建議年度、套用時寫入沙盒狀態）。
2. 或者，統一資料模型：後端在 Grid Search（單一房貸）情境下，也把結果寫進 `mortgageEarlyRepayments`（用該筆房貸的 ID 當 key，值為 `[gridSingleMortgageRepaymentYear]` 這樣的陣列），改成跟複雜多房貸情境（Genetic Algorithm）一致的單一資料形狀，前端只需要維護一套解析邏輯。
3. 無論採用哪個方向，都應該修正 `GlobalOptimizerServiceImpl.java:150-157` 預先塞入空陣列的行為，避免前端渲染出「有資料但內容空白」的卡片。

## 最小範例程式碼 (Sample Code)

```typescript
// use-global-optimizer.ts：補上遺漏的欄位定義與套用邏輯
interface GlobalOptimizerResponse {
  mortgageEarlyRepayments: Record<string, number[]>;
  mortgageEarlyRepaymentYear?: number; // 新增：Grid Search（單一房貸）情境下的建議年度
  // ...其他既有欄位
}
```

```typescript
// page.tsx：onApplyResult 補上套用邏輯
if (result.mortgageEarlyRepaymentYear != null) {
  // 新增：單一房貸情境下，把建議年度套用進沙盒覆寫狀態
  updateSandboxOverrides({ mortgageEarlyRepaymentYear: result.mortgageEarlyRepaymentYear });
}
```

## 修正報告 (Resolution Report)

我們已經完成了純前端的資料對接修復，確保 Grid Search 算出的單一房貸提前還款建議不再被無聲丟棄，而且也不會產生因為後端資料結構導致的空白卡片。

### 1. 補齊介面與對接欄位
在 `use-global-optimizer.ts` 裡的 `GlobalOptimizerResponse` 介面補上了 `mortgageEarlyRepaymentYear?: number`。同時，在 `page.tsx` 的 `onApplyResult` 處理函式中，當套用最佳化結果時，也會一併將這個建議年度寫入前端的沙盒覆寫狀態 (`updateSandboxOverrides`)。

### 2. 修復空白卡片與新增專屬顯示
在 `global-optimizer-card.tsx` 中我們進行了兩項關鍵修改：
- **過濾空陣列防呆：** 當後端為了基因演算法預先塞入「有 Key 但 Value 是空陣列」的資料結構時，我們在前端渲染前就將其過濾（`filter(([_, events]) => events.length > 0)`），避免錯誤渲染出內容空白的提前還款卡片。
- **渲染專屬卡片：** 當 `mortgageEarlyRepaymentYear` 有值時，我們在原本的 2 欄位 Grid 中動態增加一欄（變成 `md:grid-cols-3`），並顯示專屬的 **「Mortgage Full Repayment (Year X)」** 卡片。

這個修正完全不動到後端的演算法邏輯，因此不論未來 Fixed 44 最終決定如何重構（或棄用） Grid Search，這項純前端的修復都沒有任何副作用。
