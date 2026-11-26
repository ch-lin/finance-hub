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
package ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto;

import java.util.List;

import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.command.SaveETradeExtensionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitEsppPurchaseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuEventResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuGrantResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuVestScheduleResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeCashTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeEsppStockLotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeRsuStockLotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.StockLotResult;
import ch.lin.finance.hub.backend.api.app.service.base.MonetaryValue;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.request.ETradeExtensionRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.response.CompanyStockResponse;
import ch.lin.finance.hub.backend.api.controller.base.MonetaryValueRequest;
import ch.lin.finance.hub.backend.api.controller.base.MonetaryValueResponse;

/**
 * Mapper for converting between Company Stock DTOs and domain commands/results.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface CompanyStockMapper {

    /**
     * Converts an ETradeExtensionRequest to a SaveETradeExtensionCommand.
     *
     * @param request The extension request payload.
     * @return The corresponding SaveETradeExtensionCommand.
     */
    SaveETradeExtensionCommand toCommand(ETradeExtensionRequest request);

    /**
     * Converts an ETradeAccountSnapshotResult to a
     * CompanyStockResponse.Snapshot.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding Snapshot DTO.
     */
    CompanyStockResponse.Snapshot toSnapshotResponse(ETradeAccountSnapshotResult result, @Context String currentUserId);

    /**
     * Converts a StockLotResult to a CompanyStockResponse.StockLot.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding StockLot DTO.
     */
    default CompanyStockResponse.StockLot toStockLotResponse(StockLotResult result, @Context String currentUserId) {
        if (result == null) {
            return null;
        }
        return switch (result) {
            case ETradeEsppStockLotResult espp ->
                toEsppStockLotResponse(espp, currentUserId);
            case ETradeRsuStockLotResult rsu ->
                toRsuStockLotResponse(rsu, currentUserId);
            default ->
                null;
        };
    }

    /**
     * Converts an ETradeEsppStockLotResult to a
     * CompanyStockResponse.EsppStockLot.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding EsppStockLot DTO.
     */
    CompanyStockResponse.EsppStockLot toEsppStockLotResponse(ETradeEsppStockLotResult result,
            @Context String currentUserId);

    /**
     * Converts an ETradeRsuStockLotResult to a
     * CompanyStockResponse.RsuStockLot.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding RsuStockLot DTO.
     */
    CompanyStockResponse.RsuStockLot toRsuStockLotResponse(ETradeRsuStockLotResult result,
            @Context String currentUserId);

    /**
     * Converts an ETradeCashTransactionResult to a
     * CompanyStockResponse.CashTransaction.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding CashTransaction DTO.
     */
    CompanyStockResponse.CashTransaction toCashTransactionResponse(ETradeCashTransactionResult result,
            @Context String currentUserId);

    /**
     * Converts an ETradeBenefitEsppPurchaseResult to a
     * CompanyStockResponse.EsppPurchase.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding EsppPurchase DTO.
     */
    CompanyStockResponse.EsppPurchase toEsppPurchaseResponse(ETradeBenefitEsppPurchaseResult result,
            @Context String currentUserId);

    /**
     * Converts an ETradeBenefitRsuGrantResult to a
     * CompanyStockResponse.RsuGrant.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding RsuGrant DTO.
     */
    CompanyStockResponse.RsuGrant toRsuGrantResponse(ETradeBenefitRsuGrantResult result, @Context String currentUserId);

    /**
     * Converts an ETradeBenefitRsuEventResult to a
     * CompanyStockResponse.RsuEvent.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding RsuEvent DTO.
     */
    CompanyStockResponse.RsuEvent toRsuEventResponse(ETradeBenefitRsuEventResult result, @Context String currentUserId);

    /**
     * Converts an ETradeBenefitRsuVestScheduleResult to a
     * CompanyStockResponse.RsuVestSchedule.
     *
     * @param result The domain result.
     * @param currentUserId The ID of the currently authenticated user.
     * @return The corresponding RsuVestSchedule DTO.
     */
    CompanyStockResponse.RsuVestSchedule toRsuVestScheduleResponse(ETradeBenefitRsuVestScheduleResult result,
            @Context String currentUserId);

    /**
     * Converts a list of ETradeAccountSnapshotResult to a list of
     * CompanyStockResponse.Snapshot.
     *
     * @param results The domain results.
     * @param currentUserId The ID of the currently authenticated user.
     * @return A list of Snapshot DTOs.
     */
    List<CompanyStockResponse.Snapshot> toSnapshotResponseList(List<ETradeAccountSnapshotResult> results,
            @Context String currentUserId);

    /**
     * Converts a list of ETradeStockLotResult to a list of
     * CompanyStockResponse.StockLot.
     *
     * @param results The domain results.
     * @param currentUserId The ID of the currently authenticated user.
     * @return A list of StockLot DTOs.
     */
    List<CompanyStockResponse.StockLot> toStockLotResponseList(List<StockLotResult> results,
            @Context String currentUserId);

    /**
     * Converts a list of ETradeCashTransactionResult to a list of
     * CompanyStockResponse.CashTransaction.
     *
     * @param results The domain results.
     * @param currentUserId The ID of the currently authenticated user.
     * @return A list of CashTransaction DTOs.
     */
    List<CompanyStockResponse.CashTransaction> toCashTransactionResponseList(List<ETradeCashTransactionResult> results,
            @Context String currentUserId);

    /**
     * Converts a list of ETradeBenefitEsppPurchaseResult to a list of
     * CompanyStockResponse.EsppPurchase.
     *
     * @param results The domain results.
     * @param currentUserId The ID of the currently authenticated user.
     * @return A list of EsppPurchase DTOs.
     */
    List<CompanyStockResponse.EsppPurchase> toEsppPurchaseResponseList(List<ETradeBenefitEsppPurchaseResult> results,
            @Context String currentUserId);

    /**
     * Converts a list of ETradeBenefitRsuGrantResult to a list of
     * CompanyStockResponse.RsuGrant.
     *
     * @param results The domain results.
     * @param currentUserId The ID of the currently authenticated user.
     * @return A list of RsuGrant DTOs.
     */
    List<CompanyStockResponse.RsuGrant> toRsuGrantResponseList(List<ETradeBenefitRsuGrantResult> results,
            @Context String currentUserId);

    /**
     * Converts a list of ETradeBenefitRsuEventResult to a list of
     * CompanyStockResponse.RsuEvent.
     *
     * @param results The domain results.
     * @param currentUserId The ID of the currently authenticated user.
     * @return A list of RsuEvent DTOs.
     */
    List<CompanyStockResponse.RsuEvent> toRsuEventResponseList(List<ETradeBenefitRsuEventResult> results,
            @Context String currentUserId);

    /**
     * Converts a list of ETradeBenefitRsuVestScheduleResult to a list of
     * CompanyStockResponse.RsuVestSchedule.
     *
     * @param results The domain results.
     * @param currentUserId The ID of the currently authenticated user.
     * @return A list of RsuVestSchedule DTOs.
     */
    List<CompanyStockResponse.RsuVestSchedule> toRsuVestScheduleResponseList(
            List<ETradeBenefitRsuVestScheduleResult> results, @Context String currentUserId);

    /**
     * Converts a MonetaryValue domain model to a MonetaryValueResponse DTO.
     *
     * @param monetaryValue The domain model.
     * @return The corresponding DTO.
     */
    MonetaryValueResponse toMonetaryValueResponse(MonetaryValue monetaryValue);

    /**
     * Converts a MonetaryValueRequest DTO to a MonetaryValue domain model.
     *
     * @param request The request DTO.
     * @return The corresponding domain model.
     */
    MonetaryValue toMonetaryValue(MonetaryValueRequest request);

    /**
     * Applies privacy masking to Snapshot response if the current user is not
     * authorized to view the data.
     *
     * @param result The domain result containing original data.
     * @param response The target response to mask.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applySnapshotMasking(ETradeAccountSnapshotResult result,
            @MappingTarget CompanyStockResponse.Snapshot response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setTotalAccountValue(null);
            response.setCurrentAccountValue(null);
            response.setPotentialBenefitValue(null);
            response.setEsppPurchasedQty(null);
            response.setRsGrantedQty(null);
        }
    }

    /**
     * Applies privacy masking to StockLot response if the current user is not
     * authorized to view the data.
     *
     * @param result The domain result containing original data.
     * @param response The target response to mask.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applyStockLotMasking(StockLotResult result,
            @MappingTarget CompanyStockResponse.StockLot response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setPrice(null);
            response.setQuantity(null);
            response.setSellableQuantity(null);
            response.setEstMarketValue(null);
            response.setExpectedGainLoss(null);
            response.setEstCostBasis(null);
            response.setExpectedGainLossPerShare(null);
            switch (response) {
                case CompanyStockResponse.EsppStockLot espp -> {
                    espp.setPurchasePrice(null);
                    espp.setPurchasedQty(null);
                    espp.setPurchaseDateFmv(null);
                    espp.setGrantDateFmv(null);
                }
                case CompanyStockResponse.RsuStockLot rsu -> {
                    rsu.setPendingSaleQty(null);
                }
                default -> {
                }
            }
        }
    }

    /**
     * Applies privacy masking to CashTransaction response if the current user
     * is not authorized to view the data.
     *
     * @param result The domain result containing original data.
     * @param response The target response to mask.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applyCashTransactionMasking(ETradeCashTransactionResult result,
            @MappingTarget CompanyStockResponse.CashTransaction response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setAmount(null);
            response.setDescription(PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.COMPANY_STOCK));
        }
    }

    /**
     * Applies privacy masking to EsppPurchase response if the current user is
     * not authorized to view the data.
     *
     * @param result The domain result containing original data.
     * @param response The target response to mask.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applyEsppPurchaseMasking(ETradeBenefitEsppPurchaseResult result,
            @MappingTarget CompanyStockResponse.EsppPurchase response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setPurchasePrice(null);
            response.setPurchasedQty(null);
            response.setSellableQty(null);
            response.setEstMarketValue(null);
            response.setGrantDateFmv(null);
            response.setPurchaseDateFmv(null);
        }
    }

    /**
     * Applies privacy masking to RsuGrant response if the current user is not
     * authorized to view the data.
     *
     * @param result The domain result containing original data.
     * @param response The target response to mask.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applyRsuGrantMasking(ETradeBenefitRsuGrantResult result,
            @MappingTarget CompanyStockResponse.RsuGrant response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setGrantedQty(null);
            response.setVestedQty(null);
            response.setSellableQty(null);
            response.setEstMarketValue(null);
        }
    }

    /**
     * Applies privacy masking to RsuEvent response if the current user is not
     * authorized to view the data.
     *
     * @param result The domain result containing original data.
     * @param response The target response to mask.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applyRsuEventMasking(ETradeBenefitRsuEventResult result,
            @MappingTarget CompanyStockResponse.RsuEvent response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setQuantity(null);
        }
    }

    /**
     * Applies privacy masking to RsuVestSchedule response if the current user
     * is not authorized to view the data.
     *
     * @param result The domain result containing original data.
     * @param response The target response to mask.
     * @param currentUserId The ID of the currently authenticated user.
     */
    @AfterMapping
    default void applyRsuVestScheduleMasking(ETradeBenefitRsuVestScheduleResult result,
            @MappingTarget CompanyStockResponse.RsuVestSchedule response, @Context String currentUserId) {
        if (PrivacyMaskingUtil.shouldMask(currentUserId, result.getOwnerUserId(), result.getPrivacyLevel())) {
            response.setGrantedQty(null);
            response.setVestedQty(null);
            response.setReleasedQty(null);
            response.setSellableQty(null);
            response.setTaxableGain(null);
            response.setTaxWithheld(null);
            response.setTotalTaxesPaid(null);
        }
    }
}
