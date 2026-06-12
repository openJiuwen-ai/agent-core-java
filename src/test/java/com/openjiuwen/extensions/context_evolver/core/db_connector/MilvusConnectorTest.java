/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.db_connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_milvus_connector.py}.
 */
class MilvusConnectorTest {

    private static final String NAMESPACE = "test_milvus_ns";

    @Test
    void endToEndCrudAndSearchMirrorPythonMockCoverage() {
        Map<String, Map<String, Object>> data = sampleData();
        InMemoryCollectionAdapter adapter = new InMemoryCollectionAdapter();
        MilvusConnector connector = mockConnector(adapter, 4);
        String firstId = data.keySet().iterator().next();

        connector.saveToDb(NAMESPACE, data);
        assertTrue(connector.exists(NAMESPACE));
        assertEquals(data.size(), connector.count(NAMESPACE));
        assertEquals(data.size(), connector.count());

        Map<String, Map<String, Object>> loaded = connector.loadFromDb(NAMESPACE);
        assertEquals(data.size(), loaded.size());
        assertEquals(data.get(firstId).get("content"), loaded.get(firstId).get("content"));
        assertEquals(data.get(firstId).get("metadata"), loaded.get(firstId).get("metadata"));

        List<String> namespaces = connector.listNamespaces();
        assertTrue(namespaces.contains(NAMESPACE));

        @SuppressWarnings("unchecked")
        List<Double> queryEmbedding = (List<Double>) data.get(firstId).get("embedding");
        List<Map<String, Object>> searchResults = connector.search(NAMESPACE, queryEmbedding, 3, "cosine");
        assertFalse(searchResults.isEmpty());
        assertEquals(firstId, searchResults.get(0).get("id"));
        assertEquals(1.0d, (Double) searchResults.get(0).get("score"), 0.0001d);

        Map<String, Map<String, Object>> modified = new LinkedHashMap<>(data);
        modified.put(firstId, Map.of(
                "id", firstId,
                "content", "UPDATED: " + data.get(firstId).get("content"),
                "embedding", queryEmbedding,
                "metadata", data.get(firstId).get("metadata")
        ));
        connector.saveToDb(NAMESPACE, modified);
        assertEquals("UPDATED: " + data.get(firstId).get("content"), connector.loadFromDb(NAMESPACE).get(firstId).get("content"));

        connector.deleteNodes(NAMESPACE, List.of(firstId));
        assertFalse(connector.loadFromDb(NAMESPACE).containsKey(firstId));

        assertTrue(connector.delete(NAMESPACE));
        assertFalse(connector.exists(NAMESPACE));
        assertFalse(connector.delete(NAMESPACE));

        connector.flush();
        connector.close();
    }

    @Test
    void saveSkipsNodesWithoutEmbeddingsAndSearchHandlesEmptyNamespaces() {
        InMemoryCollectionAdapter adapter = new InMemoryCollectionAdapter();
        MilvusConnector connector = mockConnector(adapter, 4);
        Map<String, Map<String, Object>> data = new LinkedHashMap<>();
        data.put("node_with_emb", Map.of(
                "id", "node_with_emb",
                "content", "has embedding",
                "embedding", List.of(0.1d, 0.2d, 0.3d, 0.4d),
                "metadata", Map.of()
        ));
        Map<String, Object> nodeWithoutEmbedding = new LinkedHashMap<>();
        nodeWithoutEmbedding.put("id", "node_no_emb");
        nodeWithoutEmbedding.put("content", "missing embedding");
        nodeWithoutEmbedding.put("embedding", null);
        nodeWithoutEmbedding.put("metadata", Map.of());
        data.put("node_no_emb", nodeWithoutEmbedding);

        connector.saveToDb("ns_skip", data);
        assertTrue(connector.loadFromDb("ns_skip").containsKey("node_with_emb"));
        assertFalse(connector.loadFromDb("ns_skip").containsKey("node_no_emb"));
        assertEquals(List.of(), connector.search("ghost_ns", List.of(0.1d, 0.2d, 0.3d, 0.4d), 5));
        assertTrue(connector.deleteNodes("ghost_ns", List.of()));
    }

    @Test
    void createIndexAndHelpersMirrorPythonUtilityMethods() {
        InMemoryCollectionAdapter adapter = new InMemoryCollectionAdapter();
        MilvusConnector connector = mockConnector(adapter, 4);

        connector.createIndex("HNSW", "COSINE", 8, 32, 64);
        assertTrue(adapter.hasIndex());

        connector.createIndex("IVF_FLAT", "L2", 16, 64, 32);
        assertTrue(adapter.hasIndex());

        assertEquals("id in [\"a\", \"b\"]", MilvusConnector.idsExpr(List.of("a", "b")));
        assertTrue(MilvusConnector.truncate("hello", 32).startsWith("hello"));
        assertNotNull(connector.search("ghost_ns", List.of(1.0d, 0.0d, 0.0d, 0.0d), 1, "inner_product"));
    }

    @Test
    @Disabled("Skipped in Python source unless Milvus is reachable at localhost:19530.")
    void liveMilvusIntegrationRequiresExternalServer() {
        // Mirrors the Python skip-if-live-availability test gate.
    }

    private static MilvusConnector mockConnector(InMemoryCollectionAdapter adapter, int dim) {
        MilvusConnector connector = new MilvusConnector(
                "localhost",
                19530,
                "vector_nodes_mock",
                dim,
                "mock_alias",
                "COSINE",
                false
        );
        connector.setCollection(adapter);
        return connector;
    }

