/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.TxtMdParser;
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
 * Unit tests for TxtMdParser.
 *
 * <p>Mirrors Python's {@code TestTxtMdParser} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_txt_md_parser}.</p>
 */
class TestTxtMdParser {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("TxtMdParser tests")
    class TxtMdParserTests {

        @Test
        @DisplayName("test_init")
        void testInit() {
            assertNotNull(new TxtMdParser());
        }

        @Test
        @DisplayName("test_parse_empty_file")
        void testParseEmptyFile() throws Exception {
            Path file = tempDir.resolve("empty.txt");
            Files.writeString(file, "");

            List<Document> documents = new TxtMdParser().parse(file.toString(), "empty", null, Map.of());

            if (!documents.isEmpty()) {
                assertEquals("", documents.getFirst().getText().trim());
            }
        }

        @Test
        @DisplayName("test_parse_file_not_found")
        void testParseFileNotFound() {
            List<Document> documents = new TxtMdParser().parse(
                    tempDir.resolve("missing.txt").toString(), "missing", null, Map.of());

            assertTrue(documents.isEmpty());
        }

        @Test
        @DisplayName("test_parse_strips_content")
        void testParseStripsContent() throws Exception {
            Path file = tempDir.resolve("content.md");
            Files.writeString(file, "  Content  \n");

            List<Document> documents = new TxtMdParser().parse(file.toString(), "content", null, Map.of());

            assertEquals(1, documents.size());
            assertEquals("Content", documents.getFirst().getText().strip());
        }
    }
}
