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

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.AmazonService;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.command.SaveAmazonOrderCommand;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonItemResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonOrderResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonPaymentResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.amazon.result.AmazonShipmentResult;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto.AmazonExtensionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto.AmazonExtensionOrderItemRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto.AmazonExtensionOrderRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto.AmazonExtensionPaymentRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.amazon.dto.AmazonExtensionShipmentRequest;

@ExtendWith(MockitoExtension.class)
class AmazonExtensionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AmazonService amazonService;

    @Spy
    @SuppressWarnings("unused")
    private final AmazonExtensionMapper amazonExtensionMapper = org.mapstruct.factory.Mappers
            .getMapper(AmazonExtensionMapper.class);

    @InjectMocks
    private AmazonExtensionController controller;

    private ObjectMapper objectMapper;
    private AmazonExtensionOrderRequest request;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();

        AmazonExtensionOrderItemRequest item = new AmazonExtensionOrderItemRequest(
                "Test Book", "B000000000", "Amazon Japan G.K.", new BigDecimal("500"), 2);

        AmazonExtensionPaymentRequest payment = new AmazonExtensionPaymentRequest(
                "Credit Card", new BigDecimal("1000"));

        AmazonExtensionShipmentRequest shipment = new AmazonExtensionShipmentRequest(
                "2024年1月2日", "Amazon", "DA1234567890");

        request = new AmazonExtensionOrderRequest(
                "D01-0000000-0000000",
                "2024年1月1日",
                new BigDecimal("1500"),
                new BigDecimal("1000"),
                new BigDecimal("100"),
                new BigDecimal("200"),
                new BigDecimal("100"),
                new BigDecimal("50"),
                new BigDecimal("500"),
                new BigDecimal("0"),
                List.of(item),
                List.of(payment),
                List.of(shipment));
    }

    @Test
    void receiveOrderFromExtension_Success() throws Exception {
        mockMvc.perform(post("/amazon/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(amazonService, times(1)).saveOrder(any(SaveAmazonOrderCommand.class));
    }

    @Test
    void receiveOrderFromExtension_DryRun() throws Exception {
        mockMvc.perform(post("/amazon/extension")
                .param("dryRun", "true")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(amazonService, never()).saveOrder(any(SaveAmazonOrderCommand.class));
    }

    @Test
    void receiveOrderFromExtension_WithMissingOptionalFields() throws Exception {
        AmazonExtensionOrderItemRequest modifiedItem = new AmazonExtensionOrderItemRequest(
                request.items().get(0).itemName(),
                request.items().get(0).asin(),
                request.items().get(0).seller(),
                null,
                null);
        AmazonExtensionPaymentRequest modifiedPayment = new AmazonExtensionPaymentRequest(
                request.payments().get(0).method(),
                null);
        request = new AmazonExtensionOrderRequest(
                request.orderId(),
                request.orderDate(),
                request.totalAmount(),
                request.billedAmount(),
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(modifiedItem),
                List.of(modifiedPayment),
                request.shipments());

        mockMvc.perform(post("/amazon/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(amazonService, times(1)).saveOrder(any(SaveAmazonOrderCommand.class));
    }

    @Test
    void receiveOrderFromExtension_WithoutItemsOrPaymentsOrShipments() throws Exception {
        request = new AmazonExtensionOrderRequest(
                request.orderId(),
                request.orderDate(),
                request.totalAmount(),
                request.billedAmount(),
                request.discounts(),
                request.amazonPointsUsed(),
                request.dPointsUsed(),
                request.giftCardAmount(),
                request.shippingFee(),
                request.refundAmount(),
                null,
                null,
                null);

        mockMvc.perform(post("/amazon/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(amazonService, times(1)).saveOrder(any(SaveAmazonOrderCommand.class));
    }

    @Test
    void receiveOrderFromExtension_ShipmentWithInvalidDateAndNullDate() throws Exception {
        AmazonExtensionShipmentRequest shipmentInvalid = new AmazonExtensionShipmentRequest(
                "Invalid Date Format", null, null);
        AmazonExtensionShipmentRequest shipmentNull = new AmazonExtensionShipmentRequest(
                null, null, null);

        request = new AmazonExtensionOrderRequest(
                request.orderId(),
                request.orderDate(),
                request.totalAmount(),
                request.billedAmount(),
                request.discounts(),
                request.amazonPointsUsed(),
                request.dPointsUsed(),
                request.giftCardAmount(),
                request.shippingFee(),
                request.refundAmount(),
                request.items(),
                request.payments(),
                List.of(shipmentInvalid, shipmentNull));

        mockMvc.perform(post("/amazon/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(amazonService, times(1)).saveOrder(any(SaveAmazonOrderCommand.class));
    }

    @Test
    void receiveOrderFromExtension_ServiceException() throws Exception {
        doThrow(new RuntimeException("DB Error")).when(amazonService).saveOrder(any(SaveAmazonOrderCommand.class));

        mockMvc.perform(post("/amazon/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isInternalServerError());

        verify(amazonService, times(1)).saveOrder(any(SaveAmazonOrderCommand.class));
    }

    @Test
    void getOrderForExtension_Success() throws Exception {
        AmazonItemResult item = new AmazonItemResult(
                "Test Item",
                "B000",
                "Amazon",
                new BigDecimal("500"),
                2);

        AmazonPaymentResult payment = new AmazonPaymentResult(
                "Credit Card",
                new BigDecimal("1500"));

        AmazonShipmentResult shipment = new AmazonShipmentResult(
                java.time.LocalDate.now(),
                "Amazon",
                "123");

        AmazonOrderResult order = new AmazonOrderResult(
                "D01-0000000-0000000",
                java.time.LocalDate.now(),
                new BigDecimal("1500"),
                null, // billedAmount
                null, // discounts
                null, // pointsUsed
                null, // dPointsUsed
                null, // giftCardAmount
                null, // shippingFee
                null, // refundAmount
                List.of(item),
                List.of(payment),
                List.of(shipment));

        when(amazonService.getOrderWithDetails("D01-0000000-0000000")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/amazon/extension/D01-0000000-0000000")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("D01-0000000-0000000"))
                .andExpect(jsonPath("$.totalAmount").value(1500))
                .andExpect(jsonPath("$.items[0].itemName").value("Test Item"))
                .andExpect(jsonPath("$.payments[0].method").value("Credit Card"))
                .andExpect(jsonPath("$.shipments[0].carrier").value("Amazon"));

        verify(amazonService, times(1)).getOrderWithDetails("D01-0000000-0000000");
    }

    @Test
    void getOrderForExtension_Success_NullFields() throws Exception {
        AmazonOrderResult order = new AmazonOrderResult(
                "D01-null",
                null,
                new BigDecimal("1500"),
                null, null, null, null, null, null, null,
                null,
                null,
                null);

        when(amazonService.getOrderWithDetails("D01-null")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/amazon/extension/D01-null")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("D01-null"))
                .andExpect(jsonPath("$.orderDate").doesNotExist())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.payments").isEmpty())
                .andExpect(jsonPath("$.shipments").isEmpty());

        verify(amazonService, times(1)).getOrderWithDetails("D01-null");
    }

    @Test
    void getOrderForExtension_Success_NullShippingDate() throws Exception {
        AmazonShipmentResult shipment = new AmazonShipmentResult(
                null,
                "Amazon",
                "123");

        AmazonOrderResult order = new AmazonOrderResult(
                "D01-null-date",
                java.time.LocalDate.now(),
                null, null, null, null, null, null, null, null,
                null, null,
                List.of(shipment));

        when(amazonService.getOrderWithDetails("D01-null-date")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/amazon/extension/D01-null-date")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipments[0].shippingDate").doesNotExist());

        verify(amazonService, times(1)).getOrderWithDetails("D01-null-date");
    }

    @Test
    void getOrderForExtension_NotFound() throws Exception {
        when(amazonService.getOrderWithDetails("NOT_FOUND")).thenReturn(Optional.empty());

        mockMvc.perform(get("/amazon/extension/NOT_FOUND")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isNotFound());

        verify(amazonService, times(1)).getOrderWithDetails("NOT_FOUND");
    }

    @Test
    void getOrderForExtension_Exception() throws Exception {
        when(amazonService.getOrderWithDetails("ERR")).thenThrow(new RuntimeException("DB Error"));

        mockMvc.perform(get("/amazon/extension/ERR")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isInternalServerError());
    }
}
