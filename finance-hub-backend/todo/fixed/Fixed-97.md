# Fixed 97：NISA成長枠與定期定額枠合計試算完後沒套用總額度上限，本金可能超過1800萬終身免稅額

## 嚴重程度
High — 這個問題會讓模擬結果偷偷超過日本NISA法定的1800萬終身免稅上限，而且不只是「累積NISA本金」這一個數字失真，連使用者最關心的「淨資產」走勢也會被一起墊高。這個系統的核心用途是判斷「現金會不會撐不到退休/會不會因為房貸現金破產」，NISA累積本金與淨資產又是每一年模擬結果的基礎，一旦這個基礎數字被系統性墊高，後面所有年度的推算（還能不能再投入、退休後現金夠不夠用）都會建立在一個比真實情況更樂觀、但實際上不合法的數字上。尤其容易發生在「過去已經大量使用定期定額枠、整體終身額度快用完」的使用者身上，這正是長期使用NISA的重度用戶最常見的狀態。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/TaxAdvantagedAccountProjector.java`（問題發生的主要檔案：這裡負責算出「今年NISA最多能投入多少現金」，並把最終金額實際記到每一筆基金餘額與帳戶累積本金上）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/InvestmentContributions.java`（`getTotalEmployeeInvestments()` 把NISA投入金額算進「今年現金流要扣掉多少」，用來佐證現金被扣掉的金額，跟基金餘額實際被加上的金額不一致）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`（`applyCashflowAndSettlement()` 把NISA餘額直接加進當年度淨資產，佐證本金金額一旦失真，畫面上看到的淨資產也會跟著失真）

## 現狀
NISA（少額投資免稅制度）有一個所有帳戶共用的終身免稅本金上限：1800萬日圓（其中「成長投資枠」自己另外還有1200萬的子上限）。當使用者過去已經透過「定期定額（つみたて）」枠投入了很多錢，導致整個帳戶剩下的終身免稅額度空間已經不多時，如果同一年又同時對「成長投資」與「定期定額」兩種基金分別投入資金，系統確實會先正確算出「這一年全帳戶總共還能投入多少現金」（例如整帳戶只剩200萬空間），並且用這個受限制的金額去扣使用者的現金。

但問題是：實際被「記到」每一筆基金餘額、以及畫面上「累積NISA本金」這個數字裡的金額，卻仍然是完全沒有被這個200萬總額度限制過的原始金額（例如成長240萬+定期定額120萬=360萬）。也就是說，使用者的現金確實只被扣了合法允許的金額，但基金餘額與累積本金卻被灌入了更大的、不合法的金額。結果就是：使用者在畫面上看到的「累積NISA本金」、淨資產，以及之後每一年繼續模擬出來的結果，都會比日本法規真正允許的1800萬終身上限還要高——系統悄悄讓模擬結果超過真實法律上限，而且因為現金只被扣了合法的部分、基金餘額卻被灌入更多，等於在模擬裡「無中生有」多出一筆資產，讓淨資產被系統性高估，不只是本金數字超標而已。

## 原因
在 `TaxAdvantagedAccountProjector.processAccountsForYear()` 裡：

1. 第104-112行，程式先分別算出「成長投資枠」與「定期定額枠」各自允許投入多少（`allowedGrowth`、`allowedTsumitate`），各自只對照自己的年度上限與自己那塊的終身剩餘額度做限制：

```java
long allowedGrowth = Math.max(0L, Math.min(intendedGrowth,
        Math.min(TaxConstants.NISA_GROWTH_ANNUAL_LIMIT, growthLifetimeRemaining)));
long allowedTsumitate = Math.max(0L, Math.min(intendedTsumitate,
        Math.min(TaxConstants.NISA_TSUMITATE_ANNUAL_LIMIT, tsumitateLifetimeRemaining)));
```

2. 第117-120行，把兩者加總後，再用「整個帳戶剩餘的終身免稅額度」做第二次限制，得到這一年真正、最終允許投入的合計金額 `combinedAllowed`：

```java
long combinedAllowed = allowedGrowth + allowedTsumitate;
long totalLifetimeRemaining = TaxConstants.NISA_LIFETIME_LIMIT - context.getAccumulatedNisaPrincipal();
combinedAllowed = Math.max(0L,
        Math.min(combinedAllowed, Math.min(TaxConstants.NISA_ANNUAL_LIMIT, totalLifetimeRemaining)));
```

第129行 `annualNisa = combinedAllowed;` 之後正確地把這個「已受總額度限制」的金額，當成這一年NISA現金流出的金額使用（後續會被 `InvestmentContributions.getTotalEmployeeInvestments()` 加總、從現金裡扣掉）。**到這裡為止，「這一年可以花多少現金」的數字是對的。**

3. 但問題出在第124-125行：用來決定「實際要記到每一筆基金餘額」的比例係數 `growthScale`、`tsumitateScale`，是拿「combinedAllowed限制之前」的 `allowedGrowth`／`allowedTsumitate` 去算的，完全沒有引用 `combinedAllowed`：

```java
double growthScale = intendedGrowth > 0 ? (double) allowedGrowth / intendedGrowth : 0.0;
double tsumitateScale = intendedTsumitate > 0 ? (double) allowedTsumitate / intendedTsumitate : 0.0;
```

4. 第150-164行的「實際投入」迴圈，把每一筆NISA基金依它的類型套用這兩個「未經combinedAllowed修正」的scale，算出 `annualContribution` 後直接加進 `newNisaPrincipal`／`newGrowthNisaPrincipal`：

```java
double scale = (fund.getNisaAccountType() == NisaAccountType.TSUMITATE)
        ? tsumitateScale
        : growthScale;
