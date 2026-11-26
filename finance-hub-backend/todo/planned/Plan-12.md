# Plna 12：讓GA在試算「未來要不要提前還一筆房貸」時，除了「縮短年限」，也能試算「減少月付金額」

## 現在系統做不到什麼
提前還一筆房貸，有兩種做法：

- **做法A（縮短年限）**：每個月要付的錢不變，因為多還了一筆錢，貸款會提早在某個月還完。
- **做法B（減少月付金額）**：還款年限不變，但因為本金變少了，之後每個月要付的錢會變少。

現在系統的最佳化功能，試算「以後某一年要不要提前還一筆房貸」時，永遠只會用做法A去算，完全沒辦法試算做法B，也沒辦法拿兩種做法互相比較。

要新增的功能：讓系統試算「以後提前還一筆房貸」的時候，做法A、做法B都算過，看哪一種結果比較好就用哪一種。

## 只加在GA，Grid Search不動
這個功能原本有兩個方向：兩個引擎都加，或先在Grid Search小規模試試看。討論後已經定案：只加在GA，Grid Search維持現狀。理由：

1. Grid Search現在的「提前還款」只有兩個選項：某一年把貸款全部還完，或永遠不還——沒有「還一部分」這個概念。「減少月付金額」這個做法，前提就是要先「還一部分」。Grid Search連這個前提都不成立，這個功能在Grid Search上沒東西可以裝。
2. 就算之後要讓Grid Search也能「還一部分」，光加「金額」這一個新的可能性，一種房貸、一次還款，可能出現的組合數就已經超過系統現在設定不准跑的上限（1000種）。如果還要像GA一樣，一筆房貸最多模擬5次不同時間點的還款，組合數會爆炸性成長，兩次還款就可能超過100萬種，遠遠超過系統另一條真的會強制中斷計算的防線（5000種）。
3. 兩個引擎現在都是一個組合一個組合慢慢算，沒有同時算多個的設計，組合數愈多、等的時間愈久。GA不管你打開幾個項目一起找，固定只跑大約1000次左右的試算就會給你結果；硬要把Grid Search擴充到能支援多次、有金額的搜尋，需要試算的次數會遠遠超過GA現在的用量，等於要讓你等更久，換到的結果卻不保證比GA更好。

結論：這個功能只透過GA提供，Grid Search不會有這個能力。這是這次新功能的範圍界線，不是Grid Search有什麼問題要處理。

## 不需要使用者多填任何數字
你之前規定過：使用者真的去銀行辦好「縮短年限」的提前還款之後，系統不會自己去猜銀行實際上少算了幾個月，而是要求使用者自己填這個「少算的月數」——因為每家銀行的計算方式、四捨五入規則不一樣，系統沒辦法自己算出正確答案。

這次要新增的是「假設未來會怎麼提前還款」，是GA自己去嘗試、還沒真的發生的情境。不管GA假設的是「縮短年限」還是「減少月付金額」，套用的都是系統原本就有的公式，讓試算自己往下跑。以「縮短年限」來說：本金因為多還了一筆錢而變少，之後每個月照常扣款，貸款自然會提早在某個月被還完——不需要另外算出「這筆假設的還款讓年限剛好少了幾個月」，也不需要使用者多填任何數字。

（補充：下面第7點的程式碼裡，「剩餘期數」的算法會把使用者之前真實填過的、縮短年限提前還款少算的月數也算進去。這是系統本來就有的邏輯，用來確保「假設的未來還款」是接在「過去真實發生過的事」後面繼續算，跟這次的新功能無關，也不會因為這次新功能而改變。這裡只是提醒工程師改程式碼時別漏掉這段既有邏輯，不是說使用者要多填什麼。）

## 技術實作方向（給實際動工的工程師參考）
這一段附程式碼，是寫給實際改程式碼的工程師看的。核心概念：系統裡已經有一套現成的公式，用在「使用者自己填、真實已經發生過」的「減少月付金額」提前還款。這次只要讓GA「假設的未來提前還款」也套用同一套現成公式，不需要另外發明新算法。

### 1. 沿用既有的 `EarlyRepaymentType`，不用新增
系統裡已經有這個enum，真實已發生的提前還款一直都是用它區分兩種做法：
```java
public enum EarlyRepaymentType {
    REDUCE_TERM,    // 縮短年限
    REDUCE_PAYMENT  // 減少月付金額
}
```

