/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.core.foundation.store.graph.test_config} in
 * {@code tests/unit_tests/core/foundation/store/graph/test_config.py}.
 */
class GraphConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsMatchPythonModel() {
        GraphConfig config = GraphConfig.builder()
                .uri(tempDir.resolve("graph.db").toString())
                .build();

        assertEquals("", config.getName());
        assertEquals("", config.getToken());
        assertEquals("milvus", config.getBackend());
        assertEquals(15.0d, config.getTimeout());
        assertTrue(config.getExtras().isEmpty());
        assertEquals(10, config.getMaxConcurrent());
        assertEquals(512, config.getEmbedDim());
        assertEquals(10, config.getEmbedBatchSize());
        assertNull(config.getEmbeddingModel());
        assertNotNull(config.getDbStorageConfig());
        assertNotNull(config.getDbEmbedConfig());
        assertEquals("cosine", config.getDbEmbedConfig().getDistanceMetric());
        assertEquals(5, config.getRequestMaxRetries());
    }

    @Test
    void extrasAcceptsStringKeyedMap() {
        GraphConfig config = GraphConfig.builder()
                .uri(tempDir.resolve("graph.db").toString())
                .extras(Map.of("alias", "default"))
                .build();

        assertEquals(Map.of("alias", "default"), config.getExtras());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void extrasRejectsInvalidShapes() {
        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("graph.db").toString())
                .extras((Object) "not_a_dict")
                .build());

        Map raw = Map.of(1, "value");
        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("graph.db").toString())
                .extras(raw)
                .build());
    }

    @Test
    void filePathUriCreatesParentDirectory() {
        Path db = tempDir.resolve("nested").resolve("graph.db");

        GraphConfig config = GraphConfig.builder().uri(db.toString()).build();

        assertEquals(db.toString(), config.getUri());
        assertTrue(Files.isDirectory(db.getParent()));
    }

    @Test
    void filePathUriDirectoryFailureDoesNotRaise() throws Exception {
        Path occupied = tempDir.resolve("occupied");
        Files.writeString(occupied, "occupied");
        Path db = occupied.resolve("graph.db");

        GraphConfig config = GraphConfig.builder().uri(db.toString()).build();

        assertEquals(db.toString(), config.getUri());
        assertFalse(Files.isDirectory(occupied));
    }

    @Test
    void networkUriConnectionSuccessDoesNotRaise() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread acceptThread = new Thread(() -> {
                try (var ignored = serverSocket.accept()) {
                    // Allow one connection so the validator can complete.
                } catch (Exception ignored) {
                    // The test may close the server before accept completes.
                }
            });
            acceptThread.start();

            GraphConfig config = GraphConfig.builder()
                    .uri("http://127.0.0.1:" + serverSocket.getLocalPort())
                    .timeout(0.5d)
                    .build();

            assertEquals("http://127.0.0.1:" + serverSocket.getLocalPort(), config.getUri());
            acceptThread.join(1000);
        }
    }

    @Test
    void networkUriConnectionFailureDoesNotRaise() {
        GraphConfig config = GraphConfig.builder()
                .uri("http://127.0.0.1:9")
                .timeout(0.001d)
                .build();

        assertEquals("http://127.0.0.1:9", config.getUri());
    }

    @Test
    void numericValidatorsMatchPythonModel() {
        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("bad-timeout.db").toString())
                .timeout(0.0d)
                .build());
        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("bad-concurrency.db").toString())
                .maxConcurrent(-1)
                .build());
        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("bad-dim.db").toString())
                .embedDim(31)
                .build());
        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("bad-batch.db").toString())
                .embedBatchSize(0)
                .build());
    }
}
