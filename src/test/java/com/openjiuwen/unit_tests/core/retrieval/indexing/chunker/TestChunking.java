/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.PreprocessingPipeline;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.TextChunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.TokenizerChunker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TextChunker.
 *
 * <p>Mirrors Python's {@code test_chunking.py} text chunker tests.</p>
 */
class TestChunking {

    @Test
    @DisplayName("Test initialization with character unit")
    void testInitWithCharUnit() {
        TextChunker chunker = new TextChunker(512, 50, "char");

        assertEquals(512, chunker.getChunkSize());
        assertEquals(50, chunker.getChunkOverlap());
        assertEquals(
                chunker.getChunker().getClass(),
                chunker.getChunker(512, 50, "char", null).getClass());
    }

    @Test
    @DisplayName("Test initialization with token unit but tokenizer fallback unavailable")
    void testInitWithTokenUnitNoTiktoken() {
        BaseError error = assertThrows(
                BaseError.class,
                () -> new TextChunker(512, 50, "token", null, null, false));

        assertTrue(error.getMessage().contains("requires embed_model with tokenizer or tiktoken"));
    }

    @Test
    @DisplayName("Test initialization with preprocess options")
    void testInitWithPreprocessOptions() throws ReflectiveOperationException {
        TextChunker chunker = new TextChunker(
                512,
                50,
                "char",
                Map.of("normalize_whitespace", true, "remove_url_email", true));

        assertEquals(2, preprocessorCount(chunker));
    }

    @Test
    @DisplayName("Test initialization with whitespace normalization")
    void testInitWithNormalizeWhitespace() throws ReflectiveOperationException {
        TextChunker chunker = new TextChunker(
                512,
                50,
                "char",
                Map.of("normalize_whitespace", true));

        assertEquals(1, preprocessorCount(chunker));
    }

    @Test
    @DisplayName("Test initialization with URL/email removal")
    void testInitWithRemoveUrlEmail() throws ReflectiveOperationException {
        TextChunker chunker = new TextChunker(
                512,
                50,
                "char",
                Map.of("remove_url_email", true));

        assertEquals(1, preprocessorCount(chunker));
    }

    @Test
    @DisplayName("Test initialization without preprocess options")
    void testInitWithoutPreprocessOptions() throws ReflectiveOperationException {
        TextChunker chunker = new TextChunker(512, 50);

        assertEquals(0, preprocessorCount(chunker));
    }

    @Test
    @DisplayName("Test chunking documents with preprocessing")
    void testChunkDocumentsWithPreprocessing() {
        TextChunker chunker = new TextChunker(
                100,
                10,
                "char",
                Map.of("normalize_whitespace", true));
        List<Document> documents = List.of(new Document(
                "doc_1",
                "This   is   document   1",
                Map.of("source", "test")));

        List<TextChunk> chunks = chunker.chunkDocuments(documents);

        assertFalse(chunks.isEmpty());
        assertFalse(chunks.get(0).getText().contains("   "));
        assertTrue(chunks.stream().allMatch(chunk -> "doc_1".equals(chunk.getDocId())));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getMetadata().containsKey("chunk_index")));
    }

    @Test
    @DisplayName("Test chunking documents without preprocessing")
    void testChunkDocumentsWithoutPreprocessing() {
        TextChunker chunker = new TextChunker(100, 10);
        List<Document> documents = List.of(new Document(
                "doc_1",
                "This is document 1",
                Map.of("source", "test")));

        List<TextChunk> chunks = chunker.chunkDocuments(documents);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> "doc_1".equals(chunk.getDocId())));
    }

    @Test
    @DisplayName("Test chunking multiple documents")
    void testChunkDocumentsMultipleDocs() {
        TextChunker chunker = new TextChunker(100, 10);
        List<Document> documents = List.of(
                new Document("doc_1", "This is document 1"),
                new Document("doc_2", "This is document 2"));

        List<TextChunk> chunks = chunker.chunkDocuments(documents);

        assertFalse(chunks.isEmpty());
        Set<String> docIds = chunks.stream().map(TextChunk::getDocId).collect(java.util.stream.Collectors.toSet());
        assertTrue(docIds.contains("doc_1"));
        assertTrue(docIds.contains("doc_2"));
    }

    @Test
    @DisplayName("Test preserving metadata")
    void testChunkDocumentsPreservesMetadata() {
        TextChunker chunker = new TextChunker(100, 10);
        List<Document> documents = List.of(new Document(
                "doc_1",
                "This is document 1",
                Map.of("source", "test", "author", "test_author")));

        List<TextChunk> chunks = chunker.chunkDocuments(documents);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getMetadata().containsKey("source")));
        assertTrue(chunks.stream().allMatch(chunk -> "test".equals(chunk.getMetadata().get("source"))));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getMetadata().containsKey("author")));
    }

    @Test
    @DisplayName("Test getting character chunker")
    void testGetChunkerCharUnit() {
        TextChunker chunker = new TextChunker(512, 50);

        Chunker result = chunker.getChunker(512, 50, "char", null);

        assertInstanceOf(CharChunker.class, result);
    }

    @Test
    @DisplayName("Test token chunker automatically adjusts size")
    void testGetChunkerTokenUnitAdjustsSize() {
        MockEmbedModel mockEmbedModel = new MockEmbedModel(new MockTokenizer(256));
        TextChunker chunker = new TextChunker(512, 50);

        Chunker result = chunker.getChunker(512, 50, "token", mockEmbedModel);

        assertInstanceOf(TokenizerChunker.class, result);
        assertEquals(256, result.getChunkSize());
    }

    private static int preprocessorCount(TextChunker chunker) throws ReflectiveOperationException {
        Field preprocessors = PreprocessingPipeline.class.getDeclaredField("preprocessors");
        preprocessors.setAccessible(true);
        return ((List<?>) preprocessors.get(chunker.getPipeline())).size();
    }

    private static final class MockEmbedModel {

        private final MockTokenizer tokenizer;

        private MockEmbedModel(MockTokenizer tokenizer) {
            this.tokenizer = tokenizer;
        }
    }

    private static final class MockTokenizer {

        private final int model_max_length;

        private MockTokenizer(int modelMaxLength) {
            this.model_max_length = modelMaxLength;
        }
    }
}
