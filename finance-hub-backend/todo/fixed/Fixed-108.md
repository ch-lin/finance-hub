# Fixed 108：`isOptimizationMode`旗標目前完全沒有任何地方讀取，是清理不完整留下的空殼開關

## 嚴重程度
Low（純粹是程式碼整理問題，不影響任何計算結果——使用者已確認這個旗標原本要開關的機制，是因為造成過另一個bug才被刻意拿掉，不是修正意外遺失）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java:48`（`isOptimizationMode`欄位宣告，預設值`false`）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java:77`（整個專案裡唯一設定這個旗標為`true`的地方——只會在AI優化演算法評估方案時觸發，不會誤觸發到一般使用者的Sandbox模擬）

## 現狀
`isOptimizationMode`這個旗標，原本的設計意圖記錄在`todo/Epic PROGRESS.md`（Task 3-6）：目的是讓AI優化演算法在評估方案時，關掉一個「24個月生活費安全網」的下限機制，讓演算法在快要破產的邊界附近能感受到真實的現金流壓力差異，不會因為有安全網托著而算不出方案好壞的區別。

查證確認：這個旗標目前**完全沒有任何地方讀取**——整個專案搜尋不到任何呼叫`isOptimizationMode()`這個getter的地方；而當初說的那個「24個月安全網」機制本身，在`MortgageCalculator.java`（changelog裡指名的位置）裡也完全找不到任何蹤跡，全專案能找到的唯一「24個月」相關邏輯，是舊版、已確認為死碼（`todo/planned/Plan-44`）的`SimulationOptimizerServiceOldImpl`裡一個不相關的區域變數。

## 為何不是bug
使用者已經確認：這個「24個月安全網」機制本身，是因為先前造成過另一個bug，才被刻意移除的，不是在某次改動中意外被拿掉、忘記補回來的修正。也就是說，機制被拿掉是正確且刻意的決定；問題只在於，機制拿掉之後，當初用來開關這個機制的旗標（連同唯一設定它為true的那一行程式碼）沒有被一併清掉，變成一個沒有人讀取、形同虛設的空殼開關。

已確認這個旗標的設定範圍是安全的：只會在AI優化演算法內部評估時被設成true，不會外洩到一般使用者的Sandbox模擬裡，所以即使放著不管，也不會導致「使用者的真實模擬悄悄少了安全網保護」這種後果。

## 建議修正
直接刪除`isOptimizationMode`欄位本身，以及`AssetSimulationFitnessEvaluator.java:77`那一行設定它為true的程式碼。這是純粹的死碼清理，不需要另外設計或討論方向——保留一個沒人讀取、卻會讓後來維護的人誤以為它還在發揮作用的旗標，本身就是一個隱藏的誤導風險。

---

## 修正報告（2026/09/30 已清理完成）

### 1. 修改摘要
- **修改檔案 1**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java`
  - 刪除無人讀取的空殼欄位 `private boolean isOptimizationMode = false;`。
- **修改檔案 2**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java`
  - 刪除唯一賦值該旗標的死碼調用 `contextClone.getBaseContext().setOptimizationMode(true);`。

### 2. 清理效果
- 專案內已無任何 `isOptimizationMode` / `setOptimizationMode` 的殘留宣告與引用，死碼 100% 徹底清除。
- 徹底消除了過去「24個月生活費安全網」移除後殘留的空殼開關，避免後續維護人員被虛擬旗標誤導。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                           SIGINT(2) ↵  10017  12.19G   5.28    21:52:40  
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
index 51f7e599..43a201d8 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java
@@ -45,7 +45,6 @@ public class SimulationContext implements Cloneable {
     private int year;
     private int age;
     private boolean isRetired;
-    private boolean isOptimizationMode = false;
 
     private UserProfile profile;
     private List<ChildProfile> children = new ArrayList<>();
diff --git a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java
index ae49d36e..4d15bbbf 100644
--- a/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java
+++ b/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java
@@ -74,7 +74,6 @@ public class AssetSimulationFitnessEvaluator implements FitnessEvaluator {
         SimulationData contextClone;
         try {
             contextClone = baseData.cloneContext();
-            contextClone.getBaseContext().setOptimizationMode(true);
         } catch (CloneNotSupportedException e) {
             throw new IllegalStateException("Failed to clone SimulationContext", e);
         }
diff --git a/finance-hub-backend/todo/pending fix/issue-108.md b/finance-hub-backend/todo/pending fix/issue-108.md
index fe571309..ce38e78a 100644
--- a/finance-hub-backend/todo/pending fix/issue-108.md
+++ b/finance-hub-backend/todo/pending fix/issue-108.md
@@ -19,3 +19,18 @@ Low（純粹是程式碼整理問題，不影響任何計算結果——使用
 
 ## 建議修正
 直接刪除`isOptimizationMode`欄位本身，以及`AssetSimulationFitnessEvaluator.java:77`那一行設定它為true的程式碼。這是純粹的死碼清理，不需要另外設計或討論方向——保留一個沒人讀取、卻會讓後來維護的人誤以為它還在發揮作用的旗標，本身就是一個隱藏的誤導風險。
+
+---
+
+## 修正報告（2026/09/30 已清理完成）
+
+### 1. 修改摘要
+- **修改檔案 1**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java`
+  - 刪除無人讀取的空殼欄位 `private boolean isOptimizationMode = false;`。
+- **修改檔案 2**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java`
+  - 刪除唯一賦值該旗標的死碼調用 `contextClone.getBaseContext().setOptimizationMode(true);`。
+
+### 2. 清理效果
+- 專案內已無任何 `isOptimizationMode` / `setOptimizationMode` 的殘留宣告與引用，死碼 100% 徹底清除。
+- 徹底消除了過去「24個月生活費安全網」移除後殘留的空殼開關，避免後續維護人員被虛擬旗標誤導。
+
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                            10018  12.14G   5.36    21:52:43  