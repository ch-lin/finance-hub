# Issue 140：系統把資料隔離的責任完全交給環境裡剛好啟用的Hibernate Filter，沒有任何程式碼層級的把關——至少在兩種不同情境下造成跨使用者資料外洩

## 這份issue怎麼被發現的
在review Plan-02（多使用者架構、家族共享與隱私控制）時，背景agent提出一個安全性發現：整套多租戶隔離機制押注在Hibernate `@Filter`（`personalFilter`/`familyFilter`），但這個機制對`findById`/`deleteById`這條最常見的CRUD路徑完全不生效。這個主張本身是Hibernate規格層級的既定行為（不需要跑起來驗證），我接手後做的事情是：逐一讀原始碼，把agent抽樣列舉的「大致範圍」換成一份自己核對過、確認無誤的完整清單，同時排除agent報告裡不成立的部分。

後續在重新檢討一份已撤銷的issue（原issue-141，前提錯誤已刪除）時，另外發現了同一個根因的第二種症狀：模擬引擎建立`SimulationContext`時讀取各類財務資料用的查詢，同樣沒有自己限定擁有者範圍，而是完全依賴這次request剛好啟用了哪個filter——這跟上面的IDOR問題雖然觸發路徑不同（一個是filter對特定查詢方式完全不生效，一個是filter其實有效但範圍被動跟著環境放大），但本質是同一件事：**系統從沒有在任何一處用程式碼明確寫下「這裡只該看到自己的資料」，全部交給環境裡那個剛好被enable的filter去決定**，兩種情境都已經確認合併記錄在同一份issue裡，不分拆成兩個號碼。

查第二種症狀的過程中，進一步確認這個缺口不只房貸一處——`SimulationContextFactory`跟它底下的四個`XxxContextBuilder`，建立模擬所需的整套財務資料時，用的查詢方法全部是這個模式（方法名稱裡完全沒有`OwnerUserId`這個字樣），範圍遠比最初查到的「房貸/小孩/保單」大，詳見下方第二種症狀的完整檔案清單。

## 背景：目前的隔離機制設計
- [MultiUserBaseEntity.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/base/MultiUserBaseEntity.java)：定義了`personalFilter`（`owner_user_id = :userId`）跟`familyFilter`（`owner_user_id in (:familyUserIds) and (owner_user_id = :currentUserId or privacy_level != 'PRIVATE')`）兩個Hibernate `@Filter`。
- [OwnerFilterInterceptor.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/OwnerFilterInterceptor.java)：每個HTTP請求進來時，依`X-View-Scope`header自動enable對應的filter。
- 這套設計的隱含假設是：只要filter被enable了，所有對`MultiUserBaseEntity`子類別的查詢都會自動被限制在「我的資料」或「我家人的資料」範圍內。

## 現狀：這個假設對「依主鍵查詢」這條路徑不成立
Hibernate官方文件明確記載：filter只套用在entity query（HQL/JPQL/Criteria），不套用在direct fetching（依主鍵直接抓取）。Spring Data JPA的`SimpleJpaRepository.findById(id)`內部呼叫的是`EntityManager.find(domainClass, id)`，這條路徑完全不會檢查`owner_user_id`，不管有沒有enable任何filter都一樣——`deleteById()`內部也是先呼叫`findById()`等價的查詢再刪除，走的是同一條路徑。

這代表：只要是繼承`JpaRepository<T, Long>`、又沒有在Service層自己額外做`ownerUserId`比對的Controller/Service，任何已登入使用者只要猜/列舉一個數字ID（這些ID是連續自增的`Long`，`BaseEntity.id`用`GenerationType.IDENTITY`，極容易枚舉），就能讀取、修改、刪除**任意其他使用者**的財務紀錄——不限同一個家族，是對全系統任意使用者。

**跟`ownerUserId`寫入機制的關係（澄清，避免跟另一個完全不同的保護機制搞混）**：`MultiUserBaseEntity.ownerUserId`欄位標了`@CreatedBy`（Spring Data JPA Auditing，新建時自動從JWT的`sub`取值寫入）跟`updatable = false`，這代表攻擊者**沒有辦法偽造或竄改`owner_user_id`這一欄本身**——建立時無法冒充別人、更新時Hibernate產生的UPDATE SQL本來就不會動這一欄。這個保護機制本身是對的，而且跟這份issue講的漏洞完全不衝突、也不互相依賴：攻擊者不需要偽造`ownerUserId`，他用自己真實合法的身分登入，直接對`PUT /insurance-policies/{別人的數字ID}`這種端點發請求，`findById`/`deleteById`這條路徑從頭到尾不會把`owner_user_id`放進查詢條件，所以系統照樣把「別人的那一筆」撈出來，其餘欄位（保費、保單名稱、房貸餘額、小孩生日等）照樣被覆寫或整筆刪除——只有`owner_user_id`這一欄因為`updatable=false`保持原值不變。換句話說：**這筆資料的「所有權標籤」搶不走，但資料的內容跟存不存在，完全不受這個標籤保護**。

## 已逐一核對、確認存在此漏洞的端點清單
全域搜尋所有`*ServiceImpl.java`裡出現`findById(`/`deleteById(`的呼叫，逐一讀取每一處上下文，確認完全沒有`ownerUserId`比對、也確認Controller層的`@PathVariable`是直接從URL流進去（沒有`@PreAuthorize`等任何方法級安全註解）：

