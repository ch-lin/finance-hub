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
package ch.lin.finance.hub.backend.api.app.service.accounting.nisa.sbi;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaContributionSettingRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaSnapshotRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaContributionSetting;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaSnapshotRecord;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "null", "unchecked" })
class SbiNisaJobServiceImplTest {

    @Mock
    private NisaSnapshotRecordRepository nisaRepository;

    @Mock
    private NisaContributionSettingRepository nisaSettingRepository;

    @Mock
    private SbiNisaParser sbiNisaParser;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TransactionStatus transactionStatus;

    @TempDir
    Path tempDir;

    private SbiNisaJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SbiNisaJobServiceImpl(
                nisaRepository,
                nisaSettingRepository,
                sbiNisaParser,
                transactionTemplate,
                tempDir.toString());
    }

    @Test
    @DisplayName("processFile should match existing record and setting by fundCode even if fundName changed, preserving expectedReturnRate")
    void processFile_ExistingByFundCode_MergesAndPreservesExpectedReturnRate() throws Exception {
        File file = tempDir.resolve("nisa.html").toFile();
        Files.writeString(file.toPath(), "<html></html>");

        NisaSnapshotRecord existingRecord = new NisaSnapshotRecord(
                LocalDate.now(), "NISA (つみたて)預り", "0331418A",
                "eMAXIS Slim 全世界株式（オール・カントリー）",
                100.0, 10000.0, 12000.0, 1_000_000L, 1_200_000L, 200_000L);
        existingRecord.updateExpectedReturnRate(0.055);

        NisaSnapshotRecord incomingRecord = new NisaSnapshotRecord(
                LocalDate.now(), "NISA (つみたて)預り", "0331418A",
                "eMAXIS Slim 全世界株式（オール・カントリー）　",
                150.0, 10500.0, 13000.0, 1_575_000L, 1_950_000L, 375_000L);

        NisaContributionSetting existingSetting = new NisaContributionSetting(
                LocalDate.now(), "NISA (つみたて)", "0331418A",
                "eMAXIS Slim 全世界株式（オール・カントリー）",
                "毎月", 50_000L);
        existingSetting.updateExpectedReturnRate(0.06);

        NisaContributionSetting incomingSetting = new NisaContributionSetting(
                LocalDate.now(), "NISA (つみたて)", "0331418A",
                "eMAXIS Slim 全世界株式（オール・カントリー）　",
                "毎月", 100_000L);

        when(sbiNisaParser.parse(any(InputStream.class), any(LocalDate.class)))
                .thenReturn(List.of(incomingRecord));
        when(sbiNisaParser.parseSettings(any(InputStream.class), any(LocalDate.class)))
                .thenReturn(List.of(incomingSetting));

        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(transactionStatus);
        });

        when(nisaRepository.findByRecordDateAndAccountTypeAndFundCode(
                any(LocalDate.class), eq("NISA (つみたて)預り"), eq("0331418A")))
                .thenReturn(Optional.of(existingRecord));
        when(nisaSettingRepository.findByRecordDateAndAccountTypeAndFundCodeAndCourse(
                any(LocalDate.class), eq("NISA (つみたて)"), eq("0331418A"), eq("毎月")))
                .thenReturn(Optional.of(existingSetting));

        FileProcessingResult result = service.processFile(file, false, "user-1");

        assertNotNull(result);
        assertEquals("success", result.status());

        verify(nisaRepository).save(existingRecord);
        assertEquals(150.0, existingRecord.getUnits());
        assertEquals(1_950_000L, existingRecord.getCurrentMarketValue());
        assertEquals("eMAXIS Slim 全世界株式（オール・カントリー）　", existingRecord.getFundName());
        assertEquals(0.055, existingRecord.getExpectedReturnRate());

        verify(nisaSettingRepository).save(existingSetting);
        assertEquals(100_000L, existingSetting.getSettingAmount());
        assertEquals(0.06, existingSetting.getExpectedReturnRate());
    }

    @Test
    @DisplayName("processFile should inherit historical expectedReturnRate by fundCode when saving new date snapshot")
    void processFile_NewDate_InheritsHistoricalRateByFundCode() throws Exception {
        File file = tempDir.resolve("nisa_new.html").toFile();
        Files.writeString(file.toPath(), "<html></html>");

        NisaSnapshotRecord historicalRecord = new NisaSnapshotRecord(
                LocalDate.now().minusMonths(1), "NISA (つみたて)預り", "0331418A",
                "eMAXIS Slim 全世界株式（オール・カントリー）",
                100.0, 10000.0, 12000.0, 1_000_000L, 1_200_000L, 200_000L);
        historicalRecord.updateExpectedReturnRate(0.055);

        NisaSnapshotRecord incomingRecord = new NisaSnapshotRecord(
                LocalDate.now(), "NISA (つみたて)預り", "0331418A",
                "eMAXIS Slim 全世界株式（オール・カントリー）　",
                150.0, 10500.0, 13000.0, 1_575_000L, 1_950_000L, 375_000L);

        when(sbiNisaParser.parse(any(InputStream.class), any(LocalDate.class)))
                .thenReturn(List.of(incomingRecord));
        when(sbiNisaParser.parseSettings(any(InputStream.class), any(LocalDate.class)))
                .thenReturn(List.of());

        when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(transactionStatus);
        });

        when(nisaRepository.findByRecordDateAndAccountTypeAndFundCode(
                any(LocalDate.class), eq("NISA (つみたて)預り"), eq("0331418A")))
                .thenReturn(Optional.empty());
        when(nisaRepository.findFirstByAccountTypeAndFundCodeOrderByRecordDateDesc(
                eq("NISA (つみたて)預り"), eq("0331418A")))
                .thenReturn(Optional.of(historicalRecord));

        service.processFile(file, false, "user-1");

        verify(nisaRepository).save(incomingRecord);
        assertEquals(0.055, incomingRecord.getExpectedReturnRate());
    }
}
