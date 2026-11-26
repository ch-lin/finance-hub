Here's the complete picture, in four parts.

## Part 1 — New findings (FINDING-19~34) cross-reference

| # | Category | Tracked as | Verdict |
|---|---|---|---|
| [19](todo/claude feedback/FINDING-19-usd-jpy-fallback-still-1to1-on-null-rate.md) | ② round1/2 unfixed | H1 | Main path fixed; null-rate fallback still 1:1 |
| [20](todo/claude feedback/FINDING-20-mortgage-rate-scenario-frontend-never-migrated.md) | ③ orig. 1–10 unfixed | FINDING-03 | Backend fixed; frontend never migrated |
| [21](todo/claude feedback/FINDING-21-mortgage-test-only-checks-directional-inequality.md) | ② round1/2 unfixed | H11 | Test added but only asserts inequality |
| [22](todo/claude feedback/FINDING-22-nisa-goal-solver-still-live-pending-removal.md) | ④ overlap | 2.7 = FINDING-09 | Bug unchanged; downgraded to heads-up (feature being removed) |
| [23](todo/claude feedback/FINDING-23-nisa-no-per-fund-reinvestment-cap.md) | ② round1/2 unfixed | 2.4 (gemini-round1) | No per-fund cap exists |
| [24](todo/claude feedback/FINDING-24-nisa-growth-tsumitate-sublimit-still-not-enforced.md) | ③ orig. 1–10 unfixed | FINDING-07 | Still not enforced; accountType structurally dropped |
| [25](todo/claude feedback/FINDING-25-sbi-nisa-import-no-atomicity-guarantee.md) | ② round1/2 unfixed | 2.10 | WONTFIX premise not actually enforced by code |
| [26](todo/claude feedback/FINDING-26-nisa-carryover-test-uses-zero-contribution-fund.md) | ② round1/2 unfixed | M5 | Test added but exercises $0-contribution fund |
| [27](todo/claude feedback/FINDING-27-nisa-goal-solver-frontend-bounds-decorative.md) | ② round1/2 unfixed | H15 | Backend fixed; frontend bounds decorative |
| [28](todo/claude feedback/FINDING-28-fund-override-return-rate-bounds-check-only-in-dead-code.md) | ② round1/2 unfixed | M9 | Fix only exists in dead/unimported component |
| [29](todo/claude feedback/FINDING-29-profile-form-return-rate-bounds-check-only-in-dead-code.md) | ② round1/2 unfixed | 2.15 | Same dead-code fix, different live surface |
| [30](todo/claude feedback/FINDING-30-family-member-birthdate-no-server-side-validation.md) | ② round1/2 unfixed | 2.16 | Frontend fixed; no server-side defense-in-depth |
| [31](todo/claude feedback/FINDING-31-no-statutory-ideco-dc-contribution-limit.md) | ① new | — | No statutory iDeCo/DC limit |
| [32](todo/claude feedback/FINDING-32-ga-elitism-crashes-when-elite-exceeds-population.md) | ① new | — | GA elitism crash |
| [33](todo/claude feedback/FINDING-33-force-grid-no-combinatorial-explosion-cap.md) | ① new | — | FORCE_GRID no combinatorial cap |
| [34](todo/claude feedback/FINDING-34-mortgage-monthly-payment-ignores-bonus-payments.md) | ① new | — | Mortgage payment ignores bonus payments |

**Totals: ①=4, ②=9, ③=2, ④=1 → 16, matches all surviving files.**

## Part 2 — FINDING-01~10 status

