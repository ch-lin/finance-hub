/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.response;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import ch.lin.finance.hub.backend.api.controller.base.MonetaryValueResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Response DTO for Company Stock data. Contains structures for brokerage
 * account snapshots, stock lots, cash transactions, ESPP purchases, and RSU
 * records.
 */
public class CompanyStockResponse {

    /**
     * Account Snapshot response structure.
     */
    @Data
    @Builder
    public static class Snapshot {

        /**
         * Internal ID
         */
        private Long id;
        /**
         * Record date
         */
        private LocalDate recordDate;
        /**
         * Total account value
         */
        private MonetaryValueResponse totalAccountValue;
        /**
         * Current account value
         */
        private MonetaryValueResponse currentAccountValue;
        /**
         * Potential benefit value (e.g., unvested RSUs)
         */
        private MonetaryValueResponse potentialBenefitValue;
        /**
         * ESPP purchased quantity
         */
        private Double esppPurchasedQty;
        /**
         * RSU granted quantity
         */
        private Double rsGrantedQty;
        /**
         * Exchange rate
         */
        private Double exchangeRate;
    }

    /**
     * Stock Lot base response structure.
     */
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "recordType",
            visible = true
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = CompanyStockResponse.EsppStockLot.class, name = "ESPP"),
        @JsonSubTypes.Type(value = CompanyStockResponse.RsuStockLot.class, name = "RSU")
    })
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockLot {

        /**
         * Internal ID
         */
        private Long id;
        /**
         * Record type
         */
        private String recordType;
        /**
         * Stock symbol
         */
        private String symbol;
        /**
         * Record date
         */
        private LocalDate recordDate;
        /**
         * Acquisition price / cost basis
         */
        private MonetaryValueResponse price;
        /**
         * Total quantity
         */
        private Double quantity;
        /**
         * Sellable quantity
         */
        private Double sellableQuantity;
        /**
         * Estimated cost basis
         */
        private MonetaryValueResponse estCostBasis;
        /**
         * Estimated market value
         */
        private MonetaryValueResponse estMarketValue;
        /**
         * Expected unrealized gain/loss total
         */
        private MonetaryValueResponse expectedGainLoss;
        /**
         * Expected unrealized gain/loss per share
         */
        private MonetaryValueResponse expectedGainLossPerShare;
        /**
         * Capital gains status (e.g. Long Term / Short Term)
         */
        private String capitalGainsStatus;
        /**
         * Discount percent (if applicable)
         */
        private Double discountPercent;
    }

    /**
     * ESPP Stock Lot response structure.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class EsppStockLot extends StockLot {

        /**
         * Purchase date
         */
        private LocalDate purchaseDate;
        /**
         * Purchase price
         */
        private MonetaryValueResponse purchasePrice;
        /**
         * Purchased quantity
         */
        private Double purchasedQty;
        /**
         * Purchase date fair market value
         */
        private MonetaryValueResponse purchaseDateFmv;
        /**
         * Grant date fair market value
         */
        private MonetaryValueResponse grantDateFmv;
        /**
         * Offering/Grant date
         */
        private LocalDate grantDate;
        /**
         * Qualified plan flag
         */
        private String qualifiedPlan;
        /**
         * Transferable date
         */
        private LocalDate transferableDate;
    }

    /**
     * RSU Stock Lot response structure.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class RsuStockLot extends StockLot {

        /**
         * Grant contract number
         */
        private String grantNumber;
        /**
         * Grant date
         */
        private LocalDate grantDate;
        /**
         * Vest period tranche index
         */
        private Integer vestPeriod;
        /**
         * Vesting date
         */
        private LocalDate vestDate;
        /**
         * Release date
         */
        private LocalDate releaseDate;
        /**
         * Pending sale quantity
         */
        private Double pendingSaleQty;
    }

    /**
     * Cash Transaction response structure.
     */
    @Data
    @Builder
    public static class CashTransaction {

        /**
         * Internal ID
         */
        private Long id;
        /**
         * Transaction date
         */
        private LocalDate transactionDate;
        /**
         * Transaction description
         */
        private String description;
        /**
         * Transaction amount
         */
        private MonetaryValueResponse amount;
    }

    /**
     * Employee Stock Purchase Plan (ESPP) Purchase response structure.
     */
    @Data
    @Builder
    public static class EsppPurchase {

        /**
         * Internal ID
         */
        private Long id;
        /**
         * Purchase date
         */
        private LocalDate purchaseDate;
        /**
         * Purchase price
         */
        private MonetaryValueResponse purchasePrice;
        /**
         * Purchased quantity
         */
        private Double purchasedQty;
        /**
         * Sellable quantity
         */
        private Double sellableQty;
        /**
         * Estimated market value
         */
        private MonetaryValueResponse estMarketValue;
        /**
         * Grant date
         */
        private LocalDate grantDate;
        /**
         * Purchase discount percent
         */
        private Double discountPercent;
        /**
         * Fair Market Value (FMV) on grant date
         */
        private MonetaryValueResponse grantDateFmv;
        /**
         * Fair Market Value (FMV) on purchase date
         */
        private MonetaryValueResponse purchaseDateFmv;
        /**
         * Indicates if it is a qualified plan
         */
        private Boolean qualifiedPlan;
        /**
         * Date when shares become transferable
         */
        private LocalDate transferableDate;
    }

    /**
     * Restricted Stock Unit (RSU) Grant response structure.
     */
    @Data
    @Builder
    public static class RsuGrant {

        /**
         * Internal ID
         */
        private Long id;
        /**
         * Grant number
         */
        private String grantNumber;
        /**
         * Grant date
         */
        private LocalDate grantDate;
        /**
         * Granted quantity
         */
        private Double grantedQty;
        /**
         * Vested quantity
         */
        private Double vestedQty;
        /**
         * Sellable quantity
         */
        private Double sellableQty;
        /**
         * Estimated market value
         */
        private MonetaryValueResponse estMarketValue;
    }

    /**
     * Restricted Stock Unit (RSU) Event response structure.
     */
    @Data
    @Builder
    public static class RsuEvent {

        /**
         * Internal ID
         */
        private Long id;
        /**
         * Grant number
         */
        private String grantNumber;
        /**
         * Event date
         */
        private LocalDate eventDate;
        /**
         * Event type (e.g., vest, release)
         */
        private String eventType;
        /**
         * Quantity involved in the event
         */
        private Double quantity;
    }

    /**
     * Restricted Stock Unit (RSU) Vest Schedule response structure.
     */
    @Data
    @Builder
    public static class RsuVestSchedule {

        /**
         * Internal ID
         */
        private Long id;
        /**
         * Grant number
         */
        private String grantNumber;
        /**
         * Vest period
         */
        private Integer vestPeriod;
        /**
         * Vest date
         */
        private LocalDate vestDate;
        /**
         * Granted quantity
         */
        private Double grantedQty;
        /**
         * Vested quantity
         */
        private Double vestedQty;
        /**
         * Released quantity
         */
        private Double releasedQty;
        /**
         * Sellable quantity
         */
        private Double sellableQty;
        /**
         * Taxable gain
         */
        private MonetaryValueResponse taxableGain;
        /**
         * Tax withheld
         */
        private MonetaryValueResponse taxWithheld;
        /**
         * Total taxes paid
         */
        private MonetaryValueResponse totalTaxesPaid;
        /**
         * Tax status
         */
        private String taxStatus;
    }
}
