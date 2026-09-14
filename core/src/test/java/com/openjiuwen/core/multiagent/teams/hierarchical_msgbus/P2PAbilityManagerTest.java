/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.multiagent.team_runtime.CommunicableAgent;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for P2P ability dispatch.
 *
 * <p>Mirrors Python's {@code P2PAbilityManager} in
 * {@code openjiuwen/core/multi_agent/teams/hierarchical_msgbus/p2p_ability_manager.py}.</p>
 */
class P2PAbilityManagerTest {

    @Test
    void maxParallelSubAgentsIsClampedToAtLeastOne() {
        P2PAbilityManager manager = new P2PAbilityManager(new RecordingSupervisor(), 0);

        assertThat(manager.getMaxParallelSubAgents()).isEqualTo(1);
    }

    @Test
    void dispatchesAgentCardToolCallsThroughSupervisorSend() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        P2PAbilityManager manager = new P2PAbilityManager(supervisor, 2);
        manager.add(new AgentCard("agent-a", "delegate", "Delegate agent"));
        ToolCall call = toolCall("call-1", "delegate", "{\"question\":\"hi\"}");

        List<AbilityManager.ExecutionResult> results = manager.execute(
                null,
                call,
                new TestSession("session-1")
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isEqualTo("sent:agent-a:{question=hi}:session-1:1800.0");
        assertThat(results.get(0).toolMessage().getContent())
                .isEqualTo("sent:agent-a:{question=hi}:session-1:1800.0");
        assertThat(supervisor.lastMessage.get()).isEqualTo(Map.of("question", "hi"));
        assertThat(supervisor.lastRecipient.get()).isEqualTo("agent-a");
        assertThat(supervisor.lastSessionId.get()).isEqualTo("session-1");
        assertThat(supervisor.lastTimeout.get()).isEqualTo(1800.0);
    }

    @Test
    void preservesOriginalOrderWithMixedAgentAndBaseCalls() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        P2PAbilityManager manager = new P2PAbilityManager(supervisor, 2);
        manager.add(new AgentCard("agent-a", "delegate", "Delegate agent"));
        manager.add(new ToolCard("tool-a", "local_tool", "Local tool"));

        List<AbilityManager.ExecutionResult> results = manager.execute(
                null,
                List.of(toolCall("local-1", "local_tool", "{}"), toolCall("agent-1", "delegate", "{}")),
                new TestSession("session-1")
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).toolMessage().getToolCallId()).isEqualTo("local-1");
        assertThat(results.get(1).toolMessage().getToolCallId()).isEqualTo("agent-1");
        assertThat(results.get(1).result()).asString().startsWith("sent:agent-a:");
    }

    @Test
    void convertsAgentDispatchFailureToToolMessage() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        supervisor.fail = true;
        P2PAbilityManager manager = new P2PAbilityManager(supervisor, 2);
        manager.add(new AgentCard("agent-a", "delegate", "Delegate agent"));

        List<AbilityManager.ExecutionResult> results = manager.execute(
                null,
                toolCall("call-1", "delegate", "{}"),
                new TestSession("session-1")
        );

        assertThat(results).hasSize(1);
        assertThat(results.get(0).result()).isNull();
        assertThat(results.get(0).toolMessage().getContent())
                .asString()
                .contains("P2P parallel dispatch failed")
                .contains("P2P dispatch to 'delegate' failed");
    }

    @Test
    void invalidJsonArgumentsFallBackToEmptyPayload() {
        RecordingSupervisor supervisor = new RecordingSupervisor();
        P2PAbilityManager manager = new P2PAbilityManager(supervisor, 2);
        manager.add(new AgentCard("agent-a", "delegate", "Delegate agent"));

        manager.execute(null, toolCall("call-1", "delegate", "{bad"), new TestSession("session-1"));

        assertThat(supervisor.lastMessage.get()).isEqualTo(Map.of());
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        ToolCall call = new ToolCall();
        call.setId(id);
        call.setName(name);
        call.setArguments(arguments);
        return call;
    }

    private static final class RecordingSupervisor implements CommunicableAgent {
        private final AtomicReference<Object> lastMessage = new AtomicReference<>();
        private final AtomicReference<String> lastRecipient = new AtomicReference<>();
        private final AtomicReference<String> lastSessionId = new AtomicReference<>();
        private final AtomicReference<Double> lastTimeout = new AtomicReference<>();
        private boolean fail;

        @Override
        public CompletableFuture<Object> send(Object message, String recipient, String sessionId, Double timeout) {
            lastMessage.set(message);
            lastRecipient.set(recipient);
            lastSessionId.set(sessionId);
            lastTimeout.set(timeout);
            if (fail) {
                return CompletableFuture.failedFuture(new IllegalStateException("send failed"));
            }
            return CompletableFuture.completedFuture("sent:" + recipient + ":" + message + ":" + sessionId + ":"
                    + timeout);
        }
    }

    private record TestSession(String sessionId) implements AgentSessionApi {
        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return null;
        }

        @Override
        public void updateState(Map<String, Object> data) {
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return List.of().iterator();
        }
    }
}
