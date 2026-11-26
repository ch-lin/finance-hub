# [FIXED] 179：地震保險的merge()合併邏輯，annualPremium欄位被加了一段「新值為0就不覆寫」的額外防呆判斷，跟同一方法裡endDate欄位乾淨直接覆蓋的寫法不一致——這是修正endDate那次順手多塞的沒有被要求過的邏輯，一旦保費從非零變成「長期一括払（視為0）」，重新匯入永遠無法把金額改回0

## 這份issue怎麼被發現的
這是這次Source Code Review審查保險服務層時發現的，原本因為不確定`merge()`裡這段`> 0`判斷的真實意圖，先寫成question。使用者後來指出：`InsurancePolicy.merge()`整個方法是最近修正保單缺少`endDate`那次才寫出來的，沒有注意到裡面`annualPremium`那一行到底寫了什麼。重新對照同一個方法裡`endDate`跟`annualPremium`這兩行的寫法後，確認這點屬實——升級為issue。

## 相關檔案
- [InsurancePolicy.java:72-82](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/insurance/InsurancePolicy.java)（`merge()`方法）
- [InsuranceJobServiceImpl.java:144-158](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/insurance/InsuranceJobServiceImpl.java)（重新匯入時呼叫`existing.merge(newPolicy)`）
- [SonyAssuranceFireHtmlParser.java:97-104](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/insurance/parser/SonyAssuranceFireHtmlParser.java)（長期一括払時`annualPremium`固定設成`0`）

## 現狀
`InsurancePolicy.merge()`目前只處理兩個欄位，寫法如下：
```java
public void merge(InsurancePolicy other) {
    if (other == null) {
        return;
    }
    if (other.annualPremium != null && (other.annualPremium > 0 || this.annualPremium == null)) {
        this.annualPremium = other.annualPremium;
    }
    if (other.endDate != null) {
        this.endDate = other.endDate;
    }
}
```
`endDate`這一行的寫法很乾淨：只要新值不是null就直接覆蓋，沒有任何額外條件。但`annualPremium`這一行多了`(other.annualPremium > 0 || this.annualPremium == null)`這一段——只有當新匯入的`annualPremium > 0`，或是舊紀錄原本就是null，才會真正覆寫這個欄位；新值如果是`0`、而舊紀錄已經是一個非零值，這次`merge()`完全不會把它改成0，`this.annualPremium`會維持原來的非零值不變。

這整個`merge()`方法是修正保單缺少`endDate`那次才寫出來的——`endDate`那一行本身邏輯正確、沒有加任何額外條件，證明這個方法原本的風格就是「新值有給就直接覆蓋」。`annualPremium`那一行多出來的`> 0`這段判斷，不是舊程式碼遺留、也不是從別處複製過來的通用防呆模式，是同一次改動裡額外加上去、而且沒有被要求過的邏輯。

## 為何是問題
如果一張保單第一次匯入時被解析成「月払」（或任何非一括払的繳費方式，`annualPremium`是非零值），之後使用者重新匯入同一張保單的最新網頁存檔、而這次網頁上繳費方式顯示已經變成「長期一括払」，`annualPremium`永遠無法透過重新匯入被改回`0`——必須透過前端`PUT`端點手動改成0才能生效。`annualPremium`跟`endDate`性質上是同一類「銀行/保險公司網頁上的客觀事實欄位」，照reimport/merge的判斷原則，應該在每次重新匯入時被最新事實直接覆寫，不應該有任何「新值是0就不算數」的特例——這個特例沒有對應任何真實需求，純粹是多餘、而且方向錯誤的額外邏輯。

這跟[WONTFIX-06](../wontfix/WONTFIX-06.md)判定的「`annualPremium=0`對長期一括払保單而言，現金流模擬上是正確答案」完全不衝突（WONTFIX-06討論的是0這個數字對不對，這裡討論的是「無法改成0」這個合併邏輯本身的行為）。

## 具體錯誤情境
使用者一張地震保險保單原本是「月払」，`annualPremium`非零。之後使用者改成長期一括払繳費方式、重新匯入最新的Sony損保網頁存檔，`SonyAssuranceFireHtmlParser`正確解析出繳費方式已經變成長期一括払、`annualPremium`應該是`0`。但`merge()`裡的`> 0`判斷會擋下這次更新，`annualPremium`繼續維持原來那個已經不再正確的非零值——模擬引擎會持續把一筆實際上已經不存在的保費支出算進現金流，直到使用者自己發現、手動用PUT端點把這個值改成0為止。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：拿掉`annualPremium`那一行多餘的`> 0`判斷，改成跟`endDate`一樣乾淨的「新值不是null就直接覆蓋」**
- 做法：改成`if (other.annualPremium != null) { this.annualPremium = other.annualPremium; }`。
- 優點：跟`endDate`那一行風格一致，直接反映「銀行/保險事實欄位應該被每次匯入覆寫」這個原則，改動最小。
- 缺點：如果之後解析失敗、不小心讓`other.annualPremium`被設成0傳進來（不是真的長期一括払，是解析器本身的bug），這個修正會讓一個解析錯誤直接覆蓋掉既有正確的非零值，沒有任何防護——但這種情況本來就該在parser那一層解決（解析失敗該拋例外、不該悄悄回傳0），不該用`merge()`裡的這個特例去補救。

**方向B：維持現狀，不處理**
- 理由：如果使用者名下的保單實際上不會發生「繳費方式從非一括払改成一括払」這種情境，這題目前不會被實際觸發，可以先記錄起來不處理。

## 修正報告書（問題已自然消失）

### 審查結論
本 Issue 描述的 `annualPremium` 額外 `> 0` 判斷在現行程式碼中**已自然消失（已被修正）**。

在進行 [issue-180](issue-180.md)（Job Import Merge 規則）系列梳理與程式碼整頓時，[InsurancePolicy.java](file://finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/insurance/InsurancePolicy.java) 的 `merge(InsurancePolicy other)` 方法已被改寫為乾淨的事實覆蓋寫法：
```java
public void merge(InsurancePolicy other) {
    if (other == null) {
        return;
    }
    this.companyName = other.companyName;
    this.policyType = other.policyType;
    this.policyNumber = other.policyNumber;
    this.policyName = other.policyName;
    this.annualPremium = other.annualPremium; // 已移除原本多餘的 > 0 防呆，直接覆蓋最新值
    this.endDate = other.endDate;
}
```
原本會阻擋「長期一括払（年保費為 0）」更新的條件判斷已不復存在，若有進一步架構與跨領域 merge 規則審查需求，請參閱主議題 [issue-180](issue-180.md)。本題直接予以結案。
