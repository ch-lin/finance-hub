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
package ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonItemCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonOrderCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonPaymentCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonShipmentCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonItemResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonOrderResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonPaymentResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonShipmentResult;

@Mapper(componentModel = "spring")
public interface AmazonExtensionMapper {

    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "orderDate", source = "orderDate")
    SaveAmazonOrderCommand toCommand(AmazonExtensionOrderRequest request);

    @Mapping(target = "itemName", source = "itemName")
    @Mapping(target = "asin", source = "asin")
    SaveAmazonItemCommand toCommand(AmazonExtensionOrderItemRequest request);

    SaveAmazonPaymentCommand toCommand(AmazonExtensionPaymentRequest request);

    @Mapping(target = "shippingDate", source = "shippingDate")
    SaveAmazonShipmentCommand toCommand(AmazonExtensionShipmentRequest request);

    // Response mappings
    @Mapping(target = "orderDate", source = "orderDate")
    @Mapping(target = "amazonPointsUsed", source = "pointsUsed")
    @Mapping(target = "dPointsUsed", source = "DPointsUsed")
    AmazonExtensionOrderResponse toResponse(AmazonOrderResult result);

    @Mapping(target = "itemName", source = "itemName")
    @Mapping(target = "asin", source = "asin")
    AmazonExtensionOrderItemResponse toResponse(AmazonItemResult result);

    AmazonExtensionPaymentResponse toResponse(AmazonPaymentResult result);

    @Mapping(target = "shippingDate", source = "shippingDate")
    AmazonExtensionShipmentResponse toResponse(AmazonShipmentResult result);

    default java.time.LocalDate mapDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(dateStr,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日", java.util.Locale.JAPAN));
        } catch (Exception e) {
            return null;
        }
    }

    default String mapDate(java.time.LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年M月d日", java.util.Locale.JAPAN));
    }
}
