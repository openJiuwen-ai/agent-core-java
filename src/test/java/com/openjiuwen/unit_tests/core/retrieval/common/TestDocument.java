/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.common;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.common.exception.BaseError;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Document data model test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/common/test_document.py
 */
class TestDocument {

    @Test
    void testCreateDocument() {
        // Test creating document
        Document doc = new Document("Test document");
        assertEquals("Test document", doc.getText());
        assertNotNull(doc.getId());
        assertTrue(doc.getMetadata().isEmpty());
    }

    @Test
    void testCreateDocumentWithMetadata() {
        // Test creating document with metadata
        Map<String, Object> metadata = Map.of("source", "test", "author", "test_author");
        Document doc = new Document(null, "Test document", metadata);
        assertEquals("Test document", doc.getText());
        assertEquals(metadata, doc.getMetadata());
    }

    @Test
    void testCreateDocumentWithId() {
        // Test creating document with ID
        Document doc = new Document("test_id", "Test document");
        assertEquals("test_id", doc.getId());
        assertEquals("Test document", doc.getText());
    }

    @Test
    void testMissingText() {
        // Test missing required text
        assertThrows(BaseError.class, () -> new Document(null, null, null));
    }
}

/**
 * Text chunk data model test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/common/test_document.py
 */
class TestTextChunk {

    @Test
    void testCreateTextChunk() {
        // Test creating text chunk
        TextChunk chunk = new TextChunk("chunk_1", "Test chunk", "doc_1");
        assertEquals("chunk_1", chunk.getId());
        assertEquals("Test chunk", chunk.getText());
        assertEquals("doc_1", chunk.getDocId());
        assertTrue(chunk.getMetadata().isEmpty());
        assertNull(chunk.getEmbedding());
    }

    @Test
    void testCreateTextChunkWithMetadata() {
        // Test creating text chunk with metadata
        Map<String, Object> metadata = Map.of("chunk_index", 0, "source", "test");
        TextChunk chunk = new TextChunk("chunk_1", "Test chunk", "doc_1", metadata, null);
        assertEquals(metadata, chunk.getMetadata());
    }

    @Test
    void testCreateTextChunkWithEmbedding() {
        // Test creating text chunk with embedding
        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);
        TextChunk chunk = new TextChunk("chunk_1", "Test chunk", "doc_1", null, embedding);
        assertEquals(embedding, chunk.getEmbedding());
    }

    @Test
    void testFromDocument() {
        // Test creating text chunk from document
        Map<String, Object> docMetadata = Map.of("source", "test");
        Document doc = new Document("doc_1", "Test document", docMetadata);
        TextChunk chunk = TextChunk.fromDocument(doc, "Test chunk", "chunk_1");
        assertEquals("chunk_1", chunk.getId());
        assertEquals("Test chunk", chunk.getText());
        assertEquals("doc_1", chunk.getDocId());
        assertEquals(docMetadata, chunk.getMetadata());
    }

    @Test
    void testFromDocumentWithoutId() {
        // Test creating text chunk from document (auto-generate ID)
        Document doc = new Document("doc_1", "Test document");
        TextChunk chunk = TextChunk.fromDocument(doc, "Test chunk");
        assertNotNull(chunk.getId());
        assertEquals("Test chunk", chunk.getText());
        assertEquals("doc_1", chunk.getDocId());
    }

    @Test
    void testMissingRequiredFieldsId() {
        // Test missing required fields - id
        assertThrows(BaseError.class, () -> new TextChunk(null, null, null));
    }

    @Test
    void testMissingRequiredFieldsText() {
        // Test missing required fields - text (id provided)
        assertThrows(BaseError.class, () -> new TextChunk("chunk_1", null, "doc_1"));
    }

    @Test
    void testMissingRequiredFieldsDocId() {
        // Test missing required fields - doc_id (id and text provided)
        assertThrows(BaseError.class, () -> new TextChunk("chunk_1", "Test chunk", null));
    }
}
