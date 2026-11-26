# Fixed 63：退休年金社會保險費估算比例，程式碼用9.5%、註解寫「約5%」，兩者互相矛盾（原question-12）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java`（第324-325行）

## 現狀
```java
// Estimate Social Insurance for retirees (approx 5% of pension income)
long socialInsurance = (long) (pensionIncome * 0.095);
```
註解寫「約5%」，程式碼實際使用9.5%，兩者互相矛盾。

## 原因
查證確認日本並沒有全國統一的單一社會保險費比例，退休後年金收入的實際負擔比例依地區、所得級距而不同。查證結果顯示，實務上估算稅金加社會保險費合計扣除大約落在10%~15%之間，9.5%屬於相對合理的粗估值；相對地，5%偏低，通常只有所得非常低的級距才會落在這個範圍。既然沒有唯一「正確」的全國標準值可以拿來當基準，比較合理的作法是讓註解跟實際使用的數字一致，而不是去改動已經在用、相對合理的9.5%這個計算值。

## 建議修正
把第324行的註解從「approx 5%」改成「approx 9.5%」，程式碼本身的`0.095`不需要更動。這是純粹的註解文字修正，不影響任何計算結果。
