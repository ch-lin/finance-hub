# [FIXED] Issue 164：編輯既有房貸時點擊「計算」月付金試算，用的是貸款最初的原始本金跟原始期數，不是目前真正剩餘的餘額跟剩餘月數

## 這份issue怎麼被發現的
這是這次Source Code Review針對前端業務邏輯進行審查時發現的問題。

## 相關檔案
- `hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx:60-91`（`calculateEstimate`，試算函式本身）
- 同檔案`:101-128`（`openDialogForEdit`，開啟編輯對話框時，把表單欄位帶入既有房貸的資料）
- 同檔案`:385-396`（「計算」按鈕，在「新增房貸」跟「編輯房貸」兩種對話框裡，顯示方式完全相同，沒有針對編輯模式做任何調整）

## 現狀
`calculateEstimate()`固定讀取`formData.totalLoanAmount`(原始貸款總額)當作本金`P`、`formData.loanTermMonths`(原始總期數)當作期數`n`，去跑標準的月付金公式。`openDialogForEdit`把對話框的這兩個欄位，帶入的是這筆房貸資料庫裡的**原始**金額跟**原始**總期數——跟目前真正剩餘的餘額(`formData.currentBalance`)、剩餘月數完全不是同一組數字，而且試算函式從頭到尾都沒有讀取過`currentBalance`這個欄位。「計算」這個按鈕，在新增房貸(本金=餘額、期數=剩餘期數，這種情況下公式本身是對的)跟編輯既有房貸(本金跟餘額已經是兩個不同的數字)這兩種對話框裡，顯示跟行為完全相同，沒有任何區分。

## 為何是問題
對一筆全新的房貸，`totalLoanAmount`跟`currentBalance`相等，`loanTermMonths`就是剩餘期數，所以這個公式本身沒有問題。但對一筆已經還了好幾年的既有房貸(例如剛好遇到5年期浮動利率重新訂價，使用者想拿銀行新寄來的利率去抓算一個大概的月付金做確認)，使用者打開編輯對話框、點擊「計算」，試算用的卻是當年簽約時的1000萬円本金、420個月期數，而不是現在真正剩下的餘額跟剩餘月數——算出來的數字會跟真實情況差很多，而且這個算出來的估計值會直接覆蓋掉`monthlyPayment`這個欄位，如果使用者剛好是為了「核對銀行給的新月付金是否合理」才來編輯這筆房貸，很可能會被這個基於錯誤本金/期數算出來的數字誤導，甚至不小心把原本從銀行抓來的正確金額蓋掉。

## 建議修正方向
這裡列出可能的方向，不代為決定：

**方向A：編輯模式下，試算改用目前餘額跟剩餘月數**
- 做法：偵測到`editingMortgage`存在時，`P`改用`formData.currentBalance`，`n`改用算出來的剩餘月數(`loanTermMonths`扣掉已經過去的月數，同時要扣掉[Fixed-84](../fixed/Fixed-84.md)已經處理過的「縮短年限」提前還款累積減少的月數)。
- 優點：讓試算結果真正對應使用者編輯時想核對的情境。
- 缺點：需要正確重現「剩餘月數」的計算方式，這部分邏輯目前只存在後端(`MortgageCalculator`)，前端需要重新實作一份對應的簡化版本。

**方向B：編輯模式下直接隱藏或關閉「計算」按鈕**
- 做法：既有房貸本來就該依賴銀行提供的真實還款表，不應該用這個簡化試算工具去取代。
- 優點：跟系統目前既有的免責聲明精神一致(「請參考銀行提供的實際明細」)。
- 缺點：如果使用者確實只是想抓一個大概的數字做心理準備，拿掉這個功能會少一個方便的入口。

**方向C：保留按鈕，但依模式調整警示文字**
- 做法：在編輯模式下，明確加一行提示，說明這個試算是「假設從現在重新開始、用這組原始條件計算一筆全新貸款」，不是目前真正剩餘餘額的試算。
- 優點：不拿掉功能，只是讓使用者清楚知道這個數字代表什麼。
- 缺點：使用者仍然可能略過警示文字直接套用結果。

---

## 決策方向

原提案中方向 A、B、C 經深入檢視後，三者皆存在關鍵缺陷，故**皆不予採用**：

