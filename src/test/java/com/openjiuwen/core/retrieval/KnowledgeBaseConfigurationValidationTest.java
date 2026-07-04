/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.IndexConfig;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code TestKnowledgeBaseConfigurationValidation} in
 * {@code tests/unit_tests/core/retrieval/test_knowledge_base_validation.py}.
 */
class KnowledgeBaseConfigurationValidationTest {

    @Test
    void validationPassesWhenAllAttributesMatch() {
        TestKnowledgeBase knowledgeBase = new TestKnowledgeBase(
                config(),
                TestVectorStore.compatible(),
                TestIndexer.compatible()
        );

        assertSame(knowledgeBase.getVectorStore(), knowledgeBase.getVectorStore());
        assertSame(knowledgeBase.getIndexManager(), knowledgeBase.getIndexManager());
    }

    @Test
    void validationSkippedWhenVectorStoreIsNone() {
        TestIndexer indexer = TestIndexer.partial("test_value");

        TestKnowledgeBase knowledgeBase = new TestKnowledgeBase(config(), null, indexer);

        assertNull(knowledgeBase.getVectorStore());
        assertSame(indexer, knowledgeBase.getIndexManager());
    }

    @Test
    void validationSkippedWhenIndexManagerIsNone() {
        TestVectorStore vectorStore = TestVectorStore.partial("test_value");

        TestKnowledgeBase knowledgeBase = new TestKnowledgeBase(config(), vectorStore, null);

        assertSame(vectorStore, knowledgeBase.getVectorStore());
        assertNull(knowledgeBase.getIndexManager());
    }

    @Test
    void validationRunsWhenSettingVectorStoreAfterIndexManager() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        TestKnowledgeBase knowledgeBase = new TestKnowledgeBase(config(), null, indexer);

        knowledgeBase.setVectorStore(vectorStore);

