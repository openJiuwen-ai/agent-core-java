/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.service_api.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for response parsers, decompressors, and ParserRegistry.
 * Ported from Python: tests/unit_tests/core/foundation/tool/test_restfulapi.py
 * (response parsing and decompression validation)
 */
class ResponseParserTest {

    // ============================== JsonResponseParser tests ==============================

    @Nested
    @DisplayName("JsonResponseParser tests")
    class JsonResponseParserTests {

        private final JsonResponseParser parser = new JsonResponseParser();

        @Test
        @DisplayName("canParse returns true for application/json")
        void testCanParseApplicationJson() {
            assertTrue(parser.canParse("application/json", 200, null));
        }

        @Test
        @DisplayName("canParse returns true for text/json")
        void testCanParseTextJson() {
            assertTrue(parser.canParse("text/json", 200, null));
        }

        @Test
        @DisplayName("canParse returns true for application/json with charset")
        void testCanParseJsonWithCharset() {
            assertTrue(parser.canParse("application/json; charset=utf-8", 200, null));
        }

        @Test
        @DisplayName("canParse returns false for text/plain")
        void testCanNotParseTextPlain() {
            assertFalse(parser.canParse("text/plain", 200, null));
        }

        @Test
        @DisplayName("canParse returns false for text/html")
        void testCanNotParseHtml() {
            assertFalse(parser.canParse("text/html", 200, null));
        }

        @Test
        @DisplayName("canParse returns true for application/javascript")
        void testCanParseJavascript() {
            assertTrue(parser.canParse("application/javascript", 200, null));
        }

        @Test
        @DisplayName("Parse valid JSON bytes")
        @SuppressWarnings("unchecked")
        void testParseValidJson() {
            byte[] jsonBytes = "{\"code\": 200, \"message\": \"success\"}".getBytes(StandardCharsets.UTF_8);
            Object result = parser.parse(jsonBytes, "application/json");

            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals(200, map.get("code"));
            assertEquals("success", map.get("message"));
        }

        @Test
        @DisplayName("Parse nested JSON structure")
        @SuppressWarnings("unchecked")
        void testParseNestedJson() {
            String json = "{\"data\": {\"id\": 123, \"name\": \"test_user\", \"status\": \"active\"}}";
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            Object result = parser.parse(jsonBytes, "application/json");

            Map<String, Object> map = (Map<String, Object>) result;
            Map<String, Object> data = (Map<String, Object>) map.get("data");
            assertEquals(123, data.get("id"));
            assertEquals("test_user", data.get("name"));
            assertEquals("active", data.get("status"));
        }

        @Test
        @DisplayName("Parse empty bytes returns empty map")
        void testParseEmptyBytes() {
            Object result = parser.parse(new byte[0], "application/json");
            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            assertTrue(((Map<?, ?>) result).isEmpty());
        }

        @Test
        @DisplayName("Parse null bytes returns empty map")
        void testParseNullBytes() {
            Object result = parser.parse(null, "application/json");
            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            assertTrue(((Map<?, ?>) result).isEmpty());
        }

        @Test
        @DisplayName("Parse invalid JSON throws exception")
        void testParseInvalidJsonThrows() {
            byte[] invalid = "{invalid: json, missing: quotes}".getBytes(StandardCharsets.UTF_8);
            assertThrows(IllegalArgumentException.class,
                    () -> parser.parse(invalid, "application/json"));
        }
    }

    // ============================== TextResponseParser tests ==============================

    @Nested
    @DisplayName("TextResponseParser tests")
    class TextResponseParserTests {

        private final TextResponseParser parser = new TextResponseParser();

        @Test
        @DisplayName("canParse returns true for text/plain")
        void testCanParseTextPlain() {
            assertTrue(parser.canParse("text/plain", 200, null));
        }

        @Test
        @DisplayName("canParse returns true for text/html")
        void testCanParseTextHtml() {
            assertTrue(parser.canParse("text/html", 200, null));
        }

        @Test
        @DisplayName("canParse returns true for text/xml")
        void testCanParseTextXml() {
            assertTrue(parser.canParse("text/xml", 200, null));
        }

        @Test
        @DisplayName("canParse returns true for application/xml")
        void testCanParseAppXml() {
            assertTrue(parser.canParse("application/xml", 200, null));
        }

        @Test
        @DisplayName("canParse returns true for text/csv")
        void testCanParseCsv() {
            assertTrue(parser.canParse("text/csv", 200, null));
        }

