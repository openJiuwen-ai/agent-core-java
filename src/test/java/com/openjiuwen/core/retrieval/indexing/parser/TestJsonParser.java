/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for JsonParser.
 *
 * <p>Mirrors Python's {@code TestJSONParser} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_json_parser}.</p>
 */
class TestJsonParser {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("JsonParser tests")
    class JsonParserTests {

        @Test
        @DisplayName("test_init")
        void testInit() {
            assertNotNull(new JsonParser());
        }

        @Test
        @DisplayName("test_parse_json_success")
        void testParseJsonSuccess() throws Exception {
            Path file = tempDir.resolve("data.json");
            Files.writeString(file, "{\"name\":\"test\",\"value\":123}");

            List<Document> documents = new JsonParser().parse(file.toString(), "doc_1", null, Map.of());

            assertEquals(1, documents.size());
            assertEquals("doc_1", documents.getFirst().getId());
            assertTrue(documents.getFirst().getText().contains("test"));
            assertTrue(documents.getFirst().getText().contains("123"));
        }

        @Test
        @DisplayName("test_parse_json_empty_object")
        void testParseJsonEmptyObject() throws Exception {
            Path file = tempDir.resolve("empty.json");
            Files.writeString(file, "{}");

            List<Document> documents = new JsonParser().parse(file.toString(), "doc_empty", null, Map.of());

            assertEquals(1, documents.size());
            assertEquals("{}", documents.getFirst().getText());
        }

        @Test
        @DisplayName("test_parse_json_array")
        void testParseJsonArray() throws Exception {
            Path file = tempDir.resolve("array.json");
            Files.writeString(file, "[1,{\"name\":\"test\"}]");

            List<Document> documents = new JsonParser().parse(file.toString(), "doc_array", null, Map.of());

            assertEquals(1, documents.size());
            assertTrue(documents.getFirst().getText().contains("1"));
            assertTrue(documents.getFirst().getText().contains("test"));
        }

        @Test
        @DisplayName("test_parse_json_invalid_format")
        void testParseJsonInvalidFormat() throws Exception {
            Path file = tempDir.resolve("invalid.json");
            Files.writeString(file, "invalid json");

            List<Document> documents = new JsonParser().parse(file.toString(), "doc_invalid", null, Map.of());

            assertEquals(1, documents.size());
            assertTrue(documents.getFirst().getText().contains("invalid json"));
        }

        @Test
        @DisplayName("test_parse_json_file_not_found")
        void testParseJsonFileNotFound() {
            List<Document> documents = new JsonParser().parse(
                    tempDir.resolve("missing.json").toString(), "missing", null, Map.of());

            assertTrue(documents.isEmpty());
        }

        @Test
        @DisplayName("test_parse_json_with_exception")
        void testParseJsonWithException() throws Exception {
            Path directory = tempDir.resolve("directory.json");
            Files.createDirectory(directory);

            List<Document> documents = new JsonParser().parse(directory.toString(), "dir", null, Map.of());

            assertTrue(documents.isEmpty());
        }

        @Test
        @DisplayName("test_parse_json_with_unicode")
        void testParseJsonWithUnicode() throws Exception {
            Path file = tempDir.resolve("unicode.json");
            Files.writeString(file, "{\"message\":\"\\u6d4b\\u8bd5\"}");

            List<Document> documents = new JsonParser().parse(file.toString(), "doc_unicode", null, Map.of());

            assertEquals(1, documents.size());
            assertTrue(documents.getFirst().getText().contains("\u6d4b\u8bd5"));
        }

        @Test
        @DisplayName("test_parse_json_formatted_output")
        void testParseJsonFormattedOutput() throws Exception {
            Path file = tempDir.resolve("formatted.json");
            Files.writeString(file, "{\"outer\":{\"inner\":1}}");

            String parsedText = new JsonParser().parse(file.toString(), "doc_formatted", null, Map.of())
                    .getFirst().getText();

            assertTrue(parsedText.contains("\n") || parsedText.contains("  "));
        }
    }
}
