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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.shopping.AmazonOrderRepository;
import ch.lin.finance.hub.backend.api.app.service.job.file.FileProcessingResult;
import ch.lin.finance.hub.backend.api.app.service.job.result.JobResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import ch.lin.finance.hub.backend.api.domain.model.config.scheduler.TransactionSource;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({ "unused", "null" })
class AmazonJobServiceImplTest {

    @Mock
    private AmazonOrderRepository amazonOrderRepository;

    @Mock
    private AmazonOrderParser amazonOrderParser;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AmazonJobServiceImpl jobService;

    @TempDir
    File tempDir;

    private String basePath;

    @BeforeEach
    void setUp() {
        basePath = tempDir.getAbsolutePath();
        jobService = new AmazonJobServiceImpl(amazonOrderRepository, amazonOrderParser, transactionTemplate, basePath);

        // Mock transaction template to just execute the callback
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }

    private File createDummyFile(File dir, String name) throws IOException {
        File file = new File(dir, name);
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), "dummy content");
        return file;
    }

    @Test
    void testGetSource() {
        assertEquals(TransactionSource.AMAZON, jobService.getSource());
    }

    @Test
    void testExecute_InvalidPath() throws Exception {
        // Path doesn't exist initially, should return empty JobResult
        JobResult result = jobService.execute(false);
        assertEquals(0, result.totalFiles());
        assertEquals(0, result.successCount());
    }

    @Test
    void testExecute_NoHtmlFiles() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        createDummyFile(inputDir, "not-html.txt");

        JobResult result = jobService.execute(false);
        assertEquals(0, result.totalFiles());
        assertEquals(0, result.successCount());
    }

    @Test
    void testExecute_SuccessNewOrder() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        // Also create a dummy resource folder to test resource moving
        File resourceFolder = new File(inputDir, "order_files");
        resourceFolder.mkdirs();
        createDummyFile(resourceFolder, "image.png");

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(order)).thenReturn(order);

        JobResult result = jobService.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());

        File successFile = new File(baseAmazonDir,
                String.format("archive/success/%s/order.html", java.time.LocalDate.now().getYear()));
        assertTrue(successFile.exists());

        File successResourceFolder = new File(baseAmazonDir,
                String.format("archive/success/%s/order_files", java.time.LocalDate.now().getYear()));
        assertTrue(successResourceFolder.exists());
    }

    @Test
    void testExecute_SuccessExistingOrder() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        AmazonOrder newOrder = new AmazonOrder("123", null);
        AmazonOrder existingOrder = new AmazonOrder("123", null);

        when(amazonOrderParser.parse(htmlFile)).thenReturn(newOrder);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.of(existingOrder));
        when(amazonOrderRepository.save(existingOrder)).thenReturn(existingOrder);

        JobResult result = jobService.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());
        verify(amazonOrderRepository).save(existingOrder);
    }

    @Test
    void testExecute_NeedsReview() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        AmazonOrder order = new AmazonOrder("123", null);
        order.setNeedsReview(true);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(order)).thenReturn(order);

        JobResult result = jobService.execute(false);

        assertEquals(1, result.totalFiles());
        assertEquals(0, result.successCount());
        assertEquals(1, result.needsReviewCount());

        File reviewFile = new File(baseAmazonDir,
                String.format("archive/needs_review/%s/order.html", java.time.LocalDate.now().getYear()));
        assertTrue(reviewFile.exists());
    }

    @Test
    void testExecute_DryRun() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);

        JobResult result = jobService.execute(true);

        assertEquals(1, result.totalFiles());
        assertEquals(1, result.successCount());

        verify(amazonOrderRepository, never()).save(any());
        assertTrue(htmlFile.exists()); // not moved
    }

    @Test
    void testExecute_ParseError() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        when(amazonOrderParser.parse(htmlFile)).thenThrow(new RuntimeException("Parse error"));

        JobResult result = jobService.execute(false);

        assertEquals(1, result.totalFiles());
        // skippedCount in JobResult mapped to failedFiles.size()
        assertEquals(1, result.failedCount());

        File failedFile = new File(baseAmazonDir,
                String.format("archive/failed/%s/order.html", java.time.LocalDate.now().getYear()));
        assertTrue(failedFile.exists());
    }

    @Test
    void testProcessFile_Success() throws Exception {
        File htmlFile = createDummyFile(tempDir, "temp_order.html");

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(order)).thenReturn(order);

        FileProcessingResult result = jobService.processFile(htmlFile, false, "admin");

        assertEquals("success", result.status());
        assertEquals(order, result.data());
    }

    @Test
    void testProcessFile_ExistingOrder() throws Exception {
        File htmlFile = createDummyFile(tempDir, "temp_order.html");

        AmazonOrder newOrder = new AmazonOrder("123", null);
        AmazonOrder existingOrder = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(newOrder);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.of(existingOrder));
        when(amazonOrderRepository.save(existingOrder)).thenReturn(existingOrder);

        FileProcessingResult result = jobService.processFile(htmlFile, false, "admin");

        assertEquals("success", result.status());
        assertEquals(existingOrder, result.data());
    }

    @Test
    void testProcessFile_DryRun() throws Exception {
        File htmlFile = createDummyFile(tempDir, "temp_order.html");

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);

        FileProcessingResult result = jobService.processFile(htmlFile, true, "admin");

        assertEquals("success", result.status());
        verify(amazonOrderRepository, never()).save(any());
    }

    @Test
    void testExecute_InputPathIsNotDirectory() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.getParentFile().mkdirs();
        inputDir.createNewFile();

        JobResult result = jobService.execute(false);
        assertEquals(0, result.totalFiles());
        assertEquals(0, result.successCount());
    }

    @Test
    void testExecute_ListFilesReturnsNull() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        inputDir.setReadable(false);
        try {
            java.nio.file.AccessDeniedException exception = assertThrows(java.nio.file.AccessDeniedException.class,
                    () -> jobService.execute(false));
            assertNotNull(exception);
        } finally {
            inputDir.setReadable(true);
        }
    }

    @Test
    void testExecute_FinalOrderNull() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(order)).thenReturn(null);

        JobResult result = jobService.execute(false);
        assertEquals(1, result.successCount());
    }

    @Test
    void testExecute_NeedsReview_DryRun() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        AmazonOrder order = new AmazonOrder("123", null);
        order.setNeedsReview(true);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);

        JobResult result = jobService.execute(true);

        assertEquals(1, result.needsReviewCount());
        verify(amazonOrderRepository, never()).save(any());
        assertTrue(htmlFile.exists());
    }

    @Test
    void testExecute_TargetDirExists() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        File targetDir = new File(baseAmazonDir, "archive/success");
        targetDir.mkdirs();

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(order)).thenReturn(order);

        JobResult result = jobService.execute(false);
        assertEquals(1, result.successCount());
    }

    @Test
    void testExecute_MoveThrowsIOException() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        File targetDir = new File(baseAmazonDir, "archive/success");
        targetDir.mkdirs();
        File blockingDir = new File(targetDir, "order.html");
        blockingDir.mkdirs();
        // Create a file inside blockingDir so it's not empty, ensuring
        // DirectoryNotEmptyException
        new File(blockingDir, "blocking.txt").createNewFile();

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(order)).thenReturn(order);

        JobResult result = jobService.execute(false);
        assertEquals(1, result.successCount());
    }

    @Test
    void testExecute_ParseError_DryRun() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        when(amazonOrderParser.parse(htmlFile)).thenThrow(new RuntimeException("Parse error"));

        JobResult result = jobService.execute(true);
        assertEquals(1, result.failedCount());
        verify(amazonOrderRepository, never()).save(any());
        assertTrue(htmlFile.exists());
    }

    @Test
    void testExecute_WithResourceFolder() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        // Create a resource folder for the HTML file
        File resourceFolder = new File(inputDir, "order_files");
        resourceFolder.mkdirs();
        new File(resourceFolder, "style.css").createNewFile();

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(order)).thenReturn(order);

        JobResult result = jobService.execute(false);
        assertEquals(1, result.successCount());

        File archivedResourceFolder = new File(baseAmazonDir,
                String.format("archive/success/%s/order_files", java.time.LocalDate.now().getYear()));
        assertTrue(archivedResourceFolder.exists());
        assertTrue(archivedResourceFolder.isDirectory());
    }

    @Test
    void testExecute_WithResourceFileInsteadOfFolder() throws Exception {
        File baseAmazonDir = new File(basePath, "amazon");
        File inputDir = new File(baseAmazonDir, "source");
        inputDir.mkdirs();
        File htmlFile = createDummyFile(inputDir, "order.html");

        // Create a resource file instead of a folder
        File resourceFile = new File(inputDir, "order_files");
        resourceFile.createNewFile();

        AmazonOrder order = new AmazonOrder("123", null);
        when(amazonOrderParser.parse(htmlFile)).thenReturn(order);
        when(amazonOrderRepository.findByOrderId("123")).thenReturn(Optional.empty());
        when(amazonOrderRepository.save(order)).thenReturn(order);

        JobResult result = jobService.execute(false);
        assertEquals(1, result.successCount());

        // The file should NOT be moved into the archive because it's not a directory
        File archivedResourceFolder = new File(baseAmazonDir,
                String.format("archive/success/%s/order_files", java.time.LocalDate.now().getYear()));
        assertFalse(archivedResourceFolder.exists());
    }
}
