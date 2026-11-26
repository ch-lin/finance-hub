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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.result.RakutenItemResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.result.RakutenOrderResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.result.RakutenPaymentResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenPayment;

@Mapper(componentModel = "spring")
public interface RakutenExtensionMapper {

    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "orderDate", source = "orderDate", dateFormat = "yyyy/MM/dd")
    @Mapping(target = "pointsUsed", source = "rakutenPointsUsed")
    @Mapping(target = "needsReview", constant = "false")
    @Mapping(target = "shopId", constant = "RAKUTEN")
    @Mapping(target = "shippingDate", ignore = true)
    @Mapping(target = "reviewReason", ignore = true)
    @Mapping(target = "reconciledAmount", ignore = true)
    @Mapping(target = "taxAmount", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "payments", ignore = true)
    @Mapping(target = "shipments", ignore = true)
    // BaseEntity fields ignored
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerUserId", ignore = true)
    @Mapping(target = "privacyLevel", ignore = true)
    RakutenOrder toEntity(RakutenExtensionOrderRequest request);

    @Mapping(target = "name", source = "itemName")
    @Mapping(target = "itemId", source = "asin")
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "privacyLevel", ignore = true)
    @Mapping(target = "order", ignore = true)
    RakutenItem toEntity(RakutenExtensionOrderItemRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    RakutenPayment toEntity(RakutenExtensionPaymentRequest request);

    @AfterMapping
    default void linkCollections(RakutenExtensionOrderRequest request, @MappingTarget RakutenOrder order) {
        if (request.items() != null) {
            request.items().forEach(itemReq -> {
                RakutenItem item = toEntity(itemReq);
                if (item.getQuantity() == null)
                    item.setQuantity(1);
                order.addItem(item);
            });
        }
        if (request.payments() != null) {
            request.payments().forEach(paymentReq -> {
                order.addPayment(toEntity(paymentReq));
            });
        }
    }

    @Mapping(target = "orderDate", source = "orderDate", dateFormat = "yyyy/MM/dd")
    @Mapping(target = "rakutenPointsUsed", source = "pointsUsed")
    RakutenExtensionOrderResponse toResponse(RakutenOrderResult result);

    @Mapping(target = "itemName", source = "itemName")
    @Mapping(target = "asin", source = "itemId")
    RakutenExtensionOrderItemResponse toResponse(RakutenItemResult result);

    RakutenExtensionPaymentResponse toResponse(RakutenPaymentResult result);
}
