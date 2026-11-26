# Fixed 88：模擬頁面進階覆寫的NISA基金會重複列出歷史紀錄，調整舊紀錄悄悄不生效

## 查證說明（2026-09-15，回覆「service那邊不是有排序了嗎」的質疑）
已重新查證後端NISA相關的每一層程式碼，確認排序確實存在，但排序不等於篩選最新一批——原本的bug報告仍然成立。

前端 `fetchFunds()` 呼叫的 `/api/finance/assets/nisa` 這個API，最終會走到 `NisaSnapshotRecordServiceImpl.getAllRecords()`（第45-51行），這裡呼叫的是 `nisaRepository.findAllByOrderByRecordDateDesc()`——這確實是一段真實存在的排序邏輯，會把所有歷史快照依日期新到舊排好。但問題在於，這是 `findAllBy...`，不是 `findFirstBy...`，也不是任何有日期範圍限制的查詢——它排序的對象是「全部批次的每一筆歷史紀錄」，排完序之後，還是會把每一批（1月、2月、3月……）全部一起回傳給前端，只是回傳順序變成新到舊排好而已。同一筆基金如果有5個月的歷史紀錄，這5筆全部都會回傳，只是彼此之間依日期排列整齊——排序沒有做到「只留最新一批、把其他批次篩掉」這件事，這正是原始報告裡「畫面上出現好幾張標籤相同的卡片」這個現象的根本原因。

相對地，模擬引擎實際使用的路徑（`NisaContextBuilder.initializeNisaContext()` 第52行、第57行）用的是 `findFirstByOrderByRecordDateDesc()` 先找出最新日期，再用 `findByRecordDate(...)` 只抓那一天的紀錄——這才是真正「只留最新一批」的寫法，跟前端／控制器那條路徑用的排序邏輯是兩套完全不同的查詢。也就是說，「service那邊有排序」這句話沒有錯，但那個排序是給前端顯示API用的、只排序不篩選，跟模擬引擎自己另外用的、真正篩選最新批次的查詢是兩條不同的路徑——排序的存在並不能防止本問題描述的「前端重複列出歷史卡片、編輯舊卡片悄悄不生效」的情況，原始報告的判定維持不變。

---

## 嚴重程度
High——這個「進階覆寫」功能的整個存在目的，就是讓使用者調整NISA報酬率／每月投入金額等假設，觀察對模擬結果的影響。如果使用者剛好編輯到一筆非最新的歷史紀錄卡片，這次調整會被悄悄忽略、完全沒有任何錯誤或提示，使用者可能會因此對「NISA報酬率變化到底會不會影響我的資產」得出完全錯誤的結論，直接影響這個工具存在的核心目的——協助判斷房貸／現金流決策。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/simulation/components/simulation-override-panel.tsx`（第38-81行：`fetchFunds()`，把所有歷史NISA快照紀錄全部映射成卡片，跟DC/iDeCo正確篩選成只顯示最新一筆的作法不同；第484-542行：「單筆基金覆寫」卡片渲染，只顯示帳戶類型跟基金名稱，沒有日期，也沒有「目前生效中」的標示）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/api/nisa/NisaSnapshotRecordController.java`（第81-86行：`getAllRecords()`，後端API會回傳所有歷史快照，不只是最新一筆）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/context/NisaContextBuilder.java`（第52-57行、第119-127行：模擬引擎只會透過 `findFirstByOrderByRecordDateDesc()` 加上 `findByRecordDate(...)` 讀取最新那一批快照，並用這批紀錄自己的資料庫id當作 `fundId`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimization/FundOverrideApplier.java`（第152-161行：覆寫比對邏輯，用「帳戶類型＋這個id」去比對，比對不到就直接 `continue`，靜靜跳過、不會有任何錯誤）

## 現狀
使用者在「進階覆寫」畫面裡，可以針對個別NISA基金調整報酬率、每月投入金額等假設，觀察會如何影響模擬結果。前端的 `fetchFunds()` 呼叫 `/api/finance/assets/nisa` 這個API，這個API會回傳「所有歷史快照紀錄」（依日期排序）。目前程式碼直接把整個陣列映射成卡片（第60-69行），完全沒有篩選只保留最新日期的那一批——相對地，同一個函式處理DC/iDeCo資料的部分，確實有正確地`.find()`只取最新一筆（因為 `/assets/dc` 的API同樣是依日期新到舊排序），只有NISA這個分支漏了同樣的篩選步驟，看起來像是單純的疏漏，不是刻意設計。

