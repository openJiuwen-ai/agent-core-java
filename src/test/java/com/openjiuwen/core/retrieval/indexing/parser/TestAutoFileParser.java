/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.indexing.processor.parser.AutoFileParser;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AutoFileParser.
 *
 * <p>Mirrors Python's {@code TestRegisterParser} and {@code TestAutoFileParser} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser.test_auto_file_parser}.</p>
 */
class TestAutoFileParser {

    @TempDir
    Path tempDir;

    private Map<String, Supplier<? extends Parser>> savedRegistry;

    @BeforeEach
    void saveRegistry() throws Exception {
        savedRegistry = new LinkedHashMap<>(registry());
    }

    @AfterEach
    void restoreRegistry() throws Exception {
        Map<String, Supplier<? extends Parser>> registry = registry();
        registry.clear();
        registry.putAll(savedRegistry);
    }

    @Nested
    @DisplayName("AutoFileParser tests")
    class AutoFileParserTests {

        @Test
        @DisplayName("test_register_parser_decorator")
        void testRegisterParserDecorator() throws Exception {
            AutoFileParser.registerNewParser(".test", () -> new EchoParser("decorated parser"));
            Path file = tempDir.resolve("sample.test");
            Files.writeString(file, "payload");

            assertTrue(AutoFileParser.getSupportedFormats().contains(".test"));
            List<Document> docs = new AutoFileParser().parse(file.toString(), "doc-test", null, Map.of());

            assertEquals(1, docs.size());
            assertEquals("doc-test", docs.getFirst().getId());
            assertEquals("decorated parser", docs.getFirst().getText());
            assertEquals(".test", docs.getFirst().getMetadata().get("file_ext"));
        }

        @Test
        @DisplayName("test_register_parser_multiple_extensions")
        void testRegisterParserMultipleExtensions() {
            AutoFileParser.registerNewParser(".ext1", () -> new EchoParser("multi"));
            AutoFileParser.registerNewParser(".ext2", () -> new EchoParser("multi"));
            AutoFileParser.registerNewParser(".ext3", () -> new EchoParser("multi"));

            List<String> formats = AutoFileParser.getSupportedFormats();
            assertTrue(formats.contains(".ext1"));
            assertTrue(formats.contains(".ext2"));
            assertTrue(formats.contains(".ext3"));
        }

        @Test
        @DisplayName("test_init")
        void testInit() {
            assertNotNull(new AutoFileParser());
        }

        @Test
        @DisplayName("test_parse_pdf_file")
        void testParsePdfFile() throws Exception {
            AutoFileParser.registerNewParser(".pdf", () -> new EchoParser("pdf content"));
            Path pdf = tempDir.resolve("sample.pdf");
            Files.writeString(pdf, "%PDF fake content");

            List<Document> documents = new AutoFileParser().parse(
                    pdf.toString(), "doc_pdf", null, Map.of("file_name", "sample.pdf"));

            assertEquals(1, documents.size());
            assertEquals("doc_pdf", documents.getFirst().getId());
            assertEquals("pdf content", documents.getFirst().getText());
            assertEquals(".pdf", documents.getFirst().getMetadata().get("file_ext"));
            assertEquals("sample.pdf", documents.getFirst().getMetadata().get("title"));
        }

        @Test
        @DisplayName("test_parse_json_file")
        void testParseJsonFile() throws Exception {
            Path json = tempDir.resolve("data.json");
            Files.writeString(json, "{\"name\":\"test\"}");

            List<Document> documents = new AutoFileParser().parse(json.toString(), "doc_json", null, Map.of());

            assertEquals(1, documents.size());
            assertEquals("doc_json", documents.getFirst().getId());
            assertTrue(documents.getFirst().getText().contains("test"));
            assertEquals(".json", documents.getFirst().getMetadata().get("file_ext"));
        }

        @Test
        @DisplayName("test_parse_file_not_found")
        void testParseFileNotFound() {
            BaseError error = assertThrows(BaseError.class, () -> new AutoFileParser().parse(
                    tempDir.resolve("missing.txt").toString(), "doc", null, Map.of()));

            assertTrue(error.getMessage().contains("does not exist"));
        }

        @Test
        @DisplayName("test_parse_unsupported_format")
        void testParseUnsupportedFormat() throws Exception {
            Path unsupported = tempDir.resolve("unsupported.xyz");
            Files.writeString(unsupported, "payload");

            BaseError error = assertThrows(BaseError.class, () -> new AutoFileParser().parse(
                    unsupported.toString(), "doc", null, Map.of()));

            assertTrue(error.getMessage().contains("Unsupported format"));
        }

        @Test
        @DisplayName("test_supports_existing_file")
        void testSupportsExistingFile() throws Exception {
            Path file = tempDir.resolve("document.txt");
            Files.writeString(file, "text");

            assertTrue(new AutoFileParser().supports(file.toString()));
        }

        @Test
        @DisplayName("test_supports_nonexistent_file")
        void testSupportsNonexistentFile() {
            assertFalse(new AutoFileParser().supports(tempDir.resolve("missing.txt").toString()));
        }

        @Test
        @DisplayName("test_supports_unsupported_format")
        void testSupportsUnsupportedFormat() throws Exception {
            Path file = tempDir.resolve("document.unsupported");
            Files.writeString(file, "text");

            assertFalse(new AutoFileParser().supports(file.toString()));
        }

        @Test
        @DisplayName("test_register_new_parser")
        void testRegisterNewParser() throws Exception {
            AutoFileParser.registerNewParser(".custom", () -> new EchoParser("custom content"));
            Path file = tempDir.resolve("document.custom");
            Files.writeString(file, "text");

            AutoFileParser parser = new AutoFileParser();
            assertTrue(AutoFileParser.getSupportedFormats().contains(".custom"));
            assertTrue(parser.supports(file.toString()));
            assertEquals("custom content", parser.parse(file.toString(), "doc-custom", null, Map.of())
                    .getFirst().getText());
        }

        @Test
        @DisplayName("test_get_supported_formats")
        void testGetSupportedFormats() {
            List<String> formats = AutoFileParser.getSupportedFormats();

            assertInstanceOf(List.class, formats);
            assertTrue(formats.contains(".txt") || formats.contains(".pdf") || formats.contains(".json"));
        }

        @Test
        @DisplayName("test_parse_empty_result")
        void testParseEmptyResult() throws Exception {
            AutoFileParser.registerNewParser(".empty", EmptyParser::new);
            Path file = tempDir.resolve("document.empty");
            Files.writeString(file, "text");

            assertTrue(new AutoFileParser().parse(file.toString(), "doc-empty", null, Map.of()).isEmpty());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Supplier<? extends Parser>> registry() throws Exception {
        Field field = AutoFileParser.class.getDeclaredField("PARSER_REGISTRY");
        field.setAccessible(true);
        return (Map<String, Supplier<? extends Parser>>) field.get(null);
    }

    private static final class EchoParser extends Parser {
        private final String content;

        private EchoParser(String content) {
            this.content = content;
        }

        @Override
        public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
            return List.of(new Document(docId, content, Map.of()));
        }

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return content;
        }
    }

    private static final class EmptyParser extends Parser {
        @Override
        public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
            return List.of();
        }

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }
    }
}
