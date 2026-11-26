# Fixed 64：年金設定畫面「新增設定檔」時，滿額基準欄位還是帶入已確認過期的81萬6千元，跟後端目前的正確預設值（83萬1700元）不一致

## 嚴重程度
Medium——這是issue-24（老年基礎年金滿額過期）修好之後，新的年金設定畫面裡冒出的新落差。如果使用者透過畫面新增一個設定檔、沒留意到把數字改掉，就會不小心把系統又帶回issue-24本來要修正掉的那個過期數字。

## 相關檔案
- [use-pension-rule-configs.ts:8](hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts:8)（表單驗證規則裡，`maxBasicPensionAmount`欄位的預設值寫的是`816000`）
- [use-pension-rule-configs.ts:86-98](hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-pension-rule-configs.ts:86-98)（使用者按「新增設定檔」按鈕時，實際帶入表單的年金設定物件，`maxBasicPensionAmount`同樣是`816000`）
- [pension-rule-config-panel.tsx:56](hub-ui/hub-ui/src/app/finance/configs/general/components/pension-rule-config-panel.tsx:56)（表單元件自己的預設值，同樣是`816000`）
- [pension-rule-config-panel.tsx:149](hub-ui/hub-ui/src/app/finance/configs/general/components/pension-rule-config-panel.tsx:149)（欄位輸入框的提示文字寫著「例如816000」）

## 現狀
Issue-24確認了老年基礎年金滿額寫死81萬6千元（令和6年度／2024年度的舊數字）已經過期，真實數字令和7年度（2025年度）起已經上調為83萬1700元，並依此修正了後端：後端`PensionRuleDefaultProperties`目前的預設值已經正確設定成`831700`。

但前端「年金設定」畫面裡，使用者按下「新增設定檔」時，畫面上表單會預先帶入一組數字讓使用者參考／修改，這組預先帶入的滿額基準金額，用的卻還是舊的`816000`，跟後端目前正確的`831700`不一致。欄位旁邊的輸入提示文字也還是寫「例如816000」。也就是說，使用者如果透過畫面新增一個新的年金設定檔，沒有留意到、沒有手動把這個欄位改成正確數字，儲存下去的新設定檔就會是issue-24本來就要修正掉的那個過期數字。

## 原因
Issue-24修正後端常數時，前端這幾個沿用了舊數字當「新增設定檔的預設值」跟「輸入框提示文字」的地方，沒有一併更新成新的`831700`。

## 建議修正
把上面列出的4個地方全部從`816000`改成`831700`，讓前端的預設值跟提示文字，跟後端目前正確的預設值保持一致。這是純粹的數字修正，不涉及邏輯改動。
