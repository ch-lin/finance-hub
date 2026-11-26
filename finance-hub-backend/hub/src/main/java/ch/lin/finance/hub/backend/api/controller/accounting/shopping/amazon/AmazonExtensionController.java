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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.AmazonService;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonOrderCommand;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto.AmazonExtensionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto.AmazonExtensionOrderRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto.AmazonExtensionOrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller responsible for handling incoming Amazon order data
 * submitted by the Chrome Extension.
 */
@RestController
@RequestMapping("/amazon")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "https://www.amazon.co.jp")
public class AmazonExtensionController {

    /** Service to handle business logic for Amazon orders. */
    private final AmazonService amazonService;

    /** Mapper for converting entity to DTO. */
    private final AmazonExtensionMapper amazonExtensionMapper;

    /**
     * Receives and processes an Amazon order payload from the Chrome Extension.
     *
     * @param request The parsed order payload containing items, payments, and
     *                shipment details.
     * @param dryRun  If true, the order is parsed and logged but not saved to the
     *                database.
     * @return A ResponseEntity indicating the result of the operation.
     */
    @PostMapping("/extension")
    public ResponseEntity<Void> receiveOrderFromExtension(@Valid @RequestBody AmazonExtensionOrderRequest request,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        log.info("Received Amazon order {} from Chrome Extension (dryRun: {})", request.orderId(), dryRun);

        try {
            SaveAmazonOrderCommand command = amazonExtensionMapper.toCommand(request);

            if (dryRun) {
                log.info("\n\n\n=== [DRY RUN] AmazonOrder Data Dump ===");
                log.info("Order ID: {}", command.orderId());
                log.info("Order Date: {}", command.orderDate());
                log.info("Total Amount: {}", command.totalAmount());
                log.info("Billed Amount: {}", command.billedAmount());
                log.info("Discounts: {}", command.discounts());
                log.info("Amazon Points Used: {}", command.amazonPointsUsed());
                log.info("dPoints Used: {}", command.dPointsUsed());
                log.info("Gift Card Amount: {}", command.giftCardAmount());
                log.info("Shipping Fee: {}", command.shippingFee());
                log.info("Refund Amount: {}", command.refundAmount());

                log.info("--- Payments ({}):", command.payments() != null ? command.payments().size() : 0);
                if (command.payments() != null) {
                    command.payments()
                            .forEach(p -> log.info("  Method: {}, Amount: {}", p.method(), p.amount()));
                }

                log.info("--- Items ({}):", command.items() != null ? command.items().size() : 0);
                if (command.items() != null) {
                    command.items().forEach(i -> log.info("  Name: {}, ASIN: {}, Seller: {}, Qty: {}, Price: {}",
                            i.itemName(), i.asin(), i.seller(), i.quantity(), i.unitPrice()));
                }

                log.info("--- Shipments ({}):", command.shipments() != null ? command.shipments().size() : 0);
                if (command.shipments() != null) {
                    command.shipments().forEach(s -> log.info("  Date: {}, Carrier: {}, Tracking: {}",
                            s.shippingDate(), s.carrier(), s.trackingNumber()));
                }
                log.info("=======================================");

                log.info("Parsed AmazonOrder successfully. [DRY RUN - skipping DB save]\n\n\n");
            } else {
                log.info("Parsed AmazonOrder successfully (Total: {}, Billed: {}). Saving to DB...",
                        command.totalAmount(), command.billedAmount());
                amazonService.saveOrder(command);
            }
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Failed to process Amazon order from extension: {}", request.orderId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Retrieves an existing Amazon order with all details for the Chrome Extension.
     *
     * @param orderId The Amazon order ID.
     * @return A ResponseEntity containing the populated order details or NOT_FOUND
     *         if it does not exist.
     */
    @org.springframework.web.bind.annotation.GetMapping("/extension/{orderId}")
    public ResponseEntity<AmazonExtensionOrderResponse> getOrderForExtension(
            @org.springframework.web.bind.annotation.PathVariable String orderId) {
        log.info("Fetching existing Amazon order {} for Chrome Extension", orderId);

        try {
            return amazonService.getOrderWithDetails(orderId)
                    .map(amazonExtensionMapper::toResponse)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to fetch Amazon order from extension: {}", orderId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
