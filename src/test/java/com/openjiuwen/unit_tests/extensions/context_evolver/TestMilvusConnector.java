/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.context_evolver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.openjiuwen.extensions.context_evolver.core.db_connector.MilvusConnector;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DropIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_milvus_connector.py}.
 */
class TestMilvusConnector {

    private static final String NAMESPACE = "test_milvus_ns";
    private static final Gson GSON = new Gson();

    @Test
    @DisplayName("test_end_to_end")
    @Tag("level0")
    void testEndToEnd() throws Exception {
        Map<String, Map<String, Object>> data = createReMeData();
        MockMilvusState state = new MockMilvusState();
        MilvusConnector connector = createConnector(state, 4);

        connector.saveToDb(NAMESPACE, data);
        assertTrue(connector.exists(NAMESPACE));
        assertEquals(data.size(), connector.count(NAMESPACE));
        assertEquals(data.size(), connector.count(null));

        Map<String, Map<String, Object>> loaded = connector.loadFromDb(NAMESPACE);
        assertEquals(data.size(), loaded.size());
        for (Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            Map<String, Object> roundTrip = loaded.get(entry.getKey());
            assertEquals(entry.getValue().get("content"), roundTrip.get("content"));
            assertEquals(entry.getValue().get("metadata"), roundTrip.get("metadata"));
            assertEquals(entry.getValue().get("embedding"), roundTrip.get("embedding"));
        }

        assertTrue(connector.listNamespaces().contains(NAMESPACE));

        List<Map<String, Object>> results = connector.search(
            NAMESPACE,
            List.of(1.0d, 0.0d, 0.0d, 0.0d),
            3
        );
        assertFalse(results.isEmpty());
        assertEquals("reme_demo_user_node_01", results.getFirst().get("id"));
        assertEquals(1.0f, ((Number) results.getFirst().get("score")).floatValue(), 1e-4f);

        Map<String, Map<String, Object>> modified = new LinkedHashMap<>(data);
        Map<String, Object> updated = new LinkedHashMap<>(data.get("reme_demo_user_node_01"));
        updated.put("content", "UPDATED: " + data.get("reme_demo_user_node_01").get("content"));
        modified.put("reme_demo_user_node_01", updated);
        connector.saveToDb(NAMESPACE, modified);

        Map<String, Map<String, Object>> afterUpsert = connector.loadFromDb(NAMESPACE);
        assertEquals(data.size(), afterUpsert.size());
        assertEquals(updated.get("content"), afterUpsert.get("reme_demo_user_node_01").get("content"));

        assertTrue(connector.deleteNodes(NAMESPACE, List.of("reme_demo_user_node_01")));
        Map<String, Map<String, Object>> afterPartialDelete = connector.loadFromDb(NAMESPACE);
        assertEquals(data.size() - 1, afterPartialDelete.size());
        assertFalse(afterPartialDelete.containsKey("reme_demo_user_node_01"));

        assertTrue(connector.delete(NAMESPACE));
        assertFalse(connector.exists(NAMESPACE));
        assertFalse(connector.delete(NAMESPACE));

        assertDoesNotThrow(connector::flush);
        assertDoesNotThrow(connector::close);
    }

    @Test
    @DisplayName("test_save_skips_nodes_without_embeddings")
    @Tag("level0")
    void testSaveSkipsNodesWithoutEmbeddings() throws Exception {
        MockMilvusState state = new MockMilvusState();
        MilvusConnector connector = createConnector(state, 4);

        Map<String, Map<String, Object>> data = new LinkedHashMap<>();
        data.put("node_with_emb", node("node_with_emb", "has an embedding", List.of(0.1d, 0.2d, 0.3d, 0.4d), Map.of()));
        data.put("node_no_emb", node("node_no_emb", "no embedding", null, Map.of()));

        connector.saveToDb("ns_skip", data);

        Map<String, Map<String, Object>> loaded = connector.loadFromDb("ns_skip");
        assertTrue(loaded.containsKey("node_with_emb"));
        assertFalse(loaded.containsKey("node_no_emb"));
    }

    @Test
    @DisplayName("test_load_empty_namespace_returns_empty_dict")
    @Tag("level0")
    void testLoadEmptyNamespaceReturnsEmptyDict() throws Exception {
        MilvusConnector connector = createConnector(new MockMilvusState(), 4);
        assertEquals(Map.of(), connector.loadFromDb("ghost_ns"));
    }

    @Test
    @DisplayName("test_search_returns_empty_for_unknown_namespace")
    @Tag("level0")
    void testSearchReturnsEmptyForUnknownNamespace() throws Exception {
        MilvusConnector connector = createConnector(new MockMilvusState(), 4);
        assertEquals(List.of(), connector.search("ghost_ns", List.of(0.1d, 0.2d, 0.3d, 0.4d), 5));
    }

