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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.command;

import java.time.LocalDate;
import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.base.MonetaryValue;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeRecordType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SaveETradeExtensionCommand {

    private LocalDate recordDate; // From ETradeExtensionRequest.recordDate -> ETradeAccountSnapshot.recordDate
    private MonetaryValue totalAccountValue; // From ETradeExtensionRequest.totalAccountValue -> ETradeAccountSnapshot.totalAccountValue
    private MonetaryValue currentAccountValue; // From ETradeExtensionRequest.currentAccountValue -> ETradeAccountSnapshot.currentAccountValue
    private MonetaryValue potentialBenefitValue; // From ETradeExtensionRequest.potentialBenefitValue -> ETradeAccountSnapshot.potentialBenefitValue
    private Double esppPurchasedQty; // From ETradeExtensionRequest.esppPurchasedQty -> ETradeAccountSnapshot.esppPurchasedQty
    private Double rsGrantedQty; // From ETradeExtensionRequest.rsGrantedQty -> ETradeAccountSnapshot.rsGrantedQty
    private Double exchangeRate; // From ETradeExtensionRequest.exchangeRate -> ETradeAccountSnapshot.exchangeRate

    private List<Lot> lots; // From ETradeExtensionRequest.lots -> List<ETradeStockLot>
    private List<CashTransaction> cashTransactions; // From ETradeExtensionRequest.cashTransactions -> List<ETradeCashTransaction>
    private List<EsppPurchase> esppPurchases; // From ETradeExtensionRequest.esppPurchases -> List<ETradeBenefitEsppPurchase>
    private List<RsuGrant> rsuGrants; // From ETradeExtensionRequest.rsuGrants -> List<ETradeBenefitRsuGrant>
    private List<RsuEvent> rsuEvents; // From ETradeExtensionRequest.rsuEvents -> List<ETradeBenefitRsuEvent>
    private List<RsuVestSchedule> rsuVestSchedules; // From ETradeExtensionRequest.rsuVestSchedules -> List<ETradeBenefitRsuVestSchedule>

    @Data
    @Builder
    public static class Lot {
        private ETradeRecordType recordType; // From ETradeExtensionLotRequest.recordType -> ETradeStockLot.recordType
        private String symbol; // From ETradeExtensionLotRequest.symbol -> ETradeStockLot.symbol
        private LocalDate recordDate; // From ETradeExtensionLotRequest.recordDate -> ETradeStockLot.recordDate
        private MonetaryValue price; // From ETradeExtensionLotRequest.price -> ETradeStockLot.price
        private Double quantity; // From ETradeExtensionLotRequest.quantity -> ETradeStockLot.quantity
        private Double sellableQuantity; // From ETradeExtensionLotRequest.sellableQuantity -> ETradeStockLot.sellableQuantity
        private MonetaryValue estMarketValue; // From ETradeExtensionLotRequest.estMarketValue -> ETradeStockLot.estMarketValue
        private MonetaryValue expectedGainLoss; // From ETradeExtensionLotRequest.expectedGainLoss -> ETradeStockLot.expectedGainLoss
        private Double discountPercent; // From ETradeExtensionLotRequest.discountPercent -> ETradeStockLot.discountPercent
    }

    @Data
    @Builder
    public static class CashTransaction {
        private LocalDate transactionDate; // From ETradeExtensionCashTransactionRequest.transactionDate -> ETradeCashTransaction.transactionDate
        private String description; // From ETradeExtensionCashTransactionRequest.description -> ETradeCashTransaction.description
        private MonetaryValue amount; // From ETradeExtensionCashTransactionRequest.amount -> ETradeCashTransaction.amount
    }

    @Data
    @Builder
    public static class EsppPurchase {
        private LocalDate purchaseDate; // From ETradeBenefitEsppPurchaseRequest.purchaseDate -> ETradeBenefitEsppPurchase.purchaseDate
        private MonetaryValue purchasePrice; // From ETradeBenefitEsppPurchaseRequest.purchasePrice -> ETradeBenefitEsppPurchase.purchasePrice
        private Double purchasedQty; // From ETradeBenefitEsppPurchaseRequest.purchasedQty -> ETradeBenefitEsppPurchase.purchasedQty
        private Double sellableQty; // From ETradeBenefitEsppPurchaseRequest.sellableQty -> ETradeBenefitEsppPurchase.sellableQty
        private MonetaryValue estMarketValue; // From ETradeBenefitEsppPurchaseRequest.estMarketValue -> ETradeBenefitEsppPurchase.estMarketValue
        private LocalDate grantDate; // From ETradeBenefitEsppPurchaseRequest.grantDate -> ETradeBenefitEsppPurchase.grantDate
        private Double discountPercent; // From ETradeBenefitEsppPurchaseRequest.discountPercent -> ETradeBenefitEsppPurchase.discountPercent
        private MonetaryValue grantDateFmv; // From ETradeBenefitEsppPurchaseRequest.grantDateFmv -> ETradeBenefitEsppPurchase.grantDateFmv
        private MonetaryValue purchaseDateFmv; // From ETradeBenefitEsppPurchaseRequest.purchaseDateFmv -> ETradeBenefitEsppPurchase.purchaseDateFmv
        private Boolean qualifiedPlan; // From ETradeBenefitEsppPurchaseRequest.qualifiedPlan -> ETradeBenefitEsppPurchase.qualifiedPlan
        private LocalDate transferableDate; // From ETradeBenefitEsppPurchaseRequest.transferableDate -> ETradeBenefitEsppPurchase.transferableDate
    }

    @Data
    @Builder
    public static class RsuGrant {
        private String grantNumber; // From ETradeBenefitRsuGrantRequest.grantNumber -> ETradeBenefitRsuGrant.grantNumber
        private LocalDate grantDate; // From ETradeBenefitRsuGrantRequest.grantDate -> ETradeBenefitRsuGrant.grantDate
        private Double grantedQty; // From ETradeBenefitRsuGrantRequest.grantedQty -> ETradeBenefitRsuGrant.grantedQty
        private Double vestedQty; // From ETradeBenefitRsuGrantRequest.vestedQty -> ETradeBenefitRsuGrant.vestedQty
        private Double sellableQty; // From ETradeBenefitRsuGrantRequest.sellableQty -> ETradeBenefitRsuGrant.sellableQty
        private MonetaryValue estMarketValue; // From ETradeBenefitRsuGrantRequest.estMarketValue -> ETradeBenefitRsuGrant.estMarketValue
    }

    @Data
    @Builder
    public static class RsuEvent {
        private String grantNumber; // From ETradeBenefitRsuEventRequest.grantNumber -> ETradeBenefitRsuEvent.grantNumber
        private LocalDate eventDate; // From ETradeBenefitRsuEventRequest.eventDate -> ETradeBenefitRsuEvent.eventDate
        private String eventType; // From ETradeBenefitRsuEventRequest.eventType -> ETradeBenefitRsuEvent.eventType
        private Double quantity; // From ETradeBenefitRsuEventRequest.quantity -> ETradeBenefitRsuEvent.quantity
    }

    @Data
    @Builder
    public static class RsuVestSchedule {
        private String grantNumber; // From ETradeBenefitRsuVestScheduleRequest.grantNumber -> ETradeBenefitRsuVestSchedule.grantNumber
        private Integer vestPeriod; // From ETradeBenefitRsuVestScheduleRequest.vestPeriod -> ETradeBenefitRsuVestSchedule.vestPeriod
        private LocalDate vestDate; // From ETradeBenefitRsuVestScheduleRequest.vestDate -> ETradeBenefitRsuVestSchedule.vestDate
        private Double grantedQty; // From ETradeBenefitRsuVestScheduleRequest.grantedQty -> ETradeBenefitRsuVestSchedule.grantedQty
        private Double vestedQty; // From ETradeBenefitRsuVestScheduleRequest.vestedQty -> ETradeBenefitRsuVestSchedule.vestedQty
        private Double releasedQty; // From ETradeBenefitRsuVestScheduleRequest.releasedQty -> ETradeBenefitRsuVestSchedule.releasedQty
        private Double sellableQty; // From ETradeBenefitRsuVestScheduleRequest.sellableQty -> ETradeBenefitRsuVestSchedule.sellableQty
        private MonetaryValue taxableGain; // From ETradeBenefitRsuVestScheduleRequest.taxableGain -> ETradeBenefitRsuVestSchedule.taxableGain
        private MonetaryValue taxWithheld; // From ETradeBenefitRsuVestScheduleRequest.taxWithheld -> ETradeBenefitRsuVestSchedule.taxWithheld
        private MonetaryValue totalTaxesPaid; // From ETradeBenefitRsuVestScheduleRequest.totalTaxesPaid -> ETradeBenefitRsuVestSchedule.totalTaxesPaid
        private String taxStatus; // From ETradeBenefitRsuVestScheduleRequest.taxStatus -> ETradeBenefitRsuVestSchedule.taxStatus
    }
}


