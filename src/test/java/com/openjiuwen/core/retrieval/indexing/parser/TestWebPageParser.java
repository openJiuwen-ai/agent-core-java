/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser;
import com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for WebPageParser.
 *
 * <p>Mirrors Python's {@code TestWebPageParser} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_web_page_parser}.</p>
 */
class TestWebPageParser {

    @Nested
    @DisplayName("WebPageParser tests")
    class WebPageParserTests {

        @Test
        @DisplayName("test_http_url_pattern")
        void testHttpUrlPattern() {
            assertTrue(WebPageParser.HTTP_URL_PATTERN.matcher("https://example.com/path").matches());
            assertTrue(WebPageParser.HTTP_URL_PATTERN.matcher("HTTP://X.Y/z").matches());
            assertFalse(WebPageParser.HTTP_URL_PATTERN.matcher("ftp://example.com").matches());
            assertFalse(WebPageParser.HTTP_URL_PATTERN.matcher("not-a-url").matches());
        }

        @Test
        @DisplayName("test_wechat_mp_url_pattern")
        void testWechatMpUrlPattern() {
            assertTrue(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("https://mp.weixin.qq.com/s/abc123").matches());
            assertTrue(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("http://foo.weixin.qq.com/s?x=1").matches());
            assertFalse(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("https://example.com/article").matches());
            assertTrue(WeChatArticleParser.isWechatArticleUrl("https://mp.weixin.qq.com/s/abc123"));
        }

        @Test
        @DisplayName("test_supports_http_url_not_wechat")
        void testSupportsHttpUrlNotWechat() {
            WebPageParser parser = new WebPageParser();

            assertTrue(parser.supports("https://example.com/article"));
            assertTrue(parser.supports("http://blog.google/foo"));
        }

        @Test
        @DisplayName("test_supports_wechat_url_false")
        void testSupportsWechatUrlFalse() {
            assertFalse(new WebPageParser().supports("https://mp.weixin.qq.com/s/abc"));
        }

        @Test
        @DisplayName("test_supports_non_url_false")
        void testSupportsNonUrlFalse() {
            WebPageParser parser = new WebPageParser();

            assertFalse(parser.supports(""));
            assertFalse(parser.supports("not-a-url"));
        }

        @Test
        @DisplayName("test_parse_returns_document_with_metadata")
        void testParseReturnsDocumentWithMetadata() {
            String url = "https://example.com/article";
            StubWebPageParser parser = new StubWebPageParser("""
                    <html>
                      <head><meta property="og:title" content="Test Page Title"></head>
                      <body><article>main article content</article></body>
                    </html>
                    """);

            List<Document> docs = parser.parse(url, "doc_1", null, Map.of());

            assertEquals(url, parser.lastUrl);
            assertEquals(1, docs.size());
            assertEquals("doc_1", docs.getFirst().getId());
            assertEquals(url, docs.getFirst().getMetadata().get("source_url"));
            assertEquals("Test Page Title", docs.getFirst().getMetadata().get("title"));
            assertEquals("web_page", docs.getFirst().getMetadata().get("source_type"));
            assertTrue(docs.getFirst().getText().contains("main article content"));
        }

        @Test
        @DisplayName("test_parse_wechat_url_raises")
        void testParseWechatUrlRaises() {
            BaseError error = assertThrows(BaseError.class, () -> new WebPageParser().parse(
                    "https://mp.weixin.qq.com/s/abc", "doc", null, Map.of()));

            assertTrue(error.getMessage().contains("Use WeChatArticleParser"));
        }
    }

    private static final class StubWebPageParser extends WebPageParser {
        private final String html;
        private String lastUrl;

        private StubWebPageParser(String html) {
            this.html = html;
        }

        @Override
        protected String fetchHtml(String url) {
            lastUrl = url;
            return html;
        }
    }
}
