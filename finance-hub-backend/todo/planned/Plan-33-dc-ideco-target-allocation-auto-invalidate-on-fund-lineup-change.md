# Plan 33：DC/iDeCo重新匯入時，如果基金組合跟使用者上次設定的目標配分比例對不上，自動清掉那組使用者設定

## 這份計畫怎麼來的
這是[question-198](../pending%20fix/question-198.md)定案後的實作計畫。198查證確認：[issue-189](../pending%20fix/issue-189.md)新增的`DcTargetAllocationSetting`（使用者在DC/iDeCo沙盒情境裡設定的目標配分比例，存在`DefinedContributionUserSettings.dcTargetAllocation`/`idecoTargetAllocation`這兩個JSON欄位）只在使用者呼叫`PUT /assets/dc/user-settings/{type}/target-allocations`那一刻驗證過一次「這組比例涵蓋的`fundCode`跟當下最新snapshot的基金清單完全吻合」，之後不管使用者在券商網站上怎麼換投資標的、重新匯入多少次，都沒有任何機制再去檢查這組設定還對不對。

這不是bug，是系統結構性限制：系統沒有辦法從舊的基金組合（例如ABC三支基金各自的比例）猜出新組合（例如換成ABCD四支）裡新基金該分配多少比例，不存在一個可以「自動算出」的正確答案。唯一能做的回應方式，是偵測到基金組合變了，就讓這組已經對不上新事實的舊設定失效——逼使用者（或未來[Plan-29](Plan-29-dc-ideco-sandbox-contribution-override-by-real-allocation-percentage.md)的沙盒讀取邏輯fallback回事實欄位）重新面對這個組合已經變了的事實，而不是讓一份誤導性的舊資料繼續留在資料庫裡、看起來跟正常設定一模一樣。

## 具體情境
使用者10/30匯入DC快照，當時帳戶裡有ABC三支基金，呼叫`PUT /assets/dc/user-settings/{type}/target-allocations`設定好ABC三支基金的目標比例（例如A 40%、B 30%、C 30%），驗證通過、寫入`DcTargetAllocationSetting{remainderFundCode: "A", allocations: {"A": 40.0, "B": 30.0, "C": 30.0}}`。10/31使用者在券商網站上把配置換成ABCD四支基金（新增了D），重新匯入後，`DcTargetAllocationSetting`裡仍然只有ABC三支、加總還是100%，完全沒有涵蓋到新增的D，而且目前沒有任何程式碼會在這次匯入完成後重新檢查這件事——這組設定就這樣悄悄變成陳舊的。

## 建議方向：兩條既有匯入路徑完成後，各自比對這次匯入後的`fundCode`集合跟使用者設定的`allocations`集合是否一致，不一致就清掉

### 1. 查證確認的兩條既有匯入路徑、跟它們各自的插入點

**瀏覽器擴充功能路徑**——[DefinedContributionServiceImpl.saveSnapshot](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java:64-105)：這條路徑先用`dcRepository.deleteByRecordDateAndType`把同一天、同一帳戶類型的既有snapshot整批刪除，再整批重新建立`snapshot`這個新物件（含它的`funds`清單），最後`dcRepository.save(snapshot)`。插入點在這次save完成之後，這時`snapshot.getType()`、`snapshot.getFunds()`都是這次匯入後的最終結果，可以直接拿來比對。

**檔案掃描路徑**——[DefinedContributionJobServiceImpl](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionJobServiceImpl.java)有兩個方法都會走到同一段儲存邏輯：`execute`（第112-123行，批次掃描，走`transactionTemplate.execute`）、`processFile`（第163-174行，單檔案上傳，走一樣的`transactionTemplate.execute`）。兩處都是：如果同一天同一帳戶類型已經有`existing`，就呼叫`mergeSnapshotFacts`（第188-196行）合併進既有的`existing`物件；如果沒有，直接`dcRepository.save(snapshot)`新建。**只有`pageType == ALLOCATION`這一種分頁（走`mergeAllocationFacts`）才會真正新增或沿用`fundCode`，`MEMBER_INFO`、`STATUS`這兩種分頁不會改變基金組合**——這代表這份計畫新增的比對邏輯，只需要在`pageType == ALLOCATION`時觸發，`MEMBER_INFO`/`STATUS`分頁匯入完全不用檢查（基金組合不可能因為這兩種分頁而改變）。插入點在`transactionTemplate.execute`這個事務內、`dcRepository.save(...)`執行完成之後——這兩個方法各自都要加。

### 2. 新增一個共用的比對方法，兩條路徑都呼叫它

在[DefinedContributionUserSettingsServiceImpl](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionUserSettingsServiceImpl.java)新增一個方法，例如：

```java
/**
 * Invalidates this account type's target allocation setting if its fundCode
 * coverage no longer matches the latest imported fund lineup. Call this after
 * every import that can change a DC/iDeCo account's fund lineup (ALLOCATION
 * page facet, or the browser-extension full-snapshot replace).
 */
@Transactional
public void invalidateTargetAllocationIfFundLineupChanged(DefinedContributionType type, Set<String> currentFundCodes) {
    repository.findFirstBy().ifPresent(settings -> {
        DcTargetAllocationSetting existing = settings.getTargetAllocation(type);
        if (existing == null) {
            return; // 使用者根本沒設定過，不用檢查
        }
        if (!existing.getAllocations().keySet().equals(currentFundCodes)) {
            settings.updateTargetAllocation(type, null);
            repository.save(settings);
        }
    });
}
```

