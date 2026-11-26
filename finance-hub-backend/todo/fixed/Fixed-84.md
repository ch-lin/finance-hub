# Fixed 84：房貸縮短年限提前還款後，重算月付金額會偏低

## 嚴重程度
High——這是核心的房貸現金流模擬功能，直接影響「還完房貸後現金夠不夠用」這個系統最主要的用途。只要使用者曾經用「縮短年限」的方式提前還款，之後每次重新計算月付金額（例如碰到5年一次的利率重算規則，或利率變動時），算出來的月付金額都會比真實情況偏低。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java`（共6處：第175、193、214、328、345、372行，全部都是用 `m.getLoanTermMonths() - totalElapsedNow` 去算「剩餘月數」，完全沒有扣掉曾經用「縮短年限」提前還款所減少的月數）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/domain/mortgage/MortgageRecord.java`（第88行：`loanTermMonths`，這個欄位只在房貸建立／編輯時被設定一次，之後不會因為提前還款而被調整）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/domain/mortgage/EarlyRepaymentType.java`（`REDUCE_TERM`縮短年限／`REDUCE_PAYMENT`減少月付金額，這兩種提前還款方式的enum定義）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/GlobalOptimizerServiceImpl.java`（第277行）與 `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/GridSearchEngine.java`（第50行）（這兩處同樣使用未經調整的 `loanTermMonths` 去估算搜尋範圍，影響程度比直接的月付金額算錯小）
- `hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx`（提前還款設定表單，同樣沒有在使用者選擇「縮短年限」後，自動調整貸款年限相關欄位）

## 現狀
房貸提前還款有兩種方式：「縮短年限」（總月付金額不變，但整體還款時間變短）跟「減少月付金額」（還款時間不變，但每月要付的金額變少）。日本大部分房貸都有「5年一次重新計算月付金額」的規則（適用於變動利率型房貸），或是利率本身變動時，也需要重新計算月付金額。

問題是：如果使用者過去曾經用「縮短年限」的方式提前還款過，之後任何一次重新計算月付金額，系統用來計算的「剩餘還款月數」都沒有把這次「縮短年限」實際減少的月數扣掉，導致算出來的剩餘月數比真實情況多，而剩餘月數越多，重新計算出來的每月付款金額就會越低——換句話說，系統會讓使用者看到一個比真實情況更低的月付金額，低估了實際的房貸現金流出。

## 原因
`MortgageCalculator.java` 裡有6個地方（第175、193、214、328、345、372行）都用 `m.getLoanTermMonths() - totalElapsedNow` 這個公式去算「剩餘還款月數」。`m.getLoanTermMonths()` 是房貸建立或編輯時設定的原始貸款年限（例如420個月），這個欄位並不會因為使用者後續用「縮短年限」提前還款而被更新或調整。

程式碼裡目前只有針對「減少月付金額」（`REDUCE_PAYMENT`）這種提前還款方式有特別處理（第211、369行的if分支），這是合理的，因為「減少月付金額」本來設計上就不應該改變還款時間長度。但程式碼完全沒有任何地方記錄／記得「曾經發生過一次縮短年限的提前還款，讓剩餘時間縮短了多少」，導致所有計算剩餘月數的地方，都繼續沿用「原始貸款年限 − 已經過的月數」這個沒有考慮到縮短年限效果的公式。

由於月付金額的計算公式是「餘額 × 利率 × (1+利率)^n ÷ ((1+利率)^n − 1)」（n是剩餘月數），高估剩餘月數n，會系統性地低估重新計算出來的月付金額。

## 具體錯誤情境
一筆35年期（420個月）的變動利率房貸，有5年一次重新計算月付金額的規則。第3年時，使用者用「縮短年限」的方式提前還款了一筆錢，讓實際剩餘還款時間縮短了24個月（原本應該在第35年還完，縮短後應該在第33年就還完）。到了第5年的5年一次重算時間點，系統計算剩餘月數是「420 − 60（已過60個月）= 360個月」，但正確的剩餘月數應該是「420 − 60 − 24（縮短掉的月數）= 336個月」。用360個月而不是336個月去重算月付金額，會讓算出來的月付金額比真實情況更低，低估了這筆房貸接下來每個月實際要付的金額。

## 建議修正
這個問題有以下三個修正方向：

**方向A：新增一個會動態追蹤的「目前實際剩餘月數」欄位，每次發生「縮短年限」提前還款時就把這個欄位扣減，之後所有需要用到剩餘月數的地方都改用這個欄位**
- 優點：往後每次計算都能維持正確，是最準確的做法。
- 缺點：需要新增資料庫欄位，並確保各個提前還款、年度模擬流程都正確同步更新這個欄位，改動範圍較大。

