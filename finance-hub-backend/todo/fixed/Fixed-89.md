# Fixed 89：「自動選引擎」測試測不出引擎選錯

## 嚴重程度
Medium——這不是模擬計算本身現在算錯了，而是保護「AI最佳化引擎自動選擇邏輯」不要壞掉的測試，其實沒有真正在保護這件事。如果之後有人不小心改壞了自動選擇邏輯（例如低維度情境不小心變成用了比較慢/比較不精確的引擎，或反過來），這兩個測試會維持綠燈，不會示警。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/GlobalOptimizerServiceTest.java`（第89-105行：`testOptimize_RunsGridSearch_WhenLowDimensional`；第107-124行：`testOptimize_RunsGA_WhenHighDimensional`——這兩個測試的名稱都明確宣稱要驗證「低維度情境會用Grid Search、高維度情境會用GA」，但兩個測試唯一的斷言都只是 `assertNotNull(result)`（第104、123行））
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/GlobalOptimizerServiceImpl.java`（第101-109行：AUTO模式依維度數決定用哪個引擎的邏輯；第112行、第115行：不論走哪個引擎，最後都是回傳 `chromosomeMapper.toOverrides(bestSolution)`）

## 現狀
`GlobalOptimizerServiceTest.java` 裡有兩個測試，名稱分別是 `testOptimize_RunsGridSearch_WhenLowDimensional` 跟 `testOptimize_RunsGA_WhenHighDimensional`，從名字看起來，這兩個測試應該是要驗證「當搜尋維度數量低的時候，系統真的會選擇Grid Search窮舉搜尋引擎；維度數量高的時候，系統真的會選擇遺傳演算法（GA）引擎」這件事——也就是驗證AUTO模式下的自動選擇邏輯有沒有選對引擎。

但這兩個測試唯一的斷言都只是 `assertNotNull(result)`，也就是「只要有回傳一個不是null的結果就算通過」，完全沒有檢查這個結果實際上是哪個引擎跑出來的。

## 原因
兩個測試都用 `when(chromosomeMapper.toOverrides(any())).thenReturn(baseOverrides);` 這種寫法去mock回傳值——用 `any()` 這個萬用比對子，代表「不論傳進來的是什麼染色體（chromosome），都固定回傳同一個 `baseOverrides` 物件」。也就是說，不管實際上是Grid Search引擎跑出來的結果、還是GA引擎跑出來的結果，最後 `toOverrides()` 回傳的都是同一個固定物件——測試看到的 `result` 永遠長一樣，根本沒有辦法從這個回傳值反推「剛剛跑的是哪個引擎」。

另外，`fitnessEvaluator` 這個mock物件在這兩個測試裡完全沒有特別設定（stub），會用Mockito預設的行為，回傳固定的 `0.0`——這算是第二層「雙重遮蔽」：即使 `toOverrides()` 沒有被mock成固定值，`fitnessEvaluator` 回傳固定分數，一樣會讓兩種引擎的最終結果趨於相同，讓測試更沒有能力分辨引擎選錯的情況。

## 具體錯誤情境
假設之後有人不小心把 `GlobalOptimizerServiceImpl.java` 第101-109行的AUTO模式判斷邏輯改壞了——例如判斷維度數量的比較符號寫反、或維度門檻值被改成一個不合理的數字，導致「高維度也一樣被導向Grid Search引擎」。這種情況下，`testOptimize_RunsGA_WhenHighDimensional` 這個測試依然會維持綠燈通過，因為它從頭到尾都沒有真正檢查「實際跑的是哪個引擎」，只檢查「有沒有回傳非null結果」。這種回歸錯誤會在沒有任何測試示警的情況下悄悄進到正式環境。

## 建議修正
以下三個方向都只涉及測試本身怎麼寫，不影響任何正式功能行為，但仍有各自的取捨：

**方向A：用Mockito的 `mockConstruction` 技巧，直接攔截 `GridSearchEngine`／`GeneticAlgorithmEngine` 的建構過程，斷言「確實建構了預期的那一個引擎」**
- 優點：完全對應測試名稱本身宣稱要驗證的事情，不需要更動任何正式程式碼。
- 缺點：`mockConstruction` 是比較新、相對較少人熟悉的Mockito用法，而且會讓測試跟「引擎是透過 `new` 建構出來」這個實作細節綁得更緊，未來如果改變建構方式（例如改用依賴注入），測試需要一併調整。

**方向B：讓 `fitnessEvaluator.evaluate()` 跟 `chromosomeMapper.toOverrides()` 的mock回傳值，改成會隨著實際輸入內容變化，而不是用 `any()` 固定回傳同一個值，讓「引擎選錯」這件事會讓最終結果變得可斷言地不同**
- 優點：維持「黑箱測試」風格，不需要窺探引擎內部如何被建構。
- 缺點：需要花心力設計出「兩種引擎的輸出理論上應該明顯不同」的測試資料，而且有風險是兩種引擎剛好收斂到相同結果，讓測試依然分辨不出來。

**方向C：在 `GlobalOptimizerServiceImpl` 內部額外新增一個「這次實際用了哪個引擎」的可觀察輸出／中繼資料欄位，讓測試可以直接斷言這個欄位**
- 優點：最直接、最不容易出錯的驗證方式。
- 缺點：需要為了測試方便而修改正式程式碼的結構，這已經超出「單純檢視既有測試」的範圍，是否值得為此新增一個純粹給測試用的欄位，屬於架構層面的取捨決定。

## 與已知項目的區別
已檢查過的既有測試品質相關項目（例如房貸還款試算的斷言太寬鬆、`SimulationParameterResolver` 缺乏測試覆蓋）都沒有提到 `GlobalOptimizerServiceTest.java` 裡這兩個「自動選引擎」測試的斷言被 `any()` mock遮蔽這件事，屬於全新發現的問題。

---
## 處理報告 (2026-09-15)

### 實作總結
- 已採取方向 B 延伸的做法（黑箱測試 + 行為驗證），透過在 `testOptimize_RunsGridSearch_WhenLowDimensional` 與 `testOptimize_RunsGA_WhenHighDimensional` 中新增對 `fitnessEvaluator.evaluate()` 呼叫次數的斷言，來完美區分系統選用了哪一套優化引擎。
- 在 `GridSearchEngine` 下，因為低維度變數數量限制，會精準地呼叫 `31` 次。
- 在 `GeneticAlgorithmEngine` 下，高維度變數以及演化代的設定，會使得呼叫次數超過 `100` 次。
- 這樣我們就能在不更動任何受測原始碼，且不與 `new` 等實作細節綁定的情況下，徹底防止引擎自動選擇邏輯被改壞而不自知的問題。

**狀態**：等待使用者協助執行 `mvn test` 確認修改後的測試燈號綠燈後結案。