| # | Title | This-session status |
|---|---|---|
| [01](todo/claude feedback/FINDING-01-hibernate-tenant-filter-is-a-total-noop.md) | Hibernate tenant filter no-op | ✅ Re-checked — fixed for HTTP paths |
| [02](todo/claude feedback/FINDING-02-mortgage-bonus-payment-double-counted.md) | Mortgage bonus double-counted | ⚠️ **Not checked this session** |
| [03](todo/claude feedback/FINDING-03-mortgage-rate-scenario-override-global.md) | Mortgage rate scenario override global | ✅ Re-checked — backend fixed, frontend not (→FINDING-20) |
| [04](todo/claude feedback/FINDING-04-mortgage-tax-deduction-cap-summed-not-combined.md) | Mortgage tax deduction cap | Gemini's *proposed fix* reviewed, not live-re-verified — [FINDING-04-2.md](todo/claude feedback/FINDING-04-2.md) |
| [05](todo/claude feedback/FINDING-05-retiree-health-insurance-double-counted.md) | Retiree health insurance double-counted | ⚠️ **Not checked this session** |
| [06](todo/claude feedback/FINDING-06-social-insurance-cap-blended-average.md) | Social insurance cap blended average | Gemini's *proposed fix* reviewed, not live-re-verified — [FINDING-06-2.md](todo/claude feedback/FINDING-06-2.md) |
| [07](todo/claude feedback/FINDING-07-nisa-growth-tsumitate-sublimit-not-enforced.md) | NISA growth/tsumitate sub-limit | ✅ Re-checked — still not fixed (→FINDING-24) |
| [08](todo/claude feedback/FINDING-08-optimizer-nisa-result-discarded.md) | Optimizer NISA result discarded | ⚠️ **Not checked this session** |
| [09](todo/claude feedback/FINDING-09-nisa-goal-solver-wrong-monotonicity.md) | NISA goal solver monotonicity | ✅ Re-checked — unchanged (→FINDING-22) |
| [10](todo/claude feedback/FINDING-10-ga-retirement-age-range-mismatch.md) | GA retirement-age range mismatch | ⚠️ **Not checked this session** |

**Action needed: FINDING-02, 05, 08, 10.** (I verified just now — none of these four appear anywhere in round1.md/round2.md/gemini-round1.md either, so they can't have been swept in via the claim-verification pass. They're purely from an earlier independent code review and have simply never been re-looked-at.)

## Part 3 — round1/round2/gemini-round1 claim coverage

This is the part that actually answers "do I need to go back to those files" — and the honest answer is **yes, most of it is still unverified.** These three documents contain **48 distinct claims** that Gemini self-reported as [FIXED]/[DONE]/[WONTFIX]/[PLANNED]. This session's fresh review only independently re-verified **10** of them.

**`asset-simulation-review-round1.md` (27 claims)**

| Status | Claim IDs |
|---|---|
| ✅ Re-checked this session (all found still broken/weak) | H1, H11, H15, M5, M9 |
| Known-open, no action needed (Gemini itself marked WONTFIX/PLANNED) | H3, H5, H6, H7, H16, H18, M1, M3 |
| ⚠️ **Unverified — Gemini says [FIXED]/[DONE], never independently re-checked** | **H2, H4, H8, H9, H10, H12, H13, H14, H17, M2, M4, M6, M7, M8** (14 claims) |

**`asset-simulation-review-round2.md` (17 claims)**

| Status | Claim IDs |
|---|---|
| ✅ Re-checked this session | 2.7, 2.10, 2.15, 2.16 |
| Known-open, no action needed (WONTFIX) | 2.13 |
| ⚠️ **Unverified — Gemini says [FIXED], never independently re-checked** | **H12(revisit), 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.8, 2.9, 2.11, 2.12, 2.14** (12 claims) |

Note: round2's own `2.4` ("Advanced Overrides missing accountType") is a **different bug** from gemini-round1's `2.4` ("NISA principal pool rebalancing") — same number, different document, unrelated bugs. FINDING-23 only covers the gemini-round1 one; round2's 2.4 is still unverified.

**`asset-simulation-gemini-review-round1.md` (4 claims — a third, separate doc)**

| Status | Claim IDs |
|---|---|
| ✅ Re-checked this session | 2.4 (→FINDING-23) |
| Covered earlier via part (a), not a live re-check | 2.1 (same bug as FINDING-06/[FINDING-06-2.md](todo/claude feedback/FINDING-06-2.md)) |
| ⚠️ **Unverified** | **2.2, 2.3** |

## Part 4 — Bottom line / what's actually left to do

1. **FINDING-02, 05, 08, 10** — need a from-scratch verification pass (never checked in this fresh review).
2. **28 Gemini-self-claimed-[FIXED] items** across the three docs (14+12+2 above) have never been independently re-verified by us — we've only spot-checked the ones that seemed highest-risk or that a prior workflow selected. Given the pattern rate so far (10 checked → 10 found still broken/weak, 100% hit rate), there's a real chance more of these 28 are also not genuinely fixed.
3. FINDING-25 (2.10) may become moot once the Chrome-extension import migration lands — still your call on whether to drop it now or leave it as-is until then.

If you want, I can run a verification pass on all 32 remaining items (4 from FINDING-01-10 + 28 from round1/round2/gemini-round1) — that's a large batch, so let me know if you want it split or prioritized (e.g., High-severity claims first).