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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.command.SaveMortgageRecordCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.result.MortgageRecordResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.MortgageTaxDeductionStrategy;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.strategy.TaxStrategyFactory;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.BonusPayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MortgageRecordServiceImpl implements MortgageRecordService {

    private final MortgageRecordRepository repository;
    private final TaxStrategyFactory taxStrategyFactory;
    private final OwnershipGuard ownershipGuard;

    @Override
    @Transactional(readOnly = true)
    public List<MortgageRecordResult> getAllMortgages() {
        return repository.findAll().stream()
                .map(MortgageRecordResult::fromEntity)
                .collect(Collectors.toList());
    }

    private void validateAndApplyTaxLogic(SaveMortgageRecordCommand request, MortgageRecord record) {
        record.setOccupancyDate(request.occupancyDate());
        record.setPropertyType(request.propertyType());
        record.setTaxDeductionEligible(request.taxDeductionEligible());

        if (Boolean.TRUE.equals(request.taxDeductionEligible())) {
            String currentUserId = SecurityUtil.getCurrentUserId();

            List<MortgageRecord> otherEligibleMortgages = repository
                    .findByOwnerUserIdAndTaxDeductionEligibleTrue(currentUserId);
            for (MortgageRecord other : otherEligibleMortgages) {
                if (record.getId() == null || !other.getId().equals(record.getId())) {
                    other.setTaxDeductionEligible(false);
                    other.setResidenceType(null);
                    other.setTaxDeductionRate(null);
                    other.setMaxTaxDeductionAmount(null);
                    other.setTaxDeductionEndYear(null);
                    repository.save(other);
                }
            }

            if (request.residenceType() == null) {
                throw new IllegalArgumentException("Residence type is required when tax deduction is eligible");
            }
            record.setResidenceType(request.residenceType());

            MortgageTaxDeductionStrategy strategy = taxStrategyFactory.getStrategy(request.occupancyDate());
            if (strategy == null) {
                throw new IllegalStateException("No tax strategy found for occupancy date: " + request.occupancyDate());
            }
            record.setTaxDeductionRate(strategy.calculateRate(request.residenceType()));
            record.setMaxTaxDeductionAmount(strategy.calculateMaxAmount(request.residenceType()));
            int duration = strategy.calculateDurationYears(record.getPropertyType());
            if (request.occupancyDate() != null) {
                record.setTaxDeductionEndYear(request.occupancyDate().getYear() + duration - 1);
            } else {
                record.setTaxDeductionEndYear(null);
            }
        } else {
            record.setResidenceType(null);
            record.setTaxDeductionRate(null);
            record.setMaxTaxDeductionAmount(null);
            record.setTaxDeductionEndYear(null);
        }
    }

    private void applyEarlyRepayments(SaveMortgageRecordCommand request, MortgageRecord record) {
        if (record.getEarlyRepayments() == null) {
            record.setEarlyRepayments(new java.util.ArrayList<>());
        }
        record.getEarlyRepayments().clear();
        if (request.earlyRepayments() != null) {
            LocalDate today = LocalDate.now();
            LocalDate minDate = (record.getStartDate() != null && record.getStartDate().isAfter(today))
                    ? record.getStartDate()
                    : today;
            for (var dto : request.earlyRepayments()) {
                if (dto.repaymentDate() != null && dto.repaymentDate().isBefore(minDate)) {
                    throw new IllegalArgumentException(
                            "Early repayment date (" + dto.repaymentDate() + ") cannot be earlier than " + minDate);
                }
                ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageEarlyRepayment earlyRepayment = new ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageEarlyRepayment(
                        record, dto.repaymentDate(), dto.amount(), dto.repaymentType(), dto.fee(), dto.reducedMonths());
                record.getEarlyRepayments().add(earlyRepayment);
            }
        }
    }

    @Override
    public MortgageRecordResult createMortgage(SaveMortgageRecordCommand request) {
        if (request.totalLoanAmount() == null || request.interestRate() == null || request.loanTermMonths() == null) {
            throw new IllegalArgumentException(
                    "Cannot create mortgage: required fields (totalLoanAmount, interestRate, loanTermMonths) are missing.");
        }
        LocalDate calculatedEndDate = request.endDate() != null ? request.endDate()
                : request.startDate().plusMonths(request.loanTermMonths());
        MortgageRecord record = new MortgageRecord(
                request.bankName(),
                request.contractNumber(),
                request.loanName(),
                request.totalLoanAmount(),
                request.currentBalance(),
                request.startDate(),
                calculatedEndDate,
                request.balanceRecordDate(),
                request.loanTermMonths(),
                request.interestRate(),
                request.rateType(),
                request.repaymentMethod(),
                request.monthlyPayment(),
                request.bonusPayments() != null ? request.bonusPayments().stream()
                        .map(b -> new BonusPayment(b.month(), b.amount())).collect(Collectors.toList())
                        : new ArrayList<>(),
                request.occupancyDate(),
                request.residenceType(),
                request.propertyType(),
                request.taxDeductionEligible(),
                null, // taxDeductionEndYear is auto-calculated later in validateAndApplyTaxLogic
                request.taxDeductionRate(),
                request.maxTaxDeductionAmount(),
                request.hasFiveYearRule());

        validateAndApplyTaxLogic(request, record);
        applyEarlyRepayments(request, record);

        return MortgageRecordResult.fromEntity(repository.save(record));
    }

    @Override
    public MortgageRecordResult updateMortgage(Long id, SaveMortgageRecordCommand request) {
        if (request.totalLoanAmount() == null || request.interestRate() == null || request.loanTermMonths() == null) {
            throw new IllegalArgumentException(
                    "Cannot update mortgage: required fields (totalLoanAmount, interestRate, loanTermMonths) are missing.");
        }
        MortgageRecord record = ownershipGuard.loadOwned(repository, id);

        if (request.bankName() != null) {
            record.setBankName(request.bankName());
        }
        if (request.contractNumber() != null) {
            record.setContractNumber(request.contractNumber());
        }
        record.setLoanName(request.loanName());
        record.setTotalLoanAmount(request.totalLoanAmount());
        record.setCurrentBalance(request.currentBalance());
        record.setBalanceRecordDate(
                request.balanceRecordDate() != null ? request.balanceRecordDate() : LocalDate.now());
        record.setStartDate(request.startDate());
        record.setEndDate(request.endDate() != null ? request.endDate()
                : request.startDate().plusMonths(request.loanTermMonths()));
        record.setLoanTermMonths(request.loanTermMonths());
        record.setInterestRate(request.interestRate());
        record.setRateType(request.rateType());
        record.setRepaymentMethod(request.repaymentMethod());
        record.setMonthlyPayment(request.monthlyPayment());

        List<BonusPayment> bonusPayments = request.bonusPayments() != null ? request.bonusPayments().stream()
                .map(b -> new BonusPayment(b.month(), b.amount())).collect(Collectors.toList())
                : new ArrayList<>();
        record.getBonusPayments().clear();
        record.getBonusPayments().addAll(bonusPayments);

        record.setHasFiveYearRule(request.hasFiveYearRule() != null ? request.hasFiveYearRule() : Boolean.TRUE);

        validateAndApplyTaxLogic(request, record);
        applyEarlyRepayments(request, record);

        return MortgageRecordResult.fromEntity(repository.save(record));
    }

    @Override
    public void deleteMortgage(Long id) {
        MortgageRecord record = ownershipGuard.loadOwned(repository, id);
        repository.delete(Objects.requireNonNull(record));
    }
}
