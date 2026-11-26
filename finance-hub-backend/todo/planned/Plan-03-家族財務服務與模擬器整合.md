# Plan 03: 家族財務服務與模擬器整合 (Family Financial Service & Simulator Integration)

> **併入說明**：原本獨立記錄在feature-requirement-129（Dependent不完全棄用，定位為「真實帳號尚未綁定前的備援資料來源」）的決定，核心內容跟本文件Story 4與末段「Hybrid Family Member Architecture」參考資料描述的是同一件事（`linkedUserId`欄位、Link模式優先讀真實帳號、沒綁定才退回Dependent手動資料），已確認重複後併入本文件，feature-requirement-129.md已刪除。
>
> **跟issue-103的關係（併入自feature-requirement-129，Plan-03原文未提及）**：即使做完真實帳號連結、`Dependent`退居備援角色，[issue-103](../pending%20fix/issue-103.md)（配偶扣除完全沒有判斷70歲以上）這個bug仍然會存在——issue-103壞在`SpouseDeductionEngine`這個計算引擎本身沒有讀取生日資料去判斷年齡，跟「生日資料從`Dependent`手動填寫來、還是從真實連結帳號來」是兩個獨立的問題，換了資料來源不會自動修好這個計算邏輯本身的漏洞，issue-103需要獨立修正，不會因為做了Story 4的真實帳號連結而自動解決。
>
> **Plan-03-01（併入自原question-169，另併入原question-188之新角度）**：`FamilyServiceImpl.disbandFamily`目前是ADMIN無條件把群組底下所有成員一起移除，沒有區分「群組裡只有自己一個人」跟「群組裡已經有配偶或其他成員加入」。這個問題被判定為本末倒置——Link模式（Story 4的真實帳號連結）本身還沒做完，現在就去深究「多人群組解散要不要通知其他人」這類細節沒有意義，Plan-03最終整個方向都還可能變動。等Story 4定案、家族功能的實際使用方式確定下來後，再回頭評估`disbandFamily`的權限範圍是不是需要調整。
>
> 原question-188從同一個根因（`disbandFamily`沒有更細緻的退出選項）發現了一個額外角度：`FamilyServiceImpl.removeMember`裡，ADMIN想自己離開群組時會被擋下，錯誤訊息寫著「Transfer ownership or disband instead.」——但整個程式碼（`FamilyServiceImpl`、`FamilyMember`、`FamilyService`介面）裡完全找不到任何「把ADMIN角色轉移給別的成員」的實作，`FamilyMember.setRole()`方法存在但沒有任何Service方法會呼叫它。這代表錯誤訊息承諾了一個不存在的「轉移擁有權」功能，使用者唯一真正能走的路只有`disbandFamily`（整個解散，不是單純換人當家）。這個角度跟Plan-03-01原本記錄的「`disbandFamily`沒有區分單人/多人群組」是同一個根因的兩個症狀，一併等Story 4定案後再回頭處理；如果屆時決定不補上真正的轉移擁有權功能，至少應該把這句誤導性的錯誤訊息改成只提真正存在的選項。
>
> **Plan-03-02（併入自原question-182）**：扶養親屬扣除資格判定用「年齡是否達65歲」當作「所得是不是退休年金」的替身指標，65歲以上扶養親屬完全可能仍在打工領薪資而非領年金——同樣是本末倒置，`Dependent`資料模型本身的設計在Story 4定案前都還可能整個變動。詳見[Plan-03-02](Plan-03-02-dependent-elderly-income-type-ambiguity.md)。

## 🎯 背景與目標 (Background & Goals)
為了讓家庭財務規劃更加真實，現有的沙盒模擬器需要支援「配偶」的財務貢獻。配偶的資料來源可能有兩種：
1. **Sandbox 模式**：根據 `Dependent` 的簡單設定（例如薪資打 8 折）來粗估。
2. **Link 模式**：透過真實的帳號連結，直接使用配偶的精確數據。

為了解耦資料來源與模擬器，我們將引入 `FamilyFinancialService`，將配偶的財務狀態抽象為「時間序列快照 (Yearly Snapshot)」。這樣一來，無論上游是哪種模式，核心模擬器 (Engine) 與最佳化求解器 (GA Optimizer) 都只需進行極小的改動，即可自動適應雙人甚至多人的家庭現金流。

