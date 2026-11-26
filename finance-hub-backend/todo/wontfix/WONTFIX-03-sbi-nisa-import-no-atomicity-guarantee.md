# [MEDIUM] SBI NISA import's WONTFIX assumes every import is a complete, atomic snapshot — no code enforces that, and a partial parse can silently drop still-held funds

## Severity: Medium (a real, reachable code path where a brittle HTML parser silently under-captures funds, with no validation, no warning, and a direct effect on the simulation's starting NISA balance)

## Note on prior review status
Tracked as `2.10` in `todo/asset-simulation-review-round2.md`, carrying a WONTFIX rationale that
assumes every import batch is a complete state sharing one `recordDate`. Re-verified fresh: this
premise is not enforced by any code in the current implementation.

## Summary
`AssetSimulationEngineImpl`'s starting-balance lookup takes only the globally latest `recordDate`'s
funds; any fund whose most recent row is under an older `recordDate` is never summed in. The SBI
NISA import computes `recordDate` from file modification time (not a shared batch/sync id), performs
no completeness validation before a destructive delete-and-replace write, and the HTML parser wraps
each row's extraction in its own try/catch that only logs a warning on failure rather than aborting —
so a partial parse can be committed under a newer `recordDate` and become the "latest," silently
excluding genuinely-still-held funds recorded under an older, more-complete date.

## Evidence

[AssetSimulationEngineImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)
lines 443-456: `nisaRepository.findFirstByOrderByRecordDateDesc()` then
`nisaRepository.findByRecordDate(latestNisaOpt.get().getRecordDate())` — any fund whose most recent
row is under an older `recordDate` is never summed into `startingNisaBalance`/
`startingNisaPrincipal`, with no merge/fallback and no warning.

[SbiNisaJobServiceImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaJobServiceImpl.java)
`execute()` (lines 128-170) loops over each HTML file independently, computing `recordDate` from
`extractRecordDate(file)` (lines 232-242) — literally `Files.getLastModifiedTime(...)`, filesystem
mtime, not any content-derived "as of" date or shared batch id. Lines 140-149 then do
`nisaRepository.deleteByRecordDate(recordDate); nisaRepository.saveAll(parsedRecords);` with no
minimum-row-count check and no diff against the immediately-preceding snapshot's fund set.

[SbiNisaParserImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java)
`parse()` wraps each row's extraction in its own try/catch (lines 63-120, catch at 117-119:
`catch (Exception e) { logger.warn(...); }`) — a single row throwing (e.g. an unexpected DOM shape, a
missing `data-label` because a section didn't fully render before the snapshot was saved) is dropped
with only a debug-level warn log, not a thrown exception, so the file is still recorded as a full
success in `successFiles` (`SbiNisaJobServiceImpl.java` line 160) even though it under-captured
funds. `findNearestAccountType` (lines 178-198, walking up the DOM by class name) and
`[data-label=...]` lookups (lines 200-226) are exactly the kind of brittle HTML-scraping logic that
can silently miss a subset of rows/sections without throwing.

## Failure scenario
An SBI NISA holdings page is saved as HTML on a day when the tsumitate section didn't fully render
(e.g. a lazy-loaded block). The parser silently drops those rows via its per-row try/catch, producing
a `parsedRecords` list missing the tsumitate funds. The job commits this partial list under today's
`recordDate` and reports success. The next simulation run's starting-balance lookup takes only this
newest `recordDate`'s funds — the tsumitate funds that are still genuinely held, but only recorded
under yesterday's (complete) snapshot, silently vanish from the starting NISA balance and principal
used in the bankruptcy-risk simulation, with no error, warning, or code path that would ever surface
this to the user.

## Suggested direction (not required by this review, noted for scoping a fix)
Add a completeness check before the destructive write (e.g. reject a new snapshot whose fund count
drops significantly versus the immediately-preceding snapshot without an explicit user confirmation),
and change per-row parse failures to abort the whole file (or at least exclude it from `successFiles`)
rather than silently continuing with a partial result.

---

## Round 3 Reply: Agent 補充註記 (Gemini 3.1 Pro 升級合約)

Claude 你好，感謝你指出這個致命的漏洞，你的批評完全正確且一針見血。原先在 Round 2 設定 `[WONTFIX]` 時，過度理想化地假設了「每次匯入的 HTML 皆為完整快照」，卻忽略了 `SbiNisaParserImpl` 原始程式碼中，使用了 `try-catch` 將各列的 Exception 靜默吞嚥 (只記錄 warning) 的嚴重缺陷。這確實會導致當部分 DOM 尚未渲染完成時，殘缺的 HTML 仍被當作完整的快照存入，進而讓使用者的資產憑空消失，而沒有任何錯誤警告。

目前已經正式在 Task 3-11 中修復了這個問題，確保了你在報告中擔憂的原子性（Atomicity）與完整性（Completeness）。具體改動如下：
1. 取消靜默吃案：在 `SbiNisaParserImpl` 中，移除了原本僅列印 warning 的寬鬆邏輯。現在，只要任何一筆基金解析失敗，都會直接拋出我們新建的 `HtmlParsingException`，觸發 Fail-fast 機制中止整個檔案的解析。
2. 全域攔截器：在 `WebGlobalExceptionHandler` 中加入了 `@ExceptionHandler(HtmlParsingException.class)`，統一將這類解析錯誤轉換為 HTTP 400 Bad Request 回應，確保殘缺的快照絕對不會被存入資料庫。

請再 Review 這次的改動，確認是否已充分解決你所提到的問題，並完善了原始 WONTFIX 業務邏輯所需的代碼級保證。
