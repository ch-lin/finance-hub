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
package ch.lin.finance.hub.backend.api.app.service.profile.user;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
import ch.lin.finance.hub.backend.api.app.service.profile.user.command.SaveUserProfileCommand;
import ch.lin.finance.hub.backend.api.app.service.profile.user.result.UserProfileResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.profile.dependent.Dependent;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfileResult> getUserProfile(@NonNull String id) {
        return userProfileRepository.findById(id).map(profile -> {
            List<InsurancePolicy> policies = insurancePolicyRepository.findAll();
            return UserProfileResult.fromEntity(profile, id, policies);
        });
    }

    @Override
    @Transactional
    public UserProfileResult saveUserProfile(@NonNull String id, SaveUserProfileCommand command) {
        UserProfile profile = userProfileRepository.findById(id).orElseGet(() -> new UserProfile(id,
                command.birthDate(), command.manualMonthlyBaseExpense(), command.targetRetirementAge()));

        if (command.birthDate() != null) {
            profile.setBirthDate(command.birthDate());
        }
        profile.setDisplayName(command.displayName());
        profile.setDefaultCurrency(command.defaultCurrency());
        profile.setManualMonthlyBaseExpense(command.manualMonthlyBaseExpense());
        profile.setTargetRetirementAge(command.targetRetirementAge());
        profile.setPensionStartAge(command.pensionStartAge());

        if (command.familyMembers() != null) {
            profile.getFamilyMembers().clear();
            List<Dependent> members = command.familyMembers().stream().map(cmd -> {
                Dependent fm = new Dependent();
                fm.setUserProfileId(id);
                fm.setMemberType(cmd.memberType());
                fm.setBirthDate(cmd.birthDate());
                fm.setEstimatedAnnualIncome(cmd.estimatedAnnualIncome());
                fm.setIsCohabiting(cmd.isCohabiting());
                return fm;
            }).collect(Collectors.toList());
            profile.getFamilyMembers().addAll(members);
        }

        if (command.insurancePolicies() != null) {
            insurancePolicyRepository.deleteAll();
            List<InsurancePolicy> policies = command.insurancePolicies().stream().map(cmd -> {
                return new InsurancePolicy(cmd.companyName(), cmd.policyType(), cmd.policyNumber(), cmd.policyName(),
                        cmd.annualPremium(), cmd.endDate());
            }).collect(Collectors.toList());
            insurancePolicyRepository.saveAll(Objects.requireNonNull(policies));
        }

        profile.setLifeExpectancy(command.lifeExpectancy());
        profile.setTaxAdvantagedWithdrawalAge(command.taxAdvantagedWithdrawalAge());

        if (command.applyProfileToOptimizerBaseline() != null) {
            profile.setApplyProfileToOptimizerBaseline(command.applyProfileToOptimizerBaseline());
        }

        return UserProfileResult.fromEntity(userProfileRepository.save(profile), id,
                insurancePolicyRepository.findAll());
    }
}
