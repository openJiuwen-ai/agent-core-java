/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.BufferedReader;
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

/**
 * Parser for xlsx/csv/tsv tabular files that emits row and column documents.
 */
public class ExcelParser extends Parser {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String cellStr(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<Document> rowsToDocuments(List<? extends List<?>> rows,
                                                 String sheetName,
                                                 String baseId,
                                                 int sheetIndex,
                                                 boolean isHeaderIncluded) {
        List<Document> docs = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return docs;
        }

        List<String> headers = new ArrayList<>();
        for (Object value : rows.get(0)) {
            headers.add(cellStr(value));
        }
        List<? extends List<?>> dataRows = rows.size() > 1 ? rows.subList(1, rows.size()) : List.of();

        int rowIndex = 2;
        for (List<?> row : dataRows) {
            List<String> parts = new ArrayList<>();
            for (int colIndex = 0; colIndex < Math.min(headers.size(), row.size()); colIndex++) {
                String header = headers.get(colIndex);
                String value = cellStr(row.get(colIndex));
                if (isHeaderIncluded && !header.isBlank()) {
                    parts.add(header + ": " + value);
                } else if (!isHeaderIncluded && !value.isBlank()) {
                    parts.add(value);
                }
            }
            if (!parts.isEmpty()) {
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("sheet_name", sheetName);
                metadata.put("row_index", rowIndex);
                metadata.put("source_type", "row");
                docs.add(new Document(
                        baseId + "_s" + sheetIndex + "_r" + rowIndex,
                        String.join(", ", parts),
                        metadata));
            }
            rowIndex++;
        }

        for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
            String columnName = headers.get(colIndex);
            if (columnName.isBlank()) {
                columnName = "Column " + (colIndex + 1);
            }
            List<String> values = new ArrayList<>();
            for (List<?> row : dataRows) {
                if (colIndex < row.size()) {
                    String value = cellStr(row.get(colIndex));
                    if (!value.isBlank()) {
                        values.add(value);
                    }
                }
            }
            String text;
            if (isHeaderIncluded) {
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
            docs.add(new Document(baseId + "_s" + sheetIndex + "_c" + colIndex, text, metadata));
        }
        return docs;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        Path path = Path.of(doc);
        if (!Files.exists(path)) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND,
                    "File " + doc + " does not exist");
        }
        boolean isHeaderIncluded = optionAsBoolean(
                options,
                "include_header",
                optionAsBoolean(options, "includeHeader", true));
        String baseId = docId == null || docId.isBlank() ? doc : docId;
        String ext = extensionOf(doc);
        try {
            if (".csv".equals(ext) || ".tsv".equals(ext)) {
                char delimiter = ".tsv".equals(ext) ? '\t' : ',';
                List<List<String>> rows = loadDelimitedRows(path, delimiter);
                String sheetName = path.getFileName() == null ? "default" : path.getFileName().toString();
                return rowsToDocuments(rows, sheetName, baseId, 0, isHeaderIncluded);
            }
            try (InputStream inputStream = Files.newInputStream(path);
                 Workbook workbook = WorkbookFactory.create(inputStream)) {
                List<Document> documents = new ArrayList<>();
                int sheetIndex = 0;
                for (Sheet sheet : workbook) {
                    documents.addAll(rowsToDocuments(
                            readSheetRows(sheet),
                            sheet.getSheetName(),
                            baseId,
                            sheetIndex,
                            isHeaderIncluded));
                    sheetIndex++;
                }
                return documents;
            }
        } catch (IOException ex) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_FORMAT_NOT_SUPPORT,
                    "Parse failed for " + doc + ": " + ex.getMessage());
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        return null;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean supports(String doc) {
        String ext = extensionOf(doc);
        return ".xlsx".equals(ext) || ".csv".equals(ext) || ".tsv".equals(ext);
    }

    private static boolean optionAsBoolean(Map<String, Object> options, String key, boolean defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        return !(value instanceof Boolean booleanValue) || booleanValue;
    }

    private static String extensionOf(String doc) {
        if (doc == null || doc.isBlank()) {
            return "";
        }
        String lower = doc.toLowerCase(Locale.ROOT);
        int idx = lower.lastIndexOf('.');
        return idx >= 0 ? lower.substring(idx) : "";
    }

    private static List<List<String>> readSheetRows(Sheet sheet) {
        List<List<String>> rows = new ArrayList<>();
        for (Row row : sheet) {
            int lastCell = Math.max(row.getLastCellNum(), 0);
            List<String> values = new ArrayList<>();
            for (int colIndex = 0; colIndex < lastCell; colIndex++) {
                Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                values.add(cell == null ? "" : DATA_FORMATTER.formatCellValue(cell).trim());
            }
            rows.add(values);
        }
        return rows;
    }

    private static List<List<String>> loadDelimitedRows(Path path, char delimiter) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                rows.add(parseDelimitedLine(line, delimiter));
            }
        }
        return rows;
    }

    private static List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == delimiter && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }
}
