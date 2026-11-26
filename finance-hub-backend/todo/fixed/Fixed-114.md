# Fixed 114：系統設定跟交易來源設定頁面的「新增設定檔」按鈕，點擊後會立刻把剛帶出來的預設值清空

## 嚴重程度
Medium（不會弄壞現有資料——「預設」設定檔本身還是正常運作——但會讓使用者完全無法透過畫面新增新的設定檔，這是100%必然發生、每次都一樣，不是偶發狀況）

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts:307-326`（`handleSysNewProfileClick`，系統設定的「新增」按鈕邏輯）
- 同檔案第201-206行（監看`sysSelectedProfile`的`useEffect`，第204行`else`分支）
- 同檔案第157-174行（`fetchSystemConfigDetails`，第158行已經有`if (isSysCreatingNewProfile || !profile) return;`這個保護，但只保護了「要不要打API去抓資料」這件事，沒有保護到旁邊那個`useEffect`的`else`分支）
- 交易來源設定（Transaction Source Config）有完全對稱的一組程式碼（`handleNewProfileClick`第219-232行、對應的`useEffect`第193-199行），同樣的問題

## 現狀
點擊「新增設定檔」時，`handleSysNewProfileClick`會在同一次事件處理裡，一次做四件事：記住目前選到的設定檔、把「正在新增」的旗標打開、把目前選到的設定檔清空成空字串、把畫面上的表單內容設成一組全新的預設值。因為第310行把`sysSelectedProfile`設成空字串，觸發了第201-206行那個監看`sysSelectedProfile`的`useEffect`重新執行；因為`sysSelectedProfile`現在是空的（假值），這個`useEffect`會走進`else`分支，直接呼叫`setSystemConfigDetails(null)`——這一步完全沒有檢查「現在是不是正在新增設定檔」這個旗標，把剛剛才設好的那組預設值整個清空成`null`。

## 為何是問題
`fetchSystemConfigDetails`這個方法本身，其實已經有正確的保護（第158行：如果正在新增，直接不執行）；但旁邊這個`useEffect`的`else`分支，是另一段完全獨立的程式碼，沒有做同樣的保護，變成整個防護裡漏了一個洞。

## 具體錯誤情境
使用者到「系統設定」（或「交易來源設定」）畫面，點擊「新增設定檔」。畫面預期應該顯示一份填好合理預設值（例如退休年齡65、通膨率0等）的新設定表單，讓使用者接著去改想要調整的欄位、輸入名稱、儲存。但實際上，畫面會立刻（下一個畫面更新週期，每次都會發生，不是偶發）變成「No profile details available.」這種提示，整個表單內容消失，使用者完全沒有辦法透過這個畫面新增任何新的系統設定檔或交易來源設定檔。

## 建議修正
在第204行這個`useEffect`的`else`分支，補上同樣的`isSysCreatingNewProfile`檢查（例如改成`else if (!isSysCreatingNewProfile) { setSystemConfigDetails(null); }`），跟`fetchSystemConfigDetails`裡已經在用的保護邏輯對稱一致，讓「正在新增」這個狀態，能夠真正保護到表單內容不被這個`useEffect`清空。交易來源設定那邊也需要套用同樣的修正。

---

## 實作清單（Checklist）

1. **`use-finance-configs.ts` 修正**：
   - [x] 交易來源設定：在監看 `[selectedSource, selectedProfile]` 的 `useEffect` 的 `else` 分支加上 `else if (!isCreatingNewProfile)` 防護。
   - [x] 系統設定：在監看 `[sysSelectedProfile]` 的 `useEffect` 的 `else` 分支加上 `else if (!isSysCreatingNewProfile)` 防護。

2. **對稱設定檔 Hook 修正（全面防護）**：
   - [x] `use-pension-rule-configs.ts`：在監看 `[pensionSelectedProfile]` 的 `useEffect` 的 `else` 分支加上 `else if (!isPensionCreatingNewProfile)` 防護。
   - [x] `use-tax-rule-configs.ts`：在監看 `[taxSelectedProfile]` 的 `useEffect` 的 `else` 分支加上 `else if (!isTaxCreatingNewProfile)` 防護。

---

## 修正結果報告

### 1. 問題成因與防護機制
- 原先各設定檔 Hook 在點擊「新增設定檔（New Profile）」時，會清空選中的 Profile（例如 `setSysSelectedProfile("")`）以準備建立新名稱，並同時填入一組初始預設值（例如 `setSystemConfigDetails({...})`）。
- 然而，監看 Profile 變化的 `useEffect` 偵測到值變為空字串後，無條件走進 `else` 分支執行 `setDetails(null)`，忽略了此時已處於 `isCreatingNewProfile = true` 的新增狀態，導致剛初始化的預設物件在下一個 render cycle 瞬間被抹除為 `null`，Panel 元件因而呈現「No profile details available.」，無法進行後續填寫與儲存。

### 2. 修正範圍與落實
針對全域設定頁面的 4 組設定檔 Hook 完整落實防護：
1. **交易來源設定（Transaction Source Config）**：
   - [use-finance-configs.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts)：`useEffect` 的 `else` 分支改為 `else if (!isCreatingNewProfile)`，依賴項加入 `isCreatingNewProfile`。
2. **系統設定（System Config）**：
   - [use-finance-configs.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts)：`useEffect` 的 `else` 分支改為 `else if (!isSysCreatingNewProfile)`，依賴項加入 `isSysCreatingNewProfile`。
3. **年金規則設定（Pension Rule Config）**：
   - [use-pension-rule-configs.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts)：`useEffect` 的 `else` 分支改為 `else if (!isPensionCreatingNewProfile)`，依賴項加入 `isPensionCreatingNewProfile`。
4. **稅務規則設定（Tax Rule Config）**：
   - [use-tax-rule-configs.ts](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts)：`useEffect` 的 `else` 分支改為 `else if (!isTaxCreatingNewProfile)`，依賴項加入 `isTaxCreatingNewProfile`。

點擊「新增設定檔」時，表單能穩定呈現預設值供使用者修改、自訂名稱並送出儲存；點擊「取消」或切換下拉選單時，亦能正確依據最新選取值還原或清空細節。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                     10029  12.27G   5.30    10:46:22  
diff --git a/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts b/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
index 6dfedbe..0610367 100644
--- a/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
+++ b/hub-ui/src/app/finance/configs/general/hooks/use-finance-configs.ts
@@ -192,18 +192,18 @@ export function useFinanceConfigs() {
   useEffect(() => {
     if (selectedSource && selectedProfile) {
       fetchSourceConfigDetails(selectedSource, selectedProfile);
-    } else {
+    } else if (!isCreatingNewProfile) {
       setSourceConfigDetails(null);
     }
-  }, [selectedSource, selectedProfile, fetchSourceConfigDetails]);
+  }, [selectedSource, selectedProfile, isCreatingNewProfile, fetchSourceConfigDetails]);
 
   useEffect(() => {
     if (sysSelectedProfile) {
       fetchSystemConfigDetails(sysSelectedProfile);
-    } else {
+    } else if (!isSysCreatingNewProfile) {
       setSystemConfigDetails(null);
     }
-  }, [sysSelectedProfile, fetchSystemConfigDetails]);
+  }, [sysSelectedProfile, isSysCreatingNewProfile, fetchSystemConfigDetails]);
 
   // === Transaction Source Handlers ===
   const handleSourceChange = (source: string) => {
diff --git a/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts b/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts
index 8605311..35f223c 100644
--- a/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts
+++ b/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts
@@ -73,10 +73,10 @@ export function usePensionRuleConfigs() {
   useEffect(() => {
     if (pensionSelectedProfile) {
       fetchPensionRuleConfigDetails(pensionSelectedProfile);
-    } else {
+    } else if (!isPensionCreatingNewProfile) {
       setPensionConfigDetails(null);
     }
-  }, [pensionSelectedProfile, fetchPensionRuleConfigDetails]);
+  }, [pensionSelectedProfile, isPensionCreatingNewProfile, fetchPensionRuleConfigDetails]);
 
   const handlePensionProfileChange = (profile: string) => {
     setPensionSelectedProfile(profile);
diff --git a/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts b/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts
index 2b7330f..7e9670b 100644
--- a/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts
+++ b/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts
@@ -89,10 +89,10 @@ export function useTaxRuleConfigs() {
   useEffect(() => {
     if (taxSelectedProfile) {
       fetchTaxRuleConfigDetails(taxSelectedProfile);
-    } else {
+    } else if (!isTaxCreatingNewProfile) {
       setTaxConfigDetails(null);
     }
-  }, [taxSelectedProfile, fetchTaxRuleConfigDetails]);
+  }, [taxSelectedProfile, isTaxCreatingNewProfile, fetchTaxRuleConfigDetails]);
 
   const handleTaxProfileChange = (profile: string) => {
     setTaxSelectedProfile(profile);
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                                                                          10030  11.16G   3.28    11:29:20  