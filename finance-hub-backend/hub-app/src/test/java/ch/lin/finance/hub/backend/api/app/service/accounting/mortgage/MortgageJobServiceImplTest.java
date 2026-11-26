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
package ch.lin.finance.hub.backend.api.app.service.accounting.mortgage;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.parser.MortgageParser;
import ch.lin.finance.hub.backend.api.app.service.accounting.mortgage.parser.MortgageParserFactory;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.EarlyRepaymentType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageEarlyRepayment;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class MortgageJobServiceImplTest {

    @Mock
    private MortgageParserFactory parserFactory;

    @Mock
    private MortgageParser parser;

    @Mock
    private MortgageRecordRepository repository;

    @TempDir
    Path tempDir;

    private MortgageJobServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = new MortgageJobServiceImpl(parserFactory, repository);
        Field basePathField = MortgageJobServiceImpl.class.getDeclaredField("basePath");
        basePathField.setAccessible(true);
        basePathField.set(service, tempDir.toString());
    }

    @Test
    void testGetSource() {
        assertEquals(TransactionSource.MORTGAGE, service.getSource());
    }

    @Test
    void testProcessFile_NewRecord_SavesDirectly() throws Exception {
        File file = tempDir.resolve("sony_new.html").toFile();
        Files.writeString(file.toPath(), "<html></html>");

        MortgageRecord parsed = new MortgageRecord(
                "ソニー銀行", "SN-100", "変動金利住宅ローン", 50_000_000L, 49_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 4, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, new ArrayList<>()
        );

        when(parserFactory.getParser(file)).thenReturn(parser);
        when(parser.parse(file)).thenReturn(parsed);
        when(repository.findByBankNameAndContractNumber("ソニー銀行", "SN-100")).thenReturn(Optional.empty());

        FileProcessingResult result = service.processFile(file, false, "user-1");

        assertNotNull(result);
        assertEquals("success", result.status());
        assertFalse(result.dryRun());
        verify(repository).save(parsed);
    }

    @Test
    void testProcessFile_ExistingRecord_MergesAndSaves_PreservesUserManagedFields() throws Exception {
        File file = tempDir.resolve("sony_existing.html").toFile();
        Files.writeString(file.toPath(), "<html></html>");

        // Existing entity with user-managed configurations
        MortgageRecord existing = new MortgageRecord(
                "ソニー銀行", "SN-100", "変動金利住宅ローン", 50_000_000L, 49_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 4, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, new ArrayList<>()
        );
        existing.setOccupancyDate(LocalDate.of(2025, 5, 1));
        existing.setTaxDeductionEligible(true);
        existing.getEarlyRepayments().add(new MortgageEarlyRepayment(
                existing, LocalDate.of(2025, 6, 1), 500_000L,
                EarlyRepaymentType.REDUCE_TERM, 0L, 5
        ));

        // Newly parsed record from statement with updated balance
        MortgageRecord incoming = new MortgageRecord(
                "ソニー銀行", "SN-100", "変動金利住宅ローン", 50_000_000L, 46_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 7, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, new ArrayList<>()
        );

        when(parserFactory.getParser(file)).thenReturn(parser);
        when(parser.parse(file)).thenReturn(incoming);
        when(repository.findByBankNameAndContractNumber("ソニー銀行", "SN-100")).thenReturn(Optional.of(existing));

        FileProcessingResult result = service.processFile(file, false, "user-1");

        assertNotNull(result);
        assertEquals("success", result.status());

        // Verify repository saved the existing entity
        verify(repository).save(existing);
        // Verify facts updated
        assertEquals(46_000_000L, existing.getCurrentBalance());
        assertEquals(LocalDate.of(2025, 7, 1), existing.getBalanceRecordDate());
        // Verify user custom fields preserved
        assertEquals(LocalDate.of(2025, 5, 1), existing.getOccupancyDate());
        assertTrue(existing.getTaxDeductionEligible());
        assertEquals(1, existing.getEarlyRepayments().size());
        assertEquals(500_000L, existing.getEarlyRepayments().get(0).getAmount());
    }

    @Test
    void testProcessFile_DryRun_DoesNotSave() throws Exception {
        File file = tempDir.resolve("sony_dryrun.html").toFile();
        Files.writeString(file.toPath(), "<html></html>");

        MortgageRecord parsed = new MortgageRecord(
                "ソニー銀行", "SN-100", "変動金利住宅ローン", 50_000_000L, 49_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 4, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, new ArrayList<>()
        );

        when(parserFactory.getParser(file)).thenReturn(parser);
        when(parser.parse(file)).thenReturn(parsed);

        FileProcessingResult result = service.processFile(file, true, "user-1");

        assertNotNull(result);
        assertEquals("success", result.status());
        assertTrue(result.dryRun());
        verify(repository, never()).save(any());
    }

    @Test
    void testExecute_NoFiles_ReturnsZeroResult() throws Exception {
        Path sourceDir = tempDir.resolve("mortgage/source");
        Files.createDirectories(sourceDir);

        JobResult result = service.execute(false);

        assertEquals(0, result.totalFiles());
        assertEquals(0, result.successCount());
        assertEquals(0, result.failedCount());
    }

    @Test
    void testExecute_Success_MovesToArchiveSuccess() throws Exception {
        Path sourceDir = tempDir.resolve("mortgage/source");
        Files.createDirectories(sourceDir);
        File file = sourceDir.resolve("202504_sony.html").toFile();
        Files.writeString(file.toPath(), "<html></html>");

        MortgageRecord parsed = new MortgageRecord(
                "ソニー銀行", "SN-100", "変動金利住宅ローン", 50_000_000L, 49_000_000L,
                LocalDate.of(2025, 3, 25), LocalDate.of(2060, 3, 10), LocalDate.of(2025, 4, 1),
                420, 0.005, InterestRateType.VARIABLE, RepaymentMethod.EQUAL_PAYMENT,
                129_567L, new ArrayList<>()
        );

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parsed);
        when(repository.findByBankNameAndContractNumber("ソニー銀行", "SN-100")).thenReturn(Optional.empty());

        JobResult result = service.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());
        assertEquals(0, result.failedCount());
        verify(repository).save(parsed);
    }
}
