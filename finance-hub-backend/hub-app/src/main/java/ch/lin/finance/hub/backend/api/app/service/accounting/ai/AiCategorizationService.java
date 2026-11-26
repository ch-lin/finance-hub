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
package ch.lin.finance.hub.backend.api.app.service.accounting.ai;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for AI categorization of items.
 */
public interface AiCategorizationService {
    /**
     * Exports all uncategorized items into a CSV string format, optionally filtered by date and sources.
     *
     * @param startDate Optional start date for filtering transactions.
     * @param endDate Optional end date for filtering transactions.
     * @param sources Optional list of sources to include (e.g., AMAZON, BANK).
     * @return The CSV string containing uncategorized items.
     */
    String exportUncategorizedItems(LocalDate startDate, LocalDate endDate, List<String> sources);

    /**
     * Imports categorized items from a CSV file and updates their categories.
     *
     * @param file The CSV file containing the categorized items.
     * @throws IOException If an error occurs while reading the file.
     */
    void importCategorizedItems(MultipartFile file) throws IOException;
}
