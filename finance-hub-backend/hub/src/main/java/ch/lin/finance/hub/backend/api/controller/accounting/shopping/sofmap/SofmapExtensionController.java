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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.SofmapService;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto.SofmapExtensionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto.SofmapExtensionOrderRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto.SofmapExtensionOrderResponse;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller responsible for handling incoming Sofmap order data
 * submitted by the Chrome Extension.
 */
@RestController
@RequestMapping("/sofmap")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "https://www.sofmap.com")
public class SofmapExtensionController {

    /** Service to handle business logic for Sofmap orders. */
    private final SofmapService sofmapService;

    /** Mapper for converting entity to DTO. */
    private final SofmapExtensionMapper sofmapExtensionMapper;

    /**
     * Receives and processes a Sofmap order payload from the Chrome Extension.
     *
     * @param request The parsed order payload containing items and payments.
     * @param dryRun  If true, the order is parsed and logged but not saved to the
     *                database.
     * @return A ResponseEntity indicating the result of the operation.
     */
    @PostMapping("/extension")
    public ResponseEntity<Void> receiveOrderFromExtension(@Valid @RequestBody SofmapExtensionOrderRequest request,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("Received Sofmap order {} from Chrome Extension (dryRun: {})", request.orderId(), dryRun);

        try {
            SofmapOrder sofmapOrder = sofmapExtensionMapper.toEntity(request);

            if (dryRun) {
                log.info("\n\n\n=== [DRY RUN] SofmapOrder Data Dump ===");
                log.info("Order ID: {}", sofmapOrder.getOrderId());
                log.info("Order Date: {}", sofmapOrder.getOrderDate());
                log.info("Total Amount: {}", sofmapOrder.getTotalAmount());
                log.info("Billed Amount: {}", sofmapOrder.getBilledAmount());
                log.info("Discounts: {}", sofmapOrder.getDiscounts());
                log.info("Sofmap Points Used: {}", sofmapOrder.getPointsUsed());
                log.info("Shipping Fee: {}", sofmapOrder.getShippingFee());
                log.info("Refund Amount: {}", sofmapOrder.getRefundAmount());

                log.info("--- Payments ({}):", sofmapOrder.getPayments().size());
                sofmapOrder.getPayments()
                        .forEach(p -> log.info("  Method: {}, Amount: {}", p.getMethod(), p.getAmount()));

                log.info("--- Items ({}):", sofmapOrder.getItems().size());
                sofmapOrder.getItems().forEach(i -> log.info("  Name: {}, ItemId: {}, Seller: {}, Qty: {}, Price: {}",
                        i.getName(), i.getItemId(), i.getSeller(), i.getQuantity(), i.getUnitPrice()));

                log.info("--- Shipments ({}):", sofmapOrder.getShipments().size());
                sofmapOrder.getShipments().forEach(s -> log.info("  Date: {}, Carrier: {}, Tracking: {}",
                        s.getShippingDate(), s.getCarrier(), s.getTrackingNumber()));
                log.info("=======================================");

                log.info("Parsed SofmapOrder successfully. [DRY RUN - skipping DB save]\n\n\n");
            } else {
                log.info("Parsed SofmapOrder successfully (Total: {}, Billed: {}). Saving to DB...",
                        sofmapOrder.getTotalAmount(), sofmapOrder.getBilledAmount());
                sofmapService.saveOrder(sofmapOrder);
            }
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Failed to process Sofmap order from extension: {}", request.orderId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieves an existing Sofmap order with all details for the Chrome Extension.
     *
     * @param orderId The Sofmap order ID.
     * @return A ResponseEntity containing the populated order details or NOT_FOUND if it does not exist.
     */
    @GetMapping("/extension/{orderId}")
    public ResponseEntity<SofmapExtensionOrderResponse> getOrderForExtension(
            @PathVariable String orderId) {
        log.info("Fetching existing Sofmap order {} for Chrome Extension", orderId);

        try {
            return sofmapService.getOrderWithDetails(orderId)
                    .map(sofmapExtensionMapper::toResponse)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to fetch Sofmap order from extension: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
