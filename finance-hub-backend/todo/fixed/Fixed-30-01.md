# [RESOLVED] Re-verification (2026-09-02): `logger.warn` genuinely added at the fallback site, correctly ordered and argument-matched — CONFIRMED_FIXED

## Status: CONFIRMED_FIXED

## Relation to original finding
Re-verifies the "## Fix Applied (2026-09-02)" section appended to [Fixed-30-sbi-nisa-unknown-account-type-silent-fallback-no-logging.md](Fixed-30-sbi-nisa-unknown-account-type-silent-fallback-no-logging.md), which claimed a `logger.warn` was added at the account-type-fallback site in `SbiNisaParserImpl.java`, matching the finding's own suggested fix.

## Independently verified (confirmed real)

**`logger.warn` present, correctly ordered.** `SbiNisaParserImpl.java:70-74`:
```java
if (accountType == null || accountType.isBlank()) {
    logger.warn(
            "Could not determine NISA account type header for a row; defaulting to GROWTH. Fund context: {}",
            row.text());
    accountType = "Unknown";
}
```
The warn call fires *before* `accountType` is overwritten to `"Unknown"`, so the logged context reflects the pre-fallback row state, matching the suggested fix verbatim.

**Message/argument correctness confirmed.** One `{}` placeholder, one argument (`row.text()`) — 1:1 match, no dropped or stray argument. `row` is the in-scope loop variable, and `.text()` is a valid Jsoup `Element` method used identically elsewhere in the same file.

**Logger field consistency confirmed.** Uses the class's single pre-existing `logger` field (`LoggerFactory.getLogger(SbiNisaParserImpl.class)`) — no new/duplicate logger instance introduced.

## Noted gap (minor, not required to fix now)
The fallback sits inside a per-row loop, so if SBI's HTML template broke systemically (no header found for any row), this would emit one `WARN` per row rather than once per statement — minor log-volume consideration under a template-breakage scenario, not a correctness issue. Not worth guarding given this finding's already-lowest priority.

No test coverage exists for this parser at all (no `SbiNisaParserImplTest` anywhere in the repo) — consistent with the finding's own lowest-priority framing; not treated as a blocker.

## On execution
Could not compile — same sandbox `~/.m2`/parent-POM blocker as every other backend finding this round, unrelated to this one-line change.

## Verdict
Genuinely fixed as a trivial, correctly-scoped logging addition. No further action needed on this finding.
