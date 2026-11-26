Found it — `finance-hub-ui` is a sibling top-level repo, not nested under `finance-hub`. Now I have all the repo paths I need to launch the full verification workflow.

Now I'll write the `WONTFIX.md` and `PLANNED.md` catalogs directly from the reconnaissance already gathered, then launch the full verification workflow.

WONTFIX.md and PLANNED.md are written. Now launching the large-scale verification workflow — 16 thematic groups covering every remaining active claim across the FINDING files, round1.md, round2.md, and gemini-round1.md, each independently re-checked against current source code rather than trusted from self-reported notes.

I've kicked off the full verification pass — 16 thematic agent groups running against the current source, each independently re-checking claims rather than trusting self-reported "Fixed" notes.

**Already done and written:**
- [WONTFIX.md](finance-hub/finance-hub-backend/todo/WONTFIX.md) — catalogs H3/H6/M1/L3 (round1), 2.10/2.13 (round2), and FINDING-09/27/23 (legacy optimizer removal + the NISA per-fund-cap dispute, which I judged in FINDING-23's favor — no such cap exists in Japanese NISA law, confirming that file's own suspicion that gemini-round1's §2.4 is likely hallucinated).
- [PLANNED.md](finance-hub/finance-hub-backend/todo/PLANNED.md) — H5/H7/H16+FINDING-31/H18/M3/L2, cross-referenced against `plan.md` (noting H5 and L2 have no corresponding plan.md entry). Also flagged that FINDING-31 isn't a new finding — it's a duplicate record of round1's H16, now merged into one tracked item.

**Running in background** (`wgssdq3yi`): 16 groups covering every remaining live claim — all ~22 "claims fixed" FINDING files, round2's 16 new findings, round1's previously-"confirmed" items (spot-checked for regression given the volume of unrelated refactoring since), and gemini-round1's own 4 claims with extra legal/factual skepticism per your note. Group 2 (mortgage bonus double-counting, FINDING-02) is flagged high-priority since it gates FINDING-34. I'll get a notification when it completes, then I'll turn any non-`CONFIRMED_FIXED` verdict into a `FINDING-xx-0x.md` file and report the full results.

The verification workflow finished — all 16 groups completed. Let me pull the full results since the summary was truncated.

Now writing the seven companion files based on the fully-captured verification evidence.

Six of seven companion files are written. Now the last one — FINDING-07-01.md — which needs the severity judgment call (agent suggested Low; I'm escalating to Medium since it's a permanent, silently-compounding, uncorrectable simulation-accuracy bug rather than a cosmetic gap).

All seven re-verification companion files are written. Here's the complete round summary.

## What was done

Independently re-verified every FINDING-0X.md file, all of `round1.md`/`round2.md` (fixed and unfixed items alike), and every `gemini-round1.md` claim against current source — 16 parallel verification groups, no self-reported "Fixed" status trusted without direct code inspection.

## New files created this round (FINDING-xx-01.md — claim vs. reality mismatches)

| File | Gap found |
|---|---|
| [FINDING-16-01.md](finance-hub-backend/todo/claude%20feedback/FINDING-16-01.md) | Fund-override `fundId`-collision bug — **zero remediation attempted**, no fix section ever appended. Still High severity, highest-priority open item. |
| [FINDING-17-01.md](finance-hub-backend/todo/claude%20feedback/FINDING-17-01.md) | `mortgageRateScenarios` clear-to-null fix landed; sibling `mortgageEarlyRepayments` field and "Clear All Overrides" button still use `undefined`, so stale overrides still resurrect on reload. |
| [FINDING-21-01.md](finance-hub-backend/todo/claude%20feedback/FINDING-21-01.md) | Claimed test rewrite (20M seed cash, `MortgageCalculatorUtil`-derived exact assertions) **does not exist in current source** — still 30M seed, still only inequality assertions. |
| [FINDING-25-01.md](finance-hub-backend/todo/claude%20feedback/FINDING-25-01.md) | New `HtmlParsingException` fail-fast is real but guards a different failure mode than the one described — the actual lazy-load/partial-render scenario still goes through silent `continue`/default-to-zero paths with zero exceptions. |
| [FINDING-28-01.md](finance-hub-backend/todo/claude%20feedback/FINDING-28-01.md) | Sandbox frontend guard is real, but backend `@Valid` never cascades (missing on the controller param). Also folds in round1-M9: the actual live permanent-fund-return-rate editor (`ExpectedReturnRateEditor`) was never touched by any fix, has a fake "5.00" default, and PATCHes routes that don't exist on the backend. |
| [FINDING-33-01.md](finance-hub-backend/todo/claude%20feedback/FINDING-33-01.md) | The DoS cap itself is genuinely fixed (two independent layers). The write-up's bonus claim that `GridSearchEngine` "fully utilizes" an extracted `MortgageEarlyRepaymentSolver` is false — that class is dead/orphaned code; the logic is duplicated inline instead. |
| [FINDING-07-01.md](finance-hub-backend/todo/claude%20feedback/FINDING-07-01.md) | Core dual-track NISA cap fix is correct (verified via hand-traced arithmetic). But the fix's own self-posed follow-up question — does principal recovery need to decrement `accumulatedGrowthNisaPrincipal` too — was never answered, and the one test that could catch it was rewritten to dodge that exact scenario. Escalated to **Medium** (from the agent's suggested Low) since it's a permanent, silently-compounding drift, not a one-off inaccuracy. |

## Confirmed genuinely fixed this round (no new file needed)
FINDING-01, 30, 04+04-2, 02 (unblocks FINDING-34 for a future round, though FINDING-34 itself stays excluded per your Note 6), 03, 20, 10, 11, 32, 29, 19, 05, 06, 08, 15, 26, 24, and round1/round2 items H1, H4, H8, H9, H12, H13, H14, H17, M2, M4, M6, M7, M8, L1, and round2 §2.1, 2.2, 2.3, 2.4, 2.5, 2.8, 2.9, 2.11, 2.12, 2.14, 2.15, 2.16, plus gemini-round1 2.1, 2.2, 2.3.

## Still open, never triaged at all (no fix claim exists, so no `-01` file — nothing to contradict)
- **FINDING-12** (High) — DC/iDeCo contribution-years never seeded from hire date, still start at 0.
- **FINDING-13** (Medium) — estimated tax/social-insurance still computed full-12-month while `netIncome` is prorated.
- **FINDING-14** (Low) — no upper-bound clamp (`Math.min(75,...)`) on iDeCo/DC/tax-advantaged withdrawal ages, unlike `PensionCalculator`'s two-sided clamp.

## WONTFIX.md / PLANNED.md
Both remain accurate — this round's independent re-checks corroborate every entry (FINDING-09/27, round2-2.7, FINDING-23/gemini-2.4, round1-H3/H6/M1/L3, round1-H5/L2) with no changes needed.