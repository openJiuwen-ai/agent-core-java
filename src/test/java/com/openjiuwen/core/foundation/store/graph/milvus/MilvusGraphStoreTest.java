/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph.milvus;

import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreConstants;
import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreStorageConfig;
import com.openjiuwen.core.foundation.store.graph.WeightedRankConfig;
import com.openjiuwen.core.foundation.store.query.QueryExpressions;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code MilvusGraphStore} behavior in
 * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py}.
 */
class MilvusGraphStoreTest {

    @Test
    void constructorCreatesDatabaseAndCollections() {
        FakeClient client = new FakeClient();
        MilvusGraphStore store = new MilvusGraphStore(config("dot", null, new GraphStoreStorageConfig()), client);

        assertEquals("IP", store.getMetric());
        assertTrue(store.getAlias().startsWith("graph-store-"));
        assertEquals(List.of("graphdb"), client.createdDatabases);
        assertEquals("graphdb", client.usedDatabase);
        assertEquals(Set.of(
                GraphStoreConstants.ENTITY_COLLECTION,
                GraphStoreConstants.RELATION_COLLECTION,
                GraphStoreConstants.EPISODE_COLLECTION
        ), new LinkedHashSet<>(client.createdCollections));
        assertTrue(store.getFieldDef().get(GraphStoreConstants.ENTITY_COLLECTION).contains("name_embedding"));
        assertEquals("BM25", store.getFullTextSearchParams().get("metric_type"));
    }

    @Test
    void attachEmbedderValidatesDimension() {
        MilvusGraphStore store = new MilvusGraphStore(
                config("cosine", null, new GraphStoreStorageConfig()),
                new FakeClient()
        );

        CompletionException exception = assertThrows(CompletionException.class, () ->
                CompletableFuture.runAsync(() -> store.attachEmbedder(new StubEmbedding(32))).join());
        assertTrue(exception.getCause().getMessage().contains("different config.embed_dim"));
    }

    @Test
    void addEntityEmbedsSerializesAndTruncatesLikePython() {
        FakeClient client = new FakeClient();
        GraphStoreStorageConfig storageConfig = GraphStoreStorageConfig.builder()
                .content(8)
                .name(5)
                .build();
        MilvusGraphStore store = new MilvusGraphStore(
                config("cosine", new StubEmbedding(64), storageConfig),
                client
        );
        Entity entity = new Entity();
        entity.setUuid("entity-1");
        entity.setName("abcdefg");
        entity.setContent("0123456789");
        entity.setRelations(List.of("relation-1"));

        store.addEntity(List.of(entity), true, false, false).join();

        Map<String, Object> row = client.rowsByCollection.get(GraphStoreConstants.ENTITY_COLLECTION).getFirst();
        assertEquals("01234...", row.get("content"));
        assertEquals("ab...", row.get("name"));
        assertEquals(List.of("relation-1"), row.get("relations"));
        assertEquals(64, ((List<?>) row.get("content_embedding")).size());
        assertEquals(64, ((List<?>) row.get("name_embedding")).size());
        assertTrue(client.flushedCollections.contains(GraphStoreConstants.ENTITY_COLLECTION));
    }

    @Test
    void queryAndDeletePreserveMilvusFilterSemantics() {
        FakeClient client = new FakeClient();
        MilvusGraphStore store = new MilvusGraphStore(
                config("cosine", null, new GraphStoreStorageConfig()),
                client
        );

        assertThrows(CompletionException.class,
                () -> store.query(GraphStoreConstants.ENTITY_COLLECTION, null, null, false, Map.of()).join());

        client.queryRows = List.of(Map.of("uuid", "entity-1"));
        List<Map<String, Object>> rows = store.query(
                GraphStoreConstants.ENTITY_COLLECTION,
                List.of("entity-1"),
                null,
                false,
                Map.of("output_fields", List.of("uuid"))
        ).join();
        assertEquals(List.of(Map.of("uuid", "entity-1")), rows);
        assertEquals(List.of("entity-1"), client.lastQueryIds);
        assertEquals(List.of("uuid"), client.lastOutputFields);

        Map<String, Object> deleteResult = store.delete(
                GraphStoreConstants.ENTITY_COLLECTION,
                List.of("entity-1", "entity-2"),
                null,
                Map.of()
        ).join();
        assertEquals(2L, deleteResult.get("delete_count"));
        assertTrue(client.lastDeleteFilter.contains("uuid in [\"entity-1\",\"entity-2\"]"));

        store.query(
                GraphStoreConstants.ENTITY_COLLECTION,
                null,
                QueryExpressions.eq("uuid", "entity-1"),
                false,
                Map.of("limit", 1)
        ).join();
        assertEquals("uuid == \"entity-1\"", client.lastQueryFilter);
    }

    @Test
    void searchBuildsThreeMilvusRequestsAndRanksBySimilarity() {
        FakeClient client = new FakeClient();
        client.searchRows = List.of(
                mapOf("uuid", "low", "distance", 0.2d),
                mapOf("uuid", "high", "distance", 0.8d)
        );
        MilvusGraphStore store = new MilvusGraphStore(
                config("cosine", new StubEmbedding(64), new GraphStoreStorageConfig()),
                client
        );

        Map<String, List<Map<String, Object>>> result = store.search(
                "needle",
                2,
                GraphStoreConstants.ENTITY_COLLECTION,
                new WeightedRankConfig(),
                null,
                0,
                0,
                null,
                null,
                null,
                Map.of()
        ).join();

        assertEquals("high", result.get(GraphStoreConstants.ENTITY_COLLECTION).getFirst().get("uuid"));
        assertEquals(List.of("name_embedding", "content_embedding", "content_bm25"),
                client.lastSearchRequests.stream().map(MilvusGraphStore.SearchRequest::fieldName).toList());
        assertInstanceOf(String.class, client.lastSearchRequests.get(2).data());
        assertFalse(client.lastRanker.args().isEmpty());
    }

