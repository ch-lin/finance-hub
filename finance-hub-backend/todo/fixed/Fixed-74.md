# Fixed 74：房貸獎金月付款彙總視窗漏算當月，導致 5 年利率重設/利率變動時重新算出的「每月固定攤還金額」本身算錯（獎金加碼款金額本身沒有錯，錯的是每個月都要繳的那筆固定金額）

## 嚴重程度
Medium

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java:75-111`（`countRemainingBonusPaymentsAmount`、`presentValueOfBonusStream` 這兩個 `private static` 方法）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java`（呼叫端，共 6 處：`fastForwardMortgagesToPresent` 內的第 173-175 行與 191-193 行；`simulateMortgagesForYear` 內的第 326-328 行、343-345 行、370-372 行；另外，第 238-249 行與 401-411 行是「把獎金加碼款讀出來、跟每月固定攤還金額加在一起變成當月總支出」的地方，用來對照說明下面「錯的是哪個金額」）

## 錯的是哪個金額？（獎金／合計／非獎金——三選一，直接回答）

這個房貸功能裡，使用者每個月要繳的錢，其實是兩筆完全分開計算的錢加起來：

1. **每月固定攤還金額**（不管有沒有獎金加碼，每個月都要繳的那筆固定的錢）——**這一筆才是算錯的**。它是 `calculateFixedPaymentOrPrincipal` 算出來的（`MortgageCalculator.java:184-186` / `199-200` / `223`），程式碼裡變數叫 `mortgageMonthlyPayments[i]`。
2. **獎金加碼款**（使用者自己在設定裡填的、某個月要多繳的那一筆錢，例如 6 月、12 月各多繳一筆）——**這一筆沒有算錯，而且一定會被實際拿去還款，不會因為這個 bug 而被漏繳或跳過**。程式碼是直接把使用者填的金額讀出來加總（`MortgageCalculator.java:240-245` / `402-407`，變數叫 `currentMonthBonus`），完全不會經過本篇說的這個有 bug 的計算，所以金額不會受影響；而且這筆錢每個月只要到期，就會無條件被加進當月要繳的錢裡、並實際從貸款餘額中扣除（`MortgageCalculator.java:249` 與 `264` 的 `balance -= principal`），不受本篇 bug 影響的 `remainingMonths`／`currentMonthValue` 視窗判斷所左右。
3. **當月總支出**（有獎金加碼的月份，使用者實際要繳的錢 = 每月固定攤還金額 + 當月獎金加碼款，`MortgageCalculator.java:249` / `411` 的 `payment += currentMonthBonus`）——**這一筆本身沒有獨立的計算錯誤**，它只是把「1」跟「2」加起來而已。因為「1」算錯了，所以加總出來的「3」當然也會跟著錯，但錯誤的源頭只有一個：「1. 每月固定攤還金額」，不是「3」本身另外又算錯一次。

**結論：本 issue 要修正的，是「1. 每月固定攤還金額」本身的計算公式，不是獎金加碼款，也不是合計金額另外有問題。**

下面「現狀」「原因」章節說明的，就是「1. 每月固定攤還金額」在計算過程中，用來扣除「未來獎金加碼款現值」這一步驟裡的 bug（這一步驟只是計算「1」的中間過程，跟使用者實際拿到的獎金加碼款金額本身無關）。

## 現狀

### 1. 出問題的兩個私有方法（`MortgageCalculatorUtil.java:75-111`）

```java
private static long countRemainingBonusPaymentsAmount(int remainingMonths, List<BonusPayment> bonusPayments,
        int currentMonthValue) {
    if (bonusPayments == null || bonusPayments.isEmpty())
        return 0;
    long total = 0, month = currentMonthValue;
    for (int k = 1; k <= remainingMonths; k++) {
        month = (month == 12) ? 1 : month + 1;          // <-- 遞增寫在迴圈最前面
        for (BonusPayment bp : bonusPayments) {
            if (bp.getMonth() != null && bp.getMonth() == month) {
                Long amount = bp.getAmount();
                total += amount != null ? amount : 0L;
            }
        }
    }
    return total;
}

private static double presentValueOfBonusStream(double monthlyRate, int remainingMonths,
        List<BonusPayment> bonusPayments, int currentMonthValue) {
    if (bonusPayments == null || bonusPayments.isEmpty())
        return 0.0;
    double pv = 0.0;
    long month = currentMonthValue;
    for (int k = 1; k <= remainingMonths; k++) {
        month = (month == 12) ? 1 : month + 1;          // <-- 同樣的遞增順序問題
        for (BonusPayment bp : bonusPayments) {
            if (bp.getMonth() != null && bp.getMonth() == month) {
                Long rawAmt = bp.getAmount();
                long amt = rawAmt != null ? rawAmt : 0L;
                if (amt > 0) {
                    pv += amt / Math.pow(1 + monthlyRate, k);
                }
            }
        }
    }
    return pv;
}
```

