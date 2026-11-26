# Fixed 87：名下有兩筆房貸時，已手動排提前還款的那一筆會讓另一筆完全被跳過優化搜尋；且GA的房貸提前還款資料完全來自API請求本身，執行最佳化搜尋時完全不會忽略呼叫者夾帶的數值

## 修正說明（2026-09-15，第二次修正）
上一版「建議修正」提出的方向A——把房貸提前還款也改成跟其他4個維度一樣、去資料庫查一個「使用者目前實際儲存的房貸提前還款計畫」來當備援——已確認整個方向是錯的，理由如下：

退休年齡／請領年金年齡／iDeCo提領年齡／NISA每月投入金額這4個維度之所以需要查資料庫，是因為「鎖定」（不參與最佳化）這件事，語意上代表「維持使用者目前真實的設定」，而這4個維度都各自有一個明確、單一的「使用者目前真實設定值」存在資料庫裡（個人資料表的退休年齡、NISA設定表的每月投入金額……）可以查。

房貸提前還款不是這樣。使用者提到：GA最佳化跟Sandbox手動試算，對「房貸」這件事的處理方式本來就是刻意不同的兩套邏輯——**開啟房貸最佳化，就是不管使用者過去手動排過什麼提前還款計畫，直接找出名下所有房貸「從零開始」的最佳提前還款組合；沒開啟，語意就是最單純的「慢慢還到底」（正常攤還、完全不提前還款），不是去資料庫比對使用者手動排的計畫是否一致。** 這是一個固定、寫死就對的預設值，根本不需要查資料庫——資料庫裡並不存在一個叫「GA最佳化的正確基準值」的東西可以查。上一版建議修正在這一點上想錯了方向，已撤回。

正確的修正方向是：`buildSeedChromosome` 建構房貸提前還款種子時，不論這個維度有沒有被勾選要最佳化，都**完全不要讀取 `baseOverrides.mortgageEarlyRepayments()`**——開啟最佳化時，一律把「使用者名下的每一筆房貸」都放進種子地圖、初始值是空清單（代表從零開始搜尋）；沒開啟時，也一律是空地圖（代表零提前還款，即「慢慢還到底」）。兩種情境都不需要、也不應該去讀取呼叫者在API請求裡送來的任何房貸提前還款數字。

