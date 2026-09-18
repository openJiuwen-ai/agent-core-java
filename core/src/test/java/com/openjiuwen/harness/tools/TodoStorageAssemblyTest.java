/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.TaskPlanningRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.spi.store.KVStoreFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verifies Todo configuration copying, inheritance and initialization compatibility.
 *
 * @since 0.1.16
 */
class TodoStorageAssemblyTest {
    @TempDir
    Path workspace;

    @Test
    void factoryCopyPreservesMissingAndExplicitFileTypes() {
        for (String type : Arrays.asList(null, "file", "kv")) {
            DeepAgentConfig source = DeepAgentConfig.builder().workspacePath(workspace.toString())
                    .todoStorageType(type).todoStorageConfig(Map.of("ttl", Map.of("default_ttl", 1))).build();
            try (DeepAgent agent = HarnessFactory.createDeepAgent(
                    AgentCard.builder().id(UUID.randomUUID().toString()).name("copy").build(), source, null)) {
                assertThat(agent.getConfig().isTodoStorageTypeExplicit()).isEqualTo(type != null);
                assertThat(agent.getConfig().getTodoStorageType()).isEqualTo(type == null ? "file" : type);
                assertThat(agent.getConfig().getTodoStorageConfig()).isEqualTo(source.getTodoStorageConfig());
                assertThat(agent.isInitialized()).isFalse();
            }
        }
    }

    @Test
    void setterDistinguishesExplicitFileFromDefault() {
        DeepAgentConfig config = new DeepAgentConfig();
        assertThat(config.getTodoStorageType()).isEqualTo("file");
        assertThat(config.isTodoStorageTypeExplicit()).isFalse();
        config.setTodoStorageType("file");
        assertThat(config.isTodoStorageTypeExplicit()).isTrue();
    }

    @Test
    void rejectsConflictingStoreBeforeCreatingAnyConnection() {
        DeepAgentConfig config = DeepAgentConfig.builder().todoStorageType("checkpointer_redis")
                .kvStoreConfig(Map.of("type", "redis", "conf", Map.of("host", "localhost")))
                .workspacePath(workspace.toString()).build();
        try (var stores = mockStatic(KVStoreFactory.class)) {
            assertThatThrownBy(() -> HarnessFactory.createDeepAgent(config))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("independent KV store");
            stores.verifyNoInteractions();
        }
    }

    @Test
    void missingReuseProviderDoesNotFallBackToFile() {
        try (var stores = mockStatic(TodoStorageFactory.class)) {
            stores.when(() -> TodoStorageFactory.create("checkpointer_redis", Map.of()))
                    .thenThrow(new IllegalArgumentException("No provider"));
            assertThatThrownBy(() -> TodoTool.fromConfig("checkpointer_redis", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("No provider");
        }
    }

    @Test
    void dynamicChildInheritsReuseAndTtlButPrecreatedChildIsUnchanged() {
        SubAgentConfig child = SubAgentConfig.builder()
                .agentCard(AgentCard.builder().name("dynamic").build()).build();
        DeepAgentConfig parentConfig = DeepAgentConfig.builder().todoStorageType("checkpointer_redis")
                .todoStorageConfig(Map.of("ttl", Map.of("default_ttl", 2)))
                .workspacePath(workspace.toString()).subagents(List.of(child)).build();
        try (DeepAgent parent = HarnessFactory.createDeepAgent(parentConfig);
                DeepAgent created = parent.createSubagent("dynamic", "session")) {
            assertThat(created.getConfig().getTodoStorageType()).isEqualTo("checkpointer_redis");
            assertThat(created.getConfig().getTodoStorageConfig()).isEqualTo(parentConfig.getTodoStorageConfig());
            assertThat(created.getConfig().getTodoStorageConfig())
                    .isNotSameAs(parent.getConfig().getTodoStorageConfig());
            parent.getConfig().setSubagents(List.of(created));
            created.getConfig().setTodoStorageType("file");
            assertThat(parent.createSubagent("dynamic", "other-session")).isSameAs(created);
            assertThat(created.getConfig().getTodoStorageType()).isEqualTo("file");
        }
    }

    @Test
    void taskPlanningUsesNewProviderAndInitializesOnlyOnce() {
        Checkpointer previous = CheckpointerFactory.getCheckpointer();
        RedisStore store = mock(RedisStore.class);
        CheckpointerFactory.setDefaultCheckpointer(new RedisCheckpointer(store, Map.of("default_ttl", 1)));
        TaskPlanningRail rail = spy(new TaskPlanningRail());
        DeepAgentConfig config = DeepAgentConfig.builder().todoStorageType("checkpointer_redis")
                .rails(List.of(rail)).workspacePath(workspace.toString()).build();
        try (DeepAgent agent = new DeepAgent(
                AgentCard.builder().id(UUID.randomUUID().toString()).name("todo").build(), config,
                Workspace.builder().rootPath(workspace.toString()).build())) {
            agent.ensureInitialized();
            agent.ensureInitialized();
            verify(rail, times(1)).init(agent);
            assertThat(agent.getRegisteredTools()).hasSize(4);
            verify(store, never()).close();
        } finally {
            CheckpointerFactory.setDefaultCheckpointer(previous);
        }
        verify(store, never()).close();
    }
}
