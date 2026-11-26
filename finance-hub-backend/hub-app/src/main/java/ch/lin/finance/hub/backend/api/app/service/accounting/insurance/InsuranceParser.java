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

import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;

public interface InsuranceParser {

    boolean canParse(InsuranceFilePreview preview);

    List<InsurancePolicy> parse(InsuranceFilePreview preview) throws Exception;

    String getProviderId();

    default void mergePolicy(InsurancePolicy existing, InsurancePolicy newPolicy) {
        if (existing != null) {
            existing.merge(newPolicy);
        }
    }
}
