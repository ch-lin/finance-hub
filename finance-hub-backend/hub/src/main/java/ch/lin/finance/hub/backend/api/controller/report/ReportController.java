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

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ch.lin.finance.hub.backend.api.app.service.report.GenerateReportCommand;
import ch.lin.finance.hub.backend.api.app.service.report.ReportService;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.report.dto.GenerateReportRequest;
import jakarta.validation.Valid;

/**
 * REST controller for generating and downloading financial reports.
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    /** Logger for this controller. */
    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    /** Service for generating reports. */
    private final ReportService reportService;

    /**
     * Constructs a new ReportController.
     *
     * @param reportService The service used to generate reports.
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Generates and downloads a summary report in Markdown format.
     *
     * @param request The command object containing query parameters for start,
     *                end, type, and sortOption.
     * @return A {@link ResponseEntity} containing the Markdown file as a byte
     *         array.
     *         <p>
     *         Example cURL requests:
     * 
     *         <pre>
     * {@code
     * # Get a full year report for 2024 (defaults to ALL types, DATE_DESC sorting)
     * curl -X GET "http://localhost:8081/reports/summary?start=2024-01&end=2024-12" -OJ
     *
     * # Get a specific month report (e.g., October 2024)
     * curl -X GET "http://localhost:8081/reports/summary?start=2024-10&end=2024-10" -OJ
     *
     * # Get only credit card transactions for a specific month
     * curl -X GET "http://localhost:8081/reports/summary?start=2024-10&end=2024-10&type=CREDIT_CARD" -OJ
     *
     * # Get a shopping report for a full year sorted by highest amount
     * curl -X GET "http://localhost:8081/reports/summary?start=2024-01&end=2024-12&type=SHOPPING&sortOption=AMOUNT_DESC" -OJ
     *
     * # Get all reports from a specific start date until now (Half-open range)
     * curl -X GET "http://localhost:8081/reports/summary?start=2024-10" -OJ
     * }
     * </pre>
     */
    @GetMapping(value = "/summary")
    public ResponseEntity<byte[]> downloadSummaryReport(@Valid GenerateReportRequest request) {
        logger.info("Received request to generate summary report: {}", request);

        GenerateReportCommand command = GenerateReportCommand.builder()
                .start(request.getStart())
                .end(request.getEnd())
                .types(request.getTypes())
                .sortOption(request.getSortOption())
                .currentUserId(SecurityUtil.getCurrentUserId())
                .build();

        String markdownReport = reportService.generateSummaryReport(command);
        byte[] reportBytes = markdownReport.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/markdown"));
        String sourcesSuffix = (request.getTypes() == null || request.getTypes().isEmpty()) ? "all"
                : request.getTypes().stream().map(type -> type.name()).collect(Collectors.joining("-")).toLowerCase();
        String filename = "finance-summary-" + sourcesSuffix + "-" + command.getReportPeriodString() + ".md";
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok().headers(headers).body(reportBytes);
    }
}
