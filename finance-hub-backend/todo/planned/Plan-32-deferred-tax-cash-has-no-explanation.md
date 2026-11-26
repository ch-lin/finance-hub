# Plan 32：隔年進現金的退稅和補稅，畫面上沒有來源說明

## 這份計畫怎麼來的
複核 [issue-190](../pending%20fix/issue-190.md) 時確認：190 要改的稅額 Tooltip 不用做。191 落地後，年中退休的稅差不再寫進當年的所得稅、住民稅、社會保險欄。

同一輪確認另一件事還在：這筆錢隔年只加進現金，表格上看不出原因。房貸確定申告的退稅本來就是這樣。191 把它記成獨立功能，不擋退休計算。這份計畫只負責讓這件事不會跟著 190 的 WONTFIX 一起消失。

這不是實作規格。欄位怎麼送、說明寫在哪一列、文案怎麼寫，都還沒決定。

## 現在現金怎麼進來
[YearlySimulationPipeline.java:72-82](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java) 在隔年一開始讀 `PreviousYearTaxRecord`，把兩筆金額加進 `currentLiquidCash`，然後把這兩筆清成 0：

- `pendingKakuteiShinkokuRefund`：房貸確定申告退稅。只有大於 0 才加。
- `pendingWorkingSegmentTaxAdjustment`：年中退休、模擬起算年還有薪水時，在職段稅額和已扣繳的差額。正負都會加。正數是退稅，負數是補稅。

第二筆的計算在同檔案第 280–293 行，條件是起算年、退休年的在職段、而且 `effectiveAnnualSalary > 0`。退休月已經早於模擬當月、當年薪水是 0 時，這筆不會產生，溢繳也不會進隔年現金。那是另一個計算缺口，這份計畫不處理。

年中退休會跳過 `applyYearEndAdjustment`（同檔案第 215–217 行）。這兩筆錢都不會寫回三個稅額欄，所以 [asset-simulation-table.tsx](file://hub-ui/hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx) 的 `TaxAdjustmentCell` 看不到。

## 為什麼現在不能只改前端文字
[AssetSimulationYearlyResponse.java](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/AssetSimulationYearlyResponse.java) 沒有這兩筆金額。隔年結果只剩 `projectedLiquidCash` 這個總額。前端沒有數字可以掛說明。

兩筆錢不能共用同一句話：

- 薪資稅差是退或補，來自退休前已扣繳和在職段應繳的差。
- 房貸是確定申告退稅。

## 不要做成這些
- 不要把 [issue-190](../pending%20fix/issue-190.md) 的 `isRetired` Tooltip 分流撿回來。12 月退休仍走公司年末調整，現有稅額 Tooltip 是對的。`isRetired` 從退休那一年起每年都是 true，不是「只有退休當年」。
- 不要只在稅額欄加說明。這兩筆錢不在稅額欄。
- 不要在金額還沒送到 API 之前先寫死文案。

## 這份計畫沒有解決的部分
- 說明要掛在算出差額的那一年，還是現金進來的下一年。
- 兩筆金額是各送一個欄位，還是合成一筆再附來源。
- 薪資稅差為負（補稅、現金變少）時，說明要怎麼寫。
