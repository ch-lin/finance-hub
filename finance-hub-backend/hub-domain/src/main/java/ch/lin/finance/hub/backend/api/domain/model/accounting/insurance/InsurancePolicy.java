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
package ch.lin.finance.hub.backend.api.domain.model.accounting.insurance;

import java.time.LocalDate;
import java.util.Objects;

import ch.lin.finance.hub.backend.api.domain.model.base.MultiUserBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "finance_insurance_policy", uniqueConstraints = {
        @UniqueConstraint(name = "uk_insurance_policy_company_number", columnNames = {
                MultiUserBaseEntity.OWNER_USER_ID_COLUMN, "company_name", "policy_number" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = true)
public class InsurancePolicy extends MultiUserBaseEntity {

    @Setter
    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, updatable = false)
    private InsurancePolicyType policyType;

    @Setter
    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Setter
    @Column(name = "policy_name", nullable = false)
    private String policyName;

    @Setter
    @Column(name = "annual_premium", nullable = false, columnDefinition = "bigint default 0")
    private long annualPremium = 0L;

    @Setter
    @Column(name = "policy_end_date")
    private LocalDate endDate;

    public InsurancePolicy(String companyName, InsurancePolicyType policyType, String policyNumber, String policyName,
            long annualPremium, LocalDate endDate) {
        this.companyName = Objects.requireNonNull(companyName, "CompanyName cannot be null");
        this.policyType = Objects.requireNonNull(policyType, "PolicyType cannot be null");
        this.policyNumber = Objects.requireNonNull(policyNumber, "PolicyNumber cannot be null");
        this.policyName = Objects.requireNonNull(policyName, "PolicyName cannot be null");
        this.annualPremium = annualPremium;
        this.endDate = endDate;
    }

    public void merge(InsurancePolicy other) {
        if (other == null) {
            return;
        }
        this.companyName = other.companyName;
        this.policyType = other.policyType; // because user can replace wrong setting he set before
        this.policyNumber = other.policyNumber;
        this.policyName = other.policyName;
        this.annualPremium = other.annualPremium;
        this.endDate = other.endDate;
    }
}
