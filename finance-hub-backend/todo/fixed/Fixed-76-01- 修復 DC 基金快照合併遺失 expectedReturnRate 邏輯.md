# Fixed-76-01: 修復 DC 基金快照合併遺失 expectedReturnRate 邏輯

這個計畫旨在解決 DC 與 iDeCo 帳戶快照重新匯入或更新時，系統沒有將先前計算出（或繼承自歷史紀錄）的 `expectedReturnRate` 持久化，導致該值永遠被覆寫為 null 的 Bug。修復後，使用者自訂的基金報酬率將能正確延續，使 Sandbox 及 GA/Grid 模擬更精確。

## User Review Required

無破壞性變更。此修改僅補回原本應被複製的單一欄位，且加上了 Null Safety 的安全檢查，不會影響其他既有邏輯。

## Open Questions

無。目前的解法完美符合「只信任資料庫前一期數值，不腦補預設值」的原則。

## Proposed Changes

### Domain (Accounting / DC)

#### [MODIFY] [DefinedContributionFundSnapshot.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionFundSnapshot.java)
在 `merge(DefinedContributionFundSnapshot other)` 方法中新增以下邏輯：
```java
if (other.getExpectedReturnRate() != null) {
    this.expectedReturnRate = other.getExpectedReturnRate();
}
```
確保既有基金在合併新快照時，若有取得上個月的預期報酬率，能正確寫入並保留。

#### [MODIFY] [DefinedContributionAccountSnapshot.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionAccountSnapshot.java)
在 `merge(DefinedContributionAccountSnapshot other)` 方法裡，處理新增基金的分支（大約第 150 行起），於 `newFund` 建立完成後加入：
```java
newFund.updateExpectedReturnRate(otherFund.getExpectedReturnRate());
```
確保當同月份再次匯入新基金時，已透過 `applyExpectedReturnRates` 找回的歷史報酬率能被正確設定到新實體中。

## Verification Plan

### Automated Tests
- 將由使用者**手動執行** `mvn clean test` 進行驗證，以確保沒有破壞既有的 Snapshot 相關單元測試。

### Manual Verification
- 待修改完成後，您可以進行 Review，確認這兩段修改完全符合我們討論的邏輯。