        @Test
        @DisplayName("canParse handles text/* wildcard pattern")
        void testCanParseTextWildcard() {
            assertTrue(parser.canParse("text/anything", 200, null));
        }

        @Test
        @DisplayName("Parse plain text response")
        void testParsePlainText() {
            String text = "Operation completed successfully";
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            Object result = parser.parse(textBytes, "text/plain");
            assertEquals(text, result);
        }

        @Test
        @DisplayName("Parse HTML response")
        void testParseHtml() {
            String html = "<html><body><h1>Hello World</h1></body></html>";
            byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
            Object result = parser.parse(htmlBytes, "text/html");
            assertEquals(html, result);
        }

        @Test
        @DisplayName("Parse XML response")
        void testParseXml() {
            String xml = "<?xml version=\"1.0\"?><response><status>success</status></response>";
            byte[] xmlBytes = xml.getBytes(StandardCharsets.UTF_8);
            Object result = parser.parse(xmlBytes, "application/xml");
            assertEquals(xml, result);
        }

        @Test
        @DisplayName("Parse empty bytes returns empty string")
        void testParseEmpty() {
            Object result = parser.parse(new byte[0], "text/plain");
            assertEquals("", result);
        }

        @Test
        @DisplayName("Parse null bytes returns empty string")
        void testParseNull() {
            Object result = parser.parse(null, "text/plain");
            assertEquals("", result);
        }
    }

    // ============================== GzipDecompressor tests ==============================

    @Nested
    @DisplayName("GzipDecompressor tests")
    class GzipDecompressorTests {

        private final GzipDecompressor decompressor = new GzipDecompressor();

        @Test
        @DisplayName("canDecompress returns true for 'gzip'")
        void testCanDecompressGzip() {
            assertTrue(decompressor.canDecompress("gzip"));
        }

        @Test
        @DisplayName("canDecompress returns true for 'x-gzip'")
        void testCanDecompressXGzip() {
            assertTrue(decompressor.canDecompress("x-gzip"));
        }

        @Test
        @DisplayName("canDecompress is case-insensitive")
        void testCanDecompressCaseInsensitive() {
            assertTrue(decompressor.canDecompress("GZIP"));
            assertTrue(decompressor.canDecompress("Gzip"));
        }

        @Test
        @DisplayName("canDecompress returns false for 'deflate'")
        void testCanNotDecompressDeflate() {
            assertFalse(decompressor.canDecompress("deflate"));
        }

        @Test
        @DisplayName("canDecompress returns false for null")
        void testCanNotDecompressNull() {
            assertFalse(decompressor.canDecompress(null));
        }

