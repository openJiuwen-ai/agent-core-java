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
 * Mirrors Python's {@code AutoFileParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_file_parser.py}.
 */
class AutoFileParserTest {

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
    void registerParserKeepsFirstFactoryForExtension() throws Exception {
        AutoFileParser.registerParser(List.of(".unit", ".UNIT"), () -> new StaticParser("first"));
        AutoFileParser.registerParser(List.of(".unit"), () -> new StaticParser("second"));
        Path file = Files.writeString(tempDir.resolve("doc.UNIT"), "ignored");

        List<Document> documents = new AutoFileParser()
                .parse(file.toString(), "doc-1", null, Map.of("file_name", "Named"))
                .join();

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).getText()).isEqualTo("first");
        assertThat(documents.get(0).getMetadata())
                .containsEntry("doc_id", "doc-1")
                .containsEntry("title", "Named")
                .containsEntry("file_path", file.toString())
                .containsEntry("file_ext", ".unit");
    }

    @Test
    void registerNewParserOverridesExistingFactory() throws Exception {
        AutoFileParser.registerParser(List.of(".swap"), () -> new StaticParser("old"));
        AutoFileParser.registerNewParser(".swap", () -> new StaticParser("new"));
        Path file = Files.writeString(tempDir.resolve("doc.swap"), "ignored");

        List<Document> documents = new AutoFileParser().parse(file.toString(), "doc-2").join();

        assertThat(documents.get(0).getText()).isEqualTo("new");
        assertThat(AutoFileParser.getSupportedFormats()).containsExactly(".swap");
    }

    @Test
    void supportsRequiresExistingFileAndRegisteredExtension() throws Exception {
        AutoFileParser.registerNewParser("ok", () -> new StaticParser("body"));
        Path file = Files.writeString(tempDir.resolve("doc.ok"), "ignored");
        AutoFileParser parser = new AutoFileParser();

        assertThat(parser.supports(file.toString())).isTrue();
        assertThat(parser.supports(tempDir.resolve("missing.ok").toString())).isFalse();
        assertThat(parser.supports(Files.writeString(tempDir.resolve("doc.bad"), "ignored").toString())).isFalse();
    }

    @Test
    void parseRaisesPythonStatusForMissingAndUnsupportedFiles() throws Exception {
        AutoFileParser parser = new AutoFileParser();
        assertThatThrownBy(() -> parser.parse(tempDir.resolve("missing.unit").toString()).join())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FILE_NOT_FOUND);

        Path file = Files.writeString(tempDir.resolve("doc.unknown"), "ignored");
        assertThatThrownBy(() -> parser.parse(file.toString()).join())
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_FORMAT_NOT_SUPPORT);
    }

    @Test
    void parseReturnsEmptyListWhenDelegateReturnsNoDocuments() throws Exception {
        AutoFileParser.registerNewParser(".empty", EmptyParser::new);
        Path file = Files.writeString(tempDir.resolve("doc.empty"), "ignored");

        assertThat(new AutoFileParser().parse(file.toString()).join()).isEmpty();
    }

    /**
     * Mirrors Python's parser factory contract in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_file_parser.py}.
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
            return CompletableFuture.completedFuture(List.of(new Document(docId, text, Map.of("source", "unit"))));
        }
    }

    /**
     * Mirrors Python's empty parse result handling in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_file_parser.py}.
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
}