---

## 📋 規劃與規模 (Stories & Tasks)
這個 Epic 預計拆分為 **4 個 Story**，共包含 **11 個 Task**。

### Story 1: 建立核心介面與 DTO (基礎建設)
為了讓上游服務與下游模擬器能夠溝通，首先需要定義資料傳輸物件與服務介面。
- **[ ] Task 1.1: 建立 `SpouseYearlySnapshot` DTO**
  - 包含 `netSalary` (稅後薪水), `netPension` (稅後年金), `nisaInvestment` (預計投入 NISA 金額) 等欄位。
- **[ ] Task 1.2: 定義 `FamilyFinancialService` 介面**
  - 設計核心方法 `getSpouseFinancialsForYear(UserProfile myProfile, int simulationYear)`。
- **[ ] Task 1.3: 實作 `SandboxFamilyFinancialServiceImpl` (Mock 實作)**
  - 實作 Sandbox 模式下的計算邏輯：
    - 算出配偶當年度年齡與退休狀態。
    - 退休前：讀取 `Dependent` 薪資設定（或預設打 8 折）。
    - 退休後：回傳 0 或預設年金。

### Story 2: 擴充 Dependent 資料模型 (Sandbox 模式資料源)
因為 Sandbox 模式需要初始數據來進行模擬，現有的 `Dependent` 必須擴充資產相關欄位。
- **[ ] Task 2.1: 擴充 `Dependent` 實體**
  - 新增 `currentBankBalance` (存摺餘額)、`currentNisaBalance`、`idecoBalance` 等欄位。
- **[ ] Task 2.2: 更新 Database Schema 與 Mapper**
  - 修改對應的 DTO，並確保 Hibernate / 遷移腳本正確對應新欄位。
- **[ ] Task 2.3: 更新前端 UI (Dependent 設定)**
  - 允許使用者在新增/編輯 Dependent 時，輸入配偶的初始存款與投資餘額。

### Story 3: 改造核心沙盒模擬器 (Engine)
將 `FamilyFinancialService` 整合進 `AssetSimulationEngineImpl`，並升級狀態機以支援多使用者的資產追蹤。
- **[ ] Task 3.1: 狀態機 (State) 結構升級**
  - 將模擬器內的 `NisaBalance` 單一變數，擴充為支援多使用者的結構（例如 `Map<UserId, NisaAccount>` 或是 `FamilyNisaBalance`），確保兩人的免稅額度分開計算。
- **[ ] Task 3.2: 注入 Service 與迴圈整合**
  - 在 `AssetSimulationEngineImpl` 中注入 `FamilyFinancialService`。
  - 在大迴圈 (for age = initialAge to 100) 的現金流計算階段，呼叫 `getSpouseFinancialsForYear` 取得快照。
- **[ ] Task 3.3: 整合配偶快照至家庭水庫**
  - 根據快照，將配偶的淨收入加入 `totalHouseholdNetIncome`，並將配偶的 NISA 投資從現金流扣除、加入其獨立的 NISA 餘額中。

### Story 4: 實作真實 Link 版本的 Service (未來擴充)
當使用者的配偶實際註冊並接受 Link 邀請後，將啟動真實數據的 Service。
- **[ ] Task 4.1: 實作 `LinkedFamilyFinancialServiceImpl`**
  - 呼叫真實的稅務引擎算真實淨利。
  - 呼叫年金計算機算真實年金。
- **[ ] Task 4.2: 實作 Service 切換邏輯 (Strategy or Factory)**
  - 根據家庭狀態 (是否已 Link)，動態決定要呼叫 Sandbox 版本還是 Linked 版本的 Service。

---

## 📈 影響評估
- **AssetSimulationEngineImpl (引擎)**：中等規模改動（修改 State 結構、加入呼叫 Service 邏輯）。
- **GA Optimizer (求解器)**：**零改動**。優化器會自動接收變大後的「家庭現金流水庫」，並給出更好的建議（例如 NISA 提繳上限會自動提高），不需修改其基因演算法邏輯。

