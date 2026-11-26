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
package ch.lin.finance.hub.backend.api.app.service.accounting.pension.result;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PensionRecordResult extends BaseResult {
    private Long id;
    private LocalDate recordDate;
    private Long basicPensionAmount;
    private Long kouseiPensionAmount;
    private Long totalPensionAmount;
    private Integer kokuminCategory1Months;
    private Integer kokuminCategory3Months;
    private Integer kokuminTotalMonths;
    private Integer kouseiGeneralMonths;
    private Integer kouseiTotalMonths;
    private Integer totalEnrolledMonths;
    private Integer qualifyingMonths;
    private Long kokuminPremiumPaid;
    private Long kouseiPremiumPaid;
    private Long totalPremiumPaid;

    public static PensionRecordResult fromEntity(PensionRecord entity) {
        if (entity == null) {
            return null;
        }

        PensionRecordResult result = new PensionRecordResult();
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());

        result.setId(entity.getId());
        result.setRecordDate(entity.getRecordDate());
        result.setBasicPensionAmount(entity.getBasicPensionAmount());
        result.setKouseiPensionAmount(entity.getKouseiPensionAmount());
        result.setTotalPensionAmount(entity.getTotalPensionAmount());
        result.setKokuminCategory1Months(entity.getKokuminCategory1Months());
        result.setKokuminCategory3Months(entity.getKokuminCategory3Months());
        result.setKokuminTotalMonths(entity.getKokuminTotalMonths());
        result.setKouseiGeneralMonths(entity.getKouseiGeneralMonths());
        result.setKouseiTotalMonths(entity.getKouseiTotalMonths());
        result.setTotalEnrolledMonths(entity.getTotalEnrolledMonths());
        result.setQualifyingMonths(entity.getQualifyingMonths());
        result.setKokuminPremiumPaid(entity.getKokuminPremiumPaid());
        result.setKouseiPremiumPaid(entity.getKouseiPremiumPaid());
        result.setTotalPremiumPaid(entity.getTotalPremiumPaid());

        return result;
    }
}
