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
package ch.lin.finance.hub.backend.api.app.service.family.result;

import ch.lin.finance.hub.backend.api.app.service.base.BaseResult;
import ch.lin.finance.hub.backend.api.domain.model.family.FamilyGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyGroupResult extends BaseResult {
    private Long id;
    private String name;
    private String inviteCode;
    private LocalDateTime inviteCodeExpiresAt;

    public static FamilyGroupResult fromEntity(FamilyGroup entity) {
        if (entity == null) {
            return null;
        }
        
        FamilyGroupResult result = FamilyGroupResult.builder()
                .id(entity.getId())
                .name(entity.getName())
                .inviteCode(entity.getInviteCode())
                .inviteCodeExpiresAt(entity.getInviteCodeExpiresAt())
                .build();
                
        return result;
    }
}