用`keySet().equals(...)`做完全集合相等比對（不是子集合或交集）——新增基金、刪除基金、兩者同時發生，任何一種差異都代表舊設定已經不能正確覆蓋目前的帳戶，一律清掉，不嘗試做部分保留（部分保留需要猜測新基金該怎麼分配剩下的比例，這正是上面「這份計畫怎麼來的」一節說明的、系統結構性做不到的事）。

### 3. 兩條匯入路徑各自呼叫這個方法

**`DefinedContributionServiceImpl.saveSnapshot`**（第64-105行），在`dcRepository.save(snapshot)`之後新增：
```java
Set<String> currentFundCodes = snapshot.getFunds().stream()
        .map(DefinedContributionFundSnapshot::getFundCode)
        .collect(Collectors.toSet());
userSettingsService.invalidateTargetAllocationIfFundLineupChanged(command.getType(), currentFundCodes);
```
需要把`DefinedContributionUserSettingsService`注入到`DefinedContributionServiceImpl`的建構子裡（查證確認目前[DefinedContributionServiceImpl](file://finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/dc/DefinedContributionServiceImpl.java:40-53)的建構子只注入了`dcRepository`、`dcFundRepository`這兩個repository，沒有任何service依賴，這是一個新增的跨service依賴，但方向上（DC snapshot service依賴DC user settings service）跟現有`updateAccountAllocations`已經在同一個service內部操作使用者看得到的配分比例資料是一致的，不是突兀的新耦合）。

**`DefinedContributionJobServiceImpl.execute`**（第112-123行）、**`processFile`**（第163-174行），在各自的`transactionTemplate.execute`區塊內、`dcRepository.save(...)`之後新增同樣的邏輯，但只在`parsed.pageType() == DefinedContributionPageType.ALLOCATION`時才執行（因為只有這個分頁才可能改變基金組合）：
```java
if (parsed.pageType() == DefinedContributionPageType.ALLOCATION) {
    DefinedContributionAccountSnapshot saved = /* 上面save的回傳值 */;
    Set<String> currentFundCodes = saved.getFunds().stream()
            .map(DefinedContributionFundSnapshot::getFundCode)
            .collect(Collectors.toSet());
    userSettingsService.invalidateTargetAllocationIfFundLineupChanged(saved.getType(), currentFundCodes);
}
```
這兩個方法目前已經有`existing`（合併情境）或`snapshot`（新建情境）兩種可能的最終物件，需要在`transactionTemplate.execute`的lambda裡統一拿到實際save後的那個物件（兩個分支現在都直接`return dcRepository.save(...)`，可以把這個回傳值接住再用）。

### 4. 為什麼插入點選在「匯入完成之後」而不是「匯入進行中」
這份計畫故意不去改動`mergeAllocationFacts`、`saveSnapshot`內部建構`snapshot`的邏輯本身——匯入要怎麼合併事實欄位，是189、197已經定案、跟這份計畫完全無關的既有邏輯，不應該被這份計畫牽動。這份計畫的比對邏輯只在匯入**完全結束、資料已經落地**之後，用最終結果的`fundCode`集合去跟使用者設定比對，是一個獨立的、事後才執行的檢查步驟，不會影響匯入本身的正確性或效能（只多一次`findFirstBy()`跟可能的一次`save`，不是每支基金逐一查詢）。

## 這份計畫跟189、197、Plan-29的分工
- 189負責「這張使用者設定表本身要怎麼存、唯一鍵怎麼設計」——已經落地，這份計畫不重新討論。
- 197負責「基金列表上的事實欄位PATCH端點」——跟這份計畫完全獨立，這份計畫不會動到`DefinedContributionFundSnapshot.allocationPercentage`這個事實欄位本身。
- Plan-29負責「模擬沙盒要怎麼讀取這組使用者設定、沒有設定時怎麼fallback」——這份計畫讓Plan-29不需要在讀取時額外處理「這組設定可能跟目前基金組合不吻合」這種情況（因為讀到的時候，只要這組設定存在，就已經保證跟當前基金組合完全吻合），Plan-29的讀取邏輯可以簡化成「存在就用、不存在（包含被這份計畫清掉之後）就fallback事實欄位」，不需要在讀取端自己重新驗證一次`fundCode`集合。

## 這份計畫沒有解決的部分
- 使用者設定被自動清掉之後，要不要在前端給出任何提示（例如「您的目標配分比例設定因投資標的組合變動已被重置，請重新設定」這類通知），這份計畫只處理後端的自動清除邏輯，前端提示機制留給實作時決定要不要一併做。
- 要不要在清除之前先記錄一筆log或審計紀錄，方便之後追蹤使用者設定何時、為什麼被清掉，留給實作時決定。
- 這份計畫要不要現在就投入實作，還是等Plan-29真正要落地時再一併處理（因為在Plan-29的消費端程式碼完成之前，`DcTargetAllocationSetting`陳舊與否完全不影響任何實際模擬結果，這份計畫本身不是時間敏感的修正），留給使用者決定。
