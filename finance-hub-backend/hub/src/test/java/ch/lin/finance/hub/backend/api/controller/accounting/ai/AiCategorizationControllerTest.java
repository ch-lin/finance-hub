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
package ch.lin.finance.hub.backend.api.controller.accounting.ai;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.format.support.FormattingConversionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.finance.hub.backend.api.app.service.accounting.ai.AiCategorizationService;

@ExtendWith(MockitoExtension.class)
class AiCategorizationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiCategorizationService aiCategorizationService;

    @InjectMocks
    private AiCategorizationController aiCategorizationController;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        // Setup explicit Date formatters since @RequestParam LocalDate requires it in
        // standalone mode
        FormattingConversionService conversionService = new FormattingConversionService();
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(conversionService);

        mockMvc = MockMvcBuilders.standaloneSetup(aiCategorizationController)
                .setConversionService(conversionService)
                .build();
    }

    @Test
    void exportUncategorizedItems_shouldReturnCsvFile() throws Exception {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 12, 31);
        List<String> sources = List.of("BANK", "CREDIT_CARD");
        String mockCsvContent = "id,date,description\n1,2024-01-01,test";

        when(aiCategorizationService.exportUncategorizedItems(eq(startDate), eq(endDate), eq(sources)))
                .thenReturn(mockCsvContent);

        mockMvc.perform(get("/ai-categorization/export")
                .param("startDate", "2024-01-01")
                .param("endDate", "2024-12-31")
                .param("sources", "BANK")
                .param("sources", "CREDIT_CARD"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"uncategorized_items.csv\""))
                .andExpect(content().contentType(MediaType.parseMediaType("text/csv")))
                .andExpect(content().string(mockCsvContent));
    }

    @Test
    void exportUncategorizedItems_withoutParams_shouldReturnCsvFile() throws Exception {
        String mockCsvContent = "id,date,description\n1,2024-01-01,test";

        when(aiCategorizationService.exportUncategorizedItems(null, null, null))
                .thenReturn(mockCsvContent);

        mockMvc.perform(get("/ai-categorization/export"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"uncategorized_items.csv\""))
                .andExpect(content().contentType(MediaType.parseMediaType("text/csv")))
                .andExpect(content().string(mockCsvContent));
    }

    @Test
    void importCategorizedItems_shouldProcessFileAndReturnOk() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "test.csv",
                MediaType.TEXT_PLAIN_VALUE,
                "id,category\n1,Food".getBytes());

        mockMvc.perform(multipart("/ai-categorization/import")
                .file(mockFile))
                .andExpect(status().isOk());

        verify(aiCategorizationService).importCategorizedItems(any());
    }
}
