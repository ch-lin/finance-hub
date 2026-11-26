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
package ch.lin.finance.hub.backend.api.app.service.profile.child.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
public class ChildProfileResult extends BaseResult {

    private Long id;
    private String name;
    private java.time.LocalDate birthDate;

    public static ChildProfileResult fromEntity(ChildProfile entity) {
        if (entity == null) {
            return null;
        }

        ChildProfileResult result = ChildProfileResult.builder()
                .id(entity.getId())
                .name(entity.getName())
                .birthDate(entity.getBirthDate())
                .build();

        result.setOwnerUserId(entity.getOwnerUserId());
        result.setPrivacyLevel(PrivacyLevel.PRIVATE); // Assuming Child profiles are always private to the user

        return result;
    }
}
