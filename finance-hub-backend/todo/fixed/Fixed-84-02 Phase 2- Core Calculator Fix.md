# Fixed-84-02 Phase 2: Core Calculator Fix

## Goal
完成 Phase 2 (邏輯核心修復)：在 `MortgageCalculator.java` 中修復所有計算「剩餘月數」的邏輯。確保當房貸經過「縮短年限 (REDUCE_TERM)」的提前還款後，後續的月付金重新計算（如 5 年重算、利率變動、或是額外提前還款的觸發點）都會扣除累積已縮短的月數，從而得出正確（不被低估）的剩餘月數與月付金額。

## Open Questions
無。計算邏輯的方針已由前次討論確認（方向A的延伸，利用資料庫中保存的 `reducedMonths` 動態計算累積值）。

## Proposed Changes

### Backend App Layer

#### [MODIFY] [MortgageCalculator.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java)

1. **新增 `calculateAccumulatedReducedMonths` 方法**
在類別末尾（或其他適當位置）新增私有輔助方法：
```java
    private int calculateAccumulatedReducedMonths(MortgageRecord m, YearMonth currentYm) {
        if (m.getEarlyRepayments() == null) {
            return 0;
        }
        int total = 0;
        for (var er : m.getEarlyRepayments()) {
            if (er.getRepaymentType() == ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType.REDUCE_TERM) {
                YearMonth erYm = YearMonth.from(er.getRepaymentDate());
                // 如果提前還款的年月 小於等於 當前模擬的年月，則計入縮短月數
                if (!erYm.isAfter(currentYm)) {
                    Integer reduced = er.getReducedMonths();
                    total += (reduced != null) ? reduced : 0;
                }
            }
        }
        return total;
    }
```

2. **修正 6 處 `remainingMonths` 計算邏輯**
在 `fastForwardMortgagesToPresent` (3處) 與 `simulateMortgagesForYear` (3處) 內部，找到以下原本的程式碼：
```java
int remainingMonths = m.getLoanTermMonths() - totalElapsedNow;
```
將其替換為：
```java
int remainingMonths = m.getLoanTermMonths() - totalElapsedNow - calculateAccumulatedReducedMonths(m, currentYm);
remainingMonths = Math.max(1, remainingMonths);
```

這 6 處分別處理了：
- `EQUAL_PAYMENT` 且有 5 年重算規則時的重算 (Line 175, 328)
- `EQUAL_PAYMENT` 在利率變動時的重算 (Line 193, 345)
- 當下發生 `REDUCE_PAYMENT` (減少月付金額) 類型提前還款時的即時重算 (Line 214, 372)

## Verification Plan

### Manual Verification
1. 請使用者審核此計畫。
2. 取得核准後，我將執行程式碼替換。
3. 執行後不需撰寫新測試，僅需確認編譯無誤 (`mvn clean install -DskipTests`)，實際效果會於後續 Phase 3 接通前端介面後，由使用者在 UI 進行手動驗證。
