# [FIXED] Issue 165：全域最佳化卡片切換「策略」下拉選單後，畫面上顯示的仍然是舊策略跑出來的結果，沒有任何重新執行的入口，也沒有標示這個結果是哪個策略跑出來的

## 這份issue怎麼被發現的
這是這次Source Code Review針對前端業務邏輯進行審查時發現的問題。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx:196-203`（`toggleConfig`，勾選/取消某個最佳化維度時，會呼叫`clearResult()`清掉舊結果）
- 同檔案`:261-270`（策略下拉選單`Select`：AUTO/FORCE_GA/FORCE_GRID，`onValueChange`只呼叫`setStrategy(...)`，完全沒有呼叫`clearResult()`）
- 同檔案`:273-304`（「執行AI最佳化」按鈕只在`!result`時才渲染；`OptimizerResultPanel`只在`result`存在時渲染——一旦有結果，按鈕整個消失，而`OptimizerResultPanel`本身沒有任何重新執行的按鈕）

## 現狀
勾選/取消最佳化維度的checkbox會正確清掉舊結果，但切換策略下拉選單不會。因為「執行」按鈕只在沒有結果時才顯示，一旦有結果、使用者改了策略想比較看看，畫面上完全沒有辦法重新觸發一次執行——除非回頭去動一次維度勾選框（這樣做同時也會改變要最佳化的對象，不是單純换個策略重跑）。

## 為何是問題
使用者先用AUTO策略跑出一個結果，接著想改成FORCE_GRID策略比較看看兩者的差異，把下拉選單切到FORCE_GRID——畫面上顯示的數字完全沒有改變，仍然是AUTO策略跑出來的那組舊結果，而且`OptimizerResultPanel`裡沒有任何地方標示「這組結果是用哪個策略跑出來的」。使用者很可能誤以為眼前看到的數字已經反映了剛剛選擇的新策略，但實際上畫面顯示的是一組跟目前下拉選單選項已經不一致的舊資料。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：切換策略時也呼叫`clearResult()`**
- 做法：讓策略下拉選單的`onValueChange`跟維度勾選框一樣，切換時清掉舊結果。
- 優點：改動最小，跟既有的勾選框行為一致。
- 缺點：使用者每次手滑切換一下策略選單，就要重新點一次執行按鈕，體感上稍微多一個步驟。

**方向B：在結果面板上補一個常駐的「重新執行」按鈕**
- 做法：不要讓「執行」按鈕在有結果後完全消失，改成在`OptimizerResultPanel`裡提供一個隨時可以重新觸發的按鈕。
- 優點：讓使用者可以在不清空設定的情況下，隨時用目前的設定重新跑一次。
- 缺點：需要調整現有的「有結果就隱藏執行按鈕」這個版面配置邏輯。

**方向C：在結果面板上標示這組結果實際是用哪個策略跑出來的**
- 做法：即使不清掉舊結果，至少讓使用者看得出來「這組數字是AUTO跑出來的，不是你剛剛選的FORCE_GRID」。
- 優點：就算選擇維持現有的「不自動清除」行為，也能避免使用者誤判。
- 缺點：沒有解決「想要重新執行」這個根本的操作需求，只是讓舊結果不會被誤認。

這幾個方向不互斥，也可以同時採用。

---

## 決策方向

原提案中方向 A、B、C 雖各別提出單點修補，但經深入檢視後，三者在整體 UX 上皆存在關鍵缺陷，故**皆不予採用**：

1. **方向 A 的致命缺陷（探索懲罰與資料丟失）**：
   - 使用者花費時間等待後端最佳化運算完成後，若僅因好奇想切換其他策略查看（或操作手滑），原本辛苦算好的結果便被無情抹除清空。若想看回原本數值，必須重新點擊執行並等待後端重新計算，造成強烈的操作挫折感，嚴重打擊使用者探索演算法的意願。
2. **方向 B 的致命缺陷（語意撕裂與過渡狀態矛盾）**：
   - 僅提供重跑按鈕，卻仍維持單一全域結果。當使用者將下拉選單切換至新策略（如 FORCE_GRID）而尚未點擊重跑時，畫面上顯示的依舊是舊策略（AUTO）的數據，造成「選單顯示策略 X、下方卡片卻是策略 Y」的畫面不一致矛盾。若使用者中途忘記按重跑，極易誤判當前結果已代表新策略。
3. **方向 C 的致命缺陷（治標不治本、操作死胡同依舊）**：
   - 僅被動貼上策略標籤，完全沒有解決「使用者想要以新策略重新執行運算」的核心操作需求。使用者看著標籤知道數字是舊的，但畫面上依然沒有任何執行按鈕可以按，操作死胡同問題依然存在。
4. **方向 A+B+C 拼湊的侷限**：
   - 即使將上述單點補丁勉強結合，仍無法支援「演算法結果橫向對比（A/B Testing）」的進階需求。每次重跑都會強行覆蓋前次結果，使用者無法在不同演算法（例如啟發式 GA vs. 窮舉式 Grid Search）間流暢切換以評估最適財務建議。

---

### 方向 D：依策略獨立快取最後一次執行結果（Per-Strategy Result Caching），支援切換比對與個別重跑（採納方案）

- **核心概念**：
  打破「全域單一結果」的限制，將狀態管理升級為「依策略隔離的多槽快取字典（`Record<OptimizationStrategy, GlobalOptimizerResponse | null>`）」，實現具備高度容錯性與對比能力的流暢 UX。
- **具體運作機制**：
  1. **切換至未執行策略**：當前策略結果為空，立即呈現乾淨狀態並露出醒目的「Run AI Optimizer」大按鈕，提供明確的執行入口。
  2. **切換回已執行策略**：秒速還原該策略上次運算的完整數據，完全無需重新發送 API 請求，探索成本降為零。
  3. **獨立重跑入口（Re-run）**：已產生結果的策略面板常駐「Re-run」按鈕，執行後僅精準更新該策略之快取，不影響其他策略的已算數據。
  4. **維度連動全面失效（Invalidation）**：唯有當使用者變更上方 5 個維度卡片（問題核心定義改變）時，才一次性清空所有策略的快取，確保邏輯嚴密一致。
- **UX 效益**：
  1. **徹底消除死胡同**：任何狀態下均有明確的執行或重跑入口。
  2. **探索零負擔**：切換選單不再遺失任何成果，探索與手滑均安全無虞。
  3. **原生支援演算法 A/B 比較**：使用者可先跑 AUTO 再跑 GRID，透過下拉選單秒切對比不同演算法對退休年齡與還款計畫的差異。



## 具體落實方案（採納方向 D）

### 1. 核心狀態模型重構（`useGlobalOptimizer` / `global-optimizer-card.tsx`）
- 將 `result: GlobalOptimizerResponse | null` 擴充/映射為字典型態快取：
  ```typescript
  resultsByStrategy: Partial<Record<OptimizationStrategy, GlobalOptimizerResponse>>
  ```
- 當前策略結果判定：
  ```typescript
  const currentResult = resultsByStrategy[strategy] || null;
  ```
- 執行邏輯（`handleOptimize`）：
  - 呼叫 `optimize(targetAge, { config, strategy })`。
  - 成功後將回傳結果寫入 `resultsByStrategy[strategy]`。
- 維度連動清除（`toggleConfig`）：
  - 當點擊 5 個維度卡片開關時，因為最佳化目標定義改變，清空 `resultsByStrategy`（設為 `{}`）。
- 策略切換（`Select onValueChange`）：
  - 僅更新 `strategy` state，切換顯示對應策略的 `currentResult`，不抹除其他策略的已運算結果。

### 2. 介面控制與按鈕配置
- **尚未執行該策略（`!currentResult`）**：
  - 渲染「Run AI Optimizer」大按鈕。
- **已執行該策略（`currentResult` 存在）**：
  - 渲染 `OptimizerResultPanel`，面板上方明確標示「當前策略：Strategy Name（例如 Auto / Genetic Algorithm / Grid Search）」，消弭認知混淆。
  - 面板底部除了「Apply to Sandbox」之外，提供「Re-run Optimizer」按鈕，讓使用者可隨時依目前設定重新運算並更新該策略快取。

---

## 實作成果報告

### 一、 實作完成項目
1. **Hook 狀態模型升級（`use-global-optimizer.ts`）**：
   - 移除單一全域 `result: GlobalOptimizerResponse | null`。
   - 導入字典型快取狀態 `resultsByStrategy: Partial<Record<OptimizationStrategy, GlobalOptimizerResponse>>`。
   - `optimize(targetAge, request)` 執行成功時，以 `request.strategy` 為 key 將結果寫入快取，確保不同演算法運算結果獨立存放。
   - 增加 `clearAllResults`（清空全部）與 `clearResult(strategy?)`（清空特定策略）輔助方法。
2. **多策略獨立快取與切換渲染（`global-optimizer-card.tsx`）**：
   - 透過 `currentResult = resultsByStrategy[strategy] || null` 動態綁定當前選取之策略結果。
   - **無縫切換與對比**：切換下拉選單時，若該策略已運算過則秒還原結果面板；若未運算過則呈現未執行狀態並露出「Run AI Optimizer」執行按鈕。
   - **下拉選單標記**：在策略選單選項中為已有快取的項目追加 `✓ Ready` 徽章，讓使用者一目了然哪些策略已完成試算。
3. **結果面板透明化與重新執行入口（`OptimizerResultPanel`）**：
   - 面板頂部新增策略識別 Badge（`Strategy: Auto / Genetic Algorithm / Grid Search`），徹底解決舊結果無法辨識來源的認知偏差問題。
   - 面板底部追加「`Re-run (${strategy})`」按鈕，讓使用者無需改動任何參數即可隨時單鍵重新執行當前策略，並於計算中呈現 Loading 狀態。
4. **維度連動與防呆保護**：
   - 點擊 5 個維度卡片開關（`toggleConfig`）時，因最佳化核心維度改變，自動調用 `clearAllResults()` 清空全策略快取，確保所有產出數值恆定具備一致的前提設定。
   - 最佳化執行期間（`isLoading`），自動禁用維度切換卡片、策略選單及操作按鈕，防範非同步競態問題。

### 二、 業務與操作效益
1. **消除操作死胡同**：徹底移除「有結果就隱藏執行按鈕且無處重跑」的反直覺限制，無論處於何種狀態，使用者都有明確的「執行 / 重新執行」按鈕可供點擊。
2. **零丟失與秒速對比**：使用者切換策略探索不同演算法（例如對比啟發式 GA 與枚舉式 Grid Search 的退休年齡推薦值）時，不會再因手滑切換選單而遺失已計算好的數據。
3. **資訊透明度提升**：結果面板明確顯示生成該結果的演算法名稱，徹底避免使用者把舊策略結果誤認為新策略成效之財務誤判。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                              10052  12.87G   7.59    09:11:07  
diff --git a/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx b/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
index ba0581f..8927247 100644
--- a/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
+++ b/hub-ui/src/app/finance/simulator/simulation/components/global-optimizer-card.tsx
@@ -14,14 +14,22 @@ interface ToggleCardProps {
   icon: React.ElementType;
   isActive: boolean;
   onClick: () => void;
+  disabled?: boolean;
 }
 
-function ToggleCard({ title, description, icon: Icon, isActive, onClick }: ToggleCardProps) {
+const STRATEGY_LABELS: Record<OptimizationStrategy, string> = {
+  AUTO: "Auto (Recommended)",
+  FORCE_GA: "Genetic Algorithm",
+  FORCE_GRID: "Grid Search",
+};
+
+function ToggleCard({ title, description, icon: Icon, isActive, onClick, disabled }: ToggleCardProps) {
   return (
     <Card 
-      onClick={onClick}
+      onClick={disabled ? undefined : onClick}
       className={cn(
-        "relative p-5 cursor-pointer transition-all duration-300 overflow-hidden flex flex-col justify-between group h-40",
+        "relative p-5 transition-all duration-300 overflow-hidden flex flex-col justify-between group h-40",
+        disabled ? "opacity-60 cursor-not-allowed" : "cursor-pointer",
         "border-2 hover:border-primary/50",
         isActive 
           ? "border-primary bg-primary/5 shadow-[0_0_15px_rgba(var(--primary),0.2)]" 
@@ -64,10 +72,13 @@ function ToggleCard({ title, description, icon: Icon, isActive, onClick }: Toggl
 interface OptimizerResultPanelProps {
   result: GlobalOptimizerResponse;
   config: OptimizationConfig;
+  strategy: OptimizationStrategy;
   onApply: () => void;
+  onReoptimize: () => void;
+  isLoading: boolean;
 }
 
-function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelProps) {
+function OptimizerResultPanel({ result, config, strategy, onApply, onReoptimize, isLoading }: OptimizerResultPanelProps) {
   const validMortgageRepayments = result.mortgageEarlyRepayments 
     ? Object.entries(result.mortgageEarlyRepayments)
         .filter(([_, events]) => events && events.length > 0)
@@ -87,6 +98,10 @@ function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelP
           <span>Analysis Complete</span>
         </div>
         <h2 className="text-2xl font-bold">Optimization Results</h2>
+        <div className="mt-2 inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-primary/10 border border-primary/20 text-xs font-medium text-primary">
+          <Sparkles className="w-3.5 h-3.5" />
+          <span>Strategy: {STRATEGY_LABELS[strategy]}</span>
+        </div>
       </div>
 
       {!allOptimized && (
@@ -173,8 +188,23 @@ function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelP
         </Card>
       )}
 
-      <div className="relative z-10 flex justify-center">
-        <Button size="lg" className="w-full md:w-auto px-12 h-14 text-lg bg-primary hover:bg-primary/90 text-primary-foreground shadow-[0_0_20px_rgba(var(--primary),0.4)] transition-all hover:shadow-[0_0_30px_rgba(var(--primary),0.6)]" onClick={onApply}>
+      <div className="relative z-10 flex flex-col sm:flex-row items-center justify-center gap-4">
+        <Button 
+          variant="outline"
+          size="lg" 
+          className="w-full sm:w-auto px-8 h-14 text-base border-primary/30 hover:bg-primary/10 hover:border-primary transition-all flex items-center gap-2"
+          onClick={onReoptimize}
+          disabled={isLoading}
+        >
+          <Sparkles className={cn("w-4 h-4", isLoading && "animate-spin")} />
+          {isLoading ? "Re-optimizing..." : `Re-run (${STRATEGY_LABELS[strategy]})`}
+        </Button>
+        <Button 
+          size="lg" 
+          className="w-full sm:w-auto px-12 h-14 text-lg bg-primary hover:bg-primary/90 text-primary-foreground shadow-[0_0_20px_rgba(var(--primary),0.4)] transition-all hover:shadow-[0_0_30px_rgba(var(--primary),0.6)]" 
+          onClick={onApply}
+          disabled={isLoading}
+        >
           Apply to Sandbox
         </Button>
       </div>
@@ -183,7 +213,7 @@ function OptimizerResultPanel({ result, config, onApply }: OptimizerResultPanelP
 }
 
 export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: number, onApplyResult: (result: GlobalOptimizerResponse) => void }) {
-  const { optimize, isLoading, result, clearResult } = useGlobalOptimizer();
+  const { optimize, isLoading, resultsByStrategy, clearAllResults } = useGlobalOptimizer();
   const [strategy, setStrategy] = useState<OptimizationStrategy>('AUTO');
   const [config, setConfig] = useState<OptimizationConfig>({
     optimizeRetirementAge: true,
@@ -193,9 +223,11 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
     optimizeMortgage: true,
   });
 
+  const currentResult = resultsByStrategy[strategy] || null;
+
   const toggleConfig = (key: keyof OptimizationConfig) => {
     setConfig(prev => ({ ...prev, [key]: !prev[key] }));
-    clearResult(); // Clear previous result when config changes
+    clearAllResults(); // Clear all cached results across strategies because optimization dimensions changed
   };
 
   const handleOptimize = () => {
@@ -216,6 +248,7 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
           icon={Mountain}
           isActive={config.optimizeRetirementAge}
           onClick={() => toggleConfig('optimizeRetirementAge')}
+          disabled={isLoading}
         />
         <ToggleCard 
           title="Pension Fund"
@@ -223,6 +256,7 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
           icon={PiggyBank}
           isActive={config.optimizePensionAge}
           onClick={() => toggleConfig('optimizePensionAge')}
+          disabled={isLoading}
         />
         <ToggleCard 
           title="iDeCo (Japan)"
@@ -230,6 +264,7 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
           icon={Landmark}
           isActive={config.optimizeIdecoAge}
           onClick={() => toggleConfig('optimizeIdecoAge')}
+          disabled={isLoading}
         />
         <ToggleCard 
           title="NISA (Japan)"
@@ -237,6 +272,7 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
           icon={TrendingUp}
           isActive={config.optimizeNisa}
           onClick={() => toggleConfig('optimizeNisa')}
+          disabled={isLoading}
         />
         <ToggleCard 
           title="Mortgage Strategy"
@@ -244,6 +280,7 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
           icon={Home}
           isActive={config.optimizeMortgage}
           onClick={() => toggleConfig('optimizeMortgage')}
+          disabled={isLoading}
         />
       </div>
 
@@ -258,19 +295,34 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
           </div>
         </div>
         
-        <Select value={strategy} onValueChange={(v) => setStrategy(v as OptimizationStrategy)}>
-          <SelectTrigger className="w-[180px]">
+        <Select value={strategy} onValueChange={(v) => setStrategy(v as OptimizationStrategy)} disabled={isLoading}>
+          <SelectTrigger className="w-[200px]">
             <SelectValue placeholder="Select Strategy" />
           </SelectTrigger>
           <SelectContent>
-            <SelectItem value="AUTO">Auto (Recommended)</SelectItem>
-            <SelectItem value="FORCE_GA">Genetic Algorithm</SelectItem>
-            <SelectItem value="FORCE_GRID">Grid Search</SelectItem>
+            <SelectItem value="AUTO">
+              <span className="flex items-center justify-between w-full gap-2">
+                <span>Auto (Recommended)</span>
+                {resultsByStrategy.AUTO && <span className="text-[10px] text-primary font-medium">✓ Ready</span>}
+              </span>
+            </SelectItem>
+            <SelectItem value="FORCE_GA">
+              <span className="flex items-center justify-between w-full gap-2">
+                <span>Genetic Algorithm</span>
+                {resultsByStrategy.FORCE_GA && <span className="text-[10px] text-primary font-medium">✓ Ready</span>}
+              </span>
+            </SelectItem>
+            <SelectItem value="FORCE_GRID">
+              <span className="flex items-center justify-between w-full gap-2">
+                <span>Grid Search</span>
+                {resultsByStrategy.FORCE_GRID && <span className="text-[10px] text-primary font-medium">✓ Ready</span>}
+              </span>
+            </SelectItem>
           </SelectContent>
         </Select>
       </div>
 
-      {!result && (
+      {!currentResult && (
         <div className="flex justify-center pt-4">
           <Button 
             size="lg" 
@@ -286,7 +338,7 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
             ) : (
               <span className="flex items-center gap-2">
                 <Sparkles className="w-5 h-5 group-hover:animate-pulse" />
-                Run AI Optimizer
+                Run AI Optimizer ({STRATEGY_LABELS[strategy]})
               </span>
             )}
             <div className="absolute inset-0 bg-white/20 translate-y-full group-hover:translate-y-0 transition-transform duration-300 ease-in-out" />
@@ -294,11 +346,14 @@ export function GlobalOptimizerCard({ targetAge, onApplyResult }: { targetAge: n
         </div>
       )}
 
-      {result && (
+      {currentResult && (
         <OptimizerResultPanel 
-          result={result} 
+          result={currentResult} 
           config={config}
-          onApply={() => onApplyResult(result)} 
+          strategy={strategy}
+          onApply={() => onApplyResult(currentResult)} 
+          onReoptimize={handleOptimize}
+          isLoading={isLoading}
         />
       )}
     </div>
diff --git a/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts b/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts
index 2f91132..b0eb717 100644
--- a/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts
+++ b/hub-ui/src/app/finance/simulator/simulation/hooks/use-global-optimizer.ts
@@ -37,11 +37,10 @@ export interface GlobalOptimizerResponse {
 
 export function useGlobalOptimizer() {
   const [isLoading, setIsLoading] = useState(false);
-  const [result, setResult] = useState<GlobalOptimizerResponse | null>(null);
+  const [resultsByStrategy, setResultsByStrategy] = useState<Partial<Record<OptimizationStrategy, GlobalOptimizerResponse>>>({});
 
   const optimize = useCallback(async (targetAge: number, request: GlobalOptimizerRequest) => {
     setIsLoading(true);
-    setResult(null);
     try {
       const url = `/api/finance/simulation/optimizer/${targetAge}`;
       const res = await fetch(url, {
@@ -52,7 +51,10 @@ export function useGlobalOptimizer() {
       const json = await res.json();
 
       if (json.status === "success" && json.data) {
-        setResult(json.data);
+        setResultsByStrategy(prev => ({
+          ...prev,
+          [request.strategy]: json.data as GlobalOptimizerResponse
+        }));
         toast.success("AI 最佳化運算完成！");
         return json.data as GlobalOptimizerResponse;
       } else {
@@ -68,14 +70,27 @@ export function useGlobalOptimizer() {
     }
   }, []);
 
-  const clearResult = useCallback(() => {
-    setResult(null);
+  const clearAllResults = useCallback(() => {
+    setResultsByStrategy({});
+  }, []);
+
+  const clearResult = useCallback((strategy?: OptimizationStrategy) => {
+    if (strategy) {
+      setResultsByStrategy(prev => {
+        const next = { ...prev };
+        delete next[strategy];
+        return next;
+      });
+    } else {
+      setResultsByStrategy({});
+    }
   }, []);
 
   return {
     optimize,
     isLoading,
-    result,
+    resultsByStrategy,
+    clearAllResults,
     clearResult
   };
 }
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                                                   10053  12.63G   7.27    09:12:58  