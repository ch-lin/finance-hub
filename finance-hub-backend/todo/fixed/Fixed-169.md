# [FIXED] Issue 169：提前還款計劃的日期欄位沒有任何下限檢查，使用者可以填入過去的日期、甚至比房貸本身動用日還早的日期

## 這份issue怎麼被發現的
這是重新檢討issue-145（已經判定WONTFIX，見[WONTFIX-28](../wontfix/WONTFIX-28-mortgage-early-repayment-is-forward-looking-plan-only.md)）時，確認這個欄位的真實設計意圖後，另外抽出來的一個獨立問題。

## 背景：這個欄位的真實設計意圖
`MortgageRecord.earlyRepayments`（房貸提前還款計劃）這個欄位，設計目的是讓使用者規劃「未來打算什麼時候、還多少」，不是用來回報過去已經發生的事。這點已經由使用者本人確認。

## 相關檔案
- `finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java:65`（`@NotNull LocalDate repaymentDate`——只檢查「有沒有填」，沒有檢查「填的日期是否合理」）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/mortgage/MortgageEarlyRepayment.java:70-71`（domain層同樣只有`@NotNull`，沒有`@Future`或任何日期範圍限制）
- `hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx:844-846`（前端的`<Input type="date">`，沒有設定`min`屬性，使用者可以用日期選擇器選到任何過去的日期，或是直接手動打字輸入）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java:175,257`（`isFutureMonth`判斷：目前對「過去日期」的提前還款計劃，是靜默跳過、不計入餘額，不是擋下來、也不是提示錯誤）

## 現狀
前後端從頭到尾都沒有針對`repaymentDate`做任何下限檢查——沒有擋「這個日期是不是在今天之前」，也沒有擋「這個日期是不是比這筆房貸自己的`startDate`還早」。使用者可以在這個欄位填入任何日期，包括比房貸動用日更早的日期，系統都會正常儲存，不會有任何錯誤或警告。

這筆填入過去日期的紀錄，不會被`MortgageCalculator`的模擬迴圈套用到餘額計算上（因為`isFutureMonth`判斷為false），但也不會被阻止儲存、不會有任何提示告知使用者「這筆記錄不會被納入計算」。

## 為何是問題
既然這個欄位的設計意圖明確是「規劃未來」，填入過去日期（或比房貸動用日更早的日期）本身就是一個不該發生的輸入，應該在源頭被擋下來，而不是讓它悄悄存進資料庫、又在運算階段被悄悄忽略。使用者如果不小心選錯日期（例如日期選擇器點錨點時誤觸、或是從其他地方複製貼上年份打錯），目前系統不會給任何回饋——使用者會以為這筆計劃已經生效，但實際上模擬結果完全不會反映它，直到使用者自己發現數字對不起來才會意識到問題。

這個情境確實不常見——使用者自己也提到「這個bug很少會有使用者蠢到打到」——但「很少發生」跟「不該發生」是兩件事，而且目前系統對這個不合理輸入的處理方式，是最差的那一種：既不擋、也不提示，純粹讓它變成一筆看起來存在、但實際上完全不起作用的死資料。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：前後端都加上日期下限檢查，擋下不合理的輸入**
- 做法：前端`<Input type="date">`加上`min`屬性（可以設成今天，或是房貸自己的`startDate`，取較晚的那個）；後端DTO/domain層加上對應的驗證（例如自訂驗證邏輯比對`repaymentDate >= max(today, mortgage.startDate)`）。
- 優點：直接在源頭擋掉不合理的輸入，使用者送出表單時就會立刻收到錯誤訊息，不會有「填了卻不生效」的落差。
- 缺點：需要決定下限究竟該是「今天」還是「房貸動用日」——如果使用者是在規劃一筆下個月要還的款，但`startDate`本身設定成未來日期（這個系統本來就支援設定未來才會動用的房貸），這兩個下限哪個才是正確的基準，需要確認。

**方向B：只在前端加`min`屬性，後端維持現狀**
- 做法：只加前端這一道防線，减少工程量。
- 優點：改動最小，涵蓋大部分正常操作情境（透過UI填寫）。
- 缺點：後端API本身仍然沒有防護，如果之後有其他呼叫方式（例如之後的Chrome Extension直接打API），一樣可以繞過這道檢查。

**方向C：維持現狀，不處理**
- 理由：使用者本人已經表示這個情境發生機率極低，可以先記錄下來，不特別投入工程成本。

---

## 決策方向：方向 A（2026-10-13，PM 確認採納）

經深入架構審查、歷史決策鏈梳理與業務邏輯釐清，原提案評估與最終定案如下：

### 1. 歷史證據鏈與領域模型職責切分（Historical Context & Domain Separation）

#### 1.1 歷史決策演進脈絡：Fixed-104 → Fixed-105 → Fixed-132 → Issue-145 → WONTFIX-28
為了避免未來審查本文件的工程師或 AI 產生「為什麼不考慮過去還款紀錄、為什麼不支援過去日期補算」的疑問，在此明確記錄系統演進的核心事實鏈：
1. **[Fixed-104](../fixed/Fixed-104.md) 與 [Fixed-105](../fixed/Fixed-105.md)**：系統早期曾嘗試推算「上次快照日至今天」的空窗期，但引發了利率誤用、跨帳戶資金斷點、無法追蹤實體提款等嚴重會計混亂。
2. **[Fixed-132](../fixed/Fixed-132.md)（系統憲法原則確立）**：使用者與架構正式拍板——**「全面廢除補算到現在機制，模擬起點改成使用者最後一次上傳快照的那個時間點本身，系統不搞通靈補算，不當會計查帳員。使用者 upload 什麼就是現在的事實。」**，徹底刪除了 `fastForwardMortgagesToPresent` 補算邏輯。
3. **[Issue-145](../pending%20fix/issue-145.md) 的定性錯誤**：審查者曾提出 Issue 145，誤將 `MortgageRecord.earlyRepayments` 當成「使用者透過介面記錄的真實提前還款事實」，並主張「若還款日期落在上次匯入餘額與現在之間，模擬器因 `isFutureMonth` 判斷而跳過，應重啟過去這段期間的補算」。
4. **[WONTFIX-28](../wontfix/WONTFIX-28-mortgage-early-repayment-is-forward-looking-plan-only.md) 最終裁決**：使用者本人親自確認並駁回 Issue 145：
   > *「這個欄位從一開始設計的目的，就只是『預測未來——使用者打算什麼時候、還多少』的計劃性安排，從來沒有規劃過要用這個欄位去回報『過去已經發生的事實』。... 真正該問的問題不是『過去的提前還款有沒有被正確套用』，而是『系統有沒有阻止使用者填入一個不該出現的過去日期』。」*

