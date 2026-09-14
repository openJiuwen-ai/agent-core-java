/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.team_runtime;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Mixin-style contract that adds messaging capabilities to an agent.
 *
 * <p>Mirrors Python's {@code CommunicableAgent} in
 * {@code openjiuwen/core/multi_agent/team_runtime/communicable_agent.py}.</p>
 */
public interface CommunicableAgent {

    Map<CommunicableAgent, Binding> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Runtime binding attached by {@link TeamRuntime#registerAgent}.
     *
     * @param runtime bound runtime
     * @param agentId bound agent id
     */
    record Binding(TeamRuntime runtime, String agentId) {
    }

    /**
     * Bind a {@link TeamRuntime} to this agent instance.
     *
     * @param runtime runtime instance
     * @param agentId this agent's id
     */
    default void bindRuntime(TeamRuntime runtime, String agentId) {
        Binding existing = BINDINGS.get(this);
        if (existing != null) {
            if (existing.runtime() == runtime && Objects.equals(existing.agentId(), agentId)) {
                return;
            }
            Loggers.MULTI_AGENT.warning("[{}] Agent '{}' is already bound to a runtime. "
                            + "Rebinding may cause unexpected behavior.",
                    getClass().getSimpleName(), existing.agentId());
        }
        BINDINGS.put(this, new Binding(runtime, agentId));
    }

    /**
     * Check whether this agent is currently bound to a runtime.
     *
     * @return true if bound
     */
    default boolean isBound() {
        Binding binding = BINDINGS.get(this);
        return binding != null && binding.runtime() != null && binding.agentId() != null;
    }

    /**
     * Return the bound runtime.
     *
     * @return bound runtime
     */
    default TeamRuntime getRuntime() {
        Binding binding = BINDINGS.get(this);
        if (binding == null || binding.runtime() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "error_msg",
                    "Agent not bound to a TeamRuntime. Register the agent with a TeamRuntime first."
            );
        }
        return binding.runtime();
    }

    /**
     * Return this agent's id in the bound runtime.
     *
     * @return agent id
     */
    default String getAgentId() {
        Binding binding = BINDINGS.get(this);
        if (binding == null || binding.agentId() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    "error_msg",
                    "Agent not bound to a TeamRuntime. Register the agent with a TeamRuntime first."
            );
        }
        return binding.agentId();
    }

    /**
     * Send a P2P message and wait for the recipient response.
     *
     * @param message message payload
     * @param recipient recipient agent id
     * @param sessionId optional session id
     * @return response future
     */
    default CompletableFuture<Object> send(Object message, String recipient, String sessionId) {
        return getRuntime().send(message, recipient, getAgentId(), sessionId);
    }

    /**
     * Send a P2P message with an optional timeout in seconds.
     *
     * @param message message payload
     * @param recipient recipient agent id
     * @param sessionId optional session id
     * @param timeout optional timeout in seconds
     * @return response future
     */
    default CompletableFuture<Object> send(Object message, String recipient, String sessionId, Double timeout) {
        return getRuntime().send(message, recipient, getAgentId(), sessionId, timeout);
    }

    /**
     * Publish a fire-and-forget message to a topic.
     *
     * @param message message payload
     * @param topicId topic id
     * @param sessionId optional session id
     * @return completion future
     */
    default CompletableFuture<Void> publish(Object message, String topicId, String sessionId) {
        return getRuntime().publish(message, topicId, getAgentId(), sessionId);
    }

    /**
     * Subscribe this agent to a topic pattern.
     *
     * @param topic topic pattern
     */
    default CompletableFuture<Void> subscribe(String topic) {
        return getRuntime().subscribe(getAgentId(), topic);
    }

    /**
     * Unsubscribe this agent from a topic pattern.
     *
     * @param topic topic pattern
     */
    default CompletableFuture<Void> unsubscribe(String topic) {
        return getRuntime().unsubscribe(getAgentId(), topic);
    }
}