| 端點 | Controller | Service方法 |
|---|---|---|
| `PUT /mortgages/{id}`、`DELETE /mortgages/{id}` | [MortgageRecordController.java:73-83](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/MortgageRecordController.java) | [MortgageRecordServiceImpl.java:166,203-205](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java)——`updateMortgage`在171行`findById`後完全沒檢查擁有者就直接改寫全部欄位；`deleteMortgage`在204行`deleteById`沒有任何檢查。（注意：同一個Service裡`validateAndApplyTaxLogic`在**啟用稅務抵扣這個分支**內有做`record.getOwnerUserId().equals(currentUserId)`檢查，但這個檢查只覆蓋稅務欄位，不覆蓋`updateMortgage`其餘欄位跟`deleteMortgage`。） |
| `GET/PUT/DELETE /insurance-policies/{id}` | [InsurancePolicyController.java:63-89](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/insurance/InsurancePolicyController.java) | [InsurancePolicyServiceImpl.java:59-93](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/insurance/InsurancePolicyServiceImpl.java)——`getPolicy`/`updatePolicy`/`deletePolicy`三個方法全部直接`findById`/`deleteById`，整個class找不到一處`ownerUserId`比對。 |
| `DELETE /assets/pensions/{id}` | [PensionRecordController.java:123-124](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java) | [PensionRecordServiceImpl.java:77-78,120-122](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionRecordServiceImpl.java)——`getRecordById`/`deleteRecord`无擁有者檢查。 |
| `GET/PUT/DELETE /transaction-categories/{id}` | [CategoryController.java:79-120](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/category/CategoryController.java) | [CategoryServiceImpl.java:69,103,131](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/category/CategoryServiceImpl.java)——`updateCategory`/`deleteCategory`无擁有者檢查。 |
| `PUT /salaries/{id}` | [SalaryRecordController.java:105-107](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/salary/SalaryRecordController.java) | [SalaryRecordServiceImpl.java:81-83](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryRecordServiceImpl.java)——`updateRecord`无擁有者檢查，整筆覆寫薪資明細。 |
| `PATCH/DELETE /profile/children/{id}`、`GET /profile/children/{id}/projection` | [ChildProfileController.java:75-93](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/ChildProfileController.java) | [ChildProfileServiceImpl.java:81-84,104-123](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImpl.java)——`getInternalChildProfile`被`getChildProfile`/`updateChildProfile`/`deleteChildProfile`/`getEducationProjections`共用，无擁有者檢查，會洩漏小孩的生日跟教育費推算。 |
| `GET/PUT/DELETE /profile/children/{childProfileId}/education-settings` | [ChildEducationSettingsController.java](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsController.java) | [ChildEducationSettingsServiceImpl.java:63,78,194](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java)——同样只查`ChildProfile`是否存在，不比对擁有者。 |
| `PATCH /assets/nisa/{id}/return-rate` | [NisaSnapshotRecordController.java:110-111](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaSnapshotRecordController.java) | [NisaSnapshotRecordServiceImpl.java:56](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaSnapshotRecordServiceImpl.java)——无擁有者檢查。 |
| `PATCH /assets/nisa-contributions/{id}/allocation` | [NisaContributionSettingController.java:81-82](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingController.java) | [NisaContributionSettingServiceImpl.java:56](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java)——无擁有者檢查。 |
| `DELETE /assets/dc/{id}`、`PATCH /assets/dc/funds/{fundId}/allocation` | [DefinedContributionController.java:174-189](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java) | [DefinedContributionServiceImpl.java:114,135](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java)——`updateAllocation`/`deleteSnapshotById`无擁有者檢查。 |
| `DELETE /events/career/{id}` | [CareerEventController.java:79-80](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/event/CareerEventController.java) | [CareerEventServiceImpl.java:81-83](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/event/CareerEventServiceImpl.java)——无擁有者檢查。 |
| `DELETE /events/life/{id}` | [LifeEventController.java:80-81](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/event/LifeEventController.java) | [LifeEventServiceImpl.java:70-72](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/event/LifeEventServiceImpl.java)——无擁有者檢查。 |

以上每一筆都是我自己讀取檔案逐行核對、不是沿用agent報告的清單直接採信——核對過程中也排除了一個agent報告沒提、但我主動檢查過的疑似案例：`UserProfileServiceImpl.getUserProfile`/`saveUserProfile`雖然也用`findById(id)`，但`UserProfile`不是`MultiUserBaseEntity`子類別，它的主鍵本身就等於JWT裡的`sub`（使用者自己的ID），且[UserProfileController.java:53-70](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/user/UserProfileController.java)固定是`/user-profiles/me`端點、`id`直接從JWT取得而不是從URL path variable取得，使用者沒有輸入任何可以用來指向別人ID的管道，不構成IDOR，不列入上表。

## 影響範疇（第一種症狀：IDOR）
這些端點涵蓋房貸、保險、年金、薪資、分類、小孩資料、NISA、DC/iDeCo、人生事件——幾乎是整個REST API表層裡「單筆資源CRUD」這一類端點的全部。只要攻擊者是系統裡任何一個已登入使用者（不需要跟被害者有任何家族關係），對這些端點打一個猜測的數字ID，就能讀取/竄改/刪除其他使用者的真實財務資料。三級隱私層級、家族Filter這整套機制，對這些端點完全不生效——不管`privacyLevel`是`PRIVATE`還是`SHARED`，不管有沒有家族關係都一樣。

## 建議修正方向（第一種症狀：IDOR）
修法本身並不複雜，核心是在每個Service的`findById`/`update`/`delete`之前，統一比對`entity.getOwnerUserId()`（或家族成員名單）跟目前使用者，仿照`MortgageRecordServiceImpl.validateAndApplyTaxLogic`裡已經存在的那段檢查模式（70行附近），全面補齊到所有依ID操作的入口。因為同一個模式要在十幾個Service裡重複套用，比較適合的做法可能是一個共用的ownership檢查工具方法或AOP切面，而不是逐一手刻——但這個設計決定留給你判斷，這裡不代為拍板。

---

## 第二種症狀：模擬引擎建立`SimulationContext`時，幾乎所有財務資料查詢都用沒有範圍限制的方法，`familyFilter`啟用時會把家族成員的資料混進自己的模擬裡

### 核心原則：稅務扣除以本人為單位，模擬引擎抓資料時必須預設鎖定「只抓本人」，只有明確列舉的例外才允許讀取他人資料
日本稅務計算，絕大多數項目的計算基礎都是「本人的所得/本人名下的資產」，不是「家庭合計」——房貸抵稅看本人名下房貸、iDeCo/NISA是個人專屬免稅額度、薪資所得稅看本人薪資單、年金看本人的保險費紀錄。**只有少數幾種稅務項目，設計上本來就需要讀取「別人」的所得資料作為輸入值**——目前已知的例外是配偶特別控除（需要配偶的所得去查表）、扶養親屬控除（需要扶養親屬的所得去判斷資格），這兩者讀的是`Dependent`這個使用者自己手動填寫的「依附型家人」資料（見`feature-requirement-129`併入後的`Plan-03`「Hybrid Family Member Architecture」討論），不是透過`familyFilter`去撈真實連結帳號的資料。

這代表模擬引擎在抓取房貸、薪資、NISA、iDeCo/DC、年金、銀行現金、股票帳戶這些資料時，**預設就必須是「只抓本人」，不能依賴環境裡剛好啟用的是哪個Hibernate filter**——不是因為效能或架構偏好，是因為稅法本身就是這樣規定扣除範圍的，拉錯資料範圍，算出來的數字就是錯的，不是「精確度打折」而已。

