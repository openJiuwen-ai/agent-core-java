/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.retrieval;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.KnowledgeBase;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Knowledge base configuration validation test cases.
 *
 * <p>Mirrors Python's {@code test_knowledge_base_validation.py} in
 * {@code tests/unit_tests/core/retrieval/test_knowledge_base_validation.py}.</p>
 */
@DisplayName("KnowledgeBase Configuration Validation Tests")
class TestKnowledgeBaseValidation {

    @Test
    @DisplayName("test_validation_passes_when_all_attributes_match")
    void testValidationPassesWhenAllAttributesMatch() {
        VectorStore vectorStore = mock(VectorStore.class);
        Indexer indexManager = mock(Indexer.class);
        stubCompatible(vectorStore, indexManager);

        ConcreteKnowledgeBase kb = new ConcreteKnowledgeBase(config(), vectorStore, indexManager);

        assertThat(kb.getVectorStore()).isSameAs(vectorStore);
        assertThat(kb.getIndexManager()).isSameAs(indexManager);
    }

    @Test
    @DisplayName("test_validation_skipped_when_vector_store_is_none")
    void testValidationSkippedWhenVectorStoreIsNone() {
        Indexer indexManager = mock(Indexer.class);
        when(indexManager.getDatabaseName()).thenReturn("test_value");
        when(indexManager.getDistanceMetric()).thenReturn("test_value");
        when(indexManager.getIndexType()).thenReturn("test_value");

        ConcreteKnowledgeBase kb = new ConcreteKnowledgeBase(config(), null, indexManager);

        assertThat(kb.getVectorStore()).isNull();
        assertThat(kb.getIndexManager()).isSameAs(indexManager);
    }

