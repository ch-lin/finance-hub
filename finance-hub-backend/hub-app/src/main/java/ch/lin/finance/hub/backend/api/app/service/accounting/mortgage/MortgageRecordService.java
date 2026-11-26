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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage;

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.command.SaveMortgageRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.result.MortgageRecordResult;

/**
 * Service for managing Mortgage records.
 */
public interface MortgageRecordService {

    /**
     * Retrieves all mortgage records.
     * 
     * @return a list of all mortgages
     */
    List<MortgageRecordResult> getAllMortgages();

    /**
     * Creates a new mortgage record.
     * 
     * @param command the command containing mortgage details
     * @return the created mortgage record
     */
    MortgageRecordResult createMortgage(SaveMortgageRecordCommand command);

    /**
     * Updates an existing mortgage record.
     * 
     * @param id      the ID of the mortgage to update
     * @param command the command containing updated details
     * @return the updated mortgage record
     */
    MortgageRecordResult updateMortgage(Long id, SaveMortgageRecordCommand command);

    /**
     * Deletes a mortgage record by ID.
     * 
     * @param id the ID of the mortgage to delete
     */
    void deleteMortgage(Long id);
}
