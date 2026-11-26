# Fixed 62-07：稅制設定改成可設定值之後，「住民稅稅率」分類名稱前後端沒對齊、「無上限」勾選會讓整張級距表存不進去

## 嚴重程度
High——住民稅稅率的級距表功能完全用不起來；另外，三張級距表（薪資所得扣除、所得稅率、住民稅率）只要使用者想設定「最高一級沒有金額上限」，儲存都會失敗。也就是說，Fixed-62原本要解決的問題（基礎控除等稅制數字過期未隨年度更新），在住民稅那部分實質上還沒有真正解決——因為新設定畫面本身用不起來，使用者沒辦法真的把新數字存進去。

## 相關檔案
- [tax-bracket-editor.tsx:11](hub-ui/hub-ui/src/app/finance/configs/general/components/tax-bracket-editor.tsx:11)（前端分類名稱的型別定義，用的是`RESIDENT_TAX_RATE`）
- [tax-rule-config-panel.tsx:209](hub-ui/hub-ui/src/app/finance/configs/general/components/tax-rule-config-panel.tsx:209)（實際呼叫級距表編輯器元件時，傳入的分類字串也是`RESIDENT_TAX_RATE`）
- [use-tax-rule-configs.ts:9](hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts:9)（前端表單驗證規則裡，允許的分類字串清單同樣是`RESIDENT_TAX_RATE`）
- [TaxBracketType.java:6](finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/config/tax/TaxBracketType.java:6)（後端實際定義的分類名稱是`INHABITANT_TAX_RATE`，跟前端三處都不一樣）
- [TaxBracketRequest.java:47-48](finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/config/dto/tax/TaxBracketRequest.java:47-48)（後端接收級距上限金額的欄位，標記成「不能是空值」）
- [tax-bracket-editor.tsx:26,96-104](hub-ui/hub-ui/src/app/finance/configs/general/components/tax-bracket-editor.tsx:26)（前端「無上限」勾選框的邏輯，勾選後會把這一級的上限金額設成空值）
- [use-tax-rule-configs.ts:131](hub-ui/hub-ui/src/app/finance/configs/general/hooks/use-tax-rule-configs.ts:131)（送出儲存請求前，把空的上限金額轉換成JSON的null傳給後端）

## 現狀
Fixed-62最後把基礎控除、薪資所得扣除級距、所得稅率級距、住民稅率級距這幾項稅制數字，從程式碼裡的寫死常數，改成可以在「稅制設定」畫面上直接設定、儲存的項目，大方向是對的。但重新核對目前實際的程式碼，發現這個新畫面裡還留著兩個會讓功能實際用不起來的問題：

1. **「住民稅稅率」這個分類，畫面跟後端各自取了不一樣的名字**：畫面上「稅制設定」頁面裡，負責顯示住民稅率級距表的那個區塊，程式碼裡不管是型別定義、表單驗證規則、還是實際傳給編輯器元件的字串，用的都是`RESIDENT_TAX_RATE`這個名字；但後端實際定義的分類名字是`INHABITANT_TAX_RATE`。前後端溝通用的就是這串文字本身（不是數字代號），只要文字兜不起來，後端就完全不認得這是哪一種分類——會讓「住民稅稅率」這張級距表整個沒辦法正常運作（存不進去，或是讀回來的時候對不上、顯示空白）。

2. **「無上限」勾選框會讓整張級距表存不進去**：三張級距表都有機會需要設定「最高一級沒有金額上限」（例如「所得稅率」級距表最上面那一級，理論上一定要有一個沒有上限的最高級距）。畫面上有個「無上限」的勾選框，勾了之後代表這一級沒有上限，前端會把這一級的上限金額傳成空值（JSON的`null`）。但後端接收這個欄位時，規則是「這個欄位一定要有值，不能是空的」——只要送出的資料裡有任何一級被標成「無上限」，後端就會整批拒絕，導致整張級距表都存不進去，不只是那一級失敗。

## 原因
1. 前端型別定義用`RESIDENT_TAX_RATE`（英文Resident），後端Enum定義用`INHABITANT_TAX_RATE`（英文Inhabitant）——兩者都是日文「住民税」常見的英文翻譯，但前後端各自取了不同的名字，沒有對齊。
2. 後端計算引擎那邊其實有處理「上限金額是空值」的邏輯（把空值當作「這一級沒有上限」），但最外層接收資料的驗證規則卻直接禁止這個欄位是空值，這兩層邏輯互相矛盾。

## 建議修正
1. 把前端三個地方的`RESIDENT_TAX_RATE`都改成跟後端一致的`INHABITANT_TAX_RATE`（型別定義、表單驗證規則、傳給編輯器元件的字串）。這是純粹改名字，不影響其他任何邏輯。
2. 把後端接收上限金額的欄位上，「不能是空值」這條驗證規則移除，讓它可以合法接受空值（因為空值在這裡本來就是有意義的合法值，代表「這一級沒有上限」，不是「使用者忘了填」）。
