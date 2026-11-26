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
package ch.lin.finance.hub.backend.api.app.service.accounting.salary;

import java.io.InputStream;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;

/**
 * Interface for parsing salary statement HTML files.
 */
public interface SalaryParser {
    /**
     * Parses a salary HTML statement.
     * 
     * @param inputStream the input stream of the HTML file
     * @param filename the name of the file, used for fallback date parsing
     * @return the parsed SalaryRecord
     */
    SalaryRecord parse(InputStream inputStream, String filename);
}
