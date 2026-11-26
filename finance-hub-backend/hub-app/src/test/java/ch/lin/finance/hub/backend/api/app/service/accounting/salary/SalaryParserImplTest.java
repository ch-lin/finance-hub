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
package ch.lin.finance.hub.backend.api.app.service.accounting.salary;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryRecord;

class SalaryParserImplTest {

    private SalaryParserImpl parser;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        parser = new SalaryParserImpl();
    }

    @Test
    void testParseBasicSalaryHtml() {
        String html = "<html><body>"
                + "<h1 id=\"S7FS01H01\">2024年1月給与Process　給与明細書</h1>"
                + "<table>"
                + "<tr><th>基本給</th><td class=\"text-right\">500,000</td></tr>"
                + "<tr><th>残業手当</th><td class=\"text-right\">30,000</td></tr>"
                + "<tr><th>コミッション</th><td class=\"text-right\">100,000</td></tr>"
                + "<tr><th>ﾌｨｯﾄﾈｽ補助</th><td class=\"text-right\">10,000</td></tr>"
                + "<tr><th>健康保険料</th><td class=\"text-right\">25,000</td></tr>"
                + "<tr><th>所得税</th><td class=\"text-right\">40,000</td></tr>"
                + "<tr><th>持株会控除</th><td class=\"text-right\">50,000</td></tr>"
                + "<tfoot><tr><th>総支給</th><td class=\"text-right\">640,000</td></tr>"
                + "<tr><th>差引支給額</th><td class=\"text-right\">525,000</td></tr></tfoot>"
                + "</table>"
                + "<table id=\"listTable_S6N002_F\">"
                + "<tr><td>健保給付金：インフルエンザ予防接種補助（社員分）になります。</td></tr>"
                + "</table>"
                + "</body></html>";

        ByteArrayInputStream in = new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
        SalaryRecord record = parser.parse(in, "2024年1月給与Process　給与明細書.html");

        assertNotNull(record);
        assertEquals(LocalDate.of(2024, 1, 25), record.getPaymentDate());
        assertEquals(500000L, record.getBaseSalary());
        assertEquals(30000L, record.getOvertimePay());
        assertEquals(100000L, record.getCommission());
        assertEquals(10000L, record.getFitnessAllowance());
        assertEquals(25000L, record.getHealthInsurance());
        assertEquals(40000L, record.getIncomeTax());
        assertEquals(50000L, record.getEsppContribution());
        assertEquals(640000L, record.getGrossAmount());
        assertEquals(525000L, record.getNetAmount());
        assertEquals("健保給付金：インフルエンザ予防接種補助（社員分）になります。", record.getNote());
    }
}
