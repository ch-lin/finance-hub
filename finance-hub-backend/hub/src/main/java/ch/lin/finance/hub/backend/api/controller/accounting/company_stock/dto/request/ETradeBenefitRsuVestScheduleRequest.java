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
package ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.request;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.controller.base.MonetaryValueRequest;

/**
 * Request payload for an E-Trade RSU vesting schedule.
 *
 * @param grantNumber    The associated grant number.
 * @param vestPeriod     The vest period identifier or number.
 * @param vestDate       The date when the shares vest.
 * @param grantedQty     The quantity of shares granted for this period.
 * @param vestedQty      The quantity of shares vested in this period.
 * @param releasedQty    The quantity of shares released.
 * @param sellableQty    The quantity of shares available to sell.
 * @param taxableGain    The taxable gain from the vested shares.
 * @param taxWithheld    The amount of tax withheld.
 * @param totalTaxesPaid The total taxes paid.
 * @param taxStatus      The tax status of the vesting event.
 */
public record ETradeBenefitRsuVestScheduleRequest(
        String grantNumber, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.grantNumber
        Integer vestPeriod, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.vestPeriod
        LocalDate vestDate, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.vestDate
        Double grantedQty, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.grantedQty
        Double vestedQty, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.vestedQty
        Double releasedQty, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.releasedQty
        Double sellableQty, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.sellableQty
        MonetaryValueRequest taxableGain, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.taxableGain
        MonetaryValueRequest taxWithheld, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.taxWithheld
        MonetaryValueRequest totalTaxesPaid, // Maps to SaveETradeExtensionCommand.RsuVestSchedule.totalTaxesPaid
        String taxStatus) { // Maps to SaveETradeExtensionCommand.RsuVestSchedule.taxStatus
}
