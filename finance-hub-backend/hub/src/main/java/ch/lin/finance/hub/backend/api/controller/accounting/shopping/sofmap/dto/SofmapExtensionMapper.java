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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto;

import java.math.BigDecimal;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result.SofmapItemResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result.SofmapOrderResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result.SofmapPaymentResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result.SofmapShipmentResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapPayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapShipment;

@Mapper(componentModel = "spring")
public interface SofmapExtensionMapper {

    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "orderDate", source = "orderDate", dateFormat = "yyyy-MM-dd") // ISO_LOCAL_DATE is yyyy-MM-dd
    @Mapping(target = "needsReview", constant = "false")
    @Mapping(target = "earnedPoints", ignore = true)
    @Mapping(target = "shippingDate", ignore = true)
    @Mapping(target = "reviewReason", ignore = true)
    @Mapping(target = "reconciledAmount", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "payments", ignore = true)
    @Mapping(target = "shipments", ignore = true)
    // BaseEntity fields ignored
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerUserId", ignore = true)
    @Mapping(target = "privacyLevel", ignore = true)
    SofmapOrder toEntity(SofmapExtensionOrderRequest request);

    @Mapping(target = "itemName", source = "name") // Map to constructor arg
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "privacyLevel", ignore = true)
    @Mapping(target = "order", ignore = true)
    SofmapItem toEntity(SofmapExtensionOrderItemRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    SofmapPayment toEntity(SofmapExtensionPaymentRequest request);

    @Mapping(target = "shippingDate", source = "shippingDate", dateFormat = "yyyy-MM-dd")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "order", ignore = true)
    SofmapShipment toEntity(SofmapExtensionShipmentRequest request);

    @AfterMapping
    default void linkCollections(SofmapExtensionOrderRequest request, @MappingTarget SofmapOrder order) {
        if (request.items() != null) {
            request.items().forEach(itemReq -> {
                SofmapItem item = toEntity(itemReq);
                if (itemReq.unitPrice() == null)
                    item.setUnitPrice(BigDecimal.ZERO);
                if (itemReq.quantity() == null)
                    item.setQuantity(1);
                order.addItem(item);
            });
        }
        if (request.payments() != null) {
            request.payments().forEach(paymentReq -> {
                SofmapPayment payment = toEntity(paymentReq);
                if (paymentReq.amount() == null)
                    payment.setAmount(BigDecimal.ZERO);
                order.addPayment(payment);
            });
        }
        if (request.shipments() != null) {
            request.shipments().forEach(shipReq -> {
                try {
                    SofmapShipment shipment = toEntity(shipReq);
                    order.addShipment(shipment);
                } catch (Exception e) {
                    // Similar to the original catch block in controller
                    SofmapShipment shipment = new SofmapShipment(null, shipReq.carrier(), shipReq.trackingNumber(),
                            null);
                    order.addShipment(shipment);
                }
            });
        }
    }

    @Mapping(target = "orderDate", source = "orderDate", dateFormat = "yyyy-MM-dd")
    SofmapExtensionOrderResponse toResponse(SofmapOrderResult result);

    SofmapExtensionOrderItemResponse toResponse(SofmapItemResult result);

    SofmapExtensionPaymentResponse toResponse(SofmapPaymentResult result);

    @Mapping(target = "shippingDate", source = "shippingDate", dateFormat = "yyyy-MM-dd")
    SofmapExtensionShipmentResponse toResponse(SofmapShipmentResult result);
}
