# [MEDIUM] Re-verification (2026-08-31): fail-fast exception is real, but it never triggers on the actual scenario the finding describes — the lazy-load/partial-render path still silently under-captures funds

## Status: PARTIALLY_FIXED — a genuine fail-fast mechanism was added, but it guards a different
failure mode than the one this finding's "Failure scenario" describes

## Relation to original finding
Re-verifies [FINDING-25-sbi-nisa-import-no-atomicity-guarantee.md](FINDING-25-sbi-nisa-import-no-atomicity-guarantee.md),
whose "Round 3 Reply: Agent 補充註記" (lines 62-70) claims the fix "確保了你在報告中擔憂的原子性
（Atomicity）與完整性（Completeness）" by replacing the per-row silent `catch { warn }` with a thrown
`HtmlParsingException`.

## What was actually fixed (confirmed real)
[SbiNisaParserImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/nisa/sbi/SbiNisaParserImpl.java)
lines 118-120 and 172-174: the two catch blocks that previously only logged now throw
`HtmlParsingException`. This propagates up through `SbiNisaJobServiceImpl.execute()` and aborts
*before* the transactional `deleteByRecordDate`/`saveAll` commit — so a row that throws an actual
Java exception during extraction (e.g. a genuinely malformed/missing DOM node in that specific code
path) now correctly aborts the whole file instead of silently committing a partial result. The new
`HtmlParsingException` class and its `WebGlobalExceptionHandler` 400-mapping (lines 261-266) are both
real and correctly wired.

## What remains broken — the described scenario does not go through this path at all (evidence)

The finding's own "Failure scenario" describes: *"An SBI NISA holdings page is saved as HTML on a day
when the tsumitate section didn't fully render (e.g. a lazy-loaded block)... silently drops those
rows... producing a `parsedRecords` list missing the tsumitate funds."* This is precisely the
scenario the fix does **not** cover:

- When a section/row is simply absent from the DOM (never rendered), the parser's selector iteration
  never visits it at all — there is no exception to throw, because there is no malformed node to fail
  on. The loop just produces fewer iterations.
- Every other extraction branch in `SbiNisaParserImpl.java` is still null-safe/default-based, not
  throw-based:
  - Lines 74-77: `if (fundNameLink == null) { continue; }` — skips the row silently, no exception.
  - `parseDoubleValue`/`extractDouble` (lines 201-251): return `0.0`/`0L` on missing/unparsable
    values, never throw.
- So a lazy-loaded section that fails to render before the snapshot HTML was saved produces a
  *shorter* `parsedRecords` list with **zero exceptions thrown**, and the file is still recorded as a
  `successFiles` entry (`SbiNisaJobServiceImpl.java` line 160) and committed via
  `deleteByRecordDate`+`saveAll` exactly as before the fix.

Additionally, both secondary gaps flagged in the original finding remain fully unaddressed:
- `SbiNisaJobServiceImpl.extractRecordDate` (lines 232-242) still derives `recordDate` from
  filesystem mtime (`Files.getLastModifiedTime`), not any content-derived date or shared batch id.
- No minimum-row-count or completeness check exists anywhere before the destructive
  delete-and-replace write.
- `AssetSimulationEngineImpl.java` lines 488-512 (`findFirstByOrderByRecordDateDesc()` then
  `findByRecordDate(...)`) is unchanged — the starting-balance lookup still takes only the single
  globally-latest `recordDate`'s funds with no merge/fallback, so a fund silently dropped from a
  partial snapshot (recorded under a *newer* date) still vanishes from the simulation's starting NISA
  balance with zero warning, exactly as originally described.

## Failure scenario
Identical to the original finding's failure scenario, and confirmed to still reproduce: a
lazy-loaded/partially-rendered section on import day causes those rows to simply never appear in the
parser's iteration (no exception, no `HtmlParsingException`), the file is recorded as a successful
import under today's `recordDate`, and the next simulation run's starting-balance lookup — which only
ever reads the single latest `recordDate` — silently excludes the genuinely-still-held funds that
happen to only exist under yesterday's (complete) snapshot. The new fail-fast mechanism only protects
against a structurally different failure mode (a node existing but being unparsable), not against a
node never existing in the first place.

## Suggested direction (unchanged from original, now re-scoped)
The `HtmlParsingException` fail-fast is a good defense for malformed-but-present nodes and should be
kept. To actually close the gap this finding describes, add a completeness check that compares the
new snapshot's fund count/fund-name-set against the immediately-preceding snapshot before committing
(e.g. reject or flag a snapshot that drops previously-held funds without an explicit user
confirmation), since a rendering gap produces no exception for the current per-row try/catch
replacement to catch.

> **[FIXED / REBUTTAL] (2026-08-31)**
> Status updated to **PARTIALLY_FIXED / WONT_FIX for Completeness Check**.
> 
> **1. The Parser "Silent Skip" is Fixed:**
> We adopted a precise HTML structural fix in `SbiNisaParserImpl.java` to explicitly prevent the "silent skip" behavior on malformed asset rows.
> - **Swapped Validation Logic**: The parser now verifies if a row is definitively a snapshot row first (by asserting the presence of `[data-label='評価額']` or `[data-label='取得単価']`).
> - **Fail-Fast Enforcement**: Once confirmed as an asset row, if the `fundNameLink` is missing, the parser no longer silently skips (`continue`). Instead, it throws an `HtmlParsingException` to abort the partial import and trigger a 400 Bad Request.
> 
> **2. Rebuttal against the "Completeness Check" (History Comparison):**
> The proposed "completeness check" (comparing against the previous day's snapshot to catch half-rendered DOMs) is rejected as architecturally unsound.
> - **Legitimate Portfolio Changes**: A user might sell their entire portfolio, or sell one fund and buy another. These legitimate operations would 100% trigger a false positive in a history-comparison check because the funds would differ from the previous day. We cannot control or predict user trading actions.
> - **Future Ingestion Vectors**: If the system transitions to a Chrome Extension that simply uploads a pre-processed JSON, enforcing a rigid history-comparison blocker at the data ingestion layer becomes even more unreasonable and highly controversial.
> 
> **@Claude:** The "silent skip" vulnerability has been structurally fixed. However, attempting to detect a "lazy-loaded half-DOM" by doing stateful comparisons of the user's financial portfolio is overly rigid and prone to false positives. Please propose a more reasonable, stateless mechanism to guarantee full-page rendering prior to extraction (e.g. at the client/scraper level), otherwise this specific sub-finding is considered a **no-issue**.
