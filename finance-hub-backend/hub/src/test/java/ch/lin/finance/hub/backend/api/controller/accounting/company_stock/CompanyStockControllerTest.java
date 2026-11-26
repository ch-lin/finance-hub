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
package ch.lin.finance.hub.backend.api.controller.accounting.company_stock;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.CompanyStockService;
import ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.command.SaveETradeExtensionCommand;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingStrategy;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.CompanyStockMapper;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.request.ETradeExtensionRequest;
import ch.lin.finance.hub.backend.api.controller.accounting.company_stock.dto.response.CompanyStockResponse;

@ExtendWith(MockitoExtension.class)
class CompanyStockControllerTest {

    private MockMvc mockMvc;

    @Mock(extraInterfaces = { FileProcessingStrategy.class })
    private CompanyStockService companyStockService;

    @Mock
    private CompanyStockMapper mapper;

    @InjectMocks
    private CompanyStockController companyStockController;

    private MockedStatic<SecurityUtil> mockedSecurityUtil;

    private final String currentUserId = "test-user-id";

    @SuppressWarnings("unchecked")
    private <T> T createInstance(Class<T> clazz) {
        try {
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            Constructor<?> constructor = constructors[0];
            constructor.setAccessible(true);
            Object[] args = new Object[constructor.getParameterCount()];
            return (T) constructor.newInstance(args);
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
            throw new RuntimeException("Failed to create instance for " + clazz.getName(), e);
        }
    }

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(companyStockController).build();
        mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class);
        mockedSecurityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(currentUserId);
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        if (mockedSecurityUtil != null) {
            mockedSecurityUtil.close();
        }
    }

    @Test
    void uploadETradeFile_shouldReturnFileProcessingResult() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "holdings.html", MediaType.TEXT_HTML_VALUE,
                "<html></html>".getBytes());
        FileProcessingResult result = new FileProcessingResult("Success", false, null);

        when(((FileProcessingStrategy) companyStockService).processFile(any(), eq(false), eq(null))).thenReturn(result);

        mockMvc.perform(multipart("/assets/company-stock/etrade/upload")
                .file(file)
                .param("dryRun", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Success"));
    }

    @Test
    void receiveETradeFromExtension_shouldReturnOk() throws Exception {
        String requestJson = """
                {
                    "recordDate": "2023-10-01"
                }
                """;

        SaveETradeExtensionCommand command = createInstance(SaveETradeExtensionCommand.class);
        when(mapper.toCommand(any(ETradeExtensionRequest.class))).thenReturn(command);

        mockMvc.perform(post("/assets/company-stock/etrade/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(requestJson))
                .andExpect(status().isOk());

        verify(companyStockService).saveExtensionData(command, false);
    }

    @Test
    void getAllSnapshots_shouldReturnList() throws Exception {
        CompanyStockResponse.Snapshot response = createInstance(CompanyStockResponse.Snapshot.class);
        ReflectionTestUtils.setField(Objects.requireNonNull(response), "id", 1L);

        when(companyStockService.getAllSnapshots()).thenReturn(List.of());
        when(mapper.toSnapshotResponseList(any(), eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/assets/company-stock/etrade")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void getStockLots_shouldReturnList() throws Exception {
        CompanyStockResponse.StockLot response = createInstance(CompanyStockResponse.StockLot.class);
        ReflectionTestUtils.setField(Objects.requireNonNull(response), "id", 2L);

        when(companyStockService.getStockLots()).thenReturn(List.of());
        when(mapper.toStockLotResponseList(any(), eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/assets/company-stock/etrade/lots")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2L));
    }

    @Test
    void getCashTransactions_shouldReturnList() throws Exception {
        CompanyStockResponse.CashTransaction response = createInstance(CompanyStockResponse.CashTransaction.class);
        ReflectionTestUtils.setField(Objects.requireNonNull(response), "id", 3L);

        when(companyStockService.getCashTransactions()).thenReturn(List.of());
        when(mapper.toCashTransactionResponseList(any(), eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/assets/company-stock/etrade/cash-transactions")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3L));
    }

    @Test
    void getEsppPurchases_shouldReturnList() throws Exception {
        CompanyStockResponse.EsppPurchase response = createInstance(CompanyStockResponse.EsppPurchase.class);
        ReflectionTestUtils.setField(Objects.requireNonNull(response), "id", 4L);

        when(companyStockService.getEsppPurchases()).thenReturn(List.of());
        when(mapper.toEsppPurchaseResponseList(any(), eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/assets/company-stock/etrade/espp")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4L));
    }

    @Test
    void getRsuGrants_shouldReturnList() throws Exception {
        CompanyStockResponse.RsuGrant response = createInstance(CompanyStockResponse.RsuGrant.class);
        ReflectionTestUtils.setField(Objects.requireNonNull(response), "id", 5L);

        when(companyStockService.getRsuGrants()).thenReturn(List.of());
        when(mapper.toRsuGrantResponseList(any(), eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/assets/company-stock/etrade/rsu-grants")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5L));
    }

    @Test
    void getRsuEvents_shouldReturnList() throws Exception {
        CompanyStockResponse.RsuEvent response = createInstance(CompanyStockResponse.RsuEvent.class);
        ReflectionTestUtils.setField(Objects.requireNonNull(response), "id", 6L);

        when(companyStockService.getRsuEvents()).thenReturn(List.of());
        when(mapper.toRsuEventResponseList(any(), eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/assets/company-stock/etrade/rsu-events")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(6L));
    }

    @Test
    void getRsuVestSchedules_shouldReturnList() throws Exception {
        CompanyStockResponse.RsuVestSchedule response = createInstance(CompanyStockResponse.RsuVestSchedule.class);
        ReflectionTestUtils.setField(Objects.requireNonNull(response), "id", 7L);

        when(companyStockService.getRsuVestSchedules()).thenReturn(List.of());
        when(mapper.toRsuVestScheduleResponseList(any(), eq(currentUserId))).thenReturn(List.of(response));

        mockMvc.perform(get("/assets/company-stock/etrade/rsu-vest-schedules")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7L));
    }

    @Test
    void instantiateCompanyStockResponse_forCoverage() {
        CompanyStockResponse response = new CompanyStockResponse();
        assertNotNull(response);
    }

    @Test
    void uploadETradeFile_shouldReturnBadRequest_whenExceptionThrown() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "holdings.html", MediaType.TEXT_HTML_VALUE,
                "<html></html>".getBytes());

        when(((FileProcessingStrategy) companyStockService).processFile(any(), eq(false), eq(null)))
                .thenThrow(new RuntimeException("Test exception"));

        mockMvc.perform(multipart("/assets/company-stock/etrade/upload")
                .file(file)
                .param("dryRun", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error: Test exception"));
    }

    @Test
    void uploadETradeFile_shouldHandleFileDeleteFailure() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "holdings.html", MediaType.TEXT_HTML_VALUE,
                "<html></html>".getBytes());
        FileProcessingResult result = new FileProcessingResult("Success", false, null);

        when(((FileProcessingStrategy) companyStockService).processFile(any(), eq(false), eq(null))).thenReturn(result);

        Path realPath = Files.createTempFile("dummy", ".html");
        File dummyFile = new java.io.File(realPath.toString()) {
            @Override
            public boolean delete() {
                return false;
            }
        };

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class, Mockito.CALLS_REAL_METHODS)) {
            Path mockPath = Mockito.mock(Path.class);
            when(mockPath.toFile()).thenReturn(dummyFile);
            mockedFiles.when(() -> Files.createTempFile("etrade-upload-", ".html")).thenReturn(mockPath);

            mockMvc.perform(multipart("/assets/company-stock/etrade/upload")
                    .file(file)
                    .param("dryRun", "false"))
                    .andExpect(status().isOk());
        } finally {
            // Clean up the actual file after the test
            if (realPath.toFile().exists()) {
                realPath.toFile().delete();
            }
        }
    }

    @Test
    void receiveETradeFromExtension_shouldReturnBadRequest_whenExceptionThrown() throws Exception {
        String requestJson = """
                {
                    "recordDate": "2023-10-01"
                }
                """;

        when(mapper.toCommand(any(ETradeExtensionRequest.class))).thenThrow(new RuntimeException("Test exception"));

        mockMvc.perform(post("/assets/company-stock/etrade/extension")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(requestJson))
                .andExpect(status().isBadRequest());
    }
}