兩個迴圈都是「先把 `month` 遞增，才檢查該月是否有獎金」。因此對 `k=1..remainingMonths`，實際被檢查到的月份是 `currentMonthValue+1, currentMonthValue+2, ..., currentMonthValue+remainingMonths`（皆對 12 取循環），總共 `remainingMonths` 個月——**`currentMonthValue` 這個月本身永遠不會被檢查到**。

### 2. `remainingMonths` 的定義本身包含「當前月」（呼叫端共通模式，`MortgageCalculator.java`）

以 `simulateMortgagesForYear` 第 326-328 行為例（`fastForwardMortgagesToPresent` 的第 173-175 行、以及另外 4 處呼叫點也都是完全相同的寫法）：

```java
int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
        + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
int remainingMonths = m.getLoanTermMonths() - totalElapsedNow;
```

當觸發重算的那一次迴圈迭代恰好落在 `currentYm == 貸款起算月`（例如貸款剛開始，或本次是每滿 60 個月的利率重設檢查點/利率剛變動的那個月）時，`totalElapsedNow = 0`，也就是說 `remainingMonths = loanTermMonths`——換句話說，**`remainingMonths` 是把 `currentYm`（也就是傳入的 `currentMonthValue`）當作剩餘 `remainingMonths` 期付款中的「第 1 期」來計算的**。

同時，緊接著呼叫的付款重算公式：

```java
long newPayment = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
        balance, currentRates[i], remainingMonths, m.getRepaymentMethod(),
        m.getBonusPayments(), currentYm.getMonthValue());
...
mortgageMonthlyPayments[i] = newPayment;   // 立刻被用作「本月」的月付款金額
```

算出的 `newPayment` 會立刻在同一次月迭代中被當作**本月（`currentYm`）**的月付款金額使用（見 `simulateMortgagesForYear` 第 401 行 `long payment = mortgageMonthlyPayments[i];`）。這代表整個模型對「第 1 期付款」的定義就是「本月（`currentYm`）的付款」，而 `calculateFixedPaymentOrPrincipal` 內部使用的標準年金公式（`monthlyPortionBalance * r * (1+r)^n / ((1+r)^n - 1)`）是「期末付款」型年金（ordinary annuity）：第 1 期的付款發生在第 1 期期末，並用 `(1+r)^1` 折現回現值。

## 原因
把以上兩點放在一起看：

- `remainingMonths` 期的年金，第 1 期（`k=1`）對應的日曆月份就是 `currentMonthValue` 本身（因為 `newPayment` 立刻被當成本月付款使用）。
- 但獎金彙總迴圈卻讓 `k=1` 對應到 `currentMonthValue+1`，讓 `k=remainingMonths` 對應到 `currentMonthValue+remainingMonths`（一個超出剩餘期數視窗、屬於下一次重算週期的月份）。

結果是：**整個獎金彙總視窗相對於年金公式本身的期數編號，被錯誤地平移了一個月**——`currentMonthValue` 當月若有獎金加碼還款，會被完全遺漏在彙總金額之外；同時彙總視窗會多算進 `currentMonthValue+remainingMonths` 這個原本不屬於這次剩餘期數範圍內的月份。

