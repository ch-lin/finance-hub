# [FIXED] Issue 148：使用者現在的年齡一旦超過DC/iDeCo的解鎖年齡，模擬第一年會立刻把整筆餘額當成「今天解鎖」去課稅跟發放現金，不管這筆錢是不是早就已經在現實中被領走或還想繼續放著

## 這份issue怎麼被發現的
這是這次Source Code Review針對NISA/DC/iDeCo投資帳戶模擬引擎進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java:233-274`（`dcUnlocked`/`idecoUnlocked`判斷：`(age == taxAdvantagedWithdrawalAge) || (age == initialAge && age > taxAdvantagedWithdrawalAge)`，後面這個「追補」分支就是這份issue討論的對象）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java:46-93,121-166`（DC/iDeCo餘額跟年資一律只從「最新一筆快照」取得，沒有任何「這個帳戶是否已經結清/是否還開著」的概念）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/utils/SimulationParameterResolver.java:144-154`（`resolveTaxAdvantagedWithdrawalAge`，使用者沒有特別設定`taxAdvantagedWithdrawalAge`時，預設值是法定最低解鎖年齡60歲）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java:130`（`taxAdvantagedWithdrawalAge`欄位本身可以是null，從頭到尾沒有任何地方幫使用者補上一個預設值去代表「我打算放到幾歲」）
- 已確認全專案沒有任何「帳戶已結清」、「已提領」、「rollover」相關的欄位或機制（grep全repo確認）

## 現狀
模擬引擎判斷DC/iDeCo該不該在「今年」觸發解鎖請領（連同一次性退職所得稅計算、整筆現金入帳），除了「模擬跑到真的到達解鎖年齡那一年」這個正常分支之外，還有一個「追補」分支：如果使用者現在的實際年齡，在模擬開始的那一刻就已經超過解鎖年齡，系統會在模擬第一年立刻觸發這個解鎖事件——用的是「最新一次匯入的帳戈快照」裡的餘額跟年資。這個最新快照，完全沒有任何機制去區分「這個帳戶現在還開著、餘額正確」跟「這個帳戶其實已經結清、這筆錢早就不在這裡了，只是資料庫裡最後一筆記錄還留著舊的餘額」。

## 為何是問題
使用者在65歲時，名下有一筆DC帳戈，最後一次匯入快照是3年前(當時62歲)，顯示餘額800萬円——但實際上使用者在62歲那年已經把這筆錢真的領出來了(現實中已經花掉或存進銀行帳戶)，只是當時沒有想到要去刪除或更新這筆舊快照。使用者也從來沒有特別去設定`taxAdvantagedWithdrawalAge`這個欄位(這是一個很容易被忽略、不去碰的進階設定)。只要跑模擬：

- 預設`taxAdvantagedWithdrawalAge = 60`(法定最低解鎖年齡),現在年齡65歲。
- 模擬第一年：`(65==60) 不成立，但(65==65 且 65>60) 成立`，立刻觸發解鎖。
- 系統把這筆早已不存在的800萬円，當作「現在」才解鎖領出，整筆退職所得稅計算+現金入帳，灌進模擬第一年的流動現金裡。

這筆錢可能已經同時被算進使用者的銀行餘額裡(現實中真的領出來存進銀行、系統另外從銀行交易紀錄抓到這筆餘額)——等於同一筆錢被系統憑空多算了一次。即使帳戶真的還開著、餘額真的還在，使用者可能根本沒有打算在65歲就解鎖(只是忘記去設定`taxAdvantagedWithdrawalAge`反映自己真正的打算，例如想放到70歲)，系統卻會不打一聲招呼地強制觸發一次使用者可能根本不想要的課稅提領事件，中斷掉本來還可以繼續延稅成長的機會。

這條路徑目前完全沒有任何單元測試覆蓋(現有測試`shouldUnlockDcAndIdecoAtWithdrawalAge`用的是`initialAge=58 < withdrawalAge=60`，只測到「未來才會到達解鎖年齡」這個正常分支，沒有測到「現在已經超過」這個追補分支)。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：新增一個明確的「這筆帳戶已經在現實中被領出」開關**
- 做法：仿照既有的「年資覆寫」開關模式，新增一個布林值設定，讓使用者可以主動告訴系統「這筆帳戶的最新快照已經過時、實際上已經結清」，關閉這個追補解鎖分支。
- 優點：改動小、明確，符合現有的覆寫開關慣例。
- 缺點：使用者要先意識到有這個落差才會去設定，在他發現問題之前，系統預設行為仍然是錯的。

**方向B：改變`taxAdvantagedWithdrawalAge`為null時的預設行為**
- 做法：「使用者從沒設定過」不要自動代表「套用法定最低年齡」，而是代表「不要觸發強制解鎖事件」。
- 優點：讓系統的預設行為從「主動假設一個重大課稅事件」改成比較保守、不隨便做決定的方向。
- 缺點：會改變現有依賴這個預設值運作的使用者的行為；而且就算修正了這一半，餘額本身是否過時這個子情境仍然沒有被解決。

**方向C：針對快照過舊的情況加上警示**
- 做法：如果最新一筆DC/iDeCo快照的記錄日期，距離「即將觸發解鎖事件」的時間點已經相隔一段時間，在跑模擬前要求使用者確認這筆資料還是最新的。
- 優點：直接針對「資料是否過時」這個根本原因處理。
- 缺點：需要額外的UI/UX工作；「多久算過舊」這個門檻本身也需要產品判斷，訂得不好可能對正常只是比較少重新匯入的使用者造成不必要的誤判。

