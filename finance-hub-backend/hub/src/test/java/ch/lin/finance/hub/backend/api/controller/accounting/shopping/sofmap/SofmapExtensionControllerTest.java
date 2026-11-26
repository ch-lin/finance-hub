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

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.SofmapService;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.sofmap.result.SofmapOrderResult;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto.SofmapExtensionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto.SofmapExtensionOrderItemRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto.SofmapExtensionOrderRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto.SofmapExtensionPaymentRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.sofmap.dto.SofmapExtensionShipmentRequest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapPayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.sofmap.SofmapShipment;

@ExtendWith(MockitoExtension.class)
class SofmapExtensionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SofmapService sofmapService;

    @Spy
    @SuppressWarnings("unused")
    private final SofmapExtensionMapper sofmapExtensionMapper = org.mapstruct.factory.Mappers
            .getMapper(SofmapExtensionMapper.class);

    @InjectMocks
    private SofmapExtensionController controller;

    private ObjectMapper objectMapper;
    private SofmapExtensionOrderRequest request;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();

        SofmapExtensionOrderItemRequest item = new SofmapExtensionOrderItemRequest("Test Game", "12345", "Sofmap", 2,
                new BigDecimal("500"));
        SofmapExtensionPaymentRequest payment = new SofmapExtensionPaymentRequest("Credit Card",
                new BigDecimal("1000"));
        SofmapExtensionShipmentRequest shipment = new SofmapExtensionShipmentRequest("2024-01-02", "Sofmap Default",
                "1234567890");

        request = new SofmapExtensionOrderRequest(
                "046174418", "2024-01-01", new BigDecimal("1500"), new BigDecimal("1000"),
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("500"), new BigDecimal("0"),
                List.of(item), List.of(payment), List.of(shipment));
    }

    @Test
    void receiveOrderFromExtension_Success() throws Exception {
        mockMvc.perform(post("/sofmap/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(sofmapService, times(1)).saveOrder(any(SofmapOrder.class));
    }

    @Test
    void receiveOrderFromExtension_DryRun() throws Exception {
        mockMvc.perform(post("/sofmap/extension")
                .param("dryRun", "true")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(sofmapService, never()).saveOrder(any(SofmapOrder.class));
    }

    @Test
    void receiveOrderFromExtension_WithMissingOptionalFields() throws Exception {
        request = new SofmapExtensionOrderRequest(
                request.orderId(), request.orderDate(), request.totalAmount(), request.billedAmount(),
                null, null, null, null,
                List.of(new SofmapExtensionOrderItemRequest(request.items().get(0).name(),
                        request.items().get(0).itemId(), request.items().get(0).seller(), null, null)),
                List.of(new SofmapExtensionPaymentRequest(request.payments().get(0).method(), null)),
                request.shipments());

        mockMvc.perform(post("/sofmap/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(sofmapService, times(1)).saveOrder(any(SofmapOrder.class));
    }

    @Test
    void receiveOrderFromExtension_WithoutItemsOrPaymentsOrShipments() throws Exception {
        request = new SofmapExtensionOrderRequest(
                request.orderId(), request.orderDate(), request.totalAmount(), request.billedAmount(),
                request.discounts(), request.pointsUsed(), request.shippingFee(), request.refundAmount(),
                null, null, null);

        mockMvc.perform(post("/sofmap/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(sofmapService, times(1)).saveOrder(any(SofmapOrder.class));
    }

    @Test
    void receiveOrderFromExtension_ShipmentWithInvalidDateAndNullDate() throws Exception {
        SofmapExtensionShipmentRequest shipmentInvalid = new SofmapExtensionShipmentRequest("Invalid Date Format",
                "Sofmap Default", "1234567890");
        SofmapExtensionShipmentRequest shipmentNull = new SofmapExtensionShipmentRequest(null, "Sofmap Default",
                "1234567890");

        request = new SofmapExtensionOrderRequest(
                request.orderId(), request.orderDate(), request.totalAmount(), request.billedAmount(),
                request.discounts(), request.pointsUsed(), request.shippingFee(), request.refundAmount(),
                request.items(), request.payments(), List.of(shipmentInvalid, shipmentNull));

        mockMvc.perform(post("/sofmap/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(sofmapService, times(1)).saveOrder(any(SofmapOrder.class));
    }

    @Test
    void receiveOrderFromExtension_ServiceException() throws Exception {
        doThrow(new RuntimeException("DB Error")).when(sofmapService).saveOrder(any(SofmapOrder.class));

        mockMvc.perform(post("/sofmap/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isInternalServerError());

        verify(sofmapService, times(1)).saveOrder(any(SofmapOrder.class));
    }

    @Test
    void getOrderForExtension_Success() throws Exception {
        SofmapOrder order = new SofmapOrder("046174418", java.time.LocalDate.now());
        order.setTotalAmount(new BigDecimal("1500"));

        SofmapItem item = new SofmapItem("Test Game");
        item.setItemId("12345");
        item.setSeller("Sofmap");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("500"));
        order.addItem(item);

        SofmapPayment payment = new SofmapPayment("Credit Card");
        payment.setAmount(new BigDecimal("1500"));
        order.addPayment(payment);

        SofmapShipment shipment = new SofmapShipment(
                java.time.LocalDate.now(), "Sofmap Default", "123", null);
        order.addShipment(shipment);

        when(sofmapService.getOrderWithDetails("046174418"))
                .thenReturn(Optional.of(SofmapOrderResult.fromEntity(order)));

        mockMvc.perform(get("/sofmap/extension/046174418")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("046174418"))
                .andExpect(jsonPath("$.totalAmount").value(1500))
                .andExpect(jsonPath("$.items[0].name").value("Test Game"))
                .andExpect(jsonPath("$.payments[0].method").value("Credit Card"))
                .andExpect(jsonPath("$.shipments[0].carrier").value("Sofmap Default"));

        verify(sofmapService, times(1)).getOrderWithDetails("046174418");
    }

    @Test
    void getOrderForExtension_Success_NullFields() throws Exception {
        SofmapOrder order = new SofmapOrder("D01-null", null);
        order.setTotalAmount(new BigDecimal("1500"));

        SofmapOrder spyOrder = org.mockito.Mockito.spy(order);
        org.mockito.Mockito.doReturn(null).when(spyOrder).getItems();
        org.mockito.Mockito.doReturn(null).when(spyOrder).getPayments();
        org.mockito.Mockito.doReturn(null).when(spyOrder).getShipments();

        Optional<SofmapOrderResult> mockResult = Optional.of(SofmapOrderResult.fromEntity(spyOrder));
        when(sofmapService.getOrderWithDetails("D01-null"))
                .thenReturn(mockResult);

        mockMvc.perform(get("/sofmap/extension/D01-null")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("D01-null"))
                .andExpect(jsonPath("$.orderDate").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.payments").doesNotExist())
                .andExpect(jsonPath("$.shipments").doesNotExist());

        verify(sofmapService, times(1)).getOrderWithDetails("D01-null");
    }

    @Test
    void getOrderForExtension_Success_NullShippingDate() throws Exception {
        SofmapOrder order = new SofmapOrder("D01-null-date", java.time.LocalDate.now());

        SofmapShipment shipment = new SofmapShipment(
                null, "Sofmap Default", "123", null);
        order.addShipment(shipment);

        when(sofmapService.getOrderWithDetails("D01-null-date"))
                .thenReturn(Optional.of(SofmapOrderResult.fromEntity(order)));

        mockMvc.perform(get("/sofmap/extension/D01-null-date")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipments[0].shippingDate").doesNotExist());

        verify(sofmapService, times(1)).getOrderWithDetails("D01-null-date");
    }

    @Test
    void getOrderForExtension_NotFound() throws Exception {
        when(sofmapService.getOrderWithDetails("NOT_FOUND")).thenReturn(Optional.empty());

        mockMvc.perform(get("/sofmap/extension/NOT_FOUND")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isNotFound());

        verify(sofmapService, times(1)).getOrderWithDetails("NOT_FOUND");
    }

    @Test
    void getOrderForExtension_Exception() throws Exception {
        when(sofmapService.getOrderWithDetails("ERR")).thenThrow(new RuntimeException("DB Error"));

        mockMvc.perform(get("/sofmap/extension/ERR")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isInternalServerError());
    }
}
