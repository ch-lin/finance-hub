# WONTFIX 32：Amazon/Rakuten訂單重新匯入時shippingDate不被覆寫——不是bug，是現有收據快照本身就沒有這項資訊

## 判定：維持現狀，不需修正（2026-10-13，使用者確認，範圍含Amazon與Rakuten）

## 背景
Source Code Review（2026-10-13）發現[AmazonOrder.java:192](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/amazon/AmazonOrder.java)、[RakutenOrder.java:177](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/rakuten/RakutenOrder.java)兩者的`updateFrom`方法裡，`this.setShippingDate(parsedOrder.getShippingDate());`這一行都被整行註解掉，重新匯入同一張訂單時不會更新`shippingDate`。對照同一份發現裡[SofmapOrder.java:176](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/shopping/sofmap/SofmapOrder.java)同一行是正常生效的，一開始無法只靠讀程式碼判斷這個差異是刻意設計還是遺漏，原本記錄在question-177裡（已結案，question-177.md已刪除）。

## 為何判定WONTFIX
使用者確認：目前存下來的Amazon跟Rakuten收據快照裡，都沒有宅配單號、也沒有可靠的出貨日期資訊可以抓。[AmazonOrderParserImpl.java:111-121](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/shopping/amazon/AmazonOrderParserImpl.java)的`parseShipments`方法，程式碼註解本身也寫著「Optional, might be empty on modern Next.js pages」——這點已經由使用者的真實使用經驗確認為真，不是理論上的邊界case；Rakuten這一側原本以為從頁面embedded JSON解析、資料穩定性可能比Amazon的HTML區塊文字擷取方式更可靠，但使用者查證後確認同樣沒有這項資訊可用，這個推測並不成立。

這代表`updateFrom`不覆寫`shippingDate`這件事，不是「要不要覆寫」的設計選擇問題，而是資料源頭本身就不存在這個欄位的值，再怎麼調整覆寫邏輯都沒有意義——沒有資料可以覆寫，覆不覆寫的差異從頭到尾都不會顯現出來。

要讓Amazon/Rakuten訂單真正擁有可靠的出貨日期，唯一的辦法是回溯重新存檔上萬筆歷史訂單的網頁，這對一個只是要確認現金夠不夠、能不能貸款、生活過不過得去的FP現金流模擬工具而言，成本跟效益完全不成比例。而且對帳功能（[ReconciliationServiceImpl.java:159-160](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/reconciliation/ReconciliationServiceImpl.java)的`sortMatchesByProximity`）本身已經有「找不到`shippingDate`就退回用`orderDate`」這個運作正常的既有fallback，不是一個壞掉、沒有退路的功能。
