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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.app.service.base.MonetaryValue;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuVestSchedule;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ETradeBenefitRsuVestScheduleResult extends BaseResult {
    private Long id; // From ETradeBenefitRsuVestSchedule.id (BaseEntity) -> CompanyStockResponse.RsuVestSchedule.id
    private String grantNumber; // From ETradeBenefitRsuVestSchedule.grantNumber -> CompanyStockResponse.RsuVestSchedule.grantNumber
    private Integer vestPeriod; // From ETradeBenefitRsuVestSchedule.vestPeriod -> CompanyStockResponse.RsuVestSchedule.vestPeriod
    private LocalDate vestDate; // From ETradeBenefitRsuVestSchedule.vestDate -> CompanyStockResponse.RsuVestSchedule.vestDate
    private Double grantedQty; // From ETradeBenefitRsuVestSchedule.grantedQty -> CompanyStockResponse.RsuVestSchedule.grantedQty
    private Double vestedQty; // From ETradeBenefitRsuVestSchedule.vestedQty -> CompanyStockResponse.RsuVestSchedule.vestedQty
    private Double releasedQty; // From ETradeBenefitRsuVestSchedule.releasedQty -> CompanyStockResponse.RsuVestSchedule.releasedQty
    private Double sellableQty; // From ETradeBenefitRsuVestSchedule.sellableQty -> CompanyStockResponse.RsuVestSchedule.sellableQty
    private MonetaryValue taxableGain; // From ETradeBenefitRsuVestSchedule.taxableGain -> CompanyStockResponse.RsuVestSchedule.taxableGain
    private MonetaryValue taxWithheld; // From ETradeBenefitRsuVestSchedule.taxWithheld -> CompanyStockResponse.RsuVestSchedule.taxWithheld
    private MonetaryValue totalTaxesPaid; // From ETradeBenefitRsuVestSchedule.totalTaxesPaid -> CompanyStockResponse.RsuVestSchedule.totalTaxesPaid
    private String taxStatus; // From ETradeBenefitRsuVestSchedule.taxStatus -> CompanyStockResponse.RsuVestSchedule.taxStatus

    public static ETradeBenefitRsuVestScheduleResult fromEntity(ETradeBenefitRsuVestSchedule entity) {
        if (entity == null) {
            return null;
        }
        ETradeBenefitRsuVestScheduleResult result = new ETradeBenefitRsuVestScheduleResult();
        result.setId(entity.getId());
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        result.setGrantNumber(entity.getGrantNumber());
        result.setVestPeriod(entity.getVestPeriod());
        result.setVestDate(entity.getVestDate());
        result.setGrantedQty(entity.getGrantedQty());
        result.setVestedQty(entity.getVestedQty());
        result.setReleasedQty(entity.getReleasedQty());
        result.setSellableQty(entity.getSellableQty());
        result.setTaxableGain(MonetaryValue.fromEntity(entity.getTaxableGain()));
        result.setTaxWithheld(MonetaryValue.fromEntity(entity.getTaxWithheld()));
        result.setTotalTaxesPaid(MonetaryValue.fromEntity(entity.getTotalTaxesPaid()));
        result.setTaxStatus(entity.getTaxStatus());
        return result;
    }
}
