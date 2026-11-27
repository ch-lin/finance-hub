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
package ch.lin.finance.hub.backend.api.app.service.accounting.pension;

import java.util.List;

import org.springframework.lang.NonNull;

import ch.lin.finance.hub.backend.api.app.service.accounting.pension.command.CreatePensionRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionRecordResult;

public interface PensionRecordService {

    List<PensionRecordResult> getAllRecords();

    PensionRecordResult getRecordById(@NonNull Long id);

    PensionRecordResult createOrUpdateRecord(@NonNull CreatePensionRecordCommand command);

    void deleteRecord(@NonNull Long id);
}
