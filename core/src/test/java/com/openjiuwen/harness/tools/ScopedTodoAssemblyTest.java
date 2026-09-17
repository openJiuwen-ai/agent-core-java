/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openjiuwen.core.foundation.store.kv.ApplicationStorageScope;
import com.openjiuwen.core.foundation.store.kv.SqliteKVStore;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ScopedTodoAssemblyTest {
    @TempDir
    Path workspace;

    @Test
    void actualTodoToolsUseNamedStoreAndTtl() throws Exception {
        var scope = new ApplicationStorageScope();
        var client = new KvTodoExpiryTest.Client();
        scope.kvStores().register("todos", new RedisStore(client));
        var config = config(Map.of("storeRef", "todos", "ttl", Map.of("default_ttl", 0.5, "refresh_on_read", true)));
        DeepAgent agent = spy(HarnessFactory.createDeepAgent(config, scope));
        var rail = new TaskPlanningRail();
        try {
            rail.init(agent);
            var tools = ArgumentCaptor.forClass(Tool.class);
            verify(agent, times(4)).registerHarnessTool(tools.capture());
            Tool create = tools.getAllValues().stream().filter(t -> "todo_create".equals(t.getCard().getName()))
                    .findFirst().orElseThrow();
            create.invoke(Map.of("session_id", "shared", "tasks", List.of(Map.of("content", "verify TTL", "activeForm",
                    "verifying TTL", "description", "write and refresh"))));
            assertEquals(1, client.atomicWrites);
            assertEquals(30, client.seconds);
            Tool list = tools.getAllValues().stream().filter(t -> "todo_list".equals(t.getCard().getName())).findFirst()
                    .orElseThrow();
            list.invoke(Map.of("session_id", "shared"));
            assertEquals(1, client.refreshes);
        } finally {
            rail.uninit(agent);
            agent.destroy();
        }
    }

    @Test
    void firstTodoStoreBecomesSharedDefault() {
        var scope = new ApplicationStorageScope();
        DeepAgent agent = HarnessFactory.createDeepAgent(config(Map.of()), scope);
        var rail = new TaskPlanningRail();
        try {
            rail.init(agent);
            assertTrue(scope.kvStores().contains("default"));
            DeepAgent next = HarnessFactory.createDeepAgent(config(Map.of()), scope);
            try {
                assertSame(scope.kvStores().resolve("default"), next.getKvStore());
            } finally {
                next.destroy();
            }
        } finally {
            rail.uninit(agent);
            agent.destroy();
        }
    }

    @Test
    void unknownAndConflictingReferencesFailDuringRailAssembly() {
        var scope = new ApplicationStorageScope();
        for (Map<String, Object> todo : List.of(Map.<String, Object>of("storeRef", "missing"),
                Map.<String, Object>of("storeRef", "missing", "kvStoreType", "redis"))) {
            DeepAgent agent = HarnessFactory.createDeepAgent(config(todo), scope);
            try {
                assertThrows(IllegalArgumentException.class, () -> new TaskPlanningRail().init(agent));
            } finally {
                agent.destroy();
            }
        }
    }

    @Test
    void concurrentFirstTodoAssembliesObserveTheSameDefaultData() throws Exception {
        var scope = new ApplicationStorageScope();
        var first = spy(HarnessFactory.createDeepAgent(config(Map.of()), scope));
        var second = spy(HarnessFactory.createDeepAgent(config(Map.of()), scope));
        var firstRail = new TaskPlanningRail();
        var secondRail = new TaskPlanningRail();
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> {
                barrier.await();
                firstRail.init(first);
                return null;
            });
            var right = executor.submit(() -> {
                barrier.await();
                secondRail.init(second);
                return null;
            });
            left.get(10, TimeUnit.SECONDS);
            right.get(10, TimeUnit.SECONDS);
            var firstTools = ArgumentCaptor.forClass(Tool.class);
            var secondTools = ArgumentCaptor.forClass(Tool.class);
            verify(first, times(4)).registerHarnessTool(firstTools.capture());
            verify(second, times(4)).registerHarnessTool(secondTools.capture());
            Tool create = firstTools.getAllValues().stream().filter(t -> "todo_create".equals(t.getCard().getName()))
                    .findFirst().orElseThrow();
            Tool list = secondTools.getAllValues().stream().filter(t -> "todo_list".equals(t.getCard().getName()))
                    .findFirst().orElseThrow();
            create.invoke(Map.of("session_id", "shared-race", "tasks", List
                    .of(Map.of("content", "shared", "activeForm", "sharing", "description", "concurrent assembly"))));
            ToolOutput result = (ToolOutput) list.invoke(Map.of("session_id", "shared-race"));
            assertTrue(result.isSuccess());
            assertTrue(String.valueOf(result.getData()).contains("shared"));
        } finally {
            executor.shutdownNow();
            firstRail.uninit(first);
            secondRail.uninit(second);
            first.destroy();
            second.destroy();
        }
    }

    @Test
    void sqliteCheckpointerAndFirstTodoAssemblyPublishOneStableDefault() throws Exception {
        var scope = new ApplicationStorageScope();
        var sqlite = new SqliteKVStore(
                workspace.resolve("race.db").toString(), 30, true);
        var agent = spy(HarnessFactory.createDeepAgent(config(Map.of()), scope));
        var rail = new TaskPlanningRail();
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var checkpointer = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return CheckpointerFactory.create(
                        "persistence", Map.of("kv_store", sqlite), scope);
            });
            var todo = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                rail.init(agent);
                return null;
            });
            checkpointer.get(10, TimeUnit.SECONDS);
            todo.get(10, TimeUnit.SECONDS);
            var published = scope.kvStores().resolve("default");
            var tools = ArgumentCaptor.forClass(Tool.class);
            verify(agent, times(4)).registerHarnessTool(tools.capture());
            Tool create = tools.getAllValues().stream().filter(t -> "todo_create".equals(t.getCard().getName()))
                    .findFirst().orElseThrow();
            create.invoke(Map.of("session_id", "sqlite-race", "tasks", List.of(Map.of("content", "shared",
                    "activeForm", "sharing", "description", "SQLite races with Todo"))));
            assertEquals("shared", new KvTodoStorage(published).load("sqlite-race").get(0).getContent());
            var next = HarnessFactory.createDeepAgent(config(Map.of()), scope);
            try {
                assertSame(published, next.getKvStore());
            } finally {
                next.destroy();
            }
            assertSame(published, scope.kvStores().resolve("default"));
            // If Todo won, the explicit SQLite connection stays local instead of replacing
            // default.
            if (published != sqlite) {
                assertTrue(sqlite.getByPrefix("sqlite-race").isEmpty());
            }
        } finally {
            executor.shutdownNow();
            rail.uninit(agent);
            agent.destroy();
            sqlite.close();
        }
    }

    private DeepAgentConfig config(Map<String, Object> todo) {
        return DeepAgentConfig.builder().workspacePath(workspace.toString()).todoStorageType("kv")
                .todoStorageConfig(todo).build();
    }
}
