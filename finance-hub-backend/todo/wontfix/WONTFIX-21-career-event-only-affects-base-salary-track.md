# WONTFIX 21：職涯事件（CareerEvent）只影響月薪軌，不影響獎金軌

## 判定：維持現狀，不需修正（2026-09-30，使用者確認）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java:59-133`（`calculateSalaryForYear`，職涯事件只作用在這裡計算出的月薪軌）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java:51-81, 192-194`（獎金軌只在模擬起點計算一次，之後整個模擬期間不會再變動）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/event/CareerEvent.java`、`CareerEventType.java`（事件本身沒有區分要作用在薪水還是獎金）

## 原始提案內容
使用者在「職涯事件」裡設定的薪資變動（例如模擬轉職、加薪、減薪），目前只會影響每月固定薪水這條計算軌，完全不會連動調整獎金這條軌——獎金金額是在模擬一開始就依照歷史紀錄或使用者設定算好，之後不管職涯事件怎麼變動，都會一路維持原本的獎金金額到模擬結束。原提案認為，如果使用者模擬「轉職到收入較低、沒有獎金的工作」，系統仍然會持續假設舊工作的獎金金額，導致轉職後的收入被系統性高估。

## 為何判定不是問題
獎金本質上是無法預測的——沒有人能預先知道自己（或模擬情境裡假設的職缺）未來每一年會不會發、發多少個月。要求系統在職涯事件裡「正確」推算獎金變化，等於要系統去猜測一個連現實中都無法預先得知的數字，這不是修正一個計算錯誤，而是要求系統做一件本質上做不到的事。

## 結論
維持現狀，不需修正。若使用者想模擬「轉職後獎金減少/消失」的情境，本來就可以在職涯事件發生的當年，自行調整薪資設定裡的獎金相關數字來反映這個假設，這是使用者自己就能做到的事，不需要系統額外提供自動連動機制。
