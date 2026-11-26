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
package ch.lin.finance.hub.backend.api.app.service.accounting.creditcard;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.finance.hub.backend.api.app.repository.accounting.creditcard.CreditCardStatementRepository;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.common.exception.CsvRowsFailedException;
import ch.lin.finance.hub.backend.api.common.exception.UnsupportedCsvFormatException;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "unused", "null" })
class CreditCardServiceImplTest {

    @Mock
    private CreditCardParserFactory parserFactory;

    @Mock
    private CreditCardStatementRepository statementRepository;

    @TempDir
    File tempDir;

    private CreditCardServiceImpl service;
    private String basePath;

    @BeforeEach
    void setUp() {
        basePath = tempDir.getAbsolutePath();
        service = new CreditCardServiceImpl(parserFactory, statementRepository, basePath);
    }

    @Test
    void testGetSource() {
        assertEquals(TransactionSource.CREDIT_CARD, service.getSource());
    }

    @Test
    void testExecute_SourceDirDoesNotExist() throws Exception {
        JobResult result = service.execute(false);
        assertEquals(0, result.totalFiles());
        assertTrue(new File(basePath + "/creditcard/source").exists());
    }

    @Test
    void testExecute_NoCsvFiles() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        new File(sourceDir, "not_a_csv.txt").createNewFile();

