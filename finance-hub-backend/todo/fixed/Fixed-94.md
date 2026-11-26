# Fixed 94：2026年起房貸稅額扣抵，「節能住宅」類別的扣除上限金額沿用舊制度數字，比2026年新制多算了70,000元（原question-04）

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2026OnwardsTaxStrategy.java`（第46-56行：`calculateMaxAmount()`，依房屋類別回傳不同的扣除上限金額；第53行是本問題的位置）
- `finance-hub/finance-hub-backend/hub-app/src/test/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/Years2026OnwardsTaxStrategyTest.java`（第64行：對應這個上限金額的測試斷言，跟程式碼犯了同一個錯）

## 現狀
`Years2026OnwardsTaxStrategy.java`（依`isApplicable()`第36-38行，適用範圍是2026年1月1日以後入住的房貸，也就是現在正在生效的規則）第53行：

```java
case ENERGY_SAVING -> 210_000L; // 30m * 0.7%
```

「節能住宅（省エネ基準適合住宅）」這個類別的房貸扣除上限，目前寫死210,000元（換算回去等於是「3,000萬元貸款餘額 × 0.7%」）。測試檔案第64行也是斷言210,000元，跟程式碼一致，但兩者都是同一個錯誤數字。

## 原因
經查證確認，2026年起的新制度裡，「節能住宅（一般節能標準，非ZEH、非認定長期優良住宅）」這個類別的貸款餘額上限，已經從3,000萬元下修為2,000萬元。換算成扣除上限應該是「2,000萬元 × 0.7% = 140,000元」，不是現在的210,000元。其他兩個類別（認定長期優良住宅315,000元、ZEH住宅245,000元）維持不變，沒有問題。

210,000元是2024-2025年度舊制度的數字，看起來是新增2026年起這個類別時，忘了把這個類別的貸款餘額上限跟著新制度一起下修。

## 建議修正
把第53行的`210_000L`改成`140_000L`，comment也一併從「30m * 0.7%」改成「20m * 0.7%」；同步把測試檔案`Years2026OnwardsTaxStrategyTest.java`第64行的`210_000L`改成`140_000L`。

**狀態: 已於 2026-09-19 修復完成**