    private static Map<String, Map<String, Object>> sampleData() {
        Map<String, Map<String, Object>> data = new LinkedHashMap<>();
        data.put("reme_demo_user_node_01", Map.of(
                "id", "reme_demo_user_node_01",
                "content", "When asked how to debug Python code or find bugs",
                "embedding", List.of(1.0d, 0.0d, 0.0d, 0.0d),
                "metadata", Map.of("type", "reme", "label", "debug_python")
        ));
        data.put("reme_demo_user_node_02", Map.of(
                "id", "reme_demo_user_node_02",
                "content", "When asked about data structures and algorithms",
                "embedding", List.of(0.0d, 1.0d, 0.0d, 0.0d),
                "metadata", Map.of("type", "reme", "label", "dsa")
        ));
        data.put("reme_demo_user_node_03", Map.of(
                "id", "reme_demo_user_node_03",
                "content", "When asked about system design patterns",
                "embedding", List.of(0.0d, 0.0d, 1.0d, 0.0d),
                "metadata", Map.of("type", "reme", "label", "system_design")
        ));
        return data;
    }

    private static final class InMemoryCollectionAdapter implements MilvusConnector.MilvusCollectionAdapter {

        private final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        private boolean hasIndex = true;
        private Integer dimension = 4;

        @Override
        public void insert(List<Map<String, Object>> rows) {
            for (Map<String, Object> row : rows) {
                this.rows.put(String.valueOf(row.get(MilvusConnector.FIELD_ID)), new LinkedHashMap<>(row));
            }
        }

        @Override
        public void delete(String expr) {
            List<String> toDelete = rows.entrySet().stream()
                    .filter(entry -> matches(expr, entry.getValue()))
                    .map(Map.Entry::getKey)
                    .toList();
            toDelete.forEach(rows::remove);
        }

        @Override
        public void flush() {
            // No-op for in-memory parity tests.
        }

        @Override
        public List<Map<String, Object>> query(String expr, List<String> outputFields, Integer limit) {
            List<Map<String, Object>> matched = rows.values().stream()
                    .filter(row -> matches(expr, row))
                    .map(row -> project(row, outputFields))
                    .toList();
            if (limit == null || matched.size() <= limit) {
                return matched;
            }
            return matched.subList(0, limit);
        }

        @Override
        public List<MilvusConnector.MilvusSearchHit> search(
                List<Double> vector,
                String annsField,
                String metricType,
                Map<String, Object> searchParams,
                int limit,
                String expr,
                List<String> outputFields
        ) {
            List<MilvusConnector.MilvusSearchHit> hits = new ArrayList<>();
            for (Map<String, Object> row : rows.values()) {
                if (!matches(expr, row)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<Double> stored = (List<Double>) row.get(annsField);
                if (stored == null || stored.isEmpty()) {
                    continue;
                }
                double score = cosine(vector, stored);
                hits.add(new MilvusConnector.MilvusSearchHit(
                        String.valueOf(row.get(MilvusConnector.FIELD_ID)),
                        project(row, outputFields),
                        score
                ));
            }
            hits.sort(Comparator.comparingDouble(MilvusConnector.MilvusSearchHit::score).reversed());
            if (hits.size() <= limit) {
                return hits;
            }
            return hits.subList(0, limit);
        }

        @Override
        public boolean hasIndex() {
            return hasIndex;
        }

        @Override
        public void createIndex(String indexType, String metricType, Map<String, Object> params) {
            hasIndex = true;
        }

        @Override
        public void dropIndex() {
            hasIndex = false;
        }

        @Override
        public void load() {
            // No-op for in-memory parity tests.
        }

        @Override
        public long numEntities() {
            return rows.size();
        }

        @Override
        public Integer getDimension() {
            return dimension;
        }

        private static boolean matches(String expr, Map<String, Object> row) {
            if (expr == null || expr.isBlank()) {
                return true;
            }
            String trimmed = expr.trim();
            if (trimmed.contains(" in [")) {
                String field = trimmed.substring(0, trimmed.indexOf(" in [")).trim();
                String values = trimmed.substring(trimmed.indexOf('[') + 1, trimmed.lastIndexOf(']'));
                for (String value : values.split(",")) {
                    String candidate = value.trim().replace("\"", "");
                    if (Objects.equals(String.valueOf(row.get(field)), candidate)) {
                        return true;
                    }
                }
                return false;
            }
            if (trimmed.contains("==")) {
                String[] parts = trimmed.split("==", 2);
                return Objects.equals(
                        String.valueOf(row.get(parts[0].trim())),
                        parts[1].trim().replace("\"", "")
                );
            }
            if (trimmed.contains("!=")) {
                String[] parts = trimmed.split("!=", 2);
                return !Objects.equals(
                        String.valueOf(row.get(parts[0].trim())),
                        parts[1].trim().replace("\"", "")
                );
            }
            return true;
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

        private static double cosine(List<Double> left, List<Double> right) {
            double dot = 0.0d;
            double leftNorm = 0.0d;
            double rightNorm = 0.0d;
            for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                dot += left.get(index) * right.get(index);
                leftNorm += left.get(index) * left.get(index);
                rightNorm += right.get(index) * right.get(index);
            }
            if (leftNorm == 0.0d || rightNorm == 0.0d) {
                return 0.0d;
            }
            return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        }
    }
}
