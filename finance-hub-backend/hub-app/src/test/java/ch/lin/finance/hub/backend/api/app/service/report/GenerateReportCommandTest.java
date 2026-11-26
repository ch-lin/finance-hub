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
package ch.lin.finance.hub.backend.api.app.service.report;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class GenerateReportCommandTest {

    @Test
    void testGetReportPeriodString_AllTime() {
        GenerateReportCommand command = GenerateReportCommand.builder().build();
        assertEquals("all-time", command.getReportPeriodString());
    }

    @Test
    void testGetReportPeriodString_SameDate() {
        GenerateReportCommand command = GenerateReportCommand.builder()
                .start("2023-01-01")
                .end("2023-01-01")
                .build();
        assertEquals("2023-01-01", command.getReportPeriodString());
    }

    @Test
    void testGetReportPeriodString_StartOnly() {
        GenerateReportCommand command = GenerateReportCommand.builder()
                .start("2023-01-01")
                .build();
        assertEquals("2023-01-01 to now", command.getReportPeriodString());
    }

    @Test
    void testGetReportPeriodString_EndOnly() {
        LocalDate end = LocalDate.now().minusDays(1);
        GenerateReportCommand command = GenerateReportCommand.builder()
                .end(end.toString())
                .build();
        assertEquals("all-time to " + end.toString(), command.getReportPeriodString());
    }
    
    @Test
    void testGetReportPeriodString_StartAndEnd() {
        GenerateReportCommand command = GenerateReportCommand.builder()
                .start("2023-01-01")
                .end("2023-12-31")
                .build();
        assertEquals("2023-01-01 to 2023-12-31", command.getReportPeriodString());
    }

    @Test
    void testResolveDateRange_Normal() {
        LocalDate end = LocalDate.now().minusDays(1);
        GenerateReportCommand command = GenerateReportCommand.builder()
                .start("2023-01-01")
                .end(end.toString())
                .build();

        ReportDateRange range = command.resolveDateRange();
        assertEquals(LocalDate.of(2023, 1, 1), range.startDate());
        assertEquals(end, range.endDate());
    }

    @Test
    void testResolveDateRange_ReversedDates() {
        GenerateReportCommand command = GenerateReportCommand.builder()
                .start("2023-12-31")
                .end("2023-01-01")
                .build();

        ReportDateRange range = command.resolveDateRange();
        assertEquals(LocalDate.of(2023, 1, 1), range.startDate());
        assertEquals(LocalDate.of(2023, 12, 31), range.endDate());
    }

    @Test
    void testResolveDateRange_YearMonthFormat() {
        GenerateReportCommand command = GenerateReportCommand.builder()
                .start("2024-02")
                .end("2024-02")
                .build();

        ReportDateRange range = command.resolveDateRange();
        assertEquals(LocalDate.of(2024, 2, 1), range.startDate());
        assertEquals(LocalDate.of(2024, 2, 29), range.endDate());
    }

    @Test
    void testResolveDateRange_ReversedYearMonthFormat() {
        GenerateReportCommand command = GenerateReportCommand.builder()
                .start("2024-12")
                .end("2024-01")
                .build();

        ReportDateRange range = command.resolveDateRange();
        assertEquals(LocalDate.of(2024, 1, 1), range.startDate());
        assertEquals(LocalDate.of(2024, 12, 31), range.endDate());
    }

    @Test
    void testResolveDateRange_Defaults() {
        GenerateReportCommand command = GenerateReportCommand.builder().build();

        ReportDateRange range = command.resolveDateRange();
        assertEquals(LocalDate.of(1900, 1, 1), range.startDate());
        assertEquals(LocalDate.now(), range.endDate());
    }

    @Test
    void testResolveDateRange_FutureDates() {
        LocalDate futureEnd = LocalDate.now().plusDays(10);
        LocalDate futureStart = LocalDate.now().plusDays(5);
        
        GenerateReportCommand command = GenerateReportCommand.builder()
                .start(futureStart.toString())
                .end(futureEnd.toString())
                .build();

        ReportDateRange range = command.resolveDateRange();
        // Since start and end are in the future, they both get capped to today.
        assertEquals(LocalDate.now(), range.startDate());
        assertEquals(LocalDate.now(), range.endDate());
    }
}
