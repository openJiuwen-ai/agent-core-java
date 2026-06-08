/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for file-based checkpoint persistence.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/checkpointing/test_store_file.py}.
 */
class FileCheckpointStoreTest {

    @TempDir
    Path tempDir;

    private EvolveCheckpoint makeMockCheckpoint() {
        Map<String, Integer> step = new LinkedHashMap<>();
        step.put("epoch", 1);
        Map<String, Object> best = new LinkedHashMap<>();
        best.put("best_score", 0.5);
        Map<String, Map<String, Object>> operatorsState = new LinkedHashMap<>();
        operatorsState.put("op1", Map.of("param", "value"));
        return EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(step)
                .best(best)
                .seed(42)
                .operatorsState(operatorsState)
                .updaterState(Map.of())
                .searcherState(Map.of())
                .lastMetrics(Map.of())
                .build();
    }

    @Test
    void testInitCreatesDirectory() {
        new FileCheckpointStore(tempDir.toString());
        assertTrue(Files.exists(tempDir));
    }

    @Test
    void testInitCreatesNestedDirectory() {
        Path nested = tempDir.resolve("nested").resolve("path");
        new FileCheckpointStore(nested.toString());
        assertTrue(Files.exists(nested));
    }

    @Test
    void testInitWithNone() {
        FileCheckpointStore store = new FileCheckpointStore(null);
        assertNull(store.saveCheckpoint(makeMockCheckpoint(), "test.json"));
    }

    @Test
    void testSaveCheckpointJson() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        String path = store.saveCheckpoint(makeMockCheckpoint(), "test_ckpt.json");
        assertTrue(Files.exists(Path.of(path)));
        assertTrue(path.endsWith(".json"));
    }

    @Test
    void testSaveCheckpointContent() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        EvolveCheckpoint checkpoint = makeMockCheckpoint();
        checkpoint.setRunId("my_run");
        String path = store.saveCheckpoint(checkpoint, "test.json");
        EvolveCheckpoint loaded = store.loadCheckpoint(path);
        assertEquals("my_run", loaded.getRunId());
    }

    @Test
    void testSaveCheckpointWithNoneDir() {
        FileCheckpointStore store = new FileCheckpointStore(null);
        assertNull(store.saveCheckpoint(makeMockCheckpoint(), "test.json"));
    }

    @Test
    void testLoadCheckpoint() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        EvolveCheckpoint checkpoint = makeMockCheckpoint();
        checkpoint.setRunId("load_test");
        String path = store.saveCheckpoint(checkpoint, "load.json");
        EvolveCheckpoint loaded = store.loadCheckpoint(path);
        assertEquals("load_test", loaded.getRunId());
    }

    @Test
    void testLoadNonexistent() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        assertNull(store.loadCheckpoint(tempDir.resolve("missing.json").toString()));
    }

    @Test
    void testLoadWithNoneDir() {
        FileCheckpointStore store = new FileCheckpointStore(null);
        assertNull(store.loadCheckpoint("any_path.json"));
    }

    @Test
    void testLoadStateDict() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        EvolveCheckpoint checkpoint = makeMockCheckpoint();
        checkpoint.setOperatorsState(Map.of("op1", Map.of("param", "value")));
        String path = store.saveCheckpoint(checkpoint, "latest.json");
        assertEquals(Map.of("op1", Map.of("param", "value")), store.loadStateDict(path));
    }

    @Test
    void testCustomFilename() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        String path = store.saveCheckpoint(makeMockCheckpoint(), "custom.json");
        assertTrue(path.contains("custom.json"));
    }

    @Test
    void testDefaultFilename() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        String path = store.saveCheckpoint(makeMockCheckpoint());
        assertTrue(path.endsWith("latest.json"));
    }
}
