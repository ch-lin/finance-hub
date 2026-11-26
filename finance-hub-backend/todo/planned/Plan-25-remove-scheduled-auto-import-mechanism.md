# Plan 25：移除「背景排程自動掃描匯入」機制（TaskScheduler定時觸發），避免之後有人誤以為這是活著的功能、重新投入修正成本

## 來源
使用者確認：這套定時排程自動掃描資料夾匯入的功能，PM很久以前已經決定要移除（這個決定本身可能沒有留下明確的文件紀錄，但方向跟專案既有文件互相吻合，見下方「既有紀錄佐證」）。這份Plan的直接起因，是這次Source Code Review審查年金/薪資服務層時發現一個bug——排程Job沒有HTTP context導致`ownerUserId`為null、存檔失敗（原記錄在issue-184，已確認整篇前提因這份Plan而不成立，撤回併入本文件，見下方「原issue-184記錄的具體觸發案例」）——使用者指出「不是說排程job功能會被移除嗎？怎麼擅自被復活？」，確認這條程式碼路徑目前仍然活著、沒有被真正清除，才讓這個早先的決定重新浮上來。

## 既有紀錄佐證
這不是這次審查新發現的方向，專案裡已經有多處文件佐證這個決定的存在：
- [Fixed-81.md:127](../fixed/Fixed-81.md)：「這個掃資料夾找 HTML 檔案的匯入方式之後會整個被棄用，現在只需要把這個小 bug 堵住」。
- [WONTFIX-18.md:4](../wontfix/WONTFIX-18.md)：「這支解析器本身……屬於即將被Chrome extension取代的背景job，之後會整批棄用」。
- [Plan-18](Plan-18-chrome-extension-data-extractor.md)：說明了為什麼——家族成員（配偶、長輩）不可能被要求手動另存HTML、再上傳到伺服器/容器，這種操作模式長期不可能維持，Chrome Extension才是落實多使用者與家族財務自動化的核心關鍵設施。
- [issue-140.md:320](../pending%20fix/issue-140.md)：審查IDOR問題時已經把這個系統的匯入機制明確拆成兩層分別討論，見下方「範圍界定」。

## 範圍界定：只移除「定時自動排程」這一層，不動「手動觸發掃描」
這套匯入機制實際上有兩層，這份Plan只處理其中一層，另一層明確不動：

1. **要移除的部分：定時自動排程（`JobSchedulerServiceImpl`透過Spring`TaskScheduler`，依`cron-expression`設定自動定時觸發掃描）**——這是issue-184討論的那條路徑，也是這份Plan的範圍。
2. **不動的部分：手動觸發掃描（HTTP進來後丟給背景執行緒執行一次，`ImportTaskController`的`POST /import/tasks`、`TransactionSourceTaskController`的`POST /tasks/{transactionSource}/process`）**——[issue-140.md:320](../pending%20fix/issue-140.md)已經明確記錄這條路徑「目前會繼續保留使用（唯一使用者兼測試身分需要用它確認匯入流程正確，之後家族Link功能上線後也需要用它驗證資料隔離是否正常）」，使用者這次也確認只處理定時自動排程這一層，手動觸發的部分維持現狀不動。

## 現狀：定時自動排程目前仍然是活著的程式碼，不是已經清除的死碼
查證確認以下這些檔案目前都還存在、而且前端有完整的UI串接，不是只剩下後端沒人呼叫的孤兒程式碼：