這兩個私有方法各被兩種不同的還款方式使用，影響方式略有不同：
- `RepaymentMethod.EQUAL_PRINCIPAL`（`countRemainingBonusPaymentsAmount`）：只是加總遺漏/多算月份的獎金原始金額，直接影響「本金平均攤還」下每月本金的計算基礎。
- `RepaymentMethod.EQUAL_PAYMENT`（`presentValueOfBonusStream`）：因為連折現期數 `k` 本身也一起平移了，所以除了「漏算 `currentMonthValue`、多算 `currentMonthValue+remainingMonths`」之外，就連「有被正確加總到的那些月份」，其折現期數也都被少算了 1 期（例如 `currentMonthValue+1` 這個月的獎金，本該用 `k=1` 折現，卻被用 `k=1`??——不，需重新核對：修正後 `currentMonthValue+1` 應對應 `k=2`，但目前程式碼算成 `k=1`），導致這些月份的現值被系統性高估（折現期數少算 1 期，分母 `(1+r)^k` 變小，PV 變大）。

## 具體錯誤情境
使用者的房貸為本息平均攤還（`EQUAL_PAYMENT`）、有五年利率重設條款（`hasFiveYearRule = true`），且在 6 月與 12 月各有一筆獎金加碼還款。模擬跑到滿 60 個月的利率重設檢查點，恰好該檢查點落在 6 月（`monthsSinceStart[i] % 60 == 0` 於 `currentYm = 該年6月` 成立）。重新計算新固定月付款時：
- 6 月本身排定的獎金加碼還款被排除在彙總金額外（因為 `currentMonthValue=6` 永遠不會被檢查到）。
- 折現期數全面少算 1 期，讓「有被算到」的獎金現值被高估。
- 兩者加總的淨效果是：`presentValueOfBonusStream` 算出的獎金現值低於正確值（漏掉一整筆未折現的當月獎金，但其餘月份的獎金現值又被高估，兩者不會互相抵消），導致 `calculateFixedPaymentOrPrincipal` 算出的**每月固定攤還金額**（不含獎金加碼款本身）偏離正確值（方向依獎金金額與時間分佈而定，不保證單一方向）。使用者看到的「利率重設後每月固定攤還金額」與其貸款契約的真實攤還表不一致；6 月、12 月的獎金加碼款金額本身則不受影響，仍是使用者原本設定的正確金額。

## 建議修正
兩個私有方法的修正方式完全相同、且是最小改動：**把 `month = (month == 12) ? 1 : month + 1;` 這一行，從迴圈主體的最前面移到最後面**（迴圈的 `k` 上下界、`for` 條件本身都不需要改變）。

### `countRemainingBonusPaymentsAmount` 修正後：
```java
private static long countRemainingBonusPaymentsAmount(int remainingMonths, List<BonusPayment> bonusPayments,
        int currentMonthValue) {
    if (bonusPayments == null || bonusPayments.isEmpty())
        return 0;
    long total = 0, month = currentMonthValue;
    for (int k = 1; k <= remainingMonths; k++) {
        for (BonusPayment bp : bonusPayments) {
            if (bp.getMonth() != null && bp.getMonth() == month) {
                Long amount = bp.getAmount();
                total += amount != null ? amount : 0L;
            }
        }
        month = (month == 12) ? 1 : month + 1;
    }
    return total;
}
```

### `presentValueOfBonusStream` 修正後：
```java
private static double presentValueOfBonusStream(double monthlyRate, int remainingMonths,
        List<BonusPayment> bonusPayments, int currentMonthValue) {
    if (bonusPayments == null || bonusPayments.isEmpty())
        return 0.0;
    double pv = 0.0;
    long month = currentMonthValue;
    for (int k = 1; k <= remainingMonths; k++) {
        for (BonusPayment bp : bonusPayments) {
            if (bp.getMonth() != null && bp.getMonth() == month) {
                Long rawAmt = bp.getAmount();
                long amt = rawAmt != null ? rawAmt : 0L;
                if (amt > 0) {
                    pv += amt / Math.pow(1 + monthlyRate, k);
                }
            }
        }
        month = (month == 12) ? 1 : month + 1;
    }
    return pv;
}
```

修正後 `k=1` 會對應到 `month=currentMonthValue`（即年金公式定義中的「第 1 期＝本月」），`k=remainingMonths` 會對應到 `currentMonthValue+remainingMonths-1`，恰好覆蓋 `remainingMonths` 個連續月份、且與 `presentValueOfBonusStream` 用來折現一般月付款的年金期數編號完全對齊，不需要調整 `MortgageCalculator.java` 呼叫端的任何程式碼（呼叫端 6 處都不用改，因為兩個方法的參數簽名與語意完全不變，只是內部視窗定義修正）。

