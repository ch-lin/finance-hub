# [FIXED] Issue 151：最佳化引擎的適應度評估器是帶有可變狀態的Spring單例，同時執行的多個最佳化請求可能互相污染彼此的評分依據

## 這份issue怎麼被發現的
這是這次Source Code Review針對GA/Grid Search最佳化引擎進行商業邏輯審查時發現的問題。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java:39-41`（`@Component`，預設是Spring單例，沒有任何`@Scope`覆寫）
- 同檔案`:53-59`（`baseData`、`targetAge`兩個可變的實例欄位，程式碼自己的註解寫著「since FitnessEvaluator is used inside the GA, it's better if it's stateless... For now, let's keep mutable state for the current run.」）
- 同檔案`:61-64`（`init()`會直接改寫這兩個欄位）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:58`（`@Service`，同樣是單例）、`:71`（`fitnessEvaluator.init(baseData, targetAge)`）、`:112`（`engine.optimize(seed)`——這一步可能跑很久、評估候選幾百到幾千次，期間這個共用的bean隨時可能被另一個執行緒存取）

## 現狀
`AssetSimulationFitnessEvaluator`是一個普通的`@Component`（預設單例），裡面有兩個普通的可變實例欄位`baseData`、`targetAge`，透過`init()`方法設定。`GlobalOptimizerServiceImpl`也是單例。每一次呼叫最佳化API，雖然都會各自抓一份正確範圍的使用者資料，但呼叫的方式是：先呼叫`fitnessEvaluator.init(baseData, targetAge)`把這份資料暫存到共用的bean上，然後再執行一次可能耗費不少時間、呼叫`evaluate()`數百到數千次的GA或Grid Search搜尋迴圈。

## 為何是問題
如果兩個最佳化請求的執行時間重疊——不管是使用者自己開兩個分頁、連點兩次，還是（這個系統本來就支援多使用者/家族共享）不同的家族成員差不多同時各自按下「最佳化」——兩個執行緒都會呼叫同一個共用bean的`init()`。誰的`init()`最後執行，就會變成接下來所有`evaluate()`呼叫（不管是哪個執行緒發起的）的評分依據，直到下一次`init()`被呼叫為止。

具體情境：使用者A的執行緒開始跑一次房貸最佳化的GA搜尋；跑到一半，使用者B呼叫`init()`，把這個共用bean換成B的資料（不同的薪資、房貸、目標年齡）。從這一刻起，A執行緒剩下還沒跑完的那些`evaluate()`呼叫，實際上評分依據變成了B的財務資料，不是A自己的——而且不是「從頭到尾都算錯」這種至少一致的錯誤，是「前面幾輪用A的資料評分、後面幾輪用B的資料評分」這種分裂式的結果，最後回傳給A的「最佳解」，從來沒有真正被完整地、用同一份一致的基準資料驗證過。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：改成request/prototype scope**
- 做法：把這個bean改成每次請求都重新產生一個新的實例。
- 優點：概念上直接。
- 缺點：如果單純加`@Scope("prototype")`標註，但注入它的`GlobalOptimizerServiceImpl`本身是單例，Spring只會在`GlobalOptimizerServiceImpl`被建立時注入一次，不會每次請求都重新產生——需要搭配scoped-proxy或`ObjectProvider`/`@Lookup`一起調整注入方式才會真正生效，不是單純加一個標註就能解決。

**方向B：徹底移除「先init、再多次evaluate」這種暫存狀態的用法**
- 做法：讓`FitnessEvaluator.evaluate(Chromosome)`方法本身直接把`baseData`/`targetAge`當作參數傳進去，不要暫存在共用物件上；GA引擎、Grid Search引擎本身已經是每次請求都用`new`重新建立的（不是單例），所以只有這個評估器需要調整。
- 優點：從根本上消除「共用可變狀態」這一整類問題，不需要處理Spring scope的細節陷阱。
- 缺點：需要調整`FitnessEvaluator`介面本身的方法簽章，以及兩個呼叫端引擎的呼叫方式，改動範圍比方向A大一些。

**方向C：在關鍵區段加鎖**
- 做法：在`GlobalOptimizerServiceImpl.optimize()`裡，從`init()`到搜尋結束這一段加上鎖，確保同一時間只有一個最佳化請求在跑。
- 優點：改動最小。
- 缺點：這會讓所有使用者的最佳化請求全部排隊序列化執行，不管是不是同一個使用者——GA/Grid Search搜尋本身不是瞬間完成的，這樣會把一個正確性問題，直接變成一個全系統吞吐量的瓶頸。

---

## 決策方向

經深入分析與評估，原提案方向 A、B、C **皆不予採用**，改為採納更具工程健全性與測試彈性的**方向 D（工廠模式封裝每次請求的獨立不可變實例）**：

