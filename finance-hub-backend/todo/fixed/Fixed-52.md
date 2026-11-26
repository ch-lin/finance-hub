# Fixed-52：過渡年年末調整後，`estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance` 顯示負值時，前端沒有任何視覺區分或說明，使用者會誤以為數字算錯

## 相關檔案與 Package 對照表 (Files Involved)

| Class / 檔案 | 專案 | 檔案路徑 | 是否需要修改 |
|---|---|---|---|
| `AssetSimulationTable`（前端） | `hub-ui` | `hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx` | 是，核心修正 |
| `AssetSimulationYearlyDto`（前端型別） | `hub-ui` | `hub-ui/src/app/finance/simulator/types.ts` | 否，僅供對照欄位定義（14-17 行） |
| `Tooltip`/`TooltipProvider`/`TooltipTrigger`/`TooltipContent`（既有共用元件） | `hub-ui` | `hub-ui/src/components/ui/tooltip.tsx` | 否，直接沿用既有元件 |
| `SalaryTable`（既有「滑鼠移上去顯示明細」範例） | `hub-ui` | `hub-ui/src/app/finance/income-expenses/salaries/components/salary-table.tsx`（94-135 行） | 否，僅供對照既有 UX pattern |
| `YearEndAdjustmentEngine.calculateYearEndAdjustment` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.tax` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/tax/YearEndAdjustmentEngine.java` | 否，僅供對照「為什麼會是負值」的計算來源 |
| `YearlySimulationPipeline.applyYearEndAdjustment` | `ch.lin.finance.hub.backend.api.app.service.simulation.core.pipeline` | `hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java`（225-248 行） | 否，本 Issue 純前端顯示層修正，後端不需變更 |

**這次修正只需要改前端 `asset-simulation-table.tsx` 一個檔案，不涉及後端計算邏輯。**

## 現況說明 (Current State)

過渡年（模擬第一年，`age == initialAge`）時，`YearlySimulationPipeline.applyYearEndAdjustment`（[YearlySimulationPipeline.java:240-242](../../hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/pipeline/YearlySimulationPipeline.java:240)）會把 `estimatedIncomeTax`/`estimatedInhabitantTax`/`estimatedSocialInsurance` 三個欄位改寫成「剩餘期間應繳」基準：

```java
state.setEstimatedIncomeTax(adj.remainingIncomeTax());
state.setEstimatedInhabitantTax(adj.remainingInhabitantTax());
state.setEstimatedSocialInsurance(adj.remainingSocialInsurance());
```

只要使用者今年的實際 YTD 預扣稅金／社保，超過模擬時點推算出的「全年理論應繳」（常見原因：生命保險費/地震保険料控除、iDeCo/DC 提繳扣除、房貸控除等只在年末調整才反映的扣除額），這三個值就會是**負數**，代表「今年公司年末調整會退這筆錢給使用者」——這是預期內、方向正確的計算結果（本專案已確認過，相關討論見 [Fixed-46.md](Fixed-46.md) 的「重要警告」段落）。

但前端 [asset-simulation-table.tsx:54-56](../../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx:54) 目前對這三個欄位的渲染方式，跟表格裡其他一般數字完全一樣，沒有任何區分：

```tsx
<TableCell className="text-right">{row.estimatedSocialInsurance ? formatCurrency(row.estimatedSocialInsurance) : "-"}</TableCell>
<TableCell className="text-right">{row.estimatedIncomeTax ? formatCurrency(row.estimatedIncomeTax) : "-"}</TableCell>
<TableCell className="text-right">{row.estimatedInhabitantTax ? formatCurrency(row.estimatedInhabitantTax) : "-"}</TableCell>
```

`formatCurrency`（同檔案第 18-20 行）只是單純套用 `Intl.NumberFormat`，负数會直接印成「-¥123,456」這種格式，沒有變色、沒有任何說明文字或圖示。

## 問題原因 (Cause)

對一般使用者而言，「所得稅」「住民稅」「社會保險」這三個名詞直覺上就應該是正數（要繳的錢），尤其是同一列裡 `Salary`（[asset-simulation-table.tsx:53](../../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx:53)）也是正數的情況下，看到稅金欄位變成負數，最直覺的反應是「這是不是算錯了」，而不是「這是年末調整退稅」——這正是使用者自己在跟 Gemini 討論時提出的實際困惑（見 [Fixed-46.md](Fixed-46.md) 的處置紀錄）。