**特別提醒（避免另一種看起來合理但實際上是錯的修法）**：不要改成「把 `for (int k = 1; ...)` 改成 `for (int k = 0; ...)`，同時保留遞增寫在迴圈最前面」這種寫法。這樣做雖然一樣能讓 `currentMonthValue` 被檢查到，但在 `presentValueOfBonusStream` 中會讓 `currentMonthValue` 對應到 `k=0`（`Math.pow(1+r, 0) = 1`，即完全不折現，視為「現在立刻發生」），這與年金公式把「本月付款」當作「第 1 期期末付款」（需要用 `(1+r)^1` 折現）的既有慣例不一致，會引入另一種折現期數錯位的 bug。務必採用「移動遞增位置」而非「改變 k 的起始值」的修法，兩者對 `countRemainingBonusPaymentsAmount`（只加總原始金額、不折現）效果相同，但對 `presentValueOfBonusStream`（結果依賴 `k` 的折現期數）效果不同，只有「移動遞增位置」才與現有年金慣例一致。

### 建議驗證方式
可比對「移動遞增位置」前後，針對同一組 `remainingMonths=60`、`currentMonthValue=6`、`bonusPayments=[{month:6, amount:X}, {month:12, amount:Y}]` 的輸入：
- 修正前 `countRemainingBonusPaymentsAmount` 應回傳只含 `Y`（漏掉 `X`，因為 6 月永遠不會被檢查到，但會多檢查一個月份 `currentMonthValue+remainingMonths` 對 12 取循環後的月份，若剛好又是 6 或 12 則另計）。
- 修正後應回傳 `X+Y`（正確涵蓋 6 月到未來 59 個月內的兩筆獎金，且不多算超出視窗外的月份）。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 均未提及房貸獎金彙總視窗與年金折現期數編號錯位的問題，屬於全新發現的問題。

## 澄清：這是「算出來的金額錯」，不是「重算時間點延遲」

標題已修正，不再使用「延遲一個月」的說法，理由如下：

- **觸發時間點完全正常，沒有延遲。** 五年利率重設的判斷（`monthsSinceStart[i] % 60 == 0`）與利率變動偵測（`rateChangedThisMonth`），都會準時在第 60、120…個月，或利率真正變動的那個月觸發重新計算，不受本篇描述的 bug 影響。
- **出錯的是觸發之後算出來的「每月固定攤還金額」，不是獎金加碼款、也不是另一個獨立的合計錯誤（詳見上方「錯的是哪個金額？」一節）。** 一旦觸發，程式會呼叫 `calculateFixedPaymentOrPrincipal` 重算「每月固定攤還金額」，而這個計算內部用來加總/折現「未來獎金加碼款現值」的時間窗口，往後多平移了一個月（見上方「原因」一節：漏算 `currentMonthValue` 當月的獎金、多算超出剩餘期數之外的月份、且所有折現期數都少算 1 期）。這會讓算出來的「每月固定攤還金額」偏離正確值，但重算這件事本身**是在正確的月份發生的**，不是拖到下一個月才發生。

簡單說：「有沒有在對的時間重算」是對的；「重算出來的『每月固定攤還金額』數字對不對」是錯的——本 issue 修正的是後者，跟獎金加碼款金額本身無關。

---
## AI 驗證與修復報告

✅ **AI (Gemini) 驗證結論：這不是 Claude 的幻覺。**
這份報告的分析與程式碼現狀完全吻合。`countRemainingBonusPaymentsAmount` 與 `presentValueOfBonusStream` 確實在一進入迴圈時就先遞增月份，完美錯過了 `currentMonthValue` 本身，且導致折現期數整體錯位，計算完全不符合年金的「第 1 期期末」慣例。

✅ **修正狀態：已套用修正**
已針對 `MortgageCalculatorUtil.java` 套用建議的修正，將 `month = (month == 12) ? 1 : month + 1;` 移動到 `for` 迴圈的最後面。
此修正完全沒有負面邊際效應，因為修改的兩個私有方法僅被內部的 `calculateFixedPaymentOrPrincipal` 呼叫，修改後讓期數 `k=1` 對齊真正的本月 `currentMonthValue`，完全符合現行模型的行為，且不會影響呼叫端原本正確的時機點邏輯。
