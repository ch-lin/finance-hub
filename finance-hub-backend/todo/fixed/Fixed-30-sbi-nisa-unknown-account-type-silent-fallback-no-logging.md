# [LOWEST] SBI NISA account-type parser silently defaults unrecognized header lookups to GROWTH with no logging trace

## Status: OPEN — new finding, split out from an incidental observation made while re-verifying
Fixed-07/Fixed-07-01 (see [Fixed-07-02.md](Fixed-07-02.md)). Functionally unrelated to that
finding's principal-recovery fix; filed as its own numbered item per this engagement's convention of
giving incidentally-discovered, unrelated issues a fresh top-level number rather than a companion file.

## Priority note
User-assessed as lowest priority: this is not expected to occur in practice, since SBI's NISA account
statement HTML consistently carries the account-type header (成長/growth vs つみたて/tsumitate) on every
real export observed so far. This finding exists purely to keep the gap tracked/visible, not because
it is currently causing incorrect output.

## Where it happens
`SbiNisaParserImpl.java:66-71`:
```java
String accountType = findNearestAccountType(row);
if (accountType == null || accountType.isBlank()) {
    // Default fallback if we can't find the header
    accountType = "Unknown";
}
```
`findNearestAccountType(Element row)` (lines 184-204) walks up the DOM looking for the nearest
preceding `<h4 class="headline-sm">` header — that's how SBI's HTML marks which section (成長 or
つみたて) a given fund row belongs to. If no such header can be found for a row (e.g. a future SBI
template change, or an unusual page structure), the code doesn't fail or flag anything — it just
assigns the literal string `"Unknown"` and continues.

## What happens to that value next
`NisaAccountType.java:35-43`:
```java
public static NisaAccountType parse(String rawAccountType) {
    if (rawAccountType == null || rawAccountType.isBlank()) {
        return GROWTH;
    }
    if (rawAccountType.contains("つみたて")) {
        return TSUMITATE;
    }
    // "NISA (成長)預り", "成長", "growth" (case-insensitive fallback)
    return GROWTH;
}
```
`"Unknown"` is non-null/non-blank (skips line 36) and doesn't contain `つみたて` (skips line 39), so it
falls through to `return GROWTH` at line 43. This is a documented, intentional conservative default
(class javadoc, lines 27-30): GROWTH has the tighter ¥12,000,000 lifetime cap, so misclassifying a
tsumitate fund as growth under-projects contribution headroom rather than over-projecting it — this
fails safe on the numeric side, it is not a correctness/math bug.

## The actual gap: no diagnostic trace when the fallback fires
`grep` of every `logger.*` call in `SbiNisaParserImpl.java` returns exactly two hits, both at `debug`
level, neither specific to this fallback:
```java
115: logger.debug("Parsed NISA record: {} - {} (Units: {}, Value: {})", accountType, fundName, units, currentMarketValue);
```
This fires unconditionally for every successfully parsed row. It happens to include whatever
`accountType` ended up being (so `"Unknown"` would technically appear if you went looking), but it is
indistinguishable from a normal successful parse, and `debug` is typically suppressed outside local
development. There is no `logger.warn`/`error`/`info` anywhere near the fallback branch (lines 68-71)
itself. If SBI's template ever changes such that the header lookup starts failing, funds would be
silently misclassified with zero trace explaining why — a debuggability gap, not a data-corruption
bug.

## Suggested fix (trivial, deferred given lowest priority)
Add a single log line at the fallback site itself, e.g.:
```java
if (accountType == null || accountType.isBlank()) {
    logger.warn("Could not determine NISA account type header for a row; defaulting to GROWTH. Fund context: {}", row.text());
    accountType = "Unknown";
}
```
This costs nothing and requires no new infrastructure, test fixtures, or architecture change — it
simply makes an already-safe fallback path visible in logs if it is ever actually hit. Not required to
address now; tracked here at lowest priority per user direction.

## Fix Applied (2026-09-02)
Added the suggested `logger.warn` statement at the fallback site (`SbiNisaParserImpl.java`). Now, if the SBI HTML structure changes and the account type header cannot be found, the system will explicitly log a warning with the row's context before defaulting to `GROWTH`, significantly improving debuggability.
