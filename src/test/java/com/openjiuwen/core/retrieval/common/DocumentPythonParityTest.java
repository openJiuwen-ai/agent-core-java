/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.ValidationError;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestDocument} and {@code TestTextChunk} tests in
 * {@code tests/unit_tests/core/retrieval/common/test_document.py}.
 */
class DocumentPythonParityTest {

    @Test
    void createDocument() {
        Document doc = new Document("Test document");

        assertThat(doc.getText()).isEqualTo("Test document");
        assertThat(doc.getId_()).isNotNull();
        assertThat(doc.getMetadata()).isEmpty();
    }

    @Test
    void createDocumentWithMetadata() {
        Map<String, Object> metadata = Map.of("source", "test", "author", "test_author");

        Document doc = new Document(null, "Test document", metadata);

        assertThat(doc.getText()).isEqualTo("Test document");
        assertThat(doc.getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
    }

    @Test
    void createDocumentWithId() {
        Document doc = new Document("test_id", "Test document");

        assertThat(doc.getId_()).isEqualTo("test_id");
        assertThat(doc.getText()).isEqualTo("Test document");
    }

    @Test
    void missingTextRaisesValidationError() {
        assertThatThrownBy(Document::new).isInstanceOf(ValidationError.class);
    }

    @Test
    void createTextChunk() {
        TextChunk chunk = new TextChunk("chunk_1", "Test chunk", "doc_1");

        assertThat(chunk.getId_()).isEqualTo("chunk_1");
        assertThat(chunk.getText()).isEqualTo("Test chunk");
        assertThat(chunk.getDocId()).isEqualTo("doc_1");
        assertThat(chunk.getMetadata()).isEmpty();
        assertThat(chunk.getEmbedding()).isNull();
    }

    @Test
    void createTextChunkWithMetadata() {
        Map<String, Object> metadata = Map.of("chunk_index", 0, "source", "test");

        TextChunk chunk = new TextChunk("chunk_1", "Test chunk", "doc_1", metadata);

        assertThat(chunk.getMetadata()).containsExactlyInAnyOrderEntriesOf(metadata);
    }

    @Test
    void createTextChunkWithEmbedding() {
        List<Double> embedding = List.of(0.1, 0.2, 0.3);

        TextChunk chunk = new TextChunk("chunk_1", "Test chunk", "doc_1", null, embedding);

        assertThat(chunk.getEmbedding()).containsExactlyElementsOf(embedding);
    }

    @Test
    void fromDocument() {
        Document doc = new Document("doc_1", "Test document", Map.of("source", "test"));

        TextChunk chunk = TextChunk.fromDocument(doc, "Test chunk", "chunk_1");

        assertThat(chunk.getId_()).isEqualTo("chunk_1");
        assertThat(chunk.getText()).isEqualTo("Test chunk");
        assertThat(chunk.getDocId()).isEqualTo("doc_1");
        assertThat(chunk.getMetadata()).containsExactly(Map.entry("source", "test"));
    }

    @Test
    void fromDocumentWithoutId() {
        Document doc = new Document("doc_1", "Test document");

        TextChunk chunk = TextChunk.fromDocument(doc, "Test chunk");

        assertThat(chunk.getId_()).isNotNull();
        assertThat(chunk.getText()).isEqualTo("Test chunk");
        assertThat(chunk.getDocId()).isEqualTo("doc_1");
    }

    @Test
    void missingRequiredFieldsRaiseValidationError() {
        assertThatThrownBy(TextChunk::new).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new TextChunk("chunk_1", null, null)).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> new TextChunk("chunk_1", "Test chunk", null)).isInstanceOf(ValidationError.class);
    }
}
