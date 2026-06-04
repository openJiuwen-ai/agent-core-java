/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_auto_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class AutoParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testSupportsHttpUrl() {
        AutoParser parser = new AutoParser();

        assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc"));
        assertTrue(parser.supports("https://example.com/page"));
    }

    @Test
    void testSupportsFilePath() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "content", StandardCharsets.UTF_8);

        AutoParser parser = new AutoParser();

        assertTrue(parser.supports(file.toString()));
    }

    @Test
    void testSupportsNonUrlNonFile() {
        AutoParser parser = new AutoParser();

        assertFalse(parser.supports("not-a-url"));
        assertFalse(parser.supports(tempDir.resolve("nonexistent.txt").toString()));
    }

    @Test
    void testParseUrlDelegatesToLinkParser() {
        Document doc = new Document("link_doc", "from link", Map.of());
        RecordingParser linkParser = new RecordingParser(value -> value.startsWith("http"), List.of(doc));
        RecordingParser fileParser = new RecordingParser(value -> false, List.of());
        AutoParser parser = new AutoParser(linkParser, fileParser);

        List<Document> result = parser.parse("https://example.com/page", "id1", null, Map.of());

        assertEquals(List.of(doc), result);
        assertEquals("https://example.com/page", linkParser.lastDoc);
        assertEquals("id1", linkParser.lastDocId);
        assertEquals(0, fileParser.parseCount);
    }

    @Test
    void testParseFileDelegatesToFileParser() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "content", StandardCharsets.UTF_8);
        Document doc = new Document("file_doc", "from file", Map.of());
        RecordingParser linkParser = new RecordingParser(value -> false, List.of());
        RecordingParser fileParser = new RecordingParser(value -> Files.exists(Path.of(value)) && value.endsWith(".txt"), List.of(doc));
        AutoParser parser = new AutoParser(linkParser, fileParser);

        List<Document> result = parser.parse(file.toString(), "id2", null, Map.of());

        assertEquals(List.of(doc), result);
        assertEquals(file.toString(), fileParser.lastDoc);
        assertEquals("id2", fileParser.lastDocId);
        assertEquals(0, linkParser.parseCount);
    }

    @Test
    void testParseUnsupportedReturnsEmpty() {
        RecordingParser linkParser = new RecordingParser(value -> false, List.of());
        RecordingParser fileParser = new RecordingParser(value -> false, List.of());
        AutoParser parser = new AutoParser(linkParser, fileParser);

        assertTrue(parser.parse("https://example.com", "id3", null, Map.of()).isEmpty());
        assertTrue(parser.parse("/nonexistent.xyz", "id4", null, Map.of()).isEmpty());
        assertEquals(0, linkParser.parseCount);
        assertEquals(0, fileParser.parseCount);
    }

    private static final class RecordingParser extends Parser {

        private final java.util.function.Predicate<String> supported;
        private final List<Document> result;
        private String lastDoc;
        private String lastDocId;
        private int parseCount;

        private RecordingParser(java.util.function.Predicate<String> supported, List<Document> result) {
            this.supported = supported;
            this.result = result;
        }

        @Override
        public boolean supports(String doc) {
            return supported.test(doc);
        }

        @Override
        public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
            this.lastDoc = doc;
            this.lastDocId = docId;
            this.parseCount++;
            return result;
        }

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }
    }
}
