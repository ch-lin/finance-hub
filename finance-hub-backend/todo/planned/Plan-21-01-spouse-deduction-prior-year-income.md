# Plan 21-01：配偶特別控除「前年所得」子題——併入Plan-21，open狀態

## 跟Plan-21的關係
這是[Plan-21](Plan-21-inhabitant-tax-prior-year-income-engine.md)（住民稅前年所得課稅時差機制，open狀態、等未來評估雙引擎架構）底下的子題，記錄配偶特別控除（question-120）這個模組的具體情況。處置狀態完全跟隨Plan-21的整體結論，這裡只記錄這個模組獨有的技術細節。

## 這份子題怎麼被發現的
在review question-119（扶養親屬所得門檻）、question-120（配偶特別控除）、question-121（特定親族特別控除）這三份文件的實作完成度時，發現一個三份文件之間的交互落差：question-119跟question-121都已經正確處理了「2028年（令和10年）給與所得控除74萬特例落日、回歸69萬本則」這個未來的法規變化，各自新增了對應2028年以後的第四時代策略類別；question-120的配偶特別控除沒有對應的第四時代策略。

## 相關檔案
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/spouse/SpouseDeductionStrategyFactory.java:48-56,67-75`（`getIncomeTaxStrategy(year)`跟`getInhabitantTaxStrategy(year)`，目前只定義了三個時代：`legacyStrategy`、`reiwa7Strategy`、`reiwa8Strategy`，2026年以後所有年份——不管是2028、2035還是2060——全部落在`else`分支，派發到同一個`reiwa8Strategy`）
- 對照：`finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/dependent/DependentDeductionStrategyFactory.java:53-63,78-88`（已確認這裡正確定義了四個時代，`Reiwa10OnwardsDependentDeductionStrategy`對應2028年以後，門檻整組往下位移50,000以反映特例落日）
- `finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/calculator/strategy/tax/deduction/spouse/Reiwa8SpouseDeductionStrategy.java`（目前2028年以後長線模擬會持續套用的這個策略，是建立在「74萬特例加成」之上的136萬/169萬/207萬門檻）

## 現狀與為何是問題
`Reiwa8SpouseDeductionStrategy`裡的門檻（136萬/169萬/207萬），是建立在2026-2027年給與所得控除74萬暫定加成之上算出來的。2028年（令和10年）起，這個74萬特例依法會落日回歸69萬本則，相關門檻理論上應該系統性下移（跟`DependentDeductionStrategyFactory`裡`Reiwa10OnwardsDependentDeductionStrategy`對應門檻下移50,000的處理方式一致）。但`SpouseDeductionStrategyFactory`對2028年以後完全沒有對應的第四時代策略，長線模擬（這個系統的核心使用情境是30~40年）會持續沿用2026-2027年的過渡性門檻，產生一個固定的系統性落差。

## 這是兩個獨立的問題疊在一起
1. **已查證的技術事實**：question-119/121的標準（74萬特例2028年落日、不可接受持續性偏差）確實沒有被追溯套用到question-120，這是三份文件之間拍板時間先後不一致造成的落差，是真實存在、獨立於Plan-21之外的問題，不管Plan-21最後怎麼決定，這個「少一個時代策略」的落差都存在。
2. **這個落差本身，剛好也命中Plan-21討論的「用今年所得、不管前年」這個更大的架構限制**——`SpouseDeductionEngine`查表用的所得基準，不管年份路由選中哪一個策略物件，都是「今年」的所得，不是「前一年」的。

## 處置
跟隨Plan-21的整體open狀態，這個子題目前不投入修正，等未來評估雙引擎架構時再回頭參考。但這裡要明確記錄：**即使不處理Plan-21那個更大的「前年所得」架構問題，`SpouseDeductionStrategyFactory`缺少對應2028年以後的第四時代策略，本身是一個獨立於Plan-21之外、相對單純（只是新增一個時代策略類別跟門檻位移計算，不涉及月級別重寫）的落差，技術上可以獨立評估是否要單獨修正，不需要等Plan-21的架構重寫才能處理。**（這個判斷留給使用者決定，這裡不代為拍板。）