這不是計算邏輯的問題（計算本身已確認正確，不建議修改），純粹是**呈現層缺少足夠的上下文**：同一個數字，對懂得年末調整機制的人來說是好消息，對不知道這個機制的人來說看起來像是 bug。表格目前的呈現方式沒有給使用者任何機會理解這個差異。

## 建議修正方向 (Suggested Fix)

1. **變色**：這三個欄位若為負值，套用跟同表格裡 `netSavings` 欄位（[asset-simulation-table.tsx:59-61](../../../../hub-ui/hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx:59)）一致的既有配色慣例——但意義要反過來：`netSavings` 是「越負越糟」用 `text-destructive`，這裡的稅金欄位是「越負越好（代表退稅）」，應該用正向色（例如 `text-green-600`，跟本檔案既有的 `netSavings >= 0` 分支同色），避免使用者用「看到紅色就是壞消息」的直覺誤判。

2. **滑鼠移到上方顯示說明**：沿用本專案在 [salary-table.tsx:94-135](../../../../hub-ui/hub-ui/src/app/finance/income-expenses/salaries/components/salary-table.tsx:94) 已經在用的 `TooltipProvider`/`Tooltip`/`TooltipTrigger`/`TooltipContent` 元件與 `cursor-help underline decoration-dotted underline-offset-4` 視覺慣例，只在數值為負時才顯示 tooltip（正值是正常應繳金額，不需要額外說明），內容說明「這是年末調整（或確定申告)的退稅，不是計算錯誤」。

3. 只調整這三個欄位的呈現方式，不動 `netIncome` 欄位——`netIncome` 欄位本身的口徑一致性問題屬於 [Fixed-46.md](Fixed-46.md) 的範圍，兩者是同一批欄位但不同維度的問題（Fixed-46 是「後端回傳的數字口徑對不對」，本 Issue 是「前端怎麼呈現一個已經確認正確的數字」），修正時互相獨立，不需要合併處理。

## 最小範例程式碼 (Sample Code)

```tsx
// 檔案: hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx
// 新增 import（沿用 salary-table.tsx 已經在用的元件）：
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";

// 新增一個小的渲染輔助函式，取代原本三行幾乎重複的 TableCell：
function TaxAdjustmentCell({ value }: { value?: number }) {
  if (!value) return <TableCell className="text-right">-</TableCell>;
  if (value >= 0) {
    return <TableCell className="text-right">{formatCurrency(value)}</TableCell>;
  }
  return (
    <TableCell className="text-right text-green-600">
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger className="cursor-help underline decoration-dotted underline-offset-4">
            {formatCurrency(value)}
          </TooltipTrigger>
          <TooltipContent className="max-w-[300px] p-3">
            今年的預扣稅金已超過全年理論應繳金額（常見原因：保險費／iDeCo・DC提繳／房貸控除等只在年末調整或確定申告才會反映的扣除額），
            負數代表年末調整（或次年確定申告）會退這筆錢給你，不是計算錯誤。
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </TableCell>
  );
}

// 原本第 54-56 行替換成：
<TaxAdjustmentCell value={row.estimatedSocialInsurance} />
<TaxAdjustmentCell value={row.estimatedIncomeTax} />
<TaxAdjustmentCell value={row.estimatedInhabitantTax} />
```

---

## 修正報告 (Resolution Report)
**修復狀態：已完成**

**實作細節：**
針對「過渡年（模擬第一年）因年末調整導致稅金欄位呈現負數，易造成使用者誤解為計算錯誤」的問題，實作了純前端顯示層的優化：
1. **封裝 `TaxAdjustmentCell` 組件**：將原本 `asset-simulation-table.tsx` 內直接渲染的三個稅金/社保欄位抽離，建立可重用的功能型元件。
2. **顏色區隔與 Tooltip 提示**：
   - 數值 `>= 0` 時維持原始樣式。
   - 數值 `< 0` 時，自動將字體顏色轉為綠色 (`text-green-600`) 以代表退稅的正面含意，並透過 `@/components/ui/tooltip` 元件加上底線提示。
   - 滑鼠懸停時顯示說明文字，明確告知使用者：「負數代表年末調整（或次年確定申告）會退這筆錢給你，不是計算錯誤。」

**結果**：在不變更任何後端原始計算與口徑的前提下，有效改善了前端表格的易讀性與 UX，使用者將能輕易理解此負數的意義，消除了對系統正確性的疑慮。
