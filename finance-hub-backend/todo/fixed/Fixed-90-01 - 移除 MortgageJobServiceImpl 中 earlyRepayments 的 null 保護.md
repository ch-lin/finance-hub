# 移除 MortgageJobServiceImpl 中 earlyRepayments 的 null 保護 (Fixed 90-01)

根據 Fixed-90 的最新釐清，`earlyRepayments` 欄位因為有真實的銀行網頁（「履歴」頁面）作為資料來源，屬於「銀行事實欄位」。依據系統原則，銀行事實欄位不應該受到 null 保護（不該保護使用者手動輸入的舊值）。

目前的程式碼中，`earlyRepayments` 被加上了 fallback 保護機制，這違反了該原則，因此我們需要將其移除，讓這個欄位被無條件覆寫。

## User Review Required

請確認：拔除保護後，`mergedCmd` 建立時將直接使用 `cmd.earlyRepayments()`。由於目前的 Parser 是直接塞入 `null`，這意味著更新時會將資料庫中現有的 earlyRepayments 給清空（覆寫為 null 或空清單），直到未來實作了能解析「履歴」頁面的新 Parser 為止。這正是您所期望的行為嗎？

## Proposed Changes

### [MortgageJobServiceImpl.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageJobServiceImpl.java)

#### [MODIFY] 
- 修改 `processAndSaveFile` 內的 `mergedCmd` 建立邏輯。
- 將第 149-154 行針對 `cmd.earlyRepayments()` 的 fallback 保護邏輯：
  ```java
  cmd.earlyRepayments() != null && !cmd.earlyRepayments().isEmpty() ? cmd.earlyRepayments()
          : (current.getEarlyRepayments() != null ? current.getEarlyRepayments().stream()
                  .map(er -> new SaveMortgageRecordCommand.MortgageEarlyRepaymentCommand(
                          er.getId(), er.getRepaymentDate(), er.getAmount(),
                          er.getRepaymentType(), er.getFee()))
                  .collect(Collectors.toList()) : new ArrayList<>()));
  ```
  直接替換為沒有保護的：
  ```java
  cmd.earlyRepayments());
  ```

## Verification Plan

### Manual Verification
- 修改後透過 IDE 檢查語法是否正確，編譯是否通過。
- 由於此背景 Job 即將被 Chrome extension 取代，且目前已被歸類在 pending fix / wontfix 系列中，確保邏輯符合不加保護的原則即可。
