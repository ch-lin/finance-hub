# WONTFIX 10：新增小孩的學制起算日預設值錯設成9月，導致部分小孩教育費試算整段年齡算錯一年

## 判定：WONTFIX（2026-09-15）
**理由**：使用者確認，`todo/planned/Plan-01.md` 會在這次review後幾個小時內做完。Plan-01第4階段會把本問題涉及的整套舊機制——`EducationExpenseCalculatorOld`、`ChildProfile`裡三層寫死9月1日的預設值、前端新增小孩表單的對應欄位——全部刪除，改用新架構下每個學制軌道（`TrackConfig`）各自定義自己的`startMonth`。新架構這種「每個軌道各自設定起算月份」的設計，本質上就不會再出現「單一全域欄位被硬編碼成9月」這一整類問題——不是新架構剛好沒踩到这個bug，而是这个bug所依賴的「單一全域9月1日欄位」這個東西，在新架構裡已經不存在了。

現在去修一套幾小時後就會被整段刪除的舊程式碼，沒有實際效益，因此標記WONTFIX。

**後續留意事項（非本問題範圍，供將來核對用）**：新架構下`TrackConfig.startMonth`目前沒有Java內部預設值（見對TrackConfig的討論），日本國內學制（幼稚園／小學／中學／高中的`publicTrack`／`privateTrack`）真正該用的起算月份是4月，不是9月。等Plan-01第2、3階段真正把「誰來設定這個值」的邏輯（例如controller驗證、預設值seeding）做出來之後，建議額外確認一下：日本國內學制軌道拿到的`startMonth`實際上是不是4月，避免同一個「預設成9月」的錯誤換了個位置重新發生。目前這部分程式碼還沒寫出來，無法查證，不能算是現在的issue。

---

## 查證說明（2026-09-15，訂正：上一版誤讀了使用者原話）
上一版這裡寫的是「查證EducationExpenseCalculatorOld是不是現在只是參考用的東西」，但這是我自己誤讀出來的一個假設——使用者原話講的是「看現在還沒做完的plan-01，因此刪掉EducationExpenseCalculatorOld會在新版做完直接棄用」，本來就已經清楚寫明Plan-01還沒做完、刪除是「新版做完之後」才會發生的未來動作，並不是在宣稱`EducationExpenseCalculatorOld`現在已經是參考用的死碼。我把一個她從未主張過的前提當成她的主張去「糾正」，是我自己沒有把原話逐字讀完，在此訂正、撤回上一版的查證框架。

實際查證結果（跟使用者原話一致，不是新發現）：`todo/planned/Plan-01.md` 目前只完成到第1階段（新增 `EducationConfig`／`ChildEducationSettings` 等新的設定資料結構），第1階段明確寫著「完全不動到舊的 `FinanceHubConfig`」、「不介入計算邏輯的修改」；真正會取代 `EducationExpenseCalculatorOld` 計算邏輯的3-Step Pipeline（第2階段）、以及最後刪除舊邏輯的清理工作（第4階段），都還停在「未來計畫」，沒有排定時間。這也代表：`EducationExpenseCalculatorOld` 目前仍是核心模擬引擎唯一在用的計算路徑（`IncomeExpenseCalculator.java` 第58行、`ChildProfileServiceImpl.java` 第41、53行都直接依賴它），本問題（9月1日預設值bug）現在確實會影響到每一年的模擬結果。

是否要在Plan-01做到第4階段、把舊邏輯整批換掉之前，先單獨修這個9月1日的預設值bug，還是就跟著Plan-01一起處理，這個時程判斷交給您決定。

---

## 嚴重程度
High——這不是邊界情境才會發生的問題，而是「完全沒有選任何海外學制」的最典型、最常見的小孩資料，都會因為這個錯誤的預設值，讓整個求學階段（幼稚園到大學）的學年年齡全部算錯一年，而且因為這個欄位只在「選了海外學制」時才會顯示，使用者完全沒有機會發現或修正。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/domain/family/ChildProfile.java`（第62-68行：資料庫實體裡，`intlSchoolCutoffMonth`／`intlSchoolCutoffDay` 硬編碼預設值為9月1日）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/api/family/CreateChildProfileRequest.java`（第47-50行：API的請求物件，同樣硬編碼9月1日）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/CreateChildProfileCommand.java`（第40-43行：後端指令物件，同樣硬編碼9月1日）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/family/ChildProfileServiceImpl.java`（第85-86行：直接把這個值原樣存進資料庫）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/EducationExpenseCalculatorOld.java`（第70-89行：`calculateAcademicAge()`——這裡其實已經有正確的邏輯：當這個值是null時，會改用日本真實的4月1日學年制去換算，但因為這個值從來不是null，這段正確的備援邏輯永遠不會被觸發）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/IncomeExpenseCalculator.java`（第221行：核心模擬引擎每一年都會呼叫這個學年年齡計算——確認這不只是輔助圖表，而是核心模擬會用到的計算）
- `hub-ui/hub-ui/src/app/finance/family/components/child-profile-section.tsx`（第34-35行：前端新增小孩表單，同樣硬編碼9/1；第175-178行、第301-304行：判斷要不要顯示「學制起算日」欄位的邏輯，只檢查了幼稚園／小學／中學／高中這幾個階段有沒有選海外學制，漏掉了大學階段——而大學階段同樣有英國／加拿大／台灣／澳洲這4個海外選項）
- `hub-ui/hub-ui/src/app/finance/family/hooks/use-child-profile.ts`（前端型別定義）