---

## 📚 參考資料：架構推演分析 (Reference)

這是一個非常精準的架構推演方向！我們從「下游（模擬器）」的需求，反向推導出「上游（Service）」應該提供什麼介面（API Contract）。

假設我們已經有一個 `FamilyFinancialService`，以下是現有沙盒模擬器（Sandbox）與最佳化求解器（GA Optimizer / Grid Search）所需的改動規模分析，以及反推 Service 需要具備的功能：

---

### 1. 核心沙盒模擬器 (`AssetSimulationEngineImpl`) 的改動規模
**改動規模：中等偏小 (Medium-Small)**

現有的模擬器是在一個 `for (int age = initialAge; age <= 100; age++)` 的大迴圈中，逐年計算現金流。為了把配偶加進來，模擬器需要做以下幾件事：

1. **注入 Service 並在迴圈內呼叫**：
   在每年計算淨收入（Net Income）的階段，呼叫 Service 取得配偶當年度能貢獻的金額，並加到 `totalHouseholdNetIncome`。
2. **處理配偶的投資與資產（選配，視需求深度而定）**：
   如果配偶也有 NISA 或 iDeCo，模擬器需要把配偶的投入金額從家庭現金流中扣除，並追蹤配偶的 NISA 餘額（這部分會稍微複雜一點，因為目前 `NisaBalance` 是單一變數，若要精準追蹤免稅額上限，需擴充為 `Map<UserId, NisaAccount>` 或是簡單合併成 `FamilyNisaBalance`）。
3. **處理配偶的退休狀態**：
   模擬器是根據「您的年齡」在跑迴圈，但老婆/老公的年紀跟您不一樣，退休時間也不同。

**反向推導 `FamilyFinancialService` 需要具備的功能：**
為了解耦並讓模擬器改動最小，這個 Service 不能只回傳一個死的「淨利數字」，它必須是一個**「給定特定年份，回傳配偶當年財務快照」**的狀態機。
它的介面應該長這樣：
```java
public SpouseYearlySnapshot getSpouseFinancialsForYear(UserProfile myProfile, int simulationYear) {
    // 內部實作：
    // 1. 算出配偶在 simulationYear 時是幾歲
    // 2. 判斷配偶是否已退休？
    // 3. 若未退休 -> 走做法A(打8折) 或 做法B(呼叫稅務引擎算真實淨利)
    // 4. 若已退休 -> 走做法A(0元) 或 做法B(呼叫年金計算機算真實年金)
    // 5. 回傳當年度的快照 DTO
}

// Service 回傳的 DTO
public class SpouseYearlySnapshot {
    long netSalary;       // 稅後薪水 (退休後為 0)
    long netPension;      // 稅後年金 (退休前為 0)
    long nisaInvestment;  // 預計投入的 NISA 金額
}
```
**結論**：只要 Service 能提供這樣一個 `getSpouseFinancialsForYear(year)` 的時間序列快照，`AssetSimulationEngineImpl` 的改動就會非常小，只需要在迴圈裡把這幾個數字加減進水庫即可。

---

### 2. GA 最佳化模擬器 (`SimulationOptimizerService` / 求解器) 的改動規模
**改動規模：幾乎為零 (Near-Zero)**

這就是 GA（基因演算法）與我們現有架構最強大的地方！
現有的優化器（例如 NISA 最佳提繳求解器、房貸提前還款求解器）的運作原理是：**它不斷改變參數（染色體），然後把參數丟給 `AssetSimulationEngineImpl` 跑一次，最後看「破產年齡」或「最終淨資產」這個 Fitness Score 來決定哪組參數最好。**

只要您完成了上述第 1 點（沙盒模擬器能正確把配偶的錢算進去並產出正確的 Fitness Score），**GA 最佳化求解器完全不需要改動任何一行邏輯**！
- 對 GA 來說，它根本不在乎這筆多出來的錢是您的還是老婆的，它只看到「每年可用現金流變多了」。
- 它會自動適應這個新的現金流水位，並給出新的最佳解（例如：原本建議您 NISA 每月只能投 3 萬，加上配偶收入後，GA 會自動算出您可以每個月投 10 萬而不破產）。

