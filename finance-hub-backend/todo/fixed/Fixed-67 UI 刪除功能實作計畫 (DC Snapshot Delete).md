# UI 刪除功能實作計畫 (DC Snapshot Delete)

## 確認使用的 API
我們將使用 **Date** 作為刪除條件，呼叫 `DELETE /api/finance/assets/dc?recordDate=YYYY-MM-DD`。
- 因為 UI 上是「以日期作為群組」來顯示 (一個日期可能同時包含 iDeCo 與 Corporate DC 兩筆主表紀錄)，使用 Date 可以一次發送一個 API 請求就乾淨地刪除該日期的所有紀錄。這非常符合使用者在 UI 上的認知 (「我要刪除這天的快照」)。

---

## Proposed Changes

### UI 元件修改
我們將在原本選擇日期的 Dropdown 旁邊加入一個「垃圾桶」按鈕，並使用專案內建的 Shadcn UI `AlertDialog` 來實作現代化的確認視窗，保持 UX 與視覺的一致性。

#### [MODIFY] [dc-snapshot-viewer.tsx](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/hub-ui/hub-ui/src/app/finance/portfolios/defined-contribution/components/dc-snapshot-viewer.tsx)
- 匯入現代化元件：
  - `Button` from `@/components/ui/button`
  - `Trash2` (lucide-react) 作為刪除 Icon
  - `AlertDialog` 相關元件 from `@/components/ui/alert-dialog`
  - `toast` from `sonner`
- 狀態管理：
  - 加入 `isDeleting` 狀態，在發送刪除 API 時顯示 Loading 狀態並停用按鈕。
- 實作 `handleDeleteSnapshot`：
  - 使用 `fetch('/api/finance/assets/dc?recordDate=' + selectedDate, { method: 'DELETE' })` 呼叫剛剛做好的 endpoint。
  - 刪除成功後顯示 `toast.success`，呼叫 `mutate()` 重新抓取 SWR 資料，並將 `selectedDate` 清空 (讓 `useEffect` 自動跳到下一筆)。
- UI 結構更新：
  - 在 Date Select 旁邊加入 `AlertDialog` 結構。
  - Trigger 為一個 `variant="ghost"` 的 `Button`，內部包含 `Trash2` Icon，文字為紅色 (`text-destructive`)。
  - 彈出視窗包含警示文字：「確定要刪除 {selectedDate} 的所有 DC 快照嗎？這項操作無法復原。」

---

## Verification Plan

### Manual Verification
1. 進入 Defined Contribution 頁面。
2. 選擇某個日期的快照，點擊旁邊的紅色垃圾桶按鈕。
3. 彈出確認視窗，點擊「確定」。
4. 確認該日期的資料從畫面上消失，並跳出成功通知。
5. 檢查資料庫確認該 recordDate 的主表與明細表皆被正確刪除。
