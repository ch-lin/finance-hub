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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonOrderCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonOrderResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;

/**
 * Main orchestration service for Amazon-related data imports.
 * <p>
 * This class implements the {@link AmazonService} interface and is responsible
 * for coordinating the core business logic regarding Amazon orders. It handles
 * the parsing of order history files, orchestrating file movements (success,
 * failed, needs_review),
 * and persisting the parsed orders to the database using
 * {@link AmazonOrderRepository}.
 * <p>
 * It acts as both a {@link JobExecutor} (for batch directory processing) and a
 * {@link FileProcessingStrategy} (for single-file direct processing).
 */
@Service
public class AmazonServiceImpl implements AmazonService {

    /** Logger for this service. */
    private static final Logger logger = LoggerFactory.getLogger(AmazonServiceImpl.class);

    /** Repository to interact with the Amazon order database. */
    private final AmazonOrderRepository amazonOrderRepository;

    /** Template for executing manual database transactions. */
    private final TransactionTemplate transactionTemplate;

    /**
     * Constructs a new AmazonServiceImpl with the required dependencies.
     * 
     * @param amazonOrderRepository Database repository for Amazon orders.
     * @param transactionTemplate   Template for managing database transactions
     *                              manually.
     */
    public AmazonServiceImpl(
            AmazonOrderRepository amazonOrderRepository,
            TransactionTemplate transactionTemplate) {
        this.amazonOrderRepository = amazonOrderRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation performs a destructive cleanup by deleting all
     * records from the amazon order tables.
     */
    @Override
    @Transactional
    public void cleanup() {
        logger.info("Cleaning up all Amazon order data...");
        amazonOrderRepository.cleanTable();
        amazonOrderRepository.resetSequence();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Inserts or updates a parsed Amazon order within a transactional boundary.
     */
    @Override
    @Transactional
    public AmazonOrderResult saveOrder(SaveAmazonOrderCommand command) {
        return transactionTemplate.execute(status -> {
            AmazonOrder parsedOrder = AmazonOrderAppMapper.toEntity(command);
            Optional<AmazonOrder> existingOpt = amazonOrderRepository.findByOrderId(parsedOrder.getOrderId());
            AmazonOrder savedOrder;
            if (existingOpt.isPresent()) {
                AmazonOrder existingOrder = existingOpt.get();
                existingOrder.merge(parsedOrder);
                logger.info("Updating existing Amazon order from API: {}", parsedOrder.getOrderId());
                savedOrder = amazonOrderRepository.save(existingOrder);
            } else {
                logger.info("Saving new Amazon order from API: {}", parsedOrder.getOrderId());
                savedOrder = amazonOrderRepository.save(parsedOrder);
            }
            return AmazonOrderResult.fromEntity(savedOrder);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AmazonOrderResult> getOrder(String orderId) {
        return amazonOrderRepository.findByOrderId(orderId).map(AmazonOrderResult::fromEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AmazonOrderResult> getOrderWithDetails(String orderId) {
        return amazonOrderRepository.findWithDetailsByOrderId(orderId)
                .map(AmazonOrderResult::fromEntity);
    }
}