1. **方向 A 的缺陷（黑盒子推算易失真、系統未掌握剩餘月數）**：
   - 資料模型中只有原始期數（`loanTermMonths`）與起始日，前端並無常態保存的「剩餘月數」。若使用者曾辦理「縮短年限」提前還款（REDUCE_TERM，見 [Fixed-84](../fixed/Fixed-84.md)）或從銀行匯入缺少歷史明細，前端試圖在背後自動反推剩餘月數極易出錯且邏輯脆弱，違背現金流模擬器的設計邊界。
2. **方向 B 的缺陷（剝奪編輯時粗估輔助的彈性）**：
   - 雖然隱藏按鈕能有效防止使用者手滑污染資料，但若使用者因銀行通知調升浮動利率、在尚未取得正式還款表前想在畫面上一鍵粗估月付金以做心理準備，完全拔除功能會少了一個便利的輔助入口。
3. **方向 C 的缺陷（雞肋且誤導，未消除意外覆蓋風險）**：
   - 編輯既有房貸時，使用者根本不需要「假設現在借一筆全新貸款」的月付金。算出來的數字本質上就是錯誤的假數據，加了警示文字仍極易被使用者忽略，且點擊後依然會直接覆蓋掉原本從銀行抄來的正確月付金。

---

### 方向 D：解耦為獨立輔助試算沙盒（Decoupled Scratchpad / Helper Block），計算與套用明確分離（採納方案）

- **核心概念**：
  - 回歸 [Fixed-28](../fixed/Fixed-28-mortgage-monthly-payment-ignores-bonus-payments.md)「Data is Truth（現金流模擬器非銀行記帳系統，月付金以銀行為準）」原則。
  - 將「計算」功能從主表單的「每月金額（`monthlyPayment`）」輸入欄位旁徹底拔除，使主輸入框回歸純手動填寫銀行真實扣款金額。
  - 在對話框下方新增獨立的「輔助試算沙盒（Scratchpad）」區塊（平時可預設折疊收合，避免干擾常規填寫）。
  - 沙盒內提供獨立的輸入參數（試算本金、剩餘月數、年利率），試算結果僅在沙盒內部預覽，並提供顯式的「套用到每月金額」按鈕。
- **具體運作機制**：
  1. **主表單欄位解耦**：
     - 主表單的 `Monthly Payment` 輸入框旁不再放置任何會直接覆蓋該欄位的按鈕，徹底防止意外誤觸覆蓋。
  2. **獨立輔助沙盒（可折疊區塊）**：
     - 在表單下方提供獨立區塊（例如：`📐 簡易月付金試算工具（粗估參考）`）。
     - 點開後提供透明的自訂試算參數：
       - **試算本金**（新增模式預設帶入總貸款額；編輯模式預設帶入目前餘額，使用者皆可自由任意調整）。
       - **剩餘期數（月數）**（使用者手動填入欲試算的剩餘月數，或預設帶入原始期數）。
       - **年利率**（預設帶入表單設定的利率）。
       - **還款方式**（元利均等 / 元金均等）。
     - 提供【試算】按鈕：點擊後僅跑簡易年金公式，並在沙盒區塊內展示結果（如：*預估月付金：約 ¥98,540 / 月*），同時標註免責警語（*簡易公式粗估，不含獎金還款，實際請以銀行還款表為準*）。
  3. **顯式確認與套用（Explicit Apply）**：
     - 試算成功後，沙盒內才顯現【套用到每月金額】按鈕。
     - 唯有使用者主動點擊該按鈕時，才將試算結果填入主表單的 `monthlyPayment`。若使用者僅是好奇粗估，主表單數據毫髮無損。
  4. **使用者責任與系統界限完全釐清**：
     - 系統明確聲明這僅是簡易輔助公式，使用者要手動如實填寫銀行真實金額，或是想偷懶使用試算結果套用，皆由使用者自主決定並自行負責。
- **UX 效益**：
  1. **徹底根除資料意外污染**：計算與儲存欄位完全分離，絕不發生點擊試算即無預警覆蓋真值的情形。
  2. **過程完全透明（No Blackbox）**：使用者完全清楚當前試算是拿多少本金、多少月數計算，不再有背後偷偷拿原始貸款額計算的認知落差。
  3. **新增與編輯情境無縫通用**：無論是新開貸款或是既有貸款調息粗估，皆使用同一套直觀、安全的輔助沙盒。