**唯一的例外（需要改動優化器的情況）**：
如果您希望 GA 不只最佳化「您的」NISA，還要同時幫您決定「老婆的」NISA 要投多少。那這就涉及到擴展 GA 的染色體維度（多變數最佳化），這部分的改動就會很大。但如果只是單純把配偶當作「固定的現金流水源」，那 GA 完全免改。

### 總結您的反向工程分析
1. **Service 的核心職責**：必須具備「時間推移」的概念，能夠根據給定的 `simulationYear`，自動換算配偶年齡、退休狀態，並吐出當年度的 `(薪資/年金/投資)` 三個淨數值。
2. **模擬器 (Engine)**：只需接收 Service 的數值，做簡單的加減法併入家庭水庫，改動規模小。
3. **優化器 (Solver)**：自動受惠於引擎的升級，改動規模 0。


---
## 📚 參考資料：「計算邏輯」跟「狀態追蹤」

**是的，計算複利成長的「計算機」本身是可以複用的！** 真正的問題就像您說的：**是現有的沙盒在跑迴圈時，無法追蹤「第二個人的免稅額度與累積餘額」**。

讓我用一個具體的例子來說明為什麼會有這個區別：

### 1. 為什麼算稅金、算年金很簡單？（無狀態 Stateless）
像 `ActiveTaxCalculationEngine` 或是 `PensionCalculator`，它們是「沒有記憶」的。
今年丟進去 500 萬薪水，它吐出 100 萬稅金；明年丟進去 600 萬，它吐出 130 萬。它不需要記得去年的事。所以我們隨時可以把老婆的資料丟進去，算完拿結果就好。

### 2. 為什麼算 NISA/iDeCo 稍微複雜？（有狀態 Stateful）
NISA 和 iDeCo 是**「有記憶的（需要追蹤狀態）」**。
在 `AssetSimulationEngineImpl` 的大迴圈（從今年算到 100 歲）裡面，系統宣告了幾個變數來「記住」您每年的狀態：
```java
long nisaBalance = ...;             // 帳戶總餘額 (會逐年複利成長)
long accumulatedNisaPrincipal = ...; // 已經投入的本金
long remainingLifetimeLimit = 18_000_000; // 日本政府規定的 1800 萬終身額度
```

當您把老婆的 NISA 也加進來模擬時，**投資報酬率的計算機確實可以共用**，但上述的「變數」必須擴充。因為：
1. **額度是分開的**：老婆也有自己的 1800 萬額度，不能跟您的混在一起扣，否則模擬器會以為「家庭總共只能投 1800 萬」，導致提早撞到上限而停止投資。
2. **提取的時間點不同**：老婆跟您的年紀不同，遇到赤字時，要先賣誰的 NISA？

### 結論
您說的非常準確，就是**「主要是現在無法追蹤成長，還有（老婆的）積立額度剩多少可以投入」**。

要解決這個問題，未來在實作時，只需要在模擬器迴圈的開頭，把原本寫死的單一變數，變成一個 List 或 Map 即可（例如宣告兩個 NISA 狀態物件，一個是 `myNisaState`，一個是 `spouseNisaState`），然後在迴圈裡分別呼叫現有的投資計算機讓它們各自成長。

所以總結來說：**計算機不用重寫，只要在模擬器裡多宣告幾個變數來「記住」老婆的帳戶狀態就可以了！**

---

## 📚 參考資料：「未來模擬」與「現在狀態」的本質差異

**即使是 Link 模式，模擬器大迴圈裡面「還是需要追蹤」**。

為什麼呢？您可以這樣想：
Link 模式的另一邊（配偶的真實帳號）確實有資料，但它存的是**「現在（2026 年）的真實餘額與設定」**。
可是我們的模擬器是在**「預測未來（算到 100 歲）」**。配偶的帳號資料庫裡，不可能知道他/她 2040 年的時候 NISA 餘額會變成多少，因為那還沒發生。

### 實際在程式碼中的分工會是這樣：

