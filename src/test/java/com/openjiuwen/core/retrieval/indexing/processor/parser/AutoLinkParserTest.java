/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_auto_link_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class AutoLinkParserTest {

    @Test
    void testSupportsWechatUrl() {
        AutoLinkParser parser = new AutoLinkParser();

        assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc123"));
        assertTrue(parser.supports("http://mp.weixin.qq.com/s/xyz"));
    }

    @Test
    void testSupportsGenericHttpUrl() {
        AutoLinkParser parser = new AutoLinkParser();

        assertTrue(parser.supports("https://example.com/article"));
        assertTrue(parser.supports("http://blog.google/foo"));
    }

    @Test
    void testSupportsNonHttpFalse() {
        AutoLinkParser parser = new AutoLinkParser();

        assertFalse(parser.supports("ftp://example.com"));
        assertFalse(parser.supports("/local/path/file.txt"));
        assertFalse(parser.supports("not-a-url"));
    }

    @Test
    void testSupportsEmptyOrNone() {
        AutoLinkParser parser = new AutoLinkParser();

        assertFalse(parser.supports(""));
        assertFalse(parser.supports("   "));
        assertFalse(parser.supports(null));
    }

    @Test
    void testParseDelegatesToFirstMatchingRoute() {
        Document doc = new Document("d1", "parsed", Map.of());
        RecordingParser mockParser = new RecordingParser(List.of(doc));
        RecordingParser fallbackParser = new RecordingParser(List.of());
        AutoLinkParser router = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(url -> Pattern.compile("^https?://wechat\\.com/").matcher(url).find(), mockParser),
                new AutoLinkParser.Route(AutoLinkParser.HTTP_URL_PATTERN.asMatchPredicate(), fallbackParser)));

        List<Document> result = router.parse("https://wechat.com/article", "id1", null, Map.of());

        assertEquals(List.of(doc), result);
        assertEquals("https://wechat.com/article", mockParser.lastDoc);
        assertEquals("id1", mockParser.lastDocId);
        assertEquals(0, fallbackParser.parseCount);
    }

    @Test
    void testParseSecondRouteWhenFirstNoMatch() {
        Document doc = new Document("d2", "web", Map.of());
        RecordingParser wechatParser = new RecordingParser(List.of());
        RecordingParser webParser = new RecordingParser(List.of(doc));
        AutoLinkParser router = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(url -> Pattern.compile("^https?://wechat\\.com/").matcher(url).find(), wechatParser),
                new AutoLinkParser.Route(AutoLinkParser.HTTP_URL_PATTERN.asMatchPredicate(), webParser)));

        List<Document> result = router.parse("https://example.com/page", "id2", null, Map.of());

        assertEquals(List.of(doc), result);
        assertEquals("https://example.com/page", webParser.lastDoc);
        assertEquals("id2", webParser.lastDocId);
        assertEquals(0, wechatParser.parseCount);
    }

    @Test
    void testParseNoMatchReturnsEmpty() {
        AutoLinkParser router = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(url -> Pattern.compile("^https?://only\\.this/").matcher(url).find(),
                        new RecordingParser(List.of()))));

        List<Document> result = router.parse("https://other.com/page", "id3", null, Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void testCustomCallableRoute() {
        AutoLinkParser router = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(url -> url != null && url.length() % 2 == 1,
                        new RecordingParser(List.of()))));

        assertEquals(10, "http://a.b".length());
        assertEquals(11, "http://a.bc".length());
        assertFalse(router.supports("http://a.b"));
        assertTrue(router.supports("http://a.bc"));
        assertFalse(router.supports("not-an-http-url"));
    }

    private static final class RecordingParser extends Parser {

        private final List<Document> result;
        private String lastDoc;
        private String lastDocId;
        private int parseCount;

        private RecordingParser(List<Document> result) {
            this.result = result;
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
