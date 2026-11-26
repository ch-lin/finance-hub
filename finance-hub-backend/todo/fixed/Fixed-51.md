# Fixed-51：DC/iDeCo 年資覆寫開關與覆寫數值分離存檔，可能產生「開關開但數值是 null」的不一致狀態

## 審查結論（本案原建議修正方向已撤回，請見下方說明）

本案原始判斷是「後端在此狀態下 fallback 到 0，蓋掉了『資格取得日』推算出來的年資，是商業邏輯缺陷」，並建議後端在覆寫數值為 null 時改為退回去使用資格取得日回推。**經重新檢視，此結論撤回，不建議修改任何後端商業邏輯**：

1. **系統目前完全在本地測試階段，尚無正式使用者資料**。「資料庫已存在髒資料需要保護」不是這裡需要處理的理由。
2. **更關鍵的問題是：讓後端在覆寫數值缺失時自動退回去用資格取得日回推，本質上跟現況「退回去用 0」一樣，都是系統在使用者沒有明確給值的狀態下，自行代入一個使用者從未確認過的數字——只是換了一個看起來比較合理的猜測方式，並沒有解決「系統在背後偷偷幫使用者做主」這個根本問題。**
   使用者打開覆寫開關，代表他明確表達「這個數字我要自己控制、不要用你自動算的」。如果他沒填就存檔，系統卻悄悄換用另一個他沒填過、也不會被告知已經套用了的數字（而且這個數字的來源——OCR 掃描使用者上傳截圖推算出的「資格取得日」——本身也可能不準確），使用者完全沒有機會察覺這件事發生過。
   這比維持現況的 0 更不透明：0 是系統在 UI 上已經明確告知使用者的「沒有資料時的保守預設值」（見 `profile-contributions-section.tsx` 第 37-40 行說明文字：「If you haven't uploaded these screenshots, the simulation conservatively defaults to 0 years」），是可預期、有明確告知的行為；換成資格取得日回推，則是系統自己片面決定要不要理會使用者剛做的操作，使用者無從得知、也無從信任。
   若使用者依據這個他從未主動確認過的數字做出真實的財務決策，事後發現數字跟他實際狀況不符，系統很難自稱這是「合理的智慧推算」——使用者填寫正確、完整的輸入是使用者自己的責任，不是系統該幫忙猜測或代為補值的責任。
3. 因此，這個案例真正該解決的是**輸入驗證缺失**：前端應該在覆寫開關打開時把數值欄位設為必填，後端應該拒絕「開關為 true 但數值為 null」這個組合的請求（400 Bad Request）。依照本次 review 既定範圍原則，endpoint 的資料驗證不視為商業邏輯問題，不在此輸出修正建議。
4. 現有 `DefinedContributionContextBuilder` 在「開關開但數值為 null」時 fallback 到 0 的行為，**維持不動即可**，不建議改成任何形式的「猜測式」替代計算（包含原建議的資格取得日回推）。

## 相關檔案與 Package 對照表 (Files Involved)