    @Test
    @DisplayName("test_delete_nodes_empty_list_is_noop")
    @Tag("level0")
    void testDeleteNodesEmptyListIsNoOp() throws Exception {
        MockMilvusState state = new MockMilvusState();
        state.upsertRows(List.of(row("keep_me", "ns", "x", List.of(1.0f, 0.0f), Map.of())));
        MilvusConnector connector = createConnector(state, 2);

        assertTrue(connector.deleteNodes("ns", List.of()));
        assertEquals(1, connector.count("ns"));
    }

    @Test
    @DisplayName("test_create_index_drops_and_recreates")
    @Tag("level0")
    void testCreateIndexDropsAndRecreates() throws Exception {
        MockMilvusState state = new MockMilvusState();
        MilvusConnector connector = createConnector(state, 4);

        connector.createIndex("HNSW", "COSINE", 8, 32, 64);
        connector.createIndex("IVF_FLAT", "L2", 16, 64, 128);

        verify(state.client, times(2)).dropIndex(any(DropIndexReq.class));
        verify(state.client, times(2)).createIndex(any(CreateIndexReq.class));
        verify(state.client, times(2)).loadCollection(any(LoadCollectionReq.class));
    }

    private MilvusConnector createConnector(MockMilvusState state, int dim) throws Exception {
        MilvusConnector connector = new ObjenesisStd().newInstance(MilvusConnector.class);
        setField(connector, "host", "mock");
        setField(connector, "port", 19530);
        setField(connector, "collectionName", "vector_nodes_mock");
        setField(connector, "dim", dim);
        setField(connector, "alias", "mock_alias");
        setField(connector, "metricType", "COSINE");
        setField(connector, "client", state.client);
        setField(connector, "collectionInitialized", true);
        return connector;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = MilvusConnector.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Map<String, Map<String, Object>> createReMeData() {
        Map<String, Map<String, Object>> data = new LinkedHashMap<>();
        data.put(
            "reme_demo_user_node_01",
            node(
                "reme_demo_user_node_01",
                "When asked how to debug Python code or find bugs",
                List.of(1.0d, 0.0d, 0.0d, 0.0d),
                Map.of("type", "reme", "label", "debug_python")
            )
        );
        data.put(
            "reme_demo_user_node_02",
            node(
                "reme_demo_user_node_02",
                "When asked about data structures and algorithms",
                List.of(0.0d, 1.0d, 0.0d, 0.0d),
                Map.of("type", "reme", "label", "dsa")
            )
        );
        data.put(
            "reme_demo_user_node_03",
            node(
                "reme_demo_user_node_03",
                "When asked about system design patterns",
                List.of(0.0d, 0.0d, 1.0d, 0.0d),
                Map.of("type", "reme", "label", "system_design")
            )
        );
        return data;
    }

    private static Map<String, Object> node(
        String id,
        String content,
        List<Double> embedding,
        Map<String, Object> metadata
    ) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("content", content);
        node.put("embedding", embedding);
        node.put("metadata", metadata);
        return node;
    }

    private static JsonObject row(
        String id,
        String namespace,
        String content,
        List<Float> embedding,
        Map<String, Object> metadata
    ) {
        JsonObject row = new JsonObject();
        row.addProperty("id", id);
        row.addProperty("namespace", namespace);
        row.addProperty("content", content);
        row.add("embedding", GSON.toJsonTree(embedding));
        row.add("metadata", GSON.toJsonTree(metadata));
        return row;
    }

    private static final class MockMilvusState {
        private final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        private final MilvusClientV2 client = mock(MilvusClientV2.class);

