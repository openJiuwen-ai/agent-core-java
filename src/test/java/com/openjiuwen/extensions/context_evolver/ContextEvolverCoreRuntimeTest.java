/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.file_connector.JSONFileConnector;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.op.ParallelOp;
import com.openjiuwen.extensions.context_evolver.core.op.SequentialOp;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextEvolverCoreRuntimeTest {

    private Map<String, Object> configSnapshot;

    @BeforeEach
    void captureState() {
        configSnapshot = Config.snapshot();
        ServiceContext.getInstance().clear();
    }

    @AfterEach
    void restoreState() {
        Config.restore(configSnapshot);
        ServiceContext.getInstance().clear();
    }

    @TempDir
    Path tempDir;

    @Test
    void configReloadHonorsEnvPrecedenceTypeConversionAndEnvironmentFallback() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(
            envFile,
            "BOOL=true\nINT_VALUE=7\nFLOAT_VALUE=3.5\nLOCAL_ONLY=from_env\n",
            StandardCharsets.UTF_8
        );

        Path yamlFile = tempDir.resolve("config.yaml");
        Files.writeString(
            yamlFile,
            "BOOL: false\nYAML_ONLY: yaml_value\nFLOAT_VALUE: 9.25\n",
            StandardCharsets.UTF_8
        );

        Config.reload(yamlFile.toString(), envFile.toString());

        assertEquals(Boolean.TRUE, Config.get("BOOL"));
        assertEquals(7, Config.getInt("INT_VALUE", 0));
        assertEquals(3.5d, ((Number) Config.get("FLOAT_VALUE")).doubleValue(), 0.0001d);
        assertEquals("yaml_value", Config.getString("YAML_ONLY"));
        assertEquals("fallback", Config.getString("MISSING", "fallback"));

        String pathValue = System.getenv("PATH");
        assertNotNull(pathValue);
        Config.delete("PATH");
        assertEquals(pathValue, Config.getString("PATH"));
    }

    @Test
    void runtimeContextStoresTypedValuesAndReturnsCopies() {
        RuntimeContext context = new RuntimeContext();
        context.set("name", "memory");
        context.set("count", 2);
        context.set("enabled", true);

        Map<String, Object> nested = new HashMap<>();
        nested.put("kind", "ace");
        context.set("nested", nested);

        assertEquals("memory", context.getString("name"));
        assertEquals(2, context.getInt("count", -1));
        assertTrue(context.getBoolean("enabled", false));
        assertEquals("fallback", context.getString("missing", "fallback"));
        assertTrue(context.has("nested"));

        Map<String, Object> snapshot = context.toDict();
        snapshot.put("name", "changed");
        assertEquals("memory", context.getString("name"));
    }

    @Test
    void serviceContextIsSingletonAndExposesRegisteredServices() {
        ServiceContext first = ServiceContext.getInstance();
        ServiceContext second = ServiceContext.getInstance();

        assertSame(first, second);

        MemoryVectorStore vectorStore = new MemoryVectorStore();
        first.registerService("llm", "demo-llm");
        first.registerService("embedding_model", "demo-embedding");
        first.registerService("vector_store", vectorStore);

        assertEquals("demo-llm", second.getLlm());
        assertEquals("demo-embedding", second.getEmbeddingModel());
        assertSame(vectorStore, second.getVectorStore());

        second.clear();
        assertFalse(second.hasService("llm"));
    }

    @Test
    void jsonFileConnectorPersistsUnicodeAndSupportsAsciiEscaping() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "测试");
        data.put("items", List.of("alpha", "beta"));

        Path unicodeFile = tempDir.resolve("nested").resolve("unicode.json");
        JSONFileConnector connector = new JSONFileConnector(4, false);
        connector.saveToFile(unicodeFile.toString(), data);

        Map<String, Object> loaded = connector.loadFromFile(unicodeFile.toString());
        assertEquals(data, loaded);
        assertTrue(JSONFileConnector.exists(unicodeFile.toString()));

        String unicodeRaw = Files.readString(unicodeFile, StandardCharsets.UTF_8);
        assertTrue(unicodeRaw.contains("测试"));
        assertTrue(unicodeRaw.contains(System.lineSeparator() + "    \"items\""));

        Path asciiFile = tempDir.resolve("ascii.json");
        JSONFileConnector asciiConnector = new JSONFileConnector(2, true);
        asciiConnector.saveToFile(asciiFile.toString(), data);
        String asciiRaw = Files.readString(asciiFile, StandardCharsets.UTF_8);

        assertFalse(asciiRaw.contains("测试"));
        assertTrue(asciiRaw.contains("\\u"));
        assertTrue(JSONFileConnector.delete(unicodeFile.toString()));
        assertFalse(JSONFileConnector.exists(unicodeFile.toString()));
    }

    @Test
    void memoryVectorStoreSupportsSimilarityFilteringAndSerializedReload() {
        MemoryVectorStore store = new MemoryVectorStore();

        VectorNode aceNode = new VectorNode(
            "ace",
            "cache python results",
            List.of(1.0, 0.0),
            Map.of("workspace_id", "workspace-a", "type", "ace_memory")
        );
        VectorNode remeNode = new VectorNode(
            "reme",
            "design api responses",
            List.of(0.0, 1.0),
            Map.of("workspace_id", "workspace-b", "type", "reme_memory")
        );

        store.asyncUpsert(aceNode).join();
        store.asyncUpsert(remeNode).join();

        List<VectorNode> filtered = store.asyncSearch(
            List.of(0.9, 0.1),
            10,
            Map.of("workspace_id", "workspace-a")
        ).join();
        assertEquals(List.of("ace"), filtered.stream().map(VectorNode::getId).toList());

        Map<String, Map<String, Object>> serialized = new HashMap<>();
        serialized.put("ace", aceNode.toDict());
        serialized.put("reme", remeNode.toDict());

        MemoryVectorStore restored = new MemoryVectorStore();
        restored.loadFromDict(serialized).join();
        assertEquals(2, restored.count());
        assertTrue(restored.asyncDelete("reme").join());
        assertFalse(restored.asyncDelete("missing").join());
    }

    @Test
    void baseOpCompositionSharesContextAndServiceAccess() {
        ServiceContext serviceContext = ServiceContext.getInstance();
        serviceContext.registerService("llm", "shared-llm");

        RuntimeContext sequentialContext = new RuntimeContext();
        SequentialOp sequential = new SequentialOp(new SetValueOp("first", "A"))
            .then(new ServiceEchoOp("llm_seen"));
        sequential.execute(sequentialContext).join();

        assertEquals("A", sequentialContext.getString("first"));
        assertEquals("shared-llm", sequentialContext.getString("llm_seen"));
        assertTrue(sequential.toString().contains(">>"));

        RuntimeContext parallelContext = new RuntimeContext();
        ParallelOp parallel = new ParallelOp(
            new AsyncSetValueOp("left", "L"),
            new AsyncSetValueOp("right", "R")
        ).parallel(new AsyncSetValueOp("extra", "E"));
        parallel.execute(parallelContext).join();

        assertEquals("L", parallelContext.getString("left"));
        assertEquals("R", parallelContext.getString("right"));
        assertEquals("E", parallelContext.getString("extra"));
        assertTrue(parallel.toString().contains("|"));
    }

    private static final class SetValueOp extends BaseOp {
        private final String key;
        private final String value;

        private SetValueOp(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
            context.set(key, value);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class AsyncSetValueOp extends BaseOp {
        private final String key;
        private final String value;

        private AsyncSetValueOp(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
            return CompletableFuture.runAsync(() -> context.set(key, value));
        }
    }

    private static final class ServiceEchoOp extends BaseOp {
        private final String targetKey;

        private ServiceEchoOp(String targetKey) {
            this.targetKey = targetKey;
        }

        @Override
        protected CompletableFuture<Void> asyncExecute(RuntimeContext context) {
            context.set(targetKey, getLlm());
            return CompletableFuture.completedFuture(null);
        }
    }
}
