# WONTFIX 24：幼稚園階段沒有獨立的`NONE`選項——判定不需要新增，維持用`HOME`表達「沒讀」

## 判定：維持現狀，不需修正（2026-09-30，使用者確認）

## 背景
`PreschoolTrack`這個enum（`finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/profile/child/PreschoolTrack.java`）只有`HOME`、`DAYCARE`、`INTL`三個選項，沒有`NONE`——程式碼自己的註解寫著「HOME代替NONE使用（零學費基準）」，跟其他四個階段（小學/國中/高中/大學，透過`inactiveStage`機制正確實作`NONE`跳過）不一致。

原始查證發現：這件事目前沒有造成實際計算錯誤，是因為`HOME`軌道的預設長度（36個月，滿3歲到滿6歲）恰好跟「完全沒讀幼稚園、直接從小學滿6歲入學」算出來的起算月份一致——這是設定數值巧合對齊，不是結構上真的等價；如果之後`HOME`軌道長度被調整，這個巧合前提就會被打破。

## 為何判定不需要新增`NONE`
使用者已經拍板決定：幼稚園要支援「分兩段、各自選軌道」（見finance-hub-backend/todo/pending fix/feature-requirement-02.md，例如K3在家、K4/K5去讀國際學校）。使用者確認：幼稚園「完全不讀」這件事，維持用`HOME`表達即可（在feature-requirement-02做出來之後，等同於「整段只設定第一段HOME、不設定第二段切換」），不需要額外新增一個獨立的`NONE`選項去重複做同一件事。

## 結論
維持現狀，不新增`PreschoolTrack.NONE`。幼稚園「沒讀」統一用`HOME`表達。原始提出的「`HOME`軌道長度巧合對齊、非結構上等價」這個潜在脆弱點，因為判定不新增`NONE`，也就不需要再處理——如果`HOME`軌道長度未來被調整，這個巧合前提確實可能被打破，但這屬於「調整全域設定值時需要注意的邊界情況」，不是需要現在解決的架構問題。

## 補充：相關但反過來的問題已獨立處理
小學/國中的`NONE`選項在日本法律上是否合理（義務教育階段選`NONE`等同監護人違反就學義務）——這是反過來的問題（義務教育階段是否該有`NONE`，而不是非義務教育階段是否該加`NONE`），已經拍板決定移除，獨立列為feature-requirement-03處理，不在本文件範圍內。
