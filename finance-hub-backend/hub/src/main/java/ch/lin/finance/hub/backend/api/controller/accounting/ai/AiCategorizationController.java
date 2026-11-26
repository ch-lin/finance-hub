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

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ch.lin.finance.hub.backend.api.app.service.accounting.ai.AiCategorizationService;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for handling AI-based categorization of accounting items.
 */
@RestController
@RequestMapping("/ai-categorization")
@RequiredArgsConstructor
public class AiCategorizationController {

    /** Service that handles AI categorization logic. */
    private final AiCategorizationService aiCategorizationService;

    /**
     * Exports uncategorized items as a CSV file.
     *
     * @param startDate The start date to filter items (optional).
     * @param endDate   The end date to filter items (optional).
     * @param sources   The list of sources to filter items (optional).
     * @return A ResponseEntity containing the CSV file.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportUncategorizedItems(@RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate, @RequestParam(required = false) List<String> sources) {
        String csv = aiCategorizationService.exportUncategorizedItems(startDate, endDate, sources);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"uncategorized_items.csv\"")
                .contentType(MediaType.parseMediaType("text/csv")).body(csv);
    }

    /**
     * Imports categorized items from an uploaded CSV file.
     *
     * @param file The CSV file containing categorized items.
     * @return A ResponseEntity indicating the result of the operation.
     * @throws IOException If an error occurs while reading the file.
     */
    @PostMapping("/import")
    public ResponseEntity<Void> importCategorizedItems(@RequestParam("file") MultipartFile file) throws IOException {
        aiCategorizationService.importCategorizedItems(file);
        return ResponseEntity.ok().build();
    }
}
