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
package ch.lin.finance.hub.backend.api.controller.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.finance.hub.backend.api.app.service.report.GenerateReportCommand;
import ch.lin.finance.hub.backend.api.app.service.report.ReportService;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    private MockMvc mockMvc;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reportController).build();
    }

    @Test
    @DisplayName("Should generate summary report with all types when types are not provided")
    void downloadSummaryReport_NoTypesProvided_ReturnsAllTypes() throws Exception {
        String markdownContent = "# Test Report";
        when(reportService.generateSummaryReport(any(GenerateReportCommand.class))).thenReturn(markdownContent);

        mockMvc.perform(get("/reports/summary")
                .param("start", "2024-01")
                .param("end", "2024-12"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown"))
                .andExpect(content().string(markdownContent));

        verify(reportService, times(1)).generateSummaryReport(any(GenerateReportCommand.class));
    }

    @Test
    @DisplayName("Should generate summary report with specific types")
    void downloadSummaryReport_WithSpecificTypes_ReturnsReport() throws Exception {
        String markdownContent = "# Specific Report";
        when(reportService.generateSummaryReport(any(GenerateReportCommand.class))).thenReturn(markdownContent);

        mockMvc.perform(get("/reports/summary")
                .param("start", "2024-01-01")
                .param("end", "2024-12-31")
                .param("types", "AMAZON", "CREDIT_CARD")
                .param("sortOption", "AMOUNT_DESC"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().string(markdownContent));

        verify(reportService, times(1)).generateSummaryReport(any(GenerateReportCommand.class));
    }

    @Test
    @DisplayName("Should generate summary report with all types when types are empty")
    void downloadSummaryReport_EmptyTypesProvided_ReturnsAllTypes() throws Exception {
        String markdownContent = "# Empty Types Report";
        when(reportService.generateSummaryReport(any(GenerateReportCommand.class))).thenReturn(markdownContent);

        mockMvc.perform(get("/reports/summary")
                .param("start", "2024-01")
                .param("end", "2024-12")
                .param("types", ""))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/markdown"))
                .andExpect(content().string(markdownContent));

        verify(reportService, times(1)).generateSummaryReport(any(GenerateReportCommand.class));
    }
}