        private MockMilvusState() {
            try {
                when(client.insert(any(InsertReq.class))).thenAnswer(invocation -> {
                    InsertReq req = invocation.getArgument(0);
                    upsertRows(req.getData());
                    return createInsertResp(req.getData().size());
                });

                doAnswer(invocation -> {
                    DeleteReq req = invocation.getArgument(0);
                    deleteByFilter(req.getFilter());
                    return null;
                }).when(client).delete(any(DeleteReq.class));

                when(client.query(any(QueryReq.class))).thenAnswer(invocation -> {
                    QueryReq req = invocation.getArgument(0);
                    return createQueryResp(query(req.getFilter(), req.getOutputFields(), req.getLimit()));
                });

                when(client.search(any(SearchReq.class))).thenAnswer(invocation -> {
                    SearchReq req = invocation.getArgument(0);
                    return createSearchResp(search(req));
                });

                when(client.getCollectionStats(any())).thenAnswer(invocation -> {
                    return createCollectionStatsResp(rows.size());
                });

                doNothing().when(client).dropIndex(any(DropIndexReq.class));
                doNothing().when(client).createIndex(any(CreateIndexReq.class));
                doNothing().when(client).loadCollection(any(LoadCollectionReq.class));
                doNothing().when(client).flush(any(FlushReq.class));
                doNothing().when(client).close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private void upsertRows(List<JsonObject> insertRows) {
            for (JsonObject row : insertRows) {
                Map<String, Object> stored = new LinkedHashMap<>();
                stored.put("id", row.get("id").getAsString());
                stored.put("namespace", row.get("namespace").getAsString());
                stored.put("content", row.get("content").getAsString());
                List<Float> embedding = new ArrayList<>();
                row.getAsJsonArray("embedding").forEach(item -> embedding.add(item.getAsFloat()));
                stored.put("embedding", embedding);
                stored.put("metadata", GSON.fromJson(row.get("metadata"), Map.class));
                rows.put(String.valueOf(stored.get("id")), stored);
            }
        }

        private void deleteByFilter(String filter) {
            List<String> toDelete = rows.values().stream()
                .filter(row -> matches(filter, row))
                .map(row -> String.valueOf(row.get("id")))
                .toList();
            toDelete.forEach(rows::remove);
        }

        private List<QueryResp.QueryResult> query(
            String filter,
            List<String> outputFields,
            long limit
        ) {
            List<QueryResp.QueryResult> results = new ArrayList<>();
            long remaining = limit <= 0 ? Long.MAX_VALUE : limit;
            for (Map<String, Object> row : rows.values()) {
                if (!matches(filter, row) || remaining-- <= 0) {
                    continue;
                }
                results.add(
                    QueryResp.QueryResult.builder()
                        .entity(project(row, outputFields))
                        .build()
                );
            }
            return results;
        }

        private List<SearchResp.SearchResult> search(SearchReq req) {
            BaseVector vector = req.getData().getFirst();
            @SuppressWarnings("unchecked")
            List<Float> queryEmbedding = (List<Float>) ((FloatVec) vector).getData();

            List<SearchResp.SearchResult> results = new ArrayList<>();
            rows.values().stream()
                .filter(row -> matches(req.getFilter(), row))
                .sorted((left, right) -> Float.compare(score(right, queryEmbedding), score(left, queryEmbedding)))
                .limit(req.getTopK())
                .forEach(row -> {
                    results.add(
                        SearchResp.SearchResult.builder()
                            .entity(project(row, req.getOutputFields()))
                            .id(row.get("id"))
                            .score(score(row, queryEmbedding))
                            .build()
                    );
                });
            return results;
        }

        private static InsertResp createInsertResp(int insertCount) {
            return InsertResp.builder()
                .InsertCnt(insertCount)
                .build();
        }

        private static QueryResp createQueryResp(List<QueryResp.QueryResult> results) {
            return QueryResp.builder()
                .queryResults(results)
                .build();
        }

        private static SearchResp createSearchResp(List<SearchResp.SearchResult> results) {
            return SearchResp.builder()
                .searchResults(List.of(results))
                .build();
        }

        private static GetCollectionStatsResp createCollectionStatsResp(int entityCount) {
            return GetCollectionStatsResp.builder()
                .numOfEntities((long) entityCount)
                .build();
        }

        private static Map<String, Object> project(Map<String, Object> row, List<String> outputFields) {
            if (outputFields == null || outputFields.isEmpty()) {
                return new LinkedHashMap<>(row);
            }
            Map<String, Object> projected = new LinkedHashMap<>();
            for (String field : outputFields) {
                projected.put(field, row.get(field));
            }
            return projected;
        }

        @SuppressWarnings("unchecked")
        private static float score(Map<String, Object> row, List<Float> queryEmbedding) {
            List<Float> embedding = (List<Float>) row.get("embedding");
            double dot = 0.0d;
            double normA = 0.0d;
            double normB = 0.0d;
            for (int i = 0; i < embedding.size(); i++) {
                double a = embedding.get(i);
                double b = queryEmbedding.get(i);
                dot += a * b;
                normA += a * a;
                normB += b * b;
            }
            return normA == 0.0d || normB == 0.0d
                ? 0.0f
                : (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
        }

        private static boolean matches(String filter, Map<String, Object> row) {
            if (filter == null || filter.isBlank()) {
                return true;
            }

            Matcher eq = Pattern.compile("^(\\w+) == \"([^\"]*)\"$").matcher(filter);
            if (eq.matches()) {
                return eq.group(2).equals(String.valueOf(row.get(eq.group(1))));
            }

            Matcher ne = Pattern.compile("^(\\w+) != \"([^\"]*)\"$").matcher(filter);
            if (ne.matches()) {
                return !ne.group(2).equals(String.valueOf(row.get(ne.group(1))));
            }

            Matcher in = Pattern.compile("^(\\w+) in \\[(.*)]$").matcher(filter);
            if (in.matches()) {
                Set<String> accepted = new LinkedHashSet<>();
                String values = in.group(2);
                if (!values.isBlank()) {
                    Arrays.stream(values.split(","))
                        .map(String::trim)
                        .map(value -> value.replace("\"", ""))
                        .forEach(accepted::add);
                }
                return accepted.contains(String.valueOf(row.get(in.group(1))));
            }

            return true;
        }
    }
}
