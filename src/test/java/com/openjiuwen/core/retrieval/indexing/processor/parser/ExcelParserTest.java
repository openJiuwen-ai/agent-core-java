/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelParserTest {

    @TempDir
    Path tempDir;

    @Test
    void rowsToDocumentsGeneratesRowAndColumnDocs() {
        List<Document> docs = ExcelParser.rowsToDocuments(
                List.of(
                        List.of("Name", "Dept"),
                        List.of("Alice", "Sales"),
                        List.of("Bob", "Tech")),
                "Sheet1",
                "base",
                0,
                true);

        assertEquals(4, docs.size());
        assertTrue(docs.stream().anyMatch(doc -> "row".equals(doc.getMetadata().get("source_type")) && doc.getText().contains("Name: Alice")));
        assertTrue(docs.stream().anyMatch(doc -> "column".equals(doc.getMetadata().get("source_type")) && doc.getText().contains("Column name: Name")));
    }

    @Test
    void parseCsvProducesRowAndColumnDocs() throws IOException {
        Path file = tempDir.resolve("sample.csv");
        Files.writeString(file, "Name,Dept,Sales\nAlice,Sales,100\nBob,Tech,200\n", StandardCharsets.UTF_8);

        ExcelParser parser = new ExcelParser();
        List<Document> docs = parser.parse(file.toString(), "csv1", null, Map.of());

        assertEquals(5, docs.size());
        assertTrue(docs.stream().allMatch(doc -> doc.getId().startsWith("csv1")));
    }

    @Test
    void parseTsvSupportsIncludeHeaderFalse() throws IOException {
        Path file = tempDir.resolve("sample.tsv");
        Files.writeString(file, "A\tB\n1\t2\n", StandardCharsets.UTF_8);

        ExcelParser parser = new ExcelParser();
        List<Document> docs = parser.parse(file.toString(), "tsv1", null, Map.of("include_header", false));

        assertTrue(docs.stream().anyMatch(doc -> "row".equals(doc.getMetadata().get("source_type")) && "1, 2".equals(doc.getText())));
    }

    @Test
    void parseXlsxReadsMultipleSheets() throws IOException {
        Path file = tempDir.resolve("sample.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream output = Files.newOutputStream(file)) {
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
            workbook.write(output);
        }

        ExcelParser parser = new ExcelParser();
        List<Document> docs = parser.parse(file.toString(), "xlsx1", null, Map.of());

        assertTrue(docs.stream().anyMatch(doc -> "People".equals(doc.getMetadata().get("sheet_name"))));
        assertTrue(docs.stream().anyMatch(doc -> "Products".equals(doc.getMetadata().get("sheet_name"))));
    }

    @Test
    void parseMissingFileRaises() {
        ExcelParser parser = new ExcelParser();

        assertThrows(BaseError.class, () -> parser.parse(tempDir.resolve("missing.xlsx").toString(), "x", null, Map.of()));
    }
}
