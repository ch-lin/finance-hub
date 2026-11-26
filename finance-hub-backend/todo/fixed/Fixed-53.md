# Fixed-53：`MortgageCalculator` 中因 `Long.MAX_VALUE` 疊加導致的 Integer Overflow (數值溢位) 與 Optimizer 誤殺最佳解

## 相關檔案與 Package 對照表 (Files Involved)

| Class / 檔案 | 專案 | 檔案路徑 |
|---|---|---|
| `MortgageCalculator.java` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java` |

## 稅金計算驗證 (回答您關於 Fixed-44 的提問)
在此之前，先向您回報您稍早提到的「原本提前還款是在稅金之後，現在移到前面，稅金是不是錯了？」的問題：

**審查結果：目前的架構是完全正確的！**
- **全額結清時（不再有減稅）**：以前的「保母機制」因為在計算稅金（`taxEngine.calculate`）**之後**才把貸款結清，這導致了「明明年底把房貸還完了，卻還是拿到了一整年的住宅ローン控除（減稅）」的**稅金超額退稅 Bug**。現在我們把還款移到了最前面，所以一旦結清，`balance` 歸零，當年度12月的 `mortgageTaxDeductionThisYear` 就不會增加，正確反映「已結清無減稅」的真實情況。
- **沒還款時（正常減稅）**：如果沒有提前還款（`earlyPrincipal = 0`），系統依然會正常扣本息，並在 12 月結算時只要 `balance > 0`，就會正確把 `0.7%` 的餘額算入減稅額，交給後續的 `TaxEngine` 折抵。

---

## 現況說明與問題原因 (Current State & Cause)

針對 Fixed-44 「全額提前還款 (Feature A 化)」的實作，我們讓 Grid Search 透過 `ChromosomeMapper` 傳遞 `Long.MAX_VALUE` 的金額到 `MortgageCalculator`，藉此觸發防呆機制 (`if (earlyPrincipal > balance) earlyPrincipal = balance;`) 來達到完美的全額結清。

然而，這裡隱藏著一個極度危險的邊際效應（Integer Overflow）。

### 災難情境模擬 (Scenario)
**前情提要：**
假設經過精算，**「在 2035 年全額結清房貸」其實是使用者的完美最佳解**（能為使用者省下最多利息、讓 80 歲時的淨資產極大化）。

1. **使用者操作：** 使用者在 Sandbox 中，剛好手動設定了一筆「2035 年提前還款 5,000 元」。接著他點擊了「最佳化 (Grid Search)」。
2. **基因生成：** Optimizer 開始窮舉每一年。當它輪到評估「2035 年全額結清」這個基因時，會將 `Long.MAX_VALUE` 傳入。
3. **發生溢位：** 系統在執行疊加邏輯時：
   ```java
   earlyPrincipal += er.getAmount(); // Long.MAX_VALUE + 5000
   ```
   發生**數值溢位 (Integer Overflow)**，`earlyPrincipal` 瞬間變成一個「**極大的負數**」 (約 `-9223372036854770809`)！
4. **防呆鎖失效：**
   原先設計用來截斷超額還款的安全鎖：
   ```java
   if (earlyPrincipal > balance) { // 負數 > 正數餘額？ -> false
       earlyPrincipal = balance;
   }
   ```
   判定為 `false`，於是負數的 `earlyPrincipal` 順利闖關。
5. **餘額暴增：**
   在執行扣款時：
   ```java
   balance -= earlyPrincipal; // balance - (-9223372036854770809)
   ```
   使用者的房貸餘額瞬間暴增到**數京元**！
6. **分數評估爆炸：** 
   因為房貸餘額變成數京元，這輩子的淨資產 (`trueNetWorth`) 也變成了負數京元。
7. **無聲的錯誤 (Silent Failure)：** 
   Optimizer 看到「2035 年」的分數居然是負的，就會**理所當然地把它當作劣質基因給淘汰掉**！最後，系統推薦了 2036 年（次佳解）給使用者。
   系統不會當機、沒有任何 Error Log，使用者完全不知道**真正的最佳解 (2035年) 已經被系統因為算術 Bug 給誤殺了**。這讓 Optimizer 的結果失去了公信力。

## 建議方向 (Suggested Fix)
**防禦溢位**：在疊加 `earlyPrincipal += er.getAmount()` 之前，必須判斷如果已經是 `Long.MAX_VALUE` 就不再疊加，或者使用 `Math.addExact()` 並 Catch Exception，確保數值不會因為相加而變成負數。

---

## 撤回聲明 (Retraction Report - 2026-09-08)

**狀態：此報告之情境無效，建議廢棄 (Invalid Scenario / Pending Deletion)**

我（Gemini）必須承認這份報告的基礎情境推演存在嚴重的瑕疵，並對系統的整體架構缺乏全面性的理解。

**我的錯誤判斷基準與真相：**
1. **錯誤的 UI 流程幻想：** 我主觀假設了「Sandbox 與最佳化引擎是在同一個頁面上，且共用狀態」，認為使用者可以在 Sandbox 輸入 `5000` 元後，直接帶著這個狀態去跑 Grid Search。
2. **架構真相：** 您指出的非常正確，Sandbox 與最佳化引擎是**完全獨立的兩個頁面**，且 Sandbox 的手動設定**不會存入資料庫**。
3. **推演崩潰：** 這代表當使用者在最佳化頁面按下「開始最佳化」時，送出的 `GlobalOptimizationCommand` 根本**不可能**帶有 Sandbox 裡面的手動提前還款紀錄。
4. **不可能發生溢位：** 既然 Optimizer 永遠不會收到使用者的手動還款，那麼 `ChromosomeMapper` 在進行轉換時，就永遠只會單純地塞入一個 `Long.MAX_VALUE`，絕對不會發生 `Long.MAX_VALUE + 5000` 疊加造成的 Integer Overflow 溢位崩潰。

**結論：**
這個「Optimizer 誤殺最佳解」的情境，完全建立在我對前端架構與資料流向的錯誤想像上。這不是系統的 Bug，而是我 Source Code Review 的失職。因此，我建議將此 `Fixed-53` 廢棄，不需為此進行不必要的程式碼改動。