        assertSame(vectorStore, knowledgeBase.getVectorStore());
    }

    @Test
    void validationRunsWhenSettingIndexManagerAfterVectorStore() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        TestKnowledgeBase knowledgeBase = new TestKnowledgeBase(config(), vectorStore, null);

        knowledgeBase.setIndexManager(indexer);

        assertSame(indexer, knowledgeBase.getIndexManager());
    }

    @Test
    void validationFailsOnMismatchDatabaseName() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.databaseName = "db1";
        indexer.databaseName = "db2";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));

        assertErrorContains(error, "incompatible database_name configs");
    }

    @Test
    void validationFailsOnMismatchDistanceMetric() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.distanceMetric = "cosine";
        indexer.distanceMetric = "euclidean";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));

        assertErrorContains(error, "incompatible distance_metric configs");
    }

    @Test
    void validationFailsOnMismatchTextField() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.textField = "text";
        indexer.textField = "content";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));

        assertErrorContains(error, "incompatible text_field configs");
    }

    @Test
    void validationFailsOnMismatchVectorField() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.vectorField = "embedding";
        indexer.vectorField = "vector";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));

        assertErrorContains(error, "incompatible vector_field configs");
    }

    @Test
    void validationFailsOnMismatchSparseVectorField() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.sparseVectorField = "sparse_embedding";
        indexer.sparseVectorField = "bm25_vector";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));

        assertErrorContains(error, "incompatible sparse_vector_field configs");
    }

    @Test
    void validationFailsOnMismatchMetadataField() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.metadataField = "meta";
        indexer.metadataField = "metadata";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));

        assertErrorContains(error, "incompatible metadata_field configs");
    }

    @Test
    void validationFailsOnMismatchDocIdField() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.docIdField = "document_id";
        indexer.docIdField = "doc_id";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));

        assertErrorContains(error, "incompatible doc_id_field configs");
    }

    @Test
    void validationErrorMessageIncludesTypeNames() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.databaseName = "db1";
        indexer.databaseName = "db2";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));
        String message = String.valueOf(error.getMessage());

        assertErrorContains(error, "TestVectorStore");
        assertErrorContains(error, "Index manager");
        assertErrorContains(error, "db1");
        assertErrorContains(error, "db2");
        assertEquals(message, error.getMessage());
    }

    @Test
    void validationPassesWhenAttributesAreNone() {
        TestVectorStore vectorStore = TestVectorStore.allNull();
        TestIndexer indexer = TestIndexer.allNull();

        TestKnowledgeBase knowledgeBase = new TestKnowledgeBase(config(), vectorStore, indexer);

        assertSame(vectorStore, knowledgeBase.getVectorStore());
        assertSame(indexer, knowledgeBase.getIndexManager());
    }

    @Test
    void validationFailsOnFirstMismatch() {
        TestVectorStore vectorStore = TestVectorStore.compatible();
        TestIndexer indexer = TestIndexer.compatible();
        vectorStore.databaseName = "db1";
        indexer.databaseName = "db2";
        vectorStore.distanceMetric = "metric1";
        indexer.distanceMetric = "metric2";

        BaseError error = assertThrows(BaseError.class, () -> new TestKnowledgeBase(config(), vectorStore, indexer));

        assertErrorContains(error, "incompatible database_name configs");
    }

    private static void assertErrorContains(BaseError error, String expected) {
        String message = String.valueOf(error.getMessage());
        if (!message.contains(expected)) {
            throw new AssertionError("Expected error message to contain '" + expected + "' but was: " + message);
        }
    }

    private static KnowledgeBaseConfig config() {
        return KnowledgeBaseConfig.builder()
                .kbId("test_kb")
                .build();
    }

    private static final class TestKnowledgeBase extends KnowledgeBase {

        private TestKnowledgeBase(KnowledgeBaseConfig config, VectorStore vectorStore, Indexer indexManager) {
            super(config, vectorStore, null, null, null, null, indexManager, null);
        }

        @Override
        public CompletableFuture<List<String>> addDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(documents.stream().map(Document::getId_).toList());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> retrieve(
                String query,
                RetrievalConfig config,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> deleteDocuments(List<String> docIds, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<List<String>> updateDocuments(List<Document> documents, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(documents.stream().map(Document::getId_).toList());
        }

        @Override
        protected CompletableFuture<Map<String, Object>> getStatisticsAsync() {
            return CompletableFuture.completedFuture(Map.of("kb_id", getConfig().getKbId()));
        }
    }

    private static final class TestVectorStore implements VectorStore {
        private String databaseName;
        private String distanceMetric;
        private String textField;
        private String vectorField;
        private String sparseVectorField;
        private String metadataField;
        private String docIdField;

        private static TestVectorStore compatible() {
            TestVectorStore store = new TestVectorStore();
            store.databaseName = "test_database_name";
            store.distanceMetric = "test_distance_metric";
            store.textField = "test_text_field";
            store.vectorField = "test_vector_field";
            store.sparseVectorField = "test_sparse_vector_field";
            store.metadataField = "test_metadata_field";
            store.docIdField = "test_doc_id_field";
            return store;
        }

        private static TestVectorStore partial(String value) {
            TestVectorStore store = new TestVectorStore();
            store.databaseName = value;
            store.distanceMetric = value;
            return store;
        }

        private static TestVectorStore allNull() {
            return new TestVectorStore();
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getDistanceMetric() {
            return distanceMetric;
        }

        public String getTextField() {
            return textField;
        }

        public String getVectorField() {
            return vectorField;
        }

        public String getSparseVectorField() {
            return sparseVectorField;
        }

        public String getMetadataField() {
            return metadataField;
        }

        public String getDocIdField() {
            return docIdField;
        }

        @Override
        public void checkVectorField() {
        }

        @Override
        public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> search(
                List<Double> queryVector,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> sparseSearch(
                String queryText,
                int topK,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<List<RetrievalResult>> hybridSearch(
                String queryText,
                List<Double> queryVector,
                int topK,
                double alpha,
                VectorStoreFilter filters,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> delete(List<String> ids, DeleteFilter filterExpr, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> tableExists(String tableName) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        @Override
        public CompletableFuture<Void> deleteTable(String tableName) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class TestIndexer extends Indexer {
        private String databaseName;
        private String distanceMetric;
        private String textField;
        private String vectorField;
        private String sparseVectorField;
        private String metadataField;
        private String docIdField;

        private static TestIndexer compatible() {
            TestIndexer indexer = new TestIndexer();
            indexer.databaseName = "test_database_name";
            indexer.distanceMetric = "test_distance_metric";
            indexer.textField = "test_text_field";
            indexer.vectorField = "test_vector_field";
            indexer.sparseVectorField = "test_sparse_vector_field";
            indexer.metadataField = "test_metadata_field";
            indexer.docIdField = "test_doc_id_field";
            return indexer;
        }

        private static TestIndexer partial(String value) {
            TestIndexer indexer = new TestIndexer();
            indexer.databaseName = value;
            indexer.distanceMetric = value;
            return indexer;
        }

        private static TestIndexer allNull() {
            return new TestIndexer();
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public String getDistanceMetric() {
            return distanceMetric;
        }

        public String getTextField() {
            return textField;
        }

        public String getVectorField() {
            return vectorField;
        }

        public String getSparseVectorField() {
            return sparseVectorField;
        }

        public String getMetadataField() {
            return metadataField;
        }

        public String getDocIdField() {
            return docIdField;
        }

        @Override
        public CompletableFuture<Boolean> buildIndex(
                List<TextChunk> chunks,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> updateIndex(
                List<TextChunk> chunks,
                String docId,
                IndexConfig config,
                Embedding embedModel,
                Map<String, Object> kwargs
        ) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> deleteIndex(String docId, String indexName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> indexExists(String indexName) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getIndexInfo(String indexName) {
            return CompletableFuture.completedFuture(Map.of());
        }
    }
}
