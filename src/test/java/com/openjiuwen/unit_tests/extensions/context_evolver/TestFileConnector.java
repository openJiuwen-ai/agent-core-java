/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.file_connector.JSONFileConnector;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test script for JSONFileConnector with realistic memory scenarios.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/extensions/context_evolver/test_file_connector.py}.
 * <p>
 * Note: These tests create temporary files and directories for testing.
 */
@DisabledIfEnvironmentVariable(named = "SKIP_FILE_CONNECTOR_TESTS", matches = "true")
public class TestFileConnector {

    private Path testOutputDir;
    private MemoryVectorStore vectorStore;
    private JSONFileConnector connector;

    @BeforeEach
    void setUp() throws IOException {
        vectorStore = new MemoryVectorStore();
        connector = new JSONFileConnector(2, false);
        
        // Create test output directory
        testOutputDir = Files.createTempDirectory("file_connector_test");
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup test files
        if (testOutputDir != null && Files.exists(testOutputDir)) {
            Files.walk(testOutputDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore deletion errors
                    }
                });
        }
    }

    // ---------------------------------------------------------------------------
    // Test: Vector Store with Realistic Memories
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test saving and loading vector store with ACE, ReasoningBank, and ReMe memories")
    @Tag("level0")
    void testVectorStoreWithRealisticMemories() throws Exception {
        // Create realistic memories for each type

        // ACE Memory - Java caching
        ACEMemory aceMemory = new ACEMemory();
        aceMemory.setId("mem_ace_001");
        aceMemory.setSection("java_optimization");
        aceMemory.setContent("Use ConcurrentHashMap for thread-safe caching in Java applications");
        aceMemory.setHelpful(5);
        aceMemory.setHarmful(0);
        aceMemory.setNeutral(1);
        aceMemory.setCreatedAt(Instant.parse("2024-01-15T10:30:00Z"));
        aceMemory.setUpdatedAt(Instant.parse("2024-01-20T14:15:00Z"));
        aceMemory.setWorkspaceId("ace_workspace");

        // ReasoningBank Memory - Error handling
        ReasoningBankMemory rbMemory = new ReasoningBankMemory();
        rbMemory.setQuery("How to handle errors in Java?");
        rbMemory.setMemory(Arrays.asList(
            createReasoningBankItem("Exception Handling Best Practices",
                "Use specific exception types instead of catching all exceptions",
                "Always catch specific exceptions (e.g., FileNotFoundException, IllegalArgumentException) rather than using catch (Exception e)"),
            createReasoningBankItem("Try-with-resources",
                "Use try-with-resources for automatic resource cleanup",
                "Use try-with-resources statement for I/O operations to ensure proper cleanup")
        ));
        rbMemory.setLabel(true);
        rbMemory.setWorkspaceId("reasoningbank_workspace");

        // ReMe Memory - API design
        ReMeMemory remeMemory = new ReMeMemory();
        remeMemory.setWhenToUse("When designing RESTful APIs with proper error responses");
        remeMemory.setContent("Return appropriate HTTP status codes (200 for success, 404 for not found, 500 for server errors) and include error details in JSON response body");
        remeMemory.setScore(0.92);
        remeMemory.setCreatedAt(Instant.parse("2024-01-10T09:00:00Z"));
        remeMemory.setUpdatedAt(Instant.parse("2024-01-25T16:45:00Z"));
        remeMemory.setWorkspaceId("reme_workspace");

        // Create dummy embeddings
        List<Double> dummyEmbedding = createDummyEmbedding(1536);

        // Convert memories to VectorNodes and add embeddings
        VectorNode aceNode = aceMemory.toVectorNode();
        aceNode.setEmbedding(dummyEmbedding);

        VectorNode rbNode = rbMemory.toVectorNode();
        rbNode.setEmbedding(dummyEmbedding);

        VectorNode remeNode = remeMemory.toVectorNode();
        remeNode.setEmbedding(dummyEmbedding);

        // Add to vector store
        vectorStore.asyncUpsert(aceNode).join();
        vectorStore.asyncUpsert(rbNode).join();
        vectorStore.asyncUpsert(remeNode).join();

        assertThat(vectorStore.count()).isEqualTo(3);

        // Save to separate files using JSONFileConnector
        Map<String, Map<String, Object>> workspaces = new LinkedHashMap<>();
        
        List<VectorNode> allNodes = vectorStore.getAll();
        for (VectorNode node : allNodes) {
            String workspaceId = (String) node.getMetadata().getOrDefault("workspace_id", "default");
            workspaces.computeIfAbsent(workspaceId, k -> new LinkedHashMap<>());
            workspaces.get(workspaceId).put(node.getId(), node.toMap());
        }

        Map<String, String> savedFiles = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : workspaces.entrySet()) {
            String workspaceId = entry.getKey();
            String filePath = testOutputDir.resolve(workspaceId + "_memories.json").toString();
            connector.saveToFile(filePath, entry.getValue());
            savedFiles.put(workspaceId, filePath);
        }

        // Verify files were saved
        assertThat(savedFiles).hasSize(3);
        for (String filePath : savedFiles.values()) {
            assertThat(Files.exists(Paths.get(filePath))).isTrue();
            assertThat(Files.size(Paths.get(filePath))).isGreaterThan(0);
        }

        // Load from files and verify
        MemoryVectorStore newVectorStore = new MemoryVectorStore();
        
        for (Map.Entry<String, String> entry : savedFiles.entrySet()) {
            Map<String, Object> loadedData = connector.loadFromFile(entry.getValue());
            
            for (Map.Entry<String, Object> nodeEntry : loadedData.entrySet()) {
                VectorNode loadedNode = VectorNode.fromMap((Map<String, Object>) nodeEntry.getValue());
                loadedNode.setEmbedding(dummyEmbedding);
                newVectorStore.asyncUpsert(loadedNode).join();
            }
        }

        assertThat(newVectorStore.count()).isEqualTo(3);

        // Verify loaded memories
        List<VectorNode> loadedNodes = newVectorStore.getAll();
        for (VectorNode loadedNode : loadedNodes) {
            String memoryType = (String) loadedNode.getMetadata().get("type");

            if ("ace_memory".equals(memoryType)) {
                assertThat(loadedNode.getMetadata().get("section")).isEqualTo("java_optimization");
                assertThat(loadedNode.getMetadata().get("workspace_id")).isEqualTo("ace_workspace");
            } else if ("reasoning_bank_memory".equals(memoryType)) {
                assertThat(loadedNode.getMetadata().get("workspace_id")).isEqualTo("reasoningbank_workspace");
            } else if ("reme_memory".equals(memoryType)) {
                assertThat(loadedNode.getMetadata().get("workspace_id")).isEqualTo("reme_workspace");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Test: Save and Load Single Node
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test save and load single vector node")
    @Tag("level0")
    void testSaveAndLoadSingleNode() throws Exception {
        VectorNode node = new VectorNode("test_node_001", "Test content", null, new LinkedHashMap<>());
        node.getMetadata().put("type", "test");
        node.getMetadata().put("workspace_id", "test_workspace");
        node.setEmbedding(createDummyEmbedding(4));

        // Save
        String filePath = testOutputDir.resolve("test_node.json").toString();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(node.getId(), node.toMap());
        connector.saveToFile(filePath, data);

        // Load
        Map<String, Object> loadedData = connector.loadFromFile(filePath);
        assertThat(loadedData).hasSize(1);
        assertThat(loadedData.containsKey("test_node_001")).isTrue();

        // Verify content
        Map<String, Object> nodeData = (Map<String, Object>) loadedData.get("test_node_001");
        assertThat(nodeData.get("content")).isEqualTo("Test content");
    }

    // ---------------------------------------------------------------------------
    // Test: Empty File Handling
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test handling of empty data")
    @Tag("level0")
    void testEmptyDataHandling() throws Exception {
        // Save empty data
        String filePath = testOutputDir.resolve("empty.json").toString();
        Map<String, Object> emptyData = new LinkedHashMap<>();
        connector.saveToFile(filePath, emptyData);

        // Load empty file
        Map<String, Object> loadedData = connector.loadFromFile(filePath);
        assertThat(loadedData).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // Test: Directory Creation
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("Test automatic directory creation")
    @Tag("level0")
    void testDirectoryCreation() throws Exception {
        String nestedPath = testOutputDir.resolve("nested/sub/dir/test.json").toString();
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("test_key", "test_value");
        
        connector.saveToFile(nestedPath, data);
        
        assertThat(Files.exists(Paths.get(nestedPath))).isTrue();
        assertThat(Files.exists(testOutputDir.resolve("nested/sub/dir"))).isTrue();
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private List<Double> createDummyEmbedding(int size) {
        List<Double> embedding = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            embedding.add(0.1);
        }
        return embedding;
    }

    private ReasoningBankMemoryItem createReasoningBankItem(
            String title, String description, String content) {
        ReasoningBankMemoryItem item = new ReasoningBankMemoryItem();
        item.setTitle(title);
        item.setDescription(description);
        item.setContent(content);
        return item;
    }
}