        @Test
        @DisplayName("Decompress valid gzip data")
        void testDecompressGzip() throws IOException {
            String original = "{\"status\": \"success\", \"data\": \"hello world\"}";
            byte[] compressed = gzipCompress(original.getBytes(StandardCharsets.UTF_8));

            byte[] decompressed = decompressor.decompress(compressed);
            assertEquals(original, new String(decompressed, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("Decompress invalid data throws IOException")
        void testDecompressInvalidDataThrows() {
            byte[] invalidData = "not gzip data".getBytes(StandardCharsets.UTF_8);
            assertThrows(IOException.class, () -> decompressor.decompress(invalidData));
        }
    }

    // ============================== DeflateDecompressor tests ==============================

    @Nested
    @DisplayName("DeflateDecompressor tests")
    class DeflateDecompressorTests {

        private final DeflateDecompressor decompressor = new DeflateDecompressor();

        @Test
        @DisplayName("canDecompress returns true for 'deflate'")
        void testCanDecompressDeflate() {
            assertTrue(decompressor.canDecompress("deflate"));
        }

        @Test
        @DisplayName("canDecompress is case-insensitive")
        void testCanDecompressCaseInsensitive() {
            assertTrue(decompressor.canDecompress("DEFLATE"));
            assertTrue(decompressor.canDecompress("Deflate"));
        }

        @Test
        @DisplayName("canDecompress returns false for 'gzip'")
        void testCanNotDecompressGzip() {
            assertFalse(decompressor.canDecompress("gzip"));
        }

        @Test
        @DisplayName("canDecompress returns false for null")
        void testCanNotDecompressNull() {
            assertFalse(decompressor.canDecompress(null));
        }

        @Test
        @DisplayName("Decompress valid deflate data")
        void testDecompressDeflate() throws IOException {
            String original = "{\"message\": \"deflate test data\"}";
            byte[] compressed = deflateCompress(original.getBytes(StandardCharsets.UTF_8));

            byte[] decompressed = decompressor.decompress(compressed);
            assertEquals(original, new String(decompressed, StandardCharsets.UTF_8));
        }
    }

    // ============================== ParserRegistry tests ==============================

    @Nested
    @DisplayName("ParserRegistry tests")
    class ParserRegistryTests {

        @Test
        @DisplayName("getInstance returns singleton")
        void testSingleton() {
            ParserRegistry instance1 = ParserRegistry.getInstance();
            ParserRegistry instance2 = ParserRegistry.getInstance();
            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("Parse JSON response via registry")
        @SuppressWarnings("unchecked")
        void testParseJsonResponse() {
            Map<String, String> headers = Map.of("Content-Type", "application/json");
            byte[] data = "{\"code\": 200, \"data\": {\"id\": 123}}".getBytes(StandardCharsets.UTF_8);

            Object result = ParserRegistry.getInstance().parse(headers, data, 200);
            assertInstanceOf(Map.class, result);
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals(200, map.get("code"));
        }

        @Test
        @DisplayName("Parse text response via registry")
        void testParseTextResponse() {
            Map<String, String> headers = Map.of("Content-Type", "text/plain");
            byte[] data = "Hello World".getBytes(StandardCharsets.UTF_8);

            Object result = ParserRegistry.getInstance().parse(headers, data, 200);
            assertEquals("Hello World", result);
        }

        @Test
        @DisplayName("Parse HTML response via registry as text")
        void testParseHtmlResponse() {
            Map<String, String> headers = Map.of("Content-Type", "text/html; charset=utf-8");
            byte[] data = "<h1>Hello</h1>".getBytes(StandardCharsets.UTF_8);

            Object result = ParserRegistry.getInstance().parse(headers, data, 200);
            assertEquals("<h1>Hello</h1>", result);
        }

        @Test
        @DisplayName("Parse gzip-compressed JSON response via registry")
        @SuppressWarnings("unchecked")
        void testParseGzipCompressedJson() throws IOException {
            String json = "{\"compressed\": true}";
            byte[] compressed = gzipCompress(json.getBytes(StandardCharsets.UTF_8));

            Map<String, String> headers = Map.of(
                    "Content-Type", "application/json",
                    "Content-Encoding", "gzip"
            );

            Object result = ParserRegistry.getInstance().parse(headers, compressed, 200);
            assertInstanceOf(Map.class, result);
            Map<String, Object> map = (Map<String, Object>) result;
            assertEquals(true, map.get("compressed"));
        }

        @Test
        @DisplayName("Parse empty response returns empty map for JSON")
        void testParseEmptyJsonResponse() {
            Map<String, String> headers = Map.of("Content-Type", "application/json");

            Object result = ParserRegistry.getInstance().parse(headers, new byte[0], 200);
            assertNotNull(result);
            assertInstanceOf(Map.class, result);
            assertTrue(((Map<?, ?>) result).isEmpty());
        }

        @Test
        @DisplayName("Parse empty response returns empty string for text")
        void testParseEmptyTextResponse() {
            Map<String, String> headers = Map.of("Content-Type", "text/plain");

            Object result = ParserRegistry.getInstance().parse(headers, new byte[0], 200);
            assertEquals("", result);
        }
    }

    // ============================== BaseResponseParser utility tests ==============================

    @Nested
    @DisplayName("BaseResponseParser utility tests")
    class BaseResponseParserUtilityTests {

        @Test
        @DisplayName("decodeBytes with UTF-8 charset in Content-Type")
        void testDecodeBytesUtf8() {
            JsonResponseParser parser = new JsonResponseParser();
            // Use inherited protected method via parse behavior
            byte[] data = "测试数据".getBytes(StandardCharsets.UTF_8);
            // Parsing as text to verify charset handling
            TextResponseParser textParser = new TextResponseParser();
            Object result = textParser.parse(data, "text/plain; charset=utf-8");
            assertEquals("测试数据", result);
        }

        @Test
        @DisplayName("decodeBytes defaults to UTF-8 when no charset specified")
        void testDecodeBytesDefaultCharset() {
            TextResponseParser parser = new TextResponseParser();
            byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
            Object result = parser.parse(data, "text/plain");
            assertEquals("hello", result);
        }
    }

    // ============================== Helper methods ==============================

    private static byte[] gzipCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(data);
        }
        return baos.toByteArray();
    }

    private static byte[] deflateCompress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos)) {
            dos.write(data);
        }
        return baos.toByteArray();
    }
}
