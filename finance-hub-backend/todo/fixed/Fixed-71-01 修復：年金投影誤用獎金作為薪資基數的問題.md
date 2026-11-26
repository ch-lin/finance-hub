# 修復 Fixed 71：年金投影誤用獎金作為薪資基數的問題

修復 `PensionProjectionServiceImpl` 在抓取最新一筆 `SalaryRecord` 時沒有檢查 `SalaryType` 的嚴重錯誤。如果最新一筆紀錄剛好是 `BONUS`（獎金），它龐大的金額會被誤當作 `expectedSalary` 餵給厚生年金投影公式，導致推算出的未來的年金金額被嚴重高估。

我們將透過直接在資料庫層級撈取最新的 `MONTHLY_SALARY`（月薪）紀錄來解決這個問題，確保投影引擎拿到的是常態基數。

## 需使用者審查的項目

> [!NOTE]
> 這個改動**只會**修改 `PensionProjectionService`（純粹給年金圖表用的服務）。
> 這個改動**不會**去動到原本的 `findAllByOrderByPaymentDateDesc`，因此沙盒（Sandbox）與 GA 系統底層使用的 `SalaryContextBuilder` 完全不受影響，它們複雜的年度扣除額、稅金與獎金加總邏輯將會繼續保持完美運作。

## 待釐清問題

目前無待釐清問題。

## 預計修改項目

---

### 後端資料存取層 (Data Access Layer)

#### [MODIFY] [SalaryRecordRepository.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/repository/accounting/salary/SalaryRecordRepository.java)
- 新增一個查詢方法：`Optional<SalaryRecord> findFirstByTypeOrderByPaymentDateDesc(ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryType type);` 以高效率直接撈取特定類型（最新）的薪資紀錄。

---

### 後端商業邏輯層 (Business Logic)

#### [MODIFY] [PensionProjectionServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceImpl.java)
- 將原本呼叫的 `salaryRecordRepository.findAllByOrderByPaymentDateDesc()` 替換為新建立的 `salaryRecordRepository.findFirstByTypeOrderByPaymentDateDesc(SalaryType.MONTHLY_SALARY)`。
- 更新提取邏輯：從回傳的 `Optional<SalaryRecord>` 安全地取出 `grossAmount`。如果沒有找到紀錄，則如同原本的邏輯退回使用 Config 預設值。

---

### 後端測試 (Tests)

#### [MODIFY] [PensionProjectionServiceTest.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/pension/PensionProjectionServiceTest.java)
- 將測試中所有的 `findAllByOrderByPaymentDateDesc` Mock 替換為 `findFirstByTypeOrderByPaymentDateDesc(any())`，以對齊最新的 Repository 呼叫方式。

## 驗證計畫

### 人工驗證
- 由於單元測試將由使用者親自執行，我們只需確保程式碼語法正確、無編譯錯誤即可。使用者可親自驗證程式碼上線後，年金投影服務是否成功忽略了 `BONUS` 的紀錄。
