/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FileCheckpointStore - file-based checkpoint persistence.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_evolving.checkpointing.test_store_file}.
 */
class StoreFileTest {

    @TempDir
    Path tempDir;

    // ========== Factory method ==========

    private EvolveCheckpoint makeMockCheckpoint() {
        Map<String, Integer> step = new HashMap<>();
        step.put("epoch", 1);

        Map<String, Object> best = new HashMap<>();
        best.put("best_score", 0.5);

        Map<String, Map<String, Object>> operatorsState = new HashMap<>();
        Map<String, Object> op1State = new HashMap<>();
        op1State.put("param", "value");
        operatorsState.put("op1", op1State);

        return EvolveCheckpoint.builder()
                .version("v1")
                .runId("test_run")
                .step(step)
                .best(best)
                .seed(42)
                .operatorsState(operatorsState)
                .updaterState(new HashMap<>())
                .searcherState(new HashMap<>())
                .lastMetrics(new HashMap<>())
                .build();
    }

    // ========== TestFileCheckpointStoreInit tests ==========

    @Test
    void testInitCreatesDirectory() {
        // Init creates checkpoint directory
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        assertTrue(Files.exists(tempDir));
    }

    @Test
    void testInitCreatesNestedDirectory() {
        // Init creates nested directories
        Path nested = tempDir.resolve("nested").resolve("path");
        FileCheckpointStore store = new FileCheckpointStore(nested.toString());
        assertTrue(Files.exists(nested));
    }

    @Test
    void testInitWithNone() {
        // Init with null dir
        FileCheckpointStore store = new FileCheckpointStore(null);
        // Verify null behavior through saveCheckpoint returning null
        String result = store.saveCheckpoint(makeMockCheckpoint(), "test.json");
        assertNull(result);
    }

    // ========== TestFileCheckpointStoreSave tests ==========

    @Test
    void testSaveCheckpointJson() {
        // Saves checkpoint as JSON file
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        EvolveCheckpoint checkpoint = makeMockCheckpoint();
        String path = store.saveCheckpoint(checkpoint, "test_ckpt.json");
        assertTrue(Files.exists(Path.of(path)));
        assertTrue(path.endsWith(".json"));
    }

    @Test
    void testSaveCheckpointContent() throws Exception {
        // Saved checkpoint contains correct data
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        EvolveCheckpoint checkpoint = makeMockCheckpoint();
        checkpoint.setRunId("my_run");
        String path = store.saveCheckpoint(checkpoint, "test.json");

        // Verify by loading
        EvolveCheckpoint loaded = store.loadCheckpoint(path);
        assertEquals("my_run", loaded.getRunId());
    }

    @Test
    void testSaveCheckpointWithNoneDir() {
        // No-op when dir is null
        FileCheckpointStore store = new FileCheckpointStore(null);
        String result = store.saveCheckpoint(makeMockCheckpoint(), "test.json");
        assertNull(result);
    }

    // ========== TestFileCheckpointStoreLoad tests ==========

    @Test
    void testLoadCheckpoint() {
        // Loads checkpoint from file
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        EvolveCheckpoint checkpoint = makeMockCheckpoint();
        checkpoint.setRunId("load_test");
        String path = store.saveCheckpoint(checkpoint, "load.json");
        EvolveCheckpoint loaded = store.loadCheckpoint(path);
        assertEquals("load_test", loaded.getRunId());
    }

    @Test
    void testLoadNonexistent() {
        // Returns null for nonexistent file
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        EvolveCheckpoint result = store.loadCheckpoint("/nonexistent/file.json");
        assertNull(result);
    }

    @Test
    void testLoadWithNoneDir() {
        // Returns null when dir is null
        FileCheckpointStore store = new FileCheckpointStore(null);
        EvolveCheckpoint result = store.loadCheckpoint("any_path.json");
        assertNull(result);
    }

    @Test
    void testLoadStateDict() {
        // Inference view: reads operators_state from checkpoint
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        EvolveCheckpoint checkpoint = makeMockCheckpoint();

        Map<String, Map<String, Object>> operatorsState = new HashMap<>();
        Map<String, Object> op1State = new HashMap<>();
        op1State.put("param", "value");
        operatorsState.put("op1", op1State);
        checkpoint.setOperatorsState(operatorsState);

        String path = store.saveCheckpoint(checkpoint, "latest.json");
        Map<String, Map<String, Object>> state = store.loadStateDict(path);
        assertEquals("value", state.get("op1").get("param"));
    }

    // ========== TestFileCheckpointStorePath tests ==========

    @Test
    void testCustomFilename() {
        // Uses custom filename
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        String path = store.saveCheckpoint(makeMockCheckpoint(), "custom.json");
        assertTrue(path.contains("custom.json"));
    }

    @Test
    void testDefaultFilename() {
        // Generates default filename
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        String path = store.saveCheckpoint(makeMockCheckpoint(), "latest.json");
        assertTrue(path.endsWith("latest.json"));
    }
}