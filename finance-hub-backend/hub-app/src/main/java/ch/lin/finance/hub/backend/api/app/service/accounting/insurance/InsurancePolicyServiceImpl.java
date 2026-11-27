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
package ch.lin.finance.hub.backend.api.app.service.accounting.insurance;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.security.OwnershipGuard;
import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.command.SaveInsurancePolicyCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.insurance.result.InsurancePolicyResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class InsurancePolicyServiceImpl implements InsurancePolicyService {

    private final InsurancePolicyRepository repository;
    private final OwnershipGuard ownershipGuard;

    @Override
    @Transactional(readOnly = true)
    public List<InsurancePolicyResult> getAllPolicies() {
        String userId = SecurityUtil.getCurrentUserId();
        return repository.findAll().stream()
                .map(policy -> InsurancePolicyResult.fromEntity(policy, userId))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public InsurancePolicyResult getPolicy(Long id) {
        String userId = SecurityUtil.getCurrentUserId();
        InsurancePolicy policy = ownershipGuard.loadReadable(repository, id);
        return InsurancePolicyResult.fromEntity(policy, userId);
    }

    @Override
    public InsurancePolicyResult createPolicy(SaveInsurancePolicyCommand command) {
        String userId = SecurityUtil.getCurrentUserId();
        InsurancePolicy policy = new InsurancePolicy(
                command.companyName(),
                command.policyType(),
                command.policyNumber(),
                command.policyName(),
                command.annualPremium(),
                command.endDate());
        return InsurancePolicyResult.fromEntity(repository.save(policy), userId);
    }

    @Override
    public InsurancePolicyResult updatePolicy(Long id, SaveInsurancePolicyCommand command) {
        String userId = SecurityUtil.getCurrentUserId();
        InsurancePolicy existing = ownershipGuard.loadOwned(repository, id);

        existing.setCompanyName(command.companyName());
        existing.setPolicyNumber(command.policyNumber());
        existing.setPolicyName(command.policyName());
        existing.setAnnualPremium(command.annualPremium());
        existing.setEndDate(command.endDate());

        return InsurancePolicyResult.fromEntity(repository.save(existing), userId);
    }

    @Override
    public void deletePolicy(Long id) {
        InsurancePolicy policy = ownershipGuard.loadOwned(repository, id);
        repository.delete(Objects.requireNonNull(policy));
    }
}
