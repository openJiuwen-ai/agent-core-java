/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.kv;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.RunnerImpl;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.subagents.SubAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class ApplicationStorageScopeTest {
    @TempDir
    Path workspace;

    private RunnerConfig previousRunnerConfig;

    @BeforeEach
    void rememberRunnerConfiguration() {
        previousRunnerConfig = RunnerConfig.getRunnerConfig();
    }

    @AfterEach
    void restoreRunnerConfiguration() {
        RunnerConfig.setRunnerConfig(previousRunnerConfig);
    }

    @Test
    void runnerCheckpointerAndAgentShareOneStoreWithoutAutomaticClose() {
        ApplicationStorageScope scope = new ApplicationStorageScope();
        CloseClient client = new CloseClient();
        RedisStore store = new RedisStore(client);
        scope.kvStores().register("default", store);
        RunnerImpl runner = new RunnerImpl("scoped", RunnerConfig.builder().distributedMode(false).build(), scope);
        RedisCheckpointer checkpointer = (RedisCheckpointer) CheckpointerFactory.create("redis", Map.of(), scope);
        DeepAgent agent = runner.createDeepAgent(config(null));
        assertSame(scope, agent.getStorageScope());
        assertSame(store, agent.getKvStore());
        assertSame(store, checkpointer.getRedisStore());
        agent.destroy();
        runner.stop();
        assertEquals(0, client.closed);
        store.set("after-stop", "still available");
        assertEquals("still available", store.get("after-stop"));
        checkpointer.close();
        assertEquals(1, client.closed);
    }

    @Test
    void explicitReferencesAndIndependentScopesRemainDistinct() {
        ApplicationStorageScope first = new ApplicationStorageScope();
        ApplicationStorageScope second = new ApplicationStorageScope();
        var a = first.kvStores().create("default", "in_memory", Map.of());
        var b = second.kvStores().create("default", "in_memory", Map.of());
        DeepAgent agent = HarnessFactory.createDeepAgent(config(Map.of("storeRef", "default")), first);
        try {
            assertSame(a, agent.getKvStore());
            assertNotSame(a, b);
            assertThrows(IllegalArgumentException.class,
                    () -> CheckpointerFactory.create("redis", Map.of("storeRef", "default"), first));
            assertThrows(IllegalArgumentException.class,
                    () -> CheckpointerFactory.create("persistence", Map.of("storeRef", "missing"), first));
            assertThrows(IllegalArgumentException.class, () -> CheckpointerFactory.create("persistence",
                    Map.of("storeRef", "default", "db_path", "unused"), first));
        } finally {
            agent.destroy();
        }
    }

    @Test
    void legacyExplicitConnectionBecomesDefaultOnlyWhenAbsent() {
        ApplicationStorageScope scope = new ApplicationStorageScope();
        var first = (RedisCheckpointer) CheckpointerFactory.create("redis",
                Map.of("connection", Map.of("redis_client", new CloseClient())), scope);
        var second = (RedisCheckpointer) CheckpointerFactory.create("redis",
                Map.of("connection", Map.of("redis_client", new CloseClient())), scope);
        assertSame(first.getRedisStore(), scope.kvStores().resolve("default"));
        assertNotSame(first.getRedisStore(), second.getRedisStore());
    }

    @Test
    void sqliteDefaultIsAvailableToSubsequentAgent() {
        ApplicationStorageScope scope = new ApplicationStorageScope();
        var checkpointer = CheckpointerFactory.create("persistence",
                Map.of("db_path", workspace.resolve("cp.db").toString()), scope);
        DeepAgent agent = HarnessFactory.createDeepAgent(config(null), scope);
        try {
            assertInstanceOf(SqliteKVStore.class, scope.kvStores().resolve("default"));
            assertSame(scope.kvStores().resolve("default"), agent.getKvStore());
        } finally {
            agent.destroy();
            checkpointer.close();
        }
    }

    @Test
    void separateScopesKeepSameKeyIndependentAndDefaultsStayOptIn() {
        var first = new ApplicationStorageScope();
        var second = new ApplicationStorageScope();
        var left = first.kvStores().create("default", "in_memory", Map.of());
        var right = second.kvStores().create("default", "in_memory", Map.of());
        left.set("same-key", "left");
        right.set("same-key", "right");
        assertEquals("left", left.get("same-key"));
        assertEquals("right", right.get("same-key"));
        DeepAgent fileAgent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(workspace.toString()).todoStorageType("file").build(), first);
        try {
            assertNull(fileAgent.getKvStore());
            assertInstanceOf(InMemoryCheckpointer.class,
                    CheckpointerFactory.create("in_memory", Map.of(), first));
        } finally {
            fileAgent.destroy();
        }
    }

    @Test
    void explicitReferenceConflictsFailWithoutReplacingTheDefault() {
        var scope = new ApplicationStorageScope();
        var original = scope.kvStores().create("default", "in_memory", Map.of());
        for (Map<String, Object> bad : List.of(
                Map.<String, Object>of("storeRef", "missing"),
                Map.<String, Object>of("storeRef", " "),
                Map.<String, Object>of("storeRef", "default", "conf", Map.of("storeRef", "other")),
                Map.<String, Object>of("storeRef", "default", "conf", Map.of("host", "localhost")))) {
            assertThrows(IllegalArgumentException.class, () -> HarnessFactory.createDeepAgent(config(bad), scope));
            assertSame(original, scope.kvStores().resolve("default"));
        }
    }

    @Test
    void externalNoOpCloseStillReceivesExplicitCloseWithoutOwnershipFlags() {
        var client = new CloseClient();
        var store = new RedisStore(client);
        store.set("before", "value");
        store.close();
        assertEquals(1, client.closed);
        assertEquals("value", store.get("before"));
    }

    @Test
    void runnerRestartReusesEquivalentConfigurationAndRejectsResourceReplacement() {
        var scope = new ApplicationStorageScope();
        Map<String, Object> connection = new LinkedHashMap<>(Map.of("redis_client", new CloseClient()));
        Map<String, Object> resource = Map.of("type", "redis", "conf", Map.of("connection", connection));
        var runner = new RunnerImpl("restart", RunnerConfig.builder().distributedMode(false)
                .kvStoreConfig(resource).build(), scope);
        try {
            assertTrue(runner.start());
            var original = scope.kvStores().resolve("default");
            original.set("state", "retained");
            assertTrue(runner.stop());
            // A fresh but equivalent configuration must not try to register the name again.
            runner.setConfig(RunnerConfig.builder().distributedMode(false)
                    .kvStoreConfig(new LinkedHashMap<>(resource)).build());
            assertTrue(runner.start());
            assertSame(original, scope.kvStores().resolve("default"));
            assertEquals("retained", original.get("state"));
            runner.stop();
            connection.put("redis_client", new CloseClient());
            assertThrows(IllegalArgumentException.class, runner::start);
            assertSame(original, scope.kvStores().resolve("default"));
            assertEquals("retained", original.get("state"));
        } finally {
            runner.stop();
            scope.kvStores().resolve("default").close();
        }
    }

    @Test
    void configuredChildrenKeepTheApplicationScopeAcrossDestruction() {
        var scope = new ApplicationStorageScope();
        var client = new CloseClient();
        var store = new RedisStore(client);
        scope.kvStores().register("default", store);
        var childSpec = SubAgentConfig.builder()
                .agentCard(AgentCard.builder().name("worker").build()).build();
        var parent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath(workspace.toString()).todoStorageType("kv")
                .subagents(List.of(childSpec)).build(), scope);
        DeepAgent child = parent.createSubagent("worker", "child-session");
        try {
            assertSame(scope, child.getStorageScope());
            assertSame(parent.getKvStore(), child.getStorageScope().kvStores().resolve("default"));
            child.getStorageScope().kvStores().resolve("default").set("child", "visible");
            child.destroy();
            assertEquals("visible", parent.getKvStore().get("child"));
            parent.destroy();
            assertEquals(0, client.closed);
            assertEquals("visible", store.get("child"));
        } finally {
            child.destroy();
            parent.destroy();
            store.close();
        }
        assertEquals(1, client.closed);
    }

    private DeepAgentConfig config(Map<String, Object> kv) {
        return DeepAgentConfig.builder().workspacePath(workspace.toString()).todoStorageType("kv")
                .kvStoreConfig(kv).build();
    }

    public static class CloseClient {
        int closed;
        private final Map<String, Object> values = new HashMap<>();

        public void set(String key, Object value) {
            values.put(key, value);
        }

        public Object get(String key) {
            return values.get(key);
        }

        public void close() {
            closed++;
        }
    }
}
