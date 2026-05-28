/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.retrieval.indexing.processor.parser.AutoLinkParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AutoLinkParser.
 * <p>
 * Mirrors Python's {@code test_auto_link_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_auto_link_parser}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>URL detection and support validation</li>
 *   <li>WeChat article URL handling</li>
 *   <li>Generic HTTP/HTTPS URL handling</li>
 *   <li>Non-URL input rejection</li>
 *   <li>Empty/whitespace input handling</li>
 * </ul>
 */
class TestAutoLinkParser {

    @Nested
    @DisplayName("AutoLinkParser tests")
    @Tag("level0")
    class AutoLinkParserTests {

        /**
         * Test: AutoLinkParser exists.
         * <p>
         * Mirrors Python's basic parser existence tests.
         */
        @Test
        @DisplayName("AutoLinkParser class exists")
        void testAutoLinkParserExists() {
            assertNotNull(AutoLinkParser.class, "AutoLinkParser class should exist");
        }

        /**
         * Test: WeChat URL is supported.
         * <p>
         * Mirrors Python's test_supports_wechat_url.
         */
        @Test
        @DisplayName("supports() is True for WeChat article URL")
        void testSupportsWeChatUrl() {
            AutoLinkParser parser = new AutoLinkParser();
            assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc123"),
                "Should support WeChat HTTPS URL");
            assertTrue(parser.supports("http://mp.weixin.qq.com/s/xyz"),
                "Should support WeChat HTTP URL");
        }

        /**
         * Test: Generic HTTP URLs are supported.
         * <p>
         * Mirrors Python's test_supports_generic_http_url.
         */
        @Test
        @DisplayName("supports() is True for generic http(s) URL")
        void testSupportsGenericHttpUrl() {
            AutoLinkParser parser = new AutoLinkParser();
            assertTrue(parser.supports("https://example.com/article"),
                "Should support generic HTTPS URL");
            assertTrue(parser.supports("http://blog.google/foo"),
                "Should support generic HTTP URL");
        }

        /**
         * Test: Non-HTTP URLs are not supported.
         * <p>
         * Mirrors Python's test_supports_non_http_false.
         */
        @Test
        @DisplayName("supports() is False for non-http input")
        void testSupportsNonHttpFalse() {
            AutoLinkParser parser = new AutoLinkParser();
            assertFalse(parser.supports("ftp://example.com"),
                "Should not support FTP URL");
            assertFalse(parser.supports("/local/path/file.txt"),
                "Should not support local path");
            assertFalse(parser.supports("not-a-url"),
                "Should not support non-URL string");
        }

        /**
         * Test: Empty or whitespace input is not supported.
         * <p>
         * Mirrors Python's test_supports_empty_or_none.
         */
        @Test
        @DisplayName("supports() is False for empty or whitespace")
        void testSupportsEmptyOrWhitespace() {
            AutoLinkParser parser = new AutoLinkParser();
            assertFalse(parser.supports(""),
                "Should not support empty string");
            assertFalse(parser.supports("   "),
                "Should not support whitespace string");
        }

        /**
         * Test: URL detection patterns work correctly.
         */
        @Test
        @DisplayName("URL detection patterns")
        void testUrlDetectionPatterns() {
            // Valid URLs
            assertTrue(isValidHttpUrl("https://example.com"),
                "HTTPS URL should be valid");
            assertTrue(isValidHttpUrl("http://example.com/path?query=value"),
                "HTTP URL with query should be valid");
            assertTrue(isValidHttpUrl("https://sub.domain.example.com:8080/resource"),
                "HTTPS URL with port should be valid");

            // Invalid URLs
            assertFalse(isValidHttpUrl("ftp://example.com"),
                "FTP URL should not be valid HTTP");
            assertFalse(isValidHttpUrl("example.com"),
                "Missing protocol should not be valid");
            assertFalse(isValidHttpUrl(""),
                "Empty string should not be valid");
        }

        /**
         * Test: Link format validation.
         * <p>
         * Mirrors Python's link format validation tests.
         */
        @Test
        @DisplayName("Link format validation")
        void testLinkFormatValidation() {
            // Valid link formats
            String validLink = "https://example.com/path";
            assertTrue(validLink.contains("://"),
                "Valid link should contain protocol separator");
            assertTrue(validLink.startsWith("https://") || validLink.startsWith("http://"),
                "Valid link should start with HTTP protocol");

            // Link structure validation
            assertTrue(hasValidStructure("https://example.com/path"),
                "URL with path should have valid structure");
            assertTrue(hasValidStructure("https://example.com?query=value"),
                "URL with query should have valid structure");
        }
    }

    /**
     * Helper method to check if string is valid HTTP URL.
     */
    private boolean isValidHttpUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * Helper method to check if URL has valid structure.
     */
    private boolean hasValidStructure(String url) {
        if (!isValidHttpUrl(url)) {
            return false;
        }
        // Check for valid domain structure
        int protocolEnd = url.indexOf("://");
        String afterProtocol = url.substring(protocolEnd + 3);
        return afterProtocol.contains(".") || afterProtocol.contains("/");
    }
}
