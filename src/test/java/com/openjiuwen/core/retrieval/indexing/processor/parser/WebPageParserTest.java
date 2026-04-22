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

class WebPageParserTest {

    @Test
    void supportsGenericHttpButRejectsWechat() {
        WebPageParser parser = new WebPageParser();

        assertTrue(parser.supports("https://example.com/article"));
        assertFalse(parser.supports("https://mp.weixin.qq.com/s/abc"));
    }

    @Test
    void parseReturnsMetadataAndReadableText() {
        String html = """
                <html><head>
                <meta property="og:title" content="Test Page Title"/>
                <title>Fallback</title>
                </head><body><article><p>Main article content.</p></article></body></html>
                """;
        TestWebPageParser parser = new TestWebPageParser(html);

        List<Document> docs = parser.parse("https://example.com/page", "doc-1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("Test Page Title", docs.get(0).getMetadata().get("title"));
        assertEquals("web_page", docs.get(0).getMetadata().get("source_type"));
        assertTrue(docs.get(0).getText().contains("Main article content"));
    }

    @Test
    void parseWechatUrlRaises() {
        WebPageParser parser = new WebPageParser();

        assertThrows(BaseError.class, () -> parser.parse("https://mp.weixin.qq.com/s/abc", "doc-1", null, Map.of()));
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