### 相關檔案（已確認，不只房貸一處）
`SimulationContextFactory`本身跟它委派出去的四個`XxxContextBuilder`，建立模擬所需的整套財務資料時，用的查詢方法全部是這個模式——方法名稱裡完全沒有`OwnerUserId`這個字樣，全部依賴目前request啟用的filter決定範圍：
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java:158`（`childProfileRepository.findAll()`，小孩資料）
- 同檔案`:183-186`（`etradeRepository.findFirstByOrderByRecordDateDesc()`，E-Trade股票帳戶快照）
- 同檔案`:190-193`（`bankTransactionRepository.findDistinctBankNames()`、`findFirstByBankNameOrderByTransactionDateDesc()`，銀行現金餘額）
- 同檔案`:200`（`mortgageRecordRepository.findAll()`，房貸）
- 同檔案`:203`（`insurancePolicyRepository.findAll()`，保單）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java:69,85`（`salaryUserSettingsRepository.findFirstBy()`、`salaryRecordRepository.findAllByOrderByPaymentDateDesc()`，薪資）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java:52,54,57,111`（NISA快照、設定、提繳分配）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java:48,49,122`（企業DC、iDeCo快照跟設定）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/PensionContextBuilder.java:48`（`findAll(Sort.by(...))`，年金紀錄）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:83`（`mortgageRecordRepository.findAll()`，跟`SimulationContextFactory`裡的房貸查詢是獨立的第二處）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java:81`（`careerEventRepository.findAllByOrderByEventYearMonthAsc()`，職涯事件——跟上面房貸查詢同一個方法`fetchSimulationData()`裡，當初枚舉時漏列了同一方法裡緊鄰的這兩行）
- 同檔案`:82`（`lifeEventRepository.findAllByOrderByStartYearMonthAsc()`，人生事件——同樣的漏列）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/OwnerFilterInterceptor.java:79-92`（`X-View-Scope: FAMILY`這個header會啟用`familyFilter`，查詢範圍會涵蓋`familyService.getFamilyMemberIds(userId)`回傳的所有家族成員）

### 現狀
以上所有實體都繼承`MultiUserBaseEntity`，查詢時理論上會受`personalFilter`（預設，只看自己的）或`familyFilter`（`X-View-Scope: FAMILY`時，看得到全家）這兩個Hibernate filter的其中一個約束。但上面列出的每一處查詢，完全沒有另外加`ownerUserId`條件去限定「只抓自己的」——它們依賴的是目前這次request啟用了哪個filter，而不是自己主動限定範圍。

### 為何是問題
這個產品原始的設計是單人單機，資料模型從一開始沒有「多人」這個概念。後來為了做家族共享功能，在`MultiUserBaseEntity`上加了`personalFilter`/`familyFilter`這兩個Hibernate filter，想用「切換filter範圍」的方式讓家人彼此在列表畫面上看得到對方的資料——但這個機制同時也套用到了模擬引擎抓資料這個完全不同的情境，而模擬引擎原本的設計假設是「這份清單裡只會有我自己的資料」，從來沒有被改造成「知道怎麼把配偶的資料獨立處理、而不是跟自己的混在一起算」。

這代表只要某一次request是用`X-View-Scope: FAMILY`去呼叫模擬相關的API（不管是故意的、還是前端某個頁面不小心帶了這個header），模擬引擎會把家族成員名下的房貸、薪資、NISA、iDeCo/DC、年金、銀行現金、股票帳戶、小孩資料、保單，連同自己的一起塞進同一個`SimulationContext`裡去跑。

這不是「過濾過頭」，是「過濾不夠」——家族共享列表畫面需要的「看得到彼此」，跟模擬引擎需要的「每次只算一個人的資料」，是兩個互相衝突的需求，現在共用同一套filter機制，沒有在模擬引擎這一側額外補上「不管filter是什麼範圍，這裡一律只抓自己的」這道防線。

### 具體錯誤情境
使用者跟配偶都加入了同一個家族群組，各自名下有一筆房貸（ペアローン常見安排，各自獨立適用減稅額度，不是加總）、各自有獨立的NISA/iDeCo帳戶跟薪資紀錄。如果某次呼叫模擬API的request帶了`X-View-Scope: FAMILY`，`SimulationContextFactory`/`AssetSimulationEngineImpl`抓到的清單會同時包含使用者跟配偶兩人的房貸、薪資、NISA、iDeCo——不管是哪一項，計算結果都已經混進了不該出現在這次模擬裡的配偶資料。

### 建議修正方向
在模擬引擎讀取這些repository之前，應該明確限定只抓目前使用者自己的資料（例如改用`findByOwnerUserId(currentUserId)`這類查詢方法，不要依賴目前request啟用的是哪個Hibernate filter）。這樣可以確保不管前端有沒有不小心帶上`X-View-Scope: FAMILY`、不管家族共享列表功能的filter範圍怎麼設計，模擬引擎本身都會自己把範圍鎖定在「只算自己的」，跟列表查詢用的filter機制完全脫鉤。這個修法跟`Plan-03`裡「配偶財務資料要獨立算一份、最後再合併」的設計方向一致，但不需要等Plan-03整個做完——這些查詢沒有範圍限制的問題，是現在就存在的風險，不是只有Plan-03做完後才會出現的問題。

### 不可退讓的邊界：修第一種症狀時，絕對不能連帶放寬這裡的範圍
第一種症狀（IDOR）跟第二種症狀，雖然這份issue把它們合併記錄，但修法的「允許範圍」方向是相反的：
- **第一種症狀（`findById`/`deleteById`）**：某些端點（例如查看、而非修改）讓家族成員能看到彼此的資料本來就是這個產品的設計意圖，修法時合理的做法可能是「owner本人或在家族名單內且隱私層級允許」——這點視端點性質（唯讀vs異動）由實作者判斷。
- **第二種症狀（模擬引擎建立`SimulationContext`時的所有查詢）**：不管前面那條怎麼修，**模擬引擎抓房貸、薪資、NISA、iDeCo/DC、年金、銀行現金、股票帳戶這些查詢，絕對不能把家族成員的資料拉進來一起跑**。這些項目的稅務計算基礎原則上就是「本人」，不是「家庭」——這不是效能或架構上的偏好，是稅法原則本身決定的，沒有模糊空間。唯一的例外是配偶特別控除、扶養親屬控除這兩項本來就設計成要讀取他人所得作為輸入值的計算，但它們讀的是`Dependent`這個手動填寫的依附型資料，不是透過`familyFilter`撈真實連結帳號，跟這裡討論的`findAll()`範圍問題是兩條完全不同的資料路徑，不會被這裡的修正影響到。

修正第一種症狀時，如果圖方便直接重用同一套「family-aware」的查詢邏輯或共用方法套用到模擬引擎這裡，等於會把第二種症狀的防線重新打開一次。這兩處的修法必須分開看待、分開驗證，`SimulationContextFactory`跟它底下四個`XxxContextBuilder`、`AssetSimulationEngineImpl`裡牽涉上述財務資料的查詢，修完後都必須明確只回傳目前使用者自己的資料，不能用任何形式的家族範圍查詢取代，驗證時務必包含「使用者跟家族成員各自有房貸/NISA/iDeCo且都有資料」這種情境，確認模擬結果只包含使用者自己的那一份。

