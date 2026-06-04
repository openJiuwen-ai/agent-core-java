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
 * Mirrors Python's {@code test_txt_md_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class TxtMdParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testInit() {
        TxtMdParser parser = new TxtMdParser();

        assertNotNull(parser);
    }

    @Test
    void testParseEmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        TxtMdParser parser = new TxtMdParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParseFileNotFound() {
        TxtMdParser parser = new TxtMdParser();

        List<Document> documents = parser.parse(tempDir.resolve("nonexistent.txt").toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    @Test
    void testParseStripsContent() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "   \n  Content  \n   ", StandardCharsets.UTF_8);

        TxtMdParser parser = new TxtMdParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals("Content", documents.getFirst().getText());
    }
}
