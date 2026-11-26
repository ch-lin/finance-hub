# Fixed-54：`GlobalOptimizerMapper` 未濾除 `Long.MAX_VALUE`，導致無效基因 (天文數字) 洩漏至前端

## 相關檔案與 Package 對照表 (Files Involved)

| Class / 檔案 | 專案 | 檔案路徑 |
|---|---|---|
| `GlobalOptimizerMapper.java` | `ch.lin.finance.hub.backend.api.controller.simulation.dto` | `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java` |
| `GlobalOptimizerResponse.java` | `ch.lin.finance.hub.backend.api.controller.simulation.dto` | `hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerResponse.java` |

## 現況說明與問題原因 (Current State & Cause)

根據 Fixed-44 的實作計畫：
> `GlobalOptimizerResponse.mortgageEarlyRepaymentYear` **保留為 Display Only 欄位**，由後端從 map 中提取乾淨年份填入，確保前端 UX 不受影響。`Long.MAX_VALUE` 的金額不會暴露給前端。

在審查 `GlobalOptimizerMapper.java` 時，我發現第 69 行的實作如下：

```java
return new GlobalOptimizerResponse(
        // ... 其他欄位
        displayYear,
        // ...
        mapMortgageEarlyRepayments(overrides.mortgageEarlyRepayments()), // 這裡直接映射了整個 map！
        // ...
);
```

**問題點：**
`ChromosomeMapper.toOverrides()` 在橋接 Grid Search 時，會將 `(year, Long.MAX_VALUE)` 寫入 `mortgageOverrides` map 中。然而，在 `GlobalOptimizerMapper.toResponse()` 將結果回傳給前端時，它雖然成功提取了 `displayYear`（例如 `2035`），但它**同時也直接呼叫了 MapStruct 的 `mapMortgageEarlyRepayments`**，把整個含有 `Long.MAX_VALUE` 的 map 原封不動地塞進了 Response DTO 裡面！

### 災難性後果
1. **API 負載洩漏**：前端收到的 JSON Response 會長這樣：
   ```json
   {
     "mortgageEarlyRepaymentYear": 2035,
     "mortgageEarlyRepayments": {
       "1": [
         {
           "year": 2035,
           "amount": 9223372036854775807
         }
       ]
     }
   }
   ```
2. **前端 UI 爆炸**：如果前端的「套用最佳化結果 (Apply Optimization)」按鈕邏輯，是直接把 `mortgageEarlyRepayments` 的結果倒進 Redux Store 或 Sandbox 狀態中，那麼使用者畫面上的「提前還款金額」輸入框，將會瞬間顯示 `9,223,372,036,854,775,807` 這個極度醜陋的天文數字！這完全違反了 Fixed-44 「絕不能讓前端收到 MAX_VALUE 災難數字」的核心承諾。

## 建議方向 (Suggested Fix)

這純粹是資料傳輸層 (DTO Mapper) 的過濾漏洞。
在 `GlobalOptimizerMapper.toResponse()` 中，應該在呼叫 `mapMortgageEarlyRepayments` 之前，把 map 裡面含有 `Long.MAX_VALUE` 的特殊標記 (Sentinel value) 給濾除掉，或者對於 Grid Search 產生的專屬 overrides 進行特殊攔截，確保只有使用者手動設定的、真正合理的金額才會回傳給前端。
