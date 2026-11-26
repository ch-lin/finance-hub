# Fixed-56：`MortgageCalculator` 初始化階段 (`fastForwardMortgagesToPresent`) 缺少防呆機制漏洞

## 相關檔案與 Package 對照表 (Files Involved)

| Class / 檔案 | 專案 | 檔案路徑 |
|---|---|---|
| `MortgageCalculator.java` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.calculator` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java` |

## 現況說明與問題原因 (Current State & Cause)

在進行 `MortgageCalculator.java` 的全面審查時，我們發現除了未來的模擬迴圈（`simulateMortgagesForYear`）之外，計算「過去到現在的歷史紀錄」的方法 `fastForwardMortgagesToPresent` 也存在嚴重的安全漏洞。

在 `fastForwardMortgagesToPresent` 方法中，系統會依據歷史資料進行推演，將房貸餘額扣減到現在。當遇到有歷史提前還款紀錄時，其扣款邏輯如下：

```java
balance -= earlyPrincipal; 
```

**問題點：**
這裡**完全沒有實作** `if (earlyPrincipal > balance) earlyPrincipal = balance;` 這層防呆鎖！

### 災難情境模擬 (Scenario)
1. **使用者操作：** 假設使用者的房貸目前的剩餘餘額是 200 萬元。但他在 Sandbox 前端的「歷史提前還款」紀錄中，不小心手滑輸入了一筆「300 萬元」的錯誤紀錄。
2. **初始化執行：** 系統在執行 `MortgageCalculator` 的 `fastForwardMortgagesToPresent` (將過去的房貸推演到現在) 時，會讀取這筆 300 萬的提前還款。
3. **無防禦扣款：** 
   因為缺少了防呆機制，程式直接執行了 `balance -= 300萬`。
4. **餘額變負數：** `200萬 - 300萬` 導致 `balance` 直接變成 `-100萬`。
5. **邏輯錯亂：** 從這一個月開始，利息計算會變成 `負100萬 * 利率`，原本應該繳給銀行的利息，變成了「銀行每個月倒貼利息給使用者」。這會讓整張模擬圖表徹底扭曲，產出完全不合邏輯的財務預測，且同樣不會拋出任何 Exception 來警告使用者。

## 建議方向 (Suggested Fix)

**全面加鎖**：在 `fastForwardMortgagesToPresent` 歷史推演方法中，必須補上與未來模擬相同的安全鎖：
```java
if (earlyPrincipal > balance) {
    earlyPrincipal = balance;
}
```
並確保當 `balance` 歸零時，能夠正確跳出迴圈 (break) 或是正確處理後續的邏輯，避免無效的數學運算繼續進行。
