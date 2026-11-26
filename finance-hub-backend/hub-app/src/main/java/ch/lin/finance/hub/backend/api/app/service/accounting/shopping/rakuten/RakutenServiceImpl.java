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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.RakutenOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.result.RakutenOrderResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;

/**
 * Main orchestration service for Rakuten-related data imports.
 * <p>
 * This class implements the {@link RakutenService} interface and is responsible
 * for coordinating the core business logic regarding Rakuten orders. It handles
 * persisting the parsed orders to the database using
 * {@link RakutenOrderRepository}.
 */
@Service
public class RakutenServiceImpl implements RakutenService {

    /** Logger for this service. */
    private static final Logger logger = LoggerFactory.getLogger(RakutenServiceImpl.class);

    /** Repository to interact with the Rakuten order database. */
    private final RakutenOrderRepository rakutenOrderRepository;

    /** Template for executing manual database transactions. */
    private final TransactionTemplate transactionTemplate;

    /**
     * Constructs a new RakutenServiceImpl with the required dependencies.
     * 
     * @param rakutenOrderRepository Database repository for Rakuten orders.
     * @param transactionTemplate    Template for managing database transactions
     *                               manually.
     */
    public RakutenServiceImpl(
            RakutenOrderRepository rakutenOrderRepository,
            TransactionTemplate transactionTemplate) {
        this.rakutenOrderRepository = rakutenOrderRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation performs a destructive cleanup by deleting all
     * records from the rakuten order tables.
     */
    @Override
    @Transactional
    public void cleanup() {
        logger.info("Cleaning up all Rakuten order data...");
        rakutenOrderRepository.cleanTable();
        rakutenOrderRepository.resetSequence();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Inserts or updates a parsed Rakuten order within a transactional boundary.
     */
    @Override
    @Transactional
    public RakutenOrderResult saveOrder(RakutenOrder parsedOrder) {
        return transactionTemplate.execute(status -> {
            Optional<RakutenOrder> existingOpt = rakutenOrderRepository.findByOrderId(parsedOrder.getOrderId());
            RakutenOrder savedOrder;
            if (existingOpt.isPresent()) {
                RakutenOrder existingOrder = existingOpt.get();
                existingOrder.merge(parsedOrder);
                logger.info("Updating existing Rakuten order from API: {}", parsedOrder.getOrderId());
                savedOrder = rakutenOrderRepository.save(existingOrder);
            } else {
                logger.info("Saving new Rakuten order from API: {}", parsedOrder.getOrderId());
                savedOrder = rakutenOrderRepository.save(parsedOrder);
            }
            return RakutenOrderResult.fromEntity(savedOrder);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RakutenOrderResult> getOrder(String orderId) {
        return rakutenOrderRepository.findByOrderId(orderId).map(RakutenOrderResult::fromEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<RakutenOrderResult> getOrderWithDetails(String orderId) {
        return rakutenOrderRepository.findWithDetailsByOrderId(orderId)
                .map(RakutenOrderResult::fromEntity);
    }
}
