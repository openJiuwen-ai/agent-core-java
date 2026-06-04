// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.tests.unit_tests.core.common.utils;

import com.openjiuwen.core.common.security.UrlUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * URL utility tests.
 * <p>
 * Mirrors Python's {@code test_url_utils.py} in
 * {@code tests.unit_tests.core.common.utils.test_url_utils}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Password redaction with username</li>
 *   <li>Password redaction without username</li>
 *   <li>URLs without password</li>
 *   <li>Empty and null URL handling</li>
 *   <li>URLs with special characters in password</li>
 *   <li>Various database URL formats</li>
 * </ul>
 */
class TestUrlUtils {

    @Nested
    @DisplayName("Redact URL Password Tests")
    class RedactUrlPasswordTests {

        /**
         * Test: Redact password with username.
         * <p>
         * Mirrors Python's test_redact_password_with_username.
         */
        @Test
        @Tag("level0")
        @DisplayName("Redact password with username")
        void testRedactPasswordWithUsername() {
            String url = "redis://user:secret@host:6379/0";
            String result = UrlUtils.redactUrlPassword(url);
            assertEquals("redis://user:***@host:6379/0", result, "Password should be redacted");
            assertFalse(result.contains("secret"), "Original password should not appear in result");
        }

        /**
         * Test: Redact password without username.
         * <p>
         * Mirrors Python's test_redact_password_without_username.
         */
        @Test
        @Tag("level0")
        @DisplayName("Redact password without username")
        void testRedactPasswordWithoutUsername() {
            String url = "redis://:secret@host:6379/0";
            String result = UrlUtils.redactUrlPassword(url);
            assertTrue(result.contains("***"), "Password should be replaced with ***");
            assertFalse(result.contains("secret"), "Original password should not appear");
        }

        /**
         * Test: URL without password.
         * <p>
         * Mirrors Python's test_url_without_password.
         */
        @Test
        @Tag("level0")
        @DisplayName("URL without password remains unchanged")
        void testUrlWithoutPassword() {
            String url = "redis://host:6379/0";
            String result = UrlUtils.redactUrlPassword(url);
            assertEquals(url, result, "URL without password should remain unchanged");
        }

        /**
         * Test: URL without credentials.
         * <p>
         * Mirrors Python's test_url_without_credentials.
         */
        @Test
        @Tag("level0")
        @DisplayName("URL without credentials remains unchanged")
        void testUrlWithoutCredentials() {
            String url = "redis://localhost:6379/1";
            String result = UrlUtils.redactUrlPassword(url);
            assertEquals(url, result, "URL without credentials should remain unchanged");
        }

        /**
         * Test: Empty and null URL.
         * <p>
         * Mirrors Python's test_empty_url.
         */
        @Test
        @Tag("level0")
        @DisplayName("Empty and null URL handling")
        void testEmptyUrl() {
            assertEquals("", UrlUtils.redactUrlPassword(""), "Empty URL should return empty string");
            assertNull(UrlUtils.redactUrlPassword(null), "Null URL should return null");
        }

        /**
         * Test: URL with special characters in password.
         * <p>
         * Mirrors Python's test_url_with_special_chars_in_password.
         */
        @Test
        @Tag("level0")
        @DisplayName("URL with special characters in password")
        void testUrlWithSpecialCharsInPassword() {
            String url = "redis://:My%23SecretPwd@127.0.0.1:6379/0";
            String result = UrlUtils.redactUrlPassword(url);
            assertTrue(result.contains("***"), "Password should be redacted");
            assertFalse(result.contains("My%23SecretPwd"), "Original password should not appear");
        }

        /**
         * Test: MySQL URL format.
         * <p>
         * Mirrors Python's test_mysql_url.
         */
        @Test
        @Tag("level0")
        @DisplayName("MySQL URL password redaction")
        void testMysqlUrl() {
            String url = "mysql://root:password123@localhost:3306/mydb";
            String result = UrlUtils.redactUrlPassword(url);
            assertTrue(result.contains("root:***"), "MySQL password should be redacted");
            assertFalse(result.contains("password123"), "Original password should not appear");
        }

        /**
         * Test: PostgreSQL URL format.
         * <p>
         * Mirrors Python's test_postgres_url.
         */
        @Test
        @Tag("level0")
        @DisplayName("PostgreSQL URL password redaction")
        void testPostgresUrl() {
            String url = "postgresql://admin:secretpass@db.example.com:5432/production";
            String result = UrlUtils.redactUrlPassword(url);
            assertTrue(result.contains("admin:***"), "PostgreSQL password should be redacted");
            assertFalse(result.contains("secretpass"), "Original password should not appear");
        }

        /**
         * Test: URL without port.
         * <p>
         * Mirrors Python's test_url_without_port.
         */
        @Test
        @Tag("level0")
        @DisplayName("URL without port")
        void testUrlWithoutPort() {
            String url = "redis://:password@localhost/0";
            String result = UrlUtils.redactUrlPassword(url);
            assertTrue(result.contains("***"), "Password should be redacted");
        }

        /**
         * Test: Invalid URL returns original.
         * <p>
         * Mirrors Python's test_invalid_url_returns_original.
         */
        @Test
        @Tag("level0")
        @DisplayName("Invalid URL returns original")
        void testInvalidUrlReturnsOriginal() {
            String url = "not a valid url at all";
            String result = UrlUtils.redactUrlPassword(url);
            assertEquals(url, result, "Invalid URL should return original");
        }

        /**
         * Test: URL with query params.
         * <p>
         * Mirrors Python's test_url_with_query_params.
         */
        @Test
        @Tag("level0")
        @DisplayName("URL with query params preserves query")
        void testUrlWithQueryParams() {
            String url = "redis://:secret@host:6379/0?ssl=true";
            String result = UrlUtils.redactUrlPassword(url);
            assertFalse(result.contains("secret"), "Original password should not appear");
            assertTrue(result.contains("ssl=true"), "Query params should be preserved");
        }
    }
}