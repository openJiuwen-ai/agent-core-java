/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WeChat article parser test cases.
 * <p>
 * Mirrors Python's {@code test_wechat_article_parser} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 * </p>
 */
@DisplayName("TestWechatArticleParser")
class TestWechatArticleParser {

    private WeChatArticleParser parser;

    @BeforeEach
    void setUp() {
        parser = new WeChatArticleParser();
    }

    @Nested
    @DisplayName("URL support tests")
    class UrlSupportTests {

        @Test
        @DisplayName("supports() is True for WeChat article URL")
        void testSupportsWechatUrl() {
            // Mirrors Python: test_supports_wechat_url
            assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc123"),
                    "Should support WeChat article URL with https");
            assertTrue(parser.supports("http://mp.weixin.qq.com/s/xyz"),
                    "Should support WeChat article URL with http");
        }

        @Test
        @DisplayName("supports() is False for non-WeChat URL")
        void testSupportsNonWechatUrl() {
            // Mirrors Python: test_supports_non_wechat_url
            assertFalse(parser.supports("https://example.com/page"),
                    "Should not support non-WeChat URL");
            assertFalse(parser.supports(""),
                    "Should not support empty URL");
            assertFalse(parser.supports(null),
                    "Should not support null URL");
        }

        @Test
        @DisplayName("isWechatArticleUrl() matches mp.weixin.qq.com/s/...")
        void testIsWechatArticleUrl() {
            // Mirrors Python: test_is_wechat_article_url
            assertTrue(WeChatArticleParser.isWechatArticleUrl("https://mp.weixin.qq.com/s/abc"),
                    "Should match WeChat article URL pattern");
            assertFalse(WeChatArticleParser.isWechatArticleUrl("https://other.com"),
                    "Should not match non-WeChat URL");
        }
    }

    @Nested
    @DisplayName("Parse tests")
    class ParseTests {

        @Test
        @DisplayName("parse() raises for non-WeChat URL")
        void testParseNotWechatUrlRaises() {
            // Mirrors Python: test_parse_not_wechat_url_raises
            BaseError error = assertThrows(BaseError.class,
                    () -> parser.parse("https://example.com/page", "id1", null, null),
                    "Should throw BaseError for non-WeChat URL");

            assertEquals("Not a WeChat article URL", error.getMessage(),
                    "Should preserve the Python error message");
        }

        @Test
        @DisplayName("parse() returns Document with metadata")
        void testParseReturnsDocumentWithMetadata() throws Exception {
            // Mirrors Python: test_parse_returns_document_with_metadata
            String html = """
                    <!DOCTYPE html><html><head>
                    <meta property="og:title" content="Test WeChat Title"/>
                    <title>Fallback Title</title>
                    </head><body><div id="js_content"><p>Article body text here.</p></div></body></html>
                    """;
            String url = "https://mp.weixin.qq.com/s/abc123";
            HttpClient httpClient = mock(HttpClient.class);
            HttpResponse<String> response = mockResponse(html);
            when(httpClient.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(response);

            WeChatArticleParser parserWithStubClient = new WeChatArticleParser(httpClient);
            List<Document> docs = parserWithStubClient.parse(url, "doc_1", null, null);

            assertEquals(1, docs.size(), "Should return one Document");
            Document document = docs.get(0);
            assertEquals("doc_1", document.getId(), "Should preserve doc_id");
            assertEquals(url, document.getMetadata().get("source_url"),
                    "Should include source_url metadata");
            assertEquals("Test WeChat Title", document.getMetadata().get("title"),
                    "Should prefer og:title metadata");
            assertEquals("wechat_article", document.getMetadata().get("source_type"),
                    "Should mark WeChat source type");
            assertTrue(document.getText().contains("Article body text here"),
                    "Should extract article body text");

            ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(httpClient).send(requestCaptor.capture(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
            assertEquals(url, requestCaptor.getValue().uri().toString(),
                    "Should fetch the requested WeChat URL");
        }
    }

    @Nested
    @DisplayName("Parser structure tests")
    class ParserStructureTests {

        @Test
        @DisplayName("WeChatArticleParser extends WebPageParser")
        void testParserExtendsWebPageParser() {
            assertTrue(com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser.class
                    .isAssignableFrom(WeChatArticleParser.class),
                    "WeChatArticleParser should extend WebPageParser");
        }

        @Test
        @DisplayName("WeChatArticleParser has supports method")
        void testParserHasSupportsMethod() throws NoSuchMethodException {
            assertNotNull(WeChatArticleParser.class.getMethod("supports", String.class),
                    "Should have supports method");
        }

        @Test
        @DisplayName("WeChatArticleParser has static isWechatArticleUrl method")
        void testParserHasStaticIsWechatArticleUrlMethod() throws NoSuchMethodException {
            assertNotNull(WeChatArticleParser.class.getMethod("isWechatArticleUrl", String.class),
                    "Should have static isWechatArticleUrl method");
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(String html) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(html);
        return response;
    }
}