---

## 實作成果報告

### 1. 修改檔案
- `hub-ui/hub-ui/src/app/finance/accounts/mortgages/page.tsx`

### 2. 重點實作成效
1. **主表單欄位解耦**：
   - 移除 `Monthly Payment` 欄位旁原有的「計算」按鈕，主輸入框回歸純手動填寫，並加註真實資料提示文字（`* Ground truth from bank (返済予定表 / 通帳引き落とし額)`）。
2. **獨立輔助試算沙盒（Scratchpad）**：
   - 於表單下方新增獨立折疊區塊 `📐 簡易月付金試算工具 (粗估參考)`。
   - 支援透明化自訂參數：試算本金（新增時預設帶入總額，編輯時預設帶入剩餘餘額）、剩餘期數（月數）、年利率、還款方式。
   - 提供「帶入表單目前數值」按鈕，方便隨時從主表單一鍵同步當前數值。
3. **顯式確認與套用（Explicit Consent）**：
   - 點擊「開始試算」後，僅在沙盒內部計算並顯示預估月付金及免責聲明，絕不自動修改或覆蓋主表單資料。
   - 唯有使用者主動點擊「套用到每月金額欄位」按鈕時，才顯式帶入上方的 `monthlyPayment`。
4. **獎金加碼還款保護**：
   - 若房貸有設定獎金還款（Bonus Payments），沙盒內部會特別顯示醒目的警示框，提示簡易公式未扣除獎金折現流，引導使用者直接以銀行還款表為準。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT  git --no-pager diff             10054  12.90G   17.39    10:06:20  
diff --git a/hub-ui/src/app/finance/accounts/mortgages/page.tsx b/hub-ui/src/app/finance/accounts/mortgages/page.tsx
index e482900..bd8ed33 100644
--- a/hub-ui/src/app/finance/accounts/mortgages/page.tsx
+++ b/hub-ui/src/app/finance/accounts/mortgages/page.tsx
@@ -14,7 +14,7 @@ import {
 } from "@/components/ui/table";
 import { Button } from "@/components/ui/button";
 import { Input } from "@/components/ui/input";
