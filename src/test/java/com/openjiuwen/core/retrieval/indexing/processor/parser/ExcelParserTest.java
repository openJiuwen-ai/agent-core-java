/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_excel_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class ExcelParserTest {

    @Nested
    class CellStrTests {

        @Test
        void testNone() {
            assertEquals("", ExcelParser.cellStr(null));
        }

        @Test
        void testString() {
            assertEquals("hello", ExcelParser.cellStr("  hello  "));
        }

        @Test
        void testInt() {
            assertEquals("100", ExcelParser.cellStr(100));
        }

        @Test
        void testFloat() {
            assertEquals("3.14", ExcelParser.cellStr(3.14));
        }

        @Test
        void testEmptyString() {
            assertEquals("", ExcelParser.cellStr(""));
        }
    }

    @Nested
    class RowsToDocumentsTests {

        @Test
        void testEmptyInput() {
            assertTrue(ExcelParser.rowsToDocuments(List.of(), "Sheet1", "base", 0, true).isEmpty());
        }

        @Test
        void testHeaderOnlyNoData() {
            List<Document> docs = ExcelParser.rowsToDocuments(List.of(List.of("A", "B", "C")), "Sheet1", "base", 0, true);
            List<Document> rowDocs = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).toList();
            List<Document> colDocs = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).toList();
            assertEquals(0, rowDocs.size());
            assertEquals(3, colDocs.size());
        }

        @Test
        void testRowTextFormat() {
            List<Document> docs = ExcelParser.rowsToDocuments(List.of(List.of("Name", "Age"), List.of("Alice", "30")), "S", "b", 0, true);
            Document rowDoc = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).findFirst().orElseThrow();
            assertEquals("Name: Alice, Age: 30", rowDoc.getText());
        }

        @Test
        void testColumnTextFormat() {
            List<Document> docs = ExcelParser.rowsToDocuments(List.of(List.of("City"), List.of("Beijing"), List.of("Shanghai")), "S", "b", 0, true);
            Document colDoc = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).findFirst().orElseThrow();
            assertTrue(colDoc.getText().contains("Column name: City"));
            assertTrue(colDoc.getText().contains("Beijing"));
            assertTrue(colDoc.getText().contains("Shanghai"));
        }

        @Test
        void testDocIds() {
            List<Document> docs = ExcelParser.rowsToDocuments(List.of(List.of("X"), List.of("v1")), "S", "mybase", 2, true);
            Set<String> ids = docs.stream().map(Document::getId).collect(java.util.stream.Collectors.toSet());
            assertTrue(ids.contains("mybase_s2_r2"));
            assertTrue(ids.contains("mybase_s2_c0"));
        }

        @Test
        void testIncludeHeaderFalse() {
            List<Document> docs = ExcelParser.rowsToDocuments(List.of(List.of("Name", "Age"), List.of("Alice", "30")), "S", "b", 0, false);
            Document rowDoc = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).findFirst().orElseThrow();
            Document colDoc = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).findFirst().orElseThrow();
            assertEquals("Alice, 30", rowDoc.getText());
            assertFalse(colDoc.getText().contains("Column name:"));
            assertTrue(colDoc.getText().contains("Alice") || colDoc.getText().contains("30"));
        }
    }

    @Nested
    class SupportsTests {

        @Test
        void testSupportsXlsx() {
            ExcelParser parser = new ExcelParser();
            assertTrue(parser.supports("/some/file.xlsx"));
            assertTrue(parser.supports("/some/file.XLSX"));
        }

        @Test
        void testSupportsCsv() {
            ExcelParser parser = new ExcelParser();
            assertTrue(parser.supports("/some/file.csv"));
            assertTrue(parser.supports("/some/file.CSV"));
        }

        @Test
        void testSupportsTsv() {
            ExcelParser parser = new ExcelParser();
            assertTrue(parser.supports("/some/file.tsv"));
            assertTrue(parser.supports("/some/file.TSV"));
        }

        @Test
        void testRejectsOtherExtensions() {
            ExcelParser parser = new ExcelParser();
            assertFalse(parser.supports("/some/file.pdf"));
            assertFalse(parser.supports("/some/file.xls"));
            assertFalse(parser.supports("/some/file.txt"));
        }

        @Test
        void testRejectsEmpty() {
            ExcelParser parser = new ExcelParser();
            assertFalse(parser.supports(""));
            assertFalse(parser.supports(null));
        }
    }

    @Nested
    class XlsxTests {

        @TempDir
        Path tempDir;

        @Test
        void testParseRowAndColumnDocs() throws IOException {
            Path path = tempDir.resolve("sample.xlsx");
            createSampleXlsx(path);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "excel1", null, Map.of());
            List<Document> rowDocs = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).toList();
            List<Document> colDocs = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).toList();

            assertEquals(2, rowDocs.size());
            assertEquals(3, colDocs.size());
            rowDocs = rowDocs.stream().sorted((a, b) -> Integer.compare(
                    Integer.parseInt(a.getMetadata().get("row_index").toString()),
                    Integer.parseInt(b.getMetadata().get("row_index").toString()))).toList();
            assertEquals("Sheet1", rowDocs.getFirst().getMetadata().get("sheet_name"));
            assertEquals(2, rowDocs.getFirst().getMetadata().get("row_index"));
            assertTrue(rowDocs.getFirst().getText().contains("Alice"));
            assertTrue(rowDocs.getFirst().getText().contains("Sales"));
            assertEquals(3, rowDocs.get(1).getMetadata().get("row_index"));
            assertTrue(rowDocs.get(1).getText().contains("Bob"));
            assertTrue(rowDocs.get(1).getText().contains("Tech"));

            Set<String> colNames = colDocs.stream().map(d -> String.valueOf(d.getMetadata().get("column_name"))).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("Name", "Dept", "Sales"), colNames);
            Document nameCol = colDocs.stream().filter(d -> "Name".equals(d.getMetadata().get("column_name"))).findFirst().orElseThrow();
            assertTrue(nameCol.getText().contains("Alice"));
            assertTrue(nameCol.getText().contains("Bob"));
        }

        @Test
        void testParseDocIdPrefix() throws IOException {
            Path path = tempDir.resolve("sample.xlsx");
            createSampleXlsx(path);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "my_id", null, Map.of());

            assertTrue(docs.stream().allMatch(d -> d.getId().startsWith("my_id")));
            Document rowOne = docs.stream()
                    .filter(d -> "row".equals(d.getMetadata().get("source_type")) && Integer.valueOf(2).equals(d.getMetadata().get("row_index")))
                    .findFirst().orElseThrow();
            assertEquals("my_id_s0_r2", rowOne.getId());
        }

        @Test
        void testParseMultiSheet() throws IOException {
            Path path = tempDir.resolve("sample.xlsx");
            createMultiSheetXlsx(path);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "ms", null, Map.of());

            Set<String> sheets = docs.stream().map(d -> String.valueOf(d.getMetadata().get("sheet_name"))).collect(java.util.stream.Collectors.toSet());
            assertTrue(sheets.contains("People"));
            assertTrue(sheets.contains("Products"));

            List<Document> peopleRows = docs.stream()
                    .filter(d -> "People".equals(d.getMetadata().get("sheet_name")) && "row".equals(d.getMetadata().get("source_type")))
                    .toList();
            List<Document> productRows = docs.stream()
                    .filter(d -> "Products".equals(d.getMetadata().get("sheet_name")) && "row".equals(d.getMetadata().get("source_type")))
                    .toList();
            assertEquals(1, peopleRows.size());
            assertEquals(2, productRows.size());
        }

        @Test
        void testParseHeaderOnly() throws IOException {
            Path path = tempDir.resolve("sample.xlsx");
            createHeaderOnlyXlsx(path);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "hdr", null, Map.of());

            List<Document> rowDocs = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).toList();
            List<Document> colDocs = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).toList();
            assertEquals(0, rowDocs.size());
            assertEquals(3, colDocs.size());
        }

        @Test
        void testParseEmptyHeaderFallback() throws IOException {
            Path path = tempDir.resolve("sample.xlsx");
            createEmptyHeaderXlsx(path);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "eh", null, Map.of());
            Set<String> colNames = docs.stream()
                    .filter(d -> "column".equals(d.getMetadata().get("source_type")))
                    .map(d -> String.valueOf(d.getMetadata().get("column_name")))
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(colNames.contains("Score"));
            assertTrue(colNames.stream().anyMatch(name -> !"Score".equals(name)));
        }

        @Test
        void testParseMissingFileRaises() {
            ExcelParser parser = new ExcelParser();

            assertThrows(BaseError.class, () -> parser.parse("/nonexistent/file.xlsx", "x", null, Map.of()));
        }
    }

    @Nested
    class CsvTests {

        @TempDir
        Path tempDir;

        @Test
        void testParseCsvRowAndColumnDocs() throws IOException {
            Path path = tempDir.resolve("sample.csv");
            Files.writeString(path, "Name,Dept,Sales\nAlice,Sales,100\nBob,Tech,200\n", StandardCharsets.UTF_8);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "csv1", null, Map.of());
            List<Document> rowDocs = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).toList();
            List<Document> colDocs = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).toList();
            assertEquals(2, rowDocs.size());
            assertEquals(3, colDocs.size());
            assertEquals(path.getFileName().toString(), rowDocs.getFirst().getMetadata().get("sheet_name"));
            assertTrue(rowDocs.getFirst().getText().contains("Alice"));
            Set<String> colNames = colDocs.stream().map(d -> String.valueOf(d.getMetadata().get("column_name"))).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("Name", "Dept", "Sales"), colNames);
        }

        @Test
        void testParseCsvWithEmptyCells() throws IOException {
            Path path = tempDir.resolve("sample.csv");
            Files.writeString(path, "A,B\n1,\n,3\n", StandardCharsets.UTF_8);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "csv_empty", null, Map.of());
            List<Document> rowDocs = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).toList();

            assertEquals(2, rowDocs.size());
        }

        @Test
        void testParseCsvHeaderOnly() throws IOException {
            Path path = tempDir.resolve("sample.csv");
            Files.writeString(path, "X,Y,Z\n", StandardCharsets.UTF_8);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "csv_hdr", null, Map.of());
            List<Document> rowDocs = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).toList();
            List<Document> colDocs = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).toList();
            assertEquals(0, rowDocs.size());
            assertEquals(3, colDocs.size());
        }

        @Test
        void testParseCsvMissingFileRaises() {
            ExcelParser parser = new ExcelParser();

            assertThrows(BaseError.class, () -> parser.parse("/nonexistent/file.csv", "x", null, Map.of()));
        }
    }

    @Nested
    class TsvTests {

        @TempDir
        Path tempDir;

        @Test
        void testParseTsvRowAndColumnDocs() throws IOException {
            Path path = tempDir.resolve("sample.tsv");
            Files.writeString(path, "Name\tDept\tSales\nAlice\tSales\t100\nBob\tTech\t200\n", StandardCharsets.UTF_8);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "tsv1", null, Map.of());
            List<Document> rowDocs = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).toList();
            List<Document> colDocs = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).toList();
            assertEquals(2, rowDocs.size());
            assertEquals(3, colDocs.size());
            assertTrue(rowDocs.getFirst().getText().contains("Alice"));
            Set<String> colNames = colDocs.stream().map(d -> String.valueOf(d.getMetadata().get("column_name"))).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("Name", "Dept", "Sales"), colNames);
        }

        @Test
        void testParseTsvIncludeHeaderFalse() throws IOException {
            Path path = tempDir.resolve("sample.tsv");
            Files.writeString(path, "A\tB\n1\t2\n", StandardCharsets.UTF_8);

            ExcelParser parser = new ExcelParser();
            List<Document> docs = parser.parse(path.toString(), "tsv2", null, Map.of("include_header", false));
            List<Document> rowDocs = docs.stream().filter(d -> "row".equals(d.getMetadata().get("source_type"))).toList();
            List<Document> colDocs = docs.stream().filter(d -> "column".equals(d.getMetadata().get("source_type"))).toList();
            assertEquals(1, rowDocs.size());
            assertEquals("1, 2", rowDocs.getFirst().getText());
            assertEquals(2, colDocs.size());
            assertTrue(colDocs.stream().map(Document::getText).anyMatch(text -> text.contains("1")));
            assertTrue(colDocs.stream().map(Document::getText).anyMatch(text -> text.contains("2")));
        }
    }

    private static void createSampleXlsx(Path path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(path)) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("Name");
            sheet.getRow(0).createCell(1).setCellValue("Dept");
            sheet.getRow(0).createCell(2).setCellValue("Sales");
            sheet.createRow(1).createCell(0).setCellValue("Alice");
            sheet.getRow(1).createCell(1).setCellValue("Sales");
            sheet.getRow(1).createCell(2).setCellValue(100);
            sheet.createRow(2).createCell(0).setCellValue("Bob");
            sheet.getRow(2).createCell(1).setCellValue("Tech");
            sheet.getRow(2).createCell(2).setCellValue(200);
            workbook.write(output);
        }
    }

    private static void createMultiSheetXlsx(Path path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(path)) {
            XSSFSheet people = workbook.createSheet("People");
            people.createRow(0).createCell(0).setCellValue("Name");
            people.getRow(0).createCell(1).setCellValue("Age");
            people.createRow(1).createCell(0).setCellValue("Alice");
            people.getRow(1).createCell(1).setCellValue(30);

            XSSFSheet products = workbook.createSheet("Products");
            products.createRow(0).createCell(0).setCellValue("Item");
            products.getRow(0).createCell(1).setCellValue("Price");
            products.createRow(1).createCell(0).setCellValue("Pen");
            products.getRow(1).createCell(1).setCellValue(5);
            products.createRow(2).createCell(0).setCellValue("Book");
            products.getRow(2).createCell(1).setCellValue(20);
            workbook.write(output);
        }
    }

    private static void createHeaderOnlyXlsx(Path path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(path)) {
            XSSFSheet sheet = workbook.createSheet("Empty");
            sheet.createRow(0).createCell(0).setCellValue("Col1");
            sheet.getRow(0).createCell(1).setCellValue("Col2");
            sheet.getRow(0).createCell(2).setCellValue("Col3");
            workbook.write(output);
        }
    }

    private static void createEmptyHeaderXlsx(Path path) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(path)) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0);
            sheet.getRow(0).createCell(1).setCellValue("Score");
            sheet.getRow(0).createCell(2).setCellValue("");
            sheet.createRow(1).createCell(0).setCellValue("Alice");
            sheet.getRow(1).createCell(1).setCellValue(90);
            sheet.getRow(1).createCell(2).setCellValue("pass");
            workbook.write(output);
        }
    }
}