### 1. 原提案方向審查與否決原因
- **方向 A（Spring Prototype / Request Scope）：不予採用**
  - **缺陷**：Spring 的 Prototype 在注入單例 Service 時，必須搭配 Scoped-Proxy、`ObjectProvider` 或 `@Lookup` 等特殊機制才能動態取得新實例。更重要的是，它依然保留了「先注入、再呼叫 `init()`」的可變狀態設計，本質上並未達到不可變物件（Immutable Object）的最佳實踐。
- **方向 B（修改 `FitnessEvaluator.evaluate()` 方法簽章）：不予採用**
  - **缺陷**：破壞演算法抽象層。`FitnessEvaluator` 作為通用最佳化核心介面，其職責僅是「給予染色體一個評分（`evaluate(Chromosome)`）」，不該被塞入特定領域模型（`SimulationData`, `targetAge`）。強行修改會造成改動範圍過大，需同時牽動 `GeneticAlgorithmEngine` 與 `GridSearchEngine`。
- **方向 C（關鍵區段加鎖排隊）：不予採用**
  - **缺陷**：最佳化屬於 CPU 密集運算，耗時常達數秒。加鎖排隊會嚴重扼殺系統的多使用者併發能力，將正確性問題轉化為全系統吞吐量瓶頸。

---

### 2. 採納方案：方向 D（工廠模式建立獨立且不可變的 Evaluator 實例）

#### 核心設計理念：
本質上回歸最純粹的「依請求 `new` 出獨立實例」，並透過輕量工廠（Factory）封裝，兼顧不可變性與單元測試可 Mock 性。

1. **重構 `AssetSimulationFitnessEvaluator` 為純粹且不可變（Immutable）的 POJO**：
   - 移除 `@Component` 標註，不再由 Spring 容器作為單例管理。
   - 徹底移除 `init(SimulationData, int)` 方法。
   - 將所有欄位（包括 `assetSimulationEngine`、`chromosomeMapper`、`baseData`、`targetAge`）皆宣告為 `final`，統一於建構子中注入完成。
   - 物件生命週期隨各別最佳化任務結束自然回收，具備絕對的執行緒安全（Thread-Safe），無任何跨請求污染的可能。
2. **引入單例工廠 `AssetSimulationFitnessEvaluatorFactory`**：
   - 作為 Spring Bean（`@Component`），單例常駐。
   - 注入無狀態的共用服務依賴（`AssetSimulationEngine`、`ChromosomeMapper`）。
   - 提供方法：
     ```java
     public FitnessEvaluator create(SimulationData baseData, int targetAge) {
         return new AssetSimulationFitnessEvaluator(assetSimulationEngine, chromosomeMapper, baseData, targetAge);
     }
     ```
3. **改造呼叫端 `GlobalOptimizerServiceImpl`**：
   - 移除原單例 `AssetSimulationFitnessEvaluator` 的注入，改為注入 `AssetSimulationFitnessEvaluatorFactory`。
   - 在 `optimize()` 執行時：
     ```java
     FitnessEvaluator fitnessEvaluator = fitnessEvaluatorFactory.create(baseData, targetAge);
     ```
4. **兼顧單元測試 `GlobalOptimizerServiceTest`**：
   - 測試中只需 `@Mock private AssetSimulationFitnessEvaluatorFactory fitnessEvaluatorFactory;`，並於 `setUp()` 中 stub 其回傳測試用的 mock `FitnessEvaluator`，即可 100% 保留現有對 GA / Grid Search 迭代次數的精確驗證，測試維護成本最低且無任何破壞。

---

## 實作成果報告

### 1. 修改與新增檔案清單
- **新增檔案**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluatorFactory.java`
- **新增測試**：`finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluatorFactoryTest.java`
- **修改檔案**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/evaluator/AssetSimulationFitnessEvaluator.java`
- **修改檔案**：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java`
- **修改檔案**：`finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceTest.java`

### 2. 重點實作成效
1. **徹底根除跨請求污染（Eliminate Shared Mutable State）**：
   - `AssetSimulationFitnessEvaluator` 移除 `@Component` 與 `init()` 方法，所有欄位皆宣告為 `final`。
   - 每次最佳化請求皆由 `AssetSimulationFitnessEvaluatorFactory.create()` 產生全新專屬實例，不同使用者或多分頁併發時彼此完全隔離，達到 100% 執行緒安全。
2. **零抽象破壞（Preserve Architecture Abstraction）**：
   - 演算法引擎核心（`FitnessEvaluator.evaluate(Chromosome)`、`GeneticAlgorithmEngine`、`GridSearchEngine`）簽章完全未被破壞，通用演算法與領域模型乾淨解耦。
3. **無鎖高吞吐（Lock-free High Concurrency）**：
   - 未採用方向 C 的加鎖方案，各請求在專屬記憶體實例中獨立運算，系統保有極限平行運算能力。

