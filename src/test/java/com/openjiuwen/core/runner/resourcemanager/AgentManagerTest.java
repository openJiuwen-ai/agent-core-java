/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.runner.RunnerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors the provider-oriented behavior of Python's
 * {@code openjiuwen/core/runner/resources_manager/agent_manager.py}.
 */
class AgentManagerTest {

    @AfterEach
    void restoreRunnerConfig() {
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
    }

    @Test
    void localProviderIsResolvedLazilyByAgentId() {
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
        AgentManager manager = new AgentManager();
        Object agent = new Object();

        manager.addAgent("agent-1", () -> agent);

        assertSame(agent, manager.getAgent("agent-1").toCompletableFuture().join());
        assertNull(manager.getAgent("missing").toCompletableFuture().join());
    }

    @Test
    void duplicateLocalProviderIsRejectedByAbstractManager() {
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
        AgentManager manager = new AgentManager();

        manager.addAgent("agent-1", Object::new);

        assertThrows(IllegalArgumentException.class,
                () -> manager.addAgent("agent-1", Object::new));
    }

    @Test
    void removeAgentUnregistersLocalProviderWhenNotDistributed() {
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setDistributedMode(false);
        RunnerConfig.setRunnerConfig(config);
        AgentManager manager = new AgentManager();
        Supplier<Object> provider = Object::new;
        manager.addAgent("agent-1", provider);

        Supplier<?> removed = manager.removeAgent("agent-1");

        assertSame(provider, removed);
        assertNull(manager.getAgent("agent-1").toCompletableFuture().join());
        assertFalse(manager.containsRemoteAgent("agent-1"));
    }
}
