# GlobalOptimizerServiceTest 修復計畫 (Fixed 89-01 - 方向B 延伸)

根據您的選擇，我們放棄綁定實作細節的 `mockConstruction` (方向A)，改採**黑箱測試**的風格，讓測試依然只觀察 `GlobalOptimizerService` 依賴物件的互動。

不過，與其刻意設計讓兩種引擎跑出「不一樣的最佳解」（這非常脆弱，因為好的優化引擎本來就應該收斂到一樣的最佳解），我們採取一個更穩定、更符合方向 B 精神的做法：**驗證它們的「行為特徵（呼叫次數）」**。

## 為什麼用呼叫次數可以完美區分？
1. **Grid Search**：特性是「窮舉」。在低維度測試中，只有 NISA 解鎖（31 種可能）。所以只要系統真的選了 Grid Search，它對 `fitnessEvaluator.evaluate()` 的呼叫次數一定會是**完美的 31 次**。
2. **遺傳演算法 (GA)**：特性是「世代繁衍」。它會產生初始人口（例如 50），並繁衍數十到數百代。所以只要系統真的選了 GA，呼叫次數絕對會是**好幾千次**。

## Proposed Changes

### [GlobalOptimizerServiceTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java)

#### [MODIFY] 
- 修改 `testOptimize_RunsGridSearch_WhenLowDimensional`：
  - 維持原本的 mock。
  - 在 `optimizerService.optimize()` 執行後，追加斷言：`Mockito.verify(fitnessEvaluator, Mockito.times(31)).evaluate(any());`
  - 這個斷言保證了系統絕對是用窮舉法跑完了 31 種組合。
  
- 修改 `testOptimize_RunsGA_WhenHighDimensional`：
  - 在 `optimizerService.optimize()` 執行後，追加斷言：`Mockito.verify(fitnessEvaluator, Mockito.atLeast(1000)).evaluate(any());`
  - （或至少 `atLeast(100)`，取決於 GA 的初始設定，但絕對遠大於 Grid Search 如果被誤用的情況下窮舉的次數）。
  - *註：如果高維度的窮舉次數是 `31 * 31 * 16 * 16 > 240000` 次，若系統誤選 Grid Search，呼叫次數會飆到 24 萬次或觸發防護機制而提早報錯。所以可以結合 `atMost(10000)` 來驗證它真的是 GA，而不是失控的 Grid Search。*

## User Review Required

這是在維持黑箱測試風格下，最穩固的斷言方式。不需要像原版方向B那樣苦思如何讓結果不同，而是利用引擎本質的運算行為來做驗證。請問您同意這個修改方向嗎？同意的話我將為您執行程式碼修改。
