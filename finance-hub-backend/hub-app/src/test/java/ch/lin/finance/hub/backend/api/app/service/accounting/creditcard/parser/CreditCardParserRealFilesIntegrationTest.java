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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardParseResult;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.CreditCardParser;
import ch.lin.finance.hub.backend.api.app.service.accounting.creditcard.FilePreview;

class CreditCardParserRealFilesIntegrationTest {

    private List<CreditCardParser> parsers;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parsers = new ArrayList<>();
        parsers.add(new AeonCsvParser());
        parsers.add(new GoldPointCardCsvParser());
        parsers.add(new JaccsCsvParser());
        parsers.add(new JcbCsvParser());
        parsers.add(new RakutenCardCsvParser());
        parsers.add(new SaisonCsvParser());
        parsers.add(new SmbcCsvParser());
        parsers.add(new ViewCardCsvParser());
        parsers.add(new PayPayCardCsvParser());
        parsers.add(new EposCsvParser());
        parsers.add(new MufgCsvParser());
        parsers.add(new Tokyo2020CardCsvParser());
        parsers.add(new YahooJapanCsvParser());
    }

    @Test
    @DisplayName("Test real files to ensure no Duplicate Entry or UNKNOWN billing months are generated")
    void testRealFilesParsing() throws Exception {
        Path samplesDir = Paths.get("src/test/resources/real-samples");
        if (!Files.exists(samplesDir)) {
            return; // Skip if no samples
        }

        List<Path> cardDirectories = Files.list(samplesDir).filter(Files::isDirectory).collect(Collectors.toList());
        Map<String, Path> uniqueKeys = new HashMap<>();

        for (Path cardDir : cardDirectories) {
            List<Path> csvFiles = Files.list(cardDir)
                    .filter(p -> p.toString().endsWith(".csv"))
                    .collect(Collectors.toList());

            for (Path csvFile : csvFiles) {
                File file = csvFile.toFile();
                FilePreview preview = new FilePreview(file);

                CreditCardParser matchedParser = parsers.stream()
                        .filter(p -> p.canParse(preview))
                        .findFirst()
                        .orElse(null);

                assertNotNull(matchedParser, "No parser found for file: " + file.getAbsolutePath());

                CreditCardParseResult result = matchedParser.parse(file);
                assertNotNull(result);

                String cardName = result.getStatement().getCardName();
                String billingMonth = result.getStatement().getBillingMonth();

                assertNotEquals("UNKNOWN", billingMonth,
                        "Billing month should not be UNKNOWN for file: " + file.getName());
                assertNotNull(cardName, "Card name should not be null for file: " + file.getName());

                String uniqueKey = cardName + "-" + billingMonth;
                if (uniqueKeys.containsKey(uniqueKey)) {
                    fail("Duplicate entry detected for cardDir " + cardDir.getFileName() + "! Both files produced key: "
                            + uniqueKey + " -> "
                            + file.getName() + " and " + uniqueKeys.get(uniqueKey).getFileName());
                }
                uniqueKeys.put(uniqueKey, csvFile);
            }
        }
    }
}
