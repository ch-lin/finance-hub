# WONTFIX 23：房貸重新匯入時，6個欄位沒有「解析失敗就保留舊值」的保護——判定不是bug

## 判定：維持現狀，不需修正（2026-09-30補記；原始決策於約兩週前的第三輪review已經做出並結案，當時未留下對應文件，這裡補記回來）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageJobServiceImpl.java:126-148`（重新匯入時合併新舊資料的地方）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java:166-200`（`updateMortgage`）

## 原始提案內容
第五輪review提案認為：房貸重新匯入時，`currentBalance`、`startDate`、`endDate`、`loanTermMonths`、`rateType`、`monthlyPayment`這6個欄位，如果這次解析失敗回傳null，會直接把null寫進去，導致資料庫必填限制擋下整筆更新、回滾失敗，房貸紀錄停留在舊資料。提案建議比照另外8個欄位已經在用的「解析出來的值若為null，就保留舊值」寫法，補上同樣的保護。

## 為何判定不是問題
這6個欄位（貸款餘額、起訖日期、利率類型、下次應繳金額）全部都是銀行/房貸對帳單本身有能力回報的事實類欄位，跟另外8個欄位（`occupancyDate`、`residenceType`、`propertyType`、`taxDeductionEligible`等，這些是對帳單structurally不可能回報的純使用者設定）性質完全不同，不該套用同一套保護邏輯。

判斷原則：銀行/對帳單有的資訊，就不該信資料庫存的舊值——資料庫存的不是事實，銀行才是事實。如果對這6個事實類欄位加上「解析失敗就退回舊值」的保護，會製造一個更危險的情況：假設某次匯入因為某個原因解析失敗（例如網頁版面調整導致某個標籤抓不到），系統卻因為有保護機制，硬是繼續沿用舊的餘額/日期/利率去跑模擬，讓使用者以為自己看到的是最新資料，實際上卻是一筆早已過期、跟銀行實際狀況不一致的舊事實——這比「整筆更新失敗、停留在上一次成功匯入的舊資料」更危險，因為後者至少「舊資料」本身在當時是真實的，前者卻可能是一次已經失敗、卻被悄悄蓋掉失敗訊號的匯入。

現在的「整筆失敗回滾」行為，雖然使用者不會立刻被告知「這次匯入其實沒生效」，但至少不會產生「看似更新成功、實際上是錯誤拼裝出來的資料」這種更難察覺的問題。

## 結論
維持現狀，不需修正。這6個事實類欄位不套用「解析失敗保留舊值」的保護，是刻意的判斷，不是遺漏。
