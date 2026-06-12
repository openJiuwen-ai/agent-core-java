/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.vector_store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_file_connector.py}
 * memory vector store coverage.
 */
class MemoryVectorStoreTest {

    @Test
    void asyncUpsertSearchAndDeleteMirrorPythonInMemoryStore() {
        MemoryVectorStore store = new MemoryVectorStore();
        VectorNode first = new VectorNode("node-1", "debug python", List.of(1.0d, 0.0d), Map.of("workspace_id", "ace"));
        VectorNode second = new VectorNode("node-2", "design api", List.of(0.0d, 1.0d), Map.of("workspace_id", "reme"));

        store.asyncUpsert(first).join();
        store.asyncUpsert(second).join();

        List<VectorNode> allResults = store.asyncSearch(List.of(1.0d, 0.0d), 2).join();
        assertEquals(List.of("node-1", "node-2"), allResults.stream().map(VectorNode::getId).toList());

        List<VectorNode> filtered = store.asyncSearch(List.of(1.0d, 0.0d), 2, Map.of("workspace_id", "ace")).join();
        assertEquals(List.of("node-1"), filtered.stream().map(VectorNode::getId).toList());

        assertTrue(store.asyncDelete("node-2").join());
        assertEquals(1, store.count());
    }

    @Test
    void loadFromDictAndGetAllSupportSerializedRoundTrip() {
        MemoryVectorStore store = new MemoryVectorStore();
        Map<String, Map<String, Object>> data = new LinkedHashMap<>();
        data.put("ace-node", Map.of(
                "id", "ace-node",
                "content", "cache results",
                "embedding", List.of(0.1d, 0.2d),
                "metadata", Map.of("workspace_id", "ace")
        ));
        Map<String, Object> skipNode = new LinkedHashMap<>();
        skipNode.put("id", "skip-node");
        skipNode.put("content", "missing embedding");
        skipNode.put("embedding", null);
        skipNode.put("metadata", Map.of("workspace_id", "ace"));
        data.put("skip-node", skipNode);

        store.loadFromDict(data).join();

        assertEquals(1, store.count());
        assertEquals(List.of("ace-node"), store.getAll(Map.of("workspace_id", "ace")).stream().map(VectorNode::getId).toList());
        assertTrue(store.toString().contains("count=1"));
    }
}
