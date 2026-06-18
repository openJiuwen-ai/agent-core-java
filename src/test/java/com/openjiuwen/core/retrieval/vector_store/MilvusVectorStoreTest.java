/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusHNSW;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.StoreType;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.utils.CommonUtils;
import io.milvus.v2.common.DataType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestMilvusVectorStore} in
 * {@code tests/unit_tests/core/retrieval/vector_store/test_milvus_store.py},
 * for {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
 */
class MilvusVectorStoreTest {

    @Test
    void initKeepsConnectionFieldAndMilvusVectorConfiguration() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        MilvusHNSW hnsw = new MilvusHNSW();
        hnsw.setVectorField("custom_vector");
        hnsw.setM(16);
        hnsw.setEfConstruction(200);
        hnsw.setEfSearchFactor(2.0d);

        MilvusVectorStore store = new MilvusVectorStore(
                config("dot"),
                "http://localhost:19530",
                "test_token",
                "custom_text",
                hnsw,
                "custom_sparse",
                "custom_metadata",
                "custom_doc_id",
                null,
                client
        );

        assertThat(store.getCollectionName()).isEqualTo("test_collection");
        assertThat(store.getMilvusUri()).isEqualTo("http://localhost:19530");
        assertThat(store.getMilvusToken()).isEqualTo("test_token");
        assertThat(store.getTextField()).isEqualTo("custom_text");
        assertThat(store.getVectorField()).isEqualTo("custom_vector");
        assertThat(store.getSparseVectorField()).isEqualTo("custom_sparse");
        assertThat(store.getMetadataField()).isEqualTo("custom_metadata");
        assertThat(store.getDocIdField()).isEqualTo("custom_doc_id");
        assertThat(store.getDistanceMetric()).isEqualTo("IP");
        assertThat(store.getConstructConfig())
                .containsEntry("M", 16)
                .containsEntry("efConstruction", 200)
                .containsEntry("metric_type", "IP");
        assertThat(store.getSearchParams(5)).containsEntry("ef", 10L);
        assertThat(store.getMilvusAlias()).isEqualTo(CommonUtils.createMilvusAlias(
                null,
                "http://localhost:19530",
                "",
                "test_token"
        ));
    }

    @Test
    void initRejectsInvalidVectorFieldLikePython() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();

        assertThatThrownBy(() -> new MilvusVectorStore(
                config("cosine"),
                "uri",
                null,
                "content",
                Map.of("vector_field", "bad"),
                "sparse_vector",
                "metadata",
                "document_id",
                null,
                client
        ))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("vector_field must be either a str or MilvusVectorField instance");
    }

    @Test
    void addLoadsExistingCollectionBatchesAndFlushes() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        client.collections.add("test_collection");
        MilvusVectorStore store = newStore(client, "cosine");

        store.add(List.of(
                row("1", "Content 1", List.of(0.1d, 0.2d)),
                row("2", "Content 2", List.of(0.2d, 0.3d)),
                row("3", "Content 3", List.of(0.3d, 0.4d))
        ), 2, Map.of()).join();

        assertThat(client.loadedCollections).containsExactly("test_collection");
        assertThat(client.insertedBatches).hasSize(2);
        assertThat(client.insertedBatches.get(0)).hasSize(2);
        assertThat(client.insertedBatches.get(1)).hasSize(1);
        assertThat(client.flushedCollections).containsExactly("test_collection");
    }

    @Test
    void searchBuildsPythonFilterAndConvertsMilvusHit() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        client.collections.add("test_collection");
        client.vectorHits = List.of(new MilvusVectorStore.SearchHit(
                "hit-1",
                null,
                0.2d,
                null,
                mapOf(
                        "content", "Test content",
                        "metadata", "{\"source\":\"unit\"}",
                        "document_id", "doc-field",
                        "chunk_id", "chunk-1"
                )
        ));
        MilvusVectorStore store = newStore(client, "euclidean");

        List<RetrievalResult> results = store.search(
                List.of(0.1d, 0.2d),
                5,
                VectorStore.VectorStoreFilter.ofMap(mapOf("source", "unit", "enabled", true, "missing", null)),
                Map.of()
        ).join();

        assertThat(client.lastSearchFilter).isEqualTo("source == \"unit\" && enabled == True && missing == None");
        assertThat(client.lastSearchMetricType).isEqualTo("L2");
        assertThat(client.lastSearchRequest.annsField()).isEqualTo("embedding");
        assertThat(client.lastOutputFields).containsExactly("content", "metadata", "document_id", "chunk_id");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getText()).isEqualTo("Test content");
        assertThat(results.getFirst().getScore()).isEqualTo(0.95d);
        assertThat(results.getFirst().getDocId()).isEqualTo("doc-field");
        assertThat(results.getFirst().getChunkId()).isEqualTo("chunk-1");
        assertThat(results.getFirst().getMetadata())
                .containsEntry("source", "unit")
                .containsEntry("doc_id", "doc-field")
                .containsEntry("raw_score", 0.2d)
                .containsEntry("raw_score_scaled", 0.95d);
    }

    @Test
    void sparseSearchReturnsEmptyWhenMilvusBm25SearchFails() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        client.collections.add("test_collection");
        client.throwSparseSearch = true;
        MilvusVectorStore store = newStore(client, "cosine");

        List<RetrievalResult> results = store.sparseSearch(
                "test query",
                5,
                VectorStore.VectorStoreFilter.ofMap(Map.of("source", "unit")),
                Map.of()
        ).join();

        assertThat(results).isEmpty();
        assertThat(client.lastSearchMetricType).isEqualTo("BM25");
    }

    @Test
    void hybridSearchUsesNativeSparseAndDenseRequests() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        client.collections.add("test_collection");
        client.hybridHits = List.of(new MilvusVectorStore.SearchHit(
                "hybrid-1",
                null,
                0.9d,
                null,
                mapOf("content", "Hybrid content", "metadata", Map.of())
        ));
        MilvusVectorStore store = newStore(client, "cosine");

        List<RetrievalResult> results = store.hybridSearch(
                "test",
                List.of(0.1d, 0.2d),
                3,
                0.2d,
                VectorStore.VectorStoreFilter.ofMap(Map.of("source", "unit")),
                Map.of()
        ).join();

        assertThat(client.lastHybridRequests).hasSize(2);
        assertThat(client.lastHybridRequests.get(0).annsField()).isEqualTo("embedding");
        assertThat(client.lastHybridRequests.get(0).metricType()).isEqualTo("COSINE");
        assertThat(client.lastHybridRequests.get(1).annsField()).isEqualTo("sparse_vector");
        assertThat(client.lastHybridRequests.get(1).metricType()).isEqualTo("BM25");
        assertThat(client.lastHybridOutputFields).containsExactly("content", "metadata", "document_id");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getScore()).isEqualTo(0.9d);
    }

    @Test
    void hybridSearchFallsBackToSeparateSearchesAndIgnoresFiltersLikePython() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        client.collections.add("test_collection");
        client.throwHybridSearch = true;
        client.vectorHits = List.of(new MilvusVectorStore.SearchHit(
                "v1",
                null,
                1.0d,
                null,
                mapOf("content", "Vector result", "metadata", Map.of())
        ));
        client.sparseHits = List.of(new MilvusVectorStore.SearchHit(
                "s1",
                null,
                0.8d,
                null,
                mapOf("content", "Sparse result", "metadata", Map.of())
        ));
        MilvusVectorStore store = newStore(client, "cosine");

        List<RetrievalResult> results = store.hybridSearch(
                "test",
                List.of(0.1d, 0.2d),
                5,
                0.5d,
                VectorStore.VectorStoreFilter.ofMap(Map.of("source", "unit")),
                Map.of()
        ).join();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(RetrievalResult::getText)
                .containsExactly("Vector result", "Sparse result");
        assertThat(client.searchFilters).containsExactly(null, null);
    }

    @Test
    void deleteSupportsIdsFilterFlushAndExceptionFalse() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        client.collections.add("test_collection");
        client.deleteCount = 2L;
        MilvusVectorStore store = newStore(client, "cosine");

        assertThat(store.delete(
                List.of("1", "2"),
                VectorStore.DeleteFilter.ofExpression("source == 'test'"),
                Map.of()
        ).join()).isTrue();
        assertThat(client.lastDeleteIds).containsExactly("1", "2");
        assertThat(client.lastDeleteFilter).isEqualTo("source == 'test'");
        assertThat(client.flushedCollections).contains("test_collection");

        client.throwDelete = true;
        assertThat(store.delete(List.of("3"), VectorStore.DeleteFilter.none(), Map.of()).join()).isFalse();
    }

    @Test
    void checkVectorFieldReturnsEarlyWhenCollectionDoesNotExist() {
        FakeMilvusClientFacade client = new FakeMilvusClientFacade();
        MilvusVectorStore store = newStore(client, "cosine");

        store.checkVectorField();

        assertThat(client.describeIndexCalls).isZero();
    }

    @Test
    void checkVectorFieldDetectsMissingFieldIndexMismatchAndConfigMismatch() {
        FakeMilvusClientFacade missingIndexClient = new FakeMilvusClientFacade();
        missingIndexClient.collections.add("test_collection");
        MilvusVectorStore missingIndexStore = newStore(missingIndexClient, "cosine");

        assertThatThrownBy(missingIndexStore::checkVectorField)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("MilvusVectorStore has vector_field at embedding");

        FakeMilvusClientFacade indexTypeClient = new FakeMilvusClientFacade();
        indexTypeClient.collections.add("test_collection");
        indexTypeClient.indexDescription.put("index_type", "IVF_FLAT");
        indexTypeClient.indexDescription.put("metric_type", "COSINE");
        MilvusVectorStore hnswStore = newStoreWithHnsw(indexTypeClient, 16, 200);

        assertThatThrownBy(hnswStore::checkVectorField)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("MilvusVectorStore has index_type of hnsw");

        FakeMilvusClientFacade configClient = new FakeMilvusClientFacade();
        configClient.collections.add("test_collection");
        configClient.indexDescription.put("index_type", "HNSW");
        configClient.indexDescription.put("metric_type", "COSINE");
        configClient.indexDescription.put("M", 32);
        configClient.indexDescription.put("efConstruction", 200);
        MilvusVectorStore mismatchStore = newStoreWithHnsw(configClient, 16, 200);

        assertThatThrownBy(mismatchStore::checkVectorField)
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("database actual config differs from current knowledge base");
    }

    @Test
    void checkVectorFieldAcceptsMatchingConfigAndAutoIndexType() {
        FakeMilvusClientFacade hnswClient = new FakeMilvusClientFacade();
        hnswClient.collections.add("test_collection");
        hnswClient.indexDescription.put("index_type", "HNSW");
        hnswClient.indexDescription.put("metric_type", "COSINE");
        hnswClient.indexDescription.put("M", 16);
        hnswClient.indexDescription.put("efConstruction", 200);
        newStoreWithHnsw(hnswClient, 16, 200).checkVectorField();

        FakeMilvusClientFacade autoClient = new FakeMilvusClientFacade();
        autoClient.collections.add("test_collection");
        autoClient.indexDescription.put("index_type", "AUTOINDEX");
        autoClient.indexDescription.put("metric_type", "COSINE");
        newStore(autoClient, "cosine").checkVectorField();
    }

    private static MilvusVectorStore newStore(FakeMilvusClientFacade client, String metric) {
        return new MilvusVectorStore(
                config(metric),
                "http://localhost:19530",
                null,
                "content",
                "embedding",
                "sparse_vector",
                "metadata",
                "document_id",
                null,
                client
        );
    }

    private static MilvusVectorStore newStoreWithHnsw(FakeMilvusClientFacade client, int m, int efConstruction) {
        MilvusHNSW hnsw = new MilvusHNSW();
        hnsw.setVectorField("embedding");
        hnsw.setM(m);
        hnsw.setEfConstruction(efConstruction);
        return new MilvusVectorStore(
                config("cosine"),
                "http://localhost:19530",
                null,
                "content",
                hnsw,
                "sparse_vector",
                "metadata",
                "document_id",
                null,
                client
        );
    }

    private static VectorStoreConfig config(String metric) {
        return new VectorStoreConfig(StoreType.MILVUS, "", "test_collection", metric);
    }

    private static Map<String, Object> row(String id, String content, List<Double> embedding) {
        return mapOf("id", id, "content", content, "embedding", embedding);
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    /**
     * Mirrors Python's mocked MilvusClient in
     * {@code tests/unit_tests/core/retrieval/vector_store/test_milvus_store.py},
     * for {@code openjiuwen/core/retrieval/vector_store/milvus_store.py}.
     */
    private static final class FakeMilvusClientFacade implements MilvusVectorStore.MilvusClientFacade {
        private final Set<String> collections = new LinkedHashSet<>();
        private final List<String> loadedCollections = new ArrayList<>();
        private final List<List<Map<String, Object>>> insertedBatches = new ArrayList<>();
        private final List<String> flushedCollections = new ArrayList<>();
        private final Map<String, Object> indexDescription = new LinkedHashMap<>();
        private final List<String> searchFilters = new ArrayList<>();
        private final List<Map<String, Object>> rows = new ArrayList<>();
        private List<MilvusVectorStore.SearchHit> vectorHits = List.of();
        private List<MilvusVectorStore.SearchHit> sparseHits = List.of();
        private List<MilvusVectorStore.SearchHit> hybridHits = List.of();
        private MilvusVectorStore.SearchRequest lastSearchRequest;
        private String lastSearchMetricType;
        private String lastSearchFilter;
        private List<String> lastOutputFields = List.of();
        private List<MilvusVectorStore.SearchRequest> lastHybridRequests = List.of();
        private List<String> lastHybridOutputFields = List.of();
        private List<String> lastDeleteIds = List.of();
        private String lastDeleteFilter;
        private long deleteCount;
        private int describeIndexCalls;
        private boolean throwSparseSearch;
        private boolean throwHybridSearch;
        private boolean throwDelete;

        @Override
        public boolean hasCollection(String collectionName) {
            return collections.contains(collectionName);
        }

        @Override
        public void loadCollection(String collectionName) {
            loadedCollections.add(collectionName);
        }

        @Override
        public void insert(String collectionName, List<Map<String, Object>> inputRows, int batchSize) {
            int safeBatchSize = Math.max(1, batchSize);
            rows.addAll(inputRows.stream()
                    .map(LinkedHashMap::new)
                    .map(row -> (Map<String, Object>) row)
                    .toList());
            for (int start = 0; start < inputRows.size(); start += safeBatchSize) {
                insertedBatches.add(inputRows.subList(start, Math.min(start + safeBatchSize, inputRows.size())));
            }
        }

        @Override
        public List<MilvusVectorStore.SearchHit> search(
                String collectionName,
                MilvusVectorStore.SearchRequest request,
                String metricType,
                int topK,
                List<String> outputFields,
                Map<String, Object> searchParams,
                String filter
        ) {
            lastSearchRequest = request;
            lastSearchMetricType = metricType;
            lastSearchFilter = filter;
            searchFilters.add(filter);
            lastOutputFields = new ArrayList<>(outputFields);
            if ("BM25".equals(metricType)) {
                if (throwSparseSearch) {
                    throw new IllegalStateException("Search error");
                }
                return sparseHits;
            }
            return vectorHits;
        }

        @Override
        public List<MilvusVectorStore.SearchHit> hybridSearch(
                String collectionName,
                List<MilvusVectorStore.SearchRequest> requests,
                int topK,
                List<String> outputFields
        ) {
            if (throwHybridSearch) {
                throw new IllegalStateException("Hybrid search error");
            }
            lastHybridRequests = new ArrayList<>(requests);
            lastHybridOutputFields = new ArrayList<>(outputFields);
            return hybridHits;
        }

        @Override
        public long delete(String collectionName, List<String> ids, String filter) {
            if (throwDelete) {
                throw new IllegalStateException("Delete error");
            }
            lastDeleteIds = ids == null ? List.of() : new ArrayList<>(ids);
            lastDeleteFilter = filter;
            return deleteCount;
        }

        @Override
        public void flush(String collectionName) {
            flushedCollections.add(collectionName);
        }

        @Override
        public void dropCollection(String collectionName) {
            collections.remove(collectionName);
        }

        @Override
        public Map<String, Object> describeIndex(String collectionName, String fieldName) {
            describeIndexCalls++;
            return new LinkedHashMap<>(indexDescription);
        }

        @Override
        public MilvusVectorStore.CollectionDescription describeCollection(String collectionName) {
            return new MilvusVectorStore.CollectionDescription(List.of(
                    new MilvusVectorStore.FieldDescription("other_vector", DataType.FloatVector, Map.of())
            ));
        }

        @Override
        public void close() {
        }
    }
}