---

## 兩種症狀的共同建議
修第一種症狀（IDOR）跟第二種症狀（模擬引擎資料污染），技術手法不同（一個要補`findById`前的擁有者比對，一個要把`findAll()`換成限定擁有者的查詢），但都指向同一個根本缺口：這個系統目前沒有任何一處用程式碼明確寫下「這裡的資料範圍該是什麼」，全部交給環境裡那個剛好被enable的Hibernate filter去決定。如果要systematic地解決，可能值得在補這些個案修正的同時，一併考慮是否要建立一個更明確的「資料存取一定要經過範圍檢查」的統一機制（例如前面提過的AOP切面，或是把這類查詢方法集中到一個固定的base repository/service裡），而不是逐一在十幾個Service裡各自補一行檢查——但這個設計決定留給你判斷，這裡不代為拍板。

---

## 系統性推薦解法：解法 1 與 解法 2 併用架構

若在 15 個 Service 逐一手刻 `if (!entity.getOwnerUserId().equals(currentUserId))`，不僅容易在未來新增端點時遺漏，更會造成「有些地方靠 Filter、有些地方靠手寫 if-else」的雙重維護地獄。

最佳實踐是將兩者**併用**：透過 Spring Data JPA 基礎架構層將 Filter 真正發揮到全域（解法 1），並在計算核心劃清個人邊界（解法 2）。

### 1. 解法 1：自訂 Spring Data JPA `BaseRepository`（全域修復 IDOR，0 個 Service 需要改動）

Hibernate `@Filter` 之所以對 `findById(id)` 失效，是因為 Spring Data JPA 預設的 `SimpleJpaRepository` 底層直接呼叫了 `entityManager.find(domainClass, id)`（主鍵直查，繞過 Query 轉譯引擎）。

**根本做法**：自訂 `MultiUserJpaRepositoryImpl<T, ID>` 繼承 `SimpleJpaRepository<T, ID>`，在底層覆寫 `findById`，改走 JPQL 查詢：

```java
public class MultiUserJpaRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {

    private final EntityManager entityManager;

    public MultiUserJpaRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public Optional<T> findById(ID id) {
        // 若該 Entity 繼承 MultiUserBaseEntity，改走 JPQL 查詢！
        // 一旦走 JPQL，當前 Session 啟用的 personalFilter 或 familyFilter 就會 100% 自動追加條件！
        if (MultiUserBaseEntity.class.isAssignableFrom(getDomainClass())) {
            String jpql = "SELECT e FROM " + getDomainClass().getSimpleName() + " e WHERE e.id = :id";
            return entityManager.createQuery(jpql, getDomainClass())
                    .setParameter("id", id)
                    .getResultStream()
                    .findFirst();
        }
        return super.findById(id);
    }
}
```

* **效益**：
  - 15 個 Service 內的 `repository.findById(id)` **一行程式碼都不用改**。
  - 當使用者 A 嘗試查詢使用者 B 的資料時，SQL 自動被附加 `AND owner_user_id = 'user-A'`，直接回傳 `Optional.empty()` -> 404 Not Found。
  - Filter 真正發揮全域防護價值，維護性最高。

---

### 2. 針對 `deleteById`（刪除操作）的設計：保留未來「世帯主」管理刪除的擴充性

原先曾考慮在 `deleteById` 強制硬編碼 `e.ownerUserId = :currentUserId`，但經檢討，未來規劃可能支援「世帯主（戶長）」管理或刪除家族成員資料的功能，若在底層 Repository 硬性鎖死本人會妨礙未來擴充。

因此，**`deleteById` 遵循與 `findById` 相同的解法 1 機制**：於 `MultiUserJpaRepositoryImpl` 中改走 JPQL 查詢實體後再刪除，使之正常套用當前啟用的 Filter：

```java
    @Override
    @Transactional
    public void deleteById(ID id) {
        Assert.notNull(id, "The given id must not be null");

        if (MultiUserBaseEntity.class.isAssignableFrom(getDomainClass())) {
            // 改走 JPQL 查詢，正常套用 Hibernate Filter（個人模式下杜絕 IDOR 跨用戶刪除）
            String jpql = "SELECT e FROM " + getDomainClass().getSimpleName() + " e WHERE e.id = :id";
            T entity = entityManager.createQuery(jpql, getDomainClass())
                    .setParameter("id", id)
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException(
                            getDomainClass().getSimpleName() + " not found with id: " + id));

            entityManager.remove(entity);
            return;
        }

        super.deleteById(id);
    }
```

* **效益**：
  - **個人模式（預設）**：受 `personalFilter` 保護，查不到他人資料（404），徹底杜絕跨戶越權刪除（IDOR）。
  - **家族模式（未來擴充）**：保有彈性，世帯主是否有權刪除家人資料交由未來的家族角色與業務權限層決定，底層 Repository 不會過度設限。

---

### 3. 單筆查詢與個人設定的落地方針：一對一 vs 一對多

* **個人設定類（一對一，如 `SalaryUserSettings`）**：
  以前的 `findFirstBy()` 改為 👉 **`findByOwnerUserId(currentUserId)`**（100% 正確且最安全）。
* **一般業務資料類（一對多，如房貸、保單、小孩）**：
  繼續維持呼叫 👉 **`findById(id)`**，底層交由 **解法 1（BaseRepository 走 JPQL）** 動態且安全地套用 Filter。

#### 詳細設計理由與邊界分析：
目前 `SalaryUserSettings`、`NisaUserSettings`、`DefinedContributionUserSettings` 採用 `repository.findFirstBy()`，在家族模式（`familyFilter`）下會將全家人的設定一併查出，導致隨機抓取配偶設定並互相覆寫。

* **為什麼不能把一般業務資料的 `findById` 全部改成 `findByOwnerUserId`？**
  1. **資料是一對多的**：一個使用者可能有多筆房貸、保單或交易明細，`findByOwnerUserId` 回傳的是清單（`List<T>`），無法指定特定一筆資料。
  2. **會徹底打爛家族共享**：在家族模式下，配偶若要查看對方標記為 `SHARED` 的房貸（例如 `GET /mortgages/20`），若硬查 `owner = currentUserId` 會直接回傳 404，全家共享功能直接報銷。因此一般業務資料必須維持 `findById(id)` 並依賴解法 1 的動態 Filter。
* **為什麼個人設定不能/不需改用 `findById`？**
  - 這三個實體繼承自 `BaseEntity`，主鍵為自增流水號 `Long id`（非 `userId`）。
  - 前端發送 `GET /settings` 或 `PUT /settings` 時並未傳遞也不知曉該內部數字 `id`，後端拿不出 `id` 呼叫 `findById`。