### 2. 讓「假設的未來提前還款」的資料結構多帶一個「用哪種做法」的欄位
[SimulationOverrides.java:68-71](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/command/SimulationOverrides.java:68-71) 現況只有年份跟金額：
```java
public record MortgageEarlyRepaymentOverride(
        Integer year,
        Long amount) {
}
```
改成：
```java
public record MortgageEarlyRepaymentOverride(
        Integer year,
        Long amount,
        EarlyRepaymentType repaymentType) {
}
```

### 3. 讓GA用來「假設未來提前還款」的基因也多帶這個欄位
[Chromosome.java:63-71](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java:63-71)：
```java
@Data
@NoArgsConstructor
public static class MortgageRepaymentGene {
    private Integer year;
    private Long amount;
    private EarlyRepaymentType repaymentType;

    public MortgageRepaymentGene(Integer year, Long amount, EarlyRepaymentType repaymentType) {
        this.year = year;
        this.amount = amount;
        this.repaymentType = repaymentType;
    }
}
```
（class上面已經有`@Data`，改完之後`getRepaymentType()`/`setRepaymentType()`會自動產生，不用手寫。）

### 4. GA亂數產生、亂數微調的時候，也要一起決定類型
[Chromosome.java:104-129](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java:104-129)（`mutate`方法）：

新增一筆假設還款時，一起隨機決定類型：
```java
if (genes.size() < MAX_REPAYMENT_COUNT && random.nextDouble() < 0.05) {
    int randYear = minRepayYear + random.nextInt(maxRepayYear - minRepayYear);
    long randAmount = 500_000L + random.nextInt(45) * 100_000L; // 500k to 5M
    EarlyRepaymentType randType = random.nextBoolean()
            ? EarlyRepaymentType.REDUCE_TERM : EarlyRepaymentType.REDUCE_PAYMENT;
    genes.add(new MortgageRepaymentGene(randYear, randAmount, randType));
}
```
既有假設還款微調年份、金額的地方，一起加上「有機率把類型換成另一種」（做法跟旁邊微調年份、金額一樣，只是多一個選項）：
```java
for (MortgageRepaymentGene gene : genes) {
    if (gene.getYear() != null && gene.getAmount() != null) {
        if (random.nextDouble() < 0.5) {
            gene.setYear(gene.getYear() + (random.nextInt(3) - 1));
        }
        if (random.nextDouble() < 0.5) {
            long change = (long) ((random.nextDouble() * 2 - 1) * 1000000L);
            gene.setAmount(Math.max(100000L, gene.getAmount() + change));
        }
        if (random.nextDouble() < 0.1) {
            gene.setRepaymentType(gene.getRepaymentType() == EarlyRepaymentType.REDUCE_TERM
                    ? EarlyRepaymentType.REDUCE_PAYMENT : EarlyRepaymentType.REDUCE_TERM);
        }
    }
}
```

### 5. 兩個方案配種產生下一代時，類型要跟著年份、金額一起繼承
[Chromosome.java:174](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java:174)：
```java
if (selected != null) {
    childGenes.add(new MortgageRepaymentGene(
            selected.getYear(), selected.getAmount(), selected.getRepaymentType()));
}
```

### 6. 把類型一起轉換到最後真正拿去試算的資料裡
[ChromosomeMapper.java:50-51](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java:50-51)：
```java
overrides.add(new SimulationOverrides.MortgageEarlyRepaymentOverride(
        gene.getYear(), gene.getAmount(), gene.getRepaymentType()));
```
同一個檔案裡，[ChromosomeMapper.java:66-69](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java:66-69) 是Grid Search在用的舊接橋，把「選中的整筆結清年份」轉換成試算資料。因為Grid Search選的永遠是「整筆繳清」，繳清之後不會再有月付金額可以算，這裡的類型欄位放什麼都不會影響結果——不是一個要你選方向的地方，固定填`REDUCE_TERM`即可：
```java
mortgageOverrides.computeIfAbsent(mortgageId, k -> new ArrayList<>())
        .add(new SimulationOverrides.MortgageEarlyRepaymentOverride(
                legacyYear, Long.MAX_VALUE, EarlyRepaymentType.REDUCE_TERM));
```

### 7. 核心：讓「假設的未來提前還款」套用時，也真的重算月付金額
這是決定「這筆假設還款會不會讓每月要付的錢變少」的地方，也是整個改動裡最關鍵的一步。

