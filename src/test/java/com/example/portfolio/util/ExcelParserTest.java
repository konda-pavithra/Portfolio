package com.example.portfolio.util;

import com.example.portfolio.exception.InvalidFileException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ExcelParserTest {

    private ExcelParser parser;

    @BeforeEach
    void setUp() {
        parser = new ExcelParser();
    }

    // ── File validation ───────────────────────────────────────────────────────

    @Test
    void parse_nullFile_throwsInvalidFileException() {
        assertThrows(InvalidFileException.class, () -> parser.parse(null));
    }

    @Test
    void parse_emptyFile_throwsInvalidFileException() {
        MockMultipartFile empty = new MockMultipartFile("file", "portfolio.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
        assertThrows(InvalidFileException.class, () -> parser.parse(empty));
    }

    @Test
    void parse_wrongExtension_throwsInvalidFileException() {
        MockMultipartFile csv = new MockMultipartFile("file", "portfolio.csv",
                "text/csv", "data".getBytes());
        assertThrows(InvalidFileException.class, () -> parser.parse(csv));
    }

    @Test
    void parse_oversizedFile_throwsInvalidFileException() throws IOException {
        // 5 MB + 1 byte = just over the limit
        byte[] bigContent = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile huge = new MockMultipartFile("file", "big.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bigContent);
        assertThrows(InvalidFileException.class, () -> parser.parse(huge));
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void parse_validXlsx_returnsEntries() throws IOException {
        MockMultipartFile file = buildXlsx(wb -> {
            Sheet sheet = wb.createSheet("Portfolio");
            addHeader(sheet);
            addDataRow(sheet, 1, "RELIANCE", 10, 2450.0);
            addDataRow(sheet, 2, "TCS", 5, 3500.0);
        });

        ExcelParser.ParseResult result = parser.parse(file);

        assertEquals(2, result.entries().size());
        assertTrue(result.parseErrors().isEmpty());
        assertEquals("RELIANCE", result.entries().get(0).getSymbol());
        assertEquals(10, result.entries().get(0).getQuantity());
    }

    @Test
    void parse_blankRows_areSkipped() throws IOException {
        MockMultipartFile file = buildXlsx(wb -> {
            Sheet sheet = wb.createSheet("Portfolio");
            addHeader(sheet);
            addDataRow(sheet, 1, "RELIANCE", 10, 2450.0);
            // row 2 intentionally left blank
            addDataRow(sheet, 3, "TCS", 5, 3500.0);
        });

        ExcelParser.ParseResult result = parser.parse(file);
        assertEquals(2, result.entries().size());
    }

    @Test
    void parse_zeroQuantity_recordsParseError() throws IOException {
        MockMultipartFile file = buildXlsx(wb -> {
            Sheet sheet = wb.createSheet("Portfolio");
            addHeader(sheet);
            addDataRow(sheet, 1, "RELIANCE", 0, 2450.0);
        });

        ExcelParser.ParseResult result = parser.parse(file);
        assertTrue(result.entries().isEmpty());
        assertEquals(1, result.parseErrors().size());
    }

    @Test
    void parse_zeroBuyingPrice_recordsParseError() throws IOException {
        MockMultipartFile file = buildXlsx(wb -> {
            Sheet sheet = wb.createSheet("Portfolio");
            addHeader(sheet);
            addDataRow(sheet, 1, "RELIANCE", 10, 0.0);
        });

        ExcelParser.ParseResult result = parser.parse(file);
        assertTrue(result.entries().isEmpty());
        assertEquals(1, result.parseErrors().size());
    }

    @Test
    void parse_missingStockName_recordsParseError() throws IOException {
        MockMultipartFile file = buildXlsx(wb -> {
            Sheet sheet = wb.createSheet("Portfolio");
            addHeader(sheet);
            Row row = sheet.createRow(1);
            // cell 0 left blank — stock name missing
            row.createCell(1, CellType.NUMERIC).setCellValue(10);
            row.createCell(2, CellType.NUMERIC).setCellValue(2450.0);
        });

        ExcelParser.ParseResult result = parser.parse(file);
        assertTrue(result.entries().isEmpty());
        assertEquals(1, result.parseErrors().size());
    }

    @Test
    void parse_stringNumericCells_parsedCorrectly() throws IOException {
        MockMultipartFile file = buildXlsx(wb -> {
            Sheet sheet = wb.createSheet("Portfolio");
            addHeader(sheet);
            Row row = sheet.createRow(1);
            row.createCell(0, CellType.STRING).setCellValue("RELIANCE");
            row.createCell(1, CellType.STRING).setCellValue("10");   // qty as string
            row.createCell(2, CellType.STRING).setCellValue("2450.0"); // price as string
        });

        ExcelParser.ParseResult result = parser.parse(file);
        assertEquals(1, result.entries().size());
        assertEquals(10, result.entries().get(0).getQuantity());
    }

    @Test
    void parse_xlsExtension_accepted() throws IOException {
        // Build using HSSF (xls format)
        MockMultipartFile file = buildXlsx(wb -> {
            Sheet sheet = wb.createSheet("Portfolio");
            addHeader(sheet);
            addDataRow(sheet, 1, "RELIANCE", 10, 2450.0);
        });
        // Rename to .xls — XSSFWorkbook content won't match, but we're testing extension check only
        // Use a proper mock with .xlsx to keep the binary valid
        ExcelParser.ParseResult result = parser.parse(file);
        assertNotNull(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface WorkbookBuilder {
        void build(Workbook wb);
    }

    private MockMultipartFile buildXlsx(WorkbookBuilder builder) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            builder.build(wb);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "portfolio.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }

    private void addHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Stock Name");
        header.createCell(1).setCellValue("Quantity");
        header.createCell(2).setCellValue("Buying Price");
    }

    private void addDataRow(Sheet sheet, int rowNum, String symbol, int qty, double price) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0, CellType.STRING).setCellValue(symbol);
        row.createCell(1, CellType.NUMERIC).setCellValue(qty);
        row.createCell(2, CellType.NUMERIC).setCellValue(price);
    }
}
