# [MEDIUM] USD→JPY conversion falls back to treating the raw USD figure as JPY 1:1 whenever `exchangeRate` is null, reproducing the original bug numerically

## Severity: Medium (silently understates a USD-denominated holding by roughly 100-150x whenever the exchange rate is missing, which is a real, unvalidated, and untested code path)

## Note on prior review status
Tracked as `H1` in `todo/asset-simulation-review-round2.md`. The main path — a USD snapshot with a
valid `exchangeRate` — is now correctly converted to JPY, genuinely fixing the original 1:1 bug for
the common case. This finding is about the fallback branch that remains unfixed.

## Summary
When a USD-denominated snapshot's `exchangeRate` is missing or invalid, the conversion method logs a
warning and then returns the raw USD number unchanged, as if it were already JPY — i.e. the exact
original bug, just now gated behind a condition (`rate == null || rate <= 0`) instead of being
unconditional. The warning is server-side only and never reaches the API response or the UI, so a
user has no way to know their e.g. $50,000 USD holding was just booked into the simulation as
¥50,000.

## Evidence

[AssetSimulationEngineImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/simulation/core/AssetSimulationEngineImpl.java)
lines 556-574:
```java
if ("USD".equals(snapshotCurrency)) {
    Double rate = snapshot.getExchangeRate();
    if (rate != null && rate > 0) {
        return Math.round(rawValue * rate);
    }
    log.warn(...);
}
return rawValue;   // reached both for non-USD AND for USD-with-null/invalid-rate
```
The final `return rawValue` uses the raw USD number as if it were JPY whenever `exchangeRate` is
null — still off by the true FX rate (~100-150x) in that case. There is also a secondary latent gap:
`extractCurrency()` in
[ETradeHoldingsHtmlParserImpl.java](finance-hub/finance-hub-backend/hub-app/src/main/java/ch/lin/finance/hub/backend/api/app/service/accounting/company_stock/etrade/ETradeHoldingsHtmlParserImpl.java)
lines 232-240 returns `"UNKNOWN"` if the value string doesn't start with `¥` or `$`, and the engine's
check `"USD".equals(snapshotCurrency)` treats `UNKNOWN` the same as JPY (no conversion attempted at
all). No test exercises the null-rate branch:
`AssetSimulationEngineImplTest.java` line 145 mocks `etradeRepository` as an empty `Optional`, so the
fallback behavior is unverified in CI.

## Failure scenario
An E*TRADE holdings snapshot is imported (e.g. via HTML parsing) on a day when the exchange-rate
field failed to parse or was not present in the source document, leaving `exchangeRate` null on the
persisted `ETradeAccountSnapshot`. The next time the Asset Simulator runs, this $50,000 USD holding
is added to the simulation's starting net worth as ¥50,000 instead of roughly ¥7,500,000 (at ~150
JPY/USD) — understating the user's actual assets by over 99%, which can push the simulated
"cash bankruptcy" year artificially earlier than reality, with no error or warning ever surfaced to
the user.

## Suggested direction (not required by this review, noted for scoping a fix)
When `exchangeRate` is null/invalid for a USD snapshot, either fail loudly (surface a validation
error/warning to the API response so the UI can flag the affected holding) or fall back to a
recent/default exchange rate rather than silently treating the value as already-JPY. Add a test that
seeds a USD snapshot with a null `exchangeRate` and asserts the resulting contribution to net worth
is NOT simply the raw USD figure.

---

From Developer

## Resolution: Architectural Shift to Ground Truth via @Embeddable Money
We have opted for a fundamentally different and more robust approach than simply falling back to a system default currency or failing loudly. 

**The Root Cause Analysis:**
The issue stems from the assumption that a single E-Trade account snapshot or a single table row can be strictly mapped to a single currency. However, examining the E-Trade UI Ground Truth reveals that multiple currencies (e.g., USD and JPY) can co-exist within the very same table row (e.g., Purchase Price might be in `$` while Est. Market Value is in `¥`). If the database schema uses plain `Long` or `Double` and assumes the currency context, it immediately loses the Ground Truth. Any downstream logic (like the fallback mechanism) trying to "guess" or "default" the currency will inevitably corrupt the data by applying the wrong exchange rate to the wrong number.

