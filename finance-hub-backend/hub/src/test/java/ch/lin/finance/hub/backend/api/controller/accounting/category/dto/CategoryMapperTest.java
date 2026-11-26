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
package ch.lin.finance.hub.backend.api.controller.accounting.category.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import ch.lin.finance.hub.backend.api.app.service.accounting.category.command.SaveCategoryCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.category.result.TransactionCategoryResult;
import ch.lin.finance.hub.backend.api.app.util.PrivacyMaskingUtil;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;

class CategoryMapperTest {

    private final CategoryMapper mapper = Mappers.getMapper(CategoryMapper.class);

    @Test
    void toCommand_MapsCorrectly() {
        CategoryRequest request = new CategoryRequest("Food", true, false, 10L);

        SaveCategoryCommand command = mapper.toCommand(request);
        assertEquals("Food", command.name());
        assertEquals(true, command.isEssential());
        assertEquals(false, command.isSimulationDuplicate());
        assertEquals(10L, command.parentId());
    }

    @Test
    void toResponse_MapsCorrectly_AndDoesNotMaskWhenFalse() {
        TransactionCategoryResult result = new TransactionCategoryResult();
        result.setId(1L);
        result.setName("Food");
        result.setEssential(true);
        result.setSimulationDuplicate(false);
        result.setParentId(10L);
        result.setOwnerUserId("owner1");
        result.setPrivacyLevel(PrivacyLevel.SHARED);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask(any(), any(), any()))
                    .thenReturn(false);

            CategoryResponse response = mapper.toResponse(result, "user1");

            assertEquals(1L, response.getId());
            assertEquals("Food", response.getName());
            assertEquals(true, response.isEssential());
            assertEquals(false, response.isSimulationDuplicate());
            assertEquals(10L, response.getParentId());
        }
    }

    @Test
    void toResponse_ShouldApplyMasking_WhenPrivacyMaskingUtilReturnsTrue() {
        TransactionCategoryResult result = new TransactionCategoryResult();
        result.setId(1L);
        result.setName("Secret Category");
        result.setOwnerUserId("owner1");
        result.setPrivacyLevel(PrivacyLevel.PRIVATE);

        try (MockedStatic<PrivacyMaskingUtil> mockedMaskingUtil = Mockito.mockStatic(PrivacyMaskingUtil.class)) {
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.shouldMask("user1", "owner1", PrivacyLevel.PRIVATE))
                    .thenReturn(true);
            mockedMaskingUtil.when(() -> PrivacyMaskingUtil.getMaskedName(PrivacyMaskingUtil.MaskingType.CATEGORY))
                    .thenReturn("Masked Category");

            CategoryResponse response = mapper.toResponse(result, "user1");

            assertEquals("Masked Category", response.getName());
        }
    }

    @Test
    void map_WithNulls_ReturnsNull() {
        assertNull(mapper.toCommand(null));
        assertNull(mapper.toResponse(null, "user1"));
    }
}