    private static GraphConfig config(String metric, Embedding embedding, GraphStoreStorageConfig storageConfig) {
        return GraphConfig.builder()
                .uri(Path.of("target", "milvus-test", metric + ".db").toString())
                .name("graphdb")
                .embedDim(64)
                .embeddingModel(embedding)
                .dbStorageConfig(storageConfig)
                .dbEmbedConfig(new GraphStoreIndexConfig(new MilvusAUTO(), metric, null, null, null))
                .build();
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    /**
     * Mirrors Python's embedding boundary used by
     * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py} tests.
     */
    private static final class StubEmbedding extends Embedding {

        private final int dimension;

        private StubEmbedding(int dimension) {
            this.dimension = dimension;
        }

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(vector());
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(List<String> texts,
                                                                     Integer batchSize,
                                                                     Map<String, Object> kwargs) {
            List<List<Double>> vectors = new ArrayList<>();
            for (String ignored : texts) {
                vectors.add(vector());
            }
            return CompletableFuture.completedFuture(vectors);
        }

        @Override
        public int getDimension() {
            return dimension;
        }

        private List<Double> vector() {
            List<Double> vector = new ArrayList<>();
            for (int i = 0; i < dimension; i++) {
                vector.add(0.5d);
            }
            return vector;
        }
    }

    /**
     * Mirrors Python's Milvus client boundary used by
     * {@code openjiuwen/core/foundation/store/graph/milvus/milvus_support.py} tests.
     */
    private static final class FakeClient implements MilvusGraphStore.MilvusClientAdapter {

        private final List<String> databases = new ArrayList<>();
        private final List<String> createdDatabases = new ArrayList<>();
        private final List<String> createdCollections = new ArrayList<>();
        private final Set<String> flushedCollections = new LinkedHashSet<>();
        private final Map<String, List<Map<String, Object>>> rowsByCollection = new LinkedHashMap<>();
        private String usedDatabase;
        private List<?> lastQueryIds;
        private String lastQueryFilter;
        private List<String> lastOutputFields;
        private String lastDeleteFilter;
        private List<MilvusGraphStore.SearchRequest> lastSearchRequests = List.of();
        private MilvusGraphStore.RankerSpec lastRanker;
        private List<Map<String, Object>> queryRows = List.of();
        private List<Map<String, Object>> searchRows = List.of();

        @Override
        public List<String> listDatabases() {
            return databases;
        }

        @Override
        public void createDatabase(String database) {
            databases.add(database);
            createdDatabases.add(database);
        }

        @Override
        public void useDatabase(String database) {
            usedDatabase = database;
        }

        @Override
        public void dropDatabase(String database) {
            databases.remove(database);
        }

        @Override
        public List<String> listCollections() {
            return new ArrayList<>(rowsByCollection.keySet());
        }

        @Override
        public boolean hasCollection(String collection) {
            return rowsByCollection.containsKey(collection);
        }

        @Override
        public void createCollection(String collection,
                                     GenerateMilvusSchema.SchemaResult schema,
                                     int dimension,
                                     String metric) {
            createdCollections.add(collection);
            rowsByCollection.putIfAbsent(collection, new ArrayList<>());
        }

        @Override
        public void loadCollection(String collection) {
            rowsByCollection.putIfAbsent(collection, new ArrayList<>());
        }

        @Override
        public void dropCollection(String collection) {
            rowsByCollection.remove(collection);
        }

        @Override
        public long rowCount(String collection) {
            return rowsByCollection.getOrDefault(collection, List.of()).size();
        }

        @Override
        public void flush(String collection) {
            flushedCollections.add(collection);
        }

        @Override
        public void compact(String collection) {
        }

        @Override
        public void write(String collection, List<Map<String, Object>> rows, boolean upsert) {
            rowsByCollection.computeIfAbsent(collection, ignored -> new ArrayList<>()).addAll(rows);
        }

        @Override
        public List<Map<String, Object>> query(String collection,
                                               List<?> ids,
                                               String filter,
                                               List<String> outputFields,
                                               Map<String, Object> kwargs) {
            lastQueryIds = ids;
            lastQueryFilter = filter;
            lastOutputFields = outputFields;
            return queryRows;
        }

        @Override
        public Map<String, Object> delete(String collection, String filter, Map<String, Object> kwargs) {
            lastDeleteFilter = filter;
            return Map.of("delete_count", 2L);
        }

        @Override
        public List<Map<String, Object>> hybridSearch(String collection,
                                                      List<MilvusGraphStore.SearchRequest> searchRequests,
                                                      MilvusGraphStore.RankerSpec ranker,
                                                      int limit,
                                                      List<String> outputFields) {
            lastSearchRequests = searchRequests;
            lastRanker = ranker;
            List<Map<String, Object>> copy = new ArrayList<>();
            for (Map<String, Object> row : searchRows) {
                copy.add(new LinkedHashMap<>(row));
            }
            return copy;
        }

        @Override
        public void close() {
        }
    }
}
