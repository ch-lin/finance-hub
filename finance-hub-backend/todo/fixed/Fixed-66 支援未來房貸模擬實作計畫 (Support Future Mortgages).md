# 支援未來房貸模擬實作計畫 (Support Future Mortgages)

目前模擬器 (`MortgageCalculator.java`) 在迴圈計算時，並未判斷當前模擬月份是否已到達房貸的起始日 (`startDate`)。這會導致即使房貸設在明年，今年也會被扣繳月付金與利息。此計畫將修正該邏輯，讓未來才開始的房貸能準確在對應的年月啟動。

## User Review Required

> [!IMPORTANT]
> **淨資產 (Net Worth) 的變化邏輯與資產認定**
> 在避免現金破產為前提下，自住房產「不該」被當作高流動性資產。由於房子變現困難，且賣掉後仍有居住需求，因此在現金流模擬中，我們更關注的是 **「Liquid Cash (可用現金)」** 是否見底。
> 
> 當未來房貸啟動時，系統會把這 5,000 萬計入負債，導致「True Net Worth (真實淨資產 = 帳戶餘額 + 投資 - 房貸總和)」瞬間變成負值，這是合理的財務視角，因為您確實背了債，且房子無法立刻變現。請以「Liquid Cash」曲線作為判斷是否會破產的最關鍵指標，只要 Liquid Cash 撐得住，True Net Worth 暫時為負是正常的。

> [!NOTE]
> **頭期款的處理**
> 由於未來房貸啟動時，只會模擬每個月的房貸還款。您買房所支出的「頭期款」，請在該年度新增一筆對應的 LifeEvent (人生事件 - 支出)，例如「買房頭期款 - 1,000 萬」，這樣現金流的模擬就會完全符合現實。

> [!TIP]
> **特定月份啟動（例如 3 月開始的房貸）如何計算？**
> 您的理解完全正確！假設您設定房貸在「某年的 3 月」開始：
> 1. **1 月與 2 月**：系統走到這兩個月時，會判定「尚未達到 startDate」，直接跳過，**不扣任何錢、也不計息**。
> 2. **3 月**：系統走到 3 月時，發現「已經達到 startDate」，此時會瞬間將您的負債餘額載入（例如 5,000 萬），並且在當月**立刻開始扣除第一筆月付金與利息**。
> 3. **4 月以後**：就如同一般進行中的房貸，每個月正常扣款與計算利息。

## Open Questions

目前沒有阻礙實作的重大問題，邏輯修改範圍聚焦且單純。

## Proposed Changes

---

### Simulation Core (Mortgage Calculator)

修改 `MortgageCalculator.java` 內的邏輯，使其能夠正確處理尚未發生的未來房貸：

#### [MODIFY] [MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)
1. **`initializeMortgages` 方法**
   - 調整 `context.setMortgageBalances` 的初始化。如果發現該房貸的 `startDate` 晚於現在 (`LocalDate.now()`)，則初始餘額暫時設為 `0L`，避免未來房貸提早出現在當下的負債總額中。
2. **`fastForwardMortgagesToPresent` 方法**
   - 增加檢查，如果跑到未啟動的月份 (`currentYm.isBefore(startDate)`)，直接 `continue` 跳過。
   - 如果剛好等於起始月份且餘額為 0，則將餘額初始化為 `m.getCurrentBalance()`。
3. **`simulateMortgagesForYear` 方法**
   - 原本一進迴圈發現 `balance <= 0` 就會跳過整年。改為：若 `balance <= 0` 但該房貸就是「今年」才要啟動，則允許進入月份迴圈。
   - 在 1~12 月的迴圈中，加入 `hasStarted` 判斷。如果當前月份小於 `startDate` 所在月份，則直接 `continue` 不計息也不扣款。
   - 當迴圈走到 `startDate` 所在的月份時，將 `balance` 初始化為 `m.getCurrentBalance()`（啟動房貸餘額）。

## Verification Plan

### Manual Verification
1. 請您由 IDE 執行編譯與跑測試。
2. 在介面中設定一個 `startDate` 為「明年 3 月」的新房貸。
3. 執行 Sandbox 或 GA 模擬，並觀察以下現象是否符合預期：
   - 今年（還沒買房時）：現金與淨資產完全不會被扣除該房貸的月付金。
   - 明年（買房後）：1、2 月現金流正常；從 3 月開始，淨資產會增加一筆負債，且每個月的現金流開始扣除 `monthlyPayment`。
