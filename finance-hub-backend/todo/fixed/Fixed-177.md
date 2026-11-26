# [FIXED] 177：NISA定期定額設定頁「每月提繳金額」欄位的PATCH端點只更新了一個模擬引擎完全不會讀取的欄位，使用者修改後畫面顯示有變、但模擬結果完全不受影響

## 這份issue怎麼被發現的
這是這次Source Code Review審查NISA服務層時發現的——先確認`shouldMask`等既有邏輯沒有被誤判波及，再用獨立測試驗證過Hibernate行為後確認的程式碼事實。

## 相關檔案
- [NisaContributionSettingServiceImpl.java:53-64](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java)（`updateAllocation`方法，PATCH `/assets/nisa/settings/{id}/allocation`背後的service邏輯）
- [NisaContributionSetting.java:94-134](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/nisa/NisaContributionSetting.java)（entity上`settingAmount`跟`monthlyEquivalentAmount`是兩個分開儲存的欄位；`updateMonthlyEquivalentAmount()`只改後者）
- [NisaContextBuilder.java:124-140](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java)（模擬引擎建立`SimulationContext`時，計算年提繳金額`annualContribution`完全只用`s.getSettingAmount()`乘以頻率係數，從頭到尾沒有出現`getMonthlyEquivalentAmount()`這個方法呼叫——已重新查證確認）
- 對照：[DefinedContributionServiceImpl.java:110-126](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)（DC這邊對等的`updateAllocation`，改的是`monthlyContributionAmount`，而`DefinedContributionContextBuilder.java:73,141`確實有讀這個欄位——證明DC那邊這條PATCH路徑是有效的，NISA這邊是獨立出來的落差，不是整個系統共通的設計）
- 前端：[nisa-contribution-settings.tsx:52](file://hub-ui/hub-ui/src/app/finance/portfolios/nisa/components/nisa-contribution-settings.tsx)（畫面文案明確寫著「Values configured here directly drive 30-year projections and cash flow simulation.」）、`:110-114`（這個輸入框呼叫的正是`updateMonthlyContribution`，打到`/allocation`端點）

## 現狀
NISA定期定額設定（`NisaContributionSetting`）這個entity，身上同時存在兩個金額欄位：`settingAmount`（對帳單上原始的「每次扣款金額」，例如毎日1萬円）跟`monthlyEquivalentAmount`（系統算出來的「換算成每月一次的等值金額」，例如每日1萬円換算成每月約20萬円）。

畫面上「Active Contribution Plan」表格讓使用者直接編輯「Monthly Contribution」這一欄，送出的是PATCH `/assets/nisa/settings/{id}/allocation`，body是`{ monthlyEquivalentAmount: 新值 }`。`NisaContributionSettingServiceImpl.updateAllocation()`收到之後，只呼叫了`setting.updateMonthlyEquivalentAmount(command.getMonthlyEquivalentAmount())`——這個方法只改`monthlyEquivalentAmount`欄位本身，完全不會動`settingAmount`跟`course`（毎日/毎週/毎月）這兩個欄位。

而模擬引擎（`NisaContextBuilder.initializeNisaContext`）要組出每一筆NISA基金在模擬中的年提繳金額時，完全是重新用`s.getSettingAmount()`跟`s.getCourse()`去乘頻率係數算出來的（毎日×240、毎週×52、毎月×12）。全文搜尋整個`hub-app/src/main/java`，`getMonthlyEquivalentAmount()`這個方法除了「寫入它的地方」(`NisaContributionSettingServiceImpl.java:61`)跟「讀出來顯示在API回應裡」(`NisaContributionSettingResult.java:56`)之外，沒有任何第三處呼叫——換句話說，這個欄位從資料庫寫入之後，只會被原封不動讀出來顯示在畫面上，不會流進模擬引擎的任何計算。

## 為何是問題
使用者在「Active Contribution Plan」表格裡把某支基金的「Monthly Contribution」從20萬円改成30萬円，畫面上這一列的數字確實變成30萬円（因為前端的`activeContributionSettings`直接顯示後端存回來的`monthlyEquivalentAmount`），畫面最下方的「Total Monthly Investment」加總也會跟著變。使用者看到這個結果，會合理地以為往後30年的模擬已經反映了這筆調高後的提繳金額——UI文案本身就寫著「這裡設定的值會直接驅動30年投影跟現金流模擬」。

但實際上，下次呼叫NISA投影或整合現金流模擬時，`NisaContextBuilder`重新讀取`NisaContributionSetting`時，用的是沒有被這次PATCH碰過的`settingAmount`跟`course`，算出來的年提繳金額跟使用者改之前完全一樣——使用者的這次編輯，從模擬結果的角度看，什麼都沒發生。這不是「精確度打折」而是「這個輸入框完全不起作用」，而且因為畫面上顯示的數字確實變了，使用者沒有任何管道能發現這個落差，除非自己手動核對投影結果的提繳成長曲線跟改之前一樣。

## 具體錯誤情境
使用者某支「毎月」提繳的つみたて基金，`settingAmount=50,000`、`monthlyEquivalentAmount=50,000`（毎月提繳本來兩者就相等）。使用者透過畫面把「Monthly Contribution」改成100,000（打算把每月提繳金額加倍），PATCH成功、畫面顯示100,000、`monthlyEquivalentAmount`在資料庫裡確實變成100,000。下次重新計算30年NISA投影或是跑整合現金流模擬時，`NisaContextBuilder`讀到的`settingAmount`仍然是舊的50,000、`course`仍然是「毎月」，算出來的`annualContribution = 50,000 × 12 = 600,000`——跟使用者改之前的金額一模一樣，使用者以為自己已經把每月投資額加倍，但往後30年的資產累積投影，實際上完全沒有反映這個改動。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：PATCH時同步換算回`settingAmount`**
- 做法：`updateAllocation`收到新的`monthlyEquivalentAmount`時，依照這筆設定現有的`course`（毎日/毎週/毎月）反推回對應的`settingAmount`（例如course是毎日，就除以20；毎週除以4），兩個欄位一起更新，讓`settingAmount`重新對齊使用者看到／編輯的「每月換算值」。
- 優點：改動集中在service層一個方法，前端不需要改。
- 缺點：如果這筆設定的`course`是毎日/毎週，反推回去的`settingAmount`會變成一個使用者從沒輸入過的「推算值」，跟真實對帳單上「每次扣款金額」這個事實欄位脫鉤；下次重新匯入對帳單時，這個被使用者手動覆寫過的`settingAmount`會不會被對帳單解析結果覆蓋回去，需要額外確認（涉及Plan-24討論的「事實欄位vs使用者欄位」分類問題）。

**方向B：讓模擬引擎改讀`monthlyEquivalentAmount`，不要重新用`settingAmount`×頻率係數自己算**
- 做法：`NisaContextBuilder`直接用`s.getMonthlyEquivalentAmount() × 12`當作年提繳金額，不要重新從`settingAmount`+`course`推算。
- 優點：使用者編輯的欄位跟模擬引擎讀的欄位變成同一個，從源頭消除這種「兩個欄位各自為政」的落差，`monthlyEquivalentAmount`原本存在的目的本來就是給模擬引擎用的換算值。
- 缺點：需要確認`SbiNisaParserImpl.parseSettings()`每次重新解析對帳單時，有沒有同時正確覆寫`monthlyEquivalentAmount`——查證確認目前有正確覆寫，所以這點風險可控，但這本來就符合「對帳單事實欄位應該被覆寫」的既有規則，不是新增的風險。

## 深入查證與補充報告（致 Reviewer：歷史脈絡與架構對齊）

針對原 Review 報告中指出「模擬引擎漏讀 `monthlyEquivalentAmount`」以及所提出的「方向 A / 方向 B」，經比對專案歷史 commit、架構演進紀錄（特別是 `Fixed-149.md`）、`issue-180` 的事實合併規則，以及完整 trace 模擬引擎（Sandbox、GA、Grid）的資金流向後，發現原報告忽略了重要的歷史背景與系統架構設計，特此補充以下查證事實供 Reviewer 重新審視：

### 1. 歷史脈絡：`NisaContextBuilder` 不讀 `monthlyEquivalentAmount` 是 `Fixed-149` 的刻意設計
在過去的早期實作中，`NisaContextBuilder` 確實曾使用過 `setting.map(s -> s.getMonthlyEquivalentAmount() * 12L)`。然而在 `Fixed-149.md` 中，團隊發現直接用「每月等值金額 × 12」去粗略推算年投入額，會對「毎週提繳」（1年有52週，若粗暴以 4週×12月=48週 換算）造成約 7.7% 的系統性低估，在30年長週期複利下會產生嚴重偏差。

因此，`Fixed-149` 制定了「雙軌日曆分流法（Dual-Track Calendar Flow）」：
- 提繳年化精確計算：直接由事實欄位 `settingAmount` 依真實日曆頻率分流（`毎週 × 52`、`毎日 × 240`、`毎月 × 12`）。
- 刻意保留 `monthlyEquivalentAmount`：在 `Fixed-149.md` 第 79-80 行明確記載——**「保留 monthlyEquivalentAmount 欄位：不棄用 monthlyEquivalentAmount，繼續作為 DTO 與前端表格顯示參考值，不影響任何現有前端畫面。」**
- 結論：`NisaContextBuilder` 改用 `settingAmount` 計算年化額度是為了解決精度問題的刻意修正，**並非遺漏呼叫**；`monthlyEquivalentAmount` 在設計上就是 Parser 從對帳單爬出事實後計算的「純展示用事實欄位（Fact-derived Display Field）」。

### 2. 原報告「方向 A」與「方向 B」的架構缺陷
- **方向 B（讓模擬引擎改回讀取 `monthlyEquivalentAmount * 12`）**：
  這將直接抹煞 `Fixed-149` 的成果，造成嚴重 Regression，讓每週/每日提繳的模擬精度再次出現 7~8% 的複利偏差。
- **方向 A（PATCH 時反推覆寫 `settingAmount`）**：
  `settingAmount`、`course` 與 `monthlyEquivalentAmount` 均為 SBI 對帳單匯入解析出的「事實資料」。根據 `issue-180` 的事實合併原則（`NisaContributionSetting.merge()`），每次使用者重新匯入對帳單時，解析出的事實數值會直接無條件覆蓋現有欄位。因此，透過 API 反算覆寫 `settingAmount` 不僅破壞了事實資料的真實性，且在下次匯入對帳單時會直接被抹除。

### 3. 全局模擬架構的實際運作（Sandbox、GA、Grid）
追查整個後端模擬模組，提繳金額的流向如下：
1. **Sandbox 模擬**：最早出現的自訂沙盒。`settingAmount` 僅作為一開始初始化的「預設基準」。使用者在畫面上要調整金額時，是透過前端的 `SimulationOverrides` 傳入自訂數值，完全不依賴資料庫內的 `monthlyEquivalentAmount`。
2. **GA（基因演算法）全局最佳化**：由最佳化引擎自身的搜尋空間（0 ~ 200k/100k）主動生成 `testMonthlyContribution`，根本不需要使用者手動輸入設定值；資料庫內的提繳設定僅用來計算既有比例權重（`fund.getAnnualContribution() / totalCurrentAnnual`）。
3. **Grid Search 局部最佳化**：同 GA，由演算法搜尋空間驅動，不依賴使用者在設定頁手動修改數值。
4. **NISA 專頁圖表投影（`NisaProjectionServiceImpl`）**：僅作為現有已累積本金的被動複利試算，傳入的年投入額直接寫死為 `0L`。

### 4. 真正問題的根源與建議修正方向
本問題的核心不在於「後端模擬引擎少讀了一個欄位」，而在於 **「前端 UI 殘留了過時的編輯行為與誤導性文案，且後端殘留了一個對事實欄位進行無效修改的 PATCH 端點」**：
1. **前端誤導**：`nisa-contribution-settings.tsx` 殘留了 `InlineCurrencyEditor`，且文案宣稱「Values configured here directly drive 30-year projections」，誤導使用者以為可以在此編輯並驅動引擎。
2. **無效端點**：`PATCH /assets/nisa/settings/{id}/allocation` 允許修改展示用事實欄位，但既無法反映於對帳單事實，也無法影響已採用雙軌日曆分流的精確引擎。

**建議的正確修正方向**：
- **前端調整**：將 NISA 設定頁的「Monthly Contribution」改為純唯讀顯示（Read-only），拿掉 `InlineCurrencyEditor`，並修正「Values configured here...」這句不符現況的文案。
- **後端端點清理**：移除或標記廢棄（Deprecated）`PATCH /assets/nisa/settings/{id}/allocation` 端點，避免對帳單事實欄位遭受無效修改。
- **使用者自訂需求導流**：若使用者希望嘗試不同提繳金額對未來資產的影響，引導其至「Sandbox 模擬」頁面進行 Overrides 調整，而非在歷史對帳單設定頁竄改數值。

---
**請 Reviewer（Claude）參照上述 Fixed-149 的歷史演進脈絡與系統模擬架構重新審視，評估是否將 issue 177 的處理重心由「修改後端計算欄位」調整為「清理前端誤導 UI 與廢棄無效 PATCH 端點」。**

### 5. 延伸探討：未勾選「最佳化 NISA」時提繳額歸零的現象（依賴的核心商務假設分析）

在追蹤模擬引擎提繳金額流向時，我們注意到一個值得深入探討的行為：當使用者在全局最佳化時**未勾選「最佳化 NISA」**，現有對帳單中的定期定額提繳額會在模擬中被改寫為 `0`。

這究竟屬於「需要修正的 UX/邏輯問題」還是「架構上的 By Design」，**完全取決於系統底層對「未勾選項目」的核心商務假設**。特此將完整程式碼事實與兩種商務假設的推演補充如下，供 Reviewer 與團隊評估：

#### 程式碼事實鏈條（真實行為）：
1. **最佳化種子建構**（`GlobalOptimizerServiceImpl.java:240-258`）：
   當未勾選最佳化 NISA（`!config.optimizeNisa()`）：
   ```java
   boolean nisaLocked = (config == null || !config.optimizeNisa());
   seed.setNisaGrowthTargetLocked(nisaLocked);
   seed.setNisaTsumitateTargetLocked(nisaLocked);

   if (nisaLocked && applyProfileToOptimizerBaseline) {
       Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
       if (nisaSettings.isPresent()) {
           Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
           Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
           seed.setNisaGrowthTarget(growthOpt != null ? growthOpt : 0L);
           seed.setNisaTsumitateTarget(tsumitateOpt != null ? tsumitateOpt : 0L);
       } else {
           seed.setNisaGrowthTarget(0L);
       }
   } else {
       seed.setNisaGrowthTarget(0L);
       seed.setNisaTsumitateTarget(0L);
   }
   ```
   - 若未啟用 `applyProfileToOptimizerBaseline`，種子直接填入 `0L`。
   - 若啟用了，但使用者未在 Profile（`NisaUserSettings`）填寫大於 0 的金額，讀出來依然是 `0L`（因 DB 預設為 `0L`）。
2. **覆寫映射與執行**（`ChromosomeMapper.java:76-78` $\rightarrow$ `FundOverrideApplier.java:100-128`）：
   種子中的 `0L` 被封裝進 `SimulationOverrides`。進入模擬前，`FundOverrideApplier` 會針對現有基金執行：
   - **既有持股市值（Balance）**：**完整保留，不受影響**，在 30 年模擬中照常以預期報酬率複利增長。
   - **定期定額年提繳額（Annual Contribution）**：被執行 `fund.setAnnualContribution(0)`，**強制改寫為 0**（等同於停扣）。

---

#### 核心關鍵：此現象是否成立為「問題」，取決於商務假設

這個現象是否為問題，取決於系統對使用者「未勾選」行為的頂層商務哲學：

##### 【假設 A】：現實生活延續思維（既定投資約束假設）
- **商務意圖**：使用者在多維度最佳化介面上「未勾選 NISA」，真實預期是：**「請維持我目前現實生活的扣款習慣與財務紀律，把它當作固定的背景條件，在此基礎上去幫我尋找最佳房貸還款時機或退休年齡。」**
- **若假設 A 成立 $\rightarrow$ 內文問題成立，需要修正 UX 與傳遞邏輯**：
  若使用者每月實際在扣款 5 萬（年額 60 萬），但系統在最佳化時將提繳額歸零，等同於在模擬世界中「強制停扣 NISA」。這會釋放每年 60 萬的現金流，演算法會誤以為使用者手頭有大筆閒錢而給出過於激進的房貸提前還款方案，造成評估基礎失真。此時必須修正 Baseline 機制，在未勾選時沿用對帳單事實或傳入 `null` 跳過覆寫。
- **若採假設 A，建議的修正程式碼（直接讓路給早就做好 Fallback 的 `SimulationData`）：**
  在 `GlobalOptimizerServiceImpl.java:240-258` 中，未最佳化時若 Profile 沒填，**直接不呼叫 `seed.set...`，讓欄位自然保持為 `null`**。轉成 `SimulationOverrides` 時即為 `null`，下游 `FundOverrideApplier` 會判定跳過覆寫，模擬器即可 100% 沿用 `SimulationData` 早就建構好的對帳單扣款事實：
  ```java
  // NISA Target
  boolean nisaLocked = (config == null || !config.optimizeNisa());
  seed.setNisaGrowthTargetLocked(nisaLocked);
  seed.setNisaTsumitateTargetLocked(nisaLocked);

  if (nisaLocked && applyProfileToOptimizerBaseline) {
      Optional<NisaUserSettings> nisaSettings = nisaUserSettingsRepository.findFirstBy();
      if (nisaSettings.isPresent()) {
          Long growthOpt = nisaSettings.get().getMonthlyGrowthContribution();
          Long tsumitateOpt = nisaSettings.get().getMonthlyTsumitateContribution();
          // 只有 Profile 實質有填（> 0）時才設定目標，否則不 set（保持 null）！
          if (growthOpt != null && growthOpt > 0) {
              seed.setNisaGrowthTarget(growthOpt);
          }
          if (tsumitateOpt != null && tsumitateOpt > 0) {
              seed.setNisaTsumitateTarget(tsumitateOpt);
          }
      }
  }
  // 註：未 set 時 seed 身上的 target 保持 null -> Overrides 為 null -> FundOverrideApplier 跳過覆寫
  // -> 模擬引擎直接拿 SimulationData 早就做好 Fallback 的對帳單事實！
  ```

##### 【假設 B】：純淨預算模型思維（顯式規劃承諾假設）
- **商務意圖**：對帳單扣款只是「當下的短期事實」，不代表往後 30 年的長期財務規劃。30 年最佳化看的是「使用者明確承諾的長期預算（Profile）」。投資額與年齡不同，無法由法規給定系統預設值（不像法定退休年齡有明確的 65 歲預設），因此在系統設定層級上，沒有預算承諾即代表預設 0 投入。
- **若假設 B 成立 $\rightarrow$ 內文問題不成立，現狀完全為 By Design**：
  使用者未在 Profile 自訂長期 NISA 預算，又未在最佳化中開啟 NISA 探索，系統以純淨的「無投資投入（0L）」作為標準 Baseline 運算，在架構與邏輯上完全自洽。此時問題不在後端代碼，而是前端 UX 是否有清晰告知使用者「未在 Profile 設定長遠目標時，最佳化將以 0 投入為基準」。

---

**請 Reviewer（Claude）與團隊參照上述事實與假設進行審視：**
評估系統整體的產品定位究係採用「假設 A」還是「假設 B」，進而裁定是否需要將未勾選 NISA 時的處理方式納入 UX 或邏輯修正範疇。
