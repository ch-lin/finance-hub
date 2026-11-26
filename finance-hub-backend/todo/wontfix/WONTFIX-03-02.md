# [MEDIUM→ACCEPTED RISK] Re-verification (2026-09-01): parser fix re-confirmed correctly ordered; opinion on the rejected completeness-check direction — no clean stateless fix exists in this architecture, recommend keeping current behavior with one optional, non-blocking addition

## Status: PARTIALLY_FIXED, remaining gap resolved to ACCEPTED RISK / WONTFIX (temporary policy, see
[WONTFIX.md](../wontfix/WONTFIX.md) §4) — not because a good fix doesn't exist in the abstract, but
because neither the originally-suggested direction nor the developer's own counter-suggestion is
implementable in this specific codebase as currently architected, and the cost of building a new
mechanism from scratch is disproportionate to the risk for a single-user personal simulator.

## Relation to original finding
Re-verifies [FINDING-25-01.md](FINDING-25-01.md)'s "[FIXED / REBUTTAL] (2026-08-31)" section, which:
(a) confirmed the "silent skip" fix (fail-fast on a detected-but-malformed asset row) is real, and
(b) explicitly rejected the history-comparison completeness check I had originally suggested, asking
for either a better stateless alternative or acceptance that this sub-finding is a no-issue.

## Re-confirmed: the parser fix itself is real and correctly ordered
`SbiNisaParserImpl.java:74-75` gates row detection first (`if` both `[data-label='評価額']` and
`[data-label='取得単価']` are absent, `continue` — this is not the bug, it's the row-is-not-an-asset-row
case). Only for rows that pass this gate does `fundNameLink == null` now throw `HtmlParsingException`
(lines 79-82) instead of silently skipping. This correctly narrows to "a row that IS present but
malformed" — exactly as the original fix claimed.

## The developer's rebuttal against history-comparison is correct, and I agree with it
Comparing a new snapshot's fund set against the immediately-preceding one to detect "did something
disappear" cannot distinguish a rendering failure from a legitimate portfolio change (selling a fund,
switching funds) without false-positiving on the latter, which is common and expected user behavior.
I'm not defending my original suggested direction here — the rebuttal is right to reject it.

## But the developer's own counter-suggestion doesn't apply either — this changes the analysis
The rebuttal asks for "a more reasonable, stateless mechanism to guarantee full-page rendering prior to
extraction (e.g. at the client/scraper level)." I checked whether this is buildable:
- **No scraper or browser-automation tooling exists anywhere in this repo.** A repo-wide search for
  playwright/puppeteer/selenium/headless-browser/Chrome-extension tooling specific to NISA import found
  nothing. Ingestion is `HtmlArchiveUtils.findHtmlFiles(importPath + "/source")`
  (`SbiNisaJobServiceImpl.java:118`) — a plain directory scan. Other product lines in this codebase
  (E-Trade, bank statements, Amazon/Sofmap/Rakuten) do have dedicated "Chrome Extension" upload
  controllers that receive pre-parsed JSON directly, but **no equivalent exists for NISA** — NISA import
  is (as far as the current code shows) manually-saved HTML dropped into a folder by the user.
- **No HTML test fixtures exist for the SBI NISA parser at all** (only unrelated iDeCo fixtures were
  found), and there is zero existing test coverage for `SbiNisaParserImpl`. This means I also cannot
  verify, from a real sample, whether SBI's page template always renders the 成長/つみたて section
  containers as static markup even when a category is empty (which would make a "check both section
  headers exist, regardless of row count" structural check a viable middle ground) — that assumption
  is unverified and I won't recommend building against it blind.
- Given there is no scraper to add a render-wait to, and no sample HTML to validate a structural
  container-presence check against, there is currently no way to implement *either* side's suggested
  direction without first building new infrastructure (a scraper, or a fixture-gathering exercise) that
  doesn't yet exist for this one narrow edge case.

## My recommendation
**Keep the current behavior (no additional blocking/completeness logic) — this matches the fallback
already agreed: if no clean approach exists, the temporary policy is to leave it as-is, since building
a mechanism to cover every partial-render permutation is disproportionate effort for a personal
finance tool where the user is also the one entering/reviewing their own data.**

One optional, genuinely risk-free addition, offered only as a low-cost nicety rather than a required
fix (does not block anything, cannot false-positive on legitimate portfolio changes, requires no new
infrastructure): log the imported fund count/names at `INFO` level on every successful
`SbiNisaJobServiceImpl` import run. This gives a human occasionally skimming logs a passive breadcrumb
("today's import: 3 funds" vs. yesterday's "5 funds") without enforcing anything or requiring a
database-level comparison at commit time. This is not required — if even this is considered
unnecessary effort, doing nothing further is an acceptable outcome per the stated fallback policy.

The two secondary gaps noted in FINDING-25-01 (mtime-derived `recordDate` in
`SbiNisaJobServiceImpl.extractRecordDate`; single-latest-`recordDate`-only lookup in
`AssetSimulationEngineImpl`) remain unchanged and out of scope of this specific completeness-check
decision — they are a separate design question (should NISA record-keeping move to a
content-derived/shared batch date instead of file mtime) that can be revisited independently if it
ever becomes a real point of confusion in practice.

## Conclusion
No further code change is being requested for this sub-finding. The "silent skip on lazy-loaded/absent
sections" gap is a known, accepted residual risk, filed in WONTFIX.md as a temporary (not permanent)
policy — worth revisiting only if this scenario is ever observed to actually occur in practice, at
which point a real sample of the incomplete HTML would make it possible to design a properly-grounded
fix instead of a speculative one.

## Addendum (2026-09-01): natural resolution path if a NISA Chrome Extension is ever built
The developer noted that this whole class of problem is a byproduct of NISA being the one product
line still on manual-HTML-file import — E-Trade, bank statements, and Amazon/Sofmap/Rakuten already
have dedicated Chrome Extension controllers (`CompanyStockController`, `BankExtensionController`,
`AmazonExtensionController`/`SofmapExtensionController`/`RakutenExtensionController`) that receive
pre-parsed JSON scraped directly from the live, fully-rendered, logged-in page. If NISA is ever given
an equivalent extension, the completeness problem this WONTFIX covers would very likely be resolved
as a byproduct, not because of any new completeness-check logic, but because the extension would be
scraping a live DOM that has already finished rendering (including any lazy-loaded sections) rather
than a manually-saved, possibly-incomplete static HTML snapshot. This doesn't change today's
decision — no scraper exists yet for NISA — but it's worth recording here as the concrete "what would
actually make this moot" path, per WONTFIX.md's own stated purpose of tracking WONTFIX items for
future re-evaluation.
