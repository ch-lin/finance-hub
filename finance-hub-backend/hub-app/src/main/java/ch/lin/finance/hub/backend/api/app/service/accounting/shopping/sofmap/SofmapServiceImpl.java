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
package ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.SofmapOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result.SofmapOrderResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link SofmapService}.
 * <p>
 * Handles persisting and retrieving Sofmap orders from the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SofmapServiceImpl implements SofmapService {

    /** Repository to interact with the Sofmap order database. */
    private final SofmapOrderRepository sofmapOrderRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public SofmapOrderResult saveOrder(SofmapOrder parsedOrder) {
        log.debug("Saving Sofmap order: {}", parsedOrder.getOrderId());

        SofmapOrder savedOrder = sofmapOrderRepository.findByOrderId(Objects.requireNonNull(parsedOrder.getOrderId())).map(existingOrder -> {
            log.info("Updating existing Sofmap order: {}", existingOrder.getOrderId());
            existingOrder.merge(parsedOrder);
            return sofmapOrderRepository.save(existingOrder);
        }).orElseGet(() -> {
            log.info("Creating new Sofmap order: {}", parsedOrder.getOrderId());
            return sofmapOrderRepository.save(parsedOrder);
        });

        // Ensure collections are properly initialized for the result
        return sofmapOrderRepository.findWithDetailsByOrderId(savedOrder.getOrderId())
                .map(SofmapOrderResult::fromEntity)
                .orElseThrow(() -> new IllegalStateException("Failed to retrieve saved Sofmap order details."));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<SofmapOrderResult> getOrder(String orderId) {
        return sofmapOrderRepository.findByOrderId(Objects.requireNonNull(orderId))
                .map(SofmapOrderResult::fromEntity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<SofmapOrderResult> getOrderWithDetails(String orderId) {
        return sofmapOrderRepository.findWithDetailsByOrderId(orderId)
                .map(SofmapOrderResult::fromEntity);
    }
}