* **最佳做法**：
  直接在 Repository 宣告並於 Service 呼叫 **`findByOwnerUserId(SecurityUtil.getCurrentUserId())`**：
  ```java
  Optional<SalaryUserSettings> findByOwnerUserId(String ownerUserId);
  ```
  - **效益**：SQL 強制限定 `WHERE owner_user_id = :currentUserId`，無論當前 Filter 是個人還是家族，100% 精準鎖定本人，徹底根除覆寫與碰撞，且完全不需更動資料庫 Schema。

---

### 4. 解法 2：模擬引擎與 HTTP View Scope 徹底脫鉤（保證個人純計算邊界）

`SimulationContextFactory` 與四個 `XxxContextBuilder` 抓取資料時，本質是**「純個人財務試算」**，與前端用來顯示列表的 `X-View-Scope: FAMILY` 完全無關。

**可採行的兩種乾淨途徑**：
- **途徑 A（進入點鎖定 Scope，推薦）**：
  在進入模擬引擎或 `SimulationContextFactory.createContext()` 的入口，強制將 Session Filter 鎖定為當前使用者的 `personalFilter`（或暫時關閉 `familyFilter`）：
  ```java
  Session session = entityManager.unwrap(Session.class);
  session.disableFilter("familyFilter");
  session.enableFilter("personalFilter").setParameter("userId", currentUserId);
  ```
  確保後續所有 `findAll()` 查詢只會抓出目前使用者本人的資料，不被前端 Header 影響。
- **途徑 B（Repository 顯式宣告）**：
  在模擬引擎專用的查詢方法中，明確宣告 `findAllByOwnerUserId(currentUserId)`。即便環境開啟了 `familyFilter`，SQL 條件交集後依然嚴格鎖定本人。

---

### 5. 非 HTTP 上下文（背景 Job 排程）之影響評估：微乎其微（即將整批棄用）

關於非 HTTP 環境下缺少 JWT 上下文導致 Hibernate Filter 無法自動啟用的問題：
* **查證結果**：經查證專案既有文件（如 `Fixed-81.md:127` 明確記錄「掃資料夾找 HTML 檔案的匯入方式之後會整個被棄用」、`WONTFIX-18.md:4` 亦記錄「屬於即將被 Chrome extension 取代的背景 job，之後會整批棄用」以及 `Plan-18`）。
* **結論**：本地端資料夾掃描的 Job 排程本來就是過渡期設計，未來將全面改由 Chrome Extension 匯入資料。因此，**「非 HTTP 環境下缺少 JWT 上下文導致 Hibernate Filter 無法自動啟用」**的問題確實微乎其微，完全不值得投入成本為即將棄用的 Job 重構上下文注入機制。

---

### 6. 整體架構職責分工總覽

| 操作類別 | 呼叫方式 | 攔截與防護機制 | 權限邊界 |
|---|---|---|---|
| **一般列表查詢**<br>（GET `/mortgages` 等） | `repository.findAll()` | 依 Request Header 套用 `personalFilter` 或 `familyFilter` | 個人模式看自己；家族模式看家人 SHARED 資料 |
| **一般單筆查詢**<br>（GET `/mortgages/{id}`） | `repository.findById(id)` | **【解法 1】** `MultiUserJpaRepositoryImpl` 改走 JPQL 套用 Filter | 依當前 Scope 自動過濾，杜絕 IDOR 任意越權讀取 |
| **單筆刪除操作**<br>（DELETE `/mortgages/{id}`） | `repository.deleteById(id)` | **【解法 1】** `MultiUserJpaRepositoryImpl` 改走 JPQL 套用 Filter | 杜絕跨戶越權刪除；為未來世帯主管理家族資料保留擴充彈性 |
| **個人設定查詢/更新**<br>（GET/PUT `/settings`） | `repository.findByOwnerUserId(currentUserId)` | 明確以 `ownerUserId` 查詢 | **100% 鎖定本人**，家族模式下絕不隨機碰撞或覆寫他人設定 |
| **財務模擬試算**<br>（POST `/simulations/run`） | `SimulationContextFactory` | **【解法 2】** 強制鎖定 Personal Scope 或顯式 `ownerUserId` 查詢 | **僅限本人財務**，稅務、年金與現金流絕不受配偶資料污染 |
| **背景 Job 排程** | 排程定時執行 | 即將棄用（由 Chrome Extension 取代） | 無需過度工程重構上下文注入 |

---

### 7. 報告涵蓋端點與查詢點之完整防衛對照表（覆蓋第 22～69 行所有項目）

#### 表一：第一種症狀（IDOR 單筆 CRUD 端點 $\rightarrow$ 全部由【解法 1】全域防衛）

此類型的共同特徵是透過 `@PathVariable` 傳入資料庫 ID（`Long id`）。**全數由【解法 1（自訂 BaseRepository 改走 JPQL）】在底層自動套用 Filter 防衛**，所有對應的 Service **0 行程式碼改動**：

