/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code ExcelParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
 *
 * <p>Focused tests also mirror Python's {@code test_excel_parser.py} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_excel_parser.py}.</p>
 */
class ExcelParserTest {

    /**
     * Mirrors Python's {@code _cell_str} tests for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    @Nested
    class CellStrTests {

        @Test
        void testNone() {
            assertThat(ExcelParser.cellStr(null)).isEmpty();
        }

        @Test
        void testString() {
            assertThat(ExcelParser.cellStr("  hello  ")).isEqualTo("hello");
        }

        @Test
        void testInt() {
            assertThat(ExcelParser.cellStr(100)).isEqualTo("100");
        }

        @Test
        void testFloat() {
            assertThat(ExcelParser.cellStr(3.14)).isEqualTo("3.14");
        }

        @Test
        void testEmptyString() {
            assertThat(ExcelParser.cellStr("")).isEmpty();
        }
    }

    /**
     * Mirrors Python's {@code _rows_to_documents} tests for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    @Nested
    class RowsToDocumentsTests {

        @Test
        void testEmptyInput() {
            assertThat(ExcelParser.rowsToDocuments(List.of(), "Sheet1", "base", 0)).isEmpty();
        }

        @Test
        void testHeaderOnlyNoData() {
            List<Document> documents = ExcelParser.rowsToDocuments(List.of(List.of("A", "B", "C")),
                    "Sheet1", "base", 0);

            assertThat(documents).filteredOn(document -> "row".equals(document.getMetadata().get("source_type")))
                    .isEmpty();
            assertThat(documents).filteredOn(document -> "column".equals(document.getMetadata().get("source_type")))
                    .hasSize(3);
        }

        @Test
        void testRowTextFormat() {
            List<Document> documents = ExcelParser.rowsToDocuments(List.of(List.of("Name", "Age"),
                    List.of("Alice", "30")), "S", "b", 0);

            Document rowDocument = firstBySourceType(documents, "row");

            assertThat(rowDocument.getText()).isEqualTo("Name: Alice, Age: 30");
        }

        @Test
        void testColumnTextFormat() {
            List<Document> documents = ExcelParser.rowsToDocuments(List.of(List.of("City"), List.of("Beijing"),
                    List.of("Shanghai")), "S", "b", 0);

            Document columnDocument = firstBySourceType(documents, "column");

            assertThat(columnDocument.getText())
                    .contains("Column name: City")
                    .contains("Beijing")
                    .contains("Shanghai");
        }

        @Test
        void testDocIds() {
            List<Document> documents = ExcelParser.rowsToDocuments(List.of(List.of("X"), List.of("v1")),
                    "S", "mybase", 2);

            assertThat(documents).extracting(Document::getId)
                    .contains("mybase_s2_r2", "mybase_s2_c0");
        }

        @Test
        void testIncludeHeaderFalse() {
            List<Document> documents = ExcelParser.rowsToDocuments(List.of(List.of("Name", "Age"),
                    List.of("Alice", "30")), "S", "b", 0, false);

            Document rowDocument = firstBySourceType(documents, "row");
            Document columnDocument = firstBySourceType(documents, "column");

            assertThat(rowDocument.getText()).isEqualTo("Alice, 30");
            assertThat(columnDocument.getText()).doesNotContain("Column name:");
            assertThat(columnDocument.getText()).containsAnyOf("Alice", "30");
        }
    }

    /**
     * Mirrors Python's {@code ExcelParser.supports} tests for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    @Nested
    class SupportsTests {

        @Test
        void testSupportsXlsx() {
            ExcelParser parser = new ExcelParser();

            assertThat(parser.supports("/some/file.xlsx")).isTrue();
            assertThat(parser.supports("/some/file.XLSX")).isTrue();
        }

        @Test
        void testSupportsCsv() {
            ExcelParser parser = new ExcelParser();

            assertThat(parser.supports("/some/file.csv")).isTrue();
            assertThat(parser.supports("/some/file.CSV")).isTrue();
        }

        @Test
        void testSupportsTsv() {
            ExcelParser parser = new ExcelParser();

            assertThat(parser.supports("/some/file.tsv")).isTrue();
            assertThat(parser.supports("/some/file.TSV")).isTrue();
        }

        @Test
        void testRejectsOtherExtensions() {
            ExcelParser parser = new ExcelParser();

            assertThat(parser.supports("/some/file.pdf")).isFalse();
            assertThat(parser.supports("/some/file.xls")).isFalse();
            assertThat(parser.supports("/some/file.txt")).isFalse();
        }

        @Test
        void testRejectsEmpty() {
            ExcelParser parser = new ExcelParser();

            assertThat(parser.supports("")).isFalse();
            assertThat(parser.supports(null)).isFalse();
        }
    }

    /**
     * Mirrors Python's {@code ExcelParser.parse} xlsx tests for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    @Nested
    class XlsxTests {

        @TempDir
        Path tempDir;

        @Test
        void testParseRowAndColumnDocs() throws IOException {
            Path path = tempDir.resolve("sample.xlsx");
            createSampleXlsx(path);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "excel1").join();

            List<Document> rowDocuments = bySourceType(documents, "row");
            List<Document> columnDocuments = bySourceType(documents, "column");
            assertThat(rowDocuments).hasSize(2);
            assertThat(columnDocuments).hasSize(3);
            rowDocuments = rowDocuments.stream()
                    .sorted((left, right) -> Integer.compare(
                            Integer.parseInt(String.valueOf(left.getMetadata().get("row_index"))),
                            Integer.parseInt(String.valueOf(right.getMetadata().get("row_index")))))
                    .toList();
            assertThat(rowDocuments.get(0).getMetadata())
                    .containsEntry("sheet_name", "Sheet1")
                    .containsEntry("row_index", 2);
            assertThat(rowDocuments.get(0).getText()).contains("Alice", "Sales");
            assertThat(rowDocuments.get(1).getMetadata()).containsEntry("row_index", 3);
            assertThat(rowDocuments.get(1).getText()).contains("Bob", "Tech");
            assertThat(columnDocuments).extracting(document -> String.valueOf(document.getMetadata().get("column_name")))
                    .containsExactlyInAnyOrder("Name", "Dept", "Sales");
            Document nameColumn = columnDocuments.stream()
                    .filter(document -> "Name".equals(document.getMetadata().get("column_name")))
                    .findFirst()
                    .orElseThrow();
            assertThat(nameColumn.getText()).contains("Alice", "Bob");
        }

        @Test
        void testParseDocIdPrefix() throws IOException {
            Path path = tempDir.resolve("sample.xlsx");
            createSampleXlsx(path);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "my_id").join();

            assertThat(documents).allMatch(document -> document.getId().startsWith("my_id"));
            Document rowOne = documents.stream()
                    .filter(document -> "row".equals(document.getMetadata().get("source_type"))
                            && Integer.valueOf(2).equals(document.getMetadata().get("row_index")))
                    .findFirst()
                    .orElseThrow();
            assertThat(rowOne.getId()).isEqualTo("my_id_s0_r2");
        }

        @Test
        void testParseMultiSheet() throws IOException {
            Path path = tempDir.resolve("multi.xlsx");
            createMultiSheetXlsx(path);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "ms").join();

            Set<String> sheets = documents.stream()
                    .map(document -> String.valueOf(document.getMetadata().get("sheet_name")))
                    .collect(Collectors.toSet());
            assertThat(sheets).contains("People", "Products");
            assertThat(documents).filteredOn(document -> "People".equals(document.getMetadata().get("sheet_name"))
                    && "row".equals(document.getMetadata().get("source_type"))).hasSize(1);
            assertThat(documents).filteredOn(document -> "Products".equals(document.getMetadata().get("sheet_name"))
                    && "row".equals(document.getMetadata().get("source_type"))).hasSize(2);
        }

        @Test
        void testParseHeaderOnly() throws IOException {
            Path path = tempDir.resolve("header-only.xlsx");
            createHeaderOnlyXlsx(path);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "hdr").join();

            assertThat(bySourceType(documents, "row")).isEmpty();
            assertThat(bySourceType(documents, "column")).hasSize(3);
        }

        @Test
        void testParseEmptyHeaderFallback() throws IOException {
            Path path = tempDir.resolve("empty-header.xlsx");
            createEmptyHeaderXlsx(path);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "eh").join();
            Set<String> columnNames = bySourceType(documents, "column").stream()
                    .map(document -> String.valueOf(document.getMetadata().get("column_name")))
                    .collect(Collectors.toSet());

            assertThat(columnNames).contains("Score");
            assertThat(columnNames).anyMatch(name -> !"Score".equals(name));
        }

        @Test
        void testParseMissingFileRaises() {
            ExcelParser parser = new ExcelParser();

            assertThatThrownBy(() -> parser.parse("/nonexistent/file.xlsx", "x").join())
                    .isInstanceOf(BaseError.class)
                    .extracting(error -> ((BaseError) error).getStatus())
                    .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND);
        }
    }

    /**
     * Mirrors Python's {@code ExcelParser.parse} csv tests for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    @Nested
    class CsvTests {

        @TempDir
        Path tempDir;

        @Test
        void testParseCsvRowAndColumnDocs() throws IOException {
            Path path = tempDir.resolve("sample.csv");
            Files.writeString(path, "Name,Dept,Sales\nAlice,Sales,100\nBob,Tech,200\n", StandardCharsets.UTF_8);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "csv1").join();

            List<Document> rowDocuments = bySourceType(documents, "row");
            List<Document> columnDocuments = bySourceType(documents, "column");
            assertThat(rowDocuments).hasSize(2);
            assertThat(columnDocuments).hasSize(3);
            assertThat(rowDocuments.get(0).getMetadata()).containsEntry("sheet_name", path.getFileName().toString());
            assertThat(rowDocuments.get(0).getText()).contains("Alice");
            assertThat(columnDocuments).extracting(document -> String.valueOf(document.getMetadata().get("column_name")))
                    .containsExactlyInAnyOrder("Name", "Dept", "Sales");
        }

        @Test
        void testParseCsvWithEmptyCells() throws IOException {
            Path path = tempDir.resolve("sample.csv");
            Files.writeString(path, "A,B\n1,\n,3\n", StandardCharsets.UTF_8);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "csv_empty").join();

            assertThat(bySourceType(documents, "row")).hasSize(2);
        }

        @Test
        void testParseCsvHeaderOnly() throws IOException {
            Path path = tempDir.resolve("sample.csv");
            Files.writeString(path, "X,Y,Z\n", StandardCharsets.UTF_8);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "csv_hdr").join();

            assertThat(bySourceType(documents, "row")).isEmpty();
            assertThat(bySourceType(documents, "column")).hasSize(3);
        }

        @Test
        void testParseCsvMissingFileRaises() {
            ExcelParser parser = new ExcelParser();

            assertThatThrownBy(() -> parser.parse("/nonexistent/file.csv", "x").join())
                    .isInstanceOf(BaseError.class)
                    .extracting(error -> ((BaseError) error).getStatus())
                    .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND);
        }
    }

    /**
     * Mirrors Python's {@code ExcelParser.parse} tsv tests for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    @Nested
    class TsvTests {

        @TempDir
        Path tempDir;

        @Test
        void testParseTsvRowAndColumnDocs() throws IOException {
            Path path = tempDir.resolve("sample.tsv");
            Files.writeString(path, "Name\tDept\tSales\nAlice\tSales\t100\nBob\tTech\t200\n", StandardCharsets.UTF_8);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "tsv1").join();

            List<Document> rowDocuments = bySourceType(documents, "row");
            List<Document> columnDocuments = bySourceType(documents, "column");
            assertThat(rowDocuments).hasSize(2);
            assertThat(columnDocuments).hasSize(3);
            assertThat(rowDocuments.get(0).getText()).contains("Alice");
            assertThat(columnDocuments).extracting(document -> String.valueOf(document.getMetadata().get("column_name")))
                    .containsExactlyInAnyOrder("Name", "Dept", "Sales");
        }

        @Test
        void testParseTsvIncludeHeaderFalse() throws IOException {
            Path path = tempDir.resolve("sample.tsv");
            Files.writeString(path, "A\tB\n1\t2\n", StandardCharsets.UTF_8);
            ExcelParser parser = new ExcelParser();

            List<Document> documents = parser.parse(path.toString(), "tsv2", null,
                    Map.of("include_header", false)).join();
            List<Document> rowDocuments = bySourceType(documents, "row");
            List<Document> columnDocuments = bySourceType(documents, "column");

            assertThat(rowDocuments).hasSize(1);
            assertThat(rowDocuments.get(0).getText()).isEqualTo("1, 2");
            assertThat(columnDocuments).hasSize(2);
            assertThat(columnDocuments).extracting(Document::getText).containsExactly("1", "2");
        }
    }

    private static List<Document> bySourceType(List<Document> documents, String sourceType) {
        return documents.stream()
                .filter(document -> sourceType.equals(document.getMetadata().get("source_type")))
                .toList();
    }

    private static Document firstBySourceType(List<Document> documents, String sourceType) {
        return bySourceType(documents, sourceType).get(0);
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
