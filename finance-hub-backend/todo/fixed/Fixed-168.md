# [FIXED] Question 168：Grid Search跟GA兩個最佳化引擎，對「房貸最早可以哪一年還清」這個搜尋範圍的下限，相差一年——不確定哪一個才是真正想要的邊界

## 這份question怎麼被發現的
這是這次Source Code Review針對GA/Grid Search最佳化引擎進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java:21`（Grid Search用的`calculateMortgageYearRange`：`minYear = currentYear`，代表Grid Search可以提案「今年12月就把房貸還清」這個選項）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java:154`（GA的`mutate()`：`minRepayYear = currentYear + 1`，GA隨機產生的提前還款候選年份，只會從明年開始，不包含今年）

## 現狀（不涉及爭議的部分，純粹讀程式碼得到的事實）
兩個引擎理論上是在搜尋同一個維度（房貸提前還款的年份），但下限相差一年：Grid Search允許今年，GA只允許明年起。兩者都不會真的崩潰或出錯——「今年12月還清」仍然是一個相對模擬當下而言合理的未來月份，所以這個差異不會造成任何程式錯誤，純粹是兩個引擎對同一個使用者意圖，實際搜尋到的範圍不完全一樣。

## 不確定、需要討論才能回答的地方
這兩個引擎的搜尋範圍下限，哪一個才是真正想要的？如果答案是「今年也該算進去」，GA目前排除了這個選項，等於GA搜尋到的最佳解可能錯過一個理論上更好的「今年就還清」選項；如果答案是「至少要明年才合理」(例如考慮到使用者需要時間準備資金)，Grid Search目前多提供了一個不該出現的選項。這兩種可能性都說得通，不應該由實作者自行猜測決定。

## 需要查證的問題
「房貸最佳化提前還款年份」這個搜尋維度，下限應該是今年還是明年？

## 不建議的處理方式
不建議直接把其中一個引擎的下限改去配合另一個，而不先確認哪一個下限才是真正想要的行為——這兩個引擎目前都不是明顯錯誤，只是彼此不一致，貿然統一有可能是往錯的方向統一。

## 產品經理與架構決策（2026-10-13）
- **定案決策**：**統一為「今年（`currentYear`）」**。
- **決策理由與業務考量**：
  1. **年度結算點一致性**：模擬系統的年度財務計算是以每年 12 月底為結算點。若使用者手頭流動資產或積蓄充裕，「今年底結清房貸」是一個合理、合法且節省利息最大化的財務決策。
  2. **搜尋空間對齊**：Grid Search 原本即以 `currentYear` 作為搜尋下限（[OptimizationUtils.java:21](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/OptimizationUtils.java#L21)），GA 引擎應與之保持 100% 一致，避免 GA 因硬編碼 `currentYear + 1` 而平白錯失「今年結清」的全域最佳解。
- **實作改動**：
  - 修改 [Chromosome.java:157](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java#L157)，將 `minRepayYear = currentYear + 1;` 改為 `minRepayYear = currentYear;`。
  - 在 `ChromosomeTest` 增加對應之單元測試以確保邊界保護。
- **狀態**：**已完成修正（Fixed / Implemented）**。

