/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ExcelParser.
 *
 * <p>Mirrors Python's {@code test_excel_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.</p>
 *
 * <p>The detailed 28-case parity suite is covered by
 * {@code com.openjiuwen.core.retrieval.indexing.processor.parser.ExcelParserTest};
 * this class keeps the legacy mapping target executable with real behavior assertions.</p>
 */
@DisplayName("TestExcelParser")
class TestExcelParser {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("ExcelParser tests")
    class ExcelParserTests {

        @Test
        @DisplayName("test_cell_str")
        void testCellStr() {
            assertEquals("", ExcelParser.cellStr(null));
            assertEquals("hello", ExcelParser.cellStr("  hello  "));
            assertEquals("100", ExcelParser.cellStr(100));
        }

        @Test
        @DisplayName("test_rows_to_documents")
        void testRowsToDocuments() {
            List<Document> docs = ExcelParser.rowsToDocuments(
                    List.of(List.of("Name", "Dept"), List.of("Alice", "Sales"), List.of("Bob", "Tech")),
                    "Sheet1",
                    "excel1",
                    0,
                    true);

            List<Document> rowDocs = docs.stream()
                    .filter(doc -> "row".equals(doc.getMetadata().get("source_type")))
                    .toList();
            List<Document> columnDocs = docs.stream()
                    .filter(doc -> "column".equals(doc.getMetadata().get("source_type")))
                    .toList();
            Set<String> columnNames = columnDocs.stream()
                    .map(doc -> String.valueOf(doc.getMetadata().get("column_name")))
                    .collect(Collectors.toSet());

            assertEquals(2, rowDocs.size());
            assertEquals(2, columnDocs.size());
            assertEquals("Name: Alice, Dept: Sales", rowDocs.getFirst().getText());
            assertEquals(Set.of("Name", "Dept"), columnNames);
        }

        @Test
        @DisplayName("test_supports_xlsx_csv_tsv")
        void testSupportsXlsxCsvTsv() {
            ExcelParser parser = new ExcelParser();

            assertTrue(parser.supports("/some/file.xlsx"));
            assertTrue(parser.supports("/some/file.csv"));
            assertTrue(parser.supports("/some/file.tsv"));
            assertFalse(parser.supports("/some/file.xls"));
            assertFalse(parser.supports("/some/file.txt"));
        }

        @Test
        @DisplayName("test_parse_csv_row_and_column_docs")
        void testParseCsvRowAndColumnDocs() throws Exception {
            Path csv = tempDir.resolve("sales.csv");
            Files.writeString(csv, "Name,Dept,Sales\nAlice,Sales,100\nBob,Tech,200\n");

            List<Document> docs = new ExcelParser().parse(csv.toString(), "csv1", null, Map.of());

            List<Document> rowDocs = docs.stream()
                    .filter(doc -> "row".equals(doc.getMetadata().get("source_type")))
                    .toList();
            List<Document> columnDocs = docs.stream()
                    .filter(doc -> "column".equals(doc.getMetadata().get("source_type")))
                    .toList();

            assertEquals(2, rowDocs.size());
            assertEquals(3, columnDocs.size());
            assertTrue(rowDocs.getFirst().getText().contains("Alice"));
            assertEquals(csv.getFileName().toString(), rowDocs.getFirst().getMetadata().get("sheet_name"));
        }

        @Test
        @DisplayName("test_parse_missing_file_raises")
        void testParseMissingFileRaises() {
            BaseError error = assertThrows(BaseError.class, () -> new ExcelParser().parse(
                    tempDir.resolve("missing.csv").toString(), "missing", null, Map.of()));

            assertTrue(error.getMessage().contains("does not exist"));
        }

        @Test
        @DisplayName("test_parse_tsv_include_header_false")
        void testParseTsvIncludeHeaderFalse() throws Exception {
            Path tsv = tempDir.resolve("values.tsv");
            Files.writeString(tsv, "A\tB\n1\t2\n");

            List<Document> docs = new ExcelParser().parse(tsv.toString(), "tsv1", null, Map.of("include_header", false));
            Document rowDoc = docs.stream()
                    .filter(doc -> "row".equals(doc.getMetadata().get("source_type")))
                    .findFirst()
                    .orElseThrow();

            assertEquals("1, 2", rowDoc.getText());
        }

        @Test
        @DisplayName("test_init")
        void testInit() {
            assertNotNull(new ExcelParser());
        }
    }
}
