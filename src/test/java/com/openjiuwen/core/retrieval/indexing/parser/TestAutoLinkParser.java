/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.AutoLinkParser;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AutoLinkParser.
 *
 * <p>Mirrors Python's {@code TestAutoLinkParser} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_auto_link_parser}.</p>
 */
class TestAutoLinkParser {

    @Nested
    @DisplayName("AutoLinkParser tests")
    @Tag("level0")
    class AutoLinkParserTests {

        @Test
        @DisplayName("test_supports_wechat_url")
        void testSupportsWeChatUrl() {
            AutoLinkParser parser = new AutoLinkParser();

            assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc123"));
            assertTrue(parser.supports("http://mp.weixin.qq.com/s/xyz"));
        }

        @Test
        @DisplayName("test_supports_generic_http_url")
        void testSupportsGenericHttpUrl() {
            AutoLinkParser parser = new AutoLinkParser();

            assertTrue(parser.supports("https://example.com/article"));
            assertTrue(parser.supports("http://blog.google/foo"));
        }

        @Test
        @DisplayName("test_supports_non_http_false")
        void testSupportsNonHttpFalse() {
            AutoLinkParser parser = new AutoLinkParser();

            assertFalse(parser.supports("ftp://example.com"));
            assertFalse(parser.supports("/local/path/file.txt"));
            assertFalse(parser.supports("not-a-url"));
        }

        @Test
        @DisplayName("test_supports_empty_or_none")
        void testSupportsEmptyOrNone() {
            AutoLinkParser parser = new AutoLinkParser();

            assertFalse(parser.supports(""));
            assertFalse(parser.supports("   "));
            assertFalse(parser.supports(null));
        }

        @Test
        @DisplayName("test_parse_delegates_to_first_matching_route")
        void testParseDelegatesToFirstMatchingRoute() {
            Document expected = new Document("doc", "first route", Map.of());
            RecordingParser first = new RecordingParser(List.of(expected));
            RecordingParser second = new RecordingParser(List.of(new Document("other", "second", Map.of())));
            AutoLinkParser parser = new AutoLinkParser(List.of(
                    new AutoLinkParser.Route(url -> true, first),
                    new AutoLinkParser.Route(url -> true, second)));

            List<Document> result = parser.parse("https://example.com/article", "doc", null, Map.of());

            assertEquals(1, result.size());
            assertSame(expected, result.getFirst());
            assertEquals(1, first.calls.get());
            assertEquals(0, second.calls.get());
        }

        @Test
        @DisplayName("test_parse_second_route_when_first_no_match")
        void testParseSecondRouteWhenFirstNoMatch() {
            Document expected = new Document("doc", "second route", Map.of());
            RecordingParser first = new RecordingParser(List.of(new Document("other", "first", Map.of())));
            RecordingParser second = new RecordingParser(List.of(expected));
            AutoLinkParser parser = new AutoLinkParser(List.of(
                    new AutoLinkParser.Route(url -> false, first),
                    new AutoLinkParser.Route(url -> true, second)));

            List<Document> result = parser.parse("https://example.com/article", "doc", null, Map.of());

            assertEquals(1, result.size());
            assertSame(expected, result.getFirst());
            assertEquals(0, first.calls.get());
            assertEquals(1, second.calls.get());
        }

        @Test
        @DisplayName("test_parse_no_match_returns_empty")
        void testParseNoMatchReturnsEmpty() {
            RecordingParser route = new RecordingParser(List.of(new Document("doc", "unused", Map.of())));
            AutoLinkParser parser = new AutoLinkParser(List.of(new AutoLinkParser.Route(url -> false, route)));

            assertTrue(parser.parse("https://example.com/article", "doc", null, Map.of()).isEmpty());
            assertEquals(0, route.calls.get());
        }

        @Test
        @DisplayName("test_custom_callable_route")
        void testCustomCallableRoute() {
            RecordingParser route = new RecordingParser(List.of(new Document("doc", "custom", Map.of())));
            AutoLinkParser parser = new AutoLinkParser(List.of(
                    new AutoLinkParser.Route(url -> url != null && url.length() > 10, route)));

            assertEquals(10, "http://a.b".length());
            assertEquals(11, "http://a.bc".length());
            assertFalse(parser.supports("http://a.b"));
            assertTrue(parser.supports("http://a.bc"));
        }
    }

    private static final class RecordingParser extends Parser {
        private final List<Document> result;
        private final AtomicInteger calls = new AtomicInteger();

        private RecordingParser(List<Document> result) {
            this.result = result;
        }

        @Override
        public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
            calls.incrementAndGet();
            return result;
        }

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }
    }
}
