/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Small test-scope communicable agent base for translated multi-agent examples.
 *
 * <p>Mirrors Python's examples that combine {@code CommunicableAgent} and
 * {@code BaseAgent}.</p>
 */
public abstract class ExampleCommunicableAgent extends BaseAgent implements CommunicableAgent {

    private TeamRuntime runtime;
    private String agentId;

    protected ExampleCommunicableAgent(AgentCard card) {
        super(card);
    }

    @Override
    public BaseAgent configure(Object config) {
        return this;
    }

    @Override
    public Object getConfig() {
        return null;
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        return Collections.singletonList(invoke(inputs, session)).iterator();
    }

    @Override
    public void bindRuntime(TeamRuntime runtime, String agentId) {
        this.runtime = runtime;
        this.agentId = agentId;
    }

    @Override
    public boolean isBound() {
        return runtime != null && agentId != null;
    }

    @Override
    public java.util.concurrent.CompletableFuture<Object> send(Object message, String recipient, String sessionId) {
        ensureBound();
        return runtime.send(message, recipient, agentId, sessionId);
    }

    @Override
    public java.util.concurrent.CompletableFuture<Void> publish(Object message, String topicId, String sessionId) {
        ensureBound();
        return runtime.publish(message, topicId, agentId, sessionId);
    }

    @Override
    public void subscribe(String topicPattern) {
        ensureBound();
        runtime.subscribe(agentId, topicPattern);
    }

    @Override
    public void unsubscribe(String topicPattern) {
        ensureBound();
        runtime.getSubscriptionManager().unsubscribe(agentId, topicPattern);
    }

    @Override
    public String getAgentId() {
        return agentId;
    }

    @Override
    public TeamRuntime getRuntime() {
        return runtime;
    }

    private void ensureBound() {
        if (!isBound()) {
            throw new IllegalStateException("Agent is not bound to a TeamRuntime");
        }
    }
}