| 端點 | 相關檔案與位置 | 原始脆弱呼叫 | 負責防衛的解法 | 防衛生效原理與效果 |
|---|---|---|---|---|
| `PUT /mortgages/{id}`<br>`DELETE /mortgages/{id}` | [`MortgageRecordController.java:73-83`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/mortgage/MortgageRecordController.java)<br>[`MortgageRecordServiceImpl.java:166,203`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java) | `repository.findById(id)`<br>`repository.deleteById(id)` | **【解法 1】BaseRepository** | 查詢與刪除皆改走 JPQL 套用 Filter：個人模式下查他人房貸回傳 404；刪除操作受 Filter 約束，杜絕 IDOR 惡意刪除。 |
| `GET/PUT/DELETE`<br>`/insurance-policies/{id}` | [`InsurancePolicyController.java:63-89`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/insurance/InsurancePolicyController.java)<br>[`InsurancePolicyServiceImpl.java:59-93`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/insurance/InsurancePolicyServiceImpl.java) | `repository.findById(id)`<br>`repository.deleteById(id)` | **【解法 1】BaseRepository** | 讀取、更新、刪除保單時自動附加 Filter 條件，非本人或非家族共享保單直接回傳 404。 |
| `DELETE /assets/pensions/{id}` | [`PensionRecordController.java:123`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/pension/PensionRecordController.java)<br>[`PensionRecordServiceImpl.java:77,120`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionRecordServiceImpl.java) | `pensionRecordRepository.findById(id)`<br>`pensionRecordRepository.deleteById(id)` | **【解法 1】BaseRepository** | 年金紀錄單筆查閱與刪除自動套用 Filter，跨戶惡意操作回傳 404。 |
| `GET/PUT/DELETE`<br>`/transaction-categories/{id}` | [`CategoryController.java:79-120`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/category/CategoryController.java)<br>[`CategoryServiceImpl.java:69,103,131`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/category/CategoryServiceImpl.java) | `categoryRepository.findById(id)`<br>`categoryRepository.findById(parentId)`<br>`categoryRepository.deleteById(id)` | **【解法 1】BaseRepository** | 分類本體與綁定父分類時皆會過濾，防止將他人分類設為父分類或刪除他人分類。 |
| `PUT /salaries/{id}` | [`SalaryRecordController.java:105`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/salary/SalaryRecordController.java)<br>[`SalaryRecordServiceImpl.java:81-83`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryRecordServiceImpl.java) | `salaryRecordRepository.findById(id)` | **【解法 1】BaseRepository** | 修改薪資明細時改走 JPQL，杜絕竄改他人薪資單。 |
| `PATCH/DELETE`<br>`/profile/children/{id}` | [`ChildProfileController.java:75-93`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/ChildProfileController.java)<br>[`ChildProfileServiceImpl.java:81-123`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/ChildProfileServiceImpl.java) | `childProfileRepository.findById(childId)`<br>`childProfileRepository.delete(child)` | **【解法 1】BaseRepository** | `getInternalChildProfile` 共用查閱點受 Filter 保護，杜絕外人窺探或刪除小孩資料。 |
| `GET/PUT/DELETE`<br>`/profile/children/{id}/education-settings` | [`ChildEducationSettingsController.java`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/profile/child/education/ChildEducationSettingsController.java)<br>[`ChildEducationSettingsServiceImpl.java:63,78,194`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/profile/child/education/ChildEducationSettingsServiceImpl.java) | `childProfileRepository.findById(childProfileId)` | **【解法 1】BaseRepository** | 檢查關聯小孩主體時改走 JPQL，他人小孩直接 404，無法偷看或改寫教育費推算。 |
| `PATCH /assets/nisa/{id}/return-rate` | [`NisaSnapshotRecordController.java:110`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaSnapshotRecordController.java)<br>[`NisaSnapshotRecordServiceImpl.java:56`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaSnapshotRecordServiceImpl.java) | `nisaRepository.findById(id)` | **【解法 1】BaseRepository** | 修改 NISA 預期報酬率時套用 Filter，杜絕竄改他人資產標的設定。 |
| `PATCH /assets/nisa-contributions/{id}/allocation` | [`NisaContributionSettingController.java:81`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/nisa/NisaContributionSettingController.java)<br>[`NisaContributionSettingServiceImpl.java:56`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/NisaContributionSettingServiceImpl.java) | `settingRepository.findById(id)` | **【解法 1】BaseRepository** | 修改 NISA 定期定額提繳金額與分配時套用 Filter，杜絕竄改他人積立配置。 |
| `DELETE /assets/dc/{id}`<br>`PATCH /assets/dc/funds/{id}/allocation` | [`DefinedContributionController.java:174`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/dc/DefinedContributionController.java)<br>[`DefinedContributionServiceImpl.java:114,135`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java) | `dcFundRepository.findById(fundId)`<br>`dcRepository.existsById(id)`<br>`dcRepository.deleteById(id)` | **【解法 1】BaseRepository** | `existsById` 走 COUNT JPQL 查他人直接為 0 回傳 404；`deleteById` 與 `findById` 均受 Filter 約束，杜絕 IDOR 刪除與改寫。 |
| `DELETE /events/career/{id}` | [`CareerEventController.java:79`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/event/CareerEventController.java)<br>[`CareerEventServiceImpl.java:81`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/event/CareerEventServiceImpl.java) | `careerEventRepository.deleteById(id)` | **【解法 1】BaseRepository** | 職涯異動事件刪除操作改走 JPQL，杜絕跨用戶惡意刪除。 |
| `DELETE /events/life/{id}` | [`LifeEventController.java:80`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/event/LifeEventController.java)<br>[`LifeEventServiceImpl.java:70`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/event/LifeEventServiceImpl.java) | `lifeEventRepository.deleteById(id)` | **【解法 1】BaseRepository** | 人生事件刪除操作改走 JPQL，杜絕跨用戶惡意刪除。 |

#### 表二：第二種症狀（模擬引擎資料污染 $\rightarrow$ 由【解法 2】與【個人設定解法】防衛）

此類型的特徵是在計算模擬 Context 時調用「全抓（`findAll`）」或「抓第一筆（`findFirstBy`）」。在 `familyFilter` 下會把配偶資料混進來，由以下兩道防線分工防衛：

