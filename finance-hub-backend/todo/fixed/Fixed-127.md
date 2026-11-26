# [Fixed] Feature Requirement 127：教育設定「就讀時長」輸入框維持用「年」輸入，旁邊即時顯示換算後的月數，讓使用者親眼確認系統實際存的值

## 這是已經決定要做的任務，不是「要不要做」的討論
使用者已經拍板決定要修正這個UX/精度問題，並且指定了具體做法：**輸入框維持現狀，讓使用者用「年」輸入**——因為一般人對「這個學制念幾年」有直覺答案（問任何人「高中念幾年」都會秒答3年），但問「念幾個月」反而會讓人愣住、去糾結要不要扣掉寒暑假這類無關的細節，這種換算不是一般人腦子裡存的資訊，直接要求輸入月數會讓UX變差。真正要做的，是在輸入框旁邊即時顯示「這個年數換算成月數之後，系統實際會存的數字是多少」，讓使用者能親眼確認、及早發現任何潜在的誤差，而不是把輸入單位從年改成月。

## 原始問題（來自一份討論教育設定就讀時長年月換算誤差的question，該內容已被本文件取代整併，不再獨立存在）
`hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts:112-123`：後端／資料庫實際儲存的教育就讀時長，單位是月；畫面上目前用「年」為單位讓使用者輸入，再透過`yearsToMonths`換算回月存回後端：

```js
monthsToYears: Math.round((months / 12) * 10) / 10   // 3個月 -> 0.3年
yearsToMonths: Math.round(years * 12)                 // 0.3年 -> 4個月（不是原來的3）
```

只要使用者直接輸入一個乾淨的年數（例如3、1.5這種輸入框`step="0.5"`允許的值），`yearsToMonths`換算回月數本身不會有誤差（3年精確對應36個月，沒有四捨五入的空間）。真正會產生±1個月誤差的情境，是「資料庫裡已經存了一個不是12的整數倍的月數（例如21個月，代表某個之前用其他方式設定進去的值），這個月數先被`monthsToYears`換算顯示成一個四捨五入過的年數（顯示成1.8），使用者沒有改動這個值、單純重新觸發了這個欄位的換算（例如點進去又點出來），畫面上的1.8又被換算回`Math.round(1.8*12)=22`個月」——這個「月→年→月」來回換算的路徑，才是誤差發生的真正原因，跟「使用者直接輸入年數」這個正常操作路徑無關。

## 想要的行為
`hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:902-919`目前這個「Duration (Years)」輸入框，維持現狀——使用者一樣是輸入年、看到的一樣是年。唯一要新增的是：在這個輸入框旁邊（或下方），加一段純顯示、不可編輯的文字，即時顯示「使用者目前輸入的這個年數，換算成月數之後會是多少」（例如使用者輸入3，旁邊顯示「= 36 months」）。