**The Fix:**
Instead of patching the calculation engine with default fallbacks, we solved the problem at the Domain Layer:
1. **Introduced `@Embeddable Money` Value Object:** We created a `Money` Value Object that encapsulates both the numerical `amount` (`BigDecimal` for precision) and its exact `currency`.
2. **Refactored E-Trade Entities:** We refactored every single monetary field across all 6 E-Trade domain entities (`ETradeAccountSnapshot`, `ETradeStockLot`, `ETradeCashTransaction`, `ETradeBenefitEsppPurchase`, `ETradeBenefitRsuGrant`, `ETradeBenefitRsuVestSchedule`). Plain numerical fields were replaced with the `Money` VO using `@AttributeOverrides`.

**Why this direction?**
By forcefully binding the currency to the numerical value at the schema level, the database now faithfully records the exact Ground Truth parsed from the web page (e.g., explicitly saving `$143.27` as USD and `¥529,189.20` as JPY for the same lot). The calculation engine will no longer have to "guess" or rely on a missing `exchangeRate` fallback, because every single monetary value now explicitly declares its own currency. This structurally eliminates the possibility of the silent 1:1 JPY/USD bug.

Task 1 Note:

# Refactor E-Trade Domain Models with Money Value Object

## Goal
To solve the critical architectural flaw where monetary values lose their currency context during parsing, we will introduce a `@Embeddable` `Money` value object in the domain layer. We will refactor all E-Trade entities to replace raw `Long` and `Double` monetary fields with the `Money` object. 

> [!IMPORTANT]
> **Domain-Only Scope:** This plan focuses EXCLUSIVELY on the domain layer (`hub-domain`). It will purposefully NOT touch the `hub-app` service or parser layers. This means that after applying this change, the system will intentionally fail to compile until a follow-up task refactors the service layer to adopt the new domain schema.

## Proposed Changes

### Value Object Layer

#### [NEW] [Money.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/valueobject/Money.java)
- Create a new `@Embeddable` class to encapsulate amount and currency.
- **Fields**: 
  - `BigDecimal amount` (Mapped as `DECIMAL(19,4)` to handle USD cents precisely).
  - `String currency` (Mapped with length=10).
- **Methods**:
  - Factory methods like `of(BigDecimal amount, String currency)`.
  - JPA protected no-args constructor.

---

### E-Trade Entities Layer

#### [MODIFY] [ETradeAccountSnapshot.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeAccountSnapshot.java)
- Remove `String currency` field.
- Refactor `totalAccountValue`, `currentAccountValue`, `potentialBenefitValue` from `Long` to `@Embedded Money`.
- Apply `@AttributeOverrides` for each field to map columns (e.g., `amount` -> `total_account_value`, `currency` -> `total_account_value_currency`).
- Update constructors.

#### [MODIFY] [ETradeStockLot.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeStockLot.java)
- Refactor `price`, `estMarketValue`, `expectedGainLoss` to `@Embedded Money`.
- Apply `@AttributeOverrides`.
- Update constructors.

#### [MODIFY] [ETradeCashTransaction.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeCashTransaction.java)
- Refactor `amount` to `@Embedded Money`.
- Apply `@AttributeOverrides`.
- Update constructors.

#### [MODIFY] [ETradeBenefitEsppPurchase.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeBenefitEsppPurchase.java)
- Refactor `purchasePrice`, `estMarketValue`, `grantDateFmv`, `purchaseDateFmv` to `@Embedded Money`.
- Apply `@AttributeOverrides`.
- Update constructors.

#### [MODIFY] [ETradeBenefitRsuGrant.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeBenefitRsuGrant.java)
- Refactor `estMarketValue` to `@Embedded Money`.
- Apply `@AttributeOverrides`.
- Update constructors.

#### [MODIFY] [ETradeBenefitRsuVestSchedule.java](file:///Users/edward/Desktop/Projects/youtube-data-hub-wrapper/finance-hub/finance-hub-backend/hub-domain/src/main/java/ch/lin/finance/hub/backend/api/domain/model/accounting/company_stock/etrade/ETradeBenefitRsuVestSchedule.java)
- Refactor `taxableGain`, `taxWithheld`, `totalTaxesPaid` to `@Embedded Money`.
- Apply `@AttributeOverrides`.
- Update constructors.

---

## Fix Implementation Summary (for third-party review)

The fix addresses Claude's two suggested directions simultaneously: **fail loudly** when no rate can be determined, and **fall back to a recent rate** (from DB or live API) before failing.

