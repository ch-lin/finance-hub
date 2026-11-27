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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.command.UpdateNisaReturnRateCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.nisa.result.NisaSnapshotRecordResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NisaSnapshotRecordServiceImpl implements NisaSnapshotRecordService {

    private final NisaSnapshotRecordRepository nisaRepository;
    private final OwnershipGuard ownershipGuard;

    @Override
    @Transactional(readOnly = true)
    public List<NisaSnapshotRecordResult> getAllRecords() {
        return nisaRepository.findAllByOrderByRecordDateDesc().stream()
                .map(NisaSnapshotRecordResult::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NisaSnapshotRecordResult updateReturnRate(Long id, UpdateNisaReturnRateCommand command) {
        NisaSnapshotRecord record = ownershipGuard.loadOwned(nisaRepository, id);

        record.updateExpectedReturnRate(command.getExpectedReturnRate());
        return NisaSnapshotRecordResult.fromEntity(nisaRepository.save(record));
    }
}
