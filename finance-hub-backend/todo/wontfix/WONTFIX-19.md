# WONTFIX 19：Issue-40修正時，在API入口層整個丟棄baseOverrides，連帶讓「保留呼叫者夾帶的基金報酬率／房貸利率情境假設」機制永久失效——這個影響範圍是否可以接受

## 判定：維持方向A，不需修改（2026-09-18，使用者確認）
**理由**：GA最佳化的定位本來就是「完全交給系統跟資料庫的資料去決定」，畫面上本來就沒有開放讓使用者輸入基金報酬率／房貸利率情境假設這類複雜的個別覆寫設定——如果使用者想要手動指定這些假設值，語意上就該用Sandbox手動試算，不該用GA最佳化。讓GA接受這些覆寫值不會讓結果更好，只會讓這個本來設計成「自動找答案」的工具，被迫暴露一堆使用者摸不著頭緒、實際上對GA的搜尋邏輯毫無意義的個別設定，反而是UX上的倒退。因此`baseOverrides`在API入口就整個被丟棄，是刻意且正確的設計，不需要修改成方向B。

---

## 背景
Issue-40原本要解決的問題，範圍只限定在「房貸提前還款」這一個維度：不論最佳化引擎有沒有勾選要最佳化房貸，都不應該去讀取API請求裡`baseOverrides.mortgageEarlyRepayments()`夾帶的房貸提前還款數字。

實際的修正做了兩層防護，其中「Layer 1」是在API入口的`GlobalOptimizerMapper.toCommand()`方法上加了`@Mapping(target = "baseOverrides", ignore = true)`（[GlobalOptimizerMapper.java:36](finance-hub/finance-hub-backend/hub/src/main/java/ch/lin/finance/hub/backend/api/controller/simulation/dto/GlobalOptimizerMapper.java:36)）——這個標記的效果不是只擋掉`baseOverrides`裡的房貸資料，而是讓整個`baseOverrides`物件（不管裡面裝的是房貸資料、還是其他完全不相關的資料），從API進來的那一刻就直接被丟棄、變成null，後面所有邏輯完全不會再看到它。

重新核對程式碼後發現，`baseOverrides`除了房貸提前還款之外，其實還被用在另一個完全不同的地方：[GlobalOptimizerServiceImpl.java:149-158](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/optimizer/GlobalOptimizerServiceImpl.java:149)，這段程式碼原本的用途（程式碼裡的註解寫著「Preserve non-optimized fields from baseOverrides (like fund overrides) if necessary」），是把呼叫者在API請求裡送來的「基金報酬率假設」（`fundOverrides`）跟「房貸利率變動情境假設」（`mortgageRateScenarios`）原封不動地保留、放進最佳化結果裡回傳——這是跟本次issue-40「房貸提前還款」完全不同的另一組資料，語意上比較像是「這次最佳化搜尋，要假設基金報酬率／房貸利率會怎麼變動」，不是「要不要保留使用者手動排的房貸提前還款計畫」這個問題。

因為`baseOverrides`現在從API入口就整個變成null，這段「保留基金報酬率／房貸利率情境假設」的邏輯，現在永遠只會拿到null，等於這個保留機制被連帶永久關閉了。

## 不確定的地方
這個影響目前還沒有被實際的畫面觸發：核對過目前唯一的前端呼叫（`use-global-optimizer.ts`）送出的請求本來就沒有`baseOverrides`欄位，所以無論是房貸提前還款、還是基金報酬率／房貸利率情境假設，實際上都不會透過現有畫面被送進來，這部分修正前後的實際行為沒有差別。

但這跟issue-40當初「不管現在有沒有畫面會這樣送，程式碼結構本身就應該要防止」的態度是一樣的邏輯：如果之後有新的畫面、或是有人直接呼叫這個API、想要在最佳化搜尋時夾帶自訂的基金報酬率／房貸利率情境假設（這在語意上跟房貸提前還款是不同的需求，看起來是合理的用途），現在的做法會讓這件事在API層就直接被擋死，沒有辦法做到。

我不確定的是，這是否是使用者原本就有意接受的範圍（也就是「這個保留機制反正目前用不到，一併關掉也沒關係」），還是原本只想擋掉房貸提前還款這一項、沒有意識到會連帶影響到完全不相關的基金報酬率／房貸利率情境假設保留機制。這屬於產品範圍的取捨，不是程式碼本身對錯的問題，所以不代為決定。

## 需要使用者確認的問題
以下兩個方向，各有取捨：

**方向A：維持現狀，`baseOverrides`整個在API入口就被丟棄**
- 優點：修正已經完成，不需要再改動；且目前唯一的畫面本來就沒有用到這個保留機制，實際上沒有任何現在看得到的影響。
- 缺點：之後如果真的有人想透過這個API夾帶基金報酬率／房貸利率情境假設，會發現這個功能已經被結構性地擋死，需要回頭修改`GlobalOptimizerMapper`才能恢復。

**方向B：把Layer 1的防護範圍縮小，改成只丟棄`baseOverrides`裡的房貸提前還款欄位（`mortgageEarlyRepayments`），保留`fundOverrides`跟`mortgageRateScenarios`能正常傳遞到`GlobalOptimizerServiceImpl.java:149-158`那段保留邏輯**
- 優點：精準對應issue-40原本描述的問題範圍（只擋房貸提前還款），不影響其他語意上完全不相關的假設保留機制。
- 缺點：需要額外修改`GlobalOptimizerMapper`的Mapping設定（改成只忽略`baseOverrides`底下的房貸欄位，或是改用別的方式在Command層過濾），改動比現在的做法稍微複雜一點。

到底要選哪個方向，麻煩您確認。
