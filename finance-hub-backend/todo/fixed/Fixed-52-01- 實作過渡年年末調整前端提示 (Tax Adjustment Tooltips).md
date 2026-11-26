# Fixed-52: 實作過渡年年末調整前端提示 (Tax Adjustment Tooltips)

這份計畫將實作 Fixed-52 中的純前端顯示層優化，解決使用者看到稅金為負數時產生的「系統算錯」疑慮。當模擬第一年的預扣稅超過全年理論應繳時，系統會正確產生負數稅金（代表退稅），我們將在表格中用綠色正向文字顯示，並附上懸浮提示（Tooltip）解釋原因。

## Proposed Changes

### [MODIFY] asset-simulation-table.tsx
(位於 `hub-ui/hub-ui/src/app/finance/simulator/simulation/components/asset-simulation-table.tsx`)

#### 1. 引入 Tooltip 相關元件
從 `@/components/ui/tooltip` 引入既有的共用元件：
```tsx
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
```

#### 2. 新增輔助渲染元件 `TaxAdjustmentCell`
在檔案上方新增一個封裝好的 Cell 元件，統一處理「正值正常顯示、負值變色加 Tooltip」的邏輯：
```tsx
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
          <TooltipContent className="max-w-[300px] p-3 text-sm">
            今年的預扣稅金已超過全年理論應繳金額（常見原因：保險費／iDeCo・DC提繳／房貸控除等只在年末調整或確定申告才會反映的扣除額），
            負數代表年末調整（或次年確定申告）會退這筆錢給你，不是計算錯誤。
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </TableCell>
  );
}
```

#### 3. 替換表格中的 `TableCell`
將 `<TableBody>` 中針對 `estimatedSocialInsurance`、`estimatedIncomeTax`、`estimatedInhabitantTax` 原本直接渲染的三行程式碼：
```tsx
<TableCell className="text-right">{row.estimatedSocialInsurance ? formatCurrency(row.estimatedSocialInsurance) : "-"}</TableCell>
<TableCell className="text-right">{row.estimatedIncomeTax ? formatCurrency(row.estimatedIncomeTax) : "-"}</TableCell>
<TableCell className="text-right">{row.estimatedInhabitantTax ? formatCurrency(row.estimatedInhabitantTax) : "-"}</TableCell>
```
改為使用新的元件：
```tsx
<TaxAdjustmentCell value={row.estimatedSocialInsurance} />
<TaxAdjustmentCell value={row.estimatedIncomeTax} />
<TaxAdjustmentCell value={row.estimatedInhabitantTax} />
```

## User Review Required
這是一個單純針對前端渲染元件的修改，沒有修改任何後端資料拋轉邏輯。若您同意這個實作方向，請確認以授權我開始編輯程式碼。