- [JobSchedulerService.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/job/JobSchedulerService.java)、[JobSchedulerServiceImpl.java](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/job/JobSchedulerServiceImpl.java)（排程服務本體，用Spring原生`TaskScheduler`依每個`TransactionSource`各自的`cron-expression`設定`scheduleAtFixedRate`）
- [SchedulerController.java](file://finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/job/SchedulerController.java)（`POST /tasks/scheduler/start`、`POST /tasks/scheduler/stop`、`GET /tasks/scheduler/status`三個端點，直接呼叫`JobSchedulerService`的對應方法）
- `hub/src/main/resources/application.properties`裡每個`finance.hub.transaction-source-configs.{source}.auto-start-import-scheduler`（目前全部是`false`）跟`.cron-expression`（例如`0 0 9,15,21 * * *`）這組設定
- 前端：[use-finance-tools.ts](file://hub-ui/hub-ui/src/app/finance/tools/sync/hooks/use-finance-tools.ts)——`schedulerRunning`狀態、呼叫`/api/finance/tasks/scheduler/status`/`start`/`stop`這三個端點的完整UI串接，代表畫面上確實存在一個「啟動/停止自動排程」的開關給使用者操作

## 為何值得獨立開一個Plan去正式移除，而不是放著不管
`auto-start-import-scheduler`目前所有來源都設成`false`，排程預設不會自動啟動——這代表短期內不太可能被意外觸發。但只要這段程式碼跟對應的UI開關還留著，就一定會有以下風險：
1. 使用者（或任何協助維護這個專案的人）如果不小心在畫面上按到「啟動排程」這個開關，或是手動呼叫`POST /tasks/scheduler/start`，就會讓issue-184描述的`ownerUserId`為null存檔失敗問題被真正觸發。
2. 之後任何人（包含協助review這個專案的AI）如果沒有查過`Fixed-81`/`WONTFIX-18`/`Plan-18`這些既有紀錄，看到這段程式碼跟issue-184這類bug，很可能會像這次一樣誤以為這是一個「活著、需要被修好」的功能，重新投入時間去設計修正方案——這次已經發生過一次。與其每次都靠審查者自己翻舊文件才避開這個誤判，不如直接把這個早已拍板、但沒有真正落地的決定排入Plan，從源頭清除這個會不斷被誤判成待修bug的程式碼。

## 原issue-184記錄的具體觸發案例：排程Job沒有HTTP context，ownerUserId會是null導致存檔失敗
這是這次審查發現、原本記錄在issue-184的bug，因為這份Plan而確認不需要修正，記錄下來作為「這個功能該整個移除、不是該修好」的具體佐證：

- [PensionJobServiceImpl.java:93-125](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionJobServiceImpl.java)、[SalaryJobServiceImpl.java:100-180](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/salary/SalaryJobServiceImpl.java)的`execute()`，都是被`JobSchedulerServiceImpl`排程觸發，整段過程完全沒有建立或複製任何`SecurityContext`——跟手動觸發的`TransactionSourceTaskController`（用`DelegatingSecurityContextRunnable`把呼叫者當下的`SecurityContext`複製進新執行緒）完全不同。
- `PensionRecord`跟`SalaryRecord`都繼承[MultiUserBaseEntity.java:79-81](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/base/MultiUserBaseEntity.java)，`ownerUserId`欄位是`@CreatedBy`（由JPA Auditing在`save()`時自動填入）、資料庫層`nullable = false`。[SpringSecurityAuditorAware.java:42-58](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/config/SpringSecurityAuditorAware.java)在沒有`Authentication`時回傳`Optional.empty()`——排程觸發時沒有任何HTTP request、沒有JWT，`@CreatedBy`完全沒有值可以填，存檔時直接撞上資料庫NOT NULL約束，整個排程Job的這一筆存檔會丟出例外，被`catch`接住、檔案移到`archive/failed`，使用者完全不會被通知。
- 這代表只要有人不小心按到前端「啟動排程」這個開關、或手動呼叫`POST /tasks/scheduler/start`，年金/薪資的自動匯入就會每次都無聲失敗——但既然這整套排程機制本來就該被移除，正確的處理方式不是修好這個HTTP context缺口（那樣等於在一個即將被拆掉的地基上補強），是把整套機制連同這個bug一起清除。

## 建議移除範圍
這裡列出需要清除的程式碼跟設定，不代為決定移除的優先順序跟時程：

- 後端：`JobSchedulerService`/`JobSchedulerServiceImpl`、`SchedulerController`整支移除；`application.properties`裡每個來源的`auto-start-import-scheduler`、`cron-expression`設定一併移除。
- 前端：`use-finance-tools.ts`裡`schedulerRunning`狀態跟對應的`/tasks/scheduler/status`/`start`/`stop`呼叫、以及任何呼叫這個hook來顯示「啟動/停止自動排程」開關的UI元件，一併移除。
- 移除後，上面「原issue-184記錄的具體觸發案例」描述的`ownerUserId`為null問題會隨之自然消失，不需要為這個即將被移除的功能另外設計HTTP context注入機制。