long annualContribution = (long) (intended * scale);

newNisaPrincipal += annualContribution;
if (fund.getNisaAccountType() != NisaAccountType.TSUMITATE) {
    newGrowthNisaPrincipal += annualContribution;
}
```

第178-180行把這兩個值寫回 `context.setAccumulatedNisaPrincipal`／`setAccumulatedGrowthNisaPrincipal`，也就是畫面上「累積NISA本金」的來源。

當「成長」與「定期定額」各自的年度/自己那塊終身子額度都沒被觸發（也就是 `allowedGrowth == intendedGrowth`、`allowedTsumitate == intendedTsumitate`，兩個scale都等於1.0）、但兩者加總後仍超過整帳戶剩餘的終身額度時，`combinedAllowed` 會正確變小，但 `growthScale`／`tsumitateScale` 卻完全沒有跟著變小——「現金該扣多少」跟「本金該記多少」這兩個數字從此對不起來。

另外，`InvestmentContributions.getTotalEmployeeInvestments()`（`finance-hub/finance-hub-backend/hub-app/src/main/java/.../model/InvestmentContributions.java:31-33`）把 `annualNisaContribution`（也就是已受限制的 `combinedAllowed`）算進今年現金流的扣除項，而 `YearlySimulationPipeline.applyCashflowAndSettlement()`（同專案 `pipeline/YearlySimulationPipeline.java:305-306, 315-316`）用這個扣除後的現金去更新 `currentLiquidCash`，再把 `context.getNisaBalance()`（已經灌入未受限制金額的基金餘額）直接加進 `currentNetWorth`。也就是說現金只被扣了合法的 `combinedAllowed`，但NISA餘額卻多加了「原始意圖金額 - combinedAllowed」這一筆差額，這筆差額在淨資產計算裡等於是憑空多出來的資產，不只是本金數字超過1800萬終身上限，淨資產本身也會被墊高。

## 具體錯誤情境
假設使用者過去已經透過定期定額枠累積了1600萬本金（成長枠本金假設為0），當年度使用者又設定成長投資枠投入240萬、定期定額枠投入120萬（皆未超過個別年度上限240萬／120萬）：

- `allowedGrowth` = min(240萬, min(240萬, 1200萬-0)) = 240萬（個別子上限沒被觸發）
- `allowedTsumitate` = min(120萬, min(120萬, 1800萬-1600萬=200萬)) = 120萬（個別子上限沒被觸發）
- `combinedAllowed` = min(240萬+120萬=360萬, min(360萬, 1800萬-1600萬=200萬)) = **200萬**（正確受到整帳戶總額度限制，這是這一年真正合法能投入的金額）
- `growthScale` = 240萬/240萬 = 1.0，`tsumitateScale` = 120萬/120萬 = 1.0（因為兩個係數都是用限制前的allowedGrowth/allowedTsumitate算的，沒有被縮小）
- 實際記到基金餘額與累積本金的金額仍是240萬+120萬=**360萬**，導致 `newNisaPrincipal` = 1600萬+360萬 = **1960萬**，超過1800萬終身上限160萬
- 同時，這一年現金只被扣了合法的200萬，但NISA餘額卻多加了360萬-200萬=160萬，這160萬會直接墊高當年度算出來的淨資產，等於淨資產也被高估了160萬

## 建議修正
這個問題本質上是「係數計算的時間點不對」：`growthScale`／`tsumitateScale` 是在 `combinedAllowed`（總額度限制）算出來之前就定案了，之後沒有再被總額度限制修正過。以下三種方向都能解決，但各有取捨，不應由開發端片面決定：

**方向A：把combinedAllowed算完後，回頭等比例縮減allowedGrowth/allowedTsumitate，再用縮減後的值算scale**
- 優點：改動範圍小，能保證不論怎麼分配，最終記到本金的金額都不會超過總額度上限。
- 缺點：當總額度不夠讓兩邊都滿足原本的意圖時，「該怎麼縮減」本身是一個分配規則的選擇（例如等比例縮減 vs. 其他縮減方式），不同規則會讓「今年成長枠、定期定額枠各自實際投入多少」的結果不一樣，這個規則要不要讓使用者知道或選擇，需要額外考慮。

**方向B：改成「優先順序」分配，先把其中一種額度用滿，剩下的總額度才給另一種用**
- 優點：邏輯直覺、容易向使用者說明，未來也容易延伸成讓使用者自己選擇優先順序的選項。
- 缺點：「該優先用哪一種（成長優先或定期定額優先）」沒有標準答案，這是要不要讓使用者自己選、或系統要預設一個順序的產品/UX取捨，不應該由開發端單方面決定成唯一正確做法。

**方向C：在整年度投入跑完後，額外加一層事後檢查，把累積本金依比例往下修正**
- 優點：確保 `accumulatedNisaPrincipal` 永遠不超過1800萬、`accumulatedGrowthNisaPrincipal` 永遠不超過1200萬，即使之後有其他計算路徑漏改，這道防線都能兜底。
- 缺點：只是補救最終結果，沒有解決「係數計算時間點不對」這個根本原因，也無法一併修正前面提到的「現金扣減金額」與「本金/淨資產灌入金額」不一致的問題，不建議只做這一項，應搭配方向A或方向B一起處理。

## 與已知項目的區別
`todo/wontfix/FINDING-23-nisa-no-per-fund-reinvestment-cap.md` 討論的是另一個議題：NISA終身額度目前是「帳戶層級」統一計算，沒有針對「每一筆個別基金」各自的上限做檢查——那是一個關於「額度該不該切到per-fund層級去管」的產品設計取捨，已被列為WONTFIX。

本問題完全不涉及要不要切到per-fund層級，而是討論「即使承認額度是帳戶層級的總額度，程式碼本身有沒有把這個總額度正確套用到最後實際記帳的本金金額上」——這是一個純粹的實作錯誤（係數計算引用了限制前而非限制後的數值），跟FINDING-23討論的範疇無關，也沒有在PLANNED或其他WONTFIX清單中被記錄過，屬於全新發現的問題。

## 決策與結論 (Decision & Conclusion)
經過討論，決定採用 **方向 A（等比例縮減）** 作為最終的修復方案。

**決策理由：**
1. **符合產品定位**：資產模擬與 GA 的核心目標是宏觀優化「每年總投資金額與現金水位」，而非微觀決定每一檔單筆基金的分配比例。因此，使用數學上無偏袒的等比例縮減（方向 A）作為內部固定規則，是架構上最合理且單純的選擇。
2. **避免維度爆炸**：如果改走方向 B 或讓 GA 甚至使用者去精細調控優先順序，將導致最佳化的搜尋空間維度爆炸，不僅複雜度飆升，GA 也極可能永遠找不到最佳解。
3. **最小改動範圍**：方向 A 只需要在計算 `growthScale` 與 `tsumitateScale` 之前，在總額層級進行比例壓縮，完全不需改動下方分配給單筆基金的複雜迴圈。

**關於小數點捨去造成「無法精準達到 1800 萬」的說明：**
在實作方向 A 時，因為單筆基金在乘上比例後會經過 `(long)` 強制轉型（無條件捨去），導致最終累積本金加總時，可能會與 1800 萬的絕對上限存在 1~2 日圓的微小落差（例如停在 `17,999,998`）。
此落差被確認為 **「預期範圍內的可接受現象 (Expected Behavior)」**。考量如下：
- **實務合理性**：在真實世界的投資中，幾乎不會有人能夠剛好把終身免稅額度投資到一塊錢都不差。
- **影響微乎其微**：就算加上 30 年的複利，這 1~2 日圓的落差對最終幾千萬日圓的淨資產推演來說，實質影響趨近於零。
- **架構取捨**：不刻意在單筆基金迴圈裡去斤斤計較這幾塊錢的零頭，可以換取更乾淨、風險更低的最小範圍程式碼改動。

## 交接報告給 Claude (Handover Report)
Claude 你好，本 Issue (Fixed-97) 已經由 Gemini 協助開發者完成修復與決策。以下是修復重點與現狀交接：

1. **Bug 核心與修復方案**：原本 `TaxAdvantagedAccountProjector.java` 中 `growthScale` 與 `tsumitateScale` 引用的分子未經過 `combinedAllowed` 上限壓縮，導致單筆基金投資加總後超出 1800 萬。我們已採用「方向 A：等比例縮減」修復此問題。
2. **實作細節**：在算出 `combinedAllowed` 後，若原始總和超出上限，則使用整數運算 `(allowedGrowth * combinedAllowed) / (allowedGrowth + allowedTsumitate)` 將額度重新按比例分配，並將修正後的額度放回 Scale 的分子中。
3. **架構決策 (Expected Behavior)**：為了維持程式碼簡潔並避免更動底層的單筆基金分配迴圈，我們**刻意不處理** `(long)` 強制轉型時所造成的 1~2 塊錢零頭捨去。這意味著最終總投資額可能會停在 `17,999,998` 這樣微幅小於 1800 萬的數字，這屬於**產品方確認接受的預期行為 (Expected Behavior)**，請後續維護或撰寫測試案例時，不要將此落差視為 Bug。
4. **當前狀態**：核心代碼已實作完畢，目前由開發者進行本地自動化測試 (`mvn`/`gradle`) 與 Code Review。後續若您需要處理其他 NISA 相關任務，請將此修復邏輯與決策列為既定基準 (Ground Truth)。
