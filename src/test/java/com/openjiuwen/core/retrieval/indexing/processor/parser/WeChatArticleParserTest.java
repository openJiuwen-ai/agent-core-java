/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestWeChatArticleParser} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_wechat_article_parser.py}.
 *
 * <p>Focused tests also cover Python's {@code WeChatArticleParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.</p>
 */
class WeChatArticleParserTest {

    @Test
    void testSupportsWechatUrl() {
        WeChatArticleParser parser = new WeChatArticleParser();

        assertThat(parser.supports("https://mp.weixin.qq.com/s/abc123")).isTrue();
        assertThat(parser.supports("http://mp.weixin.qq.com/s/xyz")).isTrue();
    }

    @Test
    void testSupportsNonWechatUrl() {
        WeChatArticleParser parser = new WeChatArticleParser();

        assertThat(parser.supports("https://example.com/page")).isFalse();
        assertThat(parser.supports("")).isFalse();
    }

    @Test
    void testIsWechatArticleUrl() {
        assertThat(WeChatArticleParser.isWechatArticleUrl("https://mp.weixin.qq.com/s/abc")).isTrue();
        assertThat(WeChatArticleParser.isWechatArticleUrl("https://other.com")).isFalse();
    }

    @Test
    void testParseReturnsDocumentWithMetadata() {
        String html = """
                <!DOCTYPE html><html><head>
                <meta property="og:title" content="Test WeChat Title"/>
                <title>Fallback Title</title>
                </head><body><div id="js_content"><p>Article body text here.</p></div></body></html>
                """;
        String url = "https://mp.weixin.qq.com/s/abc123";
        StubWeChatArticleParser parser = new StubWeChatArticleParser(html);

        List<Document> documents = parser.parse(url, "doc_1", null,
                Map.of("timeout", 7.5d, "user_agent", "CustomAgent")).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getId_()).isEqualTo("doc_1");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("source_url", url)
                .containsEntry("title", "Test WeChat Title")
                .containsEntry("source_type", "wechat_article");
        assertThat(documents.get(0).getText()).contains("Article body text here");
        assertThat(parser.lastUrl).isEqualTo(url);
        assertThat(parser.lastTimeout).isEqualTo(7.5d);
        assertThat(parser.lastUserAgent).isEqualTo("CustomAgent");
    }

    @Test
    void testParseNotWechatUrlRaises() {
        WeChatArticleParser parser = new WeChatArticleParser();

        assertThatThrownBy(() -> parser.parse("https://example.com/page", "id1", null, Map.of()))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Not a WeChat article URL")
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR);
    }

    @Test
    void testParseFailsWhenJsContentMissing() {
        String html = """
                <!DOCTYPE html><html><head><title>Fallback Title</title></head>
                <body><article>Plain article body</article></body></html>
                """;
        WeChatArticleParser parser = new StubWeChatArticleParser(html);

        assertThatThrownBy(() -> parser.parse("https://mp.weixin.qq.com/s/abc123", "doc", null, Map.of()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BaseError.class)
                .hasRootCauseMessage("Could not find article content (js_content) in page (source: "
                        + "https://mp.weixin.qq.com/s/abc123)");
    }

    /**
     * Mirrors Python's patched {@code httpx.AsyncClient} for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.
     */
    private static final class StubWeChatArticleParser extends WeChatArticleParser {
        private final String html;
        private String lastUrl;
        private double lastTimeout;
        private String lastUserAgent;

        private StubWeChatArticleParser(String html) {
            this.html = html;
        }

        @Override
        protected CompletableFuture<String> downloadHtml(String url, double requestTimeout, String requestUserAgent) {
            this.lastUrl = url;
            this.lastTimeout = requestTimeout;
            this.lastUserAgent = requestUserAgent;
            return CompletableFuture.completedFuture(html);
        }
    }
}
