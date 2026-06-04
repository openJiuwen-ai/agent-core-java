/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_json_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class JsonParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testInit() {
        JsonParser parser = new JsonParser();

        assertNotNull(parser);
    }

    @Test
    void testParseJsonSuccess() throws IOException {
        Path file = tempDir.resolve("sample.json");
        Files.writeString(file, "{\"name\":\"test\",\"value\":123,\"items\":[\"item1\",\"item2\"]}", StandardCharsets.UTF_8);

        JsonParser parser = new JsonParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals("doc_1", documents.getFirst().getId());
        assertTrue(documents.getFirst().getText().contains("test"));
        assertTrue(documents.getFirst().getText().contains("123"));
    }

    @Test
    void testParseJsonEmptyObject() throws IOException {
        Path file = tempDir.resolve("empty.json");
        Files.writeString(file, "{}", StandardCharsets.UTF_8);

        JsonParser parser = new JsonParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals("{}", documents.getFirst().getText());
    }

    @Test
    void testParseJsonArray() throws IOException {
        Path file = tempDir.resolve("array.json");
        Files.writeString(file, "[1,2,3,\"test\"]", StandardCharsets.UTF_8);

        JsonParser parser = new JsonParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertTrue(documents.getFirst().getText().contains("1"));
        assertTrue(documents.getFirst().getText().contains("test"));
    }

    @Test
    void testParseJsonInvalidFormat() throws IOException {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "{ invalid json }", StandardCharsets.UTF_8);

        JsonParser parser = new JsonParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertTrue(documents.getFirst().getText().contains("invalid json"));
    }

    @Test
    void testParseJsonFileNotFound() {
        JsonParser parser = new JsonParser();

        List<Document> documents = parser.parse(tempDir.resolve("nonexistent.json").toString(), "doc_1", null, Map.of());
        assertTrue(documents.isEmpty());
    }

    @Test
    void testParseJsonWithException() throws IOException {
        Path directory = tempDir.resolve("directory.json");
        Files.createDirectory(directory);

        JsonParser parser = new JsonParser();
        List<Document> documents = parser.parse(directory.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParseJsonWithUnicode() throws IOException {
        Path file = tempDir.resolve("unicode.json");
        Files.writeString(file, "{\"name\":\"测试\",\"description\":\"这是一个测试\"}", StandardCharsets.UTF_8);

        JsonParser parser = new JsonParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertTrue(documents.getFirst().getText().contains("测试"));
    }

    @Test
    void testParseJsonFormattedOutput() throws IOException {
        Path file = tempDir.resolve("formatted.json");
        Files.writeString(file, "{\"key\":\"value\",\"nested\":{\"inner\":\"data\"}}", StandardCharsets.UTF_8);

        JsonParser parser = new JsonParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        String parsedText = documents.getFirst().getText();
        assertTrue(parsedText.contains("\n") || parsedText.contains("  "));
    }
}
