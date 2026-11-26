# Plan 24：Grid Search最佳化引擎的NISA成長型/定期定額兩個鎖定旗標，搜尋迴圈目前沒有分別檢查，只是因為唯一呼叫端恰好把兩者綁在一起設定成同值才沒出事（潛在死碼防範，性質同Plan-09）

## 來源
Source Code Review（2026-10-13）發現——跟Plan-09（DC/iDeCo解鎖年齡解耦時防範退職所得控除雙重享受）是同一種性質：架構上允許、但唯一呼叫端目前恰好不會觸發錯誤路徑的「潛在邏輯缺口」，記錄下來是為了避免未來有人觸碰這個耦合關係時，不知不覺重新引入一個真正的bug。

## 狀態
PLANNED（潛在死碼防範，目前不是會實際發生的bug）

## 相關檔案
- [GridSearchEngine.java:72-86](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/GridSearchEngine.java)（`generateCombinations`的`case 0`，NISA維度的搜尋迴圈）
- [Chromosome.java:61-65](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java)（`nisaGrowthTargetLocked`、`nisaTsumitateTargetLocked`——兩個獨立的`boolean`欄位，各自對應一個獨立的`Long`目標值）
- [GlobalOptimizerServiceImpl.java:239-241](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java)（目前唯一的呼叫端`buildSeedChromosome`，把兩個旗標設成完全相同的值）
- 對照：[Chromosome.java:145-154](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/core/Chromosome.java)（`mutate()`方法裡，GA引擎對這兩個NISA欄位是分別、各自獨立檢查`!xxxLocked`才允許變動的，這部分寫法是正確的）

## 現狀
`Chromosome`這個染色體物件，對NISA設計了兩個完全獨立的鎖定旗標：`nisaGrowthTargetLocked`跟`nisaTsumitateTargetLocked`，分別對應「成長投資型NISA每月目標」跟「定期定額型NISA每月目標」這兩個獨立的數字欄位，架構上允許「只鎖一個、放開另一個」。

但`GridSearchEngine`裡搜尋NISA這個維度的程式碼，進入搜尋迴圈的判斷條件用的是「OR」（只要其中一個沒鎖，就整個進入雙重迴圈），迴圈內部卻完全沒有再分別檢查`seed.isNisaGrowthTargetLocked()`跟`seed.isNisaTsumitateTargetLocked()`各自是不是true——不管哪一個被鎖，只要另一個沒鎖，兩個數值都會被迴圈的`growth`/`tsumitate`變數整個覆寫掉。

而目前唯一真正建構染色體的地方（`GlobalOptimizerServiceImpl.buildSeedChromosome`），兩個旗標是用同一個`boolean nisaLocked`變數同時設定的——這代表現在跑起來的實際情境裡，這兩個旗標永遠是同一個值（都true或都false），GridSearchEngine那段邏輯的OR判斷跟內部沒有分別檢查這件事，目前碰不到真正會出錯的組合。

值得注意的是，GA引擎（`Chromosome.mutate()`）對同樣這兩個欄位的處理方式是正確的，兩個欄位各自獨立判斷——「分別檢查兩個鎖定旗標」這件事在GA那一側有被正確實作，只有Grid Search這一側的搜尋迴圈漏掉了。

## 為何需要先記錄下來
一旦未來任何地方（例如使用者介面想讓使用者分別鎖定「我已經決定好每月放多少進成長型NISA，不要幫我調整這個，但定期定額那邊可以自由搜尋最佳值」這種更細緻的需求）讓這兩個旗標可以被獨立設定成不同值，Grid Search引擎會悄悄忽略使用者明確鎖定的那一個維度，把它也拿去搜尋、覆寫成任意數值——使用者原本說「這個我已經決定了，不要動」的設定，最佳化結果卻給出一個跟使用者輸入完全不同的數字，而且使用者畫面上看到的只是「這是演算法算出來最好的配置」，完全不會意識到自己明確鎖定的那個數字已經被無聲蓋掉。

這跟Plan-09記錄的性質一致：目前不是一個會實際影響使用者的bug（因為唯一的呼叫端從不產生會觸發這條錯誤路徑的輸入），但這是程式碼邏輯本身不完整、需要在任何人日後觸碰「NISA兩個鎖定旗標解耦」這個需求之前先知道的事，否則很容易在不知情的狀況下重新引入一個真實的bug。

## 建議修正方向（不要求現在就修，供未來觸碰這段耦合時參考）
在`GridSearchEngine`的NISA搜尋邏輯裡，把「分別檢查兩個旗標」這件事明確補上，讓程式碼本身在邏輯上就是完整、自洽的，不要依賴「目前唯一呼叫端恰好同步設定兩者」這個外部前提：

```java
if (!seed.isNisaGrowthTargetLocked() || !seed.isNisaTsumitateTargetLocked()) {
    for (long growth = 0; growth <= MAX_NISA_GROWTH_TARGET; growth += STEP) {
        if (seed.isNisaGrowthTargetLocked() && growth != seed.getNisaGrowthTarget()) continue;
        for (long tsumitate = 0; tsumitate <= MAX_NISA_TSUMITATE_TARGET; tsumitate += STEP) {
            if (seed.isNisaTsumitateTargetLocked() && tsumitate != seed.getNisaTsumitateTarget()) continue;
            ...
        }
    }
}
```
（具體寫法只是示意，重點是鎖定的那一維應該固定在seed本身的值，不應該跟著迴圈變動）這個改動本身不影響任何現有行為（因為現在兩個旗標永遠同值，進不去這條分支的差異化邏輯），純粹是補齊邏輯完整性，避免未來真正解耦時，這裡又變成另一個「潛伏的雙重邏輯錯誤」。

## 結論
目前不要求任何程式碼變更——這條脆弱路徑目前無法被觸及（因為`nisaGrowthTargetLocked`/`nisaTsumitateTargetLocked`永遠同值），今天的最佳化結果不受影響。這份文件存在的目的，是確保未來任何想讓這兩個NISA維度可以獨立鎖定的工作，不會透過這段已經存在、但目前死掉的程式碼，悄悄重新引入一個真正的bug。
