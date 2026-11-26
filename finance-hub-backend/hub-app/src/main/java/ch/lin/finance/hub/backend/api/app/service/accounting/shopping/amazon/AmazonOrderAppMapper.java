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

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonItemCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonOrderCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonPaymentCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonShipmentCommand;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonPayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonShipment;

public class AmazonOrderAppMapper {

    public static AmazonOrder toEntity(SaveAmazonOrderCommand command) {
        if (command == null)
            return null;

        AmazonOrder order = new AmazonOrder(command.orderId(), command.orderDate());
        order.setTotalAmount(command.totalAmount());
        order.setBilledAmount(command.billedAmount());
        order.setDiscounts(command.discounts());
        order.setPointsUsed(command.amazonPointsUsed());
        order.setDPointsUsed(command.dPointsUsed());
        order.setGiftCardAmount(command.giftCardAmount());
        order.setShippingFee(command.shippingFee());
        order.setRefundAmount(command.refundAmount());
        order.setNeedsReview(false);

        if (command.items() != null) {
            for (SaveAmazonItemCommand itemCmd : command.items()) {
                AmazonItem item = toEntity(itemCmd);
                if (item.getQuantity() == null)
                    item.setQuantity(1);
                order.addItem(item);
            }
        }
        if (command.payments() != null) {
            for (SaveAmazonPaymentCommand paymentCmd : command.payments()) {
                order.addPayment(toEntity(paymentCmd));
            }
        }
        if (command.shipments() != null) {
            for (SaveAmazonShipmentCommand shipmentCmd : command.shipments()) {
                order.addShipment(toEntity(shipmentCmd));
            }
        }

        return order;
    }

    public static AmazonItem toEntity(SaveAmazonItemCommand command) {
        if (command == null)
            return null;

        AmazonItem item = new AmazonItem(command.itemName());
        item.setItemId(command.asin());
        item.setSeller(command.seller());
        item.setUnitPrice(command.unitPrice());
        item.setQuantity(command.quantity());
        return item;
    }

    public static AmazonPayment toEntity(SaveAmazonPaymentCommand command) {
        if (command == null)
            return null;

        AmazonPayment payment = new AmazonPayment(command.method());
        payment.setAmount(command.amount());
        return payment;
    }

    public static AmazonShipment toEntity(SaveAmazonShipmentCommand command) {
        if (command == null)
            return null;

        AmazonShipment shipment = new AmazonShipment(
                command.shippingDate(),
                command.carrier(),
                command.trackingNumber());
        return shipment;
    }
}