| 查詢對象與呼叫點 | 相關檔案與位置 | 原始脆弱呼叫 | 負責防衛的解法 | 防衛生效原理與效果 |
|---|---|---|---|---|
| **薪資設定**（個人設定） | [`SalaryContextBuilder.java:69`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java) | `salaryUserSettingsRepository.findFirstBy()` | **個人設定解法**：<br>`findByOwnerUserId(currentUserId)` | SQL 明確鎖定 `owner_user_id = 本人`，Family 模式下 100% 杜絕抓到配偶設定並互相覆寫。 |
| **NISA 設定**（個人設定） | [`NisaContextBuilder.java:54`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java) | `nisaUserSettingsRepository.findFirstBy()` | **個人設定解法**：<br>`findByOwnerUserId(currentUserId)` | SQL 明確鎖定本人，家族模式下保證讀取本人專屬的 NISA 投資設定。 |
| **DC/iDeCo 設定**（個人設定） | [`DefinedContributionContextBuilder.java:49`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java) | `dcUserSettingsRepository.findFirstBy()` | **個人設定解法**：<br>`findByOwnerUserId(currentUserId)` | SQL 明確鎖定本人，家族模式下保證讀取本人專屬的 DC/iDeCo 參數。 |
| **小孩資料** | [`SimulationContextFactory.java:158`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java) | `childProfileRepository.findAll()` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，只抓本人名下小孩資料，避免教育費推算重複計算。 |
| **股票帳戶快照** | [`SimulationContextFactory.java:183`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java) | `etradeRepository.findFirstByOrderByRecordDateDesc()` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，只在本人資料中抓最新快照，配偶股票帳戶絕不混入。 |
| **銀行現金餘額** | [`SimulationContextFactory.java:190`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java) | `bankTransactionRepository.findDistinctBankNames()`<br>`findFirstByBankNameOrderBy...()` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，銀行帳戶與餘額只統計本人，杜絕現金流計算失真。 |
| **房貸紀錄** | [`SimulationContextFactory.java:200`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java)<br>[`AssetSimulationEngineImpl.java:83`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java) | `mortgageRecordRepository.findAll()` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，日本房貸控除按本人名下計算，配偶名下房貸絕不重複抵扣。 |
| **職涯事件** | [`AssetSimulationEngineImpl.java:81`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java) | `careerEventRepository.findAllByOrderByEventYearMonthAsc()` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，使用者自己規劃的轉職/加薪情境絕不被配偶的職涯事件覆蓋或疊加。 |
| **人生事件** | [`AssetSimulationEngineImpl.java:82`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java) | `lifeEventRepository.findAllByOrderByStartYearMonthAsc()` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，使用者自己規劃的一次性/定期支出事件絕不被配偶的人生事件混入。 |
| **保單資料** | [`SimulationContextFactory.java:203`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SimulationContextFactory.java) | `insurancePolicyRepository.findAll()` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，保費支出與人壽保障只包含本人保單。 |
| **薪資與獎金紀錄** | [`SalaryContextBuilder.java:85`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/SalaryContextBuilder.java) | `salaryRecordRepository.findAllByOrderByPaymentDateDesc()` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，個人薪資所得與獎金預估不受配偶薪資污染。 |
| **NISA 快照與配置** | [`NisaContextBuilder.java:52,57,111`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/NisaContextBuilder.java) | `nisaRepository.findFirstByOrderByRecordDateDesc()` 等 | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，NISA 每年 360 萬與終身 1800 萬額度嚴格按本人資產試算。 |
| **DC/iDeCo 快照** | [`DefinedContributionContextBuilder.java:48,122`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java) | `dcRepository.findFirstByTypeOrderByRecordDateDesc(...)` 等 | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，iDeCo 專屬所得扣除額嚴格依本人提繳金額試算。 |
| **年金公費紀錄** | [`PensionContextBuilder.java:48`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/PensionContextBuilder.java) | `pensionRecordRepository.findAll(Sort.by(...))` | **【解法 2】模擬個人邊界** | 模擬入口強制鎖定 `personalFilter`，未來老齡年金公費給付只抓本人繳納月數與基數。 |
| **HTTP View Scope Header** | [`OwnerFilterInterceptor.java:79-92`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/config/OwnerFilterInterceptor.java) | `X-View-Scope: FAMILY` | **【解法 2】與 View Scope 脫鉤** | 模擬 API 執行時在 ContextFactory 入口強制鎖定 Personal Scope，不受前端任何 View Scope Header 影響。 |

---

### 8. 解法 1 的完整落地步驟：Repository Base Class 接線設定

`MultiUserJpaRepositoryImpl`這個類別寫出來之後，還需要讓Spring Data JPA實際知道要用它取代預設的`SimpleJpaRepository`，解法1才會真正生效。目前[`FinanceHubApplication.java:45`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/FinanceHubApplication.java)的設定是：

```java
@EnableJpaRepositories(basePackages = "ch.lin.finance.hub.backend")
```

落地時補上`repositoryBaseClass`參數，指向新建的類別：

```java
@EnableJpaRepositories(basePackages = "ch.lin.finance.hub.backend",
    repositoryBaseClass = MultiUserJpaRepositoryImpl.class)
```

加上這一行之後，Spring Data JPA才會把每一個繼承`JpaRepository`的介面，底層實作都換成`MultiUserJpaRepositoryImpl`，前面15個Service「0行程式碼需要改動」的效益才會真正兌現。

---

### 9. 解法 1 的簡化：`deleteById`不需要另外覆寫

反編譯專案實際鎖定的`spring-data-jpa-3.5.7.jar`可以確認，`SimpleJpaRepository`預設的`deleteById()`內部實作本來就是呼叫`this.findById(id)`取得實體後再刪除（Java的虛擬方法分派會自動呼叫到子類別覆寫後的版本）。這代表只要覆寫`findById()`，`deleteById()`會自動跟著套用同一套JPQL/Filter邏輯，不需要像前面範例那樣再手刻一次幾乎相同的JPQL查詢——`MultiUserJpaRepositoryImpl`可以只覆寫`findById()`一個方法，實作更精簡，也更不容易在兩處各自維護類似邏輯時出現落差。（找不到資料時該拋出什麼例外、對應到什麼HTTP狀態碼，可以留到實際實作時再透過global exception handler統一規劃。）

---

### 10. 解法 3：讓手動觸發的背景 Job 也套用個人範圍保護

前面第5點評估背景Job時，排程觸發（Scheduler）因為即將整批棄用、且本來就沒有HTTP上下文可用，可以維持不處理。但手動觸發（HTTP進來後丟給背景執行緒執行，例如[`ImportTaskController.java:68`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/job/ImportTaskController.java)的`POST /import/tasks`、[`TransactionSourceTaskController.java:92`](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/job/TransactionSourceTaskController.java)的`POST /tasks/{transactionSource}/process`）目前會繼續保留使用（唯一使用者兼測試身分需要用它確認匯入流程正確，之後家族Link功能上線後也需要用它驗證資料隔離是否正常），值得單獨規劃一套對應的保護。

**現狀**：這兩個端點本身都有`userId`（JWT帶進來的），但實際執行匯入邏輯時用`CompletableFuture.runAsync(...)`丟到新的背景執行緒，而`OwnerFilterInterceptor`enable的Hibernate Filter是掛在原本那條HTTP執行緒的Session上，不會跟著傳到新執行緒。兩個Controller目前都已經在背景執行緒裡重建了`SecurityContext`（`ImportTaskController`手動建立、`TransactionSourceTaskController`用`DelegatingSecurityContextRunnable`），這部分負責的是「背景執行緒知道userId是誰」，但還沒有連帶讓Filter在新執行緒的Session上enable。

**建議做法**：在同一個背景執行緒邏輯裡，用請求當下已經知道的`userId`，補一次跟`OwnerFilterInterceptor.preHandle()`一樣的動作：

```java
Session session = entityManager.unwrap(Session.class);
session.enableFilter("personalFilter").setParameter("userId", userId);
```

兩個Controller都已經有重建`SecurityContext`的邏輯，可以在同一個地方順便補上這段Filter設定，讓背景執行緒內的查詢（`findById`、`findAll`等）享有跟一般HTTP請求同等的保護，不用再額外設計新的機制。

查過目前Job邏輯裡實際的查詢呼叫，唯一一處`findById`（[`InsuranceJobServiceImpl.java:130`](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/insurance/InsuranceJobServiceImpl.java)：`userProfileRepository.findById(ownerUserId)`）用的是`userId`本身當主鍵查詢，不是資料庫自增ID，現在沒有被濫用的風險，所以這一步補強並不急迫；但補上之後可以讓解法1、解法2的保護範圍完整覆蓋手動觸發Job這條路徑，之後驗證家族資料隔離時會更可靠。

