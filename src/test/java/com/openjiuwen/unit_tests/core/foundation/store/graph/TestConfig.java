/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.graph.GraphConfig;
import com.openjiuwen.core.foundation.store.graph.GraphStoreIndexConfig;
import com.openjiuwen.core.foundation.store.vector_fields.MilvusAUTO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/core/foundation/store/graph/test_config.py}.
 */
class TestConfig {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("GraphConfig defaults match Python model defaults")
    void testDefaults() {
        GraphConfig cfg = minimalConfig(tempDir.resolve("graph_db"));

        assertEquals("", cfg.getName());
        assertEquals("", cfg.getToken());
        assertEquals("milvus", cfg.getBackend());
        assertEquals(15.0, cfg.getTimeout());
        assertEquals(10, cfg.getMaxConcurrent());
        assertEquals(512, cfg.getEmbedDim());
        assertEquals(10, cfg.getEmbedBatchSize());
        assertNull(cfg.getEmbeddingCls());
        assertEquals(5, cfg.getRequestMaxRetries());
    }

    @Test
    @DisplayName("extras accepts dict with string keys")
    void testValidDictWithStringKeysPasses() {
        GraphConfig cfg = GraphConfig.builder()
                .uri(tempDir.resolve("graph_db").toString())
                .extras(Map.of("alias", "default"))
                .dbEmbedConfig(minimalEmbedConfig())
                .build();

        assertEquals(Map.of("alias", "default"), cfg.getExtras());
    }

    @Test
    @DisplayName("extras rejects non-dict values")
    void testNonDictExtrasRaises() {
        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("graph_db").toString())
                .extras((Object) "not_a_dict")
                .dbEmbedConfig(minimalEmbedConfig())
                .build());
    }

    @Test
    @DisplayName("extras rejects non-string keys")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testDictWithNonStringKeysRaises() {
        Map raw = Map.of(1, "value");

        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("graph_db").toString())
                .extras(raw)
                .dbEmbedConfig(minimalEmbedConfig())
                .build());
    }

    @Test
    @DisplayName("file path URI creates parent directory")
    void testFilePathUriCreatesParentDir() {
        Path db = tempDir.resolve("some").resolve("path").resolve("to").resolve("db");

        GraphConfig cfg = minimalConfig(db);

        assertEquals(db.toString(), cfg.getUri());
        assertTrue(Files.isDirectory(db.getParent()));
    }

    @Test
    @DisplayName("file path URI directory creation failure is logged but not raised")
    void testFilePathUriMakedirsFailureNoRaise() throws Exception {
        Path parentAsFile = tempDir.resolve("not_a_directory");
        Files.writeString(parentAsFile, "occupied");
        Path db = parentAsFile.resolve("db");

        GraphConfig cfg = minimalConfig(db);

        assertEquals(db.toString(), cfg.getUri());
        assertFalse(Files.isDirectory(parentAsFile));
    }

    @Test
    @DisplayName("network URI connection success does not raise")
    void testNetworkUriSuccessNoRaise() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptThread = new Thread(() -> {
                try (var ignored = server.accept()) {
                    // Accept one connection so GraphConfig's connect check can complete.
                } catch (Exception ignored) {
                    // Test cleanup may close the socket first.
                }
            });
            acceptThread.start();

            GraphConfig cfg = GraphConfig.builder()
                    .uri("http://127.0.0.1:" + server.getLocalPort())
                    .timeout(0.5)
                    .dbEmbedConfig(minimalEmbedConfig())
                    .build();

            assertEquals("http://127.0.0.1:" + server.getLocalPort(), cfg.getUri());
            acceptThread.join(1000);
        }
    }

    @Test
    @DisplayName("network URI connection failure is logged but not raised")
    void testNetworkUriFailureLogsError() {
        GraphConfig cfg = GraphConfig.builder()
                .uri("http://127.0.0.1:9")
                .timeout(0.001)
                .dbEmbedConfig(minimalEmbedConfig())
                .build();

        assertEquals("http://127.0.0.1:9", cfg.getUri());
    }

    @Test
    @DisplayName("timeout must be greater than zero")
    void testTimeoutGtZero() {
        GraphConfig cfg = GraphConfig.builder()
                .uri(tempDir.resolve("db").toString())
                .timeout(1.0)
                .dbEmbedConfig(minimalEmbedConfig())
                .build();
        assertEquals(1.0, cfg.getTimeout());

        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("bad_timeout").toString())
                .timeout(0)
                .dbEmbedConfig(minimalEmbedConfig())
                .build());
    }

    @Test
    @DisplayName("max_concurrent must be greater than or equal to zero")
    void testMaxConcurrentGeZero() {
        GraphConfig cfg = GraphConfig.builder()
                .uri(tempDir.resolve("db").toString())
                .maxConcurrent(0)
                .dbEmbedConfig(minimalEmbedConfig())
                .build();
        assertEquals(0, cfg.getMaxConcurrent());

        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("bad_max").toString())
                .maxConcurrent(-1)
                .dbEmbedConfig(minimalEmbedConfig())
                .build());
    }

    @Test
    @DisplayName("embed_dim must be greater than or equal to 32")
    void testEmbedDimGe32() {
        GraphConfig cfg = GraphConfig.builder()
                .uri(tempDir.resolve("db").toString())
                .embedDim(32)
                .dbEmbedConfig(minimalEmbedConfig())
                .build();
        assertEquals(32, cfg.getEmbedDim());

        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("bad_dim").toString())
                .embedDim(31)
                .dbEmbedConfig(minimalEmbedConfig())
                .build());
    }

    @Test
    @DisplayName("embed_batch_size must be greater than or equal to one")
    void testEmbedBatchSizeGeOne() {
        GraphConfig cfg = GraphConfig.builder()
                .uri(tempDir.resolve("db").toString())
                .embedBatchSize(1)
                .dbEmbedConfig(minimalEmbedConfig())
                .build();
        assertEquals(1, cfg.getEmbedBatchSize());

        assertThrows(IllegalArgumentException.class, () -> GraphConfig.builder()
                .uri(tempDir.resolve("bad_batch").toString())
                .embedBatchSize(0)
                .dbEmbedConfig(minimalEmbedConfig())
                .build());
    }

    private static GraphConfig minimalConfig(Path uri) {
        return GraphConfig.builder()
                .uri(uri.toString())
                .dbEmbedConfig(minimalEmbedConfig())
                .build();
    }

    private static GraphStoreIndexConfig minimalEmbedConfig() {
        return new GraphStoreIndexConfig(new MilvusAUTO(), "cosine", Map.of(), null, null);
    }
}
