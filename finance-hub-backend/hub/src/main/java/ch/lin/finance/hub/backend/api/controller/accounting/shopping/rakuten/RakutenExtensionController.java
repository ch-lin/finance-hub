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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.RakutenService;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto.RakutenExtensionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto.RakutenExtensionOrderRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto.RakutenExtensionOrderResponse;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller responsible for handling incoming Rakuten order data
 * submitted by the Chrome Extension.
 */
@RestController
@RequestMapping("/rakuten")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "https://order.my.rakuten.co.jp")
public class RakutenExtensionController {

    /** Service to handle business logic for Rakuten orders. */
    private final RakutenService rakutenService;

    /** Mapper for converting entity to DTO. */
    private final RakutenExtensionMapper rakutenExtensionMapper;

    /**
     * Receives and processes a Rakuten order payload from the Chrome Extension.
     *
     * @param request The parsed order payload containing items and payments.
     * @param dryRun  If true, the order is parsed and logged but not saved to the
     *                database.
     * @return A ResponseEntity indicating the result of the operation.
     */
    @PostMapping("/extension")
    public ResponseEntity<Void> receiveOrderFromExtension(@Valid @RequestBody RakutenExtensionOrderRequest request,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("Received Rakuten order {} from Chrome Extension (dryRun: {})", request.orderId(), dryRun);

        try {
            RakutenOrder rakutenOrder = rakutenExtensionMapper.toEntity(request);

            if (dryRun) {
                log.info("\n\n\n=== [DRY RUN] RakutenOrder Data Dump ===");
                log.info("Order ID: {}", rakutenOrder.getOrderId());
                log.info("Order Date: {}", rakutenOrder.getOrderDate());
                log.info("Total Amount: {}", rakutenOrder.getTotalAmount());
                log.info("Billed Amount: {}", rakutenOrder.getBilledAmount());
                log.info("Discounts: {}", rakutenOrder.getDiscounts());
                log.info("Rakuten Points Used: {}", rakutenOrder.getPointsUsed());
                log.info("Shipping Fee: {}", rakutenOrder.getShippingFee());
                log.info("Refund Amount: {}", rakutenOrder.getRefundAmount());

                log.info("--- Payments ({}):", rakutenOrder.getPayments().size());
                rakutenOrder.getPayments()
                        .forEach(p -> log.info("  Method: {}, Amount: {}", p.getMethod(), p.getAmount()));

                log.info("--- Items ({}):", rakutenOrder.getItems().size());
                rakutenOrder.getItems().forEach(i -> log.info("  Name: {}, ASIN: {}, Seller: {}, Qty: {}, Price: {}",
                        i.getName(), i.getItemId(), i.getSeller(), i.getQuantity(), i.getUnitPrice()));
                log.info("=======================================");

                log.info("Parsed RakutenOrder successfully. [DRY RUN - skipping DB save]\n\n\n");
            } else {
                log.info("Parsed RakutenOrder successfully (Total: {}, Billed: {}). Saving to DB...",
                        rakutenOrder.getTotalAmount(), rakutenOrder.getBilledAmount());
                rakutenService.saveOrder(rakutenOrder);
            }
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Failed to process Rakuten order from extension: {}", request.orderId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieves an existing Rakuten order with all details for the Chrome
     * Extension.
     *
     * @param orderId The Rakuten order ID.
     * @return A ResponseEntity containing the populated order details or NOT_FOUND
     *         if it does not exist.
     */
    @org.springframework.web.bind.annotation.GetMapping("/extension/{orderId}")
    public ResponseEntity<RakutenExtensionOrderResponse> getOrderForExtension(
            @org.springframework.web.bind.annotation.PathVariable String orderId) {
        log.info("Fetching existing Rakuten order {} for Chrome Extension", orderId);

        try {
            return rakutenService.getOrderWithDetails(orderId)
                    .map(rakutenExtensionMapper::toResponse)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to fetch Rakuten order from extension: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