-import { Plus, Edit2, Trash2, Calculator } from "lucide-react";
+import { Plus, Edit2, Trash2, Calculator, ChevronDown, Check, RefreshCw } from "lucide-react";
 import {
   Dialog,
   DialogContent,
@@ -32,6 +32,14 @@ export default function MortgagesPage() {
   const [hasBonus, setHasBonus] = useState(false);
   const [hasEarlyRepayment, setHasEarlyRepayment] = useState(false);
   
+  // Optional Scratchpad Estimator State (Direction D / Issue 164)
+  const [estimatorOpen, setEstimatorOpen] = useState(false);
+  const [estimatorPrincipal, setEstimatorPrincipal] = useState<number | "">("");
+  const [estimatorMonths, setEstimatorMonths] = useState<number | "">("");
+  const [estimatorRate, setEstimatorRate] = useState<number | "">("");
+  const [estimatorMethod, setEstimatorMethod] = useState<"EQUAL_PAYMENT" | "EQUAL_PRINCIPAL">("EQUAL_PAYMENT");
+  const [estimatedAmount, setEstimatedAmount] = useState<number | null>(null);
+  
   const getEmptyForm = (): MortgageRecordRequest => ({
     loanName: "",
     totalLoanAmount: 0,
@@ -57,37 +65,56 @@ export default function MortgagesPage() {
 
   const [formData, setFormData] = useState<MortgageRecordRequest>(getEmptyForm());
 
-  const calculateEstimate = () => {
-    if (!formData.totalLoanAmount || formData.totalLoanAmount <= 0 || 
-        !formData.interestRate || formData.interestRate < 0 || 
-        !formData.loanTermMonths || formData.loanTermMonths <= 0) {
-      toast.error("Please fill in a valid Amount, Interest Rate, and Term to estimate.");
-      return;
-    }
+  const syncEstimatorFromForm = () => {
+    const principal = editingMortgage 
+      ? (formData.currentBalance && formData.currentBalance > 0 ? formData.currentBalance : (formData.totalLoanAmount || ""))
+      : (formData.totalLoanAmount || "");
+    setEstimatorPrincipal(principal);
+    setEstimatorMonths(formData.loanTermMonths || "");
+    setEstimatorRate(formData.interestRate || "");
+    setEstimatorMethod(formData.repaymentMethod || "EQUAL_PAYMENT");
+    setEstimatedAmount(null);
+  };
 
-    if (hasBonus && formData.bonusPayments?.some((bp) => (bp.amount ?? 0) > 0)) {
-      toast.error("This estimate does not account for the bonus payments you've set, and will be highly inaccurate. Please refer to your bank's actual repayment schedule.");
+  const handleRunEstimate = () => {
+    const P = Number(estimatorPrincipal);
+    const n = Number(estimatorMonths);
+    const rate = Number(estimatorRate);
+
+    if (!P || P <= 0 || !n || n <= 0 || rate < 0 || isNaN(rate)) {
+      toast.error("Please enter a valid Principal (> 0), Term (> 0), and Rate (>= 0) to estimate.");
       return;
     }
 
-    const P = Number(formData.totalLoanAmount);
-    const n = Number(formData.loanTermMonths);
-    const annualRate = Number(formData.interestRate) / 100;
+    const annualRate = rate / 100;
     const r = annualRate / 12;
 
-    let estimate = 0;
-    if (formData.repaymentMethod === "EQUAL_PAYMENT") {
+    let res = 0;
+    if (estimatorMethod === "EQUAL_PAYMENT") {
       if (r === 0) {
-        estimate = P / n;
+        res = P / n;
       } else {
-        estimate = (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
+        res = (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
       }
-    } else if (formData.repaymentMethod === "EQUAL_PRINCIPAL") {
-      estimate = P / n;
+    } else {
+      res = P / n;
+    }
+
+    setEstimatedAmount(Math.round(res));
+    toast.success("Estimated calculation completed.");
+  };
+
+  const handleApplyEstimate = () => {
+    if (estimatedAmount === null || estimatedAmount <= 0) {
+      toast.error("No valid estimate to apply.");
+      return;
     }
 
-    setFormData({ ...formData, monthlyPayment: Math.round(estimate) });
-    toast.success("Estimated monthly payment calculated.");
+    setFormData((prev) => ({
+      ...prev,
+      monthlyPayment: estimatedAmount,
+    }));
+    toast.success(`Applied ¥${estimatedAmount.toLocaleString()} to Monthly Payment.`);
   };
 
   const openDialogForNew = () => {
@@ -95,6 +122,12 @@ export default function MortgagesPage() {
     setFormData(getEmptyForm());
     setHasBonus(false);
     setHasEarlyRepayment(false);
+    setEstimatorOpen(false);
+    setEstimatorPrincipal("");
+    setEstimatorMonths("");
+    setEstimatorRate("");
+    setEstimatorMethod("EQUAL_PAYMENT");
+    setEstimatedAmount(null);
     setIsDialogOpen(true);
   };
 
@@ -124,6 +157,12 @@ export default function MortgagesPage() {
     });
     setHasBonus(mortgage.bonusPayments && mortgage.bonusPayments.length > 0);
     setHasEarlyRepayment(mortgage.earlyRepayments && mortgage.earlyRepayments.length > 0);
+    setEstimatorOpen(false);
+    setEstimatorPrincipal(mortgage.currentBalance > 0 ? mortgage.currentBalance : mortgage.totalLoanAmount);
+    setEstimatorMonths(mortgage.loanTermMonths || "");
+    setEstimatorRate(Number((mortgage.interestRate * 100).toFixed(4)));
+    setEstimatorMethod(mortgage.repaymentMethod || "EQUAL_PAYMENT");
+    setEstimatedAmount(null);
     setIsDialogOpen(true);
   };
 
@@ -382,35 +421,174 @@ export default function MortgagesPage() {
               <label className="text-sm font-medium">
                 {formData.repaymentMethod === "EQUAL_PRINCIPAL" ? "Monthly Principal (JPY)" : "Monthly Payment (JPY)"}
               </label>
-              <div className="flex items-center space-x-2">
-                <Input
-                  type="number"
-                  value={formData.monthlyPayment || ""}
-                  onChange={(e) => setFormData({ ...formData, monthlyPayment: Number(e.target.value) })}
-                  required
-                />
-                <Button type="button" variant="secondary" onClick={calculateEstimate} title="Calculate Estimate">
-                  <Calculator size={16} className="mr-2" />
-                  計算
-                </Button>
-              </div>
-              {formData.repaymentMethod === "EQUAL_PRINCIPAL" && (
+              <Input
+                type="number"
+                value={formData.monthlyPayment || ""}
+                onChange={(e) => setFormData({ ...formData, monthlyPayment: Number(e.target.value) })}
+                required
+                placeholder="e.g. 98000"
+              />
+              {formData.repaymentMethod === "EQUAL_PRINCIPAL" ? (
                 <p className="text-xs text-amber-600">
-                  * EQUAL PRINCIPAL: Please enter only the principal portion (excluding interest). Refer to the "Principal" column in your bank's repayment schedule, NOT the total payment.
+                  * EQUAL PRINCIPAL: Please enter only the principal portion (excluding interest). Refer to the &quot;Principal&quot; column in your bank&apos;s repayment schedule, NOT the total payment.
                 </p>
-              )}
-              {hasBonus && formData.bonusPayments?.some((bp) => (bp.amount ?? 0) > 0) && (
-                <p className="text-xs text-red-600 font-medium">
-                  * ⚠️ BONUS CONFIGURED: The estimate feature does not support bonus payments and will be highly inaccurate. Please directly use the values from your bank's schedule.
-                </p>
-              )}
-              {!(formData.repaymentMethod === "EQUAL_PRINCIPAL") && !(hasBonus && formData.bonusPayments?.some((bp) => (bp.amount ?? 0) > 0)) && (
+              ) : (
                 <p className="text-xs text-muted-foreground italic">
-                  * System estimate. Please refer to actual bank details.
+                  * Ground truth from bank (返済予定表 / 通帳引き落とし額).
                 </p>
               )}
             </div>
 
+            {/* Optional Scratchpad Estimator (Direction D / Issue 164) */}
+            <div className="col-span-2 rounded-lg border border-border/70 bg-muted/20 p-3.5 space-y-3">
+              <div className="flex items-center justify-between">
+                <div className="flex items-center space-x-2">
+                  <div className="p-1.5 rounded-md bg-primary/10 text-primary">
+                    <Calculator size={16} />
+                  </div>
+                  <div>
+                    <span className="text-sm font-semibold flex items-center gap-1.5">
+                      簡易月付金試算工具 (粗估參考)
+                    </span>
+                    <p className="text-xs text-muted-foreground">
+                      可選輔助沙盒：若手邊無銀行明細表可在此粗估，試算結果不會直接修改表單
+                    </p>
+                  </div>
+                </div>
+                <Button
+                  type="button"
+                  variant="ghost"
+                  size="sm"
+                  className="h-8 px-2 text-muted-foreground hover:text-foreground"
+                  onClick={() => {
+                    const next = !estimatorOpen;
+                    setEstimatorOpen(next);
+                    if (next && estimatorPrincipal === "" && estimatorMonths === "") {
+                      syncEstimatorFromForm();
+                    }
+                  }}
+                >
+                  <span className="text-xs mr-1">{estimatorOpen ? "收合" : "展開試算"}</span>
+                  <ChevronDown size={14} className={`transition-transform duration-200 ${estimatorOpen ? "rotate-180" : ""}`} />
+                </Button>
+              </div>
+
+              {estimatorOpen && (
+                <div className="space-y-3 pt-2 border-t border-border/50">
+                  {hasBonus && formData.bonusPayments?.some((bp) => (bp.amount ?? 0) > 0) && (
+                    <div className="p-2.5 rounded-md bg-amber-500/10 border border-amber-500/20 text-xs text-amber-600 font-medium">
+                      ⚠️ 注意：此房貸已設定獎金加碼還款（Bonus Payments）。本簡易公式未扣除獎金折現流，試算金額將有顯著偏差，強烈建議直接以銀行還款表（返済予定表）金額為準。
+                    </div>
+                  )}
+
+                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
+                    <div className="space-y-1">
+                      <label className="text-xs font-medium text-muted-foreground">試算本金 (JPY)</label>
+                      <Input
+                        type="number"
+                        placeholder="e.g. 30000000"
+                        value={estimatorPrincipal}
+                        onChange={(e) => {
+                          setEstimatorPrincipal(e.target.value === "" ? "" : Number(e.target.value));
+                          setEstimatedAmount(null);
+                        }}
+                        className="h-8 text-xs"
+                      />
+                    </div>
+                    <div className="space-y-1">
+                      <label className="text-xs font-medium text-muted-foreground">剩餘期數 (Months)</label>
+                      <Input
+                        type="number"
+                        placeholder="e.g. 360"
+                        value={estimatorMonths}
+                        onChange={(e) => {
+                          setEstimatorMonths(e.target.value === "" ? "" : Number(e.target.value));
+                          setEstimatedAmount(null);
+                        }}
+                        className="h-8 text-xs"
+                      />
+                    </div>
+                    <div className="space-y-1">
+                      <label className="text-xs font-medium text-muted-foreground">年利率 (%)</label>
+                      <Input
+                        type="number"
+                        step="0.001"
+                        placeholder="e.g. 0.5"
+                        value={estimatorRate}
+                        onChange={(e) => {
+                          setEstimatorRate(e.target.value === "" ? "" : Number(e.target.value));
+                          setEstimatedAmount(null);
+                        }}
+                        className="h-8 text-xs"
+                      />
+                    </div>
+                    <div className="space-y-1">
+                      <label className="text-xs font-medium text-muted-foreground">還款方式</label>
+                      <select
+                        className="flex h-8 w-full rounded-md border border-input bg-transparent px-2 text-xs shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
+                        value={estimatorMethod}
+                        onChange={(e) => {
+                          setEstimatorMethod(e.target.value as any);
+                          setEstimatedAmount(null);
+                        }}
+                      >
+                        <option value="EQUAL_PAYMENT">元利均等</option>
+                        <option value="EQUAL_PRINCIPAL">元金均等</option>
+                      </select>
+                    </div>
+                  </div>
+
+                  <div className="flex items-center justify-between pt-1">
+                    <Button
+                      type="button"
+                      variant="ghost"
+                      size="sm"
+                      className="h-7 text-xs text-muted-foreground hover:text-foreground px-2"
+                      onClick={syncEstimatorFromForm}
+                    >
+                      <RefreshCw size={12} className="mr-1" />
+                      帶入表單目前數值
+                    </Button>
+                    <Button
+                      type="button"
+                      variant="secondary"
+                      size="sm"
+                      className="h-8 text-xs px-3"
+                      onClick={handleRunEstimate}
+                    >
+                      <Calculator size={13} className="mr-1.5" />
+                      開始試算
+                    </Button>
+                  </div>
+
+                  {estimatedAmount !== null && (
+                    <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 p-2.5 rounded-md bg-primary/10 border border-primary/20">
+                      <div>
+                        <div className="text-xs text-muted-foreground">預估每期應繳：</div>
+                        <div className="text-base font-bold text-primary font-mono">
+                          ¥{estimatedAmount.toLocaleString()}{" "}
+                          <span className="text-xs font-normal text-muted-foreground">/ 月</span>
+                        </div>
+                      </div>
+                      <Button
+                        type="button"
+                        size="sm"
+                        className="h-8 text-xs px-3 bg-primary hover:bg-primary/90 text-primary-foreground font-medium"
+                        onClick={handleApplyEstimate}
+                      >
+                        <Check size={13} className="mr-1.5" />
+                        套用到每月金額欄位
+                      </Button>
+                    </div>
+                  )}
+
+                  <p className="text-[11px] text-muted-foreground italic">
+                    * 註：此為簡易數學公式試算（無日割、不含獎金加碼）。本系統為現金流模擬器，請務必以銀行實際扣款或還款明細表為準。
+                  </p>
+                </div>
+              )}
+            </div>
+
             <div className="col-span-2 flex items-center space-x-2 pt-2">
               <input 
                 type="checkbox" 
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui     feature/FINDING-34-mortgage-bonus-payment   INSERT                                  10055  12.45G   18.10    10:06:32  