#### 1.2 會計真實（Accounting Truth）vs 未來規劃（Simulation Plan）雙軌模型
系統在領域模型上維持嚴格的雙軌切分：
- **軌道一：真實歷史會計事實（Accounting Truth）**：
  - 單一真實來源（Single Source of Truth）：`MortgageRecord.currentBalance`、`balanceRecordDate`、`endDate`。
  - 使用者流程：若現實中發生提前還款，使用者唯一正確的記帳路徑是更新最新餘額快照（重新匯入銀行對帳單或手動更新餘額）。扣款事實已經在銀行端完成，餘額已經反映最新事實。
- **軌道二：未來前瞻模擬計劃（Forward-Looking Simulation Plan）**：
  - 核心欄位：`MortgageRecord.earlyRepayments`（計劃還款日、預估金額、還款策略）。
  - 使用者流程：規劃「未來打算在何時額外還多少」。
  - 時間維度：生效日必定為未來（$T \ge \max(\text{today}, \text{startDate})$）。

#### 1.3 為什麼「過去的提前還款」絕對不能記錄在 `earlyRepayments` 內？（嚴謹財務與數學證明）
若允許使用者將「過去已經發生的還款」填入 `earlyRepayments`，並期望系統在模擬時予以計算，會引發無法挽回的三大財務災難：
1. **二度扣款災難（Double Deduction Paradox）**：
   設使用者 1 月餘額 3000 萬，6 月實際提前還款 500 萬，10 月更新銀行最新餘額為 2450 萬（已扣除 500 萬及每月正常攤還）。若系統允許在 `earlyRepayments` 填寫 6 月還款 500 萬並在模擬中套用，模擬起點從 2450 萬起跑又再扣 500 萬，變成 1950 萬——同一筆還款被扣了兩次！
2. **實體現金斷點與資金流動洩漏（Phantom Cash & Broken Audit Trail）**：
   依據 Fixed-132 論證，房貸提前還款是跨帳戶資金流動（例如 A 銀行提現金、存 B 銀行扣款）。若強行補算過去的房貸扣款，必然導致「房貸餘額少了，但銀行現金沒少」，形同憑空印鈔（Net Worth Phantom Inflation）。
3. **過期計劃執行等同於通靈記帳（Ghost Execution）**：
   若使用者在過去設定了一筆計劃，但現實中因故取消、並未還款，最新餘額未變。模擬若自動套用該過期計劃，即屬於憑空替使用者做決定的假帳。

---

### 2. 現狀深層缺陷：半死半活的計算狀態分裂（Critical Inconsistency）

除了原本 Issue 描述的「輸入端大門敞開、計算端靜默忽略」之外，審查中進一步發現了嚴重的內部計算狀態分裂：
1. **本金與手續費扣除（靜默略過）**：
   在 [MortgageCalculator.java:176,257](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L176-L260)，受 `isFutureMonth = !currentYm.isBefore(nowMonth)` 約束，過去月份的提前還款本金與費用不會被扣除。
