# Fixed-43 實作計畫 (Implementation Plan)

## 問題與現狀確認 (Analysis)
我剛才看過程式碼，確實如 Fixed-43 所述：
1. **標準不一**：`DefinedContributionContextBuilder` 在起始化時，使用真實日期算年資 (`Period.between(...)`)，但 `YearlySimulationPipeline` 卻在跑迴圈時使用 `+1` 累加。
2. **判斷條件錯誤**：在 `YearlySimulationPipeline` 中，累加 DC 年資的條件是 `contributions.annualDcMatchingContribution() > 0`。這代表如果員工沒有自行加碼 (Matching)，只有公司出資，那他的 DC 年資就會在迴圈中被凍結，導致未來的免稅額被嚴重低估！
3. **沒有處理覆寫值的一致性 (Fixed-51)**：目前如果覆寫開關打開但數值是 `null`，會因為三元運算子被轉成 `0`，這是錯的。

Claude 提議的修正方向**非常漂亮且一勞永逸**。將 `enrollmentDate` 存入 `SimulationContext` 並在迴圈中每年重算，不但解決了「公司出資不算年資」的 bug，也杜絕了未來任何 `+1` 累加可能帶來的誤差複利風險。同時，藉由把使用者覆寫值反推回「假的 enrollmentDate」，連沒有連線真實帳戶的 Profile-setting 使用者也能享受到正確的逐年推算。

## Proposed Changes

---

### Simulation Context (Data Model)

#### [MODIFY] [`SimulationContext.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/model/SimulationContext.java)
新增 `dcEnrollmentDate` 與 `idecoEnrollmentDate`：

```java
// 新增欄位（放在現有 dcContributionYears/idecoContributionYears 附近）
private LocalDate dcEnrollmentDate;
private LocalDate idecoEnrollmentDate;
```

---

### Context Initialization (Data Builder)

#### [MODIFY] [`DefinedContributionContextBuilder.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/factory/DefinedContributionContextBuilder.java)
重構 DC 與 iDeCo 各自的兩種分支（有 Snapshot、無 Snapshot）：

**有 accountSnapshot 時：**
```java
Integer dcOverrideVal = (dcSettingsOpt.isPresent()
        && Boolean.TRUE.equals(dcSettingsOpt.get().getIsDcContributionYearsOverridden()))
                ? dcSettingsOpt.get().getDcContributionYearsOverride()
                : null;

LocalDate resolvedDcEnrollmentDate = dcOverrideVal != null
        ? LocalDate.now().minusYears(dcOverrideVal)
        : accountSnapshot.getEnrollmentDate();

if (resolvedDcEnrollmentDate != null) {
    context.setDcEnrollmentDate(resolvedDcEnrollmentDate);
    context.setDcContributionYears(
            Period.between(resolvedDcEnrollmentDate, LocalDate.now()).getYears());
}
```

**無 accountSnapshot 時：**
```java
if (Boolean.TRUE.equals(dcSettingsOpt.get().getIsDcContributionYearsOverridden())) {
    Integer overrideVal = dcSettingsOpt.get().getDcContributionYearsOverride();
    if (overrideVal != null) {
        context.setDcEnrollmentDate(LocalDate.now().minusYears(overrideVal));
        context.setDcContributionYears(overrideVal);
    }
}
```
*(iDeCo 的邏輯也比照辦理)*

---

### Simulation Loop (Core Logic)

#### [MODIFY] [`YearlySimulationPipeline.java`](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java)
移除舊的 `+1` 邏輯，改用 `EnrollmentDate` 每年重算，並修正提撥條件為總提繳額：

```java
if (context.getDcEnrollmentDate() != null) {
    if (contributions.annualDcContribution() > 0) {
        context.setDcContributionYears(
                Period.between(context.getDcEnrollmentDate(), LocalDate.of(context.getYear(), 12, 31)).getYears());
    }
} else if (contributions.annualDcContribution() > 0) {
    context.setDcContributionYears(context.getDcContributionYears() + 1);
}

if (context.getIdecoEnrollmentDate() != null) {
    if (contributions.annualIdecoContribution() > 0) {
        context.setIdecoContributionYears(
                Period.between(context.getIdecoEnrollmentDate(), LocalDate.of(context.getYear(), 12, 31)).getYears());
    }
} else if (contributions.annualIdecoContribution() > 0) {
    context.setIdecoContributionYears(context.getIdecoContributionYears() + 1);
}
```

## Verification Plan
1. **Automated Tests**: 執行 `mvn clean install` 確認系統編譯通過且現有測試無損。
2. **Manual Review**: 檢視修改後的程式碼邏輯是否如上述精準一致。
