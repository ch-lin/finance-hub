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
package ch.lin.finance.hub.backend.api.app.service.accounting.dc;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionAccountSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionFundSnapshotRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.SaveDefinedContributionSnapshotCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAccountAllocationsCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.command.UpdateAllocationCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.dc.result.DefinedContributionFundSnapshotResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionAccountSnapshot;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionFundSnapshot;

@Service
public class DefinedContributionServiceImpl implements DefinedContributionService {

    private static final Logger logger = LoggerFactory.getLogger(DefinedContributionServiceImpl.class);

    private final DefinedContributionAccountSnapshotRepository dcRepository;
    private final DefinedContributionFundSnapshotRepository dcFundRepository;

    public DefinedContributionServiceImpl(
            DefinedContributionAccountSnapshotRepository dcRepository,
            DefinedContributionFundSnapshotRepository dcFundRepository) {
        this.dcRepository = dcRepository;
        this.dcFundRepository = dcFundRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DefinedContributionAccountSnapshotResult> getAllRecords() {
        return dcRepository.findAllByOrderByRecordDateDesc().stream()
                .map(DefinedContributionAccountSnapshotResult::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public void saveSnapshot(SaveDefinedContributionSnapshotCommand command, boolean dryRun) {
        DefinedContributionAccountSnapshot snapshot = new DefinedContributionAccountSnapshot(
                command.getRecordDate(),
                command.getType(),
                command.getTotalAssetValue(),
                command.getTotalAccumulatedContribution(),
                command.getMonthlyEmployerContribution(),
                command.getMonthlySubscriberContribution(),
                command.getMonthlyTotalContribution());

        if (command.getFunds() != null) {
            command.getFunds().forEach(fundCmd -> {
                String code = fundCmd.getFundCode() != null && !fundCmd.getFundCode().isEmpty()
                        ? fundCmd.getFundCode()
                        : fundCmd.getFundName();
                DefinedContributionFundSnapshot fund = new DefinedContributionFundSnapshot(
                        fundCmd.getCommodityType(),
                        fundCmd.getFundName(),
                        code,
                        fundCmd.getUnits(),
                        fundCmd.getAverageCost(),
                        fundCmd.getCurrentPrice(),
                        fundCmd.getTotalPrincipal(),
                        fundCmd.getCurrentMarketValue(),
                        fundCmd.getUnrealizedGainLoss());
                snapshot.addFund(fund);
            });
        }

        if (!dryRun) {
            // Delete existing snapshot for the same date and type to avoid constraint
            // violation
            dcRepository.deleteByRecordDateAndType(command.getRecordDate(), command.getType());
            dcRepository.save(Objects.requireNonNull(snapshot));
            logger.info("Saved DC snapshot from extension for date: {} and type: {}", command.getRecordDate(),
                    command.getType());
        } else {
            logger.info("DRY RUN - DC snapshot from extension parsed successfully (funds: {})",
                    snapshot.getFunds().size());
        }
    }

    @Override
    @Transactional
    public DefinedContributionFundSnapshotResult updateAllocation(@NonNull Long fundId,
            UpdateAllocationCommand command) {
        DefinedContributionFundSnapshot fund = dcFundRepository.findById(fundId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Fund not found"));

        fund.updateExpectedReturnRate(command != null ? command.getExpectedReturnRate() : null);

        return DefinedContributionFundSnapshotResult.fromEntity(dcFundRepository.save(fund));
    }

    @Override
    @Transactional
    public DefinedContributionAccountSnapshotResult updateAccountAllocations(@NonNull Long accountId,
            UpdateAccountAllocationsCommand command) {
        if (command == null || command.getRemainderFundCode() == null || command.getRemainderFundCode().isBlank()
                || command.getAllocations() == null || command.getAllocations().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "remainderFundCode and non-empty allocations are required");
        }

        DefinedContributionAccountSnapshot account = dcRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account snapshot not found"));

        List<DefinedContributionFundSnapshot> accountFunds = account.getFunds();
        if (accountFunds == null || accountFunds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Account snapshot has no funds");
        }

        Set<String> accountFundCodes = new HashSet<>();
        for (DefinedContributionFundSnapshot fund : accountFunds) {
            accountFundCodes.add(fund.getFundCode());
        }

        String remainderFundCode = command.getRemainderFundCode();

        Map<String, Double> requestedPercentages = DefinedContributionAllocationCalculator
                .validateAndExtractPercentages(command.getAllocations(),
                        accountFundCodes, remainderFundCode);

        Map<String, Long> computedAmounts = DefinedContributionAllocationCalculator.calculateMonthlyContributions(
                account.getMonthlyTotalContribution(),
                requestedPercentages, remainderFundCode);

        account.updateRemainderFundCode(remainderFundCode);
        for (DefinedContributionFundSnapshot fund : accountFunds) {
            String code = fund.getFundCode();
            fund.updateAllocation(computedAmounts.get(code), requestedPercentages.get(code));
        }

        return DefinedContributionAccountSnapshotResult.fromEntity(
                dcRepository.save(Objects.requireNonNull(account)));
    }

    @Override
    @Transactional
    public void deleteSnapshotById(@NonNull Long id) {
        if (!dcRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found");
        }
        dcRepository.deleteById(id);
        logger.info("Deleted DC snapshot with ID: {}", id);
    }

    @Override
    @Transactional
    public void deleteSnapshotByDate(@NonNull LocalDate recordDate) {
        List<DefinedContributionAccountSnapshot> snapshots = dcRepository.findByRecordDate(recordDate);
        if (!snapshots.isEmpty()) {
            dcRepository.deleteAll(snapshots);
            logger.info("Deleted {} DC snapshots for date: {}", snapshots.size(), recordDate);
        }
    }

    @Override
    @Transactional
    public void deleteAllSnapshots() {
        dcRepository.deleteAll();

        // Reset the auto-increment sequences for both tables
        dcAccountSnapshotRepositoryReset();
        dcFundSnapshotRepositoryReset();

        logger.info("Deleted all DC snapshots and reset auto-increment sequences");
    }

    private void dcAccountSnapshotRepositoryReset() {
        dcRepository.resetSequence();
    }

    private void dcFundSnapshotRepositoryReset() {
        dcFundRepository.resetSequence();
    }
}
