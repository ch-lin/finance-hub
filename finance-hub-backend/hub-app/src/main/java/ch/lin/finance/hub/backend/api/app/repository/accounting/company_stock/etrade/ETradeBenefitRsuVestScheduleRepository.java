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
package ch.lin.finance.hub.backend.api.app.repository.accounting.company_stock.etrade;

import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.*;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ETradeBenefitRsuVestScheduleRepository extends JpaRepository<ETradeBenefitRsuVestSchedule, String> {

    Optional<ETradeBenefitRsuVestSchedule> findBySymbolAndGrantNumberAndVestPeriod(String symbol, String grantNumber,
            Integer vestPeriod);
}
