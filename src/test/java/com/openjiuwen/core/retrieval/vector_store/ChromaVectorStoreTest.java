/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.query.ChromaQueryLanguage;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import com.openjiuwen.core.foundation.store.query.QueryExpressions;
import com.openjiuwen.core.foundation.store.vector_fields.ChromaVectorField;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ChromaDB vector store tests.
 *
 * <p>Mirrors Python's {@code ChromaVectorStore} in
 * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}; focused cases mirror
 * {@code tests/unit_tests/core/retrieval/vector_store/test_chroma_store.py}.</p>
 */
class ChromaVectorStoreTest {

    @BeforeAll
    static void registerChromaQueryLanguage() {
        QueryExpr.registerLanguage("chroma", ChromaQueryLanguage.CHROMA_DEF, true);
    }

    @Test
    void constructorCreatesPersistentClientAndNormalizesDefaults() {
        ChromaVectorStore store = new ChromaVectorStore(
                config("test_collection", "euclidean"),
                "/tmp/test_chroma",
                "body",
                "vector",
                "sparse",
                "meta",
                "doc"
        );

        assertThat(store.getCollectionName()).isEqualTo("test_collection");
        assertThat(store.getChromaPath()).isEqualTo("/tmp/test_chroma");
        assertThat(store.getTextField()).isEqualTo("body");
        assertThat(store.getVectorField().getVectorField()).isEqualTo("vector");
        assertThat(store.getSparseVectorField()).isEqualTo("sparse");
        assertThat(store.getMetadataField()).isEqualTo("meta");
        assertThat(store.getDocIdField()).isEqualTo("doc");
        assertThat(store.getDistanceMetric()).isEqualTo("l2");
        assertThat(store.getConstructConfig()).containsEntry("space", "l2");
        assertThat(store.tableExists("test_collection").join()).isTrue();
    }

    @Test
    void constructorRejectsEmptyPathAndInvalidVectorField() {
        assertThatThrownBy(() -> new ChromaVectorStore(config("test_collection", "cosine"), " "))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_VECTOR_STORE_PATH_NOT_FOUND);