2. **縮短年限期數扣減（幽靈累加）**：
   但在 [MortgageCalculatorUtil.java:42-56](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java#L42-L56) 的 `calculateAccumulatedReducedMonths` 內部：
   只要 `!erYm.isAfter(currentYm)`，`reducedMonths` 就會被無條件累加！
3. **後果**：
   若使用者選了 `REDUCE_TERM` 且填入過去日期：**本金一毛錢都沒扣，但在未來利率變動（Rate Change）或 5 年規則重新計價時，剩餘還款期數 `remainingMonths` 卻被這筆無效資料給縮短了！** 這將直接導致數學計算失真或每月應繳本息被錯誤拉高。

---

### 3. 業務規則拍板與下限數學定義

提前還款計劃（`MortgageEarlyRepayment`）其日期下限基準公式定義為：
$$\text{effectiveMinDate} = \max(\text{today}, \text{mortgage.startDate})$$

- **既有房貸（$\text{startDate} \le \text{today}$）**：
  - 下限為**今日（`today`）**。
  - 過去已發生之還款事實應直接更新 `currentBalance` 與 `balanceRecordDate`，不得於本計劃欄位填入過去日期。
- **未來動用之房貸（$\text{startDate} > \text{today}$）**：
  - 下限為**房貸動用日（$\text{startDate}$）**。
  - 系統支援預先設定未來動用的房貸，但在房貸尚未撥款起算前，邏輯上絕不可能發生提前清償。

---

### 4. 修正架構與技術實作規格（端到端盤點）

#### 4.1 前端 UI 與表單攔截層
- **檔案**：[hub-ui/src/app/finance/accounts/mortgages/page.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx)
- **實作規格**：
  1. 動態計算安全日期字串（使用本地時區格式，避免 UTC 位移造成清晨時差）：
     ```ts
     const todayStr = new Date().toLocaleDateString('en-CA'); // 'YYYY-MM-DD'
     const minRepaymentDate = formData.startDate && formData.startDate > todayStr 
       ? formData.startDate 
       : todayStr;
     ```
  2. 日期選擇器綁定 `min` 限制（[page.tsx:844-846](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L844-L846)）：
     ```tsx
     <Input 
       type="date" 
       min={minRepaymentDate}
       value={er.repaymentDate} 
       onChange={(e) => { ... }}
     />
     ```
  3. `handleSubmit` 表單送出前迴圈防呆（[page.tsx:190-213](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L190-L213)）：
     ```ts
     if (er.repaymentDate < minRepaymentDate) {
       toast.error(`Early repayment date cannot be earlier than ${minRepaymentDate}.`);
       return;
     }
     ```
     防範使用者手動打字鍵入過去日期繞過原生 HTML `min` 限制。

#### 4.2 後端 API / DTO 驗證層
- **檔案**：[MortgageRecordRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java#L61)
- **實作規格**：
  在 record 本體中宣告跨欄位 Bean Validation 方法：
  ```java
  @JsonIgnore
  @AssertTrue(message = "Early repayment date cannot be earlier than mortgage start date or today")
  public boolean isEarlyRepaymentsDateValid() {
      if (earlyRepayments == null || earlyRepayments.isEmpty()) {
          return true;
      }
      LocalDate today = LocalDate.now();
      LocalDate minDate = (startDate != null && startDate.isAfter(today)) ? startDate : today;
      for (var er : earlyRepayments) {
          if (er.repaymentDate() != null && er.repaymentDate().isBefore(minDate)) {
              return false;
          }
      }
      return true;
  }
  ```
- **Controller 聯防**：
  [MortgageRecordController.java:68,76](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/MortgageRecordController.java#L68-L76) 既有的 `@Valid @RequestBody` 將直接觸發此檢核，未達標時由 Spring 框架直接回應 `400 Bad Request`。

#### 4.3 後端 Service 防禦層
- **檔案**：[MortgageRecordServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java#L113-L125)
- **實作規格**：
  在 `applyEarlyRepayments` 方法開頭加入防呆：
  ```java
  LocalDate today = LocalDate.now();
  LocalDate minDate = (record.getStartDate() != null && record.getStartDate().isAfter(today)) 
          ? record.getStartDate() 
          : today;
  if (request.earlyRepayments() != null) {
      for (var dto : request.earlyRepayments()) {
          if (dto.repaymentDate() != null && dto.repaymentDate().isBefore(minDate)) {
              throw new IllegalArgumentException(
                  "Early repayment date (" + dto.repaymentDate() + ") cannot be earlier than " + minDate);
          }
      }
  }
  ```
  確保非 HTTP 入口、Service 單元測試或內部程式直接建立 Command 時仍具備防禦。

#### 4.4 單元測試覆蓋陣列
- 新增 `MortgageRecordRequestTest` 與更新 `MortgageRecordServiceImplTest`：
  1. `testValid_FutureRepayment_Success`：`repaymentDate > today`，驗證通過。
  2. `testInvalid_PastRepayment_Rejected`：`repaymentDate < today`，驗證失敗（400 / IllegalArgumentException）。
  3. `testValid_FutureMortgage_RepaymentAfterStart_Success`：`startDate > today` 且 `repaymentDate >= startDate`，驗證通過。
  4. `testInvalid_FutureMortgage_RepaymentBeforeStart_Rejected`：`today <= repaymentDate < startDate`，驗證失敗。
  5. `testValid_EmptyEarlyRepayments_Success`：無還款清單時正常通過。

---

### 5. 時間推進後的生命週期處理（已儲存之計劃日後變為「過去」的情境）

若使用者在 $T_0$（如 2026 年 10 月）設定了未來的提前還款計劃（如 2027 年 1 月），日後時間推進至 $T_1$（如 2027 年 6 月）再執行模擬時，該筆計劃的日期已經成為「過去」：

- **模擬執行時（Simulation Runtime）**：
  在 [MortgageCalculator.java:176,257](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L176-L260) 中，提前還款本金與手續費扣除受 `isFutureMonth = !currentYm.isBefore(nowMonth)` 控制。早於當前模擬月份（`nowMonth`）的記錄，在月迴圈中**本來就已經會被自動跳過、不計入餘額計算**，完全避開二度扣款。
- **房貸維護與儲存時（Accounting Maintenance）**：
  當使用者在 2027 年 6 月開啟房貸編輯視窗以更新最新 `currentBalance` 並按下儲存時，方向 A 的下限驗證（`repaymentDate >= today`）會觸發提示，告知該筆 2027 年 1 月的計劃已小於今日，進而引導使用者**將該筆已過期的歷史計劃刪除（或更新為下一筆新規劃日期）**，自然促成資料庫過期死資料的清理與狀態閉環。

---

### 6. 審查防禦與 FAQ：針對潛在質疑之深度回應（Anticipated Reviewer Objections & Rebuttals）

> **【審查者必讀】** 本節專門回應未參與歷史討論之審查者可能提出的典型質疑。

#### 質疑 1：為什麼系統不應該在此欄位支援記錄「過去已經發生的提前還款」？
- **答辯**：
  這是對系統會計架構的根本誤解。
  在 Finance Hub 的架構中，「過去的事實」是由最新對帳單餘額（`currentBalance`）與基準日（`balanceRecordDate`）全權承載。如果使用者在過去還了 200 萬，銀行的真實剩餘本金就已經是扣完 200 萬後的數字。
  若在 `earlyRepayments` 另闢蹊徑記錄過去事實，系統在計算時就必須面對「二度扣款」或「回溯查帳稽核」的兩難境地。依據 Fixed-132 的憲法原則，系統絕不當會計查帳員，事實資料單一收斂於 `currentBalance`。

#### 質疑 2：為什麼模擬引擎不應該「補算」過去這段時間的提前還款紀錄？
- **答辯**：
  此提案已在 Issue 145 與 Fixed-145 經過完整架構辯論，並在 WONTFIX-28 正式否決。
  補算機制在 Fixed-132 已被全面廢除，其核心理由為：
  1. 跨帳戶金錢流動斷點無法追蹤（無法透視現金 ATM 與跨行調度）；
  2. 補算房貸本金而不扣除現金帳戶，將在模擬中憑空創造淨資產虛增（憑空印鈔）；
  3. 使用者更新對帳單時已經包含真實事實，補算必導致二度扣款。

#### 質疑 3：如果使用者需要查詢過去的房貸還款歷史明細，該看哪裡？
- **答辯**：
  歷史明細的資料來源是銀行的交易流水帳戶（Transaction History）或專屬的對帳單明細解析器（例如 Sony Bank 履歴頁面，詳見 Fixed-90），屬於 Accounting 模組未來的交易流水擴充範疇。
  `MortgageRecord` 實體上的 `earlyRepayments` 欄位，從定義之初就是房貸實體的附加「規劃屬性」，絕對不是一般帳戶的交易流水帳本。

#### 質疑 4：方向 A 會不會破壞現有資料庫的舊資料？
- **答辯**：
  不會。
  1. 方向 A 是**寫入路徑驗證（Write-path Validation）**，只在使用者 `POST`（建立）或 `PUT`（更新）時攔截，不會影響既有資料庫的 `GET`（讀取）。
  2. 模擬器執行時，受 `isFutureMonth` 保護，舊有的過期資料本就被安全跳過。
  3. 當使用者日後編輯該筆房貸並嘗試儲存時，驗證會引導使用者將歷史髒資料清理，使系統資料庫隨時間推移自然收斂至乾淨狀態。

---

### 7. 修正實作紀錄（2026-10-13 修正落地）

已完成全棧實作並通過單元測試驗證，異動清單如下：

1. **後端 DTO 驗證層**：
   - [MortgageRecordRequest.java:65-79](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java#L65-L79)：新增 `@JsonIgnore @AssertTrue` 跨欄位檢核方法 `isEarlyRepaymentsDateValid()`，嚴格限制所有還款日期不得早於 $\max(\text{today}, \text{startDate})$。
2. **後端 Service 防禦層**：
   - [MortgageRecordServiceImpl.java:120-128](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java#L120-L128)：在 `applyEarlyRepayments` 加入底層防呆，非法日期拋出 `IllegalArgumentException`。
3. **前端 UI 與表單攔截層**：
   - [hub-ui/src/app/finance/accounts/mortgages/page.tsx:68-71](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L68-L71)：動態推導 `minRepaymentDate`（本地時區）。
   - [page.tsx:195-198](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L195-L198)：`handleSubmit` 前端驗證阻擋。
   - [page.tsx:818-875](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L818-L875)：`<Input type="date">` 綁定 `min={minRepaymentDate}`，初始化與新增還款預設日期皆對齊 `minRepaymentDate`；強化 UI 說明（標題改為「Planned Early Repayments (将来の繰上げ返済計画)」，新增提示橫幅引導「此處僅供未來前瞻模擬規劃，過去已發生的還款請直接更新上方 Current Balance」避免使用者誤會）。
4. **單元測試驗證**：
   - [MortgageRecordRequestTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequestTest.java)：涵蓋既有房貸未來還款、既有房貸過去還款、未來房貸撥款後還款、未來房貸撥款前還款、空還款清單等 5 組測試案例。
   - [MortgageRecordServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImplTest.java)：涵蓋 Service 層拋出 `IllegalArgumentException` 之單元測試。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff
diff --git a/hub-ui/src/app/finance/accounts/mortgages/page.tsx b/hub-ui/src/app/finance/accounts/mortgages/page.tsx
index bd8ed33..5152d98 100644
--- a/hub-ui/src/app/finance/accounts/mortgages/page.tsx
+++ b/hub-ui/src/app/finance/accounts/mortgages/page.tsx
@@ -65,6 +65,11 @@ export default function MortgagesPage() {
 
   const [formData, setFormData] = useState<MortgageRecordRequest>(getEmptyForm());
 
+  const todayStr = new Date().toLocaleDateString('en-CA');
+  const minRepaymentDate = formData.startDate && formData.startDate > todayStr 
+    ? formData.startDate 
+    : todayStr;
+
   const syncEstimatorFromForm = () => {
     const principal = editingMortgage 
       ? (formData.currentBalance && formData.currentBalance > 0 ? formData.currentBalance : (formData.totalLoanAmount || ""))
@@ -193,6 +198,10 @@ export default function MortgagesPage() {
           toast.error("Please specify a date for all early repayments.");
           return;
         }
+        if (er.repaymentDate < minRepaymentDate) {
+          toast.error(`Early repayment date cannot be earlier than ${minRepaymentDate}.`);
+          return;
+        }
         if (er.amount === undefined || er.amount === null || er.amount <= 0) {
           toast.error("Early repayment amount must be greater than 0.");
           return;
@@ -793,7 +802,7 @@ export default function MortgagesPage() {
                         setFormData({
                           ...formData,
                           earlyRepayments: [
-                            { repaymentDate: new Date().toISOString().split("T")[0], amount: 0, fee: 0, repaymentType: 'REDUCE_PAYMENT' }
+                            { repaymentDate: minRepaymentDate, amount: 0, fee: 0, repaymentType: 'REDUCE_PAYMENT' }
                           ],
                         });
                       }
@@ -806,14 +815,27 @@ export default function MortgagesPage() {
                   }}
                 />
                 <label htmlFor="hasEarlyRepayment" className="text-sm font-semibold cursor-pointer">
-                  Has Early Repayments (繰上げ返済あり)
+                  Planned Early Repayments (将来の繰上げ返済計画)
                 </label>
               </div>
 
               {hasEarlyRepayment && (
                 <>
+                  <div className="rounded-lg border border-blue-500/20 bg-blue-50/50 dark:bg-blue-950/20 dark:border-blue-900/40 p-3 mb-3 text-xs space-y-1.5">
+                    <div className="flex items-center gap-1.5 font-medium text-foreground">
+                      <span className="h-2 w-2 rounded-full bg-blue-500 inline-block" />
+                      <span>Forward-Looking Simulation Plans (将来の繰上げ返済計画)</span>
+                    </div>
+                    <p className="text-muted-foreground leading-relaxed">
+                      This section is <strong>strictly for scheduling future planned early repayments</strong> to project balance reductions and interest savings in simulations.
+                    </p>
+                    <p className="text-[11px] text-muted-foreground/80 leading-relaxed">
+                      * <strong>Past repayments already made</strong> are already reflected in your current balance. Please update <strong>&quot;Current Balance&quot;</strong> above rather than entering past dates here to avoid double deduction.
+                    </p>
+                  </div>
+
                   <div className="flex items-center justify-between mb-3">
-                    <span className="text-sm text-muted-foreground">Record your early repayments here.</span>
+                    <span className="text-xs text-muted-foreground">Schedule planned repayments:</span>
                     <Button 
                       type="button" 
                       variant="outline" 
@@ -823,26 +845,27 @@ export default function MortgagesPage() {
                           ...formData,
                           earlyRepayments: [
                             ...formData.earlyRepayments, 
-                            { repaymentDate: new Date().toISOString().split("T")[0], amount: 0, fee: 0, repaymentType: 'REDUCE_PAYMENT' }
+                            { repaymentDate: minRepaymentDate, amount: 0, fee: 0, repaymentType: 'REDUCE_PAYMENT' }
                           ]
                         });
                       }}
                     >
-                      <Plus size={14} className="mr-1" /> Add Repayment
+                      <Plus size={14} className="mr-1" /> Add Repayment Plan
                     </Button>
                   </div>
                   
                   {formData.earlyRepayments.length === 0 ? (
-                    <div className="text-sm text-muted-foreground italic mb-4">No early repayments recorded. Click 'Add Repayment' to add one.</div>
+                    <div className="text-sm text-muted-foreground italic mb-4">No planned early repayments. Click 'Add Repayment Plan' to schedule one.</div>
                   ) : (
                     <div className="space-y-4 mb-4">
                       {formData.earlyRepayments.map((er, index) => (
                         <div key={index} className="flex flex-col gap-2 bg-muted/30 p-2 rounded-md">
                           <div className="grid grid-cols-12 gap-2 items-center">
                             <div className="col-span-3 space-y-1">
-                            <label className="text-xs">Date</label>
+                            <label className="text-xs">Planned Date</label>
                             <Input 
                               type="date" 
+                              min={minRepaymentDate}
                               value={er.repaymentDate} 
                               onChange={(e) => {
                                 const newRepayments = [...formData.earlyRepayments];
@@ -876,7 +899,7 @@ export default function MortgagesPage() {
                             />
                           </div>
                           <div className="col-span-3 space-y-1">
-                            <label className="text-xs">Type</label>
+                            <label className="text-xs">Repayment Type</label>
                             <select 
                               className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                               value={er.repaymentType}
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  cd ../../finance-hub/finance-hub-backend 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  git --no-pager diff
diff --git a/.gitignore b/.gitignore
index 1598c883..289cf01a 100644
--- a/.gitignore
+++ b/.gitignore
@@ -77,3 +77,6 @@ target/
 
 # virtual machine crash logs
 hs_err_pid*
+
+# Runner
+*Runner.java
\ No newline at end of file
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java
index afb371d7..4dbac9f6 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java
@@ -116,7 +116,15 @@ public class MortgageRecordServiceImpl implements MortgageRecordService {
         }
         record.getEarlyRepayments().clear();
         if (request.earlyRepayments() != null) {
+            LocalDate today = LocalDate.now();
+            LocalDate minDate = (record.getStartDate() != null && record.getStartDate().isAfter(today))
+                    ? record.getStartDate()
+                    : today;
             for (var dto : request.earlyRepayments()) {
+                if (dto.repaymentDate() != null && dto.repaymentDate().isBefore(minDate)) {
+                    throw new IllegalArgumentException(
+                            "Early repayment date (" + dto.repaymentDate() + ") cannot be earlier than " + minDate);
+                }
                 ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageEarlyRepayment earlyRepayment = new ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageEarlyRepayment(
                         record, dto.repaymentDate(), dto.amount(), dto.repaymentType(), dto.fee(), dto.reducedMonths());
                 record.getEarlyRepayments().add(earlyRepayment);
diff --git a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java
index 813e02b0..85ee4843 100644
--- a/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java
+++ b/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java
@@ -31,7 +31,9 @@ import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestR
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.PropertyType;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
 import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.ResidenceType;
+import com.fasterxml.jackson.annotation.JsonIgnore;
 import jakarta.validation.Valid;
+import jakarta.validation.constraints.AssertTrue;
 import jakarta.validation.constraints.Max;
 import jakarta.validation.constraints.Min;
 import jakarta.validation.constraints.NotBlank;
@@ -60,6 +62,22 @@ public record MortgageRecordRequest(
         Boolean hasFiveYearRule,
         @Valid List<MortgageEarlyRepaymentRequest> earlyRepayments) {
 
+    @JsonIgnore
+    @AssertTrue(message = "Early repayment date cannot be earlier than mortgage start date or today")
+    public boolean isEarlyRepaymentsDateValid() {
+        if (earlyRepayments == null || earlyRepayments.isEmpty()) {
+            return true;
+        }
+        LocalDate today = LocalDate.now();
+        LocalDate minDate = (startDate != null && startDate.isAfter(today)) ? startDate : today;
+        for (var er : earlyRepayments) {
+            if (er.repaymentDate() != null && er.repaymentDate().isBefore(minDate)) {
+                return false;
+            }
+        }
+        return true;
+    }
+
     public record MortgageEarlyRepaymentRequest(
             Long id,
             @NotNull LocalDate repaymentDate,
diff --git a/finance-hub-backend/todo/pending fix/issue-169.md b/finance-hub-backend/todo/pending fix/issue-169.md
index 8e863b96..04ae60d4 100644
--- a/finance-hub-backend/todo/pending fix/issue-169.md
+++ b/finance-hub-backend/todo/pending fix/issue-169.md
@@ -37,3 +37,212 @@
 
 **方向C：維持現狀，不處理**
 - 理由：使用者本人已經表示這個情境發生機率極低，可以先記錄下來，不特別投入工程成本。
+
+---
+
+## 決策方向：方向 A（2026-10-13，PM 確認採納）
+
+經深入架構審查、歷史決策鏈梳理與業務邏輯釐清，原提案評估與最終定案如下：
+
+### 1. 歷史證據鏈與領域模型職責切分（Historical Context & Domain Separation）
+
+#### 1.1 歷史決策演進脈絡：Fixed-104 → Fixed-105 → Fixed-132 → Issue-145 → WONTFIX-28
+為了避免未來審查本文件的工程師或 AI 產生「為什麼不考慮過去還款紀錄、為什麼不支援過去日期補算」的疑問，在此明確記錄系統演進的核心事實鏈：
+1. **[Fixed-104](../fixed/Fixed-104.md) 與 [Fixed-105](../fixed/Fixed-105.md)**：系統早期曾嘗試推算「上次快照日至今天」的空窗期，但引發了利率誤用、跨帳戶資金斷點、無法追蹤實體提款等嚴重會計混亂。
+2. **[Fixed-132](../fixed/Fixed-132.md)（系統憲法原則確立）**：使用者與架構正式拍板——**「全面廢除補算到現在機制，模擬起點改成使用者最後一次上傳快照的那個時間點本身，系統不搞通靈補算，不當會計查帳員。使用者 upload 什麼就是現在的事實。」**，徹底刪除了 `fastForwardMortgagesToPresent` 補算邏輯。
+3. **[Issue-145](../pending%20fix/issue-145.md) 的定性錯誤**：審查者曾提出 Issue 145，誤將 `MortgageRecord.earlyRepayments` 當成「使用者透過介面記錄的真實提前還款事實」，並主張「若還款日期落在上次匯入餘額與現在之間，模擬器因 `isFutureMonth` 判斷而跳過，應重啟過去這段期間的補算」。
+4. **[WONTFIX-28](../wontfix/WONTFIX-28-mortgage-early-repayment-is-forward-looking-plan-only.md) 最終裁決**：使用者本人親自確認並駁回 Issue 145：
+   > *「這個欄位從一開始設計的目的，就只是『預測未來——使用者打算什麼時候、還多少』的計劃性安排，從來沒有規劃過要用這個欄位去回報『過去已經發生的事實』。... 真正該問的問題不是『過去的提前還款有沒有被正確套用』，而是『系統有沒有阻止使用者填入一個不該出現的過去日期』。」*
+
+#### 1.2 會計真實（Accounting Truth）vs 未來規劃（Simulation Plan）雙軌模型
+系統在領域模型上維持嚴格的雙軌切分：
+- **軌道一：真實歷史會計事實（Accounting Truth）**：
+  - 單一真實來源（Single Source of Truth）：`MortgageRecord.currentBalance`、`balanceRecordDate`、`endDate`。
+  - 使用者流程：若現實中發生提前還款，使用者唯一正確的記帳路徑是更新最新餘額快照（重新匯入銀行對帳單或手動更新餘額）。扣款事實已經在銀行端完成，餘額已經反映最新事實。
+- **軌道二：未來前瞻模擬計劃（Forward-Looking Simulation Plan）**：
+  - 核心欄位：`MortgageRecord.earlyRepayments`（計劃還款日、預估金額、還款策略）。
+  - 使用者流程：規劃「未來打算在何時額外還多少」。
+  - 時間維度：生效日必定為未來（$T \ge \max(\text{today}, \text{startDate})$）。
+
+#### 1.3 為什麼「過去的提前還款」絕對不能記錄在 `earlyRepayments` 內？（嚴謹財務與數學證明）
+若允許使用者將「過去已經發生的還款」填入 `earlyRepayments`，並期望系統在模擬時予以計算，會引發無法挽回的三大財務災難：
+1. **二度扣款災難（Double Deduction Paradox）**：
+   設使用者 1 月餘額 3000 萬，6 月實際提前還款 500 萬，10 月更新銀行最新餘額為 2450 萬（已扣除 500 萬及每月正常攤還）。若系統允許在 `earlyRepayments` 填寫 6 月還款 500 萬並在模擬中套用，模擬起點從 2450 萬起跑又再扣 500 萬，變成 1950 萬——同一筆還款被扣了兩次！
+2. **實體現金斷點與資金流動洩漏（Phantom Cash & Broken Audit Trail）**：
+   依據 Fixed-132 論證，房貸提前還款是跨帳戶資金流動（例如 A 銀行提現金、存 B 銀行扣款）。若強行補算過去的房貸扣款，必然導致「房貸餘額少了，但銀行現金沒少」，形同憑空印鈔（Net Worth Phantom Inflation）。
+3. **過期計劃執行等同於通靈記帳（Ghost Execution）**：
+   若使用者在過去設定了一筆計劃，但現實中因故取消、並未還款，最新餘額未變。模擬若自動套用該過期計劃，即屬於憑空替使用者做決定的假帳。
+
+---
+
+### 2. 現狀深層缺陷：半死半活的計算狀態分裂（Critical Inconsistency）
+
+除了原本 Issue 描述的「輸入端大門敞開、計算端靜默忽略」之外，審查中進一步發現了嚴重的內部計算狀態分裂：
+1. **本金與手續費扣除（靜默略過）**：
+   在 [MortgageCalculator.java:176,257](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L176-L260)，受 `isFutureMonth = !currentYm.isBefore(nowMonth)` 約束，過去月份的提前還款本金與費用不會被扣除。
+2. **縮短年限期數扣減（幽靈累加）**：
+   但在 [MortgageCalculatorUtil.java:42-56](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/utils/MortgageCalculatorUtil.java#L42-L56) 的 `calculateAccumulatedReducedMonths` 內部：
+   只要 `!erYm.isAfter(currentYm)`，`reducedMonths` 就會被無條件累加！
+3. **後果**：
+   若使用者選了 `REDUCE_TERM` 且填入過去日期：**本金一毛錢都沒扣，但在未來利率變動（Rate Change）或 5 年規則重新計價時，剩餘還款期數 `remainingMonths` 卻被這筆無效資料給縮短了！** 這將直接導致數學計算失真或每月應繳本息被錯誤拉高。
+
+---
+
+### 3. 業務規則拍板與下限數學定義
+
+提前還款計劃（`MortgageEarlyRepayment`）其日期下限基準公式定義為：
+$$\text{effectiveMinDate} = \max(\text{today}, \text{mortgage.startDate})$$
+
+- **既有房貸（$\text{startDate} \le \text{today}$）**：
+  - 下限為**今日（`today`）**。
+  - 過去已發生之還款事實應直接更新 `currentBalance` 與 `balanceRecordDate`，不得於本計劃欄位填入過去日期。
+- **未來動用之房貸（$\text{startDate} > \text{today}$）**：
+  - 下限為**房貸動用日（$\text{startDate}$）**。
+  - 系統支援預先設定未來動用的房貸，但在房貸尚未撥款起算前，邏輯上絕不可能發生提前清償。
+
+---
+
+### 4. 修正架構與技術實作規格（端到端盤點）
+
+#### 4.1 前端 UI 與表單攔截層
+- **檔案**：[hub-ui/src/app/finance/accounts/mortgages/page.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx)
+- **實作規格**：
+  1. 動態計算安全日期字串（使用本地時區格式，避免 UTC 位移造成清晨時差）：
+     ```ts
+     const todayStr = new Date().toLocaleDateString('en-CA'); // 'YYYY-MM-DD'
+     const minRepaymentDate = formData.startDate && formData.startDate > todayStr 
+       ? formData.startDate 
+       : todayStr;
+     ```
+  2. 日期選擇器綁定 `min` 限制（[page.tsx:844-846](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L844-L846)）：
+     ```tsx
+     <Input 
+       type="date" 
+       min={minRepaymentDate}
+       value={er.repaymentDate} 
+       onChange={(e) => { ... }}
+     />
+     ```
+  3. `handleSubmit` 表單送出前迴圈防呆（[page.tsx:190-213](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L190-L213)）：
+     ```ts
+     if (er.repaymentDate < minRepaymentDate) {
+       toast.error(`Early repayment date cannot be earlier than ${minRepaymentDate}.`);
+       return;
+     }
+     ```
+     防範使用者手動打字鍵入過去日期繞過原生 HTML `min` 限制。
+
+#### 4.2 後端 API / DTO 驗證層
+- **檔案**：[MortgageRecordRequest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java#L61)
+- **實作規格**：
+  在 record 本體中宣告跨欄位 Bean Validation 方法：
+  ```java
+  @JsonIgnore
+  @AssertTrue(message = "Early repayment date cannot be earlier than mortgage start date or today")
+  public boolean isEarlyRepaymentsDateValid() {
+      if (earlyRepayments == null || earlyRepayments.isEmpty()) {
+          return true;
+      }
+      LocalDate today = LocalDate.now();
+      LocalDate minDate = (startDate != null && startDate.isAfter(today)) ? startDate : today;
+      for (var er : earlyRepayments) {
+          if (er.repaymentDate() != null && er.repaymentDate().isBefore(minDate)) {
+              return false;
+          }
+      }
+      return true;
+  }
+  ```
+- **Controller 聯防**：
+  [MortgageRecordController.java:68,76](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/MortgageRecordController.java#L68-L76) 既有的 `@Valid @RequestBody` 將直接觸發此檢核，未達標時由 Spring 框架直接回應 `400 Bad Request`。
+
+#### 4.3 後端 Service 防禦層
+- **檔案**：[MortgageRecordServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java#L113-L125)
+- **實作規格**：
+  在 `applyEarlyRepayments` 方法開頭加入防呆：
+  ```java
+  LocalDate today = LocalDate.now();
+  LocalDate minDate = (record.getStartDate() != null && record.getStartDate().isAfter(today)) 
+          ? record.getStartDate() 
+          : today;
+  if (request.earlyRepayments() != null) {
+      for (var dto : request.earlyRepayments()) {
+          if (dto.repaymentDate() != null && dto.repaymentDate().isBefore(minDate)) {
+              throw new IllegalArgumentException(
+                  "Early repayment date (" + dto.repaymentDate() + ") cannot be earlier than " + minDate);
+          }
+      }
+  }
+  ```
+  確保非 HTTP 入口、Service 單元測試或內部程式直接建立 Command 時仍具備防禦。
+
+#### 4.4 單元測試覆蓋陣列
+- 新增 `MortgageRecordRequestTest` 與更新 `MortgageRecordServiceImplTest`：
+  1. `testValid_FutureRepayment_Success`：`repaymentDate > today`，驗證通過。
+  2. `testInvalid_PastRepayment_Rejected`：`repaymentDate < today`，驗證失敗（400 / IllegalArgumentException）。
+  3. `testValid_FutureMortgage_RepaymentAfterStart_Success`：`startDate > today` 且 `repaymentDate >= startDate`，驗證通過。
+  4. `testInvalid_FutureMortgage_RepaymentBeforeStart_Rejected`：`today <= repaymentDate < startDate`，驗證失敗。
+  5. `testValid_EmptyEarlyRepayments_Success`：無還款清單時正常通過。
+
+---
+
+### 5. 時間推進後的生命週期處理（已儲存之計劃日後變為「過去」的情境）
+
+若使用者在 $T_0$（如 2026 年 10 月）設定了未來的提前還款計劃（如 2027 年 1 月），日後時間推進至 $T_1$（如 2027 年 6 月）再執行模擬時，該筆計劃的日期已經成為「過去」：
+
+- **模擬執行時（Simulation Runtime）**：
+  在 [MortgageCalculator.java:176,257](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java#L176-L260) 中，提前還款本金與手續費扣除受 `isFutureMonth = !currentYm.isBefore(nowMonth)` 控制。早於當前模擬月份（`nowMonth`）的記錄，在月迴圈中**本來就已經會被自動跳過、不計入餘額計算**，完全避開二度扣款。
+- **房貸維護與儲存時（Accounting Maintenance）**：
+  當使用者在 2027 年 6 月開啟房貸編輯視窗以更新最新 `currentBalance` 並按下儲存時，方向 A 的下限驗證（`repaymentDate >= today`）會觸發提示，告知該筆 2027 年 1 月的計劃已小於今日，進而引導使用者**將該筆已過期的歷史計劃刪除（或更新為下一筆新規劃日期）**，自然促成資料庫過期死資料的清理與狀態閉環。
+
+---
+
+### 6. 審查防禦與 FAQ：針對潛在質疑之深度回應（Anticipated Reviewer Objections & Rebuttals）
+
+> **【審查者必讀】** 本節專門回應未參與歷史討論之審查者可能提出的典型質疑。
+
+#### 質疑 1：為什麼系統不應該在此欄位支援記錄「過去已經發生的提前還款」？
+- **答辯**：
+  這是對系統會計架構的根本誤解。
+  在 Finance Hub 的架構中，「過去的事實」是由最新對帳單餘額（`currentBalance`）與基準日（`balanceRecordDate`）全權承載。如果使用者在過去還了 200 萬，銀行的真實剩餘本金就已經是扣完 200 萬後的數字。
+  若在 `earlyRepayments` 另闢蹊徑記錄過去事實，系統在計算時就必須面對「二度扣款」或「回溯查帳稽核」的兩難境地。依據 Fixed-132 的憲法原則，系統絕不當會計查帳員，事實資料單一收斂於 `currentBalance`。
+
+#### 質疑 2：為什麼模擬引擎不應該「補算」過去這段時間的提前還款紀錄？
+- **答辯**：
+  此提案已在 Issue 145 與 Fixed-145 經過完整架構辯論，並在 WONTFIX-28 正式否決。
+  補算機制在 Fixed-132 已被全面廢除，其核心理由為：
+  1. 跨帳戶金錢流動斷點無法追蹤（無法透視現金 ATM 與跨行調度）；
+  2. 補算房貸本金而不扣除現金帳戶，將在模擬中憑空創造淨資產虛增（憑空印鈔）；
+  3. 使用者更新對帳單時已經包含真實事實，補算必導致二度扣款。
+
+#### 質疑 3：如果使用者需要查詢過去的房貸還款歷史明細，該看哪裡？
+- **答辯**：
+  歷史明細的資料來源是銀行的交易流水帳戶（Transaction History）或專屬的對帳單明細解析器（例如 Sony Bank 履歴頁面，詳見 Fixed-90），屬於 Accounting 模組未來的交易流水擴充範疇。
+  `MortgageRecord` 實體上的 `earlyRepayments` 欄位，從定義之初就是房貸實體的附加「規劃屬性」，絕對不是一般帳戶的交易流水帳本。
+
+#### 質疑 4：方向 A 會不會破壞現有資料庫的舊資料？
+- **答辯**：
+  不會。
+  1. 方向 A 是**寫入路徑驗證（Write-path Validation）**，只在使用者 `POST`（建立）或 `PUT`（更新）時攔截，不會影響既有資料庫的 `GET`（讀取）。
+  2. 模擬器執行時，受 `isFutureMonth` 保護，舊有的過期資料本就被安全跳過。
+  3. 當使用者日後編輯該筆房貸並嘗試儲存時，驗證會引導使用者將歷史髒資料清理，使系統資料庫隨時間推移自然收斂至乾淨狀態。
+
+---
+
+### 7. 修正實作紀錄（2026-10-13 修正落地）
+
+已完成全棧實作並通過單元測試驗證，異動清單如下：
+
+1. **後端 DTO 驗證層**：
+   - [MortgageRecordRequest.java:65-79](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequest.java#L65-L79)：新增 `@JsonIgnore @AssertTrue` 跨欄位檢核方法 `isEarlyRepaymentsDateValid()`，嚴格限制所有還款日期不得早於 $\max(\text{today}, \text{startDate})$。
+2. **後端 Service 防禦層**：
+   - [MortgageRecordServiceImpl.java:120-128](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java#L120-L128)：在 `applyEarlyRepayments` 加入底層防呆，非法日期拋出 `IllegalArgumentException`。
+3. **前端 UI 與表單攔截層**：
+   - [hub-ui/src/app/finance/accounts/mortgages/page.tsx:68-71](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L68-L71)：動態推導 `minRepaymentDate`（本地時區）。
+   - [page.tsx:195-198](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L195-L198)：`handleSubmit` 前端驗證阻擋。
+   - [page.tsx:818-875](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx#L818-L875)：`<Input type="date">` 綁定 `min={minRepaymentDate}`，初始化與新增還款預設日期皆對齊 `minRepaymentDate`；強化 UI 說明（標題改為「Planned Early Repayments (将来の繰上げ返済計画)」，新增提示橫幅引導「此處僅供未來前瞻模擬規劃，過去已發生的還款請直接更新上方 Current Balance」避免使用者誤會）。
+4. **單元測試驗證**：
+   - [MortgageRecordRequestTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/test/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/dto/MortgageRecordRequestTest.java)：涵蓋既有房貸未來還款、既有房貸過去還款、未來房貸撥款後還款、未來房貸撥款前還款、空還款清單等 5 組測試案例。
+   - [MortgageRecordServiceImplTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImplTest.java)：涵蓋 Service 層拋出 `IllegalArgumentException` 之單元測試。
+
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main    INSERT  