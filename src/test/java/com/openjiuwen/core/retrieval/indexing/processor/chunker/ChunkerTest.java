/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkerTest {

    @Test
    void initWithDefaults() {
        ConcreteChunker chunker = new ConcreteChunker();

        assertThat(chunker.getChunkSize()).isEqualTo(512);
        assertThat(chunker.getChunkOverlap()).isEqualTo(50);
        assertThat(chunker.getLengthFunction().applyAsInt("abcd")).isEqualTo(4);
    }

    @Test
    void initWithCustomValues() {
        ToIntFunction<String> wordCount = text -> text.split("\\s+").length;
        ConcreteChunker chunker = new ConcreteChunker(1024, 100, wordCount);

        assertThat(chunker.getChunkSize()).isEqualTo(1024);
        assertThat(chunker.getChunkOverlap()).isEqualTo(100);
        assertThat(chunker.getLengthFunction().applyAsInt("a bb ccc")).isEqualTo(3);
    }

    @Test
    void initRejectsZeroChunkSize() {
        assertThatThrownBy(() -> new ConcreteChunker(0, 50, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("chunk_size must be greater than 0, current value: 0");
    }

    @Test
    void initRejectsNegativeChunkSize() {
        assertThatThrownBy(() -> new ConcreteChunker(-1, 50, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("chunk_size must be greater than 0, current value: -1");
    }

    @Test
    void initRejectsNegativeOverlap() {
        assertThatThrownBy(() -> new ConcreteChunker(100, -1, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("chunk_overlap must be greater than or equal to 0, current value: -1");
    }

    @Test
    void initRejectsOverlapAtOrAboveChunkSize() {
        assertThatThrownBy(() -> new ConcreteChunker(100, 100, null))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("chunk_overlap must be less than chunk_size");
    }

    @Test
    void chunkTextReturnsStringChunks() {
        ConcreteChunker chunker = new ConcreteChunker();

        assertThat(chunker.chunkText("This is a test text for chunking"))
                .isNotEmpty()
                .allMatch(String.class::isInstance);
    }

    @Test
    void chunkDocumentsAddsChunkIndexAndTotalCount() {
        ConcreteChunker chunker = new ConcreteChunker();
        List<TextChunk> chunks = chunker.chunkDocuments(List.of(
                new Document("doc_1", "This is document 1"),
                new Document("doc_2", "This is document 2")
        ));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allMatch(chunk -> List.of("doc_1", "doc_2").contains(chunk.getDocId()));
        assertThat(chunks).allMatch(chunk -> chunk.getMetadata().containsKey("chunk_index"));
        assertThat(chunks).allMatch(chunk -> chunk.getMetadata().containsKey("total_chunks"));
    }

    @Test
    void chunkDocumentsPreservesSourceMetadata() {
        ConcreteChunker chunker = new ConcreteChunker();
        List<TextChunk> chunks = chunker.chunkDocuments(List.of(
                new Document("doc_1", "This is document 1", Map.of("source", "test", "author", "tester"))
        ));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allMatch(chunk -> "test".equals(chunk.getMetadata().get("source")));
    }

    @Test
    void processDelegatesToChunkDocuments() {
        ConcreteChunker chunker = new ConcreteChunker();

        List<TextChunk> chunks = chunker.process(List.of(new Document("doc_1", "Test document"))).join();

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allMatch(chunk -> "doc_1".equals(chunk.getDocId()));
    }

    private static final class ConcreteChunker extends Chunker {

        private ConcreteChunker() {
            super();
        }

        private ConcreteChunker(int chunkSize, int chunkOverlap, ToIntFunction<String> lengthFunction) {
            super(chunkSize, chunkOverlap, lengthFunction);
        }

        @Override
        public List<String> chunkText(String text) {
            List<String> chunks = new java.util.ArrayList<>();
            for (int i = 0; i < text.length(); i += 10) {
                chunks.add(text.substring(i, Math.min(i + 10, text.length())));
            }
            return chunks;
        }
    }
}
