/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.core.vector_store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.extensions.context_evolver.core.file_connector.JsonFileConnector;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemoryMetadata;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test
    @SuppressWarnings("unchecked")
    void realisticMemoriesSurviveWorkspaceFileRoundTrip(@TempDir Path tempDir) {
        MemoryVectorStore vectorStore = new MemoryVectorStore();

        ACEMemory aceMemory = new ACEMemory(
                "mem_ace_001",
                "python_optimization",
                "Use functools.lru_cache decorator for memoization to cache function results");
        aceMemory.setHelpful(5);
        aceMemory.setHarmful(0);
        aceMemory.setNeutral(1);
        aceMemory.setWorkspaceId("ace_workspace");

        ReasoningBankMemory reasoningBankMemory = new ReasoningBankMemory();
        reasoningBankMemory.setQuery("How to handle errors in Python?");
        reasoningBankMemory.setMemory(List.of(
                new ReasoningBankMemoryItem(
                        "Exception Handling Best Practices",
                        "Use specific exception types instead of catching all exceptions",
                        "Always catch specific exceptions rather than using bare except clauses"),
                new ReasoningBankMemoryItem(
                        "Context Managers",
                        "Use context managers for resource cleanup",
                        "Use 'with' statement for file operations to ensure proper cleanup even if errors occur")));
        reasoningBankMemory.setLabel(true);
        reasoningBankMemory.setWorkspaceId("reasoningbank_workspace");

        ReMeMemoryMetadata metadata = new ReMeMemoryMetadata();
        metadata.setTags(List.of("api", "rest", "http", "error-handling"));
        metadata.setStepType("design");
        metadata.setToolsUsed(List.of("flask", "fastapi"));
        metadata.setConfidence(0.95d);
        metadata.setFreq(8);
        metadata.setUtility(0.9d);

        ReMeMemory remeMemory = new ReMeMemory();
        remeMemory.setWhenToUse("When designing RESTful APIs with proper error responses");
        remeMemory.setContent("Return appropriate HTTP status codes and include error details in JSON response body");
        remeMemory.setScore(0.92d);
        remeMemory.setMetadata(metadata);
        remeMemory.setWorkspaceId("reme_workspace");

        List<Double> dummyEmbedding = List.of(0.1d, 0.1d, 0.1d, 0.1d);
        VectorNode aceNode = aceMemory.toVectorNode();
        aceNode.setEmbedding(dummyEmbedding);
        VectorNode reasoningBankNode = reasoningBankMemory.toVectorNode();
        reasoningBankNode.setEmbedding(dummyEmbedding);
        VectorNode remeNode = remeMemory.toVectorNode();
        remeNode.setEmbedding(dummyEmbedding);

        vectorStore.asyncUpsert(aceNode).join();
        vectorStore.asyncUpsert(reasoningBankNode).join();
        vectorStore.asyncUpsert(remeNode).join();
        assertEquals(3, vectorStore.count());

        Map<String, Map<String, Object>> workspaces = new LinkedHashMap<>();
        for (VectorNode node : vectorStore.getAll()) {
            String workspaceId = String.valueOf(node.getMetadata().getOrDefault("workspace_id", "default"));
            workspaces.computeIfAbsent(workspaceId, ignored -> new LinkedHashMap<>())
                    .put(node.getId(), node.toDict());
        }

        JsonFileConnector connector = new JsonFileConnector();
        MemoryVectorStore restoredStore = new MemoryVectorStore();
        for (Map.Entry<String, Map<String, Object>> workspace : workspaces.entrySet()) {
            Path filePath = tempDir.resolve(workspace.getKey() + "_memories.json");
            connector.saveToFile(filePath.toString(), workspace.getValue());

            Map<String, Object> loadedData = connector.loadFromFile(filePath.toString());
            Map<String, Map<String, Object>> typedData = new LinkedHashMap<>();
            loadedData.forEach((key, value) -> typedData.put(key, (Map<String, Object>) value));
            restoredStore.loadFromDict(typedData).join();
        }

        assertEquals(3, restoredStore.count());
        for (VectorNode loadedNode : restoredStore.getAll()) {
            String memoryType = String.valueOf(loadedNode.getMetadata().get("type"));
            if ("ace_memory".equals(memoryType)) {
                ACEMemory restoredAce = ACEMemory.fromVectorNode(loadedNode);
                assertEquals(aceMemory.getSection(), restoredAce.getSection());
                assertEquals(aceMemory.getContent(), restoredAce.getContent());
                assertEquals("ace_workspace", restoredAce.getWorkspaceId());
            } else if ("reasoning_bank_memory".equals(memoryType)) {
                ReasoningBankMemory restoredReasoningBank = ReasoningBankMemory.fromVectorNode(loadedNode);
                assertEquals(reasoningBankMemory.getQuery(), restoredReasoningBank.getQuery());
                assertEquals(reasoningBankMemory.getMemory().size(), restoredReasoningBank.getMemory().size());
                assertEquals("reasoningbank_workspace", restoredReasoningBank.getWorkspaceId());
            } else if ("reme_memory".equals(memoryType)) {
                ReMeMemory restoredReMe = ReMeMemory.fromVectorNode(loadedNode);
                assertEquals(remeMemory.getWhenToUse(), restoredReMe.getWhenToUse());
                assertEquals(remeMemory.getContent(), restoredReMe.getContent());
                assertEquals(0.92d, restoredReMe.getScore(), 0.000001d);
                assertEquals("reme_workspace", restoredReMe.getWorkspaceId());
                assertEquals(List.of("api", "rest", "http", "error-handling"), restoredReMe.getMetadata().getTags());
            }
        }
    }
}
