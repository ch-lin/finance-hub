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
package ch.lin.finance.hub.backend.api.app.service.accounting.bank.parser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ch.lin.finance.hub.backend.api.app.service.accounting.bank.BankFilePreview;
import ch.lin.finance.hub.backend.api.app.service.accounting.bank.BankParseResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.bank.BankTransaction;

class SmbcBankHtmlParserTest {

    private SmbcBankHtmlParser parser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parser = new SmbcBankHtmlParser();
    }

    @Test
    void testCanParse() throws IOException {
        String htmlWithTitle = "<html><head><title>SMBCダイレクト：明細照会</title></head><body></body></html>";
        File file1 = createTempHtml("smbc_title.html", htmlWithTitle);
        assertTrue(parser.canParse(new BankFilePreview(file1)));

        String htmlWithClass = "<html><body><div class=\"js-meisaiList\"></div></body></html>";
        File file2 = createTempHtml("smbc_class.html", htmlWithClass);
        assertTrue(parser.canParse(new BankFilePreview(file2)));

        String htmlOther = "<html><head><title>Other Bank</title></head><body></body></html>";
        File file3 = createTempHtml("other.html", htmlOther);
        assertFalse(parser.canParse(new BankFilePreview(file3)));
    }

    @Test
    void testParse_ValidTransactionWithMemo() throws Exception {
        String html = """
                <html>
                <head><title>SMBCダイレクト：明細照会</title></head>
                <body>
                <div class="js-meisaiList">
                    <div class="js-meisaiMonth">
                        <span class="js-dispDateMonth">2026年6月</span>
                        <dd class="detail js-meisai">
                            <span class="js-comment">アマゾン　ジャパン</span>
                            <span class="js-dispDate">6月25日</span>
                            <span class="numPrice -minus js-amount">3,500</span>
                            <span class="js-torihikigobalance">1,250,000</span>
                            <span class="js-meisaiMemoDisp">書籍購入</span>
                        </dd>
                    </div>
                </div>
                </body>
                </html>
                """;

        File file = createTempHtml("smbc_valid.html", html);
        BankParseResult result = parser.parse(new BankFilePreview(file));

        assertEquals(1, result.getTransactions().size());
        BankTransaction tx = result.getTransactions().get(0);
        assertEquals("SMBC", tx.getBankName());
        assertEquals(LocalDate.of(2026, 6, 25), tx.getTransactionDate());
        assertEquals("アマゾン　ジャパン", tx.getDescription());
        assertEquals(3500, tx.getWithdrawalAmount());
        assertEquals(1250000, tx.getBalance());
        assertEquals("書籍購入", tx.getMemo());
    }

    @Test
    void testParse_MissingDateDoesNotFallbackToToday_DropsInvalidRow() throws Exception {
        // One valid row, and one invalid row with missing transaction date element (.js-dispDate)
        String html = """
                <html>
                <head><title>SMBCダイレクト：明細照会</title></head>
                <body>
                <div class="js-meisaiList">
                    <div class="js-meisaiMonth">
                        <span class="js-dispDateMonth">2026年6月</span>
                        <!-- Valid transaction -->
                        <dd class="detail js-meisai">
                            <span class="js-comment">給与振込</span>
                            <span class="js-dispDate">6月20日</span>
                            <span class="numPrice -plus js-amount">500,000</span>
                            <span class="js-torihikigobalance">1,500,000</span>
                        </dd>
                        <!-- Broken transaction without date -->
                        <dd class="detail js-meisai">
                            <span class="js-comment">破損明細行</span>
                            <span class="numPrice -minus js-amount">1,000</span>
                            <span class="js-torihikigobalance">1,499,000</span>
                        </dd>
                    </div>
                </div>
                </body>
                </html>
                """;

        File file = createTempHtml("smbc_missing_date.html", html);
        BankParseResult result = parser.parse(new BankFilePreview(file));

        // Only the valid row should be parsed; broken row without date must be dropped, NOT assigned LocalDate.now()
        assertEquals(1, result.getTransactions().size());
        BankTransaction tx = result.getTransactions().get(0);
        assertEquals("給与振込", tx.getDescription());
        assertEquals(LocalDate.of(2026, 6, 20), tx.getTransactionDate());
    }

    private File createTempHtml(String filename, String content) throws IOException {
        Path path = tempDir.resolve(filename);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path.toFile();
    }
}