---

## 第三種症狀：`ExpenseServiceImpl.getExpenseDetail`完全繞過Repository層，直接用`EntityManager`查詢——比表一列出的IDOR端點更徹底，連「解法1」都救不到它

### 這份症狀怎麼被發現的
審查支出/分類/股票服務層時，在核對表一列出的IDOR端點清單是否完整時發現的——這不是表一漏列的同類項目，是一種結構上更嚴重的變體，獨立記錄在這裡。

### 相關檔案
- [ExpenseServiceImpl.java:104-137](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/expense/ExpenseServiceImpl.java)（`getExpenseDetail`，118-134行針對AMAZON/RAKUTEN/SOFMAP三種來源，各自呼叫`entityManager.find(XxxItem.class, id)`，整個方法找不到一處比對`ownerUserId`或家族關係）
- [ExpenseController.java:101-117](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/accounting/expense/ExpenseController.java)（`getExpenseDetails`，直接把URL path variable`{id}`拆成`source`跟`stringId`餵給上面的方法，`stringId`就是資料庫自增ID）
- [BaseOrderItem.java:46](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/BaseOrderItem.java)（`AmazonItem`/`RakutenItem`/`SofmapItem`繼承的是`PrivacyOverrideBaseEntity`，不是`MultiUserBaseEntity`）
- [PrivacyOverrideBaseEntity.java:41-49](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/base/PrivacyOverrideBaseEntity.java)（這個基底類別只有`privacyLevel`欄位，完全沒有`ownerUserId`欄位，也沒有宣告任何Hibernate `@Filter`——訂單項目本身在資料模型層級就沒有被設計成可以用擁有者去過濾，保護完全依賴訂單本身）
- [PrivacyMaskingUtil.java:60-74](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/util/PrivacyMaskingUtil.java)（`shouldMask`：只比對「目前使用者是不是擁有者」跟「隱私層級是不是SHARED」，完全沒有檢查目前使用者跟訂單擁有者是否屬於同一個家族——這個方法的設計前提是「能查到這筆資料的人，一定已經先被Hibernate Filter篩過一輪了」）
- [MultiUserBaseEntity.java:87-90](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/base/MultiUserBaseEntity.java)（`privacyLevel`預設值是`SHARED`——這代表系統裡幾乎所有訂單，只要使用者沒有手動改過隱私設定，預設都是`SHARED`）

### 現狀
`getExpenseDetail(source, stringId)`把`stringId`轉成`Long id`後，直接呼叫`entityManager.find(AmazonItem.class, id)`（Rakuten/Sofmap同理）。這是JPA的「依主鍵直接抓取」路徑，跟表一列出的`findById()`端點一樣會完全跳過Hibernate Filter——但情況比表一那些端點更徹底：`AmazonItem`這個實體類別本身繼承的是`PrivacyOverrideBaseEntity`，不是`MultiUserBaseEntity`，從頭到尾沒有`ownerUserId`欄位、也沒有宣告任何`@Filter`。也就是說，就算第107-144行提出的「解法1」（改走JPQL讓Filter生效）真的落地，這裡也不會被救到，因為訂單項目這個實體本來就沒有被賦予可以過濾的欄位——它的擁有者資訊只存在於它所屬的`AmazonOrder`（透過`item.getOrder().getOwnerUserId()`才拿得到）。

拿到實體之後，`ExpenseDetailResult.fromAmazonItem()`會把`item.getOrder().getOwnerUserId()`、`item.getOrder().getPrivacyLevel()`填進回傳物件，最後交給`ExpenseMapper.applyExpenseDetailMasking`判斷要不要遮罩。但`PrivacyMaskingUtil.shouldMask`的邏輯是：只要不是本人，且隱私層級不是`SHARED`才遮罩——隱私層級剛好是`MultiUserBaseEntity`的預設值，而Plan-19記錄的技術債是「所有領域目前都還沒開放`privacyLevel`欄位讓前端寫入」，代表系統裡幾乎所有訂單目前都停留在預設的`SHARED`。

### 為何是問題
把以上兩點疊加起來：這個端點完全不檢查呼叫者跟訂單擁有者是否有任何關係（不限同一家族，是任意兩個系統使用者），而且因為預設隱私層級是`SHARED`，連`PrivacyMaskingUtil`這道本來設計給「家族成員看彼此資料」用的遮罩機制，也不會把這筆資料擋下來——它會把它當成「本來就該被看到的家族共享資料」一樣直接回傳。

### 具體錯誤情境
使用者A跟使用者B是系統裡完全沒有任何家族關係的兩個獨立帳號。A對`GET /expenses/AMAZON_42/details`這個端點依序嘗試不同數字，猜到B名下第42筆Amazon訂單項目剛好存在。因為B沒有特別把這筆訂單設成`PRIVATE`（預設就是`SHARED`，而且Plan-19記錄前端目前也沒有這個設定入口），A會直接拿到B這筆訂單的賣家名稱、訂單總額、運費、已使用點數、折扣金額——這些都是具體的金錢數字，不是遮罩過的佔位字串。

### 建議修正方向
**方向A：在`getExpenseDetail`裡直接補一道擁有者/家族比對檢查**
- 做法：拿到`item.getOrder()`之後，比對`SecurityUtil.getCurrentUserId()`跟`order.getOwnerUserId()`（如果要支援家族查看，再加上家族成員名單跟隱私層級的判斷），不符合就回傳null（讓Controller轉成404）。
- 優點：修法集中在這一個方法，改動範圍小、立刻見效。
- 缺點：只解決這一個Service裡的這個漏洞；`entityManager.find()`這種繞過JpaRepository的呼叫方式，理論上系統別處如果也有同樣寫法，不會被這次修法連帶救到，需要另外全專案盤點。

**方向B：改用對應的JpaRepository.findById()，並同時處理「訂單項目本身沒有擁有者欄位」這個資料模型缺口**
- 做法：把`entityManager.find()`換成`AmazonItemRepository.findById()`等（如果這些Repository目前還沒有，需要新增），同時因為`AmazonItem`/`RakutenItem`/`SofmapItem`本身沒有`ownerUserId`，需要額外設計一個透過關聯到的`order.ownerUserId`去查詢/比對的方式（例如一個自訂的`findByIdAndOrder_OwnerUserId`查詢方法）。
- 優點：跟本份issue最終打算採用的系統性解法（統一走Repository層的保護機制）方向一致，不會變成散落各處、各自為政的修法；之後任何新呼叫這些Item類別的地方也會自動受益。
- 缺點：改動範圍比方向A大，牽涉到資料模型/查詢方法設計，需要跟本份issue最終選定的修法協調時機，不是單獨一行就能解決。

