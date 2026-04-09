/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFileParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parseExistingTextFile() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "Hello World", StandardCharsets.UTF_8);

        TextFileParser parser = new TextFileParser();
        List<Document> docs = parser.parse(file.toString(), "doc-1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("Hello World", docs.get(0).getText());
    }

    @Test
    void parseMissingFileThrows() {
        TextFileParser parser = new TextFileParser();
        assertThrows(Exception.class,
                () -> parser.parse("/nonexistent/file.txt", "doc-1", null, Map.of()));
    }

    @Test
    void supportsTxtAndMdExtensions() {
        TextFileParser parser = new TextFileParser();
        assertTrue(parser.supports("readme.txt"));
        assertTrue(parser.supports("guide.md"));
        assertTrue(parser.supports("FILE.TXT"));
        assertTrue(parser.supports("DOC.MD"));
        assertFalse(parser.supports("image.png"));
        assertFalse(parser.supports("data.csv"));
    }

    @Test
    void parseUtf8Content() throws IOException {
        Path file = tempDir.resolve("unicode.txt");
        Files.writeString(file, "中文内容 日本語 한국어", StandardCharsets.UTF_8);

        TextFileParser parser = new TextFileParser();
        List<Document> docs = parser.parse(file.toString(), "doc-utf8", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("中文内容 日本語 한국어", docs.get(0).getText());
    }

    @Test
    void parseReturnsDocumentWithGivenDocId() throws IOException {
        Path file = tempDir.resolve("test.md");
        Files.writeString(file, "# Title\nContent", StandardCharsets.UTF_8);

        TextFileParser parser = new TextFileParser();
        List<Document> docs = parser.parse(file.toString(), "my-doc-id", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("my-doc-id", docs.get(0).getId());
    }
}
