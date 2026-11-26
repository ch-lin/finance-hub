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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result;

import java.time.LocalDate;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NisaContributionSettingResult extends BaseResult {

    private Long id;
    private LocalDate recordDate;
    private String fundCode;
    private String fundName;
    private String course;
    private Long settingAmount;
    private Double expectedReturnRate;
    private String accountType;

    public static NisaContributionSettingResult fromEntity(NisaContributionSetting entity) {
        if (entity == null) {
            return null;
        }
        NisaContributionSettingResult result = new NisaContributionSettingResult();
        result.setId(entity.getId());
        result.setRecordDate(entity.getRecordDate());
        result.setFundCode(entity.getFundCode());
        result.setFundName(entity.getFundName());
        result.setCourse(entity.getCourse());
        result.setSettingAmount(entity.getSettingAmount());
        result.setExpectedReturnRate(entity.getExpectedReturnRate());
        result.setAccountType(entity.getAccountType());
        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(entity.getPrivacyLevel());
        return result;
    }
}
