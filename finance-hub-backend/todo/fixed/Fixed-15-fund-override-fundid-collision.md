# [HIGH] Per-fund sandbox overrides are matched/merged by fundId alone, ignoring accountType — corrupting overrides whenever a NISA fund and a DC/iDeCo fund share the same numeric id

## Severity: High (silently applies the wrong return-rate/contribution override to the wrong fund, feeding fabricated per-fund growth assumptions into the net-worth/bankruptcy projection — and the backend already had to guard against exactly this collision, which the frontend does not replicate)

## Summary
`getFundOverride` and `updateFundOverride` in the Sandbox override panel look up existing fund
overrides by `fundId` only. But NISA fund IDs (`NisaSnapshotRecord.id`) and DC/iDeCo fund IDs
(`DefinedContributionFundSnapshot.id`) come from separate DB tables, each with its own independent
`GenerationType.IDENTITY` auto-increment counter starting at 1 — so `id=3` existing in both tables
simultaneously is the normal case for any household with both a NISA and a DC/iDeCo snapshot, not a
rare edge case. The backend's `FundOverrideApplier` already builds its lookup with an explicit
composite key (`accountType:fundId`) specifically to prevent this cross-table collision — proving
the collision was already known and guarded against server-side — but the frontend panel that
produces the override list does not replicate that composite key.

## Evidence

[simulation-override-panel.tsx](finance-hub-ui/hub-ui/src/app/finance/simulator/simulation/components/simulation-override-panel.tsx):
```javascript
const getFundOverride = (fundId: number, accountType: string) => {
  return overrides.fundOverrides?.find(f => f.fundId === fundId) || { fundId, accountType };
};
const updateFundOverride = (fundId: number, accountType: string, partial: Partial<FundOverride>) => {
  const existing = overrides.fundOverrides || [];
  const idx = existing.findIndex(f => f.fundId === fundId);
  ...
  if (idx >= 0) {
    newOverrides[idx] = { ...newOverrides[idx], accountType, ...partial };
    ...
```
Both functions match by `fundId` only; `accountType` is not part of the lookup predicate, even
though the `FundOverride` type itself has both fields.

Backend (`FundOverrideApplier.java`), confirming the collision is real and already worked around
server-side but not client-side:
```java
// Key: "NISA:42", "CORPORATE_DC:7", "IDECO:3" etc.
// Using composite key prevents cross-table id collisions:
// NISA uses NisaSnapshotRecord.id, DC/iDeCo use DefinedContributionFundSnapshot.id.
Map<String, SimulationOverrides.FundOverride> overrideMap = overrides.fundOverrides().stream()
        .collect(Collectors.toMap(fo -> fo.accountType().toUpperCase() + ":" + fo.fundId(), fo -> fo));
```

## Failure scenario
A household has a NISA fund with `id=3` and a separate iDeCo fund that also has `id=3` (routine,
since they are auto-increment PKs from two different tables). The user sets the NISA fund's
"Return (%)" override to 8%: `updateFundOverride(3, 'NISA', { returnRateOverride: 0.08 })` creates
`{fundId:3, accountType:'NISA', returnRateOverride:0.08}`. The user then edits the iDeCo fund's
"Monthly (¥)" override to ¥23,000: `updateFundOverride(3, 'IDECO', { contributionOverride: 23000 })`.
Because the lookup matches on `fundId` only, it finds the NISA entry and merges into it, producing a
single object `{fundId:3, accountType:'IDECO', returnRateOverride:0.08, contributionOverride:23000}`.
When POSTed, the backend's composite-key `"IDECO:3"` lookup applies **both** the 8% return-rate
override AND the ¥23,000 contribution override to the iDeCo fund, while the NISA fund's override
silently vanishes (no `"NISA:3"` key remains) and reverts to its DB default. The simulation now runs
with a fabricated iDeCo return rate the user never set for that fund, and loses the NISA override
they did set — feeding wrong per-fund growth assumptions into the net-worth/bankruptcy projection.

## Suggested direction (not required by this review, noted for scoping a fix)
Change both `getFundOverride` and `updateFundOverride` to match on the composite key
(`fundId` AND `accountType`), mirroring the backend's already-established `accountType:fundId`
convention.