        assertThatThrownBy(() -> new ChromaVectorStore(
                config("test_collection", "cosine"),
                "/tmp/test_chroma",
                "content",
                42,
                "sparse_vector",
                "metadata",
                "document_id"
        ))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.RETRIEVAL_INDEXING_VECTOR_FIELD_INVALID);
    }

    @Test
    void checkVectorFieldAcceptsMatchingConfigAndIgnoresEfSearchFactor() {
        ChromaVectorField vectorField = chromaField(16, 200);
        Map<String, Object> actualConfig = hnswConfig(Map.of(
                "space", "cosine",
                "max_neighbors", 16,
                "ef_construction", 200,
                "ef_search", 100.0d,
                "efSearchFactor", 2.0d
        ));
        ChromaVectorStore store = new ChromaVectorStore(
                config("test_collection", "cosine"),
                "/tmp/test_chroma",
                "content",
                vectorField,
                "sparse_vector",
                "metadata",
                "document_id",
                new FixedClient(actualConfig)
        );

        store.checkVectorField();
    }

    @Test
    void checkVectorFieldRejectsMismatchedAndEmptyConfig() {
        ChromaVectorField vectorField = chromaField(16, 200);

        ChromaVectorStore mismatchedMaxNeighbors = new ChromaVectorStore(
                config("test_collection", "cosine"),
                "/tmp/test_chroma",
                "content",
                vectorField,
                "sparse_vector",
                "metadata",
                "document_id",
                new FixedClient(hnswConfig(Map.of("space", "cosine", "max_neighbors", 32, "ef_construction", 200)))
        );
        assertThatThrownBy(mismatchedMaxNeighbors::checkVectorField)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("database actual config differs from current knowledge base");

        ChromaVectorStore mismatchedDistance = new ChromaVectorStore(
                config("test_collection", "cosine"),
                "/tmp/test_chroma",
                "content",
                vectorField,
                "sparse_vector",
                "metadata",
                "document_id",
                new FixedClient(hnswConfig(Map.of("space", "l2", "max_neighbors", 16, "ef_construction", 200)))
        );
        assertThatThrownBy(mismatchedDistance::checkVectorField)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("database actual config differs from current knowledge base");

        ChromaVectorStore emptyConfig = new ChromaVectorStore(
                config("test_collection", "cosine"),
                "/tmp/test_chroma",
                "content",
                vectorField,
                "sparse_vector",
                "metadata",
                "document_id",
                new FixedClient(Map.of())
        );
        assertThatThrownBy(emptyConfig::checkVectorField)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("database actual config differs from current knowledge base");
    }

    @Test
    void addBatchesAndConvertsMetadataSparseVector() {
        ChromaVectorStore store = new ChromaVectorStore(config("test_collection", "cosine"), "/tmp/test_chroma");

        store.add(List.of(
                Map.of("id", "missing-vector", "content", "skip"),
                mapOf(
                        "id", "chunk-1",
                        "content", "alpha document",
                        "embedding", List.of(1.0d, 0.0d),
                        "metadata", "{\"source\":\"unit\",\"rank\":3}",
                        "document_id", "doc-1",
                        "chunk_id", "chunk-1",
                        "sparse_vector", Map.of("token", 1)
                )
        ), 1, Map.of()).join();

        List<RetrievalResult> results = store.search(
                List.of(1.0d, 0.0d),
                5,
                VectorStore.VectorStoreFilter.ofMap(Map.of("source", "unit")),
                Map.of()
        ).join();

        assertThat(results).hasSize(1);
        RetrievalResult result = results.getFirst();
        assertThat(result.getText()).isEqualTo("alpha document");
        assertThat(result.getScore()).isEqualTo(1.0d);
        assertThat(result.getDocId()).isEqualTo("doc-1");
        assertThat(result.getChunkId()).isEqualTo("chunk-1");
        assertThat(result.getMetadata())
                .containsEntry("doc_id", "doc-1")
                .containsEntry("source", "unit")
                .containsEntry("rank", 3)
                .containsEntry("raw_score", 0.0d)
                .containsEntry("raw_score_scaled", 1.0d);
        Map<?, ?> sparseVector = (Map<?, ?>) result.getMetadata().get("sparse_vector");
        assertThat(sparseVector.get("token")).isEqualTo(1);
    }

    @Test
    void searchSparseAndHybridMirrorChromaResultScoring() {
        ChromaVectorStore store = new ChromaVectorStore(config("test_collection", "dot"), "/tmp/test_chroma");
        store.add(List.of(
                mapOf("id", "chunk-1", "content", "alpha document", "embedding", List.of(1.0d, 0.0d),
                        "metadata", Map.of("source", "unit"), "document_id", "doc-1"),
                mapOf("id", "chunk-2", "content", "beta document", "embedding", List.of(0.0d, 1.0d),
                        "metadata", Map.of("source", "other"), "document_id", "doc-2")
        ), 128, Map.of()).join();

        List<RetrievalResult> sparse = store.sparseSearch(
                "alpha",
                5,
                VectorStore.VectorStoreFilter.none(),
                Map.of()
        ).join();
        assertThat(sparse).hasSize(2);
        assertThat(sparse.getFirst().getText()).isEqualTo("alpha document");
        assertThat(sparse.getFirst().getScore()).isEqualTo(1.0d);

        List<RetrievalResult> queryFiltered = store.search(
                List.of(1.0d, 0.0d),
                5,
                VectorStore.VectorStoreFilter.ofQuery(QueryExpressions.eq("source", "unit")),
                Map.of()
        ).join();
        assertThat(queryFiltered).extracting(RetrievalResult::getText).containsExactly("alpha document");

        List<RetrievalResult> hybrid = store.hybridSearch(
                "alpha",
                List.of(1.0d, 0.0d),
                1,
                0.5d,
                VectorStore.VectorStoreFilter.none(),
                Map.of()
        ).join();
        assertThat(hybrid).hasSize(1);
        assertThat(hybrid.getFirst().getText()).isEqualTo("alpha document");
        assertThat(hybrid.getFirst().getChunkId()).isEqualTo("chunk-1");
        assertThat(hybrid.getFirst().getMetadata()).doesNotContainKey("id");
    }

    @Test
    void deleteAndTableOperationsMirrorPythonReturnValues() {
        ChromaVectorStore store = new ChromaVectorStore(config("test_collection", "cosine"), "/tmp/test_chroma");
        store.add(List.of(
                mapOf("id", "chunk-1", "content", "alpha", "embedding", List.of(1.0d, 0.0d),
                        "metadata", Map.of("source", "unit")),
                mapOf("id", "chunk-2", "content", "beta", "embedding", List.of(0.0d, 1.0d),
                        "metadata", Map.of("source", "other"))
        ), 128, Map.of()).join();

        assertThat(store.delete(List.of("chunk-1"), VectorStore.DeleteFilter.none(), Map.of()).join()).isTrue();
        assertThat(store.search(List.of(1.0d, 0.0d), 5, VectorStore.VectorStoreFilter.none(), Map.of()).join())
                .extracting(RetrievalResult::getChunkId)
                .doesNotContain("chunk-1");

        assertThat(store.delete(null, VectorStore.DeleteFilter.ofQuery(QueryExpressions.eq("source", "other")), Map.of()).join())
                .isTrue();
        assertThat(store.search(List.of(1.0d, 0.0d), 5, VectorStore.VectorStoreFilter.none(), Map.of()).join())
                .isEmpty();

        assertThat(store.delete(List.of("chunk-2"), VectorStore.DeleteFilter.ofExpression("source == 'unit'"), Map.of()).join())
                .isFalse();
        assertThat(store.tableExists("test_collection").join()).isTrue();
        store.deleteTable("test_collection").join();
        assertThat(store.tableExists("test_collection").join()).isFalse();
    }

    private static VectorStoreConfig config(String collectionName, String metric) {
        return new VectorStoreConfig(StoreType.CHROMA, "test_db", collectionName, metric);
    }

    private static ChromaVectorField chromaField(int maxNeighbors, int efConstruction) {
        ChromaVectorField field = new ChromaVectorField();
        field.setVectorField("embedding");
        field.setMaxNeighbors(maxNeighbors);
        field.setEfConstruction(efConstruction);
        return field;
    }

    private static Map<String, Object> hnswConfig(Map<String, Object> hnsw) {
        return Map.of("hnsw", hnsw);
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    /**
     * Mirrors Python's mocked ChromaDB client in
     * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.
     */
    private static final class FixedClient implements ChromaVectorStore.ChromaClientAdapter {
        private final FixedCollection collection;

        private FixedClient(Map<String, Object> configuration) {
            this.collection = new FixedCollection(configuration);
        }

        @Override
        public ChromaVectorStore.ChromaCollectionAdapter getCollection(String name) {
            return collection;
        }

        @Override
        public ChromaVectorStore.ChromaCollectionAdapter getOrCreateCollection(String name, Map<String, Object> configuration) {
            return collection;
        }

        @Override
        public void deleteCollection(String name) {
        }

        @Override
        public List<ChromaVectorStore.ChromaCollectionAdapter> listCollections() {
            return List.of(collection);
        }
    }

    /**
     * Mirrors Python's mocked ChromaDB collection in
     * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.
     */
    private static final class FixedCollection implements ChromaVectorStore.ChromaCollectionAdapter {
        private final Map<String, Object> configuration;

        private FixedCollection(Map<String, Object> configuration) {
            this.configuration = configuration;
        }

        @Override
        public String name() {
            return "test_collection";
        }

        @Override
        public Map<String, Object> configuration() {
            return configuration;
        }

        @Override
        public void add(List<String> ids,
                        List<List<Double>> embeddings,
                        List<String> documents,
                        List<Map<String, Object>> metadatas) {
        }

        @Override
        public Map<String, Object> query(List<Double> queryEmbedding,
                                         String queryText,
                                         int nResults,
                                         Map<String, Object> queryArgs) {
            return Map.of("ids", List.of(new ArrayList<>()));
        }

        @Override
        public void delete(List<String> ids, Map<String, Object> queryArgs) {
        }
    }
}
