# Plan 20：支援中古住宅（既存住宅）之房貸稅制借入限度額與扣除期間模型

## 嚴重程度
Low（專案目前核心財務模擬為新築注文住宅；中古住宅為後續功能擴充，不影響當前新築模擬）。

## 背景
依日本國稅廳（[No.1214 既存住宅を取得した場合](https://www.nta.go.jp/taxes/shiraberu/taxanswer/shotoku/1214.htm)）與國土交通省房貸減稅規範：
1. **法定扣除期間**：
   - 新築住宅：原則為 **13 年**（一般住宅除外）。
   - 中古住宅（既存住宅）：法定扣除期間統一為 **10 年**。
2. **借入限度額（令和 4 年起適用之標準）**：
   - 長期優良住宅 / 低炭素住宅：**3,000 萬円**（每年扣除上限 210,000 円）
   - ZEH 水準節能住宅：**3,000 萬円**（每年扣除上限 210,000 円）
   - 省エネ基準適合住宅：**3,000 萬円**（每年扣除上限 210,000 円）
   - 一般住宅（その他の住宅）：**2,000 萬円**（每年扣除上限 140,000 円）
   > *註：新築住宅之一般住宅自 2024 年起原則上限為 0 円，但中古住宅之一般住宅仍依法享有 2,000 萬円之借入限度額。*

## 現狀
目前系統架構中：
1. `PropertyType` 已定義 `NEW_DETACHED_HOUSE`, `USED_DETACHED_HOUSE`, `NEW_APARTMENT`, `USED_APARTMENT`, `OTHER`。
2. `MortgageTaxDeductionStrategy.calculateDurationYears(PropertyType propertyType)` 雖已將非新築判定為 10 年，但 **`calculateMaxAmount(ResidenceType type)` 僅接收 `ResidenceType`**，未接收房屋性質（新築 vs 中古）。
3. 現有各策略類別（如 `Years2026OnwardsTaxStrategy`）在計算上限金額時，全部寫死新築住宅標準（4,500萬 / 3,500萬 / 2,000萬 / 0萬）。若使用者選擇中古住宅：
   - 認定住宅（長期優良等）上限會被高估為新築的 4,500 萬或 3,500 萬（法定中古上限僅 3,000 萬）。
   - 一般住宅上限會被低估為新築的 0 円（法定中古一般住宅仍有 2,000 萬額度）。

## 需求說明
1. **重構 Strategy 介面方法**：
   - 修改 `MortgageTaxDeductionStrategy.calculateMaxAmount` 方法簽章，加入 `PropertyType propertyType` 參數（亦可配合 [Plan-05](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/todo/planned/Plan-05-mortgage-child-rearing-support.md) 整合進專用 Context 物件）。
2. **實作中古住宅上限判斷邏輯**：
   - 判斷若為 `USED_DETACHED_HOUSE` 或 `USED_APARTMENT`，套用中古住宅法定借入限度額（認定住宅 3,000 萬 / 一般住宅 2,000 萬）。
   - 若為新築住宅，維持既有各年份策略之新築上限。
3. **呼叫端與模擬器串接**：
   - `MortgageRecordServiceImpl` 與 `MortgageCalculator` 於計算扣除額上限時，傳入記錄對應的 `propertyType`。

## 建議修改範圍
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/MortgageTaxDeductionStrategy.java`
- 所有實作 `MortgageTaxDeductionStrategy` 之年份策略類別（`Years2026OnwardsTaxStrategy` 等）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java`
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java`
