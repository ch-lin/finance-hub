# Plan 03-02：扶養親屬扣除資格判定用「年齡是否達65歲」當作「所得是不是退休年金」的替身指標，但65歲以上扶養親屬完全可能仍在打工領薪資而非領年金

## 併入說明
原本獨立記錄在question-182，判定為本末倒置後併入Plan-03作為子題，question-182.md已刪除。

## 為何判定為Plan-03子題，不是獨立issue立即處理
`Dependent`資料模型本身的設計（哪些欄位該有、如何跟未來Link模式串接）在[Plan-03](Plan-03-家族財務服務與模擬器整合.md)Story 4定案前都還可能整個變動——現在就去深究「所得類型該怎麼分類、要不要新增欄位」這類細節沒有意義。等Story 4定案、家族功能的實際使用方式確定下來後，再回頭評估是否需要在`Dependent`新增所得類型欄位，並修正`isEligible`的判斷邏輯。

## 相關檔案
- [AbstractDependentDeductionStrategy.java:172-184](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/AbstractDependentDeductionStrategy.java)（`isEligible`方法，`age >= 65`時直接改用`getElderlyPensionThreshold()`）
- [Dependent.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java)（`estimatedAnnualIncome`這個欄位是單一數字，沒有任何欄位標示這筆所得的性質是薪資還是年金）
- [DependentType.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/DependentType.java)（整個enum只有`SPOUSE`、`DEPENDENT`兩種，沒有進一步的所得類型分類）

## 現狀
`AbstractDependentDeductionStrategy.isEligible`判斷一名扶養親屬的所得是否還在可以申請扶養扣除額的門檻之內時，程式碼如下：
```java
protected boolean isEligible(long income, Long age) {
    long threshold = (age != null && age >= 65) ? getElderlyPensionThreshold() : getWageIncomeThreshold();
    return income <= threshold;
}
```
這裡用`age >= 65`這一個條件，直接決定要拿「薪資所得等價門檻」還是「公的年金等控除後的等價門檻」（因為公的年金等控除的扣除額比給与所得控除更高）去跟`estimatedAnnualIncome`比較。這個判斷方式的隱含假設是：年滿65歲的扶養親屬，所得來源一定是退休年金。

但`Dependent`這個資料模型裡，`estimatedAnnualIncome`就是一個單純的數字欄位，使用者填進去的時候，系統完全不知道（也沒有欄位可以記錄）這筆錢究竟是這名親屬打工賺來的薪資、還是請領的年金——不管填的是哪一種，只要這名親屬的生日算出來年滿65歲，系統一律套用年金那一組（較寬鬆的）門檻去判斷資格。

## 為何值得日後處理
日本現實生活中，65歲以上仍在打工（部分工時、顧問、自營業等）的長輩並不罕見，這些人的所得性質是「給与所得」，應該適用一般的薪資門檻，不是公的年金等控除後的門檻——這兩組門檻的差距相當大，用錯門檻會讓一名實際所得已經超過正確（較低）門檻、本不該再適用扶養扣除的親屬，被系統誤判為「所得還在門檻內、可以扣除」，低估使用者應繳稅額。

## 建議方向（等Story 4定案後再評估）
- 在`Dependent`資料模型新增一個欄位，讓使用者標示這名扶養親屬的所得來源是薪資還是年金。
- `isEligible`改讀這個新欄位決定門檻，不再用年齡當替身指標。
