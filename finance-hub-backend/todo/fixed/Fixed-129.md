# [Fixed] Feature Requirement 129：`Dependent`不棄用，定位為「真實帳號尚未綁定前的備援資料來源」——優先讀真實家族帳號，沒有才退回手動填寫

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板：`Dependent`（手動填寫的配偶／扶養親屬資料）不完全棄用，未來模擬引擎要優先讀取真實連結的家族帳號資料，只有在沒有設定連結（或對方還沒加入家族群組）時，才退回沿用`Dependent`裡手動填寫的資料。

## 背景
目前系統裡有兩套完全獨立、互不相通的「家人」資料：

1. **`Dependent`**（`finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java`，資料表名稱`finance_family_member`）：使用者自己在Profile畫面手動填寫的配偶／扶養親屬資料，只有生日跟預估年收入兩個欄位，純粹用來計算扶養扣除、配偶扣除這類稅務項目，跟任何真實帳號系統無關。
2. **`FamilyMember`**（`finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/family/FamilyMember.java`，資料表名稱`finance_family_membership`）：屬於已經完成的多用戶架構，代表一個真實、有登入帳號的使用者加入了家族群組。

這個落差已經記錄在`Epic: Phase 1 - 多使用者架構、家族共享與隱私控制.md`（第219-233行）跟`Epic: Phase 2 - 家族財務服務與模擬器整合.md`的已知設計債裡，Phase 2文件裡的「Hybrid Family Member Architecture」段落已經提出具體解法：在`Dependent`上加一個可為空的欄位`linked_user_id`，如果配偶/扶養親屬本人也在系統裡有真實帳號、且確實在同一個家族群組內，模擬引擎就改抓真實帳號的實際收入、年金等資料；如果沒有綁定，就退回沿用現在手動填寫的資料。

已查證：這個`linked_user_id`欄位目前完全沒有被實作，整個後端程式碼裡找不到任何蹤跡——這件事目前還停在規劃階段，只在Phase 2文件的參考資料段落裡以架構草稿的形式出現，沒有排入任何具體的子任務清單。

## 為何決定不棄用`Dependent`
即使家族功能真正做起來、能拿到配偶的真實所得/年金等更準確的資料，`Dependent`仍然有存在的價值——配偶/家人還沒有加入家族群組的這段期間（例如對方還沒有意願註冊、或是未成年小孩本來就不需要有自己的帳號），使用者需要能自己先粗估一次配偶扣除等稅務項目，不能因為做了真實帳號連結，就讓這段空窗期完全沒有任何資料可用。這跟Phase 2文件裡「Hybrid」架構原本的設計精神一致：兩套資料是主要／備援關係，不是要二選一淘汰其中一個。

## 與issue-103的關係（不會被這次任務自動解決）
即使做完真實帳號連結、`Dependent`退居備援角色，[issue-103](issue-103.md)（配偶扣除完全沒有判斷70歲以上）這個bug仍然會存在——issue-103壞在`SpouseDeductionEngine`這個計算引擎本身沒有讀取生日資料去判斷年齡，跟「生日資料從`Dependent`手動填寫來、還是從真實連結帳號來」是兩個獨立的問題，換了資料來源不會自動修好這個計算邏輯本身的漏洞，issue-103需要獨立修正，不會因為做了這個任務而自動解決。

## 想要的行為
1. `Dependent`實體上新增一個可為空的欄位`linkedUserId`，讓使用者可以把某一筆手動填寫的配偶/扶養親屬紀錄，綁定到一個真實的家族帳號（`FamilyMember`）上。
2. 模擬引擎在取得配偶/扶養親屬的收入、年金等資料時，依照以下順序解析：
   - 如果`linkedUserId`有值，且對應的真實帳號確實還在同一個家族群組內，改抓該真實帳號的實際財務資料（收入、年金等）。
   - 如果沒有綁定（`linkedUserId`為空），或綁定的對象已經離開家族群組，退回沿用`Dependent`裡手動填寫的生日、預估年收入。
3. 前端提供對應的UI，讓使用者可以選擇「綁定到某個已加入家族群組的成員」或「維持手動填寫」。

## 相關檔案（供實作者定位，依Phase 2文件的Hybrid架構草稿）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/dependent/Dependent.java`（新增`linkedUserId`欄位）
- `finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/family/FamilyMember.java`（真實家族帳號，`linkedUserId`要對應到這裡的`userId`）
- 模擬引擎讀取配偶/扶養親屬資料的相關路徑（`SpouseDeductionEngine`、`DependentDeductionEngine`，以及Epic Phase 2文件裡規劃的`FamilyFinancialService`——已確認這個服務目前完全沒有被實作，見對帳與購物那份review報告的確認記錄）
- `Epic: Phase 2 - 家族財務服務與模擬器整合.md`的「Hybrid Family Member Architecture」段落，裡面已經有這個架構的完整設計草稿可以直接參考

## 待確認的排程問題
`linked_user_id`（這裡命名為`linkedUserId`）本身目前完全沒有排入任何具體的子任務清單，且依賴Epic Phase 2的`FamilyFinancialService`（目前完全未實作）才能真正發揮作用——這個任務的具體排程時間點，需要放在Phase 2其他子任務的脈絡下一起規劃，不是一個能獨立、馬上動手的小任務。