後端模擬引擎（`NisaContextBuilder.java`）永遠只使用最新日期那一批快照，並用那一批資料庫紀錄自己的id當作 `fundId`。覆寫比對邏輯（`FundOverrideApplier.java`）用「帳戶類型＋這個id」的組合去比對；如果找不到符合的（`ov == null`），就直接 `continue` 跳過——沒有任何錯誤或警告。所以，如果使用者編輯的卡片，其底層id屬於某個較舊（非最新）的快照批次，這個id永遠不會跟模擬引擎目前實際使用的id對上，這筆覆寫就會完全消失，畫面上沒有任何提示。

## 具體錯誤情境
使用者每個月都匯入同一筆基金（つみたて枠「eMAXIS Slim全世界株式」）的持股資料，資料庫裡累積了1月、2月、3月……每個月各一筆快照。使用者打開「進階覆寫」，想測試「如果這筆基金報酬率是8%會怎樣」。畫面上的「單筆基金覆寫」清單裡，出現好幾張標籤完全相同、都寫著「つみたて／eMAXIS Slim全世界株式」的卡片（沒有日期可以區分），使用者隨手點了其中一張（其實是2月的快照）並把報酬率改成8%。輸入欄位顯示這次修改被接受了，但重新跑模擬後，資產成長曲線完全沒有變化——因為模擬引擎目前只認得3月（最新）批次的id，使用者編輯的（2月的）id完全對不上，整筆覆寫就這樣被靜靜丟棄。使用者可能會因此誤以為「這筆基金的報酬率對我的資產影響很小」，但其實他的修改從來沒有真正生效過。

## 建議修正
以下三個方向各有取捨：

**方向A：只改前端——在 `fetchFunds()` 裡，把NISA快照依日期分組，只保留最新那一組再渲染，跟現有DC分支的作法一致**
- 優點：改動最小，直接解決「編輯不生效」的問題，而且跟模擬引擎實際使用的資料完全對齊，不會再有「卡片顯示的」跟「引擎實際讀的」不一致的情況。
- 缺點：如果未來有需要讓使用者瀏覽／比較歷史快照（例如查看某筆基金6個月前的餘額），這個做法會讓歷史卡片整個從清單裡消失，需要另外設計一個瀏覽歷史紀錄的獨立入口。

**方向B：保留歷史紀錄可見，但加上日期標籤跟「目前生效中」的標示，或是讓非最新的紀錄輸入欄位變成不可編輯／鎖定**
- 優點：既保留瀏覽歷史快照的能力，又能解決「分不清哪一筆才是真正生效」的核心問題。
- 缺點：需要額外的UI設計工作（例如「目前生效」徽章、鎖定非最新卡片的互動邏輯），改動範圍比方向A大，而且隨著歷史紀錄累積越多，清單本身也可能越來越長、越難操作。

**方向C：把 `FundOverrideApplier.java` 裡覆寫比對用的key，從「快照紀錄的資料庫id」改成更穩定的「基金名稱＋帳戶類型」組合**
- 優點：最根本的修法——不論使用者點的是哪一張歷史卡片，只要基金名稱對得上，覆寫就能正確套用到模擬引擎目前實際使用的那筆基金上，不再依賴「id剛好對得上」這個脆弱的前提，也不用犧牲瀏覽歷史紀錄的能力。
- 缺點：需要同時調整前後端的比對邏輯與資料結構，改動範圍最大，還需要額外處理「同名但帳戶類型不同」或「基金改名」等邊界情況，實作與測試成本較高。

## 與已知項目的區別
`todo/planned/` 與 `todo/wontfix/` 都沒有提到「進階覆寫面板的NISA基金清單重複列出歷史紀錄，編輯舊紀錄會靜靜失效」的問題。`FINDING-27-nisa-goal-solver-frontend-bounds-decorative.md` 討論的是另一個已經計劃移除的元件（`nisa-goal-solver-card.tsx`）的數值範圍驗證失效問題——是「數值範圍驗證」層面的缺陷，跟本問題「覆寫比對用的key（fundId）在有多筆歷史紀錄時會失效、悄悄丟棄覆寫」完全不是同一個檔案、也不是同一條資料流程，不是重複報告。屬於全新發現的問題。

## 實作報告 (2026-09-15)
- **實作方向**：採用方向 A (只改前端)。
- **修改檔案**：[`simulation-override-panel.tsx`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx)
- **變更內容**：在 `fetchFunds()` 處理 `nisaSnapshots` 時，增加了過濾邏輯。抓出陣列第一筆（也就是最新日期）的 `recordDate`，並透過 `.filter` 只保留該日期的快照，再進行後續的 `map` 轉換。
- **結果**：前端 UI 的 NISA 基金列表現在只會渲染出最新一批的卡片（與現有的 DC/iDeCo 邏輯一致）。這確保了使用者編輯的 `fundId` 永遠會和後端模擬引擎所載入的最新 `fundId` 完全對齊，徹底解決了「編輯舊紀錄導致覆寫靜默失效」的問題。
