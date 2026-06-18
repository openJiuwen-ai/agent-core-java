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
 * Mirrors Python's {@code WebPageParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.
 *
 * <p>Focused tests also mirror Python's {@code test_web_page_parser.py} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_web_page_parser.py}.</p>
 */
class WebPageParserTest {

    @Test
    void testHttpUrlPattern() {
        assertThat(WebPageParser.HTTP_URL_PATTERN.matcher("https://example.com/path").matches()).isTrue();
        assertThat(WebPageParser.HTTP_URL_PATTERN.matcher("HTTP://X.Y/z").matches()).isTrue();
        assertThat(WebPageParser.HTTP_URL_PATTERN.matcher("ftp://example.com").matches()).isFalse();
        assertThat(WebPageParser.HTTP_URL_PATTERN.matcher("not-a-url").matches()).isFalse();
    }

    @Test
    void testWechatMpUrlPattern() {
        assertThat(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("https://mp.weixin.qq.com/s/abc123").matches()).isTrue();
        assertThat(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("http://foo.weixin.qq.com/s?x=1").matches()).isTrue();
        assertThat(WebPageParser.WECHAT_MP_URL_PATTERN.matcher("https://example.com/article").matches()).isFalse();
    }

    @Test
    void testSupportsHttpUrlNotWechat() {
        WebPageParser parser = new WebPageParser();

        assertThat(parser.supports("https://example.com/article")).isTrue();
        assertThat(parser.supports("http://blog.google/foo")).isTrue();
    }

    @Test
    void testSupportsWechatUrlFalse() {
        WebPageParser parser = new WebPageParser();

        assertThat(parser.supports("https://mp.weixin.qq.com/s/abc")).isFalse();
    }

    @Test
    void testSupportsNonUrlFalse() {
        WebPageParser parser = new WebPageParser();

        assertThat(parser.supports("")).isFalse();
        assertThat(parser.supports("not-a-url")).isFalse();
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
        WebPageParser parser = new StubWebPageParser(html);

        List<Document> documents = parser.parse(url, "doc_1", null, Map.of()).join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getId_()).isEqualTo("doc_1");
        assertThat(documents.getFirst().getMetadata())
                .containsEntry("source_url", url)
                .containsEntry("title", "Test Page Title")
                .containsEntry("source_type", "web_page");
        assertThat(documents.getFirst().getText()).contains("main article content");
    }

    @Test
    void testParseDefaultsDocumentIdToUrlAndUsesOptions() {
        String url = "https://example.com/options";
        WebPageParser parser = new StubWebPageParser(minimalHtml("Body ".repeat(40)));

        List<Document> documents = parser.parse(url, "", null,
                Map.of("timeout", 5.5d, "user_agent", "CustomAgent")).join();

        assertThat(documents.getFirst().getId_()).isEqualTo(url);
        assertThat(((StubWebPageParser) parser).lastTimeout).isEqualTo(5.5d);
        assertThat(((StubWebPageParser) parser).lastUserAgent).isEqualTo("CustomAgent");
    }

    @Test
    void testParseWechatUrlRaises() {
        WebPageParser parser = new WebPageParser();

        assertThatThrownBy(() -> parser.parse("https://mp.weixin.qq.com/s/abc", "id1", null, Map.of()))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR);
    }

    @Test
    void testInvalidUrlRaisesFetchError() {
        assertThatThrownBy(() -> WebPageParser.validateUrl("not-a-url"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Not a valid HTTP URL");
    }

    @Test
    void testDownloadFailureCompletesWithFetchError() {
        WebPageParser parser = new FailingWebPageParser();

        assertThatThrownBy(() -> parser.parse("https://example.com/fails", "id", null, Map.of()).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BaseError.class);
    }

    /**
     * Mirrors Python's patched {@code httpx.AsyncClient} for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.
     */
    private static class StubWebPageParser extends WebPageParser {
        private final String html;
        private double lastTimeout;
        private String lastUserAgent;

        private StubWebPageParser(String html) {
            this.html = html;
        }

        @Override
        protected CompletableFuture<String> downloadHtml(String url, double requestTimeout, String requestUserAgent) {
            this.lastTimeout = requestTimeout;
            this.lastUserAgent = requestUserAgent;
            return CompletableFuture.completedFuture(html);
        }
    }

    /**
     * Mirrors Python's fetch failure branch for
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.
     */
    private static final class FailingWebPageParser extends WebPageParser {
        @Override
        protected CompletableFuture<String> downloadHtml(String url, double requestTimeout, String requestUserAgent) {
            return CompletableFuture.failedFuture(new BaseError(StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
                    Map.of("error_msg", "boom")));
        }
    }

    private static String minimalHtml(String text) {
        return """
                <!DOCTYPE html><html><head><title>Title</title></head>
                <body><article><p>%s</p></article></body></html>
                """.formatted(text);
    }
}
