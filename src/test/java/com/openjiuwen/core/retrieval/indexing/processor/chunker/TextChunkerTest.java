/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TextChunker} behavior in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/chunking.py}.
 */
class TextChunkerTest {

    @Test
    void initWithCharUnitCreatesCharChunker() {
        TextChunker chunker = new TextChunker(512, 50, "char");

        assertThat(chunker.getChunkSize()).isEqualTo(512);
        assertThat(chunker.getChunkOverlap()).isEqualTo(50);
        assertThat(chunker.getInnerChunker()).isInstanceOf(CharChunker.class);
    }

    @Test
    void initWithTokenUnitWithoutTokenizerRaises() {
        assertThatThrownBy(() -> new TextChunker(512, 50, "token"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("requires embed_model with tokenizer or tiktoken");
    }

    @Test
    void initWithBothPreprocessOptionsBuildsTwoPreprocessors() {
        TextChunker chunker = new TextChunker(
                512,
                50,
                "char",
                null,
                new TextChunker.PreprocessOptions(true, true)
        );

        assertThat(chunker.getPipeline().getPreprocessors()).hasSize(2);
    }

    @Test
    void initWithNormalizeWhitespaceBuildsOnePreprocessor() {
        TextChunker chunker = new TextChunker(
                512,
                50,
                "char",
                null,
                new TextChunker.PreprocessOptions(true, false)
        );

        assertThat(chunker.getPipeline().getPreprocessors()).hasSize(1);
        assertThat(chunker.getPipeline().getPreprocessors().get(0)).isInstanceOf(WhitespaceNormalizer.class);
    }

    @Test
    void initWithRemoveUrlEmailBuildsOnePreprocessor() {
        TextChunker chunker = new TextChunker(
                512,
                50,
                "char",
                null,
                new TextChunker.PreprocessOptions(false, true)
        );

        assertThat(chunker.getPipeline().getPreprocessors()).hasSize(1);
        assertThat(chunker.getPipeline().getPreprocessors().get(0)).isInstanceOf(URLEmailRemover.class);
    }

    @Test
    void initWithoutPreprocessOptionsBuildsEmptyPipeline() {
        TextChunker chunker = new TextChunker(512, 50);

        assertThat(chunker.getPipeline().getPreprocessors()).isEmpty();
    }

    @Test
    void preprocessOptionsCanBeBuiltFromKeywordMap() {
        TextChunker.PreprocessOptions options = TextChunker.PreprocessOptions.fromKeywordArgs(Map.of(
                "normalize_whitespace", true,
                "remove_url_email", true
        ));

        assertThat(options.isNormalizeWhitespace()).isTrue();
        assertThat(options.isRemoveUrlEmail()).isTrue();
    }

    @Test
    void chunkDocumentsAppliesWhitespacePreprocessing() {
        TextChunker chunker = new TextChunker(
                100,
                10,
                "char",
                null,
                new TextChunker.PreprocessOptions(true, false)
        );
        Document document = new Document("doc_1", "This   is   document   1", Map.of("source", "test"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getText()).doesNotContain("   ");
        assertThat(chunks).allMatch(chunk -> "doc_1".equals(chunk.getDocId()));
        assertThat(chunks).allMatch(chunk -> chunk.getMetadata().containsKey("chunk_index"));
    }

    @Test
    void chunkDocumentsWithoutPreprocessingKeepsDocumentId() {
        TextChunker chunker = new TextChunker(100, 10);
        Document document = new Document("doc_1", "This is document 1", Map.of("source", "test"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allMatch(chunk -> "doc_1".equals(chunk.getDocId()));
    }

    @Test
    void chunkDocumentsHandlesMultipleDocuments() {
        TextChunker chunker = new TextChunker(100, 10);
        List<Document> documents = List.of(
                new Document("doc_1", "This is document 1"),
                new Document("doc_2", "This is document 2")
        );

        List<TextChunk> chunks = chunker.chunkDocuments(documents);
        Set<String> docIds = chunks.stream().map(TextChunk::getDocId).collect(Collectors.toSet());

        assertThat(chunks).isNotEmpty();
        assertThat(docIds).contains("doc_1", "doc_2");
    }

    @Test
    void chunkDocumentsPreservesMetadataAndAddsChunkMetadata() {
        TextChunker chunker = new TextChunker(100, 10);
        Document document = new Document("doc_1", "This is document 1", Map.of("source", "test", "author", "test_author"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(document));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getMetadata()).containsEntry("source", "test");
            assertThat(chunk.getMetadata()).containsEntry("author", "test_author");
            assertThat(chunk.getMetadata()).containsKeys("chunk_index", "total_chunks", "chunk_id");
            assertThat(chunk.getMetadata().get("chunk_id")).isEqualTo(chunk.getId_());
        });
    }

    @Test
    void getChunkerReturnsCharChunkerForCharUnit() {
        TextChunker chunker = new TextChunker(512, 50);

        Chunker result = chunker.getChunker(512, 50, "char", null);

        assertThat(result).isInstanceOf(CharChunker.class);
    }

    @Test
    void getChunkerTokenUnitAdjustsSizeFromTokenizerLimit() {
        TextChunker chunker = new TextChunker(512, 50);
        LimitedTokenizer tokenizer = new LimitedTokenizer(256);

        Chunker result = chunker.getChunker(512, 50, "token", tokenizer);

        assertThat(result).isInstanceOf(TokenizerChunker.class);
        assertThat(result.getChunkSize()).isEqualTo(256);
        assertThat(result.getChunkOverlap()).isEqualTo(50);
    }

    @Test
    void nonCharUnitUsesTokenizerChunkerWhenTokenizerExists() {
        TextChunker chunker = new TextChunker(128, 20);
        LimitedTokenizer tokenizer = new LimitedTokenizer(512);

        Chunker result = chunker.getChunker(128, 20, "token", tokenizer);

        assertThat(result).isInstanceOf(TokenizerChunker.class);
        assertThat(result.getChunkSize()).isEqualTo(128);
        assertThat(result.getChunkOverlap()).isEqualTo(20);
    }

    private static final class LimitedTokenizer implements IndexSentenceSplitter.TokenCodec {

        private final int maxTokenLength;

        private LimitedTokenizer(int maxTokenLength) {
            this.maxTokenLength = maxTokenLength;
        }

        @Override
        public List<String> encode(String text, int maxLength) {
            return List.of(text.split("\\s+"));
        }

        @Override
        public Integer maxTokenLength() {
            return maxTokenLength;
        }
    }
}