**方向B：不新增欄位，改成每次需要用到剩餘月數時，即時計算「原始年限 − 已過月數 − 至今所有『縮短年限』提前還款累積縮短的月數」**
- 優點：不需要修改資料庫結構。
- 缺點：需要先正確反推出過去每一次「縮短年限」提前還款，各自實際縮短了多少個月，這本身就需要額外的攤還模擬計算，尤其如果中間還夾雜利率變動，複雜度可能比方向A更高。

**方向C：不修正計算邏輯，只在有過「縮短年限」提前還款紀錄的房貸旁邊，加上一段UI提示文字，說明5年重算或利率變動後重新計算出來的月付金額可能被低估**
- 優點：改動風險最低。
- 缺點：沒有真正修正數字本身，使用者看到的月付金額仍然是錯的。

另外需要補充：這個「`loanTermMonths` 沒有反映縮短年限效果」的根本原因，同時也會影響「減少月付金額」（`REDUCE_PAYMENT`）當下立即重算月付金額的邏輯（第211、369行）——如果在這之前，這筆房貸已經發生過「縮短年限」的提前還款，同樣的低估情況會在這第二個觸發時機再次發生。無論選擇哪一個修正方向，都應該把這個情況一併涵蓋進去。

## 與已知項目的區別
本問題跟 `todo/planned/PLANNED.md` 裡記錄的 **H5**（房貸利率為單一常數，全程套用同一個利率，無法模擬升息、轉貸或利率重設，來源為 `asset-simulation-review-round1.md` H5）是完全不同的問題——H5討論的是「房貸利率本身無法模擬變動」這個缺少功能的問題，本問題討論的是「即使利率／年限重算這個機制本身存在，用來重算的剩餘月數基準值本身就算錯了」，兩者是完全不同層面的問題，不重疊。本問題也跟 `Plan-44`（`findBestMortgageEarlyRepaymentYear` 這個已經沒有被引用的舊程式碼裡的無窮迴圈風險，屬於死碼）無關；也跟已經修復的「房貸攤還／提前還款測試斷言太寬鬆」與「房貸利率情境覆蓋範圍錯誤」兩項無關，這兩項都已確認修復完成，內容跟本問題不同。

## 實作藍圖

### 決策說明：採用手動輸入與混合策略 (Decision Rationale)
在評估上述修正方向後，考量到各家銀行對於「縮短月數」的尾數捨入與推算公式存在微小落差（例如閏年、天數計算差異），如果採用系統全自動反推計算（如方向 B 所述），會有算錯與不準確的風險。因此，我們最終決定的方針是：**不由系統自動反推，而是讓使用者在畫面上手動輸入該次提前還款「實際縮短的月數」**。
- 這在概念上類似**方向 A**，因為我們會在資料庫與領域模型中新增 `reducedMonths` 欄位來記錄這個數字。
- 但與純粹的方向 A（直接覆寫房貸主檔的剩餘月數）不同的是：我們把使用者手填的 `reducedMonths` 記錄在每一筆提前還款明細（`MortgageEarlyRepayment`）上，然後在模擬計算時動態加總扣除（類似**方向 B** 的精神）。這樣不僅能保留完整的歷史，同時也徹底避開了撰寫複雜反推演算法的風險。

### Phase 1: Data Plumbing (Domain, Command/Result, Request/Response, Service, Controller, Mapper)
單純將 `reducedMonths` (縮短月數) 欄位從 API 請求一路貫通到資料庫儲存。
* **Domain Layer**: 
  - 修改 `MortgageEarlyRepayment.java`，新增 `private Integer reducedMonths;` 欄位並加上 JPA `@Column` 映射。
  - 更新其建構子，接收此參數。
* **Request / Response & Command / Result**:
  - `MortgageRecordRequest.java`: 內部的 `MortgageEarlyRepaymentRequest` 新增 `Integer reducedMonths;`。
  - `MortgageEarlyRepaymentResponse.java`: 新增 `Integer reducedMonths;`。
  - `SaveMortgageRecordCommand.java`: 內部的 `EarlyRepaymentCommand` 新增 `Integer reducedMonths;`。
  - `MortgageEarlyRepaymentResult.java`: 若有此 Result 類別，同步新增。
* **Service, Controller & Mapper**:
  - `MortgageRecordServiceImpl.java` (與任何實作 Entity <-> Command 轉換的 Mapper 邏輯)：確保在儲存 (save) 與讀取 (get) 時，`reducedMonths` 都有被正確賦值與傳遞。