好消息是，`MortgageCalculator.java`裡已經有一套現成、正在使用的邏輯，用在「使用者自己填、真實已經發生過」的「減少月付金額」提前還款，例如[MortgageCalculator.java:215-232](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:215-232)：
```java
if (er.getRepaymentType() == EarlyRepaymentType.REDUCE_PAYMENT) {
    int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
            + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
    int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
            - calculateAccumulatedReducedMonths(m, currentYm);
    remainingMonths = Math.max(1, remainingMonths);
    if (remainingMonths > 0) {
        mortgageMonthlyPayments[i] = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
                balance - earlyPrincipal, currentRates[i], remainingMonths,
                m.getRepaymentMethod(), m.getBonusPayments(), currentYm.getMonthValue());
        if (m.getRepaymentMethod() == RepaymentMethod.EQUAL_PAYMENT) {
            expectedPayments[i] = mortgageMonthlyPayments[i];
        }
    }
}
```
現在的問題：這套邏輯只套用在真實已發生的提前還款（`m.getEarlyRepayments()`）上；「假設的未來提前還款」（`overrides.mortgageEarlyRepayments()`，透過[MortgageCalculator.java:105-131](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:105-131)的`getOverrideEarlyRepaymentAmount`方法讀取）完全沒有套用這套邏輯——不管GA假設哪一年還多少錢，永遠只會扣本金、月付金額永遠不變，等於永遠只模擬「縮短年限」這一種效果。而且這個方法現在的做法是把同一年所有假設還款的金額全部加總成一筆再回傳，沒有保留每一筆各自的類型。

**修法：改成跟「真實已發生」的提前還款完全一樣的處理方式——同一年如果有好幾筆假設還款，一筆一筆依序處理，每一筆各自套用自己的類型，不要先加總。** 這樣不管同一年疊到幾筆、類型是否相同，都不需要另外決定「聽誰的」，因為每一筆本來就是各自獨立的一次還款、各自獨立套用自己的公式，跟真實情況（比如你今年2月辦一次、6月又辦一次）算法一致。

`getOverrideEarlyRepaymentAmount`要改成不加總、直接回傳這個月要處理的假設還款清單：
```java
private List<SimulationOverrides.MortgageEarlyRepaymentOverride> getOverrideEarlyRepayments(
        MortgageRecord m, YearMonth currentYm, SimulationOverrides overrides) {
    if (overrides == null || overrides.mortgageEarlyRepayments() == null) {
        return List.of();
    }
    List<SimulationOverrides.MortgageEarlyRepaymentOverride> list = overrides.mortgageEarlyRepayments()
            .get(m.getId());
    if (list == null || list.isEmpty()) {
        return List.of();
    }

    int logicalMonth = 12;
    if (m.getBonusPayments() != null && !m.getBonusPayments().isEmpty()) {
        Integer firstMonth = m.getBonusPayments().get(0).getMonth();
        logicalMonth = firstMonth != null ? firstMonth : 12;
    }
    if (currentYm.getMonthValue() != logicalMonth) {
        return List.of();
    }

    return list.stream().filter(o -> o.year().equals(currentYm.getYear())).toList();
}
```

呼叫的地方（兩處都一樣的改法，示範第一處，[MortgageCalculator.java:214](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:214)），改成跟下面「真實已發生」那段一樣，一筆一筆loop處理：
```java
long earlyPrincipal = 0L;
for (var override : getOverrideEarlyRepayments(m, currentYm, overrides)) {
    earlyPrincipal += override.amount();
    if (override.repaymentType() == EarlyRepaymentType.REDUCE_PAYMENT) {
        int totalElapsedNow = (currentYm.getYear() - m.getStartDate().getYear()) * 12
                + (currentYm.getMonthValue() - m.getStartDate().getMonthValue());
        int remainingMonths = m.getLoanTermMonths() - totalElapsedNow
                - calculateAccumulatedReducedMonths(m, currentYm);
        remainingMonths = Math.max(1, remainingMonths);
        if (remainingMonths > 0) {
            mortgageMonthlyPayments[i] = MortgageCalculatorUtil.calculateFixedPaymentOrPrincipal(
                    balance - earlyPrincipal, currentRates[i], remainingMonths,
                    m.getRepaymentMethod(), m.getBonusPayments(), currentYm.getMonthValue());
            if (m.getRepaymentMethod() == RepaymentMethod.EQUAL_PAYMENT) {
                expectedPayments[i] = mortgageMonthlyPayments[i];
            }
        }
    }
}
// 下面「真實已發生」的提前還款處理邏輯維持原樣，不用動
```
第二個呼叫點（[MortgageCalculator.java:376](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:376)）改法完全相同，只是變數命名、後續的`earlyFee`、`annualPayment`等既有邏輯照舊接續即可。
