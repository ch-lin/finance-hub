# Plan 11: 個人年金保險（PENSION 類型）保費被排除在所有稅務扣除計算之外

## 嚴重程度
Medium（原始評估）→ 現改列 PLANNED（使用者暫緩，見下方狀態追蹤）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:115-157,266-297,299-339`

## 現狀
在保費彙總迴圈中（第 115-157 行）：

```java
switch (policy.getPolicyType()) {
    case LIFE, ENDOWMENT -> generalLifePremium += premium;
    case NURSING_CARE, HEALTH, CANCER, MEDICAL -> nursingCarePremium += premium;
    case EARTHQUAKE -> earthquakePremium += premium;
    default -> { } // PENSION, FIRE, AUTO, OTHER are ignored for now or handled separately
}
```

`PENSION` 類型的保費完全沒有被累加進任何一個扣除類別。

另外，`calculateRetirementTax(long retirementIncome, int contributionYears)`（第 266-297 行）與 `calculatePensionTax(long pensionIncome, int age)`（第 299-339 行）這兩個方法從頭到尾都沒有引用 `insurancePolicyRepository`，也完全沒有任何保費扣除的邏輯——保費扣除只存在於 `calculate()`（在職薪資稅務計算方法）內。

## 原因
日本稅法中的「個人年金保険料控除」是一個真實存在、獨立於一般生命保險/看護醫療保險/地震保險扣除額之外的所得稅扣除類別（在現行生命保險扣除制度下有自己獨立的上限，一般帳戶約 ¥40,000）。

目前程式碼會讓任何持有 PENSION 類型保單的使用者，在**在職期間**的稅務計算中永久漏算這筆扣除額（低估扣除、高估稅負），而在**退休/請領年金**這兩個階段，則是**完全沒有**任何保費扣除機制存在——無論在職還是退休後都會系統性高估稅負，長期累積會讓模擬出的可支配現金流被低估。

## 具體錯誤情境
使用者持有一份個人年金保險（PolicyType.PENSION），每年繳交 ¥200,000 保費。模擬引擎在計算在職期間所得稅時，這筆保費完全不會進入 `generalLifePremium`/`nursingCarePremium`/`earthquakePremium` 任何一個累加器，导致該使用者本應享有的「個人年金保険料控除」被完全忽略，模擬出的稅後可支配所得被系統性低估。退休後透過 `calculateRetirementTax`/`calculatePensionTax` 計算稅金時，同樣沒有任何保費扣除路徑存在。

## 建議修正
1. 在 switch 中新增 `PENSION` 分支，累加到一個獨立的 `individualPensionPremium` 累加器，並套用其專屬的扣除上限。
2. 在 `calculateRetirementTax`/`calculatePensionTax` 中同樣加入基於 `InsurancePolicy` 的保費扣除邏輯，讓退休/請領年金階段也能反映保費扣除的稅務影響，而不是只有在職期間的 `calculate()` 方法有此邏輯。

---
## 狀態追蹤 (2026-09-09)
**狀態：已知問題，暫緩執行 (Deferred / PLANNED)**

原始記錄於 `todo/pending fix/issue-02.md`，經使用者確認後移至本清單。使用者說明：本系統的核心用途是評估使用者自身「買房子的時候會不會現金破產」，是個人化的房貸/現金流模擬工具，並非需要覆蓋所有稅務情境的通用系統。使用者本人目前並未持有個人年金保險（個人年金保険）保單，此扣除項目與當前實際使用情境無關，暫不投入時間修正。若未來使用者本人投保個人年金保險，再回頭處理此項。
