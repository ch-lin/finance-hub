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

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.accounting.salary.command.SaveSalaryRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.salary.result.SalaryRecordResult;

/**
 * Interface for managing salary records.
 */
public interface SalaryRecordService {

    /**
     * Retrieves all salary records sorted by payment date in descending order.
     *
     * @return A list of all salary records.
     */
    List<SalaryRecordResult> getAllRecords();

    /**
     * Creates a new salary record.
     *
     * @param command The command containing the salary record details.
     * @return The created salary record.
     */
    SalaryRecordResult createRecord(SaveSalaryRecordCommand command);

    /**
     * Updates an existing salary record.
     *
     * @param id      The ID of the salary record to update.
     * @param command The command containing the updated details.
     * @return The updated salary record.
     */
    SalaryRecordResult updateRecord(Long id, SaveSalaryRecordCommand command);
}
