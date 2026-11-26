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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade.result;

import java.util.List;

import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitEsppPurchase;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuEvent;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuGrant;
import ch.lin.finance.hub.backend.api.domain.model.accounting.company_stock.etrade.ETradeBenefitRsuVestSchedule;
import lombok.Value;

@Value
public class ETradeBenefitHistoryParseResult {
    List<ETradeBenefitRsuGrant> rsuGrants;
    List<ETradeBenefitRsuEvent> rsuEvents;
    List<ETradeBenefitRsuVestSchedule> rsuVestSchedules;
    List<ETradeBenefitEsppPurchase> esppPurchases;
}