| Class / 檔案 | Package | 檔案路徑 | 是否需要修改 |
|---|---|---|---|
| `DefinedContributionContextBuilder` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.factory` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java` | 否，現有 fallback 至 0 的行為是正確、不應更動的（見審查結論） |
| `DefinedContributionUserSettingsServiceImpl` | `ch.lin.finance.hub.backend.api.app.service.accounting.dc` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsServiceImpl.java` | 否，僅供對照「覆寫旗標跟覆寫數值是分開存的」 |
| `DefinedContributionUserSettings`（entity） | `ch.lin.finance.hub.backend.api.domain.model.accounting.dc` | `hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/dc/DefinedContributionUserSettings.java` | 否，僅供對照欄位定義 |
| `profile-contributions-section.tsx`（前端） | `hub-ui` | `hub-ui/hub-ui/src/app/finance/components/profile-contributions-section.tsx` | 否，屬於輸入驗證範疇，不在本次商業邏輯 review 對象內 |
| `profile/me/page.tsx`（前端） | `hub-ui` | `hub-ui/hub-ui/src/app/finance/profile/me/page.tsx` | 否，同上 |

## 現況說明 (Current State)

`DefinedContributionUserSettings` 有兩組獨立欄位（DC 跟 iDeCo 各一組）：

```java
// hub-domain/.../DefinedContributionUserSettings.java
private Boolean isDcContributionYearsOverridden = false;
private Integer dcContributionYearsOverride;
private Boolean isIdecoContributionYearsOverridden = false;
private Integer idecoContributionYearsOverride;
```

`DefinedContributionUserSettingsServiceImpl.saveSettings` 存檔時，這兩個欄位是**各自獨立**判斷是否要寫入的：

```java
// hub-app/.../DefinedContributionUserSettingsServiceImpl.java 第 69-80 行
if (command.isDcContributionYearsOverridden() != null) {
    settings.setIsDcContributionYearsOverridden(command.isDcContributionYearsOverridden());
}
if (command.dcContributionYearsOverride() != null) {
    settings.setDcContributionYearsOverride(command.dcContributionYearsOverride());
}
```

`DefinedContributionContextBuilder.initializeDefinedContributionContext` 在模擬開始時讀取這兩個欄位，決定 `dcContributionYears`（iDeCo 端邏輯完全對稱）：

```java
// hub-app/.../DefinedContributionContextBuilder.java 第 53-60 行
if (dcSettingsOpt.isPresent()
        && Boolean.TRUE.equals(dcSettingsOpt.get().getIsDcContributionYearsOverridden())) {
    Integer overrideVal = dcSettingsOpt.get().getDcContributionYearsOverride();
    context.setDcContributionYears(overrideVal != null ? overrideVal : 0);
} else if (accountSnapshot.getEnrollmentDate() != null) {
    context.setDcContributionYears(
            Period.between(accountSnapshot.getEnrollmentDate(), LocalDate.now()).getYears());
}
```

`isDcContributionYearsOverridden = true` 且 `dcContributionYearsOverride = null` 這個組合，在資料庫裡是完全合法、可達成的狀態——因為存檔邏輯把這兩個欄位當成兩個互不相關的欄位處理，沒有任何一方強制對方必須同時有值。一般使用者操作（在 `profile-contributions-section.tsx` 打開覆寫開關、還沒填數字就在 `profile/me/page.tsx` 按下「Save Profile」）就會產生這個狀態，不需要繞過前端或直接打 API。

## 結論 (Conclusion)

這個狀態雖然真實可達成，但不是商業邏輯缺陷：

1. 現有 `overrideVal != null ? overrideVal : 0` 的 fallback 選擇 0，跟系統在其他情境（無 `accountSnapshot` 時）記錄年資的保守預設值語意一致，且已在 UI 明確告知使用者這是「沒有資料時」的行為。不建議改成任何其他推算方式去取代它——那只是換一種方式讓系統自作主張。
2. 真正該解決的是「這個不一致狀態不該被存進資料庫」，這屬於輸入驗證範疇（前端必填 + 後端 400），依 review 範圍排除規則不在此輸出修正建議。

DC、iDeCo 兩邊邏輯完全對稱，結論相同。

---

## 修正報告 (Resolution Report)
**修復狀態：已完成**

**實作細節：**
針對「開關為 true 但數值為 null」的不一致狀態，實作了三層防禦架構（Triple-Layer Defense）：
1. **Frontend (UI 層防禦)**：修改 `page.tsx`，在送出表單前檢查 `isDcContributionYearsOverridden` 與 `isIdecoContributionYearsOverridden`。若開關為 true 且未提供數值，立刻以 `toast.error` 阻斷，避免送出無效 API 請求。
2. **Backend Service (API 層防禦)**：修改 `DefinedContributionUserSettingsServiceImpl.java`，在合併新舊資料後、`repository.save()` 之前，檢查若開關為 true 且數值為 null，則拋出 `IllegalArgumentException`（轉為 400 Bad Request）。此寫法同時支援在關閉開關時保留 DB 舊有數值。
3. **Backend Entity (DB 層防禦)**：修改 `DefinedContributionUserSettings.java`，於類別加上 Hibernate `@Check` 註解，於資料庫端產生 `CHECK` 限制條件，做為終極保險。

**結果**：徹底杜絕髒資料寫入。不論是透過 UI 正常操作、或直接呼叫 API，系統皆能嚴格保證 DC 與 iDeCo 年資覆寫狀態的資料一致性。