## 嚴重程度
Medium——目前實際上線的畫面（`use-global-optimizer.ts`）送出的請求裡沒有 `baseOverrides` 欄位，所以下面描述的行為目前都還無法透過畫面觸發；但這不影響修正的必要性——使用者明確要求的是程式碼本身要有「一律不理會API送來的房貸數值」這個結構性保障，不是「現在沒人這麼送所以不用管」。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java`（第162-248行：`buildSeedChromosome`；第229-245行是房貸提前還款種子的建構邏輯，也是本問題的根源；第250-262行：`extractMortgageGenes`，目前唯一會讀取 `baseOverrides.mortgageEarlyRepayments()` 的地方）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/mapper/ChromosomeMapper.java`（第40-70行左右：把搜尋結果轉換回使用者可見的模擬覆寫設定；下方「延伸檢查」會說明這裡跟本問題修正方向的關係）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java`（第93行起：`mutate`；第133行起：`crossover`——已確認 `mutate` 會依 `mortgageRepaymentsLocked` 決定是否調整房貸基因，`crossover` 則沒有這個判斷，一律交換房貸基因；下方「延伸發現」會說明這一點）
- `hub-ui/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts`（第14-17行：確認目前實際上線畫面的 `GlobalOptimizerRequest` TypeScript介面只有 `config`／`strategy` 兩個欄位，沒有 `baseOverrides`——這是本問題目前無法透過畫面觸發的原因）

## 現狀
`buildSeedChromosome` 目前的房貸提前還款種子建構邏輯：

```java
if (config != null && config.optimizeMortgage()) {
    seed.setMortgageRepaymentsLocked(false);
    Map<Long, List<MortgageRepaymentGene>> genes = extractMortgageGenes(baseOverrides);
    if (genes.isEmpty()) {
        // Initialize empty arrays for all mortgages so GA can start mutating (adding)
        if (baseData.getAllMortgages() != null) {
            for (MortgageRecord m : baseData.getAllMortgages()) {
                genes.put(m.getId(), new ArrayList<>());
            }
        }
    }
    seed.setMortgageRepayments(genes);
} else {
    seed.setMortgageRepaymentsLocked(true);
    seed.setMortgageRepayments(extractMortgageGenes(baseOverrides));
}
```

不論「開啟房貸最佳化」還是「沒開啟」，這段邏輯的唯一資料來源都是 `extractMortgageGenes(baseOverrides)`——也就是呼叫這個API的人在請求裡直接送的數字。這造成兩個子問題：

**子問題一：開啟最佳化時，只要API請求裡有夾帶任何一筆房貸資料，其他房貸就會被整段跳過，永遠不會被搜尋到**

`if (genes.isEmpty())` 判斷的是「整張地圖是不是完全空的」。只要呼叫者送了任何一筆房貸的提前還款資料（讓地圖不是空的），後面「幫每一筆房貸都補上空清單」的邏輯就會被整段跳過——沒被送到的房貸，永遠不會被加進搜尋地圖。而 `Chromosome.mutate()`／`crossover()` 都只會調整地圖裡已經存在的房貸ID，不會主動新增地圖裡沒有的房貸ID，所以這筆被漏掉的房貸，之後整個演化搜尋過程中都不會被嘗試任何提前還款組合。

**子問題二：不論開啟或沒開啟最佳化，最終結果都完全等於API請求裡送來的數字，從來沒有被「一律忽略」**

沒開啟最佳化時（`seed.setMortgageRepaymentsLocked(true)`），`seed.setMortgageRepayments(extractMortgageGenes(baseOverrides))` 這個值會被鎖定、不會被 `mutate`／`crossover` 調整，原封不動地透過 `ChromosomeMapper.toOverrides()` 變成最終結果的一部分直接回傳——如果呼叫者送的數字剛好符合「慢慢還到底」的預期（也就是完全沒送房貸資料），現在的結果不會出錯；但只要呼叫者送了任何房貸提前還款數字，這些數字就會直接變成最終的「最佳化結果」，而不是被忽略、以「零提前還款」取代。開啟最佳化時（子問題一沒有跳過的那筆房貸），也是拿 `baseOverrides` 裡送來的數字當搜尋起點，而不是一律從空清單開始搜尋。

兩個子問題的根源是同一件事：`extractMortgageGenes(baseOverrides)` 在任何情境下都不應該被呼叫，因為房貸提前還款這個維度，語意上就是「一律不理會API送來的數值」，不像其他4個維度那樣需要讀取「呼叫者想覆寫成什麼」。

## 延伸發現：對這個角度重新檢查後，有沒有另外的bug？

依照使用者的指示，針對「GA執行時是否有任何地方會被API請求裡的數值影響搜尋結果」這個角度，重新檢查了 `GlobalOptimizerServiceImpl`／`Chromosome`／`ChromosomeMapper`／`GridSearchEngine`／`GeneticAlgorithmEngine` 全部程式碼，結果如下：

1. **確認：`ChromosomeMapper.toOverrides()` 裡「Grid Search單一還款年橋接邏輯」原本疑似有的地圖鍵值遺漏問題，會被這次的修正自動解決，不需要另外處理。**

   `ChromosomeMapper.toOverrides()` 裡有一段邏輯，會把Grid Search用的單一還款年（`gridSingleMortgageRepaymentYear`）轉換成「全額還清」的覆寫，但只會套用在 `mortgageOverrides`（一張從 `chromosome.getMortgageRepayments()` 建出來的地圖）裡**已經存在**的房貸ID上：

   ```java
   for (Long mortgageId : mortgageOverrides.keySet()) {
       mortgageOverrides.computeIfAbsent(mortgageId, k -> new ArrayList<>())
               .add(new SimulationOverrides.MortgageEarlyRepaymentOverride(legacyYear, Long.MAX_VALUE));
   }
   ```

   原本擔心：如果某一筆房貸從頭到尾都沒有被加進 `mortgageOverrides` 地圖裡當作鍵值，這筆房貸就永遠不會被套用Grid Search的還款年。但只要採用上面「建議修正」的做法——開啟最佳化時一律把「使用者名下的每一筆房貸」都放進種子地圖（即使是空清單）——`chromosome.getMortgageRepayments()` 從一開始就會包含所有房貸ID作為鍵值，這裡描述的遺漏情況就不會發生。**這點已確認會隨著本次修正一起解決，不需要另外開一張issue。**

2. **另外發現一個範圍更廣、但嚴重度更低的類似模式：其他4個可最佳化維度，一樣是「API請求裡的值優先於一切」，只是影響小很多**

   重新檢查 `buildSeedChromosome` 裡退休年齡／請領年金年齡／iDeCo提領年齡／NISA每月投入金額這4個維度的程式碼，發現一個共同模式：不論這個維度是否被勾選要最佳化（鎖定與否），只要 `baseOverrides` 裡有送對應的覆寫值，這個值都會被最優先採用，資料庫查詢（`applyProfileToOptimizerBaseline`機制）只有在 `baseOverrides` 完全沒送值、且該維度被鎖定時才會被使用。也就是說，如果呼叫者在API請求裡對一個「已勾選要最佳化」的維度送了一個值，這個值會被當成搜尋種子的起點，而不是像房貸這次被要求的那樣完全不理會。

   這跟本問題房貸的情況是同一種「API請求裡的值可以影響GA搜尋」的模式，但實際風險小很多，原因是這4個維度的搜尋空間都是很小的固定範圍（例如退休年齡通常是16-31歲之間幾個離散值），GA／Grid Search幾乎可以窮舉整個範圍，就算起點被API送來的值影響，最終還是幾乎一定會找到全域最佳解；但房貸的搜尋空間是開放式的「任意年份、任意金額組合」，起點被夾帶的數值污染，會直接影響最終收斂到哪一個局部最佳解，實際影響大很多。

   這是否也需要一併修成「一律忽略API送來的值」，屬於產品面的取捨（要不要把這個底線原則套用到全部5個維度，還是只套用在房貸這個目前唯一被提出的問題上），這裡先如實記錄發現，不代為決定；如果要修，做法跟房貸完全一樣——把「先看`baseOverrides`有沒有給值」這一步整段移除即可。

3. **`Chromosome.crossover()` 沒有像 `mutate()` 一樣依 `mortgageRepaymentsLocked` 判斷是否要交換房貸基因**——`mutate()`（第93行）明確用 `if (!mortgageRepaymentsLocked)` 保護，鎖定時完全不觸碰房貸基因；但 `crossover()`（第133行起）只複製了 `mortgageRepaymentsLocked` 這個旗標本身，交換房貸基因的邏輯卻沒有依這個旗標判斷，一律執行交換。目前這件事不會造成實際錯誤，因為鎖定時兩個parent的房貸基因值本來就完全相同（都是同一份沒被`mutate`碰過的種子值）交換誰跟誰沒有差別；但這是程式碼結構上的不一致，如果之後房貸鎖定時的種子值有機會在parent之間出現差異（目前沒有這種情境），就會變成一個真正會影響結果的bug。這裡如實記錄，是否要現在就補上這個判斷（即使目前不會造成錯誤）也屬於程式碼健壯性的取捨，一併記錄供參考。

## 具體錯誤情境
使用者名下有房貸A（自住房，已經手動設定了一筆提前還款計畫，例如「2030年多還300萬元」）跟房貸B（投資用房產，完全沒有任何手動計畫）。如果未來有新的畫面、或有其他直接呼叫後端的方式，把使用者目前的完整計畫（包含房貸A的手動紀錄）一併送進最佳化搜尋：
- 開啟房貸最佳化時，房貸B會完全沒有被補進搜尋地圖裡，最終算出來的「最佳化方案」只會調整房貸A，房貸B會維持原封不動——不是因為AI判斷房貸B不需要提前還款，而是AI從頭到尾根本沒有檢視過房貸B。
- 沒開啟房貸最佳化時（例如這次只想最佳化NISA/退休年齡，不想動房貸），系統會直接把呼叫者在請求裡送的房貸提前還款數字當成最終結果的一部分，而不是使用者要的「慢慢還到底」這個固定預設值——如果呼叫者送的數字跟使用者的期待不一致，使用者會拿到一份包含非預期房貸資料的「最佳化結果」，而且畫面上不會有任何提示。

## 建議修正
使用者已明確給出決定：不需要資料庫比對，直接讓 `buildSeedChromosome` 完全不讀取 `baseOverrides.mortgageEarlyRepayments()`：

```java
if (config != null && config.optimizeMortgage()) {
    seed.setMortgageRepaymentsLocked(false);
    Map<Long, List<MortgageRepaymentGene>> genes = new HashMap<>();
    if (baseData.getAllMortgages() != null) {
        for (MortgageRecord m : baseData.getAllMortgages()) {
            genes.put(m.getId(), new ArrayList<>());
        }
    }
    seed.setMortgageRepayments(genes);
} else {
    seed.setMortgageRepaymentsLocked(true);
    seed.setMortgageRepayments(new HashMap<>()); // 慢慢還到底：零提前還款
}
```

這樣改之後，`extractMortgageGenes(baseOverrides)` 這個方法在 `buildSeedChromosome` 裡就不會再被呼叫（可以直接刪除，除非有其他地方還在用它——目前查到只有這裡用到）。這個改法同時解決子問題一（一律從 `baseData.getAllMortgages()` 補齊所有房貸，不會因為地圖不是空的就跳過）跟子問題二（不論鎖定與否都不會讀取API送來的數字），也讓上面「延伸發現」第1點的Grid Search橋接遺漏問題一併消失。

## 與已知項目的區別
本問題跟 `todo/planned/Plan-02-grid-search-multi-mortgage-dimension.md`（Grid Search引擎把單一房貸的還款年廣播到所有房貸上——這個限制只影響Grid Search窮舉搜尋，不影響本問題所在的遺傳演算法路徑）不同，本問題是「房貸提前還款資料從一開始就完全信任API請求」的問題，是更早發生、影響範圍也不限於Grid Search的問題。也跟已知的「Grid Search不支援2筆以上房貸」的限制不同——只要牽涉到房貸最佳化，系統就會強制使用遺傳演算法路徑（AUTO/FORCE_GA），FORCE_GRID在偵測到多筆房貸時會直接拒絕執行，所以Grid Search在這個情境下根本不會被用到，跟本問題不重疊。

## 實作報告 (2026-09-15)
- **Layer 1 大門防護**：已在 `GlobalOptimizerMapper.toCommand()` 方法上加上 `@Mapping(target = "baseOverrides", ignore = true)`，從 API 入口層徹底切斷最佳化引擎對 `baseOverrides` 的依賴。
- **Layer 2 核心防護**：已清理 `GlobalOptimizerServiceImpl.buildSeedChromosome()` 中全部 5 個維度的 `xxxOverride` 判斷，確保最佳化基準完全回歸 DB Profile 及系統全域設定；並將房貸最佳化的種子建置邏輯改為一律不讀取 API 傳入的覆寫數字。

# Walkthrough: Fixed-87 最佳化引擎兩層防護實作

我們已經順利完成了針對 `baseOverrides` 髒資料的兩層防護實作。

## 所做的變更

### 1. 外部防護：API 入口層阻絕
在 [`GlobalOptimizerMapper.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java) 中，我們對 `toCommand` 方法加上了 `@Mapping(target = "baseOverrides", ignore = true)`。
這確保了從 API 收到的前端參數在轉換為內部 Command 時，`baseOverrides` 欄位會被直接捨棄，後續的最佳化引擎根本沒有機會讀到這些被 Sandbox 用來覆寫的髒資料。

### 2. 內部防護：核心邏輯淨化
在 [`GlobalOptimizerServiceImpl.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java) 裡，我們對 `buildSeedChromosome` 進行了徹底的清理：
*   **一般維度 (退休 / 年金 / iDeCo / NISA)**：移除了所有讀取 `xxxOverride` 的邏輯。現在這些維度的種子設定，只會單純參考資料庫 Profile 或是系統全域設定。
*   **房貸維度**：移除了對 `extractMortgageGenes` 的呼叫。若啟用最佳化，一律從 `baseData.getAllMortgages()` 生成全空的房貸列表作為起始種子（完全從零開始探索最佳還款方案）；未啟用時也給予空地圖，代表「慢慢還到底」。

## 測試與後續
*   對應的實作報告已經加到了 [`Issue-40.md`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/pending%20fix/Issue-40.md) 的最底端。
*   依照您的指示，測試（包含 `mvn clean test` 編譯檢查與 `GlobalOptimizerServiceTest`）以及 `extractMortgageGenes` 方法的實體刪除，已保留給您親自執行。
