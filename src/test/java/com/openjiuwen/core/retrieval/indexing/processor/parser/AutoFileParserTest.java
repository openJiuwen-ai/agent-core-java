/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_auto_file_parser.py} in
 * {@code tests.unit_tests.core.retrieval.indexing.processor.parser}.
 */
class AutoFileParserTest {

    @TempDir
    Path tempDir;

    @Test
    void testRegisterParserDecorator() {
        AutoFileParser.registerNewParser(".test", TestParser::new);
        AutoFileParser.registerNewParser(".TEST", TestParser::new);

        assertTrue(AutoFileParser.getSupportedFormats().contains(".test"));
        Parser parserInstance = new TestParser();
        assertInstanceOf(TestParser.class, parserInstance);
    }

    @Test
    void testRegisterParserMultipleExtensions() {
        AutoFileParser.registerNewParser(".ext1", TestParser::new);
        AutoFileParser.registerNewParser(".ext2", TestParser::new);
        AutoFileParser.registerNewParser(".EXT3", TestParser::new);

        List<String> formats = AutoFileParser.getSupportedFormats();
        assertTrue(formats.contains(".ext1"));
        assertTrue(formats.contains(".ext2"));
        assertTrue(formats.contains(".ext3"));
    }

    @Test
    void testInit() {
        AutoFileParser parser = new AutoFileParser();

        assertNotNull(parser);
    }

    @Test
    void testParsePdfFile() throws IOException {
        Path file = tempDir.resolve("sample.pdf");
        createPdf(file, "PDF content");

        AutoFileParser parser = new AutoFileParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals(".pdf", documents.getFirst().getMetadata().get("file_ext"));
        assertTrue(documents.getFirst().getText().contains("PDF content"));
    }

    @Test
    void testParseJsonFile() throws IOException {
        Path file = tempDir.resolve("sample.json");
        Files.writeString(file, "{\"key\":\"value\"}", StandardCharsets.UTF_8);

        AutoFileParser parser = new AutoFileParser();
        List<Document> documents = parser.parse(file.toString(), "doc_1", null, Map.of());

        assertEquals(1, documents.size());
        assertEquals(".json", documents.getFirst().getMetadata().get("file_ext"));
    }

    @Test
    void testParseFileNotFound() {
        AutoFileParser parser = new AutoFileParser();

        assertThrows(BaseError.class, () -> parser.parse(tempDir.resolve("nonexistent.txt").toString(), "doc_1", null, Map.of()));
    }

    @Test
    void testParseUnsupportedFormat() throws IOException {
        Path unsupported = tempDir.resolve("sample.xyz");
        Files.writeString(unsupported, "data", StandardCharsets.UTF_8);
        AutoFileParser parser = new AutoFileParser();

        assertThrows(BaseError.class, () -> parser.parse(unsupported.toString(), "doc_1", null, Map.of()));
    }

    @Test
    void testSupportsExistingFile() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "text", StandardCharsets.UTF_8);

        AutoFileParser parser = new AutoFileParser();

        assertTrue(parser.supports(file.toString()));
    }

    @Test
    void testSupportsNonexistentFile() {
        AutoFileParser parser = new AutoFileParser();

        assertTrue(!parser.supports(tempDir.resolve("nonexistent.txt").toString()));
    }

    @Test
    void testSupportsUnsupportedFormat() throws IOException {
        Path unsupported = tempDir.resolve("sample.xyz");
        Files.writeString(unsupported, "data", StandardCharsets.UTF_8);

        AutoFileParser parser = new AutoFileParser();

        assertTrue(!parser.supports(unsupported.toString()));
    }

    @Test
    void testRegisterNewParser() throws IOException {
        AutoFileParser.registerNewParser(".custom", TestParser::new);
        Path file = tempDir.resolve("sample.custom");
        Files.writeString(file, "test", StandardCharsets.UTF_8);

        AutoFileParser parser = new AutoFileParser();

        assertTrue(AutoFileParser.getSupportedFormats().contains(".custom"));
        assertTrue(parser.supports(file.toString()));
    }

    @Test
    void testGetSupportedFormats() {
        List<String> formats = AutoFileParser.getSupportedFormats();

        assertNotNull(formats);
        assertTrue(formats.contains(".txt") || formats.contains(".pdf") || formats.contains(".json"));
    }

    @Test
    void testParseEmptyResult() throws IOException {
        Path empty = tempDir.resolve("empty.txt");
        Files.writeString(empty, "", StandardCharsets.UTF_8);

        AutoFileParser parser = new AutoFileParser();
        List<Document> documents = parser.parse(empty.toString(), "doc_1", null, Map.of());

        assertTrue(documents.isEmpty());
    }

    private static void createPdf(Path file, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(100, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(file.toFile());
        }
    }

    private static final class TestParser extends Parser {

        @Override
        public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
            return List.of(new Document(docId, "test content", Map.of()));
        }

        @Override
        protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
            return "test content";
        }

        @Override
        public boolean supports(String doc) {
            return true;
        }
    }
}
