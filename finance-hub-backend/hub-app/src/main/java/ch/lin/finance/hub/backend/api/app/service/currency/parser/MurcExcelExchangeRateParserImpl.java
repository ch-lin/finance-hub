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
package ch.lin.finance.hub.backend.api.app.service.currency.parser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateRecord;
import ch.lin.finance.hub.backend.api.domain.model.currency.ExchangeRateType;
import ch.lin.finance.hub.backend.api.domain.model.valueobject.CurrencyCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for Mitsubishi UFJ Research & Consulting (MURC) Excel exchange rate
 * files (.xls / .xlsx).
 */
@Slf4j
@Component
public class MurcExcelExchangeRateParserImpl implements ExchangeRateParser {

    private static final String SOURCE_NAME = "MURC";
    private static final DateTimeFormatter[] DATE_FORMATTERS = new DateTimeFormatter[] {
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyy年MM月dd日"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
    };

    @Override
    public boolean canParse(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return false;
        }
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".xls") && !fileName.endsWith(".xlsx")) {
            return false;
        }

        try (InputStream is = new FileInputStream(file);
                Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return false;
            }
            Map<Integer, ColumnMapping> mappings = scanHeaderColumns(sheet);
            return !mappings.isEmpty();
        } catch (Exception e) {
            log.debug("File is not a valid exchange rate Excel file: {}", file.getName());
            return false;
        }
    }

    @Override
    public ExchangeRateParseResult parse(File file) throws Exception {
        List<ExchangeRateRecord> records = new ArrayList<>();
        List<String> failedRows = new ArrayList<>();

        try (InputStream is = new FileInputStream(file);
                Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return ExchangeRateParseResult.empty();
            }

            // Map column index to target CurrencyCode & ExchangeRateType
            Map<Integer, ColumnMapping> columnMappings = scanHeaderColumns(sheet);

            if (columnMappings.isEmpty()) {
                log.warn("No currency columns detected in MURC file: {}", file.getName());
                return ExchangeRateParseResult.empty();
            }

            int firstDataRow = findFirstDataRow(sheet);

            for (int r = firstDataRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                Cell dateCell = row.getCell(0);
                LocalDate recordDate = parseDateCell(dateCell);
                if (recordDate == null) {
                    continue;
                }

                for (Map.Entry<Integer, ColumnMapping> entry : columnMappings.entrySet()) {
                    int colIdx = entry.getKey();
                    ColumnMapping mapping = entry.getValue();

                    Cell cell = row.getCell(colIdx);
                    Double rateVal = parseNumericCell(cell);

                    if (rateVal != null && rateVal > 0) {
                        ExchangeRateRecord record = new ExchangeRateRecord(
                                recordDate,
                                mapping.currencyCode,
                                CurrencyCode.JPY,
                                BigDecimal.valueOf(rateVal),
                                mapping.rateType,
                                SOURCE_NAME);
                        records.add(record);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse MURC exchange rate file: {}", file.getName(), e);
            throw e;
        }

        log.info("Successfully parsed {} exchange rate records from {}", records.size(), file.getName());
        return new ExchangeRateParseResult(records, failedRows);
    }

    private Map<Integer, ColumnMapping> scanHeaderColumns(Sheet sheet) {
        int scanRows = Math.min(5, sheet.getPhysicalNumberOfRows());

        // Phase 1: Locate currency anchor columns.
        // MURC places the currency name (e.g. "米ドル（USD）") in the FIRST of a
        // group of 3 consecutive columns (TTS / TTB / TTM). The other two cells in
        // the group are empty in that row, so we record only the anchor column here.
        Map<Integer, CurrencyCode> colToCurrency = new HashMap<>();
        for (int r = 0; r < scanRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;
            for (int c = 1; c < row.getLastCellNum(); c++) {
                String text = getCellStringValue(row.getCell(c));
                if (text == null || text.isBlank())
                    continue;
                CurrencyCode cc = matchCurrency(text);
                if (cc != null && cc != CurrencyCode.UNKNOWN) {
                    colToCurrency.put(c, cc);
                }
            }
        }

        if (colToCurrency.isEmpty()) {
            return Collections.emptyMap();
        }

        // Phase 2: Find the rate-type header row — the row that explicitly lists
        // TTS / TTB / TTM (or SPOT / CLOSING) per column.
        Map<Integer, ExchangeRateType> colToRateType = new HashMap<>();
        for (int r = 0; r < scanRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;
            boolean isRateTypeRow = false;
            for (int c = 1; c < row.getLastCellNum(); c++) {
                String text = getCellStringValue(row.getCell(c));
                if (text == null)
                    continue;
                String upper = text.toUpperCase();
                if (upper.equals("TTS") || upper.equals("TTB") || upper.equals("TTM")
                        || upper.equals("SPOT") || upper.equals("CLOSING")) {
                    isRateTypeRow = true;
                    break;
                }
            }
            if (!isRateTypeRow)
                continue;
            for (int c = 1; c < row.getLastCellNum(); c++) {
                String text = getCellStringValue(row.getCell(c));
                if (text == null || text.isBlank())
                    continue;
                String upper = text.toUpperCase();
                ExchangeRateType rt = switch (upper) {
                    case "TTS" -> ExchangeRateType.TTS;
                    case "TTB" -> ExchangeRateType.TTB;
                    case "TTM" -> ExchangeRateType.TTM;
                    case "SPOT" -> ExchangeRateType.SPOT;
                    case "CLOSING" -> ExchangeRateType.CLOSING;
                    default -> null;
                };
                if (rt != null) {
                    colToRateType.put(c, rt);
                }
            }
            break; // Only the first matching rate-type row is needed
        }

        // Phase 3: Merge — for each rate-type column, find its owning currency by
        // walking left to the nearest currency anchor column.
        List<Integer> anchorCols = new ArrayList<>(colToCurrency.keySet());
        Collections.sort(anchorCols);

        Map<Integer, ColumnMapping> mappings = new HashMap<>();
        for (Map.Entry<Integer, ExchangeRateType> entry : colToRateType.entrySet()) {
            int col = entry.getKey();
            ExchangeRateType rateType = entry.getValue();
            CurrencyCode currency = null;
            for (int anchor : anchorCols) {
                if (anchor <= col) {
                    currency = colToCurrency.get(anchor);
                } else {
                    break;
                }
            }
            if (currency != null && currency != CurrencyCode.UNKNOWN) {
                mappings.put(col, new ColumnMapping(currency, rateType));
            }
        }

        return mappings;
    }

    private int findFirstDataRow(Sheet sheet) {
        for (int r = 0; r < Math.min(10, sheet.getPhysicalNumberOfRows()); r++) {
            Row row = sheet.getRow(r);
            if (row == null)
                continue;
            Cell cell = row.getCell(0);
            if (parseDateCell(cell) != null) {
                return r;
            }
        }
        return 2; // Default fallback data row index
    }

    private LocalDate parseDateCell(Cell cell) {
        if (cell == null)
            return null;

        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            double num = cell.getNumericCellValue();
            if (num > 30000 && num < 60000) {
                return DateUtil.getLocalDateTime(num).toLocalDate();
            }
        }

        String strVal = getCellStringValue(cell);
        if (strVal == null || strVal.isBlank())
            return null;

        String trimmed = strVal.trim();
        for (DateTimeFormatter dtf : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(trimmed, dtf);
            } catch (DateTimeParseException ignored) {
            }
        }

        return null;
    }

    private Double parseNumericCell(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == CellType.STRING) {
            String str = cell.getStringCellValue().replaceAll("[^0-9\\.]", "").trim();
            if (!str.isEmpty()) {
                try {
                    return Double.valueOf(str);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString();
            }
            return String.valueOf(cell.getNumericCellValue());
        }
        return null;
    }

    private CurrencyCode matchCurrency(String text) {
        String upper = text.toUpperCase();
        if (upper.contains("USD") || upper.contains("米ドル") || upper.contains("米") || upper.contains("US DOLLAR")) {
            return CurrencyCode.USD;
        }
        if (upper.contains("EUR") || upper.contains("ユーロ")) {
            return CurrencyCode.EUR;
        }
        if (upper.contains("TWD") || upper.contains("台湾") || upper.contains("NTD")) {
            return CurrencyCode.TWD;
        }
        if (upper.contains("JPY") || upper.contains("円")) {
            return CurrencyCode.JPY;
        }
        return CurrencyCode.UNKNOWN;
    }

    private record ColumnMapping(CurrencyCode currencyCode, ExchangeRateType rateType) {
    }
}
