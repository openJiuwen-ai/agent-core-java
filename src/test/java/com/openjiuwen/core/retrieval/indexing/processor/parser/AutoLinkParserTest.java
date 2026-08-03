/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for AutoLinkParser URL routing.
 *
 * <p>Mirrors Python's {@code AutoLinkParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_link_parser.py} and the Python test module
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_auto_link_parser.py}.</p>
 */
class AutoLinkParserTest {

    @Test
    void supportsWechatUrl() {
        AutoLinkParser parser = new AutoLinkParser();

        assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc123"));
        assertTrue(parser.supports("http://mp.weixin.qq.com/s/xyz"));
    }

    @Test
    void supportsGenericHttpUrl() {
        AutoLinkParser parser = new AutoLinkParser();

        assertTrue(parser.supports("https://example.com/article"));
        assertTrue(parser.supports("http://blog.google/foo"));
    }

    @Test
    void supportsNonHttpFalse() {
        AutoLinkParser parser = new AutoLinkParser();

        assertFalse(parser.supports("ftp://example.com"));
        assertFalse(parser.supports("/local/path/file.txt"));
        assertFalse(parser.supports("not-a-url"));
    }

    @Test
    void supportsEmptyOrNoneFalse() {
        AutoLinkParser parser = new AutoLinkParser();

        assertFalse(parser.supports(""));
        assertFalse(parser.supports("   "));
        assertFalse(parser.supports(null));
    }

    @Test
    void parseDelegatesToFirstMatchingRoute() {
        Document document = new Document("d1", "parsed", Map.of());
        RecordingParser matchingParser = new RecordingParser(List.of(document));
        RecordingParser fallbackParser = new RecordingParser(List.of());
        AutoLinkParser parser = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(Pattern.compile("^https?://wechat\\.com/"), matchingParser),
                new AutoLinkParser.Route(AutoLinkParser.HTTP_URL_PATTERN, fallbackParser)));
        Map<String, Object> options = Map.of("timeout", 3);

        List<Document> result = parser.parse("https://wechat.com/article", "id1", null, options).join();

        assertEquals(List.of(document), result);
        assertEquals("https://wechat.com/article", matchingParser.lastDoc);
        assertEquals("id1", matchingParser.lastDocId);
        assertEquals(options, matchingParser.lastOptions);
        assertEquals(0, fallbackParser.parseCount);
    }

    @Test
    void parseSecondRouteWhenFirstNoMatch() {
        Document document = new Document("d2", "web", Map.of());
        RecordingParser wechatParser = new RecordingParser(List.of());
        RecordingParser webParser = new RecordingParser(List.of(document));
        AutoLinkParser parser = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(Pattern.compile("^https?://wechat\\.com/"), wechatParser),
                new AutoLinkParser.Route(AutoLinkParser.HTTP_URL_PATTERN, webParser)));

        List<Document> result = parser.parse("https://example.com/page", "id2", null, Map.of()).join();

        assertEquals(List.of(document), result);
        assertEquals("https://example.com/page", webParser.lastDoc);
        assertEquals("id2", webParser.lastDocId);
        assertEquals(0, wechatParser.parseCount);
    }

    @Test
    void parseNoMatchReturnsEmpty() {
        AutoLinkParser parser = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(
                        Pattern.compile("^https?://only\\.this/"),
                        new RecordingParser(List.of()))));

        List<Document> result = parser.parse("https://other.com/page", "id3", null, Map.of()).join();

        assertTrue(result.isEmpty());
    }

    @Test
    void customCallableRouteControlsSupportsAfterHttpPrecheck() {
        AutoLinkParser parser = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(value -> value != null && value.length() % 2 == 1, null)));

        assertEquals(10, "http://a.b".length());
        assertEquals(11, "http://a.bc".length());
        assertFalse(parser.supports("http://a.b"));
        assertTrue(parser.supports("http://a.bc"));
        assertFalse(parser.supports("not-an-http-url"));
    }

    @Test
    void patternRoutesUsePythonMatchSemantics() {
        AutoLinkParser parser = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(AutoLinkParser.HTTP_URL_PATTERN, new RecordingParser(List.of()))));

        assertTrue(parser.supports("  https://example.com/page with trailing text"));
    }

    /**
     * Records delegated parser calls for route-order assertions.
     *
     * <p>Mirrors Python's mock parser collaborators used by tests for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_link_parser.py}.</p>
     */
    private static final class RecordingParser extends Parser {
        private final List<Document> result;
        private String lastDoc;
        private String lastDocId;
        private Map<String, Object> lastOptions;
        private int parseCount;

        private RecordingParser(List<Document> result) {
            this.result = result;
        }

        @Override
        public CompletableFuture<List<Document>> parseAsync(
                String doc,
                String docId,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            this.lastDoc = doc;
            this.lastDocId = docId;
            this.lastOptions = options;
            this.parseCount++;
            return CompletableFuture.completedFuture(result);
        }
    }
}