### Phase 2: 邏輯核心修復 (Core Calculator Fix)
在 `MortgageCalculator.java` 中修復所有計算「剩餘月數」的邏輯。此階段完全不動 DB，只處理記憶體中的模擬計算。
* **詳細步驟 1：新增動態計算總縮短月數的方法**
  - 在 `MortgageCalculator` 中實作 `private int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm)`。
  - **內部邏輯**：初始化 `total = 0`，迴圈遍歷 `m.getEarlyRepayments()`。
  - **條件判定**：如果該紀錄是 `REDUCE_TERM` 且其發生年月小於或等於當前迴圈的模擬年月（即 `!YearMonth.from(er.getRepaymentDate()).isAfter(currentYm)`），則將該紀錄的 `reducedMonths`（若不為 null）加入 `total`。
  - 回傳 `total`。
* **詳細步驟 2：替換現有的剩餘月數公式**
  - 找到檔案中的 6 處原有計算式：`int remainingMonths = m.getLoanTermMonths() - totalElapsedNow;`
  - 替換為：`int remainingMonths = m.getLoanTermMonths() - totalElapsedNow - calculateAccumulatedReducedMonths(m, currentYm);`
  - 確保最低下限：`remainingMonths = Math.max(1, remainingMonths);`（防止因極端提前還款導致分母為零或負數的例外）。
* **影響範圍評估**：這項替換將自動且全面地修復 (1) 5年一次重算時的低估、(2) 利率變動重算時的低估、(3) 當使用者再次執行 REDUCE_PAYMENT 時的低估，所有用到剩餘月數推演未來現金流的地方都會變成正確數字。

### Phase 3: Frontend UI
讓使用者能在畫面上輸入此數值。
* **Types**: 修改 `hub-ui/src/app/finance/accounts/mortgages/types.ts`，在 `MortgageEarlyRepayment` 介面加上 `reducedMonths?: number;`。
* **UI Component**: 修改 `hub-ui/src/app/finance/accounts/mortgages/page.tsx`。
  - 在提前還款的表單陣列中，根據使用者選擇的 `repaymentType` 進行條件渲染：只有當選擇 `REDUCE_TERM` (期間短縮型) 時，才顯示「縮短月數」的 Number Input。
  - 在送出表單前，若類型為 `REDUCE_PAYMENT`，強制將該陣列元素的 `reducedMonths` 清為 null 或 0，避免髒資料。

### 實作進度 (Execution Progress)
- ✅ **Phase 1: Data Plumbing (2026-09-17)**
  - **Domain**: 於 `MortgageEarlyRepayment` 實體新增 `@Column(name = "reduced_months") private Integer reducedMonths` 欄位並更新建構子。
  - **Command/Result**: 於 `SaveMortgageRecordCommand.MortgageEarlyRepaymentCommand` 新增 `reducedMonths` 屬性；於 `MortgageEarlyRepaymentResult` 新增對應欄位，並於 `fromEntity` 靜態方法完成資料讀取映射。
  - **Request/Response**: 於 `MortgageRecordRequest.MortgageEarlyRepaymentRequest` 與 `MortgageEarlyRepaymentResponse` 新增 `Integer reducedMonths` 欄位，打通 API 介面。
  - **Service**: 修改 `MortgageRecordServiceImpl.applyEarlyRepayments`，將 `reducedMonths` 從 Command 層正確映射並傳入 Entity 的建構子中，確保前端請求的資料能被正確保存至資料庫。
- ✅ **Phase 2: Core Calculator Fix (2026-09-17)**
  - **Calculator Logic**: 於 `MortgageCalculator.java` 類別中實作了 `calculateAccumulatedReducedMonths` 輔助方法。該方法透過遍歷房貸的提前還款紀錄，動態加總所有發生在當前模擬年月之前的「縮短年限 (REDUCE_TERM)」類型的 `reducedMonths`。
  - **Formula Replacement**: 將 `fastForwardMortgagesToPresent` 與 `simulateMortgagesForYear` 方法中共 6 處計算 `remainingMonths` 的公式，由原本的 `m.getLoanTermMonths() - totalElapsedNow` 替換為 `m.getLoanTermMonths() - totalElapsedNow - calculateAccumulatedReducedMonths(m, currentYm)`。
  - **Safety Guard**: 加上 `remainingMonths = Math.max(1, remainingMonths)` 以防止因異常資料或極端還款造成的零或負數除外錯誤。
- ✅ **Phase 3: Frontend UI (2026-09-17)**
  - **Types**: 於 `hub-ui/src/app/finance/accounts/mortgages/types.ts` 中的 `MortgageEarlyRepayment` 介面新增 `reducedMonths?: number;` 可選屬性。
  - **Validation & Submit**: 在 `page.tsx` 的 `handleSave` 中新增防呆機制（若選擇 `REDUCE_TERM` 則強制要求填寫有效月數），並於準備 payload 時清理非 `REDUCE_TERM` 的 `reducedMonths` 資料。
  - **UI Render**: 調整提前還款的 `map` 渲染結構，並新增條件判斷：當選擇「期間短縮型 (REDUCE_TERM)」時，動態展開第二列輸入框供使用者填寫實際縮短的月數。