**1. Service 的職責：提供「現在的起跑點 (Initial State)」**
當模擬剛啟動（Year 0）時，`FamilyFinancialService` 會去 Link 的另一邊把老婆帳號裡的資料抓過來：
- 抓取真實的 NISA 總餘額（例如目前累積了 300 萬）。
- 抓取真實的每月扣款設定（例如每月投 5 萬）。
- 抓取真實的已用掉的終身額度。
這些就是模擬的「初始數值」。

**2. 模擬器 (Simulator) 的職責：推演「未來的每一天 (Future Projection)」**
因為你們是「一個家庭，共用一個現金流水庫」，所以模擬器不能讓你們各跑各的。
在 `AssetSimulationEngineImpl` 的 50 年大迴圈裡面：
- **今年度**：把你們兩人的薪水都加進水庫，然後分別扣除您要繳的 NISA 5 萬，以及老婆要繳的 NISA 5 萬。
- **計算投資成長**：呼叫計算機，讓您的 NISA 成長 5%，也讓老婆的 NISA 成長 5%。
- **更新狀態**：模擬器要把您跟老婆長大後的 NISA 餘額「記下來」，留給下一個年度（迴圈的下一次）繼續滾雪球。
- **遇到赤字（最關鍵的聯合模擬）**：如果某年家庭要繳一大筆頭期款，現金流不夠了！這時候必須在**同一個迴圈**裡做出聯合決策：「要先賣老公的 NISA，還是先賣老婆的 NISA 來救火？」

### 結論
Link 模式非常強大，它解決了「起跑點數據不準確」的問題，讓我們不用再手動輸入假資料。
但是，一旦按下了「開始模擬」按鈕，時間開始流動，**未來的財務變化就必須由核心模擬器統一接管與追蹤**，這樣才能精準模擬出家庭在遇到共同危機（現金流赤字）時的資產變化！

---

## 📚 參考資料：Hybrid Family Member Architecture

# Hybrid Family Member Architecture (Linked Account Model)

## Background
Currently, the system has two disjointed concepts of "Family Member":
1. **Profile Family Member** (`profile.user.dto.FamilyMemberResponse`): Manually inputted "dummy" data (e.g. Spouse's estimated income, birth date) used purely for the simulation engine.
2. **IAM Family Group Member** (`family.dto.FamilyMemberResponse`): Actual registered users that share the same workspace for data access.

The simulation engine currently relies heavily on hardcoded Profile Family Members. To achieve more precise simulations (e.g., pulling exact annuity and income data from a spouse's real account) without breaking the ability to simulate non-registered dependents (like infants), we will adopt a **Linked Account Model**.

## Proposed Architecture

### 1. Database & Domain Updates
- Add a new nullable column `linked_user_id` to the `UserProfile.familyMembers` entity (`finance_user_profile_family_member` table).
- `linked_user_id` will store the authentication ID of a real registered user, if the family member slot is bound to one.

### 2. Simulation Engine (Data Resolution)
Implement a "Real Data First" resolution strategy in the Simulation Engine:
- **If `linked_user_id` is NOT NULL** AND the linked user is currently in the same `FamilyGroup` (Privacy/Authorization check):
  - The engine ignores the manually inputted `estimatedAnnualIncome` and `birthDate`.
  - It dynamically fetches the real `UserProfile` of the linked user to perform calculations.
- **If `linked_user_id` is NULL** OR the linked user has left the `FamilyGroup`:
  - The engine gracefully falls back to using the manually inputted "dummy" data.

### 3. Frontend & UX 
- **Read-only Mode**: When a user links a profile family member slot to a real Family Group member, the form fields for that slot (Birth Date, Income) become disabled (grayed out) and auto-sync with the linked account's real data. A badge "Linked to [Name]" is displayed.
- **Unlinking**: The user can choose to unlink the account at any time, returning the fields to an editable state for manual hypothetical simulation (e.g. "What if my spouse's income drops to 0 next year?").

### 4. Privacy & Authorization
- **Strict Boundary**: Cross-user profile access is strictly gated by `FamilyGroup` membership. If a user is removed from a Family Group, their data is instantly decoupled from the primary user's simulation engine.
