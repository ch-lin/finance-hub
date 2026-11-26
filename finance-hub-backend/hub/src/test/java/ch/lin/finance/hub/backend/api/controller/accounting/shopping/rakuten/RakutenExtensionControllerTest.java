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

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.RakutenService;
import ch.lin.finance.hub.backend.api.app.service.accounting.shopping.rakuten.result.RakutenOrderResult;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto.RakutenExtensionMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto.RakutenExtensionOrderItemRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto.RakutenExtensionOrderRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.shopping.rakuten.dto.RakutenExtensionPaymentRequest;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenOrder;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.rakuten.RakutenPayment;

@ExtendWith(MockitoExtension.class)
class RakutenExtensionControllerTest {

    @Mock
    private RakutenService rakutenService;

    @Spy
    @SuppressWarnings("unused")
    private final RakutenExtensionMapper rakutenExtensionMapper = org.mapstruct.factory.Mappers
            .getMapper(RakutenExtensionMapper.class);

    @InjectMocks
    private RakutenExtensionController rakutenExtensionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(rakutenExtensionController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("receiveOrderFromExtension: Fully populated request, dryRun=false")
    void receiveOrderFromExtension_FullRequest_SavesOrder() throws Exception {
        RakutenExtensionPaymentRequest paymentReq = new RakutenExtensionPaymentRequest("Credit Card",
                new BigDecimal("1200"));
        RakutenExtensionOrderItemRequest itemReq = new RakutenExtensionOrderItemRequest("Test Item", "ITEM001",
                "Test Seller", new BigDecimal("1000"), 1);

        RakutenExtensionOrderRequest request = new RakutenExtensionOrderRequest(
                "order-123", "2024/05/20", new BigDecimal("1500"), new BigDecimal("1200"),
                new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("500"), new BigDecimal("0"),
                List.of(itemReq), List.of(paymentReq));

        mockMvc.perform(post("/rakuten/extension")
                .contentType(Objects.requireNonNull(
                        MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(rakutenService, times(1)).saveOrder(any(RakutenOrder.class));
    }

    @Test
    @DisplayName("receiveOrderFromExtension: Missing optional fields, dryRun=true")
    void receiveOrderFromExtension_MissingOptionals_DryRun() throws Exception {
        RakutenExtensionPaymentRequest paymentReq = new RakutenExtensionPaymentRequest("Points", null);
        RakutenExtensionOrderItemRequest itemReq = new RakutenExtensionOrderItemRequest("Test Item 2", "ITEM002",
                "Test Seller 2", null, null);

        RakutenExtensionOrderRequest request = new RakutenExtensionOrderRequest(
                "order-456", "2024/05/21", new BigDecimal("1000"), new BigDecimal("1000"),
                null, null, null, null,
                List.of(itemReq), List.of(paymentReq));

        mockMvc.perform(post("/rakuten/extension")
                .param("dryRun", "true")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(rakutenService, never()).saveOrder(any(RakutenOrder.class));
    }

    @Test
    @DisplayName("receiveOrderFromExtension: Zero items and missing payments, dryRun=false")
    void receiveOrderFromExtension_ZeroItems_SavesOrder() throws Exception {
        RakutenExtensionOrderRequest request = new RakutenExtensionOrderRequest(
                "order-789", "2024/05/22", new BigDecimal("500"), new BigDecimal("500"),
                null, null, null, null,
                null, null);

        mockMvc.perform(post("/rakuten/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(rakutenService, times(1)).saveOrder(any(RakutenOrder.class));
    }

    @Test
    @DisplayName("receiveOrderFromExtension: Exception handling")
    void receiveOrderFromExtension_Exception_Returns500() throws Exception {
        RakutenExtensionOrderRequest request = new RakutenExtensionOrderRequest(
                "order-ERR", "2024/05/23", new BigDecimal("500"), new BigDecimal("500"),
                null, null, null, null,
                null, null);

        doThrow(new RuntimeException("DB Error")).when(rakutenService).saveOrder(any(RakutenOrder.class));

        mockMvc.perform(post("/rakuten/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isInternalServerError());

        verify(rakutenService, times(1)).saveOrder(any(RakutenOrder.class));
    }

    @Test
    @DisplayName("getOrderForExtension: Order found")
    void getOrderForExtension_Success() throws Exception {
        RakutenOrder order = new RakutenOrder("order-123", java.time.LocalDate.now());
        order.setTotalAmount(new BigDecimal("1500"));

        // Add items and payments to cover non-null branches
        RakutenItem item = new RakutenItem("Test Item");
        item.setItemId("ITEM1");
        item.setSeller("Seller");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("500"));
        order.addItem(item);

        RakutenPayment payment = new RakutenPayment(
                "Credit Card");
        payment.setAmount(new BigDecimal("1500"));
        order.addPayment(payment);

        RakutenOrderResult orderResult = RakutenOrderResult.fromEntity(order);
        when(rakutenService.getOrderWithDetails("order-123")).thenReturn(Optional.of(orderResult));

        mockMvc.perform(get("/rakuten/extension/order-123")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-123"))
                .andExpect(jsonPath("$.totalAmount").value(1500))
                .andExpect(jsonPath("$.items[0].itemName").value("Test Item"))
                .andExpect(jsonPath("$.payments[0].method").value("Credit Card"));

        verify(rakutenService, times(1)).getOrderWithDetails("order-123");
    }

    @Test
    @DisplayName("getOrderForExtension: Order found with null fields")
    void getOrderForExtension_Success_NullFields() throws Exception {
        // Order date is null
        RakutenOrder order = new RakutenOrder("order-null", null);
        order.setTotalAmount(new BigDecimal("500"));
        // By default, a new RakutenOrder might initialize items/payments to empty
        // lists.
        // We explicitly set them to null if there are setters, but wait, there are no
        // setters for lists, just add methods?
        // Let's check RakutenOrder class to see if we can set them to null or if they
        // are never null.
        // If they are never null, the null check in the controller is defensive.
        // We can use Mockito to mock the order to return null for these getters.

        RakutenOrder spyOrder = org.mockito.Mockito.spy(order);
        org.mockito.Mockito.doReturn(null).when(spyOrder).getItems();
        org.mockito.Mockito.doReturn(null).when(spyOrder).getPayments();

        RakutenOrderResult orderResult = RakutenOrderResult.fromEntity(spyOrder);
        when(rakutenService.getOrderWithDetails("order-null")).thenReturn(Optional.of(orderResult));

        mockMvc.perform(get("/rakuten/extension/order-null")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-null"))
                .andExpect(jsonPath("$.orderDate").doesNotExist()) // or is null depending on jsonPath
                .andExpect(jsonPath("$.items").doesNotExist())
                .andExpect(jsonPath("$.payments").doesNotExist());

        verify(rakutenService, times(1)).getOrderWithDetails("order-null");
    }

    @Test
    @DisplayName("getOrderForExtension: Order not found returns 404")
    void getOrderForExtension_NotFound() throws Exception {
        when(rakutenService.getOrderWithDetails("NOT_FOUND")).thenReturn(Optional.empty());

        mockMvc.perform(get("/rakuten/extension/NOT_FOUND")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isNotFound());

        verify(rakutenService, times(1)).getOrderWithDetails("NOT_FOUND");
    }

    @Test
    @DisplayName("getOrderForExtension: Exception returns 500")
    void getOrderForExtension_Exception() throws Exception {
        when(rakutenService.getOrderWithDetails("ERR")).thenThrow(new RuntimeException("DB Error"));

        mockMvc.perform(get("/rakuten/extension/ERR")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isInternalServerError());
    }
}
