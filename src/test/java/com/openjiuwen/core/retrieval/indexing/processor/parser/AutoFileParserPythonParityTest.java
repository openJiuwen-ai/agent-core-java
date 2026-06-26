/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestRegisterParser} and {@code TestAutoFileParser} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_auto_file_parser.py}.
 */
class AutoFileParserPythonParityTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AutoFileParser.clearRegisteredParsersForTest();
    }

    @AfterEach
    void tearDown() {
        AutoFileParser.clearRegisteredParsersForTest();
    }

    @Test
    void registerParserDecoratorRegistersFactoryAndCreatesInstance() throws Exception {
        AutoFileParser.registerParser(List.of(".test", ".TEST"), TestParser::new);
        Path file = Files.writeString(tempDir.resolve("doc.test"), "ignored");

        List<Document> documents = new AutoFileParser().parse(file.toString(), "doc_1").join();

        assertThat(AutoFileParser.getSupportedFormats()).contains(".test").doesNotContain(".TEST");
        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getText()).isEqualTo("test content");
    }

    @Test
    void registerParserMultipleExtensionsLowercasesAllExtensions() {
        AutoFileParser.registerParser(List.of(".ext1", ".ext2", ".EXT3"), TestParser::new);

        assertThat(AutoFileParser.getSupportedFormats()).contains(".ext1", ".ext2", ".ext3");
    }

    @Test
    void initLoadsBuiltInParsers() {
        AutoFileParser parser = new AutoFileParser();

        assertThat(parser).isNotNull();
        assertThat(AutoFileParser.getSupportedFormats()).containsAnyOf(".txt", ".pdf", ".json");
    }

    @Test
    void parsePdfFileAddsPdfMetadata() throws Exception {
        AutoFileParser parser = new AutoFileParser();
        AutoFileParser.registerNewParser(".pdf", () -> new StaticParser("PDF content"));
        Path file = Files.writeString(tempDir.resolve("doc.pdf"), "ignored");

        List<Document> documents = parser.parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getMetadata()).containsEntry("file_ext", ".pdf");
    }

    @Test
    void parseJsonFileAddsJsonMetadata() throws Exception {
        AutoFileParser parser = new AutoFileParser();
        Path file = Files.writeString(tempDir.resolve("doc.json"), "{\"key\":\"value\"}");

        List<Document> documents = parser.parse(file.toString(), "doc_1").join();

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getMetadata()).containsEntry("file_ext", ".json");
    }

    @Test
    void parseFileNotFoundRaisesBaseError() {
        AutoFileParser parser = new AutoFileParser();

        assertThatThrownBy(() -> parser.parse(tempDir.resolve("missing.txt").toString(), "doc_1"))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND);
    }

    @Test
    void parseUnsupportedFormatRaisesBaseError() throws Exception {
        AutoFileParser parser = new AutoFileParser();
        Path file = Files.writeString(tempDir.resolve("doc.xyz"), "ignored");

        assertThatThrownBy(() -> parser.parse(file.toString(), "doc_1"))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FORMAT_NOT_SUPPORT);
    }

    @Test
    void supportsExistingRegisteredFile() throws Exception {
        AutoFileParser parser = new AutoFileParser();
        Path file = Files.writeString(tempDir.resolve("doc.txt"), "content");

        assertThat(parser.supports(file.toString())).isTrue();
    }

    @Test
    void supportsNonexistentFileReturnsFalse() {
        AutoFileParser parser = new AutoFileParser();

        assertThat(parser.supports(tempDir.resolve("missing.txt").toString())).isFalse();
    }

    @Test
    void supportsUnsupportedFormatReturnsFalse() throws Exception {
        AutoFileParser parser = new AutoFileParser();
        Path file = Files.writeString(tempDir.resolve("doc.xyz"), "ignored");

        assertThat(parser.supports(file.toString())).isFalse();
    }

    @Test
    void registerNewParserMakesCustomExtensionSupported() throws Exception {
        AutoFileParser parser = new AutoFileParser();
        AutoFileParser.registerNewParser(".custom", CustomParser::new);
        Path file = Files.writeString(tempDir.resolve("doc.custom"), "ignored");

        assertThat(parser.supports(file.toString())).isTrue();
        assertThat(parser.parse(file.toString(), "doc_1").join().getFirst().getText()).isEqualTo("custom content");
    }

    @Test
    void getSupportedFormatsReturnsListWithCommonFormats() {
        new AutoFileParser();

        List<String> formats = AutoFileParser.getSupportedFormats();

        assertThat(formats).isInstanceOf(List.class);
        assertThat(formats).containsAnyOf(".txt", ".pdf", ".json");
    }

    @Test
    void parseEmptyResultReturnsEmptyList() throws Exception {
        AutoFileParser parser = new AutoFileParser();
        AutoFileParser.registerNewParser(".txt", EmptyParser::new);
        Path file = Files.writeString(tempDir.resolve("empty.txt"), "");

        List<Document> documents = parser.parse(file.toString(), "doc_1").join();

        assertThat(documents).isEmpty();
    }

    /**
     * Mirrors Python's local {@code TestParser} in
     * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_auto_file_parser.py}.
     */
    private static final class TestParser extends Parser {
        @Override
        public CompletableFuture<List<Document>> parse(
                String doc,
                String docId,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(List.of(new Document(docId, "test content", Map.of())));
        }

        @Override
        public boolean supports(String doc) {
            return true;
        }
    }

    /**
     * Mirrors Python's local {@code CustomParser} in
     * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_auto_file_parser.py}.
     */
    private static final class CustomParser extends Parser {
        @Override
        public CompletableFuture<List<Document>> parse(
                String doc,
                String docId,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(List.of(new Document(docId, "custom content", Map.of())));
        }

        @Override
        public boolean supports(String doc) {
            return true;
        }
    }

    /**
     * Mirrors Python's empty parser result scenario in
     * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_auto_file_parser.py}.
     */
    private static final class EmptyParser extends Parser {
        @Override
        public CompletableFuture<List<Document>> parse(
                String doc,
                String docId,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(List.of());
        }
    }

    /**
     * Mirrors Python's mocked parser result scenario in
     * {@code tests/unit_tests/core/retrieval/indexing/processor/parser/test_auto_file_parser.py}.
     */
    private static final class StaticParser extends Parser {
        private final String text;

        private StaticParser(String text) {
            this.text = text;
        }

        @Override
        public CompletableFuture<List<Document>> parse(
                String doc,
                String docId,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(List.of(new Document(docId, text, Map.of())));
        }
    }
}
