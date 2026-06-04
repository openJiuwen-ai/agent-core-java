/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.CharChunker;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.HybridChunker;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_hybrid_chunker.py.
 */
class TestHybridChunker {

    @Test
    void testRowSourceType() {
        assertTrue(HybridChunker.defaultNoSplit(new Document("1", "a", Map.of("source_type", "row"))));
    }

    @Test
    void testColumnSourceType() {
        assertTrue(HybridChunker.defaultNoSplit(new Document("1", "a", Map.of("source_type", "column"))));
    }

    @Test
    void testOtherSourceType() {
        assertFalse(HybridChunker.defaultNoSplit(new Document("1", "a", Map.of("source_type", "paragraph"))));
    }

    @Test
    void testNoSourceType() {
        assertFalse(HybridChunker.defaultNoSplit(new Document("1", "a", Map.of("title", "hello"))));
    }

    @Test
    void testEmptyMetadata() {
        assertFalse(HybridChunker.defaultNoSplit(new Document("1", "a", Map.of())));
    }

    @Test
    void testInitInheritsInnerParams() {
        HybridChunker chunker = new HybridChunker(makeInner(256, 30));

        assertEquals(256, chunker.getChunkSize());
        assertEquals(30, chunker.getChunkOverlap());
    }

    @Test
    void testChunkTextDelegatesToInner() {
        CharChunker inner = makeInner(10, 2);
        HybridChunker chunker = new HybridChunker(inner);

        assertEquals(inner.chunkText("hello world, this is a test"), chunker.chunkText("hello world, this is a test"));
    }

    @Test
    void testRowDocSingleChunk() {
        HybridChunker chunker = new HybridChunker(makeInner(10, 2));
        Document doc = new Document("row1", "row content", Map.of("source_type", "row", "sheet_name", "Sheet1"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(doc));

        assertEquals(1, chunks.size());
        assertEquals("row1", chunks.getFirst().getDocId());
        assertEquals("row content", chunks.getFirst().getText());
        assertEquals("row", chunks.getFirst().getMetadata().get("source_type"));
        assertEquals(0, chunks.getFirst().getMetadata().get("chunk_index"));
        assertEquals(1, chunks.getFirst().getMetadata().get("total_chunks"));
    }

    @Test
    void testColumnDocSingleChunk() {
        HybridChunker chunker = new HybridChunker(makeInner(10, 2));
        Document doc = new Document("col1", "column content", Map.of("source_type", "column"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(doc));

        assertEquals(1, chunks.size());
        assertEquals("col1", chunks.getFirst().getDocId());
        assertEquals("column", chunks.getFirst().getMetadata().get("source_type"));
    }

    @Test
    void testNormalDocDelegatesToInner() {
        HybridChunker chunker = new HybridChunker(makeInner(10, 2));
        Document doc = new Document(
                "doc1",
                "This is a long document that should be split into multiple chunks by the inner chunker",
                Map.of("title", "test"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(doc));

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> "doc1".equals(chunk.getDocId())));
    }

    @Test
    void testMixedDocuments() {
        HybridChunker chunker = new HybridChunker(makeInner(10, 2));
        List<Document> docs = List.of(
                new Document("row1", "row content", Map.of("source_type", "row")),
                new Document("doc1", "This is a long document that should be split", Map.of()),
                new Document("col1", "column content", Map.of("source_type", "column")));

        List<TextChunk> chunks = chunker.chunkDocuments(docs);

        long rowChunks = chunks.stream().filter(chunk -> "row1".equals(chunk.getDocId())).count();
        long colChunks = chunks.stream().filter(chunk -> "col1".equals(chunk.getDocId())).count();
        long docChunks = chunks.stream().filter(chunk -> "doc1".equals(chunk.getDocId())).count();
        assertEquals(1, rowChunks);
        assertEquals(1, colChunks);
        assertTrue(docChunks > 1);
    }

    @Test
    void testEmptyTextRowDocDelegatesToInner() {
        CharChunker inner = makeInner(512, 50);
        HybridChunker chunker = new HybridChunker(inner);
        Document doc = new Document("row1", "   ", Map.of("source_type", "row"));

        assertEquals(inner.chunkDocuments(List.of(doc)).size(), chunker.chunkDocuments(List.of(doc)).size());
    }

    @Test
    void testEmptyStringTextRowDoc() {
        CharChunker inner = makeInner(512, 50);
        HybridChunker chunker = new HybridChunker(inner);
        Document doc = new Document("row1", "", Map.of("source_type", "row"));

        assertEquals(inner.chunkDocuments(List.of(doc)).size(), chunker.chunkDocuments(List.of(doc)).size());
    }

    @Test
    void testCustomNoSplitWhen() {
        HybridChunker chunker = new HybridChunker(
                makeInner(10, 2),
                doc -> Boolean.TRUE.equals((doc.getMetadata() == null ? Map.of() : doc.getMetadata()).get("keep_whole")));
        Document docKeep = new Document("a", "short text", Map.of("keep_whole", true));
        Document docSplit = new Document(
                "b",
                "This is a long document that should be split by the inner chunker",
                Map.of("source_type", "row"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(docKeep, docSplit));

        long keepChunks = chunks.stream().filter(chunk -> "a".equals(chunk.getDocId())).count();
        long splitChunks = chunks.stream().filter(chunk -> "b".equals(chunk.getDocId())).count();
        assertEquals(1, keepChunks);
        assertTrue(splitChunks > 1);
    }

    @Test
    void testMetadataPreservedInSingleChunk() {
        HybridChunker chunker = new HybridChunker(makeInner(512, 50));
        Document doc = new Document(
                "row1",
                "content",
                Map.of("source_type", "row", "sheet_name", "S1", "custom_key", "custom_val"));

        List<TextChunk> chunks = chunker.chunkDocuments(List.of(doc));
        Map<String, Object> metadata = chunks.getFirst().getMetadata();

        assertEquals(1, chunks.size());
        assertEquals("row", metadata.get("source_type"));
        assertEquals("S1", metadata.get("sheet_name"));
        assertEquals("custom_val", metadata.get("custom_key"));
        assertEquals(0, metadata.get("chunk_index"));
        assertEquals(1, metadata.get("total_chunks"));
        assertTrue(metadata.containsKey("chunk_id"));
    }

    @Test
    void testEmptyDocumentsList() {
        HybridChunker chunker = new HybridChunker(makeInner(512, 50));

        assertEquals(List.of(), chunker.chunkDocuments(List.of()));
    }

    private static CharChunker makeInner(int chunkSize, int chunkOverlap) {
        return new CharChunker(chunkSize, chunkOverlap);
    }
}
