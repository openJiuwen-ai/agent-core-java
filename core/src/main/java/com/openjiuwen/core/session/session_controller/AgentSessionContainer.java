/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Session-backed data container that delegates to an agent session.
 *
 * <p>Mirrors Python's {@code AgentSessionContainer} in
 * {@code openjiuwen/core/session/session_controller/data_container.py}.</p>
 */
public class AgentSessionContainer implements DataContainer {

    private AgentSession session;

    public AgentSessionContainer() {
        this(null);
    }

    public AgentSessionContainer(AgentSession session) {
        this.session = session;
    }

    public AgentSession getSession() {
        return session;
    }

    public void setSession(AgentSession session) {
        this.session = session;
    }

    public static DataContainerFactory.DataContainerProvider provider() {
        return new DataContainerFactory.DataContainerProvider() {
            @Override
            public DataContainer create(Map<String, Object> kwargs) {
                Object value = kwargs.get("session");
                return new AgentSessionContainer(value instanceof AgentSession agentSession ? agentSession : null);
            }

            @Override
            public CompletionStage<DataContainer> load(String agentId, String sessionId, Object serialized,
                                                       Map<String, Object> kwargs) {
                return AgentSessionContainer.load(agentId, sessionId, serialized).thenApply(container -> container);
            }
        };
    }

    public static CompletionStage<AgentSessionContainer> load(String agentId, String sessionId, Object serialized) {
        AgentCard card = new AgentCard(agentId, agentId, "");
        AgentSession agentSession = AgentSession.createAgentSession(sessionId, null, card);
        agentSession.preRun(null);
        return CompletableFuture.completedFuture(new AgentSessionContainer(agentSession));
    }

    @Override
    public CompletionStage<Object> dump() {
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public boolean update(Map<String, Object> data) {
        if (session == null) {
            return false;
        }
        session.updateState(data);
        return true;
    }

    @Override
    public Object get(Object key) {
        if (session == null) {
            return null;
        }
        if (key == null) {
            return session.dumpState();
        }
        return session.getState(key);
    }
}
