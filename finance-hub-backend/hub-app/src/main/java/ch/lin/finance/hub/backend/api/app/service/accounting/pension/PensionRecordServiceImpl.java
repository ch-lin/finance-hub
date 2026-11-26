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
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.pension.PensionRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.command.CreatePensionRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionRecordResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.pension.PensionRecord;
import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import lombok.RequiredArgsConstructor;

/**
 * Service for managing {@link PensionRecord} entities.
 */
@Service
@RequiredArgsConstructor
public class PensionRecordServiceImpl implements PensionRecordService {

    /**
     * Repository for accessing {@link PensionRecord} data.
     */
    private final PensionRecordRepository pensionRecordRepository;

    /**
     * Retrieves all pension records, ordered by record date in descending order.
     *
     * @return a list of all {@link PensionRecordResult}s
     */
    @Override
    @Transactional(readOnly = true)
    public List<PensionRecordResult> getAllRecords() {
        return pensionRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "recordDate")).stream()
                .map(PensionRecordResult::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a pension record by its unique identifier.
     *
     * @param id the unique identifier of the record
     * @return an {@link Optional} containing the {@link PensionRecordResult} if
     *         found, or
     *         empty otherwise
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<PensionRecordResult> getRecordById(@NonNull Long id) {
        return pensionRecordRepository.findById(id).map(PensionRecordResult::fromEntity);
    }

    @Override
    @Transactional
    public PensionRecordResult createOrUpdateRecord(@NonNull CreatePensionRecordCommand command) {
        PensionRecord record = new PensionRecord(
                command.getRecordDate(),
                command.getBasicPensionAmount(),
                command.getKouseiPensionAmount(),
                command.getTotalPensionAmount(),
                command.getKokuminCategory1Months(),
                command.getKokuminCategory3Months(),
                command.getKokuminTotalMonths(),
                command.getKouseiGeneralMonths(),
                command.getKouseiTotalMonths(),
                command.getTotalEnrolledMonths(),
                command.getQualifyingMonths(),
                command.getKokuminPremiumPaid(),
                command.getKouseiPremiumPaid(),
                command.getTotalPremiumPaid());

        if (command.getId() != null) {
            try {
                java.lang.reflect.Field idField = MultiUserBaseEntity.class.getSuperclass().getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(record, command.getId());
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Failed to set ID for PensionRecord", e);
            }
        }

        return PensionRecordResult.fromEntity(pensionRecordRepository.save(record));
    }

    /**
     * Deletes a pension record by its unique identifier.
     *
     * @param id the unique identifier of the record to delete
     */
    @Override
    @Transactional
    public void deleteRecord(@NonNull Long id) {
        pensionRecordRepository.deleteById(id);
    }
}
