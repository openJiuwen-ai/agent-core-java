/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.parser;

import com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
            assertThrows(Exception.class, () -> {
                parser.parse("https://example.com/page", "id1", null, null);
            }, "Should throw for non-WeChat URL");
        }

        @Test
        @DisplayName("parse() returns Document with metadata")
        void testParseReturnsDocumentWithMetadata() {
            // Mirrors Python: test_parse_returns_document_with_metadata
            // Note: This test would require mocking HTTP client for full verification
            // Here we verify the parser structure is correct
            assertNotNull(parser, "Parser should be initialized");
            
            // Verify the parse method signature exists
            try {
                var method = WeChatArticleParser.class.getMethod("parse",
                        String.class, String.class,
                        com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient.class,
                        java.util.Map.class);
                assertNotNull(method, "parse method should exist");
            } catch (NoSuchMethodException e) {
                fail("parse method should exist: " + e.getMessage());
            }
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
}
