# Plan 26：新增「清空所有分類」的endpoint——一次性刪除`TransactionCategory`整張表，連帶把所有引用它的交易/項目的`category_id`一併設成null

## 這份文件怎麼來的
在核對[issue-180](../pending%20fix/issue-180.md)系列（job import merge邏輯）銀行/信用卡/訂單項目三處merge方法時，確認了「分類的關聯性在現有merge邏輯下不受衝擊」這件事之後，衍生出一個新功能的討論：如果之後要做一個「清空所有分類」的畫面功能，應該怎麼設計才安全、合乎UX。這不是在修正任何現有bug，是一個全新功能，所以歸入planned/，不是issue。

## 這份Plan要解決的問題
查證確認`category_id`這個外鍵（存在[BankTransaction.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/bank/BankTransaction.java)、[CreditCardTransaction.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/creditcard/CreditCardTransaction.java)、[BaseOrderItem.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/BaseOrderItem.java)這三處，分別指向[TransactionCategory.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/category/TransactionCategory.java)）目前都沒有指定任何`onDelete`行為（Hibernate預設是`NO ACTION`/`RESTRICT`）。這代表：如果直接刪除一筆正被某筆交易/項目引用的`TransactionCategory`，資料庫會直接拋外鍵違反例外，操作失敗——順序必須反過來，**先把引用它的那一側（`category_id`）清成null，才能刪除`TransactionCategory`本身**。

查證也發現現有的單筆刪除端點（[CategoryServiceImpl.deleteCategory](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/category/CategoryServiceImpl.java)）完全沒有處理這個外鍵問題，直接呼叫`categoryRepository.deleteById(id)`——如果現在就刪除一個正被引用的分類，會直接失敗。這是一個獨立於這次討論、既有的缺口，這份Plan順便記錄，但主要目標是設計「清空所有分類」這個新功能。

## 設計定案：一次性全清
使用者已確認「清空所有分類」這個endpoint的語意是：一次性刪除`TransactionCategory`整張表的所有記錄，連帶把所有引用它的交易/項目的`category_id`一併設成null，不是只清空沒被引用的分類、保留有被引用的那些。

## 兩個落地方向，各自的優缺點

### 方向A：Service層依序執行——先批次update三個表的`category_id`為null，再呼叫`categoryRepository.deleteAll()`
做法：在一個`@Transactional`方法裡，依序對`BankTransactionRepository`、`CreditCardTransactionRepository`、以及訂單項目對應的repository（Amazon/Rakuten/Sofmap各自的item repository）各寫一個批次update（例如`@Modifying @Query("UPDATE BankTransaction b SET b.category = null")`），三個update都執行完之後，才呼叫`categoryRepository.deleteAll()`。

- **優點**：邏輯直接對應資料庫外鍵的實際限制，不依賴任何資料庫層級的隱性設定，三個步驟清楚寫在應用層程式碼裡，排查問題時容易一眼看出整個流程做了什麼。
- **缺點**：每多一個引用`category_id`的表（以後如果新增其他會用到分類的實體），這個清空方法就要跟著多加一個批次update，是需要手動維護、容易漏掉的清單——如果之後新增一個會引用`TransactionCategory`的新實體，寫這個新實體的人必須記得回來改這個清空方法，不然這個新endpoint在未來會悄悄變得不完整。

### 方向B：資料庫外鍵改成`ON DELETE SET NULL`，之後直接呼叫`deleteAll()`即可，不需要Service層手動清三個表
做法：把三個`@JoinColumn`的外鍵約束都加上Hibernate的`@OnDelete(action = OnDeleteAction.SET_NULL)`，讓資料庫層級在刪除被引用的`TransactionCategory`時，自動把所有指向它的`category_id`設成null。

- **優點**：Service層的「清空所有分類」跟現有的單筆`deleteCategory`都不需要額外寫任何清空關聯的程式碼，資料庫自動處理，而且這個改動同時補上了前面查到的現有缺口——單筆`deleteCategory`刪除被引用的分類目前會直接丟外鍵例外，加上這個設定後會自動變安全，不用額外修正那個既有問題。
- **缺點**：這是修改現有表結構，需要寫一次migration；而且這屬於資料庫層級的「隱性」行為——以後任何人刪除一筆`TransactionCategory`，不管是透過這個新端點、現有的單筆`deleteCategory`、還是之後有人直接操作資料庫或寫一支新的批次腳本，都會自動波及所有引用它的交易/項目，這個自動清空的動作不會顯式地出現在呼叫它的那段應用層程式碼裡，對不熟悉這個設定的人來說，排查「為什麼這些交易的分類突然消失了」會比較不容易追查到根因。

## 兩個方向的關係：不互斥，可以疊加
方向B解決的是資料庫外鍵約束本身（讓刪除動作在資料庫層級自動安全），方向A是完全由應用層程式碼控制、不依賴任何資料庫自動行為的清空邏輯。採用方向B之後，方向A原本要寫的三個批次update其實就不需要了（直接呼叫`deleteAll()`，資料庫會自動處理）；但如果希望保留「所有清空動作都顯式寫在程式碼裡」的可讀性、不依賴資料庫隱性行為，即使採用了方向B的FK設定，也可以選擇在Service層依然保留方向A那三行批次update（這時候資料庫的`SET NULL`只是一層保險，不是唯一依靠）。

## 這份文件沒有解決的部分
- 要採用方向A、方向B、還是兩者疊加，留給使用者拍板，不代為決定。
- 現有單筆`deleteCategory`端點目前刪除被引用分類會直接失敗這個既有缺口，要不要獨立另外修正、還是等這份Plan落地時一併處理，留給使用者決定。
- 「清空所有分類」這個endpoint本身要放在哪一層UI（例如要不要加二次確認、要不要先讓使用者看到有多少筆交易/項目會被影響），這份文件不涉及前端設計。
