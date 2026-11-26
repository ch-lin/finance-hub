# 修正 Fixed 63：退休年金社會保險費估算比例註解修正

本實作計畫旨在解決 Fixed 63 所指出的問題：
`ActiveTaxCalculationEngine.java` 第 324 行中，關於退休者社會保險費的估算註解，寫著「approx 5%」，但實際程式碼使用的比例是 `0.095`（9.5%）。這兩者互相矛盾。

## User Review Required

根據查證，實務上 9.5% 是相對合理的粗估值，因此我們不會更動計算邏輯，只會將註解文字修正為與程式碼一致。請確認以下修改。

## Open Questions

無。

## Proposed Changes

---

### [MODIFY] [ActiveTaxCalculationEngine.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/ActiveTaxCalculationEngine.java)
- 找到第 324 行附近的註解：
  `// Estimate Social Insurance for retirees (approx 5% of pension income)`
- 將其修改為：
  `// Estimate Social Insurance for retirees (approx 9.5% of pension income)`

## Verification Plan

### Automated Tests
- 此為純註解修改，不影響任何業務邏輯或現有測試，僅需確保編譯通過即可。

### Manual Verification
- 確認 `ActiveTaxCalculationEngine.java` 檔案內容正確更新且無語法錯誤。