**方向D：縮小修正範圍，只處理「追補」這一個特定分支**
- 做法：只針對`age == initialAge && age > taxAdvantagedWithdrawalAge`這個特定情境，要求使用者明確確認一次，不去動到「模擬跑到未來真的到達解鎖年齡」這個已經驗證沒問題的正常分支。
- 優點：影響範圍最小，只處理這個特定的邊角情境。
- 缺點：又多一個需要維護的明確確認流程。

---

## Question：對此提案的架構審查與深度質疑

針對 Claude 在本 Issue 提出的現象與四個方向，Gemini 與 Product Manager（Human）進行了深度商業邏輯與架構審查，提出以下根本性質疑：

### 一、系統核心定位之混淆：資產模擬引擎 vs. 稽核查帳軟體

Claude 在本 Issue 中的立論前提是：
> *「使用者在 65 歲時，名下有一筆 DC 帳戶，最後一次匯入快照是 3 年前（62 歲有 800 萬），但實際上使用者在 62 歲已經把錢領出花掉了，只是沒有刪除舊快照……系統竟然在模擬第一年把這筆錢當真領出課稅，灌進現金。」*

這個立論完全混淆了系統的權責邊界，並踩到了專案歷史決策的紅線：
1. **快照是唯一的現狀事實基準（Single Source of Truth at T=0）**：
   在 [Fixed-105](../fixed/Fixed-105.md)、[Fixed-132](../fixed/Fixed-132.md) 以及 [WONTFIX-23](../wontfix/WONTFIX-23-mortgage-reimport-fact-fields-no-fallback.md) 中早已確立鐵律：**「模擬引擎是從使用者當前匯入的最新快照（Snapshot）起算，模擬未來 30~50 年的財務生命週期，絕不向過去推算，也絕不去通靈質疑使用者的現實真偽」**。
2. **資料維護責任在使用者，非模擬引擎**：
   若使用者在現實中已結清銷戶，其責任是於系統中將該帳戶刪除或更新餘額為 0。
   如果模擬引擎因為「這筆資料是 3 年前匯入的」就懷疑「現實中會不會早就花掉了」，那麼使用者的銀行存款、證券持股、NISA 是否全都要懷疑「你昨天是不是在現實中賭博輸光了」而通通不敢計算？把一個資產投影引擎搞成通靈查帳軟體，這到底是誰在定位系統功能？

---

### 二、現行程式碼保護的實質領域意義

在 [`TaxAdvantagedAccountProjector.java:233-234`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java#L233-L234) 中：
```java
boolean dcUnlocked = (age == taxAdvantagedWithdrawalAge)
        || (age == initialAge && age > taxAdvantagedWithdrawalAge);
```
這段邏輯絕非「追補過去發生的事實」，而是**「大齡使用者的資產解鎖保底防護」**：
- 若一位使用者在 62 歲時註冊系統並匯入現存的 DC 帳戶（提領年齡預設為法定 60 歲），模擬迴圈從 62 歲起算（`initialAge = 62`）。
- **如果拔除第二個條件**：因為年齡從 62, 63, 64 往前推進，`age == 60` 將**永遠不成立**！這筆現存資產將在模擬中被**永久冰凍**，直到過世都無法變現。
- 現行邏輯是在保障「對於當前已經達到法定解鎖門檻的存量資產，於模擬起始年即視為可動用之流動資金」，這在財務規劃模型中是唯一自洽且必要的防死鎖邏輯。

---

### 三、對四個建議方向的具體質疑

請 Claude 針對以下質疑進行具體答辯，若無法給出合理的架構立論：

#### 質疑 1：方向 A（新增「已在現實中領出」開關）
- 如果使用者現實中已經結清領出，為何不直接「刪除帳戶」或「更新餘額為 0」？
- 系統若在實體帳戶旁特意新增一個「此帳戶實際上不存在」的布林值開關，豈不是產生幽靈資產欄位？這跟 WONTFIX-23 的精簡原則完全背道而馳。

#### 質疑 2：方向 B（留空就不解鎖）
- 若 `taxAdvantagedWithdrawalAge` 為 null 時不解鎖，等於大齡使用者的 DC/iDeCo 存款將永遠無法被模擬為退休現金流，直接導致模擬器失去評估老年現金流的能力。這如何能被稱為「比較保守」的合理預設？

#### 質疑 3：方向 C & D（快照過舊警示 / 要求明確確認）
- **時間戳記如何客觀驗證？** 銀行對帳單與 HTML 頁面上根本沒有任何「是否已銷戶」的旗標，匯入時間戳記更可能因手動上傳或檔案時間而產生誤差。系統要憑什麼判定這筆資料是「過期失效」還是「帳戶長期未異動但依然有效」？
- **如何區分使用者意圖？** 若使用者本身就是「意圖」要拿這筆已達解鎖年齡的現存資產數據去推算未來的退休金流，系統憑什麼跳出警示強迫中獎、中斷模擬流程去質疑使用者？
- **使用者自訂年齡早已支援**：若使用者真的想放到 70 歲，系統在 [`UserProfile`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/user/UserProfile.java#L130) 早已提供 `taxAdvantagedWithdrawalAge`（60~75 歲）可供設定，使用者主動設定 70 歲即可在 60~70 歲間享受延稅複利成長。為何要把使用者未填設定的責任，怪罪為模擬系統的缺陷？

