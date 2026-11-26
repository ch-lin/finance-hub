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
package ch.lin.finance.hub.backend.api.app.service.currency;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.currency.ExchangeRateRecordRepository;
import ch.lin.finance.hub.backend.api.app.service.currency.parser.ExchangeRateParseResult;
import ch.lin.finance.hub.backend.api.app.service.currency.parser.ExchangeRateParser;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateRecord;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateType;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;

@ExtendWith(MockitoExtension.class)
class ExchangeRateJobServiceImplTest {

    @Mock
    private ExchangeRateRecordRepository repository;

    @Mock
    private ExchangeRateParser parser;

    @Mock
    private TransactionTemplate transactionTemplate;

    private ExchangeRateJobServiceImpl jobService;

    @TempDir
    File tempDir;

    @BeforeEach
    @SuppressWarnings({ "null", "unused" })
    void setUp() {
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());

        jobService = new ExchangeRateJobServiceImpl(
                repository,
                List.of(parser),
                transactionTemplate,
                tempDir.getAbsolutePath());
    }

    @Test
    void shouldReturnExchangeRateSource() {
        assertThat(jobService.getSource()).isEqualTo(TransactionSource.EXCHANGE_RATE);
    }

    @Test
    void shouldProcessFileAndSaveRecords() throws Exception {
        File sourceDir = new File(tempDir, "exchange_rates/source");
        sourceDir.mkdirs();
        File sampleFile = new File(sourceDir, "murc_2025.xls");
        sampleFile.createNewFile();

        ExchangeRateRecord record = new ExchangeRateRecord(
                LocalDate.of(2025, 1, 6),
                CurrencyCode.USD,
                CurrencyCode.JPY,
                BigDecimal.valueOf(155.62),
                ExchangeRateType.TTM,
                "MURC");

        when(parser.canParse(any(File.class))).thenReturn(true);
        when(parser.parse(any(File.class))).thenReturn(ExchangeRateParseResult.success(List.of(record)));
        when(repository.findByRecordDateAndBaseCurrencyAndTargetCurrencyAndRateType(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        JobResult result = jobService.execute(false);

        assertThat(result.totalFiles()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(0);
        verify(repository).save(record);
    }

    @Test
    void shouldProcessSingleFileSuccessfully() throws Exception {
        File sampleFile = new File(tempDir, "murc_2025.xls");
        sampleFile.createNewFile();

        ExchangeRateRecord record = new ExchangeRateRecord(
                LocalDate.of(2025, 1, 6),
                CurrencyCode.USD,
                CurrencyCode.JPY,
                BigDecimal.valueOf(155.62),
                ExchangeRateType.TTM,
                "MURC");

        when(parser.canParse(any(File.class))).thenReturn(true);
        when(parser.parse(any(File.class))).thenReturn(ExchangeRateParseResult.success(List.of(record)));

        FileProcessingResult result = jobService.processFile(TransactionSource.EXCHANGE_RATE, sampleFile, false);

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.dryRun()).isFalse();
    }
}