這樣做的效果：
- 使用者永遠只需要用他秒答得出來的「年」去思考，不需要面對「這個階段實際上是幾個月」這種違反直覺的問題。
- 使用者能親眼確認系統實際會存的月數，如果哪次看到的月數跟自己以為的年數不吻合（例如上面提到的21個月被誤判成22個月這種情境），可以馬上察覺、及早發現，而不是被四捨五入悄悄吃掉都不知道。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx:902-919`（「Duration (Years)」輸入框本身，維持現狀，只需要在旁邊新增顯示用的月數提示文字）
- `hub-ui/hub-ui/src/app/finance/profile/children/hooks/use-child-profile.ts:112-123`（`monthsToYears`／`yearsToMonths`，維持現狀不需要修改；這裡额外要用到的是`yearsToMonths`本身，拿使用者目前輸入的年數即時算出對應的月數，用於這段新增的顯示文字）

## 實作方向（技術上單一、明確，不需要列多個選項比較）
- 輸入框的`value`跟`onChange`維持現狀不變，繼續是「年」為單位、繼續呼叫`yearsToMonths`把使用者輸入的年數換算成月數存進`onOverrideChange('manualDuration', ...)`。
- 在輸入框旁邊新增一段文字（例如小字、灰色），內容是把使用者目前在這個輸入框裡打的年數，即時再算一次`yearsToMonths(該年數)`，格式類似「= X months」，這段文字純粹顯示、不影響任何送出的資料，也不會反過來改變輸入框本身的值。
- 這個新增的顯示文字，用的换算方向跟目前`onChange`裡已經在做的换算完全一樣（都是年→月），不是新增一條獨立的换算邏輯，單純是把已經在做的这次换算結果，同時顯示給使用者看而已。

---

## 實作完成報告（Implementation Report）

### 一、實作狀態
- [x] **已完成**：在教育設定「就讀時長（Duration）」輸入框下方新增即時換算月數的純顯示提示（比照既有「年生活費」換算提示規格）。

### 二、改動檔案
- **[child-profile-section.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx#L1201-L1230)**

### 三、具體改動說明與程式碼比對

#### 1. 樣式與實作對齊既有「年生活費」換算模式
比照同檔案中 `Manual Living Cost (¥/year)` 下方的 `≈ ¥{...} / month` 提示（採用 `text-[11px] text-muted-foreground`），在 Duration 輸入框下方加上換算後的月數提示。

#### 2. 程式碼變更（Diff）
```diff
@@ -1198,6 +1198,9 @@
                   className="h-8 text-sm bg-muted text-muted-foreground"
                   value="3 Years (2-Stage Total)"
                 />
+                <p className="text-[11px] text-muted-foreground">
+                  = 36 months
+                </p>
               </div>
             ) : (
               <div className="space-y-1">
@@ -1219,6 +1219,11 @@
                     onOverrideChange('manualDuration', yearsToMonths(years));
                   }}
                 />
+                {override?.manualDuration != null && override.manualDuration > 0 && (
+                  <p className="text-[11px] text-muted-foreground">
+                    = {override.manualDuration} {override.manualDuration === 1 ? 'month' : 'months'}
+                  </p>
+                )}
               </div>
             )}
```

### 四、涵蓋情境與行為驗證
1. **即時連動確認**：
   - 使用者輸入 `3`（年）$\to$ 下方即時顯示 `= 36 months`。
   - 使用者輸入 `1.5`（年）$\to$ 下方即時顯示 `= 18 months`。
   - 單數複數單位自適應：若月數為 1 則顯示 `month`，大於 1 則顯示 `months`。
2. **無覆寫 / 清空情境**：
   - 當欄位清空或為預設值（`override?.manualDuration` 為 null 或 0）時，提示條件渲染不觸發，畫面維持簡潔。
3. **幼稚園保底檢查**：
   - 在單軌幼兒園模式下，若輸入低於 3 年，自動 clamp 為 3 年，下方亦正確同步提示 `= 36 months`。
4. **幼稚園兩段拆分模式（Preschool Split Enabled）**：
   - 兩段式固定為 3 年的欄位（`3 Years (2-Stage Total)`）下方亦補上 `= 36 months`，確保整體介面與其他學制對齊一致。
5. **純顯示且無副作用**：
   - 提示純粹為不可編輯的資訊呈現，不干涉表單值、送出 payload 與後端資料庫存取。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff                                                                 10035  12.67G   4.64    11:49:34  
diff --git a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
index 05eb59e..0095433 100644
--- a/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
+++ b/hub-ui/src/app/finance/profile/children/components/child-profile-section.tsx
@@ -1198,6 +1198,9 @@ function StageCard({
                   className="h-8 text-sm bg-muted text-muted-foreground"
                   value="3 Years (2-Stage Total)"
                 />
+                <p className="text-[11px] text-muted-foreground">
+                  = 36 months
+                </p>
               </div>
             ) : (
               <div className="space-y-1">
@@ -1220,6 +1223,11 @@ function StageCard({
                     onOverrideChange('manualDuration', yearsToMonths(years));
                   }}
                 />
+                {override?.manualDuration != null && override.manualDuration > 0 && (
+                  <p className="text-[11px] text-muted-foreground">
+                    = {override.manualDuration} {override.manualDuration === 1 ? 'month' : 'months'}
+                  </p>
+                )}
               </div>
             )}
 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                                                                      10036  12.49G   4.60    11:49:46  
