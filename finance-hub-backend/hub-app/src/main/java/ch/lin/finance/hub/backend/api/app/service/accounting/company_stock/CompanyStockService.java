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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock;

import java.util.List;

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.command.SaveETradeExtensionCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeAccountSnapshotResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitEsppPurchaseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuEventResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuGrantResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeBenefitRsuVestScheduleResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.ETradeCashTransactionResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.result.StockLotResult;

public interface CompanyStockService {

    void saveExtensionData(SaveETradeExtensionCommand command, boolean dryRun);

    List<ETradeAccountSnapshotResult> getAllSnapshots();

    List<StockLotResult> getStockLots();

    List<ETradeCashTransactionResult> getCashTransactions();

    List<ETradeBenefitEsppPurchaseResult> getEsppPurchases();

    List<ETradeBenefitRsuGrantResult> getRsuGrants();

    List<ETradeBenefitRsuEventResult> getRsuEvents();

    List<ETradeBenefitRsuVestScheduleResult> getRsuVestSchedules();
}
