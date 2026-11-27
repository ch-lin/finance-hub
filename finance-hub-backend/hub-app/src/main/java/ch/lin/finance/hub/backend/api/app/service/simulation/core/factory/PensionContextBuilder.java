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
package ch.lin.finance.hub.backend.api.app.service.simulation.core.factory;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.app.repository.accounting.pension.PensionRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.simulation.core.model.SimulationContext;
import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PensionContextBuilder {

    private final PensionRecordRepository pensionRecordRepository;

    public void initializePensionContext(SimulationContext context, String userId) {
        long accumulatedKousei = 0L;
        int currentKokuminMonths = 0;
        int currentKouseiMonths = 0;

        List<PensionRecord> pensionRecords = pensionRecordRepository
                .findAllByOwnerUserId(userId, Sort.by(Sort.Direction.DESC, "recordDate"));
        if (!pensionRecords.isEmpty()) {
            PensionRecord pr = pensionRecords.get(0);
            if (pr.getKouseiPensionAmount() != null) {
                accumulatedKousei = pr.getKouseiPensionAmount();
            }
            if (pr.getKokuminTotalMonths() != null) {
                currentKokuminMonths = pr.getKokuminTotalMonths();
            }
            if (pr.getKouseiTotalMonths() != null) {
                currentKouseiMonths = pr.getKouseiTotalMonths();
            }
        }
        context.setAccumulatedKousei(accumulatedKousei);
        context.setCurrentKokuminMonths(currentKokuminMonths);
        context.setCurrentKouseiMonths(currentKouseiMonths);
    }
}
