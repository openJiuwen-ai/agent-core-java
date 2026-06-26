/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter;
import com.openjiuwen.core.runner.drunner.server_adapter.MqServerAdapter;
import com.openjiuwen.core.single_agent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors the provider-oriented behavior of Python's
 * {@code openjiuwen/core/runner/resources_manager/agent_manager.py}.
 *
 * <p>Mirrors Python's {@code TestAgentManager} in
 * {@code tests/unit_tests/core/runner/resources_manager/test_agent_manager.py}.</p>
 */
class AgentManagerTest {

    @AfterEach
    void restoreRunnerConfig() {
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
        resetMqServerAdapterFactory();
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

    @Test
    void addAgentForwardsInterfaceUrlToAgentAdapter() {
        enableDistributedModeWithNoopMq();
        AgentManager manager = new AgentManager();

        manager.addAgent(
                "agent-x",
                Object::new,
                new AgentCard("agent-x", "agent-x", ""),
                "http://127.0.0.1:9000/a2a/jsonrpc"
        );

        AgentAdapter adapter = distributedAdapter(manager, "agent-x");
        assertEquals("agent-x", adapter.getAgentId());
        assertEquals("agent-x", adapter.getAgentCard().getId());
        assertEquals("http://127.0.0.1:9000/a2a/jsonrpc", adapter.getAgentCard().getInterfaceUrl());
        assertTrue(manager.containsDistributedAdapter("agent-x"));
    }

    @Test
    void addAgentPassesNullInterfaceUrlWhenOmitted() {
        enableDistributedModeWithNoopMq();
        AgentManager manager = new AgentManager();

        manager.addAgent("agent-y", Object::new, new AgentCard("agent-y", "agent-y", ""), null);

        AgentAdapter adapter = distributedAdapter(manager, "agent-y");
        assertEquals("agent-y", adapter.getAgentId());
        assertEquals("agent-y", adapter.getAgentCard().getId());
        assertNull(adapter.getAgentCard().getInterfaceUrl());
        assertTrue(manager.containsDistributedAdapter("agent-y"));
    }

    @Test
    void addAgentUsesAgentCardInterfaceUrlWhenKwargOmitted() {
        enableDistributedModeWithNoopMq();
        AgentManager manager = new AgentManager();
        AgentCard card = new AgentCard("agent-z", "agent-z", "");
        card.setInterfaceUrl("http://127.0.0.1:7001/a2a/jsonrpc/");

        manager.addAgent("agent-z", Object::new, card, null);

        AgentAdapter adapter = distributedAdapter(manager, "agent-z");
        assertEquals("agent-z", adapter.getAgentId());
        assertEquals("http://127.0.0.1:7001/a2a/jsonrpc/", adapter.getAgentCard().getInterfaceUrl());
    }

    @Test
    void addAgentInterfaceUrlKwargOverridesAgentCard() {
        enableDistributedModeWithNoopMq();
        AgentManager manager = new AgentManager();
        AgentCard card = new AgentCard("agent-o", "agent-o", "");
        card.setInterfaceUrl("http://127.0.0.1:7002/a2a/jsonrpc/");

        manager.addAgent(
                "agent-o",
                Object::new,
                card,
                "http://127.0.0.1:9999/a2a/jsonrpc/"
        );

        AgentAdapter adapter = distributedAdapter(manager, "agent-o");
        assertEquals("http://127.0.0.1:9999/a2a/jsonrpc/", adapter.getAgentCard().getInterfaceUrl());
    }

    private static void enableDistributedModeWithNoopMq() {
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setDistributedMode(true);
        config.setEnableA2a(false);
        RunnerConfig.setRunnerConfig(config);
        installNoopMqServerAdapterFactory();
    }

    @SuppressWarnings("unchecked")
    private static AgentAdapter distributedAdapter(AgentManager manager, String agentId) {
        try {
            Field field = AgentManager.class.getDeclaredField("remoteAgents");
            field.setAccessible(true);
            ThreadSafeDict<String, Object> remoteAgents = (ThreadSafeDict<String, Object>) field.get(manager);
            Object adapter = remoteAgents.get("agent_adapter_" + agentId);
            assertTrue(adapter instanceof AgentAdapter);
            return (AgentAdapter) adapter;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void installNoopMqServerAdapterFactory() {
        try {
            Class<?> factoryType = Class.forName(
                    "com.openjiuwen.core.runner.drunner.server_adapter.AgentAdapter$MqServerAdapterFactory"
            );
            Object factory = Proxy.newProxyInstance(
                    factoryType.getClassLoader(),
                    new Class<?>[] {factoryType},
                    (proxy, method, args) -> {
                        if ("create".equals(method.getName())) {
                            return new NoopMqServerAdapter(String.valueOf(args[0]), String.valueOf(args[1]));
                        }
                        if ("toString".equals(method.getName())) {
                            return "NoopMqServerAdapterFactory";
                        }
                        return null;
                    }
            );
            Method setter = AgentAdapter.class.getDeclaredMethod("setMqServerAdapterFactoryForTest", factoryType);
            setter.setAccessible(true);
            setter.invoke(null, factory);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void resetMqServerAdapterFactory() {
        try {
            Method reset = AgentAdapter.class.getDeclaredMethod("resetMqServerAdapterFactoryForTest");
            reset.setAccessible(true);
            reset.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class NoopMqServerAdapter extends MqServerAdapter {

        private NoopMqServerAdapter(String adapterId, String topic) {
            super(adapterId, topic, ignored -> Map.of(), ignored -> Map.of());
        }

        @Override
        public void start() {
            // Python uses monkeypatch to avoid starting real distributed infrastructure.
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
