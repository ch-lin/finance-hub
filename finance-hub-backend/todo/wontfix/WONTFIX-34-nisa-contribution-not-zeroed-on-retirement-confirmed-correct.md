# WONTFIX 34：NISA提繳計算不檢查`isRetired`，退休後不會被整年歸零——查證確認這本來就是對的，不是bug，記錄下來避免之後重複審查同一塊範圍

## 判定：WONTFIX（查證確認現狀正確，不是bug，2026-11-04）

## 這份記錄為什麼存在
這是在[question-193](../pending%20fix/issue-193.md)（原本同時記錄企業型DC、iDeCo、NISA三者在退休當年提繳計算的落差）查證真實世界制度時，順便確認的結果。企業型DC、iDeCo都確認是真正的bug，分別拆成[issue-194](../pending%20fix/issue-194.md)、[issue-193](../pending%20fix/issue-193.md)；NISA這部分查證確認現狀本來就是對的，不需要修正，記錄在這裡，避免下次review又重新懷疑、重新查證同一塊範圍。

## 查證結果：NISA提繳計算確認不受退休狀態影響，而且這是對的

查證[TaxAdvantagedAccountProjector.java:84-134](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java)（NISA提繳的`annualNisa`/`combinedAllowed`計算邏輯）確認，這整段從頭到尾沒有檢查`context.isRetired()`——跟同一個方法裡DC/iDeCo的`if (isRetired || age >= dcUnlockAge)`這類判斷（第139、143行）完全不同,NISA不會因為退休而被整年歸零。

真實制度：NISA只要求開戶者是日本境內居住的成年人（18歲以上），額度（つみたて枠每年120萬、成長枠每年240萬，生涯上限1,800萬）跟是否就業、是否退休完全無關，退休人士可以用存款或退職金隨時繼續投入。這跟現狀「不檢查`isRetired`」完全吻合——**確認NISA不是bug**。

## 跟191.md原文一段描述的落差，順便記錄更正

[question-191.md](../pending%20fix/question-191.md)第173、178行曾經把「NISA、DC、iDeCo定期定額提繳」三者放在一起描述「還停留在只認起算年這一個特例」。這句話對DC/iDeCo是準確的，**但對NISA不完全準確**——NISA確實有「只在起算年才按月折算」這個限制（跟DC/iDeCo一樣，都受[SimulationFund.java:106-109](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationFund.java)的`isInitialYear`判斷約束，`if (!isInitialYear) { return annualContribution; }`），但NISA不會因為退休而被整段歸零——DC/iDeCo是兩個限制疊加在一起（除了起算年限制，退休時還會整段歸零），NISA只有起算年那一個限制。

## 這份記錄沒有解決的部分
- 退休當年NISA提繳要不要按退休月拆分（例如退休月是6月，1-5月在職、6-12月退休，要不要讓NISA提繳只算1-5月或只算6-12月）——這點查證確認**不是法規限制或bug**，是使用者現金流配置的產品設計選擇（例如退休後沒有薪資收入，要不要維持原本的定期定額扣款設定）。這份記錄只確認「不會整年歸零」這個現狀是對的，不代表NISA的月份拆分精度沒有改進空間，但那是一個獨立的產品功能決定，不屬於這份WONTFIX記錄的bug判定範圍，如果之後要做，應該開一份新的feature-requirement或question，不要跟這份WONTFIX混在一起。