    @Test
    @DisplayName("test_validation_skipped_when_index_manager_is_none")
    void testValidationSkippedWhenIndexManagerIsNone() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.getDatabaseName()).thenReturn("test_value");
        when(vectorStore.getDistanceMetric()).thenReturn("test_value");
        when(vectorStore.getIndexType()).thenReturn("test_value");

        ConcreteKnowledgeBase kb = new ConcreteKnowledgeBase(config(), vectorStore, null);

        assertThat(kb.getVectorStore()).isSameAs(vectorStore);
    }

    @Test
    @DisplayName("test_validation_runs_when_setting_vector_store_after_index_manager")
    void testValidationRunsWhenSettingVectorStoreAfterIndexManager() {
        VectorStore vectorStore = mock(VectorStore.class);
        Indexer indexManager = mock(Indexer.class);
        stubCompatible(vectorStore, indexManager);

        ConcreteKnowledgeBase kb = new ConcreteKnowledgeBase(config(), null, indexManager);
        kb.setVectorStore(vectorStore);

        assertThat(kb.getVectorStore()).isSameAs(vectorStore);
    }

    @Test
    @DisplayName("test_validation_runs_when_setting_index_manager_after_vector_store")
    void testValidationRunsWhenSettingIndexManagerAfterVectorStore() {
        VectorStore vectorStore = mock(VectorStore.class);
        Indexer indexManager = mock(Indexer.class);
        stubCompatible(vectorStore, indexManager);

        ConcreteKnowledgeBase kb = new ConcreteKnowledgeBase(config(), vectorStore, null);
        kb.setIndexManager(indexManager);

        assertThat(kb.getIndexManager()).isSameAs(indexManager);
    }

    @Test
    @DisplayName("test_validation_fails_on_mismatch_database_name")
    void testValidationFailsOnMismatchDatabaseName() {
        assertMismatch("database_name", "db1", "db2", "incompatible database_name configs");
    }

    @Test
    @DisplayName("test_validation_fails_on_mismatch_distance_metric")
    void testValidationFailsOnMismatchDistanceMetric() {
        assertMismatch("distance_metric", "cosine", "euclidean", "incompatible distance_metric configs");
    }

    @Test
    @DisplayName("test_validation_fails_on_mismatch_text_field")
    void testValidationFailsOnMismatchTextField() {
        assertMismatch("text_field", "text", "content", "incompatible text_field configs");
    }

    @Test
    @DisplayName("test_validation_fails_on_mismatch_vector_field")
    void testValidationFailsOnMismatchVectorField() {
        assertMismatch("vector_field", "embedding", "vector", "incompatible vector_field configs");
    }

    @Test
    @DisplayName("test_validation_fails_on_mismatch_sparse_vector_field")
    void testValidationFailsOnMismatchSparseVectorField() {
        assertMismatch("sparse_vector_field", "sparse_embedding", "bm25_vector",
                "incompatible sparse_vector_field configs");
    }

    @Test
    @DisplayName("test_validation_fails_on_mismatch_metadata_field")
    void testValidationFailsOnMismatchMetadataField() {
        assertMismatch("metadata_field", "meta", "metadata", "incompatible metadata_field configs");
    }

    @Test
    @DisplayName("test_validation_fails_on_mismatch_doc_id_field")
    void testValidationFailsOnMismatchDocIdField() {
        assertMismatch("doc_id_field", "document_id", "doc_id", "incompatible doc_id_field configs");
    }

    @Test
    @DisplayName("test_validation_error_message_includes_type_names")
    void testValidationErrorMessageIncludesTypeNames() {
        VectorStore vectorStore = mock(VectorStore.class);
        Indexer indexManager = mock(Indexer.class);
        stubCompatible(vectorStore, indexManager);
        when(vectorStore.getDatabaseName()).thenReturn("db1");
        when(indexManager.getDatabaseName()).thenReturn("db2");

        assertThatThrownBy(() -> new ConcreteKnowledgeBase(config(), vectorStore, indexManager))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("VectorStore")
                .hasMessageContaining("Indexer")
                .hasMessageContaining("db1")
                .hasMessageContaining("db2");
    }

    @Test
    @DisplayName("test_validation_passes_when_attributes_are_none")
    void testValidationPassesWhenAttributesAreNone() {
        VectorStore vectorStore = mock(VectorStore.class);
        Indexer indexManager = mock(Indexer.class);

        ConcreteKnowledgeBase kb = new ConcreteKnowledgeBase(config(), vectorStore, indexManager);

        assertThat(kb.getVectorStore()).isSameAs(vectorStore);
        assertThat(kb.getIndexManager()).isSameAs(indexManager);
    }

    @Test
    @DisplayName("test_validation_fails_on_first_mismatch")
    void testValidationFailsOnFirstMismatch() {
        VectorStore vectorStore = mock(VectorStore.class);
        Indexer indexManager = mock(Indexer.class);
        stubCompatible(vectorStore, indexManager);
        when(vectorStore.getDatabaseName()).thenReturn("db1");
        when(indexManager.getDatabaseName()).thenReturn("db2");
        when(vectorStore.getDistanceMetric()).thenReturn("metric1");
        when(indexManager.getDistanceMetric()).thenReturn("metric2");

        assertThatThrownBy(() -> new ConcreteKnowledgeBase(config(), vectorStore, indexManager))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("incompatible database_name configs")
                .satisfies(error -> assertThat(error.getMessage())
                        .doesNotContain("incompatible distance_metric configs"));
    }

    private static void assertMismatch(String field, String vectorValue, String indexValue, String expectedMessage) {
        VectorStore vectorStore = mock(VectorStore.class);
        Indexer indexManager = mock(Indexer.class);
        stubCompatible(vectorStore, indexManager);
        stubField(vectorStore, indexManager, field, vectorValue, indexValue);

        assertThatThrownBy(() -> new ConcreteKnowledgeBase(config(), vectorStore, indexManager))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining(expectedMessage);
    }

    private static KnowledgeBaseConfig config() {
        return new KnowledgeBaseConfig("test_kb");
    }

    private static void stubCompatible(VectorStore vectorStore, Indexer indexManager) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("database_name", "test_database_name");
        values.put("distance_metric", "test_distance_metric");
        values.put("index_type", "test_index_type");
        values.put("text_field", "test_text_field");
        values.put("vector_field", "test_vector_field");
        values.put("sparse_vector_field", "test_sparse_vector_field");
        values.put("metadata_field", "test_metadata_field");
        values.put("doc_id_field", "test_doc_id_field");
        for (Map.Entry<String, String> entry : values.entrySet()) {
            stubField(vectorStore, indexManager, entry.getKey(), entry.getValue(), entry.getValue());
        }
    }

    private static void stubField(VectorStore vectorStore, Indexer indexManager,
                                  String field, String vectorValue, String indexValue) {
        switch (field) {
            case "database_name" -> {
                when(vectorStore.getDatabaseName()).thenReturn(vectorValue);
                when(indexManager.getDatabaseName()).thenReturn(indexValue);
            }
            case "distance_metric" -> {
                when(vectorStore.getDistanceMetric()).thenReturn(vectorValue);
                when(indexManager.getDistanceMetric()).thenReturn(indexValue);
            }
            case "index_type" -> {
                when(vectorStore.getIndexType()).thenReturn(vectorValue);
                when(indexManager.getIndexType()).thenReturn(indexValue);
            }
            case "text_field" -> {
                when(vectorStore.getTextField()).thenReturn(vectorValue);
                when(indexManager.getTextField()).thenReturn(indexValue);
            }
            case "vector_field" -> {
                when(vectorStore.getVectorField()).thenReturn(vectorValue);
                when(indexManager.getVectorField()).thenReturn(indexValue);
            }
            case "sparse_vector_field" -> {
                when(vectorStore.getSparseVectorField()).thenReturn(vectorValue);
                when(indexManager.getSparseVectorField()).thenReturn(indexValue);
            }
            case "metadata_field" -> {
                when(vectorStore.getMetadataField()).thenReturn(vectorValue);
                when(indexManager.getMetadataField()).thenReturn(indexValue);
            }
            case "doc_id_field" -> {
                when(vectorStore.getDocIdField()).thenReturn(vectorValue);
                when(indexManager.getDocIdField()).thenReturn(indexValue);
            }
            default -> throw new IllegalArgumentException("Unsupported field: " + field);
        }
    }

    private static final class ConcreteKnowledgeBase extends KnowledgeBase {

        private ConcreteKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Indexer indexManager) {
            super(config, vectorStore, null, null, null, null, indexManager, null, null);
        }

        @Override
        public List<String> addDocuments(List<Document> documents) {
            return documents.stream().map(Document::getId).toList();
        }

        @Override
        public List<RetrievalResult> retrieve(String query, RetrievalConfig config) {
            return List.of();
        }

        @Override
        public boolean deleteDocuments(List<String> docIds) {
            return true;
        }

        @Override
        public List<String> updateDocuments(List<Document> documents) {
            return documents.stream().map(Document::getId).toList();
        }

        @Override
        public Map<String, Object> getStatistics() {
            return Map.of("kb_id", getConfig().getKbId());
        }
    }
}
