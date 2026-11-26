# Plan 21-02：基礎控除「前年所得課稅時差機制」子題——併入Plan-21，open狀態

## 跟Plan-21的關係
這是[Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md)（住民稅前年所得課稅時差機制，open狀態、等未來評估雙引擎架構）底下的子題，記錄基礎控除（原`todo/planned/Plan-16-inhabitant-tax-basic-deduction-high-income-strategy.md`）這個模組的具體情況。這份子題原本獨立記錄在issue-138裡，現在併入這裡，不再單獨當成issue個案處理。

## 背景：Plan-16宣稱做了什麼
Plan-16文件主張：日本住民稅是用「前一年的所得」去課「今年的稅」，實作比照question-120的`SpouseDeductionStrategyFactory`雙軌派發架構，讓住民稅基礎控除正確反映這個時間差。文件舉的具體範例：使用者2025年所得2,305萬、2026年所得衝到2,505萬，主張「2026年住民稅依2025年前年所得課稅，仍享43萬控除；要到2027年才會因為2025年所得才歸零」。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:101-106`（薪資情境：`employmentIncome`算出「今年」所得後，第103行、第105行同時把這同一個今年所得，分別餵給`getIncomeTaxStrategy`跟`getInhabitantTaxStrategy`兩條線）
- 同檔案354-357行（退休年金情境：同樣寫法，`miscellaneousIncome`這個今年所得同時餵給兩條線）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/basic/BasicDeductionStrategyFactory.java:46-75`（`getIncomeTaxStrategy(year)`跟`getInhabitantTaxStrategy(year)`，雙軌派發的年份路由邏輯）
- 四個策略類別：`LegacyBasicDeductionStrategy.java`、`Reiwa7BasicDeductionStrategy.java`、`Reiwa8And9BasicDeductionStrategy.java`、`Reiwa10OnwardsBasicDeductionStrategy.java`（各自的`calculateInhabitantTaxDeduction`方法，逐字比對後完全相同的級距遞減邏輯）

## 現狀：級距遞減邏輯本身是對的，問題在「前年所得」這個數字從未被使用
四個策略類別的住民稅高所得級距遞減邏輯（2,400萬以下43萬、2,400萬~2,450萬降至29萬、2,450萬~2,500萬降至15萬、超過2,500萬歸零），逐字比對四個檔案完全一致，邊界判斷精確。**這部分已確認正確實作，是這個系統現在就能正常運作的功能，不受這份文件影響。**

但`getIncomeTaxStrategy(year)`跟`getInhabitantTaxStrategy(year)`這兩個方法，只決定「要套用哪一個年代的法規版本物件」，不決定「要用哪一年的所得金額去查表」——決定級距的所得金額，不管呼叫哪一條線，永遠是同一個今年的所得。拿文件自己舉的例子套：如果使用者2026年所得真的衝到2,505萬，程式碼會直接用「今年」的2,505萬去查表，直接查到0円——跟文件宣稱的「2026年依前年所得課稅、仍享43萬控除」完全相反。

## 處置
跟隨Plan-21的整體open狀態，這個時差機制目前不投入實作，等未來評估雙引擎架構時再回頭參考。但建議現在就處理一件事：**`todo/planned/Plan-16-inhabitant-tax-basic-deduction-high-income-strategy.md`文件標題跟內文「前年所得課稅時差機制」這個核心賣點的描述，應該修正成誠實反映現狀**（例如改成「住民稅基礎控除高所得級距遞減」，拿掉時差機制這個目前不存在的宣稱）——這一步跟是否要投入月級別引擎重寫無關，純粹是讓文件描述跟實際程式碼行為一致，避免未來有人依賴這份文件的描述去判斷系統行為、或誤以為這個功能已經存在。
