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
package ch.lin.finance.hub.backend.api.app.service.accounting.company_stock.etrade;

import java.io.File;
import java.net.URL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;

public class ETradeTablePrinterTest {
    @Test
    public void printTables() throws Exception {
        URL url = getClass().getClassLoader().getResource("mock-data/etrade/benefitHistory.html");
        File file = new File(url.getFile());
        Document doc = Jsoup.parse(file, "UTF-8");
        Elements tables = doc.select("table");
        int idx = 0;
        for (Element table : tables) {
            Elements rows = table.select("> tbody > tr, > thead > tr, > tr");
            if (rows.isEmpty())
                continue;
            String headerText = rows.get(0).text();
            if (headerText.length() > 500)
                continue;

            if (headerText.contains("Grant Date") || headerText.contains("付与日")) {
                System.out.println("Table " + idx + " Header: " + headerText);
                for (Element row : rows) {
                    Elements tds = row.select("> td");
                    if (tds.isEmpty())
                        continue;
                    System.out.println("  Row tds count: " + tds.size() + ", text: " + row.text());
                }
            }
            idx++;
        }
    }
}
