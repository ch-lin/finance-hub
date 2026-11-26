# Fixed 102：薪資所得扣除（給与所得控除）稅制表，多個所得區間的計算公式系統性多扣了19萬日圓

## 嚴重程度
High（直接影響每一次模擬計算出來的稅金與可支配所得，方向是讓稅金被低估、淨收入被高估，讓模擬結果比實際情況更樂觀，正好是這個工具最需要避免的方向）

## 相關檔案
- `finance-hub/finance-hub-backend/hub/src/main/resources/application.properties:111-134`
- `finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties:120-143`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java:224-231`（`calculateEmploymentIncomeDeduction`，讀取上述設定值進行計算的地方）

## 現狀
系統目前設定的薪資所得扣除級距表（`EMPLOYMENT_INCOME_DEDUCTION`）如下：

| 收入區間 | 目前設定的乘數 | 目前設定的加碼金額 |
|---|---|---|
| ~1,625,000 | 0.0 | 740,000（固定金額） |
| 1,625,000~1,800,000 | 0.4 | 90,000 |
| 1,800,000~3,600,000 | 0.3 | **270,000** |
| 3,600,000~6,600,000 | 0.2 | **630,000** |
| 6,600,000~8,500,000 | 0.1 | **1,290,000** |
| 8,500,000以上 | 0.0 | **2,140,000**（固定金額） |

## 原因
已經直接向日本國稅廳官方網頁（nta.go.jp）逐字核對，令和2年度以後（含令和7、8、9年度，只有最低那一段的門檻金額每年會微調，中高收入的這幾段公式維持不變）的官方公式如下：

| 收入區間 | 官方公式 |
|---|---|
| 1,800,001~3,600,000 | 收入金額×30%+**80,000**円 |
| 3,600,001~6,600,000 | 收入金額×20%+**440,000**円 |
| 6,600,001~8,500,000 | 收入金額×10%+**1,100,000**円 |
| 8,500,001以上 | **1,950,000**円（上限） |

把系統目前設定的加碼金額，跟官方公式逐項對照：270,000對80,000、630,000對440,000、1,290,000對1,100,000、2,140,000對1,950,000——四個區間的落差都精準地剛好是19萬日圓，這種一致的偏移量，代表這不是四個各自獨立的小誤差，而是同一個系統性的錄入問題。

另外，1,625,000~1,800,000這一段（乘數0.4、加碼90,000）本身也對不上任何一個年度的官方公式——這個區間在現行任何年度的官方表格裡都不存在，官方表格是從最低那一段的固定金額，直接跳到1,800,001以後的分段公式，中間沒有這一段獨立的過渡區間。

## 具體錯誤情境
任何年薪落在1,800,000~3,600,000、3,600,000~6,600,000、6,600,000~8,500,000，或超過8,500,000的使用者，系統算出來的應稅所得都會比真實情況少19萬日圓（固定金額的落差，不是百分比）。舉例來說，年薪600萬日圓的使用者，正確的扣除額應該是 6,000,000×0.2+440,000 = 1,640,000；系統目前會算成 6,000,000×0.2+630,000 = 1,830,000，多扣了19萬日圓的應稅所得，等於少繳了一筆稅金。以20%所得稅率加10%住民稅率概算，這筆19萬日圓的落差，每年大約會讓稅金被低估5萬7千日圓左右，而且這個落差會在整個模擬的每一年持續發生，累積數十年下來會讓模擬出的淨資產顯著偏向樂觀。

## 建議修正
把 `application.properties` 跟 `application-h2.properties` 兩份設定檔裡，四個區間的加碼金額分別修正為80,000、440,000、1,100,000、1,950,000；並移除1,625,000~1,800,000這個目前對不上任何官方公式的過渡區間，讓最低那一段的固定金額門檻，直接接續到1,800,001以後的分段公式。這是單純的數字校正，不是需要討論方向的設計選擇。

---

# Issue 102 修復總結報告

## 修復日期
2026-09-30

## 法律查證與成因分析
1. **加碼金額多扣 19 萬日圓屬實**：
   - 系統先前在中高所得區間（360萬以上及850萬以上上限）之加碼金額確實比日本國稅廳法定公式多算了 19 萬日圓（630,000 vs 440,000、1,290,000 vs 1,100,000、2,140,000 vs 1,950,000）。
   - **成因**：2026 年（令和8年）稅改將最低保障額從 55 萬提高到 74 萬（+19 萬），先前設定時誤將舊表所有級距 offset 全體無差別 +19 萬（包括將乘數 0.4、加碼 -10 萬的舊區間誤算為 +9 萬）。

2. **2026 年（令和8年）最新法定級距劃分之校正**：
   - Issue 102 原報告建議「將第一段門檻設為 1,800,000，其後直接接續 30%+8萬」，但查證日本國稅廳（国税庁 No.1410 給与所得控除）令和8年（2026年）最新規定發現：
     - 最低保障額調高至 74 萬的同時，**第一段固定金額之適用上限已同步推移至 2,200,000 円**。
     - $2,200,000 \times 30\% + 80,000 = 740,000$，正好與固定額平滑無縫接軌。
     - 若依原報告設於 180 萬，會導致年收 180~220 萬使用者扣除額出現「斷崖式驟降」（181 萬年收之扣除額跌至 62.3 萬），嚴重少扣。
   - 因此本次修復採用**符合 2026 令和8年法定標準的 5 級距架構**（220萬以下一律固定 74 萬，其後各級距無縫銜接）。

## 修改檔案清單
1. `finance-hub/finance-hub-backend/hub/src/main/resources/application.properties`
2. `finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties`
3. `finance-hub/.env.base`
4. `finance-hub/.env.dev`
5. `finance-hub/.env.prod`

## 修正後級距表（2026 令和8年官方正式標準）
| 級距索引 | 收入上限（max-salary） | 乘數（multiplier） | 加碼金額（offset） | 說明 |
|---|---|---|---|---|
| `[0]` | 2,200,000 | 0.0 | 740,000 | 最低保障額（2026 最新法定門檻） |
| `[1]` | 3,600,000 | 0.3 | 80,000 | 收入 × 30% + 8 萬（220 萬處平滑銜接 74 萬） |
| `[2]` | 6,600,000 | 0.2 | 440,000 | 收入 × 20% + 44 萬（校正原 63 萬） |
| `[3]` | 8,500,000 | 0.1 | 1,100,000 | 收入 × 10% + 110 萬（校正原 129 萬） |
| `[4]` | 9223372036854775807 | 0.0 | 1,950,000 | 法定控除上限 195 萬（校正原 214 萬） |
*(原 `[5]` 冗餘級距已全數移除)*

## 效益
- 徹底消除中高收入者每人每年 19 萬日圓之應稅所得虛報扣除，確保長期模擬之稅金與可支配現金流真實準確。
- 正確對齊 2026 年（令和8年）之 220 萬門檻與 74 萬最低保障額，避免分段點斷崖少算扣除額的問題。

## Diff

 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT  git --no-pager diff                                                                                                                                                        10005  12.76G   12.39    17:04:36  
diff --git a/.env.base b/.env.base
index 96f8cb8e..d44976b1 100644
--- a/.env.base
+++ b/.env.base
@@ -75,29 +75,25 @@ FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INCOME_TAX="1040000"
 FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INHABITANT_TAX="430000"
 FINANCE_HUB_TAX_RULE_DEPENDENT_INCOME_THRESHOLD="1780000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="1625000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="2200000"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MULTIPLIER="0.0"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_OFFSET="740000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MAX_SALARY="1800000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MULTIPLIER="0.4"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_OFFSET="90000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MAX_SALARY="3600000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MULTIPLIER="0.3"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_OFFSET="80000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MAX_SALARY="3600000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MULTIPLIER="0.3"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_OFFSET="270000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MAX_SALARY="6600000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MULTIPLIER="0.2"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_OFFSET="440000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MAX_SALARY="6600000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MULTIPLIER="0.2"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_OFFSET="630000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MAX_SALARY="8500000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MULTIPLIER="0.1"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_OFFSET="1100000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MAX_SALARY="8500000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MULTIPLIER="0.1"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_OFFSET="1290000"
-
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_MAX_SALARY="9223372036854775807"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_MULTIPLIER="0.0"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_OFFSET="2140000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MAX_SALARY="9223372036854775807"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MULTIPLIER="0.0"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_OFFSET="1950000"
 
 FINANCE_HUB_TAX_RULE_BRACKETS_INCOME_TAX_RATE_0_MAX_SALARY="1950000"
 FINANCE_HUB_TAX_RULE_BRACKETS_INCOME_TAX_RATE_0_MULTIPLIER="0.05"
diff --git a/.env.dev b/.env.dev
index 02ee695d..a648ad10 100644
--- a/.env.dev
+++ b/.env.dev
@@ -74,29 +74,25 @@ FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INCOME_TAX="1040000"
 FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INHABITANT_TAX="430000"
 FINANCE_HUB_TAX_RULE_DEPENDENT_INCOME_THRESHOLD="1780000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="1625000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="2200000"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MULTIPLIER="0.0"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_OFFSET="740000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MAX_SALARY="1800000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MULTIPLIER="0.4"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_OFFSET="90000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MAX_SALARY="3600000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MULTIPLIER="0.3"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_OFFSET="80000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MAX_SALARY="3600000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MULTIPLIER="0.3"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_OFFSET="270000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MAX_SALARY="6600000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MULTIPLIER="0.2"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_OFFSET="440000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MAX_SALARY="6600000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MULTIPLIER="0.2"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_OFFSET="630000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MAX_SALARY="8500000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MULTIPLIER="0.1"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_OFFSET="1100000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MAX_SALARY="8500000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MULTIPLIER="0.1"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_OFFSET="1290000"
-
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_MAX_SALARY="9223372036854775807"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_MULTIPLIER="0.0"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_OFFSET="2140000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MAX_SALARY="9223372036854775807"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MULTIPLIER="0.0"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_OFFSET="1950000"
 
 FINANCE_HUB_TAX_RULE_BRACKETS_INCOME_TAX_RATE_0_MAX_SALARY="1950000"
 FINANCE_HUB_TAX_RULE_BRACKETS_INCOME_TAX_RATE_0_MULTIPLIER="0.05"
diff --git a/.env.prod b/.env.prod
index 6584c49c..f7b3fce1 100644
--- a/.env.prod
+++ b/.env.prod
@@ -76,29 +76,25 @@ FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INCOME_TAX="1040000"
 FINANCE_HUB_TAX_RULE_BASIC_DEDUCTION_INHABITANT_TAX="430000"
 FINANCE_HUB_TAX_RULE_DEPENDENT_INCOME_THRESHOLD="1780000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="1625000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MAX_SALARY="2200000"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_MULTIPLIER="0.0"
 FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_0_OFFSET="740000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MAX_SALARY="1800000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MULTIPLIER="0.4"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_OFFSET="90000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MAX_SALARY="3600000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_MULTIPLIER="0.3"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_1_OFFSET="80000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MAX_SALARY="3600000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MULTIPLIER="0.3"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_OFFSET="270000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MAX_SALARY="6600000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_MULTIPLIER="0.2"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_2_OFFSET="440000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MAX_SALARY="6600000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MULTIPLIER="0.2"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_OFFSET="630000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MAX_SALARY="8500000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_MULTIPLIER="0.1"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_3_OFFSET="1100000"
 
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MAX_SALARY="8500000"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MULTIPLIER="0.1"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_OFFSET="1290000"
-
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_MAX_SALARY="9223372036854775807"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_MULTIPLIER="0.0"
-FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_5_OFFSET="2140000"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MAX_SALARY="9223372036854775807"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_MULTIPLIER="0.0"
+FINANCE_HUB_TAX_RULE_BRACKETS_EMPLOYMENT_INCOME_DEDUCTION_4_OFFSET="1950000"
 
 FINANCE_HUB_TAX_RULE_BRACKETS_INCOME_TAX_RATE_0_MAX_SALARY="1950000"
 FINANCE_HUB_TAX_RULE_BRACKETS_INCOME_TAX_RATE_0_MULTIPLIER="0.05"
diff --git a/finance-hub-backend/hub/src/main/resources/application-h2.properties b/finance-hub-backend/hub/src/main/resources/application-h2.properties
index b80ddee8..36d32da8 100644
--- a/finance-hub-backend/hub/src/main/resources/application-h2.properties
+++ b/finance-hub-backend/hub/src/main/resources/application-h2.properties
@@ -118,29 +118,25 @@ finance-hub.tax-rule.basic-deduction-inhabitant-tax=430000
 finance-hub.tax-rule.dependent-income-threshold=1780000
 
 # Tax Brackets: EMPLOYMENT_INCOME_DEDUCTION (薪資所得扣除級距)
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=1625000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=2200000
 finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].multiplier=0.0
 finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].offset=740000
 
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].max-salary=1800000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].multiplier=0.4
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].offset=90000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].max-salary=3600000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].multiplier=0.3
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].offset=80000
 
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].max-salary=3600000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].multiplier=0.3
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].offset=270000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].max-salary=6600000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].multiplier=0.2
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].offset=440000
 
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].max-salary=6600000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].multiplier=0.2
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].offset=630000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].max-salary=8500000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].multiplier=0.1
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].offset=1100000
 
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].max-salary=8500000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].multiplier=0.1
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].offset=1290000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[5].max-salary=9223372036854775807
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[5].multiplier=0.0
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[5].offset=2140000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].max-salary=9223372036854775807
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].multiplier=0.0
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].offset=1950000
 
 # Tax Brackets: INCOME_TAX_RATE (所得稅率級距)
 finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0].max-salary=1950000
diff --git a/finance-hub-backend/hub/src/main/resources/application.properties b/finance-hub-backend/hub/src/main/resources/application.properties
index 263d564f..10828322 100644
--- a/finance-hub-backend/hub/src/main/resources/application.properties
+++ b/finance-hub-backend/hub/src/main/resources/application.properties
@@ -109,29 +109,25 @@ finance-hub.tax-rule.basic-deduction-inhabitant-tax=430000
 finance-hub.tax-rule.dependent-income-threshold=1780000
 
 # Tax Brackets: EMPLOYMENT_INCOME_DEDUCTION (薪資所得扣除級距)
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=1625000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].max-salary=2200000
 finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].multiplier=0.0
 finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[0].offset=740000
 
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].max-salary=1800000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].multiplier=0.4
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].offset=90000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].max-salary=3600000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].multiplier=0.3
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[1].offset=80000
 
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].max-salary=3600000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].multiplier=0.3
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].offset=270000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].max-salary=6600000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].multiplier=0.2
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[2].offset=440000
 
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].max-salary=6600000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].multiplier=0.2
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].offset=630000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].max-salary=8500000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].multiplier=0.1
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[3].offset=1100000
 
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].max-salary=8500000
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].multiplier=0.1
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].offset=1290000
-
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[5].max-salary=9223372036854775807
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[5].multiplier=0.0
-finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[5].offset=2140000
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].max-salary=9223372036854775807
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].multiplier=0.0
+finance-hub.tax-rule.brackets.EMPLOYMENT_INCOME_DEDUCTION[4].offset=1950000
 
 # Tax Brackets: INCOME_TAX_RATE (所得稅率級距)
 finance-hub.tax-rule.brackets.INCOME_TAX_RATE[0].max-salary=1950000
diff --git a/finance-hub-backend/todo/pending fix/issue-102.md b/finance-hub-backend/todo/pending fix/issue-102.md
index 761b6835..40f99b8a 100644
--- a/finance-hub-backend/todo/pending fix/issue-102.md
+++ b/finance-hub-backend/todo/pending fix/issue-102.md
@@ -39,3 +39,43 @@ High（直接影響每一次模擬計算出來的稅金與可支配所得，方
 
 ## 建議修正
 把 `application.properties` 跟 `application-h2.properties` 兩份設定檔裡，四個區間的加碼金額分別修正為80,000、440,000、1,100,000、1,950,000；並移除1,625,000~1,800,000這個目前對不上任何官方公式的過渡區間，讓最低那一段的固定金額門檻，直接接續到1,800,001以後的分段公式。這是單純的數字校正，不是需要討論方向的設計選擇。
+
+---
+
+# Issue 102 修復總結報告
+
+## 修復日期
+2026-09-30
+
+## 法律查證與成因分析
+1. **加碼金額多扣 19 萬日圓屬實**：
+   - 系統先前在中高所得區間（360萬以上及850萬以上上限）之加碼金額確實比日本國稅廳法定公式多算了 19 萬日圓（630,000 vs 440,000、1,290,000 vs 1,100,000、2,140,000 vs 1,950,000）。
+   - **成因**：2026 年（令和8年）稅改將最低保障額從 55 萬提高到 74 萬（+19 萬），先前設定時誤將舊表所有級距 offset 全體無差別 +19 萬（包括將乘數 0.4、加碼 -10 萬的舊區間誤算為 +9 萬）。
+
+2. **2026 年（令和8年）最新法定級距劃分之校正**：
+   - Issue 102 原報告建議「將第一段門檻設為 1,800,000，其後直接接續 30%+8萬」，但查證日本國稅廳（国税庁 No.1410 給与所得控除）令和8年（2026年）最新規定發現：
+     - 最低保障額調高至 74 萬的同時，**第一段固定金額之適用上限已同步推移至 2,200,000 円**。
+     - $2,200,000 \times 30\% + 80,000 = 740,000$，正好與固定額平滑無縫接軌。
+     - 若依原報告設於 180 萬，會導致年收 180~220 萬使用者扣除額出現「斷崖式驟降」（181 萬年收之扣除額跌至 62.3 萬），嚴重少扣。
+   - 因此本次修復採用**符合 2026 令和8年法定標準的 5 級距架構**（220萬以下一律固定 74 萬，其後各級距無縫銜接）。
+
+## 修改檔案清單
+1. `finance-hub/finance-hub-backend/hub/src/main/resources/application.properties`
+2. `finance-hub/finance-hub-backend/hub/src/main/resources/application-h2.properties`
+3. `finance-hub/.env.base`
+4. `finance-hub/.env.dev`
+5. `finance-hub/.env.prod`
+
+## 修正後級距表（2026 令和8年官方正式標準）
+| 級距索引 | 收入上限（max-salary） | 乘數（multiplier） | 加碼金額（offset） | 說明 |
+|---|---|---|---|---|
+| `[0]` | 2,200,000 | 0.0 | 740,000 | 最低保障額（2026 最新法定門檻） |
+| `[1]` | 3,600,000 | 0.3 | 80,000 | 收入 × 30% + 8 萬（220 萬處平滑銜接 74 萬） |
+| `[2]` | 6,600,000 | 0.2 | 440,000 | 收入 × 20% + 44 萬（校正原 63 萬） |
+| `[3]` | 8,500,000 | 0.1 | 1,100,000 | 收入 × 10% + 110 萬（校正原 129 萬） |
+| `[4]` | 9223372036854775807 | 0.0 | 1,950,000 | 法定控除上限 195 萬（校正原 214 萬） |
+*(原 `[5]` 冗餘級距已全數移除)*
+
+## 效益
+- 徹底消除中高收入者每人每年 19 萬日圓之應稅所得虛報扣除，確保長期模擬之稅金與可支配現金流真實準確。
+- 正確對齊 2026 年（令和8年）之 220 萬門檻與 74 萬最低保障額，避免分段點斷崖少算扣除額的問題。
 edward@Edwards-MacBook-Pro   ~/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend     main   INSERT                                                                                                                                                                              10006  12.56G   5.81    17:13:37  