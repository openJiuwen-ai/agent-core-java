/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_web_page_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class WebPageParserTest {

    @Test
    void testHttpUrlPattern() {
        assertTrue(WebPageParser.HTTP_URL_PATTERN.matcher("https://example.com/path").matches());
        assertTrue(WebPageParser.HTTP_URL_PATTERN.matcher("HTTP://X.Y/z").matches());
        assertFalse(WebPageParser.HTTP_URL_PATTERN.matcher("ftp://example.com").matches());
        assertFalse(WebPageParser.HTTP_URL_PATTERN.matcher("not-a-url").matches());
    }

    @Test
    void testWechatMpUrlPattern() {
        assertTrue(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("https://mp.weixin.qq.com/s/abc123").matches());
        assertTrue(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("http://foo.weixin.qq.com/s?x=1").matches());
        assertFalse(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("https://example.com/article").matches());
    }

    @Test
    void testSupportsHttpUrlNotWechat() {
        WebPageParser parser = new WebPageParser();

        assertTrue(parser.supports("https://example.com/article"));
        assertTrue(parser.supports("http://blog.google/foo"));
    }

    @Test
    void testSupportsWechatUrlFalse() {
        WebPageParser parser = new WebPageParser();

        assertFalse(parser.supports("https://mp.weixin.qq.com/s/abc"));
    }

    @Test
    void testSupportsNonUrlFalse() {
        WebPageParser parser = new WebPageParser();

        assertFalse(parser.supports(""));
        assertFalse(parser.supports("not-a-url"));
    }

    @Test
    void testParseReturnsDocumentWithMetadata() {
        String bodyText = "This is the main article content. ".repeat(10);
        String html = """
                <!DOCTYPE html><html><head>
                <meta property="og:title" content="Test Page Title"/>
                <title>Fallback</title>
                </head><body><article><p>%s</p></article></body></html>
                """.formatted(bodyText);
        String url = "https://example.com/page";
        TestWebPageParser parser = new TestWebPageParser(html);

        List<Document> docs = parser.parse(url, "doc_1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("doc_1", docs.getFirst().getId());
        assertEquals(url, docs.getFirst().getMetadata().get("source_url"));
        assertEquals("Test Page Title", docs.getFirst().getMetadata().get("title"));
        assertEquals("web_page", docs.getFirst().getMetadata().get("source_type"));
        assertTrue(docs.getFirst().getText().contains("main article content"));
    }

    @Test
    void testParseWechatUrlRaises() {
        WebPageParser parser = new WebPageParser();

        assertThrows(BaseError.class, () -> parser.parse("https://mp.weixin.qq.com/s/abc", "id1", null, Map.of()));
    }

    private static final class TestWebPageParser extends WebPageParser {

        private final String html;

        private TestWebPageParser(String html) {
            this.html = html;
        }

        @Override
        protected String fetchHtml(String url) {
            return html;
        }
    }
}
