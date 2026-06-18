/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

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
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for the top-level parser router.
 *
 * <p>Mirrors Python's {@code AutoParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_parser.py}.</p>
 */
class AutoParserTest {

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
    void supportsHttpUrlsThroughLinkParser() {
        AutoParser parser = new AutoParser();

        assertThat(parser.supports("https://mp.weixin.qq.com/s/abc")).isTrue();
        assertThat(parser.supports("https://example.com/page")).isTrue();
    }

    @Test
    void supportsExistingFilePathThroughFileParser() throws Exception {
        AutoFileParser.registerNewParser(".txt", () -> new RecordingParser(value -> true, List.of()));
        Path file = Files.writeString(tempDir.resolve("sample.txt"), "content");

        AutoParser parser = new AutoParser();

        assertThat(parser.supports(file.toString())).isTrue();
    }

    @Test
    void supportsReturnsFalseForNonUrlAndNonExistingPath() {
        AutoParser parser = new AutoParser();

        assertThat(parser.supports("not-a-url")).isFalse();
        assertThat(parser.supports(tempDir.resolve("missing.txt").toString())).isFalse();
    }

    @Test
    void supportsDoesNotFallbackToFileParserForLikelyUrls() {
        RecordingParser linkParser = new RecordingParser(value -> false, List.of());
        RecordingParser fileParser = new RecordingParser(value -> true, List.of());
        AutoParser parser = new AutoParser(linkParser, fileParser);

        assertThat(parser.supports("https://example.com/page")).isFalse();
        assertThat(fileParser.supportsCount).isZero();
    }

    @Test
    void parseUrlDelegatesToLinkParser() {
        Document document = new Document("link_doc", "from link", Map.of());
        RecordingParser linkParser = new RecordingParser(value -> value.startsWith("http"), List.of(document));
        RecordingParser fileParser = new RecordingParser(value -> false, List.of());
        AutoParser parser = new AutoParser(linkParser, fileParser);
        Map<String, Object> options = Map.of("trace", "url");

        List<Document> result = parser.parse("https://example.com/page", "id1", null, options).join();

        assertThat(result).containsExactly(document);
        assertThat(linkParser.parseCount).isOne();
        assertThat(linkParser.lastDoc).isEqualTo("https://example.com/page");
        assertThat(linkParser.lastDocId).isEqualTo("id1");
        assertThat(linkParser.lastOptions).isSameAs(options);
        assertThat(fileParser.parseCount).isZero();
    }

    @Test
    void parseFileDelegatesToFileParserWithoutConsultingLinkParser() {
        Document document = new Document("file_doc", "from file", Map.of());
        RecordingParser linkParser = new RecordingParser(value -> true, List.of());
        RecordingParser fileParser = new RecordingParser(value -> value.endsWith(".txt"), List.of(document));
        AutoParser parser = new AutoParser(linkParser, fileParser);
        Map<String, Object> options = Map.of("trace", "file");

        List<Document> result = parser.parse("sample.txt", "id2", null, options).join();

        assertThat(result).containsExactly(document);
        assertThat(linkParser.supportsCount).isZero();
        assertThat(linkParser.parseCount).isZero();
        assertThat(fileParser.parseCount).isOne();
        assertThat(fileParser.lastDoc).isEqualTo("sample.txt");
        assertThat(fileParser.lastDocId).isEqualTo("id2");
        assertThat(fileParser.lastOptions).isSameAs(options);
    }

    @Test
    void parseUnsupportedSourceReturnsEmptyList() {
        RecordingParser linkParser = new RecordingParser(value -> false, List.of());
        RecordingParser fileParser = new RecordingParser(value -> false, List.of());
        AutoParser parser = new AutoParser(linkParser, fileParser);

        assertThat(parser.parse("https://example.com", "id3", null, Map.of()).join()).isEmpty();
        assertThat(parser.parse("/nonexistent.xyz", "id4", null, Map.of()).join()).isEmpty();
        assertThat(linkParser.parseCount).isZero();
        assertThat(fileParser.parseCount).isZero();
    }

    /**
     * Test delegate for {@link AutoParser}.
     *
     * <p>Mirrors Python's {@code AutoParser} collaborators in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_parser.py}.</p>
     */
    private static final class RecordingParser extends Parser {
        private final Predicate<String> supported;
        private final List<Document> result;
        private int supportsCount;
        private int parseCount;
        private String lastDoc;
        private String lastDocId;
        private Map<String, Object> lastOptions;

        private RecordingParser(Predicate<String> supported, List<Document> result) {
            this.supported = supported;
            this.result = result;
        }

        @Override
        public boolean supports(String doc) {
            supportsCount++;
            return supported.test(doc);
        }

        @Override
        public CompletableFuture<List<Document>> parse(
                String doc,
                String docId,
                BaseModelClient llmClient,
                Map<String, Object> options
        ) {
            parseCount++;
            lastDoc = doc;
            lastDocId = docId;
            lastOptions = options;
            return CompletableFuture.completedFuture(result);
        }
    }
}
