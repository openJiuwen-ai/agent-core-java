/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Parser for Excel, CSV, and TSV tabular files that emits row and column documents.
 *
 * <p>Mirrors Python's {@code ExcelParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.</p>
 */
public class ExcelParser extends Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelParser.class);
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    /**
     * Mirrors Python's {@code _cell_str} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    public static String cellStr(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * Mirrors Python's {@code _rows_to_documents} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    public static List<Document> rowsToDocuments(
            List<? extends List<?>> rows,
            String sheetName,
            String baseId,
            int sheetIndex
    ) {
        return rowsToDocuments(rows, sheetName, baseId, sheetIndex, true);
    }

    /**
     * Mirrors Python's {@code _rows_to_documents} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/excel_parser.py}.
     */
    public static List<Document> rowsToDocuments(
            List<? extends List<?>> rows,
            String sheetName,
            String baseId,
            int sheetIndex,
            boolean includeHeader
    ) {
        List<Document> documents = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return documents;
        }

        List<String> headers = new ArrayList<>();
        for (Object value : rows.getFirst()) {
            headers.add(cellStr(value));
        }
        List<? extends List<?>> dataRows = rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();

        int rowIndex = 2;
        for (List<?> row : dataRows) {
            List<String> parts = new ArrayList<>();
            int columnLimit = Math.min(headers.size(), row.size());
            for (int columnIndex = 0; columnIndex < columnLimit; columnIndex++) {
                String header = headers.get(columnIndex);
                String value = cellStr(row.get(columnIndex));
                if (includeHeader && !header.isBlank()) {
                    parts.add(header + ": " + value);
                } else if (!includeHeader && !value.isBlank()) {
                    parts.add(value);
                }
            }
            if (!parts.isEmpty()) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("sheet_name", sheetName);
                metadata.put("row_index", rowIndex);
                metadata.put("source_type", "row");
                documents.add(new Document(
                        baseId + "_s" + sheetIndex + "_r" + rowIndex,
                        String.join(", ", parts),
                        metadata
                ));
            }
            rowIndex++;
        }

        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            String columnName = headers.get(columnIndex);
            if (columnName.isBlank()) {
                columnName = "Column " + (columnIndex + 1);
            }
            List<String> values = new ArrayList<>();
            for (List<?> row : dataRows) {
                if (columnIndex < row.size()) {
                    String value = cellStr(row.get(columnIndex));
                    if (!value.isBlank()) {
                        values.add(value);
                    }
                }
            }
            String text;
            if (includeHeader) {
                text = values.isEmpty()
                        ? "Column name: " + columnName + ". Values: (empty)"
                        : "Column name: " + columnName + ". Values: " + String.join(", ", values);
            } else {
                text = values.isEmpty() ? "" : String.join(", ", values);
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sheet_name", sheetName);
            metadata.put("column_name", columnName);
            metadata.put("source_type", "column");
            documents.add(new Document(baseId + "_s" + sheetIndex + "_c" + columnIndex, text, metadata));
        }
        return documents;
    }

    @Override
    public CompletableFuture<List<Document>> parse(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        Path path = Path.of(doc);
        if (!Files.exists(path)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND,
                    "error_msg",
                    "File " + doc + " does not exist"
            );
        }

        Map<String, Object> safeOptions = options == null ? Map.of() : options;
        boolean includeHeader = optionAsBoolean(
                safeOptions,
                "include_header",
                optionAsBoolean(safeOptions, "includeHeader", true)
        );
        String baseId = docId == null || docId.isBlank() ? doc : docId;
        String extension = extensionOf(doc);
        return CompletableFuture.supplyAsync(() -> parseExistingFile(path, doc, baseId, extension, includeHeader));
    }

    @Override
    public boolean supports(String doc) {
        String extension = extensionOf(doc);
        return ".xlsx".equals(extension) || ".csv".equals(extension) || ".tsv".equals(extension);
    }

    private static List<Document> parseExistingFile(
            Path path,
            String doc,
            String baseId,
            String extension,
            boolean includeHeader
    ) {
        try {
            List<Document> documents;
            if (".csv".equals(extension) || ".tsv".equals(extension)) {
                char delimiter = ".tsv".equals(extension) ? '\t' : ',';
                List<List<String>> rows = loadDelimitedRows(path, delimiter);
                String sheetName = path.getFileName() == null ? "default" : path.getFileName().toString();
                documents = rowsToDocuments(rows, sheetName, baseId, 0, includeHeader);
            } else {
                documents = loadWorkbookDocuments(path, baseId, includeHeader);
            }
            LOGGER.info("Parsed {}: {} documents (rows + columns)", doc, documents.size());
            return documents;
        } catch (Exception exception) {
            String fileType = ".xlsx".equals(extension) ? "Excel" : extension.toUpperCase(Locale.ROOT).replace(".", "");
            LOGGER.warn("Failed to parse {} {}: {}", fileType, doc, exception.getMessage(), exception);
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_FORMAT_NOT_SUPPORT,
                    "error_msg",
                    ("Excel".equals(fileType) ? "Excel parse failed" : "Parse failed") + " for " + doc + ": "
                            + exception.getMessage()
            );
        }
    }

    private static List<Document> loadWorkbookDocuments(Path path, String baseId, boolean includeHeader) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            List<Document> documents = new ArrayList<>();
            int sheetIndex = 0;
            for (Sheet sheet : workbook) {
                documents.addAll(rowsToDocuments(readSheetRows(sheet), sheet.getSheetName(), baseId, sheetIndex,
                        includeHeader));
                sheetIndex++;
            }
            return documents;
        }
    }

    private static List<List<String>> readSheetRows(Sheet sheet) {
        int firstRow = sheet.getFirstRowNum();
        int lastRow = sheet.getLastRowNum();
        int maxColumns = 0;
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                maxColumns = Math.max(maxColumns, row.getLastCellNum());
            }
        }
        List<List<String>> rows = new ArrayList<>();
        if (maxColumns <= 0) {
            return rows;
        }
        for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            List<String> values = new ArrayList<>();
            for (int columnIndex = 0; columnIndex < maxColumns; columnIndex++) {
                Cell cell = row == null ? null : row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                values.add(cell == null ? "" : DATA_FORMATTER.formatCellValue(cell).trim());
            }
            rows.add(values);
        }
        return rows;
    }

    private static List<List<String>> loadDelimitedRows(Path path, char delimiter) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        return parseDelimitedContent(content, delimiter);
    }

    private static List<List<String>> parseDelimitedContent(String content, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean hasData = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            hasData = true;
            if (current == '"') {
                if (quoted && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == delimiter && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
            } else {
                cell.append(current);
            }
        }
        if (hasData && (cell.length() > 0 || !row.isEmpty())) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private static boolean optionAsBoolean(Map<String, Object> options, String key, boolean defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return true;
    }

    private static String extensionOf(String doc) {
        if (doc == null || doc.isBlank()) {
            return "";
        }
        String fileName = Path.of(doc).getFileName().toString().toLowerCase(Locale.ROOT);
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index) : "";
    }
}
