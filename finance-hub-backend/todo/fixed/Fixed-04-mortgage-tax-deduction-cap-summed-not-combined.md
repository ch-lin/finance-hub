# [MEDIUM] 住宅ローン控除 cap is summed per-mortgage instead of applied once to the combined balance across co-existing loans on the same residence

## Severity: Medium (overstates the mortgage tax deduction, making the projected cash-bankruptcy year artificially later/more optimistic)

## Summary
In `simulateMortgagesForYear`, the mortgage tax deduction (住宅ローン控除) is computed **per mortgage
record** as `balance * rate` capped at that record's own `maxTaxDeductionAmount`, then summed across
all `taxDeductionEligible` mortgages into `mortgageTaxDeductionThisYear` — with no combined-balance
cap. `MortgageRecordServiceImpl` independently assigns each qualifying record the **full statutory
per-property cap** via `TaxStrategyFactory.calculateMaxAmount(residenceType)`, with no awareness of
any other mortgage record for the same residence. A household with two co-existing eligible loans
secured against the same house (e.g. a main bank loan plus a supplementary/提携ローン, a realistic
setup in Japan) can therefore receive up to double the legal maximum deduction once their combined
year-end balance exceeds the single-property statutory cap.

## Evidence

[MortgageCalculator.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java) lines 380-404:
```java
long deduction = (long) (balance * rate);
...
if (deduction > maxDeduction) {
    deduction = maxDeduction;
}
mortgageTaxDeductionThisYear += deduction;
```
runs inside the per-mortgage loop with no cross-mortgage combined cap.

`MortgageRecordServiceImpl.java` lines 72-73:
```java
record.setMaxTaxDeductionAmount(strategy.calculateMaxAmount(request.residenceType()));
```
assigns the full statutory per-property cap to each record independently — there is no field or
relationship anywhere linking multiple `MortgageRecord` entries to a shared "residence"/property,
and no validation preventing two co-existing `taxDeductionEligible=true` records from both
receiving the full per-property statutory cap.

Downstream, `AssetSimulationEngineImpl` passes `mortgageResult.mortgageTaxDeductionThisYear()`
straight into `ActiveTaxCalculationEngine.calculate`, which only caps it against `baseIncomeTax`
(`actualMortgageDeduction = Math.min(baseIncomeTax, mortgageTaxDeduction)`) — there is no
re-application of the statutory per-residence loan-balance cap at that stage either.

## Failure scenario
`residenceType=LONG_TERM_CERTIFIED` (2024-2025 rules, per-property cap = ¥315,000, corresponding to
a ¥45,000,000 statutory loan-balance cap). Household has two `MortgageRecord` entries for the same
house: Loan A balance=¥30,000,000, Loan B balance=¥30,000,000 (combined ¥60,000,000 — above the
¥45,000,000 cap).

- `MortgageRecordServiceImpl` sets `maxTaxDeductionAmount=315,000` on **both** records independently.
- Loan A: `deduction = min(30,000,000*0.007, 315,000) = 210,000`
- Loan B: `deduction = min(30,000,000*0.007, 315,000) = 210,000`
- `mortgageTaxDeductionThisYear = 420,000`

The legally correct combined deduction, capped once on the combined ¥45,000,000 balance, is
¥315,000. The simulation overstates the mortgage tax credit by ¥105,000/yr in this scenario, which
flows into `ActiveTaxCalculationEngine` and overstates `netIncome` every year this condition holds
— making the projected cash-bankruptcy year later (more optimistic) than it should be.

## Suggested direction (not required by this review, noted for scoping a fix)
Either group `MortgageRecord`s by residence (requires a new field/relationship) and cap the
combined deduction once per group, or — more simply — cap the combined balance across *all*
`taxDeductionEligible` mortgages at a single statutory limit if the product only intends to support
one qualifying residence per household.

---

## Developer Notes (Discussion & Resolution)
Status: **Proposed Mitigation - Requesting Feedback**

經過實務情境的討論與日本稅法確認，我們計畫透過簡單的資料驗證來解決此疑慮，而無需更動核心計算邏輯。考量點如下：

1. **裝潢提攜貸款的誤區**：實務上，一般的裝潢或信貸提攜貸款並不符合「住宅ローン控除」的資格。銀行核發的「年末残高証明書」只會針對符合資格的主房貸本身。因此使用者在輸入系統時，只會有一筆 `taxDeductionEligible = true` 的貸款紀錄，報告中提到的「多筆同扣除資格貸款的情境」在單人單屋的使用下不會發生。麻煩確認這個觀點是否符合日本法律規定。
2. **多人共同貸款 (ペアローン) 是獨立計算**：依據日本稅法，減稅上限是綁定「納稅義務人（個人）」而非「房屋物件」。若夫妻共同貸款買同一棟房，兩人會「各自」擁有法定的減稅額度上限（例如各 4,500 萬），並各自折抵自己的所得稅，兩人的貸款餘額本就不能合併計算。
3. **一人多屋的限制**：日本稅法嚴格規定，住宅貸款減稅僅適用於「主要的自住用住宅 (主たる居住用家屋)」。若同一個人擁有兩棟房子的貸款，也只有一棟可以申請減稅。

**結論與預計解法**：
由於本系統專注於單一家庭的個人現金流模擬，實務上只需確保系統內 `taxDeductionEligible = true` 的貸款紀錄維持**唯一**即可。我們計畫在建立/更新貸款紀錄時，加入「僅允許一筆貸款為適用減稅對象」的防呆驗證 (Validation)。只要確保這個唯一性，目前的 `MortgageCalculator` 迴圈計算邏輯就是完全正確且無溢出風險的。

---
**@Claude (Review Request):**
Based on the domain context clarified above, we plan to implement a simple validation to ensure only a single `taxDeductionEligible=true` mortgage record can exist in the system, rather than changing the calculation engine itself. This aligns with Japanese tax law (which limits the deduction to one primary residence) and avoids out-of-scope pair loan complexities. 

Do you agree that adding this uniqueness constraint is a sufficient and logical resolution for this finding? We would appreciate your feedback!
