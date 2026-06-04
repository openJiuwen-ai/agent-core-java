/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.AutoParser;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AutoParser.
 *
 * <p>Mirrors Python's {@code TestAutoParser} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_auto_parser}.</p>
 */
class TestAutoParser {

    @TempDir
    Path tempDir;

    @Nested
    @DisplayName("AutoParser tests")
    class AutoParserTests {

        @Test
        @DisplayName("test_supports_http_url")
        void testSupportsHttpUrl() {
            AutoParser parser = new AutoParser();

            assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc"));
            assertTrue(parser.supports("https://example.com/page"));
        }

        @Test
        @DisplayName("test_supports_file_path")
        void testSupportsFilePath() throws Exception {
            AutoParser parser = new AutoParser();
            Path textFile = tempDir.resolve("document.txt");
            Path jsonFile = tempDir.resolve("data.json");
            Files.writeString(textFile, "text");
            Files.writeString(jsonFile, "{}");

            assertTrue(parser.supports(textFile.toString()));
            assertTrue(parser.supports(jsonFile.toString()));
        }

        @Test
        @DisplayName("test_supports_non_url_non_file")
        void testSupportsNonUrlNonFile() {
            AutoParser parser = new AutoParser();

            assertFalse(parser.supports("not-a-url"));
            assertFalse(parser.supports(tempDir.resolve("missing.txt").toString()));
        }

        @Test
        @DisplayName("test_parse_url_delegates_to_link_parser")
        void testParseUrlDelegatesToLinkParser() {
            Document expected = new Document("url-doc", "from url", Map.of());
            RecordingParser linkParser = new RecordingParser(true, List.of(expected));
            RecordingParser fileParser = new RecordingParser(false, List.of());
            AutoParser parser = new AutoParser(linkParser, fileParser);

            List<Document> result = parser.parse("https://example.com/page", "url-doc", null, Map.of());

            assertEquals(1, result.size());
            assertSame(expected, result.getFirst());
            assertEquals(1, linkParser.parseCalls.get());
            assertEquals(0, fileParser.parseCalls.get());
        }

        @Test
        @DisplayName("test_parse_file_delegates_to_file_parser")
        void testParseFileDelegatesToFileParser() {
            Document expected = new Document("file-doc", "from file", Map.of());
            RecordingParser linkParser = new RecordingParser(false, List.of());
            RecordingParser fileParser = new RecordingParser(true, List.of(expected));
            AutoParser parser = new AutoParser(linkParser, fileParser);

            List<Document> result = parser.parse("document.txt", "file-doc", null, Map.of());

            assertEquals(1, result.size());
            assertSame(expected, result.getFirst());
            assertEquals(0, linkParser.parseCalls.get());
            assertEquals(1, fileParser.parseCalls.get());
        }

        @Test
        @DisplayName("test_parse_unsupported_returns_empty")
        void testParseUnsupportedReturnsEmpty() {
            AutoParser stubbed = new AutoParser(
                    new RecordingParser(false, List.of()),
                    new RecordingParser(false, List.of()));
            AutoParser real = new AutoParser();

            assertTrue(stubbed.parse("not-a-url", "doc", null, Map.of()).isEmpty());
            assertTrue(real.parse("not-a-url", "doc", null, Map.of()).isEmpty());
        }
    }

    private static final class RecordingParser extends Parser {
        private final boolean supports;
        private final List<Document> result;
        private final AtomicInteger parseCalls = new AtomicInteger();

        private RecordingParser(boolean supports, List<Document> result) {
            this.supports = supports;
            this.result = result;
        }

        @Override
        public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
            parseCalls.incrementAndGet();
            return result;
        }

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }

        @Override
        public boolean supports(String doc) {
            return supports;
        }
    }
}
