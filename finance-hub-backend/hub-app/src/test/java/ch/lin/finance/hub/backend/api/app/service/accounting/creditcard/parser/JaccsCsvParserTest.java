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
package ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.parser;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardStatement;
import ch.lin.finance.hub.backend.api.domain.model.accounting.creditcard.CreditCardTransaction;

class JaccsCsvParserTest {

    private JaccsCsvParser parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new JaccsCsvParser();
    }

    @Test
    @DisplayName("canParse - should return true for JACCS files")
    void canParse_True() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("ダウンロード日,2024/08/16");
        assertTrue(parser.canParse(preview));
    }

    @Test
    @DisplayName("canParse - should return false for non-JACCS files")
    void canParse_False() {
        FilePreview preview = mock(FilePreview.class);
        when(preview.getHeaderWin31J()).thenReturn("ご請求明細書");
        assertFalse(parser.canParse(preview));

        FilePreview nullPreview = mock(FilePreview.class);
        when(nullPreview.getHeaderWin31J()).thenReturn(null);
        assertFalse(parser.canParse(nullPreview));
    }

    @Test
    @DisplayName("parseLines - should parse successful scenario")
    void parseLines_Success(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("WebDetail202408161016.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("ダウンロード日,2024/08/16\n");
            writer.write("2024年8月お支払分ご利用明細\n");
            writer.write("カード名,ソフマッププレミアムクラブ\n");
            writer.write("お支払い日,2024/08/27\n");
            writer.write("(A)+(B)お支払金額,13563\n"); // Using no commas to match splitCsv behavior
            writer.write("ご利用年月日,利用店名等,お支払方法,ご利用金額(￥),当月請求額(￥),翌月以降残高(￥),ご利用金額,手数料額,割引額,ポイント,請求月,備考\n");
            writer.write("2024/07/01,Amazon,1回払い,1000,0,0,1000,0,0,10,202408,memo text\n");
            writer.write("2024/07/05,Store,1回払い,2000,0,0,2000,0,0,20,202408\n"); // No memo
            writer.write(" , , , , , , , , , , , \n"); // Empty row in transaction section
            writer.write("InvalidDate,Store,1回払い,0,0,0,1000,0,0,0,0, \n"); // Invalid date
            writer.write("(A)お支払金額合計,3000\n"); // This should break the loop via startsWith("(")
        }

        CreditCardParseResult result = parser.parse(file);

        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertEquals("2024-08", statement.getBillingMonth());
        assertEquals(LocalDate.of(2024, 8, 27), statement.getPaymentDate());
        assertEquals(13563, statement.getTotalAmount());
        assertEquals(2, statement.getTransactions().size());

        CreditCardTransaction t1 = statement.getTransactions().get(0);
        assertEquals(LocalDate.of(2024, 7, 1), t1.getTransactionDate());
        assertEquals("Amazon", t1.getStoreName());
        assertEquals(1000, t1.getAmount()); // Check index 6
        assertEquals("memo text", t1.getMemo());
        assertEquals("JACCS ソフマッププレミアムクラブ", t1.getCardName());

        CreditCardTransaction t2 = statement.getTransactions().get(1);
        assertEquals(LocalDate.of(2024, 7, 5), t2.getTransactionDate());
        assertEquals("Store", t2.getStoreName());
        assertEquals(2000, t2.getAmount());
        assertNull(t2.getMemo()); // cols length > 11 ? cols[11] (it will actually be empty string if empty
                                  // column, let's see)
    }

    @Test
    @DisplayName("parseLines - should handle invalid metadata and month < 10")
    void parseLines_InvalidMetadata(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("WebDetail202409.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("ダウンロード日,2024/08/16\n");
            writer.write("2024年9月お支払分ご利用明細\n"); // 9月 to trigger length < 2 path
            writer.write("カード名,ソフマッププレミアムクラブ\n");
            writer.write("お支払い日,BadDate\n"); // trigger exception
            writer.write("お支払金額,BadAmount\n"); // trigger exception
            writer.write("ご利用年月日,利用店名等,お支払方法,ご利用金額(￥),当月請求額(￥),翌月以降残高(￥),ご利用金額,手数料額,割引額,ポイント,請求月,備考\n");
            writer.write("2024/07/01,Amazon,1回払い,1000,0,0,BadAmount,0,0,10,202408,memo text\n"); // Bad transaction
        }

        CreditCardParseResult result = parser.parse(file);

        assertNotNull(result);
        assertEquals(1, result.getFailedRows().size());
        assertTrue(result.getFailedRows().get(0).contains("BadAmount"));

        CreditCardStatement statement = result.getStatement();
        assertEquals("UNKNOWN", statement.getBillingMonth()); // Overwritten to UNKNOWN because transactions list is
                                                              // empty
        assertNull(statement.getPaymentDate());
        assertEquals(0, statement.getTotalAmount()); // Will sum to 0 because 0 valid tx
        assertEquals(0, statement.getTransactions().size());
    }

    @Test
    @DisplayName("parseLines - should handle header edge cases and short transaction rows")
    void parseLines_HeaderEdgeCasesAndShortRows(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("WebDetail202410.csv").toFile();
        try (FileWriter writer = new FileWriter(file, java.nio.charset.Charset.forName("Windows-31J"))) {
            writer.write("ダウンロード日,2024/10/16\n");
            writer.write("2024年10月お支払分ご利用明細\n"); // 10月 to trigger length >= 2 path
            writer.write("カード名\n"); // trigger cols.length < 2
            writer.write("お支払い日\n"); // trigger cols.length < 2
            writer.write("お支払金額\n"); // trigger cols.length < 2
            writer.write("ご利用年月日,利用店名等,お支払方法,ご利用金額(￥),当月請求額(￥),翌月以降残高(￥),ご利用金額,手数料額,割引額,ポイント,請求月,備考\n");
            writer.write("2024/07/02,Store,1回払い,1000\n"); // Length < 7
            writer.write("2024/07/03,ValidStore,1回払い,1000,0,0,1000,0,0,10,202410,memo\n"); // Valid
            writer.write("\n"); // Break with empty line.
        }

        CreditCardParseResult result = parser.parse(file);

        assertNotNull(result);
        assertEquals(0, result.getFailedRows().size());

        CreditCardStatement statement = result.getStatement();
        assertEquals("2024-10", statement.getBillingMonth()); // Overwritten from header successfully
        assertNull(statement.getPaymentDate()); // Not parsed because "お支払い日" line was too short
        assertEquals(1000, statement.getTotalAmount()); // Automatically summed from transaction
        assertEquals("JACCS", statement.getCardName()); // Defaulted because "カード名" was too short
        assertEquals(1, statement.getTransactions().size());
    }
}
