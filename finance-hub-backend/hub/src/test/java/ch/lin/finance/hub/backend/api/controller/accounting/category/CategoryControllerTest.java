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
package ch.lin.finance.hub.backend.api.controller.accounting.category;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.finance.hub.backend.api.app.service.accounting.category.CategoryService;
import ch.lin.finance.hub.backend.api.app.service.accounting.category.command.SaveCategoryCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.category.result.TransactionCategoryResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.category.dto.CategoryMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.category.dto.CategoryResponse;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CategoryService categoryService;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryController categoryController;

    private MockedStatic<SecurityUtil> mockedSecurityUtil;

    private final String currentUserId = "test-user-id";

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(categoryController).build();
        mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class);
        mockedSecurityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(currentUserId);
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        if (mockedSecurityUtil != null) {
            mockedSecurityUtil.close();
        }
    }

    @Test
    void getAllCategories_shouldReturnListOfCategories() throws Exception {
        TransactionCategoryResult result = new TransactionCategoryResult();
        CategoryResponse response = CategoryResponse.builder().id(1L).name("Food").build();

        when(categoryService.getAllCategories()).thenReturn(List.of(result));
        when(categoryMapper.toResponse(result, currentUserId)).thenReturn(response);

        mockMvc.perform(get("/transaction-categories")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Food"));
    }

    @Test
    void getCategoryById_shouldReturnCategory() throws Exception {
        TransactionCategoryResult result = new TransactionCategoryResult();
        CategoryResponse response = CategoryResponse.builder().id(1L).name("Food").build();

        when(categoryService.getCategoryById(1L)).thenReturn(result);
        when(categoryMapper.toResponse(result, currentUserId)).thenReturn(response);

        mockMvc.perform(get("/transaction-categories/1")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Food"));
    }

    @Test
    void createCategory_shouldReturnCreatedCategory() throws Exception {
        TransactionCategoryResult result = new TransactionCategoryResult();
        CategoryResponse response = CategoryResponse.builder().id(1L).name("Food").build();
        SaveCategoryCommand command = new SaveCategoryCommand("Food", true, false, null);

        when(categoryMapper.toCommand(any())).thenReturn(command);
        when(categoryService.createCategory(command)).thenReturn(result);
        when(categoryMapper.toResponse(result, currentUserId)).thenReturn(response);

        String requestJson = """
                {
                    "name": "Food",
                    "isEssential": true,
                    "isSimulationDuplicate": false
                }
                """;

        mockMvc.perform(post("/transaction-categories")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Food"));
    }

    @Test
    void updateCategory_shouldReturnUpdatedCategory() throws Exception {
        TransactionCategoryResult result = new TransactionCategoryResult();
        CategoryResponse response = CategoryResponse.builder().id(1L).name("Food").build();
        SaveCategoryCommand command = new SaveCategoryCommand("Food", true, false, null);

        when(categoryMapper.toCommand(any())).thenReturn(command);
        when(categoryService.updateCategory(eq(1L), eq(command))).thenReturn(result);
        when(categoryMapper.toResponse(result, currentUserId)).thenReturn(response);

        String requestJson = """
                {
                    "name": "Food",
                    "isEssential": true,
                    "isSimulationDuplicate": false
                }
                """;

        mockMvc.perform(put("/transaction-categories/1")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Food"));
    }

    @Test
    void createCategory_missingIsEssential_shouldReturnBadRequest() throws Exception {
        String requestJson = """
                {
                    "name": "Food",
                    "isSimulationDuplicate": false
                }
                """;

        mockMvc.perform(post("/transaction-categories")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCategory_missingIsSimulationDuplicate_shouldReturnBadRequest() throws Exception {
        String requestJson = """
                {
                    "name": "Food",
                    "isEssential": true
                }
                """;

        mockMvc.perform(put("/transaction-categories/1")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteCategory_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/transaction-categories/1")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isNoContent());

        verify(categoryService).deleteCategory(1L);
    }
}
