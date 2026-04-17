package com.openjiuwen.agent_evolving.checkpointing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileCheckpointStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void constructorCreatesNestedDirectory() {
        Path nested = tempDir.resolve("nested").resolve("dir");

        new FileCheckpointStore(nested.toString());

        assertTrue(Files.exists(nested));
    }

    @Test
    void saveCheckpointWritesSnakeCaseJson() throws IOException {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        String path = store.saveCheckpoint(checkpoint(), "latest.json");

        assertNotNull(path);
        String json = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"run_id\""));
        assertTrue(json.contains("\"operators_state\""));
        assertTrue(json.contains("\"best_score\""));
        assertTrue(json.contains("\"current_epoch_score\""));
    }

    @Test
    void loadCheckpointReadsSnakeCaseFile() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        String path = store.saveCheckpoint(checkpoint(), "snake.json");

        EvolveCheckpoint loaded = store.loadCheckpoint(path);

        assertNotNull(loaded);
        assertEquals("run_1", loaded.getRunId());
        assertEquals(0.8, loaded.getBest().get("best_score"));
        assertEquals("value", loaded.getOperatorsState().get("op_1").get("prompt"));
    }

    @Test
    void loadCheckpointReadsLegacyCamelCaseFileAndNormalizesKeys() throws IOException {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        Path legacyFile = tempDir.resolve("legacy.json");
        Files.writeString(
                legacyFile,
                """
                {
                  "version": "v1",
                  "runId": "legacy_run",
                  "step": {"epoch": 3, "batch": 9},
                  "best": {"bestScore": 0.66},
                  "seed": 7,
                  "operatorsState": {"op_legacy": {"prompt": "old"}},
                  "updaterState": {"step": 2},
                  "searcherState": {},
                  "lastMetrics": {"currentEpochScore": 0.61}
                }
                """,
                StandardCharsets.UTF_8
        );

        EvolveCheckpoint loaded = store.loadCheckpoint(legacyFile.toString());

        assertNotNull(loaded);
        assertEquals("legacy_run", loaded.getRunId());
        assertEquals(0.66, loaded.getBest().get("best_score"));
        assertEquals(0.61, loaded.getLastMetrics().get("current_epoch_score"));
        assertEquals("old", loaded.getOperatorsState().get("op_legacy").get("prompt"));
    }

    @Test
    void loadStateDictSupportsSnakeCaseAndLegacyCamelCase() throws IOException {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        String snakePath = store.saveCheckpoint(checkpoint(), "snake_state.json");

        Path legacyFile = tempDir.resolve("legacy_state.json");
        Files.writeString(
                legacyFile,
                """
                {
                  "operatorsState": {
                    "op_legacy": {"prompt": "legacy"}
                  }
                }
                """,
                StandardCharsets.UTF_8
        );

        assertEquals("value", store.loadStateDict(snakePath).get("op_1").get("prompt"));
        assertEquals("legacy", store.loadStateDict(legacyFile.toString()).get("op_legacy").get("prompt"));
    }

    @Test
    void loadCheckpointAndStateDictReturnNullForMissingFile() {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());

        assertNull(store.loadCheckpoint(tempDir.resolve("missing.json").toString()));
        assertNull(store.loadStateDict(tempDir.resolve("missing.json").toString()));
    }

    @Test
    void loadStateDictReturnsNullWhenOperatorsStateFieldIsMissing() throws IOException {
        FileCheckpointStore store = new FileCheckpointStore(tempDir.toString());
        Path file = tempDir.resolve("no_state.json");
        Files.writeString(file, "{\"version\":\"v1\"}", StandardCharsets.UTF_8);

        assertNull(store.loadStateDict(file.toString()));
    }

    private static EvolveCheckpoint checkpoint() {
        return EvolveCheckpoint.builder()
                .version("v1")
                .runId("run_1")
                .step(Map.of("epoch", 1, "batch", 2))
                .best(new LinkedHashMap<>(Map.of("best_score", 0.8)))
                .seed(42)
                .operatorsState(Map.of("op_1", Map.of("prompt", "value")))
                .updaterState(Map.of("step", 3))
                .searcherState(Map.of())
                .lastMetrics(new LinkedHashMap<>(Map.of("current_epoch_score", 0.75)))
                .build();
    }
}