## 現狀
使用者在系統裡新增一名小孩的資料時，即使這名小孩從幼稚園到大學完全都念日本國內學校（完全沒有勾選任何海外學制選項），系統仍然會在後端把「國際學制起算日」這個欄位，預設寫入「9月1日」。

這個「學年起算日」的值，會被 `EducationExpenseCalculatorOld.calculateAcademicAge()` 拿來計算「這名小孩在模擬的某一年，實際上是幾歲的學年」，而這個學年年齡，會決定 `calculateAnnualCost` 判斷這名小孩「今年應該適用5個學費級距裡的哪一個」。核心模擬引擎 `IncomeExpenseCalculator.java` 每年都會呼叫這個計算，代表這不只是某個輔助性的顯示圖表，而是會直接影響每一年模擬結果的核心計算。

## 原因
前端表單、後端API的請求物件、後端的指令物件——這三層各自獨立硬編碼了9月1日這個預設值，即使小孩完全是純日本國內學制。日本真實的學年制度，是以4月1日為起算基準，不是9月1日（9月1日是歐美／部分國際學校常見的學年起算日）。

`EducationExpenseCalculatorOld.calculateAcademicAge()` 裡其實已經正確寫好了「當這個值是null時，改用4月1日真正的日本學年制去換算」的備援邏輯——但因為前端與後端這三層都會把這個欄位「主動填上9月1日」而不是留白（null），這個原本正確的備援邏輯，實際上永遠沒有機會被觸發。

另外，前端判斷「要不要顯示這個學制起算日欄位讓使用者調整」的邏輯，只檢查了幼稚園／小學／中學／高中這4個階段有沒有選擇海外學制選項，完全沒有檢查大學階段——但大學階段同樣有英國／加拿大／台灣／澳洲這4個海外選項可以選。這代表：如果一名小孩「只有」大學打算念海外學校，其他階段都念日本國內學校，這個欄位甚至連在畫面上出現、讓使用者有機會發現或修正的機會都沒有。

## 具體錯誤情境
一名小孩生日是2018年7月1日，從小到大完全念日本國內學校（沒有勾選任何海外選項）。如果用系統目前錯誤套用的9月1日起算：這名小孩的「學年出生年」會被算成2018年，換算下來在2024年的學年年齡是6歲。但如果依照日本真實的4月1日學年制去換算：這名小孩的「學年出生年」應該算成2019年，2024年的正確學年年齡應該是5歲。

這一整年的落差，會一路延續影響這名小孩從幼稚園到大學「整段求學生涯」的每一個階段起訖年份，全部提早了一年——包括大學最後一年的學費，可能因此被少算甚至完全漏算。這個落差會逐年疊加進入這名小孩整段求學期間的每一年現金流／破產模擬結果裡。而因為這名小孩完全沒有任何海外學制，這個「學制起算日」欄位在畫面上根本不會出現，使用者完全沒有任何機會發現或自行修正這個錯誤。

## 建議修正
以下三個方向的取捨：

**方向A：把前端表單、後端API請求物件、後端指令物件這三層的硬編碼預設值，統一從9月1日改成日本真實的4月1日**
- 優點：改動範圍小、風險低。
- 缺點：無法處理「同一名小孩不同求學階段分別採用不同起算月份」（例如小學念日本4月制、之後轉念海外9月制學校）這種混合情境——不過這個更大範圍的限制，已經被規劃在未來的 `todo/planned/Plan-01.md` 重構計畫裡（該計畫打算讓每個求學階段可以各自設定起算日），所以這個方向可以視為在那個更大重構完成前的過渡性修正。

**方向B：三層都不要填任何預設值，讓欄位保持留白（null），讓 `EducationExpenseCalculatorOld.java` 裡「已經寫好、但目前從未被觸發」的4月1日備援邏輯自動生效**
- 優點：完全不需要改動任何計算邏輯本身。
- 缺點：這樣一來，如果小孩真的有海外學制、但使用者當時忘了手動填這個欄位，問題會反過來發生在這些真正需要海外起算日的小孩身上——這只是把問題從「日本國內小孩」轉移到「使用者忘記填的海外小孩」身上，並沒有真正解決問題，需要額外確認這個取捨是否可以接受。

**方向C：不論選方向A或B，都額外修正前端「要不要顯示這個欄位」的判斷邏輯，把「大學階段選了海外選項」也納入觸發顯示的條件**
- 優點：確保所有真正有海外學制需求的小孩，都至少有機會在畫面上看到並調整這個欄位。
- 缺點：改動範圍比單獨A或B更大，需要額外的前端條件判斷邏輯調整。

不論最終選擇哪個方向，都建議額外評估：資料庫裡「既有」的小孩資料，如果本身完全是純日本國內學制、但目前已經被存成9月1日，是否需要一次性的資料修正——單純修正「未來新增小孩」的預設值，並不會修正這些既存資料。

## 與已知項目的區別
本問題跟 `todo/planned/Plan-01.md`（討論的是同一名小孩「在求學過程中從一種學制切換到另一種學制」時，切換當年學費算錯，以及大學最後幾個月學費漏算的問題）明確不同——Plan-01討論的問題，前提是小孩必須「經歷過學制切換」才會發生；而本問題（issue-43）完全不需要任何切換，即使小孩從出生到大學畢業都只用「單一」日本國內學制，也一樣會因為這個錯誤的預設值，讓整段學年年齡都被平移一年。兩者的成因跟影響範圍都不同，不是重複報告。已檢查 `todo/planned/` 與 `todo/wontfix/` 其他項目，都沒有提到這個問題，屬於全新發現的問題。
