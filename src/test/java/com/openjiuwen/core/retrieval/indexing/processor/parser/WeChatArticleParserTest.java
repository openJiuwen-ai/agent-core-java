/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
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

class WeChatArticleParserTest {

    @Test
    void supportsOnlyWechatArticleUrls() {
        WeChatArticleParser parser = new WeChatArticleParser();

        assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc123"));
        assertFalse(parser.supports("https://example.com/page"));
    }

    @Test
    void parseReturnsWechatMetadata() {
        String html = """
                <html><head>
                <meta property="og:title" content="Test WeChat Title"/>
                </head><body><div id="js_content"><p>Article body text here.</p></div></body></html>
                """;
        TestWechatParser parser = new TestWechatParser(html);

        List<Document> docs = parser.parse("https://mp.weixin.qq.com/s/abc123", "doc-1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("Test WeChat Title", docs.getFirst().getMetadata().get("title"));
        assertEquals("wechat_article", docs.getFirst().getMetadata().get("source_type"));
        assertTrue(docs.getFirst().getText().contains("Article body text here"));
    }

    @Test
    void parseNonWechatUrlRaises() {
        WeChatArticleParser parser = new WeChatArticleParser();

        assertThrows(BaseError.class, () -> parser.parse("https://example.com/page", "doc-1", null, Map.of()));
    }

    private static final class TestWechatParser extends WeChatArticleParser {

        private final String html;

        private TestWechatParser(String html) {
            this.html = html;
        }

        @Override
        protected String fetchHtml(String url) {
            return html;
        }
    }
}
