# [Fixed] Issue 105：除了房貸以外，銀行現金、NISA、DC/iDeCo、公司股票、年金的模擬起點，一律直接採信最新一次匯入的數字，完全沒有考慮這筆資料實際上是多久以前的

## 這份issue的方向已經被更根本的決定取代
使用者原本在這份issue裡決定「NISA、iDeCo可以補算到今天，其他資產維持現狀不補算、只顯示新鮮度提示」，後來經過與Gemini的深入討論（討論過程記錄在下方「歷史討論記錄」區塊，保留供回溯參考），這個「補算」方向被使用者自己徹底推翻，改成一個更根本的原則：**系統不做任何時間推算，一律把使用者最後一次上傳的那筆快照，當作模擬要開始的那個時間點本身**。這個原則不只適用於這份issue原本討論的六種資產，連房貸現有運作中的補算機制也要一併移除。最終定案方向與完整理由，記錄在[feature-requirement-132](feature-requirement-132.md)，請以該文件為準。

## 這份issue現在只保留一件事：資產資料新鮮度提示
原本「NISA、iDeCo新增補算邏輯」這個方向已經撤銷，不要實作。這份issue唯一還有效的部分，是「在畫面上顯示每一筆資產快照的日期，讓使用者自己判斷要不要先重新匯入最新資料」這個提示功能——這部分需求維持不變，詳見[feature-requirement-132](feature-requirement-132.md)裡「想要的行為」第2點。

## 相關檔案（僅限資料新鮮度提示這部分，不含任何補算邏輯）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java:190-199`（銀行現金快照日期）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java:48-108`（NISA快照日期）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java:46-192`（DC/iDeCo快照日期）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/PensionContextBuilder.java:42-64`（年金快照日期）
- 公司股票/E-Trade快照日期（`SimulationContextFactory.java:184-188`）

## 修正方向
不補算任何數字，只在畫面上明確顯示「這筆資料距今已經OO天/月」，並提示使用者「這段期間的變動系統無法掌握，建議重新匯入後再跑模擬」。具體提示文案、顯示位置（例如放在Sandbox頁面哪個角落、還是每個資產卡片各自顯示自己的新鮮度），屬於前端UX設計細節，實作時可自行決定。

## 歷史討論記錄（僅供回溯參考，不代表最終方向）

這個區塊完整保留原本的討論過程，說明「補算」這個方向是如何被一步步推翻的，但下方內容已經不是最終決定，請勿依照這裡的技術細節去實作任何補算邏輯。

---

### 原始方向（已撤銷）：NISA、iDeCo各自獨立補算到今天

把六種資產分成「可以補算」跟「不能補算、只顯示提示」兩組，判斷標準原本是：「這筆錢的變化，是不是100%可以從系統已知的固定條件推算出來，不涉及任何系統無法預知的人為決定」。NISA、iDeCo原本被認為可以補算，理由是定期定額扣款金額系統本來就有記錄在案（`NisaContextBuilder.java:119-124`查詢`NisaContributionSetting`表、`DefinedContributionContextBuilder.java:75,144`讀取`DefinedContributionAccountSnapshot`裡每筆基金的`monthlyContributionAmount`）。

### 推翻過程：Gemini提出的「單邊時間旅行」質疑

使用者在確認這個方向之後，進一步追問：「補算本身可能會違反事實……除非銀行也補算，不然無意義。又或者退求其次，銀行需要看是到幾月，那就從那個幾月開始補算到現在。不然100%會違反事實。」

Gemini查證後確認，現有房貸補算機制`fastForwardMortgagesToPresent`（`MortgageCalculator.java:133-166`）補算時只會減少房貸本金，完全沒有同步扣除`context.getCurrentLiquidCash()`（銀行現金）——這代表只要銀行最新交易日不是「今天」，房貸補算每扣一個月本金，系統就等同憑空幫使用者繳了一個月房貸，虛增等額淨資產。同樣的邏輯漏洞，如果NISA/iDeCo也採用單邊補算，會在「下載日跟匯入日的時勢錯位」、「資金從銀行移到證券商不代表當月已經成功買入」這兩種情境下，產生Double Counting（雙重計入）或Ghost Money（憑空印鈔）。

### 使用者進一步補充的理由（促成最終決定）

使用者在這之後提出了比「單邊時間旅行」更根本的理由，促成了feature-requirement-132的最終決定：
1. **責任歸屬**：系統用公式推算一段沒有依據的事實，算錯了責任在開發者身上；這跟「AI會出錯，使用者能接受」是不同等級的風險。
2. **連補算起點本身都無法可靠判斷**：檔案內文沒有時間欄位的資料，沒辦法只靠檔案時間戳記判斷真實時間點；使用者可能分好幾天下載不同資料，難以確定該用哪一個當基準。
3. **這個工具不是對帳軟體**：不需要讓銀行跟NISA/iDeCo的時間點互相校準，只需要在使用者按下模擬那一刻，誠實地用他當下提供的資料去推算未來。

這三點理由，不只推翻了NISA/iDeCo補算，也連帶推翻了現有房貸補算機制本身的正當性，才有了「Feature Requirement 132：全面廢除「補算到現在」機制，模擬起點改成「使用者最後一次上傳快照的那個時間點」本身，不再嘗試推算到今天」「Fixed-132」這個最終範圍更大的決定。
