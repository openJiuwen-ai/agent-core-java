/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy;

import com.openjiuwen.core.controller.legacy.BaseController;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.legacy.config.AgentConfig;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Legacy controller-driven agent wrapper.
 */
public class ControllerAgent extends BaseAgent {

    private BaseController controller;

    public ControllerAgent(AgentConfig agentConfig, BaseController controller) {
        super(agentConfig);
        this.controller = controller;
        if (this.controller != null) {
            this.controller.setupFromAgent(this);
        }
    }

    public BaseController getController() {
        return controller;
    }

    public void setController(BaseController controller) {
        this.controller = controller;
        if (this.controller != null) {
            this.controller.setupFromAgent(this);
        }
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Session session) {
        AgentSessionApi effectiveSession = toAgentSession(inputs, session);
        try {
            return controller.invoke(inputs, effectiveSession);
        } finally {
            if (session == null) {
                effectiveSession.postRun();
            }
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Session session) {
        AgentSessionApi effectiveSession = toAgentSession(inputs, session);
        Object result = controller.invoke(inputs, effectiveSession);
        List<Object> outputs = new java.util.ArrayList<>();
        effectiveSession.streamOutput(outputs::add);
        if (outputs.isEmpty() && result != null) {
            outputs.add(result);
        }
        if (session == null) {
            effectiveSession.postRun();
        }
        return outputs.iterator();
    }

    private AgentSessionApi toAgentSession(Map<String, Object> inputs, Session session) {
        if (session instanceof AgentSessionApi agentSessionApi) {
            return agentSessionApi;
        }
        String sessionId = String.valueOf(inputs.getOrDefault("conversation_id", "default_session"));
        AgentSessionApi agentSessionApi = AgentSessionApi.create(sessionId, null, null);
        agentSessionApi.preRun(inputs);
        return agentSessionApi;
    }

    /**
     * Clear the default session.
     *
     * <p>Mirrors Python's {@code ControllerAgent.clear_session()} default
     * {@code session_id="default_session"} behavior.</p>
     */
    public void clearSession() {
        clearSession("default_session");
    }

    /**
     * Clear controller-specific and runner-managed session resources.
     *
     * <p>Mirrors Python's {@code ControllerAgent.clear_session(session_id)}:
     * call parent clear, clear context state for the session, then clean the
     * controller conversation subscription.</p>
     *
     * @param sessionId session ID to clear
     */
    @Override
    public void clearSession(String sessionId) {
        super.clearSession(sessionId);
        getContextEngine().clearContextBySession(sessionId);
        if (controller != null) {
            controller.cleanupConversation(sessionId);
        }
    }
}
