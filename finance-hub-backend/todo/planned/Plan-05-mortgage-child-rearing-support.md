# Plan 05：支援 2024/2026 年後住宅貸款扣除的「子育て世帯・若者夫婦世帯」加碼條件

## 嚴重程度
Low (功能強化，目前的保守估計不會導致破產模擬出現風險)

## 背景
根據日本 2024 年 (令和6年度) 與 2026 年 (令和8年度) 的稅制改正，住宅ローン控除 (住宅貸款扣除) 針對「子育て世帯 (育兒家庭)」與「若者夫婦世帯 (年輕夫婦家庭)」提供了借入限度額的上乘措施 (加碼)。

## 現狀
目前的系統架構中，`MortgageTaxDeductionStrategy` 介面（如 `calculateMaxAmount`）只接收 `ResidenceType` 與 `PropertyType` 兩個與房屋本身相關的參數。
雖然系統的 `UserProfile` 實際上已經包含了 `List<ChildProfile> children` (小孩資訊)，可能也能透過 `Dependent` 資訊推算配偶年齡，但這些資料目前並沒有辦法傳遞到 `MortgageTaxDeductionStrategy` 內部進行判斷。這導致 2024 年與 2026 年以後的稅務策略，只能 fallback 到一般情況下的保守上限，無法精準計算育兒家庭/年輕夫婦的實際退稅金額。

## 需求說明
為了能精準模擬育兒家庭的退稅金額，我們需要對 `MortgageTaxDeductionStrategy` 進行重構，讓策略能存取使用者的家庭背景資訊，以判斷是否符合加碼資格：

1. **定義資格判斷邏輯**：
   - **子育て世帯**：有未滿 19 歲的扶養親屬 (可由 `UserProfile` 的 `children` 列表計算)。
   - **若者夫婦世帯**：配偶年齡在 39 歲以下 (需確認 `familyMembers` 或 `Dependent` 內是否包含配偶年齡/出生年月日資訊)。
2. **重構 Strategy 介面**：
   - 修改 `MortgageTaxDeductionStrategy.calculateMaxAmount` 等方法，將 `UserProfile` (或獨立出的家庭狀態 DTO) 作為參數傳入。
   - 確保舊有年份的策略 (如 `Pre2022TaxStrategy`, `Years2022To2023TaxStrategy`) 能相容新的方法簽章 (直接忽略新參數即可)。
3. **實作 2024/2026 年後的加碼邏輯**：
   - 根據法規，針對符合資格的家庭，調高對應住宅類型 (如 ZEH, 長期優良住宅等) 的借入限度額。

## 建議修改範圍
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/strategy/MortgageTaxDeductionStrategy.java`
- 所有實作 `MortgageTaxDeductionStrategy` 的現有策略類別。
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/mortgage/MortgageRecordServiceImpl.java` (呼叫端)
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/MortgageCalculator.java` (呼叫端)