        JobResult result = service.execute(false);
        assertEquals(0, result.totalFiles());
    }

    @Test
    void testExecute_Success() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        JobResult result = service.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());
        assertEquals(0, result.failedCount());
        verify(statementRepository, times(1)).save(statement);

        assertFalse(csvFile.exists()); // moved
        File successDir = new File(basePath + "/creditcard/archive/success/TestCard");
        assertTrue(successDir.exists());
        assertTrue(successDir.list().length > 0);
        assertTrue(successDir.list()[0].startsWith("data_"));
    }

    @Test
    void testExecute_Success_ExistingStatement_MergesAndSaves() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement incomingStatement = new CreditCardStatement("TestCard", "2024-01", null, 5000);
        CreditCardParseResult parseResult = new CreditCardParseResult(incomingStatement, Collections.emptyList(),
                StandardCharsets.UTF_8);

        CreditCardStatement existingStatement = new CreditCardStatement("TestCard", "2024-01", null, 3000);
        when(statementRepository.findByCardNameAndBillingMonth("TestCard", "2024-01"))
                .thenReturn(Optional.of(existingStatement));

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        JobResult result = service.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());
        assertEquals(0, result.failedCount());
        assertEquals(5000, existingStatement.getTotalAmount(), "Facts on existing statement should be updated");
        verify(statementRepository, times(1)).save(existingStatement);
        verify(statementRepository, never()).save(same(incomingStatement));
    }

    @Test
    void testExecute_DryRun() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        JobResult result = service.execute(true);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());
        verify(statementRepository, never()).save(any(CreditCardStatement.class));
        assertTrue(csvFile.exists()); // not moved
    }

    @Test
    void testExecute_WithFailedRows() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile, "Header\nBadRow");

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        List<String> failedRows = Arrays.asList("BadRow");
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, failedRows, null);

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        JobResult result = service.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(0, result.successCount());
        assertEquals(1, result.failedCount());
        verify(statementRepository, never()).save(any(CreditCardStatement.class));

        assertFalse(csvFile.exists());
        File failedDir = new File(basePath + "/creditcard/archive/failed/TestCard");
        assertTrue(failedDir.exists());
        String[] files = failedDir.list();
        assertNotNull(files);
        assertEquals(2, files.length); // original file + error log
    }

    @Test
    void testExecute_UnsupportedFormat() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        when(parserFactory.getParser(any(File.class))).thenThrow(new UnsupportedCsvFormatException("unsupported"));

        JobResult result = service.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.failedCount());

        File unsupportedDir = new File(basePath + "/creditcard/archive/unsupported");
        assertTrue(unsupportedDir.exists());
        assertTrue(unsupportedDir.list().length > 0);
    }

    @Test
    void testExecute_GeneralException() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        when(parserFactory.getParser(any(File.class))).thenThrow(new RuntimeException("unknown error"));

        JobResult result = service.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.failedCount());

        File failedDir = new File(basePath + "/creditcard/archive/failed/unknown_card");
        assertTrue(failedDir.exists());
        assertTrue(failedDir.list().length > 0);
    }

    @Test
    void testExecute_SourceDirNotDirectory() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.getParentFile().mkdirs();
        sourceDir.createNewFile();

        JobResult result = service.execute(false);
        assertEquals(0, result.totalFiles());
    }

    @Test
    void testExecute_StatementNullCardName() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement(null, "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);
        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        service.execute(false);
        File successDir = new File(basePath + "/creditcard/archive/success/unknown_card");
        assertTrue(successDir.exists());
    }

    @Test
    void testExecute_StatementNull() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardParseResult parseResult = new CreditCardParseResult(null, Collections.emptyList(),
                StandardCharsets.UTF_8);
        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        service.execute(false);
        File successDir = new File(basePath + "/creditcard/archive/success/unknown_card");
        assertTrue(successDir.exists());
    }

    @Test
    void testExecute_DryRunWithFailedRows() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement,
                Collections.singletonList("failed row"), StandardCharsets.UTF_8);
        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        JobResult result = service.execute(true);
        assertEquals(1, result.totalFiles());
        assertEquals(0, result.successCount());
        assertEquals(0, result.failedCount());
    }

    @Test
    void testExecute_UnsupportedFormat_MoveThrowsIOException() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        when(parserFactory.getParser(any(File.class))).thenThrow(new UnsupportedCsvFormatException("unsupported"));

        File unsupportedDir = new File(basePath + "/creditcard/archive/unsupported");
        unsupportedDir.getParentFile().mkdirs();
        unsupportedDir.createNewFile();

        JobResult result = service.execute(false);
        assertEquals(1, result.failedCount());
    }

    @Test
    void testExecute_GeneralException_MoveThrowsIOException() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        when(parserFactory.getParser(any(File.class))).thenThrow(new RuntimeException("unknown error"));

        File failedDir = new File(basePath + "/creditcard/archive/failed/unknown_card");
        failedDir.getParentFile().mkdirs();
        failedDir.createNewFile();

        JobResult result = service.execute(false);
        assertEquals(1, result.failedCount());
    }

    @Test
    void testExecute_UnsupportedFormat_DryRun() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        when(parserFactory.getParser(any(File.class))).thenThrow(new UnsupportedCsvFormatException("unsupported"));

        JobResult result = service.execute(true);
        assertEquals(1, result.failedCount());
    }

    @Test
    void testExecute_GeneralException_DryRun() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        when(parserFactory.getParser(any(File.class))).thenThrow(new RuntimeException("error"));

        JobResult result = service.execute(true);
        assertEquals(1, result.failedCount());
    }

    @Test
    void testExecute_WriteErrorLogBranches() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();

        File csvFile = new File(sourceDir, ".csv");
        csvFile.createNewFile(); // Completely empty

        File targetDir = new File(basePath + "/creditcard/archive/failed/TestCard");
        targetDir.mkdirs();

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement,
                Collections.singletonList("failed row"), null);

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        service.execute(false);
        assertTrue(targetDir.exists());
    }

    @Test
    void testExecute_WriteErrorLog_CharsetNotNull() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        // Provide a non-null charset to hit the false branch of if (charset == null)
        CreditCardParseResult parseResult = new CreditCardParseResult(statement,
                Collections.singletonList("failed row"), StandardCharsets.ISO_8859_1);
        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        service.execute(false);
        File failedDir = new File(basePath + "/creditcard/archive/failed/TestCard");
        assertTrue(failedDir.exists());
    }

    @Test
    void testExecute_CleanupEmptyDirectories_ThrowsIOException() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);
        when(parserFactory.getParser(any(File.class))).thenReturn(parser);

        when(parser.parse(any(File.class))).thenAnswer(invocation -> {
            csvFile.delete();
            sourceDir.delete();
            return parseResult;
        });

        service.execute(false);
        assertFalse(sourceDir.exists());
    }

    @Test
    void testExecute_FileWithoutExtension() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);
        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        service.execute(false);
        File successDir = new File(basePath + "/creditcard/archive/success/TestCard");
        assertTrue(successDir.exists());
    }

    @Test
    void testProcessFile_Success() throws Exception {
        File csvFile = new File(tempDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        FileProcessingResult result = service.processFile(csvFile, false, "admin");

        assertEquals("success", result.status());
        verify(statementRepository, times(1)).save(statement);
    }

    @Test
    void testProcessFile_Success_ExistingStatement_MergesAndSaves() throws Exception {
        File csvFile = new File(tempDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement incomingStatement = new CreditCardStatement("TestCard", "2024-01", null, 8000);
        CreditCardParseResult parseResult = new CreditCardParseResult(incomingStatement, Collections.emptyList(),
                StandardCharsets.UTF_8);

        CreditCardStatement existingStatement = new CreditCardStatement("TestCard", "2024-01", null, 4000);
        when(statementRepository.findByCardNameAndBillingMonth("TestCard", "2024-01"))
                .thenReturn(Optional.of(existingStatement));

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        FileProcessingResult result = service.processFile(csvFile, false, "admin");

        assertEquals("success", result.status());
        assertEquals(8000, existingStatement.getTotalAmount(), "Facts on existing statement should be updated");
        verify(statementRepository, times(1)).save(existingStatement);
        verify(statementRepository, never()).save(same(incomingStatement));
    }

    @Test
    void testProcessFile_DryRun() throws Exception {
        File csvFile = new File(tempDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        FileProcessingResult result = service.processFile(csvFile, true, "admin");

        assertEquals("success", result.status());
        verify(statementRepository, never()).save(any(CreditCardStatement.class));
    }

    @Test
    void testProcessFile_ThrowsException_WhenFailedRowsExist() throws Exception {
        File csvFile = new File(tempDir, "data.csv");
        createDummyFile(csvFile);

        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        List<String> failedRows = Arrays.asList("BadRow");
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, failedRows, StandardCharsets.UTF_8);

        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        CsvRowsFailedException exception = assertThrows(CsvRowsFailedException.class,
                () -> service.processFile(csvFile, false, "admin"));
        assertNotNull(exception);
    }

    @Test
    void testExecute_CleanupEmptyDirectories() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();

        // Create a dummy CSV file so execute() doesn't return early
        File csvFile = new File(sourceDir, "data.csv");
        createDummyFile(csvFile);
        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);
        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        File emptySubDir = new File(sourceDir, "empty");
        emptySubDir.mkdirs();

        File hiddenSubDir = new File(sourceDir, "hidden");
        hiddenSubDir.mkdirs();
        File hiddenFile = new File(hiddenSubDir, ".DS_Store");
        hiddenFile.createNewFile();

        File meaningfulSubDir = new File(sourceDir, "meaningful");
        meaningfulSubDir.mkdirs();
        File meaningfulFile = new File(meaningfulSubDir, "not_csv.txt");
        meaningfulFile.createNewFile();

        service.execute(false);

        assertFalse(emptySubDir.exists());
        assertFalse(hiddenSubDir.exists());
        assertFalse(hiddenFile.exists());

        assertTrue(meaningfulSubDir.exists());
        assertTrue(meaningfulFile.exists());
    }

    @Test
    void testExecute_CleanupEmptyDirectories_WithIOException() throws Exception {
        File sourceDir = new File(basePath + "/creditcard/source");
        sourceDir.mkdirs();

        // Create a dummy CSV file so execute() doesn't return early
        File csvFile = new File(sourceDir, "data2.csv");
        createDummyFile(csvFile);
        CreditCardParser parser = mock(CreditCardParser.class);
        CreditCardStatement statement = new CreditCardStatement("TestCard", "2024-01", null, 0);
        CreditCardParseResult parseResult = new CreditCardParseResult(statement, Collections.emptyList(),
                StandardCharsets.UTF_8);
        when(parserFactory.getParser(any(File.class))).thenReturn(parser);
        when(parser.parse(any(File.class))).thenReturn(parseResult);

        File unwritableDir = new File(sourceDir, "unwritable");
        unwritableDir.mkdirs();
        File hiddenFile = new File(unwritableDir, ".hidden");
        hiddenFile.createNewFile();

        // Make directory unwritable to induce IOException when deleting the hidden file
        unwritableDir.setWritable(false);

        try {
            service.execute(false);
            // Verify it was NOT deleted due to IOException
            assertTrue(unwritableDir.exists());
        } finally {
            unwritableDir.setWritable(true);
        }
    }

    private void createDummyFile(File file) throws IOException {
        createDummyFile(file, "dummy content");
    }

    private void createDummyFile(File file, String content) throws IOException {
        file.getParentFile().mkdirs();
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }
}