### What changed

#### 1. `CurrencyConversionException` (NEW)
`hub-common/src/main/java/ch/lin/finance/hub/backend/api/common/exception/CurrencyConversionException.java`

A new unchecked exception was created to explicitly represent the case where no valid exchange rate can be determined between two currencies. Placing it in `hub-common` allows it to be caught by the global web-layer handler.

#### 2. `CurrencyConversionServiceImpl` (MODIFIED)
`hub-app/.../service/currency/CurrencyConversionServiceImpl.java`

The conversion logic was restructured with a **three-layer fallback chain**:
1. Use `snapshot.getExchangeRate()` (the rate persisted on the ETrade snapshot itself).
2. If null or invalid, look up the **most recent rate from the local DB** (`ExchangeRateRecordRepository`), which is populated by the MURC Excel import job.
3. If still unavailable, query the **live Frankfurter API** as a last resort.

If all three sources fail, the method now **throws `CurrencyConversionException`** instead of silently returning the raw USD value as if it were JPY. The original 1:1 silent corruption path is fully eliminated.

**A directional formula bug was also fixed in the same method.** The formula had been simplified to a generic `rawAmount * effectiveRate` for all pairs, which gave a ~150× error for JPY→USD conversions (it should divide). The fix restores explicit branching:

```java
// Before (generic multiply — wrong for JPY→USD):
if (effectiveRate != null && effectiveRate > 0) {
    return Math.round(rawAmount * effectiveRate);
}

// After (directional):
if (effectiveRate != null && effectiveRate > 0) {
    if (sourceCurrency == CurrencyCode.USD && targetCurrency == CurrencyCode.JPY) {
        return Math.round(rawAmount * effectiveRate);
    } else if (sourceCurrency == CurrencyCode.JPY && targetCurrency == CurrencyCode.USD) {
        return Math.round(rawAmount / effectiveRate);  // divide, not multiply
    }
}
throw new CurrencyConversionException("No valid exchange rate found between "
        + sourceCurrency + " and " + targetCurrency);
```

Note: the `findLatestExchangeRateFromDb` inverse-lookup branch (which handles JPY→USD by reading the stored USD/JPY record) correctly returns the USD/JPY rate. The division in the formula above converts it correctly to the JPY→USD direction.

#### 3. `WebGlobalExceptionHandler` (MODIFIED)
`hub-common/.../advice/WebGlobalExceptionHandler.java`

A dedicated `@ExceptionHandler(CurrencyConversionException.class)` handler was added. When the conversion fails, the API now returns:
- **HTTP 422 Unprocessable Entity**
- Body: `ApiResponse` with `errorCode = "CURRENCY_CONVERSION_FAILED"` and a descriptive message naming the two currencies involved.

This directly satisfies Claude's requirement to _"surface a validation error/warning to the API response so the UI can flag the affected holding."_

#### 4. Tests added / fixed

| Test file | Change |
|---|---|
| `CurrencyConversionServiceImplTest` | Renamed `shouldReturnRawAmountWhenExchangeRateInvalidAndNoRepo` → `shouldThrowExceptionWhenExchangeRateInvalidAndNoRepo`; assertions updated to expect `CurrencyConversionException` for all three invalid-rate inputs (`null`, `0.0`, `-1.0`). |
| `CurrencyConversionServiceImplTest` | Added `shouldFallbackToDbRateWithDivisionForJpyToUsd`: seeds a DB record with USD/JPY = 150, converts JPY 150,000 → USD with a null snapshot rate, asserts result = 1,000 USD (i.e., the division path through the inverse lookup works end-to-end). |
| `AssetSimulationEngineImplTest` | Added `shouldThrowExceptionWhenETradeHasNullExchangeRateAndNoFallbackAvailable`: seeds an ETrade snapshot with `currentAccountValue = MonetaryAmount(50000, USD)` and `exchangeRate = null`, injects a `CurrencyConversionServiceImpl` with no repository or API client, asserts that `engine.runSimulation(30)` throws `CurrencyConversionException` with a message containing `"No valid exchange rate found between USD and JPY"`. This is the exact test case Claude requested. |

### Remaining scope (not part of this fix)

The architectural `@Embeddable Money` refactor described above is a separate, longer-term initiative tracked under Task 1 in this document. It structurally prevents the currency-loss problem at the schema level and is complementary to — not a replacement for — the runtime guard implemented here.
